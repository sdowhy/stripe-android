package com.stripe.android.ui.core.address.autocomplete

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.annotation.VisibleForTesting
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.stripe.android.ui.core.DefaultPaymentsTheme
import com.stripe.android.ui.core.elements.AutocompleteTextField

class AddressAutocompleteActivity : AppCompatActivity() {

    @VisibleForTesting
    internal var viewModelFactory: ViewModelProvider.Factory =
        AddressAutocompleteViewModel.Factory(
            { application },
            {
                requireNotNull(
                    AddressAutocompleteContract.Args.fromIntent(intent)
                )
            },
            this,
            intent?.extras
        )

    private val viewModel by viewModels<AddressAutocompleteViewModel> { viewModelFactory }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        lifecycleScope.launchWhenStarted {
            viewModel.addressResult.collect { result ->
                result?.fold(
                    onSuccess = {
                        val autoCompleteResult =
                            AddressAutocompleteResult.Succeeded(it)
                        setResult(
                            autoCompleteResult.resultCode,
                            Intent().putExtras(autoCompleteResult.toBundle())
                        )
                        finish()
                    },
                    onFailure = {
                        val autoCompleteResult =
                            AddressAutocompleteResult.Failed(it)
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
                AddressAutocompleteScreen()
            }
        }
    }

    @Composable
    fun AddressAutocompleteScreen() {
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
            ) {
                viewModel.selectPrediction(it)
            }
        }
    }
}