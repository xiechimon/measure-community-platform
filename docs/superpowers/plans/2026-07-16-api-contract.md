# API 契约层(spec-first OpenAPI)Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为 measure-community-platform 引入契约先行的 API 层:OpenAPI YAML 为唯一事实来源,community-info 用 openapi-generator 从 YAML 生成 DTO 并改造为参考实现,其余服务写契约骨架。

**Architecture:** 各模块 `src/main/resources/openapi/openapi.yaml` 为 SOT;构建期 openapi-generator **仅生成 DTO 模型**到 `target/generated-sources`(不入库);手写 Controller 返回 `RetObj<生成DTO>`;运行时 Springdoc 展示,Apifox 导入 YAML 协作。

**Tech Stack:** OpenAPI 3.0.3、openapi-generator-maven-plugin 7.7.0(spring 生成器,dateLibrary=java8-localdatetime)、Spring Boot 3.3.5、MyBatis-Plus、JDK17。

## Global Constraints

- 构建**必须在 WSL 内**用原生路径跑:`wsl -d Ubuntu -e bash -lc 'cd /home/measure/ideaProject/measure-community-platform && mvn ...'`(Windows Maven 对 `\\wsl.localhost` UNC 路径解析父 POM 有 bug)。
- Maven 走 `~/.m2/settings.xml` 的阿里云 `mirrorOf=*`(已配)。
- 路径严格对齐已落地代码与 docx 4.1.2:`/api/v1/population/persons`,**不另造**。
- 生成范围:`generateApis=false`、`generateModels=true`;modelPackage=`com.measure.community.info.api.model`;modelNameSuffix=`Dto`。
- 不动 `RetObj`/`GlobalExceptionHandler`/网关鉴权等既有约定;`Population` 实体保留手写。
- 提交信息用中文。git 在 Git Bash 里跑(已配 safe.directory + 身份);如遇 `index.lock` 残留先 `rm -f .git/index.lock`。

---

## 文件结构

```
docs/api/standards.md                                   [新增] 全局 API 规范
community-info/
  src/main/resources/openapi/openapi.yaml               [新增] 信息服务契约(2 接口)
  pom.xml                                                [改] 加 generator 插件 + validation 依赖
  src/main/java/.../info/model/req/PopulationCreateReq.java   [删] 改用生成 PopulationCreateReqDto
  src/main/java/.../info/model/req/PopulationQueryReq.java    [留] query 绑定仍手写
  src/main/java/.../info/service/PopulationService.java       [改] 入参/返回类型
  src/main/java/.../info/service/impl/PopulationServiceImpl.java [改] 用生成 DTO + 映射
  src/main/java/.../info/controller/PopulationController.java    [改] body 用生成 DTO
  src/test/java/.../info/controller/PopulationControllerTest.java [改] mock 签名
community-{auth,portal,service,welfare,affairs}/
  src/main/resources/openapi/openapi.yaml               [新增] 契约骨架(暂不接生成器)
```

生成物(不入库):`community-info/target/generated-sources/openapi/src/main/java/com/measure/community/info/api/model/{PopulationDto,PopulationCreateReqDto,PopulationPageDto}.java`。

---

## Task 1: 全局规范 docs/api/standards.md

**Files:** Create `docs/api/standards.md`

**Interfaces:** Produces 团队约定文档,被各 openapi.yaml 遵循。无代码依赖。

- [ ] **Step 1: 写 standards.md**

