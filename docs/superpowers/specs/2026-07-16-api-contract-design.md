# API 契约层设计(OpenAPI 契约先行)

- 日期: 2026-07-16
- 状态: 待用户评审
- 关联: `2026-07-16-measure-community-platform-scaffold-design.md`(骨架)
- 依据: 《开发计划7.13(1).xlsx》(范围为准)、《详细功能设计说明书 V7》4.1.2/§5/§6/§7(接口/字段/权限依据)

## 1. 目标

为 measure-community-platform 引入 **API 契约层**,确立"契约先行(spec-first)"工作法:
- **OpenAPI 3.x YAML 为唯一事实来源**(放各模块 Git 内);
- **Springdoc + Swagger UI** 运行时展示与自测;
- **Apifox** 导入 YAML 做团队评审、Mock、联调、测试。

本阶段(骨架期)不写全部业务接口,只:立全局规范、把 community-info 做成 spec-first 完整样板、其余服务写契约骨架。

## 2. 决策(已确认)

| # | 决策 | 选定 |
|---|---|---|
| 同步方向 | spec-first / code-first / hybrid | **spec-first**:YAML 为准,构建期生成代码 |
| 生成范围 | 接口+模型 / 仅模型 | **仅生成 DTO 模型**(不生成 API 接口/Controller) |
| 信封调和 | RetObj vs 生成签名 | Controller 手写返回 **`RetObj<生成DTO>`**;YAML 响应用 RetObj 信封描述 |
| YAML 位置 | 各模块 / 集中 | **各模块 `src/main/resources/openapi/openapi.yaml`** |
| 路径 | — | 对齐已落地代码与 docx 4.1.2,**不另造**(如 `/api/v1/population/persons`) |

## 3. 架构与数据流

```
openapi.yaml (SOT, 每模块 src/main/resources/openapi/)
   │  build 期
   ▼
openapi-generator-maven-plugin  →  生成 DTO 模型 (target/generated-sources/openapi, 不入库)
   │  包 com.measure.community.{模块}.api.model
   ▼
手写 Controller  →  返回 RetObj<生成DTO>  →  生成DTO ↔ 实体(手工映射)  →  MyBatis-Plus
   │  运行期
   ▼
Springdoc  →  /v3/api-docs + Swagger UI(自测)
Apifox     →  导入 openapi.yaml(评审/Mock/联调/测试)
```

- 生成物只在 `target/`,不入库;`openapi.yaml` 入库,是评审对象。
- DTO 字段来自 YAML → 请求/响应字段不会与契约漂移;端点存在性以 YAML 为准。
- 本阶段不加 CI 的 openapi-diff(spec-first 下 YAML 即准);后续可加"Springdoc 运行时导出 vs YAML"对比。

## 4. 全局规范 `docs/api/standards.md`(内容清单)

- **路径**:`/api/v1/{模块域}/{资源}`;域名对齐 docx(population/service/welfare/affairs/portal…);仅名词复数资源。
- **统一响应**:`RetObj{ code:int, message:string, data:T }`;成功 `code=200`,失败 `code` 取 `SystemStatus`。
- **分页格式**:入参 `pageNo`(≥1)/`pageSize`(≤100);出参 MyBatis-Plus `Page`:`records/total/size/current/pages`。
- **鉴权与权限**:全局 `bearerAuth`(JWT);网关 `AuthFilter` 校验 token 并注入 `X-Internal-Auth/X-UserInfo`,服务 `RequestHeaderFilter` 校验并解出用户;数据权限按角色/`data_scope`(§6)。
- **错误码**:集中于 `SystemStatus` 枚举;每接口列出可能错误场景。
- **字段格式**:时间 ISO-8601(`yyyy-MM-dd'T'HH:mm:ss`);金额 `BigDecimal`(元,2 位);证件号/手机号等敏感字段 AES 密文列 + HMAC 盲索引 + 展示脱敏(§5)。
- **审计/幂等**:`createTime/updateTime/createBy/updateBy` 由 `MybatisPlusConfig` 自动填充;创建接口按业务唯一键(如证件号盲索引)幂等去重。
- **版本策略**:URI 版本 `/api/v1`;不兼容变更升 `/api/v2`。

## 5. community-info 完整样板(spec-first 参考实现)

