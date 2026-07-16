# 数智化社区服务平台 — 微服务骨架落地设计

- 日期: 2026-07-16
- 状态: 待用户评审
- 来源脚手架: `spring-cloud-alibaba-base-demo`
- 依据文档: 《详细功能设计说明书-数智化社区服务平台-V7 版本》、《开发计划7.13(1).xlsx》(一期范围)

## 1. 目标

把通用脚手架 `spring-cloud-alibaba-base-demo` 落地为「数智化社区服务平台」的正式微服务工程**骨架**:统一命名、清理 demo、保留核心模块,交付 1 个连真实 MySQL、可跑通的样板业务模块(community-info),并按 **`开发计划7.13(1).xlsx`「阶段1」功能域**(以 xlsx 为准,docx 作补充),把一期各后端域搭成**可编译可启动的空壳模块**。

**范围以 xlsx 一期为准,去除三级驾驶舱。** 一期后端骨架模块:

| xlsx 功能域 | 后端模块 | 本次交付 |
|---|---|---|
| 信息服务 | community-info | 样板(连真实 MySQL,GET/POST) |
| 系统配置(用户/角色/权限/日志) | community-auth | 复用脚手架 login+JWT |
| 首页(数据账本/待办/图表) | community-portal | 空壳 + ping |
| 社区服务(康养/助幼/上门) | community-service | 空壳 + ping |
| 社区公益(资助/助农/法援) | community-welfare | 空壳 + ping |
| 居务管理(活动/工单/随手拍/场地/工具/资讯) | community-affairs | 空壳 + ping(按 xlsx 归一域,后续可按 docx 微服务再拆) |

**空壳** = pom + 主类(`@ComponentScan` 含 common+自身)+ `application.yml`(Nacos 注册/配置)+ `/api/v1/{域}/ping` 占位控制器 + 父 POM 注册 + 网关路由;业务实体/接口后续按 community-info 样板填。

**不含**:三级驾驶舱、移动端前端(Taro/RN)、AI 智能体(FastAPI 独立仓);MQ/分布式事务/完整加密体系(见 §9)。

## 2. 命名规范(四处一致)

| 项 | 值 |
|---|---|
| groupId | `com.measure` |
| 根 artifactId | `measure-community-platform` (packaging=pom) |
| 模块前缀 | `community-` |
| 包根 | `com.measure.community.{模块}` |
| 主类 | `Community{Module}Application` |
| spring.application.name | `community-{module}` |

技术底座沿用脚手架,不变:Spring Boot 3.3.5 / Spring Cloud 2023.0.1 / Spring Cloud Alibaba 2023.0.1 / JDK 17 / Nacos / Sentinel / Seata / RocketMQ / MyBatis-Plus 3.5.8 / dynamic-datasource / JWT / Redis。

> ⚠️ 消息队列/分布式事务差异:说明书 3.2 用 **RabbitMQ/Kafka**,且未提及 RocketMQ 与 Seata;脚手架自带 RocketMQ + Seata。本次不实现 MQ 与分布式事务(YAGNI,§9),故不阻断;待后续真正落地异步/事务时,须按说明书改用 RabbitMQ/Kafka 并评估是否引入 Seata,同时替换 `doc/rocketmq-common.yaml`、`doc/seata-common.yaml`。

## 3. 工程结构

```
ideaProject/
 ├ spring-cloud-alibaba-base-demo/     原 demo,只读参考,不改动
 └ measure-community-platform/         新工程(全新 git init)
    ├ pom.xml                          父 POM,com.measure / measure-community-platform / pom
    ├ community-common/                com.measure.community.common   (由 cloud-common 改)
    ├ community-gateway/               com.measure.community.gateway  (由 cloud-gateway 改,9090)
    ├ community-auth/                  com.measure.community.auth     (系统配置域,由 cloud-user 改,9093)
    ├ community-info/                  com.measure.community.info     (信息服务,样板,连真实 MySQL,9094)
    ├ community-portal/                com.measure.community.portal   (首页,空壳,9095)
    ├ community-service/               com.measure.community.service  (社区服务,空壳,9096)
    ├ community-welfare/               com.measure.community.welfare  (社区公益,空壳,9097)
    ├ community-affairs/               com.measure.community.affairs  (居务管理,空壳,9098)
    ├ doc/                             Nacos 待导入配置(共/redis/seata + 各模块 -dev.yaml + 网关路由)
    ├ database/mysql/01-init-schema.sql  样板建表脚本
    ├ Dockerfile / Jenkinsfile         沿用并改名(含各模块 case/端口)
    └ README.md                        项目说明(含"如何新增业务模块")
```

空壳模块 = pom + 主类 + `application.yml`(Nacos)+ `application-local.yml`(数据源)+ `/api/v1/{域}/ping` 占位控制器 + 父 POM 注册 + 网关路由;暂无实体/DB 表,业务按 community-info 样板逐个填。

- **删除** demo 模块 `cloud-producer`、`cloud-consumer`,并从父 POM `<modules>` 移除。
- 每个业务模块主类保留 `@SpringBootApplication + @EnableDiscoveryClient + @ComponentScan(basePackages={"com.measure.community.common","com.measure.community.<模块>"})`。
- 每个业务模块依赖 `community-common`。

## 4. 样板业务模块 community-info(信息服务)

依据设计说明书 4.1.2「信息服务」(接口前缀 **`/api/v1/population`**)。