Create `docs/api/standards.md`:
```markdown
# API 设计规范(measure-community-platform)

契约先行:每个服务的 `src/main/resources/openapi/openapi.yaml` 为唯一事实来源。以下为全服务统一约定。

## 路径
- 前缀 `/api/v1/{模块域}/{资源}`,资源用名词复数。域名对齐设计说明书,如 `/api/v1/population/persons`。
- 不兼容变更升版本 `/api/v2`。

## 统一响应
所有接口返回 `RetObj`:
```json
{ "code": 200, "message": "请求成功", "data": {} }
```
- 成功 `code=200`;失败 `code` 取 `SystemStatus` 枚举,`message` 为原因,`data` 可空。
- OpenAPI 响应用该信封描述(`code`/`message`/`data`),`data` 引用具体 schema。

## 分页
- 入参:`pageNo`(整数,≥1,默认 1)、`pageSize`(整数,1–100,默认 10)。
- 出参(MyBatis-Plus Page):`records`(数组)、`total`、`size`、`current`、`pages`。

## 鉴权与权限
- 全局安全方案 `bearerAuth`(HTTP Bearer, JWT)。
- 网关 `AuthFilter` 校验 Redis 中 token,注入 `X-Internal-Auth`/`X-UserInfo`;服务 `RequestHeaderFilter` 校验并解出用户到 `UserContextHolder`。
- 数据权限按角色 / `data_scope`(见说明书 §6),行级过滤由 MyBatis-Plus 插件追加。

## 错误码
- 集中在 `SystemStatus` 枚举;每个接口在 OpenAPI 的 `responses` 里列出可能的错误场景(如 400/403/500)。

## 字段格式
- 时间:ISO-8601 `yyyy-MM-dd'T'HH:mm:ss`(Java `LocalDateTime`)。
- 金额:`BigDecimal`,单位元,保留 2 位。
- 敏感字段(证件号/手机号等):落库 AES 密文列 + HMAC 盲索引列(唯一/等值),展示脱敏(见说明书 §5)。

## 审计与幂等
- `createTime/updateTime/createBy/updateBy` 由 `MybatisPlusConfig`(MetaObjectHandler)自动填充。
- 创建接口按业务唯一键(如证件号盲索引)幂等去重,重复返回明确错误。

## 版本与生成
- 契约 `openapi.yaml` 入库;`openapi-generator` 仅生成 DTO 到 `target/generated-sources`(不入库)。
- 运行时 Springdoc 暴露 `/v3/api-docs` + Swagger UI;Apifox 导入 `openapi.yaml` 做评审/Mock/联调。
```

- [ ] **Step 2: 提交**

Run:
```bash
cd "//wsl.localhost/Ubuntu/home/measure/ideaProject/measure-community-platform"
rm -f .git/index.lock; git add docs/api/standards.md
git commit -q -m "docs(api): 新增全局 API 设计规范 standards.md"
```
Expected: 提交成功。

---

## Task 2: community-info 契约 + 生成器接入(DTO 与手写代码共存,先跑通生成)

**Files:** Create `community-info/src/main/resources/openapi/openapi.yaml`;Modify `community-info/pom.xml`

**Interfaces:**
- Produces 生成 DTO(供 Task 3 使用):
  - `com.measure.community.info.api.model.PopulationDto`(id:Long, type/name/idCard/gender/phone/insuredStatus/employmentStatus:String, version:Integer, createTime/updateTime:LocalDateTime)
  - `PopulationCreateReqDto`(type/name/idCard/gender/phone/insuredStatus/employmentStatus:String)
  - `PopulationPageDto`(records:List<PopulationDto>, total/size/current/pages:Long)

- [ ] **Step 1: 写 openapi.yaml**

