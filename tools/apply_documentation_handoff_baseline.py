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


for required in ("AGENTS.md", "PROJECT.md"):
    if not (ROOT / required).exists():
        raise RuntimeError(f"Required file missing: {required}")

for new_file in ("DEVELOPMENT_LOG.md", "AI_HANDOFF.md"):
    if (ROOT / new_file).exists():
        raise RuntimeError(f"Refusing to overwrite existing file: {new_file}")

append_once(
    "AGENTS.md",
    "## 开发日志与 AI 交接硬性规则",
    r'''## 开发日志与 AI 交接硬性规则

以下规则与编译、安装、验收规则同等优先，属于每个阶段的硬性完成条件。

### 开发开始前

1. 必须依次阅读：
   - `AGENTS.md`
   - `PROJECT.md`
   - `AI_HANDOFF.md`
   - `DEVELOPMENT_LOG.md` 中最近两个阶段
   - 当前任务直接相关源码
2. 必须确认 `AI_HANDOFF.md` 中的当前分支、基线提交、下一项唯一任务是否仍然有效。
3. 如文档与源码或 Git 历史冲突，以源码、Git 提交和真实验收证据为准，并先修正文档，不得猜测。

### 阶段提交前

1. 必须向 `DEVELOPMENT_LOG.md` 追加本阶段记录，不得覆盖或改写既有历史。
2. 必须更新 `AI_HANDOFF.md` 当前状态快照。
3. 日志至少包含：
   - 日期与阶段编号
   - 目标和问题背景
   - 方案与取舍
   - 起始分支和起始提交
   - 实际修改文件
   - 实际实现内容
   - 明确未修改内容
   - 编译结果
   - 安装结果
   - 真机验收结果
   - 提交 SHA
   - 已知警告、风险和待验证事项
   - 回滚位置
   - 下一项唯一任务
4. 代码、开发日志和交接状态必须在同一阶段保持一致；缺少任何一项不得宣称阶段完成。
5. 纯文档阶段可不编译、不安装，但必须明确写明“未改业务代码，因此未执行编译和安装”。

### 证据分级

- `GitHub 已核对`：提交、分支或源码已经在远端仓库中核实。
- `本地命令已核对`：由执行端提供完整命令结果。
- `用户真机确认`：由用户本人明确确认界面或 Widget 结果。
- `待核实`：缺少充分证据，不得写成已完成。

### 安全与可回溯

1. 开发日志不得记录 API Key、Cookie、Bearer Token、授权值或其长度、前后缀特征。
2. 不得伪造历史、编译结果、安装结果或真机结果。
3. 临时迁移脚本必须在执行完成后删除，不作为长期产品源码保留；其准备提交和最终业务提交可在日志中分别说明。
4. 另一个 AI 接手时，应能只通过上述四份文档和最近提交理解当前项目，不依赖聊天记录。'''
)

append_once(
    "PROJECT.md",
    "## Documentation Baseline：开发日志与 AI 交接基线",
    r'''---

## Documentation Baseline：开发日志与 AI 交接基线

本阶段不修改业务代码，只建立长期可回溯的开发记录和 AI 接手机制。

正式文档职责：

- `PROJECT.md`：产品目标、架构规则、阶段设计和确认范围。
- `AGENTS.md`：开发纪律、验证纪律、日志纪律和安全边界。
- `DEVELOPMENT_LOG.md`：按时间追加的开发流水和证据记录，原则上只追加不覆盖。
- `AI_HANDOFF.md`：当前状态快照，每个阶段完成时同步更新。

完成标准：

- 已回填可由 Git 提交、源码和用户验收记录支持的关键阶段。
- 无法证明的历史明确标记为“待核实”。
- 后续所有阶段必须同时维护开发日志和交接快照。
- 另一个 AI 不阅读历史聊天，也能定位当前基线、核心架构、已完成能力、风险和下一项任务。
- 本阶段未改业务代码，因此不要求编译或安装。'''
)

