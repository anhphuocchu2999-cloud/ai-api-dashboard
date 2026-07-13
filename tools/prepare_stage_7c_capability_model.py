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
    "app/src/main/java/com/java/myapplication/MainActivity.kt",
    "app/src/main/java/com/java/myapplication/adapter/PlatformAdapter.kt",
    "app/src/main/java/com/java/myapplication/adapter/NewApiAdapter.kt",
    "app/src/main/java/com/java/myapplication/adapter/MiMoAdapter.kt",
    "app/src/main/java/com/java/myapplication/adapter/DeepSeekOfficialAdapter.kt",
    "app/src/main/java/com/java/myapplication/adapter/AihuangniuAdapter.kt",
]
for path in required:
    if not (ROOT / path).exists():
        raise RuntimeError(f"Required file missing: {path}")

# 1) Product design first: formalize the user's three data-acquisition paths without pretending Billing is auth.
append_once(
    "PROJECT.md",
    "## Stage 7C：认证与数据能力模型统一",
    r'''---

## Stage 7C：认证与数据能力模型统一

### 用户目标

用户希望同一个 Dashboard 能统一承载三类实际数据获取路径：

1. `API`：使用模型 API Key 访问模型、余额、用量、额度等接口。
2. `网页授权`：用户在 App 内登录网页，获得 Cookie / Bearer Token / Session，再访问账户后台数据。
3. `Billing`：访问平台提供的 Billing / Subscription / Usage Billing 类接口。

内部概念必须保持准确：

- API Key、Cookie、Bearer Token 属于认证或凭据。
- Billing 属于数据接口来源，不是新的认证类型。
- 一个模型实例可以同时拥有多种数据来源。

### 本阶段目标

建立机器可读的 Provider 能力描述，让代码明确知道：

- 当前 Adapter 需要哪些凭据。
- 当前 Adapter 实际使用哪些数据来源：`API`、`网页授权`、`Billing`。
- 当前 Adapter 实际能提供哪些数据能力。

统一数据能力包括：

- Models
- Balance
- Quota
- Usage
- Requests
- Tokens
- Profile
- Subscription

### 真实性规则

只有已经在代码中真实接入的数据路径，才能声明为已支持。

尤其：

- 当前 Adapter 没有实际调用 Billing 接口时，不得仅因为平台可能存在 Billing 接口就声明 `Billing` 已接入。
- 当前 Stage 7C 先建立统一能力模型并映射现有真实能力。
- 后续新增 Billing 数据源时，必须有真实接口证据和真机验证，再把 `Billing` 加入对应 Adapter 的来源集合。

### 当前真实映射

#### Kimi / NewApiAdapter

- 数据来源：API
- 后台网页授权：无
- 数据能力：Models、Quota、Usage、Requests

#### MiMo

- 数据来源：API + 网页授权
- 后台网页授权：Cookie
- 数据能力：Models、Balance

#### DeepSeek 官方

- 数据来源：API
- 后台网页授权：无
- 数据能力：Models、Balance

#### 爱黄牛

- 数据来源：API + 网页授权
- 后台网页授权：Bearer Token
- 数据能力：Models、Balance、Usage、Requests、Tokens、Profile

### 本阶段代码范围

- 新增统一 `DataSourceType`。
- 新增统一 `DataCapability`。
- 新增统一 `ProviderCapabilityProfile`。
- `PlatformAdapter` 暴露 `capabilityProfile`。
- 四个现有 Adapter 按真实实现声明自身能力。
- 配置页高级设置显示当前实例的数据来源、账户授权方式、Billing 接入状态和可用数据能力。

### 明确不做

- 本阶段不新增 Billing HTTP 请求。
- 不修改现有 Adapter 的数据请求和 JSON 解析。
- 不修改 Widget 数据流、缓存、布局或响应式逻辑。
- 不修改 WebAuthActivity、WebAuthProfile 或授权存储格式。
- 不把固定平台槽位改造成动态实例槽位。
- 不新增 Provider。

### 验收标准

- Kimi、MiMo、DeepSeek、爱黄牛高级设置均能显示能力摘要。
- 摘要与当前真实实现一致。
- 当前四个平台的原有数据获取结果完全不变。
- MiMo 与爱黄牛原网页登录授权继续有效。
- 未真实接入 Billing 的 Adapter 显示 `Billing：当前未接入`，不得伪装为已支持。
- 编译、覆盖安装和真机回归通过。'''
)

