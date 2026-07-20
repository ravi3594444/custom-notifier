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
import androidx.compose.ui.viewinterop.AndroidView
import java.io.File

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
 *   - Plays the video on loop with its own audio (per the user's design
 *     choice — the system ringtone is NOT played in parallel).
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
        const val EXTRA_VIDEO_PATH = "extra_video_path"
        const val EXTRA_VIDEO_DISPLAY_NAME = "extra_video_display_name"
        const val EXTRA_VIDEO_MIME_TYPE = "extra_video_mime_type"
        const val EXTRA_IS_PREVIEW_MODE = "extra_is_preview_mode"
        private const val TAG = "CallVideoActivity"
    }

    private var videoPath: String? = null
    private var videoDisplayName: String? = null
    private var videoMimeType: String? = null
    private var isPreviewMode: Boolean = false
    private var telephonyManager: TelephonyManager? = null
    private var phoneStateListener: android.telephony.PhoneStateListener? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        isPreviewMode = intent.getBooleanExtra(EXTRA_IS_PREVIEW_MODE, false)

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

        // --- Read extras --------------------------------------------------
        videoPath = intent.getStringExtra(EXTRA_VIDEO_PATH)
        videoDisplayName = intent.getStringExtra(EXTRA_VIDEO_DISPLAY_NAME) ?: "Incoming Call"
        videoMimeType = intent.getStringExtra(EXTRA_VIDEO_MIME_TYPE) ?: "video/*"

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
        val name = videoDisplayName ?: "Incoming Call"
        setContent {
            CallVideoScreen(
                videoPath = path,
                videoDisplayName = name,
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
    onAccept: () -> Unit,
    onDismiss: () -> Unit
) {
    var videoViewRef by remember { mutableStateOf<VideoView?>(null) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // --- Video view (fills the whole screen) -------------------------
        AndroidView(
            factory = { ctx ->
                VideoView(ctx).apply {
                    setVideoURI(android.net.Uri.fromFile(File(videoPath)))
                    setOnPreparedListener { mp ->
                        mp.isLooping = true
                        // Audio plays at default volume (per design: video's
                        // own audio replaces the ringtone).
                        mp.setVolume(1.0f, 1.0f)
                        start()
                    }
                    setOnErrorListener { _, _, _ ->
                        // If the video can't be played, log and let the call
                        // screen handle the rest. We don't show a toast from
                        // here because there's no UI to show it on top of.
                        Log.e("CallVideoScreen", "VideoView playback error for $videoPath")
                        false
                    }
                    videoViewRef = this
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // --- Display-name chip at the top --------------------------------
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 48.dp)
                .background(
                    color = Color.Black.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(16.dp)
                )
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Text(
                text = videoDisplayName,
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp
            )
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
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Tap Accept to answer · Dismiss to reject",
                color = Color.White.copy(alpha = 0.8f),
                fontSize = 13.sp
            )
        }

        // --- Accept / Dismiss buttons ------------------------------------
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(bottom = 96.dp, start = 32.dp, end = 32.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Dismiss (red, left)
            RoundCallButton(
                icon = Icons.Default.CallEnd,
                contentDescription = "Dismiss call",
                backgroundColor = Color(0xFFE53935),
                onClick = onDismiss
            )

            // Spacer in the middle to push them apart
            Spacer(modifier = Modifier.width(120.dp))

            // Accept (green, right)
            RoundCallButton(
                icon = Icons.Default.Call,
                contentDescription = "Accept call",
                backgroundColor = Color(0xFF4CAF50),
                onClick = onAccept
            )
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
private fun RoundCallButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    backgroundColor: Color,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .size(72.dp)
            .background(backgroundColor, CircleShape)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = Color.White,
            modifier = Modifier.size(36.dp)
        )
    }
}
