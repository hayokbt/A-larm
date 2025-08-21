package io.github.arashiyama11.a_larm.alarm

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import java.time.Duration
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter


@Composable
fun FallbackAlarmRoute(
    modifier: androidx.compose.ui.Modifier = androidx.compose.ui.Modifier,
    viewModel: FallbackAlarmViewModel = hiltViewModel(),
    // Activity などから渡せる終了ハンドラ（任意）
    onFinish: () -> Unit = {}
) {
    val ui = viewModel.uiState.collectAsStateWithLifecycle().value

    // 画面入場で鳴動開始（多重開始をVM側で防止）
    LaunchedEffect(Unit) { viewModel.startRinging() }

    FallbackAlarmScreenRich(
        modifier = modifier,
        uiState = ui,
        onStop = {
            viewModel.stopRinging()
            onFinish()
        },
        onSnooze = { minutes ->
            viewModel.snooze(minutes)
            onFinish()
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FallbackAlarmScreenRich(
    uiState: FallbackAlarmUiState,
    onStop: () -> Unit,
    onSnooze: (minutes: Int) -> Unit,
    modifier: Modifier = Modifier,
    // 見た目調整
    topPadding: Dp = 24.dp
) {
    // 時刻フォーマッタ（秒表示）
    val timeFormatter = remember { DateTimeFormatter.ofPattern("HH:mm:ss") }
    val startedAt = uiState.startedAt

    // 現在時刻を1秒ごとに更新して表示
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = LocalDateTime.now()
            delay(1000L)
        }
    }

    // 経過時間（秒）
    val elapsed: Duration? =
        startedAt?.let { Duration.between(it, now).coerceAtLeast(Duration.ZERO) }

    Scaffold(
        modifier = modifier,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("アラーム", style = MaterialTheme.typography.titleMedium) },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = colorScheme.surfaceColorAtElevation(3.dp)
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp)
                .padding(top = topPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // 大きな時刻（中央）
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxWidth()) {
                // Circular decorative ring behind time
                val screenWidth = LocalConfiguration.current.screenWidthDp.dp
                val ringSize = screenWidth * 0.6f
                Box(
                    modifier = Modifier
                        .size(ringSize)
                        .clip(CircleShape)
                        .background(colorScheme.primary.copy(alpha = 0.06f))
                )

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = now.format(timeFormatter),
                        fontSize = 52.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = colorScheme.onSurface
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // ラベル
                    uiState.label?.takeIf { it.isNotBlank() }?.let { label ->
                        Text(
                            text = label,
                            style = MaterialTheme.typography.titleMedium,
                            color = colorScheme.onSurfaceVariant
                        )
                    }

                    // 経過時間表示
                    elapsed?.let {
                        val hh = it.toHours()
                        val mm = (it.toMinutes() % 60)
                        val ss = (it.seconds % 60)
                        val elapsedText = buildString {
                            if (hh > 0) append("%02d:".format(hh))
                            append("%02d:%02d".format(mm, ss))
                        }
                        Text(
                            text = "経過: $elapsedText",
                            style = MaterialTheme.typography.labelLarge,
                            color = colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                    }
                }
            }

            // Waveform / visualizer
            AlarmWaveform(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(80.dp)
            )

            // Controls: Stop (prominent) & Snooze chips
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(
                        12.dp,
                        Alignment.CenterHorizontally
                    )
                ) {
                    // Snooze chips
                    SnoozeChip(minutes = 3, onSnooze = onSnooze)
                    SnoozeChip(minutes = 5, onSnooze = onSnooze)
                    SnoozeChip(minutes = 10, onSnooze = onSnooze)
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Stop button large
                Button(
                    onClick = onStop,
                    modifier = Modifier
                        .fillMaxWidth(0.7f)
                        .height(56.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = colorScheme.error),
                    shape = RoundedCornerShape(28.dp),
                    contentPadding = PaddingValues(8.dp)
                ) {
                    Text("停止", color = Color.White, style = MaterialTheme.typography.titleMedium)
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // Small hint / swipe to dismiss hint
            Text(
                text = "画面を閉すには停止を押してください",
                style = MaterialTheme.typography.bodySmall,
                color = colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }
    }
}


@Composable
private fun SnoozeChip(minutes: Int, onSnooze: (Int) -> Unit) {
    AssistChip(
        onClick = { onSnooze(minutes) },
        label = { Text("${minutes}分スヌーズ") },
        leadingIcon = null
    )
}

/**
 * シンプルなアニメ波形（擬似的に高さが揺れるバーを描画）
 */
@Composable
private fun AlarmWaveform(modifier: Modifier = Modifier) {
    val barCount = 20
    val infinite = rememberInfiniteTransition()
    // generate per-bar phase to make pattern lively
    val phases = remember {
        List(barCount) { it * 37L % 101 } // pseudo-random phases
    }

    val animValues = phases.map { phase ->
        infinite.animateFloat(
            initialValue = 0.2f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(
                    durationMillis = 800 + (phase % 200).toInt(),
                    easing = FastOutSlowInEasing
                ),
                repeatMode = RepeatMode.Reverse,
                initialStartOffset = StartOffset((phase * 10).toInt())
            )
        )
    }

    val baseColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)

    Canvas(modifier = modifier.padding(horizontal = 8.dp)) {
        val w = size.width
        val h = size.height
        val gap = 6.dp.toPx()
        val barWidth = (w - gap * (barCount - 1)) / barCount
        val corner = 4.dp.toPx()
        for (i in 0 until barCount) {
            val v = animValues[i].value
            val barH = h * (0.15f + 0.85f * v)
            val left = i * (barWidth + gap)
            val top = (h - barH) / 2f
            drawRoundRect(
                color = baseColor,
                topLeft = Offset(left, top),
                size = androidx.compose.ui.geometry.Size(barWidth, barH),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(corner, corner)
            )
        }
    }
}

/** Preview */
@Preview(showBackground = true)
@Composable
fun FallbackAlarmScreenRichPreview() {
    val started = LocalDateTime.now().minusSeconds(73)
    val ui = FallbackAlarmUiState(isRinging = true, startedAt = started, label = "朝だよ〜")
    FallbackAlarmScreenRich(uiState = ui, onStop = {}, onSnooze = {})
}