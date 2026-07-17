# community-common 边界层加固 设计文档

> 状态：已与需求方确认，待写实现计划。日期 2026-07-17。

## 背景与目标

数智化社区平台共 6 个后端微服务（auth/info/service/welfare/affairs/portal），除 info 有部分实现外均为空壳。在铺开各业务模块前，先把**横切/边界层**（集中在 `community-common`）做正确、一致，使后续每个模块都继承同一套可靠地基。

本次通过实跑（gateway→info 端到端）与代码勘探，确认了 5 组边界缺口，本设计逐一给出目标行为。

## 范围

**纳入**（本轮）：
1. 统一响应 `RetObj` + 状态码 `SystemStatus` 目录
2. 业务异常 `BizException` + 全局异常处理 `GlobalExceptionHandler`
3. 过滤器层错误统一（修复直连绕过返回 500 而非 403 的 bug）
4. 用户信息贯穿：网关→服务→上下文→审计的契约，含异步线程传递
5. 参数校验 `@Valid` 的一致启用

**不做**（YAGNI，记为后续）：
- 敏感字段脱敏集中化（当前各 service 手写 `maskIdCard`，保持不变）
- 数据权限 `data_scope` 行级过滤（依赖 auth 角色，后续随 auth 模块）
- 内部密钥 `expected-secret` 外置（当前硬编码于 `AuthFilter.SECRET_KEY` 与 `CommonConstant.SECRET_KEY` 两处——列为安全跟进项）
- 带角色/部门的强类型 UserContext（`UserContextHolder` 暂保持 `Map<String,String>`）

## 全局约定（本设计的基石决策）

- **状态码约定**：成功 HTTP 200；错误时 HTTP status 语义化（400/401/403/404/405/409/500），`RetObj.code` 与 HTTP status 一致。只改异常处理器与过滤器，不改各 controller 的 `return RetObj.success(...)`。
- **响应分层**：service 返回领域数据（实体/DTO/void），业务错误 `throw new BizException(...)`；controller 用 `RetObj.success(data)` 包装。service 不再依赖 web 信封。
- **无登录用户审计**：白名单/公开接口写库时 `createBy/updateBy` 填固定常量 `"system"`。

---

## ① 统一响应 `RetObj`

- 修正枚举拼写 `SUSSES → SUCCESS`（`RetObj` 内引用同步）。
- 保证 `code` 永不为 null。
- 静态工厂补齐并规范：
  - `success()`、`success(T data)`
  - `error(SystemStatus status)`、`error(SystemStatus status, String message)`
  - `error(String message)`（兼容旧用法，code 默认 500）
- `RetObj{code, message, data}` 仅作响应体；HTTP status 由异常处理器 / 过滤器决定，二者的 code 与 HTTP status 保持一致。

## ② 状态码目录 `SystemStatus`（`code` == HTTP status）

| 枚举 | code | 默认 message |
|---|---|---|
| SUCCESS | 200 | 请求成功 |
| BAD_REQUEST | 400 | 请求参数错误 |
| UNAUTHORIZED | 401 | 未认证或 token 失效 |
| FORBIDDEN | 403 | 无权限访问 |
| NOT_FOUND | 404 | 资源不存在 |
| METHOD_NOT_ALLOWED | 405 | 请求方法不支持 |
| CONFLICT | 409 | 数据冲突 |
| INTERNAL_ERROR | 500 | 系统繁忙，请稍后重试 |

- 同一 HTTP status 下的业务细因通过 `message` 表达（如 `CONFLICT` + `"该证件号已存在"`），不再新造数字码。
- 移除旧的 `UNAVAILABILITY(401,...)` 与 `ERROR(500,...)`，对应引用（`RetObj`、网关等）同步迁移到 `UNAUTHORIZED` / `INTERNAL_ERROR`。

## ③ 业务异常 `BizException`

- `public class BizException extends RuntimeException`，字段 `SystemStatus status`（由构造入参指定，必填）+ 复用 `getMessage()`。
- 构造：`BizException(SystemStatus status)`、`BizException(SystemStatus status, String message)`。
- 用法：service 层遇业务错误直接抛，例：
  ```java
  if (exists) throw new BizException(SystemStatus.CONFLICT, "该证件号已存在");
  ```
- 由 `GlobalExceptionHandler` 统一转 `RetObj` + 对应 HTTP status。

## ④ 全局异常处理 `GlobalExceptionHandler`

- 返回 `ResponseEntity<RetObj<?>>`：同时设置 HTTP status 与响应体，二者 code 一致。
- 异常→状态映射：

| 异常 | HTTP status / code |
|---|---|
| `BizException` | 取其 `status` |
| `MethodArgumentNotValidException` / `BindException` / `ConstraintViolationException` / `HttpMessageNotReadableException` / `MethodArgumentTypeMismatchException` / `MissingServletRequestParameterException` | 400，message 汇总字段错误 |
| `NoHandlerFoundException` / `NoResourceFoundException` | 404 |
| `HttpRequestMethodNotSupportedException` | 405 |
| `DuplicateKeyException` / `DataIntegrityViolationException` | 409 |
| 其它 `Exception` 兜底 | 500，**固定文案“系统繁忙，请稍后重试”** |

- **移除**当前直接回传 `e.getMessage()` 的 `RuntimeException` 处理器（避免泄漏 NPE/SQL 等内部信息）；未知异常仅在服务端记录完整堆栈（带 traceId），对外只给通用文案。

## ⑤ 过滤器层错误统一（修复 403→500）

