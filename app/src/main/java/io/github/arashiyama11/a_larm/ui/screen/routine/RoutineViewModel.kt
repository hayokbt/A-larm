package io.github.arashiyama11.a_larm.ui.screen.routine

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.arashiyama11.a_larm.domain.RoutineRepository
import io.github.arashiyama11.a_larm.domain.models.CellKey
import io.github.arashiyama11.a_larm.domain.models.RoutineEntry
import io.github.arashiyama11.a_larm.domain.models.RoutineGrid
import io.github.arashiyama11.a_larm.domain.models.RoutineMode
import io.github.arashiyama11.a_larm.domain.models.RoutineType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class EditState(
    val key: CellKey,
    val working: RoutineEntry
)

data class RoutineUiState(
    val mode: RoutineMode = RoutineMode.DAILY,
    val grid: RoutineGrid = emptyMap(),
    val editing: EditState? = null,
    val isSaving: Boolean = false
)

@HiltViewModel
class RoutineViewModel @Inject constructor(
    private val routineRepository: RoutineRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(RoutineUiState())
    val uiState = _uiState.asStateFlow()

    init {
        routineRepository.getRoutineMode().flatMapLatest { mode ->
            routineRepository.load(mode).map { it to mode }
        }.onEach { (grid, mode) ->
            _uiState.update { it.copy(mode = mode, grid = grid) }
        }.launchIn(viewModelScope)
    }


    fun onCellClick(key: CellKey) {
        val current = _uiState.value.grid[key] ?: RoutineEntry()
        _uiState.update { it.copy(editing = EditState(key, current)) }
    }

    fun onEditTypeSelected(type: RoutineType) {
        _uiState.update { st ->
            val e = st.editing ?: return
            st.copy(editing = e.copy(working = e.working.copy(type = type)))
        }
    }

    fun onEditLabelChange(newLabel: String) {
        _uiState.update { st ->
            val e = st.editing ?: return
            st.copy(editing = e.copy(working = e.working.copy(label = newLabel)))
        }
    }

    fun onEditDismiss() {
        _uiState.update { it.copy(editing = null) }
    }

    fun onEditApply() = viewModelScope.launch {
        val e = _uiState.value.editing ?: return@launch
        val newGrid = _uiState.value.grid.toMutableMap().apply {
            if (e.working.type == RoutineType.NONE) {
                remove(e.key)
            } else {
                put(e.key, e.working)
            }
        }.toMap()

        _uiState.update { it.copy(grid = newGrid, isSaving = true) }
        routineRepository.save(_uiState.value.mode, newGrid)
        routineRepository.setRoutineMode(_uiState.value.mode)
        _uiState.update { it.copy(isSaving = false, editing = null) }
    }

    fun onEditMinuteChange(minute: Int) {
        _uiState.update { st ->
            val e = st.editing ?: return
            st.copy(editing = e.copy(working = e.working.copy(minute = minute.coerceIn(0, 59))))
        }
    }

    fun changeMode(mode: RoutineMode) {
        viewModelScope.launch {
            routineRepository.setRoutineMode(mode)
        }
    }

}
