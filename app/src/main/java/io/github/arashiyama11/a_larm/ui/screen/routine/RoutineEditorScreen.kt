package io.github.arashiyama11.a_larm.ui.screen.routine

import android.annotation.SuppressLint
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Nightlight
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import io.github.arashiyama11.a_larm.domain.models.AlarmId
import io.github.arashiyama11.a_larm.domain.models.CellKey
import io.github.arashiyama11.a_larm.domain.models.RoutineEntry
import io.github.arashiyama11.a_larm.domain.models.RoutineGrid
import io.github.arashiyama11.a_larm.domain.models.RoutineMode
import io.github.arashiyama11.a_larm.domain.models.RoutineType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoutineScreen(
    vm: RoutineViewModel = hiltViewModel()
) {
    val ui by vm.uiState.collectAsState()

    val topBarScrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(topBarScrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = { Text("ルーチーン入力", fontWeight = FontWeight.SemiBold) },
                scrollBehavior = topBarScrollBehavior,
                actions = {
                    ModeToggle(
                        mode = ui.mode,
                        onChange = vm::changeMode
                    )
                }
            )
        }
    ) { inner ->
        Column(
            Modifier
                .padding(inner)
                .fillMaxSize()
        ) {
            Spacer(Modifier.height(8.dp))
            RoutineGrid(
                mode = ui.mode,
                grid = ui.grid,
                onCellClick = vm::onCellClick,
                modifier = Modifier.fillMaxWidth()
            )
        }

        EditSheet(
            state = ui.editing,
            onDismiss = vm::onEditDismiss,
            onTypeSelect = vm::onEditTypeSelected,
            onLabelChange = vm::onEditLabelChange,
            onApply = vm::onEditApply,
            onMinuteChange = vm::onEditMinuteChange
        )
    }
}

@Composable
private fun ModeToggle(
    mode: RoutineMode,
    onChange: (RoutineMode) -> Unit
) {
    val items = listOf(RoutineMode.DAILY to "1日単位", RoutineMode.WEEKLY to "週単位")
    TabRow(
        selectedTabIndex = if (mode == RoutineMode.DAILY) 0 else 1,
        modifier = Modifier.widthIn(max = 220.dp)
    ) {
        items.forEachIndexed { idx, (m, label) ->
            Tab(
                selected = (m == mode),
                onClick = { onChange(m) },
                text = { Text(label) }
            )
        }
    }
}

