package com.stripe.android.core.networking.cache

import android.content.Context
import androidx.annotation.RestrictTo
import com.stripe.android.core.networking.StripeResponse
import com.stripe.android.core.utils.DefaultTimeProvider
import com.stripe.android.core.utils.TimeProvider
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Used to cache the StripeResponse. Uses the [CacheControlDirective] to decide whether or not to
 * cache the response. If cached, it will be stored to disk.
 *
 * https://developer.mozilla.org/en-US/docs/Web/HTTP/Caching
 *
 * Example usage:
 * suspend fun someApiMethod(
 *     requestOptions: ApiRequest.Options
 * ): ApiResponse? {
 *     val request = apiRequestFactory.createGet(
 *         url = "https://api.com/get",
 *         options = requestOptions
 *     )
 *     return networkCache.get(request.url)?.let { stripeResponse ->
 *         ApiResponseJsonParser().parse(stripeResponse.responseJson())
 *     } ?: fetchStripeModel(
 *         apiRequest = request,
 *         jsonParser = ApiResponseJsonParser()
 *     ) { stripeResponse ->
 *         stripeResponse?.let { response ->
 *         networkCache.put(request.url, response)
 *     }
 * }
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
interface NetworkCache {
    fun put(key: String, data: StripeResponse<String>)
    fun get(key: String): StripeResponse<String>?
    fun remove(key: String)
}

@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
class DefaultNetworkCache(
    context: Context,
    private val timeProvider: TimeProvider = DefaultTimeProvider(),
    private val diskLruCache: LruDiskCache = NetworkDiskCache(
        context = context,
        cacheFolder = "stripe_network_cache"
    )
): NetworkCache {
    override fun put(key: String, data: StripeResponse<String>) {
        data.timestamp = timeProvider.currentTimeInMillis()
        val canCache = data
            .getCacheControls(timeProvider.currentTimeInMillis())
            .all { it.canCache() }
        if (canCache) {
            diskLruCache.put(key, Json.encodeToString(data))
        }
    }

    override fun get(key: String): StripeResponse<String>? {
        val snapshot = diskLruCache.get(key) ?: return null
        return try {
            val data = Json.decodeFromString<StripeResponse<String>>(snapshot)
            val validCachedValues = data
                .getCacheControls(timeProvider.currentTimeInMillis())
                .all { it.isValid() }
            if (validCachedValues) {
                data
            } else {
                null
            }
        } catch (ex: Exception) {
            null
        }
    }

    override fun remove(key: String) {
        diskLruCache.remove(key)
    }
}
