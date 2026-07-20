package com.example

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.TelephonyManager
import android.util.Log

/**
 * Listens for PHONE_STATE broadcasts and launches [CallVideoActivity] when
 * the phone starts ringing, IF the user has previously set an active call
 * video on the Call Video Wallpaper screen.
 *
 * Broadcast flow:
 *   1. System fires ACTION_PHONE_STATE_CHANGED with EXTRA_STATE=RINGING.
 *   2. We check VideoLibraryManager.getActiveVideoAnyUser() — if there's
 *      no active video, we let the system call screen handle the call
 *      normally (we never want to disable phone-ringing functionality).
 *   3. If there IS an active video, we launch CallVideoActivity with
 *      FLAG_ACTIVITY_NEW_TASK (required because we're launching from a
 *      BroadcastReceiver context that has no Activity task).
 *   4. The activity itself monitors the phone state and dismisses itself
 *      when the state transitions to OFFHOOK (call answered) or IDLE
 *      (caller hung up).
 *
 * NOTE: We deliberately do NOT launch on OFFHOOK or IDLE — the receiver
 * only triggers on RINGING. The activity handles its own teardown via its
 * own PhoneStateListener so it can react quickly when the call ends.
 *
 * Permission notes:
 *   - READ_PHONE_STATE is required to receive the broadcast at all.
 *   - On API 28+ READ_CALL_LOG is additionally required if you want to
 *     read the incoming phone number from the broadcast. We don't need
 *     the number — just the state — so READ_CALL_LOG is requested in the
 *     manifest as defensive future-proofing but isn't strictly required
 *     for our use case.
 */
class CallVideoReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "CallVideoReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != TelephonyManager.ACTION_PHONE_STATE_CHANGED) return

        val state = intent.getStringExtra(TelephonyManager.EXTRA_STATE) ?: return
        Log.d(TAG, "Phone state changed: $state")

        if (state != TelephonyManager.EXTRA_STATE_RINGING) return

        val activeVideo = VideoLibraryManager.getActiveVideoAnyUser(context) ?: run {
            Log.d(TAG, "No active call video set — letting system handle the call.")
            return
        }

        // Don't launch the activity if the video file is missing for some
        // reason (e.g. user cleared app data). Defensive — the activity
        // would just show a black screen with no way out.
        val file = java.io.File(activeVideo.localFilePath)
        if (!file.exists()) {
            Log.w(TAG, "Active video file missing: ${activeVideo.localFilePath}. Clearing active id.")
            VideoLibraryManager.setActiveVideoId(context, null, "")
            return
        }

        try {
            val launchIntent = Intent(context, CallVideoActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra(CallVideoActivity.EXTRA_VIDEO_PATH, activeVideo.localFilePath)
                putExtra(CallVideoActivity.EXTRA_VIDEO_DISPLAY_NAME, activeVideo.displayName)
                putExtra(CallVideoActivity.EXTRA_VIDEO_MIME_TYPE, activeVideo.mimeType)
                if (activeVideo.trimStartMs != null) putExtra(CallVideoActivity.EXTRA_TRIM_START_MS, activeVideo.trimStartMs)
                if (activeVideo.trimEndMs != null) putExtra(CallVideoActivity.EXTRA_TRIM_END_MS, activeVideo.trimEndMs)
                if (activeVideo.customAudioPath != null) putExtra(CallVideoActivity.EXTRA_CUSTOM_AUDIO_PATH, activeVideo.customAudioPath)
                if (activeVideo.videoScale != null) putExtra(CallVideoActivity.EXTRA_VIDEO_SCALE, activeVideo.videoScale)
                if (activeVideo.namePositionY != null) putExtra(CallVideoActivity.EXTRA_NAME_POSITION_Y, activeVideo.namePositionY)
            }
            context.startActivity(launchIntent)
            Log.d(TAG, "Launched CallVideoActivity for video: ${activeVideo.displayName}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch CallVideoActivity", e)
        }
    }
}
