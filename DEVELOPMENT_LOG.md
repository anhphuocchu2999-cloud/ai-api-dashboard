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

**唯一一次真实构建结果**

- 聚焦修复提交：`1a37af039448368b54b55ed56776ad9caeb492e2`。
- GitHub Actions 运行：`29514548823`。
- `Require stable beta signing secrets`：成功。
- `Build installable debug APK`：成功；`BUILD SUCCESSFUL in 2m 52s`，37 个任务已执行。
- `Verify stable signing certificate`：失败；工作流只执行了最终字符串比较，没有输出 `actual_digest`，因此现有日志无法确认实际摘要是不同还是为空。
- `Prepare release asset`、Artifact 上传与 Prerelease 发布：均按安全门禁跳过。
- 临时 PKCS12 清理：成功。
- 本地再次从 PKCS12 读取公开证书 SHA-256，仍为 `A8F816B106F23274F35E3DDC8B19C464A31F7A7BD0871E3294AA6E6922954860`。
- 按“一次编译”规则，本轮不修改后立即重试；下一轮只处理指纹校验可观测性与格式兼容，不修改 Android 业务代码。

---

## 2026-07-17｜Stage 8C-1 Widget 同步角标定位修复

**已完成的前置交付**

- Stage 8D-S 最终提交：`d8ac160618d4c0466789cc1517f12c36e1c76c66`。
- GitHub Actions：`29514966427`，构建、证书指纹校验、Artifact 和 `v0.1.0-beta.3` Prerelease 发布全部成功。
- APK SHA-256：`3A018870B2A1B01362B6F0926DD99A4B6941D5969F2317911576F02363269374`。

**真机问题与根因**

- 用户截图确认 Kimi 卡片刷新时 `😂` 与右下角辅助指标重叠。
- 根因是同步 TextView 使用纵向布局的 `0dp + layout_weight=1` 占据剩余高度；卡片内容较多时该区域被压缩，Emoji 基线落入辅助指标区域。

**聚焦修复**

- 四张卡片把辅助指标与同步角标放入同一个底部横向行。
- 辅助指标使用 `0dp + weight=1`，角标使用独立 `wrap_content` 宽度和 `4dp` 间距，固定占据最右侧。
- 未修改 `BalanceWidgetProvider`、刷新触发、八连点、缓存、Adapter、配置或认证逻辑。
- XML 解析、`actionlint` 与 `git diff --check`：通过。
- 阶段提交：`67dce82d116284b7d80655b1f3cd01ef5486e3a5`。
- GitHub Actions：`29516222227`，构建、固定证书校验、Artifact 和 Prerelease 发布全部成功。
- Release：`v0.1.0-beta.4`。
- APK：`ai-api-dashboard-v0.1.0-beta.4-debug.apk`，大小 `11999794` 字节。
- APK SHA-256：`A075EC3425FAEC1D81429292F92A0B140DC26D5B57BAE0328715E4B2827A7145`。
- 覆盖安装与角标真机验收：待用户执行并确认。

---

## 2026-07-17｜Stage 8E-S Public Beta 逻辑与数据稳定性收口

**目标和背景**

用户明确要求一次性修复已摸排的逻辑与数据缺陷，避免为十余项关联问题重复安装。用户指令覆盖本轮“一次只修一个 Bug”的默认阶段限制；范围仍限定在已有配置、授权、缓存、Widget 和平台数据链路，不新增产品功能。

**起始位置**

- 远端分支：`feature/stage-8b-simple-connection-flow`
- 起始提交：`51c1c68e180c75da47ecaa03858a0ebe33c9ac4a`
- 本地实施分支：`agent/stable-signing`

**实现内容**

- 配置与授权凭据使用 Android Keystore AES/GCM 加密保存，兼容读取历史明文。
- 网页授权改为槽位独立绑定；历史公共凭据一次迁移后删除公共副本。
- URL Host 统一严格解析；WebView 禁止非 HTTPS/非平台主页面、第三方 Cookie 和混合内容，MiMo Cookie 保存前调用真实账户接口验证。
- 缓存完整度改用稳定能力槽位，新增保存时间/缓存年龄；授权切换只清当前槽位缓存。
- 近期统计五分钟内保留最早基线并串行保护 SharedPreferences 读改写。
- 多 Widget 的刷新广播、轮播状态、删除清理和异常收口按 `appWidgetId` 隔离；同 Host 请求统一协调。
- DeepSeek API 与网页登录数据解耦、区分解析失败与登录失效并补多币种；NewAPI 改用标准 JSON；爱黄牛保留具体用量错误。
- Prerelease 工作流覆盖业务源码变更并支持指定标签；新增 Host 匹配单元测试。

**明确未修改**

- 未实现动态数量的 ModelInstance UI/Widget。
- 未创建生产商店签名，固定证书仍只用于 Debug Prerelease。
- 未上传、打印或写入任何 API Key、Cookie、Token、Keystore 或密码。
- 未合并 `main`。

**验证状态**

- `git diff --check`：通过。
- 本机首次命令未进入 Gradle（缺少 `JAVA_HOME`）；补齐 Java 后 Wrapper 指向 Operit 本地 Linux 文件；临时切官方分发后又因本机没有 Android SDK 在源码编译前停止。以上均不构成源码编译结果。
- GitHub Actions `assembleDebug`、固定证书核对、Release、覆盖安装和真机验收：待执行。
- 阶段提交 SHA：待提交后回填。

**回滚位置**

`51c1c68e180c75da47ecaa03858a0ebe33c9ac4a`

**下一项唯一任务**

推送本阶段并由 GitHub Actions 完成唯一一次真实 Android 编译与 `v0.1.0-beta.5` 固定签名发布；成功后只做文档结果回填，不再次触发业务构建。

**云端构建与交付结果**

- 业务提交：`6637b6d92d230e41deb2d15299cd201db0ca28d1`。
- GitHub Actions：`29551280479`，结论 `success`。
- `testDebugUnitTest` 与唯一一次 `assembleDebug`：成功。
- 固定 Beta 证书校验：成功，SHA-256 为 `A8F816B106F23274F35E3DDC8B19C464A31F7A7BD0871E3294AA6E6922954860`。
- Release：`v0.1.0-beta.5`。
- APK：`ai-api-dashboard-v0.1.0-beta.5-debug.apk`，大小 `11999794` 字节。
- APK SHA-256：`DB49CB09CF785118C2D1474BDD62915F813E3EA21F2C5750A25500A2C2E29738`。
- 覆盖安装：待用户执行。
- 真机验收：待用户确认，不得宣称通过。

**下一项唯一任务（交付后）**

用户使用固定签名 beta.5 覆盖安装并集中真机验收；若发现问题，以 beta.5 提交和真实复现为基线处理。

---

## 2026-07-17｜Stage 8E-1 Widget 独立俏皮状态行

**真机问题**

- beta.5 截图确认缓存回退在主指标、近期消耗、比例和辅助指标前重复显示“缓存·”，信息噪声过多。
- `😂` 与辅助指标处于同一横向容器，上方数据行数变化时会被挤压或裁切，未形成真正独立状态区。

**实现方案**

