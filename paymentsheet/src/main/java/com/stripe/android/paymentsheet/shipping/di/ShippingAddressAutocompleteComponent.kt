package com.stripe.android.paymentsheet.shipping.di

import android.app.Application
import com.stripe.android.core.injection.CoroutineContextModule
import com.stripe.android.core.injection.InjectorKey
import com.stripe.android.core.injection.LoggingModule
import com.stripe.android.paymentsheet.shipping.ShippingAddressAutocompleteViewModel
import dagger.BindsInstance
import dagger.Component
import javax.inject.Singleton

@Singleton
@Component(
    modules = [
        CoroutineContextModule::class,
        ShippingAddressAutocompleteViewModelModule::class,
        LoggingModule::class
    ]
)
internal interface ShippingAddressAutocompleteComponent {
    fun inject(factory: ShippingAddressAutocompleteViewModel.Factory)

    @Component.Builder
    interface Builder {
        @BindsInstance
        fun injectorKey(@InjectorKey injectorKey: String): Builder

        fun build(): ShippingAddressAutocompleteComponent
    }
}