@SuppressLint("UnusedBoxWithConstraintsScope")
@Composable
private fun RoutineGrid(
    mode: RoutineMode,
    grid: RoutineGrid,
    onCellClick: (CellKey) -> Unit,
    modifier: Modifier = Modifier,      // 呼び出し側で .fillMaxWidth().height(...)
    minCellHeight: Dp = 28.dp,
    gap: Dp = 6.dp,
    timeLabelWidth: Dp = 56.dp
) {
    val days = if (mode == RoutineMode.DAILY) 1 else 7
    val dayLabels = listOf("月", "火", "水", "木", "金", "土", "日")
    val vScroll = rememberScrollState()

    BoxWithConstraints(modifier = modifier) {
        val maxH = maxHeight
        // ヘッダ1 + 24行、間のギャップ24箇所を考慮
        val cellH = ((maxH - gap * 24) / 25).coerceAtLeast(minCellHeight)

        Row(Modifier.fillMaxSize()) {
            // 左：時間ラベル列（右と同じ vScroll を共有して同期スクロール）
            Column(
                modifier = Modifier
                    .width(timeLabelWidth)
                    .verticalScroll(vScroll)
            ) {
                Box(Modifier.height(cellH)) {} // 左上角
                Spacer(Modifier.height(gap))
                for (hour in 0..23) {
                    Box(
                        modifier = Modifier
                            .height(cellH)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.CenterEnd
                    ) {
                        Text(
                            text = "%02d:00".format(hour),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (hour < 23) Spacer(Modifier.height(gap))
                }
            }

            Spacer(Modifier.width(8.dp))

            // 右：曜日ヘッダ + 本体。横幅配分は weight で完全等分
            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .weight(1f)               // ← 残り幅を全部受ける
                    .verticalScroll(vScroll)  // ← 左と同じ ScrollState
            ) {
                // 曜日ヘッダ行
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(gap)
                ) {
                    repeat(days) { d ->
                        DayHeaderCellWeighted(
                            label = if (mode == RoutineMode.DAILY) "Day" else dayLabels[d],
                            height = cellH,
                            modifier = Modifier.weight(1f) // ← 列を等分
                        )
                    }
                }
                Spacer(Modifier.height(gap))

                // 本体：縦＝時間、横＝日（各セルは weight(1f) で等分）
                for (hour in 0..23) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(gap)
                    ) {
                        repeat(days) { d ->
                            val key = CellKey(d, hour)
                            val entry = grid[key] ?: RoutineEntry(id = AlarmId(0))
                            GridCellWeighted(
                                entry = entry,
                                onClick = { onCellClick(key) },
                                height = cellH,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                    if (hour < 23) Spacer(Modifier.height(gap))
                }
            }
        }
    }
}

@Composable
private fun DayHeaderCellWeighted(
    label: String,
    height: Dp,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .height(height)
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun GridCellWeighted(
    entry: RoutineEntry,
    onClick: () -> Unit,
    height: Dp,
    modifier: Modifier = Modifier
) {
    val color = when (entry.type) {
        RoutineType.NONE -> MaterialTheme.colorScheme.surfaceVariant
        RoutineType.WAKE -> Color(0xAACC5C33)
        RoutineType.SLEEP -> Color(0xAA3949AB)
        RoutineType.TASK -> MaterialTheme.colorScheme.secondaryContainer
    }

    val icon = when (entry.type) {
        RoutineType.WAKE -> Icons.Outlined.WbSunny
        RoutineType.SLEEP -> Icons.Outlined.Nightlight
        RoutineType.TASK -> Icons.Outlined.TaskAlt
        else -> null
    }
    val borderColor = MaterialTheme.colorScheme.outlineVariant

    Box(
        modifier = modifier
            .height(height)
            .clip(RoundedCornerShape(6.dp))
            .background(color)
            .border(1.dp, borderColor, RoundedCornerShape(6.dp))
            .noRippleClickable(onClick),
        contentAlignment = Alignment.Center
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer
            )
        } else {
            // 何も設定されていないセルは空白
            //Spacer(Modifier.size(24.dp))
        }

        // タスクの場合は小さなタスク印を表示
//        if (entry.type == RoutineType.TASK && entry.label.isNotBlank()) {
//            Text(
//                text = "任",
//                style = MaterialTheme.typography.labelSmall,
//                color = MaterialTheme.colorScheme.onSecondaryContainer
//            )
//        }
    }
}


