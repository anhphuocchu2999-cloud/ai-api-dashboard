# Operit Adaptive Android Executor

面向 **Operit AI 1.12+** 的全局 Android 开发执行 Skill。

## 解决的问题

- 不再要求用户选择 S/M/L 档位；
- 从最小范围开始，只有证据出现时才扩大读取和修改范围；
- 长任务自动拆阶段并连续执行，不在每个小阶段等待用户说“继续”；
- 每阶段写入 `MISSION.md` 与 `TASK_STATE.md`，支持换模型、新对话和应用重启后恢复；
- Android 编译和覆盖安装只使用一次 Operit 前台终端调用；
- 禁止 `sleep`、终端轮询和重复验证命令。

## 在 Operit 中全局安装

打开：

```text
侧边栏 → 工具/技能管理 → Skills → 右下角“+” → 从仓库导入
```

粘贴本 Skill 子目录地址：

```text
https://github.com/anhphuocchu2999-cloud/ai-api-dashboard/tree/main/skills/operit-adaptive-android-executor
```

安装后确认：

1. Skill 名称为 `operit-adaptive-android-executor`；
2. “对 AI 可见”开关已开启；
3. Operit 全局目录中存在：

```text
/storage/emulated/0/Download/Operit/skills/operit-adaptive-android-executor/
```

安装后，Operit 内切换 Kimi、DeepSeek、OpenAI、Gemini 等模型都可以调用该 Skill。不同模型的遵循程度可能不同。

## 更新

Operit 当前对同名 Skill 通常不会直接覆盖。更新时：

1. 在技能管理中删除旧版；
2. 重新从上述仓库子目录导入；
3. 确认“对 AI 可见”已开启。

## 使用方法

短任务：

```text
使用 operit-adaptive-android-executor 完成下面任务：……
直接执行到验证完成，除非触发 Skill 的暂停条件，不要逐阶段询问我。
```

长任务：

```text
使用 operit-adaptive-android-executor 执行以下总任务：……
自动创建 MISSION.md 和 TASK_STATE.md，拆分阶段并连续推进。
只在需要产品选择、凭据、高风险操作、人工界面验收或两次验证失败时暂停。
```

中断后恢复：

```text
使用 operit-adaptive-android-executor 继续当前任务。
先读取 TASK_STATE.md 和 MISSION.md，从检查点继续，不重新扫描项目。
```

## Operit 终端约定

验证必须调用 `super_admin:terminal`：

```text
command: bash <Skill目录>/scripts/verify-debug.sh <项目根目录> install
background: false
timeoutMs: 600000
```

不得使用 `sleep` 或 `terminal_getscreen` 轮询。