Create `community-info/src/main/resources/openapi/openapi.yaml`:
```yaml
openapi: 3.0.3
info:
  title: 信息服务 API (community-info)
  version: v1
  description: 数智化社区服务平台 - 信息服务(人房业态)。契约先行,唯一事实来源。
servers:
  - url: http://localhost:9090
    description: 经网关访问
security:
  - bearerAuth: []
tags:
  - name: 人口信息
    description: 户籍/常住/流动人口档案
paths:
  /api/v1/population/persons:
    get:
      tags: [人口信息]
      summary: 人口信息分页查询
      operationId: listPersons
      parameters:
        - name: type
          in: query
          schema: { type: string, enum: [户籍, 常住, 流动] }
        - name: name
          in: query
          schema: { type: string }
        - name: idCard
          in: query
          schema: { type: string }
        - name: pageNo
          in: query
          schema: { type: integer, minimum: 1, default: 1 }
        - name: pageSize
          in: query
          schema: { type: integer, minimum: 1, maximum: 100, default: 10 }
      responses:
        '200':
          description: 查询成功
          content:
            application/json:
              schema:
                type: object
                properties:
                  code: { type: integer, example: 200 }
                  message: { type: string, example: 请求成功 }
                  data: { $ref: '#/components/schemas/PopulationPage' }
        '403':
          description: 无数据权限
    post:
      tags: [人口信息]
      summary: 人口信息录入
      operationId: createPerson
      requestBody:
        required: true
        content:
          application/json:
            schema: { $ref: '#/components/schemas/PopulationCreateReq' }
      responses:
        '200':
          description: 录入成功,data 为人口档案ID
          content:
            application/json:
              schema:
                type: object
                properties:
                  code: { type: integer, example: 200 }
                  message: { type: string }
                  data: { type: integer, format: int64, description: 人口档案ID }
        '400':
          description: 证件号为空
        '500':
          description: 证件号已存在
components:
  securitySchemes:
    bearerAuth:
      type: http
      scheme: bearer
      bearerFormat: JWT
  schemas:
    Population:
      type: object
      description: 人口档案(证件号脱敏展示)
      properties:
        id: { type: integer, format: int64 }
        type: { type: string, description: 户籍/常住/流动 }
        name: { type: string }
        idCard: { type: string, description: 证件号(脱敏) }
        gender: { type: string }
        phone: { type: string }
        insuredStatus: { type: string }
        employmentStatus: { type: string }
        version: { type: integer }
        createTime: { type: string, format: date-time }
        updateTime: { type: string, format: date-time }
    PopulationCreateReq:
      type: object
      required: [type, name, idCard]
      properties:
        type: { type: string, enum: [户籍, 常住, 流动] }
        name: { type: string, maxLength: 64 }
        idCard: { type: string, maxLength: 32 }
        gender: { type: string }
        phone: { type: string }
        insuredStatus: { type: string }
        employmentStatus: { type: string }
    PopulationPage:
      type: object
      properties:
        records:
          type: array
          items: { $ref: '#/components/schemas/Population' }
        total: { type: integer, format: int64 }
        size: { type: integer, format: int64 }
        current: { type: integer, format: int64 }
        pages: { type: integer, format: int64 }
```

- [ ] **Step 2: 改 community-info/pom.xml —— 加 validation 依赖 + generator 插件**

在 `<dependencies>` 内(community-common 依赖之后)加:
```xml
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-validation</artifactId>
        </dependency>
```
在 `<build><plugins>` 内(spring-boot-maven-plugin 之后)加:
```xml
            <plugin>
                <groupId>org.openapitools</groupId>
                <artifactId>openapi-generator-maven-plugin</artifactId>
                <version>7.7.0</version>
                <executions>
                    <execution>
                        <goals><goal>generate</goal></goals>
                        <configuration>
                            <inputSpec>${project.basedir}/src/main/resources/openapi/openapi.yaml</inputSpec>
                            <generatorName>spring</generatorName>
                            <output>${project.build.directory}/generated-sources/openapi</output>
                            <generateApis>false</generateApis>
                            <generateApiTests>false</generateApiTests>
                            <generateApiDocumentation>false</generateApiDocumentation>
                            <generateModels>true</generateModels>
                            <generateModelTests>false</generateModelTests>
                            <generateModelDocumentation>false</generateModelDocumentation>
                            <generateSupportingFiles>false</generateSupportingFiles>
                            <modelPackage>com.measure.community.info.api.model</modelPackage>
                            <modelNameSuffix>Dto</modelNameSuffix>
                            <configOptions>
                                <useSpringBoot3>true</useSpringBoot3>
                                <useJakartaEe>true</useJakartaEe>
                                <dateLibrary>java8-localdatetime</dateLibrary>
                                <openApiNullable>false</openApiNullable>
                                <useBeanValidation>true</useBeanValidation>
                                <serializableModel>false</serializableModel>
                            </configOptions>
                        </configuration>
                    </execution>
                </executions>
            </plugin>
```

- [ ] **Step 3: 生成 + 编译验证(此时生成 DTO 未被使用,手写代码仍在)**

