# 数智化社区服务平台 · 路线图

> 本文件是**权威路线图**(进 git、团队可见)。设计细节见 `docs/superpowers/specs/`,执行细节见 git 历史。
> 阶段划分按依赖排序:先横切地基,后业务铺开。最近更新:2026-07-17。

## 范围
一期后端。**排除 AI 侧与三级驾驶舱**(经确认)。功能清单以 `docs/开发计划7.13(1).xlsx`「阶段1」为准,接口/字段/权限依据 `docs/详细功能设计说明书 V7`(§4.1.2 接口、§5 安全、§6 权限、§7.1 接口规范)。后端能力的归属、来源追踪与验收规则以[正式后端需求基线](requirements/backend-requirements.md)为准；它优先于早期骨架文档中关于一期外能力的限定。

## 阶段进度

| 阶段 | 内容 | 状态 |
|---|---|---|
| **P0** 基础设施 + 响应地基 | gateway/auth/common 骨架、OpenAPI 契约先行、common 边界层;统一响应 `{code,message,data,traceId}` + 分段错误码(1/2/5xxxx)+ 分页 `page/size`(§7.1);auth 归一到 `/api/v1/auth` | ✅ 完成 |
| **P1** 黄金样板(community-info) | 人口版本更新 `POST /persons/{id}/versions` + 历史查询 `GET .../versions`(乐观锁 `@Version` + `t_population_his` 快照,仅追加不可删/改);`SensitiveCrypto` 真 AES-256-GCM/HMAC-SHA256(密钥外置,KMS 留位);`DesensitizeUtil` 脱敏集中 | ✅ 完成 |
| **P2a** RBAC 基础 + 功能级鉴权 + 认证加固 | `sys_*` 建表+种子(`database/mysql/02-rbac-schema.sql`);登录改 `SysUser`+BCrypt+加载角色/权限;JWT 密钥/过期外置;`LoginUser` 带 roles/permissions 经 Redis→网关 `X-UserInfo`→`UserContextHolder`;common `@RequiresPermission`+AOP 切面 | ✅ 完成 |
| **P2b** 数据范围行级 + 字段级脱敏 | 业务表加 `org_id/grid_id` + MP `DataPermissionInterceptor` + `sys_role_data_scope` 行级过滤;字段级按 `sys_role_field` 序列化层脱敏;(附)Refresh Token、网关接口级权限路由表、角色管理 CRUD | ⏳ 下一步 |
| **P3** 业务域铺开 | 按 P1 样板复制:居务管理 → 社区服务 → 社区公益 → 信息服务其余(微网格/车辆/生态链/一档)→ 首页看板。当前 portal/service/welfare/affairs 仍是 ping 空壳 | ⬜ 未开始 |
| **P4** 移动端 API | 居民侧 / 物业侧 C 端接口 | ⬜ 未开始 |

**决策依据**:业务铺开前必须先有"权限感知 + 加解密就位"的可复制样板,否则每个模块都要回填鉴权与真加密。故 P1(样板)→ P2(权限)先行,再 P3 大规模复制。

## 已知债 / 风险
- **Wave 0 生产门禁已落地并真实跑通**:`bash scripts/ci/verify.sh` 在干净主机上完整执行 Unit → Integration → System → Capacity 四级门禁并打印 `Wave 0 verification: PASS`。真实覆盖:`mvn test`(离线单测)；`community-integration-tests` 的 `DatabaseMigrationIT`/`SensitivePersistenceIT` 连真实 MySQL 8(Testcontainers)验证 Flyway 版本化迁移(`V1__population_schema.sql`/`V2__rbac_schema.sql`)与证件号 AES 密文/HMAC 盲索引真实往返；`scripts/e2e/wave0-smoke.sh` 对已构建并启动的 gateway/auth/info 容器跑 10 项真实链路断言(登录、鉴权 401/403、人口创建与脱敏查询、日志脱敏、traceId)，10/10 通过；`scripts/perf/wave0.js` 用容器化 k6(20 VU/2m)测得 p50=5.6ms、p95=18.98ms(阈值 p95<1000ms)、`http_req_failed`=0.00%、132,445 次迭代/264,893 次 check 全部通过，基线记录在 `docs/operations/wave0-capacity-baseline.md`。这只是 Wave 0 的**最低可回归门禁基线**,不代表业务需求已完成验收(业务范围/完成度仍以本文件「阶段进度」和 `docs/requirements/backend-requirements.md` 为准);正式容量评审(多副本、真实数据量级、压测梯度)留给 Wave 5。运行/复现步骤见 `docs/operations/wave0-runbook.md`。
- ~~**本地 Flyway migrate 步骤硬编码 `127.0.0.1:3306`**~~ 已修复:`scripts/ci/verify.sh` 的 flyway 迁移端口改为跟随 `${MYSQL_HOST_PORT:-3306}`,与 compose/冒烟一致;设 `MYSQL_HOST_PORT` 覆盖即可与宿主机 native MySQL(3306)共存,不再需要先停 native MySQL。

## 参考
- 设计文档:`docs/详细功能设计说明书-数智化社区服务平台-V7 版本.docx`、`docs/开发计划7.13(1).xlsx`
- 设计说明(spec):`docs/superpowers/specs/`
- API 规范:`docs/api/standards.md`
