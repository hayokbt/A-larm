package io.github.arashiyama11.a_larm.ui.screen.apikey

import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle


@Composable
fun LlmApiKeyScreen(
    viewModel: LlmApiKeyViewModel = hiltViewModel(),
    back: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val events by viewModel.events.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(events) {
        when (val e = events) {
            LlmApiKeyEvent.Saved -> {
                snackbarHostState.showSnackbar("APIキーを保存しました")
                viewModel.consumeEvent()
            }

            LlmApiKeyEvent.Cleared -> {
                snackbarHostState.showSnackbar("APIキーを削除しました")
                viewModel.consumeEvent()
            }

            is LlmApiKeyEvent.Error -> {
                snackbarHostState.showSnackbar(e.message)
                viewModel.consumeEvent()
            }

            null -> Unit
        }
    }

    val context = LocalContext.current

    LlmApiKeyScreen(
        uiState = uiState,
        onInputChange = viewModel::onInputChanged,
        onToggleMask = viewModel::toggleMask,
        onSave = viewModel::save,
        onClear = viewModel::clear,
        onPasteRequested = {
            requestClipboardPaste(context) {
                viewModel.pasteFromClipboard(
                    it
                )
            }
        },
        back = back,
        snackbarHostState = snackbarHostState
    )
}

private fun requestClipboardPaste(
    context: Context,
    onPasted: (String) -> Unit
) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = cm.primaryClip
    val desc = cm.primaryClipDescription
    if (clip != null && desc != null && desc.hasMimeType(ClipDescription.MIMETYPE_TEXT_PLAIN)) {
        val text = clip.getItemAt(0).coerceToText(context).toString()
        onPasted(text)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LlmApiKeyScreen(
    uiState: LlmApiKeyUiState,
    onInputChange: (String) -> Unit,
    onToggleMask: () -> Unit,
    onSave: () -> Unit,
    onClear: () -> Unit,
    onPasteRequested: () -> Unit,
    back: () -> Unit,
    snackbarHostState: SnackbarHostState,
) {
    Scaffold(
        topBar = {
            TopAppBar(title = {
                Row {
                    IconButton(
                        onClick = back,
                        modifier = Modifier.testTag("backButton")
                    ) {
                        Icon(Icons.AutoMirrored.Default.ArrowBack, contentDescription = "戻る")
                    }

                    Text("LLM APIキー")
                }
            })
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { inner ->
        Column(
            modifier = Modifier
                .padding(inner)
                .padding(16.dp)
                .fillMaxSize(),
            verticalArrangement = Arrangement.Top
        ) {

            OutlinedTextField(
                value = uiState.input,
                onValueChange = onInputChange,
                label = { Text("APIキー") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("apiKeyField"),
                isError = uiState.error != null,
                supportingText = {
                    val msg = uiState.error
                        ?: if (uiState.savedKeyExists) "保存済み" else "未保存"
                    Text(msg)
                },
                visualTransformation = if (uiState.isInputMasked)
                    PasswordVisualTransformation() else VisualTransformation.None,
                trailingIcon = {
                    val image = if (uiState.isInputMasked)
                        Icons.Filled.Visibility else Icons.Filled.VisibilityOff
                    IconButton(onClick = onToggleMask) {
                        Icon(imageVector = image, contentDescription = "表示切替")
                    }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            )

            Spacer(Modifier.height(12.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FilledTonalButton(
                    onClick = onPasteRequested,
                    content = {
                        Icon(Icons.Default.ContentPaste, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("貼り付け")
                    }
                )
                Button(
                    onClick = onSave,
                    enabled = !uiState.isSaving && uiState.input.isNotBlank(),
                    content = {
                        Icon(Icons.Default.Save, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(if (uiState.isSaving) "保存中..." else "保存")
                    }
                )
                OutlinedButton(
                    onClick = onClear,
                    enabled = uiState.savedKeyExists,
                    content = {
                        Icon(Icons.Default.Delete, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("削除")
                    }
                )
            }

            Spacer(Modifier.height(8.dp))
            AssistChipRow(uiState)
        }
    }
}

@Composable
private fun AssistChipRow(uiState: LlmApiKeyUiState) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        AssistChip(
            onClick = { /* no-op: ここでヘルプなどに遷移しても良い */ },
            label = { Text("ヒント: 貼り付け→保存") },
        )
        if (uiState.savedKeyExists) {
            AssistChip(
                onClick = {},
                label = { Text("保存済み") }
            )
        }
    }
}