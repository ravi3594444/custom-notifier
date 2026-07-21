package com.example

import android.app.KeyguardManager
import android.content.Context
import android.content.pm.ActivityInfo
import android.os.Build
import android.os.Bundle
import android.telephony.TelephonyManager
import android.util.Log
import android.view.View
import android.view.WindowManager
import android.widget.VideoView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.clickable
import androidx.compose.ui.viewinterop.AndroidView
import java.io.File
import androidx.compose.ui.draw.scale
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.ui.input.pointer.pointerInput
import kotlin.math.roundToInt
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.foundation.gestures.detectHorizontalDragGestures

/**
 * Full-screen Activity that plays the user's chosen video when an incoming
 * call arrives, with Accept / Dismiss buttons overlaid on top.
 *
 * Lifecycle:
 *   - Launched by [CallVideoReceiver] when the phone starts ringing and
 *     the user has an active call-video set.
 *   - Sets showWhenLocked + turnScreenOn so it appears over the lockscreen.
 *   - Dismisses the keyguard so the user can tap the buttons without first
 *     having to swipe / enter their PIN.
 *   - Plays the video on loop with its own audio (the video's audio
 *     track replaces silence, NOT the system ringtone — see note below).
 *     The system ringtone still plays on STREAM_RING in parallel; we
 *     can't silence it without becoming the default dialer (huge scope).
 *     For video-only audio, the user should set their system call
 *     ringtone to 'None' in Android Sound settings.
 *   - When the user taps Accept: calls CallVideoController.answerCall()
 *     then finishes the activity so the system call screen takes over.
 *   - When the user taps Dismiss: calls CallVideoController.rejectCall()
 *     then finishes the activity.
 *   - If the call ends on its own (caller hung up, or user answered via
 *     the system call screen), a PhoneStateListener detects the state
 *     transition to OFFHOOK or IDLE and finishes the activity.
 *
 * Layout:
 *   - VideoView fills the entire screen (VideoView keeps aspect ratio by
 *     default, so on portrait phones a landscape video will be letterboxed
 *     — this is intentional and matches user expectations for a "video
 *     wallpaper" experience).
 *   - Two large round buttons float near the bottom of the screen, mirroring
 *     the standard Android call-accept / call-reject layout: green Accept
 *     on the left, red Dismiss on the right.
 *   - The video's display name shows as a small chip at the top so the user
 *     knows which video is playing.
 *
 * Threading:
 *   - VideoView does its own background threading for media playback, so
 *     we don't need to manage that ourselves.
 *   - CallVideoController calls are synchronous and fast — they're just
 *     reflective method invocations on TelephonyManager / TelecomManager.
 *   - The PhoneStateListener fires on the main thread; we just call
 *     finish() which is safe on main.
 */
class CallVideoActivity : ComponentActivity() {

