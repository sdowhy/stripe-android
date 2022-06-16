package com.stripe.android.paymentsheet.shipping

import android.content.Context
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.AutocompleteSessionToken
import com.google.android.libraries.places.api.model.TypeFilter
import com.google.android.libraries.places.api.net.FetchPlaceRequest
import com.google.android.libraries.places.api.net.PlacesClient
import com.stripe.android.BuildConfig
import com.stripe.android.paymentsheet.R
import com.stripe.android.ui.core.isSystemDarkTheme
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

interface PlacesClientProxy {
    suspend fun findAutocompletePredictions(
        query: String?,
        country: String,
        limit: Int
    ) : Result<FindAutocompletePredictionsResponse>

    suspend fun fetchPlace(
        placeId: String
    ) : Result<FetchPlaceResponse>

    fun getPlacesPoweredByGoogleDrawable(context: Context): Int?

    companion object {
        fun create(
            context: Context,
            googlePlacesApiKey: String
        ): PlacesClientProxy {
            val isPlacesAvailable = DefaultIsPlacesAvailable()
            return if (isPlacesAvailable()) {
                DefaultPlacesClientProxy(
                    context,
                    googlePlacesApiKey
                )
            } else {
                UnsupportedPlacesClientProxy()
            }
        }
    }
}

private class DefaultPlacesClientProxy(
    context: Context,
    googlePlacesApiKey: String,
    clientFactory: (Context) -> PlacesClient = { Places.createClient(it) }
) : PlacesClientProxy {
    init {
        Places.initialize(context, googlePlacesApiKey)
    }

    private val client = clientFactory(context)
    private val token = AutocompleteSessionToken.newInstance()

    override suspend fun findAutocompletePredictions(
        query: String?,
        country: String,
        limit: Int
    ) : Result<FindAutocompletePredictionsResponse> {
        val result = suspendCoroutine<Result<FindAutocompletePredictionsResponse>> { continuation ->
            val request = com.google.android.libraries.places.api.net
                .FindAutocompletePredictionsRequest
                .builder()
                .setSessionToken(token)
                .setQuery(query)
                .setCountry(country)
                .setTypeFilter(TypeFilter.ADDRESS)
                .build()
            client.findAutocompletePredictions(request).addOnSuccessListener { response ->
                continuation.resume(
                    Result.success(
                        FindAutocompletePredictionsResponse(
                            autocompletePredictions = response.autocompletePredictions.map {
                                AutocompletePrediction(
                                    primaryText = it.getPrimaryText(null).toString(),
                                    secondaryText = it.getSecondaryText(null).toString(),
                                    placeId = it.placeId
                                )
                            }.take(limit)
                        )
                    )
                )
            }
            .addOnFailureListener {
                continuation.resume(
                    Result.failure(
                        Exception("Could not find autocomplete predictions: ${it.message}")
                    )
                )
            }
        }

        return result
    }

    override suspend fun fetchPlace(
        placeId: String
    ) : Result<FetchPlaceResponse> {
        val result = suspendCoroutine<Result<FetchPlaceResponse>> { continuation ->
            client.fetchPlace(
                FetchPlaceRequest.newInstance(
                    placeId,
                    listOf(
                        com.google.android.libraries.places.api.model.Place.Field.ADDRESS_COMPONENTS
                    )
                )
            ).addOnSuccessListener { response ->
                continuation.resume(
                    Result.success(
                        FetchPlaceResponse(
                            Place(
                                response.place.addressComponents?.asList()?.map {
                                    AddressComponent(
                                        name = it.name,
                                        types = it.types
                                    )
                                }
                            )
                        )
                    )
                )
            }.addOnFailureListener {
                continuation.resume(
                    Result.failure(
                        Exception("Could not fetch place: ${it.message}")
                    )
                )
            }
        }

        return result
    }

    override fun getPlacesPoweredByGoogleDrawable(context: Context): Int? {
        return if (context.isSystemDarkTheme()) {
            R.drawable.places_powered_by_google_dark
        } else {
            R.drawable.places_powered_by_google_light
        }
    }
}

private class UnsupportedPlacesClientProxy: PlacesClientProxy {
    override suspend fun findAutocompletePredictions(
        query: String?,
        country: String,
        limit: Int
    ): Result<FindAutocompletePredictionsResponse> {
        val exception = IllegalStateException(
            "Missing Google Places dependency, please add it to your apps build.gradle"
        )
        if (BuildConfig.DEBUG) {
            throw exception
        }
        return Result.failure(exception)
    }

    override suspend fun fetchPlace(placeId: String): Result<FetchPlaceResponse> {
        val exception = IllegalStateException(
            "Missing Google Places dependency, please add it to your apps build.gradle"
        )
        if (BuildConfig.DEBUG) {
            throw exception
        }
        return Result.failure(exception)
    }

    override fun getPlacesPoweredByGoogleDrawable(context: Context): Int? {
        if (BuildConfig.DEBUG) {
            throw IllegalStateException(
                "Missing Google Places dependency, please add it to your apps build.gradle"
            )
        }
        return null
    }
}

internal interface IsPlacesAvailable {
    operator fun invoke(): Boolean
}

private class DefaultIsPlacesAvailable : IsPlacesAvailable {
    override fun invoke(): Boolean {
        return try {
            Class.forName("com.google.android.libraries.places.api.Places")
            true
        } catch (_: Exception) {
            false
        }
    }
}

data class FindAutocompletePredictionsResponse(
    val autocompletePredictions: List<AutocompletePrediction>
)

data class AutocompletePrediction(
    val primaryText: String,
    val secondaryText: String,
    val placeId: String
)

data class FetchPlaceResponse(
    val place: Place
)

data class Place(
    val addressComponents: List<AddressComponent>?
) {
    enum class Type(val value: String) {
        STREET_NUMBER("street_number"),
        ROUTE("route"),
        LOCALITY("locality"),
        ADMINISTRATIVE_AREA_LEVEL_1("administrative_area_level_1"),
        COUNTRY("country"),
        POSTAL_CODE("postal_code")
    }
}

data class AddressComponent(
    val name: String,
    val types: List<String>
)
