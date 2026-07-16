# AI API Dashboard 开发日志

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

**提交与验证**

- 最终提交：`289c98c64b020c2c5cfe60272bbd5a081b7d83d0`
- 提交信息：`Establish development handoff documentation`
- 四层文档存在、规则与历史回填检查通过：`本地命令已核对（执行端报告）`
- 提交和源码：`GitHub 已核对`
- 本阶段未改业务代码，因此未执行编译和安装。

**下一项唯一任务**

Stage 7A-3A：定义通用 `WebAuthProfile`，并且只把现有 MiMo Cookie 登录迁入通用网页登录入口；不得同时实现爱黄牛 Bearer Token。

---

## 2026-07-12｜Stage 7A-3A 通用网页登录授权入口（MiMo 迁移）

**目标与背景**

配置页仍通过 `platform == "MiMo"` 显示专用网页登录按钮，网页登录 Activity 也直接写死 MiMo 参数。本阶段把已经验证过的 MiMo Cookie 登录迁移到通用能力描述和通用 Activity。

**方案与取舍**

- 新增 `WebAuthProfile` 与 `WebAuthProfileRegistry`。
- 第一版 Registry 只注册 MiMo。
- 新增通用 `WebAuthActivity`，只处理已经验证过的 `COOKIE` 路径。
- 配置页根据是否存在 `WebAuthProfile` 显示“连接账户”。
- 保持 `api_config / MiMo_auth`、必要 Cookie 名称和现有保存格式不变。
- 不实现爱黄牛 Bearer Token 自动提取，不修改 Adapter、Widget、缓存或布局逻辑。

**起始基线**

- 分支：`feature/stage-7a-3a-web-auth-mimo`
- 起始提交：`f1cccbbbb91324cbc7a9fefb803ed4f7f514bc61`

**计划修改文件**

- `PROJECT.md`
- `MainActivity.kt`
- `AndroidManifest.xml`
- `WebAuthActivity.kt`（新增）
- `webauth/WebAuthProfile.kt`（新增）
- `webauth/WebAuthProfileRegistry.kt`（新增）
- `activity_web_auth.xml`（新增）
- `MiMoWebLoginActivity.kt`（删除）
- `activity_mimo_web_login.xml`（删除）
- `DEVELOPMENT_LOG.md`
- `AI_HANDOFF.md`

**当前证据状态**

- 源码实现：待执行脚本后检查。
- 编译：待执行端只执行一次。
- 覆盖安装：待编译成功后只执行一次。
- 真机验收：必须由用户本人确认，未确认前不得宣称 Stage 7A-3A 完成。
- 最终实现提交 SHA：待用户真机验收通过后提交并记录。

**回滚位置**

`f1cccbbbb91324cbc7a9fefb803ed4f7f514bc61`

**下一项唯一动作**

完成 Stage 7A-3A 编译、覆盖安装和用户真机验收；验收通过前不得进入爱黄牛 Bearer Token 自动提取。
---

## 2026-07-12｜Stage 7A-3A 验收收口

**完成状态**

Stage 7A-3A 已在用户本人真机验收通过后提交并推送。

**最终提交**

- 分支：`feature/stage-7a-3a-web-auth-mimo`
- 最终提交：`c128144d836d7ba2da36b9ba1ab4552e18d7f420`
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

---

## 2026-07-12｜Stage 7A-3B + 7A-3C 爱黄牛 Bearer Token 来源确认与自动网页登录获取

**目标与背景**

Stage 7A-3B：确认爱黄牛 Bearer Token 的真实来源与稳定提取条件。
Stage 7A-3C：基于已确认证据实现自动网页登录获取 Bearer Token。

**已确认证据**

- 爱黄牛 API Base：`https://sub2.aihuangniu.com`
- 用户已在 Kiwi 浏览器控制台亲自验证：`localStorage.getItem('auth_token')` 返回有效 Bearer Token
- Token 来源：localStorage `auth_token`
- 登录入口：`https://sub2.aihuangniu.com`

**方案与取舍**

1. `WebAuthProfile` 扩展 `apiBaseHostContains` 和 `localStorageKey` 字段（可选，默认值 null，保持向后兼容）
2. `WebAuthProfileRegistry` 注册爱黄牛 profile：
   - instanceKey = "OpenAI"（爱黄牛实例位于 OpenAI 槽位）
   - authType = BEARER_TOKEN
   - apiBaseHostContains = "aihuangniu.com"
   - localStorageKey = "auth_token"
3. `WebAuthProfileRegistry.findFor(instanceKey, apiBase)`：优先精确匹配 instanceKey，其次按 apiBase 包含匹配
4. `WebAuthActivity` 扩展：
   - COOKIE 路径完全保留（MiMo 不受影响）
   - BEARER_TOKEN 路径：启动 localStorage 轮询（每 2 秒读取一次）
   - 检测到非空 token 后自动保存并刷新 Widget
5. 配置页使用 `findFor(platform, apiBase)` 替代 `findByInstanceKey(platform)`，不重新写死 `platform == "xxx"`
6. 爱黄牛后台授权保存到 `OpenAI_auth`（实例位于 OpenAI 槽位）

**起始基线**

- 分支：`feature/stage-7a-3c-aihuangniu-web-auth`
- 起始提交：`aee830d6da03d7610d3c3fa1bb87cdeeea6e13e4`

**实际修改文件**

- `webauth/WebAuthProfile.kt`：扩展 `apiBaseHostContains` 和 `localStorageKey`
- `webauth/WebAuthProfileRegistry.kt`：注册爱黄牛 profile，新增 `findFor()`
- `WebAuthActivity.kt`：扩展 BEARER_TOKEN localStorage 轮询（COOKIE 路径完全保留）
- `MainActivity.kt`：配置页使用 `findFor(platform, apiBase)`
- `AI_HANDOFF.md`：更新状态

**明确未修改**

- MiMo Cookie 路径完全保留，未改动任何 Cookie 检测/保存逻辑
- 未修改 Adapter、Widget、缓存、布局逻辑
- 未修改 AihuangniuAdapter 业务代码
- 未修改 BalanceWidgetProvider

**验证证据**