    companion object {
        /**
         * Single Parcelable extra that carries all call-video configuration
         * (path, trim, styling, etc.). Replaces the 15 individual EXTRA_*
         * strings below — those are kept as @Deprecated aliases so any
         * caller that hasn't been migrated yet still works, but new code
         * should use EXTRA_CONFIG + CallVideoConfig.
         */
        const val EXTRA_CONFIG = "extra_call_video_config"

        @Deprecated("Use EXTRA_CONFIG + CallVideoConfig instead", ReplaceWith("EXTRA_CONFIG"))
        const val EXTRA_VIDEO_PATH = "extra_video_path"
        @Deprecated("Use EXTRA_CONFIG + CallVideoConfig instead", ReplaceWith("EXTRA_CONFIG"))
        const val EXTRA_VIDEO_DISPLAY_NAME = "extra_video_display_name"
        @Deprecated("Use EXTRA_CONFIG + CallVideoConfig instead", ReplaceWith("EXTRA_CONFIG"))
        const val EXTRA_VIDEO_MIME_TYPE = "extra_video_mime_type"
        @Deprecated("Use EXTRA_CONFIG + CallVideoConfig instead", ReplaceWith("EXTRA_CONFIG"))
        const val EXTRA_IS_PREVIEW_MODE = "extra_is_preview_mode"
        @Deprecated("Use EXTRA_CONFIG + CallVideoConfig instead", ReplaceWith("EXTRA_CONFIG"))
        const val EXTRA_TRIM_START_MS = "extra_trim_start_ms"
        @Deprecated("Use EXTRA_CONFIG + CallVideoConfig instead", ReplaceWith("EXTRA_CONFIG"))
        const val EXTRA_TRIM_END_MS = "extra_trim_end_ms"
        @Deprecated("Use EXTRA_CONFIG + CallVideoConfig instead", ReplaceWith("EXTRA_CONFIG"))
        const val EXTRA_CUSTOM_AUDIO_PATH = "extra_custom_audio_path"
        @Deprecated("Use EXTRA_CONFIG + CallVideoConfig instead", ReplaceWith("EXTRA_CONFIG"))
        const val EXTRA_VIDEO_SCALE = "extra_video_scale"
        @Deprecated("Use EXTRA_CONFIG + CallVideoConfig instead", ReplaceWith("EXTRA_CONFIG"))
        const val EXTRA_NAME_POSITION_Y = "extra_name_position_y"
        @Deprecated("Use EXTRA_CONFIG + CallVideoConfig instead", ReplaceWith("EXTRA_CONFIG"))
        const val EXTRA_CALLER_NAME = "extra_caller_name"
        @Deprecated("Use EXTRA_CONFIG + CallVideoConfig instead", ReplaceWith("EXTRA_CONFIG"))
        const val EXTRA_CALLER_NUMBER = "extra_caller_number"
        @Deprecated("Use EXTRA_CONFIG + CallVideoConfig instead", ReplaceWith("EXTRA_CONFIG"))
        const val EXTRA_ANSWER_STYLE = "extra_answer_style"
        @Deprecated("Use EXTRA_CONFIG + CallVideoConfig instead", ReplaceWith("EXTRA_CONFIG"))
        const val EXTRA_NAME_FONT_SIZE = "extra_name_font_size"
        @Deprecated("Use EXTRA_CONFIG + CallVideoConfig instead", ReplaceWith("EXTRA_CONFIG"))
        const val EXTRA_NAME_FONT_FAMILY = "extra_name_font_family"
        @Deprecated("Use EXTRA_CONFIG + CallVideoConfig instead", ReplaceWith("EXTRA_CONFIG"))
        const val EXTRA_NAME_TEXT_COLOR = "extra_name_text_color"
        @Deprecated("Use EXTRA_CONFIG + CallVideoConfig instead", ReplaceWith("EXTRA_CONFIG"))
        const val EXTRA_NAME_BG_COLOR = "extra_name_bg_color"
        @Deprecated("Use EXTRA_CONFIG + CallVideoConfig instead", ReplaceWith("EXTRA_CONFIG"))
        const val EXTRA_VIDEO_FILTER = "extra_video_filter"
        private const val TAG = "CallVideoActivity"
    }

