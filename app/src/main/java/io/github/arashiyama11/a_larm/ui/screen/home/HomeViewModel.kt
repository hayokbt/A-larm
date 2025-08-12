package io.github.arashiyama11.a_larm.ui.screen.home

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

//import io.github.arashiyama11.a_larm.ui.screen.routine.RoutineMode
//import io.github.arashiyama11.a_larm.ui.screen.routine.RoutineUiState

data class HomeUiState(
    val nextAlarm: String = "--:--",
    val enabled: Boolean = false,
    //val routine: RoutineUiState = RoutineUiState(),
)

@HiltViewModel
class HomeViewModel @Inject constructor() : ViewModel() {
    var uiState: HomeUiState = HomeUiState(nextAlarm = "07:00", enabled = true)
        private set

    fun onToggleEnabled(newValue: Boolean) {
        uiState = uiState.copy(enabled = newValue)
    }

//    fun setRoutineMode(mode: RoutineMode) {
//        uiState = uiState.copy(routine = uiState.routine.copy(mode = mode))
//    }
}