- 编译：`BUILD SUCCESSFUL in 20s`：`本地命令已核对（执行端报告）`
- 覆盖安装：`Success`：`本地命令已核对（执行端报告）`
- 用户本人真机测试爱黄牛网页登录自动获取 Bearer Token 通过：`用户真机确认`
- MiMo 网页登录不受影响：`用户真机确认`

**回滚位置**

`aee830d6da03d7610d3c3fa1bb87cdeeea6e13e4`

**下一项唯一任务**

Stage 7A-3D（如有）：爱黄牛网页登录体验优化或已知问题修复；或进入 Stage 7B 其他平台扩展。

---

---

## 2026-07-12｜Stage 7A-3D WebAuthProfile 匹配规则修复

**问题与背景**

源码复核发现 `WebAuthProfileRegistry.findFor()` 先按 `instanceKey` 直接返回 Profile。由于爱黄牛 Profile 的 `instanceKey = OpenAI`，会导致任意 OpenAI 槽位都错误显示爱黄牛“连接账户”入口。

**修复方案**

- Profile 的 `instanceKey` 必须匹配。
- 未配置 `apiBaseHostContains` 的 Profile：实例键匹配即可。
- 配置了 `apiBaseHostContains` 的 Profile：实例键与 API Base 必须同时匹配。

**明确未修改**

- 不修改 MiMo Profile 内容。
- 不修改爱黄牛 Profile 内容。
- 不修改 `WebAuthActivity`。
- 不修改 Adapter、AdapterRequest、AdapterFactory。
- 不修改 Widget、缓存、布局、授权存储格式。

**验证证据**

- 编译：`BUILD SUCCESSFUL in 14s`：`本地命令已核对（执行端报告）`
- 覆盖安装：`Success`：`本地命令已核对（执行端报告）`
- 用户本人真机测试通过：`用户真机确认`
  - MiMo 入口正常显示，不要求重新登录，原余额正常
  - 爱黄牛地址仍显示"连接账户"
  - 非爱黄牛 OpenAI 地址（api.openai.com）不再显示"连接账户"
  - 恢复爱黄牛地址后入口重新出现
  - Widget 回归正常（爱黄牛、MiMo、Kimi 数据正常，无空白/崩溃）

**回滚位置**

`eca4f97`

**下一项唯一任务**

待确定 Stage 7B 的具体平台扩展目标。

---

## 2026-07-12｜Stage 7A-3D 文档最终收口

**最终提交**

- 分支：`fix/stage-7a-3d-web-auth-profile-match`
- 最终提交：`5d707575a1c9f98ca603d645a1794ab74a2c950b`
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

---

## 2026-07-13｜Stage 7B DeepSeek 官方能力闭环

**目标与背景**

DeepSeek 官方 Adapter 已存在，并通过 `AdapterFactory` 路由到 `/user/balance`，但当前交接文档明确记录“全部真实运行场景尚未在本轮逐项重新验证”。本阶段完成配置、模型获取、余额展示和真机回归的完整闭环。

**本阶段调整**

- `MainActivity.fetchModels()`：兼容 API Base 已经以 `/v1` 结尾的情况，避免重复拼接 `/v1/v1/models`。
- `DeepSeekOfficialAdapter`：余额解析后保留配置中选中的真实模型名，不用状态词替代模型名。
- 继续展示接口真实返回的总余额，以及可选的赠送余额、充值余额。
- 不新增 DeepSeek 网页登录，不修改 AdapterFactory、Provider、Widget 布局、缓存和其他平台协议。

**最终提交**

- 分支：`feature/stage-7b-deepseek-official-closure`
- 最终提交：`b7933edb48a607f57d91b0f12f67aa610a307155`
- 提交信息：`Complete DeepSeek official capability flow`

**验证证据**

- 编译：`BUILD SUCCESSFUL in 1m 16s`：`本地命令已核对（执行端报告）`
- 覆盖安装：`Success`：`本地命令已核对（执行端报告）`
- 用户本人真机测试通过：`用户真机确认`
  - DeepSeek 测试连接（API Base `https://api.deepseek.com`）：成功获取模型列表，选择 `deepseek-v4-pro`
  - DeepSeek Widget 余额：真实显示 `余额 3.54 ¥`，辅助指标显示 `充值 3.54 ¥`
  - Kimi 原次数正常：`剩余 2,025 次`
  - MiMo 不要求重新登录，原余额正常：`余额 ¥59.96`
  - 爱黄牛原数据正常：`余额 3.55 ¥`
  - Widget 无空白、崩溃或异常退出

**回滚位置**

`8598099`

**下一项唯一任务**

待确定 Stage 7C 的具体平台扩展目标。

---

## 2026-07-13｜Stage 7C 认证与数据能力模型统一

**目标与背景**

用户的核心目标不是简单增加平台，而是让同一个 Dashboard 能统一承载 API、网页授权和 Billing 三类数据获取路径。现有项目已经具备 API Key、Cookie、Bearer Token 和多个真实数据接口，但这些能力尚未形成机器可读的统一描述。

**本阶段调整**

- 新增 `DataSourceType`：API、网页授权、Billing。
- 新增 `DataCapability`：Models、Balance、Quota、Usage、Requests、Tokens、Profile、Subscription。
- 新增 `ProviderCapabilityProfile`。
- `PlatformAdapter` 统一暴露 `capabilityProfile`。
- Kimi、MiMo、DeepSeek、爱黄牛按当前真实实现声明能力。
- 配置页高级设置展示当前实例的数据来源、账户授权方式、Billing 接入状态和可用数据能力。

**真实性边界**

- 本阶段不新增 Billing HTTP 请求。
- 当前 Adapter 没有真实 Billing 请求时，必须显示 `Billing：当前未接入`。
- 不修改现有数据请求、JSON 解析、Widget 数据流、缓存或授权保存格式。

**验证证据**

