package com.stripe.android.core.utils

import android.os.SystemClock
import javax.inject.Inject

interface TimeProvider {
    fun currentTimeInMillis(): Long
}

class DefaultTimeProvider @Inject constructor() : TimeProvider {
    override fun currentTimeInMillis(): Long {
        return SystemClock.elapsedRealtime()
    }
}