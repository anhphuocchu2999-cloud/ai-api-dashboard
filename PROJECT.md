# AI API Dashboard

## 一、产品定位

开发一款高品质的 Android AI 服务监控与账户状态仪表盘。

产品名称暂定：

AI API Dashboard

产品形态：

- Android 原生 App
- Android 桌面 Widget
- Local-first（本地优先）
- 用户设备直接连接目标 AI 服务
- 默认不经过开发者自建服务器

产品最终定位不是单纯的：

"AI 余额查询工具"

而是：

"AI 模型、API 服务、账户额度、用量和运行状态的统一 Dashboard"。

---

## 二、最终目标

长期目标：

- 支持多个 AI 官方平台
- 支持多个第三方 AI API 平台
- 支持多个 API Base
- 支持多个模型实例
- 支持 OpenAI-Compatible 服务
- 支持多种认证方式
- 支持余额、额度、Token、调用次数、用量等不同数据
- 支持 Widget 响应式布局
- 支持用户按需选择模型和显示内容
- 支持后续快速扩展新的 Provider
- 保持成熟、统一、美观的产品体验

---

## 三、V1 功能范围

V1 当前优先支持：

- Kimi
- MiMo
- DeepSeek
- OpenAI

基础能力：

- API Base 配置
- API Key 配置
- Test Connection
- 自动请求 `/v1/models`
- 单模型自动选择
- 多模型用户选择
- 模型配置保存
- 手动刷新
- Widget 数据展示

V1 不要求所有平台拥有完全相同的数据能力。

---

## 四、核心对象：模型实例

Widget 的每一张卡片代表一个：

Model Instance（模型实例）

而不是：

- 平台
- 公司
- 中转站
- API 网站

一个模型实例可以包含：

- API Base
- API Key
- Model
- 认证方式
- 数据来源
- 用户自定义名称
- Widget 显示配置

例如：

API Base：

https://code.coolyeah.net

Model：

kimi-k2.6

Widget 主标题应显示：

kimi-k2.6

而不是：

蜜音AI
Kimi 中转站

中转站或服务来源可以作为：

- 配置备注
- 数据来源
- 详情信息

但默认不得替代真实模型名称。

---

## 五、模型名称显示规则

Widget 显示优先级：

第一优先：
真实模型名称（Model）

例如：

- kimi-k2.6
- mimo-v2.5-pro
- deepseek-v4-pro
- gpt-5.4

第二优先：
用户自定义名称

第三优先：
已配置

第四优先：
未配置

Widget 显示标题不得作为 Adapter 选择依据。

---

## 六、真实性原则

不同 AI 服务允许提供完全不同的数据。

例如：

- Kimi：剩余调用次数
- MiMo：账户余额
- DeepSeek：余额、Token、Usage
- OpenAI：余额、消费、使用量
- Claude：额度、重置时间
- Gemini：套餐或使用额度

不得为了统一 UI：

- 伪造数据
- 将无法获取的数据显示为 0
- 将估算数据冒充真实数据
- 使用错误字段代替真实字段
- 强制所有模型显示相同内容

统一的是：

- 数据结构
- 视觉语言
- 用户体验

不是：

"所有模型必须显示同一种指标"。

---

## 七、数据能力模型

Provider 可以根据实际能力提供不同数据。

统一能力概念包括：

- Model
- Connection Status
- Balance
- Usage
- Quota
- Tokens
- Requests
- Subscription
- Reset Time
- History

并非所有 Provider 都必须实现全部能力。

例如：

DeepSeek：

- Model
- Balance
- Usage

MiMo：

- Model
- Balance（Web Account）

Kimi：

- Model
- Remaining Requests

Claude：

- Usage
- Quota
- Reset Time

Widget 根据真实 Capability 决定显示内容。

---

## 八、数据来源

数据可以来自：

- 官方 API
- API Key 账户接口
- Web 账户接口
- Cookie / Session
- OAuth
- 第三方平台接口
- App 本地统计
- App 本地计算

必须能够区分：

- 服务端真实数据
- 本地统计数据
- 本地计算数据
- 估算数据

不得混淆数据来源。

---

## 九、Local-first 原则

本项目默认采用：

Local-first Architecture

数据流：

用户手机
   │
   ├── AI 官方 API
   ├── 第三方 API 平台
   ├── Web 账户接口
   └── OAuth / Web 登录

默认不存在：

用户手机
   ↓
开发者服务器
   ↓
AI 平台

项目原则：

- 不建立用于收集用户 API Key 的服务器
- 不建立用于收集用户 Cookie 的服务器
- 不上传用户登录 Session
- 不上传用户 API Key
- 用户请求直接从用户设备发送到目标服务
- API Key、Cookie、Session 默认保存在本机

注意：

App 为访问目标 AI 服务本身需要 Android 网络权限。

Local-first 指：

"不经过开发者自建服务器进行数据中转"。

---

## 十、认证方式

项目不得假设所有平台只能通过 API Key 认证。

长期支持：

- API Key
- WebView Login
- Cookie / Session
- OAuth
- Device Flow
- Manual Cookie Import

不同 Provider 可以支持一种或多种认证方式。

---

### API Key

主要用于：

- 模型调用
- `/v1/models`
- 官方余额接口
- 官方 Usage 接口

API Key 是当前基础认证方式。

增加 Web 登录能力后，也不得删除 API Key 能力。

---

### WebView Login

适用于：

- API Key 无法获取余额
- 账户数据只存在于网页控制台
- 需要网页登录 Session

标准流程：

用户点击"连接账户"
        ↓
App 内打开官方网页登录页
        ↓
用户主动完成登录
        ↓
WebView 获得 Cookie / Session
        ↓
本机保存登录状态
        ↓
访问账户数据接口
        ↓
获取余额 / 用量 / 套餐

---

### Cookie / Session

同一个模型实例可以同时使用：

API Key
+
Web Account Session

例如 MiMo：

API Key：
用于模型识别和 API 调用。

Web Session：
用于获取 API Key 无法返回的账户余额。

两种数据源可以互相补充。

---

### OAuth

平台如果提供标准 OAuth，应优先支持官方授权方式。

不得为了统一结构强行转换成 API Key 或 Cookie。

---

### Manual Cookie Import

可以作为高级用户备用方式。

普通用户体验优先采用：

WebView 登录
→ 自动保持登录状态

不得要求普通用户必须手动查找和复制 Cookie。

---

## 十一、网页登录状态

产品不得使用：

- 永久 Cookie
- 永久授权

等不准确表述。

统一使用：

- 连接账户
- 网页授权
- 已连接
- 保持登录状态
- 登录已失效
- 重新连接

Cookie / Session 的有效期由目标平台决定。

登录失效时：

- 不删除历史成功数据
- 不清空已有缓存
- 显示明确状态
- 提供重新连接入口

---

## 十二、平台与协议扩展体系

项目目标不是提前写死所有平台。

长期支持范围分为三层。

---

### 第一层：通用协议

优先支持：

OpenAI-Compatible

例如：

GET /v1/models

POST /v1/chat/completions

对于兼容 OpenAI API 的服务，至少可以实现：

- API Base 配置
- API Key 配置
- 模型发现
- 模型选择
- Test Connection
- 服务状态检测

即使暂时无法查询余额，也不得判定该服务"不支持"。

---

### 第二层：平台家族

未来支持识别共享同一种后台架构的平台。

例如：

- New API
- One API
- Sub2API
- LiteLLM
- 其他通用中转系统

原则：