// ヘッダ（矩形サイズ対応）
@Composable
private fun DayHeaderCellRect(
    label: String,
    width: Dp,
    height: Dp
) {
    Box(
        modifier = Modifier
            .width(width)
            .height(height)
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

// セル（矩形サイズ対応）— 既存 GridCell と同じ見た目で幅・高を別指定
@Composable
private fun GridCellRect(
    entry: RoutineEntry,
    onClick: () -> Unit,
    width: Dp,
    height: Dp
) {
    val color = when (entry.type) {
        RoutineType.NONE -> MaterialTheme.colorScheme.surfaceVariant
        RoutineType.WAKE -> MaterialTheme.colorScheme.tertiaryContainer
        RoutineType.SLEEP -> MaterialTheme.colorScheme.primaryContainer
        RoutineType.TASK -> MaterialTheme.colorScheme.secondaryContainer
    }
    val borderColor = MaterialTheme.colorScheme.outlineVariant

    Box(
        modifier = Modifier
            .width(width)
            .height(height)
            .clip(RoundedCornerShape(6.dp))
            .background(color)
            .border(1.dp, borderColor, RoundedCornerShape(6.dp))
            .noRippleClickable(onClick),
        contentAlignment = Alignment.Center
    ) {
        if (entry.type == RoutineType.TASK && entry.label.isNotBlank()) {
            Text(
                text = "任",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
    }
}


@Composable
private fun DayHeaderCell(
    label: String,
    size: Dp
) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}


@Composable
private fun GridCell(
    modifier: Modifier,
    entry: RoutineEntry,
    onClick: () -> Unit,
    size: Dp
) {
    val color = when (entry.type) {
        RoutineType.NONE -> MaterialTheme.colorScheme.surfaceVariant
        RoutineType.WAKE -> MaterialTheme.colorScheme.tertiaryContainer
        RoutineType.SLEEP -> MaterialTheme.colorScheme.primaryContainer
        RoutineType.TASK -> MaterialTheme.colorScheme.secondaryContainer
    }
    val borderColor = MaterialTheme.colorScheme.outlineVariant

    Box(
        modifier = modifier
            .height(size)
            .clip(RoundedCornerShape(6.dp))
            .background(color)
            .border(1.dp, borderColor, RoundedCornerShape(6.dp))
            .padding(2.dp)
            .let { m ->
                // クリック領域
                m.then(
                    Modifier
                        .fillMaxSize()
                        .noRippleClickable { onClick() })
            },
        contentAlignment = Alignment.Center
    ) {
        if (entry.type == RoutineType.TASK && entry.label.isNotBlank()) {
            Text(
                text = "任", // 小さなタスク印（任=任務のニュアンス）
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
    }
}

// シンプルなクリック Modifier（リップル不要）
@Composable
private fun Modifier.noRippleClickable(onClick: () -> Unit): Modifier {
    return this.then(
        Modifier
            .clickableNoIndication(onClick)
    )
}

@Composable
private fun Modifier.clickableNoIndication(onClick: () -> Unit): Modifier {
    return this.then(
        Modifier.clickable(
            interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
            indication = null,
            onClick = onClick
        )
    )
}

// ===== Edit Sheet =====

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditSheet(
    state: EditState?,
    onDismiss: () -> Unit,
    onTypeSelect: (RoutineType) -> Unit,
    onLabelChange: (String) -> Unit,
    onApply: () -> Unit,
    onMinuteChange: (Int) -> Unit = {}         // ← 追加
) {
    if (state == null) return
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showMinutePicker by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ==== 時間行（タップで分指定） ====
            ListItem(
                headlineContent = {
                    Text(
                        "時間",
                        style = MaterialTheme.typography.titleSmall
                    )
                },
                supportingContent = {
                    Text(
                        text = "%02d:%02d".format(state.key.hour, state.working.minute),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                },
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .noRippleClickable { showMinutePicker = true }
                    .border(
                        1.dp,
                        MaterialTheme.colorScheme.outlineVariant,
                        RoundedCornerShape(8.dp)
                    )
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            )

            // ==== タイプ選択 ====
            Text("タイプを選択")
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    RoutineType.WAKE to "起床",
                    RoutineType.SLEEP to "就寝",
                    RoutineType.TASK to "タスク",
                    RoutineType.NONE to "未設定（クリア）"
                ).forEach { (t, label) ->
                    val selected = state.working.type == t
                    ElevatedCard(
                        onClick = { onTypeSelect(t) },
                        colors = CardDefaults.elevatedCardColors(
                            containerColor = if (selected)
                                MaterialTheme.colorScheme.secondaryContainer
                            else
                                MaterialTheme.colorScheme.surface
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = selected, onClick = { onTypeSelect(t) })
                            Text(label, modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                }
            }

            if (state.working.type == RoutineType.TASK) {
                OutlinedTextField(
                    value = state.working.label,
                    onValueChange = onLabelChange,
                    label = { Text("タスク名（任意）") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End)
            ) {
                TextButton(onClick = onDismiss) { Text("キャンセル") }
                Button(onClick = onApply) { Text("適用") }
            }
            Spacer(Modifier.height(12.dp))
        }
    }

    if (showMinutePicker) {
        MinutePickerDialog(
            hour = state.key.hour,
            minute = state.working.minute,
            onDismiss = { showMinutePicker = false },
            onPicked = { m ->
                onMinuteChange(m)
                showMinutePicker = false
            }
        )
    }
}

@Composable
private fun MinutePickerDialog(
    hour: Int,
    minute: Int,
    onDismiss: () -> Unit,
    onPicked: (Int) -> Unit
) {
    var localMinute by remember { mutableStateOf(minute.coerceIn(0, 59)) }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { onPicked(localMinute) }) { Text("OK") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("キャンセル") }
        },
        title = { Text("時間の詳細") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "選択中: %02d:%02d".format(hour, localMinute),
                    style = MaterialTheme.typography.bodyLarge
                )

                // スライダーでも微調整
                Slider(
                    value = localMinute / 59f,
                    onValueChange = { f -> localMinute = (f * 59).toInt().coerceIn(0, 59) },
                    valueRange = 0f..1f,
                )
            }
        }
    )
}


@Preview
@Composable
private fun RoutineGridPreview() {
    // val vm = remember { RoutineViewModel() }
    //RoutineScreen(vm)
}
