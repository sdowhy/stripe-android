package com.stripe.android.paymentsheet

import android.app.Application
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.test.core.app.ApplicationProvider
import com.google.android.gms.common.api.Status
import com.google.common.truth.Truth.assertThat
import com.stripe.android.ApiKeyFixtures
import com.stripe.android.PaymentConfiguration
import com.stripe.android.PaymentIntentResult
import com.stripe.android.StripeIntentResult
import com.stripe.android.core.Logger
import com.stripe.android.core.injection.DUMMY_INJECTOR_KEY
import com.stripe.android.googlepaylauncher.GooglePayPaymentMethodLauncher
import com.stripe.android.link.LinkPaymentLauncher
import com.stripe.android.link.model.AccountStatus
import com.stripe.android.model.Address
import com.stripe.android.model.CardBrand
import com.stripe.android.model.ConfirmPaymentIntentParams
import com.stripe.android.model.ConfirmSetupIntentParams
import com.stripe.android.model.ConfirmStripeIntentParams
import com.stripe.android.model.MandateDataParams
import com.stripe.android.model.PaymentIntentFixtures
import com.stripe.android.model.PaymentMethod
import com.stripe.android.model.PaymentMethodCode
import com.stripe.android.model.PaymentMethodCreateParamsFixtures
import com.stripe.android.model.PaymentMethodFixtures
import com.stripe.android.model.PaymentMethodOptionsParams
import com.stripe.android.model.StripeIntent
import com.stripe.android.payments.paymentlauncher.PaymentResult
import com.stripe.android.paymentsheet.PaymentSheetViewModel.CheckoutIdentifier
import com.stripe.android.paymentsheet.addresselement.AddressDetails
import com.stripe.android.paymentsheet.analytics.EventReporter
import com.stripe.android.paymentsheet.model.FragmentConfig
import com.stripe.android.paymentsheet.model.PaymentSelection
import com.stripe.android.paymentsheet.model.PaymentSheetViewState
import com.stripe.android.paymentsheet.model.SavedSelection
import com.stripe.android.paymentsheet.model.StripeIntentValidator
import com.stripe.android.paymentsheet.paymentdatacollection.ach.ACHText
import com.stripe.android.paymentsheet.repositories.CustomerRepository
import com.stripe.android.paymentsheet.repositories.StripeIntentRepository
import com.stripe.android.paymentsheet.state.LinkState
import com.stripe.android.paymentsheet.ui.PrimaryButton
import com.stripe.android.paymentsheet.viewmodels.BaseSheetViewModel
import com.stripe.android.paymentsheet.viewmodels.BaseSheetViewModel.UserErrorMessage
import com.stripe.android.ui.core.forms.resources.LpmRepository
import com.stripe.android.ui.core.forms.resources.StaticLpmResourceRepository
import com.stripe.android.utils.FakeCustomerRepository
import com.stripe.android.utils.FakePaymentSheetLoader
import com.stripe.android.utils.TestUtils.idleLooper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Rule
import org.junit.runner.RunWith
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.spy
import org.mockito.kotlin.verify
import org.robolectric.RobolectricTestRunner
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

@ExperimentalCoroutinesApi
@RunWith(RobolectricTestRunner::class)
internal class PaymentSheetViewModelTest {
    @get:Rule
    val rule = InstantTaskExecutorRule()

    private val testDispatcher = UnconfinedTestDispatcher()

    private val eventReporter = mock<EventReporter>()
    private val application = ApplicationProvider.getApplicationContext<Application>()

    private val lpmRepository = LpmRepository(
        arguments = LpmRepository.LpmRepositoryArguments(application.resources),
    ).apply {
        this.forceUpdate(
            listOf(
                PaymentMethod.Type.Card.code,
                PaymentMethod.Type.USBankAccount.code,
                PaymentMethod.Type.Ideal.code,
                PaymentMethod.Type.SepaDebit.code,
                PaymentMethod.Type.Sofort.code,
                PaymentMethod.Type.Affirm.code,
                PaymentMethod.Type.AfterpayClearpay.code,
            ),
            null
        )
    }

    private val prefsRepository = FakePrefsRepository()
    private val lpmResourceRepository = StaticLpmResourceRepository(lpmRepository)

