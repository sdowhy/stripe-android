package com.stripe.android.ui.core.elements

import androidx.annotation.RestrictTo
import androidx.annotation.StringRes
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import com.stripe.android.ui.core.forms.FormFieldEntry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

/**
 * This class will provide the onValueChanged and onFocusChanged functionality to the field's
 * composable.  These functions will update the observables as needed.  It is responsible for
 * exposing immutable observers for its data
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP_PREFIX)
class JsTextFieldController constructor(
    private val jsEngine: JsEngine,
    private val jsResponseFlow: Flow<JsResponse>,
    private val textFieldConfig: TextFieldConfig,
    private val identifierSpec: IdentifierSpec,
    override val showOptionalLabel: Boolean = false,
    initialValue: String? = null,
    val onChangeJs: String? = null,
    val textChangeFnId: String? = null
) : TextFieldController(textFieldConfig, showOptionalLabel, initialValue) {
    override val capitalization: KeyboardCapitalization = textFieldConfig.capitalization
    override val keyboardType: KeyboardType = textFieldConfig.keyboard
    override val visualTransformation =
        textFieldConfig.visualTransformation ?: VisualTransformation.None

    @StringRes
    override val label: Int = textFieldConfig.label
    override val debugLabel = textFieldConfig.debugLabel

    /** This is all the information that can be observed on the element */
    private val _fieldValue = jsResponseFlow.map { it.field }
    override val fieldValue: Flow<String> = _fieldValue

    val _rawFieldValue = jsResponseFlow.map { it.rawFieldValue }
    override val rawFieldValue: Flow<String> = _rawFieldValue

    private val _fieldState = jsResponseFlow.map { it.state }

    private val _hasFocus = MutableStateFlow(false)

    override val visibleError: Flow<Boolean> =
        combine(_fieldState, _hasFocus) { fieldState, hasFocus ->
            fieldState.shouldShowError(hasFocus)
        }

    /**
     * An error must be emitted if it is visible or not visible.
     **/
    override val error: Flow<FieldError?> =
        combine(_fieldState, visibleError) { fieldState, visibleError ->
            fieldState.getError()?.takeIf { visibleError }
        }

    override val isFull: Flow<Boolean> = _fieldState.map { it.isFull() }

    override val isComplete: Flow<Boolean> = _fieldState.map {
        it.isValid() || (!it.isValid() && showOptionalLabel && it.isBlank())
    }

    override val formFieldValue: Flow<FormFieldEntry> =
        combine(isComplete, rawFieldValue) { complete, value ->
            FormFieldEntry(value, complete)
        }

    init {
        initialValue?.let { onRawValueChange(it) }
    }

    /**
     * This is called when the value changed to is a display value.
     */
    override fun onValueChange(displayFormatted: String) {

        // This is the approach using remote-ui

//        val fullJavascript = "javascript:" +
//            "(function(e) ${javascript})(${format.encodeToString(event)});"
        val fullJavascript = "javascript:" +
            "(function(e) {" +
            "window.parentIFrame.postMessage(\"5,1234,${this.textChangeFnId}\"); })();"
//            "window.remoteEndpoint.encoder.call(\"${this.textChangeFnId}\"); })();"
        //  "(function() { console.log('Is ui-text-field undefined: ' + document.querySelector('ui-text-field') == undefined); })();"

        jsEngine.runJavascript(fullJavascript)

        // This is a data driven approach with local js code execution
        onChangeJs?.let {
            jsEngine.requestCustomServer(
                it,
                JsRequest.Event(
                    JsRequest.Target(
                        id = identifierSpec.value,
                        value = displayFormatted,
                    )
                )
            )
        }
    }

    /**
     * This is called when the value changed to is a raw backing value, not a display value.
     */
    override fun onRawValueChange(rawValue: String) {
//        jsRawValueRequest.emit(rawValue)
    }

    override fun onFocusChange(newHasFocus: Boolean) {
        _hasFocus.value = newHasFocus
    }
}