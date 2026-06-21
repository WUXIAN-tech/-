package com.wuxian.mp3.ffmpeg

import android.content.ContentValues
import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import com.writingminds.ffmpeg.FFmpeg
import com.writingminds.ffmpeg.FFmpegExecuteResponseHandler
import com.writingminds.ffmpeg.FFmpegLoadBinaryResponseHandler
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
                onProgress(-1f)
                val tempInput = withContext(Dispatchers.IO) { copyUriToCache(context, inputUri) }
                val tempOutput = File(context.cacheDir, "output_${System.currentTimeMillis()}.mp3")

                val durationMs = withContext(Dispatchers.IO) { probeDuration(context, inputUri) }

                val ffmpeg = FFmpeg.getInstance(context)
                try {
                    withContext(Dispatchers.IO) {
                        ffmpeg.loadBinary(object : FFmpegLoadBinaryResponseHandler {
                            override fun onStart() {}
                            override fun onSuccess() {}
                            override fun onFailure() {}
                            override fun onFinish() {}
                        })
                    }
                } catch (e: Exception) {
                    onError("加载 FFmpeg 失败: ${e.message ?: "未知"}")
                    return@launch
                }

                val cmd = "-y -i ${tempInput.absolutePath} -vn -c:a libmp3lame -b:a 320k ${tempOutput.absolutePath}"

                withContext(Dispatchers.IO) {
                    ffmpeg.execute(cmd, object : FFmpegExecuteResponseHandler {
                        override fun onStart() {}

                        override fun onProgress(message: String?) {
                            if (durationMs > 0 && message != null) {
                                parseTimeMs(message)?.let { t ->
                                    val p = t.toFloat() / durationMs.toFloat()
                                    onProgress(p.coerceIn(0f, 1f))
                                }
                            }
                        }

                        override fun onSuccess(message: String?) {}

                        override fun onFailure(message: String?) {
                            scope.launch {
                                tempInput.delete()
                                tempOutput.delete()
                                onError("转换失败: ${message ?: "未知"}")
                            }
                        }

                        override fun onFinish() {
                            scope.launch {
                                if (tempOutput.exists() && tempOutput.length() > 0) {
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
                                } else {
                                    onError("转换失败: 输出文件为空")
                                }
                            }
                        }
                    })
                }
            } catch (e: Exception) {
                onError("错误: ${e.message ?: "未知"}")
            }
        }
    }

    private fun parseTimeMs(log: String): Long? {
        val timeIdx = log.indexOf("time=")
        if (timeIdx < 0) return null
        val start = timeIdx + 5
        val end = log.indexOf(' ', start)
        val timeStr = if (end < 0) log.substring(start) else log.substring(start, end)
        val parts = timeStr.split(":")
        if (parts.size != 3) return null
        return try {
            val h = parts[0].toLong() * 3600_000
            val m = parts[1].toLong() * 60_000
            val s = (parts[2].toDouble() * 1000).toLong()
            h + m + s
        } catch (e: Exception) {
            null
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

    private fun probeDuration(context: Context, uri: Uri): Long {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        } catch (e: Exception) {
            0L
        } finally {
            try { retriever.release() } catch (_: Exception) {}
        }
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
