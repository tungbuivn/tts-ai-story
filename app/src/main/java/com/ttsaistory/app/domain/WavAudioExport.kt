package com.ttsaistory.app.domain

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.EOFException
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream

const val WAV_EXPORT_FOLDER_SEGMENT = "tts-ai-story"

const val WAV_HEADER_BYTES = 44

const val WAV_STREAM_BUFFER = 64 * 1024

fun putLeInt(buf: ByteArray, offset: Int, v: Int) {
    buf[offset] = (v and 0xFF).toByte()
    buf[offset + 1] = ((v shr 8) and 0xFF).toByte()
    buf[offset + 2] = ((v shr 16) and 0xFF).toByte()
    buf[offset + 3] = ((v shr 24) and 0xFF).toByte()
}

fun putLeShort(buf: ByteArray, offset: Int, v: Short) {
    val x = v.toInt() and 0xFFFF
    buf[offset] = (x and 0xFF).toByte()
    buf[offset + 1] = ((x shr 8) and 0xFF).toByte()
}

fun buildWavHeaderPcm16Mono(dataSize: Int, sampleRate: Int): ByteArray {
    val channels = 1
    val bitsPerSample = 16
    val blockAlign = channels * bitsPerSample / 8
    val byteRate = sampleRate * blockAlign
    val chunkSize = 36 + dataSize
    val header = ByteArray(44)
    "RIFF".encodeToByteArray().copyInto(header, destinationOffset = 0)
    putLeInt(header, 4, chunkSize)
    "WAVE".encodeToByteArray().copyInto(header, 8)
    "fmt ".encodeToByteArray().copyInto(header, 12)
    putLeInt(header, 16, 16)
    putLeShort(header, 20, 1)
    putLeShort(header, 22, channels.toShort())
    putLeInt(header, 24, sampleRate)
    putLeInt(header, 28, byteRate)
    putLeShort(header, 32, blockAlign.toShort())
    putLeShort(header, 34, bitsPerSample.toShort())
    "data".encodeToByteArray().copyInto(header, 36)
    putLeInt(header, 40, dataSize)
    return header
}

fun writeMonoPcmWavToStream(
    pcm: ByteArray,
    sampleRate: Int,
    bitsPerSample: Int,
    stream: OutputStream,
) {
    require(bitsPerSample == 16)
    val header = buildWavHeaderPcm16Mono(pcm.size, sampleRate)
    stream.write(header)
    stream.write(pcm)
}

fun writeMonoPcmWavFile(pcm: ByteArray, sampleRate: Int, bitsPerSample: Int, out: File) {
    FileOutputStream(out).use { fos ->
        writeMonoPcmWavToStream(pcm, sampleRate, bitsPerSample, fos)
    }
}

/**
 * Ghi WAV vào [Environment.DIRECTORY_MUSIC]/[WAV_EXPORT_FOLDER_SEGMENT]/[fileName].
 * API 29+: [MediaStore.Audio.Media] + RELATIVE_PATH.
 */
fun exportWavToMusicTtsAiStory(
    context: Context,
    fileName: String,
    pcm: ByteArray,
    sampleRate: Int,
    bitsPerSample: Int,
): String {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val resolver = context.contentResolver
        val values =
            ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, "audio/wav")
                put(
                    MediaStore.MediaColumns.RELATIVE_PATH,
                    "${Environment.DIRECTORY_MUSIC}/$WAV_EXPORT_FOLDER_SEGMENT",
                )
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
        val collection =
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val uri =
            resolver.insert(collection, values)
                ?: error("Không tạo được file trong Music/$WAV_EXPORT_FOLDER_SEGMENT")
        try {
            resolver.openOutputStream(uri)?.use { os ->
                writeMonoPcmWavToStream(pcm, sampleRate, bitsPerSample, os)
            } ?: error("Không mở được luồng ghi file")
        } catch (e: Exception) {
            runCatching { resolver.delete(uri, null, null) }
            throw e
        }
        values.clear()
        values.put(MediaStore.MediaColumns.IS_PENDING, 0)
        resolver.update(uri, values, null, null)
        return "${Environment.DIRECTORY_MUSIC}/$WAV_EXPORT_FOLDER_SEGMENT/$fileName"
    }
    val base = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)
    val dir = File(base, WAV_EXPORT_FOLDER_SEGMENT)
    if (!dir.exists() && !dir.mkdirs()) {
        error("Không tạo được thư mục ${dir.absolutePath}")
    }
    val outFile = File(dir, fileName)
    writeMonoPcmWavFile(pcm, sampleRate, bitsPerSample, outFile)
    return outFile.absolutePath
}

