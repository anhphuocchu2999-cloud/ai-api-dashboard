# Focused Android Executor V2

这是本项目用于约束 Kimi 执行 Android 开发任务的专用 Skill。

它重点解决：

- 反复扫描项目；
- 重复读取相同文件；
- 工具调用过多；
- 编译、找 APK、安装被拆成多轮；
- 同一错误空转；
- 任务完成后继续发散。

## 安装

先更新或下载本仓库，然后在项目根目录执行：

```bash
bash tools/install-focused-android-executor.sh .
```

安装结果：

```text
.agent/skills/focused-android-executor/
CURRENT_TASK.md
```

如果根目录已经存在 `CURRENT_TASK.md`，安装脚本会保留原文件，不会覆盖。

## 调用指令

```text
使用 focused-android-executor Skill 执行 CURRENT_TASK.md。
只使用任务单白名单中的文件、工具和命令；按任务档位执行；触发完成条件或熔断条件后立即停止。
```

## Kimi 应执行的完整指令

```text
先拉取 ai-api-dashboard 仓库的最新 main 分支。
在项目根目录执行：bash tools/install-focused-android-executor.sh .
读取 CURRENT_TASK.md 并补全当前唯一任务的文件、工具和命令白名单。
随后使用 focused-android-executor Skill 执行该任务。
不要扫描整个项目，不要处理下一阶段。
```

## 文件

- `tools/focused-android-executor-v2.zip`：完整 Skill；
- `tools/install-focused-android-executor.sh`：自动安装脚本；
- Skill 内含 Bash 和 PowerShell 构建安装脚本；
- Skill 内含任务模板和主线程网络异常示例。
