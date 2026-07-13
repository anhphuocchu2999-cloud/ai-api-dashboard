# AI API Dashboard｜AI 交接状态

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
- 当前业务基线分支：`feature/stage-7c-capability-model`
- 当前业务基线提交：`04be69ffd7e9a0b38f36574295f0bdd994a7d14b`
- 当前文档基线分支：`docs/development-handoff-baseline`
- 当前文档基线提交：`289c98c64b020c2c5cfe60272bbd5a081b7d83d0`
- 下一业务阶段应从当前业务基线提交创建新分支；不得合并到 `main`，除非用户明确决定。

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

- 网页登录已实现：`WebAuthActivity.kt`（通用网页登录授权）
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

- 按官方 API Key 路径处理。
- 网页登录未设计为当前必需能力。
- `MainActivity.fetchModels()` 已兼容 API Base 带或不带 `/v1`。
- `DeepSeekOfficialAdapter` 已真机验证余额展示（总余额 + 赠送/充值余额）。
- 当前真机状态：模型获取正常，余额正常（用户确认）。

### OpenAI 卡片 / 爱黄牛

- `AihuangniuAdapter` 支持模型 API Key + 后台 Bearer Token 分离使用。
- `/v1/usage` 使用模型 Key；Profile/余额优先使用后台 Bearer Token。
- 自动网页登录获取 Bearer Token 已实现：WebAuthActivity 通过 localStorage 轮询读取 `auth_token`。
- 配置页展开高级设置后，点击“连接账户”即可自动完成爱黄牛网页登录授权。

## 核心文件

- `MainActivity.kt`：配置页、测试连接、高级授权入口。
- `WebAuthActivity.kt`：通用网页登录授权（MiMo Cookie + 爱黄牛 localStorage Bearer Token）。
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

## 已完成阶段

`Stage 7A-3B + 3C：爱黄牛 Bearer Token 来源确认与自动网页登录获取` 已完成。

- 开发分支：`feature/stage-7a-3c-aihuangniu-web-auth`
- 最终提交：`08c7751`
- 提交信息：`Stage 7A-3B+3C: Add aihuangniu web auth with localStorage auth_token auto-extraction`
- `WebAuthProfile` 扩展 `apiBaseHostContains` 和 `localStorageKey` 字段
- `WebAuthProfileRegistry` 注册爱黄牛 profile（instanceKey=OpenAI，authType=BEARER_TOKEN）
- `WebAuthProfileRegistry.findFor(instanceKey, apiBase)` 支持按 apiBase 匹配
- `WebAuthActivity` 扩展 localStorage 轮询自动提取（COOKIE 路径完全保留）
- 配置页使用 `findFor(platform, apiBase)` 替代 `findByInstanceKey(platform)`，不重新写死 platform == "xxx"
- 爱黄牛后台授权保存到 `OpenAI_auth`（实例位于 OpenAI 槽位）
- 用户本人已明确确认真机测试通过（爱黄牛自动获取 + MiMo 不受影响）。

## 最近完成的阶段

`Stage 7A-3D：WebAuthProfile 匹配规则修复` 已完成。

- 修复分支：`fix/stage-7a-3d-web-auth-profile-match`
- 最终提交：`5d707575a1c9f98ca603d645a1794ab74a2c950b`
- 提交信息：`Fix WebAuthProfile API base matching`
- `WebAuthProfileRegistry.findFor()` 匹配语义已修复：
  - Profile 无 `apiBaseHostContains`（如 MiMo）：仅按 `instanceKey` 匹配
  - Profile 有 `apiBaseHostContains`（如爱黄牛）：`instanceKey` + `apiBase` 同时匹配
- 非爱黄牛 OpenAI 地址不再误显示"连接账户"
- 用户本人已明确确认真机测试通过（MiMo 回归 + 爱黄牛匹配 + 非爱黄牛不匹 + Widget 正常）。

## 最近完成的阶段

`Stage 7B：DeepSeek 官方能力闭环` 已完成。

- 开发分支：`feature/stage-7b-deepseek-official-closure`
- 最终提交：`b7933edb48a607f57d91b0f12f67aa610a307155`
- `MainActivity.fetchModels()` 已兼容 API Base 带或不带 `/v1`
- `DeepSeekOfficialAdapter` 保留配置中选中的真实模型名
- Widget 展示真实余额及接口真实返回的赠送/充值余额
- 用户本人已明确确认真机测试通过（DeepSeek 模型获取 + 余额展示 + 全平台回归）

## 最近完成的阶段

`Stage 7C：认证与数据能力模型统一` 已完成。

- 开发分支：`feature/stage-7c-capability-model`
- 最终提交：`04be69ffd7e9a0b38f36574295f0bdd994a7d14b`
- `DataSourceType`（API / 网页授权 / Billing）、`DataCapability`、`ProviderCapabilityProfile` 已建立
- `PlatformAdapter` 统一暴露 `capabilityProfile`
- 四个 Adapter 已按真实实现声明能力，无 Billing 虚假接入
- 配置页高级设置动态展示数据来源、账户授权、Billing 状态、可用数据
- 用户本人已明确确认真机测试通过（四个平台能力摘要 + 全平台数据回归）

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

以 `04be69ffd7e9a0b38f36574295f0bdd994a7d14b` 对应代码为当前业务基线：

- 配置页正常打开、高级设置正常展开：用户确认。
- MiMo “连接账户”入口正常，无需重新登录，原余额正常：用户确认。
- Kimi 原次数正常：用户确认。
- 爱黄牛地址仍显示“连接账户”：用户确认。
- 非爱黄牛 OpenAI 地址不再错误显示“连接账户”：用户确认。
- 恢复爱黄牛 API Base 后“连接账户”重新出现：用户确认。
- 爱黄牛自动网页登录授权能力保持正常：用户确认。
- Widget 中爱黄牛、MiMo、Kimi 数据正常，无空白、崩溃或异常退出：用户确认。

已知非阻断 Kotlin 警告仍存在，未专门清理。

## 回滚点

- 当前业务基线：`04be69ffd7e9a0b38f36574295f0bdd994a7d14b`
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

输出与本文件不一致时，先核对 Git，不得直接继续开发。
