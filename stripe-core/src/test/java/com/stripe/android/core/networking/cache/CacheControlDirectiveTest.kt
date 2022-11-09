package com.stripe.android.core.networking.cache

import com.stripe.android.core.networking.StripeResponse
import com.stripe.android.core.utils.TimeProvider
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import kotlin.test.assertContentEquals

class CacheControlDirectiveTest {

    private val timeProvider = mock<TimeProvider>()
    private val currentTime = timeProvider.currentTimeInMillis()

    @Before
    fun setup() {
        whenever(timeProvider.currentTimeInMillis()).thenReturn(300000)
    }

    @Test
    fun `given response, cache control directives are created`() {
        val response = StripeResponse<String>(
            code = 200,
            body = null,
            headers = mapOf(
                "Cache-Control" to listOf("public, max-age=300, must-revalidate")
            ),
            timestamp = currentTime
        )

        val actual = response.getCacheControls(currentTime)

        assertContentEquals(
            listOf(
                CacheControlDirective.Public,
                CacheControlDirective.MaxAge(
                    maxAgeInMilliseconds = currentTime + 300000L,
                    currentTimeInMilliseconds = currentTime
                ),
                CacheControlDirective.MustRevalidate(
                    maxAgeInMilliseconds = currentTime + 300000L,
                    currentTimeInMilliseconds = currentTime
                )
            ),
            actual
        )
    }

    @Test
    fun `max-age can cache and is valid`() {
        val directive = CacheControlDirective.MaxAge(
            maxAgeInMilliseconds = currentTime + 300L,
            currentTimeInMilliseconds = currentTime
        )
        Assert.assertTrue(directive.canCache())
        Assert.assertTrue(directive.isValid())
    }

    @Test
    fun `s-max-age can cache and is valid`() {
        val directive = CacheControlDirective.SharedMaxAge(
            maxAgeInMilliseconds = currentTime + 300L,
            currentTimeInMilliseconds = currentTime
        )
        Assert.assertTrue(directive.canCache())
        Assert.assertTrue(directive.isValid())
    }

    @Test
    fun `public can cache and is valid`() {
        val directive = CacheControlDirective.Public
        Assert.assertTrue(directive.canCache())
        Assert.assertTrue(directive.isValid())
    }

    @Test
    fun `shared can cache and is valid`() {
        val directive = CacheControlDirective.Shared
        Assert.assertTrue(directive.canCache())
        Assert.assertTrue(directive.isValid())
    }

    @Test
    fun `private can cache and is valid`() {
        val directive = CacheControlDirective.Private
        Assert.assertTrue(directive.canCache())
        Assert.assertTrue(directive.isValid())
    }

    @Test
    fun `no-cache can cache and is not valid`() {
        val directive = CacheControlDirective.NoCache
        Assert.assertTrue(directive.canCache())
        Assert.assertFalse(directive.isValid())
    }

    @Test
    fun `no-store cannot cache and is not valid`() {
        val directive = CacheControlDirective.NoStore
        Assert.assertFalse(directive.canCache())
        Assert.assertFalse(directive.isValid())
    }

    @Test
    fun `must-revalidate can cache and is valid`() {
        val directive = CacheControlDirective.MustRevalidate(
            maxAgeInMilliseconds = currentTime + 300L,
            currentTimeInMilliseconds = currentTime
        )
        Assert.assertTrue(directive.canCache())
        Assert.assertTrue(directive.isValid())
    }

    @Test
    fun `proxy-revalidate can cache and is valid`() {
        val directive = CacheControlDirective.ProxyRevalidate(
            maxAgeInMilliseconds = currentTime + 300L,
            currentTimeInMilliseconds = currentTime
        )
        Assert.assertTrue(directive.canCache())
        Assert.assertTrue(directive.isValid())
    }
}