@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.ttsaistory.app.voxstudio

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import com.ttsaistory.app.BuildConfig
import java.io.File
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

/**
 * Tổng hợp từng câu/đoạn qua VoxStudio rồi phát bằng MediaPlayer.
 *
 * Thuật toán đệm:
 * - **Sliding-window prefetch**: giữ sẵn [PREFETCH_INITIAL] đoạn phía trước
 *   (mặc định 3, tối đa [PREFETCH_MAX]); khi đoạn hiện tại bắt đầu phát thì
 *   lấp thêm đoạn mới vào cuối cửa sổ. Server luôn có việc để chạy song song
 *   trong lúc tai đang nghe đoạn trước.
 * - **Tự tăng cửa sổ khi thiếu hàng**: nếu tới lúc cần phát mà audio đoạn kế
 *   chưa xong, tăng cửa sổ lên (tối đa 5) cho các đoạn sau.
 * - **Preload MediaPlayer**: trong lúc đoạn i đang phát, đoạn i+1 được tải và
 *   `prepare()` sẵn; khi i kết thúc chỉ cần `start()` — giảm khoảng lặng
 *   chuyển đoạn xuống mức gần như tức thời.
 */
object VoxStudioTtsPlayback {
    private const val TAG = "VoxStudioPlayback"
    private const val PREFETCH_INITIAL = 3
    private const val PREFETCH_START_READY = 2
    private const val PREFETCH_MAX = 5
    private const val CACHE_MAX_AGE_MS = 60 * 60 * 1000L

    private class PreparedAudio(val file: File, val player: MediaPlayer)

