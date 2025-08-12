package io.github.arashiyama11.a_larm.ui.screen.apikey

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.arashiyama11.a_larm.domain.LlmApiKeyRepository
import jakarta.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch


data class LlmApiKeyUiState(
    val input: String = "",
    val savedKeyExists: Boolean = false,
    val isInputMasked: Boolean = true,
    val isSaving: Boolean = false,
    val error: String? = null
)

sealed interface LlmApiKeyEvent {
    data object Saved : LlmApiKeyEvent
    data object Cleared : LlmApiKeyEvent
    data class Error(val message: String) : LlmApiKeyEvent
}

@HiltViewModel
class LlmApiKeyViewModel @Inject constructor(
    private val repo: LlmApiKeyRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(LlmApiKeyUiState())
    val uiState: StateFlow<LlmApiKeyUiState> = _uiState

    // one-shotイベントは外部でcollectする（SnackBar用）
    private val _events = MutableStateFlow<LlmApiKeyEvent?>(null)
    val events: StateFlow<LlmApiKeyEvent?> = _events

    init {
        viewModelScope.launch {
            val current = repo.getKey()
            _uiState.update {
                it.copy(
                    input = current.orEmpty(),
                    savedKeyExists = !current.isNullOrEmpty()
                )
            }
        }
    }

    fun onInputChanged(newValue: String) {
        _uiState.update { it.copy(input = newValue, error = null) }
    }

    fun toggleMask() {
        _uiState.update { it.copy(isInputMasked = !it.isInputMasked) }
    }

    fun pasteFromClipboard(text: String) {
        if (text.isNotBlank()) {
            onInputChanged(text.trim())
        }
    }

    fun save() {
        val key = uiState.value.input.trim()
        // ざっくりバリデーション（必要なら強化）
        if (key.length < 8) {
            _uiState.update { it.copy(error = "キーが短すぎます") }
            _events.value = LlmApiKeyEvent.Error("キーが短すぎます")
            return
        }

        viewModelScope.launch {
            runCatching {
                _uiState.update { it.copy(isSaving = true) }
                repo.setKey(key)
                val exists = !repo.getKey().isNullOrEmpty()
                _uiState.update { it.copy(savedKeyExists = exists, isSaving = false) }
                _events.value = LlmApiKeyEvent.Saved
            }.onFailure { e ->
                _uiState.update { it.copy(isSaving = false, error = e.message) }
                _events.value = LlmApiKeyEvent.Error(e.message ?: "保存に失敗しました")
            }
        }
    }

    fun clear() {
        viewModelScope.launch {
            runCatching {
                repo.clearKey()
                _uiState.update { it.copy(input = "", savedKeyExists = false) }
                _events.value = LlmApiKeyEvent.Cleared
            }.onFailure { e ->
                _events.value = LlmApiKeyEvent.Error(e.message ?: "削除に失敗しました")
            }
        }
    }

    fun consumeEvent() {
        _events.value = null
    }
}