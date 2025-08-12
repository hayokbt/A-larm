package io.github.arashiyama11.a_larm.ui.screen.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun HomeScreen(
    homeViewModel: HomeViewModel = hiltViewModel()
) {
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
        Spacer(Modifier.height(24.dp))
        Spacer(Modifier.height(24.dp))
        Text(text = "ルーチン")
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            //Button(onClick = { onChangeRoutineMode(RoutineMode.Daily) }) { Text("1日単位") }
            //Button(onClick = { onChangeRoutineMode(RoutineMode.Weekly) }) { Text("週単位") }
        }
        Spacer(Modifier.height(8.dp))
        Text("テキストでのチャット")
        LazyColumn(
            modifier = Modifier.weight(1f)
        ) {
            items(uiState.history) { turn ->
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "${turn.role}: ${turn.text}")
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        var text by remember { mutableStateOf("") }
        Row {
            OutlinedTextField(
                text, onValueChange = { text = it },
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(8.dp))

            OutlinedButton(
                onClick = {
                    if (text.isNotBlank()) {
                        homeViewModel.sendMessage(text)
                        text = ""
                    }
                }
            ) {
                Text("送信")
            }
        }
    }
}