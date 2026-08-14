package com.example.data.parser

import android.util.Base64
import com.example.data.model.ProxyNode
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

object SubscriptionParser {

    fun parseContent(rawContent: String, subscriptionId: Long = 0): List<ProxyNode> {
        val trimmed = rawContent.trim()
        if (trimmed.isEmpty()) return emptyList()

        // 1. Try Base64 decoding first
        val decodedBase64 = tryDecodeBase64(trimmed)
        val contentToParse = if (decodedBase64.lines().size > 1 || decodedBase64.contains("://") || decodedBase64.contains("name:") || decodedBase64.contains("proxies:")) {
            decodedBase64
        } else {
            trimmed
        }

        // 2. Try JSON (Sing-box config or Clash JSON)
        if (contentToParse.startsWith("{")) {
            val jsonNodes = parseSingBoxJson(contentToParse, subscriptionId)
            if (jsonNodes.isNotEmpty()) return jsonNodes
        }

        // 2.5. Try Clash YAML format
        if (contentToParse.contains("proxies:") || (contentToParse.contains("name:") && contentToParse.contains("server:"))) {
            val yamlNodes = parseClashYaml(contentToParse, subscriptionId)
            if (yamlNodes.isNotEmpty()) return yamlNodes
        }

        // 3. Try Line-by-line URI format
        val nodes = mutableListOf<ProxyNode>()
        contentToParse.lines().forEach { rawLine ->
            val line = rawLine.trim()
            if (line.isNotEmpty() && !line.startsWith("#")) {
                val node = parseUri(line, subscriptionId)
                if (node != null) {
                    nodes.add(node)
                }
            }
        }

        return nodes
    }

    /**
     * Parses host:port safely handling IPv6 (e.g. [2001:db8::1]:8080 or 2001:db8::1) and trailing queries.
     */
    private fun parseAddressAndPort(rawHostPort: String, defaultPort: Int = 443): Pair<String, Int> {
        val clean = rawHostPort.trim().substringBefore("?").substringBefore("/")
        if (clean.isEmpty()) return Pair("", defaultPort)

        // Handle IPv6 bracket format: [2602:294:0:b7::1]:8884 or [2602:294:0:b7::1]
        if (clean.startsWith("[")) {
            val closeBracketIdx = clean.indexOf("]")
            if (closeBracketIdx > 0) {
                val host = clean.substring(1, closeBracketIdx) // remove brackets for standard server field
                val after = clean.substring(closeBracketIdx + 1)
                val port = if (after.startsWith(":")) {
                    after.removePrefix(":").toIntOrNull() ?: defaultPort
                } else {
                    defaultPort
                }
                return Pair(host, port)
            }
        }

        // If it contains multiple colons and no brackets, it's a raw IPv6 address without explicit port
        if (clean.count { it == ':' } > 1) {
            return Pair(clean, defaultPort)
        }

        // Handle normal host:port (IPv4 or domain)
        val colonIdx = clean.lastIndexOf(":")
        return if (colonIdx > 0) {
            val host = clean.substring(0, colonIdx)
            val port = clean.substring(colonIdx + 1).toIntOrNull() ?: defaultPort
            Pair(host, port)
        } else {
            Pair(clean, defaultPort)
        }
    }

