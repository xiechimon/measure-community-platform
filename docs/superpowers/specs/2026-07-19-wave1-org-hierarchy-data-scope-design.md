# Wave 1 第二片：组织/网格层级树 + 数据范围层级展开 设计

> 日期：2026-07-19
> 状态：已确认
> 上级设计：`docs/superpowers/specs/2026-07-18-production-ready-tdd-delivery-design.md` §5.2 Wave 1 P2b
> 前置：`docs/superpowers/specs/2026-07-18-wave1-rbac-data-scope-design.md`（第一片，已交付：扁平 ALL/GRID/SELF）
> 需求编号：`docs/requirements/backend-requirements.md` **X-02**（组织/网格数据范围的层级部分）

## 1. 背景与范围

第一片交付了人口(I-01)上的扁平三档数据范围（ALL/GRID/SELF），但 `sys_role.data_scope` 里的 `COMMUNITY/STREET/DISTRICT` 三档目前 fail-closed 回落到 SELF，因为没有组织层级树无法把"街道/社区"展开成网格集合。本片补齐这三档，让行级过滤对真实组织结构可用。

- **纳入**：`sys_org` 组织/网格层级树（物化路径）；用户绑定到层级节点；登录把用户节点路径写入会话；`PopulationDataPermissionHandler` 对 COMMUNITY/STREET/DISTRICT 档展开为"我节点下所有网格"；V4 迁移 + 层级种子；`DataScope` 扩三档；真实 MySQL E2E 验证一个 COMMUNITY 用户看得到本社区下两个网格、看不到别的社区。
- **明确不纳入（留后续）**：`CUSTOM` 自定义范围映射表（仍 fail-closed）；层级树的管理 CRUD（增删改节点/移动子树）；除人口外其它业务表（拦截器白名单仍只 `t_population`）；角色管理 CRUD、Refresh Token。

## 2. 现状（第一片交付后）

- `DataScope` 枚举只有 `ALL/GRID/SELF`；`fromCode` 把 COMMUNITY/STREET/DISTRICT/CUSTOM/null 一律映射 SELF；`resolve` 取最宽（ALL>GRID>SELF）。
- `PopulationDataPermissionHandler`（community-common）：仅对 `PopulationMapper` 注入；ALL→不过滤、GRID→`grid_id=<gridId>`、SELF→`create_by=<userId>`、gridId null→`1=0`、无上下文→不过滤。
- 登录 `buildLoginUser`：写 `orgId/gridId/dataScope` 进 `LoginUser`→Redis JSON→`UserContextHolder`（有 `getOrgId/getGridId/getDataScope`）。`sys_user.org_id` 存在但**闲置**（V3 种子只设了 gridA/gridB 的 grid_id，未设 org_id）。
- `t_population.grid_id` 存在（第一片 V3 加）；`sys_org` 表**不存在**。

## 3. 设计

### 3.1 `sys_org` 层级表（物化路径）
- 列：`id BIGINT PK`、`parent_id BIGINT NULL`（根为 NULL）、`type VARCHAR(16)`（`DISTRICT/STREET/COMMUNITY/GRID`）、`name VARCHAR`、`path VARCHAR(255)`（从根到自身的祖先 id 串，格式 `"/{根id}/.../{自身id}/"`，如 `"/1/5/12/"`）、审计列。
- 约束：`path` 上建索引（子孙查询走 `LIKE '<path>%'`）。GRID 是叶子节点；`sys_user.grid_id` 与 `t_population.grid_id` 指向 GRID 型节点的 id。
- **关键决策**：用物化 `path` 而非 closure 表/递归 CTE——MySQL 8 下最简单，拦截器可直接拼子查询；代价是移动子树需批量改 path（本片不做树的 CRUD，可接受）。

### 3.2 用户绑定层级
- `sys_user.org_id`（现闲置）指向用户所在层级节点（任意层级）。用户的 `data_scope` 应与其节点层级配套（街道管理员的节点是 STREET 型、`data_scope='STREET'`）——由种子/后续管理 CRUD 保证，本片靠种子保证。
- GRID 用户的节点 = 其网格节点（`org_id` = `grid_id` 对应的 GRID 节点）。

