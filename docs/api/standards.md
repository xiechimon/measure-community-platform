# API 设计规范(measure-community-platform)

契约先行:每个服务的 `src/main/resources/openapi/openapi.yaml` 为唯一事实来源。以下为全服务统一约定。

## 路径
- 前缀 `/api/v1/{模块域}/{资源}`,资源用名词复数。域名对齐设计说明书,如 `/api/v1/population/persons`。
- 不兼容变更升版本 `/api/v2`。

## 统一响应
所有接口返回 `RetObj`:
```json
{ "code": 200, "message": "请求成功", "data": {} }
```
- 成功 `code=200`;失败 `code` 取 `SystemStatus` 枚举,`message` 为原因,`data` 可空。
- OpenAPI 响应用该信封描述(`code`/`message`/`data`),`data` 引用具体 schema。

## 分页
- 入参:`pageNo`(整数,≥1,默认 1)、`pageSize`(整数,1–100,默认 10)。
- 出参(MyBatis-Plus Page):`records`(数组)、`total`、`size`、`current`、`pages`。

## 鉴权与权限
- 全局安全方案 `bearerAuth`(HTTP Bearer, JWT)。
- 网关 `AuthFilter` 校验 Redis 中 token,注入 `X-Internal-Auth`/`X-UserInfo`;服务 `RequestHeaderFilter` 校验并解出用户到 `UserContextHolder`。
- 数据权限按角色 / `data_scope`(见说明书 §6),行级过滤由 MyBatis-Plus 插件追加。

## 错误码
- 集中在 `SystemStatus` 枚举;每个接口在 OpenAPI 的 `responses` 里列出可能的错误场景(如 400/403/500)。

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