- 编译：`BUILD SUCCESSFUL in 1m 25s`：`本地命令已核对（执行端报告）`
- 覆盖安装：`Success`：`本地命令已核对（执行端报告）`
- 用户本人真机测试通过：`用户真机确认`
  - Kimi 能力摘要：数据来源 API，账户授权无需网页授权，Billing 当前未接入，可用数据包含模型、额度、用量、请求次数
  - MiMo 能力摘要：数据来源 API + 网页授权，账户授权网页 Cookie，Billing 当前未接入，可用数据包含模型、余额
  - DeepSeek 能力摘要：数据来源 API，账户授权无需网页授权，Billing 当前未接入，可用数据包含模型、余额
  - 爱黄牛能力摘要：数据来源 API + 网页授权，账户授权网页 Bearer Token，Billing 当前未接入，可用数据包含模型、余额、用量、请求次数、Token、账户信息
  - Kimi 原数据正常：剩余 2,025 次
  - MiMo 不要求重新登录，原余额正常：余额 ¥59.96
  - DeepSeek 原余额正常：余额 3.54 ¥
  - 爱黄牛原数据正常：余额 3.55 ¥
  - Widget 无空白、崩溃或异常退出

**回滚位置**

`7ed4df4`

**最终提交**

- 分支：`feature/stage-7c-capability-model`
- 最终提交：`04be69ffd7e9a0b38f36574295f0bdd994a7d14b`
- 提交信息：`Add provider capability model`

**下一项唯一任务**

Billing 数据源真实接入（待后续阶段实现）。

---

## 2026-07-13｜Stage 7D Kimi / NewAPI Billing 数据源真实接入

**目标与背景**

Stage 7C 已建立 API、网页授权、Billing 三类统一数据来源，但 Billing 仍只是能力类型，没有任何 Adapter 被标记为真实接入。本阶段只把已有接口证据的 Kimi / NewAPI Billing 路径接入 `NewApiAdapter`。

**本阶段调整**

- `NewApiAdapter.capabilityProfile.sources` 增加 `BILLING`。
- 实际请求 `/v1/dashboard/billing/subscription`。
- 实际请求 `/v1/dashboard/billing/usage`。
- 解析 `soft_limit_usd`，兼容 `soft_limit`。
  - 解析 `total_usage`，原始值直接作为美元展示（不再除以 100）。
- Billing 与 `/api/usage/token` 独立获取；Billing 失败不覆盖原次数卡成功数据。
- Billing 成功时通过现有 `WidgetData` 合并到辅助指标。
- 同一 host 连续请求之间至少等待 500ms。

**明确未修改**

- 不修改 Provider、AdapterFactory、AdapterRequest。
- 不修改 MiMo、DeepSeek、爱黄牛。
- 不修改网页授权、配置存储、Widget 布局、响应式和缓存。
- 不修改固定槽位 / 动态实例结构。

**验证证据**

- 编译：`BUILD SUCCESSFUL in 19s`：`本地命令已核对（执行端报告）`
- 覆盖安装：`Success`：`本地命令已核对（执行端报告）`
- 用户本人真机测试通过：`用户真机确认`
  - Kimi 能力摘要：数据来源 API + Billing，Billing 已接入
  - Kimi Widget 原核心指标正常：`剩余 1,960 次`
  - Kimi Widget Billing 指标真实显示：`额度 $200163.06 · 已用 $221.53`
  - MiMo：Billing 仍显示"当前未接入"，不要求重新登录，原余额正常：`余额 ¥59.96`
  - DeepSeek：Billing 仍显示"当前未接入"，原余额正常：`余额 3.52 ¥`
  - 爱黄牛：Billing 仍显示"当前未接入"，原数据正常：`余额 3.55 ¥`
  - Widget 无空白、崩溃或异常退出

**最终提交**

- 分支：`feature/stage-7d-newapi-billing`
- 最终提交：`46a08936d00b3c0f0907b020e561e19d36377a03`
- 提交信息：`Add real NewAPI billing data source`

**回滚位置**

`6ab17ba`

**下一项唯一任务**

让窗口 / 模型实例与固定平台槽位解耦，使任意窗口能够选择并使用已经实现的 API / 网页授权 / Billing 数据来源。

---

## Stage 7D 纠偏记录：Billing 数据真实性表达修正

### 背景

原 Stage 7D 实现中，将 Billing 接口返回的 `soft_limit_usd` 和 `total_usage` 直接解释为美元金额，并在 Widget 中显示 `$200163.06` 和 `$221.53` 等数值。

### 用户真机发现

用户真机测试时发现，Widget 显示的 Billing 金额（如 $200163.06）明显不符合实际语义，怀疑单位换算错误。

### 原始字段对照

通过真机直接调用 `/api/usage/token` 和 Billing 接口，获取原始字段：

- **次数卡接口** (`/api/usage/token`):
  - `total_granted`: 12000000
  - `total_used`: 3820000
  - `total_available`: 8180000
  - `call_count`: 764
  - `per_call_quota`: 5000
  - `per_call_display_label`: "次"

- **Billing 接口** (`/v1/dashboard/billing/subscription` 和 `/v1/dashboard/billing/usage`):
  - `soft_limit_usd`: 200163.019234
  - `total_usage`: 2226950.6858

### 对应关系分析

1. **次数卡接口内部一致性**：`total_granted / per_call_quota = 2400`，与 `total_granted_display_value` 一致。
2. **Billing 与次数卡无明确对应**：`soft_limit_usd` 与 `total_granted`、`total_usage` 与 `total_used` 之间无简单线性关系。

### 结论

- Billing 接口与次数卡接口是独立数据源。
- 当前无足够证据证明 `soft_limit_usd` 和 `total_usage` 可直接作为用户侧美元金额展示。
- 字段名 `soft_limit_usd` 中的 "usd" 可能仅是命名约定，不代表实际单位。

### 纠偏措施

1. **撤销原货币解释**：不再将 `total_usage` 除以 100 转为美元。
2. **中性数值展示**：使用 "Billing 额度 X · 用量 Y" 的中性表达，不添加货币符号。
3. **保留原始精度**：解析时保留 BigDecimal 精度，UI 显示时最多保留 2 位小数。
4. **保留 Billing 数据源**：`DataSourceType.BILLING` 保留，真实请求继续。

### 代码变更

- `NewApiAdapter.kt`:
  - 移除 `totalUsageUsd = totalUsageRaw?.divide(BigDecimal("100"))`。
  - 将 `softLimitUsd` 和 `totalUsageUsd` 重命名为 `softLimitRaw` 和 `totalUsageRaw`。
  - 修改 `mergeTokenAndBilling` 函数，使用中性表达。
  - `formatUsd` 函数重命名为 `formatDecimal`。

- `PROJECT.md`:
  - 明确 `total_usage` 当前以中性数值展示，不添加货币符号。
  - Widget 展示描述更新为 "Billing 额度 X · 用量 Y"。

