package com.stripe.android.link

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Parcelable
import androidx.activity.result.contract.ActivityResultContract
import androidx.annotation.RestrictTo
import androidx.core.os.bundleOf
import com.stripe.android.core.injection.InjectorKey
import com.stripe.android.link.LinkActivityContract.Args
import com.stripe.android.link.LinkActivityContract.Companion.EXTRA_RESULT
import com.stripe.android.link.LinkActivityResult.Canceled.Reason.BackPressed
import com.stripe.android.model.PaymentMethodCreateParams
import com.stripe.android.view.ActivityStarter
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize

@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
class LinkActivityContract : ActivityResultContract<Args, LinkActivityResult>() {

    override fun createIntent(context: Context, input: Args): Intent {
        return Intent(context, LinkActivity::class.java).putExtra(EXTRA_ARGS, input)
    }

    override fun parseResult(resultCode: Int, intent: Intent?): LinkActivityResult {
        val linkResult = intent?.getParcelableExtra<LinkActivityResult>(EXTRA_RESULT)
        return linkResult ?: LinkActivityResult.Canceled(reason = BackPressed, launchedDirectly = false)
    }

    /**
     * Arguments for launching [LinkActivity] to confirm a payment with Link.
     *
     * @param configuration Configuration values
     * @param prefilledCardParams The payment method information prefilled by the user.
     * @param injectionParams Parameters needed to perform dependency injection.
     *                        If null, a new dependency graph will be created.
     */
    @Parcelize
    data class Args internal constructor(
        internal val configuration: LinkPaymentLauncher.Configuration,
        internal val launchedDirectly: Boolean,
        internal val prefilledCardParams: PaymentMethodCreateParams? = null,
        internal val injectionParams: InjectionParams? = null
    ) : ActivityStarter.Args {
        @IgnoredOnParcel
        internal val stripeIntent = configuration.stripeIntent

        @IgnoredOnParcel
        internal val merchantName = configuration.merchantName

        @IgnoredOnParcel
        internal val customerEmail = configuration.customerEmail

        @IgnoredOnParcel
        internal val customerPhone = configuration.customerPhone

        @IgnoredOnParcel
        internal val customerName = configuration.customerName

        @IgnoredOnParcel
        internal val shippingValues = configuration.shippingValues

        companion object {
            internal fun fromIntent(intent: Intent): Args? {
                return intent.getParcelableExtra(EXTRA_ARGS)
            }
        }

        @Parcelize
        internal data class InjectionParams(
            @InjectorKey val injectorKey: String,
            val productUsage: Set<String>,
            val enableLogging: Boolean,
            val publishableKey: String,
            val stripeAccountId: String?
        ) : Parcelable
    }

    companion object {
        const val EXTRA_ARGS = "com.stripe.android.link.LinkActivityContract.extra_args"
        const val EXTRA_RESULT = "com.stripe.android.link.LinkActivityContract.extra_result"
    }
}

internal fun LinkActivityResult.toBundle(): Bundle {
    return bundleOf(EXTRA_RESULT to this)
}
