from __future__ import annotations

from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def read(path: str) -> str:
    return (ROOT / path).read_text(encoding="utf-8")


def write(path: str, content: str) -> None:
    target = ROOT / path
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(content.rstrip() + "\n", encoding="utf-8")


def append_once(path: str, marker: str, content: str) -> None:
    current = read(path)
    if marker in current:
        raise RuntimeError(f"{path}: marker already exists: {marker}")
    write(path, current.rstrip() + "\n\n" + content.strip())


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected exactly 1 match, found {count}")
    return text.replace(old, new, 1)


START_COMMIT = "f1cccbbbb91324cbc7a9fefb803ed4f7f514bc61"
BRANCH = "feature/stage-7a-3a-web-auth-mimo"

required_files = [
    "AGENTS.md",
    "PROJECT.md",
    "AI_HANDOFF.md",
    "DEVELOPMENT_LOG.md",
    "app/src/main/AndroidManifest.xml",
    "app/src/main/java/com/java/myapplication/MainActivity.kt",
    "app/src/main/java/com/java/myapplication/MiMoWebLoginActivity.kt",
    "app/src/main/res/layout/activity_mimo_web_login.xml",
]
for required in required_files:
    if not (ROOT / required).exists():
        raise RuntimeError(f"Required file missing: {required}")

new_files = [
    "app/src/main/java/com/java/myapplication/WebAuthActivity.kt",
    "app/src/main/java/com/java/myapplication/webauth/WebAuthProfile.kt",
    "app/src/main/java/com/java/myapplication/webauth/WebAuthProfileRegistry.kt",
    "app/src/main/res/layout/activity_web_auth.xml",
]
for path in new_files:
    if (ROOT / path).exists():
        raise RuntimeError(f"Refusing to overwrite existing file: {path}")

# 1) Record the confirmed Stage 7A-3A scope in PROJECT.md before business code changes.
append_once(
    "PROJECT.md",
    "## Stage 7A-3A：通用网页登录授权入口（仅迁移 MiMo Cookie 路径）",
    r'''---

## Stage 7A-3A：通用网页登录授权入口（仅迁移 MiMo Cookie 路径）

本阶段采用“完整功能块”节奏，一次完成通用网页登录入口的最小闭环，但只迁移已经有真实运行证据的 MiMo Cookie 路径。

### 目标

把当前配置页中的 `platform == "MiMo"` 特判和 `MiMoWebLoginActivity` 专用入口，迁移为由平台网页登录能力描述驱动的通用入口。

### 新的最小能力模型

```text
WebAuthProfile
├─ profileId
├─ instanceKey
├─ displayName
├─ loginUrl
├─ cookieDomain
├─ authType
└─ requiredCookieNames
```

`WebAuthProfileRegistry` 负责根据模型实例键或 profileId 查找网页登录能力。

第一版注册表只注册 MiMo：

- `profileId = mimo`
- `instanceKey = MiMo`
- `authType = COOKIE`
- 登录页保持现有 MiMo 地址
- 必要 Cookie 保持 `api-platform_serviceToken` 与 `userId`

### 通用入口规则

```text
配置页模型实例
        ↓
WebAuthProfileRegistry.findByInstanceKey(instanceKey)
        ↓
存在 profile → 显示“连接账户”
不存在 profile → 不显示网页登录按钮
        ↓
WebAuthActivity(profileId)
        ↓
按 WebAuthProfile 打开登录页并检测授权
        ↓
BackgroundAuthRepository.save(instanceKey, config)
        ↓
刷新 Widget
```

### 兼容要求

- MiMo 继续保存到 `api_config / MiMo_auth`。
- 不改变现有 Cookie JSON 格式。
- 不删除或迁移现有 MiMo 授权数据。
- 覆盖安装后不得要求 MiMo 重新登录。
- MiMo Cookie 检测条件与原逻辑一致。
- 未检测到必要 Cookie 时不得覆盖现有授权。
- 配置页不再通过 `platform == "MiMo"` 决定是否显示网页登录按钮。

### 本阶段明确不做

- 不实现爱黄牛 Bearer Token 自动提取。
- 不为 Kimi、DeepSeek 或其他只需要 API Key 的平台强行显示网页登录按钮。
- 不修改 Adapter、AdapterRequest、AdapterFactory。
- 不修改 Widget 数据接口、缓存、布局或响应式逻辑。
- 不修改后台授权存储格式。

### 验收标准

- 只有存在 `WebAuthProfile` 的 MiMo 显示“连接账户”。
- Kimi、DeepSeek、OpenAI 当前不错误显示自动网页登录入口。
- MiMo 能打开原登录页面。
- 不登录直接退出时不覆盖现有 `MiMo_auth`。
- 已登录状态可以继续保存到原 `MiMo_auth` 并刷新 Widget。
- MiMo 原余额、Kimi 原次数和 Widget 现有展示不回归。
- 业务代码只编译一次、覆盖安装一次，随后必须由用户本人真机验收。'''
)

