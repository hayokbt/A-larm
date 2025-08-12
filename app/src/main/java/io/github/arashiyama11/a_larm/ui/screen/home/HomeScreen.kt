package io.github.arashiyama11.a_larm.ui.screen.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun HomeScreen(
    state: HomeUiState,
    onToggleEnabled: (Boolean) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Top,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = "次のアラーム: ${state.nextAlarm}")
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("有効")
            Spacer(Modifier.height(4.dp))
            Switch(checked = state.enabled, onCheckedChange = onToggleEnabled)
        }
        Spacer(Modifier.height(24.dp))
        Spacer(Modifier.height(24.dp))
        Text(text = "ルーチン")
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            //Button(onClick = { onChangeRoutineMode(RoutineMode.Daily) }) { Text("1日単位") }
            //Button(onClick = { onChangeRoutineMode(RoutineMode.Weekly) }) { Text("週単位") }
        }
        Spacer(Modifier.height(8.dp))
        LazyColumn {
            //items(state.routine.tasks) { t -> Text("・$t") }
        }
    }
}