### 验证状态

- 代码修改完成。
- 编译和覆盖安装完成。
- 用户真机验证通过。

**状态：纠偏记录完成，用户确认测试通过。**

### 纠偏提交

- 业务纠偏提交：`5f9dafa56149b943254ee161eda026c2337af778`
- 文档收口提交：`27480252ee1f552df1ac0ce6720933d21b0bda50`
- 推送状态：已推送

### 真实性边界确认

1. **Billing HTTP 数据链真实接入成功**：
   - `/v1/dashboard/billing/subscription` 和 `/v1/dashboard/billing/usage` 接口均已真机确认 HTTP 200。
   - 已取得真实字段：`soft_limit_usd` 和 `total_usage`。

2. **现有证据不足以证明美元金额**：
   - 原固定 `/100` 和 `$` 展示已撤销。
   - 当前按“Billing 额度 / Billing 用量”中性展示。
   - 只在显示层保留两位小数。

3. **次数数据保持独立真实计算**：
   - `/api/usage/token` 的次数数据保持独立真实计算。
   - 用户确认 Kimi 的“加载中...”只是正常刷新瞬时状态。

4. **用户确认最终真机结果正常**：
   - 用户本人确认当前真机显示没有问题。

**下一项唯一任务**

Stage 8A——模型实例与固定平台槽位解耦。

目标：让窗口不再天生等于 Kimi / MiMo / DeepSeek / OpenAI，而是绑定一个独立模型实例。

**本任务内不得开始实现 Stage 8A。**

---

## 2026-07-14｜Stage 8A-1 模型实例解耦方案摸排与 PROJECT.md 落档

**目标与背景**

Stage 8A 目标是将固定四槽位（Kimi/MiMo/DeepSeek/OpenAI）解耦为独立模型实例体系。本阶段（8A-1）只做方案摸排与文档确认，不修改业务代码。

**摸排结果**

共发现 6 处固定槽位耦合点：

| # | 耦合点 | 位置 | 评分 | 迁移方向 |
|---|--------|------|------|----------|
| 1 | 配置存储层 | `MainActivity` + `ConfigRepository` | 🔴 高 | `instanceId` 为 Key 存储，保留旧 Key 兼容读取 |
| 2 | Widget Provider | `BalanceWidgetProvider` | 🔴 高 | `slotIds` 改为 `instanceId` 列表，`platformName` 拆分为 `instanceId` + `serviceType` |
| 3 | AdapterFactory | `AdapterFactory.getAdapter()` | 🟡 中 | 新增 `getAdapterByServiceType()`，保留旧入口兼容 |
| 4 | 后台授权 | `BackgroundAuthRepository` | 🟡 中 | 调用方改用 `instanceId`，兼容回退读取旧 Key |
| 5 | WidgetData 缓存 | `WidgetData.kt` | 🟡 中 | 缓存 Key 改用 `instanceId`，兼容回退读取旧 Key |
| 6 | WebAuthProfile | `WebAuthProfileRegistry` | 🟢 低 | `instanceKey` 改为 `serviceType`，保留旧匹配回退 |

**核心概念**

```text
ModelInstance
├─ instanceId        // 稳定唯一标识（如 legacy-kimi-001）
├─ displayName       // 用户可修改的卡片名称
├─ serviceType       // 决定 Adapter 类型：kimi / mimo / deepseek / openai-compatible
├─ apiBase / apiKey / modelName / enabled
├─ backgroundAuthType
└─ capabilityProfile
```

关键区分：
- `instanceId`：配置归属、Widget 绑定、授权归属、缓存归属。
- `serviceType`：AdapterFactory 路由、平台协议差异。
- `displayName`：只负责展示，不得作为存储 Key 或路由条件。

**兼容迁移要求**

- 旧配置迁移为 `legacy-kimi` / `legacy-mimo` / `legacy-deepseek` / `legacy-openai`。
- API Base / Key / modelName / enabled 保留。
- MiMo / 爱黄牛网页登录授权不得失效。
- 旧 Widget 数据和持久化兜底不得被清空。
- 迁移幂等，只执行一次。
- 不卸载、不清除应用数据。

**Stage 8A 子阶段拆分**

| 子阶段 | 目标 |
|--------|------|
| 8A-1 | 方案摸排与文档确认（当前阶段） |
| 8A-2 | 新增 ModelInstance 数据结构和实例仓库，完成旧配置只读迁移 |
| 8A-3 | 后台授权和缓存 Key 从 platformName/index 迁移到 instanceId |
| 8A-4 | Widget Provider 改为读取窗口绑定的 instanceId |

**明确不在 Stage 8A 实现**

- 新增/删除任意数量实例
- 拖动排序
- Widget 外观重做
- 动态卡片数量
- 多尺寸布局改版
- 合并到 main

**实际修改文件**

- `PROJECT.md`：追加 Stage 8A 设计章节

**明确未修改**

- 不修改任何业务代码（Kotlin/XML）。
- 不编译、不安装。

**验证证据**

- 耦合点摸排基于源码 grep 和文件阅读：`本地命令已核对`
- PROJECT.md 追加内容已检查：`本地命令已核对`

**下一项唯一任务**

Stage 8A-2：新增 ModelInstance 数据结构和实例仓库，完成旧配置只读迁移。

---

## 2026-07-14｜Stage 8A-2 新增 ModelInstance 数据结构和实例仓库，完成旧配置安全幂等迁移

**目标与背景**

在 Stage 8A-1 方案摸排基础上，将固定四槽位（Kimi/MiMo/DeepSeek/OpenAI）的旧配置迁移到独立的 ModelInstance 实例体系。本阶段只新增数据结构和仓库，不修改现有业务代码（Provider、Adapter、Widget 等）。

**方案与取舍**

- 新增 `ServiceType` 枚举：6 种类型（NEW_API / MIMO / DEEPSEEK_OFFICIAL / AIHUANGNIU / OPENAI_COMPATIBLE / UNKNOWN），持久化字符串稳定。
- 新增 `ModelInstance` 数据类：7 字段（instanceId / displayName / serviceType / apiBase / apiKey / modelName / enabled）。
  - 不持久化 `backgroundAuthType` 和 `capabilityProfile`，由运行时 Adapter 决定。
