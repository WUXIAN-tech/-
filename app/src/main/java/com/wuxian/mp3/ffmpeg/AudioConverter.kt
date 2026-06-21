package com.wuxian.mp3.ffmpeg

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.FFprobeKit
import com.arthenica.ffmpegkit.ReturnCode
import com.arthenica.ffmpegkit.StatisticsCallback
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

object AudioConverter {

    fun convert(
        context: Context,
        inputUri: Uri,
        onProgress: (Float) -> Unit,
        onDone: (Uri) -> Unit,
        onError: (String) -> Unit,
    ): Job {
        val scope = CoroutineScope(Dispatchers.Main)
        return scope.launch {
            try {
                onProgress(-1f) // indeterminate: copying phase
                val tempInput = withContext(Dispatchers.IO) { copyUriToCache(context, inputUri) }
                val tempOutput = File(context.cacheDir, "output_${System.currentTimeMillis()}.mp3")

                val duration = withContext(Dispatchers.IO) { probeDuration(tempInput.absolutePath) }

                val cmd = "-y -i \"${tempInput.absolutePath}\" -vn -c:a libmp3lame -b:a 320k -q:a 0 \"${tempOutput.absolutePath}\""

                val statsCallback = StatisticsCallback { stats ->
                    if (duration > 0) {
                        val p = stats.time.toFloat() / (duration * 1000f)
                        onProgress(p.coerceIn(0f, 1f))
                    }
                }

                val session = FFmpegKit.executeAsync(
                    cmd,
                    { completed ->
                        scope.launch {
                            val code = completed.returnCode
                            when {
                                ReturnCode.isSuccess(code) -> {
                                    try {
                                        val outUri = withContext(Dispatchers.IO) {
                                            publishToMediaStore(context, tempOutput, tempInput.nameWithoutExtension)
                                        }
                                        tempInput.delete()
                                        tempOutput.delete()
                                        onDone(outUri)
                                    } catch (e: Exception) {
                                        onError("保存失败: ${e.message}")
                                    }
                                }
                                else -> {
                                    tempInput.delete()
                                    tempOutput.delete()
                                    onError("转换失败 (code ${code.value})")
                                }
                            }
                        }
                    },
                    { /* log callback - ignore */ },
                    statsCallback,
                )
            } catch (e: Exception) {
                onError("错误: ${e.message ?: "未知"}")
            }
        }
    }

    private fun copyUriToCache(context: Context, uri: Uri): File {
        val dest = File(context.cacheDir, "input_${System.currentTimeMillis()}.mp4")
        context.contentResolver.openInputStream(uri).use { input ->
            FileOutputStream(dest).use { out ->
                input?.copyTo(out, bufferSize = 8 * 1024)
            }
        } ?: throw IllegalStateException("无法读取所选文件")
        return dest
    }

    private fun probeDuration(path: String): Double {
        val session = FFprobeKit.execute("-v error -show_entries format=duration -of csv=p=0 \"$path\"")
        val out = session.allLogsAsString.trim()
        return out.toDoubleOrNull() ?: 0.0
    }

    private fun publishToMediaStore(context: Context, mp3File: File, displayName: String): Uri {
        val resolver = context.contentResolver
        val collection = MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val values = ContentValues().apply {
            put(MediaStore.Audio.Media.DISPLAY_NAME, "$displayName.mp3")
            put(MediaStore.Audio.Media.TITLE, displayName)
            put(MediaStore.Audio.Media.MIME_TYPE, "audio/mpeg")
            put(MediaStore.Audio.Media.RELATIVE_PATH, "Music/Converter")
            put(MediaStore.Audio.Media.IS_PENDING, 1)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Audio.Media.IS_MUSIC, 1)
            }
        }
        val uri = resolver.insert(collection, values)
            ?: throw IllegalStateException("无法创建 MediaStore 记录")
        resolver.openOutputStream(uri).use { out ->
            if (out == null) throw IllegalStateException("无法打开输出流")
            mp3File.inputStream().copyTo(out)
        }
        values.clear()
        values.put(MediaStore.Audio.Media.IS_PENDING, 0)
        resolver.update(uri, values, null, null)
        return uri
    }
}
