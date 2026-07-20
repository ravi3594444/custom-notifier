package com.example

import android.content.Context
import android.media.MediaPlayer
import android.net.Uri
import android.provider.MediaStore
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.storage.storage
import kotlinx.serialization.Serializable

@Serializable
data class UserSoundPreference(
    val email: String,
    val selected_file_name: String,
    val selected_file_size: String,
    val trim_range_start: Float,
    val trim_range_end: Float,
    val media_duration_ms: Long,
    val fade_in_sec: Float,
    val fade_out_sec: Float,
    val volume_boost: Float,
    val last_updated: Long
)

class NotificationSetterViewModel : ViewModel() {

    private val _isProcessing = MutableStateFlow(false)
    val isProcessing: StateFlow<Boolean> = _isProcessing.asStateFlow()

    private val _processingStatus = MutableStateFlow("")
    val processingStatus: StateFlow<String> = _processingStatus.asStateFlow()

    private val _selectedMediaUri = MutableStateFlow<Uri?>(null)
    val selectedMediaUri: StateFlow<Uri?> = _selectedMediaUri.asStateFlow()

    private val _selectedFileName = MutableStateFlow("")
    val selectedFileName: StateFlow<String> = _selectedFileName.asStateFlow()

    private val _selectedFileSize = MutableStateFlow("")
    val selectedFileSize: StateFlow<String> = _selectedFileSize.asStateFlow()

    private val _mediaDurationMs = MutableStateFlow(0L)
    val mediaDurationMs: StateFlow<Long> = _mediaDurationMs.asStateFlow()

    private val _trimRange = MutableStateFlow(0f..100f)
    val trimRange: StateFlow<ClosedFloatingPointRange<Float>> = _trimRange.asStateFlow()

    private val _fadeInSec = MutableStateFlow(0f)
    val fadeInSec: StateFlow<Float> = _fadeInSec.asStateFlow()

    private val _fadeOutSec = MutableStateFlow(0f)
    val fadeOutSec: StateFlow<Float> = _fadeOutSec.asStateFlow()

    private val _volumeBoost = MutableStateFlow(1.0f)
    val volumeBoost: StateFlow<Float> = _volumeBoost.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _currentPlaybackPos = MutableStateFlow(0f)
    val currentPlaybackPos: StateFlow<Float> = _currentPlaybackPos.asStateFlow()

    private val _currentSystemNotificationSound = MutableStateFlow("Checking...")
    val currentSystemNotificationSound: StateFlow<String> = _currentSystemNotificationSound.asStateFlow()

    private val _currentScreen = MutableStateFlow("customizer")
    val currentScreen: StateFlow<String> = _currentScreen.asStateFlow()

    // --- "My Sounds" library state -----------------------------------------
    // Holds the list of sounds the user has previously created. Updated
    // automatically whenever processAudioAndSet() runs, and also when the
    // user re-opens the app (loadSoundLibrary is called from the screen).
    private val _savedSounds = MutableStateFlow<List<SavedSound>>(emptyList())
    val savedSounds: StateFlow<List<SavedSound>> = _savedSounds.asStateFlow()

    // The id of the sound currently being previewed from the library, or
    // null if none. Used by the UI to show a "Stop" button on the row.
    private val _previewingSoundId = MutableStateFlow<String?>(null)
    val previewingSoundId: StateFlow<String?> = _previewingSoundId.asStateFlow()

    // --- "My Videos" library state (Call Video Wallpaper feature) --------
    // Mirrors savedSounds but for video files. Loaded when the user opens
    // the Call Video Wallpaper screen; updated whenever a new video is
    // imported or an existing one is deleted.
    private val _savedVideos = MutableStateFlow<List<SavedVideo>>(emptyList())
    val savedVideos: StateFlow<List<SavedVideo>> = _savedVideos.asStateFlow()

    // The id of the video that will play the next time an incoming call
    // arrives. Null if no video is set. Stored in SharedPreferences via
    // VideoLibraryManager.
    private val _activeVideoId = MutableStateFlow<String?>(null)
    val activeVideoId: StateFlow<String?> = _activeVideoId.asStateFlow()

    // The id of the video currently being previewed in-app (so the UI can
    // show a Stop button). Null when no preview is playing.
    private val _previewingVideoId = MutableStateFlow<String?>(null)
    val previewingVideoId: StateFlow<String?> = _previewingVideoId.asStateFlow()