# 2) New capability model files.
write(
    "app/src/main/java/com/java/myapplication/adapter/capability/DataSourceType.kt",
    '''package com.java.myapplication.adapter.capability

/**
 * Provider 实际使用的数据获取来源。
 *
 * 注意：Billing 是数据接口来源，不是认证类型。
 */
enum class DataSourceType(val displayName: String) {
    API("API"),
    WEB_AUTH("网页授权"),
    BILLING("Billing")
}
'''
)

write(
    "app/src/main/java/com/java/myapplication/adapter/capability/DataCapability.kt",
    '''package com.java.myapplication.adapter.capability

/** Provider 已真实实现的数据能力。 */
enum class DataCapability(val displayName: String) {
    MODELS("模型"),
    BALANCE("余额"),
    QUOTA("额度"),
    USAGE("用量"),
    REQUESTS("请求次数"),
    TOKENS("Token"),
    PROFILE("账户信息"),
    SUBSCRIPTION("套餐")
}
'''
)

write(
    "app/src/main/java/com/java/myapplication/adapter/capability/ProviderCapabilityProfile.kt",
    '''package com.java.myapplication.adapter.capability

import com.java.myapplication.adapter.auth.BackgroundAuthType

/**
 * 一个 Adapter 当前真实支持的认证与数据能力描述。
 *
 * 这里只描述已经落地的能力，不做接口探测，也不保存任何凭据。
 */
data class ProviderCapabilityProfile(
    val modelApiKeyRequired: Boolean = true,
    val backgroundAuthType: BackgroundAuthType = BackgroundAuthType.NONE,
    val sources: Set<DataSourceType>,
    val capabilities: Set<DataCapability>
)
'''
)

# 3) PlatformAdapter must expose a capability profile.
platform_path = "app/src/main/java/com/java/myapplication/adapter/PlatformAdapter.kt"
platform = read(platform_path)
platform = replace_once(
    platform,
    "package com.java.myapplication.adapter\n",
    "package com.java.myapplication.adapter\n\nimport com.java.myapplication.adapter.capability.ProviderCapabilityProfile\n",
    "PlatformAdapter capability import",
)
platform = replace_once(
    platform,
    '''    val platformName: String

    /**
     * 探测平台类型
''',
    '''    val platformName: String

    /** 当前 Adapter 已真实实现的认证与数据能力。 */
    val capabilityProfile: ProviderCapabilityProfile

    /**
     * 探测平台类型
''',
    "PlatformAdapter capability property",
)
write(platform_path, platform)

# 4) NewApiAdapter capability mapping.
path = "app/src/main/java/com/java/myapplication/adapter/NewApiAdapter.kt"
text = read(path)
text = replace_once(
    text,
    "package com.java.myapplication.adapter\n",
    '''package com.java.myapplication.adapter

import com.java.myapplication.adapter.auth.BackgroundAuthType
import com.java.myapplication.adapter.capability.DataCapability
import com.java.myapplication.adapter.capability.DataSourceType
import com.java.myapplication.adapter.capability.ProviderCapabilityProfile
''',
    "NewApi capability imports",
)
text = replace_once(
    text,
    '''    override val platformName: String = "Kimi"

    override fun detect''',
    '''    override val platformName: String = "Kimi"

    override val capabilityProfile = ProviderCapabilityProfile(
        modelApiKeyRequired = true,
        backgroundAuthType = BackgroundAuthType.NONE,
        sources = setOf(DataSourceType.API),
        capabilities = setOf(
            DataCapability.MODELS,
            DataCapability.QUOTA,
            DataCapability.USAGE,
            DataCapability.REQUESTS
        )
    )

    override fun detect''',
    "NewApi capability profile",
)
write(path, text)

