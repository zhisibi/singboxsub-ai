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
        inboundPort: Int = 2080
    ): String {
        val enabledNodes = if (nodes.any { it.enabled }) nodes.filter { it.enabled } else nodes.ifEmpty { listOf(fallbackDummyNode) }
        val root = JSONObject()

        // 1. Log configuration
        val log = JSONObject().apply {
            put("disabled", false)
            put("level", "info")
            put("timestamp", true)
        }
        root.put("log", log)

        // 2. Modern DNS configuration (Compatible with sing-box 1.12+ and 1.14+)
        val dns = JSONObject().apply {
            val servers = JSONArray().apply {
                put(JSONObject().apply {
                    put("type", "https")
                    put("tag", "dns-remote")
                    put("server", "1.1.1.1")
                    put("path", "/dns-query")
                    put("detour", "🎯 直连")
                })
                put(JSONObject().apply {
                    put("type", "udp")
                    put("tag", "dns-direct")
                    put("server", "223.5.5.5")
                })
            }
            put("servers", servers)

            val dnsRules = JSONArray().apply {
                put(JSONObject().apply {
                    put("clash_mode", "Direct")
                    put("server", "dns-direct")
                })
                put(JSONObject().apply {
                    put("clash_mode", "Global")
                    put("server", "dns-remote")
                })
                put(JSONObject().apply {
                    put("rule_set", JSONArray().apply { put("geosite-cn") })
                    put("server", "dns-direct")
                })
                put(JSONObject().apply {
                    put("domain_suffix", JSONArray().apply {
                        put(".cn")
                        put("baidu.com")
                        put("qq.com")
                        put("taobao.com")
                        put("alipay.com")
                        put("jd.com")
                    })
                    put("server", "dns-direct")
                })
            }
            put("rules", dnsRules)
            put("final", "dns-remote")
            put("strategy", "prefer_ipv4")
            put("independent_cache", true)
        }
        root.put("dns", dns)

        // 3. Inbounds configuration
        val inbounds = JSONArray().apply {
            put(JSONObject().apply {
                put("type", "mixed")
                put("tag", "mixed-in")
                put("listen", "0.0.0.0")
                put("listen_port", inboundPort)
            })
        }
        root.put("inbounds", inbounds)

        // 4. Outbounds configuration
        val outbounds = JSONArray()
        val nodeTags = enabledNodes.map { it.name }.filter { it.isNotEmpty() }

        // Selector outbound
        val selectorOutbound = JSONObject().apply {
            put("type", "selector")
            put("tag", "🚀 节点选择")
            val outList = JSONArray().apply {
                put("⚡ 自动选择")
                put("🎯 直连")
                nodeTags.forEach { put(it) }
            }
            put("outbounds", outList)
            put("default", if (nodeTags.isNotEmpty()) nodeTags[0] else "🎯 直连")
        }
        outbounds.put(selectorOutbound)

        // Auto URLTest outbound
        val autoTestOutbound = JSONObject().apply {
            put("type", "urltest")
            put("tag", "⚡ 自动选择")
            val outList = JSONArray().apply {
                nodeTags.forEach { put(it) }
            }
            put("outbounds", if (outList.length() > 0) outList else JSONArray().apply { put("🎯 直连") })
            put("url", "http://www.gstatic.com/generate_204")
            put("interval", "3m")
            put("tolerance", 50)
        }
        outbounds.put(autoTestOutbound)

        // Direct outbound
        outbounds.put(JSONObject().apply {
            put("type", "direct")
            put("tag", "🎯 直连")
        })

        // Block outbound
        outbounds.put(JSONObject().apply {
            put("type", "block")
            put("tag", "🛑 拦截")
        })

        // Node outbounds
        enabledNodes.forEach { node ->
            val ob = buildNodeOutbound(node)
            if (ob != null) {
                outbounds.put(ob)
            }
        }

        root.put("outbounds", outbounds)

        // 5. Route configuration
        val route = JSONObject().apply {
            put("default_domain_resolver", "dns-direct")

            val ruleSet = JSONArray().apply {
                put(JSONObject().apply {
                    put("type", "remote")
                    put("tag", "geosite-cn")
                    put("format", "binary")
                    put("url", "https://raw.githubusercontent.com/SagerNet/sing-geosite/rule-set/geosite-cn.srs")
                    put("download_detour", "🎯 直连")
                })
                put(JSONObject().apply {
                    put("type", "remote")
                    put("tag", "geoip-cn")
                    put("format", "binary")
                    put("url", "https://raw.githubusercontent.com/SagerNet/sing-geoip/rule-set/geoip-cn.srs")
                    put("download_detour", "🎯 直连")
                })
                put(JSONObject().apply {
                    put("type", "remote")
                    put("tag", "geosite-category-ads-all")
                    put("format", "binary")
                    put("url", "https://raw.githubusercontent.com/SagerNet/sing-geosite/rule-set/geosite-category-ads-all.srs")
                    put("download_detour", "🎯 直连")
                })
            }
            put("rule_set", ruleSet)

            val rules = JSONArray()

            // Modern sing-box 1.11+ sniffing & 1.12+ DNS hijack action
            rules.put(JSONObject().apply {
                put("action", "sniff")
            })
            rules.put(JSONObject().apply {
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
                        put("outbound", "🎯 直连")
                    })
                }
                else -> { // Rule mode
                    rules.put(JSONObject().apply {
                        put("ip_is_private", true)
                        put("outbound", "🎯 直连")
                    })
                    rules.put(JSONObject().apply {
                        put("rule_set", JSONArray().apply { put("geosite-category-ads-all") })
                        put("outbound", "🛑 拦截")
                    })
                    rules.put(JSONObject().apply {
                        put("rule_set", JSONArray().apply {
                            put("geosite-cn")
                            put("geoip-cn")
                        })
                        put("outbound", "🎯 直连")
                    })
                    rules.put(JSONObject().apply {
                        put("domain_suffix", JSONArray().apply {
                            put(".cn")
                            put("baidu.com")
                            put("qq.com")
                            put("taobao.com")
                            put("alipay.com")
                            put("jd.com")
                            put("163.com")
                            put("weibo.com")
                            put("amap.com")
                            put("bilibili.com")
                            put("bytedance.com")
                        })
                        put("outbound", "🎯 直连")
                    })
                }
            }
            put("rules", rules)
            put("final", "🚀 节点选择")
            put("auto_detect_interface", true)
        }
        root.put("route", route)

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
                                put("Host", JSONArray().apply { put(node.host) })
                            })
                        }
                    }
                    ob.put("transport", transport)
                }
            }
            "vmess" -> {
                ob.put("type", "vmess")
                ob.put("uuid", node.uuidOrPassword)
                ob.put("security", if (node.cipher.isNotEmpty()) node.cipher else "auto")
                ob.put("alter_id", node.alterId)
                if (node.tls) {
                    val tlsObj = JSONObject().apply {
                        put("enabled", true)
                        put("server_name", if (node.sni.isNotEmpty()) node.sni else node.server)
                    }
                    ob.put("tls", tlsObj)
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

    fun generateYaml(nodes: List<ProxyNode>, isMihomo: Boolean = true): String {
        val enabled = if (nodes.any { it.enabled }) nodes.filter { it.enabled } else nodes.ifEmpty { listOf(fallbackDummyNode) }
        val sb = StringBuilder()
        sb.appendLine("# Mihomo / Clash Meta Universal Subscription Config")
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
            sb.appendLine("  - name: \"${node.name}\"")
            sb.appendLine("    type: $nodeType")
            sb.appendLine("    server: ${node.server}")
            sb.appendLine("    port: ${node.port}")
            sb.appendLine("    udp: true")

            when (node.protocol.lowercase()) {
                "vless" -> {
                    sb.appendLine("    uuid: \"${node.uuidOrPassword}\"")
                    if (node.flow.isNotEmpty()) sb.appendLine("    flow: \"${node.flow}\"")
                    if (node.tls) {
                        sb.appendLine("    tls: true")
                        sb.appendLine("    servername: \"${if (node.sni.isNotEmpty()) node.sni else node.server}\"")
                        if (node.allowInsecure) sb.appendLine("    skip-cert-verify: true")
                    }
                    if (node.network.isNotEmpty() && node.network != "tcp") {
                        sb.appendLine("    network: ${node.network}")
                        if (node.network == "ws") {
                            sb.appendLine("    ws-opts:")
                            if (node.path.isNotEmpty()) sb.appendLine("      path: \"${node.path}\"")
                            if (node.host.isNotEmpty()) {
                                sb.appendLine("      headers:")
                                sb.appendLine("        Host: \"${node.host}\"")
                            }
                        }
                    }
                }
                "vmess" -> {
                    sb.appendLine("    uuid: \"${node.uuidOrPassword}\"")
                    sb.appendLine("    alterId: ${node.alterId}")
                    sb.appendLine("    cipher: \"${if (node.cipher.isNotEmpty()) node.cipher else "auto"}\"")
                    if (node.tls) {
                        sb.appendLine("    tls: true")
                        sb.appendLine("    servername: \"${if (node.sni.isNotEmpty()) node.sni else node.server}\"")
                    }
                }
                "ss", "shadowsocks" -> {
                    sb.appendLine("    cipher: ${if (node.cipher.isNotEmpty()) node.cipher else "aes-256-gcm"}")
                    sb.appendLine("    password: \"${node.uuidOrPassword}\"")
                }
                "trojan" -> {
                    sb.appendLine("    password: \"${node.uuidOrPassword}\"")
                    sb.appendLine("    sni: \"${if (node.sni.isNotEmpty()) node.sni else node.server}\"")
                }
                "hysteria2", "hy2" -> {
                    sb.appendLine("    password: \"${node.uuidOrPassword}\"")
                    sb.appendLine("    sni: \"${if (node.sni.isNotEmpty()) node.sni else node.server}\"")
                    if (node.allowInsecure) sb.appendLine("    skip-cert-verify: true")
                }
                "anytls" -> {
                    sb.appendLine("    password: \"${node.uuidOrPassword}\"")
                    sb.appendLine("    sni: \"${if (node.sni.isNotEmpty()) node.sni else node.server}\"")
                    if (node.allowInsecure) sb.appendLine("    skip-cert-verify: true")
                }
                "socks", "socks5" -> {
                    if (node.host.isNotEmpty()) sb.appendLine("    username: \"${node.host}\"")
                    if (node.uuidOrPassword.isNotEmpty()) sb.appendLine("    password: \"${node.uuidOrPassword}\"")
                }
                else -> { // http
                    if (node.host.isNotEmpty()) sb.appendLine("    username: \"${node.host}\"")
                    if (node.uuidOrPassword.isNotEmpty()) sb.appendLine("    password: \"${node.uuidOrPassword}\"")
                }
            }
        }

        sb.appendLine()
        sb.appendLine("proxy-groups:")
        sb.appendLine("  - name: 🚀 节点选择")
        sb.appendLine("    type: select")
        sb.appendLine("    proxies:")
        sb.appendLine("      - ⚡ 自动选择")
        sb.appendLine("      - DIRECT")
        enabled.forEach { sb.appendLine("      - \"${it.name}\"") }

        sb.appendLine("  - name: ⚡ 自动选择")
        sb.appendLine("    type: url-test")
        sb.appendLine("    url: http://www.gstatic.com/generate_204")
        sb.appendLine("    interval: 300")
        sb.appendLine("    proxies:")
        if (enabled.isNotEmpty()) {
            enabled.forEach { sb.appendLine("      - \"${it.name}\"") }
        } else {
            sb.appendLine("      - DIRECT")
        }

        sb.appendLine()
        sb.appendLine("rules:")
        sb.appendLine("  - GEOIP,private,DIRECT,no-resolve")
        sb.appendLine("  - GEOSITE,private,DIRECT")
        sb.appendLine("  - GEOSITE,category-ads-all,REJECT")
        sb.appendLine("  - GEOSITE,cn,DIRECT")
        sb.appendLine("  - GEOIP,cn,DIRECT")
        sb.appendLine("  - MATCH,🚀 节点选择")

        return sb.toString()
    }

    private fun mapClashType(protocol: String): String {
        return when (protocol.lowercase()) {
            "vless" -> "vless"
            "vmess" -> "vmess"
            "ss", "shadowsocks" -> "ss"
            "trojan" -> "trojan"
            "hysteria2", "hy2" -> "hysteria2"
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
