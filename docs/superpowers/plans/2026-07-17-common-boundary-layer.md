# community-common 边界层加固 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 把 community-common 的横切/边界层(统一响应、状态码、异常、用户上下文、校验)做正确一致,并回改 info 样板,作为其余 5 模块的地基。

**Architecture:** 成功 HTTP 200;错误语义化 HTTP status(code==status)。service 返回领域数据、错误抛 `BizException`,controller 用 `RetObj.success` 包装,`GlobalExceptionHandler` 统一转响应。过滤器层错误直写 RetObj。用户上下文经 `ContextTaskDecorator` 传异步线程,审计无用户填 `"system"`。

**Tech Stack:** Spring Boot 3.3.5、MyBatis-Plus、fastjson2、JUnit5 + Mockito + MockMvc standaloneSetup、JDK17。

## Global Constraints

- 构建/测试用 **JDK17**:所有 mvn 命令前缀 `JAVA_HOME=~/.sdkman/candidates/java/17.0.7-zulu`(本机默认 JDK26 会失败)。阿里云镜像已配 `~/.m2/settings.xml`。
- 命令在仓库根 `/Users/xmon/Code/IdeaProjects/measure-community-platform` 执行;用本机原生 `mvn` + `git`,**不要** `wsl`/`//wsl.localhost` 路径。
- 单测一律 **Mockito + MockMvc `standaloneSetup`**,不加载 Spring 上下文、不连 Nacos/DB。
- 提交信息用中文。
- 状态码约定:`RetObj.code` == HTTP status;成功 200,错误语义化。
- 响应分层:service 返回领域数据 + 抛 `BizException`;controller `return RetObj.success(...)`。
- 不做:脱敏集中化、`data_scope`、密钥外置、强类型 UserContext(见 spec §范围)。
- `RetObj.error(String)`(默认 500)被 auth/sentinel 调用,**必须保留**。

---

## 文件结构

```
community-common/src/main/java/com/measure/community/common/
  enums/SystemStatus.java              [改] 8 个语义码,code==HTTP status
  model/RetObj.java                    [改] 修 SUSSES→SUCCESS、ERROR→INTERNAL_ERROR,加 error(SystemStatus,String)
  exception/BizException.java          [新] 业务异常,携带 SystemStatus
  exception/GlobalExceptionHandler.java[改] 覆盖常见异常、语义化 status、不泄漏原始消息
  utils/ResponseWriter.java            [新] 向 HttpServletResponse 写 RetObj JSON
  filter/RequestHeaderFilter.java      [改] 鉴权失败直写 403+RetObj(不再 throw)
  config/ContextTaskDecorator.java     [新] 传 MDC + UserContextHolder 到异步线程
  config/ThreadPoolConfig.java         [改] 换用 ContextTaskDecorator
  config/MdcTaskDecorator.java         [删] 被 ContextTaskDecorator 取代
  config/MybatisPlusConfig.java        [改] 审计人无用户填 "system"
  constant/CommonConstant.java         [改] 加 AUDIT_SYSTEM_USER="system"
community-common/src/test/java/.../common/
  exception/GlobalExceptionHandlerTest.java  [新]
  filter/RequestHeaderFilterTest.java        [新]
  config/ContextTaskDecoratorTest.java       [新]
  config/MybatisPlusConfigTest.java          [新]
community-info/src/main/java/com/measure/community/info/
  service/PopulationService.java             [改] 返回领域数据
  service/impl/PopulationServiceImpl.java    [改] 抛 BizException
  controller/PopulationController.java       [改] RetObj.success 包装 + @Valid/@Validated
community-info/src/test/java/.../info/controller/PopulationControllerTest.java [改]
```

---

## Task 1: SystemStatus 状态码目录 + RetObj 重构

**Files:**
- Modify: `community-common/src/main/java/com/measure/community/common/enums/SystemStatus.java`
- Modify: `community-common/src/main/java/com/measure/community/common/model/RetObj.java`
- Test: `community-common/src/test/java/com/measure/community/common/model/RetObjTest.java`

**Interfaces:**
- Produces:
  - `SystemStatus` 枚举:`SUCCESS(200)`, `BAD_REQUEST(400)`, `UNAUTHORIZED(401)`, `FORBIDDEN(403)`, `NOT_FOUND(404)`, `METHOD_NOT_ALLOWED(405)`, `CONFLICT(409)`, `INTERNAL_ERROR(500)`;`getCode():Integer`、`getErrorMessage():String`。
  - `RetObj<T>`:`success()`、`success(T)`、`error(SystemStatus)`、`error(SystemStatus,String)`、`error(String)`;字段 `code/message/data`。

- [ ] **Step 1: 写失败测试**

Create `community-common/src/test/java/com/measure/community/common/model/RetObjTest.java`:
```java
package com.measure.community.common.model;

import com.measure.community.common.enums.SystemStatus;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RetObjTest {

    @Test
    void success_hasCode200() {
        RetObj<String> r = RetObj.success("x");
        assertEquals(200, r.getCode());
        assertEquals("请求成功", r.getMessage());
        assertEquals("x", r.getData());
    }

    @Test
    void errorWithStatusAndMessage_usesStatusCodeAndCustomMessage() {
        RetObj<?> r = RetObj.error(SystemStatus.CONFLICT, "该证件号已存在");
        assertEquals(409, r.getCode());
        assertEquals("该证件号已存在", r.getMessage());
    }

    @Test
    void errorWithString_defaultsTo500() {
        RetObj<?> r = RetObj.error("boom");
        assertEquals(500, r.getCode());
        assertEquals("boom", r.getMessage());
    }
}
```

