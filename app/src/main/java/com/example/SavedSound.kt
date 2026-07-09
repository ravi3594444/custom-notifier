package com.example

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File

/**
 * One entry in the user's "My Sounds" library.
 *
 * Every time the user taps "Notification" or "Call Ringtone" in the editor,
 * the processed audio file is copied into [filesDir]/saved_sounds/ and an
 * entry of this type is appended to the user's library index file. That way
 * the user can later re-apply any previously-saved sound with one tap,
 * without re-trimming the original file.
 */
@Serializable
data class SavedSound(
    /** Stable UUID so the UI can use it as a Compose key. */
    val id: String,
    /** Human-friendly name shown in the list (usually the source file name). */
    val displayName: String,
    /** Absolute path under app filesDir/saved_sounds/. */
    val localFilePath: String,
    /** Size in bytes of the saved file. */
    val fileSizeBytes: Long,
    /** Total duration of the SAVED (post-trim) clip in ms. */
    val durationMs: Long,
    /** Trim window originally applied. Stored for display only. */
    val trimRangeStart: Float,
    val trimRangeEnd: Float,
    /** Fade-in seconds originally applied. */
    val fadeInSec: Float,
    /** Fade-out seconds originally applied. */
    val fadeOutSec: Float,
    /** Volume multiplier originally applied (0.0 .. 1.0). */
    val volumeBoost: Float,
    /** Epoch millis when this sound was saved. */
    val createdAt: Long,
    /**
     * "notification" | "ringtone" | "both" -- the last target the user
     * applied this sound to. For display only.
     */
    val lastAppliedAs: String
)

/**
 * Persists a per-user list of [SavedSound]s as a JSON file under app filesDir.
 *
 * We deliberately use a plain JSON file (instead of Room) because:
 *   - The list is small (typically 5-50 entries per user).
 *   - We already use kotlinx.serialization elsewhere in the app.
 *   - Adding Room + KSP would add compile time and config complexity for
 *     very little benefit at this scale.
 *
 * The index file is per-user so signing out and signing in as a different
 * user shows the right library. Each user's actual audio bytes live in a
 * shared `saved_sounds/` directory keyed by [SavedSound.id] in the file
 * name, so different users' files never collide.
 */
object SoundLibraryManager {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = false
    }

    private val listSerializer = ListSerializer(SavedSound.serializer())

    /**
     * Returns the JSON index file for the supplied user. The file may not
     * exist yet -- callers should treat a missing file as an empty library.
     */
    private fun indexFile(context: android.content.Context, userEmail: String): File {
        val safeEmail = userEmail.replace("@", "_").replace(".", "_")
        return File(context.filesDir, "sound_library_$safeEmail.json")
    }

    /**
     * Returns the directory where saved audio bytes live. Created on first
     * call so callers can just drop files into it.
     */
    fun savedSoundsDir(context: android.content.Context): File {
        val dir = File(context.filesDir, "saved_sounds")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    /**
     * Loads the user's library, newest-first. Returns an empty list if the
     * index file doesn't exist or fails to parse (we never throw -- a
     * corrupt index shouldn't brick the UI).
     */
    fun loadAll(context: android.content.Context, userEmail: String): List<SavedSound> {
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

    /**
     * Atomically rewrites the index with the supplied list. Writes to a
     * temp file first then renames, so a crash mid-write never leaves a
     * truncated index.
     */
    fun saveAll(context: android.content.Context, userEmail: String, sounds: List<SavedSound>) {
        try {
            val file = indexFile(context, userEmail)
            val tmp = File(file.parentFile, "${file.name}.tmp")
            tmp.writeText(json.encodeToString(listSerializer, sounds))
            tmp.renameTo(file)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /** Appends a new entry and returns the resulting list. */
    fun add(context: android.content.Context, userEmail: String, sound: SavedSound): List<SavedSound> {
        val current = loadAll(context, userEmail).toMutableList()
        // Avoid duplicate-id duplicates just in case.
        current.removeAll { it.id == sound.id }
        current.add(sound)
        val result = current.sortedByDescending { it.createdAt }
        saveAll(context, userEmail, result)
        return result
    }

    /** Removes the entry with the matching id (and tries to delete its file). */
    fun remove(context: android.content.Context, userEmail: String, id: String): List<SavedSound> {
        val current = loadAll(context, userEmail).toMutableList()
        val toRemove = current.firstOrNull { it.id == id }
        if (toRemove != null) {
            try { File(toRemove.localFilePath).delete() } catch (_: Exception) {}
            current.remove(toRemove)
            saveAll(context, userEmail, current)
        }
        return current.sortedByDescending { it.createdAt }
    }

    /** Updates an existing entry in-place (matched by id). Used when re-applying. */
    fun update(context: android.content.Context, userEmail: String, updated: SavedSound): List<SavedSound> {
        val current = loadAll(context, userEmail).toMutableList()
        val idx = current.indexOfFirst { it.id == updated.id }
        if (idx >= 0) current[idx] = updated
        val result = current.sortedByDescending { it.createdAt }
        saveAll(context, userEmail, result)
        return result
    }
}
