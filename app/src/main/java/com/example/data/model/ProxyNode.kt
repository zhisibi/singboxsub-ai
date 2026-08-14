package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "proxy_nodes")
data class ProxyNode(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val subscriptionId: Long = 0,
    val name: String,
    val protocol: String, // vless, vmess, ss, trojan, hysteria2, socks, http
    val server: String,
    val port: Int,
    val uuidOrPassword: String = "",
    val cipher: String = "", // for ss/vmess
    val alterId: Int = 0, // for vmess
    val network: String = "tcp", // tcp, ws, grpc, quic, http
    val path: String = "",
    val host: String = "",
    val tls: Boolean = false,
    val sni: String = "",
    val alpn: String = "",
    val allowInsecure: Boolean = false,
    val flow: String = "", // for vless
    val rawUri: String = "",
    val enabled: Boolean = true,
    val pingMs: Int = -1 // -1 = untested, >0 = ms latency, -2 = error
) {
    fun toUri(): String {
        if (rawUri.isNotBlank()) return rawUri
        val encodedName = try { java.net.URLEncoder.encode(name, "UTF-8") } catch (e: Exception) { name }
        return when (protocol.lowercase()) {
            "vless" -> {
                val tlsStr = if (tls) "tls" else "none"
                "vless://${uuidOrPassword}@${server}:${port}?type=${network.ifBlank { "tcp" }}&security=${tlsStr}&sni=${sni}&host=${host}&path=${path}&flow=${flow}#${encodedName}"
            }
            "vmess" -> {
                val vmessJson = org.json.JSONObject().apply {
                    put("v", "2")
                    put("ps", name)
                    put("add", server)
                    put("port", port)
                    put("id", uuidOrPassword)
                    put("aid", alterId)
                    put("scy", cipher.ifBlank { "auto" })
                    put("net", network.ifBlank { "tcp" })
                    put("type", "none")
                    put("host", host)
                    put("path", path)
                    put("tls", if (tls) "tls" else "")
                    put("sni", sni)
                }
                val b64 = android.util.Base64.encodeToString(vmessJson.toString().toByteArray(java.nio.charset.StandardCharsets.UTF_8), android.util.Base64.NO_WRAP)
                "vmess://${b64}"
            }
            "ss", "shadowsocks" -> {
                val userPass = android.util.Base64.encodeToString("${cipher.ifBlank { "aes-256-gcm" }}:${uuidOrPassword}".toByteArray(java.nio.charset.StandardCharsets.UTF_8), android.util.Base64.NO_WRAP)
                "ss://${userPass}@${server}:${port}#${encodedName}"
            }
            "trojan" -> {
                "trojan://${uuidOrPassword}@${server}:${port}?sni=${if (sni.isNotBlank()) sni else server}#${encodedName}"
            }
            "hysteria2", "hy2" -> {
                val insecureVal = if (allowInsecure) "1" else "0"
                "hy2://${uuidOrPassword}@${server}:${port}?sni=${if (sni.isNotBlank()) sni else server}&insecure=${insecureVal}#${encodedName}"
            }
            "anytls" -> {
                val insecureVal = if (allowInsecure) "1" else "0"
                "anytls://${uuidOrPassword}@${server}:${port}?sni=${if (sni.isNotBlank()) sni else server}&insecure=${insecureVal}#${encodedName}"
            }
            "socks", "socks5" -> {
                if (host.isNotBlank()) {
                    "socks5://${host}:${uuidOrPassword}@${server}:${port}#${encodedName}"
                } else {
                    "socks5://${server}:${port}#${encodedName}"
                }
            }
            else -> {
                if (host.isNotBlank()) {
                    "http://${host}:${uuidOrPassword}@${server}:${port}#${encodedName}"
                } else {
                    "http://${server}:${port}#${encodedName}"
                }
            }
        }
    }
}
