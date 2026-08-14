package com.example.data.generator

import android.util.Base64
import com.example.data.model.ProxyNode
import org.json.JSONArray
import org.json.JSONObject
import java.nio.charset.StandardCharsets

fun isIpAddress(str: String): Boolean {
    val clean = str.removePrefix("[").removeSuffix("]").trim()
    if (clean.isEmpty()) return false
    if (clean.contains(":")) return true // IPv6
    val parts = clean.split(".")
    if (parts.size == 4 && parts.all { it.toIntOrNull() in 0..255 }) return true // IPv4
    return false
}

object SingBoxConfigGenerator {

    private val fallbackDummyNode = ProxyNode(
        id = -1,
        name = "示例-请导入节点源",
        protocol = "ss",
        server = "127.0.0.1",
        port = 8388,
        uuidOrPassword = "password",
        cipher = "aes-256-gcm",
        rawUri = "ss://YWVzLTI1Ni1nY206cGFzc3dvcmQAMTI3LjAuMC4xOjgzODg=#%E7%A4%BA%E4%BE%8B-%E8%AF%B7%E5%AF%BC%E5%85%A5%E8%8A%82%E7%82%B9%E6%BA%90"
    )

    fun generateJson(
        nodes: List<ProxyNode>,
        routingMode: String = "Rule", // Rule, Global, Direct
        inboundPort: Int = 2080,
        version: String = "1.14" // "1.14" (latest) or "1.13" (<=1.13 legacy)
    ): String {
        val isLegacy113 = version.trim() == "1.13" || version.trim().startsWith("1.13") || version.trim() == "legacy" || version.trim() == "1.13.0"
        val enabledNodes = if (nodes.isEmpty()) listOf(fallbackDummyNode) else nodes
        val root = JSONObject()

        // 0. log (optional, only if needed or keep minimal, but user format starts directly with http_clients)
        // 1. http_clients (only in 1.14+, MUST NOT be in 1.13)
        if (!isLegacy113) {
            val httpClients = JSONArray().apply {
                put(JSONObject().apply {
                    put("tag", "default-http-client")
                    put("detour", "🚀 节点选择")
                })
            }
            root.put("http_clients", httpClients)
        }

        // 2. dns configuration
        val dns = JSONObject().apply {
            val servers = JSONArray().apply {
                put(JSONObject().apply {
                    put("type", "tcp")
                    put("tag", "dns_proxy")
                    put("server", "1.1.1.1")
                    put("detour", "🚀 节点选择")
                    put("domain_resolver", "dns_resolver")
                })
                put(JSONObject().apply {
                    put("type", "https")
                    put("tag", "dns_direct")
                    put("server", "dns.alidns.com")
                    put("domain_resolver", "dns_resolver")
                })
                put(JSONObject().apply {
                    put("type", "udp")
                    put("tag", "dns_resolver")
                    put("server", "223.5.5.5")
                })
                put(JSONObject().apply {
                    put("type", "fakeip")
                    put("tag", "dns_fakeip")
                    put("inet4_range", "198.18.0.0/15")
                    put("inet6_range", "fc00::/18")
                })
            }
            put("servers", servers)

            val dnsRules = JSONArray().apply {
                put(JSONObject().apply {
                    put("rule_set", "geolocation-!cn")
                    put("query_type", JSONArray().apply {
                        put("A")
                        put("AAAA")
                    })
                    put("server", "dns_fakeip")
                })
                put(JSONObject().apply {
                    put("rule_set", "geolocation-!cn")
                    put("query_type", "CNAME")
                    put("server", "dns_proxy")
                })
                put(JSONObject().apply {
                    put("query_type", JSONArray().apply {
                        put("A")
                        put("AAAA")
                        put("CNAME")
                    })
                    put("invert", true)
                    put("action", "predefined")
                    put("rcode", "REFUSED")
                })
            }
            put("rules", dnsRules)
            put("final", "dns_direct")
        }
        root.put("dns", dns)

        // 3. ntp
        val ntp = JSONObject().apply {
            put("enabled", true)
            put("server", "time.apple.com")
            put("server_port", 123)
            put("interval", "30m")
        }
        root.put("ntp", ntp)

        // 4. inbounds configuration
        val inbounds = JSONArray().apply {
            put(JSONObject().apply {
                put("type", "mixed")
                put("tag", "mixed-in")
                put("listen", "0.0.0.0")
                put("listen_port", inboundPort)
            })
            put(JSONObject().apply {
                put("type", "tun")
                put("tag", "tun-in")
                put("address", "172.19.0.1/30")
                put("auto_route", true)
                put("strict_route", true)
                put("stack", "mixed")
            })
        }
        root.put("inbounds", inbounds)

        // 5. outbounds configuration
        val outbounds = JSONArray()
        val nodeTags = enabledNodes.map { it.name }.filter { it.isNotEmpty() }

        // Selector outbound
        val selectorOutbound = JSONObject().apply {
            put("type", "selector")
            put("tag", "🚀 节点选择")
            val outList = JSONArray().apply {
                put("⚡ 自动选择")
                nodeTags.forEach { put(it) }
                put("DIRECT")
            }
            put("outbounds", outList)
        }
        outbounds.put(selectorOutbound)

        // Auto URLTest outbound
        val autoTestOutbound = JSONObject().apply {
            put("type", "urltest")
            put("tag", "⚡ 自动选择")
            val outList = JSONArray().apply {
                nodeTags.forEach { put(it) }
            }
            put("outbounds", if (outList.length() > 0) outList else JSONArray().apply { put("DIRECT") })
            put("url", "http://www.gstatic.com/generate_204")
            put("interval", "3m")
            put("tolerance", 50)
        }
        outbounds.put(autoTestOutbound)

        // Direct outbound
        outbounds.put(JSONObject().apply {
            put("type", "direct")
            put("tag", "DIRECT")
        })

        // In Sing-Box <= 1.12 legacy DNS outbound was used, but in 1.13 and 1.14 hijack-dns action is supported.
        // Additional policy group selectors matching user template
        val extraSelectors = listOf("🏠 私有网络", "🔒 国内服务", "🌐 非中国", "🐟 漏网之鱼")
        extraSelectors.forEach { selName ->
            outbounds.put(JSONObject().apply {
                put("type", "selector")
                put("tag", selName)
                put("outbounds", JSONArray().apply {
                    if (selName == "🌐 非中国" || selName == "🐟 漏网之鱼") {
                        put("🚀 节点选择")
                        nodeTags.forEach { put(it) }
                        put("DIRECT")
                    } else {
                        put("DIRECT")
                        put("🚀 节点选择")
                        nodeTags.forEach { put(it) }
                    }
                })
            })
        }

        // Node outbounds
        enabledNodes.forEach { node ->
            val ob = buildNodeOutbound(node, isLegacy113 = isLegacy113)
            if (ob != null) {
                outbounds.put(ob)
            }
        }

        root.put("outbounds", outbounds)

        // 6. Route configuration
        val route = JSONObject().apply {
            put("default_domain_resolver", "dns_resolver")
            if (!isLegacy113) {
                put("default_http_client", "default-http-client")
            }

            val ruleSet = JSONArray().apply {
                val ruleSetItems = listOf(
                    Triple("geolocation-cn", "https://gh-proxy.com/https://github.com/MetaCubeX/meta-rules-dat/raw/refs/heads/sing/geo/geosite/geolocation-cn.srs", "binary"),
                    Triple("cn", "https://gh-proxy.com/https://github.com/MetaCubeX/meta-rules-dat/raw/refs/heads/sing/geo/geosite/cn.srs", "binary"),
                    Triple("geolocation-!cn", "https://gh-proxy.com/https://github.com/MetaCubeX/meta-rules-dat/raw/refs/heads/sing/geo/geosite/geolocation-!cn.srs", "binary"),
                    Triple("private-ip", "https://gh-proxy.com/https://github.com/MetaCubeX/meta-rules-dat/raw/refs/heads/sing/geo/geoip/private.srs", "binary"),
                    Triple("cn-ip", "https://gh-proxy.com/https://github.com/MetaCubeX/meta-rules-dat/raw/refs/heads/sing/geo/geoip/cn.srs", "binary")
                )

                ruleSetItems.forEach { (tag, url, format) ->
                    put(JSONObject().apply {
                        put("tag", tag)
                        put("type", "remote")
                        put("format", format)
                        put("url", url)
                        if (isLegacy113) {
                            put("download_detour", "🚀 节点选择")
                        } else {
                            put("http_client", "default-http-client")
                        }
                    })
                }
            }
            put("rule_set", ruleSet)

            val rules = JSONArray()

            rules.put(JSONObject().apply {
                put("clash_mode", "direct")
                put("outbound", "DIRECT")
            })
            rules.put(JSONObject().apply {
                put("clash_mode", "global")
                put("outbound", "🚀 节点选择")
            })
            rules.put(JSONObject().apply {
                put("action", "sniff")
            })
            rules.put(JSONObject().apply {
                put("protocol", "dns")
                put("action", "hijack-dns")
            })

            when (routingMode) {
                "Global" -> {
                    rules.put(JSONObject().apply {
                        put("outbound", "🚀 节点选择")
                    })
                }
                "Direct" -> {
                    rules.put(JSONObject().apply {
                        put("outbound", "DIRECT")
                    })
                }
                else -> {
                    rules.put(JSONObject().apply {
                        put("rule_set", JSONArray().apply { put("geolocation-cn"); put("cn") })
                        put("outbound", "🔒 国内服务")
                    })
                    rules.put(JSONObject().apply {
                        put("rule_set", JSONArray().apply { put("geolocation-!cn") })
                        put("outbound", "🌐 非中国")
                    })
                    rules.put(JSONObject().apply {
                        put("rule_set", JSONArray().apply { put("private-ip") })
                        put("outbound", "🏠 私有网络")
                    })
                    rules.put(JSONObject().apply {
                        put("rule_set", JSONArray().apply { put("cn-ip") })
                        put("outbound", "🔒 国内服务")
                    })
                }
            }
            put("rules", rules)
            put("auto_detect_interface", true)
            put("final", "🐟 漏网之鱼")
        }
        root.put("route", route)

        // 7. Experimental
        val experimental = JSONObject().apply {
            put("cache_file", JSONObject().apply {
                put("enabled", true)
                put("store_fakeip", true)
            })
            if (isLegacy113) {
                put("clash_api", JSONObject().apply {
                    put("external_controller", "127.0.0.1:9090")
                    put("external_ui", "ui")
                    put("external_ui_download_url", "https://gh-proxy.com/https://github.com/MetaCubeX/metacubexd/archive/refs/heads/gh-pages.zip")
                    put("external_ui_download_detour", "🚀 节点选择")
                    put("default_mode", "rule")
                })
            }
        }
        root.put("experimental", experimental)

        return root.toString(2)
    }

