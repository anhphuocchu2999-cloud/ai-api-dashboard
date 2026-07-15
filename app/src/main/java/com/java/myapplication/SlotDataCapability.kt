package com.java.myapplication

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.java.myapplication.webauth.WebAuthProfile

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

/**
 * 设置页的数据能力说明。
 *
 * 这里只展示当前代码已经真实接入或明确支持的能力，不发起额外网络请求，
 * 不展示演示数值，也不把“登录后可用”冒充为当前已可用。
 */
@Composable
fun SlotDataCapabilityCard(
    apiBase: String,
    apiConnected: Boolean,
    authConnected: Boolean,
    webProfile: WebAuthProfile?
) {
    val service = detectService(apiBase, webProfile)
    val items = capabilityItems(service, apiConnected, authConnected)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "这个槽位能展示什么",
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = "云端数据会随桌面小组件自动更新；本地测算只使用平台真实累计值。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )
            Text(
                text = "每 30 分钟自动刷新，也可以点击桌面小组件立即刷新。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp, bottom = 10.dp)
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
private fun SourceBadge(source: CapabilitySource) {
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
                normalized.isBlank() -> SlotServiceKind.GENERIC
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
