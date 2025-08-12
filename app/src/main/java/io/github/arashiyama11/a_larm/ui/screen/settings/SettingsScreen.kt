package io.github.arashiyama11.a_larm.ui.screen.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle


@Composable
fun SettingsScreen(
    settingsViewModel: SettingsViewModel = hiltViewModel(),
    navigateToApiKeySetting: () -> Unit,
) {
    val uiState by settingsViewModel.uiState.collectAsStateWithLifecycle()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Top
    ) {
        Text("キャラクター: ${uiState.character}（選択ダイアログ予定）")
        Spacer(Modifier.height(16.dp))
        Button(onClick = settingsViewModel::ttsTest) { Text("TTS試聴") }
        Spacer(Modifier.height(16.dp))
        Button(onClick = settingsViewModel::setAlarmTest) {
            Text("アラームテスト。10秒後にに1回鳴ります")
        }
        Spacer(Modifier.height(16.dp))
        Text("言語/ロケール: ${uiState.language}")
        Spacer(Modifier.height(16.dp))
        Text("通知/音量")
        Slider(value = uiState.volume, onValueChange = { /* no-op */ })
        Spacer(Modifier.height(16.dp))
        Button(onClick = navigateToApiKeySetting) { Text("APIキー管理") }
        Spacer(Modifier.height(16.dp))
        Button(onClick = { /* プライバシー */ }) { Text("プライバシー") }
        Spacer(Modifier.height(16.dp))
        Button(onClick = { /* データ削除 */ }) { Text("データ削除") }
        Spacer(Modifier.height(24.dp))
        Button(onClick = {}) { Text("戻る") }
    }
}
