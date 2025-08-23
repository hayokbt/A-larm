package io.github.arashiyama11.a_larm.ui.screen.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.arashiyama11.a_larm.R
import io.github.arashiyama11.a_larm.domain.models.AssistantPersona
import java.time.LocalTime

@Composable
fun HomeScreen(
    homeViewModel: HomeViewModel = hiltViewModel()
) {
    LaunchedEffect(Unit) {
        homeViewModel.onStart()
    }

    val uiState by homeViewModel.uiState.collectAsStateWithLifecycle()
    var showTimePicker by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 次のアラーム情報
        AlarmStatusCard(
            nextAlarm = uiState.nextAlarm,
            enabled = uiState.enabled,
            onToggleEnabled = homeViewModel::onToggleEnabled
        )

        // キャラクター選択セクション
        PersonaSelectionSection(
            personas = uiState.availablePersonas,
            selectedPersona = uiState.selectedPersona,
            isLoading = uiState.isLoading,
            onSelectPersona = homeViewModel::onSelectPersona
        )

        // アラームカスタムセクション
        AlarmCustomSection(
            customTime = uiState.customAlarmTime,
            isOneTime = uiState.isOneTimeAlarm,
            onShowTimePicker = { showTimePicker = true },
            onSkipNext = homeViewModel::onSkipNextAlarm,
            onReset = homeViewModel::onResetToDefaultAlarm,
            onToggleOneTime = homeViewModel::onToggleOneTimeAlarm
        )

        // タイムピッカーダイアログ
        if (showTimePicker) {
            TimePickerDialog(
                onTimeSelected = { time ->
                    homeViewModel.onSetCustomAlarmTime(time)
                    showTimePicker = false
                },
                onDismiss = { showTimePicker = false }
            )
        }
    }
}

@Composable
private fun AlarmStatusCard(
    nextAlarm: String,
    enabled: Boolean,
    onToggleEnabled: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "次のアラーム",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        text = nextAlarm,
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                Switch(
                    checked = enabled,
                    onCheckedChange = onToggleEnabled
                )
            }
        }
    }
}

@Composable
private fun PersonaSelectionSection(
    personas: List<AssistantPersona>,
    selectedPersona: AssistantPersona?,
    isLoading: Boolean,
    onSelectPersona: (AssistantPersona) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = null
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "アシスタントキャラクター",
                    style = MaterialTheme.typography.titleMedium
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (isLoading) {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(personas) { persona ->
                        EnhancedPersonaCard(
                            persona = persona,
                            isSelected = persona.id == selectedPersona?.id,
                            onClick = { onSelectPersona(persona) }
                        )
                    }
                }
            }
        }
    }
}

// キャラクターIDに対応する画像リソースIDを取得するヘルパー関数
private fun getCharacterImageResource(personaId: String): Int {
    return when (personaId) {
        "athletic_character" -> R.drawable.athletic_character
        "gentle_character" -> R.drawable.gentle_character
        "older_character" -> R.drawable.older_character
        "tsundere_character" -> R.drawable.tsundere_character
        "younger_character" -> R.drawable.younger_character
        else -> R.drawable.athletic_character // デフォルト
    }
}

@Composable
private fun EnhancedPersonaCard(
    persona: AssistantPersona,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .width(160.dp)
            .height(220.dp)
            .clickable { onClick() },
        colors = if (isSelected) {
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            )
        } else {
            CardDefaults.cardColors()
        },
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (isSelected) 8.dp else 4.dp
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // キャラクター画像
            Image(
                painter = painterResource(id = getCharacterImageResource(persona.id)),
                contentDescription = persona.displayName,
                modifier = Modifier
                    .size(100.dp)
                    .clip(RoundedCornerShape(12.dp)),
                contentScale = ContentScale.Crop
            )

            Spacer(modifier = Modifier.height(8.dp))

            // キャラクター名
            Text(
                text = persona.displayName,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                textAlign = TextAlign.Center,
                maxLines = 2
            )

            Spacer(modifier = Modifier.height(4.dp))

            // キャラクターの特徴
            Text(
                text = getCharacterDescription(persona.id),
                style = MaterialTheme.typography.bodySmall,
                color = if (isSelected) {
                    MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f)
                } else {
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                },
                textAlign = TextAlign.Center,
                maxLines = 2
            )
        }
    }
}

// キャラクターIDに対応する説明文を取得するヘルパー関数
private fun getCharacterDescription(personaId: String): String {
    return when (personaId) {
        "athletic_character" -> "元気で前向き\n体育会系"
        "gentle_character" -> "優しく穏やか\nお母さん系"
        "older_character" -> "大人の魅力\nお姉さん系"
        "tsundere_character" -> "照れ屋で\nツンデレ"
        "younger_character" -> "明るく活発\n妹系"
        else -> "キャラクター"
    }
}

@Composable
private fun AlarmCustomSection(
    customTime: LocalTime?,
    isOneTime: Boolean,
    onShowTimePicker: () -> Unit,
    onSkipNext: () -> Unit,
    onReset: () -> Unit,
    onToggleOneTime: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.AccessTime,
                    contentDescription = null
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "アラーム設定",
                    style = MaterialTheme.typography.titleMedium
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // カスタム時間設定
            Button(
                onClick = onShowTimePicker,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = if (customTime != null) {
                        "カスタム時間: ${
                            String.format(
                                "%02d:%02d",
                                customTime.hour,
                                customTime.minute
                            )
                        }"
                    } else {
                        "カスタム時間を設定"
                    }
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 一回限りのアラーム設定
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "一回限りのアラーム")
                Switch(
                    checked = isOneTime,
                    onCheckedChange = onToggleOneTime
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // アクションボタン
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onSkipNext,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipNext,
                        contentDescription = null
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("スキップ")
                }

                if (customTime != null) {
                    OutlinedButton(
                        onClick = onReset,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("リセット")
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimePickerDialog(
    onTimeSelected: (LocalTime) -> Unit,
    onDismiss: () -> Unit
) {
    val timePickerState = rememberTimePickerState()

    // 簡易的なダイアログ実装（実際の実装ではAlertDialogなどを使用）
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "アラーム時刻を選択",
                style = MaterialTheme.typography.titleMedium
            )

            Spacer(modifier = Modifier.height(16.dp))

            TimePicker(
                state = timePickerState
            )

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                OutlinedButton(onClick = onDismiss) {
                    Text("キャンセル")
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = {
                        val selectedTime = LocalTime.of(
                            timePickerState.hour,
                            timePickerState.minute
                        )
                        onTimeSelected(selectedTime)
                    }
                ) {
                    Text("設定")
                }
            }
        }
    }
}