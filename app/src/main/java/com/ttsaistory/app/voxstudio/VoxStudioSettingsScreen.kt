package com.ttsaistory.app.voxstudio

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import java.io.ByteArrayOutputStream
import java.io.InputStream
import kotlin.math.roundToInt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class PendingAudio(
    val bytes: ByteArray,
    val fileName: String,
    val mimeType: String,
    val sizeBytes: Int,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoxStudioSettingsScreen(
    modifier: Modifier = Modifier,
    prefs: SharedPreferences,
    onClose: () -> Unit,
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    var baseUrlInput by remember { mutableStateOf(VoxStudioPrefKeys.resolveBaseUrl(prefs)) }
    var tokenInput by remember { mutableStateOf(VoxStudioPrefKeys.resolveToken(prefs)) }
    var showToken by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var catalog by remember { mutableStateOf<VoxCatalog?>(null) }
    var selectedEngine by remember { mutableStateOf(VoxStudioPrefKeys.resolveEngine(prefs)) }
    var selectedVoice by remember { mutableStateOf(VoxStudioPrefKeys.resolveVoice(prefs)) }
    var ratePct by remember { mutableIntStateOf(VoxStudioPrefKeys.resolveRatePct(prefs)) }
    var volumePct by remember { mutableIntStateOf(VoxStudioPrefKeys.resolveVolumePct(prefs)) }

    var sampleJob by remember { mutableStateOf<Job?>(null) }
    var sampleBusy by remember { mutableStateOf(false) }

    var pendingAudio by remember { mutableStateOf<PendingAudio?>(null) }
    var showAddVoice by remember { mutableStateOf(false) }
    var newVoiceName by remember { mutableStateOf("") }
    var newVoiceRefText by remember { mutableStateOf("") }
    var uploadBusy by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<VoxVoice?>(null) }
    var deleteBusy by remember { mutableStateOf(false) }

    suspend fun reloadCatalog() {
        val token = tokenInput.trim()
        Log.i(
            "VoxStudioSettings",
            "reloadCatalog base=${VoxStudioApi.normalizeBaseUrl(baseUrlInput)} tokenLen=${token.length}",
        )
        if (token.isEmpty()) {
            Log.i("VoxStudioSettings", "reload skipped: empty token")
            catalog = null
            error = "Nhập API token để tải danh sách engine/giọng."
            return
        }
        val base = VoxStudioApi.normalizeBaseUrl(baseUrlInput)
        loading = true
        error = null
        try {
            Log.i("VoxStudioSettings", "fetchCatalog start")
            val c = withContext(Dispatchers.IO) { VoxStudioApi.fetchCatalog(base, token) }
            Log.i("VoxStudioSettings", "fetchCatalog ok engines=${c.engines.size}")
            catalog = c
            val available = c.engines.filter { it.available }
            val engine =
                available.firstOrNull { it.id == selectedEngine }
                    ?: available.firstOrNull { it.id == c.defaultEngine }
                    ?: available.firstOrNull()
            if (engine == null) {
                error = "Server chưa có engine nào sẵn sàng."
                return
            }
            if (selectedEngine != engine.id) {
                selectedEngine = engine.id
                VoxStudioPrefKeys.saveEngine(prefs, engine.id)
            }
            val voiceIds = engine.voices.map { it.id }
            if (selectedVoice.isBlank() || selectedVoice !in voiceIds) {
                val preferred =
                    if (engine.id == c.defaultEngine && c.defaultVoice in voiceIds) {
                        c.defaultVoice
                    } else {
                        engine.voices.firstOrNull()?.id.orEmpty()
                    }
                if (selectedVoice != preferred) {
                    selectedVoice = preferred
                    VoxStudioPrefKeys.saveVoice(prefs, preferred)
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w("VoxStudioSettings", "fetchCatalog failed", e)
            catalog = null
            error = voxConnectionError(e)
        } finally {
            loading = false
        }
    }

    fun saveConnectionAndReload() {
        val normalized = VoxStudioApi.normalizeBaseUrl(baseUrlInput)
        val token = tokenInput.trim()
        baseUrlInput = normalized
        tokenInput = token
        VoxStudioPrefKeys.saveConnection(prefs, normalized, token)
        scope.launch { reloadCatalog() }
    }

    fun selectEngine(engine: VoxEngine) {
        selectedEngine = engine.id
        VoxStudioPrefKeys.saveEngine(prefs, engine.id)
        if (engine.voices.none { it.id == selectedVoice }) {
            selectedVoice = engine.voices.firstOrNull()?.id.orEmpty()
            VoxStudioPrefKeys.saveVoice(prefs, selectedVoice)
        }
    }

    fun selectVoice(voiceId: String) {
        selectedVoice = voiceId
        VoxStudioPrefKeys.saveVoice(prefs, voiceId)
    }

    fun stopSample() {
        sampleJob?.cancel()
        sampleJob = null
        sampleBusy = false
    }

    fun playSample() {
        if (sampleBusy) {
            stopSample()
            return
        }
        val config = VoxStudioPrefKeys.resolveConfig(prefs)
        if (config.token.isEmpty()) {
            error = "Chưa có API token VoxStudio."
            return
        }
        lateinit var ref: Job
        ref =
            scope.launch {
                sampleBusy = true
                try {
                    VoxStudioTtsPlayback.playSample(
                        context = ctx.applicationContext,
                        config = config,
                        text = VoxStudioConfig.SAMPLE_TEXT,
                    )
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Toast.makeText(
                        ctx,
                        e.message ?: "Không phát được giọng mẫu.",
                        Toast.LENGTH_LONG,
                    ).show()
                } finally {
                    if (sampleJob === ref) {
                        sampleBusy = false
                        sampleJob = null
                    }
                }
            }
        sampleJob = ref
    }

    val pickAudioLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri == null) return@rememberLauncherForActivityResult
            scope.launch {
                try {
                    val pending =
                        withContext(Dispatchers.IO) { readPendingAudio(ctx, uri) }
                    pendingAudio = pending
                    newVoiceName = ""
                    newVoiceRefText = ""
                    showAddVoice = true
                } catch (e: Exception) {
                    Toast.makeText(
                        ctx,
                        e.message ?: "Không đọc được file âm thanh.",
                        Toast.LENGTH_LONG,
                    ).show()
                }
            }
        }

    fun uploadPendingVoice() {
        val pending = pendingAudio ?: return
        val name = newVoiceName.trim()
        if (name.length !in 2..60) {
            Toast.makeText(ctx, "Tên giọng phải từ 2 đến 60 ký tự.", Toast.LENGTH_SHORT).show()
            return
        }
        val base = VoxStudioApi.normalizeBaseUrl(baseUrlInput)
        val token = tokenInput.trim()
        if (token.isEmpty()) {
            Toast.makeText(ctx, "Chưa có API token VoxStudio.", Toast.LENGTH_SHORT).show()
            return
        }
        uploadBusy = true
        scope.launch {
            try {
                val created =
                    withContext(Dispatchers.IO) {
                        VoxStudioApi.uploadVoice(
                            baseUrl = base,
                            token = token,
                            name = name,
                            refText = newVoiceRefText,
                            fileName = pending.fileName,
                            mimeType = pending.mimeType,
                            bytes = pending.bytes,
                        )
                    }
                VoxStudioPrefKeys.saveEngine(prefs, "vieneu")
                VoxStudioPrefKeys.saveVoice(prefs, created.id)
                selectedEngine = "vieneu"
                selectedVoice = created.id
                showAddVoice = false
                pendingAudio = null
                Toast.makeText(
                    ctx,
                    "Đã thêm giọng đọc «${created.name}»",
                    Toast.LENGTH_LONG,
                ).show()
                reloadCatalog()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = voxConnectionError(e)
                Toast.makeText(ctx, error, Toast.LENGTH_LONG).show()
            } finally {
                uploadBusy = false
            }
        }
    }

    fun deleteVoice(v: VoxVoice) {
        val base = VoxStudioApi.normalizeBaseUrl(baseUrlInput)
        val token = tokenInput.trim()
        if (token.isEmpty()) return
        deleteBusy = true
        scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    VoxStudioApi.deleteVoice(base, token, v.id)
                }
                if (selectedVoice == v.id) {
                    selectedVoice = ""
                    VoxStudioPrefKeys.saveVoice(prefs, "")
                }
                deleteTarget = null
                Toast.makeText(ctx, "Đã xoá giọng «${v.name}»", Toast.LENGTH_SHORT).show()
                reloadCatalog()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = voxConnectionError(e)
                Toast.makeText(ctx, error, Toast.LENGTH_LONG).show()
            } finally {
                deleteBusy = false
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose { sampleJob?.cancel() }
    }

    LaunchedEffect(Unit) {
        if (VoxStudioPrefKeys.resolveToken(prefs).isNotEmpty()) {
            reloadCatalog()
        }
    }

    val engineList = catalog?.engines.orEmpty()
    val selectedEngineObj = engineList.firstOrNull { it.id == selectedEngine }
    val engineVoices = selectedEngineObj?.voices.orEmpty()
    val sortedVoices =
        remember(selectedEngine, engineVoices) {
            engineVoices.sortedWith(
                compareByDescending<VoxVoice> { it.isClone }
                    .thenBy { it.name.lowercase() },
            )
        }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Cấu hình VoxStudio") },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Đóng")
                    }
                },
                colors =
                    TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    ),
            )
        },
    ) { padding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp),
        ) {
            Card(
                colors =
                    CardDefaults.cardColors(
                        containerColor =
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                    ),
                modifier = Modifier.padding(bottom = 8.dp),
            ) {
                Column(Modifier.padding(12.dp)) {
                    Text("Kết nối server", style = MaterialTheme.typography.titleSmall)
                    OutlinedTextField(
                        value = baseUrlInput,
                        onValueChange = { baseUrlInput = it },
                        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                        label = { Text("Base URL") },
                        placeholder = { Text(VoxStudioConfig.DEFAULT_BASE_URL) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    )
                    OutlinedTextField(
                        value = tokenInput,
                        onValueChange = { tokenInput = it },
                        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                        label = { Text("API token (Bearer)") },
                        singleLine = true,
                        visualTransformation =
                            if (showToken) {
                                VisualTransformation.None
                            } else {
                                PasswordVisualTransformation()
                            },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        trailingIcon = {
                            IconButton(onClick = { showToken = !showToken }) {
                                Icon(
                                    imageVector =
                                        if (showToken) {
                                            Icons.Filled.VisibilityOff
                                        } else {
                                            Icons.Filled.Visibility
                                        },
                                    contentDescription = if (showToken) "Ẩn" else "Hiện",
                                )
                            }
                        },
                    )
                    Text(
                        "Tailscale: cần cài Tailscale cùng tài khoản và bật MagicDNS. " +
                            "Có thể dùng http://100.95.4.110 nếu chưa vào được HTTPS.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                    Row(
                        modifier = Modifier.padding(top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Button(
                            onClick = { saveConnectionAndReload() },
                            enabled = !loading && !uploadBusy,
                        ) {
                            Text("Lưu & tải danh sách")
                        }
                        if (loading) {
                            CircularProgressIndicator(modifier = Modifier.padding(4.dp))
                        }
                    }
                }
            }

            error?.let { message ->
                Text(
                    text = message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(bottom = 6.dp),
                )
            }

            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                contentPadding = PaddingValues(bottom = 24.dp),
            ) {
                item {
                    Text(
                        "Engine",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                if (engineList.isEmpty() && !loading) {
                    item {
                        Text(
                            "Chưa có dữ liệu — nhập token rồi bấm «Lưu & tải danh sách».",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                items(engineList, key = { "engine_${it.id}" }) { engine ->
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .clickable(enabled = engine.available) { selectEngine(engine) }
                                .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = selectedEngine == engine.id,
                            onClick = { if (engine.available) selectEngine(engine) },
                            enabled = engine.available,
                        )
                        Column(Modifier.weight(1f).padding(start = 4.dp)) {
                            Text(engine.label, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                buildString {
                                    append(engine.id)
                                    if (engine.requiresNetwork) append(" · cần mạng")
                                    if (engine.canClone) append(" · clone giọng")
                                    if (!engine.available) {
                                        append(" · ")
                                        append(engine.reason ?: "không sẵn sàng")
                                    }
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color =
                                    if (engine.available) {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    } else {
                                        MaterialTheme.colorScheme.error
                                    },
                            )
                        }
                    }
                }

                item {
                    Text(
                        "Giọng đọc",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }

                items(sortedVoices, key = { "voice_${it.id}" }) { voice ->
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .clickable { selectVoice(voice.id) }
                                .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = selectedVoice == voice.id,
                            onClick = { selectVoice(voice.id) },
                        )
                        Column(Modifier.weight(1f).padding(start = 4.dp)) {
                            Text(
                                if (voice.isClone) "🎙 ${voice.name}" else voice.name,
                                style = MaterialTheme.typography.bodyLarge,
                            )
                            Text(
                                buildString {
                                    append(voice.id)
                                    voice.lang?.let { append(" · ").append(it) }
                                    voice.note?.let { append(" · ").append(it) }
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (voice.isClone) {
                            IconButton(
                                onClick = { deleteTarget = voice },
                                enabled = !uploadBusy && !deleteBusy,
                            ) {
                                Icon(
                                    Icons.Filled.Delete,
                                    contentDescription = "Xoá giọng clone ${voice.name}",
                                )
                            }
                        }
                    }
                }

                item {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))
                    Text(
                        "Giọng clone của tôi",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        "Tải file mẫu 3–10 giây (tối đa 15 MB, 1–40 giây), một người nói, " +
                            "không nhạc/nhiễu. Giọng clone dùng engine VieNeu.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp, bottom = 6.dp),
                    )
                    OutlinedButton(
                        onClick = {
                            pickAudioLauncher.launch(
                                arrayOf("audio/*", "application/octet-stream"),
                            )
                        },
                        enabled = !uploadBusy && !deleteBusy,
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = null)
                        Text("Thêm giọng từ file…", modifier = Modifier.padding(start = 6.dp))
                    }
                }

                item {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))
                    Text("Tùy chỉnh giọng", style = MaterialTheme.typography.titleMedium)
                }
                item {
                    Text(
                        "Tốc độ: ${ratePct}%",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                    Slider(
                        value = ratePct.toFloat(),
                        onValueChange = { ratePct = it.roundToInt() },
                        valueRange = -50f..100f,
                        onValueChangeFinished = {
                            VoxStudioPrefKeys.saveRatePct(prefs, ratePct)
                        },
                    )
                    Text(
                        "Âm lượng: ${volumePct}%",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Slider(
                        value = volumePct.toFloat(),
                        onValueChange = { volumePct = it.roundToInt() },
                        valueRange = 0f..200f,
                        onValueChangeFinished = {
                            VoxStudioPrefKeys.saveVolumePct(prefs, volumePct)
                        },
                    )
                    OutlinedButton(
                        onClick = { playSample() },
                        enabled = !loading && tokenInput.isNotBlank(),
                        modifier = Modifier.padding(top = 4.dp),
                    ) {
                        Icon(
                            if (sampleBusy) Icons.Filled.Stop else Icons.Filled.PlayArrow,
                            contentDescription = null,
                        )
                        Text(
                            if (sampleBusy) "Dừng thử" else "Nghe thử giọng đang chọn",
                            modifier = Modifier.padding(start = 6.dp),
                        )
                    }
                    Text(
                        "Ghi chú: VieNeu bỏ qua cao độ; tốc độ/âm lượng do server xử lý.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }
        }
    }

    if (showAddVoice && pendingAudio != null) {
        val pending = pendingAudio!!
        AlertDialog(
            onDismissRequest = {
                if (!uploadBusy) {
                    showAddVoice = false
                    pendingAudio = null
                }
            },
            title = { Text("Thêm giọng đọc từ file") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "${pending.fileName} · ${formatBytes(pending.sizeBytes)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedTextField(
                        value = newVoiceName,
                        onValueChange = { newVoiceName = it },
                        label = { Text("Tên giọng đọc (2–60 ký tự)") },
                        singleLine = true,
                        enabled = !uploadBusy,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = newVoiceRefText,
                        onValueChange = { newVoiceRefText = it },
                        label = { Text("Nội dung đọc trong file (không bắt buộc)") },
                        enabled = !uploadBusy,
                        minLines = 2,
                        maxLines = 4,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (uploadBusy) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        Text(
                            "Đang tải lên và tạo giọng clone…",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { uploadPendingVoice() },
                    enabled = newVoiceName.trim().length in 2..60 && !uploadBusy,
                ) {
                    Text("Tải lên")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showAddVoice = false
                        pendingAudio = null
                    },
                    enabled = !uploadBusy,
                ) {
                    Text("Huỷ")
                }
            },
        )
    }

    deleteTarget?.let { voice ->
        AlertDialog(
            onDismissRequest = { if (!deleteBusy) deleteTarget = null },
            title = { Text("Xoá giọng clone?") },
            text = {
                Text(
                    "Giọng «${voice.name}» sẽ bị xoá khỏi server. " +
                        "Nếu đang chọn, app sẽ chuyển về giọng mặc định.",
                )
            },
            confirmButton = {
                Button(
                    onClick = { deleteVoice(voice) },
                    enabled = !deleteBusy,
                    colors =
                        ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                        ),
                ) {
                    Text(if (deleteBusy) "Đang xoá…" else "Xoá")
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }, enabled = !deleteBusy) {
                    Text("Huỷ")
                }
            },
        )
    }
}

// --------------------------------------------------------------------- SAF helper

private fun readPendingAudio(ctx: Context, uri: Uri): PendingAudio {
    val resolver = ctx.contentResolver
    var displayName = "sample.m4a"
    var declaredSize = -1L
    resolver
        .query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
            null,
            null,
            null,
        )
        ?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex >= 0 && !cursor.isNull(nameIndex)) {
                    cursor.getString(nameIndex)?.takeIf { it.isNotBlank() }?.let {
                        displayName = it
                    }
                }
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) {
                    declaredSize = cursor.getLong(sizeIndex)
                }
            }
        }
    if (declaredSize > VoxStudioConfig.MAX_UPLOAD_BYTES) {
        throw IllegalArgumentException("File vượt quá 15 MB.")
    }
    val mime = resolver.getType(uri).orEmpty()
    val extFromName = displayName.substringAfterLast('.', "").lowercase()
    val ext =
        extFromName.takeIf { it in VoxStudioConfig.ALLOWED_AUDIO_EXTENSIONS }
            ?: mimeToAudioExt(mime)
            ?: throw IllegalArgumentException(
                "Chỉ nhận file wav, mp3, m4a, ogg, flac hoặc webm.",
            )
    if (!displayName.lowercase().endsWith(".$ext")) {
        displayName = "$displayName.$ext"
    }
    val bytes =
        resolver.openInputStream(uri)?.use { input ->
            readLimited(input, VoxStudioConfig.MAX_UPLOAD_BYTES)
        } ?: throw IllegalArgumentException("Không mở được file đã chọn.")
    if (bytes.isEmpty()) {
        throw IllegalArgumentException("File đã chọn bị rỗng.")
    }
    return PendingAudio(
        bytes = bytes,
        fileName = displayName,
        mimeType = mime.ifBlank { audioExtToMime(ext) },
        sizeBytes = bytes.size,
    )
}