一个 Adapter 应尽可能适配一种协议或平台家族，而不是只适配一个域名。

例如：

站点 A
站点 B
站点 C

如果都属于 New API：

只需要：

NewApiAdapter

不得分别创建：

SiteAAdapter
SiteBAdapter
SiteCAdapter

---

### 第三层：特殊 Provider

无法通过通用协议或平台家族适配时，可以增加专用 Provider。

例如：

- MiMo
- DeepSeek
- Claude
- Gemini
- OpenAI
- Kimi
- 豆包
- MiniMax
- Cursor
- Copilot

特殊 Provider 只负责特殊能力。

不得破坏通用协议层。

---

## 十三、Adapter 选择原则

Adapter 可以依据：

- API Base
- API 协议
- 特征接口
- 返回格式
- Provider 类型
- 用户明确选择的平台类型

禁止依据：

- Widget 卡片名称
- 用户自定义名称
- 显示标题
- 单纯的平台 Logo

同一种协议允许多个模型共用同一个 Adapter。

例如同一个 OpenAI-Compatible Base：

可以同时提供：

- kimi-k2.6
- deepseek-v4-pro
- qwen-max
- glm-4.5

无需因为模型名称不同创建新的 Adapter。

---

## 十四、Widget 产品定位

Widget 是本产品的核心体验之一。

Widget 不应只是一个固定尺寸的静态面板。

长期目标：

Responsive Adaptive Widget

即：

用户调整 Widget 尺寸时：

- 布局变化
- 信息密度变化
- 模型数量变化
- 显示字段变化

不得只是简单整体放大或缩小。

---

## 十五、Widget V1 布局

V1 第一阶段支持：

### 4×2

显示：

2 个模型

特点：

- 信息更加完整
- 每个模型拥有较大的显示空间

---

### 4×3

显示：

4 个模型

特点：

- 多模型总览
- 充分利用桌面空间

---

Widget 必须充分利用可用空间。

禁止出现明显无意义的大面积空白。

---

## 十六、Widget 长期响应式方向

未来逐步支持更多尺寸，例如：

### 1×2
单模型 Focus 模式。

### 2×2
单模型详情或双模型简洁模式。

### 4×2
多模型 Dashboard。

### 4×3
Expanded Dashboard。

实际代码不得完全依赖 Launcher 的固定"格数"。

应根据：

- 实际可用宽度
- 实际可用高度
- Widget Options
- 尺寸断点

决定最终布局。

---

## 十七、Widget 自定义方向

长期支持用户选择：

- 显示哪些模型
- 模型排列顺序
- 不同尺寸显示哪些模型
- 优先显示哪些字段

例如：

1×2：
只显示 DeepSeek。

2×2：
显示 DeepSeek + MiMo。

4×3：
显示四个模型。

可选字段必须来自 Provider 的真实 Capability。

不得允许用户选择平台实际不存在的虚假数据。

---

## 十八、Widget 显示模式

长期预留：

### Smart

系统根据 Widget 尺寸自动决定内容。

### Fixed Models

用户指定显示哪些模型。

### Custom Fields

用户指定优先显示哪些字段。

这些能力逐步实现。

不得要求 V1 一次性全部完成。

---

## 十九、用户体验原则

产品风格：

- 专业
- 友好
- 有温度
- 清晰
- 克制

所有普通用户提示默认使用中文。

每条错误提示尽量回答：

① 发生了什么

② 为什么

③ 怎么解决

例如：

🌐 无法连接服务器

可能原因：
API Base 地址当前无法访问。

建议：
请检查地址后重新尝试。

默认隐藏：

- Java Exception
- StackTrace
- 原始英文异常
- 大段服务器返回内容

用户主动查看技术详情时，再显示：

- HTTP 状态码
- 返回内容
- 异常信息

---

## 二十、状态文案原则

成功：

- 已连接
- 数据已同步
- 账户状态正常

加载：

- 正在同步…
- 正在连接…
- 正在获取账户信息…

失效：

- 登录状态已失效
- 请重新连接账户

暂不支持：

- 当前服务暂未提供此项数据

禁止使用：

余额：0

代替：

无法获取余额

---

## 二十一、UI 目标

产品最终目标不是：

工程 Demo

而是：

成熟、精致、可以正式发布的消费级产品。

整体视觉方向：

- 高级
- 克制
- 现代
- 科技感
- 清晰
- 长期耐看

---

### V1

重点：

- 信息优先
- 简洁清晰
- 结构正确
- 功能稳定

---

### V2

逐步增加：

- 半透明
- 大圆角
- 高质量图标
- 更统一的卡片体系
- 更成熟的页面结构
- 自然的状态反馈

---

### V3

目标：

Apple Glass / 高级玻璃风格

但必须：

- 在 Android 能力范围内实现
- 不牺牲性能
- 不牺牲 Widget 稳定性
- 不为了视觉效果伪造系统能力

---

## 二十二、动画方向

产品允许使用动画，但必须：

克制、自然、有意义。

推荐：

- 数据更新反馈
- 数字变化
- 内容淡入
- 进度变化
- App 内卡片重排
- 页面切换
- 状态切换
- 点击反馈

禁止：

- 高频闪烁
- 大量持续动画
- 影响桌面续航
- 为炫技牺牲性能
- 将 Android Widget 无法实现的效果宣称为已支持

---

## 二十三、产品成熟度目标

项目最终必须同时满足：

第一层：
能运行

第二层：
功能正确

第三层：
容易使用

第四层：
视觉成熟

最终目标是：

数据能力
+
扩展能力
+
使用体验
+
视觉品质

共同达到成熟产品标准。

---

## 二十四、长期核心原则

项目始终坚持：

- 不为了统一而伪造数据
- 不把模型和平台混为一谈
- 不把一个中转站等同于一个 Adapter
- 同协议优先复用
- 特殊能力按扩展方式增加
- 用户凭据默认保存在本机
- Widget 根据真实数据能力展示内容
- 功能逐步实现，不要求一次完成长期架构
- 最终做成一个真正漂亮、成熟、可长期扩展的 AI Dashboard

---

## Stage 7A-1：Adapter 凭据输入统一

本阶段不新建第二套后端，不改变现有 Adapter 的职责。

现有 Adapter 继续作为平台差异统一层，负责：

- HTTP 请求
- 平台协议
- JSON 解析
- 错误转换
- WidgetData 标准化

本阶段只解决一个问题：

`PlatformAdapter.fetchData()` 不再把 API Key、Bearer Token、Cookie 混在同一个 `apiKey` 参数中。

统一输入对象：

```text
AdapterRequest
├─ apiBase
├─ modelApiKey
├─ modelName
├─ backgroundAuthType
└─ backgroundCredential
```

认证规则：

- 模型 API Key：用于 `/v1/models`、模型调用、模型用量等接口。
- Bearer Token：通过网页登录获得，用于账户后台余额、套餐、Profile 等接口。
- Cookie：通过网页登录获得，用于账户后台余额、套餐、Profile 等接口。
- Billing、Usage、Balance、Profile 属于数据接口类型，不属于新的认证方式。

平台组合示例：

- Kimi / New API：模型 API Key。
- DeepSeek 官方：模型 API Key。
- MiMo：模型 API Key + Cookie。
- 爱黄牛：模型 API Key + 后台 Bearer Token。

兼容要求：

- 保留现有 API Key、Cookie、Bearer Token 的存储格式。
- 覆盖安装后不得要求 MiMo 重新登录。
- 不改变现有余额、用量、缓存和 Widget 展示结果。
- 不新增平台，不修改 UI，不修改 Widget 布局。
- Adapter 仍统一通过 AdapterFactory 路由。

