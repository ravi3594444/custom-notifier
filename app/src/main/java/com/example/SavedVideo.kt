package com.example

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

/**
 * One entry in the user's "My Videos" library for the Call Video Wallpaper feature.
 *
 * When the user picks a video file on the Call Video Wallpaper screen, the file
 * is copied into [filesDir]/saved_videos/ and an entry of this type is appended
 * to the user's video library index file. The currently-active entry (the one
 * that will play the next time a call arrives) is stored separately in
 * SharedPreferences via [VideoLibraryManager.setActiveVideoId].
 *
 * This mirrors the SavedSound / SoundLibraryManager design used by the audio
 * library, but with video-specific metadata (width / height) and no Supabase
 * sync (videos are large — cloud sync would burn through the free tier fast).
 */
@Serializable
data class SavedVideo(
    /** Stable UUID so the UI can use it as a Compose key. */
    val id: String,
    /** Human-friendly name shown in the list (the source file name). */
    val displayName: String,
    /** Absolute path under app filesDir/saved_videos/. */
    val localFilePath: String,
    /** Size in bytes of the saved file. */
    val fileSizeBytes: Long,
    /** Video duration in milliseconds. */
    val durationMs: Long,
    /** Video width in pixels (nullable for safety in case extraction fails). */
    val width: Int? = null,
    /** Video height in pixels (nullable for safety in case extraction fails). */
    val height: Int? = null,
    /** Epoch millis when this video was added to the library. */
    val createdAt: Long,
    /**
     * MIME type of the source video (e.g. "video/mp4"). Used by the
     * VideoView inside CallVideoActivity to pick the right decoder.
     */
    val mimeType: String,
    
    /** Optional start time for video trimming in milliseconds. */
    val trimStartMs: Long? = null,
    /** Optional end time for video trimming in milliseconds. */
    val trimEndMs: Long? = null,
    /** Optional local file path to a custom audio song to play instead of the video's audio. */
    val customAudioPath: String? = null,
    /** Optional scale for the video playback (e.g. zoom in/out). */
    val videoScale: Float? = null,
    /** Optional position for the name from 0.0 (top) to 1.0 (bottom). */
    val namePositionY: Float? = null,
    /** Optional answer style: "swipe" or "buttons". */
    val answerStyle: String? = null,
    /** Optional font size for the caller name in sp. */
    val nameFontSize: Float? = null,
    /** Optional font family for the caller name (e.g., "sans-serif", "serif", "monospace", "cursive"). */
    val nameFontFamily: String? = null,
    /** Optional ARGB color for the caller name text. */
    val nameTextColor: Int? = null,
    /** Optional ARGB color for the caller name background (can be transparent). */
    val nameBgColor: Int? = null
)

/**
 * Persists a per-user list of [SavedVideo]s as a JSON file under app filesDir,
 * plus the "currently active" video id in SharedPreferences.
 *
 * Design notes (mirroring SoundLibraryManager):
 *   - Plain JSON file instead of Room (small library, no need for KSP).
 *   - Per-user index file so signing in as a different user shows the right
 *     library. Actual video bytes live in a shared saved_videos/ directory
 *     keyed by [SavedVideo.id] in the filename, so different users' files
 *     never collide.
 *   - Atomic writes (.tmp + rename) so a crash mid-write never leaves a
 *     truncated index.
 *   - Active-video id is stored in a single shared SharedPreferences (not
 *     per-user) because there is only ever one active call-video at a time
 *     system-wide. We still validate that the active id belongs to the
 *     current user when reading it back, so cross-user leakage can't happen.
 */
