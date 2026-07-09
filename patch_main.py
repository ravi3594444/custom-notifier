import sys

with open('app/src/main/java/com/example/MainActivity.kt', 'r') as f:
    lines = f.readlines()

out = []
skip = False
for line in lines:
    if line.startswith('suspend fun trimAudio'):
        skip = True
    if line.startswith('fun setRingtoneFromUri'):
        skip = False
    
    if not skip:
        out.append(line)

new_functions = """suspend fun trimAudio(context: Context, inputUri: Uri, outputFile: File, startTimeMs: Long, endTimeMs: Long): Boolean = withContext(Dispatchers.IO) {
    val extractor = android.media.MediaExtractor()
    var muxer: android.media.MediaMuxer? = null
    var trackIndex = -1
    var muxerTrackIndex = -1
    try {
        extractor.setDataSource(context, inputUri, null)
        for (i in 0 until extractor.trackCount) {
            val format = extractor.getTrackFormat(i)
            val mime = format.getString(android.media.MediaFormat.KEY_MIME)
            if (mime?.startsWith("audio/") == true) {
                // MediaMuxer only supports specific formats
                if (mime != "audio/mp4a-latm" && mime != "audio/3gpp" && mime != "audio/amr-wb") {
                    return@withContext false
                }
                extractor.selectTrack(i)
                trackIndex = i
                muxer = android.media.MediaMuxer(outputFile.absolutePath, android.media.MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
                muxerTrackIndex = muxer.addTrack(format)
                break
            }
        }
        if (trackIndex == -1 || muxer == null) return@withContext false
        muxer.start()
        extractor.seekTo(startTimeMs * 1000, android.media.MediaExtractor.SEEK_TO_CLOSEST_SYNC)
        val buffer = java.nio.ByteBuffer.allocate(1024 * 1024)
        val bufferInfo = android.media.MediaCodec.BufferInfo()
        while (true) {
            bufferInfo.size = extractor.readSampleData(buffer, 0)
            if (bufferInfo.size < 0) break
            val sampleTime = extractor.sampleTime
            if (sampleTime > endTimeMs * 1000) break
            bufferInfo.presentationTimeUs = sampleTime - (startTimeMs * 1000)
            if (bufferInfo.presentationTimeUs < 0) bufferInfo.presentationTimeUs = 0
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

suspend fun extractAudioFromVideo(context: Context, videoUri: Uri, outputFile: File): Boolean = withContext(Dispatchers.IO) {
    val extractor = android.media.MediaExtractor()
    var muxer: android.media.MediaMuxer? = null
    var audioTrackIndex = -1
    var muxerAudioTrackIndex = -1
    try {
        extractor.setDataSource(context, videoUri, null)
        for (i in 0 until extractor.trackCount) {
            val format = extractor.getTrackFormat(i)
            val mime = format.getString(android.media.MediaFormat.KEY_MIME)
            if (mime?.startsWith("audio/") == true) {
                if (mime != "audio/mp4a-latm" && mime != "audio/3gpp" && mime != "audio/amr-wb") {
                    return@withContext false
                }
                extractor.selectTrack(i)
                audioTrackIndex = i
                muxer = android.media.MediaMuxer(outputFile.absolutePath, android.media.MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
                muxerAudioTrackIndex = muxer.addTrack(format)
                break
            }
        }
        if (audioTrackIndex == -1 || muxer == null) return@withContext false
        muxer.start()
        val buffer = java.nio.ByteBuffer.allocate(1024 * 1024)
        val bufferInfo = android.media.MediaCodec.BufferInfo()
        while (true) {
            bufferInfo.size = extractor.readSampleData(buffer, 0)
            if (bufferInfo.size < 0) break
            bufferInfo.presentationTimeUs = extractor.sampleTime
            bufferInfo.offset = 0
            bufferInfo.flags = extractor.sampleFlags
            muxer.writeSampleData(muxerAudioTrackIndex, buffer, bufferInfo)
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

"""

# Insert new functions
out.insert(out.index('fun setRingtoneFromUri(context: Context, sourceUri: Uri, isExtracted: Boolean = false) {\n'), new_functions)

# Remove FFmpeg imports if they exist
final_out = []
for line in out:
    if "com.arthenica.ffmpegkit" in line:
        continue
    final_out.append(line)

with open('app/src/main/java/com/example/MainActivity.kt', 'w') as f:
    f.writelines(final_out)