- [ ] **Step 2: 运行测试,确认失败**

Run: `JAVA_HOME=~/.sdkman/candidates/java/17.0.7-zulu mvn -pl community-common test -Dtest=RetObjTest`
Expected: 编译失败或断言失败(`error(SystemStatus,String)` 不存在、`SUCCESS` 未定义)。

- [ ] **Step 3: 重写 SystemStatus**

覆盖 `community-common/src/main/java/com/measure/community/common/enums/SystemStatus.java`:
```java
package com.measure.community.common.enums;

import lombok.Getter;

@Getter
public enum SystemStatus {

    SUCCESS(200, "请求成功"),
    BAD_REQUEST(400, "请求参数错误"),
    UNAUTHORIZED(401, "未认证或token失效"),
    FORBIDDEN(403, "无权限访问"),
    NOT_FOUND(404, "资源不存在"),
    METHOD_NOT_ALLOWED(405, "请求方法不支持"),
    CONFLICT(409, "数据冲突"),
    INTERNAL_ERROR(500, "系统繁忙，请稍后重试"),
    ;

    private final Integer code;
    private final String errorMessage;

    SystemStatus(Integer code, String errorMessage) {
        this.code = code;
        this.errorMessage = errorMessage;
    }
}
```

- [ ] **Step 4: 重写 RetObj(修枚举引用 + 加 error(SystemStatus,String))**

覆盖 `community-common/src/main/java/com/measure/community/common/model/RetObj.java`:
```java
package com.measure.community.common.model;

import com.measure.community.common.enums.SystemStatus;
import lombok.Data;

/**
 * 全局统一响应对象。code 与 HTTP status 一致。
 */
@Data
public class RetObj<T> {

    private Integer code;
    private String message;
    private T data;

    public RetObj(Integer code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
    }

    public RetObj(SystemStatus status) {
        this.code = status.getCode();
        this.message = status.getErrorMessage();
    }

    public RetObj(SystemStatus status, T data) {
        this.code = status.getCode();
        this.message = status.getErrorMessage();
        this.data = data;
    }

    public static <T> RetObj<T> success() {
        return new RetObj<>(SystemStatus.SUCCESS);
    }

    public static <T> RetObj<T> success(T data) {
        return new RetObj<>(SystemStatus.SUCCESS, data);
    }

    public static <T> RetObj<T> error(SystemStatus status) {
        return new RetObj<>(status);
    }

    public static <T> RetObj<T> error(SystemStatus status, String message) {
        return new RetObj<>(status.getCode(), message, null);
    }

    public static <T> RetObj<T> error(String message) {
        return new RetObj<>(SystemStatus.INTERNAL_ERROR.getCode(), message, null);
    }
}
```

- [ ] **Step 5: 运行测试,确认通过**

Run: `JAVA_HOME=~/.sdkman/candidates/java/17.0.7-zulu mvn -pl community-common test -Dtest=RetObjTest`
Expected: `Tests run: 3, Failures: 0, Errors: 0`, BUILD SUCCESS。

- [ ] **Step 6: 提交**

```bash
git add community-common/src/main/java/com/measure/community/common/enums/SystemStatus.java community-common/src/main/java/com/measure/community/common/model/RetObj.java community-common/src/test/java/com/measure/community/common/model/RetObjTest.java
git commit -m "refactor(common): SystemStatus 扩为语义状态码目录,RetObj 补 error(SystemStatus,String)"
```

---

## Task 2: BizException 业务异常

**Files:**
- Create: `community-common/src/main/java/com/measure/community/common/exception/BizException.java`
- Test: `community-common/src/test/java/com/measure/community/common/exception/BizExceptionTest.java`

**Interfaces:**
- Consumes: `SystemStatus`(Task 1)。
- Produces: `BizException extends RuntimeException`,`getStatus():SystemStatus`,`getMessage():String`;构造 `BizException(SystemStatus)`、`BizException(SystemStatus,String)`。

- [ ] **Step 1: 写失败测试**

Create `community-common/src/test/java/com/measure/community/common/exception/BizExceptionTest.java`:
```java
package com.measure.community.common.exception;

import com.measure.community.common.enums.SystemStatus;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BizExceptionTest {

    @Test
    void carriesStatusAndCustomMessage() {
        BizException e = new BizException(SystemStatus.CONFLICT, "该证件号已存在");
        assertEquals(SystemStatus.CONFLICT, e.getStatus());
        assertEquals("该证件号已存在", e.getMessage());
    }

    @Test
    void statusOnly_usesStatusDefaultMessage() {
        BizException e = new BizException(SystemStatus.FORBIDDEN);
        assertEquals(SystemStatus.FORBIDDEN, e.getStatus());
        assertEquals("无权限访问", e.getMessage());
    }
}
```

- [ ] **Step 2: 运行测试,确认失败**

Run: `JAVA_HOME=~/.sdkman/candidates/java/17.0.7-zulu mvn -pl community-common test -Dtest=BizExceptionTest`
Expected: 编译失败(`BizException` 不存在)。

- [ ] **Step 3: 实现 BizException**