    private var videoPath: String? = null
    private var videoDisplayName: String? = null
    private var callerName: String? = null
    private var callerNumber: String? = null
    private var videoMimeType: String? = null
    private var trimStartMs: Long = -1L
    private var trimEndMs: Long = -1L
    private var customAudioPath: String? = null
    private var videoScale: Float = 1.0f
    private var namePositionY: Float = 0.1f // Default near top
    private var answerStyle: String = "swipe"
    private var nameFontSize: Float = 24f
    private var nameFontFamily: String = "sans-serif"
    private var nameTextColor: Int = android.graphics.Color.WHITE
    private var nameBgColor: Int = android.graphics.Color.parseColor("#80000000")
    private var videoFilter: String = "normal"
    private var isPreviewMode: Boolean = false
    private var telephonyManager: TelephonyManager? = null
    private var phoneStateListener: android.telephony.PhoneStateListener? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // --- Read configuration from the intent ---------------------------
        // Prefer the single EXTRA_CONFIG Parcelable (B1 refactor). Fall back
        // to the legacy individual extras if a caller hasn't been migrated
        // yet (the EXTRA_* constants are @Deprecated but still functional).
        val config: CallVideoConfig? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(EXTRA_CONFIG, CallVideoConfig::class.java)
        } else {
            @Suppress("DEPRECATION", "DEPRECATION")
            intent.getParcelableExtra<CallVideoConfig>(EXTRA_CONFIG)
        }

        if (config != null) {
            isPreviewMode = config.isPreviewMode
            videoPath = config.videoPath
            videoDisplayName = config.videoDisplayName
            callerName = config.callerName
            callerNumber = config.callerNumber
            videoMimeType = config.videoMimeType
            trimStartMs = config.trimStartMs
            trimEndMs = config.trimEndMs
            customAudioPath = config.customAudioPath
            videoScale = config.videoScale
            namePositionY = config.namePositionY
            answerStyle = config.answerStyle
            nameFontSize = config.nameFontSize
            nameFontFamily = config.nameFontFamily
            nameTextColor = config.nameTextColor
            nameBgColor = config.nameBgColor
            videoFilter = config.videoFilter
        } else {
            // Legacy path: read individual extras. Kept so older callers
            // (e.g. an older build of the app that's still installed, or
            // external triggers) still work. All EXTRA_* constants are
            // @Deprecated — new code should use EXTRA_CONFIG.
            @Suppress("DEPRECATION")
            isPreviewMode = intent.getBooleanExtra(EXTRA_IS_PREVIEW_MODE, false)
            @Suppress("DEPRECATION")
            videoPath = intent.getStringExtra(EXTRA_VIDEO_PATH)
            videoDisplayName = if (isPreviewMode) {
                "Ravi"
            } else {
                @Suppress("DEPRECATION")
                intent.getStringExtra(EXTRA_VIDEO_DISPLAY_NAME) ?: "Incoming Call"
            }
            @Suppress("DEPRECATION")
            callerName = intent.getStringExtra(EXTRA_CALLER_NAME)
            @Suppress("DEPRECATION")
            callerNumber = intent.getStringExtra(EXTRA_CALLER_NUMBER)
            @Suppress("DEPRECATION")
            videoMimeType = intent.getStringExtra(EXTRA_VIDEO_MIME_TYPE) ?: "video/*"
            @Suppress("DEPRECATION")
            trimStartMs = intent.getLongExtra(EXTRA_TRIM_START_MS, -1L)
            @Suppress("DEPRECATION")
            trimEndMs = intent.getLongExtra(EXTRA_TRIM_END_MS, -1L)
            @Suppress("DEPRECATION")
            customAudioPath = intent.getStringExtra(EXTRA_CUSTOM_AUDIO_PATH)
            @Suppress("DEPRECATION")
            videoScale = intent.getFloatExtra(EXTRA_VIDEO_SCALE, 1.0f)
            @Suppress("DEPRECATION")
            namePositionY = intent.getFloatExtra(EXTRA_NAME_POSITION_Y, 0.1f)
            @Suppress("DEPRECATION")
            answerStyle = intent.getStringExtra(EXTRA_ANSWER_STYLE) ?: "swipe"
            @Suppress("DEPRECATION")
            nameFontSize = intent.getFloatExtra(EXTRA_NAME_FONT_SIZE, 24f)
            @Suppress("DEPRECATION")
            nameFontFamily = intent.getStringExtra(EXTRA_NAME_FONT_FAMILY) ?: "sans-serif"
            @Suppress("DEPRECATION")
            nameTextColor = intent.getIntExtra(EXTRA_NAME_TEXT_COLOR, android.graphics.Color.WHITE)
            @Suppress("DEPRECATION")
            nameBgColor = intent.getIntExtra(EXTRA_NAME_BG_COLOR, android.graphics.Color.parseColor("#80000000"))
            @Suppress("DEPRECATION")
            videoFilter = intent.getStringExtra(EXTRA_VIDEO_FILTER) ?: "normal"
        }

        // --- Lockscreen / wake flags --------------------------------------
        // Show over the lockscreen and turn the screen on when the activity
        // starts. These flags work on API 24+ (our minSdk).
        if (!isPreviewMode) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                setShowWhenLocked(true)
                setTurnScreenOn(true)
            } else {
                @Suppress("DEPRECATION")
                window.addFlags(
                    WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
                )
            }
            // Keep the screen on while the call is ringing.
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

            // Dismiss the keyguard so the user can tap Accept/Dismiss without
            // first unlocking the phone.
            try {
                val keyguardMgr = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    keyguardMgr.requestDismissKeyguard(this, null)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Could not request keyguard dismissal", e)
            }
        }

        // --- Hide system UI for a true "wallpaper" feel -------------------
        // We hide the status + navigation bars and force a fullscreen layout.
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_FULLSCREEN or
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        )

        // Lock to portrait to match a typical phone-call UI. (VideoView
        // will letterbox landscape videos inside the portrait frame.)
        @Suppress("DEPRECATION")
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT

        if (videoPath == null || !File(videoPath).exists()) {
            Log.e(TAG, "Video path missing or file does not exist: $videoPath")
            finish()
            return
        }

        // --- PhoneStateListener: auto-finish when call ends --------------
        if (!isPreviewMode) {
            telephonyManager = getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
            phoneStateListener = object : android.telephony.PhoneStateListener() {
                override fun onCallStateChanged(state: Int, phoneNumber: String?) {
                    when (state) {
                        TelephonyManager.CALL_STATE_OFFHOOK -> {
                            // Call was answered (either via our Accept button or
                            // via the system call screen). Dismiss the video so
                            // the system in-call UI can take over.
                            Log.d(TAG, "Call answered (OFFHOOK) — finishing activity.")
                            finish()
                        }
                        TelephonyManager.CALL_STATE_IDLE -> {
                            // Call ended / rejected / caller hung up. Dismiss.
                            Log.d(TAG, "Call ended (IDLE) — finishing activity.")
                            finish()
                        }
                        // CALL_STATE_RINGING: still ringing — do nothing.
                    }
                }
            }
            try {
                @Suppress("DEPRECATION")
                telephonyManager?.listen(phoneStateListener, android.telephony.PhoneStateListener.LISTEN_CALL_STATE)
            } catch (e: SecurityException) {
                Log.w(TAG, "Cannot listen to phone state (READ_PHONE_STATE missing?)", e)
            }
        }

        // --- Render Compose UI --------------------------------------------
        val path = videoPath!!
        
        // Priority: callerName (custom) > contact name (from phone book) > videoDisplayName > "Incoming Call"
        val displayNameForCall = when {
            !callerName.isNullOrBlank() -> callerName!!
            isPreviewMode -> "Ravi" // Preview always shows "Ravi"
            else -> {
                // Try to get contact name from phone number
                val contactName = callerNumber?.let { 
                    ContactUtils.getContactName(contentResolver, it) 
                }
                contactName ?: videoDisplayName ?: "Incoming Call"
            }
        }
        
        setContent {
            CallVideoScreen(
                videoPath = path,
                videoDisplayName = displayNameForCall,
                callerNumber = if (isPreviewMode) null else callerNumber,
                trimStartMs = trimStartMs,
                trimEndMs = trimEndMs,
                customAudioPath = customAudioPath,
                videoScale = videoScale,
                namePositionY = namePositionY,
                answerStyle = answerStyle,
                nameFontSize = nameFontSize,
                nameFontFamily = nameFontFamily,
                nameTextColor = nameTextColor,
                nameBgColor = nameBgColor,
                videoFilter = videoFilter,
                onAccept = {
                    if (isPreviewMode) {
                        finish()
                    } else {
                        val ok = CallVideoController.answerCall(this)
                        if (!ok) {
                            // Fallback: open the system call screen so the user
                            // can answer there.
                            CallVideoController.openSystemCallScreen(this)
                        }
                        finish()
                    }
                },
                onDismiss = {
                    if (isPreviewMode) {
                        finish()
                    } else {
                        val ok = CallVideoController.rejectCall(this)
                        if (!ok) {
                            CallVideoController.openSystemCallScreen(this)
                        }
                        finish()
                    }
                }
            )
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Stop listening to phone state to avoid leaking the listener.
        try {
            @Suppress("DEPRECATION")
            telephonyManager?.listen(phoneStateListener, android.telephony.PhoneStateListener.LISTEN_NONE)
        } catch (_: Exception) {}
    }

    // Block back button — the user must use Accept/Dismiss to leave this
    // screen. Pressing back during an incoming call would drop them into
    // a confusing half-video / half-call-screen state.
    @Suppress("DEPRECATION")
    @android.annotation.SuppressLint("MissingSuperCall", "GestureBackNavigation")
    override fun onBackPressed() {
        // Intentionally do nothing.
    }
}

