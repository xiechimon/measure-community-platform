# 根目录聚合 OpenAPI 快照设计

## 目标

在仓库根目录生成一个自包含的 `openapi.yaml`，供 Apifox 一次导入六个业务模块的 API。该文件是导入快照，不替代各模块已有的 OpenAPI 契约。

## 来源与范围

汇总以下六个源文件：

- `community-info/src/main/resources/openapi/openapi.yaml`
- `community-auth/src/main/resources/openapi/openapi.yaml`
- `community-portal/src/main/resources/openapi/openapi.yaml`
- `community-service/src/main/resources/openapi/openapi.yaml`
- `community-welfare/src/main/resources/openapi/openapi.yaml`
- `community-affairs/src/main/resources/openapi/openapi.yaml`

不包含 `community-gateway` 自身的管理端点，也不修改六个源文件。汇总结果包含 7 个唯一路径和 9 个接口操作。

## 文件结构

根目录 `openapi.yaml` 使用 OpenAPI 3.0.3，并采用统一元数据：

- 标题：`数智化社区服务平台 API`
- 版本：`v1`
- 服务地址：`http://localhost:9090`
- 描述：说明所有外部请求经网关访问，文件用于 Apifox 导入

`tags` 合并六个模块已有的业务标签；`paths` 复制六份契约中的全部路径和操作；`components.schemas` 收纳 `community-info` 的六个数据模型；`components.securitySchemes.bearerAuth` 只保留一份。

## 合并规则

1. 保留源契约的请求参数、请求体、响应和示例。
2. 汇总文件默认使用 Bearer JWT；登录接口显式设置 `security: []`，保持免鉴权。
3. 四个空壳模块重复使用的 `operationId: ping` 在汇总文件中分别改为 `portalPing`、`servicePing`、`welfarePing`、`affairsPing`。
4. 所有 `$ref` 都使用汇总文件内部的 `#/components/...` 引用，不使用跨文件引用。
5. 各模块源契约仍是模块级事实来源；源契约变化后，根文件需要重新合并。

## 验证

生成后执行以下验证：

1. YAML 能被解析。
2. `openapi` 值为 `3.0.3`。
3. 唯一路径数为 7，接口操作数为 9。
4. 所有 `operationId` 非空且唯一。
5. 所有本地 `$ref` 都能解析到实际组件。
6. 根文件不存在外部文件引用。
7. `git diff --check` 无空白错误。

Apifox 的最终验收方式是导入根目录 `openapi.yaml`，确认六组标签和全部 9 个接口操作可见。

## 非目标

- 不增加自动合并脚本或构建插件。
- 不调整现有接口实现、路由或鉴权逻辑。
- 不把 Swagger UI 聚合配置纳入本次变更。
- 不删除或重命名任何模块级 OpenAPI 文件。