# 2) Add the generic profile model.
write(
    "app/src/main/java/com/java/myapplication/webauth/WebAuthProfile.kt",
    r'''package com.java.myapplication.webauth

import com.java.myapplication.adapter.auth.BackgroundAuthType

/**
 * 描述一个模型实例是否支持 App 内网页登录授权，以及如何检测授权成功。
 *
 * Stage 7A-3A 只实现已经验证过的 COOKIE 路径；未来其他认证方式必须单独验证后再扩展。
 */
data class WebAuthProfile(
    val profileId: String,
    val instanceKey: String,
    val displayName: String,
    val loginUrl: String,
    val cookieDomain: String,
    val authType: BackgroundAuthType,
    val requiredCookieNames: Set<String>
)'''
)

# 3) Add a registry. First version intentionally contains only MiMo.
write(
    "app/src/main/java/com/java/myapplication/webauth/WebAuthProfileRegistry.kt",
    r'''package com.java.myapplication.webauth

import com.java.myapplication.adapter.auth.BackgroundAuthType

object WebAuthProfileRegistry {
    private val profiles = listOf(
        WebAuthProfile(
            profileId = "mimo",
            instanceKey = "MiMo",
            displayName = "MiMo",
            loginUrl = "https://platform.xiaomimimo.com/#/console/balance",
            cookieDomain = "platform.xiaomimimo.com",
            authType = BackgroundAuthType.COOKIE,
            requiredCookieNames = setOf(
                "api-platform_serviceToken",
                "userId"
            )
        )
    )

    fun findByProfileId(profileId: String): WebAuthProfile? {
        return profiles.firstOrNull { it.profileId == profileId }
    }

    fun findByInstanceKey(instanceKey: String): WebAuthProfile? {
        return profiles.firstOrNull { it.instanceKey == instanceKey }
    }
}'''
)

