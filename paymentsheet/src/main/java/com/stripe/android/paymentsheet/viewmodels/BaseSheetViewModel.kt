package com.stripe.android.paymentsheet.viewmodels

import android.app.Application
import androidx.annotation.StringRes
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.stripe.android.core.Logger
import com.stripe.android.core.injection.InjectorKey
import com.stripe.android.core.injection.NonFallbackInjector
import com.stripe.android.link.LinkPaymentDetails
import com.stripe.android.link.LinkPaymentLauncher
import com.stripe.android.link.model.AccountStatus
import com.stripe.android.link.ui.inline.UserInput
import com.stripe.android.model.PaymentMethod
import com.stripe.android.model.PaymentMethodCreateParams
import com.stripe.android.model.StripeIntent
import com.stripe.android.payments.paymentlauncher.PaymentResult
import com.stripe.android.paymentsheet.PaymentOptionsState
import com.stripe.android.paymentsheet.PaymentOptionsViewModel
import com.stripe.android.paymentsheet.PaymentSheet
import com.stripe.android.paymentsheet.PrefsRepository
import com.stripe.android.paymentsheet.analytics.EventReporter
import com.stripe.android.paymentsheet.model.FragmentConfig
import com.stripe.android.paymentsheet.model.PaymentSelection
import com.stripe.android.paymentsheet.model.SavedSelection
import com.stripe.android.paymentsheet.repositories.CustomerRepository
import com.stripe.android.paymentsheet.state.LinkState
import com.stripe.android.paymentsheet.state.PaymentSheetState
import com.stripe.android.paymentsheet.state.loggedInToLink
import com.stripe.android.paymentsheet.state.loggedOutOfLink
import com.stripe.android.paymentsheet.state.mapAsFlow
import com.stripe.android.paymentsheet.state.removePaymentMethod
import com.stripe.android.paymentsheet.toPaymentSelection
import com.stripe.android.paymentsheet.ui.PrimaryButton
import com.stripe.android.ui.core.Amount
import com.stripe.android.ui.core.address.AddressRepository
import com.stripe.android.ui.core.forms.resources.LpmRepository
import com.stripe.android.ui.core.forms.resources.ResourceRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jetbrains.annotations.TestOnly
import kotlin.coroutines.CoroutineContext

/**
 * Base `ViewModel` for activities that use `BottomSheet`.
 */
