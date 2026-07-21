package com.example

import android.content.ContentResolver
import android.database.Cursor
import android.provider.ContactsContract
import android.util.Log

/**
 * Utility object to look up contact names from phone numbers.
 */
object ContactUtils {
    private const val TAG = "ContactUtils"

    /**
     * Looks up a contact name from a phone number.
     * 
     * @param contentResolver The content resolver to use
     * @param phoneNumber The incoming phone number
     * @return The contact's display name, or null if not found
     */
    fun getContactName(contentResolver: ContentResolver, phoneNumber: String?): String? {
        if (phoneNumber.isNullOrBlank()) {
            return null
        }

        try {
            // First, try to find by phone number (exact or normalized match)
            val contactName = lookupByPhoneNumber(contentResolver, phoneNumber)
            if (contactName != null) {
                Log.d(TAG, "Found contact name for $phoneNumber: $contactName")
                return contactName
            }

            // If not found, try normalizing the phone number (remove spaces, dashes, etc.)
            val normalizedNumber = normalizePhoneNumber(phoneNumber)
            if (normalizedNumber != phoneNumber) {
                val normalizedName = lookupByPhoneNumber(contentResolver, normalizedNumber)
                if (normalizedName != null) {
                    Log.d(TAG, "Found contact name for normalized $normalizedNumber: $normalizedName")
                    return normalizedName
                }
            }

            Log.d(TAG, "No contact found for phone number: $phoneNumber")
            return null
        } catch (e: Exception) {
            Log.e(TAG, "Error looking up contact name", e)
            return null
        }
    }

    /**
     * Looks up a contact by phone number using the contacts provider.
     */
    private fun lookupByPhoneNumber(contentResolver: ContentResolver, phoneNumber: String): String? {
        var cursor: Cursor? = null
        try {
            // Query the contacts database for a matching phone number
            val uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
            val projection = arrayOf(
                ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY,
                ContactsContract.CommonDataKinds.Phone.NUMBER
            )

            // Use LIKE for partial matching (in case of country code differences)
            val selection = "${ContactsContract.CommonDataKinds.Phone.NUMBER} LIKE ?"
            val selectionArgs = arrayOf("%$phoneNumber%")
            
            cursor = contentResolver.query(uri, projection, selection, selectionArgs, null)

            if (cursor != null && cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY)
                if (nameIndex >= 0) {
                    val name = cursor.getString(nameIndex)
                    return name?.takeIf { it.isNotBlank() }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in lookupByPhoneNumber", e)
        } finally {
            cursor?.close()
        }
        return null
    }

    /**
     * Normalizes a phone number by removing non-digit characters except +.
     * This helps match numbers with different formatting.
     */
    private fun normalizePhoneNumber(phoneNumber: String): String {
        return phoneNumber.filter { it.isDigit() || it == '+' }
    }

    /**
     * Checks if the app has permission to read contacts.
     */
    fun hasContactsPermission(contentResolver: ContentResolver): Boolean {
        return try {
            val cursor = contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(ContactsContract.CommonDataKinds.Phone._ID),
                null,
                null,
                null
            )
            cursor?.close()
            true
        } catch (e: SecurityException) {
            false
        } catch (e: Exception) {
            false
        }
    }
}
