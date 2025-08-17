package io.github.arashiyama11.a_larm.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.github.arashiyama11.a_larm.domain.models.Gender

@Composable
fun UserProfileForm(
    modifier: Modifier = Modifier,
    name: String,
    displayName: String,
    gender: Gender?,
    onNameChange: (String) -> Unit,
    onGenderChange: (Gender) -> Unit,
    onSubmit: () -> Unit,
    title: String = "プロフィールを教えてください",
    description: String = "あなたに合わせた応答を生成するために利用します。この情報は後から変更できます。",
    submitLabel: String = "次へ",
    fillAndCenter: Boolean = false
) {
    val baseModifier = if (fillAndCenter) {
        modifier
            .fillMaxSize()
            .padding(24.dp)
    } else {
        modifier
            .fillMaxWidth()
            .padding(24.dp)
    }

    val arrangement = if (fillAndCenter) Arrangement.Center else Arrangement.Top

    Column(
        modifier = baseModifier,
        verticalArrangement = arrangement,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            title,
            style = MaterialTheme.typography.headlineSmall
        )
        if (description.isNotBlank() || description.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            Text(
                description,
                style = MaterialTheme.typography.bodyMedium.copy(color = Color.Gray)
            )
        }

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
            onClick = onSubmit,
            enabled = name.isNotBlank() && gender != null,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(submitLabel)
        }
    }
}
