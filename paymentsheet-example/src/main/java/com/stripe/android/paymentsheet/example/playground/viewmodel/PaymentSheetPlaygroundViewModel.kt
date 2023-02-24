package com.stripe.android.paymentsheet.example.playground.viewmodel

import android.app.Application
import androidx.annotation.Keep
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.asFlow
import com.github.kittinunf.fuel.Fuel
import com.github.kittinunf.fuel.core.extensions.jsonBody
import com.github.kittinunf.result.Result
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.stripe.android.PaymentConfiguration
import com.stripe.android.core.model.CountryCode
import com.stripe.android.paymentsheet.PaymentSheet
import com.stripe.android.paymentsheet.example.playground.model.CheckoutCurrency
import com.stripe.android.paymentsheet.example.playground.model.CheckoutCustomer
import com.stripe.android.paymentsheet.example.playground.model.CheckoutMode
import com.stripe.android.paymentsheet.example.playground.model.CheckoutRequest
import com.stripe.android.paymentsheet.example.playground.model.CheckoutResponse
import com.stripe.android.paymentsheet.example.playground.model.InitializationType
import com.stripe.android.paymentsheet.example.playground.model.SavedToggles
import com.stripe.android.paymentsheet.example.playground.model.Toggle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.serialization.Serializable
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

