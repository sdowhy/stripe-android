package com.stripe.android.core.networking.cache

import android.content.Context
import android.util.Log
import androidx.annotation.RestrictTo
import com.jakewharton.disklrucache.DiskLruCache
import com.stripe.android.core.BuildConfig
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.charset.Charset

@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
interface LruDiskCache {
    fun put(key: String, data: String)
    fun get(key: String): String?
    fun remove(key: String)
}

internal class NetworkDiskCache(
    context: Context,
    cacheFolder: String,
    maxSizeBytes: Long = 10L * 1024 * 1024, // 10MB
) : LruDiskCache {
    private lateinit var diskLruCache: DiskLruCache

    init {
        try {
            diskLruCache = DiskLruCache.open(
                /* directory = */ getDiskCacheDir(context, cacheFolder),
                /* appVersion = */ APP_VERSION,
                /* valueCount = */ VALUE_COUNT,
                /* maxSize = */ maxSizeBytes
            )
        } catch (e: IOException) {
            Log.e(TAG, "error opening cache", e)
        }
    }

    override fun put(key: String, data: String) {
        var editor: DiskLruCache.Editor? = null
        val hashedKey = key.toKey()

        try {
            editor = diskLruCache.edit(hashedKey)
            if (editor == null) return

            if (writeToFile(data, editor)) {
                diskLruCache.flush()
                editor.commit()
                debug("data put on disk cache $hashedKey")
            } else {
                editor.abort()
                Log.e(TAG, "ERROR on: data put on disk cache $hashedKey")
            }
        } catch (e: IOException) {
            Log.e(TAG, "ERROR on: data put on disk cache $hashedKey")
            kotlin.runCatching { editor?.abort() }
        }
    }

    override fun get(key: String): String? {
        var data: String? = null
        var snapshot: DiskLruCache.Snapshot? = null
        val hashedKey = key.toKey()
        try {
            snapshot = diskLruCache.get(hashedKey)
            if (snapshot == null) {
                debug("data not in cache: $hashedKey")
                return null
            }
            val inputStream: InputStream = snapshot.getInputStream(0)
            val buffIn = BufferedInputStream(inputStream, IO_BUFFER_SIZE)
            data = buffIn.readBytes().toString(Charset.defaultCharset())
        } catch (e: IOException) {
            Log.e(TAG, "error getting data from cache", e)
        } finally {
            snapshot?.close()
        }
        debug(
            if (data == null) {
                "data not in cache: $hashedKey"
            } else {
                "data read from disk $hashedKey"
            }
        )
        return data
    }

    override fun remove(key: String) {
        val hashedKey = key.toKey()
        diskLruCache.remove(hashedKey)
    }

    private fun debug(s: String) {
        if (BuildConfig.DEBUG) Log.d(TAG, s)
    }

    /**
     * [DiskLruCache] just accepts keys matching [a-z0-9_-]{1,64}. Keys
     * are hashed to ensure pattern match.
     */
    private fun String.toKey(): String = hashCode().toString()

    @Suppress("SENSELESS_COMPARISON")
    @Throws(IOException::class, FileNotFoundException::class)
    private fun writeToFile(data: String, editor: DiskLruCache.Editor): Boolean {
        var out: OutputStream? = null
        return try {
            out = BufferedOutputStream(editor.newOutputStream(0), IO_BUFFER_SIZE)
            out.write(data.toByteArray())
            true
        } catch(ex: Exception) {
            Log.e(TAG, "error writing data to cache", ex)
            false
        } finally {
            out?.close()
        }
    }

    private fun getDiskCacheDir(context: Context, uniqueName: String): File {
        val cachePath: String = context.cacheDir.path
        return File(cachePath + File.separator + uniqueName)
    }

    private companion object {
        private const val TAG = "StripeDiskCache"
        private const val APP_VERSION = 1
        private const val VALUE_COUNT = 1
        private const val IO_BUFFER_SIZE = 8 * 1024
    }
}