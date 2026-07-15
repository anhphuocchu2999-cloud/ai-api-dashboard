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
import com.java.myapplication.adapter.WidgetData
import com.java.myapplication.adapter.auth.BackgroundAuthRepository
import com.java.myapplication.config.ApiAccountConfig
import com.java.myapplication.config.ConfigRepository
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
    LOCAL("本地测算")
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
        val note: String? = null
    ) : LiveDataState()
    data class Error(val message: String) : LiveDataState()
}

/**
 * 设置页的数据能力与当前真实值。
 *
 * 进入已配置槽位时会通过与桌面 Widget 相同的 Adapter 请求一次真实数据。
 * 页面不展示演示值；接口未返回的项目会明确标记为未返回、登录后可用或仍在积累。
 */
@Composable
fun SlotDataCapabilityCard(
    apiBase: String,
    apiConnected: Boolean,
    authConnected: Boolean,
    webProfile: WebAuthProfile?
) {
    val context = LocalContext.current
    val service = detectService(apiBase, webProfile)
    val items = capabilityItems(service, apiConnected, authConnected)
    var refreshVersion by remember { mutableIntStateOf(0) }
    var liveState by remember(apiBase) { mutableStateOf<LiveDataState>(LiveDataState.Waiting) }

    LaunchedEffect(apiBase, apiConnected, authConnected, refreshVersion) {
        if (!apiConnected) {
            liveState = LiveDataState.Waiting
        } else {
            liveState = LiveDataState.Loading
            liveState = withContext(Dispatchers.IO) {
                loadCurrentSlotData(context, apiBase, service)
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
            Text(
                text = "当前真实数据",
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = "这里直接请求当前账户。返回多少就显示多少；没有返回的不会补数字。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = 10.dp)
            )

            LiveDataContent(
                state = liveState,
                apiConnected = apiConnected,
                onRefresh = { refreshVersion++ }
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

            Text(
                text = "这个槽位还能展示什么",
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = "云端数据每 30 分钟随桌面小组件更新；本地测算只使用平台真实累计值。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = 10.dp)
            )

            items.forEachIndexed { index, item ->
                if (index > 0) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))
                }
                CapabilityRow(item)
            }
        }
    }
}

@Composable
private fun LiveDataContent(
    state: LiveDataState,
    apiConnected: Boolean,
    onRefresh: () -> Unit
) {
    when (state) {
        LiveDataState.Waiting -> {
            Text(
                text = if (apiConnected) {
                    "等待读取当前真实数据。"
                } else {
                    "完成 API 检测并选择模型后，这里会显示当前真实数值。"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        LiveDataState.Loading -> {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                CircularProgressIndicator()
                Text("正在读取平台当前数据…")
            }
        }

        is LiveDataState.Error -> {
            Text(
                text = "当前没有取得真实数值：${state.message}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error
            )
            Button(
                onClick = onRefresh,
                modifier = Modifier.padding(top = 10.dp)
            ) {
                Text("重新读取")
            }
        }

        is LiveDataState.Success -> {
            state.metrics.forEachIndexed { index, metric ->
                if (index > 0) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 9.dp))
                }
                LiveMetricRow(metric)
            }
            state.note?.let { note ->
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
                    text = "读取时间 ${state.updatedAt}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Button(onClick = onRefresh) {
                    Text("刷新真实数据")
                }
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
            Text(
                text = metric.title,
                style = MaterialTheme.typography.bodyLarge
            )
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
        SourceBadge(
            source = metric.source,
            modifier = Modifier.padding(top = 6.dp)
        )
    }
}

