package com.stripe.android.paymentsheet.viewmodels

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.google.common.truth.Truth.assertThat
import com.stripe.android.model.PaymentMethod
import com.stripe.android.model.PaymentMethodFixtures
import com.stripe.android.paymentsheet.PaymentOptionsItem
import com.stripe.android.paymentsheet.PaymentOptionsState
import com.stripe.android.paymentsheet.model.PaymentSelection
import com.stripe.android.paymentsheet.model.SavedSelection
import com.stripe.android.utils.TestUtils.idleLooper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runBlockingTest
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.AfterTest
import kotlin.test.BeforeTest

@OptIn(ExperimentalCoroutinesApi::class)
class PaymentOptionsStateMapperTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private val paymentMethodsFlow = MutableStateFlow<List<PaymentMethod>>(listOf())
    private val initialSelectionFlow = MutableStateFlow<SavedSelection>(SavedSelection.None)
    private val currentSelectionFlow = MutableStateFlow<PaymentSelection?>(null)
    private val isGooglePayReadyFlow = MutableStateFlow<Boolean>(false)
    private val isLinkEnabledFlow = MutableStateFlow<Boolean>(false)

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun cleanup() {
        Dispatchers.resetMain()
    }

    @Test
    fun `Doesn't include Google Pay and Link in payment flow`() = runTest(
        UnconfinedTestDispatcher()
    ) {
        val mapper = PaymentOptionsStateMapper(
            paymentMethods = paymentMethodsFlow,
            initialSelection = initialSelectionFlow,
            currentSelection = currentSelectionFlow,
            isGooglePayReady = isGooglePayReadyFlow,
            isLinkEnabled = isLinkEnabledFlow,
            isNotPaymentFlow = false,
        )

        var paymentOptionsState: PaymentOptionsState? = null

        val job = launch {
            mapper().collect {
                paymentOptionsState = it
            }
        }

        val cards = PaymentMethodFixtures.createCards(2)
        paymentMethodsFlow.value = cards
        initialSelectionFlow.value = SavedSelection.PaymentMethod(id = cards.first().id!!)
        isGooglePayReadyFlow.value = true
        isLinkEnabledFlow.value = true

        assertThat(paymentOptionsState?.items).containsNoneOf(
            PaymentOptionsItem.GooglePay,
            PaymentOptionsItem.Link,
        )

        job.cancel()
    }

    @Test
    fun `Removing selected payment option results in saved selection being selected`() = runTest(
        UnconfinedTestDispatcher()
    ) {
        val mapper = PaymentOptionsStateMapper(
            paymentMethods = paymentMethodsFlow,
            initialSelection = initialSelectionFlow,
            currentSelection = currentSelectionFlow,
            isGooglePayReady = isGooglePayReadyFlow,
            isLinkEnabled = isLinkEnabledFlow,
            isNotPaymentFlow = true,
        )

        var paymentOptionsState: PaymentOptionsState? = null

        val job = launch {
            mapper().collect {
                paymentOptionsState = it
            }
        }

        val cards = PaymentMethodFixtures.createCards(2)
        val selectedPaymentMethod = PaymentSelection.Saved(paymentMethod = cards.last())
        paymentMethodsFlow.value = cards
        initialSelectionFlow.value = SavedSelection.Link
        currentSelectionFlow.value = selectedPaymentMethod
        isGooglePayReadyFlow.value = true
        isLinkEnabledFlow.value = true

        assertThat(paymentOptionsState).isNotNull()
        assertThat(paymentOptionsState?.selectedItem).isEqualTo(
            PaymentOptionsItem.SavedPaymentMethod(selectedPaymentMethod.paymentMethod)
        )

        // Remove the currently selected payment option
        paymentMethodsFlow.value = cards - selectedPaymentMethod.paymentMethod
        currentSelectionFlow.value = null

        assertThat(paymentOptionsState?.selectedItem).isEqualTo(PaymentOptionsItem.Link)

        job.cancel()
    }

    @Test
    fun `Removing selected payment option results in first available option if no saved selection`() =
        runTest(UnconfinedTestDispatcher()
    ) {
        val mapper = PaymentOptionsStateMapper(
            paymentMethods = paymentMethodsFlow,
            initialSelection = initialSelectionFlow,
            currentSelection = currentSelectionFlow,
            isGooglePayReady = isGooglePayReadyFlow,
            isLinkEnabled = isLinkEnabledFlow,
            isNotPaymentFlow = true,
        )

        var paymentOptionsState: PaymentOptionsState? = null

        val job = launch {
            mapper().collect {
                paymentOptionsState = it
            }
        }

        val cards = PaymentMethodFixtures.createCards(2)
        val selectedPaymentMethod = PaymentSelection.Saved(paymentMethod = cards.last())
        paymentMethodsFlow.value = cards
        initialSelectionFlow.value = SavedSelection.None
        currentSelectionFlow.value = selectedPaymentMethod
        isGooglePayReadyFlow.value = true
        isLinkEnabledFlow.value = true

        assertThat(paymentOptionsState).isNotNull()
        assertThat(paymentOptionsState?.selectedItem).isEqualTo(
            PaymentOptionsItem.SavedPaymentMethod(selectedPaymentMethod.paymentMethod)
        )

        // Remove the currently selected payment option
        paymentMethodsFlow.value = cards - selectedPaymentMethod.paymentMethod
        currentSelectionFlow.value = null

        assertThat(paymentOptionsState?.selectedItem).isEqualTo(PaymentOptionsItem.GooglePay)

        job.cancel()
    }
}
