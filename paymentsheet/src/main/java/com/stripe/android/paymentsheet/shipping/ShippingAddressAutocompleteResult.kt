package com.stripe.android.paymentsheet.shipping

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.Parcelable
import androidx.core.os.bundleOf
import com.stripe.android.view.ActivityStarter
import kotlinx.parcelize.Parcelize

sealed class ShippingAddressAutocompleteResult(
    val resultCode: Int,
    open val shippingAddress: ShippingAddress? = null
) : Parcelable {
    fun toBundle(): Bundle {
        return bundleOf(EXTRA_RESULT to this)
    }

    @Parcelize
    data class Succeeded(
        override val shippingAddress: ShippingAddress? = null
    ) : ShippingAddressAutocompleteResult(Activity.RESULT_OK, shippingAddress)

    @Parcelize
    data class Failed(
        val error: Throwable,
        override val shippingAddress: ShippingAddress? = null
    ) : ShippingAddressAutocompleteResult(Activity.RESULT_CANCELED, shippingAddress)

    @Parcelize
    data class Canceled(
        val mostRecentError: Throwable?,
        override val shippingAddress: ShippingAddress? = null
    ) : ShippingAddressAutocompleteResult(Activity.RESULT_CANCELED, shippingAddress)

    internal companion object {
        private const val EXTRA_RESULT = ActivityStarter.Result.EXTRA

        @JvmSynthetic
        internal fun fromIntent(intent: Intent?): ShippingAddressAutocompleteResult? {
            return intent?.getParcelableExtra(EXTRA_RESULT)
        }
    }
}