- 新增 `ModelInstanceRepository`：
  - SharedPreferences Key：`model_instances_v1`
  - Schema Version Key：`model_instance_schema_version`，版本号 1
  - `ensureMigrated(prefs)`：幂等检查，已有有效实例则跳过
  - `saveInstances()`：使用 `commit()` 同步写入，写入成功才设置版本号
  - `migrateFromLegacy()`：调用 `ConfigRepository.loadAllConfigs()` 获取旧配置，映射为 ModelInstance
  - `inferServiceType()`：按 config.id 和 apiBase 推断服务类型
  - instanceId 生成：`legacy-${config.id.lowercase()}`
- `DashboardApplication.onCreate` 触发迁移。
- 旧配置（`api_configs`、`Kimi`/`MiMo`/`DeepSeek`/`OpenAI` Key）不删除，保留兼容回滚来源。

**固定实例迁移映射**

| 旧平台 | instanceId | serviceType |
|--------|-----------|-------------|
| Kimi | `legacy-kimi` | NEW_API |
| MiMo | `legacy-mimo` | MIMO |
| DeepSeek | `legacy-deepseek` | DEEPSEEK_OFFICIAL（apiBase 含 api.deepseek.com）或 OPENAI_COMPATIBLE |
| OpenAI | `legacy-openai` | 按 apiBase 域名推断（coolyeah.net→NEW_API 等） |

**明确未修改**

- 不修改 `BalanceWidgetProvider`、`BackgroundAuthRepository`、`WidgetData`、`WebAuthActivity`、`WebAuthProfileRegistry`、`AdapterFactory`、任何 Adapter 实现、任何 Widget XML 布局。
- 不开始 Stage 8A-3 或 Stage 8A-4。
- 不合并到 `main`。

**验证证据**

- 编译：`BUILD SUCCESSFUL in 13s`：`本地命令已核对（执行端报告）`
- 覆盖安装：`Success`：`本地命令已核对（执行端报告）`
- 首次迁移验证：`本地命令已核对（执行端报告）`
  - `model_instances_v1` key 存在
  - 4 个 legacy 实例存在：`legacy-kimi`、`legacy-mimo`、`legacy-deepseek`、`legacy-openai`
  - serviceType 正确：`newapi`、`mimo`、`deepseek-official`、`aihuangniu`
- 二次启动幂等验证：`本地命令已核对（执行端报告）`
  - 实例数量不变（4 个）
  - instanceId 不变
  - 无重复实例
- 用户本人真机测试通过：`用户真机确认`
  - App 正常启动，无崩溃
  - 配置页 4 个平台数据正常显示
  - Widget 正常显示 4 张卡片
  - 点击 Widget 进入 App 正常
  - 各平台余额/数据正常刷新
  - Billing 中性数据仍正常
  - Widget 无空白、崩溃或异常退出

**最终提交**

- 分支：`feature/stage-8a-model-instances`
- 提交信息：`Stage 8A-2: Add ModelInstance data structure and repository with legacy config migration`

**回滚位置**

`a0136def27e1b307941f79a4dca6affff6ac62f8`

**下一项唯一任务**

Stage 8A-3：后台授权和缓存 Key 从 platformName/index 迁移到 instanceId。

## 2026-07-14｜Stage 8A-2B 加固 ModelInstanceRepository

**目标与背景**

Stage 8A-2 已完成四个固定槽位到 ModelInstance 的迁移，但仓库的健壮性不足：实例 JSON 完整性检查不完整、commit 结果未检查、额外配置无稳定 ID 生成规则。本轮加固代码主要保护未来损坏恢复和额外配置迁移，当前设备已有四个真实实例不受影响。

**方案与取舍**

- `hasValidInstances()` 加固为公开方法，执行 6 条完整性检查：
  1. `model_instances_v1` 存在且是非空 JSON 数组
  2. 每一项都能完整解析
  3. 每个 `instanceId` 都非空
  4. `instanceId` 不能重复
  5. `serviceType` 字段能够解析（UNKNOWN 是合法显式类型）
  6. 数组中不存在解析失败后被静默丢弃的对象
- `saveInstances()` 返回 `Boolean`，两步 commit：
  1. 先只写入 `model_instances_v1`
  2. 检查 `commit()` 返回值
  3. 数据写入成功后才单独写入 `model_instance_schema_version = 1`
  4. 再检查第二次 `commit()` 返回值
- `ensureMigrated()` 返回 `Boolean`：已有有效结构 → true；迁移和两步写入全部成功 → true；迁移来源为空或写入失败 → false
- 固定槽位 instanceId 严格映射（大小写不敏感匹配）：Kimi→`legacy-kimi`、MiMo→`legacy-mimo`、DeepSeek→`legacy-deepseek`、OpenAI→`legacy-openai`
- 额外配置稳定 ID 生成：`legacy-extra-<安全slug>-<稳定短摘要>`
  - slug 只保留小写英文字母、数字和短横线；连续非法字符合并为一个短横线；首尾短横线移除；为空时用 `unnamed`
  - 稳定短摘要：SHA-256 前 12 位
  - 摘要输入（稳定字段）：`config.id`、`config.name`、规范化 `apiBase`、`config.model`、原列表索引
  - 不得把 API Key 原文写入日志、文档或终端输出
  - 重复执行迁移必须生成相同 instanceId
  - 最终列表内如仍发生冲突，确定性消歧（追加递增序号），不能覆盖前一项
- ServiceType 推断扩展：OpenAI 槽位和所有额外配置均按 `apiBase` 域名推断（coolyeah.net→NEW_API、api.deepseek.com→DEEPSEEK_OFFICIAL、aihuangniu.com→AIHUANGNIU、platform.xiaomimimo.com→MIMO、其他→OPENAI_COMPATIBLE/UNKNOWN）
- `DashboardApplication.onCreate` 已处理 `ensureMigrated()` 返回值

**明确未修改**

- 当前设备已有四个真实实例不重迁移
- 不改变现有实例的 instanceId、serviceType、displayName
- 不删除旧 `api_configs`、`Kimi`/`MiMo`/`DeepSeek`/`OpenAI` Key
- 不清除 MiMo Cookie、爱黄牛 Bearer Token、Widget 缓存
- 不修改 `BalanceWidgetProvider`、`BackgroundAuthRepository`、`WidgetData`、`WebAuthActivity`、`WebAuthProfileRegistry`、`AdapterFactory`、任何 Adapter 实现、任何 Widget XML 布局
- 不开始 Stage 8A-3 或 Stage 8A-4
- 不合并到 `main`

