package com.stripe.android.link

import android.app.Activity
import android.os.Parcelable
import androidx.annotation.RestrictTo
import kotlinx.parcelize.Parcelize

@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
sealed class LinkActivityResult(
    val resultCode: Int
) : Parcelable {

    abstract val launchedDirectly: Boolean

    /**
     * Indicates that the flow was completed successfully and the Stripe Intent was confirmed.
     */
    @Parcelize
    data class Completed(
        override val launchedDirectly: Boolean,
    ) : LinkActivityResult(Activity.RESULT_OK)

    /**
     * The user cancelled the Link flow without completing it.
     */
    @Parcelize
    data class Canceled(
        val reason: Reason,
        override val launchedDirectly: Boolean,
    ) : LinkActivityResult(Activity.RESULT_CANCELED) {
        enum class Reason {
            BackPressed,
            PayAnotherWay,
            LoggedOut
        }
    }

    /**
     * Something went wrong. See [error] for more information.
     */
    @Parcelize
    data class Failed(
        val error: Throwable,
        override val launchedDirectly: Boolean,
    ) : LinkActivityResult(Activity.RESULT_CANCELED)
}