Run:
```bash
wsl -d Ubuntu -e bash -lc 'cd /home/measure/ideaProject/measure-community-platform && mvn -q -pl community-info -am -DskipTests package 2>&1 | tail -15; echo "RC=${PIPESTATUS[0]}"; echo "=== 生成的 DTO ==="; ls community-info/target/generated-sources/openapi/src/main/java/com/measure/community/info/api/model/ 2>&1'
```
Expected: `RC=0`,`BUILD SUCCESS`;生成目录列出 `PopulationDto.java`、`PopulationCreateReqDto.java`、`PopulationPageDto.java`。

- [ ] **Step 4: 提交**

Run:
```bash
cd "//wsl.localhost/Ubuntu/home/measure/ideaProject/measure-community-platform"
rm -f .git/index.lock; git add community-info/pom.xml community-info/src/main/resources/openapi/openapi.yaml
git commit -q -m "feat(info): 接入 OpenAPI 契约与 generator(仅生成 DTO)"
```
Expected: 提交成功。

---

## Task 3: 改造 community-info 使用生成 DTO(参考实现)

**Files:**
- Delete: `community-info/src/main/java/com/measure/community/info/model/req/PopulationCreateReq.java`
- Modify: `PopulationService.java`、`PopulationServiceImpl.java`、`PopulationController.java`、`PopulationControllerTest.java`
- Keep: `PopulationQueryReq.java`(query 绑定手写)

**Interfaces:**
- Consumes 生成 DTO(Task 2):`PopulationCreateReqDto`、`PopulationDto`、`PopulationPageDto`;`RetObj`(common)。
- Produces:
  - `PopulationService.pagePersons(PopulationQueryReq) -> RetObj`(data=`PopulationPageDto`)
  - `PopulationService.createPerson(PopulationCreateReqDto) -> RetObj`(data=Long id)

- [ ] **Step 1: 删除手写请求体 DTO**

Run:
```bash
cd "//wsl.localhost/Ubuntu/home/measure/ideaProject/measure-community-platform"
rm community-info/src/main/java/com/measure/community/info/model/req/PopulationCreateReq.java
```

- [ ] **Step 2: 改 PopulationService 接口**

覆盖 `community-info/src/main/java/com/measure/community/info/service/PopulationService.java`:
```java
package com.measure.community.info.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.measure.community.common.model.RetObj;
import com.measure.community.info.api.model.PopulationCreateReqDto;
import com.measure.community.info.model.entity.Population;
import com.measure.community.info.model.req.PopulationQueryReq;

public interface PopulationService extends IService<Population> {
    RetObj pagePersons(PopulationQueryReq req);
    RetObj createPerson(PopulationCreateReqDto req);
}
```

- [ ] **Step 3: 改 PopulationServiceImpl(用生成 DTO + 实体映射 + 脱敏)**

