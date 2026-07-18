package com.java.myapplication.discovery

import org.json.JSONArray
import org.json.JSONObject

data class DashboardMetricRecipe(
    val type: String,
    val label: String,
    val endpoint: String,
    val jsonPath: String,
    val valueType: String,
    val unit: String
)

data class DashboardRequestRecipe(
    val pagePurpose: String,
    val dashboardUrl: String,
    val origin: String,
    val endpoints: List<String>,
    val metrics: List<DashboardMetricRecipe>,
    val createdAt: Long,
    val lastSuccessAt: Long
) {
    fun toJson(): JSONObject = JSONObject()
        .put("version", 1)
        .put("pagePurpose", pagePurpose)
        .put("dashboardUrl", dashboardUrl)
        .put("origin", origin)
        .put("endpoints", JSONArray(endpoints))
        .put(
            "metrics",
            JSONArray(metrics.map { metric ->
                JSONObject()
                    .put("type", metric.type)
                    .put("label", metric.label)
                    .put("endpoint", metric.endpoint)
                    .put("jsonPath", metric.jsonPath)
                    .put("valueType", metric.valueType)
                    .put("unit", metric.unit)
            })
        )
        .put("createdAt", createdAt)
        .put("lastSuccessAt", lastSuccessAt)

    companion object {
        fun fromJson(root: JSONObject): DashboardRequestRecipe? {
            if (root.optInt("version") != 1) return null
            val endpointArray = root.optJSONArray("endpoints") ?: return null
            val endpoints = buildList {
                for (index in 0 until endpointArray.length()) {
                    endpointArray.optString(index).takeIf { it.isNotBlank() }?.let(::add)
                }
            }
            val metricArray = root.optJSONArray("metrics") ?: return null
            val metrics = buildList {
                for (index in 0 until metricArray.length()) {
                    val metric = metricArray.optJSONObject(index) ?: continue
                    add(
                        DashboardMetricRecipe(
                            type = metric.optString("type"),
                            label = metric.optString("label"),
                            endpoint = metric.optString("endpoint"),
                            jsonPath = metric.optString("jsonPath"),
                            valueType = metric.optString("valueType"),
                            unit = metric.optString("unit")
                        )
                    )
                }
            }
            return DashboardRequestRecipe(
                pagePurpose = root.optString("pagePurpose"),
                dashboardUrl = root.optString("dashboardUrl"),
                origin = root.optString("origin"),
                endpoints = endpoints,
                metrics = metrics,
                createdAt = root.optLong("createdAt"),
                lastSuccessAt = root.optLong("lastSuccessAt")
            ).takeIf { DashboardRecipeRules.validate(it) == null }
        }
    }
}

data class DashboardRecipeDraft(
    val recipe: DashboardRequestRecipe? = null,
    val error: String? = null
)

object DashboardRecipeRules {
    private const val MAX_ENDPOINTS = 4
    private const val MAX_METRICS = 40
    private val allowedMetricTypes = setOf(
        "balance", "usage", "quota", "tokens", "requests",
        "subscription", "reset_time", "other"
    )
    private val allowedValueTypes = setOf("number", "string", "boolean", "object", "array")

    fun create(
        pagePurpose: String,
        dashboardUrl: String,
        lockedOrigin: String,
        metrics: List<DashboardMetricRecipe>,
        candidates: List<CapturedResponseCandidate>,
        now: Long
    ): DashboardRecipeDraft {
        if (metrics.isEmpty()) return DashboardRecipeDraft(error = "没有通过本机核对的字段，不能保存配方")
        val endpoints = metrics.map { it.endpoint }.distinct()
        if (endpoints.size > MAX_ENDPOINTS) {
            return DashboardRecipeDraft(error = "首版最多支持 $MAX_ENDPOINTS 个数据接口")
        }
        for (endpoint in endpoints) {
            val matching = candidates.filter { it.endpoint == endpoint }
            if (matching.isEmpty()) return DashboardRecipeDraft(error = "数据接口不属于本次真实捕获")
            if (matching.any { it.method != "GET" }) {
                return DashboardRecipeDraft(error = "首版只能保存 GET 接口；这个站点需要后续适配")
            }
            if (matching.any { it.hadQuery }) {
                return DashboardRecipeDraft(error = "首版不能保存带查询参数的接口；避免误存登录信息")
            }
            if (DashboardDiscoveryRules.originOf(endpoint) != lockedOrigin) {
                return DashboardRecipeDraft(error = "首版只能保存与当前仪表盘同站点的数据接口")
            }
        }
        val recipe = DashboardRequestRecipe(
            pagePurpose = pagePurpose.trim().take(200).ifBlank { "网页仪表盘" },
            dashboardUrl = DashboardDiscoveryRules.withoutQuery(dashboardUrl, lockedOrigin),
            origin = lockedOrigin,
            endpoints = endpoints,
            metrics = metrics,
            createdAt = now,
            lastSuccessAt = 0L
        )
        return validate(recipe)?.let { DashboardRecipeDraft(error = it) }
            ?: DashboardRecipeDraft(recipe = recipe)
    }

    fun validate(recipe: DashboardRequestRecipe): String? {
        if (DashboardDiscoveryRules.originOf(recipe.dashboardUrl) != recipe.origin) {
            return "仪表盘地址与保存站点不一致"
        }
        if (recipe.endpoints.isEmpty() || recipe.endpoints.size > MAX_ENDPOINTS) {
            return "请求配方的数据接口数量无效"
        }
        if (recipe.metrics.isEmpty() || recipe.metrics.size > MAX_METRICS) {
            return "请求配方的字段数量无效"
        }
        for (endpoint in recipe.endpoints) {
            if (!DashboardDiscoveryRules.isHttpsUrl(endpoint) ||
                DashboardDiscoveryRules.originOf(endpoint) != recipe.origin ||
                DashboardDiscoveryRules.hasQueryOrFragment(endpoint)
            ) {
                return "请求配方包含不安全的数据接口"
            }
        }
        for (metric in recipe.metrics) {
            if (metric.type !in allowedMetricTypes || metric.valueType !in allowedValueTypes) {
                return "请求配方包含不支持的字段类型"
            }
            if (metric.endpoint !in recipe.endpoints || DashboardJsonPathValidator.canonicalize(metric.jsonPath) == null) {
                return "请求配方包含无效字段路径"
            }
        }
        return null
    }
}