    suspend fun playSequence(
        context: Context,
        paragraphs: List<String>,
        startIndex: Int,
        config: VoxStudioPrefKeys.Config,
        onIndex: (Int) -> Unit,
        onPreparing: (Boolean) -> Unit,
        onFinished: () -> Unit,
    ) = supervisorScope {
        val sanitized = paragraphs.map { it.trim() }
        var from = startIndex.coerceIn(0, sanitized.lastIndex.coerceAtLeast(0))
        while (from <= sanitized.lastIndex && sanitized[from].isEmpty()) {
            from++
        }
        val items =
            if (from > sanitized.lastIndex) {
                emptyList()
            } else {
                sanitized.indices
                    .filter { it >= from && sanitized[it].isNotEmpty() }
                    .map { it to sanitized[it] }
            }
        if (items.isEmpty()) {
            withContext(Dispatchers.Main + NonCancellable) { onFinished() }
            return@supervisorScope
        }

        val cacheDir = File(context.cacheDir, "voxstudio_tts").apply { mkdirs() }
        purgeStaleCache(cacheDir)

        val prefetched = HashMap<Int, Deferred<File>>()
        var launchCursor = 0
        var ahead = PREFETCH_INITIAL

        fun launchThrough(lastIndex: Int) {
            val end = lastIndex.coerceAtMost(items.lastIndex)
            while (launchCursor <= end) {
                val k = launchCursor++
                prefetched[k] =
                    async(Dispatchers.IO) {
                        synthesizeToFile(context, cacheDir, config, items[k].second)
                    }
            }
        }

        // Khởi động sớm cả cửa sổ: server bắt đầu xử lý trước khi tai nghe câu đầu.
        launchThrough(ahead)

        var current: PreparedAudio? = null

        // Pre-buffer trước khi phát: chờ sẵn 2 đoạn đầu để không hở khoảng lặng
        // ngay giữa câu 1 → câu 2 (server thường cần vài giây cho job đầu).
        withContext(Dispatchers.Main + NonCancellable) { onPreparing(true) }
        try {
            val prebufferStart = SystemClock.elapsedRealtime()
            val startReady = minOf(PREFETCH_START_READY, items.size)
            val firstFile =
                prefetched[0]?.await()
                    ?: throw IllegalStateException("Chưa tạo được audio đoạn đầu.")
            current = prepareAudio(context, firstFile)
            for (k in 1 until startReady) {
                prefetched[k]?.await()
            }
            if (BuildConfig.DEBUG) {
                Log.d(
                    TAG,
                    "prebuffered ready=$startReady inMs=" +
                        "${SystemClock.elapsedRealtime() - prebufferStart}",
                )
            }
        } finally {
            withContext(Dispatchers.Main + NonCancellable) { onPreparing(false) }
        }
        var preparedNext: PreparedAudio? = null
        var prepareJob: Job? = null
        var prepareError: Exception? = null
        var lastEndAt = 0L

        try {
            for (i in items.indices) {
                coroutineContext.ensureActive()
                val (origIndex, _) = items[i]
                val deferred =
                    prefetched[i]
                        ?: async(Dispatchers.IO) {
                            synthesizeToFile(context, cacheDir, config, items[i].second)
                        }.also { prefetched[i] = it }

                val wasReady = deferred.isCompleted
                if (!wasReady) {
                    withContext(Dispatchers.Main + NonCancellable) { onPreparing(true) }
                }
                val file =
                    try {
                        deferred.await()
                    } finally {
                        if (!wasReady) {
                            withContext(Dispatchers.Main + NonCancellable) {
                                onPreparing(false)
                            }
                        }
                    }
                prefetched.remove(i)

                if (i > 0 && !wasReady && ahead < PREFETCH_MAX) {
                    ahead++
                    if (BuildConfig.DEBUG) {
                        Log.d(TAG, "underrun at i=$i -> ahead=$ahead")
                    }
                }
                // Bù vào cuối cửa sổ ngay khi đoạn hiện tại đã sẵn sàng.
                launchThrough(i + ahead)

                val prepared = current ?: prepareAudio(context, file)
                current = prepared

                // Chuẩn bị decoder cho đoạn kế trong lúc đoạn này đang phát.
                preparedNext = null
                prepareError = null
                if (i + 1 < items.size) {
                    val nextDeferred = prefetched[i + 1]
                    if (nextDeferred != null) {
                        prepareJob =
                            launch(Dispatchers.Main) {
                                try {
                                    val nextFile = nextDeferred.await()
                                    val nextPlayer = createPlayer(context, nextFile)
                                    if (isActive) {
                                        preparedNext = PreparedAudio(nextFile, nextPlayer)
                                    } else {
                                        runCatching { nextPlayer.release() }
                                    }
                                } catch (e: CancellationException) {
                                    throw e
                                } catch (e: Exception) {
                                    prepareError = e
                                }
                            }
                    }
                }

                val startAt = SystemClock.elapsedRealtime()
                if (BuildConfig.DEBUG) {
                    val gap = if (lastEndAt == 0L) -1L else startAt - lastEndAt
                    Log.d(TAG, "start i=$i gapMs=$gap ahead=$ahead")
                }
                withContext(Dispatchers.Main) { onIndex(origIndex) }
                playPrepared(prepared.player)
                lastEndAt = SystemClock.elapsedRealtime()
                if (BuildConfig.DEBUG) {
                    Log.d(TAG, "end i=$i playedMs=${lastEndAt - startAt}")
                }

                prepared.player.release()
                current = null
                withContext(Dispatchers.IO) { file.delete() }

                prepareJob?.join()
                prepareJob = null
                prepareError?.let { throw it }

                val next = preparedNext
                preparedNext = null
                if (next != null) {
                    current = next
                } else if (i + 1 < items.size) {
                    // Hiếm: preload fail/cancel — chuẩn bị ngay tại chỗ.
                    val nextFile =
                        prefetched[i + 1]?.await()
                            ?: throw IllegalStateException("Thiếu audio đoạn kế.")
                    current = prepareAudio(context, nextFile)
                }
            }
        } finally {
            prepareJob?.cancel()
            prefetched.values.forEach { d ->
                d.cancel()
                runCatching { d.getCompleted().delete() }
            }
            runCatching { preparedNext?.player?.release() }
            preparedNext?.file?.delete()
            runCatching { current?.player?.release() }
            current?.file?.delete()
            withContext(Dispatchers.Main + NonCancellable) { onFinished() }
        }
    }