Create `community-common/src/main/java/com/measure/community/common/exception/BizException.java`:
```java
package com.measure.community.common.exception;

import com.measure.community.common.enums.SystemStatus;
import lombok.Getter;

/**
 * 业务异常。service 层遇业务错误抛出,由 GlobalExceptionHandler 统一转 RetObj + HTTP status。
 */
@Getter
public class BizException extends RuntimeException {

    private final SystemStatus status;

    public BizException(SystemStatus status) {
        super(status.getErrorMessage());
        this.status = status;
    }

    public BizException(SystemStatus status, String message) {
        super(message);
        this.status = status;
    }
}
```

- [ ] **Step 4: 运行测试,确认通过**

Run: `JAVA_HOME=~/.sdkman/candidates/java/17.0.7-zulu mvn -pl community-common test -Dtest=BizExceptionTest`
Expected: `Tests run: 2, Failures: 0, Errors: 0`。

- [ ] **Step 5: 提交**

```bash
git add community-common/src/main/java/com/measure/community/common/exception/BizException.java community-common/src/test/java/com/measure/community/common/exception/BizExceptionTest.java
git commit -m "feat(common): 新增业务异常 BizException(携带 SystemStatus)"
```

---

## Task 3: ResponseWriter 工具(过滤器写 RetObj JSON)

**Files:**
- Create: `community-common/src/main/java/com/measure/community/common/utils/ResponseWriter.java`
- Test: `community-common/src/test/java/com/measure/community/common/utils/ResponseWriterTest.java`

**Interfaces:**
- Consumes: `SystemStatus`(Task 1)、`RetObj`(Task 1)。
- Produces: `ResponseWriter.writeError(HttpServletResponse resp, SystemStatus status)` —— 设 status、`application/json;charset=UTF-8`,写出 `RetObj.error(status)` 的 JSON。

- [ ] **Step 1: 写失败测试**

Create `community-common/src/test/java/com/measure/community/common/utils/ResponseWriterTest.java`:
```java
package com.measure.community.common.utils;

import com.measure.community.common.enums.SystemStatus;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResponseWriterTest {

    @Test
    void writeError_setsStatusAndJsonBody() throws Exception {
        MockHttpServletResponse resp = new MockHttpServletResponse();
        ResponseWriter.writeError(resp, SystemStatus.FORBIDDEN);
        assertEquals(403, resp.getStatus());
        assertTrue(resp.getContentType().contains("application/json"));
        String body = resp.getContentAsString();
        assertTrue(body.contains("\"code\":403"), body);
        assertTrue(body.contains("无权限访问"), body);
    }
}
```

- [ ] **Step 2: 运行测试,确认失败**

Run: `JAVA_HOME=~/.sdkman/candidates/java/17.0.7-zulu mvn -pl community-common test -Dtest=ResponseWriterTest`
Expected: 编译失败(`ResponseWriter` 不存在)。
若报找不到 `MockHttpServletResponse`:它来自 `spring-test`(已随 spring-boot-starter-test 在 test scope,common 有该依赖)。

- [ ] **Step 3: 实现 ResponseWriter**

Create `community-common/src/main/java/com/measure/community/common/utils/ResponseWriter.java`:
```java
package com.measure.community.common.utils;

import com.alibaba.fastjson2.JSON;
import com.measure.community.common.enums.SystemStatus;
import com.measure.community.common.model.RetObj;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

/**
 * 在 Servlet 过滤器等 DispatcherServlet 之前的位置,统一写出 RetObj 错误响应。
 */
public final class ResponseWriter {

    private ResponseWriter() {
    }

    public static void writeError(HttpServletResponse resp, SystemStatus status) throws IOException {
        resp.setStatus(status.getCode());
        resp.setContentType("application/json;charset=UTF-8");
        resp.setCharacterEncoding("UTF-8");
        resp.getWriter().write(JSON.toJSONString(RetObj.error(status)));
    }
}
```

- [ ] **Step 4: 运行测试,确认通过**

Run: `JAVA_HOME=~/.sdkman/candidates/java/17.0.7-zulu mvn -pl community-common test -Dtest=ResponseWriterTest`
Expected: `Tests run: 1, Failures: 0, Errors: 0`。

- [ ] **Step 5: 提交**

```bash
git add community-common/src/main/java/com/measure/community/common/utils/ResponseWriter.java community-common/src/test/java/com/measure/community/common/utils/ResponseWriterTest.java
git commit -m "feat(common): 新增 ResponseWriter,过滤器层统一写 RetObj 错误响应"
```

---

## Task 4: GlobalExceptionHandler 扩展(语义化 HTTP status)

**Files:**
- Modify: `community-common/src/main/java/com/measure/community/common/exception/GlobalExceptionHandler.java`
- Test: `community-common/src/test/java/com/measure/community/common/exception/GlobalExceptionHandlerTest.java`

**Interfaces:**
- Consumes: `BizException`(Task 2)、`SystemStatus`、`RetObj`(Task 1)。
- Produces: `@RestControllerAdvice` 处理器,返回 `ResponseEntity<RetObj<?>>`,HTTP status == body.code。

- [ ] **Step 1: 写失败测试(含内嵌测试 controller)**

