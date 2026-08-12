package com.example.audiobook

import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import java.io.File
import java.io.RandomAccessFile

/** Encodes the temporary PCM WAV chapter into a seekable AAC/M4A file. */
object AacChapterEncoder {
    fun encodeWav(input: File, output: File): Long {
        require(input.isFile && input.length() > WAV_HEADER_BYTES) { "Generated WAV is empty" }
        val header = ByteArray(WAV_HEADER_BYTES)
        RandomAccessFile(input, "r").use { file ->
            file.readFully(header)
        }
        val channels = littleShort(header, 22)
        val sampleRate = littleInt(header, 24)
        val bitsPerSample = littleShort(header, 34)
        val byteRate = littleInt(header, 28)
        require(channels > 0 && sampleRate > 0 && bitsPerSample == 16 && byteRate > 0) { "Unsupported WAV format" }

        output.parentFile?.mkdirs()
        output.delete()
        val format = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, sampleRate, channels).apply {
            setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            setInteger(MediaFormat.KEY_BIT_RATE, AAC_BITRATE)
            setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, INPUT_BUFFER_BYTES)
            setInteger(MediaFormat.KEY_PCM_ENCODING, AudioFormat.ENCODING_PCM_16BIT)
        }
        val codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
        val muxer = MediaMuxer(output.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        var track = -1
        var muxerStarted = false
        var inputDone = false
        var outputDone = false
        var presentationUs = 0L
        val info = MediaCodec.BufferInfo()
        val pcm = ByteArray(INPUT_BUFFER_BYTES)
        try {
            codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            codec.start()
            RandomAccessFile(input, "r").use { source ->
                source.seek(WAV_HEADER_BYTES.toLong())
                while (!outputDone) {
                    if (!inputDone) {
                            val inputIndex = codec.dequeueInputBuffer(TIMEOUT_US)
                        if (inputIndex >= 0) {
                            val buffer = codec.getInputBuffer(inputIndex) ?: error("AAC input buffer unavailable")
                            buffer.clear()
                            val count = source.read(pcm, 0, minOf(buffer.remaining(), INPUT_BUFFER_BYTES))
                            if (count < 0) {
                                codec.queueInputBuffer(inputIndex, 0, 0, presentationUs, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                inputDone = true
                            } else {
                                buffer.put(pcm, 0, count)
                                codec.queueInputBuffer(inputIndex, 0, count, presentationUs, 0)
                                presentationUs += count * 1_000_000L / byteRate
                            }
                        }
                    }
                    when (val outputIndex = codec.dequeueOutputBuffer(info, TIMEOUT_US)) {
                        MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                            check(!muxerStarted) { "AAC output format changed twice" }
                            track = muxer.addTrack(codec.outputFormat)
                            muxer.start()
                            muxerStarted = true
                        }
                        MediaCodec.INFO_TRY_AGAIN_LATER -> Unit
                        else -> if (outputIndex >= 0) {
                            codec.getOutputBuffer(outputIndex)?.let { buffer ->
                                if (info.size > 0 && muxerStarted && info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0) {
                                    buffer.position(info.offset)
                                    buffer.limit(info.offset + info.size)
                                    muxer.writeSampleData(track, buffer, info)
                                }
                            }
                            codec.releaseOutputBuffer(outputIndex, false)
                            if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
                        }
                    }
                }
            }
            check(muxerStarted && output.length() > 0) { "AAC encoder produced no output" }
            return (input.length() - WAV_HEADER_BYTES) * 1000L / byteRate
        } finally {
            runCatching { codec.stop() }
            codec.release()
            if (muxerStarted) runCatching { muxer.stop() }
            muxer.release()
            if (!output.isFile || output.length() == 0L) output.delete()
        }
    }

    private fun littleInt(bytes: ByteArray, offset: Int) =
        (bytes[offset].toInt() and 0xff) or ((bytes[offset + 1].toInt() and 0xff) shl 8) or
            ((bytes[offset + 2].toInt() and 0xff) shl 16) or ((bytes[offset + 3].toInt() and 0xff) shl 24)

    private fun littleShort(bytes: ByteArray, offset: Int) =
        (bytes[offset].toInt() and 0xff) or ((bytes[offset + 1].toInt() and 0xff) shl 8)

    private const val WAV_HEADER_BYTES = 44
    private const val AAC_BITRATE = 64_000
    private const val INPUT_BUFFER_BYTES = 16 * 1024
    private const val TIMEOUT_US = 10_000L
}
