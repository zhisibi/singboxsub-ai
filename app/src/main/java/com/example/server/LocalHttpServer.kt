package com.example.server

import android.content.Context
import com.example.data.db.AppDatabase
import com.example.data.generator.Base64Generator
import com.example.data.generator.ClashConfigGenerator
import com.example.data.generator.SingBoxConfigGenerator
import com.example.data.model.ProxyNode
import com.example.data.model.ServerLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder

class LocalHttpServer(private val context: Context) {

    private val db = AppDatabase.getDatabase(context)
    private var serverSocket: ServerSocket? = null
    private var serverJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning

    private val _port = MutableStateFlow(8080)
    val port: StateFlow<Int> = _port

    private val _secretToken = MutableStateFlow("")
    val secretToken: StateFlow<String> = _secretToken

    private val _requestCount = MutableStateFlow(0)
    val requestCount: StateFlow<Int> = _requestCount

    fun startServer(port: Int = 8080, secretToken: String = "") {
        stopServer()

        _port.value = port
        _secretToken.value = secretToken

        serverJob = scope.launch(Dispatchers.IO) {
            var ss: ServerSocket? = null
            var bound = false
            var attempts = 0

            while (!bound && attempts < 5) {
                try {
                    ss = ServerSocket()
                    ss.reuseAddress = true
                    ss.bind(InetSocketAddress("0.0.0.0", port))
                    bound = true
                } catch (e: Exception) {
                    attempts++
                    try { ss?.close() } catch (_: Exception) {}
                    ss = null
                    if (attempts < 5) Thread.sleep(150)
                }
            }

            if (!bound || ss == null) {
                _isRunning.value = false
                return@launch
            }

            serverSocket = ss
            _isRunning.value = true

            try {
                while (_isRunning.value && !ss.isClosed) {
                    try {
                        val clientSocket = ss.accept()
                        handleClient(clientSocket)
                    } catch (e: Exception) {
                        if (!_isRunning.value || ss.isClosed) break
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isRunning.value = false
            }
        }
    }

    fun stopServer() {
        _isRunning.value = false
        try {
            serverSocket?.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        serverSocket = null
        serverJob?.cancel()
        serverJob = null
    }

    private fun handleClient(socket: Socket) {
        scope.launch {
            try {
                socket.soTimeout = 5000
                val inputStream = socket.getInputStream()
                val reader = BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8))
                val outputStream = socket.getOutputStream()

                val requestLine = reader.readLine() ?: return@launch
                val parts = requestLine.split(" ")
                if (parts.size < 2) return@launch

                val method = parts[0]
                val fullPath = parts[1]

                // Read headers
                var userAgent = ""
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    if (line.isNullOrEmpty()) break
                    if (line!!.startsWith("User-Agent:", ignoreCase = true)) {
                        userAgent = line!!.substringAfter(":").trim()
                    }
                }

                _requestCount.value += 1
                val clientIp = socket.inetAddress?.hostAddress ?: "127.0.0.1"

                val uriParts = fullPath.split("?", limit = 2)
                val path = uriParts[0]
                val queryParams = if (uriParts.size > 1) parseQueryParams(uriParts[1]) else emptyMap()

                // Handle CORS preflight OPTIONS request
                if (method.equals("OPTIONS", ignoreCase = true)) {
                    sendRawResponse(outputStream, 200, "OK", "text/plain; charset=UTF-8", "OK")
                    socket.close()
                    return@launch
                }

                // Token check - Local requests from 127.0.0.1 / localhost are always allowed
                val reqToken = queryParams["token"]?.trim() ?: ""
                val currentSecret = _secretToken.value.trim()

                val isLocalRequest = clientIp == "127.0.0.1" || clientIp == "localhost" || clientIp == "::1" || clientIp == "0:0:0:0:0:0:0:1"
                val isAuthorized = when {
                    isLocalRequest -> true
                    currentSecret.isEmpty() -> true
                    reqToken == currentSecret -> true
                    else -> {
                        val customSubTokens = try {
                            db.savedCustomSubDao().getAllSavedCustomSubsList().mapNotNull { sub -> sub.token.trim().ifBlank { null } }
                        } catch (e: Exception) {
                            emptyList()
                        }
                        reqToken.isNotEmpty() && customSubTokens.contains(reqToken)
                    }
                }

                if (!isAuthorized) {
                    sendRawResponse(outputStream, 403, "Forbidden", "text/plain; charset=UTF-8", "Error: Invalid Security Token / 鉴权Token错误")
                    logAccess(clientIp, path, "unauthorized", userAgent, 403)
                    socket.close()
                    return@launch
                }

                // Routing
                when {
                    path == "/" || path == "/index.html" -> {
                        val html = buildDashboardHtml(clientIp)
                        sendRawResponse(outputStream, 200, "OK", "text/html; charset=UTF-8", html)
                        logAccess(clientIp, path, "web_dashboard", userAgent, 200)
                    }
                    path == "/sub" || path == "/singbox" || path == "/mihomo" || path == "/config" || path == "/clash" || path == "/base64" -> {
                        val requestedType = queryParams["type"]?.lowercase() ?: when {
                            path == "/mihomo" || path == "/clash" -> "mihomo"
                            path == "/base64" -> "base64"
                            userAgent.contains("Clash", ignoreCase = true) || userAgent.contains("Mihomo", ignoreCase = true) -> "mihomo"
                            userAgent.contains("v2ray", ignoreCase = true) -> "base64"
                            else -> "singbox"
                        }

                        // Filter nodes by query 'nodes' parameter if present
                        val nodesParam = queryParams["nodes"]
                        val allDbNodes = db.proxyNodeDao().getAllNodes()
                        val enabledNodes = db.proxyNodeDao().getEnabledNodes().ifEmpty { allDbNodes }

                        val targetNodes: List<ProxyNode> = if (!nodesParam.isNullOrBlank()) {
                            val ids = nodesParam.split(",").mapNotNull { it.trim().toLongOrNull() }
                            if (ids.isNotEmpty()) {
                                val found = db.proxyNodeDao().getNodesByIds(ids)
                                if (found.isNotEmpty()) found else enabledNodes
                            } else {
                                enabledNodes
                            }
                        } else {
                            enabledNodes
                        }

                        when (requestedType) {
                            "mihomo", "clash", "clashmeta" -> {
                                val yaml = ClashConfigGenerator.generateYaml(targetNodes, isMihomo = true)
                                sendRawResponse(outputStream, 200, "OK", "text/yaml; charset=UTF-8", yaml, filename = "subscription.yaml")
                                logAccess(clientIp, path, "mihomo", userAgent, 200)
                            }
                            "base64" -> {
                                val b64 = Base64Generator.generateBase64(targetNodes)
                                sendRawResponse(outputStream, 200, "OK", "text/plain; charset=UTF-8", b64, filename = "subscription.txt")
                                logAccess(clientIp, path, "base64", userAgent, 200)
                            }
                            else -> { // singbox
                                val json = SingBoxConfigGenerator.generateJson(targetNodes, inboundPort = 2080)
                                sendRawResponse(outputStream, 200, "OK", "application/json; charset=UTF-8", json, filename = "singbox.json")
                                logAccess(clientIp, path, "singbox", userAgent, 200)
                            }
                        }
                    }
                    else -> {
                        sendRawResponse(outputStream, 404, "Not Found", "text/plain; charset=UTF-8", "404 Not Found")
                        logAccess(clientIp, path, "unknown", userAgent, 404)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                try {
                    socket.close()
                } catch (e: Exception) {
                    // ignore
                }
            }
        }
    }

    private fun sendRawResponse(
        outputStream: OutputStream,
        statusCode: Int,
        statusText: String,
        contentType: String,
        body: String,
        filename: String = ""
    ) {
        val bodyBytes = body.toByteArray(Charsets.UTF_8)
        var header = "HTTP/1.1 $statusCode $statusText\r\n" +
                "Content-Type: $contentType\r\n" +
                "Content-Length: ${bodyBytes.size}\r\n" +
                "Access-Control-Allow-Origin: *\r\n" +
                "Access-Control-Allow-Methods: GET, POST, OPTIONS\r\n" +
                "profile-update-interval: 24\r\n" +
                "subscription-userinfo: upload=0; download=0; total=1073741824000; expire=0\r\n"
        if (filename.isNotEmpty()) {
            header += "Content-Disposition: attachment; filename=\"$filename\"\r\n"
        }
        header += "Connection: close\r\n\r\n"

        outputStream.write(header.toByteArray(Charsets.UTF_8))
        outputStream.write(bodyBytes)
        outputStream.flush()
    }

    private fun logAccess(clientIp: String, path: String, format: String, userAgent: String, statusCode: Int) {
        scope.launch {
            db.serverLogDao().insertLog(
                ServerLog(
                    clientIp = clientIp,
                    path = path,
                    format = format,
                    userAgent = userAgent,
                    statusCode = statusCode
                )
            )
        }
    }

    private fun parseQueryParams(queryString: String): Map<String, String> {
        val map = mutableMapOf<String, String>()
        queryString.split("&").forEach { pair ->
            val split = pair.split("=", limit = 2)
            if (split.size == 2) {
                map[urlDecode(split[0])] = urlDecode(split[1])
            }
        }
        return map
    }

    private fun urlDecode(str: String): String {
        return try {
            URLDecoder.decode(str, "UTF-8")
        } catch (e: Exception) {
            str
        }
    }

    private fun buildDashboardHtml(clientIp: String): String {
        val lanIp = NetworkUtils.getLocalIpAddress()
        val port = _port.value
        val token = _secretToken.value
        val tokenParam = if (token.isNotEmpty()) "&token=$token" else ""

        return """
            <!DOCTYPE html>
            <html lang="zh-CN">
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>SingBox & Mihomo Sub Local Server</title>
                <style>
                    body { font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif; background: #0f172a; color: #f8fafc; padding: 20px; margin: 0; }
                    .card { background: #1e293b; border-radius: 12px; padding: 18px; margin-bottom: 16px; border: 1px solid #334155; }
                    h1 { color: #38bdf8; margin-top: 0; font-size: 20px; }
                    .tag { display: inline-block; background: #0284c7; color: white; padding: 4px 10px; border-radius: 6px; font-size: 11px; font-weight: bold; margin-right: 6px; }
                    .url-box { background: #0f172a; padding: 10px; border-radius: 8px; font-family: monospace; color: #7dd3fc; word-break: break-all; margin: 8px 0; border: 1px solid #334155; font-size: 13px; }
                    a.btn { display: inline-block; background: #38bdf8; color: #0f172a; font-weight: bold; padding: 8px 14px; border-radius: 6px; text-decoration: none; margin-top: 6px; font-size: 13px; }
                </style>
            </head>
            <body>
                <h1>⚡ SingBox Sub Local Server (局域网订阅服务器)</h1>
                <div class="card">
                    <div><span class="tag">Server Active</span> Address: <strong>$lanIp:$port</strong></div>
                    <p style="color:#94a3b8; font-size: 13px;">Client IP: $clientIp</p>
                </div>
                
                <div class="card">
                    <h3>🚀 Sing-Box 1.14+ JSON 订阅</h3>
                    <div class="url-box">http://$lanIp:$port/sub?type=singbox$tokenParam</div>
                    <a class="btn" href="/sub?type=singbox$tokenParam">获取 Sing-Box 配置</a>
                </div>

                <div class="card">
                    <h3>⚡ Mihomo / Clash Meta YAML 订阅</h3>
                    <div class="url-box">http://$lanIp:$port/sub?type=mihomo$tokenParam</div>
                    <a class="btn" href="/sub?type=mihomo$tokenParam">获取 Mihomo 配置</a>
                </div>

                <div class="card">
                    <h3>🔗 Base64 通用节点订阅</h3>
                    <div class="url-box">http://$lanIp:$port/sub?type=base64$tokenParam</div>
                    <a class="btn" href="/sub?type=base64$tokenParam">查看 Base64 URIs</a>
                </div>
            </body>
            </html>
        """.trimIndent()
    }
}