**验证证据**

- 编译：待执行端只执行一次
- 覆盖安装：待编译成功后只执行一次
- 当前设备实例验证：待安装后检查
- 用户本人真机测试通过：待用户确认

**回滚位置**

`8fa901c3ac2822ae843a6d7c25667423e05b9a01`

**下一项唯一任务**

Stage 8A-3：后台授权和缓存 Key 从 platformName/index 迁移到 instanceId。

---

## 2026-07-14｜Stage 8A-2C 修复实例仓库剩余语义缺口

**目标与背景**

Stage 8A-2B 已加固实例 JSON 完整性检查和两步 commit，但仍有三个语义缺口：
1. `ensureMigrated()` 不区分 schema version 缺失 vs 数据无效——schema version 丢失会导致整个重迁移
2. `hasValidInstances()` 中 serviceType 检查使用 `fromString()` 而非严格校验——"abc"/"kimi" 等非法值会被静默接受为 UNKNOWN
3. `parseInstanceFromJson()` 使用 `optString`/`optBoolean` 掩盖字段缺失
4. OpenAI 历史槽位无法识别域名时回退到 `UNKNOWN`，应为 `OPENAI_COMPATIBLE`

**方案与取舍**

- `ensureMigrated()` 区分三种状态：
  - A. 实例数据有效 + schema version 正确 → `true`
  - B. 实例数据有效 + schema version 缺失或错误 → 只补写 schema version，不重迁移
  - C. 实例数据无效 → 从旧配置迁移
- 新增 `hasCurrentSchemaVersion(prefs)` 和 `commitSchemaVersionOnly(prefs)`
- `ServiceType` 新增 `fromStringStrict()` 方法：只接受枚举声明的持久化字符串，未知字符串返回 `null`
- `hasValidInstances()` 使用 `fromStringStrict()` 校验 serviceType，使用 `has()` + `getString`/`getBoolean` 严格读取七个字段
- `parseInstanceFromJson()` 改用 `getString`/`getBoolean` 严格读取（字段缺失时抛异常，外层 catch 返回 false）
- `inferServiceTypeByApiBase()` 新增 `defaultForOpenAiSlot` 参数：OpenAI 槽位回退到 `OPENAI_COMPATIBLE`，额外配置无法识别时才返回 `UNKNOWN`

**明确未修改**

- 当前四个真实实例不重迁移
- 不修改 `DashboardApplication`、`BalanceWidgetProvider`、`BackgroundAuthRepository`、`WidgetData`、`AdapterFactory`、`WebAuthProfileRegistry`、任何 Adapter、任何布局
- 不开始 Stage 8A-3

**验证证据**

- 编译和覆盖安装：待执行端只执行一次
- 用户本人真机测试通过：待用户确认

**回滚位置**

`686340095e53775e7ad4b92e2cb13feaec2a884c`

**下一项唯一任务**

Stage 8A-3：后台授权和缓存 Key 从 platformName/index 迁移到 instanceId。

---

## 2026-07-16｜Stage 8B-R GitHub 云端构建与远程安装交付

**目标与背景**

用户明确要求以 GitHub 远端数据为唯一基线，并希望最终直接获得手机可下载的远程安装链接，不再由用户手工执行 Gradle 和 ADB。仓库当前已为 Public，因此交付链路同时按公开仓库标准处理。

**方案与取舍**

- 保留现有 `Baseline Android Build`，在 `assembleDebug` 成功后上传 Debug APK 构建产物。
- 新增 `Android Prerelease` 工作流；推送 `v*-beta.*` 标签时构建 Debug APK，并创建 GitHub Prerelease。
- Release APK 文件名包含标签，Release 目标提交使用 GitHub 运行时的精确 SHA。
- 当前没有生产签名材料，首个远程交付明确标记为 Debug Prerelease，不冒充商店正式版。
- 不新增服务器，不在 GitHub 保存用户 API Key、Cookie、Bearer Token 或签名私钥。

**起始分支和提交**

- 分支：`feature/stage-8b-simple-connection-flow`
- 起始云端提交：`b92c4b667f50191320b57d3d18a7aa79c0968f19`

**实际修改文件**

- `.github/workflows/baseline-build.yml`
- `.github/workflows/android-prerelease.yml`
- `PROJECT.md`
- `AI_HANDOFF.md`
- `DEVELOPMENT_LOG.md`

**明确未修改**

- 未修改 Android 业务代码、Adapter、缓存、授权、配置保存和 Widget 布局。
- 未创建生产签名或正式商店 Release。
- 未合并到 `main`。

**验证状态**

- 工作流静态检查：`git diff --check` 通过。
- GitHub Actions 首次运行：`29506642960`，失败。
- 失败步骤：`Build debug APK`。
- 真实错误：Gradle Wrapper 尝试读取 Operit 专用本地路径 `file:///root/gradle/gradle-9.1.0-bin.zip`，GitHub Runner 返回 `Permission denied`，尚未进入 Android 编译。
- 修复：只在两个 GitHub 工作流中把 Wrapper 地址临时替换为 `https://services.gradle.org/distributions/gradle-9.1.0-bin.zip`；仓库内 Operit 本地配置保持不变。
- 修复后 GitHub Actions：`29506968616`，成功。
- `Build debug APK`：成功。
- `Upload debug APK`：成功。
- APK 构建产物：GitHub Actions 已确认上传。
- GitHub Prerelease：`v0.1.0-beta.1`，成功。
- 发布工作流：`29507477102`，`Build installable debug APK`、Artifact 上传和 Prerelease 发布全部成功。
- Release 目标提交：`042a7bd411cc0c90c8faefeb484c7324f0233263`。
- APK：`ai-api-dashboard-v0.1.0-beta.1-debug.apk`，大小 `11999506` 字节。
- APK SHA-256：`3F4236038302CDB27F2B76CA6D19D9BDB6E6AF3837B05F339D0574793E4AB347`。
- 公开下载已实际执行并核对文件大小与 SHA-256。
- 手机覆盖安装：待用户执行发布链接安装命令后验证。
- 真机核心流程：待用户确认。

