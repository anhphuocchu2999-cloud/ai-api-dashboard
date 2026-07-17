package com.java.myapplication

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.java.myapplication.adapter.AdapterFactory
import com.java.myapplication.adapter.AdapterRequest
import com.java.myapplication.adapter.HostRequestCoordinator
import com.java.myapplication.adapter.WidgetData
import com.java.myapplication.adapter.auth.BackgroundAuthConfig
import com.java.myapplication.adapter.auth.BackgroundAuthRepository
import com.java.myapplication.webauth.WebAuthProfile
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private enum class SlotServiceKind {
    KIMI_NEW_API,
    MIMO,
    DEEPSEEK,
    AIHUANGNIU,
    GENERIC
}

private enum class CapabilitySource(val label: String) {
    API("云端 API"),
    ACCOUNT("云端账户"),
    LOCAL("本地测算"),
    CACHE("本地缓存")
}

private data class CapabilityItem(
    val title: String,
    val detail: String,
    val source: CapabilitySource,
    val state: String,
    val active: Boolean
)

private data class LiveMetric(
    val title: String,
    val value: String,
    val source: CapabilitySource,
    val available: Boolean = true
)

private sealed class LiveDataState {
    data object Waiting : LiveDataState()
    data object Loading : LiveDataState()
    data class Success(
        val metrics: List<LiveMetric>,
        val updatedAt: String,
        val note: String? = null,
        val cached: Boolean = false
    ) : LiveDataState()
    data class Error(val message: String) : LiveDataState()
}

/**
 * 当前槽位的数据预览。
 *
 * 直接使用当前编辑槽位的 slotId、API 地址、API Key 和模型，禁止再按 URL 搜索第一个配置。
 * MiMo 的账户数据不依赖模型 API 返回金额：只要地址已识别且网页登录有效，就可以单独预览。
 */
@Composable
fun SlotDataCapabilityCard(
    slotId: String,
    apiBase: String,
    apiKey: String,
    modelName: String,
    apiConnected: Boolean,
    authConnected: Boolean,
    webProfile: WebAuthProfile?
) {
    val context = LocalContext.current
    val service = detectService(apiBase, webProfile)
    val items = capabilityItems(service, apiConnected, authConnected)
    val accountPreviewReady = service == SlotServiceKind.MIMO &&
        authConnected &&
        apiBase.isNotBlank()
    val canRead = apiConnected || accountPreviewReady

    var refreshVersion by remember { mutableIntStateOf(0) }
    var liveState by remember(slotId, apiBase, apiKey, modelName) {
        mutableStateOf<LiveDataState>(LiveDataState.Waiting)
    }

    LaunchedEffect(
        slotId,
        apiBase,
        apiKey,
        modelName,
        canRead,
        authConnected,
        refreshVersion
    ) {
        if (!canRead) {
            liveState = LiveDataState.Waiting
        } else {
            liveState = LiveDataState.Loading
            liveState = withContext(Dispatchers.IO) {
                loadCurrentSlotData(
                    context = context,
                    slotId = slotId,
                    apiBase = apiBase,
                    apiKey = apiKey,
                    modelName = modelName,
                    service = service,
                    webProfile = webProfile
                )
            }
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("当前真实数据", style = MaterialTheme.typography.titleMedium)
            Text(
                text = when (service) {
                    SlotServiceKind.MIMO ->
                        "MiMo 模型 API 只负责连接模型；余额、金额、Token 和请求统计来自平台账户。"
                    else ->
                        "平台本次返回多少就显示多少；字段没返回不会补 0。"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = 10.dp)
            )

            LiveDataContent(
                state = liveState,
                canRead = canRead,
                service = service,
                authConnected = authConnected,
                onRefresh = { refreshVersion++ }
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

            Text("这个槽位还能展示什么", style = MaterialTheme.typography.titleMedium)
            Text(
                text = "云端数据随桌面小组件更新；本地测算只使用平台真实累计值。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = 10.dp)
            )

            items.forEachIndexed { index, item ->
                if (index > 0) HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))
                CapabilityRow(item)
            }
        }
    }
}

