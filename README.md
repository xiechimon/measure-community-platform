# 数智化社区服务平台(measure-community-platform)

面向区/街镇/社区三级治理 + 居民 + 物业的一体化微服务平台。基于 Spring Cloud Alibaba(Nacos/Sentinel + 脚手架自带 Seata/RocketMQ)。

> 注:设计说明书 3.2 消息队列用 RabbitMQ/Kafka(未含 RocketMQ/Seata)。本骨架暂沿用脚手架自带的 RocketMQ/Seata 且不实现 MQ/分布式事务;后续落地时按说明书对齐。

## 模块

| 模块 | 说明 | 包根 | 端口 |
| --- | --- | --- | --- |
| community-common | 公共:统一响应、异常、MyBatis-Plus、Redis 等 | com.measure.community.common | - |
| community-gateway | API 网关、统一鉴权、路由 | com.measure.community.gateway | 9090 |
| community-auth | 用户/鉴权(JWT) | com.measure.community.auth | 9093 |
| community-info | 信息服务(人房业态,样板模块) | com.measure.community.info | 9094 |

> AI 智能体(FastAPI)为独立技术栈、独立仓库,经网关/内部 REST 对接,不在本工程。

## 环境准备
JDK 17+、Maven 3.8+、Nacos 2.x(必须)、MySQL 8、Redis。

## 启动步骤
1. **建库**:执行 `database/mysql/01-init-schema.sql`(库名 `measure_community`)。
2. **Nacos 命名空间**:新建命名空间,手填 ID `74193cd9-fac4-4f2a-addc-47c60508b15c`(免改代码)。
3. **导入 Nacos 配置**(命名空间下):`doc/` 内
   `common-config.yaml`、`redis-common.yaml`、`seata-common.yaml`、`rocketmq-common.yaml`、
   `community-gateway-dev.yaml`、`community-info-dev.yaml`。
4. **环境变量**(可选):`NACOS_SERVER_ADDR`、`NACOS_USERNAME`、`NACOS_PWD`、`SERVER_ADDRESS`(MySQL 主机)。
5. **启动**:`community-gateway` → `community-info`。到 Nacos 控制台确认注册成功。
   (`community-auth` 由脚手架 cloud-user 改名而来,如需启动须另在 Nacos 建 `community-auth-dev.yml` 及其数据源;本骨架验证只需 gateway + info。)
6. **验证**:
   - `POST http://<gateway>/api/v1/population/persons` body `{"type":"户籍","name":"张三","idCard":"3301X"}`
   - `GET  http://<gateway>/api/v1/population/persons?pageNo=1&pageSize=10`

## 如何新增业务模块(以 community-xxx 为例)
1. 复制 `community-info` 为 `community-xxx`,改 `pom.xml` 的 `<artifactId>`。
2. 包 `com.measure.community.info` → `com.measure.community.xxx`;主类 `CommunityXxxApplication`,`@ComponentScan` 含 `com.measure.community.common` 与本模块包。
3. `application.yml` 改 `spring.application.name: community-xxx`。
4. 父 `pom.xml` 的 `<modules>` 增加 `community-xxx`。
5. Nacos 建 `community-xxx-dev.yaml`;网关 `community-gateway-dev.yaml` 增加对应路由。
6. 按 `controller/service/service.impl/mapper/model` 分层开发。

## 命名规范
groupId `com.measure`;模块前缀 `community-`;包根 `com.measure.community.{模块}`;
主类 `Community{Module}Application`;`spring.application.name` = `community-{module}`。

License: Apache 2.0
