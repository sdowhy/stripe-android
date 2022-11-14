package com.stripe.example.activity

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.PlaceholderVerticalAlign
import com.stripe.android.core.networking.StripeResponse
import com.stripe.android.core.networking.cache.DefaultNetworkCache
import com.stripe.android.ui.core.R
import com.stripe.android.uicore.image.StripeImageLoader
import com.stripe.android.uicore.text.EmbeddableImage
import com.stripe.android.uicore.text.Html
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException

class StripeImageActivity : AppCompatActivity() {
    private val LocalStripeImageLoader = staticCompositionLocalOf<StripeImageLoader> {
        error("No ImageLoader provided")
    }

    private val imageLoader by lazy {
        StripeImageLoader(
            context = this,
            memoryCache = null,
            diskCache = null
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val httpClient = OkHttpClient.Builder().build()
        val cache = DefaultNetworkCache(this)
        val request = Request.Builder().get().url("https://berry-chisel-spinosaurus.glitch.me/content").build()
        super.onCreate(savedInstanceState)
        setContent {
            val text = remember { mutableStateOf("") }
            httpClient.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                }

                override fun onResponse(call: Call, response: Response) {
                    val result = response.body?.string()
                    cache.get(request.url.toString())?.let {
                        it.body?.let {
                            text.value = it
                        }
                    } ?: run {
                        result?.let {
                            text.value = it
                            val headers = response.headers.toMultimap()
                                .mapValues { it.value.joinToString(", ") }
                                .mapValues { listOf(it.value) }
                            cache.put(
                                request.url.toString(),
                                StripeResponse(
                                    code = 200,
                                    body = it,
                                    headers = headers
                                )
                            )
                        }
                    }
                }
            })
            CompositionLocalProvider(LocalStripeImageLoader provides imageLoader) {
                Column {
                    Text(text = text.value)
                    Html(
                        imageLoader = mapOf(
                            "affirm" to EmbeddableImage.Drawable(
                                R.drawable.stripe_ic_affirm_logo,
                                R.string.stripe_paymentsheet_payment_method_affirm
                            )
                        ),
                        html = """
                            HTML with single local image
                            <br/>
                            Local image <img src="affirm"/>.
                            <br/>
                        """.trimIndent(),
                        color = MaterialTheme.colors.onSurface,
                        style = MaterialTheme.typography.body1
                    )
                    Html(
                        imageLoader = mapOf(
                            "affirm" to EmbeddableImage.Drawable(
                                R.drawable.stripe_ic_affirm_logo,
                                R.string.stripe_paymentsheet_payment_method_affirm
                            )
                        ),
                        html = """
                            HTML with local and remote images
                            <br/>
                            Local image <img src="affirm"/>.
                            <br/>
                            Unknown remote image <img src="https://qa-b.stripecdn.com/unknown_image.png"/>
                            <br/>
                            Remote images 
                            <img src="https://qa-b.stripecdn.com/payment-method-messaging-statics-srv/assets/afterpay_logo_black.png"/>
                            <img src="https://qa-b.stripecdn.com/payment-method-messaging-statics-srv/assets/klarna_logo_black.png"/>
                            <br/>
                            Paragraph text <b>bold text</b>. ⓘ
                        """.trimIndent(),
                        color = MaterialTheme.colors.onSurface,
                        style = MaterialTheme.typography.body1,
                        imageAlign = PlaceholderVerticalAlign.TextCenter
                    )
                }
            }
        }
    }
}