Create `community-common/src/test/java/com/measure/community/common/exception/GlobalExceptionHandlerTest.java`:
```java
package com.measure.community.common.exception;

import com.measure.community.common.enums.SystemStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GlobalExceptionHandlerTest {

    @RestController
    static class DummyController {
        @GetMapping("/biz")
        public String biz() {
            throw new BizException(SystemStatus.CONFLICT, "该证件号已存在");
        }

        @GetMapping("/boom")
        public String boom() {
            throw new RuntimeException("secret sql detail");
        }
    }

    MockMvc mockMvc;

    @BeforeEach
    void setup() {
        mockMvc = MockMvcBuilders.standaloneSetup(new DummyController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void bizException_mapsToItsStatusAndMessage() throws Exception {
        mockMvc.perform(get("/biz"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(409))
                .andExpect(jsonPath("$.message").value("该证件号已存在"));
    }

    @Test
    void unknownException_returns500AndDoesNotLeakMessage() throws Exception {
        mockMvc.perform(get("/boom"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("系统繁忙，请稍后重试"))
                .andExpect(jsonPath("$.message", not(containsString("secret sql"))));
    }

    @Test
    void methodNotSupported_returns405() throws Exception {
        mockMvc.perform(post("/biz"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.code").value(405));
    }
}
```

- [ ] **Step 2: 运行测试,确认失败**

Run: `JAVA_HOME=~/.sdkman/candidates/java/17.0.7-zulu mvn -pl community-common test -Dtest=GlobalExceptionHandlerTest`
Expected: 失败(当前处理器无 BizException 分支、不设 HTTP status、泄漏原始消息)。

- [ ] **Step 3: 重写 GlobalExceptionHandler**

覆盖 `community-common/src/main/java/com/measure/community/common/exception/GlobalExceptionHandler.java`:
```java
package com.measure.community.common.exception;

import com.measure.community.common.enums.SystemStatus;
import com.measure.community.common.model.RetObj;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * 全局异常处理器。成功走 controller 的 RetObj.success;错误在此统一转
 * ResponseEntity(HTTP status == RetObj.code)。未知异常只记服务端日志,不对外泄漏。
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BizException.class)
    public ResponseEntity<RetObj<?>> handleBiz(BizException e) {
        log.warn("业务异常: code={}, msg={}", e.getStatus().getCode(), e.getMessage());
        return build(e.getStatus(), e.getMessage());
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, BindException.class})
    public ResponseEntity<RetObj<?>> handleValidation(BindException e) {
        StringBuilder sb = new StringBuilder();
        for (FieldError fe : e.getBindingResult().getFieldErrors()) {
            sb.append(fe.getDefaultMessage()).append("; ");
        }
        String msg = !sb.isEmpty() ? sb.toString().trim() : SystemStatus.BAD_REQUEST.getErrorMessage();
        log.warn("参数校验异常: {}", msg);
        return build(SystemStatus.BAD_REQUEST, msg);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<RetObj<?>> handleConstraint(ConstraintViolationException e) {
        log.warn("约束校验异常: {}", e.getMessage());
        return build(SystemStatus.BAD_REQUEST, e.getMessage());
    }

    @ExceptionHandler({HttpMessageNotReadableException.class,
            MethodArgumentTypeMismatchException.class,
            MissingServletRequestParameterException.class})
    public ResponseEntity<RetObj<?>> handleBadInput(Exception e) {
        log.warn("请求解析异常: {}", e.getMessage());
        return build(SystemStatus.BAD_REQUEST, SystemStatus.BAD_REQUEST.getErrorMessage());
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<RetObj<?>> handleMethod(HttpRequestMethodNotSupportedException e) {
        log.warn("请求方法不支持: {}", e.getMessage());
        return build(SystemStatus.METHOD_NOT_ALLOWED, SystemStatus.METHOD_NOT_ALLOWED.getErrorMessage());
    }

    @ExceptionHandler({DuplicateKeyException.class, DataIntegrityViolationException.class})
    public ResponseEntity<RetObj<?>> handleConflict(Exception e) {
        log.warn("数据冲突: {}", e.getMessage());
        return build(SystemStatus.CONFLICT, SystemStatus.CONFLICT.getErrorMessage());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<RetObj<?>> handleException(Exception e) {
        log.error("系统内部异常: ", e);
        return build(SystemStatus.INTERNAL_ERROR, SystemStatus.INTERNAL_ERROR.getErrorMessage());
    }

    private ResponseEntity<RetObj<?>> build(SystemStatus status, String message) {
        return ResponseEntity.status(status.getCode()).body(RetObj.error(status, message));
    }
}
```

- [ ] **Step 4: 运行测试,确认通过**

Run: `JAVA_HOME=~/.sdkman/candidates/java/17.0.7-zulu mvn -pl community-common test -Dtest=GlobalExceptionHandlerTest`
Expected: `Tests run: 3, Failures: 0, Errors: 0`。

- [ ] **Step 5: 提交**

```bash
git add community-common/src/main/java/com/measure/community/common/exception/GlobalExceptionHandler.java community-common/src/test/java/com/measure/community/common/exception/GlobalExceptionHandlerTest.java
git commit -m "refactor(common): 全局异常处理语义化 HTTP status,覆盖常见异常且不泄漏原始消息"
```

---

## Task 5: RequestHeaderFilter 直写 403

**Files:**
- Modify: `community-common/src/main/java/com/measure/community/common/filter/RequestHeaderFilter.java`(第 56-58 行的鉴权失败分支)
- Test: `community-common/src/test/java/com/measure/community/common/filter/RequestHeaderFilterTest.java`

