from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
FINAL_COMMIT = "5d707575a1c9f98ca603d645a1794ab74a2c950b"
BRANCH = "fix/stage-7a-3d-web-auth-profile-match"


def read(path: str) -> str:
    return (ROOT / path).read_text(encoding="utf-8")


def write(path: str, content: str) -> None:
    (ROOT / path).write_text(content.rstrip() + "\n", encoding="utf-8")


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected exactly 1 match, found {count}")
    return text.replace(old, new, 1)


for required in ["AI_HANDOFF.md", "DEVELOPMENT_LOG.md"]:
    if not (ROOT / required).exists():
        raise RuntimeError(f"Required file missing: {required}")

# 1) AI_HANDOFF: point the business baseline and Stage 7A-3D completion to the real final commit.
handoff_path = "AI_HANDOFF.md"
handoff = read(handoff_path)

handoff = replace_once(
    handoff,
    "- 当前业务基线提交：`eca4f97`",
    f"- 当前业务基线提交：`{FINAL_COMMIT}`",
    "current business baseline commit",
)

handoff = replace_once(
    handoff,
    "- 最终提交：待用户真机验收通过后记录",
    f"- 最终提交：`{FINAL_COMMIT}`\n- 提交信息：`Fix WebAuthProfile API base matching`",
    "Stage 7A-3D final commit",
)

old_verification = '''## 最近真机验证状态

以 `db34e3e36beb889f1c259b45b0dd4f0a5d3501ad` 对应代码为业务基线：

- 配置页正常打开：用户确认。
- 高级设置正常展开：用户确认。
- MiMo 无需重新登录：用户确认。
- MiMo 显示原余额：用户确认。
- Kimi 显示原次数：用户确认。
- Widget 无空白、崩溃或异常退出：用户确认。
'''

new_verification = f'''## 最近真机验证状态

以 `{FINAL_COMMIT}` 对应代码为当前业务基线：

- 配置页正常打开、高级设置正常展开：用户确认。
- MiMo “连接账户”入口正常，无需重新登录，原余额正常：用户确认。
- Kimi 显示原次数：用户确认。
- 爱黄牛地址仍显示“连接账户”：用户确认。
- 非爱黄牛 OpenAI 地址不再错误显示“连接账户”：用户确认。
- 恢复爱黄牛 API Base 后“连接账户”重新出现：用户确认。
- 爱黄牛自动网页登录授权能力保持正常：用户确认。
- Widget 中爱黄牛、MiMo、Kimi 数据正常，无空白、崩溃或异常退出：用户确认。
'''

handoff = replace_once(
    handoff,
    old_verification,
    new_verification,
    "recent device verification baseline",
)

write(handoff_path, handoff)

# 2) DEVELOPMENT_LOG: append an immutable closure note with the real final commit.
log_path = "DEVELOPMENT_LOG.md"
log = read(log_path)
marker = "## 2026-07-12｜Stage 7A-3D 文档最终收口"
if marker in log:
    raise RuntimeError("DEVELOPMENT_LOG.md already contains Stage 7A-3D final documentation closure")

closure = f'''---

{marker}

**最终提交**

- 分支：`{BRANCH}`
- 最终提交：`{FINAL_COMMIT}`
- 提交信息：`Fix WebAuthProfile API base matching`

**验证证据**

- 源码与最终提交：`GitHub 已核对`。
- 编译：`BUILD SUCCESSFUL in 14s`：`本地命令已核对（执行端报告）`。
- 覆盖安装：`Success`：`本地命令已核对（执行端报告）`。
- 用户本人明确确认 Stage 7A-3D 真机测试通过：`用户真机确认`。
- MiMo 入口、原授权与余额正常；爱黄牛匹配正常；非爱黄牛 OpenAI 不再误显示“连接账户”；恢复爱黄牛地址后入口重新出现；Widget 回归正常。

**文档状态**

- `AI_HANDOFF.md` 当前业务基线已更新为本阶段最终提交。
- `AI_HANDOFF.md` 最近真机验证状态已更新到 Stage 7A-3D 实际验收结果。

**下一项唯一任务**

待确定 Stage 7B 的具体平台扩展目标。
'''

write(log_path, log.rstrip() + "\n\n" + closure.strip())

print("Stage 7A-3D documentation finalization applied successfully.")
print("Changed:")
print("- AI_HANDOFF.md")
print("- DEVELOPMENT_LOG.md")