- 删除 Widget 指标中的缓存前缀和“缓存时间”辅助项，缓存元数据仍留在数据层。
- 四张卡片新增底部独占状态行，使用弹性占位将其固定到右下角，不再与辅助指标共享宽度。
- 同步中、临时失败和缓存回退统一显示 `😂 数据在路上～`；新数据成功后隐藏。
- 设置页仍通过 `isFallback` 明确说明数据来源，未伪造实时数据。

**起始位置**

- 分支：`feature/stage-8b-simple-connection-flow`
- 起始提交：`786e79aacb4ea320d69f1b09b24898130833582a`
- 计划 Release：`v0.1.0-beta.6`

**验证状态**

- XML 结构解析：通过。
- `git diff --check`：通过。
- GitHub Actions、固定证书、Release、覆盖安装与真机验收：待执行。

**下一项唯一任务**

提交并触发一次云端 `testDebugUnitTest + assembleDebug`，发布固定签名 beta.6 后等待用户真机验收。

**云端交付结果**

- 业务提交：`77c25508e24cffb6d1a5019f963a99bf429816b0`。
- GitHub Actions：`29558328511`，结论 `success`。
- `testDebugUnitTest`、唯一一次 `assembleDebug`、固定证书校验：全部成功。
- Release：`v0.1.0-beta.6`。
- APK：`ai-api-dashboard-v0.1.0-beta.6-debug.apk`，大小 `11999850` 字节。
- APK SHA-256：`C8E81A1FC919EE63FAFA936122C44F3C7C181F3A2CA3A062FB98ED42A771AA7C`。
- 覆盖安装与真机验收：待用户执行，不得宣称通过。

**下一项唯一任务（交付后）**

用户覆盖安装 beta.6，真机确认状态行独立、完整且缓存回退不污染指标文本。

---

## 2026-07-17｜Stage 8F-P1 通用网页仪表盘识别实验版

**目标和背景**

用户确认先做一个可独立试验的版本，验证“用户输入仪表盘网址并登录 → 捕获页面自身网络响应 → 本机脱敏 → 调用一次用户配置模型判断字段语义 → 人工预览结果”的完整链路；验证通过后再决定是否接入现有 Widget、Adapter 和缓存。

**方案与取舍**

- 使用同一 APK 中的第二个桌面入口“仪表盘识别实验室”，避免改动现有 App 首页和 Widget 业务链路。
- 实验 Activity 运行在独立进程，并在 Android 9 及以上使用独立 WebView 数据目录；退出时清除实验 Cookie、网页存储和缓存。
- 不对任意网页使用 `addJavascriptInterface`；用户确认当前 HTTPS 页面后，主框架脚本只观察该页面 `fetch` / `XMLHttpRequest` 已可读取的 JSON 响应，再由 `evaluateJavascript` 主动取回。
- 不读取请求头、请求体、Cookie、localStorage、sessionStorage 或密码输入；查询参数、常见认证字段、个人信息和高熵凭据在本机脱敏。
- 每次点击只调用一次 OpenAI-Compatible `POST /v1/chat/completions`，不自动重试；页面内容按不可信输入处理。
- 模型返回 endpoint 必须属于实际捕获集合，JSONPath 和指标类型通过本地结构校验；本阶段只展示，不保存映射。
- 第三方 Cookie 默认关闭，用户仅在登录循环时可主动开启兼容模式；退出实验室仍清除。

**起始分支和提交**

- 远端分支：`feature/stage-8b-simple-connection-flow`
- 起始提交：`c14643ce50f643590065ebabfbb6de5a1424f2e2`
- 本地实施分支：`agent/stable-signing`

**实际修改文件**

- `PROJECT.md`
- `app/src/main/AndroidManifest.xml`
- `app/src/main/java/com/java/myapplication/DashboardApplication.kt`
- `app/src/main/java/com/java/myapplication/discovery/DashboardDiscoveryActivity.kt`
- `app/src/main/java/com/java/myapplication/discovery/DashboardDiscoveryRules.kt`
- `app/src/main/java/com/java/myapplication/discovery/DashboardCaptureSanitizer.kt`
- `app/src/main/java/com/java/myapplication/discovery/DashboardAiAnalyzer.kt`
- `app/src/main/res/layout/activity_dashboard_discovery.xml`
- `app/src/test/java/com/java/myapplication/DashboardDiscoveryRulesTest.kt`
- `.github/workflows/android-prerelease.yml`
- `AI_HANDOFF.md`
- `DEVELOPMENT_LOG.md`

**明确未修改**

- 未修改现有 Widget、Adapter、配置仓库、授权仓库、最近成功缓存和平台解析逻辑。
- 未保存实验 API Key、捕获正文或模型映射。
- 未实现后台重放、自动刷新或开发者服务器上传。
- 未支持 Claude/Gemini 原生协议；实验首版只验证 OpenAI-Compatible 模型。
- 未合并到 `main`。

**验证状态**

- Git 基线与远端开发分支一致：`c14643ce50f643590065ebabfbb6de5a1424f2e2`。
- `git diff --check`：通过，只有工作区换行格式提示。
- 新增布局与 Manifest XML 解析：通过。
- 新实验源码搜索：未使用 `addJavascriptInterface`，未读取 Cookie 内容、请求头、请求体或 Web Storage 内容。
- 本机未发现 Android SDK、Java 或 Kotlin 编译器，因此未伪造本地编译结果。
- GitHub Actions `testDebugUnitTest + assembleDebug`、固定证书校验、`v0.1.0-beta.7` Prerelease：待推送后只执行一次。
- 覆盖安装与真机链路验收：待用户执行。

**阶段提交 SHA**

- 业务与实验实现提交：`5cc1311eccc2465cd09eba5ed87d1e95c8c3d793`。

**已知限制与待验证事项**

- Service Worker、原生网络栈、超大 JSON、服务器端渲染且无 JSON 请求的站点可能无法由首版脚本捕获。
- Google 等禁止嵌入式 User-Agent 的 OAuth 登录可能失败，需后续评估系统浏览器/OAuth 回跳方案。
- Android 8 及更早系统没有 WebView 独立数据目录 API，需以真实设备结果确认第二进程兼容性。
- AI 对字段语义的判断必须由用户核对，不得直接视为可信生产映射。

**回滚位置**

`c14643ce50f643590065ebabfbb6de5a1424f2e2`

**下一项唯一任务**

推送精确提交，由 GitHub Actions 执行唯一一次单元测试与 `assembleDebug`，发布固定签名 `v0.1.0-beta.7`；用户安装后只验收实验链路，不接入现有 Widget。

**云端构建与交付结果**

- 远端开发分支 HEAD：`5cc1311eccc2465cd09eba5ed87d1e95c8c3d793`，与业务提交一致。
- GitHub Actions：`29565363529`，结论 `success`。
- `Test and build installable debug APK`：成功；本轮只执行这一次 `testDebugUnitTest + assembleDebug`。
- 固定 Beta 证书校验：成功；工作流期望证书 SHA-256 为 `A8F816B106F23274F35E3DDC8B19C464A31F7A7BD0871E3294AA6E6922954860`。
- Release：`v0.1.0-beta.7`，目标提交与业务提交一致，标记为 Prerelease。
- APK：`ai-api-dashboard-v0.1.0-beta.7-debug.apk`，大小 `12036378` 字节。
- GitHub Release 摘要：`sha256:a84b1f0b0725aeb5322bd94eed642e62364ce5ca605e6632ec620c17d423f216`。
- 从公开 Release 重新下载后本地复核 SHA-256：`A84B1F0B0725AEB5322BD94EED642E62364CE5CA605E6632EC620C17D423F216`，一致。
- 覆盖安装：待用户在 Operit AI 中执行一次。
- 真机实验验收：待用户验证登录、确认页面、捕获数量、单次 AI 识别和结果预览，不得宣称通过。