---

## Stage 7A-2：后台授权凭据仓库统一

本阶段继续沿用现有 Adapter、AdapterRequest 和 BackgroundAuthConfig，不新建第二套后端。

本阶段只解决一个问题：

后台 Cookie / Bearer Token 的 JSON 保存和读取不再分散在 Widget Provider 与各网页登录 Activity 中，统一通过 BackgroundAuthRepository 处理。

统一入口：

```text
BackgroundAuthRepository
├─ load(prefs, instanceKey)
├─ save(prefs, instanceKey, config)
└─ clear(prefs, instanceKey)
```

存储兼容规则：

- SharedPreferences 名称继续由调用方决定。
- 授权键名继续使用 `${instanceKey}_auth`。
- MiMo 继续使用现有 `api_config / MiMo_auth`。
- JSON 字段继续使用 `authType`、`authValue`、`enabled`、`updatedAt`。
- 覆盖安装后不得要求 MiMo 重新登录。

本阶段不做：

- 不新增平台。
- 不修改 AdapterRequest。
- 不修改任何 Adapter 的数据接口。
- 不修改配置页面。
- 不修改 Widget 布局、缓存和响应式逻辑。
- 不改变 Cookie / Bearer Token 的获取方式。

### Stage 7A-2 补充修复：配置页统一使用授权仓库

源码复核发现配置页仍保留独立的后台授权 JSON 读写函数，未完全经过 `BackgroundAuthRepository`。

本补充修复只完成：

- 配置页读取后台授权时调用 `BackgroundAuthRepository.load()`。
- 配置页保存后台授权时调用 `BackgroundAuthRepository.save()`。
- 配置页清除后台授权时调用 `BackgroundAuthRepository.clear()`。
- 删除配置页内部重复的 JSON 序列化和反序列化函数。

不改变现有授权键名、JSON 格式、登录方式、Adapter、数据接口、Widget 布局和缓存逻辑。

---

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
- 本阶段未改业务代码，因此不要求编译或安装。

---

## Stage 7A-3A：通用网页登录授权入口（仅迁移 MiMo Cookie 路径）

本阶段采用“完整功能块”节奏，一次完成通用网页登录入口的最小闭环，但只迁移已经有真实运行证据的 MiMo Cookie 路径。

### 目标

把当前配置页中的 `platform == "MiMo"` 特判和 `MiMoWebLoginActivity` 专用入口，迁移为由平台网页登录能力描述驱动的通用入口。

### 新的最小能力模型

```text
WebAuthProfile
├─ profileId
├─ instanceKey
├─ displayName
├─ loginUrl
├─ cookieDomain
├─ authType
└─ requiredCookieNames
```

`WebAuthProfileRegistry` 负责根据模型实例键或 profileId 查找网页登录能力。

第一版注册表只注册 MiMo：

- `profileId = mimo`
- `instanceKey = MiMo`
- `authType = COOKIE`
- 登录页保持现有 MiMo 地址
- 必要 Cookie 保持 `api-platform_serviceToken` 与 `userId`

### 通用入口规则

```text
配置页模型实例
        ↓
WebAuthProfileRegistry.findByInstanceKey(instanceKey)
        ↓
存在 profile → 显示“连接账户”
不存在 profile → 不显示网页登录按钮
        ↓
WebAuthActivity(profileId)
        ↓
按 WebAuthProfile 打开登录页并检测授权
        ↓
BackgroundAuthRepository.save(instanceKey, config)
        ↓
刷新 Widget
```

### 兼容要求

- MiMo 继续保存到 `api_config / MiMo_auth`。
- 不改变现有 Cookie JSON 格式。
- 不删除或迁移现有 MiMo 授权数据。
- 覆盖安装后不得要求 MiMo 重新登录。
- MiMo Cookie 检测条件与原逻辑一致。
- 未检测到必要 Cookie 时不得覆盖现有授权。
- 配置页不再通过 `platform == "MiMo"` 决定是否显示网页登录按钮。

### 本阶段明确不做

- 不实现爱黄牛 Bearer Token 自动提取。
- 不为 Kimi、DeepSeek 或其他只需要 API Key 的平台强行显示网页登录按钮。
- 不修改 Adapter、AdapterRequest、AdapterFactory。
- 不修改 Widget 数据接口、缓存、布局或响应式逻辑。
- 不修改后台授权存储格式。

### 验收标准

- 只有存在 `WebAuthProfile` 的 MiMo 显示“连接账户”。
- Kimi、DeepSeek、OpenAI 当前不错误显示自动网页登录入口。
- MiMo 能打开原登录页面。
- 不登录直接退出时不覆盖现有 `MiMo_auth`。
- 已登录状态可以继续保存到原 `MiMo_auth` 并刷新 Widget。
- MiMo 原余额、Kimi 原次数和 Widget 现有展示不回归。
- 业务代码只编译一次、覆盖安装一次，随后必须由用户本人真机验收。

---

## Stage 7A-3D：WebAuthProfile 匹配规则修复

### 问题

Stage 7A-3C 后，爱黄牛 Profile 使用：

- `instanceKey = OpenAI`
- `apiBaseHostContains = aihuangniu.com`

但当前 `WebAuthProfileRegistry.findFor(instanceKey, apiBase)` 先按 `instanceKey` 直接返回匹配项，因此所有 OpenAI 槽位都会命中爱黄牛 Profile，即使 API Base 不是爱黄牛。

### 正确匹配规则

`findFor(instanceKey, apiBase)` 必须同时尊重 Profile 自身的约束：

- `instanceKey` 必须匹配。
- Profile 未配置 `apiBaseHostContains` 时，只需实例键匹配。
- Profile 配置了 `apiBaseHostContains` 时，还必须要求 `apiBase` 命中该域名片段。

因此：

- MiMo：`instanceKey == MiMo` 即可匹配。
- OpenAI + `aihuangniu.com` API Base：匹配爱黄牛。
- OpenAI + 官方 OpenAI API Base：不得匹配爱黄牛。
- OpenAI + 其他兼容中转：不得匹配爱黄牛。

### 本阶段只做

- 修复 `WebAuthProfileRegistry.findFor()` 的匹配逻辑。
- 保持现有 MiMo Profile 和爱黄牛 Profile 内容不变。
- 保持 `WebAuthActivity`、Adapter、Widget、缓存、授权存储和 UI 布局不变。
- 更新 `DEVELOPMENT_LOG.md` 和 `AI_HANDOFF.md` 的阶段状态。

### 验收标准

- MiMo 仍显示“连接账户”。
- 当前爱黄牛 OpenAI 卡片仍显示“连接账户”。
- 将 OpenAI 卡片 API Base 临时改成非爱黄牛地址后，“连接账户”不得继续显示。
- 恢复爱黄牛 API Base 后，“连接账户”重新出现。
- MiMo 余额、Kimi 次数、爱黄牛自动授权和 Widget 现有行为不回归。

---

## Stage 7B：DeepSeek 官方能力闭环

### 目标

把现有 DeepSeek 官方路径从“已有 Adapter、但本轮未完整重新验证”推进到可独立验收的完整闭环：

```text
DeepSeek 配置
→ 测试连接 / 获取模型
→ 保存真实模型名
→ DeepSeekOfficialAdapter 查询官方余额接口
→ Widget 展示真实余额与可用辅助余额字段
→ 复用现有缓存与临时网络故障兜底
→ 真机验收
```

