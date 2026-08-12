# PIRT Pi 重构交接

更新时间：2026-08-11

仓库：`C:\Users\ZIXT\AndroidStudioProjects\PIRT`

主线：`master`

## 当前产品边界

PIRT 是 Pi 在 Android 上的本地界面。全局只有一个共享 workspace；Pi 会话是顶层对象。PIRT 不管理 Git、worktree、Diff、Checkpoint 或每会话文件隔离；Fork、Clone 和 Steer 直接使用 Pi SDK 原生语义。

## 已确定的 Pi 原则

涉及 Pi 的数据和控制前，先检查当前固定版本 Pi 0.84.1 的源码。Pi 已实现的能力直接映射，不在 Android 建立平行模型。

- Pi JSONL 是唯一会话目录和消息历史。
- Android 不保存会话标题、路径、时间、归档状态或会话 ID。
- 新会话启动时不传 `--session-id`：Pi 自己生成 ID，RPC `get_state` 返回它。
- 新会话不传 `--name`：列表使用 Pi `session_info.name`，没有名称时使用 Pi `firstMessage`。
- Pi 在启动时已经有内存 session ID；出现第一条 assistant 消息后才把 JSONL 刷到磁盘。
- 会话列表由 Pi `SessionManager.list("/workspace", "/root/.pi/pirt-sessions")` 产生。
- 恢复会话使用 Pi JSONL path。
- 活跃会话重命名使用 RPC `set_session_name`；非活跃会话通过 `SessionManager.open(...).appendSessionInfo(...)`。
- 删除会话就是删除对应 Pi JSONL，不删除共享 workspace。
- Fork 使用 `AgentSessionRuntime.fork(entryId)`；Clone 使用当前 leaf 的 `fork(..., { position: "at" })`。
- Android 只投影 Pi `get_entries` 返回的真实 `entryId`，不按消息文本推断分支点。
- Steer 使用 `AgentSession.steer()` 和原生 `queue_update`；当前不实现 Follow-up 或自维护消息队列。
- 不做归档；列表超过 20 个时只做 UI 展开/收起。

Android 在 Pi 返回 ID 前只持有一个不落盘的 runtime handle，用于寻址刚启动的子进程。它不是会话 ID，不进入 catalog。

## 当前所有权链

```text
Compose
  -> AppViewModel（固定 workspace，无会话存储）
  -> RuntimeConnection
       -> RuntimeService
            -> PiSessionCatalog
                 -> PiControlClient
                      -> pirt-control-bridge.mjs
                           -> Pi SessionManager / ModelRuntime / SettingsManager
                           -> AgentSessionRuntime（每个 live 会话）
            -> PiSessionManager
                 -> SessionController
                      -> PiControlClient
```

`RuntimeService` 是 Pi、认证、终端和桌面进程的唯一所有者。页面退出、Activity 重建和页面切换不决定子进程寿命。

## 文件与路径

```text
files/pirt/workspace/                              固定共享工作区
files/pirt/runtime/ubuntu/root/.pi/pirt-sessions/ Pi 会话 JSONL
files/pirt/runtime/ubuntu/root/.pi/agent/          Pi 账号、模型和设置
```

Guest 内所有 Pi 会话的 cwd 都是 `/workspace`。

关键实现：

- `runtime/pi/PiSessionCatalog.kt`：Pi catalog 的只读 StateFlow 和 rename/delete 命令。
- `assets/runtime/pirt-control-bridge.mjs`：常驻全局控制进程，直接调用当前 Pi 包导出的 `SessionManager`、`ModelRuntime` 和 `SettingsManager`。
- `runtime/pi/PiSessionManager.kt`：live Pi Runtime 路由、身份迁移与状态归一化。
- `runtime/pi/PiRpcProtocol.kt`：严格 request/response 与 SDK 事件投影。
- `runtime/PiAuthManager.kt` 与 `PiControlClient.kt`：直接复用 Pi 认证和模型体系，不再单独冷启动 bridge。

全局只有一个常驻 PRoot/Node SDK host，不再维护 CLI RPC 子进程池或预热槽位。多个 `AgentSessionRuntime` 在该 Node 进程内并存；会话切换不产生新的 OS 进程。Runtime replacement 负责扩展的 `session_before_fork`、`session_shutdown`、`session_start` 生命周期和订阅重绑。

## 验证边界

自动门禁负责 Kotlin 编译、单测、lint、assemble、静态结构和 Git cleanliness。Compose 手势、真机返回、流式显示、登录以及会话切换由用户直接操作验证，不用自动化点击替代用户体验。