write(
    "DEVELOPMENT_LOG.md",
    r'''# AI API Dashboard 开发日志

> 本文件按时间追加，原则上不得覆盖既有历史。  
> 证据标签：`GitHub 已核对`、`本地命令已核对`、`用户真机确认`、`待核实`。

## 日志模板

每个新阶段追加以下内容：

- 日期与阶段
- 目标与背景
- 方案与取舍
- 起始分支 / 起始提交
- 实际修改文件
- 实现结果
- 明确未修改内容
- 编译 / 安装 / 真机证据
- 提交 SHA
- 已知风险与待验证事项
- 回滚位置
- 下一项唯一任务

---

## 2026-07-12｜Stage 6-2 仓库开发基线

**目标与背景**

将已有 Android 项目作为正常源码导入 GitHub，建立后续功能开发的基线，同时保持 `main` 不直接承载未经确认的业务改动。

**方案与结果**

- 基线分支：`baseline/stage-6-2`
- 基线提交：`cd64310d9abf604b11acebbf8549b62649487431`
- 草稿 PR：`work/project-import -> main`，PR #1，未合并。
- `main` 保持未合并状态。

**证据**

- 分支、提交和 PR：`GitHub 已核对`
- 该阶段早期完整构建证据曾不充分，因此不能仅凭 PR 描述宣称全部设备验收完成。

**回滚位置**

`cd64310d9abf604b11acebbf8549b62649487431`

---

## 2026-07-12｜持久化 Widget 网络故障兜底

**目标与背景**

短暂网络故障不得用错误状态覆盖各卡片最后一次成功数据；App 进程重启后仍能读取最近成功值。

**方案与结果**

- 分支：`feature/persistent-widget-fallback`
- 提交：`51f934773ca6da305080d4c49fb2b805c47480c3`
- 草稿 PR：PR #3，基于 `baseline/stage-6-2`，未合并。
- Kimi、MiMo、DeepSeek、OpenAI 统一使用每卡片最近成功数据兜底。
- 兜底提示：`网络有点抖，先看上次数据～`，并显示 `😂`。
- 最近成功数据持久化在本机 SharedPreferences；不缓存 API Key、Cookie 或 Token。

**验证证据**

- 源码与提交：`GitHub 已核对`
- Kimi 断网后继续显示缓存数据：`用户真机确认`
- 其他三张卡共用同一路径，但并非全部逐张制造故障验证：`待核实`

**回滚位置**

`51f934773ca6da305080d4c49fb2b805c47480c3`

---

## 2026-07-12｜Widget 响应式布局探索与竞态修复

**目标与背景**

探索 4×2、可调整大小和响应式布局，同时修复连续刷新时旧请求覆盖新状态的竞态风险。

**已知记录**

- 响应式分支准备提交：`59c19961a2fd825fb05a9e8658e240ae38727445`
- 竞态修复分支准备提交：`7f5136150d25288bcb75f73b70393d68dc0a047d`
- Provider 使用更新代次判断，避免旧异步结果覆盖最新刷新。

**当前决定**

布局方案暂缓，不在 Stage 7A 凭据工作中继续修改。现阶段不得把响应式探索当作已完成最终产品布局。

**证据**

- 分支和准备提交：`GitHub 已核对`
- 最终布局产品验收：`待核实 / 已暂缓`

---

## 2026-07-12｜Stage 7A-1 Adapter 凭据输入统一

**目标与背景**

旧 `PlatformAdapter.fetchData()` 的 `apiKey` 参数同时承载模型 API Key、后台 Bearer Token 和 Cookie，语义混乱，后续平台扩展容易错误复用凭据。

**方案与取舍**

- 不新建第二套后端。
- 保持 Adapter 为平台差异统一层。
- 新增 `AdapterRequest`，明确区分：
  - `modelApiKey`
  - `backgroundAuthType`
  - `backgroundCredential`
- 保留旧 `fetchData(apiBase, apiKey, modelName)` 作为兼容入口。
- Provider 全部通过 `AdapterFactory` 路由，不直接特殊构造 MiMo 或爱黄牛 Adapter。

**起始与提交**

- 功能分支：`feature/adapter-credential-request`
- 最终提交：`7f1cfa1140aff2c609a666a89d2274788030c947`
- 提交信息：`Unify adapter credential input`

**主要修改文件**

- `PROJECT.md`
- `BalanceWidgetProvider.kt`
- `adapter/AdapterRequest.kt`
- `adapter/PlatformAdapter.kt`
- `adapter/MiMoAdapter.kt`
- `adapter/AihuangniuAdapter.kt`

**实现结果**

- MiMo 从后台 `COOKIE` 凭据读取网页登录 Cookie。
- 爱黄牛分开使用模型 API Key 与后台 Bearer Token。
- Kimi、DeepSeek 等只需要模型 Key 的 Adapter 继续通过默认兼容入口工作。
- 日志只记录授权类型和模型 Key 是否配置，不记录凭据值或长度。

**验证证据**

- 提交和源码：`GitHub 已核对`
- `BUILD SUCCESSFUL in 28s`：`本地命令已核对（执行端报告）`
- `pm install -r` 返回 `Success`：`本地命令已核对（执行端报告）`
- MiMo 原余额、Kimi 原次数仍显示：`用户 / 执行端真机确认`

**已知警告**

- Kotlin 非阻断警告仍存在；本阶段未为清警告扩大范围。

**回滚位置**

`7f1cfa1140aff2c609a666a89d2274788030c947`

---

## 2026-07-12｜Stage 7A-2 后台授权持久化统一

**目标与背景**

后台 Cookie / Bearer Token 的 JSON 读写原先散落在 Provider 和网页登录 Activity 中，容易继续复制出多套格式。

**方案与取舍**

新增 `BackgroundAuthRepository`，统一提供：

- `load(prefs, instanceKey)`
- `save(prefs, instanceKey, config)`
- `clear(prefs, instanceKey)`

保留兼容：

- SharedPreferences：`api_config`
- 键名：`${instanceKey}_auth`
- MiMo 键：`MiMo_auth`
- JSON 字段：`authType`、`authValue`、`enabled`、`updatedAt`

**起始与提交**

- 分支：`feature/background-auth-repository`
- 最终提交：`f2a67cd66aa7d989d285d71805deef44ed8319b1`
- 提交信息：`Centralize background auth persistence`

**主要修改文件**

- `PROJECT.md`
- `BalanceWidgetProvider.kt`
- `MiMoWebLoginActivity.kt`
- `adapter/auth/BackgroundAuthRepository.kt`

**实现结果**

- Provider 不再解析后台授权 JSON。
- MiMo 登录页不再手工拼接授权 JSON。
- 原有 MiMo Cookie 无需迁移或重新登录。

**验证证据**

- 提交和源码：`GitHub 已核对`
- `BUILD SUCCESSFUL in 26s`：`本地命令已核对（执行端报告）`
- 覆盖安装 `Success`：`本地命令已核对（执行端报告）`
- MiMo 无需重新登录并显示原余额；Kimi 显示原次数：`用户 / 执行端真机确认`

**回滚位置**

`f2a67cd66aa7d989d285d71805deef44ed8319b1`

---

## 2026-07-12｜Stage 7A-2 补充修复：配置页授权读写收口

**问题发现**

源码复核发现 `MainActivity.kt` 的高级设置仍保留 `loadBackgroundAuthConfig()` 和 `saveBackgroundAuthConfig()`，配置页尚未完全经过统一仓库。

**方案与结果**

- 配置页读取改为 `BackgroundAuthRepository.load()`。
- 保存改为 `BackgroundAuthRepository.save()`。
- 清除改为 `BackgroundAuthRepository.clear()`。
- 删除配置页重复 JSON 读写函数。
- 不修改 Adapter、AdapterRequest、Widget 布局、缓存或响应式代码。

**起始与提交**

- 分支：`fix/config-auth-repository`
- 最终提交：`db34e3e36beb889f1c259b45b0dd4f0a5d3501ad`
- 提交信息：`Route config auth through repository`

**验证证据**

- 提交和源码：`GitHub 已核对`
- 修改前已执行一次编译并覆盖安装成功：`本地命令已核对（执行端报告）`
- 用户本人确认：配置页正常、高级设置正常、MiMo 无需重新登录、MiMo 原余额和 Kimi 原次数正常、Widget 无空白或崩溃：`用户真机确认`
- 最终提交阶段未重复编译和安装，符合“一次构建、一次安装”纪律。

**回滚位置**

`db34e3e36beb889f1c259b45b0dd4f0a5d3501ad`

**下一项唯一任务**

先完成 Documentation Baseline；通过后再进入 Stage 7A-3 通用网页登录授权入口设计与实现。

---

## 2026-07-12｜Documentation Baseline 开发日志与 AI 交接基线

**目标**

让另一个 AI 不依赖聊天记录，也能准确理解当前项目、证据状态、风险和下一步。

**本阶段计划**

- 新建 `DEVELOPMENT_LOG.md`。
- 新建 `AI_HANDOFF.md`。
- 在 `AGENTS.md` 增加强制日志、证据分级和交接规则。
- 在 `PROJECT.md` 记录文档职责与完成标准。
- 回填能够由 GitHub 和真机验收记录支持的关键历史。

**明确未修改**

- 不修改 Android 业务源码。
- 不修改 Adapter、授权数据、Widget、布局、缓存或配置。
- 因未改业务代码，不执行编译和安装。

**提交 SHA**

待本阶段执行端提交后填写。

**下一项唯一任务**

Stage 7A-3：先设计并实现可配置的通用网页登录授权入口，第一步只迁移并保持 MiMo Cookie 路径不回归。'''
)