@Composable
private fun LiveDataContent(
    state: LiveDataState,
    canRead: Boolean,
    service: SlotServiceKind,
    authConnected: Boolean,
    onRefresh: () -> Unit
) {
    when (state) {
        LiveDataState.Waiting -> Text(
            text = when {
                service == SlotServiceKind.MIMO && !authConnected ->
                    "MiMo 金额和用量需要先登录平台账户；模型 API 本身不会返回这些数据。"
                canRead -> "等待读取当前真实数据。"
                else -> "完成 API 检测并选择模型后，这里会显示当前真实数值。"
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        LiveDataState.Loading -> Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            CircularProgressIndicator()
            Text("正在读取平台当前数据…")
        }

        is LiveDataState.Error -> {
            Text(
                text = "当前没有取得真实数值：${state.message}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error
            )
            Button(onClick = onRefresh, modifier = Modifier.padding(top = 10.dp)) {
                Text("重新读取")
            }
        }

        is LiveDataState.Success -> {
            if (state.cached) {
                Text(
                    text = "本次云端读取失败，下面显示的是上次成功缓存。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }

            state.metrics.forEachIndexed { index, metric ->
                if (index > 0) HorizontalDivider(modifier = Modifier.padding(vertical = 9.dp))
                LiveMetricRow(metric)
            }

            state.note?.takeIf { it.isNotBlank() }?.let { note ->
                Text(
                    text = note,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 10.dp)
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (state.cached) {
                        "缓存展示时间 ${state.updatedAt}"
                    } else {
                        "读取时间 ${state.updatedAt}"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Button(onClick = onRefresh) { Text("刷新真实数据") }
            }
        }
    }
}

@Composable
private fun LiveMetricRow(metric: LiveMetric) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(metric.title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = metric.value,
                style = MaterialTheme.typography.titleSmall,
                color = if (metric.available) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
        }
        SourceBadge(metric.source, Modifier.padding(top = 6.dp))
    }
}

@Composable
private fun CapabilityRow(item: CapabilityItem) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(item.title, style = MaterialTheme.typography.bodyLarge)
        Text(
            text = item.detail,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp)
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 7.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            SourceBadge(item.source)
            Text(
                text = item.state,
                style = MaterialTheme.typography.labelMedium,
                color = if (item.active) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
        }
    }
}

@Composable
private fun SourceBadge(source: CapabilitySource, modifier: Modifier = Modifier) {
    val containerColor: Color = when (source) {
        CapabilitySource.API -> MaterialTheme.colorScheme.primaryContainer
        CapabilitySource.ACCOUNT -> MaterialTheme.colorScheme.secondaryContainer
        CapabilitySource.LOCAL -> MaterialTheme.colorScheme.tertiaryContainer
        CapabilitySource.CACHE -> MaterialTheme.colorScheme.errorContainer
    }
    val contentColor: Color = when (source) {
        CapabilitySource.API -> MaterialTheme.colorScheme.onPrimaryContainer
        CapabilitySource.ACCOUNT -> MaterialTheme.colorScheme.onSecondaryContainer
        CapabilitySource.LOCAL -> MaterialTheme.colorScheme.onTertiaryContainer
        CapabilitySource.CACHE -> MaterialTheme.colorScheme.onErrorContainer
    }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(50),
        color = containerColor
    ) {
        Text(
            text = source.label,
            style = MaterialTheme.typography.labelSmall,
            color = contentColor,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
        )
    }
}

private fun loadCurrentSlotData(
    context: Context,
    slotId: String,
    apiBase: String,
    apiKey: String,
    modelName: String,
    service: SlotServiceKind,
    webProfile: WebAuthProfile?
): LiveDataState {
    val prefs = context.getSharedPreferences("api_config", Context.MODE_PRIVATE)
    val adapter = AdapterFactory.getAdapter(slotId, apiBase)
        ?: return LiveDataState.Error("当前服务还没有可读取余额或用量的适配器")

    val auth = loadPlatformAuthorization(prefs, slotId, webProfile)
    val data = try {
        HostRequestCoordinator.withHost(apiBase) { adapter.fetchData(
            AdapterRequest(
                apiBase = apiBase,
                modelApiKey = apiKey,
                modelName = modelName.takeIf { it.isNotBlank() },
                instanceId = slotId,
                backgroundAuthType = auth.authType,
                backgroundCredential = auth.authValue
            )
        ) }
    } catch (_: Exception) {
        return LiveDataState.Error("读取失败，请稍后重试")
    }

    if (!data.isSuccess || !data.isAvailable) {
        return LiveDataState.Error(
            data.statusText ?: data.errorMessage ?: "平台没有返回可展示数据"
        )
    }

    val metrics = buildLiveMetrics(service, data)
    if (metrics.isEmpty()) {
        return LiveDataState.Error("平台本次没有返回可展示数值")
    }

    return LiveDataState.Success(
        metrics = metrics,
        updatedAt = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date()),
        note = data.statusText,
        cached = data.isFallback
    )
}

