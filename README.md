# ai-api-dashboard

Android 桌面 Widget 项目，用于展示多个 AI API 平台的模型、余额、额度或用量信息。

## 推荐：Operit Adaptive Android Executor

仓库现已提供面向 **Operit AI 1.12+** 的全局自适应 Android 执行 Skill：

```text
skills/operit-adaptive-android-executor/
```

它支持：

- 无需选择 S/M/L 档位；
- 最小上下文启动，证据驱动扩大范围；
- 长任务自动拆阶段并连续推进；
- 使用 `MISSION.md` 和 `TASK_STATE.md` 保存断点；
- 一次 Operit 前台终端完成编译和覆盖安装；
- 禁止 `sleep`、终端轮询和重复验证命令。

在 Operit 的 Skill 管理中选择“从仓库导入”，粘贴：

```text
https://github.com/anhphuocchu2999-cloud/ai-api-dashboard/tree/main/skills/operit-adaptive-android-executor
```

安装后确认“对 AI 可见”已开启。这样 Operit 内的 Kimi、DeepSeek、OpenAI、Gemini 等模型都可以使用。

## 旧版：Focused Android Executor V2

旧版项目内 Skill 与安装脚本仍保留在：

```text
tools/focused-android-executor-v2.zip
tools/install-focused-android-executor.sh
docs/FOCUSED_ANDROID_EXECUTOR_V2.md
```

V2 使用 S/M/L 固定预算，仅建议作为历史备份；新任务优先使用全局自适应版。

## 原始项目包

仓库保留 `ai-api-dashboard-project.zip` 作为项目文件包。
