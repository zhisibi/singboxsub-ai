package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "proxy_nodes")
data class ProxyNode(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val subscriptionId: Long = 0,
    val name: String,
    val protocol: String, // vless, vmess, ss, trojan, hysteria2, anytls, tuic, socks, http
    val server: String,
    val port: Int,
    val uuidOrPassword: String = "",
    val cipher: String = "", // for ss/vmess
    val alterId: Int = 0, // for vmess
    val network: String = "tcp", // tcp, ws, grpc, quic, http, httpupgrade
    val path: String = "",
    val host: String = "",
    val tls: Boolean = false,
    val sni: String = "",
    val alpn: String = "",
    val allowInsecure: Boolean = false,
    val flow: String = "", // for vless (e.g. xtls-rprx-vision)
    val fingerprint: String = "", // chrome, firefox, safari, etc.
    val realityPublicKey: String = "", // pbk
    val realityShortId: String = "", // sid
    val realitySpiderX: String = "", // spx
    val grpcServiceName: String = "", // grpc serviceName
    val obfs: String = "", // hysteria2 obfs type e.g. salamander
    val obfsPassword: String = "", // hysteria2 obfs password
    val rawUri: String = "",
    val enabled: Boolean = true,
    val pingMs: Int = -1 // -1 = untested, >0 = ms latency, -2 = error
) {
    fun toUri(): String {
        if (rawUri.isNotBlank()) return rawUri
        val encodedName = try { java.net.URLEncoder.encode(name, "UTF-8") } catch (e: Exception) { name }
        val cleanServer = server.removePrefix("[").removeSuffix("]").trim()
        val formattedHost = if (cleanServer.contains(":")) "[$cleanServer]" else cleanServer
        val effectiveSni = if (sni.isNotBlank()) sni.removePrefix("[").removeSuffix("]") else ""
        return when (protocol.lowercase()) {
            "vless" -> {
                val securityStr = when {
                    realityPublicKey.isNotBlank() -> "reality"
                    tls -> "tls"
                    else -> "none"
                }
                val sb = StringBuilder("vless://${uuidOrPassword}@${formattedHost}:${port}?")
                sb.append("type=${network.ifBlank { "tcp" }}")
                sb.append("&security=$securityStr")
                if (effectiveSni.isNotBlank()) sb.append("&sni=$effectiveSni")
                if (host.isNotBlank()) sb.append("&host=$host")
                if (path.isNotBlank()) sb.append("&path=$path")
                if (flow.isNotBlank()) sb.append("&flow=$flow")
                if (fingerprint.isNotBlank()) sb.append("&fp=$fingerprint")
                if (realityPublicKey.isNotBlank()) sb.append("&pbk=$realityPublicKey")
                if (realityShortId.isNotBlank()) sb.append("&sid=$realityShortId")
                if (realitySpiderX.isNotBlank()) sb.append("&spx=$realitySpiderX")
                if (grpcServiceName.isNotBlank()) sb.append("&serviceName=$grpcServiceName")
                if (allowInsecure) sb.append("&insecure=1")
                sb.append("#$encodedName")
                sb.toString()
            }
            "vmess" -> {
                val vmessJson = org.json.JSONObject().apply {
                    put("v", "2")
                    put("ps", name)
                    put("add", cleanServer)
                    put("port", port)
                    put("id", uuidOrPassword)
                    put("aid", alterId)
                    put("scy", cipher.ifBlank { "auto" })
                    put("net", network.ifBlank { "tcp" })
                    put("type", "none")
                    put("host", host)
                    put("path", path)
                    put("tls", if (tls) "tls" else "")
                    put("sni", effectiveSni)
                }
                val b64 = android.util.Base64.encodeToString(vmessJson.toString().toByteArray(java.nio.charset.StandardCharsets.UTF_8), android.util.Base64.NO_WRAP)
                "vmess://${b64}"
            }
            "ss", "shadowsocks" -> {
                val userPass = android.util.Base64.encodeToString("${cipher.ifBlank { "aes-256-gcm" }}:${uuidOrPassword}".toByteArray(java.nio.charset.StandardCharsets.UTF_8), android.util.Base64.NO_WRAP)
                "ss://${userPass}@${formattedHost}:${port}#${encodedName}"
            }
            "trojan" -> {
                val sb = StringBuilder("trojan://${uuidOrPassword}@${formattedHost}:${port}?")
                if (effectiveSni.isNotBlank()) sb.append("sni=$effectiveSni")
                if (network.isNotBlank() && network != "tcp") sb.append("&type=$network")
                if (host.isNotBlank()) sb.append("&host=$host")
                if (path.isNotBlank()) sb.append("&path=$path")
                if (grpcServiceName.isNotBlank()) sb.append("&serviceName=$grpcServiceName")
                if (allowInsecure) sb.append("&insecure=1")
                sb.append("#$encodedName")
                sb.toString()
            }
            "hysteria2", "hy2" -> {
                val insecureVal = if (allowInsecure) "1" else "0"
                val sb = StringBuilder("hy2://${uuidOrPassword}@${formattedHost}:${port}?insecure=${insecureVal}")
                if (effectiveSni.isNotBlank()) sb.append("&sni=$effectiveSni")
                if (obfs.isNotBlank()) sb.append("&obfs=$obfs")
                if (obfsPassword.isNotBlank()) sb.append("&obfs-password=$obfsPassword")
                sb.append("#$encodedName")
                sb.toString()
            }
            "anytls" -> {
                val insecureVal = if (allowInsecure) "1" else "0"
                val sniPart = if (effectiveSni.isNotBlank()) "&sni=$effectiveSni" else ""
                "anytls://${uuidOrPassword}@${formattedHost}:${port}?insecure=${insecureVal}${sniPart}#${encodedName}"
            }
            "socks", "socks5" -> {
                if (host.isNotBlank()) {
                    "socks5://${host}:${uuidOrPassword}@${formattedHost}:${port}#${encodedName}"
                } else {
                    "socks5://${formattedHost}:${port}#${encodedName}"
                }
            }
            else -> {
                if (host.isNotBlank()) {
                    "http://${host}:${uuidOrPassword}@${formattedHost}:${port}#${encodedName}"
                } else {
                    "http://${formattedHost}:${port}#${encodedName}"
                }
            }
        }
    }
}
