package com.example

import android.content.Intent
import android.net.Uri
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
 * 1. User sets this app as "Default Phone App" in Android Settings
 * 2. When a call comes in, Android calls our onCallAdded() method
 * 3. We launch CallVideoActivity with the caller's number
 * 4. When user accepts/rejects, we call answer() or disconnect() via CallManager
 * 
 * Requirements:
 * - User must set app as "Default Phone App" in Android Settings
 * - READ_CONTACTS permission to show contact names
 */
class CallInCallService : InCallService() {

    companion object {
        private const val TAG = "CallInCallService"
    }

    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)
        Log.d(TAG, "Call added: ${call.details.handle}")

        // Store the call in CallManager for button access
        CallManager.currentCall = call
        
        // Register for call state changes
        call.registerCallback(object : Call.Callback() {
            override fun onCallStateChanged(call: Call, state: Int) {
                Log.d(TAG, "Call state changed: $state")
                when (state) {
                    Call.STATE_ACTIVE -> {
                        // Call is active (answered)
                        Log.d(TAG, "Call is active")
                    }
                    Call.STATE_DISCONNECTED -> {
                        // Call ended
                        Log.d(TAG, "Call disconnected")
                        CallManager.currentCall = null
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
        if (CallManager.currentCall == call) {
            CallManager.currentCall = null
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        CallManager.currentCall = null
    }

    /**
     * Launches the video activity for the incoming call.
     */
    private fun launchVideoActivity(call: Call) {
        val context = this

        // Phone number and caller display name are exposed through Call.Details,
        // not directly on Call.
        val callDetails = call.details
        val phoneNumber = extractPhoneNumber(callDetails.handle?.toString())
        val callerName = callDetails.callerDisplayName

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
            callerNumber = phoneNumber,
            callerName = callerName ?: activeVideo.callerName
        )

        try {
            val launchIntent = Intent(context, CallVideoActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP or
                        Intent.FLAG_ACTIVITY_NO_USER_ACTION
                putExtra(CallVideoActivity.EXTRA_CONFIG, config)
                putExtra(CallVideoActivity.EXTRA_FROM_INCALL_SERVICE, true)
            }
            context.startActivity(launchIntent)
            Log.d(TAG, "Video activity launched for call from: $phoneNumber")
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
}
