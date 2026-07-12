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


# 1) Record the Stage 7A-2 completion fix.
project_path = "PROJECT.md"
project = read(project_path)
marker = "### Stage 7A-2 补充修复：配置页统一使用授权仓库"
if marker not in project:
    project = project.rstrip() + """

### Stage 7A-2 补充修复：配置页统一使用授权仓库

源码复核发现配置页仍保留独立的后台授权 JSON 读写函数，未完全经过 `BackgroundAuthRepository`。

本补充修复只完成：

- 配置页读取后台授权时调用 `BackgroundAuthRepository.load()`。
- 配置页保存后台授权时调用 `BackgroundAuthRepository.save()`。
- 配置页清除后台授权时调用 `BackgroundAuthRepository.clear()`。
- 删除配置页内部重复的 JSON 序列化和反序列化函数。

不改变现有授权键名、JSON 格式、登录方式、Adapter、数据接口、Widget 布局和缓存逻辑。
""".rstrip() + "\n"
    write(project_path, project)


# 2) Make MainActivity use the repository as the only auth persistence entry.
main_path = "app/src/main/java/com/java/myapplication/MainActivity.kt"
main = read(main_path)

if "import com.java.myapplication.adapter.auth.BackgroundAuthRepository" not in main:
    main = replace_once(
        main,
        "import com.java.myapplication.adapter.auth.BackgroundAuthConfig\n",
        "import com.java.myapplication.adapter.auth.BackgroundAuthConfig\n"
        "import com.java.myapplication.adapter.auth.BackgroundAuthRepository\n",
        "MainActivity repository import",
    )

main = replace_once(
    main,
    "            loadBackgroundAuthConfig(prefs, platform)\n",
    "            BackgroundAuthRepository.load(prefs, platform)\n",
    "Config screen repository load",
)

save_call = "saveBackgroundAuthConfig(prefs, platform, newAuth)"
save_count = main.count(save_call)
if save_count != 2:
    raise RuntimeError(f"Config screen repository save: expected 2 matches, found {save_count}")
main = main.replace(save_call, "BackgroundAuthRepository.save(prefs, platform, newAuth)")

main = replace_once(
    main,
    "                                            saveBackgroundAuthConfig(prefs, platform, emptyAuth)\n",
    "                                            BackgroundAuthRepository.clear(prefs, platform)\n",
    "Config screen repository clear",
)

start_marker = "/**\n * 加载后台授权配置\n */\nfun loadBackgroundAuthConfig"
end_marker = "/**\n * 刷新 Widget（触发 onUpdate）\n */"
start = main.find(start_marker)
end = main.find(end_marker)
if start == -1 or end == -1 or end <= start:
    raise RuntimeError("Unable to locate duplicated background auth helper block")
main = main[:start] + main[end:]

write(main_path, main)

print("Stage 7A-2 config auth repository gap fixed successfully.")
print("Changed:")
print("- PROJECT.md")
print("- MainActivity.kt")
