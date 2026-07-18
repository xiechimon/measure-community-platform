# Wave 1 第一片：RBAC 行级数据范围 + 角色感知字段脱敏 设计

> 日期：2026-07-18
> 状态：已确认
> 上级设计：`docs/superpowers/specs/2026-07-18-production-ready-tdd-delivery-design.md` §5.2 Wave 1（横切安全与公共能力 / P2b）
> 需求编号：`docs/requirements/backend-requirements.md` 的 **X-02**（RBAC、数据范围、字段级脱敏），在既有 **I-01**（人口样板）上验证

## 1. 背景与范围

Wave 0 已交付生产门禁与人口黄金样板，但业务数据边界（谁能看到哪些行、哪些字段返回明文）尚未强制。上级设计把「组织/网格行级数据范围、字段级脱敏」列为 Wave 1 P2b 的地基能力，所有业务域都会依赖。本 spec 只切其中**最小可验收的一片**（遵循上级设计 §7「限制在制品」）：

- **纳入**：按登录用户的数据范围对**人口查询**做行级过滤；按角色权限对**人口敏感字段**（idCard/phone）做明文/打码分支渲染；打通 org/grid/dataScope 从登录到 `UserContextHolder` 的传递链。
- **明确不纳入（留后续切片）**：组织/网格**多级层级树**及其展开（COMMUNITY/STREET/DISTRICT）、`CUSTOM` 自定义范围映射表、角色管理 CRUD、Refresh Token、除人口外其它业务表的数据范围、附件/导出/审计（X-03/X-04/X-06）。

## 2. 现状 ground truth（已核实）

已有但「声明未启用」：
- `sys_user.org_id` / `sys_user.grid_id`（裸 `BIGINT`，无层级表、无种子；`SysUser` 实体已镜像）。
- `sys_role.data_scope`（`VARCHAR(16)`，枚举 `SELF/GRID/COMMUNITY/STREET/DISTRICT/CUSTOM/ALL`，默认 `SELF`，代码从未读取）。
- 传递链：登录 `UserServiceImpl.login` 不拷 org/grid；`LoginUser` 无 org/grid/dataScope；`UserContextHolder` 是 `Map<String,String>` + roles/permissions，无 org/grid/dataScope getter；`RequestHeaderFilter` 会把 X-UserInfo 里的标量字段自动带进 map。
- `@RequiresPermission` + `RequiresPermissionAspect`：仅功能级（权限码 containsAll/anyMatch），无行级。
- `MybatisPlusConfig`：只注册乐观锁 + 分页两个 InnerInterceptor。
- 人口查询：`PopulationController` → `PopulationServiceImpl.pagePersons` 用 `LambdaQueryWrapper` + `this.page(...)`；`PopulationMapper` 是空 `BaseMapper`。
- 脱敏：`toDto` 恒调 `DesensitizeUtil.idCard/phone`（静态，不看角色）；idCard 列经 `AesTypeHandler` 密文落库、读取解密为明文后再打码；phone 为明文列。

缺口（本片要补）：组织/网格数据的**归属列**（`t_population` 只有 `create_by`）、**行级过滤机制**、org/grid/dataScope **传递**、**角色感知脱敏**、多网格**验收种子数据**。

## 3. 设计

### 3.1 数据范围模型与解析
- 本片只强制**扁平三档**：`ALL`（不过滤）、`GRID`（按 `grid_id` 相等）、`SELF`（按 `create_by` 是本人）。
- 用户可有多角色 → 有效 dataScope 取**最宽档**：`ALL > GRID > SELF`。
- **未实现档位**（`COMMUNITY/STREET/DISTRICT/CUSTOM`）→ fail-closed 回落到 `SELF`（最窄），不报错、不放行更宽范围。

