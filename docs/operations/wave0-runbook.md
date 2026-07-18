# Wave 0 运行手册

> 本文档是**唯一**的本地/CI 启动与验证权威路径。README 的"自动化本地启动"一节和
> `docs/ROADMAP.md` 的门禁状态都引用本文档；三处应保持一致，若有出入以本文档为准。
> 关联文档：`docs/operations/wave0-capacity-baseline.md`(容量基线数据)、
> `docs/requirements/backend-requirements.md`(业务范围基线)。

## 1. 工具版本

| 工具 | 版本 / 要求 |
| --- | --- |
| JDK | 17(本机验证用 `17.0.7-zulu`；`pom.xml` `<java.version>17</java.version>`，用更高版本 JDK 编译会失败) |
| Maven | 3.8+，且 `~/.m2/settings.xml` 必须配置阿里云镜像(`mirrorOf=*` → `https://maven.aliyun.com/repository/public`)，否则 surefire/failsafe provider 在本网络下拉不到 |
| Docker / Docker Compose | 需支持 `docker compose`(v2 CLI 语法，`--profile`、`--wait`) |
| k6 | 容器化运行，镜像固定 `grafana/k6:0.52.0`，不需要本机安装 k6 二进制 |
| curl / jq | `scripts/nacos/bootstrap.sh`、`scripts/e2e/wave0-smoke.sh` 等脚本的运行时依赖，脚本会在开头用 `command -v` 检查 |

## 2. 从 `.env.example` 创建 `.env`

```bash
cp .env.example .env
```

`.env.example` 里的值(`local-only-*` 前缀、示例 AES/HMAC key 等)**只适用于本地/CI 一次性依赖栈**，不是生产密钥。复制后按需覆盖：

- `COMPOSE_PROJECT_NAME`：不设置时 compose 项目名固定为 `measure-community`(见 `docker-compose.yml` 顶部 `name:`)；如果宿主机上已有另一份同名依赖栈在跑，设置一个不同的项目名可避免容器/网络冲突。
- `*_HOST_PORT`(`MYSQL_HOST_PORT`、`REDIS_HOST_PORT`、`NACOS_HOST_PORT`、`NACOS_GRPC_HOST_PORT`、`GATEWAY_HOST_PORT`、`AUTH_HOST_PORT`、`INFO_HOST_PORT`)：默认分别为 `3306`/`6379`/`8848`/`9848`/`9090`/`9093`/`9094`；宿主机上默认端口已被占用(例如本机已有原生 MySQL 监听 3306)时，覆盖为空闲端口。

`.env` 已在 `.gitignore` 中排除，**绝不能提交**。

## 3. 依赖启动

```bash
set -a; source .env; set +a
docker compose up -d --wait mysql redis nacos
```

`--wait` 会阻塞直到三个依赖服务的 healthcheck 全部通过(MySQL `mysqladmin ping`、Redis `redis-cli ping`、Nacos `/nacos/v1/console/health/readiness` + gRPC 端口探测)。

## 4. Flyway migrate / validate

```bash
mvn -N flyway:migrate \
  -Dflyway.url="jdbc:mysql://127.0.0.1:${MYSQL_HOST_PORT:-3306}/measure_community" \
  -Dflyway.user=root -Dflyway.password="$MYSQL_ROOT_PASSWORD"
```

迁移脚本位于 `database/mysql/migration/`(`V1__population_schema.sql`、`V2__rbac_schema.sql`)，由父 `pom.xml` 的 Flyway Maven plugin 驱动。迁移端口**跟随 `.env` 的 `MYSQL_HOST_PORT` 覆盖**(默认 3306)，与 compose 和冒烟脚本一致：本机原生 MySQL 已占用 3306 时，把 `MYSQL_HOST_PORT` 覆盖成空闲端口即可与其共存，flyway 会连到 compose 容器而非 native 库(不再需要先停 native MySQL)。

