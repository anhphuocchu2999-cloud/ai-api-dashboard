from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def read(path: str) -> str:
    return (ROOT / path).read_text(encoding="utf-8")


def write(path: str, content: str) -> None:
    (ROOT / path).write_text(content.rstrip() + "\n", encoding="utf-8")


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected exactly 1 match, found {count}")
    return text.replace(old, new, 1)


COMMIT = "c128144d836d7ba2da36b9ba1ab4552e18d7f420"
BRANCH = "feature/stage-7a-3a-web-auth-mimo"

# 1) Update AI_HANDOFF current baseline and stage status.
handoff_path = "AI_HANDOFF.md"
handoff = read(handoff_path)

handoff = replace_once(
    handoff,
    """- 当前业务基线分支：`fix/config-auth-repository`
- 当前业务基线提交：`db34e3e36beb889f1c259b45b0dd4f0a5d3501ad`
- 当前文档基线分支：`docs/development-handoff-baseline`
- 当前文档基线提交：`289c98c64b020c2c5cfe60272bbd5a081b7d83d0`
- 下一业务阶段必须从该文档基线提交创建新分支；不得合并到 `main`，除非用户明确决定。
""",
    f"""- 当前业务基线分支：`{BRANCH}`
- 当前业务基线提交：`{COMMIT}`
- 当前文档基线分支：`docs/development-handoff-baseline`
- 当前文档基线提交：`289c98c64b020c2c5cfe60272bbd5a081b7d83d0`
- 下一业务阶段应从当前业务基线提交创建新分支；不得合并到 `main`，除非用户明确决定。
""",
    "handoff current baseline",
)

start = handoff.find("## 正在进行的阶段\n")
end = handoff.find("## 严禁操作\n")
if start == -1 or end == -1 or end <= start:
    raise RuntimeError("Unable to locate current stage block in AI_HANDOFF.md")

new_stage_block = f"""## 最近完成的阶段

`Stage 7A-3A：通用网页登录授权入口（仅迁移 MiMo Cookie 路径）` 已完成。

- 开发分支：`{BRANCH}`
- 最终提交：`{COMMIT}`
- 提交信息：`Stage 7A-3A: Migrate MiMo web auth to generic WebAuthActivity with WebAuthProfile registry`
- `WebAuthProfile`、`WebAuthProfileRegistry`、`WebAuthActivity` 已落地。
- 第一版 Registry 只注册 MiMo。
- 配置页已取消 `platform == "MiMo"` 写死判断，改为按 WebAuthProfile 能力显示“连接账户”。
- `api_config / MiMo_auth` 保持兼容。
- 用户本人已明确确认真机测试通过。
- 最终汇报未单独复述编译和覆盖安装命令输出，因此不补写不存在的具体构建时长或终端输出。

## 下一项唯一任务

`Stage 7A-3B：爱黄牛 Bearer Token 真实来源摸排`

本阶段只做证据采集与来源确认，不直接猜测实现：

1. 确认登录入口与实际后台域名。
2. 确认 Bearer Token 实际来自响应头、localStorage、sessionStorage、Cookie 或接口响应中的哪一种。
3. 记录可稳定复现的提取位置与成功判定条件。
4. 未取得真实证据前，不修改 `WebAuthActivity` 去自动提取 Bearer Token。
5. 不修改 Adapter、Widget、缓存和布局。

"""

handoff = handoff[:start] + new_stage_block + handoff[end:]
write(handoff_path, handoff)

# 2) Append a correction/closure entry to DEVELOPMENT_LOG without rewriting history.
log_path = "DEVELOPMENT_LOG.md"
log = read(log_path)
marker = "## 2026-07-12｜Stage 7A-3A 验收收口"
if marker in log:
    raise RuntimeError("DEVELOPMENT_LOG.md already contains Stage 7A-3A closure entry")

closure = f"""
---

{marker}

**完成状态**

Stage 7A-3A 已在用户本人真机验收通过后提交并推送。

**最终提交**

- 分支：`{BRANCH}`
- 最终提交：`{COMMIT}`
- 提交信息：`Stage 7A-3A: Migrate MiMo web auth to generic WebAuthActivity with WebAuthProfile registry`

**实际实现结果**

- `WebAuthProfile` 与 `WebAuthProfileRegistry` 已建立。
- 第一版 Registry 只注册 MiMo。
- MiMo 专用 `MiMoWebLoginActivity` 已迁移为通用 `WebAuthActivity`。
- 配置页按 WebAuthProfile 能力显示“连接账户”，不再写死 `platform == "MiMo"`。
- `api_config / MiMo_auth` 和现有 Cookie 检测、保存路径保持兼容。
- 未实现爱黄牛 Bearer Token 自动提取。
- 未修改 Adapter、Widget、缓存或响应式布局逻辑。

**验证证据**

- 最终提交与源码：`GitHub 已核对`。
- 用户本人明确确认真机测试通过：`用户真机确认`。
- 最终汇报未单独复述编译和覆盖安装命令输出，因此不补写不存在的具体构建时长或终端输出。

**回滚位置**

`f1cccbbbb91324cbc7a9fefb803ed4f7f514bc61`

**下一项唯一任务**

Stage 7A-3B：只摸排爱黄牛 Bearer Token 的真实来源与稳定提取条件；未取得证据前不实现自动提取。
"""

write(log_path, log.rstrip() + "\n" + closure.strip())

print("Stage 7A-3A documentation finalization applied successfully.")
print("Changed:")
print("- AI_HANDOFF.md")
print("- DEVELOPMENT_LOG.md")
