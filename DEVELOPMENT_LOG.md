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

**提交 SHA**

待本阶段执行端提交后填写。

**下一项唯一任务**

Stage 7A-3：先设计并实现可配置的通用网页登录授权入口，第一步只迁移并保持 MiMo Cookie 路径不回归。