# 5) MiMo capability mapping.
path = "app/src/main/java/com/java/myapplication/adapter/MiMoAdapter.kt"
text = read(path)
text = replace_once(
    text,
    "import com.java.myapplication.adapter.auth.BackgroundAuthType\n",
    '''import com.java.myapplication.adapter.auth.BackgroundAuthType
import com.java.myapplication.adapter.capability.DataCapability
import com.java.myapplication.adapter.capability.DataSourceType
import com.java.myapplication.adapter.capability.ProviderCapabilityProfile
''',
    "MiMo capability imports",
)
text = replace_once(
    text,
    '''    override val platformName: String = "MiMo"

    override fun detect''',
    '''    override val platformName: String = "MiMo"

    override val capabilityProfile = ProviderCapabilityProfile(
        modelApiKeyRequired = true,
        backgroundAuthType = BackgroundAuthType.COOKIE,
        sources = setOf(DataSourceType.API, DataSourceType.WEB_AUTH),
        capabilities = setOf(
            DataCapability.MODELS,
            DataCapability.BALANCE
        )
    )

    override fun detect''',
    "MiMo capability profile",
)
write(path, text)

# 6) DeepSeek capability mapping.
path = "app/src/main/java/com/java/myapplication/adapter/DeepSeekOfficialAdapter.kt"
text = read(path)
text = replace_once(
    text,
    "package com.java.myapplication.adapter\n",
    '''package com.java.myapplication.adapter

import com.java.myapplication.adapter.auth.BackgroundAuthType
import com.java.myapplication.adapter.capability.DataCapability
import com.java.myapplication.adapter.capability.DataSourceType
import com.java.myapplication.adapter.capability.ProviderCapabilityProfile
''',
    "DeepSeek capability imports",
)
text = replace_once(
    text,
    '''    override val platformName: String = "DeepSeek"

    override fun detect''',
    '''    override val platformName: String = "DeepSeek"

    override val capabilityProfile = ProviderCapabilityProfile(
        modelApiKeyRequired = true,
        backgroundAuthType = BackgroundAuthType.NONE,
        sources = setOf(DataSourceType.API),
        capabilities = setOf(
            DataCapability.MODELS,
            DataCapability.BALANCE
        )
    )

    override fun detect''',
    "DeepSeek capability profile",
)
write(path, text)

# 7) Aihuangniu capability mapping.
path = "app/src/main/java/com/java/myapplication/adapter/AihuangniuAdapter.kt"
text = read(path)
text = replace_once(
    text,
    "import com.java.myapplication.adapter.auth.BackgroundAuthType\n",
    '''import com.java.myapplication.adapter.auth.BackgroundAuthType
import com.java.myapplication.adapter.capability.DataCapability
import com.java.myapplication.adapter.capability.DataSourceType
import com.java.myapplication.adapter.capability.ProviderCapabilityProfile
''',
    "Aihuangniu capability imports",
)
text = replace_once(
    text,
    '''    override val platformName: String = "Aihuangniu"

    override fun detect''',
    '''    override val platformName: String = "Aihuangniu"

    override val capabilityProfile = ProviderCapabilityProfile(
        modelApiKeyRequired = true,
        backgroundAuthType = BackgroundAuthType.BEARER_TOKEN,
        sources = setOf(DataSourceType.API, DataSourceType.WEB_AUTH),
        capabilities = setOf(
            DataCapability.MODELS,
            DataCapability.BALANCE,
            DataCapability.USAGE,
            DataCapability.REQUESTS,
            DataCapability.TOKENS,
            DataCapability.PROFILE
        )
    )

    override fun detect''',
    "Aihuangniu capability profile",
)
write(path, text)

