package com.example

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log

/**
 * This is a "trampoline" activity that acts as the default dialer.
 * 
 * When Android needs to dial a number (user taps phone number in browser, etc.),
 * this activity opens briefly and immediately forwards the user to the 
 * ORIGINAL system dialer (Samsung, Google, etc.)
 * 
 * This allows users to keep using their normal dialer while we handle incoming calls.
 */
class DialerActivity : Activity() {
    
    companion object {
        private const val TAG = "DialerActivity"
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        Log.d(TAG, "DialerActivity opened with intent: $intent")
        
        // Get the phone number from the intent
        val phoneNumber = intent.data
        
        // Forward to the original system dialer
        forwardToSystemDialer(phoneNumber)
    }
    
    /**
     * Finds the original system dialer and forwards the call to it.
     */
    private fun forwardToSystemDialer(phoneNumber: Uri?) {
        val systemDialerPackage = getSystemDialerPackage()
        
        if (systemDialerPackage != null) {
            Log.d(TAG, "Forwarding to system dialer: $systemDialerPackage")
            
            // Create intent to open the original system dialer
            val forwardIntent = Intent(Intent.ACTION_DIAL).apply {
                data = phoneNumber ?: Uri.parse("tel:")
                setPackage(systemDialerPackage)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            
            try {
                startActivity(forwardIntent)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to open system dialer", e)
                // Fallback: just open our app normally
                openFallback()
                return
            }
        } else {
            Log.w(TAG, "Could not find system dialer, opening fallback")
            openFallback()
            return
        }
        
        // Close this activity immediately - user never sees it
        finish()
    }
    
    /**
     * Opens a basic dialer fallback if we can't find the system dialer.
     */
    private fun openFallback() {
        try {
            val fallbackIntent = Intent(Intent.ACTION_DIAL).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            startActivity(fallbackIntent)
        } catch (e: Exception) {
            Log.e(TAG, "No dialer available at all", e)
        }
        finish()
    }
    
    /**
     * Finds the system default dialer package.
     */
    private fun getSystemDialerPackage(): String? {
        // Try to find the current default dialer
        val telecomManager = getSystemService(android.telecom.TelecomManager::class.java)
        val defaultDialer = telecomManager?.defaultDialerPackage
        
        // Return the default dialer if it's not us
        if (defaultDialer != null && defaultDialer != packageName) {
            return defaultDialer
        }
        
        // If we're the default, try to find another dialer
        val dialIntent = Intent(Intent.ACTION_DIAL).apply {
            data = Uri.parse("tel:123")
        }
        
        val resolveInfoList = packageManager.queryIntentActivities(dialIntent, 0)
        
        for (info in resolveInfoList) {
            val pkg = info.activityInfo.packageName
            // Return the first app that isn't us
            if (pkg != packageName) {
                Log.d(TAG, "Found system dialer: $pkg")
                return pkg
            }
        }
        
        return null
    }
}
