package com.stripe.android.ui.core.address.autocomplete.di

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import com.stripe.android.ui.core.address.autocomplete.AddressAutocompleteContract
import com.stripe.android.ui.core.address.autocomplete.AddressAutocompleteViewModel
import dagger.BindsInstance
import dagger.Subcomponent

@Subcomponent
interface AddressAutocompleteViewModelSubcomponent {
    val viewModel: AddressAutocompleteViewModel

    @Subcomponent.Builder
    interface Builder {
        @BindsInstance
        fun application(application: Application): Builder

        @BindsInstance
        fun savedStateHandle(handle: SavedStateHandle): Builder

        @BindsInstance
        fun configuration(configuration: AddressAutocompleteContract.Args): Builder

        fun build(): AddressAutocompleteViewModelSubcomponent
    }
}
