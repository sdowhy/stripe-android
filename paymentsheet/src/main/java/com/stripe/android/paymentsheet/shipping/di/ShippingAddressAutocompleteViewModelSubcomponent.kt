package com.stripe.android.paymentsheet.shipping.di

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import com.stripe.android.paymentsheet.shipping.ShippingAddressAutocompleteContract
import com.stripe.android.paymentsheet.shipping.ShippingAddressAutocompleteViewModel
import dagger.BindsInstance
import dagger.Subcomponent

@Subcomponent
internal interface ShippingAddressAutocompleteViewModelSubcomponent {
    val viewModel: ShippingAddressAutocompleteViewModel

    @Subcomponent.Builder
    interface Builder {
        @BindsInstance
        fun application(application: Application): Builder

        @BindsInstance
        fun savedStateHandle(handle: SavedStateHandle): Builder

        @BindsInstance
        fun configuration(configuration: ShippingAddressAutocompleteContract.Args): Builder

        fun build(): ShippingAddressAutocompleteViewModelSubcomponent
    }
}