- **真实 MySQL 表**:
  - `t_population`(人口信息):id、type(户籍/常住/流动)、姓名、证件号(AES 密文列)、证件号 HMAC 盲索引列(唯一)、性别、参保/就业等状态字段、version、create/update 审计字段。
  - `t_population_his`(人口变更历史):追加式,记录每次版本变更。
  - 随工程附 `database/mysql/01-init-schema.sql` 建表脚本。
- **落地 2 个样板接口**(其余 10 个接口留作后续按此模式扩展):
  - `GET /api/v1/population/persons` — MyBatis-Plus 分页查询。说明书 4.1.2 的 `keyword` 因证件号已加密(密文无法 LIKE)拆为:`type` 等值 + `name` 模糊 + `idCard` 经 HMAC 盲索引等值精确匹配。
  - `POST /api/v1/population/persons` — 录入,证件号唯一性校验。
- **敏感字段**:按说明书第 5 章,证件号落库为 AES 密文(MyBatis TypeHandler 占位),另存一列 HMAC 盲索引(blind index)承担唯一约束与等值精确查询;本次 AES/HMAC 均为直通占位,不展开真实密钥/加解密体系(后续接 KMS)。
- 分层:`controller / service / service.impl / mapper / model(entity·req·vo)`,沿用 `cloud-user` 分层风格。
- 通过网关路由 `/api/v1/population/**` 访问,连真实 MySQL 跑通"增 + 查"。

## 5. Nacos 配置策略

- 沿用脚手架命名空间 ID `74193cd9-fac4-4f2a-addc-47c60508b15c`(免改代码;可后续更换)。
- `doc/` 下 4 个公共配置沿用:`common-config.yaml`、`redis-common.yaml`、`seata-common.yaml`、`rocketmq-common.yaml`。
- 每服务一份 `{app-name}-dev.yml`(如 `community-info-dev.yml`,含数据源)。
- README 列出需在 Nacos 建立的命名空间与配置清单。
- 支持环境变量覆盖:`NACOS_SERVER_ADDR` / `NACOS_USERNAME` / `NACOS_PWD`。

## 6. AI 智能体(FastAPI)— 不在本工程

Python/FastAPI 智能体为独立技术栈,单独建仓库,经网关 / 内部 REST 对接。本骨架仅在 README 标注对接位置,不含 AI 代码。

## 7. 文档交叉核实结论

1. 信息服务真实接口前缀为 `/api/v1/population`(说明书 4.1.2),已据此修正样板。
2. 样板表 `t_population` / `t_population_his` 与说明书 4.1.2 及 9.5 迁移脚本 `V1.0.1__add_population_his.sql` 一致。
2b. 说明书 §5(L2471)要求加密字段落库为 AES-256 密文、另建 HMAC 盲索引列做唯一/等值匹配;样板据此:`id_card` 密文列 + `id_card_hmac` 唯一盲索引列,判重/等值查询走盲索引,不对密文列建唯一键。
3. 开发计划(xlsx)为**一期范围**,是说明书 20 个后台模块的子集;信息服务在一期内,故适合做样板。
4. 系统管理真实表 `sys_user/sys_role/sys_permission/sys_grid` 等与 `community-auth`/后续 `community-system` 对应。

## 8. 验证标准(成功判据)

- `mvn -q -DskipTests package` 全模块编译通过。
- 执行 `01-init-schema.sql` 建表后,启动 Nacos + MySQL,启动 `community-gateway` 与 `community-info`:
  - 服务在 Nacos 注册成功;
  - `POST /api/v1/population/persons` 能写入 `t_population`;
  - `GET /api/v1/population/persons` 能分页读回;
  - 经网关 `/api/v1/population/**` 可访问。
- 无 Nacos/MySQL 时,启动失败信息应为连接类错误,而非代码/配置结构错误。

## 9. 不做的事(YAGNI)

- 一期各域已建**空壳**(portal/service/welfare/affairs),但**不实现其业务逻辑**(实体/接口),仅 `/ping` 占位;业务按 community-info 样板后续填。
- **不做三级驾驶舱**(xlsx 明确排除);不做移动端前端(Taro/RN);不做一期外的域(时间银行/商城/健康档案/安防/支付等)。
- 不实现完整 AES 加密/脱敏、权限矩阵、Seata 事务样例、RocketMQ 业务样例(依赖已保留,按需再加)。
- 不含 AI/FastAPI 代码(独立仓)。
- 不改动原 `spring-cloud-alibaba-base-demo` 目录。

## 10. 实施状态(2026-07-16)

**骨架已完成。** 8 个模块:

| 模块 | 交付 | 验证级别 |
|---|---|---|
| community-common / gateway / auth | 改名保留 | 编译✅;gateway 端到端✅ |
| community-info | 信息服务样板(t_population + 盲索引,GET/POST) | **端到端✅**(真实 Docker:Nacos+MySQL+Redis,增查/判重跑通) |
| community-portal / service / welfare / affairs | 空壳(ping) | 编译✅(未逐个真启动;结构与已验证的 info 同构) |

- 全部 8 模块 `mvn -DskipTests package` 通过;community-info 控制器单测 2/2 通过。
- 端到端验证顺带修复 2 个配置问题:common-config 补 `server.addr`;网关 dev 配 `server.port: 9090`。
- 环境:构建须在 **WSL 内**用原生路径(Windows Maven 对 `\\wsl.localhost` 路径有 bug);Maven 用阿里云镜像、Docker 用 daocloud 镜像(境外仓不通)。
