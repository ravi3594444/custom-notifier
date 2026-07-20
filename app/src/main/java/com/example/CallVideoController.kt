package com.example

import android.Manifest
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.telecom.TelecomManager
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat
import java.lang.reflect.Method

/**
 * Helpers for programmatically answering / rejecting an incoming call.
 *
 * This is the gnarliest part of the Call Video Wallpaper feature because
 * Android has steadily tightened call-control permissions over the years:
 *
 *   - API < 26: Use the hidden ITelephony.aidl via reflection
 *     (`answerRingingCall()` / `endCall()`). Requires READ_PHONE_STATE.
 *
 *   - API 26+: Use the public TelecomManager.acceptRingingCall(), which
 *     requires the ANSWER_PHONE_CALLS permission. Still works fine.
 *
 *   - API 28+: ITelephony.endCall() is officially deprecated and silently
 *     does nothing on some OEMs (Pixel, AOSP). The closest public API is
 *     TelecomManager.endCall() — but that is restricted to system apps and
 *     the default dialer. The realistic options for a third-party app are:
 *       a) Become the default dialer via RoleManager and use InCallService
 *          (huge scope; the user would lose their stock dialer UI).
 *       b) Try ITelephony.endCall() reflection anyway — works on many
 *          devices, fails silently on others.
 *       c) Use CallScreeningService — can screen (silence / reject) calls
 *          but only at the moment they arrive, not later from a button.
 *
 * We go with (b) for reject — try the reflection, fall back to opening the
 * system call screen if it fails. For answer we use the official public
 * TelecomManager.acceptRingingCall() on API 26+, with ITelephony reflection
 * as a fallback on older devices.
 *
 * Note: The reflection code below is wrapped in try/catch at every level
 * because on some OEM ROMs the ITelephony class is hidden from app
 * reflection (Android P+ hidden-api restrictions). If reflection fails we
 * silently fall through to opening the system call screen.
 */
object CallVideoController {

    /**
     * Answers the currently-ringing call. Returns true if the API call was
     * made (does NOT guarantee the call was actually answered — that's up
     * to the telephony stack).
     */
    fun answerCall(context: Context): Boolean {
        // Preferred path on API 26+: public TelecomManager API.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val granted = ContextCompat.checkSelfPermission(
                    context, Manifest.permission.ANSWER_PHONE_CALLS
                ) == PackageManager.PERMISSION_GRANTED
                if (granted) {
                    val telecom = context.getSystemService(Context.TELECOM_SERVICE) as TelecomManager
                    telecom.acceptRingingCall()
                    return true
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // Fallback: ITelephony.answerRingingCall() via reflection.
        return try {
            val telephony = getITelephony(context) ?: return false
            val method: Method? = telephony.javaClass.getMethod("answerRingingCall")
            method?.invoke(telephony)
            method != null
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * Rejects the currently-ringing call. Returns true if we managed to
     * invoke the endCall API (does NOT guarantee the call was actually
     * rejected on all OEMs).
     */
    fun rejectCall(context: Context): Boolean {
        // First, try to silence the ringer so the audio cuts immediately
        // even if endCall takes a moment to take effect.
        try {
            val telephony = getITelephony(context)
            if (telephony != null) {
                try {
                    val silence: Method? = telephony.javaClass.getMethod("silenceRinger")
                    silence?.invoke(telephony)
                } catch (_: Exception) {}
                val endCall: Method? = telephony.javaClass.getMethod("endCall")
                endCall?.invoke(telephony)
                return endCall != null
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Last-ditch fallback on API 28+: TelecomManager.endCall() —
        // restricted to system / default-dialer apps, but worth trying.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                val telecom = context.getSystemService(Context.TELECOM_SERVICE) as TelecomManager
                telecom.endCall()
                return true
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        return false
    }

    /**
     * Opens the system's default phone app to the in-call screen. Used as a
     * fallback when our programmatic answer/reject fails (e.g. on Android
     * 10+ where ITelephony.endCall() is restricted).
     */
    fun openSystemCallScreen(context: Context) {
        try {
            val intent = Intent(Intent.ACTION_CALL_BUTTON).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Reflectively obtains the hidden ITelephony interface from
     * TelephonyManager. Returns null on any failure.
     */
    private fun getITelephony(context: Context): Any? {
        return try {
            val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
            val getITelephony = telephonyManager.javaClass.getDeclaredMethod("getITelephony")
            getITelephony.isAccessible = true
            getITelephony.invoke(telephonyManager)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
