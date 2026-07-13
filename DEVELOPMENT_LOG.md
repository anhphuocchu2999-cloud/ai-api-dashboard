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