### 3.3 会话传递：新增 `orgPath`
- 登录 `buildLoginUser`：由 `sys_user.org_id` 查 `sys_org.path`，写入 `LoginUser.orgPath`。新增 mapper 查询 `String selectOrgPath(Long orgId)`（`SELECT path FROM sys_org WHERE id=#{orgId}`）。
- `LoginUser` 加字段 `orgPath:String`→Redis JSON→`RequestHeaderFilter` 自动带入 map→`UserContextHolder.getOrgPath():String`（缺失返回 null）。
- 向后兼容：旧会话/无 org_id 用户 `orgPath` 为 null → 层级档 fail-closed（见 3.4）。

### 3.4 `DataScope` 扩档
- 枚举扩为 `ALL, DISTRICT, STREET, COMMUNITY, GRID, SELF`。
- `fromCode`：精确映射六档；`CUSTOM`/null/未知 → `SELF`（仍 fail-closed）。
- `resolve`（多角色取最宽）优先级：`ALL > DISTRICT > STREET > COMMUNITY > GRID > SELF`。
- 新增 helper：`boolean isHierarchical(DataScope)` = true for DISTRICT/STREET/COMMUNITY。

### 3.5 拦截器层级展开
`PopulationDataPermissionHandler` 按 `UserContextHolder.getDataScope()`：
- `ALL` → 不过滤；`SELF` → `create_by='<userId>'`；`GRID` → `grid_id=<gridId>`（gridId null→`1=0`）——**均不变**。
- **DISTRICT/STREET/COMMUNITY（层级档）** → 用 `getOrgPath()`：
  - orgPath 非空 → `grid_id IN (SELECT id FROM sys_org WHERE type='GRID' AND path LIKE '<orgPath>%')`（"我节点下所有网格"；三档共用同一逻辑，差异体现在用户节点在树中的高度，由 orgPath 捕获）。
  - orgPath 为 null → `1 = 0`（fail-closed，看不到任何行）。
- **注入安全**：orgPath 来自系统生成的 `sys_org.path`（仅数字与 `/`），仍按现有方式转义单引号；子查询是定值 `sys_org` 表名，无用户可控字符串进 SQL。
- 作用域仍仅 `PopulationMapper` 白名单；`t_population_his` 跨网格历史继续由 `getById`（受拦截）门控（沿用第一片结论）。
- **关键决策（边界=用户节点）**：三个层级档共用"我节点下所有网格"的展开——即**用户所在节点 orgPath 决定边界**，档名（COMMUNITY/STREET/DISTRICT）本片不被独立再解析，只区分"层级展开 vs GRID/SELF/ALL"。因此**必须把用户分配到与其预期宽度匹配的节点**（街道管理员分到 STREET 节点、社区管理员分到 COMMUNITY 节点），由种子/后续 CRUD 保证。若 scope 档比节点层级更宽（如 scope=DISTRICT 但节点是 COMMUNITY），只会看到更窄的节点范围（fail-safe）；反之更窄档配更高节点会过授——本片靠分配纪律避免，未来精化方向是"按 scope 档沿 path 上溯到对应层级祖先再展开"。

### 3.6 V4 迁移与种子
- `V4__org_hierarchy.sql`：
  - 建 `sys_org` + `path` 索引。
  - 种子小树（显式 id + 物化 path，格式 `"/{根}/.../{自身}/"`）：
    - `DISTRICT id=1`，path `"/1/"`
    - `STREET id=5`，parent=1，path `"/1/5/"`
    - `COMMUNITY id=10`，parent=5，path `"/1/5/10/"`
    - `COMMUNITY id=11`，parent=5，path `"/1/5/11/"`
    - `GRID id=1001`，parent=10，path `"/1/5/10/1001/"`
    - `GRID id=1003`，parent=10，path `"/1/5/10/1003/"`（社区 10 的第二个网格，用来证明"多网格展开"）
    - `GRID id=1002`，parent=11，path `"/1/5/11/1002/"`
  - 把第一片种子用户 gridA/gridB 的 `org_id` 更新为各自 GRID 节点（1001/1002）；新增 GRID 用户 `gridC`（org_id=1003，`data_scope='GRID'`，同 gridOfficer 角色所在网格由 grid_id=1003 决定）。
  - 新增 `COMMUNITY` 用户 `commX`：org_id=10、绑一个新 `communityOfficer` 角色（`data_scope='COMMUNITY'`，权限含 `population:query`+`population:create`，**不含** `population:sensitive:view`）。密码复用 V2/V3 的 admin BCrypt 串（123456）。
  - 供验收断言：commX(社区 10) 可见网格 {1001,1003} 的人口、**不可见** 社区 11 网格 1002 的人口。