**阶段提交 SHA**

- 云端交付工作流：`25620bce72bf4502d45d45bafe2405ca62edefd0`
- CI Gradle 路径修复：`5f8dee4e2fc3c990f36acb489dc0e5fd73b00866`
- 首个 Beta 发布触发：`042a7bd411cc0c90c8faefeb484c7324f0233263`

**回滚位置**

`b92c4b667f50191320b57d3d18a7aa79c0968f19`

**下一项唯一任务**

用户在 Operit AI 中执行一次公开 APK 下载与覆盖安装命令，并真机验收配置保存、模型映射、八连点刷新、授权绑定和缓存隔离。

---

## 2026-07-16｜Stage 8C Widget 静默刷新与卡片同步角标

**目标与问题背景**

用户安装 `v0.1.0-beta.1` 后提供真机截图：Widget 自动刷新开始时，四张卡片的数据区和底部更新时间会被“正在同步…”替换。用户明确要求不显示任何自动同步文字，刷新期间继续使用上次数据，并在正在请求的对应卡片右下角显示 `😂`。

**根因证据**

- `BalanceWidgetProvider.updateAppWidget()` 在启动后台线程前执行完整 `updateAppWidget`，主动把 `update_time` 设置为“正在同步…”。
- `showLoading()` 同时把每张卡片核心指标设置为“正在同步…”，并清空近期消耗、百分比、进度和辅助指标。
- 因为刷新开始阶段使用全量 RemoteViews 更新，Launcher 上已经显示的上次数据被加载态覆盖。
- `WidgetData.bindCacheKey()` 还会在网络失败缓存数据的辅助指标中追加 `😂`，与新的“仅表示正在刷新”语义冲突。

**方案与取舍**

- 刷新开始阶段只调用 `partiallyUpdateAppWidget()`，局部清除八连点文案并显示独立卡片角标，不更新数据区和上次更新时间。
- 新结果全部准备完成后才执行一次全量 Widget 更新，并隐藏全部同步角标。
- 仅对已启用、API Base/API Key/模型完整且当前可见的卡片显示 `😂`。
- 四张视觉卡片增加独立同步 TextView；不复用辅助指标，避免真实数据被角标覆盖。
- 删除失败缓存辅助指标中的旧 `😂` 装饰；缓存回退文案和最近成功数据逻辑保持不变。
- 保留八连点三秒窗口、前七次调皮文案和第八次真实请求规则。

**起始分支和提交**

- 远端分支：`feature/stage-8b-simple-connection-flow`
- 起始提交：`e1b0b7fde43e876af58f1eb77e24777dfc8c558b`
- 本地实施分支：`agent/widget-cache-refresh`

**实际修改文件**

- `app/src/main/java/com/java/myapplication/BalanceWidgetProvider.kt`
- `app/src/main/java/com/java/myapplication/adapter/WidgetData.kt`
- `app/src/main/res/layout/widget_balance.xml`
- `.github/workflows/android-prerelease.yml`
- `PROJECT.md`
- `AI_HANDOFF.md`
- `DEVELOPMENT_LOG.md`

**明确未修改**

- 未修改 Adapter HTTP 请求、平台 JSON 解析、AdapterFactory 路由、认证存储、缓存身份和配置保存。
- 未改变八连点计数规则。
- 未实现任意数量模型实例、拖动排序或新 Widget 尺寸。
- 未合并到 `main`。

**验证状态**

- `git diff --check`：通过，`本地命令已核对`。
- Android 主源码中“正在同步/自动同步”文本搜索：0 处，`本地命令已核对`。
- `widget_balance.xml` XML 解析：通过，`本地命令已核对`。
- GitHub Actions `assembleDebug`：待远端精确提交后只执行一次。
- APK 覆盖安装：待 GitHub Prerelease 生成后由用户执行一次。
- 真机验收：待用户确认。

**阶段提交 SHA**

- 待云端提交后回填。

**已知风险和待验证事项**

- Android Launcher 对 `partiallyUpdateAppWidget()` 的渲染由系统负责，右下角位置和刷新完成后的隐藏状态必须以用户真机为准。
- 首次添加 Widget 且没有历史 RemoteViews 时，首轮请求期间可以保持默认空数据；本阶段不伪造缓存。

**回滚位置**

`e1b0b7fde43e876af58f1eb77e24777dfc8c558b`

**下一项唯一任务**

GitHub Actions 构建并发布 `v0.1.0-beta.2`，用户覆盖安装后验收 Stage 8C。验收通过前不开始下一个业务阶段。

---

## 2026-07-16｜Stage 8C 云端构建与 Beta 交付闭环

**云端业务提交**

- 分支：`feature/stage-8b-simple-connection-flow`
- 提交：`8f4609b8dbabe802f9bd24705c896e07954a6b81`
- 提交树与本地已审阅实现树一致：`62611a91f150d82824ce55b5241ac60b8fbeb916`

**编译与发布证据**

- GitHub Actions 运行：`29509997090`
- 运行事件：开发分支 push，精确 HEAD 为 `8f4609b8dbabe802f9bd24705c896e07954a6b81`
- `Build installable debug APK`：成功。
- `Prepare release asset`：成功。
- `Upload installable APK`：成功。
- `Publish GitHub prerelease`：成功。
- 本轮只触发上述一次 `assembleDebug`。

**公开交付物**

- Prerelease：`v0.1.0-beta.2`
- Release 目标提交：`8f4609b8dbabe802f9bd24705c896e07954a6b81`
- APK：`ai-api-dashboard-v0.1.0-beta.2-debug.apk`
- 文件大小：`11999746` 字节。
- GitHub Release 摘要：`sha256:c36cdc43283fd10280ff3667a239a1b9aca21faeec14d9719d3d92c27ced3b99`。
- 公开 APK 已实际下载；本地复核 SHA-256：`C36CDC43283FD10280FF3667A239A1B9ACA21FAEEC14D9719D3D92C27CED3B99`，一致。

**安装与真机状态**

- 覆盖安装：待用户在 Operit AI 中执行一次公开下载与 `adb install -r` 命令。
- 真机验收：待用户确认同步期间旧数据、卡片 `😂` 角标、无同步文字和完成后角标消失。