覆盖 `community-info/src/main/java/com/measure/community/info/service/impl/PopulationServiceImpl.java`:
```java
package com.measure.community.info.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.measure.community.common.model.RetObj;
import com.measure.community.info.api.model.PopulationCreateReqDto;
import com.measure.community.info.api.model.PopulationDto;
import com.measure.community.info.api.model.PopulationPageDto;
import com.measure.community.info.mapper.PopulationMapper;
import com.measure.community.info.model.entity.Population;
import com.measure.community.info.model.req.PopulationQueryReq;
import com.measure.community.info.service.PopulationService;
import com.measure.community.info.support.HmacUtil;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
public class PopulationServiceImpl extends ServiceImpl<PopulationMapper, Population> implements PopulationService {

    @Override
    public RetObj pagePersons(PopulationQueryReq req) {
        LambdaQueryWrapper<Population> qw = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(req.getType())) {
            qw.eq(Population::getType, req.getType());
        }
        if (StringUtils.hasText(req.getName())) {
            qw.like(Population::getName, req.getName());
        }
        if (StringUtils.hasText(req.getIdCard())) {
            qw.eq(Population::getIdCardHmac, HmacUtil.blindIndex(req.getIdCard()));
        }
        qw.orderByDesc(Population::getCreateTime);
        Page<Population> page = this.page(new Page<>(req.getPageNo(), req.getPageSize()), qw);

        PopulationPageDto dto = new PopulationPageDto();
        List<PopulationDto> records = page.getRecords().stream().map(this::toDto).toList();
        dto.setRecords(records);
        dto.setTotal(page.getTotal());
        dto.setSize(page.getSize());
        dto.setCurrent(page.getCurrent());
        dto.setPages(page.getPages());
        return RetObj.success(dto);
    }

    @Override
    public RetObj createPerson(PopulationCreateReqDto req) {
        if (!StringUtils.hasText(req.getIdCard())) {
            return RetObj.error("证件号不能为空");
        }
        String hmac = HmacUtil.blindIndex(req.getIdCard());
        long exists = this.count(new LambdaQueryWrapper<Population>()
                .eq(Population::getIdCardHmac, hmac));
        if (exists > 0) {
            return RetObj.error("该证件号已存在");
        }
        Population p = new Population();
        p.setType(req.getType());
        p.setName(req.getName());
        p.setIdCard(req.getIdCard());
        p.setIdCardHmac(hmac);
        p.setGender(req.getGender());
        p.setPhone(req.getPhone());
        p.setInsuredStatus(req.getInsuredStatus());
        p.setEmploymentStatus(req.getEmploymentStatus());
        p.setVersion(1);
        this.save(p);
        return RetObj.success(p.getId());
    }

    /** 实体 → 展示 DTO,证件号脱敏 */
    private PopulationDto toDto(Population p) {
        PopulationDto d = new PopulationDto();
        d.setId(p.getId());
        d.setType(p.getType());
        d.setName(p.getName());
        d.setIdCard(maskIdCard(p.getIdCard()));
        d.setGender(p.getGender());
        d.setPhone(p.getPhone());
        d.setInsuredStatus(p.getInsuredStatus());
        d.setEmploymentStatus(p.getEmploymentStatus());
        d.setVersion(p.getVersion());
        d.setCreateTime(p.getCreateTime());
        d.setUpdateTime(p.getUpdateTime());
        return d;
    }

    private String maskIdCard(String v) {
        if (v == null || v.length() < 8) return v;
        return v.substring(0, 4) + "********" + v.substring(v.length() - 4);
    }
}
```

- [ ] **Step 4: 改 PopulationController(POST body 用生成 DTO)**

覆盖 `community-info/src/main/java/com/measure/community/info/controller/PopulationController.java`:
```java
package com.measure.community.info.controller;

import com.measure.community.common.model.RetObj;
import com.measure.community.info.api.model.PopulationCreateReqDto;
import com.measure.community.info.model.req.PopulationQueryReq;
import com.measure.community.info.service.PopulationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@Tag(name = "人口信息(信息服务)")
@RestController
@RequestMapping("/api/v1/population")
public class PopulationController {

    @Autowired
    private PopulationService populationService;

    @Operation(summary = "人口信息分页查询")
    @GetMapping("/persons")
    public RetObj listPersons(PopulationQueryReq req) {
        return populationService.pagePersons(req);
    }

    @Operation(summary = "人口信息录入")
    @PostMapping("/persons")
    public RetObj createPerson(@RequestBody PopulationCreateReqDto req) {
        return populationService.createPerson(req);
    }
}
```

- [ ] **Step 5: 改单测(mock 的 createPerson 签名换成生成 DTO)**

