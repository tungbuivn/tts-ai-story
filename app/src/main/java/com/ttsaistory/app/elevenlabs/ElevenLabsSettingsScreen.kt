package com.ttsaistory.app.elevenlabs

import android.content.SharedPreferences
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import android.widget.Toast
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ElevenLabsSettingsScreen(
    modifier: Modifier = Modifier,
    prefs: SharedPreferences,
    onClose: () -> Unit,
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var samplePlayJob by remember { mutableStateOf<Job?>(null) }
    var playingVoiceId by remember { mutableStateOf<String?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            samplePlayJob?.cancel()
        }
    }
    var apiKeyInput by remember { mutableStateOf(ElevenLabsPrefKeys.resolveApiKey(prefs)) }
    var showApiKeyPlain by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var voices by remember { mutableStateOf<List<ElevenLabsVoice>>(emptyList()) }
    var models by remember { mutableStateOf<List<ElevenLabsModel>>(emptyList()) }
    var cacheLoadedAtMillis by remember { mutableStateOf<Long?>(null) }
    var subscriptionQuota by remember { mutableStateOf<ElevenLabsSubscriptionQuota?>(null) }
    var subscriptionQuotaError by remember { mutableStateOf<String?>(null) }

    val initialLanguageKey =
        when {
            !prefs.contains(ElevenLabsPrefKeys.LANGUAGE_CODE) -> "vi"
            prefs.getString(ElevenLabsPrefKeys.LANGUAGE_CODE, "")?.isEmpty() == true -> "auto"
            prefs.getString(ElevenLabsPrefKeys.LANGUAGE_CODE, "") == "en" -> "en"
            else -> "vi"
        }

    fun apiLanguageFromUiKey(key: String): String? =
        when (key) {
            "vi" -> "vi"
            "en" -> "en"
            else -> null
        }

    var languageKey by remember { mutableStateOf(initialLanguageKey) }

    var selectedVoiceId by remember {
        mutableStateOf(
            ElevenLabsPrefKeys.resolveVoiceIdForLanguage(prefs, apiLanguageFromUiKey(initialLanguageKey)),
        )
    }
    var selectedModelId by remember {
        mutableStateOf(
            prefs.getString(ElevenLabsPrefKeys.MODEL_ID, null)?.trim()?.takeIf { it.isNotEmpty() }
                ?: ElevenLabsConfig.MODEL_ID,
        )
    }

    var genderFilterKey by remember { mutableStateOf("all") }

    val voicesAfterLanguage =
        remember(voices, languageKey) {
            ElevenLabsApi.voicesMatchingLanguageOnly(languageKey, voices)
        }

    val displayedVoices =
        remember(voicesAfterLanguage, genderFilterKey) {
            ElevenLabsApi.voicesMatchingGenderFilter(genderFilterKey, voicesAfterLanguage)
        }

    val cacheTimeFormatter =
        remember {
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault())
        }

    fun formatCacheTime(millis: Long): String =
        cacheTimeFormatter.format(Instant.ofEpochMilli(millis))

    fun persistLanguage(key: String) {
        languageKey = key
        prefs
            .edit()
            .apply {
                when (key) {
                    "vi" -> putString(ElevenLabsPrefKeys.LANGUAGE_CODE, "vi")
                    "en" -> putString(ElevenLabsPrefKeys.LANGUAGE_CODE, "en")
                    "auto" -> putString(ElevenLabsPrefKeys.LANGUAGE_CODE, "")
                }
            }
            .apply()
        selectedVoiceId =
            ElevenLabsPrefKeys.resolveVoiceIdForLanguage(prefs, apiLanguageFromUiKey(key))
    }

    fun effectiveApiKeyForRequest(): String =
        apiKeyInput.trim().ifBlank { ElevenLabsPrefKeys.resolveApiKey(prefs) }

    suspend fun applySnapshotFromCache() {
        val key = effectiveApiKeyForRequest()
        if (key.isEmpty()) {
            voices = emptyList()
            models = emptyList()
            cacheLoadedAtMillis = null
            return
        }
        val snap =
            withContext(Dispatchers.IO) {
                ElevenLabsCatalogCache.read(ctx, key)
            }
        if (snap != null) {
            voices = snap.voices
            models = snap.models
            cacheLoadedAtMillis = snap.savedAtMillis
        } else {
            voices = emptyList()
            models = emptyList()
            cacheLoadedAtMillis = null
        }
    }

    fun updateCatalogFromNetwork() {
        val key = effectiveApiKeyForRequest()
        if (key.isEmpty()) {
            error = "Chưa có API key. Nhập key hoặc cấu hình mặc định trong app."
            return
        }
        scope.launch {
            loading = true
            error = null
            try {
                val v = withContext(Dispatchers.IO) { ElevenLabsApi.fetchVoices(key) }
                val m = withContext(Dispatchers.IO) { ElevenLabsApi.fetchTextToSpeechModels(key) }
                withContext(Dispatchers.IO) {
                    ElevenLabsCatalogCache.write(ctx, key, v, m)
                }
                voices = v
                models = m
                cacheLoadedAtMillis = System.currentTimeMillis()
                if (v.isEmpty()) {
                    error = "Không có giọng trong phản hồi API."
                }
                try {
                    subscriptionQuota =
                        withContext(Dispatchers.IO) { ElevenLabsApi.fetchSubscriptionQuota(key) }
                    subscriptionQuotaError = null
                } catch (qe: Exception) {
                    subscriptionQuota = null
                    subscriptionQuotaError = qe.message?.take(240)
                }
            } catch (e: Exception) {
                error = e.message ?: e.javaClass.simpleName
            } finally {
                loading = false
            }
        }
    }

    suspend fun refreshSubscriptionQuota() {
        val key = effectiveApiKeyForRequest()
        if (key.isEmpty()) {
            subscriptionQuota = null
            subscriptionQuotaError = null
            return
        }
        try {
            subscriptionQuota =
                withContext(Dispatchers.IO) { ElevenLabsApi.fetchSubscriptionQuota(key) }
            subscriptionQuotaError = null
        } catch (e: Exception) {
            subscriptionQuota = null
            subscriptionQuotaError = e.message?.take(240)
        }
    }

    LaunchedEffect(Unit) {
        applySnapshotFromCache()
        refreshSubscriptionQuota()
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Cấu hình ElevenLabs") },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Đóng",
                        )
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
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                    ),
                modifier = Modifier.padding(bottom = 8.dp),
            ) {
                Column(Modifier.padding(12.dp)) {
                    Text(
                        "API key",
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                    OutlinedTextField(
                        value = apiKeyInput,
                        onValueChange = { apiKeyInput = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("xi-api-key") },
                        singleLine = true,
                        visualTransformation =
                            if (showApiKeyPlain) {
                                VisualTransformation.None
                            } else {
                                PasswordVisualTransformation()
                            },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        trailingIcon = {
                            IconButton(onClick = { showApiKeyPlain = !showApiKeyPlain }) {
                                Icon(
                                    imageVector =
                                        if (showApiKeyPlain) {
                                            Icons.Filled.VisibilityOff
                                        } else {
                                            Icons.Filled.Visibility
                                        },
                                    contentDescription = if (showApiKeyPlain) "Ẩn" else "Hiện",
                                )
                            }
                        },
                    )
                    Row(
                        modifier = Modifier.padding(top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Button(
                            onClick = {
                                ElevenLabsPrefKeys.saveApiKey(prefs, apiKeyInput)
                                apiKeyInput = ElevenLabsPrefKeys.resolveApiKey(prefs)
                                Toast.makeText(ctx, "Đã lưu API key", Toast.LENGTH_SHORT).show()
                                scope.launch {
                                    applySnapshotFromCache()
                                    refreshSubscriptionQuota()
                                }
                            },
                        ) {
                            Text("Lưu API key")
                        }
                    }
                }
            }
            Card(
                colors =
                    CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
                    ),
                modifier = Modifier.padding(bottom = 8.dp),
            ) {
                Column(Modifier.padding(12.dp)) {
                    Text("Quota tài khoản", style = MaterialTheme.typography.titleSmall)
                    val keyOk = effectiveApiKeyForRequest().isNotBlank()
                    val quotaMainText =
                        when {
                            !keyOk -> "Nhập API key để xem."
                            subscriptionQuotaError != null -> subscriptionQuotaError!!
                            subscriptionQuota != null -> subscriptionQuota!!.displayAsRatio()
                            else -> "—"
                        }
                    Text(
                        quotaMainText,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
            if (loading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            Button(
                onClick = { updateCatalogFromNetwork() },
                enabled = !loading && effectiveApiKeyForRequest().isNotBlank(),
                modifier = Modifier.padding(vertical = 8.dp),
            ) {
                Text("Cập nhật danh sách giọng & model")
            }
            cacheLoadedAtMillis?.let { t ->
                Text(
                    "Dữ liệu cache: ${formatCacheTime(t)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (cacheLoadedAtMillis == null && voices.isEmpty() && models.isEmpty() && !loading) {
                Text(
                    "Chưa có cache cho API key hiện tại — bấm «Cập nhật…» để tải từ mạng.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            error?.let { err ->
                Text(
                    text = err,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 24.dp),
            ) {
                item {
                    Text("Model TTS", style = MaterialTheme.typography.titleMedium)
                }
                if (models.isEmpty() && !loading) {
                    item {
                        Text(
                            "Chưa có model trong cache — bấm «Cập nhật danh sách giọng & model».",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                items(models, key = { it.modelId }) { m ->
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = selectedModelId == m.modelId,
                            onClick = {
                                selectedModelId = m.modelId
                                prefs.edit().putString(ElevenLabsPrefKeys.MODEL_ID, m.modelId).apply()
                                Toast.makeText(ctx, "Đã chọn model", Toast.LENGTH_SHORT).show()
                            },
                        )
                        Column(Modifier.padding(start = 4.dp)) {
                            Text(m.name, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                m.modelId,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                item {
                    Card(
                        colors =
                            CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                            ),
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Text(
                                "Ngôn ngữ đọc (language_code)",
                                style = MaterialTheme.typography.titleSmall,
                            )
                            Text(
                                "Chọn ngôn ngữ trước; mỗi ngôn ngữ (và chế độ tự động) có thể gán một giọng riêng.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(bottom = 8.dp),
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(
                                    selected = languageKey == "vi",
                                    onClick = { persistLanguage("vi") },
                                )
                                Text("Tiếng Việt (vi)", modifier = Modifier.padding(start = 4.dp))
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(
                                    selected = languageKey == "en",
                                    onClick = { persistLanguage("en") },
                                )
                                Text("English (en)", modifier = Modifier.padding(start = 4.dp))
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(
                                    selected = languageKey == "auto",
                                    onClick = { persistLanguage("auto") },
                                )
                                Text("Tự động (không gửi language_code)", modifier = Modifier.padding(start = 4.dp))
                            }
                        }
                    }
                }
                item {
                    val langTitle =
                        when (languageKey) {
                            "vi" -> "Giọng cho tiếng Việt"
                            "en" -> "Giọng cho English"
                            else -> "Giọng cho chế độ tự động"
                        }
                    Column {
                        Text(langTitle, style = MaterialTheme.typography.titleMedium)
                        Row(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(top = 4.dp)
                                    .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            listOf(
                                "all" to "Tất cả",
                                "male" to "Nam",
                                "female" to "Nữ",
                                "neutral" to "Trung tính",
                                "none" to "Chưa ghi",
                                "other" to "Khác",
                            ).forEach { (key, label) ->
                                FilterChip(
                                    selected = genderFilterKey == key,
                                    onClick = { genderFilterKey = key },
                                    label = { Text(label) },
                                )
                            }
                        }
                    }
                }
                if (voices.isEmpty() && !loading) {
                    item {
                        Text(
                            "Chưa có giọng trong cache — bấm «Cập nhật danh sách giọng & model» (cần API key).",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (voices.isNotEmpty() && voicesAfterLanguage.isEmpty() && !loading) {
                    item {
                        Text(
                            "Không có giọng khớp ngôn ngữ đã chọn trong cache. Đổi ngôn ngữ hoặc bấm cập nhật.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (voicesAfterLanguage.isNotEmpty() && displayedVoices.isEmpty() && !loading) {
                    item {
                        Text(
                            "Không có giọng khớp bộ lọc giới tính. Chọn «Tất cả» hoặc đổi chip lọc.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                items(displayedVoices, key = { it.voiceId }) { v ->
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = selectedVoiceId == v.voiceId,
                            onClick = {
                                ElevenLabsPrefKeys.saveVoiceForLanguageUi(prefs, languageKey, v.voiceId)
                                selectedVoiceId = v.voiceId
                                Toast.makeText(ctx, "Đã lưu giọng cho ngôn ngữ này", Toast.LENGTH_SHORT).show()
                            },
                        )
                        Column(
                            Modifier
                                .weight(1f)
                                .padding(start = 4.dp),
                        ) {
                            Text(v.name, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                buildString {
                                    append(v.voiceId)
                                    v.category?.let { append(" · ").append(it) }
                                    v.labelLanguage?.let { append(" · ngôn ngữ: ").append(it) }
                                    v.genderLabelForUi()?.let { append(" · giới tính: ").append(it) }
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            v.description?.takeIf { it.isNotBlank() }?.let { d ->
                                Text(
                                    d.take(200),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        IconButton(
                            onClick = {
                                if (playingVoiceId == v.voiceId) {
                                    samplePlayJob?.cancel()
                                    return@IconButton
                                }
                                samplePlayJob?.cancel()
                                lateinit var jobRef: Job
                                jobRef =
                                    scope.launch {
                                        playingVoiceId = v.voiceId
                                        val vid = v.voiceId
                                        try {
                                            ElevenLabsTtsPlayback.playVoiceSample(
                                                context = ctx.applicationContext,
                                                apiKey = effectiveApiKeyForRequest(),
                                                voice = v,
                                            )
                                        } catch (_: CancellationException) {
                                            // hủy — bình thường
                                        } catch (e: Exception) {
                                            Toast.makeText(
                                                ctx,
                                                e.message ?: "Không phát được mẫu.",
                                                Toast.LENGTH_SHORT,
                                            ).show()
                                        } finally {
                                            if (playingVoiceId == vid) {
                                                playingVoiceId = null
                                            }
                                            if (samplePlayJob === jobRef) {
                                                samplePlayJob = null
                                            }
                                        }
                                    }
                                samplePlayJob = jobRef
                            },
                            enabled =
                                !loading &&
                                    effectiveApiKeyForRequest().isNotBlank() &&
                                    v.hasPlayableSample(),
                        ) {
                            Icon(
                                imageVector =
                                    if (playingVoiceId == v.voiceId) {
                                        Icons.Filled.Stop
                                    } else {
                                        Icons.Filled.PlayArrow
                                    },
                                contentDescription =
                                    if (playingVoiceId == v.voiceId) {
                                        "Dừng mẫu"
                                    } else {
                                        "Nghe mẫu giọng"
                                    },
                            )
                        }
                    }
                }
            }
        }
    }
}
