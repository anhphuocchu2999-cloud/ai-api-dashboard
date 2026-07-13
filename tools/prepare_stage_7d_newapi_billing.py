from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def read(path: str) -> str:
    return (ROOT / path).read_text(encoding="utf-8")


def write(path: str, content: str) -> None:
    target = ROOT / path
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(content.rstrip() + "\n", encoding="utf-8")


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected exactly 1 match, found {count}")
    return text.replace(old, new, 1)


def append_once(path: str, marker: str, content: str) -> None:
    current = read(path)
    if marker in current:
        raise RuntimeError(f"{path}: marker already exists: {marker}")
    write(path, current.rstrip() + "\n\n" + content.strip())


required = [
    "AGENTS.md",
    "PROJECT.md",
    "AI_HANDOFF.md",
    "DEVELOPMENT_LOG.md",
    "app/src/main/java/com/java/myapplication/adapter/NewApiAdapter.kt",
    "app/src/main/java/com/java/myapplication/adapter/capability/DataSourceType.kt",
]
for path in required:
    if not (ROOT / path).exists():
        raise RuntimeError(f"Required file missing: {path}")

# 1) Product design first: make Billing the third real data source for the currently verified Kimi/NewAPI path.
append_once(
    "PROJECT.md",
    "## Stage 7D：Kimi / NewAPI Billing 数据源真实接入",
    r'''---

## Stage 7D：Kimi / NewAPI Billing 数据源真实接入

### 目标

在 Stage 7C 已建立的 `API / 网页授权 / Billing` 统一能力模型上，把当前已经有接口证据的 Kimi / NewAPI Billing 路径真正接入数据层。

本阶段真实请求：

```text
GET /v1/dashboard/billing/subscription
GET /v1/dashboard/billing/usage
Authorization: Bearer <模型 API Key>
```

当前已知真实字段：

- `soft_limit_usd`，兼容旧返回字段 `soft_limit`
- `total_usage`

`total_usage` 按项目既有 Billing 探测语义以“美分 → 美元”换算后展示；不得把原始美分数直接冒充美元。

### 数据合并规则

1. 原 `/api/usage/token` 次数卡路径继续保留，现有“剩余次数 / 调用次数 / 本地近期用量”不得回归。
2. Billing 请求与原次数卡请求相互独立：
   - 原次数卡成功 + Billing 成功：合并展示。
   - 原次数卡成功 + Billing 失败：继续显示原次数卡数据，不因 Billing 失败降级。
   - 原次数卡失败 + Billing 成功：允许返回 Billing 数据，证明 Billing 是独立数据来源，不只是能力标签。
   - 两条路径都失败：返回原真实错误。
3. Billing 成功时，Kimi / NewApiAdapter 的 `capabilityProfile.sources` 才加入 `DataSourceType.BILLING`。
4. Billing 数据通过现有 `WidgetData` 返回，不建立第二套 Widget 数据结构。
5. 同一 host 的连续 HTTP 请求必须串行，间隔至少 500ms。

### Widget 展示

- 现有核心指标继续优先保留“剩余次数”。
- Billing 成功时，在辅助轮播数据中增加一条真实 Billing 指标：
  - `Billing 额度 $X · 已用 $Y`
- 如果只取得其中一个真实字段，只显示实际取得的字段。
- 不伪造余额、Token、请求次数或使用率。

### 本阶段范围

只修改：

- `NewApiAdapter.kt`
- `PROJECT.md`
- `DEVELOPMENT_LOG.md`
- `AI_HANDOFF.md`

不修改：

- `BalanceWidgetProvider`
- `AdapterFactory`
- `AdapterRequest`
- `MainActivity`
- MiMo / DeepSeek / 爱黄牛 Adapter
- 网页授权
- 配置存储
- Widget 布局、响应式和缓存
- 固定槽位 / 动态实例结构

### 验收标准

- Kimi 能力摘要从 `API` 变为 `API + Billing`，Billing 显示“已接入”。
- `/v1/dashboard/billing/subscription` 与 `/v1/dashboard/billing/usage` 当前真实返回可被解析。
- Widget 保留原 Kimi 剩余次数，并能轮播显示真实 Billing 指标。
- Billing 请求失败不得破坏原次数卡成功数据。
- MiMo、DeepSeek、爱黄牛能力摘要和真实数据不回归。
- 编译、覆盖安装和用户真机验收通过。'''
)

