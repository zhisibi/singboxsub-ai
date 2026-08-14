package com.example.data.generator

import android.util.Base64
import com.example.data.model.ProxyNode
import org.json.JSONArray
import org.json.JSONObject
import java.nio.charset.StandardCharsets

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
        val enabledNodes = if (nodes.any { it.enabled }) nodes.filter { it.enabled } else nodes.ifEmpty { listOf(fallbackDummyNode) }
        val root = JSONObject()

        // 0. log
        val log = JSONObject().apply {
            put("level", "info")
            put("timestamp", true)
        }
        root.put("log", log)

        // 1. http_clients (only in 1.14+)
        if (!isLegacy113) {
            val httpClients = JSONArray().apply {
                put(JSONObject().apply {
                    put("tag", "default-http-client")
                    put("detour", "🚀 节点选择")
                })
            }
            root.put("http_clients", httpClients)
        }

        // 2. dns configuration matching version template
        val dns = JSONObject().apply {
            val servers = JSONArray().apply {
                if (isLegacy113) {
                    put(JSONObject().apply {
                        put("tag", "dns_proxy")
                        put("address", "1.1.1.1")
                        put("detour", "🚀 节点选择")
                        put("address_resolver", "dns_resolver")
                    })
                    put(JSONObject().apply {
                        put("tag", "dns_direct")
                        put("address", "https://dns.alidns.com/dns-query")
                        put("detour", "DIRECT")
                        put("address_resolver", "dns_resolver")
                    })
                    put(JSONObject().apply {
                        put("tag", "dns_resolver")
                        put("address", "223.5.5.5")
                        put("detour", "DIRECT")
                    })
                    put(JSONObject().apply {
                        put("tag", "dns_fakeip")
                        put("address", "fakeip")
                    })
                } else {
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
            }
            put("servers", servers)

            val dnsRules = JSONArray().apply {
                put(JSONObject().apply {
                    put("rule_set", "geolocation-!cn")
                    put("query_type", JSONArray().apply { put("A"); put("AAAA") })
                    put("server", "dns_fakeip")
                })
                put(JSONObject().apply {
                    put("rule_set", "geolocation-!cn")
                    put("query_type", "CNAME")
                    put("server", "dns_proxy")
                })
                put(JSONObject().apply {
                    put("query_type", JSONArray().apply { put("A"); put("AAAA"); put("CNAME") })
                    put("invert", true)
                    put("action", "predefined")
                    put("rcode", "REFUSED")
                })
            }
            put("rules", dnsRules)
            put("final", "dns_direct")

            if (isLegacy113) {
                put("fakeip", JSONObject().apply {
                    put("enabled", true)
                    put("inet4_range", "198.18.0.0/15")
                    put("inet6_range", "fc00::/18")
                })
            }
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
            val ob = buildNodeOutbound(node)
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
        }
        root.put("experimental", experimental)

        return root.toString(2)
    }

    private fun buildNodeOutbound(node: ProxyNode): JSONObject? {
        val ob = JSONObject()
        ob.put("tag", node.name)
        ob.put("server", node.server)
        ob.put("server_port", node.port)

        when (node.protocol.lowercase()) {
            "vless" -> {
                ob.put("type", "vless")
                ob.put("uuid", node.uuidOrPassword)
                if (node.flow.isNotEmpty()) ob.put("flow", node.flow)
                if (node.tls) {
                    val tlsObj = JSONObject().apply {
                        put("enabled", true)
                        put("server_name", if (node.sni.isNotEmpty()) node.sni else node.server)
                        put("insecure", node.allowInsecure)
                    }
                    ob.put("tls", tlsObj)
                }
                if (node.network.isNotEmpty() && node.network != "tcp") {
                    val transport = JSONObject().apply {
                        put("type", node.network)
                        if (node.path.isNotEmpty()) put("path", node.path)
                        if (node.host.isNotEmpty()) {
                            put("headers", JSONObject().apply {
                                put("host", node.host)
                            })
                        }
                    }
                    ob.put("transport", transport)
                }
            }
            "vmess" -> {
                ob.put("type", "vmess")
                ob.put("uuid", node.uuidOrPassword)
                ob.put("alter_id", node.alterId)
                ob.put("security", if (node.cipher.isNotEmpty()) node.cipher else "auto")
                if (node.tls) {
                    val tlsObj = JSONObject().apply {
                        put("enabled", true)
                        put("server_name", if (node.sni.isNotEmpty()) node.sni else node.server)
                    }
                    ob.put("tls", tlsObj)
                }
                if (node.network.isNotEmpty() && node.network != "tcp") {
                    val transport = JSONObject().apply {
                        put("type", node.network)
                        if (node.path.isNotEmpty()) put("path", node.path)
                        if (node.host.isNotEmpty()) {
                            put("headers", JSONObject().apply {
                                put("host", node.host)
                            })
                        }
                    }
                    ob.put("transport", transport)
                }
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
                    put("server_name", if (node.sni.isNotEmpty()) node.sni else node.server)
                    put("insecure", node.allowInsecure)
                }
                ob.put("tls", tlsObj)
            }
            "hysteria2", "hy2" -> {
                ob.put("type", "hysteria2")
                ob.put("password", node.uuidOrPassword)
                val tlsObj = JSONObject().apply {
                    put("enabled", true)
                    put("server_name", if (node.sni.isNotEmpty()) node.sni else node.server)
                    put("insecure", node.allowInsecure)
                }
                ob.put("tls", tlsObj)
            }
            "tuic" -> {
                ob.put("type", "tuic")
                ob.put("uuid", node.uuidOrPassword)
                ob.put("password", node.uuidOrPassword)
                ob.put("congestion_control", "bbr")
                val tlsObj = JSONObject().apply {
                    put("enabled", true)
                    put("server_name", if (node.sni.isNotEmpty()) node.sni else node.server)
                    put("insecure", node.allowInsecure)
                    put("alpn", JSONArray().apply { put("h3") })
                }
                ob.put("tls", tlsObj)
            }
            "anytls" -> {
                ob.put("type", "anytls")
                ob.put("password", node.uuidOrPassword)
                val tlsObj = JSONObject().apply {
                    put("enabled", true)
                    put("server_name", if (node.sni.isNotEmpty()) node.sni else node.server)
                    put("insecure", node.allowInsecure)
                }
                ob.put("tls", tlsObj)
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
                        put("server_name", if (node.sni.isNotEmpty()) node.sni else node.server)
                        put("insecure", node.allowInsecure)
                    }
                    ob.put("tls", tlsObj)
                }
            }
            else -> return null
        }

        return ob
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
        val enabled = if (nodes.any { it.enabled }) nodes.filter { it.enabled } else nodes.ifEmpty { listOf(fallbackDummyNode) }
        val sb = StringBuilder()
        sb.appendLine("port: 7890")
        sb.appendLine("socks-port: 7891")
        sb.appendLine("allow-lan: true")
        sb.appendLine("mode: rule")
        sb.appendLine("log-level: info")
        sb.appendLine("ipv6: false")
        sb.appendLine("external-controller: 0.0.0.0:9090")
        sb.appendLine()
        sb.appendLine("dns:")
        sb.appendLine("  enable: true")
        sb.appendLine("  listen: 0.0.0.0:1053")
        sb.appendLine("  ipv6: false")
        sb.appendLine("  enhanced-mode: fake-ip")
        sb.appendLine("  fake-ip-range: 198.18.0.1/16")
        sb.appendLine("  nameserver:")
        sb.appendLine("    - 223.5.5.5")
        sb.appendLine("    - 119.29.29.29")
        sb.appendLine("  fallback:")
        sb.appendLine("    - https://1.1.1.1/dns-query")
        sb.appendLine("    - https://8.8.8.8/dns-query")
        sb.appendLine()
        sb.appendLine("proxies:")

        enabled.forEach { node ->
            val nodeType = mapClashType(node.protocol)
            sb.appendLine("  - name: \"${escapeYaml(node.name)}\"")
            sb.appendLine("    type: $nodeType")
            sb.appendLine("    server: \"${escapeYaml(node.server)}\"")
            sb.appendLine("    port: ${node.port}")
            sb.appendLine("    udp: true")

            when (node.protocol.lowercase()) {
                "vless" -> {
                    sb.appendLine("    uuid: \"${escapeYaml(node.uuidOrPassword)}\"")
                    if (node.flow.isNotEmpty()) sb.appendLine("    flow: \"${escapeYaml(node.flow)}\"")
                    if (node.tls) {
                        sb.appendLine("    tls: true")
                        sb.appendLine("    servername: \"${escapeYaml(if (node.sni.isNotEmpty()) node.sni else node.server)}\"")
                        if (node.allowInsecure) sb.appendLine("    skip-cert-verify: true")
                    }
                    if (node.network.isNotEmpty() && node.network != "tcp") {
                        sb.appendLine("    network: ${node.network}")
                        if (node.network == "ws") {
                            sb.appendLine("    ws-opts:")
                            if (node.path.isNotEmpty()) sb.appendLine("      path: \"${escapeYaml(node.path)}\"")
                            if (node.host.isNotEmpty()) {
                                sb.appendLine("      headers:")
                                sb.appendLine("        Host: \"${escapeYaml(node.host)}\"")
                            }
                        }
                    }
                }
                "vmess" -> {
                    sb.appendLine("    uuid: \"${escapeYaml(node.uuidOrPassword)}\"")
                    sb.appendLine("    alterId: ${node.alterId}")
                    sb.appendLine("    cipher: \"${escapeYaml(if (node.cipher.isNotEmpty()) node.cipher else "auto")}\"")
                    if (node.tls) {
                        sb.appendLine("    tls: true")
                        sb.appendLine("    servername: \"${escapeYaml(if (node.sni.isNotEmpty()) node.sni else node.server)}\"")
                        if (node.allowInsecure) sb.appendLine("    skip-cert-verify: true")
                    }
                    if (node.network.isNotEmpty() && node.network != "tcp") {
                        sb.appendLine("    network: ${node.network}")
                        if (node.network == "ws") {
                            sb.appendLine("    ws-opts:")
                            if (node.path.isNotEmpty()) sb.appendLine("      path: \"${escapeYaml(node.path)}\"")
                            if (node.host.isNotEmpty()) {
                                sb.appendLine("      headers:")
                                sb.appendLine("        Host: \"${escapeYaml(node.host)}\"")
                            }
                        }
                    }
                }
                "ss", "shadowsocks" -> {
                    sb.appendLine("    cipher: \"${escapeYaml(if (node.cipher.isNotEmpty()) node.cipher else "aes-256-gcm")}\"")
                    sb.appendLine("    password: \"${escapeYaml(node.uuidOrPassword)}\"")
                }
                "trojan" -> {
                    sb.appendLine("    password: \"${escapeYaml(node.uuidOrPassword)}\"")
                    sb.appendLine("    sni: \"${escapeYaml(if (node.sni.isNotEmpty()) node.sni else node.server)}\"")
                    if (node.allowInsecure) sb.appendLine("    skip-cert-verify: true")
                }
                "hysteria2", "hy2" -> {
                    sb.appendLine("    password: \"${escapeYaml(node.uuidOrPassword)}\"")
                    sb.appendLine("    sni: \"${escapeYaml(if (node.sni.isNotEmpty()) node.sni else node.server)}\"")
                    if (node.allowInsecure) sb.appendLine("    skip-cert-verify: true")
                }
                "tuic" -> {
                    sb.appendLine("    uuid: \"${escapeYaml(node.uuidOrPassword)}\"")
                    sb.appendLine("    password: \"${escapeYaml(node.uuidOrPassword)}\"")
                    sb.appendLine("    sni: \"${escapeYaml(if (node.sni.isNotEmpty()) node.sni else node.server)}\"")
                    sb.appendLine("    congestion-control: bbr")
                    sb.appendLine("    alpn: [h3]")
                    if (node.allowInsecure) sb.appendLine("    skip-cert-verify: true")
                }
                "anytls" -> {
                    sb.appendLine("    password: \"${escapeYaml(node.uuidOrPassword)}\"")
                    sb.appendLine("    sni: \"${escapeYaml(if (node.sni.isNotEmpty()) node.sni else node.server)}\"")
                    if (node.allowInsecure) sb.appendLine("    skip-cert-verify: true")
                }
                "socks", "socks5" -> {
                    if (node.host.isNotEmpty()) sb.appendLine("    username: \"${escapeYaml(node.host)}\"")
                    if (node.uuidOrPassword.isNotEmpty()) sb.appendLine("    password: \"${escapeYaml(node.uuidOrPassword)}\"")
                }
                else -> { // http
                    if (node.host.isNotEmpty()) sb.appendLine("    username: \"${escapeYaml(node.host)}\"")
                    if (node.uuidOrPassword.isNotEmpty()) sb.appendLine("    password: \"${escapeYaml(node.uuidOrPassword)}\"")
                    if (node.tls) {
                        sb.appendLine("    tls: true")
                        sb.appendLine("    servername: \"${escapeYaml(if (node.sni.isNotEmpty()) node.sni else node.server)}\"")
                        if (node.allowInsecure) sb.appendLine("    skip-cert-verify: true")
                    }
                }
            }
        }

        sb.appendLine()
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
        sb.appendLine("    tolerance: 50")
        sb.appendLine("    proxies:")
        if (enabled.isNotEmpty()) {
            enabled.forEach { sb.appendLine("      - \"${escapeYaml(it.name)}\"") }
        } else {
            sb.appendLine("      - DIRECT")
        }

        // 3. 🏠 私有网络
        sb.appendLine("  - name: 🏠 私有网络")
        sb.appendLine("    type: select")
        sb.appendLine("    proxies:")
        sb.appendLine("      - DIRECT")
        sb.appendLine("      - 🚀 节点选择")
        enabled.forEach { sb.appendLine("      - \"${escapeYaml(it.name)}\"") }

        // 4. 🔒 国内服务
        sb.appendLine("  - name: 🔒 国内服务")
        sb.appendLine("    type: select")
        sb.appendLine("    proxies:")
        sb.appendLine("      - DIRECT")
        sb.appendLine("      - 🚀 节点选择")
        enabled.forEach { sb.appendLine("      - \"${escapeYaml(it.name)}\"") }

        // 5. 🌐 非中国
        sb.appendLine("  - name: 🌐 非中国")
        sb.appendLine("    type: select")
        sb.appendLine("    proxies:")
        sb.appendLine("      - 🚀 节点选择")
        enabled.forEach { sb.appendLine("      - \"${escapeYaml(it.name)}\"") }
        sb.appendLine("      - DIRECT")

        // 6. 🐟 漏网之鱼
        sb.appendLine("  - name: 🐟 漏网之鱼")
        sb.appendLine("    type: select")
        sb.appendLine("    proxies:")
        sb.appendLine("      - 🚀 节点选择")
        enabled.forEach { sb.appendLine("      - \"${escapeYaml(it.name)}\"") }
        sb.appendLine("      - DIRECT")

        sb.appendLine()
        sb.appendLine("rules:")
        sb.appendLine("  - GEOIP,private,DIRECT,no-resolve")
        sb.appendLine("  - GEOSITE,private,DIRECT")
        sb.appendLine("  - GEOSITE,category-ads-all,REJECT")
        sb.appendLine("  - GEOSITE,cn,DIRECT")
        sb.appendLine("  - GEOIP,cn,DIRECT")
        sb.appendLine("  - MATCH,🐟 漏网之鱼")

        return sb.toString()
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
        val targetList = if (nodes.any { it.enabled }) nodes.filter { it.enabled } else nodes.ifEmpty { listOf(fallbackDummyNode) }
        val uris = targetList.joinToString("\n") { if (it.rawUri.isNotBlank()) it.rawUri else it.toUri() }
        return Base64.encodeToString(uris.toByteArray(StandardCharsets.UTF_8), Base64.NO_WRAP or Base64.DEFAULT)
    }
}
