from __future__ import annotations

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


# ---------------------------------------------------------------------------
# 1) PROJECT.md — formal confirmed scope before business code.
# ---------------------------------------------------------------------------
append_once(
    "PROJECT.md",
    "## Stage 7A-3A：通用网页登录授权入口（仅迁移 MiMo Cookie 路径）",
    r'''---

## Stage 7A-3A：通用网页登录授权入口（仅迁移 MiMo Cookie 路径）

本阶段只完成一个能力：把现有 MiMo 专用网页登录入口迁移为由 `WebAuthProfile` 驱动的通用网页登录入口。

### 目标

- 新增最小 `WebAuthProfile`，描述某个平台是否支持网页登录，以及登录 URL、Cookie 域名、授权类型和必要 Cookie 名称。
- 新增 `WebAuthRegistry`，当前只注册 MiMo。
- 将 `MiMoWebLoginActivity` 迁移为通用 `WebAuthActivity`。
- 配置页按是否存在 `WebAuthProfile` 决定是否显示“连接账户”，不得继续写死 `platform == "MiMo"`。
- 保持现有 `api_config / MiMo_auth`、Cookie 提取规则和 Widget 刷新行为兼容。

### MiMo 当前 Profile

```text
profileId: mimo
instanceKey: MiMo
displayName: MiMo
loginUrl: https://platform.xiaomimimo.com/#/console/balance
cookieDomain: platform.xiaomimimo.com
authType: COOKIE
requiredCookieNames:
- api-platform_serviceToken
- userId
```

### 本阶段明确不做

- 不实现爱黄牛 Bearer Token 自动提取。
- 不给 Kimi、DeepSeek、OpenAI 强行显示网页登录按钮。
- 不修改 Adapter、AdapterRequest、AdapterFactory。
- 不修改 Widget、缓存、响应式布局或平台数据接口。
- 不改变 MiMo 授权键名、JSON 格式或 SharedPreferences 名称。
- 不读取、打印或记录任何真实 API Key、Cookie、Bearer Token。

### 验收标准

- 配置页不再存在 `if (platform == "MiMo")` 的网页登录入口特判。
- MiMo 仍显示“连接账户”按钮，其他当前平台不错误显示。
- 点击 MiMo“连接账户”仍打开原 MiMo 登录页。
- 未取得必要 Cookie 时退出，不覆盖现有 `MiMo_auth`。
- 成功取得必要 Cookie 后仍通过 `BackgroundAuthRepository.save()` 保存到 `api_config / MiMo_auth`。
- MiMo 原余额、Kimi 原次数和 Widget 正常状态不回归。
'''
)


# ---------------------------------------------------------------------------
# 2) Generic WebAuthProfile + registry. Current registry contains MiMo only.
# ---------------------------------------------------------------------------
write(
    "app/src/main/java/com/java/myapplication/webauth/WebAuthProfile.kt",
    r'''package com.java.myapplication.webauth

import com.java.myapplication.adapter.auth.BackgroundAuthType

/**
 * 描述一个模型实例的网页登录授权能力。
 *
 * 本模型只描述公开的登录规则，不保存任何真实凭据。
 */
data class WebAuthProfile(
    val profileId: String,
    val instanceKey: String,
    val displayName: String,
    val loginUrl: String,
    val cookieDomain: String,
    val authType: BackgroundAuthType,
    val requiredCookieNames: Set<String>
)
'''
)

write(
    "app/src/main/java/com/java/myapplication/webauth/WebAuthRegistry.kt",
    r'''package com.java.myapplication.webauth

import com.java.myapplication.adapter.auth.BackgroundAuthType

/**
 * 网页登录能力注册表。
 *
 * Stage 7A-3A 只注册已经真实验证过的 MiMo Cookie 路径。
 * 未验证的平台不得为了统一界面伪造 WebAuthProfile。
 */
object WebAuthRegistry {
    private val profiles: List<WebAuthProfile> = listOf(
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
}
'''
)


# ---------------------------------------------------------------------------
# 3) Replace MiMo-specific Activity with generic WebAuthActivity.
# ---------------------------------------------------------------------------
old_activity_path = ROOT / "app/src/main/java/com/java/myapplication/MiMoWebLoginActivity.kt"
if not old_activity_path.exists():
    raise RuntimeError("MiMoWebLoginActivity.kt is missing")

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
import com.java.myapplication.webauth.WebAuthRegistry

