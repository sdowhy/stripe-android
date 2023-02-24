package com.stripe.android.paymentsheet

import androidx.annotation.RestrictTo
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
fun interface ConfirmCallback {

    /**
     * 🚧 Under construction 🚧
     * The callback to implement to retrieve the intent client secret in the deferred flow.
     *
     * @param paymentMethodId the payment method ID to create the intent with
     *
     * @return a [Result] which contains the intent client secret and whether or not the
     * intent was confirmed on the integrators server.
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    suspend fun onRetrieveConfirmResponse(paymentMethodId: String): Result

    /**
     * 🚧 Under construction 🚧
     * The model for the confirmation response from the integrators servers.
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    sealed interface Result {

        /**
         * 🚧 Under construction 🚧
         * @param clientSecret the client secret returned by the integrators server
         */
        @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
        data class Success(val clientSecret: String) : Result

        /**
         * 🚧 Under construction 🚧
         * @param error the error returned while retrieving the confirmation response
         */
        @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
        data class Failure(val error: String) : Result
    }
}

internal fun interface LegacyConfirmCallback : ConfirmCallback {
    override suspend fun onRetrieveConfirmResponse(paymentMethodId: String): ConfirmCallback.Result {
        return suspendCoroutine { continuation ->
            onRetrieveConfirmResponse(
                paymentMethodId = paymentMethodId,
                onResult = {
                    val result = ConfirmCallback.Result.Success(it)
                    continuation.resume(result)
                    result
                },
                onError = {
                    val result = ConfirmCallback.Result.Failure(it.toString())
                    continuation.resumeWithException(it)
                    result
                }
            )
        }
    }

    fun onRetrieveConfirmResponse(
        paymentMethodId: String,
        onResult: (String) -> ConfirmCallback.Result,
        onError: (Throwable) -> ConfirmCallback.Result
    )
}