    private fun buildNodeOutbound(node: ProxyNode, isLegacy113: Boolean = false): JSONObject? {
        val ob = JSONObject()
        ob.put("tag", node.name)
        val cleanServer = node.server.removePrefix("[").removeSuffix("]").trim()
        ob.put("server", cleanServer)
        ob.put("server_port", node.port)

        val rawSni = node.sni.removePrefix("[").removeSuffix("]").trim()
        val effectiveSni = if (rawSni.isNotBlank()) rawSni else if (!isIpAddress(cleanServer)) cleanServer else ""

        when (node.protocol.lowercase()) {
            "vless" -> {
                ob.put("type", "vless")
                ob.put("uuid", node.uuidOrPassword)
                if (node.flow.isNotBlank() && (node.network.isBlank() || node.network.lowercase() == "tcp") && (node.tls || node.realityPublicKey.isNotBlank())) {
                    ob.put("flow", node.flow)
                }
                if (node.network.isBlank() || node.network.lowercase() == "tcp") {
                    ob.put("network", "tcp")
                    ob.put("tcp_fast_open", false)
                } else if (!isLegacy113) {
                    ob.put("packet_encoding", "xudp")
                }

                if (node.tls || node.realityPublicKey.isNotEmpty()) {
                    val tlsObj = JSONObject().apply {
                        put("enabled", true)
                        if (effectiveSni.isNotEmpty()) put("server_name", effectiveSni)
                        put("insecure", node.allowInsecure)

                        if (node.realityPublicKey.isNotEmpty()) {
                            put("reality", JSONObject().apply {
                                put("enabled", true)
                                put("public_key", node.realityPublicKey)
                                if (node.realityShortId.isNotEmpty()) put("short_id", node.realityShortId)
                            })
                            put("utls", JSONObject().apply {
                                put("enabled", true)
                                put("fingerprint", if (node.fingerprint.isNotEmpty()) node.fingerprint else "chrome")
                            })
                        } else {
                            if (node.fingerprint.isNotEmpty()) {
                                put("utls", JSONObject().apply {
                                    put("enabled", true)
                                    put("fingerprint", node.fingerprint)
                                })
                            }
                            if (node.alpn.isNotEmpty()) {
                                val alpnList = node.alpn.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                                put("alpn", JSONArray(alpnList))
                            }
                        }
                    }
                    ob.put("tls", tlsObj)
                }

                buildTransport(node)?.let { ob.put("transport", it) }
            }
            "vmess" -> {
                ob.put("type", "vmess")
                ob.put("uuid", node.uuidOrPassword)
                ob.put("alter_id", node.alterId)
                ob.put("security", if (node.cipher.isNotEmpty()) node.cipher else "auto")
                if (node.network.isBlank() || node.network.lowercase() == "tcp") {
                    ob.put("network", "tcp")
                    ob.put("tcp_fast_open", false)
                }

                if (node.tls) {
                    val tlsObj = JSONObject().apply {
                        put("enabled", true)
                        if (effectiveSni.isNotEmpty()) put("server_name", effectiveSni)
                        put("insecure", node.allowInsecure)
                        if (node.fingerprint.isNotEmpty()) {
                            put("utls", JSONObject().apply {
                                put("enabled", true)
                                put("fingerprint", node.fingerprint)
                            })
                        }
                        if (node.alpn.isNotEmpty()) {
                            val alpnList = node.alpn.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                            put("alpn", JSONArray(alpnList))
                        }
                    }
                    ob.put("tls", tlsObj)
                }

                buildTransport(node)?.let { ob.put("transport", it) }
            }
            "ss", "shadowsocks" -> {
                ob.put("type", "shadowsocks")
                ob.put("method", if (node.cipher.isNotEmpty()) node.cipher else "aes-256-gcm")
                ob.put("password", node.uuidOrPassword)
            }
            "trojan" -> {
                ob.put("type", "trojan")
                ob.put("password", node.uuidOrPassword)
                val tlsObj = JSONObject().apply {
                    put("enabled", true)
                    if (effectiveSni.isNotEmpty()) put("server_name", effectiveSni)
                    put("insecure", node.allowInsecure)
                    if (node.alpn.isNotEmpty()) {
                        val alpnList = node.alpn.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                        put("alpn", JSONArray(alpnList))
                    }
                    if (node.fingerprint.isNotEmpty()) {
                        put("utls", JSONObject().apply {
                            put("enabled", true)
                            put("fingerprint", node.fingerprint)
                        })
                    }
                }
                ob.put("tls", tlsObj)
                buildTransport(node)?.let { ob.put("transport", it) }
            }
            "hysteria2", "hy2" -> {
                ob.put("type", "hysteria2")
                ob.put("password", node.uuidOrPassword)
                val tlsObj = JSONObject().apply {
                    put("enabled", true)
                    if (effectiveSni.isNotEmpty()) put("server_name", effectiveSni)
                    put("insecure", node.allowInsecure || isIpAddress(cleanServer))
                    if (node.alpn.isNotEmpty()) {
                        val alpnList = node.alpn.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                        put("alpn", JSONArray(alpnList))
                    }
                }
                ob.put("tls", tlsObj)

                if (node.obfs.isNotEmpty()) {
                    ob.put("obfs", JSONObject().apply {
                        put("type", node.obfs)
                        if (node.obfsPassword.isNotEmpty()) put("password", node.obfsPassword)
                    })
                }
            }
            "tuic" -> {
                ob.put("type", "tuic")
                ob.put("uuid", node.uuidOrPassword)
                ob.put("password", node.uuidOrPassword)
                ob.put("congestion_control", "bbr")
                val tlsObj = JSONObject().apply {
                    put("enabled", true)
                    if (effectiveSni.isNotEmpty()) put("server_name", effectiveSni)
                    put("insecure", node.allowInsecure || isIpAddress(cleanServer))
                    if (node.alpn.isNotEmpty()) {
                        val alpnList = node.alpn.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                        put("alpn", JSONArray(alpnList))
                    } else {
                        put("alpn", JSONArray().apply { put("h3") })
                    }
                }
                ob.put("tls", tlsObj)
            }
            "anytls" -> {
                ob.put("type", "anytls")
                ob.put("password", node.uuidOrPassword)
                val tlsObj = JSONObject().apply {
                    put("enabled", true)
                    if (effectiveSni.isNotEmpty()) put("server_name", effectiveSni) else put("server_name", cleanServer)
                    put("insecure", node.allowInsecure || isIpAddress(cleanServer))
                    if (node.fingerprint.isNotEmpty()) {
                        put("utls", JSONObject().apply {
                            put("enabled", true)
                            put("fingerprint", node.fingerprint)
                        })
                    }
                }
                ob.put("tls", tlsObj)
                buildTransport(node)?.let { ob.put("transport", it) }
            }
            "socks", "socks5" -> {
                ob.put("type", "socks")
                if (node.host.isNotEmpty()) ob.put("username", node.host)
                if (node.uuidOrPassword.isNotEmpty()) ob.put("password", node.uuidOrPassword)
            }
            "http" -> {
                ob.put("type", "http")
                if (node.host.isNotEmpty()) ob.put("username", node.host)
                if (node.uuidOrPassword.isNotEmpty()) ob.put("password", node.uuidOrPassword)
                if (node.tls) {
                    val tlsObj = JSONObject().apply {
                        put("enabled", true)
                        if (effectiveSni.isNotEmpty()) put("server_name", effectiveSni)
                        put("insecure", node.allowInsecure)
                    }
                    ob.put("tls", tlsObj)
                }
            }
            else -> return null
        }

        return ob
    }

