package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class CallVideoWallpaperTest {

    private lateinit var context: Context
    private val testEmail = "test@example.com"

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        // Clean up any test artifacts from SharedPreferences or index files
        VideoLibraryManager.setActiveVideoId(context, null, testEmail)
        val index = File(context.filesDir, "video_library_test_example_com.json")
        if (index.exists()) index.delete()
        val dir = File(context.filesDir, "saved_videos")
        if (dir.exists()) dir.deleteRecursively()
    }

    @Test
    fun testActiveVideoIdStorage() {
        assertNull(VideoLibraryManager.getActiveVideoId(context))
        
        VideoLibraryManager.setActiveVideoId(context, "test_video_123", testEmail)
        assertEquals("test_video_123", VideoLibraryManager.getActiveVideoId(context))
        
        VideoLibraryManager.setActiveVideoId(context, null, testEmail)
        assertNull(VideoLibraryManager.getActiveVideoId(context))
    }

    @Test
    fun testSaveAndLoadVideoLibrary() {
        val videos = listOf(
            SavedVideo(
                id = "video_1",
                displayName = "Test Video 1.mp4",
                localFilePath = "/fake/path/1.mp4",
                fileSizeBytes = 1024,
                durationMs = 5000,
                width = 1080,
                height = 1920,
                createdAt = System.currentTimeMillis(),
                mimeType = "video/mp4"
            )
        )
        
        VideoLibraryManager.saveAll(context, testEmail, videos)
        
        val loaded = VideoLibraryManager.loadAll(context, testEmail)
        assertEquals(1, loaded.size)
        assertEquals("video_1", loaded[0].id)
        assertEquals("Test Video 1.mp4", loaded[0].displayName)
        assertEquals(1080, loaded[0].width)
        assertEquals(1920, loaded[0].height)
    }

    @Test
    fun testRemoveVideoAndActiveSync() {
        // Create dummy video file to test existence check inside getActiveVideo
        val videoDir = VideoLibraryManager.savedVideosDir(context)
        val dummyFile = File(videoDir, "video_test.mp4")
        dummyFile.writeText("dummy content")

        val video = SavedVideo(
            id = "test_id",
            displayName = "video_test.mp4",
            localFilePath = dummyFile.absolutePath,
            fileSizeBytes = dummyFile.length(),
            durationMs = 3000,
            width = 720,
            height = 1280,
            createdAt = System.currentTimeMillis(),
            mimeType = "video/mp4"
        )

        // Save to index
        VideoLibraryManager.saveAll(context, testEmail, listOf(video))
        // Set as active
        VideoLibraryManager.setActiveVideoId(context, video.id, testEmail)

        // Verify active video works
        val activeVideo = VideoLibraryManager.getActiveVideo(context, testEmail)
        assertNotNull(activeVideo)
        assertEquals("test_id", activeVideo?.id)

        // Remove video
        val remaining = VideoLibraryManager.remove(context, testEmail, video.id)
        assertTrue(remaining.isEmpty())

        // File should be deleted
        assertFalse(dummyFile.exists())

        // Active video should be cleared automatically since it was deleted
        assertNull(VideoLibraryManager.getActiveVideoId(context))
    }

    @Test
    fun testCallVideoControllerGracefulAnswersAndRejections() {
        // Check that CallVideoController doesn't crash under JVM Robolectric.
        // These methods interact with system telephony/telecom frameworks and should execute safely without uncaught exceptions.
        try {
            CallVideoController.answerCall(context)
            CallVideoController.rejectCall(context)
        } catch (e: Exception) {
            fail("CallVideoController threw an exception during standard execution: ${e.message}")
        }
    }
}
