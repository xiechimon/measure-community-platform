# 数智化社区服务平台 · 路线图

> 本文件是**权威路线图**(进 git、团队可见)。设计细节见 `docs/superpowers/specs/`,执行细节见 git 历史。
> 阶段划分按依赖排序:先横切地基,后业务铺开。最近更新:2026-07-17。

## 范围
一期后端。**排除 AI 侧与三级驾驶舱**(经确认)。功能清单以 `docs/开发计划7.13(1).xlsx`「阶段1」为准,接口/字段/权限依据 `docs/详细功能设计说明书 V7`(§4.1.2 接口、§5 安全、§6 权限、§7.1 接口规范)。

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
- **尚无端到端真跑**:全程仅单测+编译,MySQL/Nacos/Redis 未连过;版本历史落库、证件号密文往返、RBAC 登录链路、`@RequiresPermission` 真实 AOP 拦截**均未真实执行**。建议尽早用 docker-compose 起依赖做一次端到端验证。
- **种子 admin 的 BCrypt 是占位哈希**,需用应用侧 `BCryptPasswordEncoder` 重算 `123456` 后再导入,否则登录不了。
- **内部密钥 `expected-secret` 硬编码两处**(`AuthFilter` / `CommonConstant`),待外置。
- **两库未对齐**:auth 本地数据源已改指 `measure_community`,Nacos 的 `community-auth-dev.yml` 需同步。
- **样板未受权限保护**:`/api/v1/population` 在网关白名单免登录,撤白名单后需补 `population:*` 权限。

## 参考
- 设计文档:`docs/详细功能设计说明书-数智化社区服务平台-V7 版本.docx`、`docs/开发计划7.13(1).xlsx`
- 设计说明(spec):`docs/superpowers/specs/`
- API 规范:`docs/api/standards.md`