private fun loadPlatformAuthorization(
    prefs: android.content.SharedPreferences,
    slotId: String,
    profile: WebAuthProfile?
): BackgroundAuthConfig {
    if (profile == null) return BackgroundAuthConfig()
    val local = BackgroundAuthRepository.load(prefs, slotId)
    return local.takeIf {
        it.enabled &&
            it.authType == profile.authType &&
            it.authValue.isNotBlank()
    } ?: BackgroundAuthConfig()
}

private fun buildLiveMetrics(
    service: SlotServiceKind,
    data: WidgetData
): List<LiveMetric> {
    val result = mutableListOf<LiveMetric>()
    val cachedSource = data.isFallback

    data.primaryMetric?.let { metric ->
        result.add(
            LiveMetric(
                title = cleanMetricLabel(metric.label),
                value = metric.value.ifBlank { "未返回" },
                source = if (cachedSource) CapabilitySource.CACHE else metricSource(service, metric.label),
                available = metric.value.isNotBlank()
            )
        )
    }

    data.usageMetrics.forEach { metric ->
        val label = cleanMetricLabel(metric.label)
        val value = metric.value.ifBlank {
            when {
                label.contains("统计中") -> "正在积累"
                label.contains("暂无") -> "暂无可计算数据"
                else -> "当前未返回数值"
            }
        }
        result.add(
            LiveMetric(
                title = if (label.contains("统计中")) "近期消耗" else label,
                value = value,
                source = if (cachedSource) CapabilitySource.CACHE else CapabilitySource.LOCAL,
                available = metric.value.isNotBlank()
            )
        )
    }

    if (data.percentage != null && !data.percentageLabel.isNullOrBlank()) {
        result.add(
            LiveMetric(
                title = cleanMetricLabel(data.percentageLabel.orEmpty()),
                value = "${data.percentage}%",
                source = if (cachedSource) CapabilitySource.CACHE else CapabilitySource.LOCAL
            )
        )
    }

    data.auxiliaryMetrics.forEach { metric ->
        result.add(
            LiveMetric(
                title = cleanMetricLabel(metric.label),
                value = metric.value.ifBlank { "未返回" },
                source = if (cachedSource) CapabilitySource.CACHE else metricSource(service, metric.label),
                available = metric.value.isNotBlank()
            )
        )
    }

    return result.distinctBy { "${it.title}|${it.value}|${it.source}" }
}

private fun metricSource(service: SlotServiceKind, rawLabel: String): CapabilitySource {
    if (rawLabel.trim().startsWith("网页·")) return CapabilitySource.ACCOUNT
    val label = cleanMetricLabel(rawLabel)
    return when (service) {
        SlotServiceKind.MIMO -> CapabilitySource.ACCOUNT
        SlotServiceKind.KIMI_NEW_API,
        SlotServiceKind.GENERIC -> CapabilitySource.API
        SlotServiceKind.DEEPSEEK -> {
            if (
                label.contains("本月") ||
                label.contains("累计消耗") ||
                label.contains("预计可用")
            ) CapabilitySource.ACCOUNT else CapabilitySource.API
        }
        SlotServiceKind.AIHUANGNIU -> {
            if (
                label == "余额" ||
                label.contains("累计充值") ||
                label.contains("并发") ||
                label.contains("账户状态") ||
                label.contains("最近活跃") ||
                label.contains("余额占充值")
            ) CapabilitySource.ACCOUNT else CapabilitySource.API
        }
    }
}

private fun cleanMetricLabel(raw: String): String {
    return raw.trim()
        .removePrefix("云端·")
        .removePrefix("本地·")
        .removePrefix("缓存·")
        .removePrefix("网页·")
        .ifBlank { "未命名数据" }
}

private fun detectService(apiBase: String, webProfile: WebAuthProfile?): SlotServiceKind {
    return when (webProfile?.profileId) {
        "mimo" -> SlotServiceKind.MIMO
        "deepseek" -> SlotServiceKind.DEEPSEEK
        "aihuangniu" -> SlotServiceKind.AIHUANGNIU
        else -> {
            val normalized = apiBase.trim().lowercase()
            when {
                normalized.contains("xiaomimimo.com") -> SlotServiceKind.MIMO
                normalized.contains("deepseek.com") -> SlotServiceKind.DEEPSEEK
                normalized.contains("aihuangniu.com") -> SlotServiceKind.AIHUANGNIU
                normalized.contains("coolyeah.net") || normalized.contains("kimi") -> {
                    SlotServiceKind.KIMI_NEW_API
                }
                else -> SlotServiceKind.GENERIC
            }
        }
    }
}

