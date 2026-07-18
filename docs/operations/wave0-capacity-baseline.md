# Wave 0 容量基线

## 目的

`scripts/ci/verify.sh` 的第四级门禁（Capacity gate）用 k6 对已启动的 compose 栈
（`scripts/perf/wave0.js`）跑一轮固定负载，作为 Wave 0 阶段的**最低可回归容量基线**：
只要指标不劣于本文档记录的基线，就认为本次变更没有引入明显的性能回归。

**这不是正式的容量评审**——数据量、机型、并发模型都是本地/CI 单机 compose 的最小可行
配置，不代表生产容量规划结论。正式的容量评审（多副本、真实数据量级、压测梯度、容量
拐点分析）安排在 **Wave 5**，届时应替换或扩展本文档。

## 测试场景

- 工具：`grafana/k6:0.52.0`（容器化运行，通过 `--network` 加入 compose 网络访问
  `community-gateway:8080`）
- 脚本：`scripts/perf/wave0.js`
- 负载：20 VUs，持续 2 分钟（`vus: 20, duration: '2m'`）
- 请求组合（`setup()` 登录一次拿 token，`default` 函数每次迭代发起）：
  1. `POST /api/v1/auth/login`（仅 setup 阶段一次，获取 Bearer token）
  2. `GET /api/v1/auth/getUserName`
  3. `GET /api/v1/population/persons?page=1&size=10`
- 阈值（k6 `thresholds`，任一不满足则该轮 k6 进程非零退出，门禁 FAIL）：
  - `http_req_failed`: `rate < 0.01`（请求失败率低于 1%）
  - `http_req_duration`: `p(95) < 1000`（95 分位响应时间低于 1000ms）

## 门禁接入

`scripts/ci/verify.sh` 在 System gate 之后新增 Capacity gate：

```bash
echo "Capacity gate"
docker run --rm --network "${COMPOSE_PROJECT_NAME:-measure-community}_default" \
  -e BASE_URL=http://community-gateway:8080 \
  -e ADMIN_ACCOUNT=admin -e ADMIN_PASSWORD=123456 \
  -i grafana/k6:0.52.0 run - < scripts/perf/wave0.js
echo "Capacity gate: PASS"
```

网络名跟随 `COMPOSE_PROJECT_NAME`（本地 `.env` 覆盖为
`measure-community-verify`，未设置时的干净 CI 环境回退为 `measure-community`），
与 `scripts/e2e/wave0-smoke.sh` 的项目名约定一致，因为 `docker compose` 的默认
网络名固定为 `<project>_default`。

## 基线记录

> 下表数值取自一次真实的 `bash scripts/ci/verify.sh` Capacity gate 运行（干净
> 主机、`.env` 端口覆盖、native MySQL/Redis 停机后由 compose 提供），k6 输出与
> 主机信息如实回填，未做任何估算。

| 字段 | 值 |
| --- | --- |
| 记录日期 | 2026-07-18 |
| Git SHA | `1ee41d3`（基线测量时的 Task 12 提交；本表回填后 amend，代码内容不变） |
| 运行环境 CPU | Apple M2（8 核） |
| 运行环境内存 | 16 GB |
| Docker 版本 | 29.6.1 |
| 数据量（`t_population` 行数等） | 全新迁移库：V1/V2 迁移种子 + 冒烟新增 1 条常住人口；`population persons` 查询 `page=1&size=10` 命中小结果集 |
| 场景 | 20 VUs / 2m，`getUserName` + `population persons` 查询 |
| 吞吐 | 132,445 次迭代 / 264,891 次 HTTP 请求，≈1102 iter/s；264,893 次 check 全部通过 |
| p50 (`http_req_duration`) | 5.60 ms |
| p95 (`http_req_duration`) | 18.98 ms（阈值 `p(95)<1000`，✓ 通过，约 50× 余量） |
| p99 (`http_req_duration`) | k6 默认摘要未输出（仅 p90=13.8 ms / p95=18.98 ms，max=1.67 s）；如需 p99 用 `summaryTrendStats` 追加 |
| 失败率 (`http_req_failed`) | 0.00%（0 / 264,891，阈值 `rate<0.01`，✓ 通过） |
| 各应用容器最大 RSS | 本轮门禁未采样（k6 只测客户端侧；需 `docker stats`/`docker compose top` 采样，留 Wave 5 正式评审补） |

> p99 与容器 RSS 是本轮未采集项（k6 verbatim 脚本用默认摘要 p90/p95；RSS 需容器侧
> 采样）。二者不影响本次两个硬阈值门禁的通过判定，作为 Wave 5 正式容量评审的待补项。

## 后续回归判断

- 若某次改动后 Capacity gate 本身失败（k6 进程非零退出），说明已经跌破本文档定义
  的两个硬阈值（失败率 < 1%、p95 < 1000ms），门禁会直接拦截。
- 若门禁通过但相较本文档记录的基线数值出现明显劣化（例如 p95/p99 显著上升、RSS
  明显增长），应在 PR 中说明原因，并视情况更新本文档的基线记录。
- Wave 5 需要在此基础上补充：多副本/多节点场景、更接近生产的数据量级、压测梯度
  （逐步提升 VU 找容量拐点）、慢查询与资源热点分析，形成正式的容量评审结论。