### 本阶段代码调整

1. 测试连接的 `/v1/models` 地址兼容 API Base 末尾已经带 `/v1` 的情况，避免拼成 `/v1/v1/models`。
2. `DeepSeekOfficialAdapter` 继续只使用模型 API Key，不引入网页登录或后台授权。
3. DeepSeek 余额成功返回时，`WidgetData.modelName` 保留配置中选中的真实模型名，不再用“可用/余额不足”之类状态词代替模型名。
4. 余额、赠送余额、充值余额只展示接口真实返回字段；不伪造调用次数、Token 或百分比。
5. 继续复用现有 AdapterFactory、AdapterRequest、WidgetData 缓存与失败兜底，不建立第二套链路。

### 明确不做

- 不新增 DeepSeek 网页登录。
- 不修改 MiMo、Kimi、爱黄牛协议。
- 不修改 Widget 布局和响应式逻辑。
- 不新增数据库、WorkManager 或新的后台架构。
- 不伪造 DeepSeek 官方接口没有提供的数据。

### 验收标准

- DeepSeek 配置页可使用官方 API Base + API Key 完成“测试连接”。
- `/v1/models` 在 API Base 带或不带 `/v1` 时均不会重复拼接版本路径。
- 单模型自动保存；多模型继续使用现有选择弹窗。
- Widget DeepSeek 卡片标题继续显示用户选择的真实模型名。
- Widget 展示真实余额；接口存在赠送/充值余额时作为辅助指标展示。
- 无可靠使用率或近期用量数据时继续保持空，不伪造。
- 临时网络错误继续使用现有最近成功数据兜底。
- MiMo、Kimi、爱黄牛现有真机能力不回归。

---

## Stage 7C：认证与数据能力模型统一

### 用户目标

用户希望同一个 Dashboard 能统一承载三类实际数据获取路径：

1. `API`：使用模型 API Key 访问模型、余额、用量、额度等接口。
2. `网页授权`：用户在 App 内登录网页，获得 Cookie / Bearer Token / Session，再访问账户后台数据。
3. `Billing`：访问平台提供的 Billing / Subscription / Usage Billing 类接口。

内部概念必须保持准确：

- API Key、Cookie、Bearer Token 属于认证或凭据。
- Billing 属于数据接口来源，不是新的认证类型。
- 一个模型实例可以同时拥有多种数据来源。

### 本阶段目标

建立机器可读的 Provider 能力描述，让代码明确知道：

- 当前 Adapter 需要哪些凭据。
- 当前 Adapter 实际使用哪些数据来源：`API`、`网页授权`、`Billing`。
- 当前 Adapter 实际能提供哪些数据能力。

统一数据能力包括：

- Models
- Balance
- Quota
- Usage
- Requests
- Tokens
- Profile
- Subscription

### 真实性规则

只有已经在代码中真实接入的数据路径，才能声明为已支持。

尤其：

- 当前 Adapter 没有实际调用 Billing 接口时，不得仅因为平台可能存在 Billing 接口就声明 `Billing` 已接入。
- 当前 Stage 7C 先建立统一能力模型并映射现有真实能力。
- 后续新增 Billing 数据源时，必须有真实接口证据和真机验证，再把 `Billing` 加入对应 Adapter 的来源集合。

### 当前真实映射

#### Kimi / NewApiAdapter

- 数据来源：API
- 后台网页授权：无
- 数据能力：Models、Quota、Usage、Requests

#### MiMo

- 数据来源：API + 网页授权
- 后台网页授权：Cookie
- 数据能力：Models、Balance

#### DeepSeek 官方

- 数据来源：API
- 后台网页授权：无
- 数据能力：Models、Balance

#### 爱黄牛

- 数据来源：API + 网页授权
- 后台网页授权：Bearer Token
- 数据能力：Models、Balance、Usage、Requests、Tokens、Profile

### 本阶段代码范围

- 新增统一 `DataSourceType`。
- 新增统一 `DataCapability`。
- 新增统一 `ProviderCapabilityProfile`。
- `PlatformAdapter` 暴露 `capabilityProfile`。
- 四个现有 Adapter 按真实实现声明自身能力。
- 配置页高级设置显示当前实例的数据来源、账户授权方式、Billing 接入状态和可用数据能力。

### 明确不做

- 本阶段不新增 Billing HTTP 请求。
- 不修改现有 Adapter 的数据请求和 JSON 解析。
- 不修改 Widget 数据流、缓存、布局或响应式逻辑。
- 不修改 WebAuthActivity、WebAuthProfile 或授权存储格式。
- 不把固定平台槽位改造成动态实例槽位。
- 不新增 Provider。

### 验收标准

- Kimi、MiMo、DeepSeek、爱黄牛高级设置均能显示能力摘要。
- 摘要与当前真实实现一致。
- 当前四个平台的原有数据获取结果完全不变。
- MiMo 与爱黄牛原网页登录授权继续有效。
- 未真实接入 Billing 的 Adapter 显示 `Billing：当前未接入`，不得伪装为已支持。
- 编译、覆盖安装和真机回归通过。

---

## Stage 7D：Kimi / NewAPI Billing 数据源真实接入

### 目标

在 Stage 7C 已建立的 `API / 网页授权 / Billing` 统一能力模型上，把当前已经有接口证据的 Kimi / NewAPI Billing 路径真正接入数据层。

本阶段真实请求：

```text
GET /v1/dashboard/billing/subscription
GET /v1/dashboard/billing/usage
Authorization: Bearer <模型 API Key>
```

当前已知真实字段：

- `soft_limit_usd`，兼容旧返回字段 `soft_limit`
- `total_usage`

`total_usage` 原始值以中性数值展示（当前未确认单位，不添加货币符号）。

### 数据合并规则

1. 原 `/api/usage/token` 次数卡路径继续保留，现有"剩余次数 / 调用次数 / 本地近期用量"不得回归。
2. Billing 请求与原次数卡请求相互独立：
   - 原次数卡成功 + Billing 成功：合并展示。
   - 原次数卡成功 + Billing 失败：继续显示原次数卡数据，不因 Billing 失败降级。
   - 原次数卡失败 + Billing 成功：允许返回 Billing 数据，证明 Billing 是独立数据来源，不只是能力标签。
   - 两条路径都失败：返回原真实错误。
3. Billing 成功时，Kimi / NewApiAdapter 的 `capabilityProfile.sources` 才加入 `DataSourceType.BILLING`。
4. Billing 数据通过现有 `WidgetData` 返回，不建立第二套 Widget 数据结构。
5. 同一 host 的连续 HTTP 请求必须串行，间隔至少 500ms。

### Widget 展示

- 现有核心指标继续优先保留"剩余次数"。
- Billing 成功时，在辅助轮播数据中增加一条真实 Billing 指标：
   - `Billing 额度 X · 用量 Y`（中性数值，最多保留2位小数，无货币符号）
- 如果只取得其中一个真实字段，只显示实际取得的字段。
- 不伪造余额、Token、请求次数或使用率。

### 本阶段范围

只修改：

- `NewApiAdapter.kt`
- `PROJECT.md`
- `DEVELOPMENT_LOG.md`
- `AI_HANDOFF.md`

不修改：

- `BalanceWidgetProvider`
- `AdapterFactory`
- `AdapterRequest`
- `MainActivity`
- MiMo / DeepSeek / 爱黄牛 Adapter
- 网页授权
- 配置存储
- Widget 布局、响应式和缓存
- 固定槽位 / 动态实例结构

