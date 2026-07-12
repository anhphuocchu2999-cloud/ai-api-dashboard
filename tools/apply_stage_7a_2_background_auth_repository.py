from __future__ import annotations

from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def read(path: str) -> str:
    return (ROOT / path).read_text(encoding="utf-8")


def write(path: str, content: str) -> None:
    target = ROOT / path
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(content, encoding="utf-8")


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected exactly 1 match, found {count}")
    return text.replace(old, new, 1)


# 1) Document the confirmed scope before business code changes.
project_path = "PROJECT.md"
project = read(project_path)
marker = "## Stage 7A-2：后台授权凭据仓库统一"
if marker not in project:
    project = project.rstrip() + """

---

## Stage 7A-2：后台授权凭据仓库统一

本阶段继续沿用现有 Adapter、AdapterRequest 和 BackgroundAuthConfig，不新建第二套后端。

本阶段只解决一个问题：

后台 Cookie / Bearer Token 的 JSON 保存和读取不再分散在 Widget Provider 与各网页登录 Activity 中，统一通过 BackgroundAuthRepository 处理。

统一入口：

```text
BackgroundAuthRepository
├─ load(prefs, instanceKey)
├─ save(prefs, instanceKey, config)
└─ clear(prefs, instanceKey)
```

存储兼容规则：

- SharedPreferences 名称继续由调用方决定。
- 授权键名继续使用 `${instanceKey}_auth`。
- MiMo 继续使用现有 `api_config / MiMo_auth`。
- JSON 字段继续使用 `authType`、`authValue`、`enabled`、`updatedAt`。
- 覆盖安装后不得要求 MiMo 重新登录。

本阶段不做：

- 不新增平台。
- 不修改 AdapterRequest。
- 不修改任何 Adapter 的数据接口。
- 不修改配置页面。
- 不修改 Widget 布局、缓存和响应式逻辑。
- 不改变 Cookie / Bearer Token 的获取方式。
""".rstrip() + "\n"
    write(project_path, project)


# 2) Add the single repository responsible for auth JSON persistence.
repository_path = "app/src/main/java/com/java/myapplication/adapter/auth/BackgroundAuthRepository.kt"
repository = """package com.java.myapplication.adapter.auth

import android.content.SharedPreferences
import java.util.Locale
import org.json.JSONObject

/**
 * 后台授权凭据的唯一持久化入口。
 *
 * 保持现有 `${instanceKey}_auth` 键名和 JSON 字段兼容，
 * 不改变已保存的 MiMo Cookie / Bearer Token 格式。
 */
object BackgroundAuthRepository {

    fun load(prefs: SharedPreferences, instanceKey: String): BackgroundAuthConfig {
        val raw = prefs.getString(storageKey(instanceKey), null)
        if (raw.isNullOrBlank()) {
            return BackgroundAuthConfig()
        }

        return try {
            val obj = JSONObject(raw)
            val enabled = obj.optBoolean("enabled", false)
            if (!enabled) {
                BackgroundAuthConfig()
            } else {
                val authType = try {
                    BackgroundAuthType.valueOf(
                        obj.optString("authType", BackgroundAuthType.NONE.name)
                            .uppercase(Locale.ROOT)
                    )
                } catch (_: Exception) {
                    BackgroundAuthType.NONE
                }

                BackgroundAuthConfig(
                    authType = authType,
                    authValue = obj.optString("authValue", ""),
                    enabled = authType != BackgroundAuthType.NONE,
                    updatedAt = obj.optLong("updatedAt", 0L)
                )
            }
        } catch (_: Exception) {
            BackgroundAuthConfig()
        }
    }

    fun save(
        prefs: SharedPreferences,
        instanceKey: String,
        config: BackgroundAuthConfig
    ) {
        val json = JSONObject()
            .put("authType", config.authType.name)
            .put("authValue", config.authValue)
            .put("enabled", config.enabled)
            .put("updatedAt", config.updatedAt)
            .toString()

        prefs.edit().putString(storageKey(instanceKey), json).apply()
    }

    fun clear(prefs: SharedPreferences, instanceKey: String) {
        prefs.edit().remove(storageKey(instanceKey)).apply()
    }

    private fun storageKey(instanceKey: String): String = "${instanceKey}_auth"
}
"""
write(repository_path, repository)


