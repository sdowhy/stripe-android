package com.stripe.android.paymentsheet

import androidx.activity.result.ActivityResultCaller
import androidx.annotation.VisibleForTesting
import androidx.lifecycle.SavedStateHandle
import com.stripe.android.link.LinkActivityResult
import com.stripe.android.link.LinkPaymentDetails
import com.stripe.android.link.LinkPaymentLauncher
import com.stripe.android.link.model.AccountStatus
import com.stripe.android.link.ui.inline.UserInput
import com.stripe.android.model.PaymentMethodCreateParams
import com.stripe.android.payments.paymentlauncher.PaymentResult
import com.stripe.android.paymentsheet.analytics.EventReporter
import com.stripe.android.paymentsheet.model.PaymentSelection
import com.stripe.android.paymentsheet.state.LinkState
import com.stripe.android.paymentsheet.viewmodels.BaseSheetViewModel.Companion.LINK_CONFIGURATION
import com.stripe.android.paymentsheet.viewmodels.BaseSheetViewModel.Companion.SAVE_PROCESSING
import com.stripe.android.paymentsheet.viewmodels.BaseSheetViewModel.Companion.SAVE_SELECTION
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Provider

// TODO(linkextraction): Long term this should probably be singleton.
internal class LinkHandler @Inject constructor(
    val linkLauncher: LinkPaymentLauncher,
    private val savedStateHandle: SavedStateHandle,
    private val eventReporter: EventReporter,
    private val paymentSelectionRepositoryProvider: Provider<PaymentSelectionRepository>,
) {
    // TODO(linkextraction): Should these states be split into 2 iterations? One for inline, one for other?
    sealed class ProcessingState {
        object Ready : ProcessingState()

        object Launched : ProcessingState()

        object Started : ProcessingState()

        class PaymentDetailsCollected(val details: LinkPaymentDetails.New?) : ProcessingState()

        class Error(val message: String?) : ProcessingState()

        object Cancelled : ProcessingState()

        object Complete : ProcessingState()

        class CompletedWithPaymentResult(val result: PaymentResult) : ProcessingState()
    }

    private val _processingState =
        MutableSharedFlow<ProcessingState>(replay = 1, extraBufferCapacity = 5)
    val processingState: Flow<ProcessingState> = _processingState

    var linkInlineSelection = MutableStateFlow<PaymentSelection.New.LinkInline?>(null)

    private var launchedLinkDirectly: Boolean = false

    private val _showLinkVerificationDialog = MutableStateFlow(false)
    val showLinkVerificationDialog: StateFlow<Boolean> = _showLinkVerificationDialog

    @VisibleForTesting
    val _isLinkEnabled = MutableStateFlow(false)
    val isLinkEnabled: StateFlow<Boolean> = _isLinkEnabled

    private val _activeLinkSession = MutableStateFlow(false)
    val activeLinkSession: StateFlow<Boolean> = _activeLinkSession

    private val _linkConfiguration = MutableStateFlow(
        savedStateHandle.get<LinkPaymentLauncher.Configuration>(LINK_CONFIGURATION)
    )
    val linkConfiguration: StateFlow<LinkPaymentLauncher.Configuration?> = _linkConfiguration

    private val linkVerificationChannel = Channel<Boolean>(capacity = 1)

    fun registerFromActivity(activityResultCaller: ActivityResultCaller) {
        linkLauncher.register(
            activityResultCaller,
            ::onLinkActivityResult,
        )
    }

    fun unregisterFromActivity() {
        linkLauncher.unregister()
    }

    fun setupLink(scope: CoroutineScope, state: LinkState?) {
        _isLinkEnabled.value = state != null
        _activeLinkSession.value = state?.loginState == LinkState.LoginState.LoggedIn

        if (state == null) return

        _linkConfiguration.value = state.configuration

        when (state.loginState) {
            LinkState.LoginState.LoggedIn -> {
                launchLink(state.configuration, launchedDirectly = true)
            }
            LinkState.LoginState.NeedsVerification -> {
                scope.launch {
                    setupLinkWithVerification(state.configuration)
                }
            }
            LinkState.LoginState.LoggedOut -> {
                // Nothing to do here
            }
        }
        if (state.isReadyForUse) {
            // If account exists, select Link by default
            savedStateHandle[SAVE_SELECTION] = PaymentSelection.Link
        }
    }

    private suspend fun requestLinkVerification(): Boolean {
        _showLinkVerificationDialog.value = true
        return linkVerificationChannel.receive()
    }

    fun handleLinkVerificationResult(success: Boolean) {
        _showLinkVerificationDialog.value = false
        _activeLinkSession.value = success
        linkVerificationChannel.trySend(success)
    }

    suspend fun payWithLinkInline(
        configuration: LinkPaymentLauncher.Configuration,
        userInput: UserInput?
    ) {
        val selection = paymentSelectionRepositoryProvider.get().paymentSelection
        (selection as? PaymentSelection.New.Card?)?.paymentMethodCreateParams?.let { params ->
            savedStateHandle[SAVE_PROCESSING] = true
            _processingState.emit(ProcessingState.Started)

            when (linkLauncher.getAccountStatusFlow(configuration).first()) {
                AccountStatus.Verified -> {
                    _activeLinkSession.value = true
                    completeLinkInlinePayment(
                        configuration,
                        params,
                        userInput is UserInput.SignIn
                    )
                }
                AccountStatus.VerificationStarted,
                AccountStatus.NeedsVerification -> {
                    val success = requestLinkVerification()

                    if (success) {
                        completeLinkInlinePayment(
                            configuration,
                            params,
                            userInput is UserInput.SignIn
                        )
                    } else {
                        savedStateHandle[SAVE_PROCESSING] = false
                        _processingState.emit(ProcessingState.Ready)
                    }
                }
                AccountStatus.SignedOut,
                AccountStatus.Error -> {
                    _activeLinkSession.value = false
                    userInput?.let {
                        linkLauncher.signInWithUserInput(configuration, userInput).fold(
                            onSuccess = {
                                // If successful, the account was fetched or created, so try again
                                payWithLinkInline(configuration, userInput)
                            },
                            onFailure = {
                                _processingState.emit(ProcessingState.Error(it.localizedMessage))
                                savedStateHandle[SAVE_PROCESSING] = false
                                _processingState.emit(ProcessingState.Ready)
                            }
                        )
                    } ?: run {
                        savedStateHandle[SAVE_PROCESSING] = false
                        _processingState.emit(ProcessingState.Ready)
                    }
                }
            }
        }
    }

    private suspend fun setupLinkWithVerification(
        configuration: LinkPaymentLauncher.Configuration,
    ) {
        val success = requestLinkVerification()
        if (success) {
            launchLink(configuration, launchedDirectly = true)
        }
    }

    private suspend fun completeLinkInlinePayment(
        configuration: LinkPaymentLauncher.Configuration,
        paymentMethodCreateParams: PaymentMethodCreateParams,
        isReturningUser: Boolean
    ) {
        if (isReturningUser) {
            launchLink(configuration, launchedDirectly = false, paymentMethodCreateParams)
        } else {
            _processingState.emit(
                ProcessingState.PaymentDetailsCollected(
                    linkLauncher.attachNewCardToAccount(
                        configuration,
                        paymentMethodCreateParams
                    ).getOrNull()
                )
            )
        }
    }

    fun launchLink(
        configuration: LinkPaymentLauncher.Configuration,
        launchedDirectly: Boolean,
        paymentMethodCreateParams: PaymentMethodCreateParams? = null
    ) {
        launchedLinkDirectly = launchedDirectly

        linkLauncher.present(
            configuration,
            paymentMethodCreateParams,
        )

        _processingState.tryEmit(ProcessingState.Launched)
    }

    /**
     * Method called with the result of launching the Link UI to collect a payment.
     */
    private fun onLinkActivityResult(result: LinkActivityResult) {
        val completePaymentFlow = result is LinkActivityResult.Completed
        val cancelPaymentFlow = launchedLinkDirectly &&
            result is LinkActivityResult.Canceled && result.reason == LinkActivityResult.Canceled.Reason.BackPressed

        if (completePaymentFlow) {
            // If payment was completed inside the Link UI, dismiss immediately.
            eventReporter.onPaymentSuccess(PaymentSelection.Link)
            _processingState.tryEmit(ProcessingState.Complete)
        } else if (cancelPaymentFlow) {
            // We launched the user straight into Link, but they decided to exit out of it.
            _processingState.tryEmit(ProcessingState.Cancelled)
        } else {
            _processingState.tryEmit(
                ProcessingState.CompletedWithPaymentResult(result.convertToPaymentResult())
            )
        }
    }

    private fun LinkActivityResult.convertToPaymentResult() =
        when (this) {
            is LinkActivityResult.Completed -> PaymentResult.Completed
            is LinkActivityResult.Canceled -> PaymentResult.Canceled
            is LinkActivityResult.Failed -> PaymentResult.Failed(error)
        }
}