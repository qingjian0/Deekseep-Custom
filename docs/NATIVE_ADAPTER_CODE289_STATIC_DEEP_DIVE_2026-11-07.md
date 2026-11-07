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


## 8. Request Factory 补充：`nn0.d -> mw1 -> ly1 -> zn3.o`

本轮继续沿 code289 的 Smali 向上追踪，已找到比单独检查 `ly1` 更完整的请求构造入口：

### 8.1 `nn0.d` 创建 suspend lambda

签名：

```smali
nn0.d(
  Lys,
  String,
  Integer,
  List,
  boolean,
  boolean,
  String,
  ada,
  nr2,
  Continuation
): Object
```

该方法是 Kotlin suspend 状态机。方法体在请求构造阶段创建 `mw1`，将当前 `nn0`、`ys`、Integer、List、多个 String 和 boolean 参数捕获到该 lambda 对象中。不要将其解释成可同步调用的 DTO 构造器。

### 8.2 `mw1` 将捕获参数整理为 `ly1`

`mw1` 构造器保存：

| 字段 | 声明类型 |
|---|---|
| `f` | `nn0` |
| `g` | `ys` |
| `h` | `Integer` |
| `i` | `List` |
| `j` | `String` |
| `k` | `String` |
| `l` | `boolean` |
| `m` | `boolean` |
| `n` | `String` |
| `e` | lambda 执行时收到的 String |

`mw1.y(Object)` 随后：

1. 从捕获的 `ys` 和 Integer 调用 `nn0.h(ys, Integer)`，取得一个布尔条件；
2. 从 `nn0.b` 取得 `zn3`；
3. 将 List 转换为 ArrayList；
4. 读取 `ys.k()`；
5. 依据 Integer 是否为空决定一个传给 DTO 的 String 参数是否置空；
6. 使用捕获的字段构造 `ly1`；
7. 调用 `zn3.o(ly1, null)`；
8. 返回 `y60`。

因此可确认一条直接的请求构造链：

```text
nn0.d(ys, ...)
  -> new mw1(nn0, ys, ...)
      -> mw1.y(...)
          -> new ly1(...)
          -> zn3.o(ly1, null)
              -> jw3.g(ly1, null)
                  -> y60
```

### 8.3 请求 DTO 的字段写入顺序

`ly1` 主构造器签名：

```smali
ly1(
  String, Integer, String, ArrayList,
  boolean, boolean, String, boolean,
  String, String, int
)
```

构造器将前十个业务参数写入字段 `a..k`（其中字段 `j` 在构造器内写入默认值；synthetic mask 用于默认参数处理）。当前可确认的是参数类型、捕获来源和传递顺序；尚不能仅凭短字段名把所有 String/boolean 解释成 prompt、parent id、附件开关或联网开关。字段语义必须结合 serializer `ly1` 的 descriptor/element 名称与调用者参数来源逐一确认。

### 8.4 当前可以实施的离线工作

- 为 `nn0.d` 的参数签名和 `mw1` 捕获字段建立静态 schema；
- 为 `mw1.y` 到 `ly1` 的参数位置映射建立单测；
- 对 `Integer == null` 与非 null 两条路径分别做请求模型测试；
- 对 `List -> ArrayList`、默认参数 mask、可选 String 的 null 语义做回归；
- 校验 `zn3.o` 接收的对象必须是 code289 的 `ly1`。

不应在没有运行时生命周期证据时直接调用宿主 `nn0.d` 或 `mw1.y`；它们依赖宿主内部对象、协程上下文和网络/挑战状态。


## 9. Session Provider 回退路径已确认：真实命中 `/api/v0/chat_session/create`

本节是对第 2 节“尚不能断言完整服务端创建请求”的更新。继续展开 `mb0`、`ch2`、`rz0`、`zs9` 与 `j41` 后，code289 静态证据已经足以确认：**`fx1.m` 的预取队列 miss 回退会进入 DeepSeek 的 chat-session create 网络路由，并在业务成功分支构造本地 `ys` 状态对象。**

### 9.1 调用链

