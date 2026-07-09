package com.example

import android.content.Context
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

object AudioProcessor {
    suspend fun process(
        context: Context,
        inputUri: Uri,
        outputFile: File,
        startTimeMs: Long,
        endTimeMs: Long,
        fadeInMs: Long = 0L,
        fadeOutMs: Long = 0L,
        volumeMultiplier: Float = 1.0f
    ): Boolean = withContext(Dispatchers.IO) {
        val extractor = MediaExtractor()
        var decoder: MediaCodec? = null
        var encoder: MediaCodec? = null
        var muxer: MediaMuxer? = null
        var isMuxerStarted = false

        try {
            extractor.setDataSource(context, inputUri, null)
            var trackIndex = -1
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME)
                if (mime?.startsWith("audio/") == true) {
                    extractor.selectTrack(i)
                    trackIndex = i
                    break
                }
            }

            if (trackIndex == -1) {
                return@withContext false
            }

            val inputFormat = extractor.getTrackFormat(trackIndex)
            val mime = inputFormat.getString(MediaFormat.KEY_MIME) ?: return@withContext false
            val sampleRate = if (inputFormat.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                inputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            } else {
                44100
            }
            val channelCount = if (inputFormat.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                inputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            } else {
                1
            }

            // 1. Initialize and run Decoder to get raw PCM data
            decoder = MediaCodec.createDecoderByType(mime)
            decoder.configure(inputFormat, null, null, 0)
            decoder.start()

            val pcmBytes = ByteArrayOutputStream()
            val decodeBufferInfo = MediaCodec.BufferInfo()
            var isDecoderInputEOS = false
            var isDecoderOutputEOS = false
            val timeoutUs = 5000L

            // Seek to start time
            extractor.seekTo(startTimeMs * 1000L, MediaExtractor.SEEK_TO_CLOSEST_SYNC)

