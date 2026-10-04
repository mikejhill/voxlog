package com.mikejhill.voxlog.core.data.audio

import android.Manifest
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.annotation.RequiresPermission
import com.mikejhill.voxlog.engine.whisper.WhisperAudioFormat
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sqrt
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Records 16 kHz mono PCM straight into a WAV file on a dedicated thread. There is no time limit:
 * recording runs until [stop] is called. Data is flushed continuously and the header can be
 * repaired after a crash with [WavHeader.repair], so audio is never lost.
 */
class WavRecorder(private val file: File) {
    private val level = MutableStateFlow(0f)
    private var audioRecord: AudioRecord? = null
    private var thread: Thread? = null

    @Volatile
    private var isRunning = false

    /** Normalized input level 0–1 for the UI meter. */
    val inputLevel: StateFlow<Float> = level.asStateFlow()

    /** Bytes of audio written so far. */
    val recordedBytes: Long
        get() = (file.length() - WavHeader.SIZE_BYTES).coerceAtLeast(0)

    /** Opens the microphone and starts writing. Returns once the first buffer is being captured. */
    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    fun start() {
        check(!isRunning) { "Recorder already started" }
        val bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL, ENCODING).coerceAtLeast(MIN_BUFFER_BYTES)
        val record = AudioRecord(MediaRecorder.AudioSource.MIC, SAMPLE_RATE, CHANNEL, ENCODING, bufferSize * 2)
        check(record.state == AudioRecord.STATE_INITIALIZED) { "Microphone unavailable" }
        file.parentFile?.mkdirs()
        RandomAccessFile(file, "rw").use { output ->
            output.setLength(0)
            output.write(WavHeader.build(dataSizeBytes = 0))
        }
        record.startRecording()
        audioRecord = record
        isRunning = true
        thread = Thread({ captureLoop(record, bufferSize) }, "voxlog-recorder").apply {
            priority = Thread.MAX_PRIORITY
            start()
        }
    }

    /** Stops recording, finalizes the header and returns the recorded duration in milliseconds. */
    fun stop(): Long {
        isRunning = false
        thread?.join()
        thread = null
        audioRecord?.run {
            stop()
            release()
        }
        audioRecord = null
        WavHeader.repair(file)
        return recordedBytes * MILLIS_PER_SECOND / WhisperAudioFormat.BYTES_PER_SECOND
    }

    private fun captureLoop(record: AudioRecord, bufferSize: Int) {
        val buffer = ByteArray(bufferSize)
        RandomAccessFile(file, "rw").use { output ->
            output.seek(output.length())
            while (isRunning) {
                val read = record.read(buffer, 0, buffer.size)
                if (read <= 0) continue
                output.write(buffer, 0, read)
                level.value = rmsLevel(buffer, read)
            }
        }
        level.value = 0f
    }

    private fun rmsLevel(buffer: ByteArray, length: Int): Float {
        val samples = ByteBuffer.wrap(buffer, 0, length).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
        if (samples.remaining() == 0) return 0f
        var sumOfSquares = 0.0
        for (index in 0 until samples.remaining()) {
            val sample = samples.get(index) / Short.MAX_VALUE.toDouble()
            sumOfSquares += sample * sample
        }
        return (sqrt(sumOfSquares / samples.remaining()) * LEVEL_GAIN).toFloat().coerceIn(0f, 1f)
    }

    private companion object {
        const val SAMPLE_RATE = WhisperAudioFormat.SAMPLE_RATE_HZ
        const val CHANNEL = AudioFormat.CHANNEL_IN_MONO
        const val ENCODING = AudioFormat.ENCODING_PCM_16BIT
        const val MIN_BUFFER_BYTES = 4096
        const val MILLIS_PER_SECOND = 1000L
        const val LEVEL_GAIN = 4.0
    }
}

/** Canonical 44-byte RIFF/WAVE header for 16 kHz mono 16-bit PCM. */
object WavHeader {
    /** Header length in bytes. */
    const val SIZE_BYTES: Int = 44

    private const val RIFF_CHUNK_OVERHEAD = 36
    private const val FMT_CHUNK_SIZE = 16
    private const val PCM_FORMAT: Short = 1
    private const val DATA_SIZE_OFFSET = 40L
    private const val RIFF_SIZE_OFFSET = 4L

    /** Builds a header declaring [dataSizeBytes] of PCM data. */
    fun build(dataSizeBytes: Long): ByteArray {
        val blockAlign = WhisperAudioFormat.CHANNEL_COUNT * WhisperAudioFormat.BITS_PER_SAMPLE / Byte.SIZE_BITS
        return ByteBuffer.allocate(SIZE_BYTES).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray())
            putInt((dataSizeBytes + RIFF_CHUNK_OVERHEAD).toInt())
            put("WAVE".toByteArray())
            put("fmt ".toByteArray())
            putInt(FMT_CHUNK_SIZE)
            putShort(PCM_FORMAT)
            putShort(WhisperAudioFormat.CHANNEL_COUNT.toShort())
            putInt(WhisperAudioFormat.SAMPLE_RATE_HZ)
            putInt(WhisperAudioFormat.BYTES_PER_SECOND)
            putShort(blockAlign.toShort())
            putShort(WhisperAudioFormat.BITS_PER_SAMPLE.toShort())
            put("data".toByteArray())
            putInt(dataSizeBytes.toInt())
        }.array()
    }

    /** Rewrites the size fields from the actual file length; safe to call on interrupted recordings. */
    fun repair(file: File) {
        if (!file.exists() || file.length() < SIZE_BYTES) return
        val dataSize = file.length() - SIZE_BYTES
        RandomAccessFile(file, "rw").use { output ->
            output.seek(RIFF_SIZE_OFFSET)
            output.write(littleEndianInt((dataSize + RIFF_CHUNK_OVERHEAD).toInt()))
            output.seek(DATA_SIZE_OFFSET)
            output.write(littleEndianInt(dataSize.toInt()))
        }
    }

    private fun littleEndianInt(value: Int): ByteArray =
        ByteBuffer.allocate(Int.SIZE_BYTES).order(ByteOrder.LITTLE_ENDIAN).putInt(value).array()
}