    private fun parseClashYaml(yamlStr: String, subscriptionId: Long): List<ProxyNode> {
        val nodes = mutableListOf<ProxyNode>()
        try {
            val lines = yamlStr.lines()
            var inProxies = false
            var currentProxyProps = mutableMapOf<String, String>()
            var currentSubMap = "" // "ws-opts", "grpc-opts", "reality-opts", "headers"

            fun flushProxy() {
                if (currentProxyProps.isNotEmpty()) {
                    val name = currentProxyProps["name"] ?: "Clash Node"
                    val type = currentProxyProps["type"] ?: "ss"
                    val server = currentProxyProps["server"] ?: ""
                    val port = currentProxyProps["port"]?.toIntOrNull() ?: 443
                    if (server.isNotBlank()) {
                        val password = currentProxyProps["password"] ?: currentProxyProps["uuid"] ?: currentProxyProps["passwd"] ?: ""
                        val cipher = currentProxyProps["cipher"] ?: "aes-256-gcm"
                        val alterId = currentProxyProps["alterid"]?.toIntOrNull() ?: 0
                        val sni = currentProxyProps["sni"] ?: currentProxyProps["servername"] ?: ""
                        val network = currentProxyProps["network"] ?: "tcp"
                        val path = currentProxyProps["path"] ?: currentProxyProps["ws-opts.path"] ?: ""
                        val host = currentProxyProps["host"] ?: currentProxyProps["ws-opts.headers.host"] ?: ""
                        val flow = currentProxyProps["flow"] ?: ""
                        val fp = currentProxyProps["client-fingerprint"] ?: currentProxyProps["fingerprint"] ?: ""
                        val pbk = currentProxyProps["public-key"] ?: currentProxyProps["reality-opts.public-key"] ?: ""
                        val sid = currentProxyProps["short-id"] ?: currentProxyProps["reality-opts.short-id"] ?: ""
                        val grpcService = currentProxyProps["grpc-service-name"] ?: currentProxyProps["grpc-opts.grpc-service-name"] ?: ""
                        val obfs = currentProxyProps["obfs"] ?: ""
                        val obfsPass = currentProxyProps["obfs-password"] ?: ""

                        val isTls = currentProxyProps["tls"]?.toBoolean() ?: (
                            type.lowercase() in setOf("vless", "trojan", "hysteria2", "tuic", "anytls") || pbk.isNotBlank()
                        )
                        val insecure = currentProxyProps["skip-cert-verify"]?.toBoolean() ?: false

                        val validTypes = setOf("ss", "ssr", "vmess", "vless", "trojan", "hysteria", "hysteria2", "hy2", "socks5", "socks", "http", "snell", "tuic", "wireguard", "shadowsocks", "anytls")
                        if (validTypes.contains(type.lowercase()) && server.isNotBlank() && !server.contains("://")) {
                            nodes.add(
                                ProxyNode(
                                    subscriptionId = subscriptionId,
                                    name = name,
                                    protocol = type,
                                    server = server,
                                    port = port,
                                    uuidOrPassword = password,
                                    cipher = cipher,
                                    alterId = alterId,
                                    network = network,
                                    path = path,
                                    host = host,
                                    tls = isTls,
                                    sni = sni,
                                    allowInsecure = insecure,
                                    flow = flow,
                                    fingerprint = fp,
                                    realityPublicKey = pbk,
                                    realityShortId = sid,
                                    grpcServiceName = grpcService,
                                    obfs = obfs,
                                    obfsPassword = obfsPass
                                )
                            )
                        }
                    }
                    currentProxyProps.clear()
                    currentSubMap = ""
                }
            }

            for (rawLine in lines) {
                val line = rawLine.trim()
                if (line.startsWith("proxies:")) {
                    inProxies = true
                    continue
                }
                if (inProxies) {
                    if (line.startsWith("proxy-groups:") || line.startsWith("rules:") || line.startsWith("rule-providers:")) {
                        flushProxy()
                        inProxies = false
                        break
                    }
                    if (line.startsWith("- ")) {
                        flushProxy()
                        currentSubMap = ""
                        val contentAfterDash = line.removePrefix("- ").trim()
                        if (contentAfterDash.contains(":")) {
                            val kv = contentAfterDash.split(":", limit = 2)
                            if (kv.size == 2) {
                                currentProxyProps[kv[0].trim().lowercase()] = kv[1].trim().removeSurrounding("\"").removeSurrounding("'")
                            }
                        }
                    } else if (line.contains(":") && !line.startsWith("#")) {
                        val kv = line.split(":", limit = 2)
                        if (kv.size == 2) {
                            val rawKey = kv[0].trim().lowercase()
                            val rawVal = kv[1].trim().removeSurrounding("\"").removeSurrounding("'")
                            if (rawVal.isEmpty() && (rawKey.endsWith("-opts") || rawKey == "headers")) {
                                currentSubMap = rawKey
                            } else {
                                if (currentSubMap.isNotEmpty()) {
                                    currentProxyProps["$currentSubMap.$rawKey"] = rawVal
                                }
                                currentProxyProps[rawKey] = rawVal
                            }
                        }
                    }
                }
            }
            flushProxy()
        } catch (e: Exception) {
            // ignore
        }
        return nodes
    }