    /** Tổng hợp + phát một câu mẫu (nút «Nghe thử» trong cài đặt). */
    suspend fun playSample(
        context: Context,
        config: VoxStudioPrefKeys.Config,
        text: String,
    ) {
        val cacheDir = File(context.cacheDir, "voxstudio_tts").apply { mkdirs() }
        val file = synthesizeToFile(context, cacheDir, config, text)
        val prepared = prepareAudio(context, file)
        try {
            playPrepared(prepared.player)
        } finally {
            runCatching { prepared.player.release() }
            withContext(Dispatchers.IO) { file.delete() }
        }
    }

    private suspend fun synthesizeToFile(
        context: Context,
        cacheDir: File,
        config: VoxStudioPrefKeys.Config,
        text: String,
    ): File {
        val job =
            withContext(Dispatchers.IO) {
                VoxStudioApi.createJob(
                    baseUrl = config.baseUrl,
                    token = config.token,
                    text = text,
                    engine = config.engine,
                    voice = config.voice,
                    ratePct = config.ratePct,
                    volumePct = config.volumePct,
                )
            }
        val finished = awaitJob(config, job.id)
        return withContext(Dispatchers.IO) {
            VoxStudioApi.downloadAudio(
                baseUrl = config.baseUrl,
                token = config.token,
                jobId = finished.id,
                format = finished.format,
                destDir = cacheDir,
            )
        }
    }

    private suspend fun awaitJob(
        config: VoxStudioPrefKeys.Config,
        jobId: String,
    ): VoxJob {
        val deadline = SystemClock.elapsedRealtime() + VoxStudioConfig.JOB_TIMEOUT_MS
        var delayMs = 700L
        while (true) {
            coroutineContext.ensureActive()
            val job =
                withContext(Dispatchers.IO) {
                    VoxStudioApi.fetchJob(config.baseUrl, config.token, jobId)
                }
            when (job.status) {
                "done" -> return job
                "error", "canceled" ->
                    throw IllegalStateException(
                        job.error
                            ?: if (job.status == "canceled") {
                                "Job VoxStudio đã bị huỷ."
                            } else {
                                "VoxStudio tổng hợp thất bại."
                            },
                    )
            }
            if (SystemClock.elapsedRealtime() > deadline) {
                throw IllegalStateException(
                    "Hết thời gian chờ VoxStudio " +
                        "(${VoxStudioConfig.JOB_TIMEOUT_MS / 60_000} phút).",
                )
            }
            delay(delayMs)
            delayMs = (delayMs + 500L).coerceAtMost(1_500L)
        }
    }

    private suspend fun prepareAudio(context: Context, file: File): PreparedAudio =
        withContext(Dispatchers.Main) {
            PreparedAudio(file, createPlayer(context, file))
        }

    private fun createPlayer(appContext: Context, file: File): MediaPlayer {
        val player = MediaPlayer()
        try {
            player.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build(),
            )
            @Suppress("DEPRECATION")
            player.setWakeMode(
                appContext.applicationContext,
                PowerManager.PARTIAL_WAKE_LOCK,
            )
            player.setDataSource(file.absolutePath)
            player.prepare()
            return player
        } catch (e: Exception) {
            runCatching { player.release() }
            throw e
        }
    }

    private suspend fun playPrepared(player: MediaPlayer) =
        withContext(Dispatchers.Main) {
            suspendCancellableCoroutine { cont ->
                player.setOnCompletionListener {
                    if (cont.isActive) cont.resume(Unit) {}
                }
                player.setOnErrorListener { _, what, extra ->
                    if (cont.isActive) {
                        cont.resumeWith(
                            Result.failure(
                                IllegalStateException("MediaPlayer lỗi $what/$extra"),
                            ),
                        )
                    }
                    true
                }
                try {
                    player.start()
                } catch (e: Exception) {
                    if (cont.isActive) cont.resumeWith(Result.failure(e))
                    return@suspendCancellableCoroutine
                }
                cont.invokeOnCancellation {
                    runCatching { player.stop() }
                }
            }
        }

    private fun purgeStaleCache(cacheDir: File) {
        val cutoff = System.currentTimeMillis() - CACHE_MAX_AGE_MS
        runCatching {
            cacheDir.listFiles()?.forEach { f ->
                if (f.isFile && f.lastModified() < cutoff) f.delete()
            }
        }
    }
}
