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
    "app/src/main/java/com/java/myapplication/MainActivity.kt",
    "app/src/main/java/com/java/myapplication/adapter/DeepSeekOfficialAdapter.kt",
]
for path in required:
    if not (ROOT / path).exists():
        raise RuntimeError(f"Required file missing: {path}")

# 1) PROJECT.md: record Stage 7B design before code changes.
append_once(
    "PROJECT.md",
    "## Stage 7B：DeepSeek 官方能力闭环",
    r'''---

## Stage 7B：DeepSeek 官方能力闭环

### 目标

把现有 DeepSeek 官方路径从“已有 Adapter、但本轮未完整重新验证”推进到可独立验收的完整闭环：

```text
DeepSeek 配置
→ 测试连接 / 获取模型
→ 保存真实模型名
→ DeepSeekOfficialAdapter 查询官方余额接口
→ Widget 展示真实余额与可用辅助余额字段
→ 复用现有缓存与临时网络故障兜底
→ 真机验收
```

### 本阶段代码调整

1. 测试连接的 `/v1/models` 地址兼容 API Base 末尾已经带 `/v1` 的情况，避免拼成 `/v1/v1/models`。
2. `DeepSeekOfficialAdapter` 继续只使用模型 API Key，不引入网页登录或后台授权。
3. DeepSeek 余额成功返回时，`WidgetData.modelName` 保留配置中选中的真实模型名，不再用“可用/余额不足”之类状态词代替模型名。
4. 余额、赠送余额、充值余额只展示接口真实返回字段；不伪造调用次数、Token 或百分比。
5. 继续复用现有 AdapterFactory、AdapterRequest、WidgetData 缓存与失败兜底，不建立第二套链路。

### 明确不做

- 不新增 DeepSeek 网页登录。
- 不修改 MiMo、Kimi、爱黄牛协议。
- 不修改 Widget 布局和响应式逻辑。
- 不新增数据库、WorkManager 或新的后台架构。
- 不伪造 DeepSeek 官方接口没有提供的数据。

### 验收标准

- DeepSeek 配置页可使用官方 API Base + API Key 完成“测试连接”。
- `/v1/models` 在 API Base 带或不带 `/v1` 时均不会重复拼接版本路径。
- 单模型自动保存；多模型继续使用现有选择弹窗。
- Widget DeepSeek 卡片标题继续显示用户选择的真实模型名。
- Widget 展示真实余额；接口存在赠送/充值余额时作为辅助指标展示。
- 无可靠使用率或近期用量数据时继续保持空，不伪造。
- 临时网络错误继续使用现有最近成功数据兜底。
- MiMo、Kimi、爱黄牛现有真机能力不回归。'''
)

# 2) MainActivity: make /v1/models URL tolerant of an API Base that already ends with /v1.
main_path = "app/src/main/java/com/java/myapplication/MainActivity.kt"
main = read(main_path)
main = replace_once(
    main,
    '''            val normalizedBase = apiBase.trim().trimEnd('/')
            val requestUrl = "$normalizedBase/v1/models"
''',
    '''            val normalizedBase = apiBase.trim().trimEnd('/')
            val requestUrl = if (normalizedBase.endsWith("/v1", ignoreCase = true)) {
                "$normalizedBase/models"
            } else {
                "$normalizedBase/v1/models"
            }
''',
    "MainActivity fetchModels URL normalization",
)
write(main_path, main)

# 3) DeepSeek adapter: preserve the configured model name in WidgetData.
adapter_path = "app/src/main/java/com/java/myapplication/adapter/DeepSeekOfficialAdapter.kt"
adapter = read(adapter_path)
adapter = replace_once(
    adapter,
    "                parseBalanceResponse(responseBody)\n",
    "                parseBalanceResponse(responseBody, modelName)\n",
    "DeepSeek parse call",
)
adapter = replace_once(
    adapter,
    "    private fun parseBalanceResponse(response: String): WidgetData {\n",
    "    private fun parseBalanceResponse(response: String, configuredModelName: String?): WidgetData {\n",
    "DeepSeek parser signature",
)
adapter = replace_once(
    adapter,
    '''        return try {
            val root = org.json.JSONObject(response)
            val isAvailable = root.optBoolean("is_available", false)
''',
    '''        return try {
            val root = org.json.JSONObject(response)
            val isAvailable = root.optBoolean("is_available", false)
            val effectiveModelName = configuredModelName?.trim()?.takeIf { it.isNotEmpty() }
''',
    "DeepSeek effective model name",
)
adapter = replace_once(
    adapter,
    "                        modelName = displayLabel,\n",
    "                        modelName = effectiveModelName,\n",
    "DeepSeek success modelName",
)
adapter = replace_once(
    adapter,
    "                    modelName = \"余额为空\",\n",
    "                    modelName = effectiveModelName,\n",
    "DeepSeek empty balance modelName",
)
write(adapter_path, adapter)

