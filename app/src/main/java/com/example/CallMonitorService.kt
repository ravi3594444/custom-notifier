package com.example

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.IBinder
import android.telephony.TelephonyManager
import android.util.Log
import androidx.core.app.NotificationCompat
import java.io.File

/**
 * Foreground Service that monitors incoming phone calls and launches
 * CallVideoActivity when a call arrives.
 *
 * WHY A FOREGROUND SERVICE?
 * Starting from Android 8.0 (API 26), the PHONE_STATE broadcast is no longer
 * delivered to manifest-registered BroadcastReceivers for non-dialer apps.
 * This is an Android security restriction. The only reliable way to detect
 * incoming calls without being the default dialer is to:
 * 1. Register a BroadcastReceiver dynamically (at runtime)
 * 2. Keep it alive using a Foreground Service with a persistent notification
 *
 * The Foreground Service ensures Android doesn't kill our receiver when
 * the app is in the background.
 *
 * Permissions required:
 * - READ_PHONE_STATE (runtime permission)
 * - FOREGROUND_SERVICE permission
 * - POST_NOTIFICATIONS (Android 13+)
 */
class CallMonitorService : Service() {

    companion object {
        private const val TAG = "CallMonitorService"
        private const val CHANNEL_ID = "call_monitor_channel"
        private const val NOTIFICATION_ID = 1001

        /** Action to start this service */
        const val ACTION_START = "com.example.action.START_CALL_MONITOR"
        /** Action to stop this service */
        const val ACTION_STOP = "com.example.action.STOP_CALL_MONITOR"

        /**
         * Convenience method to start the service from any context.
         */
        fun start(context: Context) {
            val intent = Intent(context, CallMonitorService::class.java).apply {
                action = ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        /**
         * Convenience method to stop the service.
         */
        fun stop(context: Context) {
            val intent = Intent(context, CallMonitorService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }

    private var phoneStateReceiver: BroadcastReceiver? = null
    private var lastState: String? = null

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "CallMonitorService created")
        createNotificationChannel()
        registerPhoneStateReceiver()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "CallMonitorService started with action: ${intent?.action}")

        when (intent?.action) {
            ACTION_STOP -> {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            else -> {
                // Start as foreground service with persistent notification
                val notification = createNotification()
                startForeground(NOTIFICATION_ID, notification)
            }
        }

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        unregisterPhoneStateReceiver()
        Log.d(TAG, "CallMonitorService destroyed")
    }

    /**
     * Creates the notification channel for Android O+ devices.
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Call Monitor",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps call detection active in the background"
                setShowBadge(false)
            }

            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    /**
     * Creates the persistent foreground notification.
     */
    private fun createNotification(): Notification {
        val stopIntent = Intent(this, CallMonitorService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            0,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Custom Notifier Active")
            .setContentText("Monitoring for incoming calls...")
            .setSmallIcon(android.R.drawable.ic_menu_call)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "Stop Monitoring",
                stopPendingIntent
            )
            .build()
    }

    /**
     * Registers the PHONE_STATE BroadcastReceiver dynamically.
     * This works on Android 8.0+ because we're registering at runtime
     * rather than via the manifest.
     */
    private fun registerPhoneStateReceiver() {
        phoneStateReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action != TelephonyManager.ACTION_PHONE_STATE_CHANGED) {
                    return
                }

                val state = intent.getStringExtra(TelephonyManager.EXTRA_STATE) ?: return
                Log.d(TAG, "Phone state changed: $state (last: $lastState)")

                // Get the incoming phone number (available in RINGING state)
                val phoneNumber = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER)
                Log.d(TAG, "Incoming phone number: $phoneNumber")

                // Only trigger on RINGING state, and only if we weren't already ringing
                if (state == TelephonyManager.EXTRA_STATE_RINGING && lastState != TelephonyManager.EXTRA_STATE_RINGING) {
                    Log.d(TAG, "Incoming call detected from $phoneNumber! Launching CallVideoActivity...")
                    launchCallVideoActivity(context!!, phoneNumber)
                }

                lastState = state
            }
        }

        val filter = IntentFilter(TelephonyManager.ACTION_PHONE_STATE_CHANGED)

        // Register with RECEIVER_NOT_EXPORTED for Android 13+
        // This ensures the broadcast is delivered to us
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(phoneStateReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            registerReceiver(phoneStateReceiver, filter)
        } else {
            registerReceiver(phoneStateReceiver, filter)
        }

        Log.d(TAG, "Phone state receiver registered")
    }

    /**
     * Unregisters the BroadcastReceiver when the service is destroyed.
     */
    private fun unregisterPhoneStateReceiver() {
        try {
            phoneStateReceiver?.let {
                unregisterReceiver(it)
                Log.d(TAG, "Phone state receiver unregistered")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error unregistering receiver", e)
        }
        phoneStateReceiver = null
    }

    /**
     * Launches CallVideoActivity when an incoming call is detected.
     */
    private fun launchCallVideoActivity(context: Context, phoneNumber: String?) {
        val activeVideo = VideoLibraryManager.getActiveVideoAnyUser(context) ?: run {
            Log.d(TAG, "No active call video set — not launching")
            return
        }

        // Check if video file exists
        val file = File(activeVideo.localFilePath)
        if (!file.exists()) {
            Log.w(TAG, "Active video file missing: ${activeVideo.localFilePath}")
            VideoLibraryManager.setActiveVideoId(context, null, "")
            return
        }

        // Build the config with the caller's phone number
        val config = activeVideo.toCallVideoConfig().copy(
            callerNumber = phoneNumber
        )

        try {
            val launchIntent = Intent(context, CallVideoActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra(CallVideoActivity.EXTRA_CONFIG, config)
            }
            context.startActivity(launchIntent)
            Log.d(TAG, "Successfully launched CallVideoActivity for: ${activeVideo.displayName}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch CallVideoActivity", e)
        }
    }
}
