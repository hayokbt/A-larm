package io.github.arashiyama11.a_larm.ui.screen.onboarding

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.arashiyama11.a_larm.PermissionManager

@Composable
fun OnboardingScreen(
    viewModel: OnboardingViewModel = hiltViewModel(),
    navigateToHome: () -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val permissionManager = remember { PermissionManager(context) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
        onResult = { results ->
            viewModel.onPermissionsResult(results)
        }
    )

    LaunchedEffect(Unit) {
        // Initialize permission states
        viewModel.onPermissionsResult(
            mapOf(
                Manifest.permission.RECORD_AUDIO to permissionManager.isMicGranted(),
                Manifest.permission.POST_NOTIFICATIONS to permissionManager.isNotificationsGranted(),
            )
        )
        viewModel.refreshExactAlarmState(permissionManager.canScheduleExactAlarms())

        // Event handling from ViewModel (UI triggers actual permission flows)
        viewModel.events.collect { event ->
            when (event) {
                OnboardingViewModel.UiEvent.RequestRuntimePermissions -> {
                    val toRequest = permissionManager.requiredRuntimePermissions().filter { perm ->
                        when (perm) {
                            Manifest.permission.RECORD_AUDIO -> !permissionManager.isMicGranted()
                            Manifest.permission.POST_NOTIFICATIONS -> !permissionManager.isNotificationsGranted()
                            else -> false
                        }
                    }
                    if (toRequest.isNotEmpty()) {
                        permissionLauncher.launch(toRequest.toTypedArray())
                    } else {
                        // Reflect current state as granted
                        viewModel.onPermissionsResult(
                            mapOf(
                                Manifest.permission.RECORD_AUDIO to true,
                                Manifest.permission.POST_NOTIFICATIONS to true,
                            )
                        )
                    }
                }

                OnboardingViewModel.UiEvent.OpenExactAlarmSettings -> {
                    permissionManager.openExactAlarmSettings()
                }
            }
            // After any event, refresh exact alarm state best-effort
            viewModel.refreshExactAlarmState(permissionManager.canScheduleExactAlarms())
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Top,
        horizontalAlignment = Alignment.Start
    ) {
        Text(text = "A-larm へようこそ")
        Spacer(Modifier.height(8.dp))
        Text(text = "AI と会話して起きるための準備をしましょう。")

        Spacer(Modifier.height(24.dp))
        OutlinedTextField(
            value = state.apiKey,
            onValueChange = viewModel::onApiKeyChange,
            label = { Text("LLM API キー") },
            singleLine = true,
        )

        Spacer(Modifier.height(24.dp))
        Text("権限の状態")
        Spacer(Modifier.height(8.dp))
        Row { Text("マイク: "); Text(if (state.micGranted) "許可済み" else "未許可") }
        Row { Text("通知: "); Text(if (state.notificationsGranted) "許可済み" else "未許可") }
        Row { Text("正確なアラーム: "); Text(if (state.exactAlarmAllowed) "許可済み/不要" else "未許可") }

        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = viewModel::requestRuntimePermissions) { Text("通知・マイクを許可") }
            Button(onClick = viewModel::openExactAlarmSettings) { Text("正確なアラーム設定") }
        }

        Spacer(Modifier.height(24.dp))
        Button(onClick = { navigateToHome() }, enabled = true) { Text("開始する") }
    }
}