**下一项唯一任务（交付后）**

用户覆盖安装 `v0.1.0-beta.7`，打开第二个桌面入口“仪表盘识别实验室”，完成一次真实站点实验并回传识别结果；在用户确认前不接入现有 Widget。

---

## 2026-07-17｜Stage 8F-P2 更早捕获与本机字段核对

**目标和真实验收背景**

用户使用 beta.7 完成真实站点实验并回传模型 JSON。AI 正确理解页面用途、两个渠道余额和两个模型 Token 信息，同时捕获到 `https://openrouter.fans/dashboard/billing/usage` 的 `$.total_usage`；但余额与 Token 四项只有页面文字，没有 endpoint 和 JSON 路径。该证据确认语义识别链路可行，也确认 P1 仍无法区分“页面看见”与“真实接口字段”。

**方案与取舍**

- 新增官方稳定版 AndroidX WebKit `1.16.0`，用户确认页面后按精确 HTTPS Origin 注册 Document Start Script，使观察器在网页业务脚本之前安装。
- WebView 不支持该能力时继续使用 P1 的定时注入，不因设备版本中断实验。
- 捕获候选保留本机脱敏后的完整、有效 JSON 用于核对；发送给模型的每条文本仍限制为 8,000 字符。
- 新增有限 JSON 路径解释器，只允许属性和数组下标，不执行脚本、过滤器、递归或通配符。
- AI 的 endpoint 必须来自本次捕获，JSON 路径必须在对应响应中真实取值，实际类型必须与 AI 声明一致，脱敏/截断标记不得通过。
- 通过核对的字段输出到 `verifiedMetrics`；空 endpoint、空路径、虚构接口、取值失败或类型不符统一降级为 `observations` 并给出中文原因。
- 针对用户这次的真实结果新增回归规则：空接口余额不能成为自动刷新字段，真实 `$.total_usage` 才能继续进入本机取值核对。

**起始分支和提交**

- 远端分支：`feature/stage-8b-simple-connection-flow`
- 起始提交：`df46595608f6de9bc9b7f3ff7568ce6c0f309165`
- 本地实施分支：`agent/stable-signing`

**实际修改文件**

- `PROJECT.md`
- `gradle/libs.versions.toml`
- `app/build.gradle.kts`
- `app/src/main/java/com/java/myapplication/discovery/DashboardDiscoveryActivity.kt`
- `app/src/main/java/com/java/myapplication/discovery/DashboardCaptureSanitizer.kt`
- `app/src/main/java/com/java/myapplication/discovery/DashboardAiAnalyzer.kt`
- `app/src/main/java/com/java/myapplication/discovery/DashboardJsonPathValidator.kt`
- `app/src/main/java/com/java/myapplication/discovery/DashboardMetricVerificationRules.kt`
- `app/src/main/res/layout/activity_dashboard_discovery.xml`
- `app/src/test/java/com/java/myapplication/DashboardJsonPathValidatorTest.kt`
- `app/src/test/java/com/java/myapplication/DashboardMetricVerificationRulesTest.kt`
- `.github/workflows/android-prerelease.yml`
- `AI_HANDOFF.md`
- `DEVELOPMENT_LOG.md`

**明确未修改**

- 未修改现有 Widget、Adapter、配置、授权和最近成功缓存。
- 未保存站点规则、实验 Cookie、捕获正文、API Key 或模型映射。
- 未捕获请求头、请求体或认证字段，未实现后台接口重放。
- 未把页面文字观察值直接接入 Widget。
- 未合并到 `main`。

**验证状态**

- P1 真实站点实验：`用户真机确认`，语义识别成功；一个接口字段具备候选路径，四个页面观察值缺少接口路径。
- AndroidX WebKit 官方文档：`addDocumentStartJavaScript` 在页面脚本之前运行并支持 Origin 规则；已核对。
- 官方 WebKit 1.16.0 AAR metadata：`minCompileSdk=33`、`minAndroidGradlePluginVersion=7.2.0`，兼容项目 `compileSdk=35`、AGP 9.0.0。
- `git diff --check`：通过，仅有现有工作区换行提示。
- 新增布局和 Manifest XML 解析：通过。
- 实验源码未新增 `addJavascriptInterface`，未读取网页请求头、请求体或 Web Storage 内容。
- 新增纯 JVM 规则测试覆盖安全路径、数组下标、缺失路径、危险表达式、空 endpoint 降级和虚构 endpoint 拒绝；GitHub Actions 已执行通过。
- 本机无 Android SDK/Java/Kotlin 编译环境，未伪造本地编译结果。
- GitHub Actions `29571350421`：`testDebugUnitTest + assembleDebug`、固定证书校验与 beta.8 发布均成功；本阶段只执行这一轮云端测试与编译。
- 覆盖安装与真机验收：待用户执行。

**阶段提交 SHA**

- 业务与实验实现提交：`02aae94f2f9360cac30be0f255d156c04397b9d5`。

**云端构建与交付结果**

- 远端开发分支和 Release 目标：`02aae94f2f9360cac30be0f255d156c04397b9d5`。
- GitHub Actions：`29571350421`，结论 `success`，运行时间 `2026-07-17T09:49:44Z` 至 `2026-07-17T09:53:24Z`。
- Release：`v0.1.0-beta.8`，标记为 Prerelease。
- APK：`ai-api-dashboard-v0.1.0-beta.8-debug.apk`，大小 `12134847` 字节。
- 从公开 Release 下载后 SHA-256：`5BAC11C9662087920F4532915551AD9F4EBDB2F2C27508FAC8AECFC5BA9A6DD1`。
- 从 APK v2 签名块独立提取的证书 SHA-256：`A8F816B106F23274F35E3DDC8B19C464A31F7A7BD0871E3294AA6E6922954860`，与固定 Beta 证书一致。
- 覆盖安装和真机 P2 复测：待用户执行，不得宣称通过。

**已知限制与待验证事项**

- 提前注入仍无法直接取得 Service Worker、原生网络栈或不可读跨域响应的正文。
- POST/GraphQL 即使识别出响应字段，本阶段也没有保存请求参数，因此还不能后台重放。
- 页面观察值仍可帮助用户理解页面，但必须保持为观察信息。
- 本阶段只证明字段来源和取值，不代表已完成自动刷新或 Widget 集成。

**回滚位置**

`df46595608f6de9bc9b7f3ff7568ce6c0f309165`

**下一项唯一任务**

用户覆盖安装 `v0.1.0-beta.8`，使用 beta.7 的同一站点复测：`$.total_usage` 应进入 `verifiedMetrics`，四个缺少 endpoint/jsonPath 的页面文字指标应进入 `observations`；通过前不保存规则、不接入 Widget。

---

## 2026-07-18｜Stage 8F-P2-T 模型识别请求超时修复

**目标和真机问题**

