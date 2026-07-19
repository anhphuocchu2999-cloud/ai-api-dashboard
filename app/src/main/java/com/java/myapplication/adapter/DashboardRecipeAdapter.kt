package com.java.myapplication.adapter

import android.content.Context
import com.java.myapplication.adapter.auth.BackgroundAuthType
import com.java.myapplication.adapter.capability.DataCapability
import com.java.myapplication.adapter.capability.DataSourceType
import com.java.myapplication.adapter.capability.ProviderCapabilityProfile
import com.java.myapplication.config.InstanceKeyResolver
import com.java.myapplication.discovery.DashboardRecipeClient
import com.java.myapplication.discovery.DashboardRecipeRepository
import com.java.myapplication.discovery.DashboardReplayMetric
import com.java.myapplication.discovery.DashboardRequestRecipe
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/** Adapter for the one user-validated dashboard recipe from Stage 8F-P3. */
internal class DashboardRecipeAdapter(context: Context) : PlatformAdapter {
    private val repository = DashboardRecipeRepository(context.applicationContext)
    private val client = DashboardRecipeClient()

    override val platformName: String = "网页仪表盘"

    override val capabilityProfile = ProviderCapabilityProfile(
        modelApiKeyRequired = false,
        backgroundAuthType = BackgroundAuthType.COOKIE,
        sources = setOf(DataSourceType.WEB_AUTH, DataSourceType.BILLING),
        capabilities = setOf(
            DataCapability.BALANCE,
            DataCapability.QUOTA,
            DataCapability.USAGE,
            DataCapability.REQUESTS,
            DataCapability.TOKENS,
            DataCapability.SUBSCRIPTION
        )
    )

    override fun detect(apiBase: String, apiKey: String): Boolean = false

    override fun fetchData(request: AdapterRequest): WidgetData {
        val saved = repository.load()
            ?: return WidgetData.error(platformName, "已保存的仪表盘配方不可用，请重新识别")
        val requestedInstance = InstanceKeyResolver.canonicalInstanceId(request.instanceId)
        if (saved.recipe.boundInstanceId != requestedInstance) {
            return WidgetData.error(platformName, "仪表盘配方没有绑定到当前卡片")
        }

        return try {
            val replay = client.fetch(
                saved.recipe,
                saved.cookiesByEndpoint,
                saved.replayHeadersByEndpoint
            )
            val projection = DashboardRecipeWidgetMapper.project(replay.metrics)
            WidgetData(
                platformName = platformName,
                modelName = request.modelName,
                primaryMetric = projection.primary.toDisplayMetric(),
                usageMetrics = projection.usage.map(DashboardProjectedMetric::toDisplayMetric),
                auxiliaryMetrics = projection.auxiliary.map(DashboardProjectedMetric::toDisplayMetric),
                statusText = "网页仪表盘直连",
                isSuccess = true,
                isAvailable = true
            )
        } catch (_: SocketTimeoutException) {
            WidgetData.error(platformName, "连接超时")
        } catch (_: UnknownHostException) {
            WidgetData.error(platformName, "域名解析失败")
        } catch (_: ConnectException) {
            WidgetData.error(platformName, "连接失败")
        } catch (_: IOException) {
            WidgetData.error(platformName, "网络异常")
        } catch (error: IllegalArgumentException) {
            WidgetData.error(platformName, error.message ?: "请求配方无效")
        } catch (error: IllegalStateException) {
            WidgetData.error(platformName, error.message ?: "仪表盘数据不可用")
        } catch (_: Exception) {
            WidgetData.error(platformName, "仪表盘数据不可用")
        }
    }

    override fun fetchData(apiBase: String, apiKey: String, modelName: String?): WidgetData =
        WidgetData.error(platformName, "仪表盘配方需要明确的模型实例")

    companion object {
        fun cacheNamespace(recipe: DashboardRequestRecipe): String = buildString {
            append("dashboard-recipe:")
            append(recipe.createdAt)
            append(':')
            append(recipe.origin)
            recipe.endpoints.forEach { endpoint -> append(':').append(endpoint) }
        }
    }
}

internal data class DashboardProjectedMetric(
    val label: String,
    val value: String
) {
    fun toDisplayMetric() = WidgetData.DisplayMetric(label, value)
}

internal data class DashboardWidgetProjection(
    val primary: DashboardProjectedMetric,
    val usage: List<DashboardProjectedMetric>,
    val auxiliary: List<DashboardProjectedMetric>
)

internal object DashboardRecipeWidgetMapper {
    private val primaryPriority = mapOf(
        "balance" to 0,
        "quota" to 1,
        "tokens" to 2,
        "usage" to 3,
        "requests" to 4,
        "subscription" to 5,
        "reset_time" to 6,
        "other" to 7
    )
    private val usageTypes = setOf("usage", "requests", "tokens")

    fun project(metrics: List<DashboardReplayMetric>): DashboardWidgetProjection {
        require(metrics.isNotEmpty()) { "仪表盘没有返回可展示字段" }
        val primaryIndex = metrics.indices.minByOrNull { index ->
            primaryPriority[metrics[index].type] ?: Int.MAX_VALUE
        } ?: 0
        val primary = metrics[primaryIndex].projected()
        val remaining = metrics.filterIndexed { index, _ -> index != primaryIndex }
        return DashboardWidgetProjection(
            primary = primary,
            usage = remaining.filter { it.type in usageTypes }.map { it.projected() },
            auxiliary = remaining.filterNot { it.type in usageTypes }.map { it.projected() }
        )
    }

    private fun DashboardReplayMetric.projected() = DashboardProjectedMetric(
        label = compactLabel(type, label),
        value = formatValue(value, unit)
    )

    private fun compactLabel(type: String, rawLabel: String): String {
        val label = rawLabel.trim().ifBlank { type }
        if (label.length <= 10) return label
        val lower = label.lowercase()
        val remaining = listOf("剩余", "可用", "remaining", "remain", "available").any(lower::contains)
        val used = listOf("已用", "使用", "used", "usage").any(lower::contains)
        return when (type) {
            "balance" -> when {
                remaining -> "可用余额"
                used -> "已用余额"
                else -> "余额"
            }
            "quota" -> when {
                remaining -> "剩余额度"
                used -> "已用额度"
                else -> "额度"
            }
            "tokens" -> when {
                remaining -> "剩余 Token"
                used -> "已用 Token"
                else -> "Token"
            }
            "usage" -> "用量"
            "requests" -> "请求"
            "subscription" -> "套餐"
            "reset_time" -> "重置时间"
            else -> label.take(10) + "…"
        }
    }

    private fun formatValue(rawValue: String, rawUnit: String): String {
        val value = rawValue.trim()
        val unit = rawUnit.trim()
        val normalizedUnit = unit.lowercase()
        if (
            unit.isBlank() ||
            normalizedUnit in setOf("额度单位", "原始额度单位", "quota unit", "raw quota unit")
        ) return value
        return when (unit.uppercase()) {
            "USD" -> if (value.startsWith('$')) value else "\$$value"
            "CNY", "RMB" -> if (value.startsWith('¥') || value.startsWith('￥')) value else "¥$value"
            "%" -> if (value.endsWith('%')) value else "$value%"
            else -> "$value $unit"
        }
    }
}