internal abstract class BaseSheetViewModel<TransitionTargetType>(
    application: Application,
    initialState: PaymentSheetState,
    internal val config: PaymentSheet.Configuration?,
    internal val eventReporter: EventReporter,
    protected val customerRepository: CustomerRepository,
    protected val prefsRepository: PrefsRepository,
    protected val workContext: CoroutineContext = Dispatchers.IO,
    protected val logger: Logger,
    @InjectorKey val injectorKey: String,
    val lpmResourceRepository: ResourceRepository<LpmRepository>,
    val addressResourceRepository: ResourceRepository<AddressRepository>,
    val savedStateHandle: SavedStateHandle,
    val linkLauncher: LinkPaymentLauncher
) : AndroidViewModel(application) {

    private val _state = MutableStateFlow(initialState)
    protected val state: StateFlow<PaymentSheetState> = _state

    protected val fullState: PaymentSheetState.Full?
        get() = state.value as? PaymentSheetState.Full

    protected fun setFullState(state: PaymentSheetState.Full) {
        _state.value = state
    }

    protected fun updateFullState(
        transform: (PaymentSheetState.Full) -> PaymentSheetState.Full,
    ) {
        _state.update {
            if (it is PaymentSheetState.Full) {
                transform(it)
            } else {
                it
            }
        }
    }

    /**
     * This ViewModel exists during the whole user flow, and needs to share the Dagger dependencies
     * with the other, screen-specific ViewModels. So it holds a reference to the injector which is
     * passed as a parameter to the other ViewModel factories.
     */
    lateinit var injector: NonFallbackInjector

    internal val customerConfig = config?.customer
    internal val merchantName = config?.merchantDisplayName
        ?: application.applicationInfo.loadLabel(application.packageManager).toString()

    protected var mostRecentError: Throwable? = null

    internal val isGooglePayReady: StateFlow<Boolean> = state.mapAsFlow { it.isGooglePayReady }
        .stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            false
        )

    // Don't save the resource repository state because it must be re-initialized
    // with the save server specs when reconstructed.
    private var _isResourceRepositoryReady = MutableStateFlow<Boolean?>(null)

    internal val isResourceRepositoryReady: StateFlow<Boolean?> =
        _isResourceRepositoryReady.stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            false
        )

    internal val isLinkEnabled: StateFlow<Boolean> = state.mapAsFlow { it.isLinkAvailable }
        .stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            false
        )

    fun reportShowNewPaymentOptionForm() {
        eventReporter.onShowNewPaymentOptionForm(
            linkEnabled = fullState?.isLinkAvailable ?: false,
            activeLinkSession = fullState?.linkState?.loginState == LinkState.LoginState.LoggedIn,
        )
    }

    internal val linkConfiguration: StateFlow<LinkPaymentLauncher.Configuration?> =
        state.mapAsFlow { it.linkState?.configuration }
            .stateIn(
                viewModelScope,
                SharingStarted.Eagerly,
                null
            )

    internal val stripeIntent: StateFlow<StripeIntent?> = state.mapAsFlow { it.stripeIntent }
        .stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            null
        )

    internal val supportedPaymentMethods: List<LpmRepository.SupportedPaymentMethod>
        get() = fullState?.supportedPaymentMethodTypes.orEmpty().mapNotNull {
            lpmResourceRepository.getRepository().fromCode(it)
        }

    /**
     * The list of saved payment methods for the current customer.
     * Value is null until it's loaded, and non-null (could be empty) after that.
     */
    internal val paymentMethods: StateFlow<List<PaymentMethod>> = state.mapAsFlow {
        it.customerPaymentMethods
    }.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        listOf()
    )

    internal val amount: Amount?
        get() = fullState?.amount

    internal val headerText = MutableStateFlow<String?>("")

    private val savedSelection: StateFlow<SavedSelection> = state.mapAsFlow { it.savedSelection }
        .stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            SavedSelection.None
        )

    private val _transition = MutableStateFlow<Event<TransitionTargetType?>>(Event(null))
    internal val transition: StateFlow<Event<TransitionTargetType?>> = _transition

    internal val liveMode: StateFlow<Boolean> = state.mapAsFlow { it.stripeIntent.isLiveMode }
        .stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            false
        )
    internal val selection: StateFlow<PaymentSelection?> = state.mapAsFlow { it.selection }
        .stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            null
        )

    private val editing: StateFlow<Boolean> = state.mapAsFlow { it.isEditing }
        .stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            false
        )

    val processing: StateFlow<Boolean> = state.mapAsFlow { it.isProcessing }
        .stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            false
        )

    private val _contentVisible = MutableStateFlow(true)
    internal val contentVisible: StateFlow<Boolean> = _contentVisible

    /**
     * Use this to override the current UI state of the primary button. The UI state is reset every
     * time the payment selection is changed.
     */
    val primaryButtonUIState: StateFlow<PrimaryButton.UIState?> =
        state.mapAsFlow { it.primaryButtonUiState }
            .stateIn(
                viewModelScope,
                SharingStarted.Eagerly,
                null
            )

    private val _primaryButtonState = MutableStateFlow<PrimaryButton.State?>(null)
    val primaryButtonState: StateFlow<PrimaryButton.State?> = _primaryButtonState

    internal val notesText: StateFlow<String?> = state.mapAsFlow { it.notesText }
        .stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            null
        )

    private val _showLinkVerificationDialog = MutableStateFlow(false)
    val showLinkVerificationDialog: StateFlow<Boolean> = _showLinkVerificationDialog

    private val linkVerificationChannel = Channel<Boolean>(capacity = 1)

    /**
     * This should be initialized from the starter args, and then from that point forward it will be
     * the last valid new payment method entered by the user.
     * In contrast to selection, this field will not be updated by the list fragment. It is used to
     * save a new payment method that is added so that the payment data entered is recovered when
     * the user returns to that payment method type.
     */
    abstract var newPaymentSelection: PaymentSelection.New?

    open var linkInlineSelection = MutableStateFlow<PaymentSelection.New.LinkInline?>(null)

    abstract fun onFatal(throwable: Throwable)

    val buttonsEnabled: StateFlow<Boolean> = state.mapAsFlow { it.areWalletButtonsEnabled }
        .stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            false
        )

    val ctaEnabled: StateFlow<Boolean> = state.mapAsFlow { it.isPrimaryButtonEnabled }
        .stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            false
        )

    internal var lpmServerSpec
        get() = savedStateHandle.get<String>(LPM_SERVER_SPEC_STRING)
        set(value) = savedStateHandle.set(LPM_SERVER_SPEC_STRING, value)

    private val paymentOptionsStateMapper: PaymentOptionsStateMapper by lazy {
        PaymentOptionsStateMapper(
            paymentMethods = paymentMethods,
            initialSelection = savedSelection,
            currentSelection = selection,
            isGooglePayReady = isGooglePayReady,
            isLinkEnabled = isLinkEnabled,
            isNotPaymentFlow = this is PaymentOptionsViewModel,
        )
    }

    val paymentOptionsState: StateFlow<PaymentOptionsState> = paymentOptionsStateMapper()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(),
            initialValue = PaymentOptionsState(),
        )

    init {
        if (_isResourceRepositoryReady.value == null) {
            viewModelScope.launch {
                // This work should be done on the background
                CoroutineScope(workContext).launch {
                    // If we have been killed and are being restored then we need to re-populate
                    // the lpm repository
                    stripeIntent.value?.paymentMethodTypes?.let { intentPaymentMethodTypes ->
                        lpmResourceRepository.getRepository().apply {
                            if (!isLoaded()) {
                                update(intentPaymentMethodTypes, lpmServerSpec)
                            }
                        }
                    }

                    lpmResourceRepository.waitUntilLoaded()
                    addressResourceRepository.waitUntilLoaded()
                    _isResourceRepositoryReady.update { true }
                }
            }
        }

//        viewModelScope.launch {
//            // If the currently selected payment option has been removed, we set it to the one
//            // determined in the payment options state.
//            paymentOptionsState
//                .mapNotNull { it.selectedItem?.toPaymentSelection() }
//                .filter { it != selection.value }
//                .collect { updateSelection(it) }
//        }
    }

    val fragmentConfigEvent: Flow<Event<FragmentConfig?>> = combine(
            combine(
                savedSelection,
                stripeIntent,
                paymentMethods,
                ::Triple
            ),
            combine(
                isGooglePayReady,
                isResourceRepositoryReady,
                isLinkEnabled,
                ::Triple
            )
        ) { _, _ ->
        createFragmentConfig()
    }.map {
        Event(it)
    }

    fun reportShowExistingPaymentOptions() {
        eventReporter.onShowExistingPaymentOptions(
            linkEnabled = fullState?.isLinkAvailable ?: false,
            activeLinkSession = fullState?.linkState?.loginState == LinkState.LoginState.LoggedIn,
        )
    }

    private fun createFragmentConfig(): FragmentConfig? {
        val stripeIntentValue = stripeIntent.value
        val isGooglePayReadyValue = isGooglePayReady.value
        val savedSelectionValue = savedSelection.value
        // List of Payment Methods is not passed in the config but we still wait for it to be loaded
        // before adding the Fragment.

        return stripeIntentValue?.let {
            FragmentConfig(
                stripeIntent = it,
                isGooglePayReady = isGooglePayReadyValue,
                savedSelection = savedSelectionValue
            )
        }
    }

    fun transitionTo(target: TransitionTargetType) {
        _transition.update { Event(target) }
    }

    fun updatePrimaryButtonUIState(state: PrimaryButton.UIState?) {
        updateFullState { it.copy(primaryButtonUiState = state) }
    }

    fun updatePrimaryButtonState(state: PrimaryButton.State) {
        _primaryButtonState.value = state
    }

    fun updateBelowButtonText(text: String?) {
        updateFullState { it.copy(notesText = text) }
    }

    open fun updateSelection(selection: PaymentSelection?) {
        updateFullState {
            it.copy(
                selection = selection,
                newPaymentSelection = (selection as? PaymentSelection.New) ?: it.newPaymentSelection,
                notesText = null,
            )
        }
    }

    fun setEditing(isEditing: Boolean) {
        updateFullState {
            it.copy(isEditing = isEditing)
        }
    }

    fun setContentVisible(visible: Boolean) {
        _contentVisible.value = visible
    }

    fun removePaymentMethod(paymentMethod: PaymentMethod) {
        val paymentMethodId = paymentMethod.id ?: return

        viewModelScope.launch {
            updateFullState {
                it.removePaymentMethod(paymentMethodId)
            }

            customerConfig?.let {
                customerRepository.detachPaymentMethod(
                    it,
                    paymentMethodId
                )
            }
        }
    }

    protected suspend fun requestLinkVerification(): Boolean {
        _showLinkVerificationDialog.value = true
        return linkVerificationChannel.receive()
    }

    fun handleLinkVerificationResult(success: Boolean) {
        updateFullState {
            if (success) {
                it.loggedInToLink()
            } else {
                it.loggedOutOfLink()
            }
        }
        _showLinkVerificationDialog.value = false
        linkVerificationChannel.trySend(success)
    }

    fun payWithLinkInline(configuration: LinkPaymentLauncher.Configuration, userInput: UserInput?) {
        (selection.value as? PaymentSelection.New.Card)?.paymentMethodCreateParams?.let { params ->
            updateFullState { it.copy(isProcessing = true) }

            updatePrimaryButtonState(PrimaryButton.State.StartProcessing)

            viewModelScope.launch {
                when (linkLauncher.getAccountStatusFlow(configuration).first()) {
                    AccountStatus.Verified -> {
                        updateFullState { it.loggedInToLink() }
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
                            updateFullState { it.copy(isProcessing = false) }
                            updatePrimaryButtonState(PrimaryButton.State.Ready)
                        }
                    }
                    AccountStatus.SignedOut,
                    AccountStatus.Error -> {
                        updateFullState { it.loggedOutOfLink() }
                        userInput?.let {
                            linkLauncher.signInWithUserInput(configuration, userInput).fold(
                                onSuccess = {
                                    // If successful, the account was fetched or created, so try again
                                    payWithLinkInline(configuration, userInput)
                                },
                                onFailure = {
                                    onError(it.localizedMessage)
                                    updateFullState { it.copy(isProcessing = false) }
                                    updatePrimaryButtonState(PrimaryButton.State.Ready)
                                }
                            )
                        } ?: run {
                            updateFullState { it.copy(isProcessing = false) }
                            updatePrimaryButtonState(PrimaryButton.State.Ready)
                        }
                    }
                }
            }
        }
    }

    internal open fun completeLinkInlinePayment(
        configuration: LinkPaymentLauncher.Configuration,
        paymentMethodCreateParams: PaymentMethodCreateParams,
        isReturningUser: Boolean
    ) {
        viewModelScope.launch {
            onLinkPaymentDetailsCollected(
                linkLauncher.attachNewCardToAccount(
                    configuration,
                    paymentMethodCreateParams
                ).getOrNull()
            )
        }
    }

    /**
     * Method called after completing collection of payment data for a payment with Link.
     */
    abstract fun onLinkPaymentDetailsCollected(linkPaymentDetails: LinkPaymentDetails.New?)

    abstract fun onUserCancel()

    fun onUserBack() {
        // Reset the selection to the one from before opening the add payment method screen
        val paymentOptionsState = paymentOptionsState.value
        updateSelection(paymentOptionsState.selectedItem?.toPaymentSelection())
    }

    abstract fun onPaymentResult(paymentResult: PaymentResult)

    abstract fun onFinish()

    abstract fun onError(@StringRes error: Int? = null)

    abstract fun onError(error: String? = null)

    data class UserErrorMessage(val message: String)

    /**
     * Used as a wrapper for data that is exposed via a LiveData that represents an event.
     * From https://medium.com/androiddevelopers/livedata-with-snackbar-navigation-and-other-events-the-singleliveevent-case-ac2622673150
     * TODO(brnunes): Migrate to Flows once stable: https://medium.com/androiddevelopers/a-safer-way-to-collect-flows-from-android-uis-23080b1f8bda
     */
    class Event<out T>(private val content: T) {

        var hasBeenHandled = false
            private set // Allow external read but not write

        /**
         * Returns the content and prevents its use again.
         */
        fun getContentIfNotHandled(): T? {
            return if (hasBeenHandled) {
                null
            } else {
                hasBeenHandled = true
                content
            }
        }

        /**
         * Returns the content, even if it's already been handled.
         */
        @TestOnly
        fun peekContent(): T = content
    }

    companion object {
        internal const val SAVE_AMOUNT = "amount"
        internal const val LPM_SERVER_SPEC_STRING = "lpm_server_spec_string"
    }
}
