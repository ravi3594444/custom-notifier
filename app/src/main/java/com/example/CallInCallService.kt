package com.example

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.telecom.Call
import android.telecom.InCallService
import android.util.Log
import java.io.File

/**
 * InCallService that intercepts incoming calls and launches CallVideoActivity.
 * 
 * This is the proper Android way to handle calls - much better than trying to
 * intercept PHONE_STATE broadcasts with a foreground service.
 * 
 * How it works:
 * 1. User sets this app as "Default Caller ID & Spam App" in Android settings
 * 2. When a call comes in, Android calls our onCallAdded() method
 * 3. We launch CallVideoActivity with the caller's number
 * 4. When user accepts/rejects, we call answer() or disconnect() on the Call object
 * 
 * Requirements:
 * - User must set app as "Default Caller ID & Spam App" in Android Settings
 * - READ_CONTACTS permission to show contact names
 */
class CallInCallService : InCallService() {

    companion object {
        private const val TAG = "CallInCallService"
        
        // Store the current active call so we can answer/reject from the activity
        var currentCall: Call? = null
            private set
        
        // Store the current caller's number
        var currentCallerNumber: String? = null
            private set
        
        // Store the current caller's name (if available from contacts)
        var currentCallerName: String? = null
            private set

        /**
         * Answer the current incoming call.
         */
        fun answerCall() {
            try {
                currentCall?.answer()
                Log.d(TAG, "Call answered")
            } catch (e: Exception) {
                Log.e(TAG, "Error answering call", e)
            }
        }

        /**
         * Reject/disconnect the current call.
         */
        fun rejectCall() {
            try {
                currentCall?.reject(/* suppressSound = */ false, /* reason = */ null)
                Log.d(TAG, "Call rejected")
            } catch (e: Exception) {
                Log.e(TAG, "Error rejecting call", e)
            }
        }
    }

    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)
        Log.d(TAG, "Call added: ${call.handle}")

        // Store the call for answer/reject actions
        currentCall = call
        currentCallerNumber = extractPhoneNumber(call.handle?.toString())
        
        // Try to get caller name from call details
        currentCallerName = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            call.callerDisplayName
        } else {
            null
        }

        // Register for call state changes
        call.registerCallback(object : Call.Callback() {
            override fun onCallStateChanged(call: Call, state: Int) {
                Log.d(TAG, "Call state changed: $state")
                when (state) {
                    Call.STATE_ACTIVE -> {
                        // Call is active (answered)
                        Log.d(TAG, "Call is active")
                        // Finish the video activity since call screen will show
                        finishVideoActivity()
                    }
                    Call.STATE_DISCONNECTED -> {
                        // Call ended
                        Log.d(TAG, "Call disconnected")
                        currentCall = null
                        currentCallerNumber = null
                        currentCallerName = null
                        finishVideoActivity()
                    }
                    Call.STATE_RINGING -> {
                        Log.d(TAG, "Call is ringing")
                    }
                }
            }
        })

        // If it's a ringing call, launch the video activity
        if (call.state == Call.STATE_RINGING) {
            Log.d(TAG, "Incoming call detected, launching video activity")
            launchVideoActivity(call)
        }
    }

    override fun onCallRemoved(call: Call) {
        super.onCallRemoved(call)
        Log.d(TAG, "Call removed")
        currentCall = null
        currentCallerNumber = null
        currentCallerName = null
    }

    override fun onDestroy() {
        super.onDestroy()
        currentCall = null
        currentCallerNumber = null
        currentCallerName = null
    }

    /**
     * Launches the video activity for the incoming call.
     */
    private fun launchVideoActivity(call: Call) {
        val context = this

        // Get the active video from VideoLibraryManager
        val activeVideo = VideoLibraryManager.getActiveVideoAnyUser(context)
        
        if (activeVideo == null) {
            Log.d(TAG, "No active video set, not launching video activity")
            return
        }

        // Check if video file exists
        val file = File(activeVideo.localFilePath)
        if (!file.exists()) {
            Log.w(TAG, "Active video file missing")
            VideoLibraryManager.setActiveVideoId(context, null, "")
            return
        }

        // Build the config with the caller's phone number
        val config = activeVideo.toCallVideoConfig().copy(
            callerNumber = currentCallerNumber,
            // If we have the caller name from the call, use it (higher priority than saved custom name)
            callerName = currentCallerName ?: activeVideo.callerName
        )

        try {
            val launchIntent = Intent(context, CallVideoActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP or
                        Intent.FLAG_ACTIVITY_NO_USER_ACTION
                putExtra(CallVideoActivity.EXTRA_CONFIG, config)
                // Mark this as launched from InCallService
                putExtra(CallVideoActivity.EXTRA_FROM_INCALL_SERVICE, true)
            }
            context.startActivity(launchIntent)
            Log.d(TAG, "Video activity launched for call from: $currentCallerNumber")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch video activity", e)
        }
    }

    /**
     * Extracts the phone number from a URI string (e.g., "tel:+91-98765-43210").
     */
    private fun extractPhoneNumber(handle: String?): String? {
        if (handle == null) return null
        return try {
            val uri = Uri.parse(handle)
            if (uri.scheme == "tel") {
                uri.path ?: uri.schemeSpecificPart
            } else {
                handle
            }
        } catch (e: Exception) {
            handle
        }
    }

    /**
     * Finishes the video activity if it's running.
     */
    private fun finishVideoActivity() {
        try {
            val intent = Intent(this, CallVideoActivity::class.java).apply {
                action = CallVideoActivity.ACTION_FINISH
            }
            startActivity(intent)
        } catch (e: Exception) {
            // Activity might not be running, that's fine
        }
    }
}
