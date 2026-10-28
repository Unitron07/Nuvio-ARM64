package com.nuvio.app.stage3

import com.nuvio.app.core.network.*
import kotlin.test.*

class Stage3AuthConfigurationTest {
    private fun config(url: String, key: String) = ServerConfiguration(
        backendUrl = url, publishableKey = key,
        capabilities = ServerCapabilities(true, true), isCustom = false,
    )

    @Test fun missingBuildConfigurationFailsBeforeClientCreation() {
        for ((url, key) in listOf("" to "", "https://example.test" to "", "" to "synthetic")) {
            val error = assertFailsWith<MissingAuthConfiguration> { requireAuthConfiguration(config(url, key)) }
            assertFalse(error.message.orEmpty().contains("synthetic"))
        }
    }

    @Test fun configuredBackendIsPreserved() {
        val configuration = config("https://example.test", "synthetic")
        requireAuthConfiguration(configuration)
        assertEquals("https://example.test", configuration.backendUrl)
    }
}
