package com.stripe.android.ui.core.address.autocomplete

import android.app.Application
import android.os.Bundle
import androidx.lifecycle.AbstractSavedStateViewModelFactory
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.savedstate.SavedStateRegistryOwner
import com.stripe.android.core.injection.DUMMY_INJECTOR_KEY
import com.stripe.android.core.injection.Injectable
import com.stripe.android.core.injection.injectWithFallback
import com.stripe.android.model.Address
import com.stripe.android.ui.core.R
import com.stripe.android.ui.core.address.autocomplete.di.AddressAutocompleteViewModelSubcomponent
import com.stripe.android.ui.core.address.autocomplete.di.DaggerAddressAutocompleteComponent
import com.stripe.android.ui.core.elements.SimpleTextFieldConfig
import com.stripe.android.ui.core.elements.SimpleTextFieldController
import com.stripe.android.ui.core.elements.TextFieldIcon
import com.stripe.android.ui.core.address.autocomplete.model.AddressComponent
import com.stripe.android.ui.core.address.autocomplete.model.AutocompletePrediction
import com.stripe.android.ui.core.address.autocomplete.model.Place
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Provider

class AddressAutocompleteViewModel @Inject constructor(
    application: Application,
    private val args: AddressAutocompleteContract.Args,
) : AndroidViewModel(application) {
    private val client = PlacesClientProxy.create(application, args.googlePlacesApiKey)
    private var searchJob: Job? = null

    val predictions = MutableStateFlow(listOf<AutocompletePrediction>())
    val loading = MutableStateFlow(false)
    val addressResult = MutableStateFlow<Result<Address?>?>(null)
    val autocompleteController = SimpleTextFieldController(
        SimpleTextFieldConfig(
            label = R.string.address_label_address,
            trailingIcon = MutableStateFlow(
                TextFieldIcon.Trailing(
                    idRes = R.drawable.stripe_ic_clear,
                    isTintable = true,
                    onClick = { clearQuery() }
                )
            )
        )
    )

    init {
        startWatching(
            viewModelScope,
            autocompleteController.formFieldValue
                .map { it.takeIf { it.isComplete }?.value }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), "")
        )
    }

    fun selectPrediction(prediction: AutocompletePrediction) {
        viewModelScope.launch {
            loading.value = true
            client.fetchPlace(
                placeId = prediction.placeId
            ).fold(
                onSuccess = {
                    it.place.addressComponents?.let { addressComponents ->
                        loading.value = false
                        addressResult.value = Result.success(addressComponents.toAddress())
                    }
                },
                onFailure = {
                    loading.value = false
                    addressResult.value = Result.failure(it)
                }
            )
        }
    }

    private fun startWatching(
        coroutineScope: CoroutineScope,
        addressFlow: StateFlow<String?>
    ) {
        coroutineScope.launch {
            addressFlow.collect { query ->
                searchJob?.cancel()
                searchJob = launch {
                    delay(SEARCH_DEBOUNCE_MS)
                    client.findAutocompletePredictions(
                        query = query,
                        country = args.country,
                        limit = AUTO_COMPLETE_LIMIT
                    ).fold(
                        onSuccess = {
                            loading.value = false
                            predictions.value = it.autocompletePredictions
                        },
                        onFailure = {
                            loading.value = false
                            addressResult.value = Result.failure(it)
                        }
                    )
                }
            }
        }
    }

    private fun List<AddressComponent>.toAddress(): Address {
        val filter: (Place.Type) -> String? = { type ->
            this.find { it.types.contains(type.value) }?.name
        }

        val line1 = listOfNotNull(filter(Place.Type.STREET_NUMBER), filter(Place.Type.ROUTE))
            .joinToString(" ")
            .ifBlank { null }
        val city = filter(Place.Type.LOCALITY) ?: filter(Place.Type.SUBLOCALITY)
        val state = filter(Place.Type.ADMINISTRATIVE_AREA_LEVEL_1)
        val country = filter(Place.Type.COUNTRY)
        val postalCode = filter(Place.Type.POSTAL_CODE)

        return Address.Builder()
            .setLine1(line1)
            .setCity(city)
            .setState(state)
            .setCountry(country)
            .setPostalCode(postalCode)
            .build()
    }

    private fun clearQuery() {
        autocompleteController.onRawValueChange("")
    }

    class Factory(
        private val applicationSupplier: () -> Application,
        private val argsSupplier: () -> AddressAutocompleteContract.Args,
        owner: SavedStateRegistryOwner,
        defaultArgs: Bundle? = null
    ) : AbstractSavedStateViewModelFactory(owner, defaultArgs),
        Injectable<Factory.FallbackInitializeParam> {

        data class FallbackInitializeParam(
            val application: Application,
        )

        @Inject
        lateinit var subComponentBuilderProvider:
            Provider<AddressAutocompleteViewModelSubcomponent.Builder>

        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(
            key: String,
            modelClass: Class<T>,
            savedStateHandle: SavedStateHandle
        ): T {
            val args = argsSupplier()

            injectWithFallback(args.injectorKey,
                FallbackInitializeParam(applicationSupplier())
            )

            return subComponentBuilderProvider.get()
                .configuration(args)
                .application(applicationSupplier())
                .savedStateHandle(savedStateHandle)
                .build().viewModel as T
        }

        override fun fallbackInitialize(arg: FallbackInitializeParam) {
            DaggerAddressAutocompleteComponent
                .builder()
                .injectorKey(DUMMY_INJECTOR_KEY)
                .build().inject(this)
        }
    }

    private companion object {
        const val SEARCH_DEBOUNCE_MS = 1000L
        const val AUTO_COMPLETE_LIMIT = 3
    }
}