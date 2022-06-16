package com.stripe.android.paymentsheet.shipping

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
import com.stripe.android.paymentsheet.R
import com.stripe.android.paymentsheet.shipping.di.DaggerShippingAddressAutocompleteComponent
import com.stripe.android.paymentsheet.shipping.di.ShippingAddressAutocompleteViewModelSubcomponent
import com.stripe.android.ui.core.elements.SimpleTextFieldConfig
import com.stripe.android.ui.core.elements.SimpleTextFieldController
import com.stripe.android.ui.core.elements.TextFieldIcon
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.lang.NullPointerException
import javax.inject.Inject
import javax.inject.Provider

internal class ShippingAddressAutocompleteViewModel @Inject constructor(
    application: Application,
    private val args: ShippingAddressAutocompleteContract.Args,
) : AndroidViewModel(application) {
    private val client = PlacesClientProxy.create(application, args.googlePlacesApiKey)
    private var searchJob: Job? = null

    val predictions = MutableStateFlow(listOf<AutocompletePrediction>())
    val loading = MutableStateFlow(false)
    val shippingAddressResult = MutableStateFlow<Result<ShippingAddress?>?>(null)
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

    fun getPlacesPoweredByGoogleDrawable(): Int? {
        return client.getPlacesPoweredByGoogleDrawable(getApplication())
    }

    fun selectPrediction(prediction: AutocompletePrediction) {
        viewModelScope.launch {
            loading.value = true
            client.fetchPlace(
                placeId = prediction.placeId
            ).fold(
                onSuccess = {
                    it.place.addressComponents?.let { addressComponents ->
                        val address = try {
                            val (
                                addressLine1,
                                locality,
                                administrativeAreaLevel1,
                                country,
                                postalCode
                            ) = addressComponents.getComponents(
                                listOf(Place.Type.STREET_NUMBER, Place.Type.ROUTE),
                                listOf(Place.Type.LOCALITY),
                                listOf(Place.Type.ADMINISTRATIVE_AREA_LEVEL_1),
                                listOf(Place.Type.COUNTRY),
                                listOf(Place.Type.POSTAL_CODE)
                            )

                            ShippingAddress(
                                country = country,
                                addressLine1 = addressLine1,
                                city = locality,
                                state = administrativeAreaLevel1,
                                zip = postalCode
                            )
                        } catch (ex: Exception) {
                            null
                        }

                        loading.value = false
                        shippingAddressResult.value = Result.success(address)
                    }
                },
                onFailure = {
                    loading.value = false
                    shippingAddressResult.value = Result.failure(it)
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
                            shippingAddressResult.value = Result.failure(it)
                        }
                    )
                }
            }
        }
    }

    @Throws(NullPointerException::class)
    private fun List<AddressComponent>.getComponents(vararg types: List<Place.Type>): List<String> {
        return types.map { type ->
            type.joinToString(" ") {
                find { component ->
                    component.types.contains(it.value)
                }!!.name
            }
        }
    }

    private fun clearQuery() {
        autocompleteController.onRawValueChange("")
    }

    internal class Factory(
        private val applicationSupplier: () -> Application,
        private val argsSupplier: () -> ShippingAddressAutocompleteContract.Args,
        owner: SavedStateRegistryOwner,
        defaultArgs: Bundle? = null
    ) : AbstractSavedStateViewModelFactory(owner, defaultArgs),
        Injectable<Factory.FallbackInitializeParam> {

        internal data class FallbackInitializeParam(
            val application: Application,
        )

        @Inject
        lateinit var subComponentBuilderProvider:
            Provider<ShippingAddressAutocompleteViewModelSubcomponent.Builder>

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
            DaggerShippingAddressAutocompleteComponent
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