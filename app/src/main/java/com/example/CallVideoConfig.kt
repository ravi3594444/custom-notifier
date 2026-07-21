package com.example

import android.os.Parcel
import android.os.Parcelable

/**
 * All the configuration that [CallVideoActivity] needs to render a call-video
 * wallpaper. Replaces the previous 15 individual intent extras
 * (EXTRA_VIDEO_PATH, EXTRA_TRIM_START_MS, EXTRA_NAME_TEXT_COLOR, etc.) with
 * a single Parcelable — adding a new customization option is now a one-line
 * change to this data class instead of touching 5 files (the EXTRA_*
 * constant, the field+read in onCreate, the screen parameter, the receiver
 * forward, the Test Call forward).
 *
 * Construct via [SavedVideo.toCallVideoConfig] (real call) or
 * [SavedVideo.toCallVideoConfigPreview] (Test Call button) so the defaults
 * for null styling fields stay in one place.
 *
 * Bundle / Intent usage:
 *   intent.putExtra(CallVideoActivity.EXTRA_CONFIG, config)
 *   val config = intent.getParcelableExtra<CallVideoConfig>(CallVideoActivity.EXTRA_CONFIG)
 *
 * Note: getParcelableExtra is deprecated on API 33+ in favor of
 * getParcelableExtra(name, class). We use the compat helper in
 * CallVideoActivity to keep this readable.
 */
data class CallVideoConfig(
    /** Absolute path to the video file under filesDir/saved_videos/. */
    val videoPath: String,
    /** Human-friendly name shown in the chip at the top of the call screen. */
    val videoDisplayName: String,
    /** MIME type of the video (e.g. "video/mp4"). Currently unused by VideoView
     *  but kept for future use and for logging. */
    val videoMimeType: String,
    /** True when launched from the Test Call button — affects back-press
     *  handling (allowed in preview, blocked in real call) and caller-name
     *  display ("Ravi" vs the video's filename). */
    val isPreviewMode: Boolean,
    /** Trim window start in ms, or -1 to play from the beginning. */
    val trimStartMs: Long,
    /** Trim window end in ms, or -1 to play to the end. */
    val trimEndMs: Long,
    /** Optional path to a custom audio file that replaces the video's own
     *  audio track. Null = use video's audio. */
    val customAudioPath: String?,
    /** Video scale multiplier (1.0 = fit, 2.0 = 2x zoom). */
    val videoScale: Float,
    /** Vertical position of the caller-name chip, 0.0 = top, 1.0 = bottom. */
    val namePositionY: Float,
    /** Custom caller name to display on incoming calls (e.g., "Ravi"). Falls back to videoDisplayName if null/blank. */
    val callerName: String?,
    /** The phone number of the incoming caller (used to look up contact name). */
    val callerNumber: String?,
    /** "swipe" or "buttons" — which answer UI to show. */
    val answerStyle: String,
    /** Caller-name font size in sp. */
    val nameFontSize: Float,
    /** Caller-name font family: "sans-serif" | "serif" | "monospace" | "cursive". */
    val nameFontFamily: String,
    /** Caller-name text color as ARGB int. */
    val nameTextColor: Int,
    /** Caller-name background color as ARGB int. */
    val nameBgColor: Int,
    /** Video filter: "normal" | "grayscale" | "sepia" | "invert" | "vintage". */
    val videoFilter: String
) : Parcelable {

    constructor(parcel: Parcel) : this(
        videoPath = parcel.readString() ?: "",
        videoDisplayName = parcel.readString() ?: "Incoming Call",
        videoMimeType = parcel.readString() ?: "video/*",
        isPreviewMode = parcel.readByte().toInt() != 0,
        trimStartMs = parcel.readLong(),
        trimEndMs = parcel.readLong(),
        customAudioPath = parcel.readString(),
        videoScale = parcel.readFloat(),
        namePositionY = parcel.readFloat(),
        callerName = parcel.readString(),
        callerNumber = parcel.readString(),
        answerStyle = parcel.readString() ?: "swipe",
        nameFontSize = parcel.readFloat(),
        nameFontFamily = parcel.readString() ?: "sans-serif",
        nameTextColor = parcel.readInt(),
        nameBgColor = parcel.readInt(),
        videoFilter = parcel.readString() ?: "normal"
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(videoPath)
        parcel.writeString(videoDisplayName)
        parcel.writeString(videoMimeType)
        parcel.writeByte(if (isPreviewMode) 1 else 0)
        parcel.writeLong(trimStartMs)
        parcel.writeLong(trimEndMs)
        parcel.writeString(customAudioPath)
        parcel.writeFloat(videoScale)
        parcel.writeFloat(namePositionY)
        parcel.writeString(callerName)
        parcel.writeString(callerNumber)
        parcel.writeString(answerStyle)
        parcel.writeFloat(nameFontSize)
        parcel.writeString(nameFontFamily)
        parcel.writeInt(nameTextColor)
        parcel.writeInt(nameBgColor)
        parcel.writeString(videoFilter)
    }

    override fun describeContents(): Int = 0

    companion object CREATOR : Parcelable.Creator<CallVideoConfig> {
        override fun createFromParcel(parcel: Parcel): CallVideoConfig = CallVideoConfig(parcel)
        override fun newArray(size: Int): Array<CallVideoConfig?> = arrayOfNulls(size)
    }
}

/**
 * Builds a [CallVideoConfig] for a real incoming call (not preview mode).
 *
 * Defaults for null styling fields match the previous intent-extra defaults
 * so existing user libraries keep working without migration.
 */
fun SavedVideo.toCallVideoConfig(): CallVideoConfig = CallVideoConfig(
    videoPath = localFilePath,
    videoDisplayName = displayName,
    videoMimeType = mimeType,
    isPreviewMode = false,
    trimStartMs = trimStartMs ?: -1L,
    trimEndMs = trimEndMs ?: -1L,
    customAudioPath = customAudioPath,
    videoScale = videoScale ?: 1.0f,
    namePositionY = namePositionY ?: 0.1f,
    callerName = callerName,
    callerNumber = null, // Set at runtime when call comes in
    answerStyle = answerStyle ?: "swipe",
    nameFontSize = nameFontSize ?: 15f,
    nameFontFamily = nameFontFamily ?: "sans-serif",
    nameTextColor = nameTextColor ?: android.graphics.Color.WHITE,
    nameBgColor = nameBgColor ?: android.graphics.Color.parseColor("#80000000"),
    videoFilter = videoFilter ?: "normal"
)

/**
 * Builds a [CallVideoConfig] for the Test Call preview button. Same as
 * [toCallVideoConfig] but with isPreviewMode = true and a fixed caller
 * name ("Ravi") so the preview always shows the same way.
 */
fun SavedVideo.toCallVideoConfigPreview(previewDisplayName: String = "Ravi"): CallVideoConfig =
    toCallVideoConfig().copy(
        isPreviewMode = true,
        videoDisplayName = previewDisplayName
    )