### 验收标准

- Kimi 能力摘要从 `API` 变为 `API + Billing`，Billing 显示"已接入"。
- `/v1/dashboard/billing/subscription` 与 `/v1/dashboard/billing/usage` 当前真实返回可被解析。
- Widget 保留原 Kimi 剩余次数，并能轮播显示真实 Billing 指标。
- Billing 请求失败不得破坏原次数卡成功数据。
- MiMo、DeepSeek、爱黄牛能力摘要和真实数据不回归。
- 编译、覆盖安装和用户真机验收通过。

---

## Stage 8A：模型实例与固定平台槽位解耦

### 目标

让 Widget 的每一张卡片不再天生等于 Kimi / MiMo / DeepSeek / OpenAI，而是绑定一个独立的**模型实例（ModelInstance）**。实例通过 `serviceType` 决定使用哪个 Adapter，通过 `instanceId` 稳定标识自身，配置、授权、缓存和 Widget 绑定全部围绕 `instanceId` 进行。

### 核心概念

```text
ModelInstance
├─ instanceId        // 稳定唯一标识（如 legacy-kimi）
├─ displayName       // 用户可修改的卡片名称（如"个人 Kimi 中转"）
├─ serviceType       // 决定 Adapter 类型
├─ apiBase           // API Base URL
├─ apiKey            // 模型 API Key
├─ modelName         // 真实模型名称
└─ enabled           // 是否启用
```

关键区分：

- `instanceId`：负责配置归属、Widget 绑定、后台授权归属、缓存归属。
- `serviceType`：负责 AdapterFactory 路由、平台协议差异、能力模型。
- `displayName`：只负责展示，不得作为存储 Key 或 Adapter 路由条件。

**注意：** `backgroundAuthType` 和 `capabilityProfile` 不在 ModelInstance 中持久化，由运行时 Adapter 的 `capabilityProfile` 决定。

### Stage 8A 子阶段拆分

| 子阶段 | 目标 | 范围 |
|--------|------|------|
| **8A-1** | 方案摸排与文档确认 | 只读分析 + 更新 PROJECT.md（已完成） |
| **8A-2** | 新增 ModelInstance 数据结构和实例仓库 | 新增数据类 + ConfigRepository 只读迁移（当前阶段） |
| **8A-3** | 后台授权和缓存 Key 迁移到 instanceId | BackgroundAuthRepository + WidgetData 缓存 |
| **8A-4** | Widget Provider 读取窗口绑定的 instanceId | BalanceWidgetProvider 槽位解耦 |

### 明确不在 Stage 8A 实现

- 新增 / 删除任意数量实例
- 拖动排序
- Widget 外观重做
- 动态卡片数量
- 多尺寸布局改版
- 合并到 main

### 兼容迁移要求

现有四个平台配置不得丢失。首次读取新结构时，自动把原固定四槽配置迁移为四个实例：

- `legacy-kimi`
- `legacy-mimo`
- `legacy-deepseek`
- `legacy-openai`

迁移规则：

- API Base / Key / modelName / enabled 保留。
- MiMo / 爱黄牛网页登录授权不得失效（`instanceKey` 从平台名改为 `legacy-*` 后，授权数据需要同步迁移或兼容读取）。
- 旧 Widget 数据和持久化兜底不得被清空。
- 迁移幂等，只执行一次。
- 不卸载、不清除应用数据。

### ModelInstanceRepository 安全规则（Stage 8A-2B）

#### 实例有效性检查

`hasValidInstances()` 必须同时满足：

1. `model_instances_v1` 存在且是非空 JSON 数组；
2. 每一项都能完整解析；
3. 每个 `instanceId` 都非空；
4. `instanceId` 不能重复；
5. `serviceType` 字段能够解析（UNKNOWN 是合法显式类型）；
6. 数组中不存在解析失败后被静默丢弃的对象。

任一条件不满足 → `hasValidInstances` 返回 `false`，允许从旧配置重新恢复。

#### 写入结果处理

`saveInstances()` 返回 `Boolean`：

1. 先只写入 `model_instances_v1`；
2. 检查 `commit()` 返回值；
3. 只有数据写入成功，才单独写入 `model_instance_schema_version = 1`；
4. 再检查第二次 `commit()` 返回值。

`ensureMigrated()` 返回 `Boolean`：

- 已有有效结构 → `true`
- 迁移和两步写入全部成功 → `true`
- 迁移来源为空或写入失败 → `false`

#### 固定 instanceId 映射

| 旧平台 | instanceId |
|--------|-----------|
| Kimi | `legacy-kimi` |
| MiMo | `legacy-mimo` |
| DeepSeek | `legacy-deepseek` |
| OpenAI | `legacy-openai` |

匹配时大小写不敏感，输出严格固定。不得因 displayName、API Base 或 API Key 改变固定 instanceId。

#### 额外配置稳定 instanceId

`ConfigRepository.loadAllConfigs()` 返回四条以外的配置时：

- 格式：`legacy-extra-<安全slug>-<稳定短摘要>`
- slug：只保留小写英文字母、数字和短横线；连续非法字符合并为一个短横线；首尾短横线移除；为空时用 `unnamed`
- 稳定短摘要：SHA-256 前 12 位
- 摘要输入（稳定字段）：`config.id`、`config.name`、规范化 `apiBase`、`config.model`、原列表索引
- 不得把 API Key 原文写入日志、文档或终端输出
- 重复执行迁移必须生成相同 instanceId
- 最终列表内如仍发生冲突，确定性消歧（追加递增序号），不能覆盖前一项

#### ServiceType 推断

固定槽位：

- Kimi → `NEW_API`
- MiMo → `MIMO`
- DeepSeek（apiBase 含 `api.deepseek.com`）→ `DEEPSEEK_OFFICIAL`
- DeepSeek（其他）→ `OPENAI_COMPATIBLE`

OpenAI 和额外配置按 apiBase 域名推断：

- `coolyeah.net` → `NEW_API`
- `api.deepseek.com` → `DEEPSEEK_OFFICIAL`
- `aihuangniu.com` → `AIHUANGNIU`
- `platform.xiaomimimo.com` → `MIMO`
- OpenAI 槽位无法识别 → `OPENAI_COMPATIBLE`
- 额外配置无法识别 → `UNKNOWN`（不得静默改成任意已知平台）

### 固定槽位耦合点摸排结果（Stage 8A-1）

#### 1. 配置存储层（ConfigRepository + MainActivity）

**当前状态：**

- `MainActivity` 硬编码 `platforms = listOf("Kimi", "MiMo", "DeepSeek", "OpenAI")`。
- 配置按 `prefs.getString(platform, null)` 保存，Key 就是平台名字符串。
- `savePlatformConfig(prefs, platform, config)` 直接以平台名作为 SharedPreferences Key。
- `ConfigRepository.loadAllConfigs()` 已支持新旧格式兼容，但 `LEGACY_KEYS` 仍是固定四个平台名。

**耦合点评分：** 🔴 高

**迁移方向：**

- 新增 `ModelInstance` 数据类。
- 新增 `InstanceRepository`（或扩展 `ConfigRepository`），以 `instanceId` 为 Key 存储。
- 保留旧 `Kimi`/`MiMo`/`DeepSeek`/`OpenAI` Key 的读取能力，首次启动时自动迁移到新结构。

#### 2. Widget Provider 层（BalanceWidgetProvider）

**当前状态：**