fun exportWavToMusicTtsAiStoryFromChunkFiles(
    context: Context,
    fileName: String,
    chunkFiles: List<File>,
): String {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val resolver = context.contentResolver
        val values =
            ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, "audio/wav")
                put(
                    MediaStore.MediaColumns.RELATIVE_PATH,
                    "${Environment.DIRECTORY_MUSIC}/$WAV_EXPORT_FOLDER_SEGMENT",
                )
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
        val collection =
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val uri =
            resolver.insert(collection, values)
                ?: error("Không tạo được file trong Music/$WAV_EXPORT_FOLDER_SEGMENT")
        try {
            resolver.openOutputStream(uri)?.use { os ->
                mergeWavChunkFilesToOutputStream(chunkFiles, os)
            } ?: error("Không mở được luồng ghi file")
        } catch (e: Exception) {
            runCatching { resolver.delete(uri, null, null) }
            throw e
        }
        values.clear()
        values.put(MediaStore.MediaColumns.IS_PENDING, 0)
        resolver.update(uri, values, null, null)
        return "${Environment.DIRECTORY_MUSIC}/$WAV_EXPORT_FOLDER_SEGMENT/$fileName"
    }
    val base = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)
    val dir = File(base, WAV_EXPORT_FOLDER_SEGMENT)
    if (!dir.exists() && !dir.mkdirs()) {
        error("Không tạo được thư mục ${dir.absolutePath}")
    }
    val outFile = File(dir, fileName)
    FileOutputStream(outFile).use { fos ->
        mergeWavChunkFilesToOutputStream(chunkFiles, fos)
    }
    return outFile.absolutePath
}

/** Ghi file .m4a (AAC trong MP4) vào Music/[WAV_EXPORT_FOLDER_SEGMENT]/[fileName]. */
fun exportM4aToMusicTtsAiStory(
    context: Context,
    fileName: String,
    sourceFile: File,
): String {
    if (!sourceFile.isFile) error("File nguồn không tồn tại: ${sourceFile.absolutePath}")
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val resolver = context.contentResolver
        val values =
            ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, "audio/mp4")
                put(
                    MediaStore.MediaColumns.RELATIVE_PATH,
                    "${Environment.DIRECTORY_MUSIC}/$WAV_EXPORT_FOLDER_SEGMENT",
                )
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
        val collection =
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val uri =
            resolver.insert(collection, values)
                ?: error("Không tạo được file trong Music/$WAV_EXPORT_FOLDER_SEGMENT")
        try {
            resolver.openOutputStream(uri)?.use { os ->
                BufferedInputStream(FileInputStream(sourceFile), WAV_STREAM_BUFFER).use { bis ->
                    val buf = ByteArray(WAV_STREAM_BUFFER)
                    while (true) {
                        val r = bis.read(buf)
                        if (r <= 0) break
                        os.write(buf, 0, r)
                    }
                }
            } ?: error("Không mở được luồng ghi file")
        } catch (e: Exception) {
            runCatching { resolver.delete(uri, null, null) }
            throw e
        }
        values.clear()
        values.put(MediaStore.MediaColumns.IS_PENDING, 0)
        resolver.update(uri, values, null, null)
        return "${Environment.DIRECTORY_MUSIC}/$WAV_EXPORT_FOLDER_SEGMENT/$fileName"
    }
    val base = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)
    val dir = File(base, WAV_EXPORT_FOLDER_SEGMENT)
    if (!dir.exists() && !dir.mkdirs()) {
        error("Không tạo được thư mục ${dir.absolutePath}")
    }
    val outFile = File(dir, fileName)
    BufferedInputStream(FileInputStream(sourceFile), WAV_STREAM_BUFFER).use { bis ->
        FileOutputStream(outFile).use { fos ->
            val buf = ByteArray(WAV_STREAM_BUFFER)
            while (true) {
                val r = bis.read(buf)
                if (r <= 0) break
                fos.write(buf, 0, r)
            }
        }
    }
    return outFile.absolutePath
}

