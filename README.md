# ai-api-dashboard

Android 桌面 Widget 项目，用于展示多个 AI API 平台的模型、余额、额度或用量信息。

## Focused Android Executor V2

仓库内已经提供用于约束 Kimi 开发执行过程的专用 Skill，减少无关文件读取、重复工具调用、上下文膨胀和无进展循环。

安装：

```bash
bash tools/install-focused-android-executor.sh .
```

然后填写根目录的 `CURRENT_TASK.md`，并对 Kimi 使用：

```text
使用 focused-android-executor Skill 执行 CURRENT_TASK.md。
只使用任务单白名单中的文件、工具和命令；按任务档位执行；触发完成条件或熔断条件后立即停止。
```

详细说明见 `docs/FOCUSED_ANDROID_EXECUTOR_V2.md`。

## 原始项目包

仓库保留 `ai-api-dashboard-project.zip` 作为项目文件包。