- 硬编码 `slotIds = listOf("Kimi", "MiMo", "DeepSeek", "OpenAI")`。
- `platforms` 和 `configs` 按 `slotIds` 顺序取前 2 或前 4 个。
- `fetchWidgetDataForPlatform(platformName, config, prefs, context)` 的 `platformName` 同时用于：
  - AdapterFactory 路由
  - BackgroundAuthRepository 读取授权（`${platformName}_auth`）
  - WidgetData 缓存 Key（`cachedData[platformName]`）
  - 渲染前缀映射（`kimi`/`mimo`/`ds`/`oai` → 布局 ID）
- `updateCardTitle` 和 `renderWidgetData` 按 `prefix` 映射到固定布局 ID。

**耦合点评分：** 🔴 高

**迁移方向：**

- `slotIds` 从固定平台名改为读取窗口绑定的 `instanceId` 列表。
- `fetchWidgetDataForPlatform` 的 `platformName` 参数拆分为 `instanceId` + `serviceType`。
- AdapterFactory 路由改用 `serviceType`。
- BackgroundAuthRepository 改用 `instanceId` 读取授权。
- WidgetData 缓存 Key 改用 `instanceId`。
- 渲染前缀映射暂时保留（Stage 8A-4 只改绑定逻辑，不改布局 ID）。

#### 3. AdapterFactory 路由层

**当前状态：**

- `getAdapter(platformName: String, apiBase: String)` 同时依赖 `platformName` 和 `apiBase`。
- `platformName == "MiMo"` → `MiMoAdapter`
- `platformName == "Kimi"` → `NewApiAdapter`
- `apiBase.contains("coolyeah.net")` → `NewApiAdapter`
- `apiBase.contains("api.deepseek.com")` → `DeepSeekOfficialAdapter`
- `apiBase.contains("aihuangniu.com")` → `AihuangniuAdapter`
- `apiBase.contains("platform.xiaomimimo.com")` → `MiMoAdapter`

**耦合点评分：** 🟡 中

**迁移方向：**

- 新增 `getAdapterByServiceType(serviceType: String, apiBase: String)`。
- `serviceType` 枚举：`kimi`, `mimo`, `deepseek`, `openai-compatible`。
- 保留旧 `getAdapter(platformName, apiBase)` 作为兼容入口（内部转发到新方法）。
- Adapter 内部 `platformName` 字段暂时保留（不影响功能，后续可逐步清理）。

#### 4. 后台授权层（BackgroundAuthRepository）

**当前状态：**

- `load(prefs, instanceKey)` / `save(prefs, instanceKey, config)` / `clear(prefs, instanceKey)`
- 存储 Key：`${instanceKey}_auth`
- 当前调用方传入的 `instanceKey` 是平台名（`Kimi`/`MiMo`/`DeepSeek`/`OpenAI`）。

**耦合点评分：** 🟡 中

**迁移方向：**

- 接口本身已经抽象为 `instanceKey`，只需把调用方从平台名改为 `instanceId`。
- 兼容读取：如果 `${instanceId}_auth` 不存在，尝试回退读取旧 `${platformName}_auth`（迁移期内）。
- MiMo Cookie 和爱黄牛 Bearer Token 格式不变。

#### 5. WidgetData 缓存层（WidgetData.kt）

**当前状态：**

- `loadLastSuccessfulData(platformName: String)` 和 `persistLastSuccessfulData()` 使用 `platformName` 作为 SharedPreferences Key。
- 缓存 Prefs 名称：`widget_last_success`

**耦合点评分：** 🟡 中

**迁移方向：**

- 缓存 Key 从 `platformName` 改为 `instanceId`。
- 首次读取时，如果 `${instanceId}` 缓存不存在，尝试回退读取旧 `${platformName}` 缓存（迁移期内）。

#### 6. WebAuthProfileRegistry 层

**当前状态：**

- `findFor(instanceKey: String, apiBase: String)` 按 `instanceKey` 匹配。
- MiMo Profile：`instanceKey = "MiMo"`
- 爱黄牛 Profile：`instanceKey = "OpenAI"`

**耦合点评分：** 🟢 低

**迁移方向：**

- `findFor` 的 `instanceKey` 参数改为 `serviceType`（或新增 `findForServiceType`）。
- Profile 注册表的 `instanceKey` 字段改为 `serviceType` 字段。
- 兼容读取：保留旧 `instanceKey` 匹配逻辑作为回退。

### Stage 8A-1 完成标准

- [x] 固定槽位耦合点摸排完成（6 处）
- [x] 迁移方向明确
- [x] PROJECT.md 已追加 Stage 8A 设计
- [ ] DEVELOPMENT_LOG.md 已追加 Stage 8A-1 记录
- [ ] AI_HANDOFF.md 已更新当前状态
- [ ] 提交并推送

---

## Stage 8B-R：GitHub 云端构建与远程安装交付

### 目标

以 GitHub 远端开发分支作为唯一代码基线，让用户无需在本地执行 Gradle 或 ADB，也能获得与指定提交严格对应的可安装 APK。

### 交付规则

- 开发分支和 Pull Request 通过 GitHub Actions 执行 `assembleDebug`。
- 构建成功后上传 `app-debug.apk` 作为短期构建产物，便于阶段验收。
- 推送 `v*-beta.*` 标签时，自动创建 GitHub Prerelease，并附带公开可下载 APK。
- APK 文件名必须包含版本标签，Release 必须能够追溯到唯一提交 SHA。
- 正式签名尚未建立前，只允许标记为 Debug Prerelease，不得冒充应用商店正式版本。
- Release 页面和构建日志不得包含 API Key、Cookie、Bearer Token 或其他用户凭据。

### 本阶段范围

- GitHub Actions Debug 构建产物上传。
- GitHub Prerelease APK 发布。
- 项目状态、开发日志和交接文档同步。

### 明确不做

- 不修改 Adapter、缓存、授权、配置保存或 Widget 业务逻辑。
- 不引入开发者中转服务器。
- 不在仓库保存签名密钥。
- 不把 Debug APK 宣称为生产签名正式版。

### 验收标准

- GitHub Actions 对指定提交执行 `assembleDebug` 成功。
- 构建产物中真实存在 APK。
- GitHub Prerelease 提供可由手机直接下载的 APK 链接。
- 用户覆盖安装后，首页显示的阶段和构建版本与 Release 对应。
- 用户完成真机核心流程验收前，不合并到 `main`。

---

## Stage 8C：Widget 静默刷新与卡片同步角标

### 用户目标

Widget 自动刷新或第八次手动点击触发刷新时，不再用“正在同步…”或“自动同步”等文字覆盖卡片数据。刷新期间继续显示上一次已经渲染的可用数据，仅在正在请求的已配置卡片右下角显示 `😂`；新结果准备完成后一次性更新数据并隐藏角标。

### 刷新规则

1. 刷新开始只允许对同步角标和八连点底部文案执行局部更新，不得全量重绘数据区。
2. 已配置且参与本轮请求的可见卡片显示 `😂`；未配置、未启用或当前尺寸未展示的卡片不显示角标。
3. 核心指标、近期消耗、进度、辅助指标和上次更新时间在请求完成前保持原样。
4. 请求完成后，无论获得实时数据、缓存兜底或明确错误，都隐藏同步角标，并一次性渲染最终结果。
5. `😂` 只表示当前正在刷新，不再追加到网络失败后的缓存指标中。
6. 首次添加 Widget、尚无任何历史渲染时，可以保持布局默认空状态，等待首轮结果；不得伪造数据。
7. 八连点刷新门槛和前七次调皮倒计时文案保持不变，第八次才开始真实请求。