### 3.2 传递链（community-auth + community-common）
- `UserServiceImpl.login`：把 `SysUser.orgId/gridId` 拷入 `LoginUser`；从用户所有角色的 `data_scope` 解析有效档（§3.1 最宽档），写入 `LoginUser.dataScope`。
- `LoginUser` 新增字段：`orgId(Long)`、`gridId(Long)`、`dataScope(String)`。序列化进 Redis 会话 JSON（gateway 原样透传，无需改 gateway）。
- `RequestHeaderFilter` 无需改动（标量字段已自动进 map）；`UserContextHolder` 新增 typed getter：`getOrgId():Long`、`getGridId():Long`、`getDataScope():String`（从 map 取值，缺失返回 null / 默认最窄 `SELF`）。

### 3.3 行级过滤机制（community-common）
- 在 `MybatisPlusConfig.mybatisPlusInterceptor()` 注册 `DataPermissionInterceptor`（**排在 `PaginationInnerInterceptor` 之前**）+ 自定义 `DataPermissionHandler`。
- Handler 逻辑：读 `UserContextHolder` 的 dataScope/gridId/userId，对被拦截 SQL 追加 WHERE：
  - `ALL` → 不追加；
  - `GRID` → `grid_id = <gridId>`（gridId 为 null 时 fail-closed：`grid_id IS NULL AND 1=0` 等价「查不到」，避免越权看全表）；
  - `SELF` → `create_by = <userId>`；
  - 未实现档位 → 回落 `SELF`。
- **作用域**：仅对 `t_population` 生效（表名白名单，可扩展）；`sys_*` 等其它表一律不追加，避免误伤登录/权限查询。
- **无用户上下文**（系统/内部/定时任务，`UserContextHolder` 为空）→ 不追加（视为系统调用）。外部请求由 `RequestHeaderFilter` 保证上下文并在 finally 清理。

### 3.4 角色感知字段脱敏（community-common + community-info）
- 新增权限码 `population:sensitive:view`（种子授予 admin 角色）。
- `PopulationServiceImpl.toDto`：`UserContextHolder.hasPermission("population:sensitive:view")` 为真 → idCard/phone 返回全值；否则 `DesensitizeUtil` 打码。AES 解密与现状一致（idCard 密文列读取即解密）。
- 数据库中 idCard 仍为密文落库、盲索引查询不变；本改动只影响**读取渲染**。

### 3.5 人口归属列、迁移 V3 与种子（community-info）
- `V3__population_data_scope.sql`：
  - `ALTER TABLE t_population ADD COLUMN org_id BIGINT NULL COMMENT '所属组织ID', ADD COLUMN grid_id BIGINT NULL COMMENT '所属网格ID'; CREATE INDEX idx_population_grid ON t_population(grid_id);`
  - 既有行 org_id/grid_id 回填 `NULL`（GRID 用户看不到 = 正确 fail-closed；admin ALL 全可见）。
  - 新增权限种子 `population:sensitive:view` 及其到 admin 角色的映射。
  - **验收种子（仅用户，不含人口行）**：插入一个 `data_scope='GRID'` 的角色（含 `population:query`/`population:create`，**不含** `population:sensitive:view`，以便断言打码），及 2 个绑定该角色、不同 `grid_id` 的普通用户。密码用与现有 admin 种子一致的 BCrypt 方式。
  - **不用 SQL 种子人口行**：`t_population.id_card` 是 AES 密文列（应用侧密钥 + 随机 IV）+ HMAC 盲索引，原始 SQL 无法产出有效密文/盲索引。验收所需人口行一律在**运行时经 service/API 由对应网格用户创建**（`create_by`/`grid_id` 由上下文自然写入，加密由 `AesTypeHandler` 自然发生）。
- `PopulationServiceImpl.createPerson`：写入时从 `UserContextHolder.getOrgId()/getGridId()` 填充 `org_id/grid_id`。`Population` 实体新增 `orgId/gridId` 字段（`@TableField`，参与审计填充无关）。

