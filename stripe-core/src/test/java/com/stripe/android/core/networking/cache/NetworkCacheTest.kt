package com.stripe.android.core.networking.cache

import com.stripe.android.core.networking.StripeResponse
import com.stripe.android.core.utils.TimeProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class NetworkCacheTest {

    private val networkDiskCache = mock<NetworkDiskCache>()
    private val timeProvider = mock<TimeProvider>()
    private val networkCache = DefaultNetworkCache(
        context = mock(),
        timeProvider = timeProvider,
        diskLruCache = networkDiskCache
    )

    @Before
    fun setup() {
        whenever(timeProvider.currentTimeInMillis()).thenReturn(300000)
    }

    @Test
    fun `load - if data is fresh, return from cache`() = runTest {
        val expected = StripeResponse<String>(
            code = 200,
            body = null,
            headers = mapOf(
                "cache-control" to listOf("public, max-age=300, must-revalidate")
            ),
            timestamp = currentTime
        )
        networkCacheReturns("freshResponse", Json.encodeToString(expected))

        val actual = networkCache.get("freshResponse")

        assertEquals(
            expected,
            actual
        )
    }

    @Test
    fun `load - if data is stale, return from network`() = runTest {
        val response = StripeResponse<String>(
            code = 200,
            body = null,
            headers = mapOf(
                "Cache-Control" to listOf("public, max-age=0, must-revalidate")
            ),
            timestamp = currentTime
        )
        networkCacheReturns("staleResponse", Json.encodeToString(response))

        val actual = networkCache.get("staleResponse")

        assertEquals(
            null,
            actual
        )
    }

    private fun networkCacheReturns(key: String, data: String) {
        whenever(networkDiskCache.get(key)).thenReturn(data)
    }
}