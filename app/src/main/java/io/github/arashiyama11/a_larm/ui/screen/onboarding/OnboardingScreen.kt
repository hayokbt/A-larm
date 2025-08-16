package io.github.arashiyama11.a_larm.ui.screen.onboarding

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.arashiyama11.a_larm.PermissionManager
import io.github.arashiyama11.a_larm.domain.models.Gender
import io.github.arashiyama11.a_larm.ui.theme.AlarmTheme

@Composable
fun OnboardingScreen(
    viewModel: OnboardingViewModel = hiltViewModel(),
    navigateToHome: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val permissionManager = remember { PermissionManager(context) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
        onResult = { results ->
            viewModel.updatePhase()
        }
    )

    LaunchedEffect(Unit) {
        viewModel.onStart()
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
                        viewModel.updatePhase()
                    }
                }

                OnboardingViewModel.UiEvent.OpenExactAlarmSettings -> {
                    permissionManager.openExactAlarmSettings()
                }
            }
        }
    }

    Scaffold(modifier = Modifier.fillMaxSize()) { paddingValues ->
        when (uiState.phase) {
            OnboardingStep.LOADING -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }

            OnboardingStep.GRANT_PERMISSIONS ->
                GrantPermissions(
                    modifier = Modifier.padding(paddingValues),
                    requestRuntimePermissions = viewModel::requestRuntimePermissions
                )

            OnboardingStep.USER_PROFILE ->
                UserProfileStep(
                    modifier = Modifier.padding(paddingValues),
                    name = uiState.rawName,
                    gender = uiState.gender,
                    displayName = uiState.displayName,
                    onNameChange = viewModel::onNameChange,
                    onGenderChange = viewModel::onGenderChange,
                    onNext = viewModel::updatePhase
                )

            OnboardingStep.GRANT_API_KEY ->
                GrantApiKey(
                    modifier = Modifier.padding(paddingValues),
                    apiKey = uiState.apiKey,
                    isSaved = uiState.isApiKeySaved,
                    onApiKeyChange = viewModel::onApiKeyChange,
                    onNext = viewModel::updatePhase,
                    onSaveApiKey = viewModel::onSaveApiKey,
                    onSkipApiKey = viewModel::skipApiKey
                )

            OnboardingStep.EXACT_ALARM ->
                GrantExactAlarm(
                    modifier = Modifier.padding(paddingValues),
                    onOpenSettings = viewModel::openExactAlarmSettings,
                    onNext = viewModel::updatePhase
                )

            OnboardingStep.COMPLETED ->
                OnboardingCompleted(
                    modifier = Modifier.padding(paddingValues),
                    onNext = navigateToHome
                )
        }
    }
}


@Composable
fun GrantPermissions(modifier: Modifier, requestRuntimePermissions: () -> Unit) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            "マイクと通知の権限が必要です",
            style = MaterialTheme.typography.headlineSmall
        )
        Spacer(Modifier.height(16.dp))
        Text(
            "AIとの対話とアラームの起動のためにマイクと通知の権限が必要です",
            style = MaterialTheme.typography.bodyMedium.copy(color = Color.Gray)
        )
        Spacer(Modifier.height(16.dp))
        Button(onClick = requestRuntimePermissions) { Text("権限を許可") }
    }
}

@Composable
fun UserProfileStep(
    modifier: Modifier,
    name: String,
    displayName: String,
    gender: Gender?,
    onNameChange: (String) -> Unit,
    onGenderChange: (Gender) -> Unit,
    onNext: () -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            "プロフィールを教えてください",
            style = MaterialTheme.typography.headlineSmall
        )
        Spacer(Modifier.height(16.dp))
        Text(
            "あなたに合わせた応答を生成するために利用します。この情報は後から変更できます。",
            style = MaterialTheme.typography.bodyMedium.copy(color = Color.Gray)
        )
        Spacer(Modifier.height(24.dp))

        OutlinedTextField(
            value = name,
            onValueChange = onNameChange,
            label = { Text("名前") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(8.dp))

        Box(contentAlignment = Alignment.CenterStart, modifier = Modifier.fillMaxWidth()) {
            Text("表示名: $displayName", style = MaterialTheme.typography.bodyLarge)
        }
        Spacer(Modifier.height(16.dp))

        Text("性別", style = MaterialTheme.typography.bodyLarge)


        Row(Modifier.fillMaxWidth()) {
            Gender.entries.forEach {
                Row(
                    Modifier
                        .selectable(
                            selected = (it == gender),
                            onClick = { onGenderChange(it) }
                        )
                        .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = (it == gender),
                        onClick = { onGenderChange(it) }
                    )
                    Text(
                        text = when (it) {
                            Gender.MALE -> "男性"
                            Gender.FEMALE -> "女性"
                            Gender.OTHER -> "その他"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(start = 4.dp)
                    )
                }
            }
        }

        Spacer(Modifier.height(24.dp))
        Button(
            onClick = onNext,
            enabled = name.isNotBlank() && gender != null,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("次へ")
        }
    }
}

@Composable
fun GrantApiKey(
    modifier: Modifier,
    apiKey: String,
    isSaved: Boolean,
    onApiKeyChange: (String) -> Unit,
    onNext: () -> Unit,
    onSaveApiKey: () -> Unit,
    onSkipApiKey: () -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            "APIキーを入力してください",
            style = MaterialTheme.typography.headlineSmall
        )

        Spacer(Modifier.height(16.dp))
        Text(
            "AIとの対話にはGoogle Gemini APIキーが必要です。まだ持っていない場合は、Google Cloud Consoleで取得してください。APIキーは後で設定から変更できます。",
            style = MaterialTheme.typography.bodyMedium.copy(color = Color.Gray)
        )
        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = apiKey,
                onValueChange = onApiKeyChange,
                label = { Text("API キー") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            Button(onClick = onSaveApiKey, enabled = !isSaved) { Text("保存") }
        }

        Spacer(Modifier.height(16.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            OutlinedButton(onClick = onSkipApiKey) { Text("スキップ") }
            Spacer(Modifier.width(8.dp))
            Button(onClick = onNext, enabled = isSaved) { Text("次へ") }
        }
    }
}

@Composable
fun GrantExactAlarm(
    modifier: Modifier,
    onOpenSettings: () -> Unit,
    onNext: () -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            "正確なアラームの許可が必要です",
            style = MaterialTheme.typography.headlineSmall
        )
        Spacer(Modifier.height(16.dp))
        Text(
            "正確なアラームを使用するには、設定から「正確なアラーム」を有効にする必要があります。",
            style = MaterialTheme.typography.bodyMedium.copy(color = Color.Gray)
        )
        Spacer(Modifier.height(16.dp))
        Button(onClick = onOpenSettings) { Text("設定を開く") }
        Spacer(Modifier.height(16.dp))
        Button(onNext) {
            Text("次へ")
        }
    }
}

@Composable
fun OnboardingCompleted(
    modifier: Modifier,
    onNext: () -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("初期設定が完了しました！", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(16.dp))
        Button(onClick = onNext) { Text("ホームへ") }
    }
}


@Preview
@Composable
fun Preview() {
    AlarmTheme {
        Surface {
            GrantApiKey(
                modifier = Modifier.fillMaxSize(),
                apiKey = "",
                isSaved = false,
                onApiKeyChange = { },
                onNext = { },
                onSaveApiKey = { },
                onSkipApiKey = { }
            )
        }
    }
}