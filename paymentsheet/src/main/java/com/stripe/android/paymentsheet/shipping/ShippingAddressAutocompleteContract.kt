package com.stripe.android.paymentsheet.shipping

import android.content.Context
import android.content.Intent
import androidx.activity.result.contract.ActivityResultContract
import androidx.annotation.ColorInt
import com.stripe.android.core.injection.InjectorKey
import com.stripe.android.view.ActivityStarter
import kotlinx.parcelize.Parcelize

internal class ShippingAddressAutocompleteContract : ActivityResultContract<ShippingAddressAutocompleteContract.Args, ShippingAddressAutocompleteResult?>() {
    override fun createIntent(
        context: Context,
        input: Args
    ): Intent {
        return Intent(context, ShippingAddressAutocompleteActivity::class.java)
            .putExtra(EXTRA_ARGS, input)
    }

    override fun parseResult(
        resultCode: Int,
        intent: Intent?
    ): ShippingAddressAutocompleteResult? {
        return ShippingAddressAutocompleteResult.fromIntent(intent)
    }

    /**
     * Arguments for launching [ShippingAddressAutocompleteActivity]
     *
     * @param country The country to constrain the autocomplete suggestions, must be passed as a
     *                two-character, ISO 3166-1 Alpha-2 compatible country code.
     *                https://en.wikipedia.org/wiki/ISO_3166-1_alpha-2
     * @param googlePlacesApiKey The merchants Google Places API key
     */
    @Parcelize
    internal data class Args(
        val country: String,
        val googlePlacesApiKey: String,
        @ColorInt val statusBarColor: Int?,
        @InjectorKey val injectorKey: String
    ) : ActivityStarter.Args {
        internal companion object {
            internal fun fromIntent(intent: Intent): Args? {
                return intent.getParcelableExtra(EXTRA_ARGS)
            }
        }
    }

    internal companion object {
        internal const val EXTRA_ARGS = "extra_activity_args"
    }
}