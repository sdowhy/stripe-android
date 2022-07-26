package com.stripe.android.paymentsheet.addresselement

/**
 * Callback that is invoked when a [AddressLauncherResult] is available.
 */
interface AddressLauncherResultCallback {
    fun onAddressLauncherResult(addressLauncherResult: AddressLauncherResult)
}
