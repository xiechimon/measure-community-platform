# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

数智化社区服务平台：Spring Cloud Alibaba(Nacos + Sentinel) 微服务，JDK 17 / Spring Boot 3.3.5 / Maven 多模块。
详细的模块清单、端口、环境准备与新增模块步骤见 `README.md`——本文件只补充跨文件才能看清的架构与约定。

## 构建 / 运行 / 测试

```bash
# 全量编译（父 pom 聚合，跳过测试）
mvn -T 1C clean install -DskipTests

# 只编译单个模块（需先 install community-common，其它模块依赖它）
mvn -pl community-common install -DskipTests
mvn -pl community-info -am spring-boot:run        # -am 连带构建依赖模块

# 全部单测 / 单个模块 / 单个测试类或方法
mvn test
mvn -pl community-info test
mvn -pl community-info test -Dtest=PopulationControllerTest
mvn -pl community-info test -Dtest=PopulationControllerTest#createPerson_returnsOk
```

- 单测用 **Mockito + MockMvc standaloneSetup**（见 `PopulationControllerTest`）——不加载 Spring 上下文、不连 Nacos/DB，可离线跑。新写的 controller 测试沿用这个模式，不要用 `@SpringBootTest`。
- 依赖走阿里云镜像：父 pom `repositories` 覆盖普通依赖;**插件及其 provider（如 surefire-junit-platform）走 `~/.m2/settings.xml` 里 `mirrorOf=*` 的阿里云镜像**（境外中央仓在本网络不稳，缺此镜像会在 `mvn test` 阶段拉不到 surefire provider）。
- 构建须在 **WSL 内用原生路径**（`/home/...`）跑,Windows 版 Maven 对 `\\wsl.localhost\` UNC 路径解析父 POM 有 bug。

### Wave 0 四级门禁命令（unit / integration / e2e / perf）
完整的启动与验证权威路径见 [`docs/operations/wave0-runbook.md`](docs/operations/wave0-runbook.md)；以下是分层执行入口，一条 `bash scripts/ci/verify.sh` 会按顺序全部跑一遍：
```bash
# unit：全离线，不连 MySQL/Redis/Nacos
mvn test

# integration：真实 Testcontainers MySQL 8（DatabaseMigrationIT/SensitivePersistenceIT）+ compose/Nacos 契约脚本
mvn -pl community-integration-tests -am -Pintegration verify
bash scripts/tests/compose-contract-test.sh
bash scripts/tests/nacos-bootstrap-it.sh

# e2e（system）：起依赖栈 + Flyway migrate + Nacos bootstrap + app profile 后跑真实链路冒烟
bash scripts/e2e/wave0-smoke.sh          # 需已有一套运行中的栈（COMPOSE_PROJECT_NAME 等变量）
ENV_FILE=.env.example bash scripts/e2e/wave0-smoke.sh --setup   # 一次性起栈 + 验证 + 清理

# perf（capacity）：容器化 k6，20 VU/2m，阈值 http_req_failed<1%、p95<1000ms
docker run --rm --network "${COMPOSE_PROJECT_NAME:-measure-community}_default" \
  -e BASE_URL=http://community-gateway:8080 -e ADMIN_ACCOUNT=admin -e ADMIN_PASSWORD=123456 \
  -i grafana/k6:0.52.0 run - < scripts/perf/wave0.js

