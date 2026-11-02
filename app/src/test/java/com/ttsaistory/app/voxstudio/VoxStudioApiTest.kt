package com.ttsaistory.app.voxstudio

import org.junit.Assert.assertEquals
import org.junit.Test

class VoxStudioApiTest {

    @Test
    fun blankUsesDefaultBaseUrl() {
        assertEquals(VoxStudioConfig.DEFAULT_BASE_URL, VoxStudioApi.normalizeBaseUrl("   "))
    }

    @Test
    fun keepsExplicitHttpsAndTrimsTrailingSlash() {
        assertEquals(
            "https://pro400.tailaf56ea.ts.net",
            VoxStudioApi.normalizeBaseUrl("https://pro400.tailaf56ea.ts.net/"),
        )
    }

    @Test
    fun domainWithoutSchemeUsesHttps() {
        assertEquals(
            "https://pro400.tailaf56ea.ts.net",
            VoxStudioApi.normalizeBaseUrl("pro400.tailaf56ea.ts.net"),
        )
    }

    @Test
    fun ipv4WithoutSchemeUsesHttp() {
        assertEquals("http://100.95.4.110", VoxStudioApi.normalizeBaseUrl("100.95.4.110"))
        assertEquals(
            "http://192.168.0.112:8080",
            VoxStudioApi.normalizeBaseUrl("192.168.0.112:8080/"),
        )
    }

    @Test
    fun localhostUsesHttp() {
        assertEquals("http://localhost:8080", VoxStudioApi.normalizeBaseUrl("localhost:8080"))
    }
}