**明确未继续的内容**

- 本闭环只回填云端构建与发布证据，未新增或修改业务代码，因此未再次触发编译。
- Stage 8C 用户真机验收前，不开始 `PROJECT.md` 的下一业务阶段，不合并到 `main`。

**回滚位置**

`e1b0b7fde43e876af58f1eb77e24777dfc8c558b`

**下一项唯一任务**

用户覆盖安装 `v0.1.0-beta.2` 并验收 Stage 8C。

---

## 2026-07-16｜Stage 8D-S 固定 Beta 签名准备

**目标与问题背景**

用户发现连续 GitHub Debug Prerelease 的签名证书不一致。源码和工作流确认 `app/build.gradle.kts` 没有固定签名配置，GitHub Actions 也没有重建固定 Keystore，因此每个临时 Runner 都使用自己新生成的默认 Debug Keystore。`beta.1` 与 `beta.2` 私钥均未保存，无法重新获得旧签名。

**方案与取舍**

- 新建仅用于 `.dev` Beta 包的固定 PKCS12 签名，不与未来生产签名混用。
- 私钥和密码只进入 GitHub Actions Secrets；Public 仓库只记录公开证书 SHA-256。
- Gradle 支持四项环境变量完整时启用 `stableBeta`，部分配置直接失败，本地完全未配置时继续允许普通默认 Debug 构建。
- Prerelease 工作流在编译前强制校验 Secrets，重建临时 Keystore，编译后用 `apksigner` 核对证书，再允许发布。
- 普通 CI Artifact 明确改名为临时签名测试产物，避免与可持续安装包混淆。
- 旧签名无法直接覆盖，后续使用 `run-as` 设计一次性私有 SharedPreferences 迁移；本准备阶段不执行手机卸载或数据操作。

**起始分支和提交**

- 远端分支：`feature/stage-8b-simple-connection-flow`
- 起始提交：`edef1e3af734c0c5a97bb932f07b0b68b524e509`
- 本地实施分支：`agent/stable-signing`

**签名材料状态**

- 固定 Beta Alias：`ai-api-dashboard-beta`
- 公开证书 SHA-256：`A8F816B106F23274F35E3DDC8B19C464A31F7A7BD0871E3294AA6E6922954860`
- 私钥和密码：已生成在工作区私密目录，未输出值，未加入 Git。
- 原始 PEM 私钥和证书临时文件：生成 PKCS12 后已删除。
- GitHub Actions Secrets：待写入；浏览器安全策略阻止代理访问 GitHub Settings，未绕过。

**实际修改文件（尚未提交）**

- `app/build.gradle.kts`
- `.github/workflows/android-prerelease.yml`
- `.github/workflows/baseline-build.yml`
- `.gitignore`
- `PROJECT.md`
- `AI_HANDOFF.md`
- `DEVELOPMENT_LOG.md`

**明确未修改**

- 未修改 Widget、Adapter、缓存、配置、授权或网络业务逻辑。
- 未把 Keystore、密码、PEM 或私钥提交到仓库。
- 未创建生产签名。
- 未推送工作流，未触发 GitHub Actions，未执行编译或发布。
- 未卸载手机旧版，未读取或迁移用户凭据。

**验证状态**

- 固定 PKCS12 已生成：`本地命令已核对`。
- 公开证书指纹已从生成证书读取：`本地命令已核对`。
- 源码与工作流静态检查：待执行。
- GitHub Actions Secrets：待用户允许 GitHub Settings 操作后写入。
- `assembleDebug`、APK 发布、覆盖安装和真机验收：均未执行。

**阶段提交 SHA**

- 待 Secrets 写入并完成静态检查后提交。

**已知风险和待验证事项**

- 旧 Beta 私钥不可恢复，首次固定签名版不能直接 `adb install -r`。
- 一次性数据迁移包含本机敏感凭据，必须只经过用户控制的私有目录，并在验收后删除备份。
- Secrets 缺失时不得推送 Prerelease 工作流，否则只会产生一次可预见的失败构建。

**回滚位置**

`edef1e3af734c0c5a97bb932f07b0b68b524e509`

**下一项唯一任务**

安全写入四项 GitHub Actions Secrets，然后提交并只执行一次云端构建。

---

## 2026-07-17｜Stage 8D-S GitHub Actions Secrets 写入

**实际完成**

- 使用用户明确授权的临时 GitHub 凭据写入四项仓库级 Actions Secrets。
- 仅核对 Secret 名称存在，不读取、不回显 Secret 值。
- 固定 Beta 私钥、PKCS12 和密码仍未加入 Git；Public 仓库只记录公开证书指纹。
- 写入前核对远端 `feature/stage-8b-simple-connection-flow` 仍为起始提交 `edef1e3af734c0c5a97bb932f07b0b68b524e509`，没有云端分叉。

**验证证据**

- 四项 Secret 名称均已由 GitHub 返回：`ANDROID_SIGNING_KEYSTORE_BASE64`、`ANDROID_SIGNING_STORE_PASSWORD`、`ANDROID_SIGNING_KEY_ALIAS`、`ANDROID_SIGNING_KEY_PASSWORD`。
- Secret 值未写入本文档、命令输出或仓库文件。
- 静态检查、阶段提交、云端构建与发布在下一步执行并回填。

**下一项唯一任务**

完成静态检查并一次性推送 Stage 8D-S，确认 GitHub Actions 只运行一次 `assembleDebug`。

**首次工作流定义失败与修复**

- Stage 8D-S 初始提交：`005753f11f19c579ebab8a494ce6ca937d1ff368`。
- GitHub Actions 运行：`29514358210`，在创建任何 Job 前失败，`jobs` 为空，因此没有执行 `assembleDebug`。
- `actionlint` 明确报告 Job 级 `env` 不允许使用 `runner` 上下文；错误位置为 `ANDROID_SIGNING_STORE_FILE: ${{ runner.temp }}/...`。
- 聚焦修复：在 Secrets 重建步骤中使用 Runner 自带的 `$RUNNER_TEMP`，并通过 `$GITHUB_ENV` 传给后续构建与清理步骤；签名材料、指纹和 Android 业务代码不变。
- 修复后必须先通过 `actionlint` 和 `git diff --check`，再推送触发唯一一次真实 `assembleDebug`。