如需在不修改的前提下核对迁移状态，可将 `flyway:migrate` 换成 `flyway:info` 或 `flyway:validate`(同样的 `-Dflyway.url/user/password` 参数)。

## 5. Nacos bootstrap

```bash
bash scripts/nacos/bootstrap.sh
```

脚本行为(幂等，可重复执行)：
1. 轮询 `NACOS_URL`(默认 `http://127.0.0.1:8848`)的 readiness 接口直到就绪。
2. 用 `.env`(或 `NACOS_ENV_FILE` 指定的文件)里的 `NACOS_USERNAME`/`NACOS_PASSWORD` 登录；首次启动会先做管理员初始化。
3. 创建固定命名空间 `74193cd9-fac4-4f2a-addc-47c60508b15c`(已存在视为成功，不报错)。
4. 发布 `doc/*.yaml` 到该命名空间(`common-config.yaml`、`redis-common.yaml`、`community-gateway-dev.yaml`、`community-auth-dev.yml`、`community-info-dev.yaml` 等)。

**不需要**手工登录 Nacos 控制台创建命名空间或粘贴 YAML 内容——这一步完全由脚本自动完成，这也是本手册相对旧文档的关键简化。

## 6. 应用构建 + app profile

```bash
mvn package -DskipTests
docker compose --profile app up -d --build --wait
```

`--profile app` 会额外构建并启动 `community-gateway`(9090)、`community-auth`(9093)、`community-info`(9094)三个容器，走 `SPRING_PROFILES_ACTIVE=dev`，从 Nacos 拉取业务配置，连接 compose 内的 `mysql`/`redis`/`nacos` 服务名。`--wait` 会等待这三个容器的 `/actuator/health/readiness` 变为 `UP`。

## 7. 四/五级门禁

单条命令跑完全部门禁(依次执行第 3～6 节的等价步骤，外加单元/集成/容量测试)：

```bash
bash scripts/ci/verify.sh
```

`verify.sh` 会先校验必需的环境变量(`MYSQL_ROOT_PASSWORD`、`REDIS_PASSWORD`、`SECURITY_INTERNAL_SECRET`、`JWT_SECRET`、`SENSITIVE_AES_KEY`、`SENSITIVE_HMAC_KEY`、`NACOS_AUTH_TOKEN`、`NACOS_AUTH_IDENTITY_KEY`、`NACOS_AUTH_IDENTITY_VALUE`)都已设置(本地从 `.env` 加载，CI 用 credentials 注入)，然后依次执行：

1. **Unit gate**：`mvn test`(全离线，不连 MySQL/Redis/Nacos)。
2. **Integration gate**：`mvn -pl community-integration-tests -am -Pintegration verify`(真实 Testcontainers MySQL 8，`DatabaseMigrationIT`/`SensitivePersistenceIT`)+ `scripts/tests/compose-contract-test.sh` + `scripts/tests/nacos-bootstrap-it.sh`。
3. **System gate**：起依赖栈 → Flyway migrate → Nacos bootstrap → `mvn package` → 起 app profile → `scripts/e2e/wave0-smoke.sh`(10 项真实链路断言)。
4. **Capacity gate**：容器化 k6(`grafana/k6:0.52.0`)对已启动的 gateway 跑 20 VU / 2 分钟固定负载(`scripts/perf/wave0.js`)，阈值 `http_req_failed rate<0.01` 且 `http_req_duration p(95)<1000`。

全部通过时的最终输出(这五行是本次 Wave 0 验收的证据契约，已在干净主机上真实跑通)：

```text
Unit gate: PASS
Integration gate: PASS
System gate: PASS
Capacity gate: PASS
Wave 0 verification: PASS
```

已记录的一次真实容量数据(细节见 `docs/operations/wave0-capacity-baseline.md`)：20 VU / 2 分钟，p50=5.60ms，p95=18.98ms(阈值 p95<1000ms，约 50× 余量)，`http_req_failed`=0.00%，132,445 次迭代、264,893 次 check 全部通过。