### 5.1 `community-info/src/main/resources/openapi/openapi.yaml`
- `openapi: 3.0.3`;`info`(信息服务);`servers`;`security: [bearerAuth]`;`tags: [人口信息]`。
- `components.securitySchemes.bearerAuth`(http bearer JWT)。
- `components.schemas`:
  - `RetObj`(code/message/data) —— 信封;各响应用 `allOf` 或直接内联描述 data。
  - `Population`(id/type/name/idCard(脱敏)/gender/phone/insuredStatus/employmentStatus/version/审计字段)。
  - `PopulationCreateReq`(type/name/idCard/gender/phone/insuredStatus/employmentStatus)。
  - `PopulationPage`(records/total/size/current/pages)。
- `paths`:
  - `GET /api/v1/population/persons`:query `type/name/idCard/pageNo/pageSize`;200 → RetObj+PopulationPage;403 无数据权限。
  - `POST /api/v1/population/persons`:body `PopulationCreateReq`;200 → RetObj+人口ID;400 证件号为空;500 证件号已存在。

### 5.2 `community-info/pom.xml`
加 `openapi-generator-maven-plugin`(7.x):
- `generatorName=spring`、`generateApis=false`、`generateModels=true`、`generateSupportingFiles=false`;
- `inputSpec=${project.basedir}/src/main/resources/openapi/openapi.yaml`;
- `output=${project.build.directory}/generated-sources/openapi`;
- `configOptions`:`useSpringBoot3=true`、`openApiNullable=false`、`useJakartaEe=true`;
- `modelPackage=com.measure.community.info.api.model`;`modelNameSuffix=Dto`(生成 `PopulationCreateReqDto` 等,避免与实体重名)。
- 生成目录默认加入编译源。

### 5.3 代码改造
- **请求体**用生成 DTO:删手写 `model/req/PopulationCreateReq.java`,改用生成的 `PopulationCreateReqDto`。
- **查询参数**保留手写 `model/req/PopulationQueryReq.java`:因 `generateApis=false`,query 参数不产生生成模型(它属于未生成的 API 接口签名),故 GET 的 `type/name/idCard/pageNo/pageSize` 仍用手写对象(或 `@RequestParam`)绑定。
- `Population` 实体保留(MyBatis)。
- `PopulationService`/`Impl`:创建入参改 `PopulationCreateReqDto`,`Dto → Population` 手工映射;查询入参仍 `PopulationQueryReq`;返回 `RetObj`。
- `PopulationController`:POST body 用 `PopulationCreateReqDto`,GET 用 `PopulationQueryReq`,均返回 `RetObj<...>`。
- `PopulationControllerTest`:mock 的 `createPerson` 签名同步为 `PopulationCreateReqDto`;仍 MockMvc standalone、2 用例。

## 6. 其余 5 服务契约骨架

`community-{auth,portal,service,welfare,affairs}/src/main/resources/openapi/openapi.yaml` 各写骨架:
- `openapi/info/servers/security(bearerAuth)/tags`;
- 现有端点:`/api/v1/{portal|service|welfare|affairs}/ping`;auth 写 `/community-auth/user/login`(登录,免鉴权)。
- **暂不接 generator 插件**(无真实业务 DTO);等各自开发真实接口时按 info 样板接。

## 7. 验证标准

- `mvn -pl community-info -am package`:generator 生成 DTO 到 `target/generated-sources`,全模块编译通过。
- `PopulationControllerTest` 2/2 通过(签名改生成 DTO 后)。
- 每个 `openapi.yaml` 通过 OpenAPI 校验(generator `validate` goal 或 `swagger-cli validate`)合法。
- 端到端(可选,需 Docker 栈):经网关 `POST/GET /api/v1/population/persons` 行为与改造前一致。

## 8. 不做的事(YAGNI)

- 不写 info 以外的业务接口(仅骨架 + ping/login)。
- 不生成 API 接口/Controller,只生成 DTO 模型。
- 不加 CI openapi-diff 校验(后续可加)。
- Apifox 的导入/定期同步为团队手动操作,不写进构建。
- 不动 RetObj/GlobalExceptionHandler/网关鉴权等既有约定。

## 9. 风险

- **改造 community-info**:唯一已端到端跑通的模块要改(手写 DTO → 生成 DTO + 映射),需重新验证;收益是确立 spec-first 样板。
- **generator + JDK/Lombok 兼容**:generator 生成的 DTO 用 Bean 校验注解;须确认在 WSL JDK17 下编译无碍(依赖走阿里云镜像)。
- **信封在 YAML 里的表达**:RetObj 泛型信封用 OpenAPI 描述需内联/allOf,YAML 稍冗;可接受。