    private fun buildTransport(node: ProxyNode): JSONObject? {
        val net = node.network.lowercase()
        if (net.isEmpty() || net == "tcp") return null

        val transport = JSONObject()
        when (net) {
            "ws", "websocket" -> {
                transport.put("type", "ws")
                if (node.path.isNotEmpty()) transport.put("path", node.path)
                if (node.host.isNotEmpty()) {
                    transport.put("headers", JSONObject().apply {
                        put("Host", node.host)
                    })
                }
            }
            "grpc" -> {
                transport.put("type", "grpc")
                val serviceName = if (node.grpcServiceName.isNotEmpty()) node.grpcServiceName else node.path
                if (serviceName.isNotEmpty()) transport.put("service_name", serviceName)
            }
            "httpupgrade" -> {
                transport.put("type", "httpupgrade")
                if (node.host.isNotEmpty()) transport.put("host", node.host)
                if (node.path.isNotEmpty()) transport.put("path", node.path)
            }
            "http" -> {
                transport.put("type", "http")
                if (node.host.isNotEmpty()) transport.put("host", JSONArray().apply { put(node.host) })
                if (node.path.isNotEmpty()) transport.put("path", node.path)
            }
            "quic" -> {
                transport.put("type", "quic")
            }
            else -> return null
        }
        return transport
    }
}

