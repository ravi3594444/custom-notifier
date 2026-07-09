package com.example

import android.content.Context
import android.media.*
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer

object VolumeNormalizer {
    suspend fun normalize(context: Context, inputUri: Uri, outputFile: File): Boolean = withContext(Dispatchers.IO) {
        val extractor = MediaExtractor()
        var muxer: MediaMuxer? = null
        var trackIndex = -1
        var muxerTrackIndex = -1
        try {
            extractor.setDataSource(context, inputUri, null)
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME)
                if (mime?.startsWith("audio/") == true) {
                    if (mime != "audio/mp4a-latm" && mime != "audio/3gpp" && mime != "audio/amr-wb") {
                        return@withContext false
                    }
                    extractor.selectTrack(i)
                    trackIndex = i
                    muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
                    muxerTrackIndex = muxer.addTrack(format)
                    break
                }
            }
            if (trackIndex == -1 || muxer == null) return@withContext false
            muxer.start()
            val buffer = ByteBuffer.allocate(1024 * 1024)
            val bufferInfo = MediaCodec.BufferInfo()
            while (true) {
                bufferInfo.size = extractor.readSampleData(buffer, 0)
                if (bufferInfo.size < 0) break
                bufferInfo.presentationTimeUs = extractor.sampleTime
                bufferInfo.offset = 0
                bufferInfo.flags = extractor.sampleFlags
                muxer.writeSampleData(muxerTrackIndex, buffer, bufferInfo)
                extractor.advance()
            }
            return@withContext true
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext false
        } finally {
            try { extractor.release() } catch (e: Exception) {}
            try { muxer?.stop(); muxer?.release() } catch (e: Exception) {}
        }
    }
}
