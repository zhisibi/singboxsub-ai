package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.generator.ClashConfigGenerator
import com.example.data.model.ProxyNode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("SingBox Sub", appName)
    }

    @Test
    fun `test yaml escape character safety`() {
        val testNode = ProxyNode(
            id = 1,
            name = "Special \\ Name \"with\" Quotes",
            protocol = "ss",
            server = "127.0.0.1",
            port = 8388,
            uuidOrPassword = "pass\\word\"with#quote",
            cipher = "aes-256-gcm"
        )
        val yaml = ClashConfigGenerator.generateYaml(listOf(testNode), isMihomo = true)
        assertTrue(yaml.contains("\\\\"))
        assertTrue(yaml.contains("\\\""))
    }

    @Test
    fun `test anytls yaml output matches specification`() {
        val node = ProxyNode(
            id = 2,
            name = "anytls-chi2",
            protocol = "anytls",
            server = "163.192.119.133",
            port = 31939,
            uuidOrPassword = "0574c0ec-d2dc-4b1d-9101-648025cd9c18",
            sni = "www.bing.com"
        )
        val yaml = ClashConfigGenerator.generateYaml(listOf(node), isMihomo = true)
        assertTrue(yaml.contains("mixed-port: 2080"))
        assertTrue(yaml.contains("external-controller: 127.0.0.1:9090"))
        assertTrue(yaml.contains("fallback:\n    - https://dns.cloudflare.com/dns-query\n    - https://dns.google/dns-query"))
        assertTrue(yaml.contains("type: anytls"))
        assertTrue(yaml.contains("skip-cert-verify: true"))
        assertTrue(yaml.contains("GEOIP,private,🏠 私有网络,no-resolve"))
        assertTrue(yaml.contains("GEOSITE,cn,🔒 国内服务"))
        assertTrue(yaml.contains("GEOIP,CN,🔒 国内服务"))
        assertTrue(yaml.contains("GEOSITE,geolocation-!cn,🌐 非中国"))
        assertTrue(yaml.contains("MATCH,🐟 漏网之鱼"))
    }

    @Test
    fun `test vless reality parser and generators`() {
        val vlessUri = "vless://44444444-4444-4444-4444-444444444444@reality.example.com:443?type=tcp&security=reality&pbk=1111111111111111111111111111111111111111111&fp=chrome&sni=reality.example.com&sid=abcdef12&flow=xtls-rprx-vision#US-Reality"
        val nodes = com.example.data.parser.SubscriptionParser.parseContent(vlessUri)
        assertEquals(1, nodes.size)
        val node = nodes[0]
        assertEquals("US-Reality", node.name)
        assertEquals("vless", node.protocol)
        assertEquals("reality.example.com", node.server)
        assertEquals(443, node.port)
        assertEquals("xtls-rprx-vision", node.flow)
        assertEquals("chrome", node.fingerprint)
        assertEquals("1111111111111111111111111111111111111111111", node.realityPublicKey)

        val singboxJson = com.example.data.generator.SingBoxConfigGenerator.generateJson(nodes)
        assertTrue(singboxJson.contains("reality"))
        assertTrue(singboxJson.contains("public_key"))
        assertTrue(singboxJson.contains("xtls-rprx-vision"))
        assertTrue(singboxJson.contains("xudp"))

        val clashYaml = ClashConfigGenerator.generateYaml(nodes)
        assertTrue(clashYaml.contains("reality-opts"))
        assertTrue(clashYaml.contains("public-key"))
        assertTrue(clashYaml.contains("flow: \"xtls-rprx-vision\""))
    }
}
