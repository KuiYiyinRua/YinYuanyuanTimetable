package com.yinyuanyuan.timetable

import org.junit.Assert.*
import org.junit.Test

class PortalAddressTest {
    @Test fun websiteWithoutSchemeGetsHttps() {
        assertEquals("https://www.example.edu.cn/portal",ImportPreferences.normalizePortal("  www.example.edu.cn/portal  "))
        assertTrue(ImportPreferences.validPortal("www.example.edu.cn/portal"))
    }
    @Test fun arbitraryUniversityAndHttpsPortAccepted() {
        assertTrue(ImportPreferences.validPortal("https://new-university.example.edu:8443/portal?a=1#login"))
    }
    @Test fun nonWebSchemesAndEmbeddedCredentialsRejected() {
        listOf("javascript:alert(1)","file:///etc/passwd","http://example.edu","https://user:secret@example.edu").forEach {
            assertFalse(it,ImportPreferences.validPortal(it))
        }
    }
    @Test fun emptyHostAndInvalidPortRejected() {
        listOf("","https://","https://example.edu:65536","https://example.edu:0","not a website").forEach {
            assertFalse(it,ImportPreferences.validPortal(it))
        }
    }
}