**Interfaces:**
- Consumes: `ResponseWriter`(Task 3)、`SystemStatus`(Task 1)、`CommonConstant.X_INTERNAL_AUTH/SECRET_KEY`(既有)。
- Produces: 内部鉴权失败时 HTTP 403 + RetObj JSON,不再抛异常。

- [ ] **Step 1: 写失败测试**

Create `community-common/src/test/java/com/measure/community/common/filter/RequestHeaderFilterTest.java`:
```java
package com.measure.community.common.filter;

import com.measure.community.common.constant.CommonConstant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RequestHeaderFilterTest {

    @RestController
    static class PingController {
        @GetMapping("/ping")
        public String ping() {
            return "pong";
        }
    }

    MockMvc mockMvc;

    @BeforeEach
    void setup() {
        mockMvc = MockMvcBuilders.standaloneSetup(new PingController())
                .addFilters(new RequestHeaderFilter())
                .build();
    }

    @Test
    void missingInternalAuth_returns403AndRetObj() throws Exception {
        mockMvc.perform(get("/ping"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
    }

    @Test
    void correctInternalAuth_passesThrough() throws Exception {
        mockMvc.perform(get("/ping")
                        .header(CommonConstant.X_INTERNAL_AUTH, CommonConstant.SECRET_KEY))
                .andExpect(status().isOk());
    }
}
```

- [ ] **Step 2: 运行测试,确认失败**

Run: `JAVA_HOME=~/.sdkman/candidates/java/17.0.7-zulu mvn -pl community-common test -Dtest=RequestHeaderFilterTest`
Expected: `missingInternalAuth` 失败(当前抛 `ResponseStatusException`,standalone 下冒泡为 500 而非 403)。

- [ ] **Step 3: 改鉴权失败分支为直写 403**

在 `RequestHeaderFilter.java`,把当前的:
```java
        String header = req.getHeader(CommonConstant.X_INTERNAL_AUTH);
        if (!CommonConstant.SECRET_KEY.equals(header)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "非法访问接口，禁止绕过网关访问");
        }
```
替换为:
```java
        String header = req.getHeader(CommonConstant.X_INTERNAL_AUTH);
        if (!CommonConstant.SECRET_KEY.equals(header)) {
            log.warn("非法访问接口，禁止绕过网关访问: {}", uri);
            com.measure.community.common.utils.ResponseWriter.writeError(
                    (jakarta.servlet.http.HttpServletResponse) response,
                    com.measure.community.common.enums.SystemStatus.FORBIDDEN);
            return;
        }
```
并删除不再使用的 import `org.springframework.web.server.ResponseStatusException` 与 `org.springframework.http.HttpStatus`(若无其它引用)。

- [ ] **Step 4: 运行测试,确认通过**

Run: `JAVA_HOME=~/.sdkman/candidates/java/17.0.7-zulu mvn -pl community-common test -Dtest=RequestHeaderFilterTest`
Expected: `Tests run: 2, Failures: 0, Errors: 0`。

- [ ] **Step 5: 提交**

```bash
git add community-common/src/main/java/com/measure/community/common/filter/RequestHeaderFilter.java community-common/src/test/java/com/measure/community/common/filter/RequestHeaderFilterTest.java
git commit -m "fix(common): RequestHeaderFilter 鉴权失败直写 403+RetObj(修直连绕过返回 500)"
```

---

## Task 6: ContextTaskDecorator(异步传递用户上下文)

**Files:**
- Create: `community-common/src/main/java/com/measure/community/common/config/ContextTaskDecorator.java`
- Modify: `community-common/src/main/java/com/measure/community/common/config/ThreadPoolConfig.java`(第 13 行注释、第 35 行 decorator)
- Delete: `community-common/src/main/java/com/measure/community/common/config/MdcTaskDecorator.java`
- Test: `community-common/src/test/java/com/measure/community/common/config/ContextTaskDecoratorTest.java`

**Interfaces:**
- Consumes: `UserContextHolder`(既有,`set/get/getUserId/clear`)。
- Produces: `ContextTaskDecorator implements TaskDecorator`,把主线程 MDC + UserContextHolder 快照传入线程池子线程,finally 双清理。

- [ ] **Step 1: 写失败测试**

Create `community-common/src/test/java/com/measure/community/common/config/ContextTaskDecoratorTest.java`:
```java
package com.measure.community.common.config;

import com.measure.community.common.utils.UserContextHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ContextTaskDecoratorTest {

    @AfterEach
    void tearDown() {
        UserContextHolder.clear();
    }

    @Test
    void propagatesUserContextToChildThread() throws Exception {
        Map<String, String> user = new HashMap<>();
        user.put("id", "u1");
        UserContextHolder.set(user);

        AtomicReference<String> seen = new AtomicReference<>("none");
        Runnable decorated = new ContextTaskDecorator().decorate(
                () -> seen.set(UserContextHolder.getUserId()));

        Thread t = new Thread(decorated);
        t.start();
        t.join();

        assertEquals("u1", seen.get());
    }
}
```

- [ ] **Step 2: 运行测试,确认失败**

Run: `JAVA_HOME=~/.sdkman/candidates/java/17.0.7-zulu mvn -pl community-common test -Dtest=ContextTaskDecoratorTest`
Expected: 编译失败(`ContextTaskDecorator` 不存在)。

- [ ] **Step 3: 实现 ContextTaskDecorator**