# 4) Replace the MiMo-only Activity with a generic WebAuthActivity.
write(
    "app/src/main/java/com/java/myapplication/WebAuthActivity.kt",
    r'''package com.java.myapplication

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ProgressBar
import android.widget.Toast
import com.java.myapplication.adapter.auth.BackgroundAuthConfig
import com.java.myapplication.adapter.auth.BackgroundAuthRepository
import com.java.myapplication.adapter.auth.BackgroundAuthType
import com.java.myapplication.webauth.WebAuthProfile
import com.java.myapplication.webauth.WebAuthProfileRegistry

/**
 * 通用网页登录授权 Activity。
 *
 * Stage 7A-3A 只迁移已经验证过的 MiMo COOKIE 流程。
 * 未经真实验证，不在这里猜测或实现 Bearer Token 自动提取。
 */
class WebAuthActivity : Activity() {

    companion object {
        private const val EXTRA_PROFILE_ID = "web_auth_profile_id"
        private const val PREFS_NAME = "api_config"

        fun createIntent(context: Context, profileId: String): Intent {
            return Intent(context, WebAuthActivity::class.java)
                .putExtra(EXTRA_PROFILE_ID, profileId)
        }
    }

    private lateinit var webView: WebView
    private lateinit var progressBar: ProgressBar
    private lateinit var profile: WebAuthProfile
    private var loginDetected = false
    private var showCancelToast = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val profileId = intent.getStringExtra(EXTRA_PROFILE_ID).orEmpty()
        val resolvedProfile = WebAuthProfileRegistry.findByProfileId(profileId)
        if (resolvedProfile == null) {
            Toast.makeText(this, "暂不支持这个网页登录入口", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        if (resolvedProfile.authType != BackgroundAuthType.COOKIE) {
            Toast.makeText(this, "这个授权方式还没有完成验证", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        profile = resolvedProfile
        showCancelToast = true

        setContentView(R.layout.activity_web_auth)
        title = "${profile.displayName} 网页登录授权"

        webView = findViewById(R.id.web_view)
        progressBar = findViewById(R.id.progress_bar)

        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            javaScriptCanOpenWindowsAutomatically = true
            mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            userAgentString = "Mozilla/5.0 (Linux; Android 14; SM-G998B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Mobile Safari/537.36"
        }

        webView.webChromeClient = WebChromeClient()
        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest?
            ): Boolean {
                return false
            }

            override fun onPageStarted(
                view: WebView?,
                url: String?,
                favicon: android.graphics.Bitmap?
            ) {
                super.onPageStarted(view, url, favicon)
                progressBar.visibility = View.VISIBLE
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                progressBar.visibility = View.GONE
                checkAndSaveCookies()
            }
        }

        webView.loadUrl(profile.loginUrl)
    }

    override fun onDestroy() {
        super.onDestroy()
        if (showCancelToast && !loginDetected) {
            Toast.makeText(
                this,
                "未检测到登录状态，已取消授权",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun checkAndSaveCookies() {
        if (loginDetected) return

        val cookieString = CookieManager.getInstance().getCookie(profile.cookieDomain) ?: ""
        if (cookieString.isBlank()) return

        val cookies = parseCookieString(cookieString)
        val hasRequiredCookies = profile.requiredCookieNames.all(cookies::containsKey)
        if (!hasRequiredCookies) return

        loginDetected = true
        val allCookies = buildString {
            cookies.forEach { (name, value) ->
                if (isNotEmpty()) append("; ")
                append("$name=$value")
            }
        }

        saveCredentialAndRefresh(allCookies)
    }

    private fun saveCredentialAndRefresh(cookieString: String) {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val authConfig = BackgroundAuthConfig(
            authType = profile.authType,
            authValue = cookieString,
            enabled = true,
            updatedAt = System.currentTimeMillis()
        )

        BackgroundAuthRepository.save(
            prefs = prefs,
            instanceKey = profile.instanceKey,
            config = authConfig
        )

        Toast.makeText(
            this,
            "${profile.displayName} 网页授权成功",
            Toast.LENGTH_SHORT
        ).show()

        refreshWidget(this)
        finish()
    }

    private fun refreshWidget(context: Context) {
        try {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val componentName = ComponentName(context, BalanceWidgetProvider::class.java)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)
            if (appWidgetIds.isNotEmpty()) {
                val intent = Intent(context, BalanceWidgetProvider::class.java)
                intent.action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, appWidgetIds)
                context.sendBroadcast(intent)
            }
        } catch (_: Exception) {
        }
    }

    private fun parseCookieString(cookieString: String): Map<String, String> {
        val result = mutableMapOf<String, String>()
        cookieString.split(";").forEach { pair ->
            val trimmed = pair.trim()
            val eqIndex = trimmed.indexOf('=')
            if (eqIndex > 0) {
                val name = trimmed.substring(0, eqIndex).trim()
                val value = trimmed.substring(eqIndex + 1).trim()
                if (name.isNotEmpty()) {
                    result[name] = value
                }
            }
        }
        return result
    }
}'''
)