            while (!isDecoderOutputEOS) {
                if (!isDecoderInputEOS) {
                    val inputBufferIndex = decoder.dequeueInputBuffer(timeoutUs)
                    if (inputBufferIndex >= 0) {
                        val inputBuffer = decoder.getInputBuffer(inputBufferIndex)
                        if (inputBuffer != null) {
                            inputBuffer.clear()
                            val sampleSize = extractor.readSampleData(inputBuffer, 0)
                            val sampleTime = extractor.sampleTime

                            if (sampleSize < 0 || sampleTime > endTimeMs * 1000L) {
                                decoder.queueInputBuffer(
                                    inputBufferIndex,
                                    0,
                                    0,
                                    0L,
                                    MediaCodec.BUFFER_FLAG_END_OF_STREAM
                                )
                                isDecoderInputEOS = true
                            } else {
                                decoder.queueInputBuffer(
                                    inputBufferIndex,
                                    0,
                                    sampleSize,
                                    sampleTime,
                                    0
                                )
                                extractor.advance()
                            }
                        }
                    }
                }

                val outputBufferIndex = decoder.dequeueOutputBuffer(decodeBufferInfo, timeoutUs)
                if (outputBufferIndex >= 0) {
                    val outputBuffer = decoder.getOutputBuffer(outputBufferIndex)
                    if (outputBuffer != null && decodeBufferInfo.size > 0) {
                        val chunk = ByteArray(decodeBufferInfo.size)
                        outputBuffer.position(decodeBufferInfo.offset)
                        outputBuffer.get(chunk)
                        pcmBytes.write(chunk)
                    }
                    decoder.releaseOutputBuffer(outputBufferIndex, false)

                    if ((decodeBufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        isDecoderOutputEOS = true
                    }
                }
            }

            // Release decoder and extractor early to free up memory/resources
            try {
                decoder.stop()
                decoder.release()
                decoder = null
            } catch (e: Exception) {}
            try {
                extractor.release()
            } catch (e: Exception) {}

            // Convert PCM bytes to ShortArray for processing
            val bytes = pcmBytes.toByteArray()
            if (bytes.isEmpty()) {
                return@withContext false
            }
            val shorts = ShortArray(bytes.size / 2)
            ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(shorts)

            // 2. Volume Normalization (Peak amplitude mapping to 97% of max)
            var maxVal = 0
            for (s in shorts) {
                val absVal = java.lang.Math.abs(s.toInt())
                if (absVal > maxVal) {
                    maxVal = absVal
                }
            }

            val targetPeak = 32000.0
            val gain = if (maxVal > 0) targetPeak / maxVal else 1.0
            // Cap gain between 0.1 and 10.0 to prevent severe distortion or noise floor explosion
            val cappedGain = gain.coerceIn(0.1, 10.0)

            for (i in shorts.indices) {
                val normalized = (shorts[i] * cappedGain).toInt()
                shorts[i] = normalized.coerceIn(-32768, 32767).toShort()
            }

            // Apply manual volume multiplier (Volume Booster)
            if (volumeMultiplier != 1.0f) {
                for (i in shorts.indices) {
                    val scaled = (shorts[i] * volumeMultiplier).toInt()
                    shorts[i] = scaled.coerceIn(-32768, 32767).toShort()
                }
            }

            // Apply fade-in effect
            if (fadeInMs > 0L) {
                val fadeInSamples = ((fadeInMs * sampleRate * channelCount) / 1000L).toInt()
                val limit = Math.min(fadeInSamples, shorts.size)
                for (i in 0 until limit) {
                    val factor = i.toDouble() / fadeInSamples
                    shorts[i] = (shorts[i] * factor).toInt().toShort()
                }
            }

            // Apply fade-out effect
            if (fadeOutMs > 0L) {
                val fadeOutSamples = ((fadeOutMs * sampleRate * channelCount) / 1000L).toInt()
                val startFadeOut = Math.max(0, shorts.size - fadeOutSamples)
                for (i in startFadeOut until shorts.size) {
                    val remaining = shorts.size - 1 - i
                    val factor = remaining.toDouble() / fadeOutSamples
                    shorts[i] = (shorts[i] * factor).toInt().toShort()
                }
            }

            // 3. Initialize and run AAC Encoder to write to outputFile
            val encoderFormat = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, sampleRate, channelCount)
            encoderFormat.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            encoderFormat.setInteger(MediaFormat.KEY_BIT_RATE, 128000)
            encoderFormat.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 1024 * 1024)

            encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
            encoder.configure(encoderFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            encoder.start()

            muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            var muxerTrackIndex = -1
            var encoderInputEOS = false
            var encoderOutputEOS = false
            var shortsWritten = 0
            val encBufferInfo = MediaCodec.BufferInfo()

            while (!encoderOutputEOS) {
                if (!encoderInputEOS) {
                    val inputBufferIndex = encoder.dequeueInputBuffer(timeoutUs)
                    if (inputBufferIndex >= 0) {
                        val inputBuffer = encoder.getInputBuffer(inputBufferIndex)
                        if (inputBuffer != null) {
                            inputBuffer.clear()
                            val remainingShorts = shorts.size - shortsWritten
                            val shortsToCopy = Math.min(remainingShorts, inputBuffer.remaining() / 2)

                            if (shortsToCopy > 0) {
                                val shortBuffer = inputBuffer.asShortBuffer()
                                shortBuffer.put(shorts, shortsWritten, shortsToCopy)
                                shortsWritten += shortsToCopy

                                val bytesCopied = shortsToCopy * 2
                                val presentationTimeUs = (shortsWritten - shortsToCopy) * 1000000L / (sampleRate * channelCount)

                                encoder.queueInputBuffer(
                                    inputBufferIndex,
                                    0,
                                    bytesCopied,
                                    presentationTimeUs,
                                    0
                                )
                            } else {
                                val presentationTimeUs = shortsWritten * 1000000L / (sampleRate * channelCount)
                                encoder.queueInputBuffer(
                                    inputBufferIndex,
                                    0,
                                    0,
                                    presentationTimeUs,
                                    MediaCodec.BUFFER_FLAG_END_OF_STREAM
                                )
                                encoderInputEOS = true
                            }
                        }
                    }
                }

                val outputBufferIndex = encoder.dequeueOutputBuffer(encBufferInfo, timeoutUs)
                if (outputBufferIndex >= 0) {
                    val outputBuffer = encoder.getOutputBuffer(outputBufferIndex)
                    if (outputBuffer != null && encBufferInfo.size > 0 && isMuxerStarted) {
                        outputBuffer.position(encBufferInfo.offset)
                        outputBuffer.limit(encBufferInfo.offset + encBufferInfo.size)
                        muxer.writeSampleData(muxerTrackIndex, outputBuffer, encBufferInfo)
                    }
                    encoder.releaseOutputBuffer(outputBufferIndex, false)

                    if ((encBufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        encoderOutputEOS = true
                    }
                } else if (outputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    val newFormat = encoder.outputFormat
                    muxerTrackIndex = muxer.addTrack(newFormat)
                    muxer.start()
                    isMuxerStarted = true
                }
            }

            return@withContext true
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext false
        } finally {
            try { decoder?.stop() } catch (e: Exception) {}
            try { decoder?.release() } catch (e: Exception) {}
            try { encoder?.stop() } catch (e: Exception) {}
            try { encoder?.release() } catch (e: Exception) {}
            try { extractor.release() } catch (e: Exception) {}
            try {
                if (isMuxerStarted) {
                    muxer?.stop()
                }
            } catch (e: Exception) {}
            try { muxer?.release() } catch (e: Exception) {}
        }
    }
}
