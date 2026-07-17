package com.java.myapplication

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.java.myapplication.adapter.auth.BackgroundAuthConfig
import com.java.myapplication.adapter.auth.BackgroundAuthRepository
import com.java.myapplication.config.ApiAccountConfig
import com.java.myapplication.config.ConfigRepository
import com.java.myapplication.ui.theme.MyApplicationTheme
import com.java.myapplication.webauth.WebAuthProfile
import com.java.myapplication.webauth.WebAuthProfileRegistry
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

class MainActivity : ComponentActivity() {
    private var authRefreshToken by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    ConfigScreen(
                        modifier = Modifier.padding(innerPadding),
                        context = this,
                        authRefreshToken = authRefreshToken
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        authRefreshToken++
    }
}

data class PlatformConfig(
    val name: String,
    val apiBase: String,
    val apiKey: String,
    val model: String,
    val enabled: Boolean
)

sealed class TestResult {
    data class Success(val models: List<String>) : TestResult()

    data class Error(
        val title: String,
        val reason: String,
        val suggestion: String,
        val detail: String? = null
    ) : TestResult()
}

sealed class ConnectionStatus {
    data class Success(val model: String) : ConnectionStatus()

    data class Error(
        val title: String,
        val reason: String,
        val suggestion: String
    ) : ConnectionStatus()
}

suspend fun fetchModels(apiBase: String, apiKey: String): TestResult {
    return withContext(Dispatchers.IO) {
        try {
            val normalizedBase = apiBase.trim().trimEnd('/')
            val requestUrl = if (normalizedBase.endsWith("/v1", ignoreCase = true)) {
                "$normalizedBase/models"
            } else {
                "$normalizedBase/v1/models"
            }

            val conn = URL(requestUrl).openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.setRequestProperty("Authorization", "Bearer $apiKey")
            conn.setRequestProperty("Accept", "application/json")
            conn.connectTimeout = 10_000
            conn.readTimeout = 10_000
            conn.doInput = true

            try {
                conn.connect()
            } catch (e: Exception) {
                return@withContext TestResult.Error(
                    title = "没有连接成功",
                    reason = "API 地址填写有误，或者服务器暂时无法访问。",
                    suggestion = "检查 API 地址后再试一次。",
                    detail = "请求 URL: $requestUrl\n异常: ${e.javaClass.simpleName}\n${e.message}"
                )
            }

            val responseCode = conn.responseCode
            val response = if (responseCode == 200) {
                conn.inputStream.bufferedReader().use { it.readText() }
            } else {
                val errorText = try {
                    conn.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                } catch (_: Exception) {
                    ""
                }
                conn.disconnect()
                val error = when (responseCode) {
                    401, 403 -> Triple(
                        "API Key 无法通过验证",
                        "当前 API Key 无效、已失效，或者没有读取模型列表的权限。",
                        "请检查 API Key 和对应账户权限后再试。"
                    )
                    404 -> Triple(
                        "没有找到模型接口",
                        "当前地址没有提供兼容的 /v1/models 接口。",
                        "请检查 API 地址，或向服务提供方确认模型列表路径。"
                    )
                    429 -> Triple(
                        "请求过于频繁",
                        "服务暂时限制了模型检测请求。",
                        "请稍等一会再试，不需要更换 API Key。"
                    )
                    in 500..599 -> Triple(
                        "服务器暂时异常",
                        "服务端当前无法完成模型检测。",
                        "请稍后重试；如果持续失败，再联系服务提供方。"
                    )
                    else -> Triple(
                        "模型检测失败",
                        "服务返回了 HTTP $responseCode，当前没有取得模型列表。",
                        "请检查 API 地址后重试。"
                    )
                }
                return@withContext TestResult.Error(
                    title = error.first,
                    reason = error.second,
                    suggestion = error.third,
                    detail = "请求 URL: $requestUrl\nHTTP 状态码: $responseCode\n返回内容: ${errorText.take(500)}"
                )
            }
            conn.disconnect()

            val root = JSONObject(response)
            val data = root.optJSONArray("data")
                ?: return@withContext TestResult.Error(
                    title = "没找到模型",
                    reason = "这个服务没有返回兼容的模型列表。",
                    suggestion = "确认服务支持 OpenAI 兼容的 /v1/models 接口。",
                    detail = "请求 URL: $requestUrl\n返回内容: ${response.take(500)}"
                )

            val models = buildList {
                for (index in 0 until data.length()) {
                    val modelId = data.optJSONObject(index)?.optString("id").orEmpty().trim()
                    if (modelId.isNotBlank()) add(modelId)
                }
            }.distinct()

            if (models.isEmpty()) {
                TestResult.Error(
                    title = "没找到模型",
                    reason = "接口没有返回可使用的模型名称。",
                    suggestion = "稍后再试，或向服务提供方确认模型列表接口。",
                    detail = "请求 URL: $requestUrl\n返回内容: ${response.take(500)}"
                )
            } else {
                TestResult.Success(models)
            }
        } catch (e: java.net.SocketTimeoutException) {
            TestResult.Error(
                title = "网络有点慢",
                reason = "连接超时了。",
                suggestion = "换个网络，或者等几秒钟再试一次。",
                detail = "异常: ${e.javaClass.simpleName}\n${e.message}"
            )
        } catch (e: Exception) {
            TestResult.Error(
                title = "没有连接成功",
                reason = "API 地址填写有误，或者服务器暂时无法访问。",
                suggestion = "检查 API 地址后再试一次。",
                detail = "异常: ${e.javaClass.simpleName}\n${e.message}"
            )
        }
    }
}