private fun readLimited(input: InputStream, maxBytes: Int): ByteArray {
    val out = ByteArrayOutputStream()
    val buffer = ByteArray(64 * 1024)
    var total = 0
    while (true) {
        val read = input.read(buffer)
        if (read < 0) break
        total += read
        if (total > maxBytes) {
            throw IllegalArgumentException("File vượt quá 15 MB.")
        }
        out.write(buffer, 0, read)
    }
    return out.toByteArray()
}

private fun mimeToAudioExt(mime: String): String? =
    when (mime.lowercase().substringBefore(';').trim()) {
        "audio/wav", "audio/x-wav", "audio/wave" -> "wav"
        "audio/mpeg", "audio/mp3" -> "mp3"
        "audio/mp4", "audio/x-m4a", "audio/m4a" -> "m4a"
        "audio/ogg", "application/ogg" -> "ogg"
        "audio/flac", "audio/x-flac" -> "flac"
        "audio/webm" -> "webm"
        else -> null
    }

private fun audioExtToMime(ext: String): String =
    when (ext) {
        "wav" -> "audio/wav"
        "mp3" -> "audio/mpeg"
        "m4a" -> "audio/mp4"
        "ogg" -> "audio/ogg"
        "flac" -> "audio/flac"
        "webm" -> "audio/webm"
        else -> "application/octet-stream"
    }

private fun formatBytes(bytes: Int): String =
    if (bytes < 1024 * 1024) {
        "${(bytes / 1024.0).roundToInt()} KB"
    } else {
        String.format(java.util.Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0))
    }

private fun voxConnectionError(e: Exception): String =
    when (e) {
        is java.net.UnknownHostException ->
            "Không phân giải được tên server. Kiểm tra Tailscale/MagicDNS, " +
                "hoặc dùng Base URL http://100.95.4.110."
        is java.net.ConnectException ->
            "Không kết nối được tới server VoxStudio. Kiểm tra mạng/Tailscale."
        is java.net.SocketTimeoutException ->
            "Kết nối tới VoxStudio quá lâu. Thử lại sau."
        else -> e.message?.takeIf { it.isNotBlank() } ?: e.javaClass.simpleName
    }