Create `community-common/src/main/java/com/measure/community/common/config/ContextTaskDecorator.java`:
```java
package com.measure.community.common.config;

import com.measure.community.common.utils.UserContextHolder;
import org.slf4j.MDC;
import org.springframework.core.task.TaskDecorator;

import java.util.Map;

/**
 * 线程池上下文装饰器:把主线程的 MDC(traceId) 与 UserContextHolder(用户信息)
 * 传入线程池子线程,执行后清理,避免异步任务丢失审计人/traceId 及线程复用污染。
 */
public class ContextTaskDecorator implements TaskDecorator {

    @Override
    public Runnable decorate(Runnable runnable) {
        Map<String, String> mdc = MDC.getCopyOfContextMap();
        Map<String, String> user = UserContextHolder.get();
        return () -> {
            try {
                if (mdc != null) {
                    MDC.setContextMap(mdc);
                }
                if (user != null) {
                    UserContextHolder.set(user);
                }
                runnable.run();
            } finally {
                MDC.clear();
                UserContextHolder.clear();
            }
        };
    }
}
```

- [ ] **Step 4: 切换 ThreadPoolConfig + 删除 MdcTaskDecorator**

在 `ThreadPoolConfig.java`:第 13 行注释里的 `MdcTaskDecorator` 改为 `ContextTaskDecorator`;第 35 行 `executor.setTaskDecorator(new MdcTaskDecorator());` 改为 `executor.setTaskDecorator(new ContextTaskDecorator());`。
然后删除文件 `community-common/src/main/java/com/measure/community/common/config/MdcTaskDecorator.java`:
```bash
rm community-common/src/main/java/com/measure/community/common/config/MdcTaskDecorator.java
```

- [ ] **Step 5: 运行测试 + 模块编译,确认通过**

Run: `JAVA_HOME=~/.sdkman/candidates/java/17.0.7-zulu mvn -pl community-common test -Dtest=ContextTaskDecoratorTest`
Expected: `Tests run: 1, Failures: 0, Errors: 0`,且无 `MdcTaskDecorator` 相关编译错误。

- [ ] **Step 6: 提交**

```bash
git add -A community-common/src/main/java/com/measure/community/common/config community-common/src/test/java/com/measure/community/common/config/ContextTaskDecoratorTest.java
git commit -m "refactor(common): ContextTaskDecorator 传递用户上下文到异步线程,取代 MdcTaskDecorator"
```

---

## Task 7: 审计人无用户填 "system"

**Files:**
- Modify: `community-common/src/main/java/com/measure/community/common/constant/CommonConstant.java`(加常量)
- Modify: `community-common/src/main/java/com/measure/community/common/config/MybatisPlusConfig.java`(insertFill/updateFill 用兜底)
- Test: `community-common/src/test/java/com/measure/community/common/config/MybatisPlusConfigTest.java`

**Interfaces:**
- Consumes: `UserContextHolder.getUserId()`(既有)。
- Produces: `MybatisPlusConfig.currentAuditUser():String` —— 有用户返回其 id,否则返回 `CommonConstant.AUDIT_SYSTEM_USER`("system");`CommonConstant.AUDIT_SYSTEM_USER`。

- [ ] **Step 1: 写失败测试**

Create `community-common/src/test/java/com/measure/community/common/config/MybatisPlusConfigTest.java`:
```java
package com.measure.community.common.config;

import com.measure.community.common.utils.UserContextHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MybatisPlusConfigTest {

    @AfterEach
    void tearDown() {
        UserContextHolder.clear();
    }

    @Test
    void noUser_fallsBackToSystem() {
        UserContextHolder.clear();
        assertEquals("system", MybatisPlusConfig.currentAuditUser());
    }

    @Test
    void withUser_returnsUserId() {
        Map<String, String> user = new HashMap<>();
        user.put("id", "42");
        UserContextHolder.set(user);
        assertEquals("42", MybatisPlusConfig.currentAuditUser());
    }
}
```

- [ ] **Step 2: 运行测试,确认失败**

Run: `JAVA_HOME=~/.sdkman/candidates/java/17.0.7-zulu mvn -pl community-common test -Dtest=MybatisPlusConfigTest`
Expected: 编译失败(`currentAuditUser` 不存在)。

- [ ] **Step 3: 加常量**

在 `CommonConstant.java` 类体内(其它常量附近)新增:
```java
	/** 无登录用户时的审计人占位 */
	public static final String AUDIT_SYSTEM_USER = "system";
```

- [ ] **Step 4: 加 currentAuditUser 并在填充中使用**

在 `MybatisPlusConfig.java` 内新增静态方法:
```java
    /** 当前审计人:有登录用户返回其 id,否则返回系统占位 "system"。 */
    public static String currentAuditUser() {
        String userId = UserContextHolder.getUserId();
        return (userId != null && !userId.isBlank())
                ? userId
                : com.measure.community.common.constant.CommonConstant.AUDIT_SYSTEM_USER;
    }
```
并把 `insertFill` 中原来"有 userId 才填 createBy/updateBy"的条件逻辑改为无条件填充:
```java
        this.strictInsertFill(metaObject, "createTime", LocalDateTime.class, LocalDateTime.now());
        this.strictInsertFill(metaObject, "updateTime", LocalDateTime.class, LocalDateTime.now());
        String auditUser = currentAuditUser();
        this.strictInsertFill(metaObject, "createBy", String.class, auditUser);
        this.strictInsertFill(metaObject, "updateBy", String.class, auditUser);
```
把 `updateFill` 中改为:
```java
        this.strictUpdateFill(metaObject, "updateTime", LocalDateTime.class, LocalDateTime.now());
        this.strictUpdateFill(metaObject, "updateBy", String.class, currentAuditUser());
```
(若 `updateFill` 原本没有 updateTime 填充,保留其原有 updateTime 行;只把 updateBy 改为 `currentAuditUser()`。)

