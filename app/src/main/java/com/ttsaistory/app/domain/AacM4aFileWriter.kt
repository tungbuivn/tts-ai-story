package com.ttsaistory.app.domain

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import com.ttsaistory.app.model.AppEditorConstants
import java.io.File
import java.io.FileInputStream
import kotlin.math.min

/**
 * Ghi một file .m4a (AAC-LC trong MPEG-4) bằng cách nạp tuần tự PCM 16-bit mono từng khối
 * (ví dụ từng file WAV đoạn TTS, đã bỏ header 44 byte).
 */
class AacM4aFileWriter(
    private val outFile: File,
) {
    private var sampleRate: Int = 0
    private var encoder: MediaCodec? = null
    private var muxer: MediaMuxer? = null
    private var trackIndex = -1
    private var muxerStarted = false
    private var inputPtsUs = 0L
    private val bufferInfo = MediaCodec.BufferInfo()

    private fun ensureEncoder(sampleRate: Int) {
        if (encoder != null) return
        this.sampleRate = sampleRate
        if (outFile.exists()) outFile.delete()
        val mime = MediaFormat.MIMETYPE_AUDIO_AAC
        val enc = MediaCodec.createEncoderByType(mime)
        val format = MediaFormat.createAudioFormat(mime, sampleRate, 1)
        format.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
        format.setInteger(MediaFormat.KEY_BIT_RATE, AppEditorConstants.TTS_EXPORT_AAC_BITRATE)
        format.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 32 * 1024)
        enc.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        enc.start()
        encoder = enc
        muxer = MediaMuxer(outFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
    }

    private fun drainEncoder(endOfStream: Boolean) {
        val enc = encoder ?: return
        val mux = muxer ?: return
        val timeoutUs = if (endOfStream) 50_000L else 0L
        while (true) {
            val outIndex = enc.dequeueOutputBuffer(bufferInfo, timeoutUs)
            when {
                outIndex == MediaCodec.INFO_TRY_AGAIN_LATER -> break
                outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    if (!muxerStarted) {
                        val fmt = enc.outputFormat
                        trackIndex = mux.addTrack(fmt)
                        mux.start()
                        muxerStarted = true
                    }
                }
                outIndex >= 0 -> {
                    val outBuf = enc.getOutputBuffer(outIndex)
                    if (outBuf == null) {
                        enc.releaseOutputBuffer(outIndex, false)
                    } else {
                        if (bufferInfo.size > 0 && muxerStarted) {
                            outBuf.position(bufferInfo.offset)
                            outBuf.limit(bufferInfo.offset + bufferInfo.size)
                            mux.writeSampleData(trackIndex, outBuf, bufferInfo)
                        }
                        enc.releaseOutputBuffer(outIndex, false)
                        if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                            return
                        }
                    }
                }
            }
        }
    }

    /** Nối PCM 16-bit LE từ một file WAV chuẩn (mono) vào luồng AAC. */
    fun appendFromWavFile(wav: File) {
        val header = readWavHeaderBytesFromFile(wav)
        val (sr, bits) = wavSampleRateBitsFromHeader(header)
        require(bits == 16) { "Chỉ hỗ trợ PCM 16-bit" }
        ensureEncoder(sr)
        require(sr == sampleRate) { "Sample rate khác đoạn trước ($sampleRate vs $sr)" }
        val enc = encoder ?: error("encoder null")
        FileInputStream(wav).use { fis ->
            fis.skip(WAV_HEADER_BYTES.toLong())
            val buf = ByteArray(8192)
            while (true) {
                val r = fis.read(buf)
                if (r <= 0) break
                feedPcmChunk(enc, buf, r)
            }
        }
        drainEncoder(false)
    }

    private fun feedPcmChunk(enc: MediaCodec, pcm: ByteArray, length: Int) {
        var offset = 0
        var remaining = length - (length % 2)
        while (remaining > 0) {
            val inIndex = enc.dequeueInputBuffer(50_000)
            if (inIndex < 0) {
                drainEncoder(false)
                continue
            }
            val inBuf = enc.getInputBuffer(inIndex)
            if (inBuf == null) {
                enc.queueInputBuffer(inIndex, 0, 0, inputPtsUs, 0)
                continue
            }
            inBuf.clear()
            val space = inBuf.remaining()
            val n = min(remaining, space - (space % 2))
            if (n <= 0) {
                enc.queueInputBuffer(inIndex, 0, 0, inputPtsUs, 0)
                drainEncoder(false)
                continue
            }
            inBuf.put(pcm, offset, n)
            val samples = n / 2
            enc.queueInputBuffer(inIndex, 0, n, inputPtsUs, 0)
            inputPtsUs += (samples * 1_000_000L) / sampleRate
            offset += n
            remaining -= n
            drainEncoder(false)
        }
    }

    fun finish() {
        val enc = encoder ?: return
        var tries = 0
        while (tries < 200) {
            val inIndex = enc.dequeueInputBuffer(50_000)
            if (inIndex >= 0) {
                enc.queueInputBuffer(
                    inIndex,
                    0,
                    0,
                    inputPtsUs,
                    MediaCodec.BUFFER_FLAG_END_OF_STREAM,
                )
                break
            }
            drainEncoder(false)
            tries++
        }
        drainEncoder(true)
        while (true) {
            val outIndex = enc.dequeueOutputBuffer(bufferInfo, 50_000)
            if (outIndex == MediaCodec.INFO_TRY_AGAIN_LATER) break
            if (outIndex >= 0) {
                val outBuf = enc.getOutputBuffer(outIndex)
                if (outBuf != null && bufferInfo.size > 0 && muxerStarted) {
                    outBuf.position(bufferInfo.offset)
                    outBuf.limit(bufferInfo.offset + bufferInfo.size)
                    muxer?.writeSampleData(trackIndex, outBuf, bufferInfo)
                }
                enc.releaseOutputBuffer(outIndex, false)
                if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) break
            }
        }
        runCatching {
            enc.stop()
            enc.release()
        }
        encoder = null
        runCatching {
            muxer?.stop()
            muxer?.release()
        }
        muxer = null
    }

    fun releaseQuietly() {
        runCatching { encoder?.stop(); encoder?.release() }
        encoder = null
        runCatching { muxer?.stop(); muxer?.release() }
        muxer = null
        runCatching { outFile.delete() }
    }
}
