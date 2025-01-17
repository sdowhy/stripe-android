package com.stripe.android.paymentsheet.viewmodels

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.stripe.android.model.PaymentMethod
import com.stripe.android.model.PaymentMethodFixtures
import com.stripe.android.paymentsheet.PaymentOptionsItem
import com.stripe.android.paymentsheet.model.PaymentSelection
import com.stripe.android.paymentsheet.model.SavedSelection
import com.stripe.android.paymentsheet.state.GooglePayState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

class PaymentOptionsStateMapperTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private val paymentMethodsFlow = MutableStateFlow<List<PaymentMethod>?>(null)
    private val initialSelectionFlow = MutableStateFlow<SavedSelection?>(null)
    private val currentSelectionFlow = MutableStateFlow<PaymentSelection?>(null)
    private val googlePayStateFlow = MutableStateFlow<GooglePayState>(GooglePayState.Indeterminate)
    private val isLinkEnabledFlow = MutableStateFlow<Boolean?>(null)

    @Test
    fun `Only emits value if required flows have emitted values`() = runTest {
        val mapper = PaymentOptionsStateMapper(
            paymentMethods = paymentMethodsFlow,
            initialSelection = initialSelectionFlow,
            currentSelection = currentSelectionFlow,
            googlePayState = googlePayStateFlow,
            isLinkEnabled = isLinkEnabledFlow,
            isNotPaymentFlow = true,
            nameProvider = { it!! },
        )

        mapper().test {
            assertThat(awaitItem()).isNull()

            paymentMethodsFlow.value = PaymentMethodFixtures.createCards(2)
            assertThat(awaitItem()).isNull()

            initialSelectionFlow.value = SavedSelection.GooglePay
            assertThat(awaitItem()).isNull()

            googlePayStateFlow.value = GooglePayState.Available
            assertThat(awaitItem()).isNull()

            isLinkEnabledFlow.value = true
            assertThat(awaitItem()).isNotNull()
        }
    }

    @Test
    fun `Doesn't include Google Pay and Link in payment flow`() = runTest {
        val mapper = PaymentOptionsStateMapper(
            paymentMethods = paymentMethodsFlow,
            initialSelection = initialSelectionFlow,
            currentSelection = currentSelectionFlow,
            googlePayState = googlePayStateFlow,
            isLinkEnabled = isLinkEnabledFlow,
            isNotPaymentFlow = false,
            nameProvider = { it!! },
        )

        mapper().test {
            val cards = PaymentMethodFixtures.createCards(2)
            paymentMethodsFlow.value = cards
            initialSelectionFlow.value = SavedSelection.PaymentMethod(id = cards.first().id!!)
            googlePayStateFlow.value = GooglePayState.Available
            isLinkEnabledFlow.value = true

            assertThat(expectMostRecentItem()?.items).containsNoneOf(
                PaymentOptionsItem.GooglePay,
                PaymentOptionsItem.Link,
            )
        }
    }

    @Test
    fun `Removing selected payment option results in saved selection being selected`() = runTest {
        val mapper = PaymentOptionsStateMapper(
            paymentMethods = paymentMethodsFlow,
            initialSelection = initialSelectionFlow,
            currentSelection = currentSelectionFlow,
            googlePayState = googlePayStateFlow,
            isLinkEnabled = isLinkEnabledFlow,
            isNotPaymentFlow = true,
            nameProvider = { it!! },
        )

        mapper().test {
            val cards = PaymentMethodFixtures.createCards(2)
            val selectedPaymentMethod = PaymentSelection.Saved(paymentMethod = cards.last())
            paymentMethodsFlow.value = cards
            initialSelectionFlow.value = SavedSelection.Link
            currentSelectionFlow.value = selectedPaymentMethod
            googlePayStateFlow.value = GooglePayState.Available
            isLinkEnabledFlow.value = true

            assertThat(expectMostRecentItem()?.selectedItem).isEqualTo(
                PaymentOptionsItem.SavedPaymentMethod(
                    displayName = "card",
                    paymentMethod = selectedPaymentMethod.paymentMethod,
                )
            )

            // Remove the currently selected payment option
            paymentMethodsFlow.value = cards - selectedPaymentMethod.paymentMethod
            currentSelectionFlow.value = null

            assertThat(expectMostRecentItem()?.selectedItem).isEqualTo(PaymentOptionsItem.Link)
        }
    }

    @Test
    fun `Removing selected payment option results in first available option if no saved selection`() = runTest {
        val mapper = PaymentOptionsStateMapper(
            paymentMethods = paymentMethodsFlow,
            initialSelection = initialSelectionFlow,
            currentSelection = currentSelectionFlow,
            googlePayState = googlePayStateFlow,
            isLinkEnabled = isLinkEnabledFlow,
            isNotPaymentFlow = true,
            nameProvider = { it!! },
        )

        mapper().test {
            val cards = PaymentMethodFixtures.createCards(2)
            val selectedPaymentMethod = PaymentSelection.Saved(paymentMethod = cards.last())
            paymentMethodsFlow.value = cards
            initialSelectionFlow.value = SavedSelection.None
            currentSelectionFlow.value = selectedPaymentMethod
            googlePayStateFlow.value = GooglePayState.Available
            isLinkEnabledFlow.value = true

            assertThat(expectMostRecentItem()?.selectedItem).isEqualTo(
                PaymentOptionsItem.SavedPaymentMethod(
                    displayName = "card",
                    paymentMethod = selectedPaymentMethod.paymentMethod,
                )
            )

            // Remove the currently selected payment option
            paymentMethodsFlow.value = cards - selectedPaymentMethod.paymentMethod
            currentSelectionFlow.value = null

            assertThat(expectMostRecentItem()?.selectedItem).isEqualTo(PaymentOptionsItem.GooglePay)
        }
    }
}