object ClashConfigGenerator {
    private val fallbackDummyNode = ProxyNode(
        id = -1,
        name = "示例-请导入节点源",
        protocol = "ss",
        server = "127.0.0.1",
        port = 8388,
        uuidOrPassword = "password",
        cipher = "aes-256-gcm",
        rawUri = "ss://YWVzLTI1Ni1nY206cGFzc3dvcmQAMTI3LjAuMC4xOjgzODg=#%E7%A4%BA%E4%BE%8B-%E8%AF%B7%E5%AF%BC%E5%85%A5%E8%8A%82%E7%82%B9%E6%BA%90"
    )

    private fun escapeYaml(value: String): String {
        val sb = StringBuilder()
        for (ch in value) {
            when (ch) {
                '\\' -> sb.append("\\\\")
                '\"' -> sb.append("\\\"")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                '\b' -> sb.append("\\b")
                '\u000C' -> sb.append("\\f")
                else -> {
                    if (ch.code < 0x20) {
                        sb.append(String.format("\\x%02x", ch.code))
                    } else {
                        sb.append(ch)
                    }
                }
            }
        }
        return sb.toString()
    }

    fun generateYaml(nodes: List<ProxyNode>, isMihomo: Boolean = true): String {
        val enabled = if (nodes.isEmpty()) listOf(fallbackDummyNode) else nodes
        val sb = StringBuilder()
        sb.appendLine("port: 7890")
        sb.appendLine("socks-port: 7891")
        sb.appendLine("mixed-port: 2080")
        sb.appendLine("allow-lan: true")
        sb.appendLine("mode: rule")
        sb.appendLine("log-level: info")
        sb.appendLine("ipv6: true")
        sb.appendLine("external-controller: 127.0.0.1:9090")
        sb.appendLine("dns:")
        sb.appendLine("  enable: true")
        sb.appendLine("  ipv6: true")
        sb.appendLine("  enhanced-mode: fake-ip")
        sb.appendLine("  fake-ip-range: 198.18.0.1/16")
        sb.appendLine("  listen: 0.0.0.0:1053")
        sb.appendLine("  nameserver:")
        sb.appendLine("    - 223.5.5.5")
        sb.appendLine("    - 119.29.29.29")
        sb.appendLine("    - \"2400:3200::1\"")
        sb.appendLine("    - \"2400:3200:baba::1\"")
        sb.appendLine("  fallback:")
        sb.appendLine("    - https://dns.cloudflare.com/dns-query")
        sb.appendLine("    - https://dns.google/dns-query")
        sb.appendLine("    - \"2606:4700:4700::1111\"")
        sb.appendLine("    - \"2001:4860:4860::8888\"")
        sb.appendLine("proxies:")

        enabled.forEach { node ->
            val nodeType = mapClashType(node.protocol)
            val cleanServer = node.server.removePrefix("[").removeSuffix("]").trim()
            val rawSni = node.sni.removePrefix("[").removeSuffix("]").trim()
            val effectiveSni = if (rawSni.isNotBlank() && !isIpAddress(rawSni)) rawSni else if (!isIpAddress(cleanServer)) cleanServer else ""
            val isServerIp = isIpAddress(cleanServer)

            sb.appendLine("  - name: \"${escapeYaml(node.name)}\"")
            sb.appendLine("    type: $nodeType")
            sb.appendLine("    server: \"${escapeYaml(cleanServer)}\"")
            sb.appendLine("    port: ${node.port}")

            when (node.protocol.lowercase()) {
                "anytls" -> {
                    sb.appendLine("    password: \"${escapeYaml(node.uuidOrPassword)}\"")
                    sb.appendLine("    udp: true")
                    if (effectiveSni.isNotEmpty()) sb.appendLine("    sni: \"${escapeYaml(effectiveSni)}\"")
                    sb.appendLine("    skip-cert-verify: true")
                }
                "vless" -> {
                    sb.appendLine("    uuid: \"${escapeYaml(node.uuidOrPassword)}\"")
                    sb.appendLine("    udp: true")
                    if (node.flow.isNotEmpty() && (node.network.isBlank() || node.network.lowercase() == "tcp") && (node.tls || node.realityPublicKey.isNotBlank())) {
                        sb.appendLine("    flow: \"${escapeYaml(node.flow)}\"")
                    }
                    if (node.tls || node.realityPublicKey.isNotEmpty()) {
                        sb.appendLine("    tls: true")
                        if (effectiveSni.isNotEmpty()) sb.appendLine("    servername: \"${escapeYaml(effectiveSni)}\"")
                        if (node.allowInsecure || (isServerIp && node.realityPublicKey.isEmpty())) sb.appendLine("    skip-cert-verify: true")
                        val fp = if (node.fingerprint.isNotEmpty()) node.fingerprint else if (node.realityPublicKey.isNotEmpty()) "chrome" else ""
                        if (fp.isNotEmpty()) sb.appendLine("    client-fingerprint: \"${escapeYaml(fp)}\"")
                        if (node.realityPublicKey.isNotEmpty()) {
                            sb.appendLine("    reality-opts:")
                            sb.appendLine("      public-key: \"${escapeYaml(node.realityPublicKey)}\"")
                            if (node.realityShortId.isNotEmpty()) sb.appendLine("      short-id: \"${escapeYaml(node.realityShortId)}\"")
                        }
                    }
                    appendClashTransport(sb, node)
                }
                "vmess" -> {
                    sb.appendLine("    uuid: \"${escapeYaml(node.uuidOrPassword)}\"")
                    sb.appendLine("    alterId: ${node.alterId}")
                    sb.appendLine("    cipher: \"${escapeYaml(if (node.cipher.isNotEmpty()) node.cipher else "auto")}\"")
                    sb.appendLine("    udp: true")
                    if (node.tls) {
                        sb.appendLine("    tls: true")
                        if (effectiveSni.isNotEmpty()) sb.appendLine("    servername: \"${escapeYaml(effectiveSni)}\"")
                        if (node.allowInsecure || isServerIp) sb.appendLine("    skip-cert-verify: true")
                        if (node.fingerprint.isNotEmpty()) sb.appendLine("    client-fingerprint: \"${escapeYaml(node.fingerprint)}\"")
                    }
                    appendClashTransport(sb, node)
                }
                "ss", "shadowsocks" -> {
                    sb.appendLine("    cipher: \"${escapeYaml(if (node.cipher.isNotEmpty()) node.cipher else "aes-256-gcm")}\"")
                    sb.appendLine("    password: \"${escapeYaml(node.uuidOrPassword)}\"")
                    sb.appendLine("    udp: true")
                }
                "trojan" -> {
                    sb.appendLine("    password: \"${escapeYaml(node.uuidOrPassword)}\"")
                    sb.appendLine("    udp: true")
                    if (effectiveSni.isNotEmpty()) sb.appendLine("    sni: \"${escapeYaml(effectiveSni)}\"")
                    if (node.allowInsecure || isServerIp) sb.appendLine("    skip-cert-verify: true")
                    if (node.alpn.isNotEmpty()) sb.appendLine("    alpn: [\"${node.alpn.replace(",", "\", \"")}\"]")
                    if (node.fingerprint.isNotEmpty()) sb.appendLine("    client-fingerprint: \"${escapeYaml(node.fingerprint)}\"")
                    appendClashTransport(sb, node)
                }
                "hysteria2", "hy2" -> {
                    sb.appendLine("    password: \"${escapeYaml(node.uuidOrPassword)}\"")
                    sb.appendLine("    udp: true")
                    if (effectiveSni.isNotEmpty()) sb.appendLine("    sni: \"${escapeYaml(effectiveSni)}\"")
                    if (node.allowInsecure || isServerIp) sb.appendLine("    skip-cert-verify: true")
                    if (node.obfs.isNotEmpty()) {
                        sb.appendLine("    obfs: \"${escapeYaml(node.obfs)}\"")
                        if (node.obfsPassword.isNotEmpty()) sb.appendLine("    obfs-password: \"${escapeYaml(node.obfsPassword)}\"")
                    }
                }
                "tuic" -> {
                    sb.appendLine("    uuid: \"${escapeYaml(node.uuidOrPassword)}\"")
                    sb.appendLine("    password: \"${escapeYaml(node.uuidOrPassword)}\"")
                    sb.appendLine("    udp: true")
                    if (effectiveSni.isNotEmpty()) sb.appendLine("    sni: \"${escapeYaml(effectiveSni)}\"")
                    sb.appendLine("    congestion-control: bbr")
                    sb.appendLine("    alpn: [h3]")
                    if (node.allowInsecure || isServerIp) sb.appendLine("    skip-cert-verify: true")
                }
                "socks", "socks5" -> {
                    if (node.host.isNotEmpty()) sb.appendLine("    username: \"${escapeYaml(node.host)}\"")
                    if (node.uuidOrPassword.isNotEmpty()) sb.appendLine("    password: \"${escapeYaml(node.uuidOrPassword)}\"")
                    sb.appendLine("    udp: true")
                }
                else -> { // http
                    if (node.host.isNotEmpty()) sb.appendLine("    username: \"${escapeYaml(node.host)}\"")
                    if (node.uuidOrPassword.isNotEmpty()) sb.appendLine("    password: \"${escapeYaml(node.uuidOrPassword)}\"")
                    if (node.tls) {
                        sb.appendLine("    tls: true")
                        if (effectiveSni.isNotEmpty()) sb.appendLine("    servername: \"${escapeYaml(effectiveSni)}\"")
                        if (node.allowInsecure || isServerIp) sb.appendLine("    skip-cert-verify: true")
                    }
                }
            }
        }

        sb.appendLine("proxy-groups:")
        // 1. 🚀 节点选择
        sb.appendLine("  - name: 🚀 节点选择")
        sb.appendLine("    type: select")
        sb.appendLine("    proxies:")
        sb.appendLine("      - ⚡ 自动选择")
        sb.appendLine("      - DIRECT")
        enabled.forEach { sb.appendLine("      - \"${escapeYaml(it.name)}\"") }

        // 2. ⚡ 自动选择
        sb.appendLine("  - name: ⚡ 自动选择")
        sb.appendLine("    type: url-test")
        sb.appendLine("    url: http://www.gstatic.com/generate_204")
        sb.appendLine("    interval: 300")
        sb.appendLine("    proxies:")
        if (enabled.isNotEmpty()) {
            enabled.forEach { sb.appendLine("      - \"${escapeYaml(it.name)}\"") }
        } else {
            sb.appendLine("      - DIRECT")
        }

        // 3. 🔒 国内服务
        sb.appendLine("  - name: 🔒 国内服务")
        sb.appendLine("    type: select")
        sb.appendLine("    proxies:")
        sb.appendLine("      - DIRECT")
        sb.appendLine("      - 🚀 节点选择")

        // 4. 🌐 非中国
        sb.appendLine("  - name: 🌐 非中国")
        sb.appendLine("    type: select")
        sb.appendLine("    proxies:")
        sb.appendLine("      - 🚀 节点选择")
        sb.appendLine("      - DIRECT")

        // 5. 🏠 私有网络
        sb.appendLine("  - name: 🏠 私有网络")
        sb.appendLine("    type: select")
        sb.appendLine("    proxies:")
        sb.appendLine("      - DIRECT")
        sb.appendLine("      - 🚀 节点选择")

        // 6. 🐟 漏网之鱼
        sb.appendLine("  - name: 🐟 漏网之鱼")
        sb.appendLine("    type: select")
        sb.appendLine("    proxies:")
        sb.appendLine("      - 🚀 节点选择")
        sb.appendLine("      - DIRECT")

        sb.appendLine("rules:")
        sb.appendLine("  - GEOIP,private,🏠 私有网络,no-resolve")
        sb.appendLine("  - GEOSITE,cn,🔒 国内服务")
        sb.appendLine("  - GEOIP,CN,🔒 国内服务")
        sb.appendLine("  - GEOSITE,geolocation-!cn,🌐 非中国")
        sb.appendLine("  - MATCH,🐟 漏网之鱼")

        return sb.toString()
    }

    private fun appendClashTransport(sb: StringBuilder, node: ProxyNode) {
        val net = node.network.lowercase()
        if (net.isEmpty() || net == "tcp") return

        when (net) {
            "ws", "websocket" -> {
                sb.appendLine("    network: ws")
                sb.appendLine("    ws-opts:")
                if (node.path.isNotEmpty()) sb.appendLine("      path: \"${escapeYaml(node.path)}\"")
                if (node.host.isNotEmpty()) {
                    sb.appendLine("      headers:")
                    sb.appendLine("        Host: \"${escapeYaml(node.host)}\"")
                }
            }
            "grpc" -> {
                sb.appendLine("    network: grpc")
                val serviceName = if (node.grpcServiceName.isNotEmpty()) node.grpcServiceName else node.path
                if (serviceName.isNotEmpty()) {
                    sb.appendLine("    grpc-opts:")
                    sb.appendLine("      grpc-service-name: \"${escapeYaml(serviceName)}\"")
                }
            }
            "http", "httpupgrade" -> {
                sb.appendLine("    network: http")
                if (node.path.isNotEmpty() || node.host.isNotEmpty()) {
                    sb.appendLine("    http-opts:")
                    if (node.path.isNotEmpty()) sb.appendLine("      path: [\"${escapeYaml(node.path)}\"]")
                    if (node.host.isNotEmpty()) {
                        sb.appendLine("      headers:")
                        sb.appendLine("        Host: [\"${escapeYaml(node.host)}\"]")
                    }
                }
            }
        }
    }

    private fun mapClashType(protocol: String): String {
        return when (protocol.lowercase()) {
            "vless" -> "vless"
            "vmess" -> "vmess"
            "ss", "shadowsocks" -> "ss"
            "trojan" -> "trojan"
            "hysteria2", "hy2" -> "hysteria2"
            "tuic" -> "tuic"
            "anytls" -> "anytls"
            "socks", "socks5" -> "socks5"
            else -> "http"
        }
    }
}

object Base64Generator {
    private val fallbackDummyNode = ProxyNode(
        id = -1,
        name = "示例-请导入节点源",
        protocol = "ss",
        server = "127.0.0.1",
        port = 8388,
        uuidOrPassword = "password",
        cipher = "aes-256-gcm",
        rawUri = "ss://YWVzLTI1Ni1nY206cGFzc3dvcmQAMTI3LjAuMC4xOjgzODg=#%E7%A4%BA%E4%BE%8B-%E8%AF%B7%E5%AF%BC%E5%85%A5%E8%8A%82%E7%82%B9%E6%BA%90"
    )

    fun generateBase64(nodes: List<ProxyNode>): String {
        val targetList = if (nodes.isEmpty()) listOf(fallbackDummyNode) else nodes
        val uris = targetList.joinToString("\n") { if (it.rawUri.isNotBlank()) it.rawUri else it.toUri() }
        return Base64.encodeToString(uris.toByteArray(StandardCharsets.UTF_8), Base64.NO_WRAP or Base64.DEFAULT)
    }
}