    // Separate MediaPlayer used for video previews. We keep it independent
    // of the audio mediaPlayer above so audio previews and video previews
    // never step on each other.
    private val videoPreviewPlayer = MediaPlayer()

    private val mediaPlayer = MediaPlayer()
    private var playbackJob: Job? = null

    fun setCurrentScreen(screen: String) {
        _currentScreen.value = screen
    }

    fun updateTrimRange(range: ClosedFloatingPointRange<Float>) {
        _trimRange.value = range
    }

    fun updateFadeInSec(value: Float) {
        _fadeInSec.value = value
    }

    fun updateFadeOutSec(value: Float) {
        _fadeOutSec.value = value
    }

    fun updateVolumeBoost(value: Float) {
        _volumeBoost.value = value
    }

    fun seekTo(position: Float) {
        try {
            mediaPlayer.seekTo(position.toInt())
            _currentPlaybackPos.value = position
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun setSystemNotificationSoundName(name: String) {
        _currentSystemNotificationSound.value = name
    }

    fun refreshNotificationSoundName(context: Context) {
        val appContext = context.applicationContext
        viewModelScope.launch(Dispatchers.IO) {
            val name = RingtoneUtils.getCurrentNotificationSoundName(appContext)
            _currentSystemNotificationSound.value = name
        }
    }

    // =========================================================================
    // "My Sounds" library
    // =========================================================================

    /**
     * Loads the user's saved-sound library into [_savedSounds]. Called when
     * the user opens the "My Sounds" screen and also after sign-in.
     */
    fun loadSoundLibrary(context: Context, userEmail: String) {
        val appContext = context.applicationContext
        viewModelScope.launch(Dispatchers.IO) {
            _savedSounds.value = SoundLibraryManager.loadAll(appContext, userEmail)
        }
    }

    /**
     * Toggles preview playback for a saved sound. Tapping the same row
     * twice stops the preview. Tapping a different row switches to it.
     */
    fun togglePreview(context: Context, sound: SavedSound) {
        val appContext = context.applicationContext
        if (_previewingSoundId.value == sound.id) {
            stopPreview()
            return
        }
        stopPreview()
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    mediaPlayer.reset()
                    mediaPlayer.setDataSource(appContext, Uri.fromFile(File(sound.localFilePath)))
                    mediaPlayer.prepare()
                    mediaPlayer.setOnCompletionListener {
                        _isPlaying.value = false
                        _previewingSoundId.value = null
                    }
                    mediaPlayer.start()
                }
                _isPlaying.value = true
                _previewingSoundId.value = sound.id
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    Toast.makeText(appContext, "Could not play this sound.", Toast.LENGTH_SHORT).show()
                }
                _previewingSoundId.value = null
            }
        }
    }

    /** Stops any library preview that's currently playing. */
    fun stopPreview() {
        try {
            if (mediaPlayer.isPlaying) mediaPlayer.pause()
            mediaPlayer.reset()
        } catch (_: Exception) {}
        _isPlaying.value = false
        _previewingSoundId.value = null
    }

    /**
     * Re-applies a previously-saved sound as the system default without
     * re-trimming or re-processing. The saved file already has all the
     * user's trim / fade / volume settings baked in.
     */
    fun applySavedSound(
        context: Context,
        userEmail: String,
        sound: SavedSound,
        setAsNotification: Boolean,
        setAsRingtone: Boolean
    ) {
        val appContext = context.applicationContext
        val file = File(sound.localFilePath)
        if (!file.exists()) {
            viewModelScope.launch {
                withContext(Dispatchers.Main) {
                    Toast.makeText(appContext, "Saved file is missing — re-create it in the editor.", Toast.LENGTH_LONG).show()
                }
                // Drop the stale entry so the list isn't full of dead rows.
                _savedSounds.value = SoundLibraryManager.remove(appContext, userEmail, sound.id)
            }
            return
        }
        viewModelScope.launch {
            _isProcessing.value = true
            _processingStatus.value = "Applying saved sound..."
            try {
                withContext(Dispatchers.IO) {
                    RingtoneUtils.setRingtoneFromUri(
                        appContext,
                        Uri.fromFile(file),
                        isExtracted = true,
                        setAsNotification = setAsNotification,
                        setAsRingtone = setAsRingtone
                    )
                }
                // Update the "last applied as" badge on the entry.
                val newBadge = when {
                    setAsNotification && setAsRingtone -> "both"
                    setAsRingtone -> "ringtone"
                    else -> "notification"
                }
                val updated = sound.copy(lastAppliedAs = newBadge)
                _savedSounds.value = SoundLibraryManager.update(appContext, userEmail, updated)
                refreshNotificationSoundName(appContext)
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    Toast.makeText(appContext, "Failed to apply: ${e.message}", Toast.LENGTH_LONG).show()
                }
            } finally {
                _isProcessing.value = false
            }
        }
    }

    /** Permanently removes a sound from the library and deletes its file. */
    fun deleteSavedSound(context: Context, userEmail: String, sound: SavedSound) {
        val appContext = context.applicationContext
        if (_previewingSoundId.value == sound.id) stopPreview()
        viewModelScope.launch(Dispatchers.IO) {
            _savedSounds.value = SoundLibraryManager.remove(appContext, userEmail, sound.id)
        }
    }

    // =========================================================================
    // "My Videos" library (Call Video Wallpaper feature)
    // =========================================================================

    /**
     * Loads the user's saved-video library into [_savedVideos] and the
     * active video id into [_activeVideoId]. Called when the user opens
     * the Call Video Wallpaper screen and also after sign-in.
     */
    fun loadVideoLibrary(context: Context, userEmail: String) {
        val appContext = context.applicationContext
        viewModelScope.launch(Dispatchers.IO) {
            _savedVideos.value = VideoLibraryManager.loadAll(appContext, userEmail)
            _activeVideoId.value = VideoLibraryManager.getActiveVideoId(appContext)
        }
    }

    /**
     * Imports the supplied [sourceUri] into the user's video library.
     * This is a long operation (file copy + metadata extraction), so it
     * runs on Dispatchers.IO and surfaces progress via [_processingStatus].
     */
    fun importVideoFromUri(context: Context, userEmail: String, sourceUri: Uri) {
        val appContext = context.applicationContext
        viewModelScope.launch {
            _isProcessing.value = true
            _processingStatus.value = "Importing video..."
            try {
                val added = withContext(Dispatchers.IO) {
                    VideoLibraryManager.importFromUri(appContext, userEmail, sourceUri)
                }
                if (added != null) {
                    _savedVideos.value = VideoLibraryManager.loadAll(appContext, userEmail)
                    withContext(Dispatchers.Main) {
                        Toast.makeText(appContext, "Video added: ${added.displayName}", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(appContext, "Could not import that video.", Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    Toast.makeText(appContext, "Import failed: ${e.message}", Toast.LENGTH_LONG).show()
                }
            } finally {
                _isProcessing.value = false
            }
        }
    }

    /**
     * Toggles in-app preview playback for a saved video. Plays only the
     * audio track (no video surface) since the library list doesn't have a
     * place to render the picture — this is just a "what does this sound
     * like?" check. The actual full-screen video plays on the incoming-call
     * activity.
     *
     * Tapping the same row twice stops the preview. Tapping a different
     * row switches to it.
     */
    fun toggleVideoPreview(context: Context, video: SavedVideo) {
        val appContext = context.applicationContext
        if (_previewingVideoId.value == video.id) {
            stopVideoPreview()
            return
        }
        stopVideoPreview()
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    videoPreviewPlayer.reset()
                    videoPreviewPlayer.setDataSource(appContext, Uri.fromFile(File(video.localFilePath)))
                    videoPreviewPlayer.prepare()
                    videoPreviewPlayer.setOnCompletionListener {
                        _previewingVideoId.value = null
                    }
                    videoPreviewPlayer.start()
                }
                _previewingVideoId.value = video.id
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    Toast.makeText(appContext, "Could not preview this video.", Toast.LENGTH_SHORT).show()
                }
                _previewingVideoId.value = null
            }
        }
    }

    /** Stops any video preview that's currently playing. */
    fun stopVideoPreview() {
        try {
            if (videoPreviewPlayer.isPlaying) videoPreviewPlayer.pause()
            videoPreviewPlayer.reset()
        } catch (_: Exception) {}
        _previewingVideoId.value = null
    }

    /**
     * Marks the supplied video as the one that should play on the next
     * incoming call. Persists to SharedPreferences so CallVideoReceiver
     * can read it when the phone rings.
     */
    fun setActiveVideo(context: Context, userEmail: String, video: SavedVideo) {
        val appContext = context.applicationContext
        viewModelScope.launch(Dispatchers.IO) {
            VideoLibraryManager.setActiveVideoId(appContext, video.id, userEmail)
            _activeVideoId.value = video.id
            withContext(Dispatchers.Main) {
                Toast.makeText(
                    appContext,
                    "Set as call video: ${video.displayName}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    /** Clears the active video (so calls ring normally with no video wallpaper). */
    fun clearActiveVideo(context: Context, userEmail: String) {
        val appContext = context.applicationContext
        viewModelScope.launch(Dispatchers.IO) {
            VideoLibraryManager.setActiveVideoId(appContext, null, userEmail)
            _activeVideoId.value = null
            withContext(Dispatchers.Main) {
                Toast.makeText(
                    appContext,
                    "Call video wallpaper disabled",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    /** Permanently removes a video from the library and deletes its file. */
    fun deleteSavedVideo(context: Context, userEmail: String, video: SavedVideo) {
        val appContext = context.applicationContext
        if (_previewingVideoId.value == video.id) stopVideoPreview()
        viewModelScope.launch(Dispatchers.IO) {
            _savedVideos.value = VideoLibraryManager.remove(appContext, userEmail, video.id)
            // If we just deleted the active video, also clear the active
            // state flow so the UI updates.
            if (_activeVideoId.value == video.id) {
                _activeVideoId.value = null
            }
        }
    }

    /** Updates an existing video in the library. */
    fun updateSavedVideo(context: Context, userEmail: String, video: SavedVideo) {
        val appContext = context.applicationContext
        viewModelScope.launch(Dispatchers.IO) {
            val currentList = VideoLibraryManager.loadAll(appContext, userEmail).toMutableList()
            val index = currentList.indexOfFirst { it.id == video.id }
            if (index >= 0) {
                currentList[index] = video
                VideoLibraryManager.saveAll(appContext, userEmail, currentList)
                _savedVideos.value = VideoLibraryManager.loadAll(appContext, userEmail)
            }
        }
    }

    /**
     * Copies the processed audio file into the user's permanent saved_sounds
     * directory and appends a [SavedSound] entry to the library index. Called
     * automatically at the end of [processAudioAndSet]. Safe to call from any
     * dispatcher -- does disk IO on Dispatchers.IO.
     *
     * Returns the entry that was added, or null if saving failed.
     */
    private suspend fun saveProcessedToLibrary(
        context: Context,
        userEmail: String,
        processedFile: File,
        originalFileName: String,
        durationMs: Long,
        setAsNotification: Boolean,
        setAsRingtone: Boolean
    ): SavedSound? = withContext(Dispatchers.IO) {
        try {
            val id = UUID.randomUUID().toString()
            val dir = SoundLibraryManager.savedSoundsDir(context)
            // Use the same extension as the processed file (.m4a) so the
            // MediaPlayer / MediaStore are happy later.
            val ext = processedFile.extension.ifBlank { "m4a" }
            val savedFile = File(dir, "sound_${id}.$ext")
            processedFile.inputStream().use { input ->
                savedFile.outputStream().use { output -> input.copyTo(output) }
            }

            val badge = when {
                setAsNotification && setAsRingtone -> "both"
                setAsRingtone -> "ringtone"
                else -> "notification"
            }

            val entry = SavedSound(
                id = id,
                displayName = originalFileName.ifBlank { "Custom Sound" },
                localFilePath = savedFile.absolutePath,
                fileSizeBytes = savedFile.length(),
                durationMs = durationMs,
                trimRangeStart = _trimRange.value.start,
                trimRangeEnd = _trimRange.value.endInclusive,
                fadeInSec = _fadeInSec.value,
                fadeOutSec = _fadeOutSec.value,
                volumeBoost = _volumeBoost.value,
                createdAt = System.currentTimeMillis(),
                lastAppliedAs = badge
            )
            _savedSounds.value = SoundLibraryManager.add(context, userEmail, entry)
            entry
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun stopAndResetPlayer() {
        _isPlaying.value = false
        playbackJob?.cancel()
        try {
            if (mediaPlayer.isPlaying) {
                mediaPlayer.pause()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        try {
            mediaPlayer.reset()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private suspend fun copyUriToPermanentFile(context: Context, uri: Uri): File? = withContext(Dispatchers.IO) {
        try {
            val directory = File(context.filesDir, "custom_sounds")
            if (!directory.exists()) {
                directory.mkdirs()
            }
            val permanentFile = File(directory, "selected_sound_${System.currentTimeMillis()}.bin")
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                java.io.FileOutputStream(permanentFile).use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
            }
            // Delete older files to save storage space
            try {
                directory.listFiles()?.forEach { file ->
                    if (file.name != permanentFile.name) {
                        file.delete()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            permanentFile
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun saveLocalPreference(context: Context, email: String) {
        val prefs = context.getSharedPreferences("user_sound_prefs_$email", Context.MODE_PRIVATE)
        prefs.edit().apply {
            putString("selectedFileName", _selectedFileName.value)
            putString("selectedFileSize", _selectedFileSize.value)
            putLong("mediaDurationMs", _mediaDurationMs.value)
            putFloat("trimRangeStart", _trimRange.value.start)
            putFloat("trimRangeEnd", _trimRange.value.endInclusive)
            putFloat("fadeInSec", _fadeInSec.value)
            putFloat("fadeOutSec", _fadeOutSec.value)
            putFloat("volumeBoost", _volumeBoost.value)
            putString("selectedMediaUri", _selectedMediaUri.value?.toString() ?: "")
            apply()
        }
    }

    private fun loadLocalPreference(context: Context, email: String): Boolean {
        val prefs = context.getSharedPreferences("user_sound_prefs_$email", Context.MODE_PRIVATE)
        val fileName = prefs.getString("selectedFileName", null) ?: return false
        val fileSize = prefs.getString("selectedFileSize", "") ?: ""
        val durationMs = prefs.getLong("mediaDurationMs", 0L)
        val start = prefs.getFloat("trimRangeStart", 0f)
        val end = prefs.getFloat("trimRangeEnd", durationMs.toFloat())
        val fadeIn = prefs.getFloat("fadeInSec", 0f)
        val fadeOut = prefs.getFloat("fadeOutSec", 0f)
        val volume = prefs.getFloat("volumeBoost", 1.0f)
        val uriStr = prefs.getString("selectedMediaUri", null)
        
        _selectedFileName.value = fileName
        _selectedFileSize.value = fileSize
        _mediaDurationMs.value = durationMs
        _trimRange.value = start..end
        _fadeInSec.value = fadeIn
        _fadeOutSec.value = fadeOut
        _volumeBoost.value = volume
        
        if (!uriStr.isNullOrEmpty()) {
            try {
                val uri = Uri.parse(uriStr)
                if (uri.scheme == "file") {
                    val file = File(uri.path ?: "")
                    if (file.exists()) {
                        _selectedMediaUri.value = uri
                        mediaPlayer.reset()
                        mediaPlayer.setDataSource(context, uri)
                        mediaPlayer.prepare()
                        _currentPlaybackPos.value = start
                        mediaPlayer.seekTo(start.toInt())
                        return true
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        return false
    }

    fun loadCloudPreference(context: Context, userEmail: String) {
        if (userEmail.isEmpty()) return
        val appContext = context.applicationContext
        _isProcessing.value = true
        _processingStatus.value = "Loading sound preferences..."

        // Everything here runs on Dispatchers.IO because:
        //  - loadLocalPreference calls mediaPlayer.setDataSource / prepare(),
        //    which do file IO + media extraction and MUST NOT run on the
        //    main thread (causes ANR -> system kills the app).
        //  - Supabase calls are network IO.
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // 1. Load from local SharedPreferences instantly for best
                //    offline responsiveness.
                val hasLocal = try {
                    loadLocalPreference(appContext, userEmail)
                } catch (e: Exception) {
                    e.printStackTrace()
                    false
                }
                if (hasLocal) {
                    _isProcessing.value = false
                }

                // 2. Query Supabase for cloud sync (only if not guest account)
                if (!userEmail.startsWith("guest_")) {
                    try {
                        val pref = try {
                            SupabaseClientManager.client.postgrest["user_preferences"]
                                .select {
                                    filter {
                                        eq("email", userEmail)
                                    }
                                }.decodeSingleOrNull<UserSoundPreference>()
                        } catch (e: Exception) {
                            e.printStackTrace()
                            null
                        }

                        if (pref != null) {
                            // Only override if local values are different or non-existent
                            if (_selectedFileName.value != pref.selected_file_name) {
                                _processingStatus.value = "Downloading cloud sound file..."
                                val localFile = File(appContext.cacheDir, "cloud_${pref.selected_file_name}")
                                try {
                                    val bucket = SupabaseClientManager.client.storage["sounds"]
                                    val safeEmail = userEmail.replace("@", "_").replace(".", "_")
                                    val bytes = bucket.downloadPublic("users/$safeEmail/notification_sound")
                                    localFile.writeBytes(bytes)

                                    val restoredUri = Uri.fromFile(localFile)
                                    _selectedMediaUri.value = restoredUri
                                    _selectedFileName.value = pref.selected_file_name
                                    _selectedFileSize.value = pref.selected_file_size
                                    _mediaDurationMs.value = pref.media_duration_ms
                                    _trimRange.value = pref.trim_range_start..pref.trim_range_end
                                    _fadeInSec.value = pref.fade_in_sec
                                    _fadeOutSec.value = pref.fade_out_sec
                                    _volumeBoost.value = pref.volume_boost

                                    mediaPlayer.reset()
                                    mediaPlayer.setDataSource(appContext, restoredUri)
                                    mediaPlayer.prepare()
                                    _currentPlaybackPos.value = pref.trim_range_start
                                    mediaPlayer.seekTo(pref.trim_range_start.toInt())

                                    saveLocalPreference(appContext, userEmail)
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    } finally {
                        _isProcessing.value = false
                    }
                } else {
                    _isProcessing.value = false
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _isProcessing.value = false
            }
        }
    }

    fun onFilePicked(context: Context, uri: Uri) {
        val appContext = context.applicationContext
        viewModelScope.launch {
            _isProcessing.value = true
            _processingStatus.value = "Reading selected file..."
            
            // Clean up old audio state completely to prevent any multi-threading media player crashes
            stopAndResetPlayer()
            
            var name = "sound_clip.mp3"
            var sizeStr = ""
            
            try {
                withContext(Dispatchers.IO) {
                    appContext.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                        if (cursor.moveToFirst()) {
                            val nameIndex = cursor.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
                            val sizeIndex = cursor.getColumnIndex(MediaStore.MediaColumns.SIZE)
                            if (nameIndex != -1) {
                                name = cursor.getString(nameIndex)
                            }
                            if (sizeIndex != -1) {
                                sizeStr = RingtoneUtils.formatFileSize(cursor.getLong(sizeIndex))
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            
            _selectedFileName.value = name
            _selectedFileSize.value = sizeStr

            // Immediately copy to a persistent local cache file so we have permanent file access permissions
            val permanentLocalFile = copyUriToPermanentFile(appContext, uri)
            val uriToUse = if (permanentLocalFile != null) Uri.fromFile(permanentLocalFile) else uri

            val mimeType = appContext.contentResolver.getType(uri) ?: ""
            val finalUri = if (mimeType.startsWith("video/")) {
                _processingStatus.value = "Extracting high-quality audio track..."
                val cacheFile = File(appContext.cacheDir, "extracted_audio_${System.currentTimeMillis()}.m4a")
                val success = RingtoneUtils.extractAudioFromVideo(appContext, uriToUse, cacheFile)
                if (success) Uri.fromFile(cacheFile) else uriToUse
            } else {
                uriToUse
            }

            try {
                _processingStatus.value = "Preparing preview player..."
                withContext(Dispatchers.IO) {
                    mediaPlayer.reset()
                    mediaPlayer.setDataSource(appContext, finalUri)
                    mediaPlayer.prepare()
                }
                val duration = mediaPlayer.duration.toLong()
                _mediaDurationMs.value = duration
                _trimRange.value = 0f..duration.toFloat()
                _currentPlaybackPos.value = 0f
                _selectedMediaUri.value = finalUri
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(appContext, "Failed to load media file", Toast.LENGTH_SHORT).show()
                }
                _selectedFileName.value = ""
                _selectedFileSize.value = ""
                _selectedMediaUri.value = null
            }
            _isProcessing.value = false
        }
    }

    fun togglePlayPause() {
        if (_isPlaying.value) {
            pause()
        } else {
            play()
        }
    }

    private fun play() {
        _isPlaying.value = true
        mediaPlayer.start()
        startPlaybackProgressTicker()
    }

    fun pause() {
        _isPlaying.value = false
        try {
            if (mediaPlayer.isPlaying) {
                mediaPlayer.pause()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        playbackJob?.cancel()
    }

    fun rewind5s() {
        val newPos = (_currentPlaybackPos.value - 5000f).coerceAtLeast(_trimRange.value.start)
        seekTo(newPos)
    }

    fun forward5s() {
        val newPos = (_currentPlaybackPos.value + 5000f).coerceIn(_trimRange.value.start, _trimRange.value.endInclusive)
        seekTo(newPos)
    }

    fun clearSelectedFile() {
        stopAndResetPlayer()
        _selectedMediaUri.value = null
        _selectedFileName.value = ""
        _selectedFileSize.value = ""
        _mediaDurationMs.value = 0L
        _trimRange.value = 0f..100f
        _currentPlaybackPos.value = 0f
        _fadeInSec.value = 0f
        _fadeOutSec.value = 0f
        _volumeBoost.value = 1.0f
    }

    private fun startPlaybackProgressTicker() {
        playbackJob?.cancel()
        playbackJob = viewModelScope.launch {
            while (_isPlaying.value) {
                try {
                    val currentPos = mediaPlayer.currentPosition.toFloat()
                    _currentPlaybackPos.value = currentPos
                    if (currentPos >= _trimRange.value.endInclusive) {
                        mediaPlayer.pause()
                        mediaPlayer.seekTo(_trimRange.value.start.toInt())
                        _currentPlaybackPos.value = _trimRange.value.start
                        _isPlaying.value = false
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                delay(50)
            }
        }
    }

    fun processAudioAndSet(
        context: Context,
        userEmail: String,
        setAsNotification: Boolean,
        setAsRingtone: Boolean
    ) {
        val appContext = context.applicationContext
        viewModelScope.launch {
            _isProcessing.value = true
            stopAndResetPlayer()
            
            _processingStatus.value = "Slicing and normalizing audio levels..."
            
            val processedFile = File(appContext.cacheDir, "processed_audio_${System.currentTimeMillis()}.m4a")
            val success = AudioProcessor.process(
                appContext,
                _selectedMediaUri.value!!,
                processedFile,
                _trimRange.value.start.toLong(),
                _trimRange.value.endInclusive.toLong(),
                fadeInMs = (_fadeInSec.value * 1000).toLong(),
                fadeOutMs = (_fadeOutSec.value * 1000).toLong(),
                volumeMultiplier = _volumeBoost.value
            )
            
            val finalUri: Uri
            val finalIsExtracted: Boolean
            
            if (success) {
                finalUri = Uri.fromFile(processedFile)
                finalIsExtracted = true
                withContext(Dispatchers.Main) {
                    Toast.makeText(appContext, "Audio normalized successfully!", Toast.LENGTH_SHORT).show()
                }
            } else {
                finalUri = _selectedMediaUri.value!!
                finalIsExtracted = false
                withContext(Dispatchers.Main) {
                    Toast.makeText(appContext, "Audio processing failed, using original file.", Toast.LENGTH_SHORT).show()
                }
            }
            
            withContext(Dispatchers.IO) {
                RingtoneUtils.setRingtoneFromUri(
                    appContext,
                    finalUri,
                    isExtracted = finalIsExtracted,
                    setAsNotification = setAsNotification,
                    setAsRingtone = setAsRingtone
                )
            }

            // --- "My Sounds" library: copy the processed file into the
            // user's permanent saved_sounds/ directory and append an entry
            // to their library index. Only do this when processing actually
            // succeeded — on failure we used the original file and there's
            // no new artifact to save.
            if (success) {
                saveProcessedToLibrary(
                    appContext, userEmail,
                    processedFile,
                    _selectedFileName.value,
                    _mediaDurationMs.value,
                    setAsNotification,
                    setAsRingtone
                )
            }

            // Capture editor state BEFORE clearSelectedFile() wipes it,
            // so the Supabase upload below has correct values to send.
            val capturedFileName = _selectedFileName.value
            val capturedFileSize = _selectedFileSize.value
            val capturedTrimStart = _trimRange.value.start
            val capturedTrimEnd = _trimRange.value.endInclusive
            val capturedDurationMs = _mediaDurationMs.value
            val capturedFadeIn = _fadeInSec.value
            val capturedFadeOut = _fadeOutSec.value
            val capturedVolumeBoost = _volumeBoost.value

            // Always save preferences locally first
            saveLocalPreference(appContext, userEmail)

            _isProcessing.value = false
            clearSelectedFile()
            refreshNotificationSoundName(appContext)

            // Sync to Supabase in background if user is not a guest.
            // Only upload if processing succeeded -- on failure processedFile
            // may not exist and there's nothing new to sync anyway.
            if (success && userEmail.isNotEmpty() && !userEmail.startsWith("guest_")) {
                launch(Dispatchers.IO) {
                    try {
                        val safeEmail = userEmail.replace("@", "_").replace(".", "_")
                        val bucket = SupabaseClientManager.client.storage["sounds"]
                        val bytes = processedFile.readBytes()
                        bucket.upload("users/$safeEmail/notification_sound", bytes) {
                            upsert = true
                        }

                        val pref = UserSoundPreference(
                            email = userEmail,
                            selected_file_name = capturedFileName,
                            selected_file_size = capturedFileSize,
                            trim_range_start = capturedTrimStart,
                            trim_range_end = capturedTrimEnd,
                            media_duration_ms = capturedDurationMs,
                            fade_in_sec = capturedFadeIn,
                            fade_out_sec = capturedFadeOut,
                            volume_boost = capturedVolumeBoost,
                            last_updated = System.currentTimeMillis()
                        )
                        SupabaseClientManager.client.postgrest["user_preferences"].upsert(pref)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        }
    }

    fun resetToSystemDefault(context: Context, userEmail: String, setAsNotification: Boolean, setAsRingtone: Boolean) {
        val appContext = context.applicationContext
        viewModelScope.launch {
            _isProcessing.value = true
            _processingStatus.value = "Resetting sounds..."
            
            try {
                withContext(Dispatchers.IO) {
                    if (setAsNotification) {
                        val defaultUri = RingtoneUtils.getSystemDefaultNotificationUri(appContext)
                        android.media.RingtoneManager.setActualDefaultRingtoneUri(
                            appContext,
                            android.media.RingtoneManager.TYPE_NOTIFICATION,
                            defaultUri
                        )
                    }
                    
                    if (setAsRingtone) {
                        val defaultUri = RingtoneUtils.getSystemDefaultRingtoneUri(appContext)
                        try {
                            android.media.RingtoneManager.setActualDefaultRingtoneUri(
                                appContext,
                                android.media.RingtoneManager.TYPE_RINGTONE,
                                defaultUri
                            )
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                    
                    RingtoneUtils.deleteCustomRingtonesFromSystem(appContext)
                }
                
                withContext(Dispatchers.Main) {
                    Toast.makeText(appContext, "Sound preferences reset!", Toast.LENGTH_SHORT).show()
                }
                
                // Clear local preferences
                val prefs = appContext.getSharedPreferences("user_sound_prefs_$userEmail", Context.MODE_PRIVATE)
                prefs.edit().clear().apply()

                // Clean up any persistent files
                try {
                    val directory = File(appContext.filesDir, "custom_sounds")
                    directory.listFiles()?.forEach { it.delete() }
                } catch (e: Exception) {
                    e.printStackTrace()
                }

                // Delete from Supabase in background
                if (userEmail.isNotEmpty() && !userEmail.startsWith("guest_")) {
                    launch(Dispatchers.IO) {
                        try {
                            val safeEmail = userEmail.replace("@", "_").replace(".", "_")
                            SupabaseClientManager.client.storage["sounds"].delete("users/$safeEmail/notification_sound")
                            SupabaseClientManager.client.postgrest["user_preferences"].delete {
                                filter {
                                    eq("email", userEmail)
                                }
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    Toast.makeText(appContext, "Reset failed: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            } finally {
                _isProcessing.value = false
                clearSelectedFile()
                refreshNotificationSoundName(appContext)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        try {
            stopPreview()
        } catch (_: Exception) {}
        try {
            stopVideoPreview()
        } catch (_: Exception) {}
        try {
            mediaPlayer.release()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        try {
            videoPreviewPlayer.release()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        playbackJob?.cancel()
    }
}