# 5) Use a generic layout name; content remains intentionally unchanged.
write(
    "app/src/main/res/layout/activity_web_auth.xml",
    r'''<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:orientation="vertical">

    <ProgressBar
        android:id="@+id/progress_bar"
        style="?android:attr/progressBarStyleHorizontal"
        android:layout_width="match_parent"
        android:layout_height="3dp"
        android:indeterminate="true" />

    <WebView
        android:id="@+id/web_view"
        android:layout_width="match_parent"
        android:layout_height="0dp"
        android:layout_weight="1" />

</LinearLayout>'''
)

# 6) Update the config page: capability-driven button instead of platform == "MiMo".
main_path = "app/src/main/java/com/java/myapplication/MainActivity.kt"
main = read(main_path)
if "import com.java.myapplication.webauth.WebAuthProfileRegistry" not in main:
    main = replace_once(
        main,
        "import com.java.myapplication.ui.theme.MyApplicationTheme\n",
        "import com.java.myapplication.ui.theme.MyApplicationTheme\n"
        "import com.java.myapplication.webauth.WebAuthProfileRegistry\n",
        "MainActivity WebAuthProfileRegistry import",
    )

old_button = '''                                // MiMo 网页登录授权按钮
                                if (platform == "MiMo") {
                                    Button(
                                        onClick = {
                                            val intent = Intent(context, MiMoWebLoginActivity::class.java)
                                            context.startActivity(intent)
                                        },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 8.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = MaterialTheme.colorScheme.primary
                                        )
                                    ) {
                                        Text("🌐 网页登录授权")
                                    }
                                }
'''
new_button = '''                                // 网页登录入口由平台能力描述决定，不再写死 MiMo。
                                val webAuthProfile = WebAuthProfileRegistry.findByInstanceKey(platform)
                                if (webAuthProfile != null) {
                                    Button(
                                        onClick = {
                                            context.startActivity(
                                                WebAuthActivity.createIntent(
                                                    context = context,
                                                    profileId = webAuthProfile.profileId
                                                )
                                            )
                                        },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 8.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = MaterialTheme.colorScheme.primary
                                        )
                                    ) {
                                        Text("🌐 连接账户")
                                    }
                                }
'''
main = replace_once(main, old_button, new_button, "MainActivity web auth button")
write(main_path, main)

# 7) Update the manifest to the generic Activity.
manifest_path = "app/src/main/AndroidManifest.xml"
manifest = read(manifest_path)
manifest = replace_once(
    manifest,
    '''        <activity
            android:name=".MiMoWebLoginActivity"
            android:exported="false"
            android:label="MiMo 网页登录授权"
            android:theme="@style/Theme.MyApplication" />
''',
    '''        <activity
            android:name=".WebAuthActivity"
            android:exported="false"
            android:label="网页登录授权"
            android:theme="@style/Theme.MyApplication" />
''',
    "Manifest WebAuthActivity",
)
write(manifest_path, manifest)

# 8) Remove the MiMo-only Activity and old MiMo-named layout after generic replacements exist.
(ROOT / "app/src/main/java/com/java/myapplication/MiMoWebLoginActivity.kt").unlink()
(ROOT / "app/src/main/res/layout/activity_mimo_web_login.xml").unlink()

