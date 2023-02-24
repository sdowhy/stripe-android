package com.stripe.android.paymentsheet.viewmodels

import android.content.Context
import com.stripe.android.paymentsheet.PaymentSheet
import com.stripe.android.paymentsheet.R
import com.stripe.android.paymentsheet.model.PaymentSelection
import com.stripe.android.paymentsheet.navigation.PaymentSheetScreen
import com.stripe.android.paymentsheet.ui.PrimaryButton
import com.stripe.android.ui.core.Amount
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

internal class PrimaryButtonUiStateMapper(
    private val context: Context,
    private val config: PaymentSheet.Configuration?,
    private val isProcessingPayment: Boolean,
    private val onClick: () -> Unit,
) {

    fun forCompleteFlow(
        currentScreenFlow: Flow<PaymentSheetScreen>,
        buttonsEnabledFlow: Flow<Boolean>,
        amountFlow: Flow<Amount?>,
        selectionFlow: Flow<PaymentSelection?>,
        usBankPrimaryButtonUiStateFlow: Flow<PrimaryButton.UIState?>,
        linkPrimaryButtonUiStateFlow: Flow<PrimaryButton.UIState?>,
    ): Flow<PrimaryButton.UIState?> {
        return combine(
            currentScreenFlow,
            buttonsEnabledFlow,
            amountFlow,
            selectionFlow,
            usBankPrimaryButtonUiStateFlow,
            linkPrimaryButtonUiStateFlow,
        ) { items ->
            val screen = items[0] as PaymentSheetScreen
            val buttonsEnabled = items[1] as Boolean
            val amount = items[2] as? Amount
            val selection = items[3] as? PaymentSelection
            val usBankButtonState = items[4] as? PrimaryButton.UIState
            val linkButtonState = items[5] as? PrimaryButton.UIState

            usBankButtonState ?: linkButtonState ?: PrimaryButton.UIState(
                label = buyButtonLabel(amount),
                onClick = onClick,
                enabled = buttonsEnabled && selection != null,
            ).takeIf { screen.showsBuyButton }
        }
    }

    fun forCustomFlow(
        currentScreenFlow: Flow<PaymentSheetScreen>,
        buttonsEnabledFlow: Flow<Boolean>,
        selectionFlow: Flow<PaymentSelection?>,
        usBankPrimaryButtonUiStateFlow: Flow<PrimaryButton.UIState?>,
        linkPrimaryButtonUiStateFlow: Flow<PrimaryButton.UIState?>,
    ): Flow<PrimaryButton.UIState?> {
        return combine(
            currentScreenFlow,
            buttonsEnabledFlow,
            selectionFlow,
            usBankPrimaryButtonUiStateFlow,
            linkPrimaryButtonUiStateFlow,
        ) { screen, buttonsEnabled, selection, usBankButtonState, linkButtonState ->
            usBankButtonState ?: linkButtonState ?: PrimaryButton.UIState(
                label = continueButtonLabel(),
                onClick = onClick,
                enabled = buttonsEnabled && selection != null,
            ).takeIf { screen.showsContinueButton }
        }
    }

    private fun buyButtonLabel(amount: Amount?): String {
        return if (config?.primaryButtonLabel != null) {
            config.primaryButtonLabel
        } else if (isProcessingPayment) {
            val payFallback = context.getString(R.string.stripe_paymentsheet_pay_button_label)
            amount?.buildPayButtonLabel(context.resources) ?: payFallback
        } else {
            context.getString(R.string.stripe_setup_button_label)
        }
    }

    private fun continueButtonLabel(): String {
        val customLabel = config?.primaryButtonLabel
        return customLabel ?: context.getString(R.string.stripe_continue_button_label)
    }
}
