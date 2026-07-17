# API 设计规范(measure-community-platform)

契约先行:每个服务的 `src/main/resources/openapi/openapi.yaml` 为唯一事实来源。以下为全服务统一约定。

## 路径
- 前缀 `/api/v1/{模块域}/{资源}`,资源用名词复数。域名对齐设计说明书,如 `/api/v1/population/persons`。
- 不兼容变更升版本 `/api/v2`。

## 统一响应
所有接口返回 `RetObj`(依据设计说明书 §7.1):
```json
{ "code": 200, "message": "请求成功", "data": {}, "traceId": "xxxx" }
```
- 成功 `code=200`;失败 `code` 取分段业务码(见「错误码」),`message` 为原因,`data` 可空。
- `traceId` 为全链路追踪 ID,由 `RetObj` 构造时从 MDC 自动回填,报障时按其检索日志。
- OpenAPI 响应用该信封描述(`code`/`message`/`data`/`traceId`),`data` 引用具体 schema。

## 分页
- 入参:`page`(整数,≥1,默认 1)、`size`(整数,1–100,默认 10);排序/过滤用 `sort`/`filter`(§7.1)。
- 出参(MyBatis-Plus Page):`records`(数组)、`total`、`size`、`current`、`pages`。

## 鉴权与权限
- 全局安全方案 `bearerAuth`(HTTP Bearer, JWT)。
- 网关 `AuthFilter` 校验 Redis 中 token,注入 `X-Internal-Auth`/`X-UserInfo`;服务 `RequestHeaderFilter` 校验并解出用户到 `UserContextHolder`。
- 数据权限按角色 / `data_scope`(见说明书 §6),行级过滤由 MyBatis-Plus 插件追加。

## 错误码
- **分段(5 位,§7.1)**:`1xxxx 鉴权` / `2xxxx 业务` / `5xxxx 系统`。响应体 `code` 用分段业务码,HTTP status 另置(语义化)。
- 通用码集中在 `SystemStatus`(实现 `ErrorCode`):如 `UNAUTHORIZED=10001`、`FORBIDDEN=10002`、`BAD_REQUEST=20001`、`NOT_FOUND=20002`、`METHOD_NOT_ALLOWED=20003`、`CONFLICT=20004`、`INTERNAL_ERROR=50000`。
- 模块专属业务码:各模块自建枚举实现 `ErrorCode`(扩展 2xxxx),`BizException` 携带之;不要堆进 `SystemStatus`。
- 每个接口在 OpenAPI 的 `responses` 里列出可能的错误场景。

## 字段格式
- 时间:ISO-8601 `yyyy-MM-dd'T'HH:mm:ss`(Java `LocalDateTime`)。
- 金额:`BigDecimal`,单位元,保留 2 位。
- 敏感字段(证件号/手机号等):落库 AES 密文列 + HMAC 盲索引列(唯一/等值),展示脱敏(见说明书 §5)。

## 审计与幂等
- `createTime/updateTime/createBy/updateBy` 由 `MybatisPlusConfig`(MetaObjectHandler)自动填充。
- 创建接口按业务唯一键(如证件号盲索引)幂等去重,重复返回明确错误。

## 版本与生成
- 契约 `openapi.yaml` 入库;`openapi-generator` 仅生成 DTO 到 `target/generated-sources`(不入库)。
- 运行时 Springdoc 暴露 `/v3/api-docs` + Swagger UI;Apifox 导入 `openapi.yaml` 做评审/Mock/联调。