### 3.7 人口归属与验收数据
- `t_population.grid_id` 已存在（第一片）；人口行运行时由**对应 GRID 用户**经 API 创建，`grid_id` 从上下文写入（第一片 createPerson 已做，不变）。AES 密文列不能 SQL 种子，故不预置人口行。
- 验收数据在 E2E 里运行时造：gridA（1001）建 R1、gridC（1003）建 R3、gridB（1002）建 R2。commX 只**查询**不创建（commX 节点是 COMMUNITY，`getGridId()` 为 null，若创建则 grid_id 落 null——属已知边界，E2E 不走 commX 创建路径）。

## 4. 关键决策（已确认默认）
| 决策 | 取值 | 理由 |
| --- | --- | --- |
| 层级存储 | 物化 path（`LIKE '<path>%'`） | MySQL 8 最简、拦截器可拼子查询 |
| 用户定位 | `org_id`→节点，任意层级 | data_scope 与节点层级配套 |
| 层级档展开 | 我节点下所有 GRID | 三档共用，高度由 orgPath 决定 |
| orgPath 为 null 的层级用户 | `1=0`（fail-closed） | 无节点绝不放行 |
| CUSTOM | 仍 fail-closed SELF | 本片不做 |
| 会话新增 | `orgPath`（登录查 sys_org.path） | 拦截器不查库 |
| 树的 CRUD/移动子树 | 不做 | 超范围，靠种子 |

## 5. 接口 / 契约变更
- 无新增 REST 端点；人口查询结果集因层级 scope 变化。
- 会话 JSON 新增 `orgPath`（向后兼容，缺失→层级档 fail-closed）。
- DB：新增 `sys_org` 表 + 种子；`sys_user.org_id` 开始被使用。均由 Flyway V4 承载，走迁移门禁（`DatabaseMigrationIT` 期望 3→4）。

## 6. 测试与验收（TDD，先红后绿）
- **单元（离线）**：`DataScope` 六档 resolve/fromCode（含 CUSTOM→SELF、层级优先级）；`isHierarchical`；`PopulationDataPermissionHandler` 层级档生成 `grid_id IN (SELECT ... path LIKE '<orgPath>%')`、orgPath null→`1=0`、GRID/SELF/ALL 不变；`UserContextHolder.getOrgPath`。
- **集成/迁移门禁**：`DatabaseMigrationIT` 期望迁移数 3→4；V4 在真实 MySQL 应用干净、幂等。
- **E2E（真实 MySQL 过网关，扩 `wave0-smoke.sh`）**：gridA(1001) 建 R1、gridC(1003) 建 R3、gridB(1002) 建 R2（各自登录经 API 创建，idCard 随机）。断言：`commX`(COMMUNITY, 社区 10) 登录查人口 → 可见 R1、R3，**不可见** R2；gridA(GRID) 只见 R1（不见 R3/R2，证明层级档比 GRID 宽）；admin(ALL) 全见。沿用第一片 7a–7e 的 helper 与随机 idCard 风格，作为 7f+ 追加。全走五级门禁。
- 沿用第一片"人口行运行时经 API 由网格用户创建"（AES 密文列不能 SQL 种子）。

## 7. 非目标 / 后续切片
- CUSTOM 自定义范围映射表；层级树管理 CRUD（增删改/移动子树 + path 重算）；角色管理 CRUD；Refresh Token+账户安全；拦截器扩到其它业务表；X-03/04/05/06。

## 8. 预计触及文件
- community-common：`enums/DataScope.java`（扩档 + isHierarchical）、`config/PopulationDataPermissionHandler.java`（层级展开）、`utils/UserContextHolder.java`（getOrgPath）。
- community-auth：`model/vo/LoginUser.java`（orgPath 字段）、`mapper/SysUserMapper.java`（selectOrgPath）、`service/impl/UserServiceImpl.java`（buildLoginUser 写 orgPath）。
- community-info/database：`database/mysql/migration/V4__org_hierarchy.sql`。
- 测试：community-common/auth 单测、`community-integration-tests/DatabaseMigrationIT`、`scripts/e2e/wave0-smoke.sh`。
