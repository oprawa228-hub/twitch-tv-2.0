package com.oprawa.twitch20.auth

import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class HelixCatalogAccessTest {
    @Test fun endpointAndCredentialsCannotEscapeTheCatalogBoundary() {
        listOf("../users", "users/follows", "streams?token=x", "https://example.test/", "users/..").forEach {
            assertTrue("Reject endpoint $it", runCatching { HelixCatalogAccess.url(it, emptyMap()) }.exceptionOrNull() is AuthException)
        }
        listOf("access_token", "refresh_token", "authorization", "client_secret").forEach {
            assertTrue("Credentials must not enter query strings", runCatching {
                HelixCatalogAccess.url("streams", mapOf(it to "secret"))
            }.exceptionOrNull() is AuthException)
        }
    }

    @Test fun publicCatalogEndpointsKeepCursorAsAnEncodedQueryValue() {
        listOf("streams", "games/top", "videos", "clips", "users").forEach { endpoint ->
            val url = HelixCatalogAccess.url(endpoint, mapOf("after" to "a+/=&?", "first" to "30"))
            assertEquals("api.twitch.tv", url.host)
            assertEquals("/helix/$endpoint", url.encodedPath)
            assertEquals("a+/=&?", url.queryParameter("after"))
            assertEquals(setOf("after", "first"), url.queryParameterNames)
        }
    }

    @Test fun expiredAccessRetriesOnceUsingTheReplacementSession() = runTest {
        var rotated = false
        var requests = 0
        var refreshes = 0
        val result = HelixCatalogAccess.fetch(
            request = {
                requests++
                if (rotated) HelixReply(200, JSONObject("""{"data":[{"id":"42"}]}"""))
                else HelixReply(401, JSONObject("""{"message":"expired"}"""))
            },
            refresh = { refreshes++; rotated = true },
            expire = { fail("A successful rotation must preserve the session") },
        )
        assertEquals("42", result.optJSONArray("data")?.optJSONObject(0)?.optString("id"))
        assertEquals(2, requests)
        assertEquals(1, refreshes)
    }

    @Test fun repeatedUnauthorizedExpiresSessionWithoutARefreshLoop() = runTest {
        var refreshes = 0
        var expired = false
        val failure = runCatching {
            HelixCatalogAccess.fetch({ HelixReply(401, JSONObject()) }, { refreshes++ }, { expired = true })
        }.exceptionOrNull()
        assertTrue(failure is AuthException)
        assertTrue(expired)
        assertEquals(1, refreshes)
    }

    @Test fun serviceErrorsPreserveSessionAndNeverExposeResponseBodies() = runTest {
        for (status in listOf(403, 429, 500)) {
            val failure = runCatching {
                HelixCatalogAccess.fetch(
                    { HelixReply(status, JSONObject("""{"message":"secret-response"}""")) },
                    { fail("Do not refresh for $status") },
                    { fail("Do not remove credentials for $status") },
                )
            }.exceptionOrNull()
            assertTrue(failure is AuthException)
            assertFalse(failure?.message.orEmpty().contains("secret-response"))
        }
    }
}
