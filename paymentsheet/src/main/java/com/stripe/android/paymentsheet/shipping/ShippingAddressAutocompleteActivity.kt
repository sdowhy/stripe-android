package com.stripe.android.paymentsheet.shipping

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.annotation.VisibleForTesting
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Divider
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.stripe.android.ui.core.DefaultPaymentsTheme
import com.stripe.android.ui.core.elements.TextFieldController
import com.stripe.android.ui.core.elements.TextFieldSection
import com.stripe.android.ui.core.elements.annotatedStringResource
import com.stripe.android.ui.core.paymentsColors

class ShippingAddressAutocompleteActivity : AppCompatActivity() {

    @VisibleForTesting
    internal var viewModelFactory: ViewModelProvider.Factory =
        ShippingAddressAutocompleteViewModel.Factory(
            { application },
            {
                requireNotNull(
                    ShippingAddressAutocompleteContract.Args.fromIntent(intent)
                )
            },
            this,
            intent?.extras
        )

    private val viewModel by viewModels<ShippingAddressAutocompleteViewModel> { viewModelFactory }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        lifecycleScope.launchWhenStarted {
            viewModel.shippingAddressResult.collect { result ->
                result?.fold(
                    onSuccess = {
                        val autoCompleteResult =
                            ShippingAddressAutocompleteResult.Succeeded(it)
                        setResult(
                            autoCompleteResult.resultCode,
                            Intent().putExtras(autoCompleteResult.toBundle())
                        )
                        finish()
                    },
                    onFailure = {
                        val autoCompleteResult =
                            ShippingAddressAutocompleteResult.Failed(it)
                        setResult(
                            autoCompleteResult.resultCode,
                            Intent().putExtras(autoCompleteResult.toBundle())
                        )
                        finish()
                    }
                )
            }
        }

        setContent {
            DefaultPaymentsTheme {
                ShippingAddressAutocompleteScreen()
            }
        }
    }

    @Composable
    fun AutocompleteTextField(
        controller: TextFieldController,
        loading: Boolean,
        predictions: List<AutocompletePrediction>,
        modifier: Modifier = Modifier
    ) {
        val query = controller.fieldValue.collectAsState(initial = "")

        Column(modifier = Modifier.fillMaxWidth()) {
            Box(modifier = modifier.fillMaxWidth()) {
                TextFieldSection(
                    textFieldController = controller,
                    imeAction = ImeAction.Done,
                    enabled = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            if (loading) {
                Row(
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    CircularProgressIndicator()
                }
            } else if (predictions.isNotEmpty()) {
                Divider(
                    modifier = Modifier.padding(vertical = 8.dp)
                )
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                ) {
                    predictions.forEach { prediction ->
                        val primaryText = prediction.primaryText
                        val secondaryText = prediction.secondaryText
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.selectPrediction(prediction)
                                }
                        ) {
                            val regex = query.value
                                .replace(" ", "|")
                                .toRegex(RegexOption.IGNORE_CASE)
                            val matches = regex.findAll(primaryText).toList()
                            val values = matches.map {
                                it.value
                            }.filter { it.isNotBlank() }
                            var text = primaryText
                            values.forEach {
                                text = text.replace(it, "<b>$it</b>")
                            }
                            Text(
                                text = annotatedStringResource(text = text),
                                color = MaterialTheme.paymentsColors.onComponent,
                                style = MaterialTheme.typography.body1,
                            )
                            Text(text = secondaryText)
                        }
                        Divider(
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }
                }
                viewModel.getPlacesPoweredByGoogleDrawable()?.let { drawable ->
                    Image(
                        painter = painterResource(
                            id = drawable
                        ),
                        contentDescription = null,
                        modifier = Modifier
                            .padding(top = 8.dp)
                            .padding(horizontal = 16.dp)
                    )
                }
            }
        }
    }

    @Composable
    fun ShippingAddressAutocompleteScreen() {
        val predictions by viewModel.predictions.collectAsState()
        val loading by viewModel.loading.collectAsState(initial = false)

        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            AutocompleteTextField(
                controller = viewModel.autocompleteController,
                loading = loading,
                predictions = predictions,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }
    }
}