class PaymentSheetPlaygroundViewModel(
    application: Application
) : AndroidViewModel(application) {
    val inProgress = MutableLiveData<Boolean>()
    val status = MutableLiveData<String>()

    val customerConfig = MutableLiveData<PaymentSheet.CustomerConfiguration?>(null)
    val clientSecret = MutableLiveData<String?>(null)

    val initializationType = MutableStateFlow(InitializationType.Normal)
    val paymentMethodTypes = MutableStateFlow<List<String>>(emptyList())

    val intentConfigurationMode = MutableStateFlow<PaymentSheet.IntentConfiguration.Mode?>(null)

    val readyToCheckout = combine(
        initializationType,
        clientSecret.asFlow(),
    ) { type, secret ->
        when (type) {
            InitializationType.Normal -> secret != null
            InitializationType.Deferred -> true
        }
    }

    val checkoutMode = MutableStateFlow(CheckoutMode.Payment)
    var temporaryCustomerId: String? = null

    private val sharedPreferencesName = "playgroundToggles"

    fun storeToggleState(
        initializationType: String,
        customer: String,
        link: Boolean,
        googlePay: Boolean,
        currency: String,
        merchantCountryCode: String,
        mode: String,
        shipping: String,
        setDefaultBillingAddress: Boolean,
        setAutomaticPaymentMethods: Boolean,
        setDelayedPaymentMethods: Boolean,
    ) {
        val sharedPreferences = getApplication<Application>().getSharedPreferences(
            sharedPreferencesName,
            AppCompatActivity.MODE_PRIVATE
        )

        sharedPreferences.edit {
            putString(Toggle.Initialization.key, initializationType)
            putString(Toggle.Customer.key, customer)
            putBoolean(Toggle.Link.key, link)
            putBoolean(Toggle.GooglePay.key, googlePay)
            putString(Toggle.Currency.key, currency)
            putString(Toggle.MerchantCountryCode.key, merchantCountryCode)
            putString(Toggle.Mode.key, mode)
            putString(Toggle.ShippingAddress.key, shipping)
            putBoolean(Toggle.SetDefaultBillingAddress.key, setDefaultBillingAddress)
            putBoolean(Toggle.SetAutomaticPaymentMethods.key, setAutomaticPaymentMethods)
            putBoolean(Toggle.SetDelayedPaymentMethods.key, setDelayedPaymentMethods)
        }
    }

    fun getSavedToggleState(): SavedToggles {
        val sharedPreferences = getApplication<Application>().getSharedPreferences(
            sharedPreferencesName,
            AppCompatActivity.MODE_PRIVATE
        )

        val initialization = sharedPreferences.getString(
            Toggle.Initialization.key,
            Toggle.Initialization.default.toString(),
        )
        val customer = sharedPreferences.getString(
            Toggle.Customer.key,
            Toggle.Customer.default.toString()
        )
        val googlePay = sharedPreferences.getBoolean(
            Toggle.GooglePay.key,
            Toggle.GooglePay.default as Boolean
        )
        val currency = sharedPreferences.getString(
            Toggle.Currency.key,
            Toggle.Currency.default.toString()
        )
        val merchantCountryCode = sharedPreferences.getString(
            Toggle.MerchantCountryCode.key,
            Toggle.MerchantCountryCode.default.toString()
        )
        val mode = sharedPreferences.getString(
            Toggle.Mode.key,
            Toggle.Mode.default.toString()
        )
        val shippingAddress = sharedPreferences.getString(
            Toggle.ShippingAddress.key,
            Toggle.ShippingAddress.default as String
        )
        val setAutomaticPaymentMethods = sharedPreferences.getBoolean(
            Toggle.SetAutomaticPaymentMethods.key,
            Toggle.SetAutomaticPaymentMethods.default as Boolean
        )
        val setDelayedPaymentMethods = sharedPreferences.getBoolean(
            Toggle.SetDelayedPaymentMethods.key,
            Toggle.SetDelayedPaymentMethods.default as Boolean
        )
        val setDefaultBillingAddress = sharedPreferences.getBoolean(
            Toggle.SetDefaultBillingAddress.key,
            Toggle.SetDefaultBillingAddress.default as Boolean
        )
        val setLink = sharedPreferences.getBoolean(
            Toggle.Link.key,
            Toggle.Link.default as Boolean
        )

        return SavedToggles(
            initialization = initialization.toString(),
            customer= customer.toString(),
            googlePay = googlePay,
            currency = currency.toString(),
            merchantCountryCode = merchantCountryCode.toString(),
            mode = mode.toString(),
            shippingAddress = shippingAddress!!,
            setAutomaticPaymentMethods = setAutomaticPaymentMethods,
            setDelayedPaymentMethods = setDelayedPaymentMethods,
            setDefaultBillingAddress = setDefaultBillingAddress,
            link = setLink
        )

    }

    /**
     * Calls the backend to prepare for checkout. The server creates a new Payment or Setup Intent
     * that will be confirmed on the client using Payment Sheet.
     */
    fun prepareCheckout(
        initializationType: InitializationType,
        customer: CheckoutCustomer,
        currency: CheckoutCurrency,
        merchantCountry: CountryCode,
        mode: CheckoutMode,
        linkEnabled: Boolean,
        setShippingAddress: Boolean,
        setAutomaticPaymentMethod: Boolean,
        backendUrl: String,
        supportedPaymentMethods: List<String>?
    ) {
        customerConfig.value = null
        clientSecret.value = null

        inProgress.postValue(true)

        val requestBody = CheckoutRequest(
            initialization = initializationType.value,
            customer = customer.value,
            currency = currency.value.lowercase(),
            mode = mode.value,
            set_shipping_address = setShippingAddress,
            automatic_payment_methods = setAutomaticPaymentMethod,
            use_link = linkEnabled,
            merchant_country_code = merchantCountry.value,
            supported_payment_methods = supportedPaymentMethods
        )

        Fuel.post(backendUrl + "checkout")
            .jsonBody(Gson().toJson(requestBody))
            .responseString { _, _, result ->
                when (result) {
                    is Result.Failure -> {
                        status.postValue("Preparing checkout failed:\n${result.getException().message}")
                    }
                    is Result.Success -> {
                        val checkoutResponse = Gson().fromJson(
                            result.get(),
                            CheckoutResponse::class.java
                        )
                        checkoutMode.value = mode
                        temporaryCustomerId = if (customer == CheckoutCustomer.New) {
                            checkoutResponse.customerId
                        } else {
                            null
                        }

                        // Init PaymentConfiguration with the publishable key returned from the backend,
                        // which will be used on all Stripe API calls
                        PaymentConfiguration.init(getApplication(), checkoutResponse.publishableKey)

                        customerConfig.postValue(checkoutResponse.makeCustomerConfig())
                        clientSecret.postValue(checkoutResponse.intentClientSecret)
                        paymentMethodTypes.value = checkoutResponse.paymentMethodTypes.orEmpty()
                    }
                }
                inProgress.postValue(false)
            }
    }

    suspend fun createPaymentIntent(
        paymentMethodId: String?,
        amount: Long?,
        currency: String?,
        confirm: Boolean
    ): ConfirmResponse {
        return suspendCoroutine { continuation ->
            /**
             * The amount and currency should be set on the server side to avoid malicious behavior
             */
            Fuel.post("https://tills-playground-for-decoupling.glitch.me/create_payment_intent")
                .jsonBody("""
                {
                    "payment_method_id": "$paymentMethodId",
                    "amount": $amount,
                    "currency": "$currency",
                    "confirm": $confirm
                }
            """.trimIndent())
                .responseString { _, _, result ->
                    when (result) {
                        is Result.Failure -> {
                            status.postValue(
                                "Preparing checkout failed:\n${result.getException().message}"
                            )
                        }
                        is Result.Success -> {
                            val response = Gson().fromJson(result.get(), ConfirmResponse::class.java)
                            continuation.resume(response)
                        }
                    }
                }
        }
    }

    @Serializable
    @Keep
    data class ConfirmResponse(
        @SerializedName("client_secret")
        val clientSecret: String,
        @SerializedName("confirmed")
        val confirmed: Boolean
    )
}
