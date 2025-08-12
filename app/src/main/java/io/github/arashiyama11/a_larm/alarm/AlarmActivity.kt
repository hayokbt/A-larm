package io.github.arashiyama11.a_larm.alarm

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import dagger.hilt.android.AndroidEntryPoint
import io.github.arashiyama11.a_larm.ui.theme.AlarmTheme

@AndroidEntryPoint
class AlarmActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setShowWhenLocked(true)
        setTurnScreenOn(true)


        setContent {
            AlarmTheme {
                Scaffold() { contentPadding ->
                    Column(
                        modifier = androidx.compose.ui.Modifier
                            .fillMaxSize()
                            .padding(contentPadding),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text("ここはアラームのUI")
                        OutlinedButton(
                            onClick = { finish() },
                            modifier = androidx.compose.ui.Modifier.padding(8.dp)
                        ) {
                            Text("戻る")
                        }

                    }
                }
            }
        }
    }
}