用户覆盖安装 beta.8 后完成页面捕获，但点击“调用一次 AI 识别”时提示模型接口超时。源码核对确认 `DashboardAiAnalyzer` 的读取超时固定为 60 秒；P2 的提示数据最多包含 12 条、每条 8,000 字符的响应片段和 6,000 字符页面文字，最坏接近 10 万字符，较慢中转模型可能无法在 60 秒内完成。

**方案与取舍**

- 只精简发送给模型的副本：最高优先级 8 条响应、每条 4,000 字符，页面文字 4,000 字符。
- 本机继续保留最多 12 条完整脱敏候选，AI 返回后仍在完整候选中核对 endpoint、JSON 路径和值类型。
- 连接时限保持 20 秒，读取时限调整为有限的 120 秒；不自动重试、不无限等待。
- 调用期间显示“通常需要 10～90 秒”，超过 120 秒后给出普通用户可理解的中文建议。
- 将请求限制集中到纯 Kotlin `DashboardAiRequestPolicy`，新增测试覆盖候选数、文本上限和有限超时。

**起始分支和提交**

- 远端分支：`feature/stage-8b-simple-connection-flow`
- 起始提交：`9c734bad602756cd33d50049bf064a96ba28a753`
- 本地实施分支：`agent/stable-signing`

**实际修改文件**

- `PROJECT.md`
- `app/src/main/java/com/java/myapplication/discovery/DashboardAiRequestPolicy.kt`
- `app/src/main/java/com/java/myapplication/discovery/DashboardAiAnalyzer.kt`
- `app/src/main/java/com/java/myapplication/discovery/DashboardCaptureSanitizer.kt`
- `app/src/main/java/com/java/myapplication/discovery/DashboardDiscoveryActivity.kt`
- `app/src/main/res/layout/activity_dashboard_discovery.xml`
- `app/src/test/java/com/java/myapplication/DashboardAiRequestPolicyTest.kt`
- `.github/workflows/android-prerelease.yml`
- `AI_HANDOFF.md`
- `DEVELOPMENT_LOG.md`

**明确未修改**

- 未改变已验证字段和页面观察的判定规则。
- 未修改现有 Widget、Adapter、配置、授权和最近成功缓存。
- 未保存映射、捕获正文或 API Key，未实现后台接口重放。
- 未新增自动重试、并发模型请求或开发者服务器上传。
- 未合并到 `main`。

**验证状态（推送前）**

- 用户真机证据：beta.8 在模型识别阶段报告超时。
- 源码证据：读取时限为 60 秒；模型输入上限最坏接近 10 万字符。
- `git diff --check`：通过，仅有现有工作区换行提示。
- 实验布局 XML 解析：通过。
- 新增纯 JVM 测试：GitHub Actions 已执行通过。
- 本机未执行 Android 编译；GitHub Actions `29638659078` 的 `testDebugUnitTest + assembleDebug`、固定证书校验与 beta.9 发布均成功，本阶段只执行这一轮云端测试与编译。
- 覆盖安装与真机验收：待用户执行。

**阶段提交 SHA**

- 业务与超时修复提交：`6d9dad75f5f083a49d4ec926ba995f33ac1056d3`。

**云端构建与交付结果**

- 远端开发分支和 Release 目标：`6d9dad75f5f083a49d4ec926ba995f33ac1056d3`。
- GitHub Actions：`29638659078`，结论 `success`，运行时间 `2026-07-18T09:08:20Z` 至 `2026-07-18T09:09:47Z`。
- Release：`v0.1.0-beta.9`，标记为 Prerelease。
- APK：`ai-api-dashboard-v0.1.0-beta.9-debug.apk`，大小 `12134847` 字节。
- 从公开 Release 下载后 SHA-256：`135EF2063C22CC4F780F9B54C4172B01D1E69B9D5DB27D61F32E857DC59E3888`。
- 从 APK v2 签名块独立提取的证书 SHA-256：`A8F816B106F23274F35E3DDC8B19C464A31F7A7BD0871E3294AA6E6922954860`，与固定 Beta 证书一致。
- 覆盖安装与真机超时复测：待用户执行，不得宣称通过。

**已知限制与待验证事项**

- 如果目标中转或模型自身超过 120 秒仍未返回，本阶段会明确结束而不是无限等待。
- 精简后的响应片段仍可能遗漏位于超长 JSON 尾部的语义线索；本机完整候选只负责验证 AI 已指出的路径。
- 本阶段不解决 Service Worker、POST/GraphQL 重放或 Widget 集成。

**回滚位置**

`9c734bad602756cd33d50049bf064a96ba28a753`

**下一项唯一任务**

用户覆盖安装 `v0.1.0-beta.9`，使用同一站点和模型复测“调用一次 AI 识别”；确认不再被原 60 秒时限提前终止，并核对 `verifiedMetrics` / `observations` 分流。

---

## 2026-07-18｜Stage 8F-P2-J 安全相对 JSON 路径兼容

**目标和真机问题**

用户使用 beta.9 成功完成一次 AI 识别，证明本次没有再被原 60 秒时限提前终止。模型从 MiMo 真实 `https://platform.xiaomimimo.com/api/v1/usage` 响应识别出 12 个余额、Token、请求次数和限流字段，但返回 `data.costUsage.totalCost` 等省略 `$` 根标记的点路径；本机验证器只接受 `$.data.costUsage.totalCost`，因此 `verifiedMetrics` 为空，12 项全部以“字段路径格式不安全”降级到 `observations`。另有 6 项页面文字观察信息按预期保留。

**方案与取舍**

- 新增安全路径规范化：普通相对点路径在本机补为标准 `$` 根路径。
- 规范化仅改变语法表示，不跳过原有安全解析、endpoint 捕获集合、真实取值和类型一致性核对。
- AI 结果展示统一写回规范化路径，避免后续规则格式混杂。
- 提示模型优先返回 `$.` 标准路径，但本机不能把正确性寄托在模型格式完全一致上。
- 相对形式的递归、通配符、过滤器、函数、脚本和反斜线仍拒绝。

**起始分支和提交**

- 远端分支：`feature/stage-8b-simple-connection-flow`
- 起始提交：`91ba4d2c22dc2cf7f8705110fc157a95c1560e81`
- 本地实施分支：`agent/stable-signing`

**实际修改文件**

- `PROJECT.md`
- `app/src/main/java/com/java/myapplication/discovery/DashboardJsonPathValidator.kt`
- `app/src/main/java/com/java/myapplication/discovery/DashboardAiAnalyzer.kt`
- `app/src/main/res/layout/activity_dashboard_discovery.xml`
- `app/src/test/java/com/java/myapplication/DashboardJsonPathValidatorTest.kt`
- `.github/workflows/android-prerelease.yml`
- `AI_HANDOFF.md`
- `DEVELOPMENT_LOG.md`

**明确未修改**

- 未放宽 endpoint 必须来自本次捕获、字段必须真实存在和实际类型必须一致的规则。
- 未修改模型超时、捕获条数、脱敏逻辑、Widget、Adapter、配置、授权或缓存。
- 未保存映射、捕获正文或 API Key，未实现后台接口重放。
- 未新增自动重试或开发者服务器上传，未合并 `main`。

**验证状态（推送前）**