/**
 * Compose UI for CallVideoActivity.
 *
 * Renders a full-bleed VideoView (looping, with audio) with two large round
 * buttons overlaid near the bottom of the screen.
 */
@Composable
private fun CallVideoScreen(
    videoPath: String,
    videoDisplayName: String,
    callerNumber: String?,
    trimStartMs: Long,
    trimEndMs: Long,
    customAudioPath: String?,
    videoScale: Float,
    namePositionY: Float,
    answerStyle: String,
    nameFontSize: Float,
    nameFontFamily: String,
    nameTextColor: Int,
    nameBgColor: Int,
    videoFilter: String,
    onAccept: () -> Unit,
    onDismiss: () -> Unit
) {
    var videoViewRef by remember { mutableStateOf<VideoView?>(null) }
    
    val callerFontFamily = remember(nameFontFamily) {
        when (nameFontFamily.lowercase()) {
            "serif" -> androidx.compose.ui.text.font.FontFamily.Serif
            "monospace" -> androidx.compose.ui.text.font.FontFamily.Monospace
            "cursive" -> androidx.compose.ui.text.font.FontFamily.Cursive
            else -> androidx.compose.ui.text.font.FontFamily.SansSerif
        }
    }
    
    // Manage custom audio playback
    DisposableEffect(customAudioPath) {
        var audioPlayer: android.media.MediaPlayer? = null
        if (customAudioPath != null && File(customAudioPath).exists()) {
            try {
                audioPlayer = android.media.MediaPlayer().apply {
                    setDataSource(customAudioPath)
                    isLooping = true
                    prepareAsync()
                    setOnPreparedListener { start() }
                }
            } catch (e: Exception) {
                Log.e("CallVideoScreen", "Error playing custom audio", e)
            }
        }
        onDispose {
            audioPlayer?.release()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // --- Video view (fills the whole screen) -------------------------
        AndroidView(
            factory = { ctx ->
                VideoView(ctx).apply {
                    // Set 32-bit high-quality color format to prevent color banding
                    holder.setFormat(android.graphics.PixelFormat.RGBA_8888)
                    setVideoURI(android.net.Uri.fromFile(File(videoPath)))
                    setOnPreparedListener { mp ->
                        mp.isLooping = true
                        // Set high-fidelity scaling mode to scale and crop perfectly instead of stretching
                        try {
                            mp.setVideoScalingMode(android.media.MediaPlayer.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING)
                        } catch (e: Exception) {
                            Log.e("CallVideoScreen", "Error setting video scaling mode", e)
                        }
                        if (trimStartMs > 0) {
                            mp.seekTo(trimStartMs.toInt())
                        }
                        
                        // Mute video if custom audio is provided
                        if (customAudioPath != null) {
                            mp.setVolume(0f, 0f)
                        } else {
                            mp.setVolume(1.0f, 1.0f)
                        }
                        start()
                    }
                    setOnErrorListener { _, _, _ ->
                        Log.e("CallVideoScreen", "VideoView playback error for $videoPath")
                        false
                    }
                    videoViewRef = this
                }
            },
            update = { view ->
                // Poll for trimEndMs
                if (trimEndMs > 0 && trimEndMs > trimStartMs) {
                    view.postDelayed(object : Runnable {
                        override fun run() {
                            try {
                                if (view.isPlaying && view.currentPosition >= trimEndMs) {
                                    view.seekTo(trimStartMs.coerceAtLeast(0).toInt())
                                }
                                view.postDelayed(this, 100)
                            } catch (_: Exception) {}
                        }
                    }, 100)
                }
            },
            modifier = Modifier.fillMaxSize().scale(videoScale)
        )

        // Overlay video quality enhancement filter
        VideoFilterOverlay(filter = videoFilter)

        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val screenHeight = maxHeight
            // --- Display-name chip at the top --------------------------------
            Box(
                modifier = Modifier
                    .padding(top = screenHeight * namePositionY)
                    .align(Alignment.TopCenter)
                    .background(
                        color = Color(nameBgColor),
                        shape = RoundedCornerShape(16.dp)
                    )
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(
                    text = videoDisplayName,
                    color = Color(nameTextColor),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = nameFontSize.sp,
                    fontFamily = callerFontFamily
                )
            }
        }

        // --- Caller label above the buttons ------------------------------
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(bottom = 56.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Incoming Call",
                color = Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(16.dp))
            
            if (answerStyle == "buttons") {
                Text(
                    text = "Tap Accept to answer · Dismiss to reject",
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 13.sp
                )
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RoundCallButton(
                        icon = Icons.Default.CallEnd,
                        contentDescription = "Dismiss call",
                        backgroundColor = Color(0xFFE53935),
                        onClick = onDismiss
                    )
                    RoundCallButton(
                        icon = Icons.Default.Call,
                        contentDescription = "Accept call",
                        backgroundColor = Color(0xFF4CAF50),
                        onClick = onAccept
                    )
                }
            } else {
                Text(
                    text = "Swipe to answer",
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 13.sp
                )
                Spacer(modifier = Modifier.height(16.dp))
                SwipeToAnswerButton(onAccept = onAccept, onDismiss = onDismiss)
            }
        }
    }

    // Stop the video when the composable leaves the composition.
    DisposableEffect(videoPath) {
        onDispose {
            try {
                videoViewRef?.stopPlayback()
            } catch (_: Exception) {}
        }
    }
}