# 3) Provider reads auth only through BackgroundAuthRepository.
provider_path = "app/src/main/java/com/java/myapplication/BalanceWidgetProvider.kt"
provider = read(provider_path)
provider = replace_once(
    provider,
    "import com.java.myapplication.adapter.auth.BackgroundAuthConfig\nimport com.java.myapplication.adapter.auth.BackgroundAuthType\n",
    "import com.java.myapplication.adapter.auth.BackgroundAuthRepository\n",
    "Provider auth imports"
)

old_helper = """        private fun loadBackgroundAuth(
            prefs: android.content.SharedPreferences,
            platformName: String
        ): BackgroundAuthConfig {
            val authJson = prefs.getString("${platformName}_auth", null)
            if (authJson.isNullOrBlank()) {
                return BackgroundAuthConfig()
            }

            return try {
                val obj = org.json.JSONObject(authJson)
                val enabled = obj.optBoolean("enabled", false)
                if (!enabled) {
                    BackgroundAuthConfig()
                } else {
                    val authType = try {
                        BackgroundAuthType.valueOf(
                            obj.optString("authType", BackgroundAuthType.NONE.name)
                                .uppercase(Locale.ROOT)
                        )
                    } catch (_: Exception) {
                        BackgroundAuthType.NONE
                    }

                    BackgroundAuthConfig(
                        authType = authType,
                        authValue = obj.optString("authValue", ""),
                        enabled = authType != BackgroundAuthType.NONE,
                        updatedAt = obj.optLong("updatedAt", 0L)
                    )
                }
            } catch (_: Exception) {
                BackgroundAuthConfig()
            }
        }

"""
provider = replace_once(provider, old_helper, "", "Provider inline auth parser")
provider = replace_once(
    provider,
    "            val backgroundAuth = loadBackgroundAuth(prefs, platformName)\n",
    "            val backgroundAuth = BackgroundAuthRepository.load(prefs, platformName)\n",
    "Provider repository load"
)
write(provider_path, provider)


# 4) MiMo login writes auth only through BackgroundAuthRepository.
mimo_path = "app/src/main/java/com/java/myapplication/MiMoWebLoginActivity.kt"
mimo = read(mimo_path)
mimo = replace_once(
    mimo,
    "import com.java.myapplication.adapter.auth.BackgroundAuthConfig\n",
    "import com.java.myapplication.adapter.auth.BackgroundAuthConfig\nimport com.java.myapplication.adapter.auth.BackgroundAuthRepository\n",
    "MiMo repository import"
)
mimo = replace_once(
    mimo,
    "        const val AUTH_KEY = \"MiMo_auth\"\n\n",
    "",
    "MiMo manual auth key"
)
old_save = """        val json = org.json.JSONObject()
            .put("authType", authConfig.authType.name)
            .put("authValue", authConfig.authValue)
            .put("enabled", authConfig.enabled)
            .put("updatedAt", authConfig.updatedAt)
            .toString()

        prefs.edit().putString(AUTH_KEY, json).apply()
"""
new_save = """        BackgroundAuthRepository.save(
            prefs = prefs,
            instanceKey = "MiMo",
            config = authConfig
        )
"""
mimo = replace_once(mimo, old_save, new_save, "MiMo repository save")
write(mimo_path, mimo)

print("Stage 7A-2 background auth repository migration applied successfully.")
print("Changed:")
print("- PROJECT.md")
print("- adapter/auth/BackgroundAuthRepository.kt")
print("- BalanceWidgetProvider.kt")
print("- MiMoWebLoginActivity.kt")