@Composable
private fun CapabilityRow(item: CapabilityItem) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = item.title,
            style = MaterialTheme.typography.bodyLarge
        )
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
private fun SourceBadge(
    source: CapabilitySource,
    modifier: Modifier = Modifier
) {
    val containerColor: Color = when (source) {
        CapabilitySource.API -> MaterialTheme.colorScheme.primaryContainer
        CapabilitySource.ACCOUNT -> MaterialTheme.colorScheme.secondaryContainer
        CapabilitySource.LOCAL -> MaterialTheme.colorScheme.tertiaryContainer
    }
    val contentColor: Color = when (source) {
        CapabilitySource.API -> MaterialTheme.colorScheme.onPrimaryContainer
        CapabilitySource.ACCOUNT -> MaterialTheme.colorScheme.onSecondaryContainer
        CapabilitySource.LOCAL -> MaterialTheme.colorScheme.onTertiaryContainer
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
    apiBase: String,
    service: SlotServiceKind
): LiveDataState {
    val prefs = context.getSharedPreferences("api_config", Context.MODE_PRIVATE)
    val normalizedBase = normalizeApiBase(apiBase)
    val matching = ConfigRepository.loadAllConfigs(prefs)
        .filter { config ->
            config.enabled &&
                config.apiKey.isNotBlank() &&
                config.model.isNotBlank() &&
                normalizeApiBase(config.apiBase) == normalizedBase
        }

    val config = matching.firstOrNull()
        ?: return LiveDataState.Error("没有找到这个槽位的完整 API 配置")

    val adapter = AdapterFactory.getAdapter(config.id, config.apiBase)
        ?: return LiveDataState.Error("当前服务还没有可读取余额或用量的适配器")
    val auth = BackgroundAuthRepository.load(prefs, config.id)

    val data = try {
        adapter.fetchData(
            AdapterRequest(
                apiBase = config.apiBase,
                modelApiKey = config.apiKey,
                modelName = config.model,
                backgroundAuthType = auth.authType,
                backgroundCredential = auth.authValue
            )
        )
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

    val note = if (matching.size > 1) {
        "检测到多个槽位使用同一 API 地址，当前展示第一个完整配置返回的数据。"
    } else {
        null
    }

    return LiveDataState.Success(
        metrics = metrics,
        updatedAt = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date()),
        note = note
    )
}

private fun buildLiveMetrics(
    service: SlotServiceKind,
    data: WidgetData
): List<LiveMetric> {
    val result = mutableListOf<LiveMetric>()

    data.primaryMetric?.let { metric ->
        result.add(
            LiveMetric(
                title = cleanMetricLabel(metric.label),
                value = metric.value.ifBlank { "未返回" },
                source = primarySource(service),
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
                source = CapabilitySource.LOCAL,
                available = metric.value.isNotBlank()
            )
        )
    }

    if (data.percentage != null && !data.percentageLabel.isNullOrBlank()) {
        result.add(
            LiveMetric(
                title = cleanMetricLabel(data.percentageLabel.orEmpty()),
                value = "${data.percentage}%",
                source = CapabilitySource.LOCAL
            )
        )
    }

    data.auxiliaryMetrics.forEach { metric ->
        result.add(
            LiveMetric(
                title = cleanMetricLabel(metric.label),
                value = metric.value.ifBlank { "未返回" },
                source = auxiliarySource(service, metric.label),
                available = metric.value.isNotBlank()
            )
        )
    }

    return result.distinctBy { metric ->
        "${metric.title}|${metric.value}|${metric.source}"
    }
}

private fun primarySource(service: SlotServiceKind): CapabilitySource {
    return when (service) {
        SlotServiceKind.MIMO,
        SlotServiceKind.AIHUANGNIU -> CapabilitySource.ACCOUNT
        SlotServiceKind.KIMI_NEW_API,
        SlotServiceKind.DEEPSEEK,
        SlotServiceKind.GENERIC -> CapabilitySource.API
    }
}

private fun auxiliarySource(
    service: SlotServiceKind,
    rawLabel: String
): CapabilitySource {
    val label = cleanMetricLabel(rawLabel)
    return when (service) {
        SlotServiceKind.MIMO -> CapabilitySource.ACCOUNT
        SlotServiceKind.KIMI_NEW_API,
        SlotServiceKind.GENERIC -> CapabilitySource.API
        SlotServiceKind.DEEPSEEK -> {
            if (
                label.contains("预计可用") ||
                label.contains("本月") ||
                label.contains("累计消耗")
            ) {
                CapabilitySource.ACCOUNT
            } else {
                CapabilitySource.API
            }
        }
        SlotServiceKind.AIHUANGNIU -> {
            if (
                label.contains("累计 Token") ||
                label.contains("累计调用") ||
                label.contains("累计消耗")
            ) {
                CapabilitySource.API
            } else {
                CapabilitySource.ACCOUNT
            }
        }
    }
}

private fun cleanMetricLabel(raw: String): String {
    return raw.trim()
        .removePrefix("云端·")
        .removePrefix("本地·")
        .removePrefix("缓存·")
        .ifBlank { "未命名数据" }
}

private fun normalizeApiBase(apiBase: String): String {
    return apiBase.trim().trimEnd('/').lowercase()
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
        SlotServiceKind.KIMI_NEW_API -> kimiItems(apiConnected)
        SlotServiceKind.MIMO -> mimoItems(authConnected)
        SlotServiceKind.DEEPSEEK -> deepSeekItems(apiConnected, authConnected)
        SlotServiceKind.AIHUANGNIU -> aihuangniuItems(apiConnected, authConnected)
        SlotServiceKind.GENERIC -> genericItems(apiConnected)
    }
}

private fun kimiItems(apiConnected: Boolean): List<CapabilityItem> = listOf(
    CapabilityItem(
        title = "剩余次数、总额、已用次数、调用次数",
        detail = "由次数卡接口直接返回，可作为桌面主指标和辅助数据。",
        source = CapabilitySource.API,
        state = apiState(apiConnected),
        active = apiConnected
    ),
    CapabilityItem(
        title = "Billing 额度与 Billing 用量",
        detail = "平台提供时自动补充；不强行解释为某种货币。",
        source = CapabilitySource.API,
        state = apiState(apiConnected),
        active = apiConnected
    ),
    CapabilityItem(
        title = "近 1／6／12／24 小时调用变化",
        detail = "手机保存多次真实累计调用数，再按时间窗口计算。",
        source = CapabilitySource.LOCAL,
        state = localState(apiConnected),
        active = apiConnected
    )
)