# 8) Config UI: show the capability summary without changing existing auth behavior.
main_path = "app/src/main/java/com/java/myapplication/MainActivity.kt"
main = read(main_path)
main = replace_once(
    main,
    "import com.java.myapplication.adapter.auth.BackgroundAuthConfig\n",
    '''import com.java.myapplication.adapter.AdapterFactory
import com.java.myapplication.adapter.auth.BackgroundAuthConfig
import com.java.myapplication.adapter.capability.DataSourceType
''',
    "MainActivity capability imports",
)
main = replace_once(
    main,
    '''                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = "后台授权",
                                    style = MaterialTheme.typography.titleSmall,
                                    modifier = Modifier.padding(bottom = 8.dp)
                                )

                                // 授权类型选择
''',
    '''                            Column(modifier = Modifier.padding(12.dp)) {
                                val capabilityAdapter = AdapterFactory.getAdapter(
                                    platform,
                                    configs[index].apiBase
                                )
                                val capabilityProfile = capabilityAdapter?.capabilityProfile

                                if (capabilityProfile != null) {
                                    val sourceText = capabilityProfile.sources
                                        .joinToString(" + ") { it.displayName }
                                    val authText = when (capabilityProfile.backgroundAuthType) {
                                        BackgroundAuthType.NONE -> "无需网页授权"
                                        BackgroundAuthType.COOKIE -> "网页 Cookie"
                                        BackgroundAuthType.BEARER_TOKEN -> "网页 Bearer Token"
                                    }
                                    val capabilityText = capabilityProfile.capabilities
                                        .joinToString("、") { it.displayName }
                                    val billingText = if (DataSourceType.BILLING in capabilityProfile.sources) {
                                        "已接入"
                                    } else {
                                        "当前未接入"
                                    }

                                    Text(
                                        text = "连接与数据能力",
                                        style = MaterialTheme.typography.titleSmall
                                    )
                                    Text(
                                        text = "模型连接：${if (capabilityProfile.modelApiKeyRequired) "API Key" else "平台自定义"}",
                                        style = MaterialTheme.typography.bodySmall,
                                        modifier = Modifier.padding(top = 6.dp)
                                    )
                                    Text(
                                        text = "数据来源：$sourceText",
                                        style = MaterialTheme.typography.bodySmall,
                                        modifier = Modifier.padding(top = 2.dp)
                                    )
                                    Text(
                                        text = "账户授权：$authText",
                                        style = MaterialTheme.typography.bodySmall,
                                        modifier = Modifier.padding(top = 2.dp)
                                    )
                                    Text(
                                        text = "Billing：$billingText",
                                        style = MaterialTheme.typography.bodySmall,
                                        modifier = Modifier.padding(top = 2.dp)
                                    )
                                    Text(
                                        text = "可用数据：$capabilityText",
                                        style = MaterialTheme.typography.bodySmall,
                                        modifier = Modifier.padding(top = 2.dp, bottom = 10.dp)
                                    )
                                    HorizontalDivider(modifier = Modifier.padding(bottom = 10.dp))
                                }

                                Text(
                                    text = "后台授权",
                                    style = MaterialTheme.typography.titleSmall,
                                    modifier = Modifier.padding(bottom = 8.dp)
                                )

                                // 授权类型选择
''',
    "MainActivity capability summary",
)
write(main_path, main)