覆盖 `community-info/src/test/java/com/measure/community/info/controller/PopulationControllerTest.java`:
```java
package com.measure.community.info.controller;

import com.measure.community.common.model.RetObj;
import com.measure.community.info.api.model.PopulationCreateReqDto;
import com.measure.community.info.model.req.PopulationQueryReq;
import com.measure.community.info.service.PopulationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class PopulationControllerTest {

    @Mock
    PopulationService populationService;

    @InjectMocks
    PopulationController populationController;

    MockMvc mockMvc;

    @BeforeEach
    void setup() {
        mockMvc = MockMvcBuilders.standaloneSetup(populationController).build();
    }

    @Test
    void listPersons_returnsOk() throws Exception {
        when(populationService.pagePersons(any(PopulationQueryReq.class)))
                .thenReturn(RetObj.success("paged"));
        mockMvc.perform(get("/api/v1/population/persons").param("pageNo", "1"))
                .andExpect(status().isOk());
    }

    @Test
    void createPerson_returnsOk() throws Exception {
        when(populationService.createPerson(any(PopulationCreateReqDto.class)))
                .thenReturn(RetObj.success(1L));
        mockMvc.perform(post("/api/v1/population/persons")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"张三\",\"idCard\":\"3301X\",\"type\":\"户籍\"}"))
                .andExpect(status().isOk());
    }
}
```

- [ ] **Step 6: 编译 + 单测**

Run:
```bash
wsl -d Ubuntu -e bash -lc 'cd /home/measure/ideaProject/measure-community-platform && mvn -pl community-info -am test 2>&1 | grep -E "Tests run|BUILD|ERROR|\.java:\[" | head -30; echo "RC=${PIPESTATUS[0]}"'
```
Expected: `Tests run: 2, Failures: 0, Errors: 0`,`BUILD SUCCESS`,`RC=0`。
若报找不到 `PopulationCreateReqDto`/`PopulationDto`/`PopulationPageDto`:说明 Task 2 生成器未先跑;先执行 `mvn -pl community-info -am generate-sources` 再重试。

- [ ] **Step 7: 提交**

Run:
```bash
cd "//wsl.localhost/Ubuntu/home/measure/ideaProject/measure-community-platform"
rm -f .git/index.lock; git add -A
git commit -q -m "refactor(info): 改用生成 DTO(spec-first 参考实现),证件号脱敏"
```
Expected: 提交成功。

---

## Task 4: 其余 5 服务契约骨架

**Files:** Create `community-{auth,portal,service,welfare,affairs}/src/main/resources/openapi/openapi.yaml`

**Interfaces:** 无代码依赖(不接生成器);产出可被 Apifox 导入、Springdoc 参考的契约骨架。

- [ ] **Step 1: 批量生成 4 个 ping 服务骨架 + auth 骨架**

Run(portal/service/welfare/affairs 用占位模板,逐文件替换):
```bash
cd "//wsl.localhost/Ubuntu/home/measure/ideaProject/measure-community-platform"
declare -A T=( [portal]=首页门户 [service]=社区服务 [welfare]=社区公益 [affairs]=居务管理 )
for m in portal service welfare affairs; do
  mkdir -p community-$m/src/main/resources/openapi
  cat > community-$m/src/main/resources/openapi/openapi.yaml <<EOF
openapi: 3.0.3
info:
  title: @@D@@ API (community-$m)
  version: v1
  description: 数智化社区服务平台 - @@D@@。契约骨架,业务接口随开发计划补齐。
servers:
  - url: http://localhost:9090
    description: 经网关访问
security:
  - bearerAuth: []
tags:
  - name: @@D@@
paths:
  /api/v1/$m/ping:
    get:
      tags: [@@D@@]
      summary: 健康探针
      operationId: ping
      responses:
        '200':
          description: ok
          content:
            application/json:
              schema:
                type: object
                properties:
                  code: { type: integer, example: 200 }
                  message: { type: string }
                  data: { type: string, example: community-$m ok }
components:
  securitySchemes:
    bearerAuth:
      type: http
      scheme: bearer
      bearerFormat: JWT
EOF
  sed -i "s/@@D@@/${T[$m]}/g" community-$m/src/main/resources/openapi/openapi.yaml
done
echo "生成完成"; ls community-*/src/main/resources/openapi/openapi.yaml
```

- [ ] **Step 2: 写 community-auth 骨架(登录接口,免鉴权)**