- 用户真机证据：beta.9 AI 调用成功返回；12 个真实接口候选只因缺少 `$` 根标记被拒绝，6 个页面观察值分流正确。
- 源码证据：原解析器 `parse()` 明确要求路径首字符必须为 `$`。
- `git diff --check`：通过，仅有现有工作区换行提示。
- 实验布局 XML 解析：通过。
- 新增回归测试覆盖相对数组点路径规范化，以及相对通配符、过滤器和函数继续拒绝；GitHub Actions 已执行通过。
- 本机未执行 Android 编译；GitHub Actions `29639173092` 的 `testDebugUnitTest + assembleDebug`、固定证书校验与 beta.10 发布均成功，本阶段只执行这一轮云端测试与编译。
- 覆盖安装与真机验收：待用户执行。

**阶段提交 SHA**

- 业务与路径兼容提交：`d391f938b30313cf2f186d4f7e7b2bd41ed166a3`。

**云端构建与交付结果**

- 远端开发分支和 Release 目标：`d391f938b30313cf2f186d4f7e7b2bd41ed166a3`。
- GitHub Actions：`29639173092`，结论 `success`，运行时间 `2026-07-18T09:26:55Z` 至 `2026-07-18T09:28:21Z`。
- Release：`v0.1.0-beta.10`，标记为 Prerelease。
- APK：`ai-api-dashboard-v0.1.0-beta.10-debug.apk`，大小 `12134847` 字节。
- 从公开 Release 下载后 SHA-256：`D62EA7E062B675D35F1AF51DE29B6CF8EB810B9898B36950F134037424CFDA75`。
- 从 APK v2 签名块独立提取的证书 SHA-256：`A8F816B106F23274F35E3DDC8B19C464A31F7A7BD0871E3294AA6E6922954860`，与固定 Beta 证书一致。
- 覆盖安装与真机路径复测：待用户执行，不得宣称通过。

**已知限制与待验证事项**

- 只有安全简单路径会被规范化；复杂 JSONPath 表达式仍不支持。
- 路径通过语法规范化后仍可能因真实字段不存在或类型不一致而降级，这是预期真实性保护。
- 本阶段不保存映射、不重放请求、不接入 Widget。

**回滚位置**

`91ba4d2c22dc2cf7f8705110fc157a95c1560e81`

**下一项唯一任务**

用户覆盖安装 `v0.1.0-beta.10`，使用同一 MiMo 页面复测；确认 12 个相对点路径规范化后按真实存在性和类型进入 `verifiedMetrics` 或给出准确降级原因，6 个页面文字值继续留在 `observations`。

---

## 2026-07-18｜Stage 8F-P2-J 真机验收闭环

**用户真机结果**

- beta.10 成功完成单次 AI 识别，没有出现原 60 秒提前超时。
- 13 个 MiMo 真实接口字段进入 `verifiedMetrics`。
- 所有通过字段的路径均以标准 `$.` 开头，`actualValueType` 与 AI 声明类型一致，并带有“本机已从本次捕获响应中取到该值”的验证标记。
- 2 个无法对应真实接口字段的页面文字数字继续进入 `observations`，没有冒充可自动刷新数据。
- 为保护用户数据，公开开发日志只记录字段数量与验证结论，不记录本次真实消费额、Token 数或限额样本值。

**验收结论**

- Stage 8F-P2-T 模型超时修复：`用户真机确认`，本次调用成功完成。
- Stage 8F-P2-J 相对 JSON 路径兼容：`用户真机确认`，通过。
- “真实接口字段”和“页面观察信息”分流：`用户真机确认`，通过。

**本次文档闭环**

- 未修改业务代码，因此未执行新的编译和安装。
- 未触发新的 GitHub Actions 或 APK Release。
- 未保存识别规则、未重放网页请求、未接入 Widget、未合并 `main`。

**下一项唯一任务**

停止开发并等待用户决定下一阶段；在用户确认前不得把实验映射持久化或接入现有 Widget。

---

## 2026-07-18｜Stage 8F-P3 已验证 GET + Cookie 请求配方

**目标和问题背景**

Stage 8F-P2-J 已由用户真机确认：MiMo 页面能够捕获真实 `/api/v1/usage` JSON，13 个字段通过 endpoint、JSON 路径和值类型的本机核对。用户进一步确认将识别结果整合进现有版本，使 App 记住已验证接口和字段规则，以后能够直接请求，而不是每次重新打开网页并调用 AI。

**方案与取舍**

- 本阶段只完成一项能力：把已验证的简单 `GET + Cookie + JSON` 接口保存为最近一份本机请求配方。
- AI 仍只负责首次字段语义识别；保存前必须使用当前 WebView Cookie 重新直连一次，并对全部字段做第二次真实取值与类型核对。
- endpoint 必须来自本次捕获、与确认页面精确同 Origin、无查询参数且方法只能是 GET。
- Cookie 按 endpoint 收集后整体使用 Android Keystore 加密；配方 JSON 只保存最小规则，不含凭据、捕获正文、样本值或 AI API Key。
- 后续可从实验首页直接刷新或删除配方；刷新失败保留配方并提示重新登录/识别，不把旧值冒充新值。
- 先在实验室完成真机闭环，验收通过后才允许作为通用数据源接入 Widget。

**起始分支和提交**

- 远端分支：`feature/stage-8b-simple-connection-flow`
- 起始提交：`dc37698e140e92cb0e79c5c9c8c14d9d02e7b9a5`
- 本地实施分支：`agent/stable-signing`

**实际修改文件（提交前）**

- `PROJECT.md`
- `app/src/main/java/com/java/myapplication/discovery/DashboardDiscoveryActivity.kt`
- `app/src/main/java/com/java/myapplication/discovery/DashboardDiscoveryRules.kt`
- `app/src/main/java/com/java/myapplication/discovery/DashboardCaptureSanitizer.kt`
- `app/src/main/java/com/java/myapplication/discovery/DashboardAiAnalyzer.kt`
- `app/src/main/java/com/java/myapplication/discovery/DashboardRequestRecipe.kt`
- `app/src/main/java/com/java/myapplication/discovery/DashboardRecipeRepository.kt`
- `app/src/main/java/com/java/myapplication/discovery/DashboardRecipeClient.kt`
- `app/src/main/res/layout/activity_dashboard_discovery.xml`
- `app/src/test/java/com/java/myapplication/DashboardDiscoveryRulesTest.kt`
- `app/src/test/java/com/java/myapplication/DashboardRecipeRulesTest.kt`
- `.github/workflows/android-prerelease.yml`
- `AI_HANDOFF.md`
- `DEVELOPMENT_LOG.md`

**明确未修改**

- 未修改 Widget、AdapterFactory、现有平台 Adapter、槽位配置、授权仓库或最近成功缓存。
- 未替换 MiMo、DeepSeek 已有专用直连接口。
- 未支持 POST、GraphQL、查询参数、跨 Origin、Bearer/OAuth 或 localStorage 重放。
- 未保存多份配方、未定时刷新、未上传开发者服务器、未合并 `main`。

**验证状态（推送前）**

