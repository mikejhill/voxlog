package com.mikejhill.voxlog.core.data.audio

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import com.mikejhill.voxlog.engine.whisper.WhisperAudioFormat
import java.io.File
import java.io.RandomAccessFile

/**
 * Compresses a VoxLog WAV recording into AAC-LC in an MP4 (`.m4a`) container using the platform
 * encoder. 64 kbps mono speech is roughly 0.5 MB per minute versus 1.9 MB for raw PCM.
 */
object AacTranscoder {
    private const val MIME_TYPE = MediaFormat.MIMETYPE_AUDIO_AAC
    private const val BIT_RATE = 64_000
    private const val TIMEOUT_MICROS = 10_000L
    private const val MICROS_PER_SECOND = 1_000_000L

    /** Encodes [wavFile] into [outputFile], overwriting it. */
    fun transcode(wavFile: File, outputFile: File) {
        val format = MediaFormat.createAudioFormat(MIME_TYPE, WhisperAudioFormat.SAMPLE_RATE_HZ, WhisperAudioFormat.CHANNEL_COUNT).apply {
            setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            setInteger(MediaFormat.KEY_BIT_RATE, BIT_RATE)
        }
        val codec = MediaCodec.createEncoderByType(MIME_TYPE)
        codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        val muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        try {
            codec.start()
            RandomAccessFile(wavFile, "r").use { input ->
                input.seek(WavHeader.SIZE_BYTES.toLong())
                EncodeLoop(codec, muxer, input).run()
            }
        } finally {
            codec.release()
            muxer.release()
        }
    }

    /** Feeds PCM into the encoder and drains encoded frames into the muxer until end of stream. */
    private class EncodeLoop(private val codec: MediaCodec, private val muxer: MediaMuxer, private val input: RandomAccessFile) {
        private val info = MediaCodec.BufferInfo()
        private var trackIndex = -1
        private var isInputDone = false
        private var isOutputDone = false
        private var bytesQueued = 0L

        fun run() {
            while (!isOutputDone) {
                if (!isInputDone) queueInput()
                drainOutput()
            }
            muxer.stop()
        }

        private fun queueInput() {
            val index = codec.dequeueInputBuffer(TIMEOUT_MICROS)
            if (index < 0) return
            val buffer = codec.getInputBuffer(index) ?: return
            val chunk = ByteArray(buffer.remaining())
            val read = input.read(chunk)
            val presentationTimeMicros = bytesQueued * MICROS_PER_SECOND / WhisperAudioFormat.BYTES_PER_SECOND
            if (read <= 0) {
                codec.queueInputBuffer(index, 0, 0, presentationTimeMicros, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                isInputDone = true
            } else {
                buffer.put(chunk, 0, read)
                codec.queueInputBuffer(index, 0, read, presentationTimeMicros, 0)
                bytesQueued += read
            }
        }

        private fun drainOutput() {
            val index = codec.dequeueOutputBuffer(info, TIMEOUT_MICROS)
            when {
                index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    trackIndex = muxer.addTrack(codec.outputFormat)
                    muxer.start()
                }

                index >= 0 -> {
                    val buffer = codec.getOutputBuffer(index)
                    val isConfig = info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0
                    val hasMediaData = info.size > 0 && !isConfig
                    if (buffer != null && hasMediaData && trackIndex >= 0) {
                        muxer.writeSampleData(trackIndex, buffer, info)
                    }
                    codec.releaseOutputBuffer(index, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) isOutputDone = true
                }
            }
        }
    }
}
