package com.example

import android.content.Context
import android.media.MediaPlayer
import android.net.Uri
import android.provider.MediaStore
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

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

    fun loadCloudPreference(context: Context, userEmail: String) {
        if (userEmail.isEmpty()) return
        val appContext = context.applicationContext
        _isProcessing.value = true
        _processingStatus.value = "Loading cloud sound preference..."
        
        try {
            val db = FirebaseFirestore.getInstance()
            db.collection("users").document(userEmail).get()
                .addOnSuccessListener { document ->
                    if (document != null && document.exists()) {
                        val fileName = document.getString("selectedFileName") ?: ""
                        val fileSize = document.getString("selectedFileSize") ?: ""
                        val durationMs = document.getLong("mediaDurationMs") ?: 0L
                        val start = document.getDouble("trimRangeStart")?.toFloat() ?: 0f
                        val end = document.getDouble("trimRangeEnd")?.toFloat() ?: durationMs.toFloat()
                        val storagePath = document.getString("audioStoragePath") ?: ""
                        
                        if (fileName.isNotEmpty() && storagePath.isNotEmpty()) {
                            try {
                                val storage = com.google.firebase.storage.FirebaseStorage.getInstance()
                                val storageRef = storage.reference.child(storagePath)
                                val localFile = File(appContext.cacheDir, "cloud_$fileName")
                                
                                storageRef.getFile(localFile)
                                    .addOnSuccessListener {
                                        viewModelScope.launch {
                                            try {
                                                val restoredUri = Uri.fromFile(localFile)
                                                _selectedMediaUri.value = restoredUri
                                                _selectedFileName.value = fileName
                                                _selectedFileSize.value = fileSize
                                                _mediaDurationMs.value = durationMs
                                                _trimRange.value = start..end
                                                
                                                withContext(Dispatchers.IO) {
                                                    mediaPlayer.reset()
                                                    mediaPlayer.setDataSource(appContext, restoredUri)
                                                    mediaPlayer.prepare()
                                                }
                                                _currentPlaybackPos.value = start
                                                mediaPlayer.seekTo(start.toInt())
                                            } catch (e: Exception) {
                                                e.printStackTrace()
                                            }
                                            _isProcessing.value = false
                                        }
                                    }
                                    .addOnFailureListener {
                                        _isProcessing.value = false
                                        Toast.makeText(appContext, "Failed to download cloud sound", Toast.LENGTH_SHORT).show()
                                    }
                            } catch (e: Exception) {
                                e.printStackTrace()
                                _isProcessing.value = false
                            }
                        } else {
                            _isProcessing.value = false
                        }
                    } else {
                        _isProcessing.value = false
                    }
                }
                .addOnFailureListener {
                    _isProcessing.value = false
                }
        } catch (e: Exception) {
            e.printStackTrace()
            _isProcessing.value = false
        }
    }

    fun onFilePicked(context: Context, uri: Uri) {
        val appContext = context.applicationContext
        viewModelScope.launch {
            _isProcessing.value = true
            _processingStatus.value = "Reading selected file..."
            
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

            val mimeType = appContext.contentResolver.getType(uri) ?: ""
            val uriToUse = if (mimeType.startsWith("video/")) {
                _processingStatus.value = "Extracting high-quality audio track..."
                val cacheFile = File(appContext.cacheDir, "extracted_audio_${System.currentTimeMillis()}.m4a")
                val success = RingtoneUtils.extractAudioFromVideo(appContext, uri, cacheFile)
                if (success) Uri.fromFile(cacheFile) else uri
            } else {
                uri
            }

            try {
                _processingStatus.value = "Preparing preview player..."
                withContext(Dispatchers.IO) {
                    mediaPlayer.reset()
                    mediaPlayer.setDataSource(appContext, uriToUse)
                    mediaPlayer.prepare()
                }
                val duration = mediaPlayer.duration.toLong()
                _mediaDurationMs.value = duration
                _trimRange.value = 0f..duration.toFloat()
                _currentPlaybackPos.value = 0f
                _selectedMediaUri.value = uriToUse
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(appContext, "Failed to load media file", Toast.LENGTH_SHORT).show()
                }
                _selectedFileName.value = ""
                _selectedFileSize.value = ""
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
        mediaPlayer.pause()
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
        pause()
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
                val currentPos = mediaPlayer.currentPosition.toFloat()
                _currentPlaybackPos.value = currentPos
                if (currentPos >= _trimRange.value.endInclusive) {
                    mediaPlayer.pause()
                    mediaPlayer.seekTo(_trimRange.value.start.toInt())
                    _currentPlaybackPos.value = _trimRange.value.start
                    _isPlaying.value = false
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
            _isPlaying.value = false
            playbackJob?.cancel()
            mediaPlayer.pause()
            
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

            _isProcessing.value = false
            clearSelectedFile()
            refreshNotificationSoundName(appContext)

            // Upload to Cloud Storage and save metadata in Firestore if email is provided
            if (userEmail.isNotEmpty()) {
                try {
                    val safeEmail = userEmail.replace("@", "_").replace(".", "_")
                    val storage = com.google.firebase.storage.FirebaseStorage.getInstance()
                    val storageRef = storage.reference.child("users/$safeEmail/notification_sound")
                    
                    storageRef.putFile(finalUri)
                        .addOnSuccessListener {
                            try {
                                val db = FirebaseFirestore.getInstance()
                                val data = hashMapOf(
                                    "selectedFileName" to _selectedFileName.value,
                                    "selectedFileSize" to _selectedFileSize.value,
                                    "trimRangeStart" to _trimRange.value.start,
                                    "trimRangeEnd" to _trimRange.value.endInclusive,
                                    "mediaDurationMs" to _mediaDurationMs.value,
                                    "audioStoragePath" to "users/$safeEmail/notification_sound",
                                    "lastUpdated" to System.currentTimeMillis()
                                )
                                db.collection("users").document(userEmail).set(data)
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                        .addOnFailureListener { e ->
                            e.printStackTrace()
                        }
                } catch (e: Exception) {
                    e.printStackTrace()
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
                
                if (userEmail.isNotEmpty()) {
                    try {
                        val db = FirebaseFirestore.getInstance()
                        db.collection("users").document(userEmail).delete()
                        
                        val safeEmail = userEmail.replace("@", "_").replace(".", "_")
                        val storage = com.google.firebase.storage.FirebaseStorage.getInstance()
                        storage.reference.child("users/$safeEmail/notification_sound").delete()
                            .addOnFailureListener {
                                // ignore if file doesn't exist
                            }
                    } catch (e: Exception) {
                        e.printStackTrace()
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
            mediaPlayer.release()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        playbackJob?.cancel()
    }
}
