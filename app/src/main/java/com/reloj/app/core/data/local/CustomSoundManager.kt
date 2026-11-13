package com.reloj.app.core.data.local

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.net.Uri
import android.provider.OpenableColumns
import androidx.annotation.RawRes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest

data class ImportedAudio(
    val path: String,
    val displayName: String,
)

/**
 * Copia archivos de audio externos al almacenamiento privado de la app (`filesDir/custom_sounds/`)
 * para garantizar su reproducción aunque el usuario borre o mueva el archivo original.
 */
object CustomSoundManager {

    private const val DIR_NAME = "custom_sounds"
    private const val MAX_FILE_BYTES = 50L * 1024L * 1024L // 50 MB límite razonable

    suspend fun importAudio(context: Context, sourceUri: Uri): Result<ImportedAudio> =
        withContext(Dispatchers.IO) {
            runCatching {
                val appContext = context.applicationContext
                val dir = File(appContext.filesDir, DIR_NAME).apply { mkdirs() }
                val rawName = resolveDisplayName(appContext, sourceUri)
                val ext = rawName.substringAfterLast('.', "")
                    .lowercase()
                    .filter { it.isLetterOrDigit() }
                    .take(8)
                    .ifBlank { "audio" }
                val cleanTitle = rawName.substringBeforeLast('.').ifBlank { "Audio personalizado" }

                val tempFile = File(dir, "tmp_${System.nanoTime()}.$ext")
                val digest = MessageDigest.getInstance("SHA-256")
                var totalBytes = 0L

                appContext.contentResolver.openInputStream(sourceUri)?.use { input ->
                    tempFile.outputStream().use { output ->
                        val buffer = ByteArray(16 * 1024)
                        while (true) {
                            val read = input.read(buffer)
                            if (read <= 0) break
                            totalBytes += read
                            if (totalBytes > MAX_FILE_BYTES) {
                                error("El archivo supera el límite de 50 MB")
                            }
                            digest.update(buffer, 0, read)
                            output.write(buffer, 0, read)
                        }
                    }
                } ?: error("No se pudo abrir el archivo de audio")

                if (totalBytes == 0L || !isPlayableAudio(tempFile)) {
                    tempFile.delete()
                    error("El archivo seleccionado no es un audio compatible")
                }

                val hashHex = digest.digest().joinToString("") { "%02x".format(it) }.take(24)
                val targetFile = File(dir, "$hashHex.$ext")
                if (targetFile.exists() && targetFile.length() > 0L) {
                    tempFile.delete()
                } else if (!tempFile.renameTo(targetFile)) {
                    tempFile.copyTo(targetFile, overwrite = true)
                    tempFile.delete()
                }

                ImportedAudio(
                    path = targetFile.absolutePath,
                    displayName = cleanTitle,
                )
            }
        }

    suspend fun pruneUnusedSounds(context: Context) = withContext(Dispatchers.IO) {
        val appContext = context.applicationContext
        val dir = File(appContext.filesDir, DIR_NAME)
        if (!dir.exists()) return@withContext

        val db = AppDatabase.get(appContext)
        val soundSettings = SoundPreferences.get(appContext).settings.value
        val activePaths = buildSet {
            soundSettings.customAlarmPath?.let(::add)
            soundSettings.customTimerPath?.let(::add)
            db.alarmDao().getAll().mapNotNullTo(this) { it.customSoundPath }
            db.timerDao().getAll().mapNotNullTo(this) { it.customSoundPath }
        }

        dir.listFiles()?.forEach { file ->
            if (file.isFile && file.absolutePath !in activePaths) {
                file.delete()
            }
        }
    }

    private fun resolveDisplayName(context: Context, uri: Uri): String {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) {
                        val name = cursor.getString(idx)
                        if (!name.isNullOrBlank()) return name
                    }
                }
            }
        return uri.lastPathSegment?.substringAfterLast('/')?.ifBlank { null } ?: "Audio personalizado"
    }

    private fun isPlayableAudio(file: File): Boolean {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO) != null ||
                (retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L) > 0L
        } catch (_: Throwable) {
            false
        } finally {
            runCatching { retriever.release() }
        }
    }
}

/** Reproductor simple de preescucha para probar tonos y canciones sin esperar una alarma. */
object SoundPreviewPlayer {
    private var player: MediaPlayer? = null
    var activeKey: String? = null
        private set

    fun playRaw(
        context: Context,
        @RawRes rawResId: Int,
        key: String,
        onCompletion: () -> Unit,
    ) {
        stop()
        val alarmAttrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        runCatching {
            val afd = context.resources.openRawResourceFd(rawResId) ?: return
            MediaPlayer().apply {
                setAudioAttributes(alarmAttrs)
                afd.use { setDataSource(it.fileDescriptor, it.startOffset, it.length) }
                setOnCompletionListener {
                    stop()
                    onCompletion()
                }
                prepare()
                start()
            }
        }.onSuccess {
            player = it
            activeKey = key
        }.onFailure {
            stop()
            onCompletion()
        }
    }

    fun playFile(
        path: String,
        key: String,
        onCompletion: () -> Unit,
    ) {
        stop()
        val file = File(path)
        if (!file.exists()) {
            onCompletion()
            return
        }
        val alarmAttrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()
        runCatching {
            MediaPlayer().apply {
                setAudioAttributes(alarmAttrs)
                setDataSource(file.absolutePath)
                setOnCompletionListener {
                    stop()
                    onCompletion()
                }
                prepare()
                start()
            }
        }.onSuccess {
            player = it
            activeKey = key
        }.onFailure {
            stop()
            onCompletion()
        }
    }

    fun stop() {
        activeKey = null
        player?.let { mp ->
            runCatching {
                if (mp.isPlaying) mp.stop()
            }
            runCatching { mp.release() }
        }
        player = null
    }
}