# 全部四级门禁一次跑完，最终打印 Unit/Integration/System/Capacity/Wave 0 verification 五行 PASS
bash scripts/ci/verify.sh
```
- **Red/Green 证据规则**：新增或修改行为时必须先写测试并**确认因目标行为缺失而失败**（Red，通常是编译失败或断言失败），再写最小实现使其转绿（Green），禁止跳过“先见到失败”这一步直接补实现——这是本仓库 Wave 0 门禁（Tasks 1–12）实际遵循并验证过的流程，新提交延续同一约定。

### 本地起服务的两种 profile
每个业务模块有两份配置，关键区别是**业务配置从哪来**：
- `application.yml`（`profiles.active: dev`）：只 bootstrap Nacos，端口/数据源/网关路由等**全部从 Nacos 拉取**（`spring.config.import: nacos:...`）。需要先把 `doc/*.yaml` 导入 Nacos 命名空间 `74193cd9-fac4-4f2a-addc-47c60508b15c`。
- `application-local.yml`：内嵌数据源与端口，用 `--spring.profiles.active=local` 即可**脱离部分 Nacos 配置**独立启动（仍需 Nacos 做注册发现）。
- 覆盖项走环境变量：`NACOS_SERVER_ADDR`、`NACOS_USERNAME`、`NACOS_PWD`、`DB_HOST`(MySQL 主机)。
- 最小验证链路：`community-gateway`(9090) → `community-info`(9094)。

## 架构要点（跨文件才能看清的部分）

### community-info 是唯一的"样板模块"，其余是空壳
`community-portal/service/welfare/affairs` 目前只有一个 `/api/v1/{域}/ping` 占位 controller。真正的分层实现（`controller / service / service.impl / mapper / model.entity / model.req / support`）只有 `community-info` 有。**新增业务一律照 community-info 抄**，步骤见 README「如何新增业务模块」。

### community-common 靠 @ComponentScan 注入，不是 auto-config
common 里的 bean（`RetObj`、`GlobalExceptionHandler`、`RequestHeaderFilter`、`MybatisPlusConfig`、各 `config/*`）**没有 spring.factories 自动装配**。每个业务模块的启动类必须显式声明才能生效：
```java
@ComponentScan(basePackages = {"com.measure.community.common", "com.measure.community.<模块>"})
```
漏掉 `com.measure.community.common` 会导致统一响应、鉴权过滤器、审计字段填充等全部失效。

### 网关↔服务的内部鉴权握手（跨 gateway + common）
这是最容易踩坑的横切逻辑，分布在两个模块：
1. **网关** `AuthFilter`(order=-100)：只有 `/api/v1/auth/login` 是业务 API 白名单；健康检查和 API 文档也被单独放行。其余业务请求校验 Redis 中 `alibaba-token:<token>`，再向下游注入 `X-Internal-Auth`、`X-UserInfo`(用户 JSON 的 Base64) 和 `traceId`。
2. **各业务服务** `RequestHeaderFilter`(common)：校验 `X-Internal-Auth` 与配置的 `security.internal.secret` 相同，不匹配直接 403（**禁止绕过网关直连**）；再把 `X-UserInfo` 解码进 `UserContextHolder`(ThreadLocal) 和 MDC。
- 内部密钥通过 Spring 属性 `security.internal.secret` 注入；容器环境变量为 `SECURITY_INTERNAL_SECRET`。网关与所有下游服务必须配置为同一个值，不能在代码或文档中硬编码示例密钥。
- `UserContextHolder` 是 ThreadLocal，`RequestHeaderFilter` 在 finally 里 clear，新增手动开线程/异步时要自行传递。

### 敏感字段：AES 密文列 + HMAC 盲索引
人口证件号等敏感字段的存储模式（见 `Population` 实体、`AesTypeHandler`、`HmacUtil`、schema 注释「§5」）：
- 明文列用 `@TableField(typeHandler = AesTypeHandler.class)` 落库为 AES-256-GCM 密文（随机 12 字节 IV 与密文拼接后 Base64）；读取时由 `SensitiveCrypto` 解密。AES 密钥通过 `sensitive.aes-key` 注入，未配置时仅为离线测试回退开发密钥，生产必须配置。
- 密文列无法 `LIKE`/等值查询，因此额外存一个 `id_card_hmac`(HMAC-SHA256 盲索引) 列做唯一约束与精确匹配。查询证件号走 `HmacUtil.blindIndex(...)` 等值匹配，不要对密文列做 wrapper 条件；HMAC 密钥通过 `sensitive.hmac-key` 注入。后续接 KMS 时替换 `SensitiveCrypto.configure(...)` 的密钥来源，不改 TypeHandler 或业务调用方。

### 数据访问约定
- MyBatis-Plus，Service 继承 `ServiceImpl<Mapper, Entity>`，Mapper 继承 `BaseMapper`。
- 分页依赖 `MybatisPlusConfig` 里注册的 `PaginationInnerInterceptor(MYSQL)`——不注册则 `page()` 退化为全表查、`total` 为空。
- 审计字段 `createTime/updateTime/createBy/updateBy` 由 `MybatisPlusConfig`(实现 `MetaObjectHandler`) 自动填充，`createBy/updateBy` 取自 `UserContextHolder.getUserId()`。实体对应字段要标 `@TableField(fill = ...)`。
- 多数据源用 baomidou `dynamic-datasource`，`primary: master`。
- 建表走 Flyway 版本化迁移 `database/mysql/migration/V1__population_schema.sql`、`V2__rbac_schema.sql`，库名 `measure_community`（不再用旧的 `01-init-schema.sql` 单文件初始化）。

### 统一响应与异常
所有 controller 返回 `RetObj<T>`（`{code, message, data}`，`RetObj.success()/error()`）。异常由 common 的 `GlobalExceptionHandler` 兜底，`SystemStatus` 枚举定义状态码。

## 约定
- groupId `com.measure`；模块目录/artifactId 前缀 `community-`；包根 `com.measure.community.{模块}`；启动类 `Community{Module}Application`；`spring.application.name = community-{module}`。
- 新增模块后：父 `pom.xml` 的 `<modules>` 加一行 + 网关 `doc/community-gateway-dev.yaml` 加路由（`lb://community-xxx`，`Path=/api/v1/xxx/**`）+ Nacos 建 `community-xxx-dev.yaml`。
- 依赖版本统一在**父 pom 的 `<properties>` + `dependencyManagement`** 管理，子模块不写版本号。
- `community-auth` 由脚手架 `cloud-user` 改名而来；CI(Jenkinsfile) 按 Job 名（`community-` 前缀,如 `community-gateway`/`community-info`）识别服务并构建 Docker 镜像部署，`case` 块含各服务端口(gateway 9090 / auth 9093 / info 9094 / 空壳 9095-9098)。
- 设计文档：`docs/详细功能设计说明书...docx`、`docs/开发计划7.13(1).xlsx`（阶段范围以此为准，不含三级驾驶舱）、`docs/superpowers/` 下有脚手架 spec/plan。
