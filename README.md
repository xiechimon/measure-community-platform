# 数智化社区服务平台(measure-community-platform)

面向区/街镇/社区三级治理 + 居民 + 物业的一体化微服务平台。基于 Spring Cloud Alibaba(Nacos/Sentinel + 脚手架自带 Seata/RocketMQ)。

> 注:设计说明书 3.2 消息队列用 RabbitMQ/Kafka(未含 RocketMQ/Seata)。本骨架暂沿用脚手架自带的 RocketMQ/Seata 且不实现 MQ/分布式事务;后续落地时按说明书对齐。

## 模块

| 模块 | 说明 | 包根 | 端口 |
| --- | --- | --- | --- |
| community-common | 公共:统一响应、异常、MyBatis-Plus、Redis 等 | com.measure.community.common | - |
| community-gateway | API 网关、统一鉴权、路由 | com.measure.community.gateway | 9090 |
| community-auth | 系统配置:用户/角色/权限/日志、鉴权(JWT) | com.measure.community.auth | 9093 |
| community-info | 信息服务(人房业态,**样板模块**,连真实 MySQL) | com.measure.community.info | 9094 |
| community-portal | 首页(数据账本/待办/图表,**空壳**) | com.measure.community.portal | 9095 |
| community-service | 社区服务(康养/助幼/上门,**空壳**) | com.measure.community.service | 9096 |
| community-welfare | 社区公益(资助/助农/法援,**空壳**) | com.measure.community.welfare | 9097 |
| community-affairs | 居务管理(活动/工单/随手拍/场地/工具/资讯,**空壳**) | com.measure.community.affairs | 9098 |

> 范围以 `开发计划7.13(1).xlsx`「阶段1」为准(docx 作补充),**不含三级驾驶舱和所有 AI 能力**；后端需求边界和追踪规则以[后端需求基线](docs/requirements/backend-requirements.md)为准，优先于早期骨架文档的范围说明。空壳模块含 `/api/v1/{域}/ping` 占位,业务按 community-info 样板逐个填。
> 移动端(Taro/RN)前端为独立工程；AI 能力完全不在本仓库实现，也不创建或对接 FastAPI 服务。

## 环境准备
JDK 17+、Maven 3.8+、Nacos 2.x(必须)、MySQL 8、Redis。

## 自动化本地启动与冒烟验证

**权威的启动/验证路径见 [`docs/operations/wave0-runbook.md`](docs/operations/wave0-runbook.md)**：工具版本、`.env.example` → `.env`、依赖启动、Flyway migrate/validate、Nacos bootstrap、应用构建与 app profile、四/五级门禁(`bash scripts/ci/verify.sh`)、健康检查与日志脱敏、常见失败排查、清理和生产 Secrets 清单都在该文档里。**手工启动不需要**登录 Nacos 控制台创建命名空间或粘贴 YAML——`scripts/nacos/bootstrap.sh` 会幂等地创建命名空间 `74193cd9-fac4-4f2a-addc-47c60508b15c` 并导入 `doc/` 下全部配置。

`.env.example` 提供仅供本地使用的完整变量集。以下命令会创建一个唯一的 Compose 项目、让 Docker 分配所有宿主机端口、运行 Flyway 迁移、初始化 Nacos 命名空间并导入配置、构建并启动 gateway、auth 和 info 三个应用 profile，最后执行真实 10 项冒烟断言：

```bash
ENV_FILE=.env.example bash scripts/e2e/wave0-smoke.sh --setup
```

默认 `--setup` 是一次性完整验证：完成后只清理本次生成的 Compose 项目和项目卷。`KEEP_STACK=1` 仅用于本地调试；脚本会打印包含项目名和全部 Docker 动态端口的完整复用命令，复制该命令才能重跑断言。

跑完整的四/五级门禁(单测 + 集成 + 系统 E2E + 容量)用一条命令：

```bash
bash scripts/ci/verify.sh
```

默认 Nacos 镜像是原生多架构的 `nacos/nacos-server:v2.5.2-slim`。如需固定到组织镜像，可在 `.env` 或命令环境中设置 `NACOS_IMAGE`；该覆盖项不改变 bootstrap 的 v1 API 合约。

应用使用 `NACOS_SERVER_ADDR`、`NACOS_USERNAME`、`NACOS_PWD`、`DB_HOST` 等环境变量覆盖连接参数；生产环境必需的完整 secrets 清单见运行手册第 11 节。

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