# 9) In-progress stage log.
append_once(
    "DEVELOPMENT_LOG.md",
    "## 2026-07-13｜Stage 7C 认证与数据能力模型统一",
    r'''---

## 2026-07-13｜Stage 7C 认证与数据能力模型统一

**目标与背景**

用户的核心目标不是简单增加平台，而是让同一个 Dashboard 能统一承载 API、网页授权和 Billing 三类数据获取路径。现有项目已经具备 API Key、Cookie、Bearer Token 和多个真实数据接口，但这些能力尚未形成机器可读的统一描述。

**本阶段调整**

- 新增 `DataSourceType`：API、网页授权、Billing。
- 新增 `DataCapability`：Models、Balance、Quota、Usage、Requests、Tokens、Profile、Subscription。
- 新增 `ProviderCapabilityProfile`。
- `PlatformAdapter` 统一暴露 `capabilityProfile`。
- Kimi、MiMo、DeepSeek、爱黄牛按当前真实实现声明能力。
- 配置页高级设置展示当前实例的数据来源、账户授权方式、Billing 接入状态和可用数据能力。

**真实性边界**

- 本阶段不新增 Billing HTTP 请求。
- 当前 Adapter 没有真实 Billing 请求时，必须显示 `Billing：当前未接入`。
- 不修改现有数据请求、JSON 解析、Widget 数据流、缓存或授权保存格式。

**当前证据状态**

- 源码修改：待执行脚本后检查。
- 编译：待只执行一次。
- 覆盖安装：待只执行一次。
- 能力摘要真机显示：待用户本人确认。
- Kimi / MiMo / DeepSeek / 爱黄牛原数据回归：待用户本人确认。

**下一项唯一动作**

完成一次编译、一次覆盖安装和用户本人真机验收。用户确认前不得宣称 Stage 7C 完成。'''
)

# 10) AI handoff current-stage block.
handoff_path = "AI_HANDOFF.md"
handoff = read(handoff_path)
insert_before = "## 严禁操作\n"
idx = handoff.find(insert_before)
if idx == -1:
    raise RuntimeError("AI_HANDOFF.md: forbidden-operations section not found")
if "## 正在进行的阶段\n\n`Stage 7C：认证与数据能力模型统一`" in handoff:
    raise RuntimeError("AI_HANDOFF.md already contains Stage 7C in-progress block")
block = '''## 正在进行的阶段

`Stage 7C：认证与数据能力模型统一`

- 用户核心目标：统一承载 API、网页授权和 Billing 三类数据获取路径。
- Billing 仍按数据接口来源处理，不作为认证类型。
- 本阶段建立 `DataSourceType`、`DataCapability`、`ProviderCapabilityProfile`。
- 四个现有 Adapter 只声明已经真实实现的能力。
- 配置页高级设置显示当前数据来源、账户授权、Billing 接入状态和数据能力。
- 本阶段不新增 Billing HTTP 请求，不修改现有数据获取结果。

## 当前唯一动作

完成一次编译、一次覆盖安装和用户本人真机验收。用户确认前不得提交“阶段完成”。

'''
handoff = handoff[:idx] + block + handoff[idx:]
write(handoff_path, handoff)

print("Stage 7C capability model applied successfully.")
print("Changed:")
print("- PROJECT.md")
print("- DEVELOPMENT_LOG.md")
print("- AI_HANDOFF.md")
print("- app/src/main/java/com/java/myapplication/MainActivity.kt")
print("- app/src/main/java/com/java/myapplication/adapter/PlatformAdapter.kt")
print("- app/src/main/java/com/java/myapplication/adapter/NewApiAdapter.kt")
print("- app/src/main/java/com/java/myapplication/adapter/MiMoAdapter.kt")
print("- app/src/main/java/com/java/myapplication/adapter/DeepSeekOfficialAdapter.kt")
print("- app/src/main/java/com/java/myapplication/adapter/AihuangniuAdapter.kt")
print("- app/src/main/java/com/java/myapplication/adapter/capability/DataSourceType.kt")
print("- app/src/main/java/com/java/myapplication/adapter/capability/DataCapability.kt")
print("- app/src/main/java/com/java/myapplication/adapter/capability/ProviderCapabilityProfile.kt")