- [ ] **Step 5: 运行测试,确认通过**

Run: `JAVA_HOME=~/.sdkman/candidates/java/17.0.7-zulu mvn -pl community-common test -Dtest=MybatisPlusConfigTest`
Expected: `Tests run: 2, Failures: 0, Errors: 0`。

- [ ] **Step 6: 提交**

```bash
git add community-common/src/main/java/com/measure/community/common/constant/CommonConstant.java community-common/src/main/java/com/measure/community/common/config/MybatisPlusConfig.java community-common/src/test/java/com/measure/community/common/config/MybatisPlusConfigTest.java
git commit -m "feat(common): 审计人无登录用户时填 system 占位"
```

---

## Task 8: 回改 info 样板(service 返回领域数据 + 抛 BizException + controller 校验)

**Files:**
- Modify: `community-info/src/main/java/com/measure/community/info/service/PopulationService.java`
- Modify: `community-info/src/main/java/com/measure/community/info/service/impl/PopulationServiceImpl.java`
- Modify: `community-info/src/main/java/com/measure/community/info/controller/PopulationController.java`
- Modify: `community-info/src/test/java/com/measure/community/info/controller/PopulationControllerTest.java`

**Interfaces:**
- Consumes: `BizException`、`SystemStatus`(Task 1-2)、生成 DTO `PopulationCreateReqDto/PopulationPageDto`(上一轮已生成)、`RetObj`。
- Produces:
  - `PopulationService.pagePersons(PopulationQueryReq): PopulationPageDto`
  - `PopulationService.createPerson(PopulationCreateReqDto): Long`
  - controller GET/POST `/api/v1/population/persons` 返回 `RetObj`,POST body 带 `@Valid`。

- [ ] **Step 1: 改 PopulationService 接口(返回领域数据)**

覆盖 `community-info/src/main/java/com/measure/community/info/service/PopulationService.java`:
```java
package com.measure.community.info.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.measure.community.info.api.model.PopulationCreateReqDto;
import com.measure.community.info.api.model.PopulationPageDto;
import com.measure.community.info.model.entity.Population;
import com.measure.community.info.model.req.PopulationQueryReq;

public interface PopulationService extends IService<Population> {
    PopulationPageDto pagePersons(PopulationQueryReq req);
    Long createPerson(PopulationCreateReqDto req);
}
```

- [ ] **Step 2: 改 PopulationServiceImpl(返回数据 + 抛 BizException)**

在 `PopulationServiceImpl.java` 中:
- 方法签名 `public RetObj pagePersons(...)` 改为 `public PopulationPageDto pagePersons(...)`,末尾 `return RetObj.success(dto);` 改为 `return dto;`。
- 方法签名 `public RetObj createPerson(...)` 改为 `public Long createPerson(...)`;把两处错误返回改为抛异常,成功返回改为返回 id:
```java
    @Override
    public Long createPerson(PopulationCreateReqDto req) {
        if (!StringUtils.hasText(req.getIdCard())) {
            throw new com.measure.community.common.exception.BizException(
                    com.measure.community.common.enums.SystemStatus.BAD_REQUEST, "证件号不能为空");
        }
        String hmac = HmacUtil.blindIndex(req.getIdCard());
        long exists = this.count(new LambdaQueryWrapper<Population>()
                .eq(Population::getIdCardHmac, hmac));
        if (exists > 0) {
            throw new com.measure.community.common.exception.BizException(
                    com.measure.community.common.enums.SystemStatus.CONFLICT, "该证件号已存在");
        }
        Population p = new Population();
        p.setType(req.getType() == null ? null : req.getType().getValue());
        p.setName(req.getName());
        p.setIdCard(req.getIdCard());
        p.setIdCardHmac(hmac);
        p.setGender(req.getGender());
        p.setPhone(req.getPhone());
        p.setInsuredStatus(req.getInsuredStatus());
        p.setEmploymentStatus(req.getEmploymentStatus());
        p.setVersion(1);
        this.save(p);
        return p.getId();
    }
```
- 删除不再需要的 `import com.measure.community.common.model.RetObj;`(若 pagePersons 也不再用)。`toDto`/`maskIdCard`/`pagePersons` 其余逻辑保持不变,仅返回类型与末行调整。

- [ ] **Step 3: 改 PopulationController(RetObj 包装 + 校验)**

覆盖 `community-info/src/main/java/com/measure/community/info/controller/PopulationController.java`:
```java
package com.measure.community.info.controller;

import com.measure.community.common.model.RetObj;
import com.measure.community.info.api.model.PopulationCreateReqDto;
import com.measure.community.info.model.req.PopulationQueryReq;
import com.measure.community.info.service.PopulationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Tag(name = "人口信息(信息服务)")
@Validated
@RestController
@RequestMapping("/api/v1/population")
public class PopulationController {

    @Autowired
    private PopulationService populationService;

    @Operation(summary = "人口信息分页查询")
    @GetMapping("/persons")
    public RetObj listPersons(PopulationQueryReq req) {
        return RetObj.success(populationService.pagePersons(req));
    }

    @Operation(summary = "人口信息录入")
    @PostMapping("/persons")
    public RetObj createPerson(@Valid @RequestBody PopulationCreateReqDto req) {
        return RetObj.success(populationService.createPerson(req));
    }
}
```