# 2) NewApiAdapter: add real Billing requests and merge them with the existing quota path.
adapter_path = "app/src/main/java/com/java/myapplication/adapter/NewApiAdapter.kt"
adapter = read(adapter_path)

adapter = replace_once(
    adapter,
    '''import com.java.myapplication.adapter.capability.ProviderCapabilityProfile

import java.net.HttpURLConnection
import java.net.URL
''',
    '''import com.java.myapplication.adapter.capability.ProviderCapabilityProfile
import org.json.JSONObject
import java.math.BigDecimal
import java.math.RoundingMode
import java.net.HttpURLConnection
import java.net.URL
''',
    "NewApi billing imports",
)

adapter = replace_once(
    adapter,
    "        sources = setOf(DataSourceType.API),\n",
    "        sources = setOf(DataSourceType.API, DataSourceType.BILLING),\n",
    "NewApi Billing capability source",
)

old_fetch = '''    override fun fetchData(apiBase: String, apiKey: String, modelName: String?): WidgetData {
        val normalizedBase = apiBase.trim().trimEnd('/')
        
        // 检查 API Key 是否为空
        if (apiKey.isBlank()) {
            return WidgetData.error(platformName, "Key未配置")
        }
        
        val url = "$normalizedBase/api/usage/token"

        return try {
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.setRequestProperty("Authorization", "Bearer $apiKey")
            conn.setRequestProperty("Accept", "application/json")
            conn.connectTimeout = 10000
            conn.readTimeout = 10000

            val responseCode = conn.responseCode
            if (responseCode == 200) {
                val responseBody = conn.inputStream.bufferedReader().use { it.readText() }
                conn.disconnect()
                parseTokenResponse(responseBody)
            } else {
                conn.disconnect()
                val errorMessage = when (responseCode) {
                    401 -> "Key无效"
                    403 -> "访问被拒绝"
                    404 -> "接口不存在"
                    429 -> "请求过于频繁"
                    in 500..599 -> "服务器错误"
                    else -> "请求失败 ($responseCode)"
                }
                WidgetData.error(platformName, errorMessage)
            }
        } catch (e: java.net.SocketTimeoutException) {
            WidgetData.error(platformName, "连接超时")
        } catch (e: java.net.ConnectException) {
            WidgetData.error(platformName, "连接失败")
        } catch (e: java.net.UnknownHostException) {
            WidgetData.error(platformName, "域名解析失败")
        } catch (e: java.io.IOException) {
            WidgetData.error(platformName, "网络错误")
        } catch (e: Exception) {
            WidgetData.error(platformName, "未知错误")
        }
    }
'''