    private fun parseUri(uri: String, subscriptionId: Long): ProxyNode? {
        return try {
            when {
                uri.startsWith("vless://", ignoreCase = true) -> parseVless(uri, subscriptionId)
                uri.startsWith("vmess://", ignoreCase = true) -> parseVmess(uri, subscriptionId)
                uri.startsWith("ss://", ignoreCase = true) -> parseShadowsocks(uri, subscriptionId)
                uri.startsWith("trojan://", ignoreCase = true) -> parseTrojan(uri, subscriptionId)
                uri.startsWith("hy2://", ignoreCase = true) || uri.startsWith("hysteria2://", ignoreCase = true) -> parseHysteria2(uri, subscriptionId)
                uri.startsWith("anytls://", ignoreCase = true) -> parseAnyTls(uri, subscriptionId)
                uri.startsWith("socks5://", ignoreCase = true) || uri.startsWith("socks://", ignoreCase = true) -> parseSocks(uri, subscriptionId)
                uri.startsWith("http://", ignoreCase = true) || uri.startsWith("https://", ignoreCase = true) -> parseHttp(uri, subscriptionId)
                else -> null
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun parseVless(uri: String, subscriptionId: Long): ProxyNode? {
        val main = uri.removePrefix("vless://").removePrefix("VLESS://")
        val nameSplit = main.split("#", limit = 2)
        val name = if (nameSplit.size > 1) urlDecode(nameSplit[1]) else "VLESS Node"
        val body = nameSplit[0]

        val querySplit = body.split("?", limit = 2)
        val userInfoAndAddress = querySplit[0]
        val queryParams = if (querySplit.size > 1) parseQueryParams(querySplit[1]) else emptyMap()

        val atSplit = userInfoAndAddress.split("@", limit = 2)
        if (atSplit.size < 2) return null
        val uuid = atSplit[0]

        val (server, port) = parseAddressAndPort(atSplit[1], 443)
        if (server.isBlank()) return null

        val network = queryParams["type"] ?: queryParams["network"] ?: "tcp"
        val security = (queryParams["security"] ?: "").lowercase()
        val pbk = queryParams["pbk"] ?: queryParams["publickey"] ?: ""
        val sid = queryParams["sid"] ?: queryParams["shortid"] ?: ""
        val spx = queryParams["spx"] ?: queryParams["spiderx"] ?: ""
        val fp = queryParams["fp"] ?: queryParams["fingerprint"] ?: queryParams["client-fingerprint"] ?: (if (security == "reality" || pbk.isNotBlank()) "chrome" else "")
        val tls = security == "tls" || security == "reality" || pbk.isNotBlank()
        val sni = queryParams["sni"] ?: queryParams["peer"] ?: server
        val host = queryParams["host"] ?: queryParams["headerType"] ?: ""
        val path = queryParams["path"] ?: ""
        val flow = queryParams["flow"] ?: ""
        val grpcService = queryParams["serviceName"] ?: queryParams["servicename"] ?: queryParams["grpc-service-name"] ?: ""
        val insecure = queryParams["insecure"] == "1" || queryParams["allow_insecure"] == "1" || queryParams["allowInsecure"] == "1"

        return ProxyNode(
            subscriptionId = subscriptionId,
            name = name,
            protocol = "vless",
            server = server,
            port = port,
            uuidOrPassword = uuid,
            network = network,
            path = path,
            host = host,
            tls = tls,
            sni = sni,
            flow = flow,
            fingerprint = fp,
            realityPublicKey = pbk,
            realityShortId = sid,
            realitySpiderX = spx,
            grpcServiceName = grpcService,
            allowInsecure = insecure,
            rawUri = uri
        )
    }

    private fun parseVmess(uri: String, subscriptionId: Long): ProxyNode? {
        val base64Content = uri.removePrefix("vmess://").removePrefix("VMESS://")
        val decoded = tryDecodeBase64(base64Content)
        val json = JSONObject(decoded)

        val name = json.optString("ps", "VMess Node")
        val server = json.optString("add", "")
        val port = json.optInt("port", 443)
        val uuid = json.optString("id", "")
        val cipher = json.optString("scy", "auto")
        val alterId = json.optInt("aid", 0)
        val net = json.optString("net", "tcp")
        val host = json.optString("host", "")
        val path = json.optString("path", "")
        val tlsStr = json.optString("tls", "")
        val sni = json.optString("sni", "")

        val tls = tlsStr.equals("tls", ignoreCase = true)

        return ProxyNode(
            subscriptionId = subscriptionId,
            name = name,
            protocol = "vmess",
            server = server,
            port = port,
            uuidOrPassword = uuid,
            cipher = cipher,
            alterId = alterId,
            network = net,
            path = path,
            host = host,
            tls = tls,
            sni = if (sni.isNotEmpty()) sni else host,
            rawUri = uri
        )
    }

    private fun parseShadowsocks(uri: String, subscriptionId: Long): ProxyNode? {
        val main = uri.removePrefix("ss://").removePrefix("SS://")
        val nameSplit = main.split("#", limit = 2)
        val name = if (nameSplit.size > 1) urlDecode(nameSplit[1]) else "Shadowsocks"
        val body = nameSplit[0]

        return if (body.contains("@")) {
            val parts = body.split("@", limit = 2)
            val userInfoDecoded = tryDecodeBase64(parts[0])
            val creds = if (userInfoDecoded.contains(":")) userInfoDecoded else parts[0]
            val methodPass = creds.split(":", limit = 2)
            val (server, port) = parseAddressAndPort(parts[1], 8388)

            ProxyNode(
                subscriptionId = subscriptionId,
                name = name,
                protocol = "ss",
                server = server,
                port = port,
                cipher = methodPass.getOrElse(0) { "aes-256-gcm" },
                uuidOrPassword = methodPass.getOrElse(1) { "" },
                rawUri = uri
            )
        } else {
            val decoded = tryDecodeBase64(body)
            val atSplit = decoded.split("@", limit = 2)
            if (atSplit.size < 2) return null
            val methodPass = atSplit[0].split(":", limit = 2)
            val (server, port) = parseAddressAndPort(atSplit[1], 8388)

            ProxyNode(
                subscriptionId = subscriptionId,
                name = name,
                protocol = "ss",
                server = server,
                port = port,
                cipher = methodPass.getOrElse(0) { "aes-256-gcm" },
                uuidOrPassword = methodPass.getOrElse(1) { "" },
                rawUri = uri
            )
        }
    }

    private fun parseTrojan(uri: String, subscriptionId: Long): ProxyNode? {
        val main = uri.removePrefix("trojan://").removePrefix("TROJAN://")
        val nameSplit = main.split("#", limit = 2)
        val name = if (nameSplit.size > 1) urlDecode(nameSplit[1]) else "Trojan Node"
        val body = nameSplit[0]

        val querySplit = body.split("?", limit = 2)
        val userInfoAndAddress = querySplit[0]
        val queryParams = if (querySplit.size > 1) parseQueryParams(querySplit[1]) else emptyMap()

        val atSplit = userInfoAndAddress.split("@", limit = 2)
        if (atSplit.size < 2) return null
        val password = atSplit[0]

        val (server, port) = parseAddressAndPort(atSplit[1], 443)
        val sni = queryParams["sni"] ?: queryParams["peer"] ?: server
        val network = queryParams["type"] ?: queryParams["network"] ?: "tcp"
        val path = queryParams["path"] ?: ""
        val host = queryParams["host"] ?: ""
        val grpcService = queryParams["serviceName"] ?: queryParams["servicename"] ?: queryParams["grpc-service-name"] ?: ""
        val insecure = queryParams["insecure"] == "1" || queryParams["allow_insecure"] == "1" || queryParams["allowInsecure"] == "1"
        val alpn = queryParams["alpn"] ?: ""

        return ProxyNode(
            subscriptionId = subscriptionId,
            name = name,
            protocol = "trojan",
            server = server,
            port = port,
            uuidOrPassword = password,
            network = network,
            path = path,
            host = host,
            grpcServiceName = grpcService,
            tls = true,
            sni = sni,
            alpn = alpn,
            allowInsecure = insecure,
            rawUri = uri
        )
    }

    private fun parseHysteria2(uri: String, subscriptionId: Long): ProxyNode? {
        val main = uri.removePrefix("hy2://").removePrefix("hysteria2://")
        val nameSplit = main.split("#", limit = 2)
        val name = if (nameSplit.size > 1) urlDecode(nameSplit[1]) else "Hysteria2 Node"
        val body = nameSplit[0]

        val querySplit = body.split("?", limit = 2)
        val userInfoAndAddress = querySplit[0]
        val queryParams = if (querySplit.size > 1) parseQueryParams(querySplit[1]) else emptyMap()

        val atSplit = userInfoAndAddress.split("@", limit = 2)
        if (atSplit.size < 2) return null
        val password = atSplit[0]

        val (server, port) = parseAddressAndPort(atSplit[1], 443)
        val sni = queryParams["sni"] ?: server
        val insecure = queryParams["insecure"] == "1" || queryParams["allow_insecure"] == "1" || queryParams["insecure"] == "true"
        val obfs = queryParams["obfs"] ?: ""
        val obfsPass = queryParams["obfs-password"] ?: queryParams["obfs_password"] ?: ""

        return ProxyNode(
            subscriptionId = subscriptionId,
            name = name,
            protocol = "hysteria2",
            server = server,
            port = port,
            uuidOrPassword = password,
            tls = true,
            sni = sni,
            allowInsecure = insecure,
            obfs = obfs,
            obfsPassword = obfsPass,
            rawUri = uri
        )
    }

    private fun parseAnyTls(uri: String, subscriptionId: Long): ProxyNode? {
        val main = uri.removePrefix("anytls://").removePrefix("ANYTLS://")
        val nameSplit = main.split("#", limit = 2)
        val name = if (nameSplit.size > 1) urlDecode(nameSplit[1]) else "AnyTLS Node"
        val body = nameSplit[0]

        val querySplit = body.split("?", limit = 2)
        val userInfoAndAddress = querySplit[0]
        val queryParams = if (querySplit.size > 1) parseQueryParams(querySplit[1]) else emptyMap()

        val atSplit = userInfoAndAddress.split("@", limit = 2)
        if (atSplit.size < 2) return null
        val password = atSplit[0]

        val (server, port) = parseAddressAndPort(atSplit[1], 443)
        val sni = queryParams["sni"] ?: queryParams["peer"] ?: server
        val insecure = queryParams["insecure"] == "1" || queryParams["allow_insecure"] == "1" || queryParams["skip-cert-verify"] == "true"

        return ProxyNode(
            subscriptionId = subscriptionId,
            name = name,
            protocol = "anytls",
            server = server,
            port = port,
            uuidOrPassword = password,
            tls = true,
            sni = sni,
            allowInsecure = insecure,
            rawUri = uri
        )
    }

    private fun parseSocks(uri: String, subscriptionId: Long): ProxyNode? {
        val main = uri.removePrefix("socks5://").removePrefix("socks://")
        val nameSplit = main.split("#", limit = 2)
        val name = if (nameSplit.size > 1) urlDecode(nameSplit[1]) else "SOCKS5 Node"
        val body = nameSplit[0]

        val atSplit = body.split("@", limit = 2)
        return if (atSplit.size == 2) {
            val userPass = atSplit[0].split(":", limit = 2)
            val (server, port) = parseAddressAndPort(atSplit[1], 1080)
            ProxyNode(
                subscriptionId = subscriptionId,
                name = name,
                protocol = "socks",
                server = server,
                port = port,
                uuidOrPassword = userPass.getOrElse(1) { "" },
                host = userPass.getOrElse(0) { "" },
                rawUri = uri
            )
        } else {
            val (server, port) = parseAddressAndPort(body, 1080)
            ProxyNode(
                subscriptionId = subscriptionId,
                name = name,
                protocol = "socks",
                server = server,
                port = port,
                rawUri = uri
            )
        }
    }

    private fun parseHttp(uri: String, subscriptionId: Long): ProxyNode? {
        val lowerUri = uri.lowercase()
        if (lowerUri.contains(".txt") || lowerUri.contains(".list") || lowerUri.contains(".json") ||
            lowerUri.contains(".yaml") || lowerUri.contains(".yml") || lowerUri.contains(".conf") ||
            lowerUri.contains("/rules") || lowerUri.contains("/raw/") || lowerUri.contains("/master/") ||
            lowerUri.contains("rule-set") || lowerUri.contains("rule_set") || lowerUri.contains("payload:")
        ) {
            return null
        }

        val main = uri.removePrefix("http://").removePrefix("HTTP://").removePrefix("https://").removePrefix("HTTPS://")
        val nameSplit = main.split("#", limit = 2)
        val name = if (nameSplit.size > 1) urlDecode(nameSplit[1]) else ""
        val body = nameSplit[0].trim()

        if (body.contains("/") || body.contains("?")) return null

        val atSplit = body.split("@", limit = 2)
        val tls = uri.startsWith("https://", ignoreCase = true)

        val (hostPortStr, userPassStr) = if (atSplit.size == 2) {
            Pair(atSplit[1], atSplit[0])
        } else {
            Pair(atSplit[0], "")
        }

        val (server, port) = parseAddressAndPort(hostPortStr, if (tls) 443 else 80)
        if (server.isBlank() || server.contains(" ")) return null

        var user = ""
        var pass = ""
        if (userPassStr.isNotBlank()) {
            val up = userPassStr.split(":", limit = 2)
            user = up.getOrElse(0) { "" }
            pass = up.getOrElse(1) { "" }
        }

        val finalName = if (name.isNotBlank()) name else "HTTP-$server"

        return ProxyNode(
            subscriptionId = subscriptionId,
            name = finalName,
            protocol = "http",
            server = server,
            port = port,
            host = user,
            uuidOrPassword = pass,
            tls = tls,
            rawUri = uri
        )
    }

    private fun parseSingBoxJson(jsonStr: String, subscriptionId: Long): List<ProxyNode> {
        val nodes = mutableListOf<ProxyNode>()
        try {
            val root = JSONObject(jsonStr)
            val outbounds = root.optJSONArray("outbounds") ?: JSONArray()
            for (i in 0 until outbounds.length()) {
                val ob = outbounds.optJSONObject(i) ?: continue
                val type = ob.optString("type", "")
                val tag = ob.optString("tag", "Node $i")
                val server = ob.optString("server", "")
                val port = ob.optInt("server_port", 0)

                val validTypes = setOf("vless", "vmess", "shadowsocks", "ss", "trojan", "hysteria2", "tuic", "anytls", "socks", "http")
                if (type.lowercase() in validTypes && server.isNotEmpty() && port > 0) {
                    val uuid = ob.optString("uuid", ob.optString("password", ""))
                    val method = ob.optString("method", ob.optString("security", "aes-256-gcm"))
                    val alterId = ob.optInt("alter_id", 0)
                    val flow = ob.optString("flow", "")

                    val tlsObj = ob.optJSONObject("tls")
                    val tls = tlsObj?.optBoolean("enabled", false) ?: (type == "trojan" || type == "hysteria2" || type == "tuic")
                    val sni = tlsObj?.optString("server_name", "") ?: ""
                    val insecure = tlsObj?.optBoolean("insecure", false) ?: false

                    val utlsObj = tlsObj?.optJSONObject("utls")
                    val fp = utlsObj?.optString("fingerprint", "") ?: ""

                    val realityObj = tlsObj?.optJSONObject("reality")
                    val pbk = realityObj?.optString("public_key", "") ?: ""
                    val sid = realityObj?.optString("short_id", "") ?: ""

                    val transportObj = ob.optJSONObject("transport")
                    val netType = transportObj?.optString("type", "tcp") ?: "tcp"
                    val path = transportObj?.optString("path", "") ?: ""
                    val grpcService = transportObj?.optString("service_name", "") ?: ""
                    val headersObj = transportObj?.optJSONObject("headers")
                    val host = headersObj?.optString("host", headersObj.optString("Host", "")) ?: ""

                    val obfsObj = ob.optJSONObject("obfs")
                    val obfsType = obfsObj?.optString("type", "") ?: ""
                    val obfsPass = obfsObj?.optString("password", "") ?: ""

                    nodes.add(
                        ProxyNode(
                            subscriptionId = subscriptionId,
                            name = tag,
                            protocol = type,
                            server = server,
                            port = port,
                            uuidOrPassword = uuid,
                            cipher = method,
                            alterId = alterId,
                            network = netType,
                            path = path,
                            host = host,
                            tls = tls,
                            sni = sni,
                            allowInsecure = insecure,
                            flow = flow,
                            fingerprint = fp,
                            realityPublicKey = pbk,
                            realityShortId = sid,
                            grpcServiceName = grpcService,
                            obfs = obfsType,
                            obfsPassword = obfsPass
                        )
                    )
                }
            }
        } catch (e: Exception) {
            // ignore
        }
        return nodes
    }

    private fun tryDecodeBase64(input: String): String {
        val cleaned = input.trim().replace("\n", "").replace("\r", "").replace(" ", "")
        for (flags in intArrayOf(
            Base64.DEFAULT or Base64.NO_WRAP or Base64.URL_SAFE,
            Base64.DEFAULT or Base64.NO_WRAP,
            Base64.URL_SAFE or Base64.NO_WRAP,
            Base64.DEFAULT
        )) {
            try {
                val padded = when (cleaned.length % 4) {
                    2 -> "$cleaned=="
                    3 -> "$cleaned="
                    else -> cleaned
                }
                val decoded = Base64.decode(padded, flags)
                val result = String(decoded, StandardCharsets.UTF_8)
                if (result.isNotBlank() && (result.contains("://") || result.contains("name:") || result.contains("{") || result.contains("proxies:"))) {
                    return result
                }
            } catch (e: Exception) {
                // continue
            }
        }
        try {
            val decoded = Base64.decode(cleaned, Base64.DEFAULT or Base64.NO_WRAP or Base64.URL_SAFE)
            return String(decoded, StandardCharsets.UTF_8)
        } catch (e: Exception) {
            return input
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

    fun extractSubscriptionUrlAndName(input: String): Pair<String, String?> {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return Pair("", null)

        if (trimmed.startsWith("http://", ignoreCase = true) || trimmed.startsWith("https://", ignoreCase = true)) {
            val nameFromHash = if (trimmed.contains("#")) {
                try { URLDecoder.decode(trimmed.substringAfter("#"), "UTF-8") } catch (e: Exception) { null }
            } else null
            return Pair(trimmed, nameFromHash)
        }

        val lower = trimmed.lowercase()
        if (lower.startsWith("sing-box://") || lower.startsWith("singbox://") ||
            lower.startsWith("clash://") || lower.startsWith("v2rayn://") ||
            lower.startsWith("sn://") || lower.startsWith("sub://")) {

            if (trimmed.contains("url=", ignoreCase = true)) {
                val urlParam = trimmed.substringAfter("url=").substringBefore("&").substringBefore("#")
                if (urlParam.isNotBlank()) {
                    val decodedUrl = try { URLDecoder.decode(urlParam, "UTF-8") } catch (e: Exception) { urlParam }
                    if (decodedUrl.startsWith("http://", ignoreCase = true) || decodedUrl.startsWith("https://", ignoreCase = true)) {
                        var name: String? = null
                        if (trimmed.contains("#")) {
                            val fragment = trimmed.substringAfter("#")
                            if (fragment.isNotBlank()) {
                                name = try { URLDecoder.decode(fragment, "UTF-8") } catch (e: Exception) { fragment }
                            }
                        } else if (trimmed.contains("name=", ignoreCase = true)) {
                            val nameParam = trimmed.substringAfter("name=").substringBefore("&").substringBefore("#")
                            if (nameParam.isNotBlank()) {
                                name = try { URLDecoder.decode(nameParam, "UTF-8") } catch (e: Exception) { nameParam }
                            }
                        }
                        return Pair(decodedUrl, name)
                    }
                }
            }

            if (lower.startsWith("sub://")) {
                val afterSub = trimmed.removePrefix("sub://").removePrefix("SUB://")
                val decoded = tryDecodeBase64(afterSub)
                if (decoded.startsWith("http://", ignoreCase = true) || decoded.startsWith("https://", ignoreCase = true)) {
                    return Pair(decoded, null)
                }
                if (afterSub.startsWith("http://", ignoreCase = true) || afterSub.startsWith("https://", ignoreCase = true)) {
                    return Pair(afterSub, null)
                }
            }
        }

        val httpIdx = trimmed.indexOf("http://", ignoreCase = true)
        val httpsIdx = trimmed.indexOf("https://", ignoreCase = true)
        val idx = when {
            httpIdx >= 0 && httpsIdx >= 0 -> minOf(httpIdx, httpsIdx)
            httpIdx >= 0 -> httpIdx
            httpsIdx >= 0 -> httpsIdx
            else -> -1
        }
        if (idx >= 0) {
            val candidate = trimmed.substring(idx).split("\\s+".toRegex())[0]
            if (candidate.startsWith("http://", ignoreCase = true) || candidate.startsWith("https://", ignoreCase = true)) {
                return Pair(candidate, null)
            }
        }

        return Pair("", null)
    }
}