- [ ] **Step 4: 改单测(适配新签名 + 加冲突用例)**

覆盖 `community-info/src/test/java/com/measure/community/info/controller/PopulationControllerTest.java`:
```java
package com.measure.community.info.controller;

import com.measure.community.common.enums.SystemStatus;
import com.measure.community.common.exception.BizException;
import com.measure.community.common.exception.GlobalExceptionHandler;
import com.measure.community.info.api.model.PopulationCreateReqDto;
import com.measure.community.info.api.model.PopulationPageDto;
import com.measure.community.info.model.req.PopulationQueryReq;
import com.measure.community.info.service.PopulationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class PopulationControllerTest {

    @Mock
    PopulationService populationService;

    @InjectMocks
    PopulationController populationController;

    MockMvc mockMvc;

    @BeforeEach
    void setup() {
        mockMvc = MockMvcBuilders.standaloneSetup(populationController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void listPersons_returnsOk() throws Exception {
        when(populationService.pagePersons(any(PopulationQueryReq.class)))
                .thenReturn(new PopulationPageDto());
        mockMvc.perform(get("/api/v1/population/persons").param("pageNo", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    void createPerson_returnsOk() throws Exception {
        when(populationService.createPerson(any(PopulationCreateReqDto.class)))
                .thenReturn(1L);
        mockMvc.perform(post("/api/v1/population/persons")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"张三\",\"idCard\":\"3301X\",\"type\":\"户籍\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(1));
    }

    @Test
    void createPerson_duplicate_returns409() throws Exception {
        when(populationService.createPerson(any(PopulationCreateReqDto.class)))
                .thenThrow(new BizException(SystemStatus.CONFLICT, "该证件号已存在"));
        mockMvc.perform(post("/api/v1/population/persons")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"张三\",\"idCard\":\"3301X\",\"type\":\"户籍\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(409))
                .andExpect(jsonPath("$.message").value("该证件号已存在"));
    }
}
```

- [ ] **Step 5: 编译 + 单测(含依赖模块)**

Run: `JAVA_HOME=~/.sdkman/candidates/java/17.0.7-zulu mvn -pl community-info -am test -Dtest=PopulationControllerTest`
Expected: `Tests run: 3, Failures: 0, Errors: 0`,BUILD SUCCESS。
若报找不到生成 DTO:先 `JAVA_HOME=~/.sdkman/candidates/java/17.0.7-zulu mvn -pl community-info -am generate-sources` 再重试。

- [ ] **Step 6: 提交**

```bash
git add community-info/src/main/java/com/measure/community/info/service community-info/src/main/java/com/measure/community/info/controller/PopulationController.java community-info/src/test/java/com/measure/community/info/controller/PopulationControllerTest.java
git commit -m "refactor(info): service 返回领域数据并抛 BizException,controller 加 @Valid 包装 RetObj"
```

---

## 最终验证(全部 Task 后)

- [ ] 全量编译 + 单测:
```bash
JAVA_HOME=~/.sdkman/candidates/java/17.0.7-zulu mvn -T 1C clean install
```
Expected: 8 模块 BUILD SUCCESS,所有单测通过。
- [ ] (可选)端到端复跑(需 Nacos/MySQL/Redis 起着,见 spec §验证):直连 info 绕过网关应返回 **HTTP 403 + RetObj**;录入重复证件号应返回 **HTTP 409**;无登录录入 DB `create_by = "system"`。

---

## Self-Review

**1. Spec coverage(对照 2026-07-17-common-boundary-layer-design):**
- ① 响应/状态码 → Task 1 ✅
- ② 业务异常 + 全局异常处理 → Task 2、Task 4 ✅
- ③ 过滤器 403 → Task 3(ResponseWriter)、Task 5 ✅
- ④ 用户信息贯穿/异步 + 审计 system → Task 6、Task 7 ✅(FeignConfig 不改,符合 spec)
- ⑤ 参数校验 → Task 8(controller @Valid/@Validated)✅
- 回改 info 样板 → Task 8 ✅

**2. Placeholder scan:** 无 TBD/TODO;每个代码步骤给出完整代码。Task 2/5/7 中对 `BizException`/`ResponseWriter`/`SystemStatus` 用全限定名以避免 import 顺序歧义。✅

**3. Type consistency:** `SystemStatus`(SUCCESS/BAD_REQUEST/UNAUTHORIZED/FORBIDDEN/NOT_FOUND/METHOD_NOT_ALLOWED/CONFLICT/INTERNAL_ERROR)在 Task 1 定义,后续 Task 2/4/5/8 一致引用;`RetObj.error(SystemStatus,String)`(Task 1)被 Task 4 `build()` 使用;`currentAuditUser()`(Task 7)命名一致;`pagePersons→PopulationPageDto`、`createPerson→Long`(Task 8)在接口/实现/controller/单测一致;`ContextTaskDecorator`(Task 6)在 ThreadPoolConfig 引用一致。✅
