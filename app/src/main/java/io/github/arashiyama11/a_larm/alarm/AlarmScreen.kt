package io.github.arashiyama11.a_larm.alarm

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.arashiyama11.a_larm.domain.LlmVoiceChatState
import io.github.arashiyama11.a_larm.domain.models.ConversationTurn
import io.github.arashiyama11.a_larm.domain.models.Role
import io.github.arashiyama11.a_larm.ui.theme.AlarmTheme
import kotlinx.coroutines.delay
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlin.time.ExperimentalTime


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
        AlarmPhase.FALLBACK_ALARM -> {
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


@OptIn(ExperimentalTime::class)
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
            verticalArrangement = Arrangement.Top
        ) {

            Spacer(Modifier.height(128.dp))
            CurrentTimeText()
            Spacer(Modifier.height(16.dp))

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

            val showProgress = when (uiState.chatState) {
                LlmVoiceChatState.INITIALIZING -> true
                else -> false
            }
            if (showProgress) {
                CircularProgressIndicator()
                Spacer(Modifier.height(8.dp))
            }

            // --- ここから会話リスト（新しい発言が上） ---
            ChatList(
                turns = uiState.assistantTalk,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            )
            // --- ここまで会話リスト ---

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

@Composable
private fun ChatList(turns: List<ConversationTurn>, modifier: Modifier = Modifier) {
    val messages = remember(turns) { turns.asReversed() }
    val listState = rememberLazyListState()

    // 新しいメッセージが追加されたら先頭へスクロール
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            // 0 にスクロール（最新が先頭）
            listState.animateScrollToItem(0)
        }
    }

    LazyColumn(
        state = listState,
        reverseLayout = false, // items を逆順に渡しているのでここは false
        contentPadding = PaddingValues(vertical = 8.dp),
        verticalArrangement = Arrangement.Top,
        modifier = modifier
    ) {
        items(items = messages, key = { it.hashCode() }) { turn ->
            ChatRow(turn = turn)
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun ChatRow(turn: ConversationTurn) {
    val isUser = when (turn.role) {
        Role.User -> true
        Role.Assistant -> false
        Role.System -> false
    }

    Row(
        modifier = Modifier
            .fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        if (!isUser) {
            // Assistant: avatar on left
            ChatBubble(turn = turn, alignLeft = true)
        } else {
            // User: bubble on right
            ChatBubble(turn = turn, alignLeft = false)
        }
    }
}

@Composable
private fun ChatBubble(turn: ConversationTurn, alignLeft: Boolean) {
    val textColor = MaterialTheme.colorScheme.onSurfaceVariant
    val shape = RoundedCornerShape(12.dp)
    if (turn.text.isBlank()) return
    Row(
        verticalAlignment = Alignment.Top,
        horizontalArrangement = if (alignLeft) Arrangement.Start else Arrangement.End,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp)
    ) {
        if (alignLeft) {
            // small icon + bubble
            RoleIcon(turn.role)
            Spacer(Modifier.width(8.dp))
            Surface(
                tonalElevation = 2.dp,
                shape = shape,
                modifier = Modifier
                    .widthIn(max = (0.75f * LocalConfiguration.current.screenWidthDp).dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = roleLabel(turn.role),
                        style = MaterialTheme.typography.labelSmall,
                        color = textColor//MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = turn.text,
                        color = textColor,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }
        } else {
            // bubble then icon
            Surface(
                tonalElevation = 2.dp,
                shape = shape,
                modifier = Modifier
                    .widthIn(max = (0.75f * LocalConfiguration.current.screenWidthDp).dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = roleLabel(turn.role),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.9f)
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = turn.text,
                        color = textColor,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            RoleIcon(turn.role)
        }
    }
}

@Composable
private fun RoleIcon(role: Role) {
    val label = when (role) {
        Role.User -> "U"
        Role.Assistant -> "A"
        Role.System -> "S"
    }
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center
    ) {
        Text(text = label, style = MaterialTheme.typography.labelMedium)
    }
}

private fun roleLabel(role: Role): String = when (role) {
    Role.User -> "あなた"
    Role.Assistant -> "アシスタント"
    Role.System -> "システム"
}

private fun phaseLabel(phase: AlarmPhase): String = when (phase) {
    AlarmPhase.RINGING -> "アラーム鳴動中"
    AlarmPhase.TALKING -> "会話中"
    AlarmPhase.SUCCESS -> "完了"
    AlarmPhase.NIDONE -> "二度寝モード"
    AlarmPhase.FAILED_RESPONSE -> "応答エラー"
    AlarmPhase.FAILED_TTS -> "音声合成エラー"
    AlarmPhase.FALLBACK_ALARM -> "フォールバックアラーム"
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


@OptIn(ExperimentalTime::class)
@Preview
@Composable
fun AlarmScreenPreview() {
    AlarmTheme {
        AlarmScreen(
            uiState = AlarmUiState(
                phase = AlarmPhase.RINGING,
                chatState = LlmVoiceChatState.INITIALIZING,
                startAt = null,
                assistantTalk = listOf(
                    ConversationTurn(
                        role = Role.Assistant,
                        text = "おはようございます！今日はどんな予定がありますか？"
                    ),
                    ConversationTurn(
                        role = Role.User,
                        text = "今日は仕事が忙しいです。"
                    ),
                )
            ),
            onAction = {},
            finish = {}
        )
    }
}
