package io.github.arashiyama11.a_larm.ui.screen.routine

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.arashiyama11.a_larm.domain.RoutineRepository
import io.github.arashiyama11.a_larm.domain.models.AlarmId
import io.github.arashiyama11.a_larm.domain.models.AlarmRule
import io.github.arashiyama11.a_larm.domain.models.CellKey
import io.github.arashiyama11.a_larm.domain.models.RoutineEntry
import io.github.arashiyama11.a_larm.domain.models.RoutineGrid
import io.github.arashiyama11.a_larm.domain.models.RoutineMode
import io.github.arashiyama11.a_larm.domain.models.RoutineType
import io.github.arashiyama11.a_larm.domain.usecase.AlarmRulesUseCase
import kotlinx.coroutines.ExperimentalCoroutinesApi
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

fun entryToRule(
    mode: RoutineMode,
    key: CellKey,
    entry: RoutineEntry
): AlarmRule {
    return AlarmRule(
        mode = mode,
        dayIndex = when (mode) {
            RoutineMode.DAILY -> 0
            RoutineMode.WEEKLY -> key.dayIndex
        },
        hour = key.hour,
        type = entry.type,
        label = entry.label.takeIf { it.isNotEmpty() },
        minute = entry.minute,
        id = AlarmId(0)
    )
}

fun List<AlarmRule>.toGrid(): RoutineGrid {
    println("Converting AlarmRules to RoutineGrid: $this")
    return this.associate { rule ->
        CellKey(rule.dayIndex, rule.hour) to RoutineEntry(
            type = rule.type,
            label = rule.label.orEmpty(),
            minute = rule.minute,
            id = rule.id
        )
    }
}

fun RoutineGrid.toAlarmRules(mode: RoutineMode): List<AlarmRule> {
    return this.map { (key, entry) ->
        AlarmRule(
            mode = mode,
            dayIndex = when (mode) {
                RoutineMode.DAILY -> 0
                RoutineMode.WEEKLY -> key.dayIndex
            },
            hour = key.hour,
            type = entry.type,
            label = entry.label.takeIf { it.isNotEmpty() },
            minute = entry.minute,
            id = AlarmId(0)
        )
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class RoutineViewModel @Inject constructor(
    private val routineRepository: RoutineRepository,
    private val alarmRulesUseCase: AlarmRulesUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(RoutineUiState())
    val uiState = _uiState.asStateFlow()


    init {
        routineRepository.getRoutineMode().flatMapLatest { mode ->
            routineRepository.load(mode).map { it.toGrid() to mode }
        }.onEach { (grid, mode) ->
            _uiState.update { it.copy(mode = mode, grid = grid) }
        }.launchIn(viewModelScope)
    }


    fun onCellClick(key: CellKey) {
        val current = _uiState.value.grid[key] ?: RoutineEntry(id = AlarmId(0))
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
        println("alarm grid: ${_uiState.value.grid}")

        val newGrid = _uiState.value.grid.toMutableMap().apply {
            if (e.working.type == RoutineType.NONE) {
                remove(e.key)
            } else {
                put(e.key, e.working)
            }
        }.toMap()

        _uiState.update { it.copy(grid = newGrid, isSaving = true) }
        routineRepository.replaceAll(newGrid.toAlarmRules(_uiState.value.mode))
        routineRepository.setRoutineMode(_uiState.value.mode)
        val mode = _uiState.value.mode
        entryToRule(mode, e.key, e.working).let { rule ->
            println("updating alarm rule: $rule")
            if (rule.type == RoutineType.NONE) {
                alarmRulesUseCase.removeAndCancel(rule.id)
            } else {
                alarmRulesUseCase.upsertAndReschedule(rule)
            }
        }
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
            alarmRulesUseCase.rescheduleAll(mode)
            alarmRulesUseCase.cancelAll(mode.otherwise)
        }
    }

}
