package com.stripe.android.paymentsheet

import com.stripe.android.PaymentConfiguration
import com.stripe.android.core.networking.ApiRequest
import com.stripe.android.model.StripeIntent
import com.stripe.android.networking.StripeRepository
import com.stripe.android.paymentsheet.model.ClientSecret
import com.stripe.android.paymentsheet.model.PaymentIntentClientSecret
import com.stripe.android.paymentsheet.model.PaymentSelection
import com.stripe.android.paymentsheet.model.SetupIntentClientSecret
import com.stripe.android.paymentsheet.repositories.ElementsSessionRepository
import dagger.Lazy
import javax.inject.Inject

internal class DeferredIntentRepository @Inject constructor(
    private val lazyPaymentConfig: Lazy<PaymentConfiguration>,
    private val stripeRepository: StripeRepository,
    private val elementsSessionRepository: ElementsSessionRepository
) {
    sealed interface Result {
        class Error(val error: String) : Result
        class Success(
            val clientSecret: ClientSecret,
            val stripeIntent: StripeIntent
        ) : Result
    }

    suspend fun get(
        paymentSelection: PaymentSelection?,
        mode: PaymentSheet.InitializationMode
    ): Result {
        val paymentMethodId = retrievePaymentMethodId(paymentSelection)
        val confirmResponse = retrieveConfirmResponseForDeferredIntent(
            paymentMethodId = paymentMethodId
        )
        val clientSecret = when (confirmResponse) {
            is ConfirmCallback.Result.Failure -> {
                error(confirmResponse.error)
            }
            is ConfirmCallback.Result.Success -> {
                if (mode.isProcessingPayment) {
                    PaymentIntentClientSecret(confirmResponse.clientSecret)
                } else {
                    SetupIntentClientSecret(confirmResponse.clientSecret)
                }
            }
        }

        val stripeIntent = retrieveDeferredIntent(mode, clientSecret)

        return Result.Success(
            clientSecret = clientSecret,
            stripeIntent = stripeIntent
        )
    }

    private suspend fun retrieveDeferredIntent(
        mode: PaymentSheet.InitializationMode,
        clientSecret: ClientSecret
    ): StripeIntent {
        return elementsSessionRepository.get(
            if (mode.isProcessingPayment) {
                PaymentSheet.InitializationMode.PaymentIntent(clientSecret.value)
            } else {
                PaymentSheet.InitializationMode.SetupIntent(clientSecret.value)
            }
        ).stripeIntent
    }

    private suspend fun retrievePaymentMethodId(
        paymentSelection: PaymentSelection?
    ): String {
        return paymentSelection?.let {
            when (paymentSelection) {
                is PaymentSelection.Saved -> {
                    paymentSelection.paymentMethod.id
                }
                is PaymentSelection.New -> {
                    stripeRepository.createPaymentMethod(
                        paymentSelection.paymentMethodCreateParams,
                        ApiRequest.Options(
                            apiKey = lazyPaymentConfig.get().publishableKey,
                            stripeAccount = lazyPaymentConfig.get().stripeAccountId
                        )
                    )?.id
                }
                else -> {
                    null
                }
            }
        } ?: error(
            "The DeferredIntent flow requires a valid payment method ID"
        )
    }

    private suspend fun retrieveConfirmResponseForDeferredIntent(
        paymentMethodId: String
    ): ConfirmCallback.Result {
        return PaymentSheet.retrieveConfirmCallback?.onRetrieveConfirmResponse(
            paymentMethodId
        ) ?: error(
            "The DeferredIntent initialization mode requires the ClientSecretCallback to be " +
                "implemented. Implement the callback using the PaymentSheet initializer."
        )
    }
}
