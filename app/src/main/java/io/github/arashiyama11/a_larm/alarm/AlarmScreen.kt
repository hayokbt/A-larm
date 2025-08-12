package io.github.arashiyama11.a_larm.alarm

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.arashiyama11.a_larm.ui.theme.AlarmTheme


@Composable
fun AlarmScreen(alarmViewModel: AlarmViewModel = hiltViewModel(), finish: () -> Unit) {
    val uiState by alarmViewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        alarmViewModel.onStart()
    }

    Scaffold() { contentPadding ->
        Column(
            modifier = androidx.compose.ui.Modifier
                .fillMaxSize()
                .padding(contentPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("ここはアラームのUI")
            OutlinedButton(
                onClick = { finish() },
                modifier = androidx.compose.ui.Modifier.padding(8.dp)
            ) {
                Text("戻る")
            }
        }
    }
}

@Preview
@Composable
fun AlarmScreenPreview() {
    AlarmTheme {
        AlarmScreen(finish = {})
    }
}