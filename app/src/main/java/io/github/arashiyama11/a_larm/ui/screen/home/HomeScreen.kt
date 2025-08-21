package io.github.arashiyama11.a_larm.ui.screen.home

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.arashiyama11.a_larm.alarm.AlarmRoute

@Composable
fun HomeScreen(
    homeViewModel: HomeViewModel = hiltViewModel()
) {

    LaunchedEffect(Unit) {
        homeViewModel.onStart()
    }


    val uiState by homeViewModel.uiState.collectAsStateWithLifecycle()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Top,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = "次のアラーム: ${uiState.nextAlarm}")
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("有効")
            Spacer(Modifier.height(4.dp))
            //Switch(checked = uiState.enabled, onCheckedChange = onToggleEnabled)
        }
        Text("モード: ${uiState.mode.name}")
        Spacer(Modifier.height(24.dp))
        Spacer(Modifier.height(24.dp))
        Text(text = "ルーチン")
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            //Button(onClick = { onChangeRoutineMode(RoutineMode.Daily) }) { Text("1日単位") }
            //Button(onClick = { onChangeRoutineMode(RoutineMode.Weekly) }) { Text("週単位") }
        }
        Spacer(Modifier.height(8.dp))

        Text("デバッグでここにアラーム用UIを表示")
        Box(
            modifier = Modifier
                .border(
                    1.dp, MaterialTheme.colorScheme.primary,
                    RoundedCornerShape(4.dp)
                )
                .padding(8.dp)
        ) {
            AlarmRoute { }
        }
    }
}