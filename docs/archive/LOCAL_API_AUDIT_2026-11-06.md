# Local API 历史审计快照（2026-11-06）

> 类型：历史审计摘要（依据此前分支审计记录重建，并非原始报告逐字归档）。
> 原始来源分支：`work/local-api-event-decoder-diagnostics`；该分支现已不在远端分支列表。
> 当前承接主线：`dev`。本文只保存历史证据，不代表当前 Gateway 的协议限制或运行状态。

## 1. 当时的设备请求审计

此前记录的真机 API 捕获结果包括 6 个 HTTP 400：
- 5 个请求包含 `tools`；
- 1 个请求包含 `stream_options.include_usage`。

当时的审计判断是：这些失败首先暴露了协议表面兼容限制，不能据此直接归类为原生 Transport failure。该结论只描述 2026-11-06 当时的请求与实现快照；后续主线已增加工具桥接与相关协议映射，不能将旧的 400 结论套用到当前代码。

原审计记录还引用了 5 份设备日志文件。由于原始分支已删除且当前无法重新读取原文件，本摘要不声称保留了原始日志全文或全部字段。

## 2. 当时确认的 code289 调用边界

当时静态/设备调查记录了以下候选链路边界：

```text
ly1 implements wu1
  -> zn3.o(wu1, Long)
  -> jw3.g(wu1, Long)
  -> y60 / Ly60 cold Flow
```

后续主线已进一步补充并校正了调用语义：
- `zn3.o` 委托至 `jw3.g`，返回冷 Flow；调用入口本身不等于请求已经执行，必须 collect。
- `y60.b(fl4, Continuation)` 是 Flow collect 边界，不是 SSE parser。
- 后续静态调用链还识别了 Ktor 执行、HTTP response handler、SSE line parser、event wrapper 与 Delta reducer。

## 3. 当前适用边界

- 本文记录的是历史审计结论，不是当前协议支持矩阵。
- 后续主线已加入 Chat Completions、Responses、Anthropic Messages 等协议层实现；具体行为以当前 `docs/architecture/OPEN_LOCAL_API_GATEWAY.md` 和实际源码为准。
- code289 的静态结构证据不等于 Native Completion Adapter 已完成。
- 未取得当前 schema-6 诊断构建的设备运行记录前，不得宣称已观察到完整的 `ready`、DeltaCommand 与 `close` 事件序列。
- 不得将 HTTP 400 协议输入拒绝、HTTP 503 adapter 未就绪、上游 HTTP 错误或 PoW/Challenge 失败混为同一种 Transport 故障。

## 4. 归档说明

原始 `docs/LOCAL_API_AUDIT_2026-11-06.md` 曾位于已删除的 `work/local-api-event-decoder-diagnostics` 分支。本文件是依据此前已审计并留存的摘要重建的最小历史记录；如需原始逐行日志或原报告全文，必须从用户本地 Git 对象库、备份或其他仍保留该提交的远端引用恢复，不能从本摘要反推缺失内容。