fun readLeInt(b: ByteArray, o: Int): Int =
    (b[o].toInt() and 0xFF) or
        ((b[o + 1].toInt() and 0xFF) shl 8) or
        ((b[o + 2].toInt() and 0xFF) shl 16) or
        ((b[o + 3].toInt() and 0xFF) shl 24)

fun readLeShort(b: ByteArray, o: Int): Short =
    ((b[o].toInt() and 0xFF) or ((b[o + 1].toInt() and 0xFF) shl 8)).toShort()

fun assertStandardTtsWavHeader(header: ByteArray) {
    require(header.size >= WAV_HEADER_BYTES) { "File WAV quá ngắn" }
    require(header.copyOfRange(0, 4).contentEquals("RIFF".encodeToByteArray())) { "Không phải WAV RIFF" }
    require(header.copyOfRange(8, 12).contentEquals("WAVE".encodeToByteArray()))
    require(header.copyOfRange(12, 16).contentEquals("fmt ".encodeToByteArray()))
    require(header.copyOfRange(36, 40).contentEquals("data".encodeToByteArray()))
}

fun wavSampleRateBitsFromHeader(header: ByteArray): Pair<Int, Int> {
    assertStandardTtsWavHeader(header)
    val sr = readLeInt(header, 24)
    val bits = readLeShort(header, 34).toInt() and 0xFFFF
    return sr to bits
}

fun readWavHeaderBytesFromFile(f: File): ByteArray {
    FileInputStream(f).use { fis ->
        val h = ByteArray(WAV_HEADER_BYTES)
        var o = 0
        while (o < WAV_HEADER_BYTES) {
            val r = fis.read(h, o, WAV_HEADER_BYTES - o)
            if (r <= 0) throw IllegalStateException("WAV fragment quá ngắn: ${f.name}")
            o += r
        }
        return h
    }
}

fun InputStream.discardExactly(byteCount: Int) {
    var left = byteCount
    val buf = ByteArray(8192)
    while (left > 0) {
        val r = read(buf, 0, minOf(buf.size, left))
        if (r <= 0) throw EOFException()
        left -= r
    }
}

fun mergeWavChunkFilesToOutputStream(chunkFiles: List<File>, out: OutputStream) {
    require(chunkFiles.isNotEmpty())
    val h0 = readWavHeaderBytesFromFile(chunkFiles.first())
    val (sr0, bits0) = wavSampleRateBitsFromHeader(h0)
    require(bits0 == 16) { "Chỉ hỗ trợ xuất PCM 16-bit ($bits0)" }
    var totalPcm = 0L
    for ((idx, f) in chunkFiles.withIndex()) {
        val len = f.length()
        require(len >= WAV_HEADER_BYTES) { "File WAV không hợp lệ: ${f.absolutePath}" }
        if (idx > 0) {
            val hi = readWavHeaderBytesFromFile(f)
            val (sri, bitsi) = wavSampleRateBitsFromHeader(hi)
            require(sri == sr0 && bitsi == bits0) {
                "Các đoạn WAV khác sample rate hoặc bit depth ($sr0/$bits0 vs $sri/$bitsi)"
            }
        }
        totalPcm += len - WAV_HEADER_BYTES
    }
    if (totalPcm > Int.MAX_VALUE - WAV_HEADER_BYTES) {
        throw IllegalStateException("Tổng PCM vượt giới hạn WAV (~2GB)")
    }
    val headerOut = buildWavHeaderPcm16Mono(totalPcm.toInt(), sr0)
    BufferedOutputStream(out, WAV_STREAM_BUFFER).use { bos ->
        bos.write(headerOut)
        val pcmBuf = ByteArray(WAV_STREAM_BUFFER)
        for (f in chunkFiles) {
            BufferedInputStream(FileInputStream(f), WAV_STREAM_BUFFER).use { bis ->
                bis.discardExactly(WAV_HEADER_BYTES)
                while (true) {
                    val r = bis.read(pcmBuf)
                    if (r <= 0) break
                    bos.write(pcmBuf, 0, r)
                }
            }
        }
        bos.flush()
    }
}
