package com.example

import android.content.ContentValues
import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.media.MediaScannerConnection
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.Settings
import android.widget.Toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer

object RingtoneUtils {

    fun formatFileSize(bytes: Long): String {
        if (bytes <= 0) return "0 KB"
        val kb = bytes / 1024f
        if (kb < 1024) return String.format("%.1f KB", kb)
        val mb = kb / 1024f
        return String.format("%.1f MB", mb)
    }

    fun formatTime(ms: Long): String {
        val totalSeconds = ms / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return String.format("%02d:%02d", minutes, seconds)
    }

    suspend fun extractAudioFromVideo(context: Context, videoUri: Uri, outputFile: File): Boolean = withContext(Dispatchers.IO) {
        val extractor = MediaExtractor()
        var muxer: MediaMuxer? = null
        var audioTrackIndex = -1
        var muxerAudioTrackIndex = -1
        try {
            extractor.setDataSource(context, videoUri, null)
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME)
                if (mime?.startsWith("audio/") == true) {
                    // Try to add track, catch any unsupported format exceptions from MediaMuxer
                    try {
                        extractor.selectTrack(i)
                        audioTrackIndex = i
                        muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
                        muxerAudioTrackIndex = muxer.addTrack(format)
                        break
                    } catch (e: Exception) {
                        e.printStackTrace()
                        muxer?.release()
                        muxer = null
                        audioTrackIndex = -1
                        continue
                    }
                }
            }
            if (audioTrackIndex == -1 || muxer == null) return@withContext false
            muxer.start()
            val buffer = ByteBuffer.allocate(1024 * 1024)
            val bufferInfo = MediaCodec.BufferInfo()
            while (true) {
                bufferInfo.size = extractor.readSampleData(buffer, 0)
                if (bufferInfo.size < 0) break
                bufferInfo.presentationTimeUs = extractor.sampleTime
                bufferInfo.offset = 0
                val sampleFlags = extractor.sampleFlags
                bufferInfo.flags = if ((sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC) != 0) {
                    MediaCodec.BUFFER_FLAG_KEY_FRAME
                } else {
                    0
                }
                muxer.writeSampleData(muxerAudioTrackIndex, buffer, bufferInfo)
                extractor.advance()
            }
            return@withContext true
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext false
        } finally {
            try { extractor.release() } catch (e: Exception) {}
            try { muxer?.stop(); muxer?.release() } catch (e: Exception) {}
        }
    }

    fun setRingtoneFromUri(
        context: Context,
        sourceUri: Uri,
        isExtracted: Boolean = false,
        setAsNotification: Boolean = true,
        setAsRingtone: Boolean = false
    ) {
        if (!Settings.System.canWrite(context)) {
            Toast.makeText(context, "Write Settings permission is missing.", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            var displayName = "custom_notification_sound_${System.currentTimeMillis()}.mp3"
            if (!isExtracted) {
                context.contentResolver.query(sourceUri, null, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val nameIndex = cursor.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
                        if (nameIndex != -1) {
                            displayName = cursor.getString(nameIndex)
                        }
                    }
                }
            } else {
                displayName = "extracted_audio_${System.currentTimeMillis()}.m4a"
            }

            // Clean up display name to avoid illegal chars
            displayName = displayName.replace(Regex("[^a-zA-Z0-9._-]"), "_")
            val title = displayName.substringBeforeLast(".")
            val mimeType = if (isExtracted) "audio/mp4" else (context.contentResolver.getType(sourceUri) ?: "audio/mpeg")

            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
                put(MediaStore.Audio.Media.TITLE, title)
                put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                put(MediaStore.Audio.Media.IS_NOTIFICATION, 1)
                put(MediaStore.Audio.Media.IS_RINGTONE, 1)
                put(MediaStore.Audio.Media.IS_ALARM, 1)
                put(MediaStore.Audio.Media.IS_MUSIC, 0)
                
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_NOTIFICATIONS)
                    put(MediaStore.Audio.Media.IS_PENDING, 1)
                } else {
                    val directory = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_NOTIFICATIONS)
                    if (!directory.exists()) {
                        directory.mkdirs()
                    }
                    val targetFile = File(directory, displayName)
                    put(MediaStore.MediaColumns.DATA, targetFile.absolutePath)
                }
            }

            val uri = context.contentResolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, contentValues)
            if (uri != null) {
                context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                    context.contentResolver.openInputStream(sourceUri)?.use { inputStream ->
                        inputStream.copyTo(outputStream)
                    }
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val updateValues = ContentValues().apply {
                        put(MediaStore.Audio.Media.IS_PENDING, 0)
                    }
                    context.contentResolver.update(uri, updateValues, null, null)
                } else {
                    val directory = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_NOTIFICATIONS)
                    val targetFile = File(directory, displayName)
                    MediaScannerConnection.scanFile(
                        context,
                        arrayOf(targetFile.absolutePath),
                        arrayOf(mimeType)
                    ) { _, _ -> }
                }

                if (setAsNotification) {
                    RingtoneManager.setActualDefaultRingtoneUri(context, RingtoneManager.TYPE_NOTIFICATION, uri)
                }
                if (setAsRingtone) {
                    try {
                        RingtoneManager.setActualDefaultRingtoneUri(context, RingtoneManager.TYPE_RINGTONE, uri)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }

                val msg = if (setAsNotification && setAsRingtone) "Notification & Ringtone sound updated successfully!" 
                          else if (setAsNotification) "Notification sound updated successfully!"
                          else "Ringtone updated successfully!"
                Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(context, "Failed to save audio file to system database.", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "Error setting sound: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    fun getCurrentNotificationSoundName(context: Context): String {
        return try {
            val uri = RingtoneManager.getActualDefaultRingtoneUri(context, RingtoneManager.TYPE_NOTIFICATION)
            if (uri != null) {
                val ringtone = RingtoneManager.getRingtone(context, uri)
                ringtone?.getTitle(context) ?: "Custom Sound"
            } else {
                "None / Silent"
            }
        } catch (e: Exception) {
            "Default / Unknown"
        }
    }

    fun deleteCustomRingtonesFromSystem(context: Context) {
        try {
            val projection = arrayOf(MediaStore.Audio.Media._ID, MediaStore.Audio.Media.DISPLAY_NAME)
            val selection = "${MediaStore.Audio.Media.DISPLAY_NAME} LIKE ? OR ${MediaStore.Audio.Media.DISPLAY_NAME} LIKE ?"
            val selectionArgs = arrayOf("%custom_notification_sound_%", "%extracted_audio_%")
            context.contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                null
            )?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idColumn)
                    val uri = android.content.ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)
                    try {
                        context.contentResolver.delete(uri, null, null)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun getSystemDefaultNotificationUri(context: Context): Uri {
        try {
            val manager = RingtoneManager(context).apply {
                setType(RingtoneManager.TYPE_NOTIFICATION)
            }
            val cursor = manager.cursor
            if (cursor.moveToFirst()) {
                do {
                    val id = cursor.getLong(RingtoneManager.ID_COLUMN_INDEX)
                    val uriStr = cursor.getString(RingtoneManager.URI_COLUMN_INDEX)
                    val title = cursor.getString(RingtoneManager.TITLE_COLUMN_INDEX) ?: ""
                    val fullUri = Uri.parse("$uriStr/$id")
                    
                    if (uriStr.contains("internal") && 
                        !title.contains("extracted_audio", ignoreCase = true) && 
                        !title.contains("custom_notification_sound", ignoreCase = true) && 
                        !title.contains("sound_clip", ignoreCase = true)) {
                        return fullUri
                    }
                } while (cursor.moveToNext())
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION) ?: Settings.System.DEFAULT_NOTIFICATION_URI
    }

    fun getSystemDefaultRingtoneUri(context: Context): Uri {
        try {
            val manager = RingtoneManager(context).apply {
                setType(RingtoneManager.TYPE_RINGTONE)
            }
            val cursor = manager.cursor
            if (cursor.moveToFirst()) {
                do {
                    val id = cursor.getLong(RingtoneManager.ID_COLUMN_INDEX)
                    val uriStr = cursor.getString(RingtoneManager.URI_COLUMN_INDEX)
                    val title = cursor.getString(RingtoneManager.TITLE_COLUMN_INDEX) ?: ""
                    val fullUri = Uri.parse("$uriStr/$id")
                    
                    if (uriStr.contains("internal") && 
                        !title.contains("extracted_audio", ignoreCase = true) && 
                        !title.contains("custom_notification_sound", ignoreCase = true) && 
                        !title.contains("sound_clip", ignoreCase = true)) {
                        return fullUri
                    }
                } while (cursor.moveToNext())
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE) ?: Settings.System.DEFAULT_RINGTONE_URI
    }
}