write(
    "AI_HANDOFF.md",
    r'''# AI API Dashboard｜AI 交接状态

> 最近更新：2026-07-12  
> 本文件是当前状态快照；历史过程见 `DEVELOPMENT_LOG.md`。

## 新 AI 的强制阅读顺序

1. `AGENTS.md`
2. `PROJECT.md`
3. `AI_HANDOFF.md`
4. `DEVELOPMENT_LOG.md` 最近两个阶段
5. 当前任务直接相关源码

不得只依据聊天摘要直接改代码。

## 仓库与当前基线

- 仓库：`anhphuocchu2999-cloud/ai-api-dashboard`
- `main`：保持未合并，不直接开发。
- Stage 6-2 基线：`baseline/stage-6-2` / `cd64310d9abf604b11acebbf8549b62649487431`
- 当前业务基线分支：`fix/config-auth-repository`
- 当前业务基线提交：`db34e3e36beb889f1c259b45b0dd4f0a5d3501ad`
- 当前文档分支：`docs/development-handoff-baseline`
- 文档阶段完成后，以该分支最终提交作为下一阶段起点；不得合并到 `main`，除非用户明确决定。

## 应用信息

- Android 包名：`com.java.myapplication.dev`
- Debug 构建：`./gradlew assembleDebug`
- APK：`app/build/outputs/apk/debug/app-debug.apk`
- 覆盖安装：

```bash
cp app/build/outputs/apk/debug/app-debug.apk /data/local/tmp/app-debug.apk
pm install -r /data/local/tmp/app-debug.apk
```

严禁通过卸载、`pm clear` 或清数据解决问题，因为会丢失 MiMo 网页授权。

## 产品目标

桌面 Widget 监控多个 AI API 模型实例的真实余额、用量、Token、请求次数或平台可提供的其他指标。

当前固定卡片：

- Kimi
- MiMo
- DeepSeek
- OpenAI（当前实际可承载爱黄牛等 OpenAI 兼容实例）

Widget 主标题显示模型名称；中转站名称只作为备注。不得为统一界面伪造平台不存在的数据。

## 当前核心数据流

```text
配置页保存模型实例
        ↓
BalanceWidgetProvider
        ↓
AdapterFactory 路由
        ↓
AdapterRequest
├─ apiBase
├─ modelApiKey
├─ modelName
├─ backgroundAuthType
└─ backgroundCredential
        ↓
对应 PlatformAdapter
        ↓
统一 WidgetData
        ↓
每卡片缓存与 Widget 展示
```

Provider 不应解析平台协议，也不应直接特殊构造某个平台 Adapter。

## 当前授权流

```text
模型 API Key
→ 模型列表、模型调用或模型用量接口

网页登录 Cookie / Bearer Token
→ BackgroundAuthRepository
→ `${instanceKey}_auth`
→ AdapterRequest.backgroundCredential
→ 账户余额 / 套餐 / Profile 等后台接口
```

授权类型：

- `NONE`
- `COOKIE`
- `BEARER_TOKEN`

重要：Billing、Usage、Balance、Profile 是数据接口能力，不是新的认证类型。

## 平台现状

### MiMo

- 网页登录已实现：`MiMoWebLoginActivity.kt`
- 登录页：`https://platform.xiaomimimo.com/#/console/balance`
- 必要 Cookie：`api-platform_serviceToken`、`userId`
- 保存位置：`api_config / MiMo_auth`
- 读取方式：`BackgroundAuthRepository`
- 当前真机状态：无需重新登录，余额正常（用户确认）。

### Kimi / New API

- 主要使用模型 API Key。
- 已验证过 `/v1/models`、billing subscription 和 usage 类接口的兼容路径。
- 当前真机状态：请求次数仍正常（用户确认）。

### DeepSeek 官方

- 当前按官方 API Key 路径处理。
- 网页登录未设计为当前必需能力。
- 全部真实运行场景尚未在本轮逐项重新验证。

### OpenAI 卡片 / 爱黄牛

- `AihuangniuAdapter` 支持模型 API Key + 后台 Bearer Token 分离使用。
- `/v1/usage` 使用模型 Key；Profile/余额优先使用后台 Bearer Token。
- 自动网页登录获取 Bearer Token 尚未实现。
- 当前只能通过高级设置手工保存后台授权。

## 核心文件

- `MainActivity.kt`：配置页、测试连接、高级授权入口。
- `MiMoWebLoginActivity.kt`：MiMo Cookie 登录和提取。
- `BalanceWidgetProvider.kt`：刷新、Adapter 调用、缓存、Widget 渲染。
- `adapter/AdapterFactory.kt`：平台路由唯一入口。
- `adapter/AdapterRequest.kt`：模型 Key 与后台凭据分离输入。
- `adapter/PlatformAdapter.kt`：统一 Adapter 接口。
- `adapter/auth/BackgroundAuthConfig.kt`：后台授权数据模型。
- `adapter/auth/BackgroundAuthRepository.kt`：后台授权唯一持久化入口。
- `config/ConfigRepository.kt`：模型实例配置持久化。
- `PROJECT.md`：产品和架构规则。
- `AGENTS.md`：开发纪律。
- `DEVELOPMENT_LOG.md`：历史日志。

## 已完成能力

- 标准 Android 项目骨架、Widget Provider 和配置页。
- 四平台卡片与模型名称显示。
- `/v1/models` 测试连接；单模型自动选择，多模型弹窗选择。
- AdapterFactory 平台路由。
- Kimi、MiMo、DeepSeek、爱黄牛等 Adapter 路径。
- 每卡片最近成功数据持久化和临时网络故障兜底。
- MiMo 网页 Cookie 登录。
- API Key / Cookie / Bearer Token 分离传递。
- 后台授权 JSON 统一读写仓库。

## 正在进行的阶段

`Documentation Baseline`：建立可回溯开发日志和 AI 交接机制。

该阶段不改业务代码，不编译，不安装。

## 下一项唯一任务

`Stage 7A-3：通用网页登录授权入口`

最小顺序：

1. 先定义 `WebAuthProfile` 或等价的平台授权描述，不新建第二套后端。
2. 第一小步只把现有 MiMo 登录流程迁入通用入口，保持 Cookie 提取和现有 `MiMo_auth` 完全兼容。
3. MiMo 真机通过后，再单独研究爱黄牛 Bearer Token 的自动提取。
4. Kimi、DeepSeek 等只需要 API Key 的平台不强行显示网页登录按钮。

不得在同一阶段同时实现 MiMo 与爱黄牛。

## 严禁操作

- 不得修改 `main`。
- 不得未经用户确认合并分支。
- 不得卸载 App、`pm clear` 或清除应用数据。
- 不得删除、打印或暴露 MiMo Cookie、API Key、Bearer Token。
- 不得绕过 `AdapterFactory`。
- 不得让 Provider 解析平台协议或 Cookie。
- 不得同时修改布局、授权和平台接口。
- 每个阶段只做一个可独立验收的小目标。
- 业务代码变化后只编译一次、覆盖安装一次；真机测试需要用户介入时必须用醒目标题明确提醒。

## 最近真机验证状态

以 `db34e3e36beb889f1c259b45b0dd4f0a5d3501ad` 对应代码为业务基线：

- 配置页正常打开：用户确认。
- 高级设置正常展开：用户确认。
- MiMo 无需重新登录：用户确认。
- MiMo 显示原余额：用户确认。
- Kimi 显示原次数：用户确认。
- Widget 无空白、崩溃或异常退出：用户确认。

已知非阻断 Kotlin 警告仍存在，未专门清理。

## 回滚点

- 当前业务基线：`db34e3e36beb889f1c259b45b0dd4f0a5d3501ad`
- Stage 7A-2：`f2a67cd66aa7d989d285d71805deef44ed8319b1`
- Stage 7A-1：`7f1cfa1140aff2c609a666a89d2274788030c947`
- 持久化兜底：`51f934773ca6da305080d4c49fb2b805c47480c3`
- Stage 6-2 基线：`cd64310d9abf604b11acebbf8549b62649487431`

## 接手时的第一条检查命令

```bash
git branch --show-current
git status --short
git log -1 --oneline
```

输出与本文件不一致时，先核对 Git，不得直接继续开发。'''
)

print("Documentation handoff baseline applied successfully.")
print("Changed:")
print("- AGENTS.md")
print("- PROJECT.md")
print("- DEVELOPMENT_LOG.md")
print("- AI_HANDOFF.md")
