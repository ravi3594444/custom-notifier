package com.example

import android.content.Context
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.tasks.Task

/**
 * Thin wrapper around the Google Sign-In SDK.
 *
 * The flow we use is the one Supabase officially documents for Android:
 *
 *   1. User taps "Sign in with Google".
 *   2. GoogleSignInClient shows the account picker.
 *   3. We extract the Google `idToken` from the resulting account.
 *   4. We hand that token to Supabase via
 *      `auth.signInWith(IdToken) { provider = Google; idToken = ... }`.
 *   5. Supabase validates the token against its configured Google OAuth
 *      Web Client ID and issues a Supabase session. Google itself does
 *      NOT keep the user logged in -- Supabase does.
 *
 * Important: the Web Client ID we pass to `requestIdToken()` MUST match
 * the Client ID configured in the Supabase dashboard under
 * Authentication -> Providers -> Google. Using the Android OAuth Client
 * ID here will cause Supabase to reject the token with an "audience
 * mismatch" error.
 */
object GoogleSignInHelper {

    /**
     * Builds a configured [GoogleSignInClient] for the supplied app context.
     * Re-creating this each call is cheap -- GoogleSignIn caches the
     * underlying account state internally.
     */
    fun createClient(context: Context): GoogleSignInClient {
        val appContext = context.applicationContext
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(BuildConfig.GOOGLE_WEB_CLIENT_ID)
            .requestEmail()
            .build()
        return GoogleSignIn.getClient(appContext, gso)
    }

    /**
     * Synchronously extracts the [GoogleSignInAccount] from a completed
     * `Task<GoogleSignInAccount>` returned by the activity-result launcher.
     *
     * @return the account, or `null` if the user cancelled / picked an
     *         account that failed to sign in. Callers should surface a
     *         friendly toast on `null`.
     */
    fun getAccountFromTask(task: Task<GoogleSignInAccount>): GoogleSignInAccount? {
        return try {
            task.getResult(ApiException::class.java)
        } catch (e: ApiException) {
            android.util.Log.w(
                "GoogleSignInHelper",
                "Google sign-in failed. status=${e.statusCode} msg=${e.message}"
            )
            null
        }
    }
}
