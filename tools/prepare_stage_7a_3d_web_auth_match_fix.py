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


required = [
    "AGENTS.md",
    "PROJECT.md",
    "AI_HANDOFF.md",
    "DEVELOPMENT_LOG.md",
    "app/src/main/java/com/java/myapplication/webauth/WebAuthProfileRegistry.kt",
]
for path in required:
    if not (ROOT / path).exists():
        raise RuntimeError(f"Required file missing: {path}")

# 1) PROJECT.md: record the confirmed bug and exact Stage 7A-3D scope before code.
append_once(
    "PROJECT.md",
    "## Stage 7A-3D：WebAuthProfile 匹配规则修复",
    r'''---

## Stage 7A-3D：WebAuthProfile 匹配规则修复

### 问题

Stage 7A-3C 后，爱黄牛 Profile 使用：

- `instanceKey = OpenAI`
- `apiBaseHostContains = aihuangniu.com`

但当前 `WebAuthProfileRegistry.findFor(instanceKey, apiBase)` 先按 `instanceKey` 直接返回匹配项，因此所有 OpenAI 槽位都会命中爱黄牛 Profile，即使 API Base 不是爱黄牛。

### 正确匹配规则

`findFor(instanceKey, apiBase)` 必须同时尊重 Profile 自身的约束：

- `instanceKey` 必须匹配。
- Profile 未配置 `apiBaseHostContains` 时，只需实例键匹配。
- Profile 配置了 `apiBaseHostContains` 时，还必须要求 `apiBase` 命中该域名片段。

因此：

- MiMo：`instanceKey == MiMo` 即可匹配。
- OpenAI + `aihuangniu.com` API Base：匹配爱黄牛。
- OpenAI + 官方 OpenAI API Base：不得匹配爱黄牛。
- OpenAI + 其他兼容中转：不得匹配爱黄牛。

### 本阶段只做

- 修复 `WebAuthProfileRegistry.findFor()` 的匹配逻辑。
- 保持现有 MiMo Profile 和爱黄牛 Profile 内容不变。
- 保持 `WebAuthActivity`、Adapter、Widget、缓存、授权存储和 UI 布局不变。
- 更新 `DEVELOPMENT_LOG.md` 和 `AI_HANDOFF.md` 的阶段状态。

### 验收标准

- MiMo 仍显示“连接账户”。
- 当前爱黄牛 OpenAI 卡片仍显示“连接账户”。
- 将 OpenAI 卡片 API Base 临时改成非爱黄牛地址后，“连接账户”不得继续显示。
- 恢复爱黄牛 API Base 后，“连接账户”重新出现。
- MiMo 余额、Kimi 次数、爱黄牛自动授权和 Widget 现有行为不回归。'''
)

# 2) Fix the registry matching semantics.
registry_path = "app/src/main/java/com/java/myapplication/webauth/WebAuthProfileRegistry.kt"
registry = read(registry_path)
old = '''    /**
     * 根据平台实例键和 apiBase 查找匹配的网页登录配置。
     * 优先精确匹配 instanceKey，其次按 apiBase 包含匹配。
     */
    fun findFor(instanceKey: String, apiBase: String): WebAuthProfile? {
        // 1. 精确匹配 instanceKey
        val exact = profiles.firstOrNull { it.instanceKey == instanceKey }
        if (exact != null) return exact

        // 2. 按 apiBase 包含匹配（用于 OpenAI 槽位配置爱黄牛等场景）
        return profiles.firstOrNull {
            it.apiBaseHostContains != null &&
            apiBase.contains(it.apiBaseHostContains, ignoreCase = true)
        }
    }
'''
new = '''    /**
     * 根据平台实例键和 apiBase 查找匹配的网页登录配置。
     *
     * Profile 没有 apiBaseHostContains 时，仅要求 instanceKey 匹配；
     * Profile 配置了 apiBaseHostContains 时，instanceKey 与 apiBase 必须同时匹配。
     */
    fun findFor(instanceKey: String, apiBase: String): WebAuthProfile? {
        return profiles.firstOrNull { profile ->
            if (profile.instanceKey != instanceKey) {
                false
            } else {
                val hostConstraint = profile.apiBaseHostContains
                hostConstraint.isNullOrBlank() ||
                    apiBase.contains(hostConstraint, ignoreCase = true)
            }
        }
    }
'''
registry = replace_once(registry, old, new, "WebAuthProfileRegistry.findFor")
write(registry_path, registry)

# 3) Append in-progress development log entry.
append_once(
    "DEVELOPMENT_LOG.md",
    "## 2026-07-12｜Stage 7A-3D WebAuthProfile 匹配规则修复",
    r'''---

## 2026-07-12｜Stage 7A-3D WebAuthProfile 匹配规则修复

**问题与背景**

源码复核发现 `WebAuthProfileRegistry.findFor()` 先按 `instanceKey` 直接返回 Profile。由于爱黄牛 Profile 的 `instanceKey = OpenAI`，会导致任意 OpenAI 槽位都错误显示爱黄牛“连接账户”入口。

**修复方案**

- Profile 的 `instanceKey` 必须匹配。
- 未配置 `apiBaseHostContains` 的 Profile：实例键匹配即可。
- 配置了 `apiBaseHostContains` 的 Profile：实例键与 API Base 必须同时匹配。

**明确未修改**

- 不修改 MiMo Profile 内容。
- 不修改爱黄牛 Profile 内容。
- 不修改 `WebAuthActivity`。
- 不修改 Adapter、AdapterRequest、AdapterFactory。
- 不修改 Widget、缓存、布局、授权存储格式。

**当前证据状态**

- 源码修复：待执行脚本后检查。
- 编译：待只执行一次。
- 覆盖安装：待只执行一次。
- 真机验收：待用户本人确认。

**下一项唯一动作**

完成一次编译、一次覆盖安装和用户本人真机验收；用户确认前不得宣称 Stage 7A-3D 完成。'''
)

# 4) Update AI_HANDOFF current-stage block.
handoff_path = "AI_HANDOFF.md"
handoff = read(handoff_path)
anchor = "## 下一项唯一任务\n"
start = handoff.find(anchor)
if start == -1:
    raise RuntimeError("AI_HANDOFF.md: next-task section not found")
end = handoff.find("## 严禁操作\n", start)
if end == -1:
    raise RuntimeError("AI_HANDOFF.md: forbidden-operations section not found")
new_block = '''## 正在进行的阶段

`Stage 7A-3D：WebAuthProfile 匹配规则修复`

- 目标：修复 OpenAI 槽位无条件命中爱黄牛 WebAuthProfile 的问题。
- 只修改 `WebAuthProfileRegistry.findFor()` 匹配语义。
- MiMo 仅按实例键匹配。
- 爱黄牛必须同时满足 `instanceKey = OpenAI` 与 API Base 命中 `aihuangniu.com`。
- 不修改 WebAuthActivity、Adapter、Widget、缓存、布局和授权存储。

## 当前唯一动作

完成一次编译、一次覆盖安装和用户本人真机验收。用户确认前不得提交“阶段完成”，不得进入下一阶段。

'''
handoff = handoff[:start] + new_block + handoff[end:]
write(handoff_path, handoff)

print("Stage 7A-3D WebAuthProfile match fix applied successfully.")
print("Changed:")
print("- PROJECT.md")
print("- DEVELOPMENT_LOG.md")
print("- AI_HANDOFF.md")
print("- app/src/main/java/com/java/myapplication/webauth/WebAuthProfileRegistry.kt")
