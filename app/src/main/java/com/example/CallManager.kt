package com.example

import android.telecom.Call
import android.telecom.VideoProfile

/**
 * Singleton object to manage the current active call.
 * This allows the video activity buttons to access the call directly.
 */
object CallManager {
    var currentCall: Call? = null
    
    /**
     * Accept/Answer the current incoming call.
     */
    fun accept() {
        try {
            currentCall?.answer(VideoProfile.STATE_AUDIO_ONLY)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    /**
     * Accept/Answer with video enabled.
     */
    fun acceptWithVideo() {
        try {
            currentCall?.answer(VideoProfile.STATE_BIDIRECTIONAL)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    /**
     * Reject/Decline the current call.
     */
    fun decline() {
        try {
            currentCall?.reject(false, null)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    /**
     * Disconnect the current call if active.
     */
    fun disconnect() {
        try {
            currentCall?.disconnect()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