new_fetch = r'''    override fun fetchData(apiBase: String, apiKey: String, modelName: String?): WidgetData {
        val serviceRoot = normalizeServiceRoot(apiBase)

        if (apiKey.isBlank()) {
            return WidgetData.error(platformName, "Key未配置")
        }

        // API 次数卡与 Billing 是两条独立数据来源；任一成功都应尽量返回真实数据。
        val tokenData = fetchTokenData(serviceRoot, apiKey)
        waitForSameHostInterval()
        val billingSnapshot = fetchBillingSnapshot(serviceRoot, apiKey)

        return mergeTokenAndBilling(
            tokenData = tokenData,
            billingSnapshot = billingSnapshot,
            configuredModelName = modelName
        )
    }

    private fun fetchTokenData(serviceRoot: String, apiKey: String): WidgetData {
        val url = "$serviceRoot/api/usage/token"

        return try {
            val conn = URL(url).openConnection() as HttpURLConnection
            try {
                conn.requestMethod = "GET"
                conn.setRequestProperty("Authorization", "Bearer $apiKey")
                conn.setRequestProperty("Accept", "application/json")
                conn.connectTimeout = 10000
                conn.readTimeout = 10000

                val responseCode = conn.responseCode
                if (responseCode == 200) {
                    val responseBody = conn.inputStream.bufferedReader().use { it.readText() }
                    parseTokenResponse(responseBody)
                } else {
                    val errorMessage = when (responseCode) {
                        401 -> "Key无效"
                        403 -> "访问被拒绝"
                        404 -> "接口不存在"
                        429 -> "请求过于频繁"
                        in 500..599 -> "服务器错误"
                        else -> "请求失败 ($responseCode)"
                    }
                    WidgetData.error(platformName, errorMessage)
                }
            } finally {
                conn.disconnect()
            }
        } catch (e: java.net.SocketTimeoutException) {
            WidgetData.error(platformName, "连接超时")
        } catch (e: java.net.ConnectException) {
            WidgetData.error(platformName, "连接失败")
        } catch (e: java.net.UnknownHostException) {
            WidgetData.error(platformName, "域名解析失败")
        } catch (e: java.io.IOException) {
            WidgetData.error(platformName, "网络错误")
        } catch (_: Exception) {
            WidgetData.error(platformName, "未知错误")
        }
    }

    private fun fetchBillingSnapshot(serviceRoot: String, apiKey: String): BillingSnapshot? {
        val subscription = fetchJsonObject(
            "$serviceRoot/v1/dashboard/billing/subscription",
            apiKey
        )

        waitForSameHostInterval()

        val usage = fetchJsonObject(
            "$serviceRoot/v1/dashboard/billing/usage",
            apiKey
        )

        val softLimitUsd = readDecimal(subscription, "soft_limit_usd", "soft_limit")
        val totalUsageRaw = readDecimal(usage, "total_usage")
        val totalUsageUsd = totalUsageRaw?.divide(
            BigDecimal("100"),
            6,
            RoundingMode.HALF_UP
        )

        return if (softLimitUsd == null && totalUsageUsd == null) {
            null
        } else {
            BillingSnapshot(
                softLimitUsd = softLimitUsd,
                totalUsageUsd = totalUsageUsd
            )
        }
    }

    private fun fetchJsonObject(url: String, apiKey: String): JSONObject? {
        return try {
            val conn = URL(url).openConnection() as HttpURLConnection
            try {
                conn.requestMethod = "GET"
                conn.setRequestProperty("Authorization", "Bearer $apiKey")
                conn.setRequestProperty("Accept", "application/json")
                conn.connectTimeout = 10000
                conn.readTimeout = 10000

                if (conn.responseCode != 200) {
                    null
                } else {
                    val body = conn.inputStream.bufferedReader().use { it.readText() }
                    JSONObject(body)
                }
            } finally {
                conn.disconnect()
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun mergeTokenAndBilling(
        tokenData: WidgetData,
        billingSnapshot: BillingSnapshot?,
        configuredModelName: String?
    ): WidgetData {
        if (billingSnapshot == null) {
            return tokenData
        }

        val billingParts = mutableListOf<String>()
        billingSnapshot.softLimitUsd?.let {
            billingParts.add("额度 \$${formatUsd(it)}")
        }
        billingSnapshot.totalUsageUsd?.let {
            billingParts.add("已用 \$${formatUsd(it)}")
        }

        val billingMetric = WidgetData.DisplayMetric(
            "Billing",
            billingParts.joinToString(" · ")
        )

        if (tokenData.isSuccess && tokenData.isAvailable) {
            return tokenData.copy(
                modelName = tokenData.modelName ?: configuredModelName,
                auxiliaryMetrics = listOf(billingMetric) + tokenData.auxiliaryMetrics
            )
        }

        val primaryMetric = billingSnapshot.softLimitUsd?.let {
            WidgetData.DisplayMetric("Billing额度", "\$${formatUsd(it)}")
        } ?: billingSnapshot.totalUsageUsd?.let {
            WidgetData.DisplayMetric("Billing已用", "\$${formatUsd(it)}")
        }

        val auxiliaryMetrics = if (
            billingSnapshot.softLimitUsd != null && billingSnapshot.totalUsageUsd != null
        ) {
            listOf(
                WidgetData.DisplayMetric(
                    "Billing已用",
                    "\$${formatUsd(billingSnapshot.totalUsageUsd)}"
                )
            )
        } else {
            emptyList()
        }

        return WidgetData(
            platformName = platformName,
            modelName = configuredModelName,
            primaryMetric = primaryMetric,
            auxiliaryMetrics = auxiliaryMetrics,
            statusText = "Billing 已同步",
            isSuccess = true,
            isAvailable = true
        )
    }

    private fun readDecimal(json: JSONObject?, vararg keys: String): BigDecimal? {
        if (json == null) return null

        for (key in keys) {
            if (!json.has(key) || json.isNull(key)) continue
            val value = json.opt(key)?.toString()?.trim()?.toBigDecimalOrNull()
            if (value != null) return value
        }
        return null
    }

    private fun formatUsd(value: BigDecimal): String {
        return value
            .setScale(6, RoundingMode.HALF_UP)
            .stripTrailingZeros()
            .toPlainString()
    }

    private fun normalizeServiceRoot(apiBase: String): String {
        val normalized = apiBase.trim().trimEnd('/')
        return if (normalized.endsWith("/v1", ignoreCase = true)) {
            normalized.dropLast(3).trimEnd('/')
        } else {
            normalized
        }
    }

    private fun waitForSameHostInterval() {
        try {
            Thread.sleep(500L)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        }
    }

    private data class BillingSnapshot(
        val softLimitUsd: BigDecimal?,
        val totalUsageUsd: BigDecimal?
    )
'''

