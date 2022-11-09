package com.stripe.android.core.networking.cache

import com.stripe.android.core.networking.StripeResponse

internal interface CacheControlDirective {
    fun canCache(): Boolean
    fun isValid(): Boolean

    data class MaxAge(
        private val currentTimeInMilliseconds: Long,
        private val maxAgeInMilliseconds: Long
    ) : CacheControlDirective {
        override fun canCache(): Boolean = true
        override fun isValid(): Boolean {
            return currentTimeInMilliseconds <= maxAgeInMilliseconds
        }
    }

    data class SharedMaxAge(
        private val currentTimeInMilliseconds: Long,
        private val maxAgeInMilliseconds: Long
    ) : CacheControlDirective {
        override fun canCache(): Boolean = true
        override fun isValid(): Boolean {
            return currentTimeInMilliseconds <= maxAgeInMilliseconds
        }
    }

    object Public : CacheControlDirective {
        override fun canCache(): Boolean = true
        override fun isValid(): Boolean = true
    }

    object Shared : CacheControlDirective {
        override fun canCache(): Boolean = true
        override fun isValid(): Boolean = true
    }

    object Private : CacheControlDirective {
        override fun canCache(): Boolean = true
        override fun isValid(): Boolean = true
    }

    object NoCache : CacheControlDirective {
        override fun canCache(): Boolean = true
        override fun isValid(): Boolean = false
    }

    object NoStore : CacheControlDirective {
        override fun canCache(): Boolean = false
        override fun isValid(): Boolean = false
    }

    data class MustRevalidate(
        private val currentTimeInMilliseconds: Long,
        private val maxAgeInMilliseconds: Long
    ) : CacheControlDirective {
        override fun canCache(): Boolean = true
        override fun isValid(): Boolean {
            return currentTimeInMilliseconds <= maxAgeInMilliseconds
        }
    }

    data class ProxyRevalidate(
        private val currentTimeInMilliseconds: Long,
        private val maxAgeInMilliseconds: Long
    ) : CacheControlDirective {
        override fun canCache(): Boolean = true
        override fun isValid(): Boolean {
            return currentTimeInMilliseconds <= maxAgeInMilliseconds
        }
    }

    companion object {
        const val HEADER_KEY_VALUE_DELIMITER = "="
        const val CACHE_CONTROL_HEADER = "cache-control"
        const val CACHE_CONTROL_DIRECTIVE_MAX_AGE = "max-age"
        const val CACHE_CONTROL_DIRECTIVE_S_MAX_AGE = "s-maxage"
        const val CACHE_CONTROL_DIRECTIVE_PUBLIC = "public"
        const val CACHE_CONTROL_DIRECTIVE_SHARED = "shared"
        const val CACHE_CONTROL_DIRECTIVE_PRIVATE = "private"
        const val CACHE_CONTROL_DIRECTIVE_NO_CACHE = "no-cache"
        const val CACHE_CONTROL_DIRECTIVE_MUST_REVALIDATE = "must-revalidate"
        const val CACHE_CONTROL_DIRECTIVE_PROXY_REVALIDATE = "proxy-revalidate"
        const val CACHE_CONTROL_DIRECTIVE_NO_STORE = "no-store"
    }
}

internal fun StripeResponse<String>.getCacheControls(
    currentTimeInMilliseconds: Long
): List<CacheControlDirective> {
    val cacheControlKeyValues = getHeaderValue(CacheControlDirective.CACHE_CONTROL_HEADER)
        ?.firstOrNull()
        ?.replace(Regex("\\s+"), "")
        ?.split(",")
        ?.map {
            val pair = it.split(CacheControlDirective.HEADER_KEY_VALUE_DELIMITER)
            Pair(pair.first(), pair.getOrNull(1))
        }
    val maxAge = cacheControlKeyValues
        ?.find {
            it.first.contains(CacheControlDirective.CACHE_CONTROL_DIRECTIVE_MAX_AGE)
        }
        ?.second
        ?.toLongOrNull()
    return cacheControlKeyValues?.mapNotNull { keyValue ->
        when (keyValue.first) {
            CacheControlDirective.CACHE_CONTROL_DIRECTIVE_MAX_AGE -> {
                if (maxAge != null) {
                    CacheControlDirective.MaxAge(
                        maxAgeInMilliseconds = timestamp + (maxAge * 1000),
                        currentTimeInMilliseconds = currentTimeInMilliseconds
                    )
                } else {
                    null
                }
            }
            CacheControlDirective.CACHE_CONTROL_DIRECTIVE_S_MAX_AGE -> {
                if (maxAge != null) {
                    CacheControlDirective.SharedMaxAge(
                        maxAgeInMilliseconds = timestamp + (maxAge * 1000),
                        currentTimeInMilliseconds = currentTimeInMilliseconds
                    )
                } else {
                    null
                }
            }
            CacheControlDirective.CACHE_CONTROL_DIRECTIVE_PUBLIC -> {
                CacheControlDirective.Public
            }
            CacheControlDirective.CACHE_CONTROL_DIRECTIVE_SHARED -> {
                CacheControlDirective.Shared
            }
            CacheControlDirective.CACHE_CONTROL_DIRECTIVE_PRIVATE -> {
                CacheControlDirective.Private
            }
            CacheControlDirective.CACHE_CONTROL_DIRECTIVE_NO_CACHE -> {
                CacheControlDirective.NoCache
            }
            CacheControlDirective.CACHE_CONTROL_DIRECTIVE_NO_STORE -> {
                CacheControlDirective.NoStore
            }
            CacheControlDirective.CACHE_CONTROL_DIRECTIVE_MUST_REVALIDATE -> {
                if (maxAge != null) {
                    CacheControlDirective.MustRevalidate(
                        maxAgeInMilliseconds = timestamp + (maxAge * 1000),
                        currentTimeInMilliseconds = currentTimeInMilliseconds
                    )
                } else {
                    null
                }
            }
            CacheControlDirective.CACHE_CONTROL_DIRECTIVE_PROXY_REVALIDATE -> {
                if (maxAge != null) {
                    CacheControlDirective.ProxyRevalidate(
                        maxAgeInMilliseconds = timestamp + (maxAge * 1000),
                        currentTimeInMilliseconds = currentTimeInMilliseconds
                    )
                } else {
                    null
                }
            }
            else -> {
                null
            }
        }
    } ?: listOf()
}
