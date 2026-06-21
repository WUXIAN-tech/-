package com.wuxian.mp3.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.wuxian.mp3.ffmpeg.AudioConverter

private sealed class State {
    object Idle : State()
    data class Selected(val name: String, val uri: Uri) : State()
    data class Converting(val progress: Float) : State()
    data class Done(val name: String) : State()
    data class Error(val message: String) : State()
}

@Composable
fun ConverterScreen() {
    var state by remember { mutableStateOf<State>(State.Idle) }
    val context = LocalContext.current

    val pickLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            val name = uri.lastPathSegment?.substringAfterLast('/') ?: "未命名"
            state = State.Selected(name, uri)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Text(
                text = "MP4 → MP3",
                style = MaterialTheme.typography.headlineLarge,
                textAlign = TextAlign.Center,
            )
            Text(
                text = "提取视频中的音频",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(24.dp))

            when (val s = state) {
                State.Idle -> {
                    PrimaryButton(
                        text = "选择 MP4 文件",
                        onClick = { pickLauncher.launch(arrayOf("video/mp4", "video/*")) },
                    )
                }
                is State.Selected -> {
                    Text(
                        text = s.name,
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                    )
                    PrimaryButton(
                        text = "转换为 MP3",
                        onClick = {
                            state = State.Converting(-1f)
                            AudioConverter.convert(
                                context = context,
                                inputUri = s.uri,
                                onProgress = { p -> state = State.Converting(p) },
                                onDone = { state = State.Done(s.name) },
                                onError = { msg -> state = State.Error(msg) },
                            )
                        },
                    )
                    OutlinedButton(
                        onClick = { state = State.Idle },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(18.dp),
                    ) { Text("重新选择") }
                }
                is State.Converting -> {
                    if (s.progress < 0) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(48.dp),
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            text = "准备中…",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    } else {
                        LinearProgressIndicator(
                            progress = { s.progress.coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth().height(8.dp),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = Color(0xFFE8E8ED),
                        )
                        Text(
                            text = "${(s.progress * 100).toInt().coerceIn(0, 100)}%",
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                }
                is State.Done -> {
                    Text(
                        text = "完成 ✓",
                        style = MaterialTheme.typography.headlineMedium,
                        color = Color(0xFF34C759),
                    )
                    Text(
                        text = "已保存到 Music/Converter/${s.name}.mp3",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                    )
                    PrimaryButton(
                        text = "再转一个",
                        onClick = { state = State.Idle },
                    )
                }
                is State.Error -> {
                    Text(
                        text = s.message,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center,
                    )
                    PrimaryButton(
                        text = "重试",
                        onClick = { state = State.Idle },
                    )
                }
            }
        }
    }
}

@Composable
private fun PrimaryButton(text: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(54.dp),
        shape = RoundedCornerShape(18.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = Color.White,
        ),
    ) { Text(text = text, style = MaterialTheme.typography.bodyLarge) }
}