- 云端分支与本地起点一致，起始工作区干净。
- `PROJECT.md` 已先记录用户确认的正式 P3 需求和安全边界。
- `git diff --check`：通过，仅有现有 Windows 换行提示。
- 实验布局 XML：解析通过。
- 新增纯规则测试覆盖同 Origin GET 成功、POST 拒绝、带查询参数拒绝和跨 Origin 拒绝；配方序列化由 Android 真机链路待验收。
- 本机没有 Android SDK、ADB 或 Java，无法执行 Android 编译和安装；不得宣称本地已编译或已安装。
- GitHub Actions 首次运行 `29640204414`：`testDebugUnitTest + assembleDebug` 步骤失败，签名和发布未执行。公开日志只暴露退出码；源码复核发现新增普通 JVM 测试直接调用 Android `org.json`，该环境不提供真实 Android JSON 实现。只移除这条环境不成立的序列化测试，保留全部纯规则测试；业务代码未改。
- 修正后 GitHub Actions `29640399072`：`testDebugUnitTest + assembleDebug` 成功，固定证书校验、Artifact 上传和 beta.11 发布全部成功。
- 覆盖安装与真机验收：待用户执行。

**阶段提交 SHA**

- 业务提交：`1f1c23b9f3fd00b3b107ccfde78577d274cb4cd2`。
- JVM 测试环境修正与 Release 目标：`28b9df9ef6333154828a87caaa3a03624f6f78c6`。

**云端构建与交付结果**

- 成功运行：`29640399072`，结论 `success`。
- Release：`v0.1.0-beta.11`，标记为 Prerelease。
- APK：`ai-api-dashboard-v0.1.0-beta.11-debug.apk`，大小 `12151827` 字节。
- 从公开 Release 下载后 SHA-256：`A8390E6E7FE89FE52D8FBB18AED7178C3ECF696776F65784E70693A2B7D9777A`。
- 工作流固定 Beta 证书 SHA-256 校验：`A8F816B106F23274F35E3DDC8B19C464A31F7A7BD0871E3294AA6E6922954860`，通过。
- 覆盖安装和真机配方复测：待用户执行，不得宣称通过。

**已知限制与待验证事项**

- 部分网站会使用 POST、GraphQL、动态 CSRF、查询参数或 localStorage Token；首版会明确拒绝，不会假装通用直连成功。
- 某些 Cookie 具有更窄的 Path；实现按 endpoint 分别读取和加密保存，仍需真实站点验证其退出 WebView 后能否重放。
- P3 成功只证明实验室直连闭环，不等于 Widget 已接入。

**回滚位置**

`dc37698e140e92cb0e79c5c9c8c14d9d02e7b9a5`

**下一项唯一任务**

用户覆盖安装 beta.11，验证同 Origin GET + Cookie 站点能够直接测试并保存；关闭重开实验室后点击“不调用 AI，直接刷新”仍取得新数据。验收前停止，不进入 Widget 接入。

---

## 2026-07-18｜Stage 8F-P3 真机验收闭环

**用户真机结果**

- 用户明确回复 beta.11 测试通过。
- 已验证请求配方能够完成“直接测试并加密保存”。
- 关闭并重新打开实验室后，已保存配方能够在不调用 AI 的情况下直接刷新。

**验收结论**

- GET + Cookie 真实接口二次直连核对：`用户真机确认`，通过。
- Android Keystore 加密保存后的跨启动读取与直刷：`用户真机确认`，通过。
- Stage 8F-P3：代码、云端构建、固定签名 beta.11 发布和用户真机验收均已闭环。

**本次文档闭环**

- 未修改业务代码，因此未执行新的编译和安装。
- 未触发新的 GitHub Actions 或 APK Release。
- 未接入 Widget、AdapterFactory、槽位配置或最近成功缓存，未合并 `main`。

**下一项唯一任务**

停止开发并等待用户确认下一阶段。建议 Stage 8F-P4：将已验收请求配方作为通用数据源接入 AdapterFactory 和 Widget；用户确认前不得开始。

---

## 2026-07-18｜Stage 8F-P4 已验证请求配方接入 Widget

**目标和用户确认**

- 用户已确认 beta.11 的配方保存与跨启动直接刷新通过，并明确回复“好的，接入”。
- 本阶段只把这一份已验收配方接入现有模型实例、AdapterFactory 和 Widget，不扩展新的站点协议。

**方案与数据边界**

- 用户必须在实验室明确选择一张已启用且完整配置的卡片；配方保存稳定 `instanceId`，禁止按站点或模型名称猜测。
- `AdapterFactory` 对绑定实例优先返回通用 `DashboardRecipeAdapter`；其他实例继续使用原有专用 Adapter。
- Widget Provider 与设置页预览不读取 Cookie、不解析 JSON；通用 Adapter 重放 P3 已验证请求并将全部真实字段确定性映射为 `WidgetData`。
- 任意配方字段缺失或类型变化时整次结果失败，不产生残缺成功数据；网络临时失败沿用现有实例缓存回退。
- 通用配方使用单独缓存命名空间，避免同一实例的专用 Adapter 历史缓存串入通用仪表盘字段。
- 实验室是独立进程，因此把配方元数据和 Keystore 加密 Cookie 从多进程 SharedPreferences 迁移到 `noBackupFilesDir` 原子文件；旧 beta.11 数据自动迁移，不保存明文凭据。

**起始分支和提交**

- 远端分支：`feature/stage-8b-simple-connection-flow`
- 起始提交：`005fe9ecf87ab0bd0a5c68c69c218747b1752f76`
- 本地实施分支：`agent/stable-signing`

**实际修改文件（提交前）**

- `PROJECT.md`
- `.github/workflows/android-prerelease.yml`
- `app/src/main/java/com/java/myapplication/BalanceWidgetProvider.kt`
- `app/src/main/java/com/java/myapplication/SlotDataCapability.kt`
- `app/src/main/java/com/java/myapplication/adapter/AdapterFactory.kt`
- `app/src/main/java/com/java/myapplication/adapter/NetworkAwareAdapter.kt`
- `app/src/main/java/com/java/myapplication/adapter/DashboardRecipeAdapter.kt`
- `app/src/main/java/com/java/myapplication/discovery/DashboardDiscoveryActivity.kt`
- `app/src/main/java/com/java/myapplication/discovery/DashboardRecipeClient.kt`
- `app/src/main/java/com/java/myapplication/discovery/DashboardRecipeRepository.kt`
- `app/src/main/java/com/java/myapplication/discovery/DashboardRequestRecipe.kt`
- `app/src/main/res/layout/activity_dashboard_discovery.xml`
- `app/src/test/java/com/java/myapplication/DashboardRecipeWidgetMapperTest.kt`
- `AI_HANDOFF.md`
- `DEVELOPMENT_LOG.md`

**明确未修改**

- 未修改 MiMo、DeepSeek、NewAPI、爱黄牛等专用 Adapter 的协议和解析。
- 未新增多配方、定时后台任务、开发者服务器或云端凭据同步。
- 未支持 POST、GraphQL、查询参数、跨 Origin、Bearer/OAuth 或 localStorage 重放。
- 未合并 `main`，未创建正式商店签名。

**提交前验证**

