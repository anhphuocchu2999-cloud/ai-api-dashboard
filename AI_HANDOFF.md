# AI API Dashboard｜AI 交接状态

> 最近更新：2026-07-14
> 本文件只保存当前状态快照；历史过程以 `DEVELOPMENT_LOG.md` 和 GitHub 提交为准。

## 强制阅读顺序

1. `AGENTS.md`
2. `PROJECT.md`
3. `AI_HANDOFF.md`
4. `DEVELOPMENT_LOG.md` 最近阶段
5. 当前任务直接相关源码

不得只依据聊天摘要直接改代码。

## 当前阶段

`Stage 8A-4：Widget 固定窗口绑定稳定 instanceId` 已完成云端代码，等待一次本地编译、覆盖安装和用户真机验收。

- 当前开发分支：`feature/stage-8a-model-instances`
- Stage 8A-3 已验收业务提交：`a144230bd31948018113206d9395cbabb28e1646`
- 断网快速兜底已验收业务提交：`404b35fcd7ae1d3a5fee128c8bc571acae6d9e05`
- Stage 8A-4 配置绑定提交：`cf00bb1e6896d35e48cee3fc8f5d153b114c1d1b`
- Stage 8A-4 Adapter 路由提交：`4899211f61eb41c652508fbcff5503848e0ebefd`
- 当前状态：代码已推送，尚未声称编译或真机通过
- 下一步：只执行拉取、一次 `assembleDebug`、一次覆盖安装和真机回归

## Stage 8A 当前进度

- Stage 8A-1：方案摸排与文档确认——完成
- Stage 8A-2：ModelInstance、ServiceType、实例仓库和旧配置迁移——完成
- Stage 8A-2B：实例仓库有效性、稳定 ID 和两步写入加固——完成
- Stage 8A-2C：schema 重试、严格字段和 serviceType 校验、OpenAI 回退修正——完成
- Stage 8A-3：授权与持久化缓存 Key 迁移到 instanceId——完成并验收
- Stage 8A-3F：断网刷新快速回退最近成功数据——完成并验收
- Stage 8A-4：固定窗口绑定 instanceId、按 serviceType 路由——云端代码完成，待验收

## Stage 8A-4 实现

### 固定窗口与实例绑定

现有 Widget 仍保留四个视觉窗口和原布局顺序：

- Kimi 窗口 → `legacy-kimi`
- MiMo 窗口 → `legacy-mimo`
- DeepSeek 窗口 → `legacy-deepseek`
- OpenAI 窗口 → `legacy-openai`

`BalanceWidgetProvider` 仍通过唯一配置入口 `ConfigRepository.loadAllConfigs()` 读取数据。该入口现在在 `model_instances_v1` 有效时，以 `ModelInstanceRepository` 为真实数据源，再映射成当前固定布局能够消费的兼容配置。固定平台名只保留为视觉槽位别名，不再承担真实配置身份。

### 旧配置页兼容

当前配置页仍写入旧 `Kimi / MiMo / DeepSeek / OpenAI` SharedPreferences 键。为避免本阶段同时改动大体量 UI：

1. `ConfigRepository` 每次读取实例时吸收固定四槽的最新兼容写入；
2. 只更新实例的可变字段：displayName、serviceType、apiBase、apiKey、modelName、enabled；
3. `instanceId` 永不改变；
4. 同步后继续保留旧键，不删除、不清数据；
5. 额外实例不会被丢弃，仍保留在实例仓库中。

### Adapter 路由

`AdapterFactory` 新增正式入口：

```text
getAdapterByServiceType(serviceType)
```

映射：

- `NEW_API` → `NewApiAdapter`
- `MIMO` → `MiMoAdapter`
- `DEEPSEEK_OFFICIAL` → `DeepSeekOfficialAdapter`
- `AIHUANGNIU` → `AihuangniuAdapter`
- `OPENAI_COMPATIBLE / UNKNOWN` → 当前无对应数据 Adapter，返回 null

旧 `getAdapter(platformName, apiBase)` 仍保留。它先把历史槽位别名解析成稳定 `instanceId`，在 apiBase 与实例一致时使用持久化 `serviceType`；配置页正在编辑尚未同步的新地址时，才回退到旧域名推断，避免现有功能回归。

所有已识别 Adapter 继续统一经过 `NetworkAwareAdapter`，断网快速兜底保持有效。

## 已验收的 Stage 8A-3 与断网修复

- MiMo 无需重新登录，余额正常；
- 爱黄牛无需重新登录，余额和用量正常；
- Kimi、DeepSeek 正常；
- Kimi Billing 中性展示正常；
- Widget 四张卡无空白或崩溃；
- 断网刷新不再长时间卡在“加载中”，最近成功数据能快速恢复。

## Stage 8A-4 明确未修改

- 未修改 Widget XML、卡片数量、布局顺序或视觉外观；
- 未新增、删除、排序任意实例；
- 未修改平台 HTTP 协议或 JSON 解析；
- 未修改 Cookie、Bearer Token、API Key 的格式；
- 未删除旧配置、旧授权或旧缓存；
- 未合并到 `main`。

## 验收重点

安装 Stage 8A-4 后必须确认：

1. 四张卡仍处于原位置，标题和数据不变；
2. Kimi、MiMo、DeepSeek、爱黄牛均能正常刷新；
3. MiMo、爱黄牛不要求重新登录；
4. 修改任一固定槽位的 API Base、模型或启用状态后，Widget 能读取更新后的实例配置；
5. Billing 中性展示和断网快速兜底不回归；
6. 无空白、崩溃或重复实例。

## 应用信息

- Android 包名：`com.java.myapplication.dev`
- Debug 构建：`./gradlew assembleDebug`
- APK：`app/build/outputs/apk/debug/app-debug.apk`
- 覆盖安装：`pm install -r`

严禁卸载、`pm clear` 或清除应用数据，因为会破坏现有配置和网页登录授权。

## 严禁操作

- 不得修改 `main`；
- 不得未经用户确认合并分支；
- 不得卸载 App、`pm clear` 或清数据；
- 不得打印或暴露 API Key、Cookie、Bearer Token；
- 不得绕过 `AdapterFactory`；
- 不得让 Provider 解析平台协议或授权内容；
- 每次只推进一个可独立验收的目标；
- OperitAI 仅在用户明确授权时执行拉取、编译、覆盖安装和启动，不参与代码修改、文档修改、提交或推送。
