package io.github.arashiyama11.a_larm.alarm

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.arashiyama11.a_larm.domain.LlmVoiceChatState
import io.github.arashiyama11.a_larm.ui.theme.AlarmTheme
import kotlinx.coroutines.delay
import java.time.LocalTime
import java.time.format.DateTimeFormatter


@Composable
fun AlarmRoute(
    alarmViewModel: AlarmViewModel = hiltViewModel(),
    finish: () -> Unit
) {
    val uiState by alarmViewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        alarmViewModel.reduce(AlarmUiAction.Start)
    }

    when (uiState.phase) {
        AlarmPhase.FAILED_RESPONSE -> {
            FallbackAlarmRoute(onFinish = finish)
        }

        else -> {
            AlarmScreen(
                uiState = uiState,
                onAction = alarmViewModel::reduce,
                finish = finish
            )
        }
    }
}


@Composable
fun AlarmScreen(
    uiState: AlarmUiState,
    onAction: (AlarmUiAction) -> Unit,
    finish: () -> Unit
) {
    Scaffold { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {

            CurrentTimeText()
            Spacer(Modifier.height(16.dp))
            // Title and phase
            Text(
                text = phaseLabel(uiState.phase),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = chatLabel(uiState.chatState),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
            Spacer(Modifier.height(12.dp))

            // Progress indicator during talking/initializing
            val showProgress = when (uiState.chatState) {
                LlmVoiceChatState.INITIALIZING -> true

                else -> false
            }
            if (showProgress) {
                CircularProgressIndicator()
                Spacer(Modifier.height(8.dp))
            }

//            if (uiState.chatState == LlmVoiceChatState.ASSISTANT_SPEAKING) {
//                Text("なんか喋ってる風の表示")
//                Spacer(Modifier.height(8.dp))
//            }

            Text(
                uiState.assistantTalk.joinToString("\n"),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                modifier = Modifier.verticalScroll(rememberScrollState())
            )

            uiState.startAt?.let { startAt ->
                val fmt = DateTimeFormatter.ofPattern("HH:mm:ss")
                Text(
                    text = "開始: ${startAt.format(fmt)}",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
                Spacer(Modifier.height(16.dp))
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = { finish() }) { Text("停止") }
            }
        }
    }
}

private fun phaseLabel(phase: AlarmPhase): String = when (phase) {
    AlarmPhase.RINGING -> "アラーム鳴動中"
    AlarmPhase.TALKING -> "会話中"
    AlarmPhase.SUCCESS -> "完了"
    AlarmPhase.NIDONE -> "二度寝モード"
    AlarmPhase.FAILED_RESPONSE -> "応答エラー"
    AlarmPhase.FAILED_TTS -> "音声合成エラー"
}

private fun chatLabel(state: LlmVoiceChatState): String = when (state) {
    LlmVoiceChatState.IDLE -> "待機中"
    LlmVoiceChatState.INITIALIZING -> "初期化中..."
    LlmVoiceChatState.STOPPING -> "停止処理中"
    LlmVoiceChatState.ERROR -> "セッションエラー"
    LlmVoiceChatState.ACTIVE -> "会話中"
}


@Composable
fun CurrentTimeText(modifier: Modifier = Modifier) {
    val formatter = remember { DateTimeFormatter.ofPattern("HH:mm") }
    val now = remember { mutableStateOf(LocalTime.now()) }

    LaunchedEffect(Unit) {
        while (true) {
            now.value = LocalTime.now()
            delay(1_000L)
        }
    }

    Text(
        text = now.value.format(formatter),
        style = MaterialTheme.typography.displayLarge,
        modifier = modifier
    )
}


@Preview
@Composable
fun AlarmScreenPreview() {
    AlarmTheme {
        AlarmScreen(
            uiState = AlarmUiState(
                phase = AlarmPhase.RINGING,
                chatState = LlmVoiceChatState.INITIALIZING,
                startAt = null
            ),
            onAction = {},
            finish = {}
        )
    }
}