- 本地与远端起点均为 `005fe9ecf87ab0bd0a5c68c69c218747b1752f76`，起始工作区干净。
- `PROJECT.md` 已在业务代码前记录 P4 范围和验收标准。
- `git diff --check`：通过，仅有 Windows 换行提示。
- 实验布局 XML：按 UTF-8 解析通过。
- 新增纯映射测试覆盖核心指标优先、用量/辅助字段分流、货币单位显示和不伪造百分比。
- 本机没有 Java、Android SDK 或 ADB，无法执行 Android 编译和安装；GitHub Actions 是唯一测试、构建、签名和发布执行端。
- GitHub Actions `29641354417`：`testDebugUnitTest + assembleDebug`、固定证书校验、Artifact 上传和 beta.12 发布全部一次通过。
- 覆盖安装与真机验收：待用户执行，不得宣称通过。

**阶段提交 SHA**

- 业务提交与 Release 目标：`74375e472c779bed56ee2b420efaa4da4da91441`。

**云端构建与交付结果**

- GitHub Actions：`29641354417`，结论 `success`，所有步骤一次通过。
- Release：`v0.1.0-beta.12`，标记为 Prerelease。
- APK：`ai-api-dashboard-v0.1.0-beta.12-debug.apk`，大小 `12168475` 字节。
- 从公开 Release 下载后 SHA-256：`7219F8F288F265917624AD44CB139C3D2AD8BB839BDB556123D6EF3D1BAB83FB`。
- 工作流固定 Beta 证书 SHA-256 校验：`A8F816B106F23274F35E3DDC8B19C464A31F7A7BD0871E3294AA6E6922954860`，通过。
- 覆盖安装与真机 Widget 绑定、解绑、断网回退验收：待用户执行。

**下一项唯一任务**

用户覆盖安装 beta.12，在实验室把已保存配方接入一张已配置卡片，刷新 Widget 核对真实字段；再验证解除接入恢复原 Adapter，以及断网时只读取该实例的配方缓存。验收前不进入下一阶段。

---

## 2026-07-18｜Stage 8F-P4.1 实验室自动检测并选择模型

**目标和用户确认**

- 用户明确要求实验室不再手动输入模型名称，而是发送一次真实模型列表请求后让用户选择。
- 本阶段只修正实验室的模型配置入口；不改 Widget、配方协议、网页捕获范围、通用 Adapter 或现有平台 Adapter。

**实现范围**

- 把主 App 既有 `/v1/models` 请求与错误分类抽成共享 `ModelCatalogClient`，主 App 调用行为保持不变。
- 实验室删除模型名称输入框，新增“测试连接并获取模型”按钮、连接状态与模型下拉选择器。
- 单模型自动选择，多模型由用户选择；真实检测完成前禁用“打开仪表盘并登录”。
- API Base 或 API Key 改动时立即作废旧选择；使用 generation 和输入快照阻止旧异步结果覆盖新输入。
- 只接受 HTTPS API Base 和标准 `data[].id`，单次请求超时 10 秒且不自动重试；普通界面不显示响应正文或异常堆栈。
- API Key 和模型选择只留在当前 Activity 内存，不写入配方、日志或仓库。

**修改文件（提交前）**

- `PROJECT.md`
- `.github/workflows/android-prerelease.yml`
- `app/src/main/java/com/java/myapplication/MainActivity.kt`
- `app/src/main/java/com/java/myapplication/ModelCatalogClient.kt`
- `app/src/main/java/com/java/myapplication/discovery/DashboardDiscoveryActivity.kt`
- `app/src/main/res/layout/activity_dashboard_discovery.xml`
- `app/src/test/java/com/java/myapplication/ModelCatalogClientTest.kt`
- `AI_HANDOFF.md`
- `DEVELOPMENT_LOG.md`

**提交前验证**

- `PROJECT.md` 已在业务代码前记录 P4.1 正式范围和验收标准。
- `git diff --check`：通过，仅有现有 Windows 换行提示。
- 实验布局 XML：按 UTF-8 解析通过。
- 新增纯 URL 规则测试覆盖普通 Base、已有 `/v1`、HTTP、userinfo、查询参数和非法地址。
- 本机没有 Java、Android SDK 或 ADB，无法执行 Android 单元测试、编译和安装；GitHub Actions 执行唯一一轮 `testDebugUnitTest + assembleDebug`、固定证书校验和 beta.13 发布。
- GitHub Actions `29642475652`：全部步骤一次通过，没有修复后重跑。

**阶段提交 SHA**

- 业务提交与 Release 目标：`39646786e021ce43654b47851467c7243b06caf2`。

**云端构建与交付结果**

- GitHub Actions：`29642475652`，结论 `success`。
- `testDebugUnitTest + assembleDebug`、固定 Beta 证书校验、Artifact 上传和 beta.13 Prerelease 发布：全部成功。
- Release：`v0.1.0-beta.13`。
- APK：`ai-api-dashboard-v0.1.0-beta.13-debug.apk`，大小 `12168659` 字节。
- 从公开 Release 下载后 SHA-256：`7100645467D32811FE0BBB9C3129DE98D2C92061EDA15BEFDBEBF8F211BA5166`。
- 工作流固定 Beta 证书 SHA-256：`A8F816B106F23274F35E3DDC8B19C464A31F7A7BD0871E3294AA6E6922954860`，通过。
- 覆盖安装与真机验收：待用户执行，不得宣称通过。

**下一项唯一任务**

用户覆盖安装 beta.13，真机验证真实模型列表、单/多模型选择和输入变化失效行为；用户确认前停止开发。

---

## 2026-07-19｜Stage 8F-P4.1 真机验收闭环

**用户真机结果**

- 用户覆盖安装 beta.13 后明确回复“可以用了”，确认实验室模型自动检测与选择可以完成。
- 随后进入配方二次直连时立即收到“网页登录已失效”，该问题属于下一阶段认证重放范围，不否定 P4.1 模型检测本身。

**验收结论**

- `/v1/models` 真实检测：`用户真机确认`，通过。
- 不再手输模型名称并从真实列表选择：`用户真机确认`，通过。
- Stage 8F-P4.1：代码、云端构建、固定签名 beta.13 和用户真机验收闭环。

---

## 2026-07-19｜Stage 8F-P5-A 同源 GET 认证头自动捕获与加密重放

**目标和问题背景**

- beta.13 的网页捕获和 AI 字段核对成功，但“直接请求并二次核对”立即返回 401/403。
- 源码确认旧链路只重放 Cookie，并把所有 401/403 统一解释为“网页登录已失效”；目标站点的成功 GET 请求可能还依赖 Authorization、API Token、CSRF 或用户/租户路由头。
- 用户已明确确认开始修复，并同意由 App 自动处理不同站点的常见认证组合，不要求用户手工复制凭据。

**方案与取舍**

- 本阶段只完成造成当前 401/403 的一个能力：同源、无查询参数 GET 请求的必要认证头捕获、加密保存和重放。
- 认证头通过 Android WebView 原生请求回调读取，不进入 JavaScript 导出、脱敏响应、AI Prompt、结果 JSON或普通日志。
- 使用严格允许列表和长度/换行校验，只保留认证、CSRF 和必要路由头；Cookie、Origin、Referer、User-Agent、未知头和浏览器安全头均拒绝。
- Cookie 与允许认证头整体经 Android Keystore 加密；旧 Cookie-only 文件和旧 SharedPreferences 配方继续兼容读取并可在绑定/解绑时迁移。
- 二次请求、实验室直接刷新与 Widget 通用 Adapter 使用同一加密凭据；任一 endpoint 同时缺少 Cookie 和认证头时拒绝保存。
- POST、GraphQL、请求体、查询参数、跨 Origin 和动态签名继续冻结，待 P5-A 真机通过后再单独推进。