# 9) Add a development log entry. Stage is not complete until user truthfully finishes device acceptance.
append_once(
    "DEVELOPMENT_LOG.md",
    "## 2026-07-12｜Stage 7A-3A 通用网页登录授权入口（MiMo 迁移）",
    f'''---

## 2026-07-12｜Stage 7A-3A 通用网页登录授权入口（MiMo 迁移）

**目标与背景**

配置页仍通过 `platform == "MiMo"` 显示专用网页登录按钮，网页登录 Activity 也直接写死 MiMo 参数。本阶段把已经验证过的 MiMo Cookie 登录迁移到通用能力描述和通用 Activity。

**方案与取舍**

- 新增 `WebAuthProfile` 与 `WebAuthProfileRegistry`。
- 第一版 Registry 只注册 MiMo。
- 新增通用 `WebAuthActivity`，只处理已经验证过的 `COOKIE` 路径。
- 配置页根据是否存在 `WebAuthProfile` 显示“连接账户”。
- 保持 `api_config / MiMo_auth`、必要 Cookie 名称和现有保存格式不变。
- 不实现爱黄牛 Bearer Token 自动提取，不修改 Adapter、Widget、缓存或布局逻辑。

**起始基线**

- 分支：`{BRANCH}`
- 起始提交：`{START_COMMIT}`

**计划修改文件**

- `PROJECT.md`
- `MainActivity.kt`
- `AndroidManifest.xml`
- `WebAuthActivity.kt`（新增）
- `webauth/WebAuthProfile.kt`（新增）
- `webauth/WebAuthProfileRegistry.kt`（新增）
- `activity_web_auth.xml`（新增）
- `MiMoWebLoginActivity.kt`（删除）
- `activity_mimo_web_login.xml`（删除）
- `DEVELOPMENT_LOG.md`
- `AI_HANDOFF.md`

**当前证据状态**

- 源码实现：待执行脚本后检查。
- 编译：待执行端只执行一次。
- 覆盖安装：待编译成功后只执行一次。
- 真机验收：必须由用户本人确认，未确认前不得宣称 Stage 7A-3A 完成。
- 最终实现提交 SHA：待用户真机验收通过后提交并记录。

**回滚位置**

`{START_COMMIT}`

**下一项唯一动作**

完成 Stage 7A-3A 编译、覆盖安装和用户真机验收；验收通过前不得进入爱黄牛 Bearer Token 自动提取。'''
)

# 10) Update the current handoff state to reflect an in-progress, test-gated stage.
handoff_path = "AI_HANDOFF.md"
handoff = read(handoff_path)
old_next = '''## 下一项唯一任务

`Stage 7A-3A：通用网页登录授权入口（仅迁移 MiMo Cookie 路径）`

最小顺序：

1. 定义最小 `WebAuthProfile` 或等价的平台授权描述，不新建第二套后端。
2. 只把现有 MiMo 登录流程迁入通用入口，保持 Cookie 提取、`api_config / MiMo_auth` 和真机行为完全兼容。
3. 配置页按是否存在 WebAuthProfile 决定是否显示“连接账户”，不得继续写死 `platform == "MiMo"`。
4. 本阶段不实现爱黄牛 Bearer Token 自动提取；MiMo 真机通过后再单独立项。
5. Kimi、DeepSeek 等只需要 API Key 的平台不强行显示网页登录按钮。

不得在同一阶段同时实现 MiMo 与爱黄牛。
'''
new_next = f'''## 正在进行的阶段

`Stage 7A-3A：通用网页登录授权入口（仅迁移 MiMo Cookie 路径）`

- 开发分支：`{BRANCH}`
- 起始提交：`{START_COMMIT}`
- 目标：用 `WebAuthProfile` + `WebAuthProfileRegistry` + `WebAuthActivity` 替代 MiMo 专用网页登录入口。
- 第一版 Registry 只允许注册 MiMo。
- 配置页必须按能力显示“连接账户”，不得继续写死 `platform == "MiMo"`。
- `api_config / MiMo_auth` 必须保持兼容。
- 本阶段不实现爱黄牛 Bearer Token 自动提取。

## 当前唯一动作

完成 Stage 7A-3A 的一次编译、一次覆盖安装和用户本人真机验收。用户确认前不得提交“阶段完成”，不得进入下一平台。
'''
handoff = replace_once(handoff, old_next, new_next, "AI_HANDOFF current Stage 7A-3A")
write(handoff_path, handoff)

print("Stage 7A-3A MiMo web auth migration applied successfully.")
print("Changed business capability:")
print("- WebAuthProfile + WebAuthProfileRegistry")
print("- generic WebAuthActivity")
print("- capability-driven config button")
print("- MiMo Cookie storage compatibility preserved")
print("Stage remains pending until build, install, and user device acceptance.")