`verify.sh` 用 `trap cleanup EXIT` 在脚本退出时自动执行 `docker compose --profile app down -v`，无论成功失败都会清理本次起的应用栈和卷。

## 8. 健康 / 日志

- **就绪探针**：三个业务容器都在 `/actuator/health/readiness` 暴露 Spring Boot Actuator 的 `readinessState` probe(auth/info 额外聚合 `db`、`redis`；gateway 聚合 `redis`、`discoveryComposite`)。`docker compose --profile app up --wait` 和 `wave0-smoke.sh` 的第 1 步都依赖这个端点判断服务是否真正可用，而不是仅看容器进程存活。网关/下游过滤器只放行精确的 `/actuator/health` 与 `/actuator/health/**` 路径，`/actuator/env` 等其余 Actuator 端点仍然需要鉴权(网关侧 401，下游服务直连 403)。
- **脱敏日志**：`RequestHeaderFilter`(见 `community-common`)记录访问日志时对查询串调用 `LogSanitizer.sanitizeQuery(...)`，`password`/`token`/`secret`/`idcard`/`phone`(大小写不敏感)对应的值一律替换为 `***`，其余 key(如 `page`)原样保留，日志里同时保留 `traceId` 便于跟排查。`wave0-smoke.sh` 第 7 步会真实抓取 `community-info` 容器日志，断言其中既不出现完整身份证号/密码明文，又能通过 `idCard=***&password=***` 和 traceId 找到对应请求。

## 9. 常见失败

| 现象 | 原因 | 处理 |
| --- | --- | --- |
| `docker compose up` 时 MySQL/Redis/Nacos 端口绑定失败 | 宿主机默认端口(3306/6379/8848/9848/9090/9093/9094)已被其他进程占用 | 在 `.env` 里设置对应的 `*_HOST_PORT` 覆盖为空闲端口；`scripts/e2e/wave0-smoke.sh --setup` 默认让 Docker 动态分配端口(`*_HOST_PORT=0`)可以完全规避这个问题 |
| Flyway `migrate` 连接失败或连到了错误的库 | 本机原生 MySQL 占用 3306，且未覆盖 `MYSQL_HOST_PORT`(compose MySQL 因此起不来或与 native 争 3306) | flyway 端口现已跟随 `MYSQL_HOST_PORT`(`scripts/ci/verify.sh` 与第 4 节命令一致)：在 `.env` 里把 `MYSQL_HOST_PORT` 覆盖成空闲端口，compose MySQL 与 flyway 都用该端口，可与 native MySQL 共存；或先 `brew services stop mysql` 让 compose 独占 3306 |
| 多个开发者/多次运行互相冲突(容器名、网络名、卷冲突) | Compose 项目名默认固定为 `measure-community`(`docker-compose.yml` 的顶层 `name:`) | 设置 `COMPOSE_PROJECT_NAME` 为唯一值；`scripts/ci/verify.sh` 的 Capacity gate 会按 `${COMPOSE_PROJECT_NAME:-measure-community}_default` 拼网络名，覆盖项要保持一致，否则 k6 容器加入不了正确的 Docker 网络 |
| Nacos 容器反复重启或建库失败 | 单机 Derby 首次建库时如果堆配置不当会触发 Full GC 风暴，超过 30s 登录超时 | 保持 `docker-compose.yml` 里的 `JVM_XMS/XMX/XMN` 设置不变；不要清空 `mc-nacos-data-v2` 卷后又用过小的 JVM 参数重建 |
| `scripts/nacos/bootstrap.sh` 报 "missing Nacos environment file" | 显式设置了 `NACOS_ENV_FILE` 但指向的文件不存在 | 确认路径正确，或不设置该变量让脚本回退到 `$ROOT/.env` |
| 直连 `community-info`(跳过网关)所有请求返回 403 | 这是预期行为，不是故障 | 下游服务的 `RequestHeaderFilter` 强制校验 `X-Internal-Auth` 是否等于 `security.internal.secret`；业务请求必须经网关(9090)访问，不允许绕过网关直连业务端口 |

