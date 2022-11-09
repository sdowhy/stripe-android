package com.stripe.android.core.injection

import androidx.annotation.RestrictTo
import androidx.core.os.LocaleListCompat
import com.stripe.android.core.Logger
import com.stripe.android.core.utils.DefaultTimeProvider
import com.stripe.android.core.utils.TimeProvider
import dagger.Binds
import dagger.Module
import dagger.Provides
import javax.inject.Named
import javax.inject.Singleton

@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
@Module
class CoreCommonModule {
    @Provides
    @Singleton
    fun provideLogger(@Named(ENABLE_LOGGING) enableLogging: Boolean) =
        Logger.getInstance(enableLogging)

    @Provides
    @Singleton
    fun provideLocale() =
        LocaleListCompat.getAdjustedDefault().takeUnless { it.isEmpty }?.get(0)

    @Provides
    @Singleton
    fun bindsTimeProvider(impl: DefaultTimeProvider): TimeProvider = impl
}