private fun mimoItems(authConnected: Boolean): List<CapabilityItem> = listOf(
    CapabilityItem(
        title = "余额、赠送余额、现金余额、冻结余额、可用透支",
        detail = "登录 MiMo 平台账户后，由余额接口直接返回。",
        source = CapabilitySource.ACCOUNT,
        state = accountState(authConnected),
        active = authConnected
    ),
    CapabilityItem(
        title = "累计金额、累计请求、累计 Token",
        detail = "登录后由用量接口直接返回，并持续自动更新。",
        source = CapabilitySource.ACCOUNT,
        state = accountState(authConnected),
        active = authConnected
    ),
    CapabilityItem(
        title = "缓存 Token、Web 搜索、RPM、TPM、并发",
        detail = "属于账户用量和限流信息，可在辅助动态位轮播。",
        source = CapabilitySource.ACCOUNT,
        state = accountState(authConnected),
        active = authConnected
    ),
    CapabilityItem(
        title = "近 1／6／12／24 小时请求、Token、金额",
        detail = "手机根据账户返回的真实累计值计算，不是估算。",
        source = CapabilitySource.LOCAL,
        state = localState(authConnected),
        active = authConnected
    )
)

private fun deepSeekItems(
    apiConnected: Boolean,
    authConnected: Boolean
): List<CapabilityItem> = listOf(
    CapabilityItem(
        title = "总余额、充值余额、赠送余额",
        detail = "使用 DeepSeek API Key 调用官方余额接口直接返回。",
        source = CapabilitySource.API,
        state = apiState(apiConnected),
        active = apiConnected
    ),
    CapabilityItem(
        title = "本月消耗、累计消耗",
        detail = "登录 DeepSeek 平台账户后，由账户汇总接口直接返回。",
        source = CapabilitySource.ACCOUNT,
        state = accountState(authConnected),
        active = authConnected
    ),
    CapabilityItem(
        title = "本月 Token、预计可用 Token",
        detail = "登录后由账户汇总接口直接返回，可作为辅助数据。",
        source = CapabilitySource.ACCOUNT,
        state = accountState(authConnected),
        active = authConnected
    ),
    CapabilityItem(
        title = "近 1／6／12／24 小时 Token 与金额",
        detail = "手机根据真实累计 Token 和金额计算；不虚构调用次数。",
        source = CapabilitySource.LOCAL,
        state = localState(authConnected),
        active = authConnected
    )
)

private fun aihuangniuItems(
    apiConnected: Boolean,
    authConnected: Boolean
): List<CapabilityItem> = listOf(
    CapabilityItem(
        title = "累计请求、累计 Token、累计实际消耗",
        detail = "使用模型 API Key 调用用量接口直接返回。",
        source = CapabilitySource.API,
        state = apiState(apiConnected),
        active = apiConnected
    ),
    CapabilityItem(
        title = "余额、累计充值",
        detail = "登录爱黄牛平台账户后，由账户资料接口直接返回。",
        source = CapabilitySource.ACCOUNT,
        state = accountState(authConnected),
        active = authConnected
    ),
    CapabilityItem(
        title = "并发上限、账户状态、最近活跃",
        detail = "登录后可作为账户辅助信息展示。",
        source = CapabilitySource.ACCOUNT,
        state = accountState(authConnected),
        active = authConnected
    ),
    CapabilityItem(
        title = "近 1／6／12／24 小时调用、Token、金额",
        detail = "手机根据真实累计请求、Token 和金额计算。",
        source = CapabilitySource.LOCAL,
        state = if (apiConnected && authConnected) {
            "积累后自动生成"
        } else {
            "连接 API 并登录后生成"
        },
        active = apiConnected && authConnected
    )
)

private fun genericItems(apiConnected: Boolean): List<CapabilityItem> = listOf(
    CapabilityItem(
        title = "模型列表与连接状态",
        detail = "所有兼容 OpenAI 的服务都可以先检测并选择模型。",
        source = CapabilitySource.API,
        state = apiState(apiConnected),
        active = apiConnected
    ),
    CapabilityItem(
        title = "余额、用量和账单数据",
        detail = "当前地址尚未匹配到已验证的平台，系统不会猜测或伪造这些指标。",
        source = CapabilitySource.API,
        state = "当前服务未识别",
        active = false
    )
)

private fun apiState(connected: Boolean): String {
    return if (connected) "已接入 · 自动更新" else "连接 API 后可用"
}

private fun accountState(connected: Boolean): String {
    return if (connected) "已接入 · 自动更新" else "登录后可用"
}

private fun localState(sourceConnected: Boolean): String {
    return if (sourceConnected) "积累后自动生成" else "连接来源后生成"
}
