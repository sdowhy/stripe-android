package com.stripe.android.ui.core.address.autocomplete.di

import android.app.Application
import android.content.Context
import com.stripe.android.BuildConfig
import com.stripe.android.PaymentConfiguration
import com.stripe.android.core.injection.ENABLE_LOGGING
import com.stripe.android.core.injection.PUBLISHABLE_KEY
import com.stripe.android.payments.core.injection.PRODUCT_USAGE
import dagger.Module
import dagger.Provides
import javax.inject.Named

@Module(
    subcomponents = [AddressAutocompleteViewModelSubcomponent::class]
)
internal class AddressAutocompleteViewModelModule {
    @Provides
    fun providesAppContext(application: Application): Context = application

    @Provides
    @Named(PUBLISHABLE_KEY)
    fun providePublishableKey(
        appContext: Context
    ): () -> String = { PaymentConfiguration.getInstance(appContext).publishableKey }

    @Provides
    @Named(PRODUCT_USAGE)
    fun providesProductUsage(): Set<String> = emptySet()

    @Provides
    @Named(ENABLE_LOGGING)
    fun providesEnableLogging(): Boolean = BuildConfig.DEBUG
}
