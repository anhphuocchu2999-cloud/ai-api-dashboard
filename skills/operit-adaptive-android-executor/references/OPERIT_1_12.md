# Operit 1.12+ 执行约定

## 全局 Skill 目录

```text
/storage/emulated/0/Download/Operit/skills/
```

## 推荐验证调用

工具：`super_admin:terminal`

```text
command: bash <Skill目录>/scripts/verify-debug.sh <项目根目录> install
background: false
timeoutMs: 600000
```

前台终端返回后直接处理结果。不得使用固定 `sleep`，不得轮询终端屏幕。

## 超时

若工具结果包含 `timedOut=true`：

- 视为当前验证被取消；
- 更新 `TASK_STATE.md`；
- 立即暂停；
- 不调用 `terminal_wait`；
- 不重新执行相同命令。

## package_proxy

Operit 可能通过 `package_proxy` 包装包工具调用。这是工具路由行为，不应被当作新的业务步骤。仍需遵守“不得重复命令、不得轮询”的执行规则。