```text
fx1.m(Continuation)
  -> new mb0(pi, Continuation)              // mb0 branch e=7
      -> mb0.F()
          -> pi.c : zn3
          -> zn3.b : ch2
          -> ch2.b(Continuation)
              -> new rz0(..., e=2)
              -> bi5.e(serializer, rz0, Continuation)
                  -> rz0.y()
                      -> /api/v0/chat_session/create
                      -> 5000ms timeout
              -> zs9
          -> zs9.a(true, Continuation)
              -> normalize HTTP/business response
          -> business code == 0
              -> j41(gs9, ft9, zs9, ...)
                  -> decode ft9.biz_data as ok2
                  -> ok2.a : ss9
                  -> new ys(ss9, ...)
                  -> new tn2(ys, false)
                  -> new pt7(businessCode, tn2)
          -> non-zero business code / transport error
              -> explicit tt7 / ut7 / st7 / rt7 error results
```

### 9.2 网络请求与响应包装证据

- `ch2.b(Continuation)` 创建 `rz0`，构造 `ft9` 的泛型 serializer：外层为 `ServerResponseBizDataWrapper`，业务码类型为 `ServerDefaultBizCode`。
- `rz0` 的 branch id 为 2；在 `rz0.y()` 对应的 packed-switch 分支中，明确构造路径 `/api/v0/chat_session/create`。
- 同一分支设置请求超时为 `0x1388`，即 5000 ms。
- `z2c.V(Lng5, Lxa3)` 设置的是 `Content-Type` header，不是 HTTP method。因此本证据不据此宣称请求一定是 POST；method 仍需沿 `Lng5` / `rh5` / 网络执行层继续核实。
- `dt9` serializer descriptor 明确列出 `biz_code`、`biz_msg`、`biz_data`；`us9` 对应 `ServerDefaultBizCode`。
- `zs9.a(true)` 对业务码执行分支判断；`j41` 在成功路径从 `ft9.c`（biz_data JSON element）使用 `ok2` serializer 解码，取得 `ok2.a : ss9`。
- `j41` 使用该 `ss9` 构造 `ys`，再包装为 `tn2(ys, false)` 与 `pt7` 返回。这里确认的是本地 session state 初始化，不把 `ys.J(String)` 的短字段语义猜成 session id 或 title。
- 业务码非零时会进入显式错误对象路径；不能把 HTTP 200 等同于业务成功。

### 9.3 对前一版结论的修订

此前第 2 节只确认 `fx1.m` 的回退进入 `ru7.N -> mb0`，并保留“可能没有完整服务端创建”的限制。现在应更新为：

1. **已确认：** 预取队列 miss 时，回退路径确实发起 `/api/v0/chat_session/create` 请求。
2. **已确认：** 响应按 `ServerResponseBizDataWrapper<ServerDefaultBizCode>` 解析；成功业务码分支解码 `ok2` / `ss9` 并构造 `ys`。
3. **尚未确认：** 该创建请求是否还依赖未在当前链路中完全展开的认证、challenge/PoW、设备上下文或远端配置；也未证明在任意外部调用上下文中可安全复用该内部 API。
4. **尚未确认：** `fx1.m` 返回对象在所有 UI / Agent 调用场景下的生命周期、是否写入持久化、与 `fx1.l -> pi.e` 的关系，以及取消时宿主侧清理语义。
5. **不可直接移植：** 不能只复制 URL、serializer 和 DTO 就绕过宿主的 `bi5` 网络栈、拦截器、身份态或请求上下文。

### 9.4 对 Local API Adapter 的工程含义

这项发现让 Session Bootstrap 从“未知回退”推进为“静态可定位的原生 Session Create 入口”。但它还不是可直接接入的完成适配器：

- 不应自行构造 `rz0`、`ch2` 或调用 `fx1.m`，除非先解决宿主依赖对象的获取、生命周期和线程约束。
- 应继续追踪 `bi5.e` 到实际 HTTP 执行、认证拦截器、公共 header 与失败转换。
- 应追踪 `ys` 的后续写入和 `tn2` 包装消费者，确认新会话如何进入宿主当前状态。
- 应将业务码失败、HTTP 失败、超时、取消、反序列化失败分别建模；不得将任何失败伪装成空会话或成功响应。
- 在缺少设备验证时，Gateway 继续保持 503 fail-closed。

### 9.5 可复现静态审计

新增脚本：`tools/audit_code289_session_fallback.py`

执行方式：

```bash
python3 tools/audit_code289_session_fallback.py /path/to/all-smali.zip
```

本轮对 workflow artifact 中的 `all-smali.zip` 执行了相同的 14 项静态断言，结果 **14/14 PASS**。脚本只检查 Smali 结构和关键调用/常量，不执行宿主代码，也不代表 Android 构建、设备运行或认证链路通过。
