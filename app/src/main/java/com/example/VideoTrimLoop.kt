package com.example

import android.widget.VideoView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import kotlinx.coroutines.delay

/**
 * Reusable trim-loop helper for VideoView-based screens.
 *
 * Replaces the old `view.postDelayed { ... postDelayed(this, 100) }` pattern
 * that was copy-pasted across 3 call sites (CallVideoActivity + two places
 * in VideoEditDialog). The old pattern had a serious bug: the `update`
 * lambda of AndroidView runs on EVERY recomposition, and each invocation
 * spawned a new self-perpetuating polling Runnable. `removeCallbacks` was
 * never called, so after 30 seconds of typing in the trim fields the user
 * would have 50+ Runnables all calling `view.seekTo()` 10x/second on the
 * same VideoView — massive CPU/battery waste and jank.
 *
 * This composable uses LaunchedEffect keyed on [videoView], [startMs], and
 * [endMs]. When any of those change (or the composable leaves the
 * composition), the coroutine is automatically cancelled — no manual
 * cleanup needed, no Runnable leak.
 *
 * Call it from inside any composable that owns a VideoView, right next to
 * the AndroidView that produces the [videoView] ref:
 *
 *   val videoViewRef = remember { mutableStateOf<VideoView?>(null) }
 *   AndroidView(... videoViewRef = it ...)
 *   rememberTrimLoop(videoViewRef.value, startMs, endMs)
 *
 * @param videoView the VideoView to monitor. Pass null while the view
 *   isn't ready yet — the loop will just no-op until it becomes non-null.
 * @param startMs trim start in milliseconds. The loop seeks back to this
 *   position when the playback position passes [endMs].
 * @param endMs trim end in milliseconds. When playback reaches this,
 *   the loop seeks back to [startMs]. If endMs <= 0 or endMs <= startMs,
 *   the loop is a no-op (trim is effectively disabled).
 */
@Composable
fun rememberTrimLoop(
    videoView: VideoView?,
    startMs: Long,
    endMs: Long
) {
    // Kept as a lambda so callers can also invoke it manually if needed
    // (e.g. from a click listener). The main usage is the LaunchedEffect
    // below.
    val tick: () -> Unit = remember(videoView, startMs, endMs) {
        {
            if (videoView == null) return@remember
            if (endMs <= 0 || endMs <= startMs) return@remember
            try {
                if (videoView.isPlaying && videoView.currentPosition >= endMs) {
                    videoView.seekTo(startMs.coerceAtLeast(0).toInt())
                }
            } catch (_: Exception) {
                // VideoView can throw if the media is unprepared or has
                // been released between checks. Swallow — we'll retry on
                // the next tick.
            }
        }
    }

    LaunchedEffect(videoView, startMs, endMs) {
        if (videoView == null) return@LaunchedEffect
        if (endMs <= 0 || endMs <= startMs) return@LaunchedEffect
        while (true) {
            tick()
            delay(100)
        }
    }
}
