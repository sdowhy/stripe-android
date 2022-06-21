package com.stripe.android.ui.core.address.autocomplete.di

import com.stripe.android.core.injection.CoroutineContextModule
import com.stripe.android.core.injection.InjectorKey
import com.stripe.android.core.injection.LoggingModule
import com.stripe.android.ui.core.address.autocomplete.AddressAutocompleteViewModel
import dagger.BindsInstance
import dagger.Component
import javax.inject.Singleton

@Singleton
@Component(
    modules = [
        CoroutineContextModule::class,
        AddressAutocompleteViewModelModule::class,
        LoggingModule::class
    ]
)
internal interface AddressAutocompleteComponent {
    fun inject(factory: AddressAutocompleteViewModel.Factory)

    @Component.Builder
    interface Builder {
        @BindsInstance
        fun injectorKey(@InjectorKey injectorKey: String): Builder

        fun build(): AddressAutocompleteComponent
    }
}
