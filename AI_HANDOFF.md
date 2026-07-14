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

`Stage 8A-3：后台授权和 Widget 持久化缓存 Key 迁移到 instanceId` 已完成，并通过编译、覆盖安装和用户真机验收。

- 当前开发分支：`feature/stage-8a-model-instances`
- Stage 8A-2C 基线：`9bd9aa4e67e3aaedb2eb35e588fbc46160b5de1c`
- Stage 8A-3 当前业务提交：`a144230bd31948018113206d9395cbabb28e1646`
- 当前文档收口提交：以本文件所在提交为准
- 下一项唯一任务：`Stage 8A-4——BalanceWidgetProvider 读取窗口绑定的 instanceId，现有四卡外观保持不变`

## Stage 8A 当前进度

- Stage 8A-1：方案摸排与文档确认——完成
- Stage 8A-2：ModelInstance、ServiceType、实例仓库和旧配置迁移——完成
- Stage 8A-2B：实例仓库有效性、稳定 ID 和两步写入加固——完成
- Stage 8A-2C：schema 重试、严格字段和 serviceType 校验、OpenAI 回退修正——完成
- Stage 8A-3：授权与持久化缓存 Key 迁移到 instanceId——完成
- Stage 8A-4：Widget 窗口绑定 instanceId——尚未开始

## Stage 8A-3 实现结果

### 稳定实例 Key

新增 `config/InstanceKeyResolver.kt`，把历史槽位别名归一化为稳定实例 ID：

- `Kimi` → `legacy-kimi`
- `MiMo` → `legacy-mimo`
- `DeepSeek` → `legacy-deepseek`
- `OpenAI` → `legacy-openai`
- Adapter 内部名称 `Aihuangniu` → `legacy-openai`

旧别名只用于兼容读取，不删除。

### 后台授权

`BackgroundAuthRepository` 当前规则：

1. 优先读取 `${instanceId}_auth`；
2. 新键不存在时，兼容读取原平台键；
3. 读取到旧 Cookie / Bearer Token 后复制到新键；
4. 旧键保留，便于回滚；
5. 用户明确点击“清除授权”时，同时删除新键和兼容旧键，防止旧凭据被重新恢复；
6. 不改变授权 JSON 字段和凭据格式；
7. 不打印 Cookie、Bearer Token 或 API Key。

### Widget 最近成功数据

`WidgetData` 当前规则：

1. 成功数据统一保存到稳定 `instanceId` 键；
2. 读取时优先读取稳定键；
3. 新键不存在时兼容读取历史平台键并复制到新键；
4. 旧缓存键保留；
5. 临时网络故障继续显示最近成功数据和既有兜底提示。

### 本阶段明确未修改

- 未修改 `BalanceWidgetProvider` 的固定四槽读取与布局绑定；
- 未修改 `AdapterFactory` 路由；
- 未修改任何 Adapter 协议和接口解析；
- 未修改配置页外观；
- 未修改 Widget XML 或响应式布局；
- 未合并到 `main`。

## Stage 8A-3 验证证据

本地执行端仅负责拉取、编译、覆盖安装和启动，没有修改代码、文档、提交或推送。

- 拉取后 HEAD：`a144230bd31948018113206d9395cbabb28e1646`
- `git pull --ff-only`：Fast-forward 成功
- `./gradlew assembleDebug`：`BUILD SUCCESSFUL in 39s`
- `pm install -r`：`Success`
- App 启动：成功
- 本地工作区：干净
- 用户真机验收：通过

用户确认的真机结果：

- MiMo 无需重新登录，余额正常；
- 爱黄牛无需重新登录，余额和用量正常；
- Kimi、DeepSeek 正常；
- Kimi Billing 中性展示正常；
- Widget 四张卡无空白、崩溃或长时间异常加载；
- 临时网络故障下最近成功数据兜底正常。

## 当前核心数据流

```text
历史平台槽位 / instanceId
        ↓
InstanceKeyResolver
        ↓
稳定 instanceId
        ├─ BackgroundAuthRepository 授权归属
        └─ WidgetData 最近成功缓存归属
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
- 每次只推进一个可独立验收的目标。