## 10. 安全清理

```bash
docker compose --profile app down -v
```

- `--profile app` 确保连同 gateway/auth/info 应用容器一起清理，不遗留半启动的应用栈。
- `-v` 删除本次 compose 项目创建的数据卷(`mc-mysql-data`、`mc-redis-data`、`mc-nacos-data-v2` 等)，避免残留敏感数据(测试身份证密文、种子密码哈希等)或状态漂移影响下一次干净验证。
- `scripts/ci/verify.sh` 已经用 `trap cleanup EXIT` 自动做这一步；手工调试(未跑 `verify.sh`)时需要自己执行，尤其是在切换 `.env`/`COMPOSE_PROJECT_NAME` 之前。
- 清理前**不要**假设卷内数据可以随意留存——任何在本地/CI 依赖栈里跑过的数据（包括冒烟测试写入的示例人口记录）都当作测试数据处理，不导出、不留存为"数据库 dump"。

## 11. 生产 Secrets 清单

以下变量在 `SPRING_PROFILES_ACTIVE=prod` 时**必须**显式配置；`JwtConfig`/`SensitiveCryptoInitializer` 等生产快速失败检查会在缺失时直接抛 `IllegalStateException`，阻止应用启动(dev/local profile 仍保留仅供离线测试的回退默认值，不代表生产可以省略)。

| 变量 | 用途 | 缺失后果(prod) |
| --- | --- | --- |
| `JWT_SECRET` | `community-auth` 签发/校验登录 JWT(`jwt.secret`) | 启动失败 |
| `SECURITY_INTERNAL_SECRET` | 网关↔业务服务内部鉴权握手(`security.internal.secret`)，网关注入 `X-Internal-Auth`，下游 `RequestHeaderFilter`/`FeignConfig` 校验 | 启动失败 |
| `SENSITIVE_AES_KEY` | 证件号等敏感字段 AES-256-GCM 加解密密钥(`sensitive.aes-key`) | 启动失败 |
| `SENSITIVE_HMAC_KEY` | 敏感字段盲索引 HMAC-SHA256 密钥(`sensitive.hmac-key`) | 启动失败 |
| `DB_HOST` / `DB_USERNAME` / `DB_PASSWORD` | MySQL 连接(`spring.datasource`，`dynamic-datasource` `master`) | 应用无法建立数据源，启动失败 |
| `NACOS_SERVER_ADDR` / `NACOS_USERNAME` / `NACOS_PWD` | 服务注册发现与配置中心连接 | 无法注册/拉取配置，启动失败 |
| `REDIS_HOST` / `REDIS_PASSWORD` | 会话 token(`alibaba-token:<token>`)存取 | 登录态失效或启动失败(取决于 Redis health 指示器配置) |

`NACOS_AUTH_TOKEN` / `NACOS_AUTH_IDENTITY_KEY` / `NACOS_AUTH_IDENTITY_VALUE` 是 Nacos **服务端**(容器)鉴权初始化用的值，只在自建/CI 的 Nacos 依赖栈里需要；连接已有生产 Nacos 集群时由该集群自身的鉴权配置负责，不需要客户端应用重复配置。

生产环境的这些值必须来自独立的 secrets 管理(CI Credentials、KMS 等)，绝不写入代码、`doc/*.yaml`、Nacos 配置内容或本仓库任何文件——仓库里出现的都只是 `${VAR}` 占位符和仅供本地/CI 一次性使用的 `local-only-*` 示例值。

## 禁止事项

- **绝不提交 `.env`**(已在 `.gitignore`)。
- **绝不提交生产 secret 的真实值**——文档、代码、commit message 里只允许出现变量名/占位符，不允许出现真实密钥、密码或 token。
- **绝不提交数据库 dump**(含测试数据、种子数据的导出文件)；需要复现数据状态时用 Flyway 迁移 + 冒烟脚本重新生成，而不是保存并入库一份 dump。
