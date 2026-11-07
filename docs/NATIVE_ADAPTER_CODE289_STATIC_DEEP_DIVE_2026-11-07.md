# code289 Native Adapter 静态深挖补充（2026-11-07）

> 目标 APK：DeepSeek 国内版 2.7.2 / versionCode 289（code289-cn）。
> 证据来源：`qingjian0/apps` workflow artifact `deepseek-272-static-evidence`，run `42342447740`，artifact `13561683064`，APK SHA-256 见该 artifact 的 `apk.sha256`。
> 本文只记录 Smali 静态事实。它不证明反射、宿主对象生命周期、挑战流程或真实 Flow 在设备上可执行。

## 1. 本轮纠正：不要把 `oe2.i()` 当作 Session Factory

之前的口头梳理把 `oe2.i(oe2, ik2, o97, fy4)` 误列为 Session Factory 主入口。该判断不成立。

实际签名：

- `oe2.i(Loe2;Lik2;Lo97;Lfy4;)V`：静态 void 方法；从 `ik2.Y()` 取当前 `ys`，读取其会话标识，并访问模型/状态列表及 UI 状态。现有方法体证据不足以把它定义为会话创建器。
- `oe2.j()Lik2;`：构造一个初始 `ys`，再构造并返回 `ik2` 组件。
- `oe2.k(Lys;)Lik2;`：接收既有 `ys`，构造并返回 `ik2` 组件。
- `oe2.r()Lys;`：从当前 `oe2.q()` 状态分支取得 `ys`，不是新建会话方法。

因此，Session UI/组件创建入口应从 `oe2.j/k/r` 分开研究；不能把 `oe2.i` 当成 Native API 可直接调用的 Session Factory。

## 2. `fx1.m(Continuation)` 的准确语义边界

方法签名：

```smali
.method public final m(Lyb3;)Ljava/lang/Object;
```

静态观察：

1. 通过 `fx1.m -> pi.e -> ce3` 取得一个预取/队列管理对象。
2. 检查管理对象状态，调用其准备/刷新方法。
3. 从 `ConcurrentLinkedDeque` 调用 `pollFirst()`，候选经 `ke4.a()`、`fn8.a()` 等状态检查。
4. 有可用候选时，从 `fn8` 取得 `ss9`，按耗时重建 `ss9`，构造 `ys`，再构造 `pt7(ws9, tn2(ys, true))` 并返回。
5. 没有可用候选时，走 `ru7.N(tc3, vy4, Continuation)`，其 lambda 为 `mb0(pi, continuation)`。

重要限制：

- 该方法确实是 suspend 状态机（返回值为 `Object`，最后参数为 Continuation），不能当作同步 Java 方法调用。
- 它有预取队列快速路径与异步回退路径。
- 从该方法体只能确认回退进入 `ru7.N` 和 `mb0`，尚不能仅凭此方法断言完整的服务端创建请求、挑战/PoW 生命周期及持久化完成条件。
- `fx1.l(Lys)` 只显示委托到 `pi.e(Lys)`；这证明存在一个写入/更新入口，不证明调用时序、持久化成功或对现有用户会话无副作用。

## 3. code289 Transport 入口的强静态证据

### 3.1 委托层

```smali
zn3.o(Lwu1;Ljava/lang/Long;)Ly60;
  -> iget-object p0, zn3.a : jw3
  -> jw3.g(Lwu1, null)
  -> return Ly60
```

`zn3.o` 明确将传入的 `wu1` 请求交给其字段 `jw3`，第二个 Long 参数在该方法中被置为 null。

### 3.2 `jw3.g` 请求路由

`jw3.g(Lwu1;Ljava/lang/Long;)Ly60`：

- 检查请求对象是否为 `ly1`。
- 命中 `ly1` 时取得对应 serializer，并构造序列化配置。
- 读取 `ly1.k` 字段。
- 读取 completion request timeout 配置（本地持久化、运行时覆盖及远端设置回退）。
- 使用路径字符串 `/api/v0/chat/completion` 构造请求路径。
- 同一方法还分支处理 regenerate、continue、resume_stream、edit_message 等请求类型；这些请求类型不可混同为普通 completion。