## 4. 关键决策（已确认默认）
| 决策 | 取值 | 理由 |
| --- | --- | --- |
| 启用档位 | 扁平 ALL/GRID/SELF | 最小片，无需层级树 |
| 未实现档位 | 回落 SELF（fail-closed） | 绝不放行更宽范围 |
| 无上下文 | 不过滤（系统调用） | 外部请求由 RequestHeaderFilter 保证上下文 |
| 多角色 | 取最宽档 | 符合 RBAC 直觉 |
| 解码权限码 | `population:sensitive:view` | 与现有 `population:*` 命名一致 |
| 拦截器作用域 | 仅 `t_population` 白名单 | 不误伤 sys_* |
| 旧行回填 | NULL | GRID 用户 fail-closed，admin 可见 |
| gridId 为 null 的 GRID 用户 | 查不到任何行 | 避免 `grid_id = null` 退化成看全表 |

## 5. 接口 / 契约变更
- 无新增 REST 端点；`GET /api/v1/population/persons` 行为按调用者数据范围/权限变化（同一 API，结果集与字段明文性因人而异）。
- 会话 JSON（Redis + X-UserInfo）新增 `orgId/gridId/dataScope`——向后兼容（旧字段不变，消费方按缺失走最窄默认）。
- 数据库：`t_population` 加两列 + 索引；新增一个权限种子。均由 Flyway V3 版本化迁移承载，走 Wave 0 的迁移门禁。

## 6. 测试与验收策略（TDD，先红后绿）
- **单元（离线）**：
  - `DataPermissionHandler`：ALL 不加条件；GRID 生成 `grid_id = ?`；SELF 生成 `create_by = ?`；未实现档位回落 SELF；无上下文不加条件；gridId=null 的 GRID 生成永假条件。
  - `toDto` 脱敏分支：有 `population:sensitive:view` 返回全值，无则打码。
  - `UserServiceImpl` dataScope 解析：多角色取最宽档。
  - `UserContextHolder` 新 getter。
- **集成（真实 MySQL 8，Testcontainers，扩 community-integration-tests）**：迁移 V3 后，直接设置 `UserContextHolder` 为网格 A / 网格 B 用户上下文，经 service 层各创建人口行（`grid_id` 自然写入），再切换上下文查询 `t_population`：GRID 用户只回本网格行；ALL 回全部；SELF 回自己创建行。无需 SQL 种子人口行。
- **E2E（扩 `scripts/e2e/wave0-smoke.sh` 或新增断言）**：网格 A 用户登录 → 创建本网格人口 → 查人口 → 断言只见本网格 + idCard/phone 打码；admin（ALL + 解码权）→ 断言可见全部（含网格 A 那条）+ idCard 明文。种子仅提供网格用户账号，人口行运行时经 API 创建。
- 全程走 Wave 0 四级门禁（unit/integration/system/capacity），不新引入离线 Mock 替代真实链路。

## 7. 非目标 / 后续切片
- 组织/网格多级层级树与 COMMUNITY/STREET/DISTRICT 展开、CUSTOM 映射表。
- 角色管理 CRUD、网关接口权限细化、Refresh Token（同属 Wave 1 P2b，另切）。
- 除人口外其它业务表的数据范围（拦截器白名单后续逐表接入）。
- X-03 隐私主体权利、X-04 统一审计、X-06 附件/导出策略。

## 8. 预计触及文件
- community-auth：`model/vo/LoginUser.java`、`service/impl/UserServiceImpl.java`、（可能）`SysUserMapper` 角色 data_scope 查询。
- community-common：`utils/UserContextHolder.java`、`config/MybatisPlusConfig.java`、新增 `config/DataPermissionHandler.java`（或 `support/`）。
- community-info：`model/entity/Population.java`、`service/impl/PopulationServiceImpl.java`、`database/mysql/migration/V3__population_data_scope.sql`。
- 测试：community-common / community-auth 单测、`community-integration-tests` 集成、`scripts/e2e/wave0-smoke.sh`。