# 4) DEVELOPMENT_LOG.md: append in-progress stage entry.
append_once(
    "DEVELOPMENT_LOG.md",
    "## 2026-07-13｜Stage 7B DeepSeek 官方能力闭环",
    r'''---

## 2026-07-13｜Stage 7B DeepSeek 官方能力闭环

**目标与背景**

DeepSeek 官方 Adapter 已存在，并通过 `AdapterFactory` 路由到 `/user/balance`，但当前交接文档明确记录“全部真实运行场景尚未在本轮逐项重新验证”。本阶段完成配置、模型获取、余额展示和真机回归的完整闭环。

**本阶段调整**

- `MainActivity.fetchModels()`：兼容 API Base 已经以 `/v1` 结尾的情况，避免重复拼接 `/v1/v1/models`。
- `DeepSeekOfficialAdapter`：余额解析后保留配置中选中的真实模型名，不用状态词替代模型名。
- 继续展示接口真实返回的总余额，以及可选的赠送余额、充值余额。
- 不新增 DeepSeek 网页登录，不修改 AdapterFactory、Provider、Widget 布局、缓存和其他平台协议。

**当前证据状态**

- 源码修改：待执行脚本后检查。
- 编译：待只执行一次。
- 覆盖安装：待只执行一次。
- DeepSeek 测试连接：待用户本人真机确认。
- DeepSeek Widget 余额：待用户本人真机确认。
- 其他平台回归：待用户本人真机确认。

**下一项唯一动作**

完成一次编译、一次覆盖安装和用户本人真机验收。用户确认前不得宣称 Stage 7B 完成。'''
)

# 5) AI_HANDOFF.md: mark Stage 7B as in progress without rewriting prior history.
handoff_path = "AI_HANDOFF.md"
handoff = read(handoff_path)
insert_before = "## 严禁操作\n"
idx = handoff.find(insert_before)
if idx == -1:
    raise RuntimeError("AI_HANDOFF.md: forbidden-operations section not found")
if "## 正在进行的阶段\n\n`Stage 7B：DeepSeek 官方能力闭环`" in handoff:
    raise RuntimeError("AI_HANDOFF.md already contains Stage 7B in-progress block")
block = '''## 正在进行的阶段

`Stage 7B：DeepSeek 官方能力闭环`

- 目标：完成 DeepSeek 官方配置、模型获取、余额展示和真机回归的完整闭环。
- 测试连接需兼容 API Base 带或不带 `/v1`。
- `DeepSeekOfficialAdapter` 继续只使用模型 API Key，不新增网页登录。
- Widget 只展示真实余额以及接口真实返回的赠送/充值余额；不伪造近期用量和百分比。
- DeepSeek 数据对象保留配置中选中的真实模型名。
- 不修改 AdapterFactory、Provider、Widget 布局、缓存、MiMo、Kimi 或爱黄牛协议。

## 当前唯一动作

完成一次编译、一次覆盖安装和用户本人真机验收。用户确认前不得提交“阶段完成”。

'''
handoff = handoff[:idx] + block + handoff[idx:]
write(handoff_path, handoff)

print("Stage 7B DeepSeek official closure applied successfully.")
print("Changed:")
print("- PROJECT.md")
print("- DEVELOPMENT_LOG.md")
print("- AI_HANDOFF.md")
print("- app/src/main/java/com/java/myapplication/MainActivity.kt")
print("- app/src/main/java/com/java/myapplication/adapter/DeepSeekOfficialAdapter.kt")