private fun capabilityItems(
    service: SlotServiceKind,
    apiConnected: Boolean,
    authConnected: Boolean
): List<CapabilityItem> {
    return when (service) {
        SlotServiceKind.KIMI_NEW_API -> listOf(
            CapabilityItem(
                "剩余次数、总额、已用次数、调用次数",
                "由次数卡接口直接返回。",
                CapabilitySource.API,
                apiState(apiConnected),
                apiConnected
            ),
            CapabilityItem(
                "Billing 额度与 Billing 用量",
                "平台提供时自动补充，不强行解释为货币。",
                CapabilitySource.API,
                apiState(apiConnected),
                apiConnected
            ),
            CapabilityItem(
                "近 1／6／12／24 小时调用变化",
                "手机保存真实累计调用数后计算。",
                CapabilitySource.LOCAL,
                localState(apiConnected),
                apiConnected
            )
        )

        SlotServiceKind.MIMO -> listOf(
            CapabilityItem(
                "余额、赠送余额、现金余额、冻结余额、透支",
                "登录 MiMo 平台账户后由余额接口直接返回；模型 API 不提供金额。",
                CapabilitySource.ACCOUNT,
                accountState(authConnected),
                authConnected
            ),
            CapabilityItem(
                "本月与累计消耗、累计请求、累计 Token",
                "登录后由平台账户用量接口直接返回。",
                CapabilitySource.ACCOUNT,
                accountState(authConnected),
                authConnected
            ),
            CapabilityItem(
                "输入、输出、缓存 Token、Web 搜索、RPM、TPM、并发",
                "平台返回什么就展示什么，真实 0 也会保留。",
                CapabilitySource.ACCOUNT,
                accountState(authConnected),
                authConnected
            ),
            CapabilityItem(
                "近 1／6／12／24 小时请求、Token、金额",
                "手机根据账户真实累计值计算。",
                CapabilitySource.LOCAL,
                localState(authConnected),
                authConnected
            )
        )

        SlotServiceKind.DEEPSEEK -> listOf(
            CapabilityItem(
                "总余额、充值余额、赠送余额",
                "使用 DeepSeek API Key 调用官方余额接口返回。",
                CapabilitySource.API,
                apiState(apiConnected),
                apiConnected
            ),
            CapabilityItem(
                "本月消耗、累计消耗、本月 Token、预计可用 Token",
                "登录 DeepSeek 平台账户后由账户汇总接口返回。",
                CapabilitySource.ACCOUNT,
                accountState(authConnected),
                authConnected
            ),
            CapabilityItem(
                "近 1／6／12／24 小时 Token 与金额",
                "手机根据真实累计 Token 和金额计算。",
                CapabilitySource.LOCAL,
                localState(authConnected),
                authConnected
            )
        )

        SlotServiceKind.AIHUANGNIU -> listOf(
            CapabilityItem(
                "累计请求、累计 Token、累计实际消耗",
                "只使用模型 API Key 请求用量接口，不依赖网页登录。",
                CapabilitySource.API,
                apiState(apiConnected),
                apiConnected
            ),
            CapabilityItem(
                "余额、累计充值、并发、账户状态、最近活跃",
                "登录爱黄牛平台账户后由账户资料接口返回。",
                CapabilitySource.ACCOUNT,
                accountState(authConnected),
                authConnected
            ),
            CapabilityItem(
                "近 1／6／12／24 小时调用、Token、金额",
                "只要 API 用量返回成功即可开始积累。",
                CapabilitySource.LOCAL,
                localState(apiConnected),
                apiConnected
            )
        )

        SlotServiceKind.GENERIC -> listOf(
            CapabilityItem(
                "模型列表与连接状态",
                "兼容 OpenAI 的服务可以检测并选择模型。",
                CapabilitySource.API,
                apiState(apiConnected),
                apiConnected
            ),
            CapabilityItem(
                "余额、用量和账单数据",
                "当前地址尚未匹配到已验证平台，不猜测或伪造指标。",
                CapabilitySource.API,
                "当前服务未识别",
                false
            )
        )
    }
}

private fun apiState(connected: Boolean): String {
    return if (connected) "已配置 · 自动更新" else "配置 API 后可用"
}

private fun accountState(connected: Boolean): String {
    return if (connected) "授权已保存 · 自动更新" else "登录后可用"
}

private fun localState(sourceConnected: Boolean): String {
    return if (sourceConnected) "积累后自动生成" else "连接来源后生成"
}