/**
 * 通用网页登录授权 Activity。
 *
 * Stage 7A-3A 只迁移已经验证的 MiMo Cookie 路径。
 * Bearer Token 等其他提取方式必须单独验证后再扩展，不能在这里猜测实现。
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
        val resolvedProfile = WebAuthRegistry.findByProfileId(profileId)
        if (resolvedProfile == null) {
            Toast.makeText(this, "暂不支持这个网页登录授权", Toast.LENGTH_SHORT).show()
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
                checkAndSaveCredential()
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

    private fun checkAndSaveCredential() {
        if (loginDetected) return

        val cookieString = CookieManager.getInstance().getCookie(profile.cookieDomain) ?: ""
        if (cookieString.isBlank()) return

        val cookies = parseCookieString(cookieString)
        val hasAllRequiredCookies = profile.requiredCookieNames.all { cookies.containsKey(it) }
        if (!hasAllRequiredCookies) return

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
        val pairs = cookieString.split(";")
        for (pair in pairs) {
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
}
'''
)
old_activity_path.unlink()


# ---------------------------------------------------------------------------
# 4) Rename layout to generic name without changing its UI structure.
# ---------------------------------------------------------------------------
old_layout_path = ROOT / "app/src/main/res/layout/activity_mimo_web_login.xml"
new_layout_path = ROOT / "app/src/main/res/layout/activity_web_auth.xml"
if not old_layout_path.exists():
    raise RuntimeError("activity_mimo_web_login.xml is missing")
if new_layout_path.exists():
    raise RuntimeError("Refusing to overwrite existing activity_web_auth.xml")
write("app/src/main/res/layout/activity_web_auth.xml", old_layout_path.read_text(encoding="utf-8"))
old_layout_path.unlink()


# ---------------------------------------------------------------------------
# 5) Manifest registers generic activity.
# ---------------------------------------------------------------------------
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
    "Manifest generic web auth activity",
)
write(manifest_path, manifest)


# ---------------------------------------------------------------------------
# 6) Config UI is capability-driven instead of hard-coded MiMo condition.
# ---------------------------------------------------------------------------
main_path = "app/src/main/java/com/java/myapplication/MainActivity.kt"
main = read(main_path)
if "import com.java.myapplication.webauth.WebAuthRegistry" not in main:
    main = replace_once(
        main,
        "import com.java.myapplication.ui.theme.MyApplicationTheme\n",
        "import com.java.myapplication.ui.theme.MyApplicationTheme\n"
        "import com.java.myapplication.webauth.WebAuthRegistry\n",
        "MainActivity WebAuthRegistry import",
    )

main = replace_once(
    main,
    '''                                // MiMo 网页登录授权按钮
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
''',
    '''                                // 网页登录授权入口由平台注册能力决定，不再写死 MiMo。
                                val webAuthProfile = WebAuthRegistry.findByInstanceKey(platform)
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
''',
    "MainActivity capability-driven web auth button",
)
write(main_path, main)


# ---------------------------------------------------------------------------
# 7) Append in-progress development log. Final evidence is filled after user test.
# ---------------------------------------------------------------------------
append_once(
    "DEVELOPMENT_LOG.md",
    "## 2026-07-12｜Stage 7A-3A 通用网页登录授权入口（MiMo Cookie 迁移）",
    r'''---

## 2026-07-12｜Stage 7A-3A 通用网页登录授权入口（MiMo Cookie 迁移）

**状态**

进行中；尚未完成真机验收，不得宣称阶段完成。

**目标与背景**

现有配置页通过 `if (platform == "MiMo")` 决定是否显示网页登录按钮，且 Activity、Manifest 和布局均使用 MiMo 专用命名。后续扩展其他真实网页登录能力时会继续产生平台特判。

**方案与取舍**

- 新增最小 `WebAuthProfile` 和 `WebAuthRegistry`。
- 当前注册表只包含已经验证过的 MiMo Cookie 路径。
- 将 MiMo 专用 Activity 迁移为通用 `WebAuthActivity`。
- 配置页按 `WebAuthRegistry.findByInstanceKey()` 的结果显示“连接账户”。
- 不实现任何未验证的 Bearer Token 自动提取。

**起始基线**

- 起始分支：`docs/finalize-handoff-baseline`
- 起始提交：`f1cccbbbb91324cbc7a9fefb803ed4f7f514bc61`
- 工作分支：`feature/web-auth-profile-mimo`

**计划修改文件**

- `PROJECT.md`
- `DEVELOPMENT_LOG.md`
- `AI_HANDOFF.md`
- `AndroidManifest.xml`
- `MainActivity.kt`
- 删除 `MiMoWebLoginActivity.kt`
- 新增 `WebAuthActivity.kt`
- 新增 `webauth/WebAuthProfile.kt`
- 新增 `webauth/WebAuthRegistry.kt`
- 将 `activity_mimo_web_login.xml` 迁移为 `activity_web_auth.xml`

**明确不修改**

- Adapter、AdapterRequest、AdapterFactory
- Widget、缓存、响应式布局
- MiMo 授权键名和 JSON 格式
- Kimi、DeepSeek、OpenAI 的网页登录能力
- 爱黄牛 Bearer Token 自动提取

**验证状态**

- 编译：待执行
- 覆盖安装：待执行
- 用户真机验收：待执行
- 最终提交 SHA：待本阶段验收后记录
'''
)


# ---------------------------------------------------------------------------
# 8) AI_HANDOFF reflects the currently executing stage.
# ---------------------------------------------------------------------------
handoff_path = "AI_HANDOFF.md"
handoff = read(handoff_path)
handoff = replace_once(
    handoff,
    r'''## 下一项唯一任务

`Stage 7A-3A：通用网页登录授权入口（仅迁移 MiMo Cookie 路径）`

最小顺序：

1. 定义最小 `WebAuthProfile` 或等价的平台授权描述，不新建第二套后端。
2. 只把现有 MiMo 登录流程迁入通用入口，保持 Cookie 提取、`api_config / MiMo_auth` 和真机行为完全兼容。
3. 配置页按是否存在 WebAuthProfile 决定是否显示“连接账户”，不得继续写死 `platform == "MiMo"`。
4. 本阶段不实现爱黄牛 Bearer Token 自动提取；MiMo 真机通过后再单独立项。
5. Kimi、DeepSeek 等只需要 API Key 的平台不强行显示网页登录按钮。

不得在同一阶段同时实现 MiMo 与爱黄牛。
''',
    r'''## 当前进行的阶段

`Stage 7A-3A：通用网页登录授权入口（仅迁移 MiMo Cookie 路径）`

- 工作分支：`feature/web-auth-profile-mimo`
- 起始提交：`f1cccbbbb91324cbc7a9fefb803ed4f7f514bc61`
- 当前状态：业务代码待编译、覆盖安装和用户真机验收。
- 只迁移 MiMo Cookie 路径；不得同时实现爱黄牛 Bearer Token。

本阶段验收重点：

1. MiMo 通过 `WebAuthProfile` 获得“连接账户”能力。
2. 配置页不再写死 `platform == "MiMo"`。
3. `api_config / MiMo_auth` 完全兼容，退出未成功登录页面不得覆盖现有授权。
4. Kimi、DeepSeek、OpenAI 当前不错误显示“连接账户”。
5. MiMo 余额、Kimi 次数和 Widget 状态不得回归。

## 本阶段验收通过后的下一项

单独评估爱黄牛 Bearer Token 的真实获取位置和可验证提取方式。没有运行证据前不得实现自动提取。
''',
    "AI_HANDOFF current Stage 7A-3A",
)
write(handoff_path, handoff)

print("Stage 7A-3A generic MiMo web auth migration applied successfully.")
print("Changed business files:")
print("- AndroidManifest.xml")
print("- MainActivity.kt")
print("- WebAuthActivity.kt")
print("- webauth/WebAuthProfile.kt")
print("- webauth/WebAuthRegistry.kt")
print("- activity_web_auth.xml")
print("Removed:")
print("- MiMoWebLoginActivity.kt")
print("- activity_mimo_web_login.xml")
print("Updated documentation:")
print("- PROJECT.md")
print("- DEVELOPMENT_LOG.md")
print("- AI_HANDOFF.md")