### 本阶段范围

- `BalanceWidgetProvider.kt`：移除刷新开始时的数据清空和同步文字，改为局部同步角标。
- `widget_balance.xml`：为四个视觉卡片增加独立的右下角同步角标。
- `WidgetData.kt`：移除失败缓存指标中的旧 `😂` 装饰，避免同步状态语义混淆。
- GitHub Debug Prerelease 构建与公开 APK 交付。

### 明确不做

- 不修改 Adapter 请求、平台 JSON 解析、认证、缓存身份或配置存储。
- 不改变八连点计数和三秒窗口。
- 不实现任意数量模型实例、拖动排序或新的 Widget 尺寸。
- 不合并到 `main`。

### 验收标准

- 自动刷新和第八次点击刷新期间，Widget 内不出现“正在同步…”或“自动同步”文字。
- 已有数据在刷新期间保持可见，不闪空、不被加载文案替换。
- 正在请求的已配置卡片右下角显示 `😂`，请求结束后消失。
- 未配置卡片不显示同步角标，也不发起平台请求。
- 请求失败时继续遵守最近成功缓存规则，但缓存数据显示不残留同步角标。
- GitHub Actions `assembleDebug` 成功并发布新的公开 Debug Prerelease APK。
- 用户覆盖安装并完成真机验收前，不进入下一业务阶段。

---

## Stage 8D-S：固定 Beta 签名与持续覆盖升级

### 问题背景

`v0.1.0-beta.1` 与 `v0.1.0-beta.2` 都由 GitHub Actions 临时 Runner 使用自动生成的 Debug Keystore 签名。Runner 每次全新创建，导致每个 APK 的签名证书不同，同一包名 `com.java.myapplication.dev` 无法通过 `adb install -r` 持续覆盖升级。

旧 Runner 的 Debug 私钥没有保存，只有公开证书，无法重新签出与旧 Beta 相同的 APK。因此本阶段必须建立新的固定 Beta 签名身份，并明确执行一次签名迁移；之后所有 Beta 才能稳定覆盖安装。

### 签名边界

- 固定签名仅用于 Debug Prerelease 包 `com.java.myapplication.dev`。
- 本阶段签名不是应用商店生产签名，不用于未来正式包 `com.java.myapplication`。
- 私钥、Keystore 和密码不得提交到 Public 仓库、Release、Artifact、构建日志或开发日志。
- 私钥通过 GitHub Actions Secrets 注入；Runner 只在临时目录重建签名文件，工作结束后删除。
- 仓库只允许保存公开证书 SHA-256 指纹，用于 CI 防错校验。

### GitHub Actions Secrets

固定使用以下 Secret 名称：

- `ANDROID_SIGNING_KEYSTORE_BASE64`
- `ANDROID_SIGNING_STORE_PASSWORD`
- `ANDROID_SIGNING_KEY_ALIAS`
- `ANDROID_SIGNING_KEY_PASSWORD`

Secret 缺少任意一项时，Prerelease 工作流必须在编译前失败，不得回退到临时 Debug 签名并发布。

### Gradle 与 CI 规则

1. 本地未提供签名环境变量时，开发者仍可使用默认 Debug 签名执行普通本地调试构建。
2. 只要提供了任意固定签名环境变量，就必须四项完整；不完整时 Gradle 配置直接失败。
3. Prerelease 工作流从 Secrets 重建 PKCS12 Keystore，并通过环境变量向 Gradle 注入。
4. `assembleDebug` 完成后，必须使用 Android `apksigner` 读取 APK 实际证书摘要，并与仓库记录的公开指纹严格比较。
5. 指纹不一致时不得上传 Artifact、不得创建 GitHub Release。
6. 普通 Pull Request 基线构建不读取签名 Secrets，其 APK 只能标记为临时签名测试产物，不得作为可持续安装版本分发。

### 当前固定 Beta 证书

- Alias：`ai-api-dashboard-beta`
- 类型：PKCS12 / RSA 4096 / SHA-256
- 公开证书 SHA-256：`A8F816B106F23274F35E3DDC8B19C464A31F7A7BD0871E3294AA6E6922954860`
- 证书用途：仅 Beta Debug 包

### 一次性迁移规则

- `beta.1` / `beta.2` 无法直接覆盖安装固定签名版，这是 Android 签名安全限制，不得宣称可绕过。
- 当前旧 Debug 包和新固定签名包都可调试时，可以在卸载前通过 `run-as` 备份 App 私有 SharedPreferences，再安装固定签名包并恢复。
- 备份可能包含 API Key、Cookie 和 Token，只能写入用户控制的私有目录；不得上传到 GitHub、开发者服务器或公开存储。
- 恢复成功并经用户确认后必须删除临时备份。
- 如果设备不支持 `run-as` 或私有恢复失败，立即停止，不得自动清除旧应用数据。

### 本阶段范围

- `app/build.gradle.kts` 固定 Beta 签名注入。
- GitHub Prerelease 工作流 Secrets 重建、签名校验和 `v0.1.0-beta.3` 交付。
- Baseline Artifact 明确标记为临时签名测试产物。
- `.gitignore` 增加 PKCS12、PEM 等签名材料保护。
- 项目状态、开发日志与 AI 交接同步。

### 明确不做

- 不修改 Widget、Adapter、缓存、认证、配置或网络业务逻辑。
- 不创建正式商店签名或 Play App Signing 配置。
- 不把任何私钥或密码提交到仓库。
- 不继续下一项产品功能。
- 不合并到 `main`。

### 验收标准

- GitHub Secrets 四项完整存在，但界面和日志不显示其值。
- GitHub Actions 对精确提交只执行一次 `assembleDebug` 并成功。
- APK 实际证书 SHA-256 与固定公开指纹一致。
- `v0.1.0-beta.3` Release 目标提交、文件大小和 SHA-256 可追溯。
- 用户完成一次旧签名到固定签名的安全迁移。
- 后续 Beta 使用同一证书，可通过 `adb install -r` 覆盖固定签名版。

---

## Stage 8C-1：Widget 同步角标定位修复

- `😂` 仅表示对应卡片正在后台刷新，不替换上次成功数据。
- 角标必须位于卡片底部信息行最右侧，拥有独立宽度，不得与辅助指标重叠或被高度压缩裁切。
- 四张卡片使用相同布局规则；不修改刷新触发、缓存、Adapter 或数据展示逻辑。
- 使用固定 Beta 签名发布 `v0.1.0-beta.4`，允许从 `beta.3` 直接覆盖安装。

---

## Stage 8E-S：Public Beta 逻辑与数据稳定性收口

用户明确要求将已摸排的逻辑和数据缺陷合并为一次稳定性批次，不再逐项安装。该批次不增加新平台或重新设计界面，只收口既有能力：

- 本机 API Key、Cookie、Token 使用 Android Keystore 加密后持久化，系统备份继续关闭。
- 网页授权按槽位独立保存；历史平台公共授权只做一次迁移，不再让同平台多个槽位强制共享账户。
- 服务识别、网页登录跳转和 JavaScript 桥只允许真实 HTTPS Host，不使用 URL 字符串包含判断。
- 最近成功缓存使用稳定能力字段判断完整度，记录保存时间；残缺结果不得降级覆盖完整结果。
- Cookie/Token 轮换不制造无限缓存身份；账户重新授权时只清除当前槽位缓存。
- 近期用量采样线程安全，五分钟内保留最早基线，避免高频刷新导致统计永远无法形成。
- 同 Host 请求在进程内统一串行，多个 Widget 与设置页预览不得并发轰击同一服务。
- Widget 刷新令牌、轮播位置、生命周期状态按 `appWidgetId` 隔离，异常退出必须清除同步角标。
- NewAPI 使用标准 JSON 解析；DeepSeek API 与网页来源相互独立并支持多币种余额；爱黄牛保留具体 HTTP/网络错误。
- Prerelease 工作流对业务源码变更自动构建，并支持手动指定后续 Beta 标签。