Create `community-auth/src/main/resources/openapi/openapi.yaml`:
```yaml
openapi: 3.0.3
info:
  title: 系统配置 / 鉴权 API (community-auth)
  version: v1
  description: 数智化社区服务平台 - 系统配置(用户/角色/权限/日志)与登录鉴权。契约骨架。
servers:
  - url: http://localhost:9090
    description: 经网关访问
tags:
  - name: 鉴权
paths:
  /community-auth/user/login:
    post:
      tags: [鉴权]
      summary: 登录(免鉴权白名单)
      operationId: login
      requestBody:
        required: true
        content:
          application/json:
            schema:
              type: object
              required: [account, password]
              properties:
                account: { type: string, example: admin }
                password: { type: string, example: "123456" }
      responses:
        '200':
          description: 登录成功,data 含 token
          content:
            application/json:
              schema:
                type: object
                properties:
                  code: { type: integer, example: 200 }
                  message: { type: string }
                  data:
                    type: object
                    properties:
                      id: { type: integer, format: int64 }
                      account: { type: string }
                      name: { type: string }
                      token: { type: string }
components:
  securitySchemes:
    bearerAuth:
      type: http
      scheme: bearer
      bearerFormat: JWT
```

- [ ] **Step 3: 校验 5 个 yaml 合法**

Run(用 generator 的 validate goal 逐个校验):
```bash
wsl -d Ubuntu -e bash -lc 'cd /home/measure/ideaProject/measure-community-platform
for m in auth portal service welfare affairs; do
  echo "=== community-$m ==="
  mvn -q org.openapitools:openapi-generator-maven-plugin:7.7.0:validate \
    -Dopenapi.generator.maven.plugin.inputSpec=community-$m/src/main/resources/openapi/openapi.yaml 2>&1 | tail -3
done'
```
Expected: 每个输出含 `No validation issues detected.`(或无 ERROR)。
备选(若上面参数名不被识别):`npx @redocly/cli lint community-*/src/main/resources/openapi/openapi.yaml`,或人工确认 YAML 结构与 Task 2 一致。

- [ ] **Step 4: 全量编译(确认加 yaml 不影响构建)+ 提交**

Run:
```bash
wsl -d Ubuntu -e bash -lc 'cd /home/measure/ideaProject/measure-community-platform && mvn -q -DskipTests package 2>&1 | tail -6; echo "RC=${PIPESTATUS[0]}"'
```
Expected: `RC=0`,8 模块 BUILD SUCCESS。
```bash
cd "//wsl.localhost/Ubuntu/home/measure/ideaProject/measure-community-platform"
rm -f .git/index.lock; git add -A
git commit -q -m "feat(api): 其余 5 服务补 OpenAPI 契约骨架(暂不接生成器)"
```
Expected: 提交成功。

---

## Self-Review

**1. Spec coverage(对照 api-contract-design spec):**
- §2 决策(spec-first / 仅 DTO / RetObj<生成DTO> / 各模块 src/openapi / 路径对齐)→ Task 2 插件配置 + Task 3 改造,全覆盖 ✅
- §4 standards.md 清单 → Task 1 ✅
- §5 community-info 完整样板(yaml + 插件 + 改造)→ Task 2、Task 3 ✅
- §5.3 请求体换生成 DTO、query 保留手写、实体保留 → Task 3 Step 1-4 ✅
- §6 其余 5 服务骨架 → Task 4 ✅
- §7 验证(生成 + 编译 + 单测 2/2 + yaml 校验)→ Task 2 Step3、Task 3 Step6、Task 4 Step3-4 ✅
- §8 YAGNI(不生成接口/不写业务接口/不加 CI diff)→ 计划未纳入 ✅

**2. Placeholder scan:** 无 TBD/TODO。Task 4 Step1 的 `@@D@@` 是脚本内占位,同步骤 sed 替换,非计划缺口。✅

**3. Type consistency:** `pagePersons(PopulationQueryReq)`、`createPerson(PopulationCreateReqDto)` 在接口/实现/controller/单测一致;生成 DTO 名 `PopulationDto/PopulationCreateReqDto/PopulationPageDto`(schema 名 + `Dto` 后缀)在 Task 2 Interfaces 与 Task 3 用法一致;`PopulationDto` 用 `LocalDateTime`(dateLibrary=java8-localdatetime)与实体一致,映射无类型转换。✅