@Composable
fun ConfigScreen(
    modifier: Modifier = Modifier,
    context: Context,
    authRefreshToken: Int
) {
    val prefs = remember { context.getSharedPreferences("api_config", Context.MODE_PRIVATE) }
    val slots = remember { listOf("Kimi", "MiMo", "DeepSeek", "OpenAI") }
    val scope = rememberCoroutineScope()
    val initialConfigs = remember { loadPlatformConfigs(prefs, slots) }

    var configs by remember { mutableStateOf(initialConfigs) }
    var backgroundAuths by remember {
        mutableStateOf(synchronizePlatformAuthorizations(prefs, initialConfigs, slots))
    }
    var connectionStatuses by remember {
        mutableStateOf(List<ConnectionStatus?>(slots.size) { null })
    }
    var testingIndex by remember { mutableIntStateOf(-1) }
    var editingIndex by remember { mutableIntStateOf(-1) }
    var showModelDialog by remember { mutableStateOf(false) }
    var modelList by remember { mutableStateOf(emptyList<String>()) }
    var selectedPlatformIndex by remember { mutableIntStateOf(-1) }
    var showErrorDialog by remember { mutableStateOf(false) }
    var errorTitle by remember { mutableStateOf("") }
    var errorReason by remember { mutableStateOf("") }
    var errorSuggestion by remember { mutableStateOf("") }
    var errorDetail by remember { mutableStateOf<String?>(null) }
    var showDetail by remember { mutableStateOf(false) }

    val latestConfigs by rememberUpdatedState(configs)
    val latestEditingIndex by rememberUpdatedState(editingIndex)
    val activity = context as? ComponentActivity

    DisposableEffect(activity, prefs, slots) {
        if (activity == null) {
            onDispose { }
        } else {
            val observer = LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_PAUSE) {
                    val index = latestEditingIndex
                    if (index in slots.indices) {
                        if (savePlatformConfig(prefs, slots[index], latestConfigs[index])) {
                            refreshWidget(context)
                        } else {
                            Toast.makeText(context, "配置保存失败，请返回应用重试", Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }
            activity.lifecycle.addObserver(observer)
            onDispose { activity.lifecycle.removeObserver(observer) }
        }
    }

    LaunchedEffect(authRefreshToken) {
        val refreshedConfigs = loadPlatformConfigs(prefs, slots)
        configs = refreshedConfigs
        backgroundAuths = synchronizePlatformAuthorizations(prefs, refreshedConfigs, slots)
        // Activity 恢复时不保留旧的“检测成功”卡片，避免配置为空仍显示成功。
        connectionStatuses = List(slots.size) { null }
    }

    fun replaceConfig(
        index: Int,
        newConfig: PlatformConfig,
        persist: Boolean,
        clearStatus: Boolean = true
    ): List<PlatformConfig> {
        val updated = configs.toMutableList().apply { this[index] = newConfig }
        configs = updated
        if (clearStatus) {
            connectionStatuses = connectionStatuses.toMutableList().apply { this[index] = null }
        }
        if (persist) {
            if (!savePlatformConfig(prefs, slots[index], newConfig)) {
                Toast.makeText(context, "配置保存失败，请重试", Toast.LENGTH_LONG).show()
            }
        }
        return updated
    }

    fun closeEditor() {
        val index = editingIndex
        if (index in slots.indices) {
            if (!savePlatformConfig(prefs, slots[index], configs[index])) {
                Toast.makeText(context, "配置保存失败，请重试", Toast.LENGTH_LONG).show()
                return
            }
            refreshWidget(context)
        }
        editingIndex = -1
    }

    BackHandler(enabled = editingIndex >= 0) {
        closeEditor()
    }

    if (editingIndex < 0) {
        SlotListScreen(
            modifier = modifier,
            configs = configs,
            slots = slots,
            backgroundAuths = backgroundAuths,
            onToggle = { index, enabled ->
                replaceConfig(index, configs[index].copy(enabled = enabled), persist = true)
                refreshWidget(context)
            },
            onEdit = { index -> editingIndex = index }
        )
    } else {
        val index = editingIndex
        val slotName = slots[index]
        val config = configs[index]
        val webProfile = WebAuthProfileRegistry.findFor(slotName, config.apiBase)
        val authConnected = isMatchingAuthorization(webProfile, backgroundAuths[index])

        SlotEditorScreen(
            modifier = modifier,
            slotId = slotName,
            slotNumber = index + 1,
            config = config,
            webProfile = webProfile,
            authConnected = authConnected,
            status = connectionStatuses[index],
            isTesting = testingIndex == index,
            onBack = ::closeEditor,
            onConfigChange = { updated ->
                // 输入期间只更新内存，不在每个字符上同步写磁盘和实例仓库。
                if (updated.apiBase != configs[index].apiBase) {
                    backgroundAuths = backgroundAuths.toMutableList().apply {
                        this[index] = BackgroundAuthConfig()
                    }
                }
                replaceConfig(index, updated, persist = false)
            },
            onTest = test@{
                val testConfig = configs[index]
                if (testConfig.apiBase.isBlank() || testConfig.apiKey.isBlank()) {
                    connectionStatuses = connectionStatuses.toMutableList().apply {
                        this[index] = ConnectionStatus.Error(
                            title = "还不能检测",
                            reason = "API 地址或 API Key 没有填写。",
                            suggestion = "把两项填写完整后再试一次。"
                        )
                    }
                } else {
                    if (!savePlatformConfig(prefs, slotName, testConfig)) {
                        Toast.makeText(context, "配置保存失败，未开始检测", Toast.LENGTH_LONG).show()
                        return@test
                    }
                    testingIndex = index
                    connectionStatuses = connectionStatuses.toMutableList().apply { this[index] = null }
                    scope.launch {
                        val result = fetchModels(testConfig.apiBase, testConfig.apiKey)
                        val currentConfig = configs.getOrNull(index)
                        if (
                            currentConfig == null ||
                            currentConfig.apiBase != testConfig.apiBase ||
                            currentConfig.apiKey != testConfig.apiKey
                        ) {
                            testingIndex = -1
                            return@launch
                        }

                        when (result) {
                            is TestResult.Success -> {
                                testingIndex = -1
                                if (result.models.size == 1) {
                                    val selectedModel = result.models.first()
                                    val selectedConfig = testConfig.copy(model = selectedModel)
                                    val updatedConfigs = replaceConfig(
                                        index,
                                        selectedConfig,
                                        persist = true,
                                        clearStatus = false
                                    )
                                    connectionStatuses = connectionStatuses.toMutableList().apply {
                                        this[index] = ConnectionStatus.Success(selectedModel)
                                    }
                                    backgroundAuths = synchronizePlatformAuthorizations(
                                        prefs,
                                        updatedConfigs,
                                        slots
                                    )
                                    refreshWidget(context)
                                } else {
                                    modelList = result.models
                                    selectedPlatformIndex = index
                                    showModelDialog = true
                                }
                            }

                            is TestResult.Error -> {
                                testingIndex = -1
                                connectionStatuses = connectionStatuses.toMutableList().apply {
                                    this[index] = ConnectionStatus.Error(
                                        result.title,
                                        result.reason,
                                        result.suggestion
                                    )
                                }
                                errorTitle = result.title
                                errorReason = result.reason
                                errorSuggestion = result.suggestion
                                errorDetail = result.detail
                                showDetail = false
                                showErrorDialog = true
                            }
                        }
                    }
                }
            },
            onConnect = connect@{
                webProfile?.let { profile ->
                    // 先保存当前输入，避免网页登录返回 onResume 时被旧配置覆盖。
                    if (!savePlatformConfig(prefs, slotName, configs[index])) {
                        Toast.makeText(context, "配置保存失败，未打开登录", Toast.LENGTH_LONG).show()
                        return@connect
                    }
                    context.startActivity(
                        WebAuthActivity.createIntent(
                            context = context,
                            profileId = profile.profileId,
                            targetInstanceKey = slotName
                        )
                    )
                }
            },
            onDisconnect = {
                webProfile?.let { profile ->
                    clearPlatformAuthorization(prefs, slotName)
                    backgroundAuths = synchronizePlatformAuthorizations(prefs, configs, slots)
                    Toast.makeText(context, "平台账户已断开", Toast.LENGTH_SHORT).show()
                    refreshWidget(context)
                }
            }
        )
    }

    if (showModelDialog && modelList.isNotEmpty() && selectedPlatformIndex in slots.indices) {
        AlertDialog(
            onDismissRequest = { showModelDialog = false },
            title = { Text("选择模型") },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    modelList.forEach { modelId ->
                        TextButton(
                            onClick = {
                                val index = selectedPlatformIndex
                                val selectedConfig = configs[index].copy(model = modelId)
                                val updatedConfigs = replaceConfig(
                                    index,
                                    selectedConfig,
                                    persist = true,
                                    clearStatus = false
                                )
                                connectionStatuses = connectionStatuses.toMutableList().apply {
                                    this[index] = ConnectionStatus.Success(modelId)
                                }
                                showModelDialog = false
                                backgroundAuths = synchronizePlatformAuthorizations(
                                    prefs,
                                    updatedConfigs,
                                    slots
                                )
                                refreshWidget(context)
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(modelId)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showModelDialog = false }) {
                    Text("取消")
                }
            }
        )
    }

    if (showErrorDialog) {
        AlertDialog(
            onDismissRequest = { showErrorDialog = false },
            title = { Text(errorTitle) },
            text = {
                Column {
                    Text(errorReason)
                    Text(
                        text = errorSuggestion,
                        modifier = Modifier.padding(top = 10.dp)
                    )
                    errorDetail?.let { detail ->
                        TextButton(onClick = { showDetail = !showDetail }) {
                            Text(if (showDetail) "隐藏详情" else "查看详情")
                        }
                        if (showDetail) {
                            Text(
                                text = detail,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showErrorDialog = false }) {
                    Text("知道了")
                }
            }
        )
    }
}

@Composable
private fun SlotListScreen(
    modifier: Modifier,
    configs: List<PlatformConfig>,
    slots: List<String>,
    backgroundAuths: List<BackgroundAuthConfig>,
    onToggle: (Int, Boolean) -> Unit,
    onEdit: (Int) -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Spacer(modifier = Modifier.height(16.dp))
        Text("AI API Dashboard", style = MaterialTheme.typography.headlineMedium)
        Text(
            text = "打开需要显示的槽位，点设置即可。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp, bottom = 16.dp)
        )

        configs.forEachIndexed { index, config ->
            val profile = WebAuthProfileRegistry.findFor(slots[index], config.apiBase)
            val authConnected = isMatchingAuthorization(profile, backgroundAuths[index])

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "槽位 ${index + 1}",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = slotTitle(config),
                                style = MaterialTheme.typography.titleLarge,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                            Text(
                                text = simpleSlotStatus(config, authConnected),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                        Switch(
                            checked = config.enabled,
                            onCheckedChange = { onToggle(index, it) }
                        )
                    }

                    if (config.enabled) {
                        Button(
                            onClick = { onEdit(index) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 12.dp)
                        ) {
                            Text(if (isConfigured(config, authConnected)) "编辑" else "设置")
                        }
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun SlotEditorScreen(
    modifier: Modifier,
    slotId: String,
    slotNumber: Int,
    config: PlatformConfig,
    webProfile: WebAuthProfile?,
    authConnected: Boolean,
    status: ConnectionStatus?,
    isTesting: Boolean,
    onBack: () -> Unit,
    onConfigChange: (PlatformConfig) -> Unit,
    onTest: () -> Unit,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit
) {
    val apiConnected = config.apiBase.isNotBlank() &&
        config.apiKey.isNotBlank() &&
        config.model.isNotBlank()

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Spacer(modifier = Modifier.height(12.dp))
        TextButton(onClick = onBack) {
            Text("‹ 返回")
        }
        Text("设置槽位 $slotNumber", style = MaterialTheme.typography.headlineMedium)

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("API 连接", style = MaterialTheme.typography.titleMedium)
                Text(
                    text = "填写 API 地址和 API Key，系统会自动查找模型。输入完成前不会反复写入配置。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )

                OutlinedTextField(
                    value = config.apiBase,
                    onValueChange = { onConfigChange(config.copy(apiBase = it, model = "")) },
                    label = { Text("API 地址") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    singleLine = true
                )
                OutlinedTextField(
                    value = config.apiKey,
                    onValueChange = { onConfigChange(config.copy(apiKey = it, model = "")) },
                    label = { Text("API Key") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation()
                )

                if (config.model.isNotBlank()) {
                    Text(
                        text = "当前模型：${config.model}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 10.dp)
                    )
                }

                status?.let {
                    when (it) {
                        is ConnectionStatus.Success -> {
                            if (apiConnected && it.model == config.model) {
                                SimpleStatusCard(
                                    title = "连接成功",
                                    body = "已选择 ${it.model}",
                                    isError = false
                                )
                            }
                        }

                        is ConnectionStatus.Error -> SimpleStatusCard(
                            title = it.title,
                            body = "${it.reason}\n${it.suggestion}",
                            isError = true
                        )
                    }
                }

                Button(
                    onClick = onTest,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    enabled = !isTesting
                ) {
                    Text(if (isTesting) "正在查找模型…" else "检测并选择模型")
                }
            }
        }

        SlotDataCapabilityCard(
            slotId = slotId,
            apiBase = config.apiBase,
            apiKey = config.apiKey,
            modelName = config.model,
            apiConnected = apiConnected,
            authConnected = authConnected,
            webProfile = webProfile
        )

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp, bottom = 24.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("平台账户（可选）", style = MaterialTheme.typography.titleMedium)
                Text(
                    text = when (webProfile?.profileId) {
                        "mimo" -> "MiMo 的余额、金额和用量来自平台账户，不是模型 API；登录后会在上方直接读取。"
                        "deepseek" -> "DeepSeek 的网页账单与 Token 汇总来自平台账户；API Key 余额仍独立读取。"
                        "aihuangniu" -> "爱黄牛的账户余额和资料来自平台账户；模型用量仍使用 API Key 读取。"
                        else -> "平台账户用于补充模型 API 无法提供的余额、用量或账户资料。"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )

                if (webProfile == null) {
                    Text(
                        text = if (config.apiBase.isBlank()) {
                            "填写 API 地址后，系统会自动匹配登录入口。"
                        } else {
                            "当前服务暂不支持平台账户登录，API 连接仍可正常使用。"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 14.dp)
                    )
                } else {
                    SimpleStatusCard(
                        title = if (authConnected) "平台账户授权已保存" else "平台账户未授权",
                        body = if (authConnected) {
                            "已解锁并自动更新：${webDataSummary(webProfile.profileId)}"
                        } else {
                            "登录后可解锁：${webDataSummary(webProfile.profileId)}"
                        },
                        isError = false
                    )
                }

                Button(
                    onClick = onConnect,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    enabled = webProfile != null
                ) {
                    Text(
                        when {
                            webProfile == null -> "暂不支持平台账户登录"
                            authConnected -> "重新登录平台账户"
                            else -> "登录平台账户"
                        }
                    )
                }

                if (authConnected) {
                    TextButton(
                        onClick = onDisconnect,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp)
                    ) {
                        Text("断开平台账户")
                    }
                }
            }
        }
    }
}

@Composable
private fun SimpleStatusCard(
    title: String,
    body: String,
    isError: Boolean
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isError) {
                MaterialTheme.colorScheme.errorContainer
            } else {
                MaterialTheme.colorScheme.secondaryContainer
            }
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = if (isError) {
                    MaterialTheme.colorScheme.onErrorContainer
                } else {
                    MaterialTheme.colorScheme.onSecondaryContainer
                }
            )
            Text(
                text = body,
                style = MaterialTheme.typography.bodySmall,
                color = if (isError) {
                    MaterialTheme.colorScheme.onErrorContainer
                } else {
                    MaterialTheme.colorScheme.onSecondaryContainer
                },
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

private fun slotTitle(config: PlatformConfig): String {
    return config.model.ifBlank {
        config.name.takeUnless {
            it.equals("Kimi", true) ||
                it.equals("MiMo", true) ||
                it.equals("DeepSeek", true) ||
                it.equals("OpenAI", true)
        }.orEmpty().ifBlank { "未配置" }
    }
}

private fun simpleSlotStatus(config: PlatformConfig, authConnected: Boolean): String {
    if (!config.enabled) return "已隐藏"
    val apiConfigured = config.apiBase.isNotBlank() &&
        config.apiKey.isNotBlank() &&
        config.model.isNotBlank()
    return when {
        apiConfigured && authConnected -> "API 已配置 · 平台账户授权已保存"
        apiConfigured -> "API 已配置"
        authConnected -> "平台账户授权已保存 · 等待选择模型"
        else -> "未配置"
    }
}

private fun isConfigured(config: PlatformConfig, authConnected: Boolean): Boolean {
    return (
        config.apiBase.isNotBlank() &&
            config.apiKey.isNotBlank() &&
            config.model.isNotBlank()
        ) || authConnected
}

private fun webDataSummary(profileId: String): String {
    return when (profileId) {
        "mimo" -> "余额构成、本月与累计金额、请求、Token、限流信息"
        "deepseek" -> "本月与累计消耗、本月 Token、预计可用 Token"
        "aihuangniu" -> "余额、累计充值、并发、账户状态和最近活跃"
        else -> "平台实际提供的账户数据"
    }
}

private fun isMatchingAuthorization(
    profile: WebAuthProfile?,
    auth: BackgroundAuthConfig
): Boolean {
    return profile != null &&
        auth.enabled &&
        auth.authType == profile.authType &&
        auth.authValue.isNotBlank()
}

private fun synchronizePlatformAuthorizations(
    prefs: android.content.SharedPreferences,
    configs: List<PlatformConfig>,
    slots: List<String>
): List<BackgroundAuthConfig> {
    val profiles = slots.indices.map { index ->
        WebAuthProfileRegistry.findFor(slots[index], configs[index].apiBase)
    }
    return profiles.mapIndexed { index, profile ->
        if (profile == null) return@mapIndexed BackgroundAuthConfig()
        val slotName = slots[index]
        val savedProfileId = prefs.getString(webAuthProfileKey(slotName), null)
        val local = BackgroundAuthRepository.load(prefs, slotName)
        if (savedProfileId == profile.profileId && isMatchingAuthorization(profile, local)) {
            return@mapIndexed local
        }

        // One-time migration from the old platform-wide credential. Afterwards
        // every slot owns its credential and can use a different account.
        val legacyShared = BackgroundAuthRepository.load(prefs, profile.instanceKey)
        if (isMatchingAuthorization(profile, legacyShared)) {
            BackgroundAuthRepository.save(prefs, slotName, legacyShared)
            prefs.edit().putString(webAuthProfileKey(slotName), profile.profileId).commit()
            legacyShared
        } else {
            BackgroundAuthConfig()
        }
    }.also {
        profiles.filterNotNull().distinctBy { it.instanceKey }.forEach { profile ->
            prefs.edit().remove("${profile.instanceKey}_auth").commit()
        }
    }
}

private fun clearPlatformAuthorization(
    prefs: android.content.SharedPreferences,
    slotName: String
) {
    BackgroundAuthRepository.clear(prefs, slotName)
    prefs.edit().remove(webAuthProfileKey(slotName)).commit()
}

private fun webAuthProfileKey(slotName: String): String {
    return "web_auth_profile_${slotName.lowercase()}"
}

private fun loadPlatformConfigs(
    prefs: android.content.SharedPreferences,
    platforms: List<String>
): List<PlatformConfig> {
    val repositoryConfigs = ConfigRepository.loadAllConfigs(prefs)
    return platforms.map { platform ->
        val config = repositoryConfigs.firstOrNull {
            it.id.equals(platform, ignoreCase = true)
        }
        if (config != null) {
            PlatformConfig(
                name = config.name,
                apiBase = config.apiBase,
                apiKey = config.apiKey,
                model = config.model,
                enabled = config.enabled
            )
        } else {
            PlatformConfig(platform, "", "", "", true)
        }
    }
}

fun savePlatformConfig(
    prefs: android.content.SharedPreferences,
    platform: String,
    config: PlatformConfig
): Boolean {
    return ConfigRepository.saveConfig(
        prefs,
        ApiAccountConfig(
            id = platform,
            name = config.name,
            apiBase = config.apiBase,
            apiKey = config.apiKey,
            model = config.model,
            enabled = config.enabled
        )
    )
}

fun refreshWidget(context: Context) {
    try {
        val appWidgetManager = AppWidgetManager.getInstance(context)
        val componentName = ComponentName(context, BalanceWidgetProvider::class.java)
        val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)
        if (appWidgetIds.isNotEmpty()) {
            val intent = Intent(context, BalanceWidgetProvider::class.java).apply {
                action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, appWidgetIds)
            }
            context.sendBroadcast(intent)
        }
    } catch (_: Exception) {
        // Widget 不存在或桌面暂不可用时，不影响配置页保存。
    }
}