**起始分支和提交**

- 远端分支：`feature/stage-8b-simple-connection-flow`
- 起始提交：`417bde3ba69333bdccbcdab45063fce99958b5b0`
- 本地实施分支：`agent/stable-signing`

**实际修改文件**

- `PROJECT.md`
- `.github/workflows/android-prerelease.yml`
- `app/src/main/java/com/java/myapplication/adapter/DashboardRecipeAdapter.kt`
- `app/src/main/java/com/java/myapplication/discovery/DashboardCaptureSanitizer.kt`
- `app/src/main/java/com/java/myapplication/discovery/DashboardDiscoveryActivity.kt`
- `app/src/main/java/com/java/myapplication/discovery/DashboardRecipeClient.kt`
- `app/src/main/java/com/java/myapplication/discovery/DashboardRecipeRepository.kt`
- `app/src/main/java/com/java/myapplication/discovery/DashboardReplayHeaderPolicy.kt`
- `app/src/main/java/com/java/myapplication/discovery/DashboardRequestRecipe.kt`
- `app/src/main/res/layout/activity_dashboard_discovery.xml`
- `app/src/test/java/com/java/myapplication/DashboardRecipeRulesTest.kt`
- `app/src/test/java/com/java/myapplication/DashboardReplayHeaderPolicyTest.kt`
- `AI_HANDOFF.md`
- `DEVELOPMENT_LOG.md`

**明确未修改**

- 未修改 MiMo、DeepSeek、NewAPI、爱黄牛等专用 Adapter。
- 未修改 Widget 布局、八连点、模型配置、缓存写入规则或现有平台授权仓库。
- 未增加 POST、GraphQL、请求体、查询参数、跨 Origin、localStorage、Service Worker 或动态签名支持。
- 未新增依赖、数据库、WorkManager、开发者服务器或云端凭据同步；未合并 `main`。

**提交前验证状态**

- `PROJECT.md` 已先记录 P5-A 正式范围、安全边界和验收标准。
- 本地与远端起点一致，开始时工作区干净。
- `git diff --check`：通过，仅有现有 Windows 换行提示。
- 实验布局 XML：按 UTF-8 解析通过。
- 新增纯规则测试覆盖允许认证头、Cookie/Origin/未知头拒绝、换行注入拒绝、同源无查询 GET 请求键和跨 Origin/POST/查询参数拒绝。
- 现有配方规则测试补充认证头随候选进入内存草稿且再次经过允许列表清洗。
- 本机没有 Java、Android SDK 或 ADB；单元测试、Android 编译、固定签名和发布由 GitHub Actions 执行。

**编译、安装和验收状态**

- 业务提交与 Release 目标：`16014a3a91cf5319d400d5ae1334cab1a285e56b`。
- GitHub Actions：`29652425265`，结论 `success`；本阶段唯一一轮 `testDebugUnitTest + assembleDebug` 一次通过，没有修复重跑。
- 固定 Beta 证书校验：成功，SHA-256 为 `A8F816B106F23274F35E3DDC8B19C464A31F7A7BD0871E3294AA6E6922954860`。
- Release：`v0.1.0-beta.14`，目标为上述业务提交。
- APK：`ai-api-dashboard-v0.1.0-beta.14-debug.apk`，大小 `12185043` 字节。
- 从公开 Release 下载后 SHA-256：`A7B6D406AC9C2B49539131CE66AE9E1CBA5C2B4FD2C749FE1F1EED4E39CE7B49`。
- 覆盖安装：待用户执行。
- 真机认证头捕获与 Widget 复测：待用户确认。

**回滚位置**

`417bde3ba69333bdccbcdab45063fce99958b5b0`

**下一项唯一任务**

用户覆盖安装 beta.14；由于 beta.13 旧配方没有认证头，必须重新捕获并保存同一站点配方，再复测二次直连与 Widget。无需卸载或清除 App 数据。真机通过前不进入 POST/GraphQL。

---

## 2026-07-19｜Stage 8F-P5-A.1 直接测试按钮无反馈修复

**目标和问题背景**

- 用户在 beta.14 完成 PuppyRouter AI 识别后，点击“直接测试并加密保存”没有可见反应。
- 用户提供的结果包含 `/api/user/self` 与 `/api/data/self` 两个同源、无查询参数 GET 接口，以及 9 个已通过本机核对的字段；因此本次只修复保存按钮动作反馈，不扩展 POST、GraphQL 或新站点协议。

**根因与方案**

- 源码中 `recipeInProgress`、`currentAnalysis` 或 `currentCapture` 不满足时会直接返回，既不修改状态文字也不提示用户。
- 即使进入正常直连流程，按钮也保持原文字，只在按钮上方更改一行状态；用户无法从点击目标本身确认操作已经开始。
- 修复为所有路径均有可见反馈：准备阶段和网络阶段分别更新按钮文字与状态，失效状态、凭据缺失、准备异常和网络异常均恢复按钮并显示中文原因。
- 不持久化识别结果到 Activity 状态或磁盘；如果页面状态已失效，明确要求重新识别，避免把捕获响应或认证信息写进 Bundle。

**起始分支和提交**

- 远端分支：`feature/stage-8b-simple-connection-flow`
- 起始提交：`e0078e6ade11449373aef83652e03ee0c87c6fa0`
- 本地实施分支：`agent/stable-signing`

**实际修改文件**

- `app/src/main/java/com/java/myapplication/discovery/DashboardDiscoveryActivity.kt`
- `app/src/main/res/layout/activity_dashboard_discovery.xml`
- `app/src/test/java/com/java/myapplication/DashboardRecipeRulesTest.kt`
- `.github/workflows/android-prerelease.yml`
- `AI_HANDOFF.md`
- `DEVELOPMENT_LOG.md`

**明确未修改**

- 未修改认证头允许列表、Cookie/Keystore 格式、AI Prompt、配方协议、Widget Adapter、缓存或现有平台 Adapter。
- 未增加 POST、GraphQL、查询参数、跨 Origin、动态签名、依赖、数据库或云端服务。
- 未记录用户附件中的余额、用量样本或任何认证值。

**验证状态**

- 新增 PuppyRouter 两个真实 endpoint 形状的纯规则回归用例，验证可生成双接口配方并携带允许认证头。
- `git diff --check`：通过，仅有现有 Windows 换行提示。
- 实验布局 XML：按 UTF-8 解析通过；差异敏感信息模式检查未发现凭据。
- 本机没有 Java、Android SDK 或 ADB；单元测试、Android 编译、固定签名和 beta.15 发布待 GitHub Actions 执行一次。
- 覆盖安装和真机点击反馈：待用户确认，不得宣称通过。

**提交与回滚**

- 业务提交与 Release 目标：待提交后回填。
- 回滚位置：`e0078e6ade11449373aef83652e03ee0c87c6fa0`

**下一项唯一任务**

完成静态检查、提交和一次 GitHub Actions，发布 beta.15 后等待用户复测同一个 PuppyRouter 流程。真机通过前不进入 POST/GraphQL。