这证明 `jw3.g` 是多请求类型的路由/构造入口，而不是单一“只发 completion”的简单函数。

### 3.3 返回值与 Flow collect

`jw3.g` 返回 `y60`。类 `y60` 的关键方法为：

```smali
y60.b(Lfl4;Lyb3;)Ljava/lang/Object;
```

该方法包含多个协程状态分支，并把传入的 `fl4` collector 与内部适配/上游 Flow 连接。不能把 `y60` 当作已经收集完毕的响应对象；必须沿 collector、上游 Flow 和响应事件转换继续追踪。

## 4. `r3.c` 是事件 reducer，不是普通文本拼接器

签名：

```smali
r3.c(Ldv1;Lyb3;)Ljava/lang/Object;
```

当前 Smali 显示：

- 从输入 `dv1` 分派具体事件类型。
- `cv1` 包装 `sha`；`sha` 有两个 String 字段，事件名与 JSON data 分别被读取。
- 对 null event-name 分支执行 DeltaCommand 反序列化，并进入 `doc` 状态树更新路径。
- 命名事件分支包括 `ready`、`hint`、`title`、`update_session`、`close` 等。
- Delta/命名事件处理存在 suspend 调用、状态更新与错误分支；非法事件会进入明确错误路径。

实现约束：

- 不得以 `StringBuilder.append(data)` 代替 reducer。
- 不得把所有 null-name 事件直接当成通用 Delta，除非 serializer 和类型判别条件也被复刻。
- 必须保留 BATCH 内部顺序、失败传播和 reducer 状态隔离。
- 不得把 `doc` 状态树的内部对象当作稳定公开 API DTO。

## 5. 可实现与不可宣称的边界

| 子系统 | 仅凭本 artifact 可做 | 仍不能宣称 |
|---|---|---|
| Session component | 复刻 DTO/状态机模型的离线测试；识别 `oe2.j/k/r` 关系 | 可安全创建或复用真实宿主会话 |
| Prefetch/session provider | 复刻 `fx1.m` 两路径控制流的测试模型 | 回退必然创建服务端会话、可直接生产调用 |
| Session update | 识别 `fx1.l -> pi.e` | 持久化已完成、无用户会话副作用 |
| Request transport | 复刻 `zn3.o -> jw3.g` 的类型分支与路由断言 | 真实 Challenge/PoW 已满足、反射调用可执行 |
| Flow | 建立 collector 生命周期模型和取消契约测试 | 真实 Flow 可被模块外部收集且不破坏宿主 |
| Delta | 复刻事件分类和 reducer 测试 | 已取得完整真实响应序列与所有事件覆盖 |

## 6. 下一步静态工作顺序

1. 从 `oe2.j/k/r` 向上追踪调用者，确认 UI/session component 的生命周期和调用上下文。
2. 展开 `mb0`、`ru7.N`、`vc3`、`tc3`，还原 `fx1.m` 的异步回退，不预设其为服务端创建。
3. 从 `jw3.g` 的 `/api/v0/chat/completion` 分支继续追踪序列化、请求 header、challenge 依赖和实际 HTTP 执行对象。
4. 从 `y60.b`、上游 Flow 和 `r3.c` 建立完整 collector → event → reducer → state tree 链。
5. 在纯 JVM 测试中先验证状态机模型、顺序、取消、异常传播和并发隔离。
6. 真机不可用期间，Gateway 仍保持 503；静态实现完成不等于 native readiness。

## 7. 取消与协程设计约束

宿主方法使用 Kotlin suspend/Continuation 形态。模块侧桥接必须遵守协程结构化并发：客户端断开或超时应取消对应请求任务；取消异常不得被吞成普通 500；清理动作必须释放 collector/资源，不能用 `runBlocking` 阻塞 HTTP accept/UI 线程。

参考 Kotlin 官方协程指南：
- https://kotlinlang.org/docs/coroutines-guide.html
- https://kotlinlang.org/docs/coroutines-basics.html

这些是模块桥接设计原则，不代表已经验证宿主协程可被任意外部 Job 取消。
