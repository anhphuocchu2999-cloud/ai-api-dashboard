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

`Stage 8B：四张配置卡统一连接方式面板` 已完成第二轮云端纠偏，等待一次本地编译、覆盖安装和用户真机验收。

- 当前开发分支：`feature/stage-8a-model-instances`
- Stage 8A-4 已验收基线：`f5a9507201f1bf836f2e03fbae2dd297eb2a3409`
- Stage 8B 第一版：`514967ca0fb3919c88653e0dd3f12bc39fd8ea40`
- Stage 8B 纠偏业务提交：`2f61bd9833f0574b4961585be45d77ec9c0707f2`、`c9f4c722a8435b23fedd81bb775aeb9f8bd6b160`、`0fc97c885978c1a84cd05af986efbdcfab4847ac`、`4d93c1313cdf94d08c94f304edd04437acd9c5ad`
- 当前状态：代码已推送，尚未声称编译或真机通过
- 下一步：只执行拉取、一次 `assembleDebug`、一次覆盖安装和真机验收

## 主线目标

四个 Widget / 配置窗口必须是独立模型实例，不再天生等于 Kimi、MiMo、DeepSeek、OpenAI。每个窗口可以选择当前已真实接入的服务，随后按该服务能力开放：

1. API
2. 网页授权
3. Billing
4. 余额、近期消费/用量、请求次数、Token 等数据能力

未经真实接口验证的能力必须明确标注“当前未接入”，不得显示 0 或伪造数据。

## Stage 8B 第二轮纠偏

第一版虽然展示了 API、网页授权和 Billing，但窗口仍被历史平台身份限制，且“不支持”区域使用“锁定”文案，容易让用户误认为整张卡不可配置。第二轮完成以下纠偏：

### 服务类型选择

四张配置卡都新增“服务类型”选择，目前可选：

- Kimi / NewAPI
- MiMo
- DeepSeek 官方
- 爱黄牛

切换服务后：

- 自动填写该服务默认 API Base；
- 清空旧模型名，要求重新测试连接；
- 保留用户 API Key，避免擅自删除敏感配置；
- `ConfigRepository` 按 API Base 推断 `serviceType`，历史窗口名称只作为无法识别时的兼容回退；
- Adapter、网页授权和 Billing 能力随所选服务重新计算。

### 网页授权绑定

- MiMo 和爱黄牛的已验证网页登录 Profile 改为按 API Base 匹配，因此可以在任意窗口中使用；
- `WebAuthActivity` 新增目标卡片 instanceKey，凭据保存到当前选择该服务的窗口；
- 旧调用未传目标卡片时仍回退到 Profile 历史 instanceKey；
- 不新增 DeepSeek 或 Kimi 的虚假网页登录方案。

### 数据能力展示

每张卡新增“可获取数据”区域，明确显示：

- 账户余额
- 近期消费 / 用量
- 请求次数
- Token

DeepSeek 当前已验证的官方公开 API 只有 `/user/balance`。因此：

- 余额：已接入；
- 近期消费 / 用量：待接入；
- 不用 `0.00` 冒充真实消费；
- 后续需要单独验证真实网页或官方数据接口后才能接入。

### 文案纠偏

不再显示“该入口已锁定”。统一改为：

> 当前尚未接入，并不是卡片被锁死。切换服务类型后，会按所选服务的真实能力自动开放。

## 已保留能力

- 四张卡 API Base、API Key、模型列表和多模型选择；
- MiMo Cookie 网页授权；
- 爱黄牛 Bearer Token 网页授权；
- Kimi / NewAPI Billing；
- 原 Widget 四卡布局、缓存和断网快速兜底；
- 旧配置、授权和最近成功数据均不删除；
- `main` 未合并。

## 验收重点

安装后必须确认：

1. 四张卡都出现“服务类型”选择；
2. 任意卡可切换 Kimi / MiMo / DeepSeek / 爱黄牛；
3. 切换后 API Base 更新，模型名清空，提示重新测试；
4. MiMo 或爱黄牛选到任意窗口后，网页授权入口能够出现；
5. Kimi / NewAPI 选到任意窗口后，Billing 显示已接入；
6. DeepSeek 显示余额已接入、近期消费/用量待接入；
7. 页面不再出现“卡片锁定”误导文案；
8. 原四个平台数据、登录状态、Widget、缓存和断网兜底不回归；
9. App 无崩溃、空白、串卡或凭据丢失。

## 应用信息

- Android 包名：`com.java.myapplication.dev`
- Debug 构建：`./gradlew assembleDebug`
- APK：`app/build/outputs/apk/debug/app-debug.apk`
- 覆盖安装：`pm install -r`

严禁卸载、`pm clear` 或清除应用数据。

## 严禁操作

- 不得修改 `main`；
- 不得未经用户确认合并分支；
- 不得卸载 App、`pm clear` 或清数据；
- 不得打印或暴露 API Key、Cookie、Bearer Token；
- 不得绕过 `AdapterFactory`；
- 不得让 Provider 解析平台协议或授权内容；
- 每次只推进一个可独立验收的目标；
- OperitAI 仅执行拉取、编译、覆盖安装和启动，不参与代码修改、文档修改、提交或推送。
