package com.stripe.android.features

import androidx.annotation.RestrictTo
import com.stripe.android.BuildConfig
import com.stripe.android.features.FeatureAvailability.Disabled
import com.stripe.android.features.FeatureAvailability.Release

@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
data class FeatureFlag(
    val name: String,
    val availability: FeatureAvailability
) {
    var overrideAvailability: FeatureAvailability? = null
}

@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
enum class FeatureAvailability {
    Disabled, Debug, Release
}

val FeatureFlag.isEnabled: Boolean
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    get() = (BuildConfig.DEBUG || (overrideAvailability ?: availability) == Release) && (overrideAvailability ?: availability) != Disabled