object VideoLibraryManager {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = false
    }

    private val listSerializer = ListSerializer(SavedVideo.serializer())

    private const val PREFS_NAME = "call_video_prefs"
    private const val KEY_ACTIVE_VIDEO_ID = "active_video_id"
    private const val KEY_ACTIVE_USER_EMAIL = "active_user_email"

    /**
     * Returns the JSON index file for the supplied user. The file may not
     * exist yet -- callers should treat a missing file as an empty library.
     */
    private fun indexFile(context: Context, userEmail: String): File {
        val safeEmail = userEmail.replace("@", "_").replace(".", "_")
        return File(context.filesDir, "video_library_$safeEmail.json")
    }

    /**
     * Returns the directory where saved video bytes live. Created on first
     * call so callers can just drop files into it.
     */
    fun savedVideosDir(context: Context): File {
        val dir = File(context.filesDir, "saved_videos")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    /** Loads the user's library, newest-first. Returns an empty list on any error. */
    fun loadAll(context: Context, userEmail: String): List<SavedVideo> {
        return try {
            val file = indexFile(context, userEmail)
            if (!file.exists()) return emptyList()
            val text = file.readText()
            if (text.isBlank()) return emptyList()
            json.decodeFromString(listSerializer, text)
                .sortedByDescending { it.createdAt }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    /** Atomically rewrites the index with the supplied list. */
    fun saveAll(context: Context, userEmail: String, videos: List<SavedVideo>) {
        try {
            val file = indexFile(context, userEmail)
            val tmp = File(file.parentFile, "${file.name}.tmp")
            tmp.writeText(json.encodeToString(listSerializer, videos))
            tmp.renameTo(file)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Imports the supplied [sourceUri] into the user's library:
     *   1. Copies the bytes into saved_videos/video_<id>.<ext>
     *   2. Extracts duration / dimensions via MediaMetadataRetriever.
     *   3. Appends a [SavedVideo] entry to the index.
     * Returns the new entry on success, or null on failure.
     */
    fun importFromUri(context: Context, userEmail: String, sourceUri: Uri): SavedVideo? {
        return try {
            val id = UUID.randomUUID().toString()
            val dir = savedVideosDir(context)

            // Figure out the source display name + extension + mime type.
            var displayName = "video_${System.currentTimeMillis()}"
            var mimeType = "video/mp4"
            var ext = "mp4"
            try {
                context.contentResolver.query(sourceUri, null, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val nameIdx = cursor.getColumnIndex(android.provider.MediaStore.MediaColumns.DISPLAY_NAME)
                        val mimeIdx = cursor.getColumnIndex(android.provider.MediaStore.MediaColumns.MIME_TYPE)
                        if (nameIdx >= 0) {
                            cursor.getString(nameIdx).takeIf { it.isNotBlank() }?.let {
                                displayName = it
                            }
                        }
                        if (mimeIdx >= 0) {
                            cursor.getString(mimeIdx).takeIf { it.isNotBlank() }?.let {
                                mimeType = it
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            // Derive extension from display name if it has one, otherwise from mime type.
            val dotIdx = displayName.lastIndexOf('.')
            if (dotIdx > 0 && dotIdx < displayName.length - 1) {
                ext = displayName.substring(dotIdx + 1).lowercase()
            } else {
                ext = mimeType.substringAfter('/', "mp4").lowercase()
            }

            val savedFile = File(dir, "video_$id.$ext")
            context.contentResolver.openInputStream(sourceUri).use { input ->
                if (input == null) return null
                savedFile.outputStream().use { output -> input.copyTo(output) }
            }

            // Extract duration + dimensions via MediaMetadataRetriever.
            var durationMs = 0L
            var width: Int? = null
            var height: Int? = null
            try {
                val retriever = MediaMetadataRetriever()
                retriever.setDataSource(context, Uri.fromFile(savedFile))
                durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
                width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull()
                height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull()
                try { retriever.release() } catch (_: Exception) {}
            } catch (e: Exception) {
                e.printStackTrace()
            }

            val entry = SavedVideo(
                id = id,
                displayName = displayName,
                localFilePath = savedFile.absolutePath,
                fileSizeBytes = savedFile.length(),
                durationMs = durationMs,
                width = width,
                height = height,
                createdAt = System.currentTimeMillis(),
                mimeType = mimeType
            )

            val current = loadAll(context, userEmail).toMutableList()
            current.removeAll { it.id == id }
            current.add(entry)
            val result = current.sortedByDescending { it.createdAt }
            saveAll(context, userEmail, result)
            entry
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Copies an audio file to saved_videos/ and returns its absolute path.
     */
    fun importAudioForVideo(context: Context, sourceUri: Uri): String? {
        return try {
            val dir = savedVideosDir(context)
            var ext = "mp3"
            try {
                context.contentResolver.query(sourceUri, null, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val nameIdx = cursor.getColumnIndex(android.provider.MediaStore.MediaColumns.DISPLAY_NAME)
                        if (nameIdx >= 0) {
                            val name = cursor.getString(nameIdx)
                            val dotIdx = name.lastIndexOf('.')
                            if (dotIdx > 0 && dotIdx < name.length - 1) {
                                ext = name.substring(dotIdx + 1).lowercase()
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            val id = UUID.randomUUID().toString()
            val savedFile = File(dir, "audio_$id.$ext")
            context.contentResolver.openInputStream(sourceUri).use { input ->
                if (input == null) return null
                savedFile.outputStream().use { output -> input.copyTo(output) }
            }
            savedFile.absolutePath
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /** Removes the entry with the matching id (and deletes its file). */
    fun remove(context: Context, userEmail: String, id: String): List<SavedVideo> {
        val current = loadAll(context, userEmail).toMutableList()
        val toRemove = current.firstOrNull { it.id == id } ?: return current
        try { File(toRemove.localFilePath).delete() } catch (_: Exception) {}
        // Also delete the associated custom audio file (if any) — otherwise
        // every video delete leaves a dangling audio_<uuid>.<ext> file in
        // saved_videos/ that accumulates as a slow disk leak over weeks.
        toRemove.customAudioPath?.let { audioPath ->
            try { File(audioPath).delete() } catch (_: Exception) {}
        }
        current.remove(toRemove)
        saveAll(context, userEmail, current)
        // If the removed entry was the active one, clear the active id.
        if (getActiveVideoId(context) == id) {
            setActiveVideoId(context, null, userEmail)
        }
        return current.sortedByDescending { it.createdAt }
    }

    /**
     * Records which video in the user's library should play on the next
     * incoming call. Pass null to disable the feature (calls will ring
     * normally with no video wallpaper).
     *
     * We also store the user's email so that if a different user signs in,
     * we don't accidentally play the previous user's video.
     */
    fun setActiveVideoId(context: Context, id: String?, userEmail: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().apply {
            if (id == null) {
                remove(KEY_ACTIVE_VIDEO_ID)
                remove(KEY_ACTIVE_USER_EMAIL)
            } else {
                putString(KEY_ACTIVE_VIDEO_ID, id)
                putString(KEY_ACTIVE_USER_EMAIL, userEmail)
            }
            apply()
        }
    }

    /**
     * Returns the active video id, or null if none is set.
     *
     * Note: callers should also verify the returned id still exists in the
     * current user's library (use [getActiveVideo] for a one-shot helper).
     */
    fun getActiveVideoId(context: Context): String? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_ACTIVE_VIDEO_ID, null)
    }

    /**
     * Returns the active [SavedVideo] for the supplied user, or null if
     * there is no active video, the active video belongs to a different
     * user, or the active id no longer exists in the library.
     *
     * This is the canonical "what should I play when the phone rings"
     * lookup, called by [CallVideoReceiver].
     */
    fun getActiveVideo(context: Context, userEmail: String): SavedVideo? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val activeUser = prefs.getString(KEY_ACTIVE_USER_EMAIL, null) ?: return null
        if (activeUser != userEmail) return null
        val id = prefs.getString(KEY_ACTIVE_VIDEO_ID, null) ?: return null
        val videos = loadAll(context, userEmail)
        val match = videos.firstOrNull { it.id == id } ?: return null
        // Make sure the underlying file still exists; if not, clear the
        // active id so we don't keep trying to play a dead file.
        if (!File(match.localFilePath).exists()) {
            setActiveVideoId(context, null, userEmail)
            return null
        }
        return match
    }

    /**
     * Convenience overload used by [CallVideoReceiver] when the current
     * user's email isn't readily available: reads the active user email
     * from prefs and looks up the video in their library.
     */
    fun getActiveVideoAnyUser(context: Context): SavedVideo? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val activeUser = prefs.getString(KEY_ACTIVE_USER_EMAIL, null) ?: return null
        return getActiveVideo(context, activeUser)
    }
}