adapter = replace_once(
    adapter,
    old_fetch,
    new_fetch,
    "NewApi independent API and Billing fetch flow",
)
write(adapter_path, adapter)

# 3) Development log: stage starts as pending real execution and user acceptance.
append_once(
    "DEVELOPMENT_LOG.md",
    "## 2026-07-13｜Stage 7D Kimi / NewAPI Billing 数据源真实接入",
    r'''---

## 2026-07-13｜Stage 7D Kimi / NewAPI Billing 数据源真实接入

**目标与背景**

Stage 7C 已建立 API、网页授权、Billing 三类统一数据来源，但 Billing 仍只是能力类型，没有任何 Adapter 被标记为真实接入。本阶段只把已有接口证据的 Kimi / NewAPI Billing 路径接入 `NewApiAdapter`。

**本阶段调整**

- `NewApiAdapter.capabilityProfile.sources` 增加 `BILLING`。
- 实际请求 `/v1/dashboard/billing/subscription`。
- 实际请求 `/v1/dashboard/billing/usage`。
- 解析 `soft_limit_usd`，兼容 `soft_limit`。
- 解析 `total_usage`，沿用项目既有 Billing 语义按美分转换为美元。
- Billing 与 `/api/usage/token` 独立获取；Billing 失败不覆盖原次数卡成功数据。
- Billing 成功时通过现有 `WidgetData` 合并到辅助指标。
- 同一 host 连续请求之间至少等待 500ms。

**明确未修改**

- 不修改 Provider、AdapterFactory、AdapterRequest。
- 不修改 MiMo、DeepSeek、爱黄牛。
- 不修改网页授权、配置存储、Widget 布局、响应式和缓存。
- 不修改固定槽位 / 动态实例结构。

**当前证据状态**

- 源码修改：待执行脚本后检查。
- 编译：待只执行一次。
- 覆盖安装：待只执行一次。
- Billing 两个接口当前真实返回：待用户本人真机确认。
- Kimi Widget Billing 指标：待用户本人真机确认。
- 其他平台回归：待用户本人真机确认。

**下一项唯一动作**

完成一次编译、一次覆盖安装和用户本人真机验收。用户确认前不得宣称 Stage 7D 完成。'''
)

# 4) AI handoff: record in-progress stage without replacing the last validated business baseline yet.
handoff_path = "AI_HANDOFF.md"
handoff = read(handoff_path)
insert_before = "## 严禁操作\n"
idx = handoff.find(insert_before)
if idx == -1:
    raise RuntimeError("AI_HANDOFF.md: forbidden-operations section not found")
if "`Stage 7D：Kimi / NewAPI Billing 数据源真实接入`" in handoff:
    raise RuntimeError("AI_HANDOFF.md already contains Stage 7D block")
block = '''## 正在进行的阶段

`Stage 7D：Kimi / NewAPI Billing 数据源真实接入`

- 目标：把 Stage 7C 中的 Billing 能力类型变成第一条真实 Billing 数据链。
- 当前范围只针对已具备历史接口证据的 Kimi / NewAPI 路径。
- 实际接口：`/v1/dashboard/billing/subscription`、`/v1/dashboard/billing/usage`。
- 认证继续使用模型 API Key；Billing 不是新的认证类型。
- Billing 与现有 `/api/usage/token` 独立获取并通过 `WidgetData` 合并。
- Billing 失败不得破坏原 Kimi 次数卡成功数据。
- 本阶段不修改 MiMo、DeepSeek、爱黄牛、网页登录、Widget Provider、布局、缓存或动态实例结构。

## 当前唯一动作

完成一次编译、一次覆盖安装和用户本人真机验收。用户确认前不得提交“阶段完成”。

'''
handoff = handoff[:idx] + block + handoff[idx:]
write(handoff_path, handoff)

print("Stage 7D NewAPI billing integration applied successfully.")
print("Changed:")
print("- PROJECT.md")
print("- DEVELOPMENT_LOG.md")
print("- AI_HANDOFF.md")
print("- app/src/main/java/com/java/myapplication/adapter/NewApiAdapter.kt")
