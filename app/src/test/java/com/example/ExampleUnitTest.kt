package com.example

import com.example.data.generator.SingBoxConfigGenerator
import com.example.data.model.ProxyNode
import com.example.data.parser.SubscriptionParser
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleUnitTest {

    @Test
    fun parseVlessUri_isCorrect() {
        val uri = "vless://12345678-1234-1234-1234-1234567890ab@example.com:443?type=ws&security=tls&sni=example.com&path=/ws#TestVless"
        val nodes = SubscriptionParser.parseContent(uri)

        assertEquals(1, nodes.size)
        val node = nodes[0]
        assertEquals("TestVless", node.name)
        assertEquals("vless", node.protocol)
        assertEquals("example.com", node.server)
        assertEquals(443, node.port)
        assertEquals("12345678-1234-1234-1234-1234567890ab", node.uuidOrPassword)
        assertTrue(node.tls)
        assertEquals("example.com", node.sni)
        assertEquals("ws", node.network)
        assertEquals("/ws", node.path)
    }

    @Test
    fun generateSingBoxConfig_114_isCorrect() {
        val node = ProxyNode(
            name = "Node1",
            protocol = "vless",
            server = "1.2.3.4",
            port = 443,
            uuidOrPassword = "uuid-123",
            tls = true,
            sni = "example.com"
        )

        val jsonStr = SingBoxConfigGenerator.generateJson(listOf(node), version = "1.14")
        val root = JSONObject(jsonStr)

        assertNotNull(root.optJSONArray("http_clients"))
        assertNotNull(root.optJSONObject("dns"))
        assertNotNull(root.optJSONArray("inbounds"))
        assertNotNull(root.optJSONArray("outbounds"))
        
        val route = root.optJSONObject("route")!!
        assertEquals("dns_resolver", route.optString("default_domain_resolver"))
        assertEquals("default-http-client", route.optString("default_http_client"))

        val inbounds = root.getJSONArray("inbounds")
        val tun = inbounds.getJSONObject(1)
        assertEquals("172.19.0.1/30", tun.optString("address"))
        assertEquals("mixed", tun.optString("stack"))
    }

    @Test
    fun generateSingBoxConfig_113_isCorrect() {
        val node = ProxyNode(
            name = "anytls-ocichi",
            protocol = "anytls",
            server = "163.192.206.209",
            port = 62467,
            uuidOrPassword = "66d759fc-d0ca-42bc-b77a-abd39836bca4",
            sni = "www.bing.com",
            allowInsecure = true
        )

        val jsonStr = SingBoxConfigGenerator.generateJson(listOf(node), version = "1.13")
        val root = JSONObject(jsonStr)

        // Must not have http_clients in 1.13
        assertTrue("1.13 must not have http_clients", root.optJSONArray("http_clients") == null)
        
        // DNS servers check
        val dns = root.getJSONObject("dns")
        val dnsServers = dns.getJSONArray("servers")
        assertEquals(4, dnsServers.length())

        val route = root.optJSONObject("route")!!
        assertEquals("dns_resolver", route.optString("default_domain_resolver"))
        assertTrue("1.13 route must not have default_http_client", route.optString("default_http_client").isEmpty())

        val ruleSet = route.getJSONArray("rule_set")
        val item0 = ruleSet.getJSONObject(0)
        assertEquals("🚀 节点选择", item0.optString("download_detour"))
        assertTrue(item0.optString("http_client").isEmpty())

        val experimental = root.getJSONObject("experimental")
        assertNotNull(experimental.optJSONObject("cache_file"))
        assertNotNull(experimental.optJSONObject("clash_api"))
    }
}
