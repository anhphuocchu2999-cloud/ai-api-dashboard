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

`Stage 8A-3：后台授权和 Widget 持久化缓存 Key 迁移到 instanceId` 已完成。

Stage 8A-3 验收后发现并修复了一个独立缺陷：断网刷新时，Widget 会先显示“加载中”，随后因四个平台顺序等待 HTTP 超时，最近成功数据可能要等待约一至两分钟才恢复。该缺陷现已修复，并通过用户真机验收。

- 当前开发分支：`feature/stage-8a-model-instances`
- Stage 8A-2C 基线：`9bd9aa4e67e3aaedb2eb35e588fbc46160b5de1c`
- Stage 8A-3 业务提交：`a144230bd31948018113206d9395cbabb28e1646`
- Stage 8A-3 文档收口：`07b1ea4dd8ee8f9724d97c0d4d812daf69d9f9ab`
- 断网快速兜底修复业务提交：`404b35fcd7ae1d3a5fee128c8bc571acae6d9e05`
- 当前文档收口提交：以本文件所在提交为准
- 下一项唯一任务：`Stage 8A-4——BalanceWidgetProvider 读取窗口绑定的 instanceId，现有四卡外观保持不变`

## Stage 8A 当前进度

- Stage 8A-1：方案摸排与文档确认——完成
- Stage 8A-2：ModelInstance、ServiceType、实例仓库和旧配置迁移——完成
- Stage 8A-2B：实例仓库有效性、稳定 ID 和两步写入加固——完成
- Stage 8A-2C：schema 重试、严格字段和 serviceType 校验、OpenAI 回退修正——完成
- Stage 8A-3：授权与持久化缓存 Key 迁移到 instanceId——完成
- Stage 8A-3F：断网刷新快速回退最近成功数据——完成
- Stage 8A-4：Widget 窗口绑定 instanceId——尚未开始

## Stage 8A-3 核心结果

### 稳定实例 Key

`config/InstanceKeyResolver.kt` 将历史槽位别名归一化为稳定实例 ID：

- `Kimi` → `legacy-kimi`
- `MiMo` → `legacy-mimo`
- `DeepSeek` → `legacy-deepseek`
- `OpenAI` → `legacy-openai`
- Adapter 内部名称 `Aihuangniu` → `legacy-openai`

旧别名只用于兼容读取，不删除。

### 后台授权

`BackgroundAuthRepository` 当前规则：

1. 优先读取 `${instanceId}_auth`；
2. 新键不存在时兼容读取原平台键；
3. 读取到旧 Cookie / Bearer Token 后复制到新键；
4. 旧键保留，便于回滚；
5. 用户明确点击“清除授权”时，同时删除新键和兼容旧键；
6. 不改变授权 JSON 字段和凭据格式；
7. 不打印 Cookie、Bearer Token 或 API Key。

### Widget 最近成功数据

`WidgetData` 当前规则：

1. 成功数据统一保存到稳定 `instanceId` 键；
2. 读取时优先读取稳定键；
3. 新键不存在时兼容读取历史平台键并复制到新键；
4. 旧缓存键保留；
5. 临时网络故障继续显示最近成功数据和既有兜底提示。

## 断网刷新快速兜底修复

### 原缺陷

- Provider 发起刷新时先显示“加载中”；
- 四个平台按顺序执行网络请求；
- 断网时多个请求依次等待连接或读取超时；
- 虽然最近成功数据仍然存在，但需要等整轮请求结束后才重新渲染。

### 修复实现

新增 `adapter/NetworkAwareAdapter.kt`，并由 `AdapterFactory` 统一包装所有已识别 Adapter：

1. 进入具体平台请求前，通过系统网络状态快速判断当前是否存在已验证互联网连接；
2. 明确断网时立即返回临时“网络错误”；
3. 该错误继续经过现有 `WidgetData.error()`，立即读取最近成功数据；
4. 网络状态服务不可用或权限异常时采用 fail-open，不阻断真实请求；
5. Manifest 新增 `ACCESS_NETWORK_STATE`；
6. 未修改 Widget XML、授权格式、缓存 JSON 格式或平台接口解析。

业务提交链：

- `6241819326879dc5ac3d15965b8357d0c6796a90`：新增快速断网包装器
- `249c7c801b4e5f669d69b208e4271194fbb853db`：AdapterFactory 统一接入
- `404b35fcd7ae1d3a5fee128c8bc571acae6d9e05`：增加网络状态权限

## 验证证据

### Stage 8A-3

- 拉取后 HEAD：`a144230bd31948018113206d9395cbabb28e1646`
- `./gradlew assembleDebug`：`BUILD SUCCESSFUL in 39s`
- 覆盖安装：`Success`
- App 启动：成功
- 本地工作区：干净
- 用户真机验收：通过

用户确认：

- MiMo 无需重新登录，余额正常；
- 爱黄牛无需重新登录，余额和用量正常；
- Kimi、DeepSeek 正常；
- Kimi Billing 中性展示正常；
- Widget 四张卡无空白或崩溃。

### 断网快速兜底修复

- 云端代码 HEAD：`404b35fcd7ae1d3a5fee128c8bc571acae6d9e05`
- 用户在断网状态下重新刷新 Widget；
- 最近成功数据能够正常恢复，不再长期卡在“加载中”；
- 用户明确回复“测试通过”。

## 当前核心数据流

```text
历史平台槽位 / instanceId
        ↓
InstanceKeyResolver
        ↓
稳定 instanceId
        ├─ BackgroundAuthRepository 授权归属
        └─ WidgetData 最近成功缓存归属

AdapterFactory
        ↓
NetworkAwareAdapter
        ├─ 明确断网：立即触发最近成功数据兜底
        └─ 网络可用/状态未知：继续调用真实 PlatformAdapter
```

Provider 仍通过 `AdapterFactory` 获取 Adapter，不得解析平台协议、Cookie 或 Bearer Token。

## 应用信息

- Android 包名：`com.java.myapplication.dev`
- Debug 构建：`./gradlew assembleDebug`
- APK：`app/build/outputs/apk/debug/app-debug.apk`
- 覆盖安装：`pm install -r`

严禁卸载、`pm clear` 或清除应用数据，因为会破坏现有配置和网页登录授权。

## 下一阶段边界

Stage 8A-4 只处理：

- Provider 从 ModelInstanceRepository 读取现有四个实例；
- 四个现有窗口绑定稳定 instanceId；
- Adapter 路由使用 serviceType；
- 卡片布局、数量和外观保持不变；
- 保留旧配置、旧授权和旧缓存的兼容读取。

Stage 8A-4 不处理：

- 任意新增或删除实例；
- 动态卡片数量；
- 拖动排序；
- Widget 外观重做；
- 多尺寸布局改版；
- 合并到 main。

## 严禁操作

- 不得修改 `main`；
- 不得未经用户确认合并分支；
- 不得卸载 App、`pm clear` 或清数据；
- 不得打印或暴露 API Key、Cookie、Bearer Token；
- 不得绕过 `AdapterFactory`；
- 不得让 Provider 解析平台协议或授权内容；
- 每次只推进一个可独立验收的目标；
- OperitAI 仅在用户明确授权时执行拉取、编译、覆盖安装和启动，不参与代码修改、文档修改、提交或推送。