本阶段交付为固定 Beta 签名 `v0.1.0-beta.5`。动态 ModelInstance 列表、生产商店签名和 UI 重设计不属于本稳定性批次。

### Stage 8E-1：Widget 独立俏皮状态行

- 指标区只显示真实数值与原始指标名称，不显示“缓存·”或“缓存时间”。
- 同步过程中继续显示上次成功数据；暂时失败时也继续保留相同数据。
- 同步中与暂时失败不在 Widget 上作技术状态区分，统一在每张卡片右下角独立显示 `😂 数据在路上～`。
- 状态行不得与辅助指标共享宽度，也不得随上方指标由两行变一行而被挤压或裁切。
- 获取到新数据后隐藏状态行；设置页仍可保留明确的数据来源说明。

---

## Stage 8F-P1：通用网页仪表盘识别实验版

### 实验目标

先用一个与现有 Widget、Adapter 和授权仓库隔离的入口，验证以下完整链路是否能在真实第三方站点工作：

用户填写可用的 OpenAI-Compatible API Base、API Key 和模型名称
→ 用户填写任意 HTTPS 仪表盘网址
→ App 内打开网页并由用户自行登录、导航到目标仪表盘
→ 用户确认当前页面后开始只读捕获页面自身的 JSON 响应
→ 本机脱敏并筛选候选数据
→ 只调用一次用户所选模型进行字段语义识别
→ 在实验页展示识别结果，供用户人工核对

这一次模型调用只用于判断捕获字段的语义，不用于制造统计基线、写入目标网站或生成业务数据。

### 数据与安全边界

1. 仅允许用户主动输入并打开 `https://` 页面。
2. 用户确认前不安装网络观察脚本；确认后只对当时页面主 Origin 捕获。
3. 不使用 `addJavascriptInterface` 向任意网页暴露原生对象；捕获结果保留在页面内存中，通过主框架 `evaluateJavascript` 主动读取。
4. 只读取页面自身已经能够读取的 `fetch` / `XMLHttpRequest` JSON 响应；不读取请求体、请求头、Cookie、localStorage、sessionStorage、密码输入框或文件内容。
5. 查询参数在进入分析前移除；常见认证字段、邮箱、手机号、JWT、Bearer 和 API Key 形态在本机脱敏。
6. 捕获条数、单条大小、页面文字和 AI 请求体均设硬上限，避免无限收集或把整页内容发送给模型。
7. API Key 仅保存在当前实验 Activity 内存中，不写入实验配置、不输出日志；AI 请求直接从用户手机发送到用户填写的 API Base，不经过开发者服务器。
8. 页面内容视为不可信数据，模型提示明确禁止执行页面中的指令；模型结果必须通过本地 JSON 结构校验后才展示。
9. 实验 WebView 使用独立进程和独立数据目录；退出实验室时清除实验 Cookie 与网页存储，不触碰主 App 的网页登录会话。

### 本阶段范围

- 新增独立启动入口“仪表盘识别实验室”。
- 新增实验配置页、受限 WebView 捕获页和识别结果预览。
- 支持 OpenAI-Compatible `POST /v1/chat/completions` 的单次语义识别。
- 新增脱敏、候选筛选和 AI 返回结构解析单元测试。
- 使用固定 Beta 签名发布独立实验 Prerelease。

### 明确不做

- 不把识别结果写入现有 Widget、Adapter、缓存或后台授权仓库。
- 不自动持久化网页捕获内容、AI API Key 或识别映射。
- 不实现后台定时重放、不自动刷新第三方网页、不上传开发者服务器。
- 不承诺所有站点都可登录；禁止嵌入式 WebView 的 OAuth 平台仍可能要求系统浏览器或官方 OAuth。
- 不支持 Claude、Gemini 等原生非 OpenAI 请求协议；首版只验证用户已配置的 OpenAI-Compatible 模型调用。

### 验收标准

- 安装后可从独立桌面入口打开实验室，不影响现有 App 与 Widget。
- 无完整 API 配置、网址非 HTTPS 或未确认当前页面时，不能开始 AI 识别。
- 捕获列表不包含 Cookie、Authorization、请求体和完整查询参数。
- 每次用户点击识别只发出一次模型请求，失败不自动重试。
- 结果页明确展示捕获数量、脱敏说明、模型原始判断和结构校验结论。
- 退出实验室后不在本地留下捕获正文、API Key 或识别映射。

---

## Stage 8F-P2：更早捕获与本机字段核对

### 用户目标

在 P1 已经证明“网页捕获 + AI 看懂字段”可行的基础上，进一步让实验室回答两个普通用户能直接理解的问题：

1. 哪些数字已经找到真实数据来源，App 以后有机会自动刷新？
2. 哪些数字只是 AI 从页面文字中看见，暂时不能自动刷新？

### 实现规则

1. 用户确认当前页面后，优先使用 AndroidX WebKit 的 Document Start Script，在目标网页自己的脚本运行前安装只读观察器，再刷新一次页面。
2. 提前注入只允许用户确认的精确 HTTPS Origin；不向其他登录页、跳转页或 iframe 扩大权限。
3. 当前 WebView 不支持提前注入时，继续使用 P1 的多次 `evaluateJavascript` 方式兜底，并在界面明确显示当前捕获方式。
4. AI 返回的每个指标必须在本机再次核对：
   - endpoint 必须来自本次真实捕获；
   - JSON 路径必须能够在对应响应中实际取到值；
   - 实际值类型必须与数字、文字、布尔值、对象或数组之一匹配。
5. 同时满足真实 endpoint 与本机取值验证的结果进入“已验证字段”。
6. endpoint 或字段路径为空、路径取不到值、接口不属于捕获集合的结果进入“页面观察信息”，不得冒充可自动刷新字段。
7. 本机只支持安全、有限的简单路径格式，例如 `$.balance`、`$.data.usage.total`、`$.items[0].quota`；拒绝脚本、过滤器、递归和通配符表达式。
8. P2 仍然只预览结果，不保存站点规则，不持久化登录态，不接入 Widget。

### 依赖取舍

- 新增官方稳定版 `androidx.webkit:webkit:1.16.0`。
- 引入原因：Android 原生 WebView 没有等价的按 Origin 限制、文档开始阶段注入接口；该依赖只用于提升实验捕获完整度，不引入新的网络、数据库或后台框架。

### 验收标准

- 实验首页显示 `Stage 8F-P2 · Prototype 20260717-002`。
- 支持提前注入的 WebView 在刷新前完成精确 Origin 脚本注册；不支持时功能仍可继续。
- 结果页分别显示“已验证字段”和“页面观察信息”的数量与原因。
- 空 endpoint、空 JSON 路径或不存在的路径绝不能进入已验证字段。
- 类似 P1 的结果中，只有真实存在的 `$.total_usage` 可以通过本机核对；四个仅来自页面文字的余额/Token 指标应进入观察信息。
- 单次点击仍只调用一次 AI，不新增后台重试，不读取或上传认证凭据。
