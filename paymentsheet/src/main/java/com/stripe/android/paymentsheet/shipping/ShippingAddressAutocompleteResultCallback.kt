package com.stripe.android.paymentsheet.shipping

/**
 * Callback that is invoked when the customer's [ShippingAddress] changes.
 */
fun interface ShippingAddressAutocompleteResultCallback {

    /**
     * @param shippingAddress The new [ShippingAddress]. If null, the customer has not yet
     * selected a [ShippingAddress].
     */
    fun onShippingAddress(shippingAddress: ShippingAddress?)
}