@Composable
private fun VideoFilterOverlay(filter: String?, modifier: Modifier = Modifier) {
    if (filter == null || filter == "normal") return
    
    val brush = when (filter.lowercase()) {
        "vivid" -> {
            androidx.compose.ui.graphics.Brush.verticalGradient(
                colors = listOf(
                    Color(0xFFFF5722).copy(alpha = 0.08f),
                    Color(0xFFE040FB).copy(alpha = 0.05f),
                    Color(0xFF00E5FF).copy(alpha = 0.08f)
                )
            )
        }
        "warm" -> {
            androidx.compose.ui.graphics.Brush.verticalGradient(
                colors = listOf(
                    Color(0xFFFFB74D).copy(alpha = 0.16f),
                    Color(0xFFFF9800).copy(alpha = 0.10f),
                    Color(0xFFE65100).copy(alpha = 0.06f)
                )
            )
        }
        "cyberpunk" -> {
            androidx.compose.ui.graphics.Brush.verticalGradient(
                colors = listOf(
                    Color(0xFF00F0FF).copy(alpha = 0.14f),
                    Color(0xFFFF007F).copy(alpha = 0.16f),
                    Color(0xFF7000FF).copy(alpha = 0.12f)
                )
            )
        }
        "hdr" -> {
            androidx.compose.ui.graphics.Brush.verticalGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.06f),
                    Color.Transparent,
                    Color.Black.copy(alpha = 0.22f)
                )
            )
        }
        "noir" -> {
            androidx.compose.ui.graphics.Brush.verticalGradient(
                colors = listOf(
                    Color(0xFF3E2723).copy(alpha = 0.18f),
                    Color(0xFF212121).copy(alpha = 0.22f),
                    Color(0xFF0D0D0D).copy(alpha = 0.28f)
                )
            )
        }
        else -> null
    }

    if (brush != null) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(brush)
        )
    }
}