    private val linkLauncher = mock<LinkPaymentLauncher> {
        on { getAccountStatusFlow(any()) } doReturn flowOf(AccountStatus.SignedOut)
    }

    private val primaryButtonUIState = PrimaryButton.UIState(
        label = "Test",
        onClick = {},
        enabled = true,
        visible = true
    )

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        MockitoAnnotations.openMocks(this)
    }

    @AfterTest
    fun cleanup() {
        Dispatchers.resetMain()
    }

    @Test
    fun `init should fire analytics event`() {
        createViewModel()
        verify(eventReporter).onInit(PaymentSheetFixtures.CONFIG_CUSTOMER_WITH_GOOGLEPAY)
    }

    @Test
    fun `removePaymentMethod triggers async removal`() = runTest {
        val customerRepository = spy(FakeCustomerRepository())
        val viewModel = createViewModel(
            customerRepository = customerRepository
        )

        viewModel.removePaymentMethod(PaymentMethodFixtures.CARD_PAYMENT_METHOD)
        idleLooper()

        verify(customerRepository).detachPaymentMethod(
            any(),
            eq(PaymentMethodFixtures.CARD_PAYMENT_METHOD.id!!)
        )
    }

    @Test
    fun `checkout() should confirm saved card payment methods`() = runTest(UnconfinedTestDispatcher()) {
        val viewModel = createViewModel()

        val paymentSelection = PaymentSelection.Saved(PaymentMethodFixtures.CARD_PAYMENT_METHOD)
        viewModel.updateSelection(paymentSelection)
        viewModel.checkout(CheckoutIdentifier.None)

        val result = viewModel.startConfirm.stateIn(viewModel.viewModelScope).value

        assertThat(result?.peekContent())
            .isEqualTo(
                ConfirmPaymentIntentParams.createWithPaymentMethodId(
                    requireNotNull(PaymentMethodFixtures.CARD_PAYMENT_METHOD.id),
                    CLIENT_SECRET,
                    paymentMethodOptions = PaymentMethodOptionsParams.Card(
                        setupFutureUsage = ConfirmPaymentIntentParams.SetupFutureUsage.Blank
                    )
                )
            )
    }

    @Test
    fun `checkout() should confirm saved us_bank_account payment methods`() = runTest(
        UnconfinedTestDispatcher()
    ) {
        val viewModel = createViewModel()

        val paymentSelection = PaymentSelection.Saved(PaymentMethodFixtures.US_BANK_ACCOUNT)
        viewModel.updateSelection(paymentSelection)
        viewModel.checkout(CheckoutIdentifier.None)

        val result = viewModel.startConfirm.stateIn(viewModel.viewModelScope).value

        assertThat(result?.peekContent())
            .isEqualTo(
                ConfirmPaymentIntentParams.createWithPaymentMethodId(
                    requireNotNull(PaymentMethodFixtures.US_BANK_ACCOUNT.id),
                    CLIENT_SECRET,
                    paymentMethodOptions = PaymentMethodOptionsParams.USBankAccount(
                        setupFutureUsage = ConfirmPaymentIntentParams.SetupFutureUsage.OffSession
                    ),
                    mandateData = MandateDataParams(
                        type = MandateDataParams.Type.Online.DEFAULT
                    )
                )
            )
    }

    @Test
    fun `checkout() for Setup Intent with saved payment method that requires mandate should include mandate`() = runTest(
        UnconfinedTestDispatcher()
    ) {
        val viewModel = createViewModel(
            args = ARGS_CUSTOMER_WITH_GOOGLEPAY_SETUP
        )

        val paymentSelection =
            PaymentSelection.Saved(PaymentMethodFixtures.SEPA_DEBIT_PAYMENT_METHOD)
        viewModel.updateSelection(paymentSelection)
        viewModel.checkout(CheckoutIdentifier.None)

        val result = viewModel.startConfirm.stateIn(viewModel.viewModelScope).value

        val confirmParams = result?.peekContent() as ConfirmSetupIntentParams
        assertThat(confirmParams.mandateData)
            .isNotNull()
    }

    @Test
    fun `checkout() should confirm new payment methods`() = runTest(UnconfinedTestDispatcher()) {
        val viewModel = createViewModel()

        val paymentSelection = PaymentSelection.New.Card(
            PaymentMethodCreateParamsFixtures.DEFAULT_CARD,
            CardBrand.Visa,
            customerRequestedSave = PaymentSelection.CustomerRequestedSave.RequestReuse
        )
        viewModel.updateSelection(paymentSelection)
        viewModel.checkout(CheckoutIdentifier.None)

        val result = viewModel.startConfirm.stateIn(viewModel.viewModelScope).value

        assertThat(result?.peekContent())
            .isEqualTo(
                ConfirmPaymentIntentParams.createWithPaymentMethodCreateParams(
                    PaymentMethodCreateParamsFixtures.DEFAULT_CARD,
                    CLIENT_SECRET,
                    paymentMethodOptions = PaymentMethodOptionsParams.Card(
                        setupFutureUsage = ConfirmPaymentIntentParams.SetupFutureUsage.OffSession
                    )
                )
            )
    }

    @Test
    fun `checkout() with shipping should confirm new payment methods`() = runTest(
        UnconfinedTestDispatcher()
    ) {
        val viewModel = createViewModel(
            args = ARGS_CUSTOMER_WITH_GOOGLEPAY.copy(
                config = ARGS_CUSTOMER_WITH_GOOGLEPAY.config?.copy(
                    shippingDetails = AddressDetails(
                        address = PaymentSheet.Address(
                            country = "US"
                        ),
                        name = "Test Name"
                    )
                )
            )
        )

        val paymentSelection = PaymentSelection.New.Card(
            PaymentMethodCreateParamsFixtures.DEFAULT_CARD,
            CardBrand.Visa,
            customerRequestedSave = PaymentSelection.CustomerRequestedSave.RequestReuse
        )
        viewModel.updateSelection(paymentSelection)
        viewModel.checkout(CheckoutIdentifier.None)

        val result = viewModel.startConfirm.stateIn(viewModel.viewModelScope).value

        assertThat(result?.peekContent())
            .isEqualTo(
                ConfirmPaymentIntentParams.createWithPaymentMethodCreateParams(
                    PaymentMethodCreateParamsFixtures.DEFAULT_CARD,
                    CLIENT_SECRET,
                    paymentMethodOptions = PaymentMethodOptionsParams.Card(
                        setupFutureUsage = ConfirmPaymentIntentParams.SetupFutureUsage.OffSession
                    ),
                    shipping = ConfirmPaymentIntentParams.Shipping(
                        address = Address(
                            country = "US"
                        ),
                        name = "Test Name"
                    )
                )
            )
    }

    @Test
    fun `Launches Link when user is logged in to their Link account`() = runTest {
        val configuration: LinkPaymentLauncher.Configuration = mock()

        val viewModel = createViewModel(
            linkState = LinkState(
                configuration = configuration,
                loginState = LinkState.LoginState.LoggedIn,
            ),
        )

        assertThat(viewModel.showLinkVerificationDialog.value).isFalse()
//        assertThat(viewModel.activeLinkSession.value).isTrue()
        assertThat(viewModel.isLinkEnabled.value).isTrue()

        verify(linkLauncher).present(
            configuration = eq(configuration),
            prefilledNewCardParams = isNull(),
        )
    }

    @Test
    fun `Launches Link verification when user needs to verify their Link account`() = runTest {
        val viewModel = createViewModel(
            linkState = LinkState(
                configuration = mock(),
                loginState = LinkState.LoginState.NeedsVerification,
            ),
        )

        assertThat(viewModel.showLinkVerificationDialog.value).isTrue()
//        assertThat(viewModel.activeLinkSession.value).isFalse()
        assertThat(viewModel.isLinkEnabled.value).isTrue()
    }

    @Test
    fun `Enables Link when user is logged out of their Link account`() = runTest {
        val viewModel = createViewModel(
            linkState = LinkState(
                configuration = mock(),
                loginState = LinkState.LoginState.LoggedOut,
            ),
        )

//        assertThat(viewModel.activeLinkSession.value).isFalse()
        assertThat(viewModel.isLinkEnabled.value).isTrue()
    }

    @Test
    fun `Does not enable Link when the Link state can't be determined`() = runTest {
        val viewModel = createViewModel(
            linkState = null,
        )

//        assertThat(viewModel.activeLinkSession.value).isFalse()
        assertThat(viewModel.isLinkEnabled.value).isFalse()
    }

    @Test
    fun `Google Pay checkout cancelled returns to Ready state`() = runTest(UnconfinedTestDispatcher()) {
        val viewModel = createViewModel()

        viewModel.updateSelection(PaymentSelection.GooglePay)
        viewModel.checkout(CheckoutIdentifier.SheetTopGooglePay)

        var viewState = viewModel.getButtonStateObservable(CheckoutIdentifier.SheetTopGooglePay)
            .stateIn(viewModel.viewModelScope).value
        var processing = viewModel.processing.stateIn(viewModel.viewModelScope).value

        assertThat(viewState)
            .isEqualTo(PaymentSheetViewState.StartProcessing)
        assertThat(processing).isTrue()

        viewModel.onGooglePayResult(GooglePayPaymentMethodLauncher.Result.Canceled)

        viewState = viewModel.getButtonStateObservable(CheckoutIdentifier.SheetTopGooglePay)
            .stateIn(viewModel.viewModelScope).value
        processing = viewModel.processing.stateIn(viewModel.viewModelScope).value

        assertThat(viewState)
            .isEqualTo(PaymentSheetViewState.Reset(null))
        assertThat(processing).isFalse()
    }

    @Test
    fun `On checkout clear the previous view state error`() = runTest(UnconfinedTestDispatcher()) {
        val viewModel = createViewModel()
        viewModel.checkoutIdentifier = CheckoutIdentifier.SheetTopGooglePay

        val googleViewState = viewModel.getButtonStateObservable(CheckoutIdentifier.SheetTopGooglePay)
            .stateIn(viewModel.viewModelScope).value

        viewModel.checkout(CheckoutIdentifier.SheetBottomBuy)

        val buyViewState = viewModel.getButtonStateObservable(CheckoutIdentifier.SheetBottomBuy)
            .stateIn(viewModel.viewModelScope).value

        assertThat(googleViewState).isEqualTo(PaymentSheetViewState.Reset(null))
        assertThat(buyViewState).isEqualTo(PaymentSheetViewState.StartProcessing)
    }

    @Test
    fun `Google Pay checkout failed returns to Ready state and shows error`() = runTest(
        UnconfinedTestDispatcher()
    ) {
        val viewModel = createViewModel()

        viewModel.updateSelection(PaymentSelection.GooglePay)
        viewModel.checkout(CheckoutIdentifier.SheetTopGooglePay)

        var viewState = viewModel.getButtonStateObservable(CheckoutIdentifier.SheetTopGooglePay)
            .stateIn(viewModel.viewModelScope).value
        var processing = viewModel.processing.stateIn(viewModel.viewModelScope).value

        assertThat(viewState).isEqualTo(PaymentSheetViewState.StartProcessing)
        assertThat(processing).isTrue()

        viewModel.onGooglePayResult(
            GooglePayPaymentMethodLauncher.Result.Failed(
                Exception("Test exception"),
                Status.RESULT_INTERNAL_ERROR.statusCode
            )
        )

        viewState = viewModel.getButtonStateObservable(CheckoutIdentifier.SheetTopGooglePay)
            .stateIn(viewModel.viewModelScope).value
        processing = viewModel.processing.stateIn(viewModel.viewModelScope).value

        assertThat(viewState)
            .isEqualTo(PaymentSheetViewState.Reset(UserErrorMessage("An internal error occurred.")))
        assertThat(processing).isFalse()
    }

    @Test
    fun `onPaymentResult() should update ViewState and save preferences`() =
        runTest(UnconfinedTestDispatcher()) {
            val viewModel = createViewModel()

            val selection = PaymentSelection.Saved(PaymentMethodFixtures.CARD_PAYMENT_METHOD)
            viewModel.updateSelection(selection)

            viewModel.onPaymentResult(PaymentResult.Completed)

            val viewState = viewModel.viewState.stateIn(viewModel.viewModelScope).value

            assertThat(viewState)
                .isInstanceOf(PaymentSheetViewState.FinishProcessing::class.java)

            (viewState as PaymentSheetViewState.FinishProcessing).onComplete()

            val paymentSheetResult = viewModel.paymentSheetResult
                .stateIn(viewModel.viewModelScope).value

            assertThat(paymentSheetResult).isEqualTo(PaymentSheetResult.Completed)

            verify(eventReporter)
                .onPaymentSuccess(selection)

            assertThat(prefsRepository.paymentSelectionArgs)
                .containsExactly(selection)
            assertThat(prefsRepository.getSavedSelection(
                isGooglePayAvailable = true,
                isLinkAvailable = true
            ))
                .isEqualTo(
                    SavedSelection.PaymentMethod(selection.paymentMethod.id.orEmpty())
                )
        }

    @Test
    fun `onPaymentResult() should update ViewState and save new payment method`() =
        runTest(UnconfinedTestDispatcher()) {
            val viewModel = createViewModel(stripeIntent = PAYMENT_INTENT_WITH_PM)

            val selection = PaymentSelection.New.Card(
                PaymentMethodCreateParamsFixtures.DEFAULT_CARD,
                CardBrand.Visa,
                customerRequestedSave = PaymentSelection.CustomerRequestedSave.RequestReuse
            )
            viewModel.updateSelection(selection)

            viewModel.onPaymentResult(PaymentResult.Completed)

            val viewState = viewModel.viewState.stateIn(viewModel.viewModelScope).value

            assertThat(viewState)
                .isInstanceOf(PaymentSheetViewState.FinishProcessing::class.java)

            (viewState as PaymentSheetViewState.FinishProcessing).onComplete()

            val paymentSheetResult = viewModel.paymentSheetResult
                .stateIn(viewModel.viewModelScope).value

            assertThat(paymentSheetResult).isEqualTo(PaymentSheetResult.Completed)

            verify(eventReporter)
                .onPaymentSuccess(selection)

            assertThat(prefsRepository.paymentSelectionArgs)
                .containsExactly(
                    PaymentSelection.Saved(
                        PAYMENT_INTENT_RESULT_WITH_PM.intent.paymentMethod!!
                    )
                )
            assertThat(prefsRepository.getSavedSelection(true, true))
                .isEqualTo(
                    SavedSelection.PaymentMethod(
                        PAYMENT_INTENT_RESULT_WITH_PM.intent.paymentMethod!!.id!!
                    )
                )
        }

    @Test
    fun `onPaymentResult() with non-success outcome should report failure event`() = runTest(
        UnconfinedTestDispatcher()
    ) {
        val viewModel = createViewModel()
        val selection = PaymentSelection.Saved(PaymentMethodFixtures.CARD_PAYMENT_METHOD)
        viewModel.updateSelection(selection)

        viewModel.onPaymentResult(PaymentResult.Failed(Throwable()))
        verify(eventReporter).onPaymentFailure(selection)
    }

    @Test
    fun `onPaymentResult() should update emit API errors`() =
        runTest(UnconfinedTestDispatcher()) {
            val viewModel = createViewModel()

            var viewState = viewModel.viewState.stateIn(viewModel.viewModelScope).value

            assertThat(viewState)
                .isEqualTo(
                    PaymentSheetViewState.Reset(null)
                )

            val errorMessage = "very helpful error message"
            viewModel.onPaymentResult(PaymentResult.Failed(Throwable(errorMessage)))

            viewState = viewModel.viewState.stateIn(viewModel.viewModelScope).value

            assertThat(viewState)
                .isEqualTo(
                    PaymentSheetViewState.Reset(
                        UserErrorMessage(errorMessage)
                    )
                )
        }

    @Test
    fun `fetchPaymentIntent() should update ViewState LiveData`() = runTest(UnconfinedTestDispatcher()) {
        val viewModel = createViewModel()

        val viewState = viewModel.viewState.stateIn(viewModel.viewModelScope).value

        assertThat(viewState)
            .isEqualTo(
                PaymentSheetViewState.Reset(null)
            )
    }

    @Test
    fun `Loading payment sheet state should propagate errors`() = runTest(UnconfinedTestDispatcher()) {
        val viewModel = createViewModel(shouldFailLoad = true)

        val result = viewModel.paymentSheetResult.stateIn(viewModel.viewModelScope).value

        assertThat(result).isInstanceOf(PaymentSheetResult.Failed::class.java)
    }

    @Test
    fun `isGooglePayReady without google pay config should emit false`() = runTest(
        UnconfinedTestDispatcher()
    ) {
        val viewModel = createViewModel(PaymentSheetFixtures.ARGS_CUSTOMER_WITHOUT_GOOGLEPAY)
        val isReady = viewModel.isGooglePayReady.stateIn(viewModel.viewModelScope).value

        assertThat(isReady)
            .isFalse()
    }

    @Test
    fun `isGooglePayReady for SetupIntent missing currencyCode should emit false`() = runTest(
        UnconfinedTestDispatcher()
    ) {
        val viewModel = createViewModel(
            ARGS_CUSTOMER_WITH_GOOGLEPAY_SETUP.copy(
                config = PaymentSheetFixtures.CONFIG_CUSTOMER_WITH_GOOGLEPAY.copy(
                    googlePay = ConfigFixtures.GOOGLE_PAY.copy(
                        currencyCode = null
                    )
                )
            )
        )
        val isReady = viewModel.isGooglePayReady.stateIn(viewModel.viewModelScope).value

        assertThat(isReady)
            .isFalse()
    }

    @Test
    fun `googlePayLauncherConfig for SetupIntent with currencyCode should be valid`() {
        val viewModel = createViewModel(ARGS_CUSTOMER_WITH_GOOGLEPAY_SETUP)
        assertThat(viewModel.googlePayLauncherConfig)
            .isNotNull()
    }

    @Test
    fun `fragmentConfig when all data is ready should emit value`() = runTest(
        UnconfinedTestDispatcher()
    ) {
        val viewModel = createViewModel()

        val config = viewModel.fragmentConfigEvent.stateIn(viewModel.viewModelScope).value

        assertThat(config.getContentIfNotHandled())
            .isNotNull()
    }

    @Test
    fun `buyButton is enabled when primaryButtonEnabled is true, else not processing, not editing, and a selection has been made`() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        var isEnabled: Boolean

        viewModel.updateSelection(PaymentSelection.GooglePay)
        viewModel.setEditing(false)
        isEnabled = viewModel.ctaEnabled.stateIn(viewModel.viewModelScope).value

        assertThat(isEnabled).isTrue()

        viewModel.updateSelection(null)
        isEnabled = viewModel.ctaEnabled.stateIn(viewModel.viewModelScope).value
        assertThat(isEnabled).isFalse()

        viewModel.updateSelection(PaymentSelection.GooglePay)
        isEnabled = viewModel.ctaEnabled.stateIn(viewModel.viewModelScope).value
        assertThat(isEnabled).isTrue()

        viewModel.updatePrimaryButtonUIState(primaryButtonUIState.copy(enabled = false))
        isEnabled = viewModel.ctaEnabled.stateIn(viewModel.viewModelScope).value
        assertThat(isEnabled).isFalse()

        viewModel.updatePrimaryButtonUIState(primaryButtonUIState.copy(enabled = true))
        isEnabled = viewModel.ctaEnabled.stateIn(viewModel.viewModelScope).value
        assertThat(isEnabled).isTrue()

        viewModel.setEditing(true)
        isEnabled = viewModel.ctaEnabled.stateIn(viewModel.viewModelScope).value
        assertThat(isEnabled).isFalse()

        viewModel.setEditing(false)
        isEnabled = viewModel.ctaEnabled.stateIn(viewModel.viewModelScope).value
        assertThat(isEnabled).isTrue()
    }

    @Test
    fun `Should show amount is true for PaymentIntent`() {
        val viewModel = createViewModel()

        assertThat(viewModel.isProcessingPaymentIntent)
            .isTrue()
    }

    @Test
    fun `Should show amount is false for SetupIntent`() {
        val viewModel = createViewModel(
            args = ARGS_CUSTOMER_WITH_GOOGLEPAY_SETUP
        )

        assertThat(viewModel.isProcessingPaymentIntent)
            .isFalse()
    }

    @Test
    fun `When configuration is empty, merchant name should reflect the app name`() {
        val viewModel = createViewModel(
            args = ARGS_WITHOUT_CUSTOMER
        )

        // In a real app, the app name will be used. In tests the package name is returned.
        assertThat(viewModel.merchantName)
            .isEqualTo("com.stripe.android.paymentsheet.test")
    }

    @Test
    fun `updateSelection() posts mandate text when selected payment is us_bank_account`() {
        val viewModel = createViewModel()
        viewModel.updateSelection(
            PaymentSelection.Saved(
                PaymentMethodFixtures.US_BANK_ACCOUNT
            )
        )

        assertThat(viewModel.notesText.value)
            .isEqualTo(
                ACHText.getContinueMandateText(ApplicationProvider.getApplicationContext())
            )

        viewModel.updateSelection(
            PaymentSelection.New.GenericPaymentMethod(
                iconResource = 0,
                labelResource = "",
                paymentMethodCreateParams = PaymentMethodCreateParamsFixtures.US_BANK_ACCOUNT,
                customerRequestedSave = PaymentSelection.CustomerRequestedSave.NoRequest
            )
        )

        assertThat(viewModel.notesText.value)
            .isEqualTo(null)

        viewModel.updateSelection(
            PaymentSelection.Saved(
                PaymentMethodFixtures.CARD_PAYMENT_METHOD
            )
        )

        assertThat(viewModel.notesText.value)
            .isEqualTo(null)
    }

    private fun createViewModel(
        args: PaymentSheetContract.Args = ARGS_CUSTOMER_WITH_GOOGLEPAY,
        stripeIntent: StripeIntent = PAYMENT_INTENT,
        customerRepository: CustomerRepository = FakeCustomerRepository(PAYMENT_METHODS),
        shouldFailLoad: Boolean = false,
        linkState: LinkState? = null,
    ): PaymentSheetViewModel {
        val paymentConfiguration = PaymentConfiguration(ApiKeyFixtures.FAKE_PUBLISHABLE_KEY)
        return PaymentSheetViewModel(
            application,
            args,
            eventReporter,
            { paymentConfiguration },
            StripeIntentRepository.Static(stripeIntent),
            StripeIntentValidator(),
            FakePaymentSheetLoader(
                stripeIntent = stripeIntent,
                shouldFail = shouldFailLoad,
                linkState = linkState,
            ),
            customerRepository,
            prefsRepository,
            lpmResourceRepository,
            mock(),
            mock(),
            mock(),
            Logger.noop(),
            testDispatcher,
            DUMMY_INJECTOR_KEY,
            savedStateHandle = SavedStateHandle(),
            linkLauncher
        )
    }

    private companion object {
        private const val CLIENT_SECRET = PaymentSheetFixtures.CLIENT_SECRET
        private val ARGS_CUSTOMER_WITH_GOOGLEPAY_SETUP =
            PaymentSheetFixtures.ARGS_CUSTOMER_WITH_GOOGLEPAY_SETUP
        private val ARGS_CUSTOMER_WITH_GOOGLEPAY = PaymentSheetFixtures.ARGS_CUSTOMER_WITH_GOOGLEPAY
        private val ARGS_WITHOUT_CUSTOMER = PaymentSheetFixtures.ARGS_WITHOUT_CONFIG

        private val PAYMENT_METHODS = listOf(PaymentMethodFixtures.CARD_PAYMENT_METHOD)

        val PAYMENT_INTENT = PaymentIntentFixtures.PI_REQUIRES_PAYMENT_METHOD

        val PAYMENT_INTENT_WITH_PM = PaymentIntentFixtures.PI_SUCCEEDED.copy(
            paymentMethod = PaymentMethodFixtures.CARD_PAYMENT_METHOD
        )
        val PAYMENT_INTENT_RESULT_WITH_PM = PaymentIntentResult(
            intent = PAYMENT_INTENT_WITH_PM,
            outcomeFromFlow = StripeIntentResult.Outcome.SUCCEEDED
        )
    }
}
