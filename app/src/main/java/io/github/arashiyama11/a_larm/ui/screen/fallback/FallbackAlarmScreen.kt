package io.github.arashiyama11.a_larm.ui.screen.fallback

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun FallbackAlarmScreen(
    state: FallbackAlarmUiState,
    onBack: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(if (state.ringing) "アラーム鳴動中" else "停止中")
        Spacer(Modifier.height(16.dp))
        Button(onClick = { /* 停止 */ onBack() }) { Text("停止") }
        Spacer(Modifier.height(8.dp))
        Button(onClick = { /* スヌーズ */ }) { Text("スヌーズ") }
    }
}

