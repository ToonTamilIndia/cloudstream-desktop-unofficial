package com.lagradost.server.routes

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class LinkMapperTest {

    @Test
    fun `defaultSessionHeaders includes standard headers`() {
        val headers = defaultSessionHeaders("https://example.com/page")
        assertEquals("Mozilla/5.0", headers["User-Agent"]?.substring(0, 11))
        assertEquals("https://example.com/page", headers["Referer"])
        assertEquals("https://example.com", headers["Origin"])
    }

    @Test
    fun `defaultSessionHeaders without referer`() {
        val headers = defaultSessionHeaders("")
        assertFalse(headers.containsKey("Referer"))
        assertFalse(headers.containsKey("Origin"))
    }

    @Test
    fun `mergeHeaders per-link wins over defaults`() {
        val defaults = mutableMapOf("User-Agent" to "default", "Referer" to "default")
        val perLink = listOf(mapOf("User-Agent" to "custom"))
        mergeHeaders(perLink, defaults)
        assertEquals("custom", defaults["User-Agent"])
        assertEquals("default", defaults["Referer"])
    }

    @Test
    fun `mergeHeaders deduplicates case-insensitive`() {
        val defaults = mutableMapOf("user-agent" to "default")
        val perLink = listOf(mapOf("User-Agent" to "custom"))
        mergeHeaders(perLink, defaults)
        assertEquals(1, defaults.keys.count { it.equals("User-Agent", ignoreCase = true) })
        assertEquals("custom", defaults["User-Agent"])
    }

    @Test
    fun `mergeHeaders empty per-link leaves defaults`() {
        val defaults = mutableMapOf("Key" to "value")
        mergeHeaders(emptyList(), defaults)
        assertEquals("value", defaults["Key"])
    }
}
