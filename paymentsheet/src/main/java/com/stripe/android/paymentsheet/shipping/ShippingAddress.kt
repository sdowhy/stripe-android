package com.stripe.android.paymentsheet.shipping

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class ShippingAddress(
    val country: String,
    val addressLine1: String,
    val city: String,
    val state: String,
    val zip: String
) : Parcelable