- 根因：`RequestHeaderFilter` 抛 `ResponseStatusException`，在 servlet Filter 层（DispatcherServlet 之前）不被 `@RestControllerAdvice` 捕获，冒泡到 Tomcat 兜成 500。
- 方案：新增 common 工具 `ResponseWriter.writeError(HttpServletResponse resp, SystemStatus status)`——设置 `resp.setStatus(status.code)`、`Content-Type: application/json;charset=UTF-8`，写出 `RetObj.error(status)` 的 JSON。
- `RequestHeaderFilter` 内部鉴权失败时改为 `ResponseWriter.writeError(resp, FORBIDDEN)` 并 `return`（不再 throw）。直连绕过网关将返回 **HTTP 403 + RetObj**，与异常处理器风格一致。

## ⑥ 用户信息贯穿 + 异步传递 + 审计

**契约（文档化，写入设计与 CLAUDE.md 现有说明一致）：**
1. 网关 `AuthFilter`：校验 Redis 中 `alibaba-token:<token>`，向下游注入 `X-Internal-Auth`、`X-UserInfo`(用户 JSON 的 Base64)、`traceId`。
2. 服务 `RequestHeaderFilter`：校验 `X-Internal-Auth`；解码 `X-UserInfo` 存入 `UserContextHolder`(ThreadLocal) 与 MDC；`finally` 清理。
3. 服务间：`FeignConfig`（已实现）转发 `X-Internal-Auth` + `X-UserInfo`(取自 `UserContextHolder`) + `traceId`(取自 MDC)。**本设计不改动 FeignConfig**。

**异步线程传递（本次修复）：**
- 现状：`ThreadPoolConfig` 的 `taskExecutor` 使用 `MdcTaskDecorator`，只拷贝 MDC、不拷贝 `UserContextHolder` → 异步池内审计人丢失、Feign 转发的 `X-UserInfo` 为空。
- 方案：新增 `ContextTaskDecorator implements TaskDecorator`，在提交任务时快照**主线程的 MDC + UserContextHolder**，在子线程 `run` 前 set、`finally` 双清理。将 `ThreadPoolConfig.taskExecutor` 的 decorator 换成它。`MdcTaskDecorator` 可保留或删除（实现计划决定；倾向以 `ContextTaskDecorator` 取代并删除旧类，避免两个装饰器并存）。

**审计填充（`MybatisPlusConfig` MetaObjectHandler）：**
- 新增常量 `CommonConstant.AUDIT_SYSTEM_USER = "system"`。
- `createBy/updateBy` 取 `UserContextHolder.getUserId()`；为空则填 `AUDIT_SYSTEM_USER`。`createTime/updateTime` 保持 `LocalDateTime.now()`。

## ⑦ 参数校验

- 引入依赖：各业务模块的 `spring-boot-starter-validation`（info 已在上一轮加入；其余模块随各自实现补）。
- controller 规范：`@RequestBody` 参数加 `@Valid`；controller 类加 `@Validated` 以校验 `@RequestParam`/路径参数。
- 校验失败经 ④ 统一映射为 400，message 汇总字段级错误。

## 测试策略

沿用项目约定：**Mockito + MockMvc `standaloneSetup`**，不加载 Spring 上下文、不连 Nacos/DB。

- `GlobalExceptionHandler`：用 `standaloneSetup(...).setControllerAdvice(new GlobalExceptionHandler())` 注册；断言 `BizException`→对应 status/body；参数校验→400；未知异常→500 且**响应体不含原始异常消息**。
- `RequestHeaderFilter`：`standaloneSetup(...).addFilters(new RequestHeaderFilter())`；缺/错 `X-Internal-Auth`→403 + RetObj JSON；带正确头 + `X-UserInfo`→放行且 `UserContextHolder` 被填充。
- `ContextTaskDecorator`：单元测试——主线程 set 上下文后，装饰后的 Runnable 在另一线程能读到相同 MDC/UserContext，且执行后被清理。
- `MybatisPlusConfig` 审计：无用户上下文时 `insertFill` 填 `"system"`；有用户时填其 id。

## 影响面

- 改动集中在 `community-common`：`RetObj`、`SystemStatus`、`GlobalExceptionHandler`、新增 `BizException`、新增 `ResponseWriter`、`RequestHeaderFilter`、`ThreadPoolConfig`、新增 `ContextTaskDecorator`、`MybatisPlusConfig`、`CommonConstant`。
- **回改 info 样板**（作为其余 5 模块的正确模板，放入下一轮实现计划）：
  - `PopulationService`/`PopulationServiceImpl`：方法返回领域数据（`PopulationPageDto` / `Long`），业务错误改抛 `BizException`（替换手写 `RetObj.error`）。
  - `PopulationController`：`return RetObj.success(...)` 包装；`@RequestBody` 加 `@Valid`、类加 `@Validated`。
  - `PopulationControllerTest`：适配签名与新的校验/异常路径。
- 兼容性：`SystemStatus` 枚举常量重命名会波及现有引用（`RetObj`、网关 `AuthFilter`/`SystemStatus` 若各模块有副本需核对），实现计划中先全量 grep 再改。

## 验证

- `community-common` 及 info 编译通过（JDK17）。
- 单测全绿（含上述新增用例）。
- 端到端复跑（gateway→info）：
  - 正常查询/录入：HTTP 200 + RetObj。
  - 直连 info 绕过网关：**HTTP 403** + RetObj（此前为 500）。
  - 触发业务冲突（重复证件号）：HTTP 409 + RetObj，message 为业务原因。
  - 无登录录入：DB `create_by = "system"`。
