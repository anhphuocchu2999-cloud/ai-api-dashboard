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


# DEVELOPMENT_LOG: replace the baseline placeholder with the verified final commit.
log_path = "DEVELOPMENT_LOG.md"
log = read(log_path)
log = replace_once(
    log,
    """**提交 SHA**

待本阶段执行端提交后填写。

**下一项唯一任务**

Stage 7A-3：先设计并实现可配置的通用网页登录授权入口，第一步只迁移并保持 MiMo Cookie 路径不回归。
""",
    """**提交与验证**

- 最终提交：`289c98c64b020c2c5cfe60272bbd5a081b7d83d0`
- 提交信息：`Establish development handoff documentation`
- 四层文档存在、规则与历史回填检查通过：`本地命令已核对（执行端报告）`
- 提交和源码：`GitHub 已核对`
- 本阶段未改业务代码，因此未执行编译和安装。

**下一项唯一任务**

Stage 7A-3A：定义通用 `WebAuthProfile`，并且只把现有 MiMo Cookie 登录迁入通用网页登录入口；不得同时实现爱黄牛 Bearer Token。
""",
    "Documentation log completion",
)
write(log_path, log)


# AI_HANDOFF: mark documentation stage complete and establish the exact next starting point.
handoff_path = "AI_HANDOFF.md"
handoff = read(handoff_path)
handoff = replace_once(
    handoff,
    """- 当前文档分支：`docs/development-handoff-baseline`
- 文档阶段完成后，以该分支最终提交作为下一阶段起点；不得合并到 `main`，除非用户明确决定。
""",
    """- 当前文档基线分支：`docs/development-handoff-baseline`
- 当前文档基线提交：`289c98c64b020c2c5cfe60272bbd5a081b7d83d0`
- 下一业务阶段必须从该文档基线提交创建新分支；不得合并到 `main`，除非用户明确决定。
""",
    "Handoff document baseline",
)
handoff = replace_once(
    handoff,
    """## 正在进行的阶段

`Documentation Baseline`：建立可回溯开发日志和 AI 交接机制。

该阶段不改业务代码，不编译，不安装。

## 下一项唯一任务

`Stage 7A-3：通用网页登录授权入口`
""",
    """## 最近完成的阶段

`Documentation Baseline` 已完成：四层文档、证据分级、历史回填和 AI 接手规则已落库。

- 完成提交：`289c98c64b020c2c5cfe60272bbd5a081b7d83d0`
- 未改业务代码，因此未执行编译和安装。

## 下一项唯一任务

`Stage 7A-3A：通用网页登录授权入口（仅迁移 MiMo Cookie 路径）`
""",
    "Handoff current stage",
)
handoff = replace_once(
    handoff,
    """1. 先定义 `WebAuthProfile` 或等价的平台授权描述，不新建第二套后端。
2. 第一小步只把现有 MiMo 登录流程迁入通用入口，保持 Cookie 提取和现有 `MiMo_auth` 完全兼容。
3. MiMo 真机通过后，再单独研究爱黄牛 Bearer Token 的自动提取。
4. Kimi、DeepSeek 等只需要 API Key 的平台不强行显示网页登录按钮。
""",
    """1. 定义最小 `WebAuthProfile` 或等价的平台授权描述，不新建第二套后端。
2. 只把现有 MiMo 登录流程迁入通用入口，保持 Cookie 提取、`api_config / MiMo_auth` 和真机行为完全兼容。
3. 配置页按是否存在 WebAuthProfile 决定是否显示“连接账户”，不得继续写死 `platform == \"MiMo\"`。
4. 本阶段不实现爱黄牛 Bearer Token 自动提取；MiMo 真机通过后再单独立项。
5. Kimi、DeepSeek 等只需要 API Key 的平台不强行显示网页登录按钮。
""",
    "Handoff next task details",
)
write(handoff_path, handoff)

print("Documentation handoff baseline finalization applied successfully.")
print("Changed:")
print("- DEVELOPMENT_LOG.md")
print("- AI_HANDOFF.md")
