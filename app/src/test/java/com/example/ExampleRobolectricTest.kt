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
}
