# Filtro Spring Boot Starter

基于 RSQL（RESTful Service Query Language）的查询过滤与元数据框架，为 Spring Boot 应用提供声明式的查询参数解析功能。

## 场景

该项目最初旨在为某资产管理系统增强检索功能，使得用户在添加自定义属性后，能立即在界面上感知到，并能够使用该属性进行资产检索。

## 特性

- **注解即可推断** — 字段标注 `@Filtro` 后，系统根据 Java 类型自动推断查询意图和操作符集（也可显式覆盖）
- **查询意图驱动** — 3 种 `QueryIntent`（SEARCH / EXACT / RANGE），表达查询形态；值转型与控件细节由 Java 字段类型推导
- `@Filtro(intent = ...)` 给定默认操作符集，`operators` 做减法
- **动态字段 Provider** — 实现 `FiltroFieldMetaProvider` 即可为 schemaless / 自定义属性提供元数据（首个 `supports` 命中独占）
- 自动注册元数据接口，前端可凭 `queryIntent` + `component` / `dictionary` 选择控件
- 基于 `classgraph` 的字节码扫描，无需加载类，启动快且兼容 JDK 17+
- 自动装配 MyBatis-Plus / MongoDB / Meilisearch handler，无需手动注册 Bean
- 类似 Jakarta Bean Validation 的分组概念，支持不同场景下的查询方案

## 快速开始

### 1. 添加依赖

```xml
<dependency>
    <groupId>cc.ddrpa.filtro</groupId>
    <artifactId>filtro-spring-boot-starter</artifactId>
    <version>0.0.2-SNAPSHOT</version>
</dependency>
```

### 2. 添加后端适配（任选其一）

```xml
<!-- MyBatis-Plus -->
<dependency>
    <groupId>cc.ddrpa.filtro</groupId>
    <artifactId>filtro-mp-query-wrapper-support</artifactId>
    <version>0.0.2-SNAPSHOT</version>
</dependency>

<!-- Spring Data MongoDB -->
<dependency>
    <groupId>cc.ddrpa.filtro</groupId>
    <artifactId>filtro-jpa-mongo-support</artifactId>
    <version>0.0.2-SNAPSHOT</version>
</dependency>

<!-- Meilisearch filter 表达式 -->
<dependency>
    <groupId>cc.ddrpa.filtro</groupId>
    <artifactId>filtro-meilisearch-support</artifactId>
    <version>0.0.2-SNAPSHOT</version>
</dependency>

<!-- Springdoc / OpenAPI（可选：文档化元数据端点与 q 参数） -->
<dependency>
    <groupId>cc.ddrpa.filtro</groupId>
    <artifactId>filtro-springdoc-support</artifactId>
    <version>0.0.2-SNAPSHOT</version>
</dependency>
```

Handler 会自动装配——匹配 classpath 中的 `QueryWrapper`、`Criteria` 或 `MeilisearchFilter`。

引入 `filtro-springdoc-support` 且项目已启用 springdoc 时，会自动：

- 将动态注册的元数据端点（如 `GET /api/book:filtro`）写入 OpenAPI
- 为带 `@FiltroQuery` 的查询接口补充查询参数 `q`（RSQL）说明，并隐藏该注解对应的方法参数

### 3. 配置扫描路径

```yaml
filtro:
  controller-packages:
    - com.example.controller
```

### 4. 定义查询实体

```java
public class Book {
    // String → SEARCH（模糊搜索）
    @Filtro
    private String title;

    // Enum → EXACT（EQ / IN 等）+ 枚举字典
    @Filtro
    private Genre genre;

    // Integer → RANGE（数值范围）
    @Filtro
    private Integer price;

    // 显式声明：精确匹配，不允许模糊搜索
    @Filtro(intent = QueryIntent.EXACT)
    private String isbn;

    // 减法模式：SEARCH 里只留 CONTAINS
    @Filtro(value = "作者",
            operators = {FiltroOperator.CONTAINS})
    private List<String> authors;

    // RANGE 自动推断
    @Filtro
    private LocalDate publishDate;

    // 分组隔离：仅 SysAdmin 角色可用
    @Filtro(value = "ISBN", groups = {SysAdmin.class})
    private String adminIsbn;
}
```

> 字段必须带 `@Filtro` 才会被扫描注册。`email` 字段？`@Filtro` + String → SEARCH → 前端搜索输入框，默认 CONTAINS / NOT_CONTAINS。

### 5. 控制器

```java
// MyBatis-Plus:
@GetMapping
public Page<Book> pageBooks(
        @FiltroQuery(Book.class) QueryWrapper<Book> wrapper,
        PageRequest page) {
    return bookService.page(page, wrapper);
}

// Meilisearch
@GetMapping
public SearchResult searchBooks(@FiltroQuery(Book.class) MeilisearchFilter filter) {
    SearchRequest request = new SearchRequest("");
    if (filter != null && !filter.isEmpty()) {
        request.setFilter(new String[]{filter.expression()});
    }
    return meilisearchClient.index("books").search(request);
}
```

### 6. 查询

```http
GET /api/book?q=title=contains=java,(title=contains=python;price<40)
```

查找书名包含 `java`，或者书名包含 `python` 且价格小于 40 的图书。

### 7. 查看元数据（可选）

```http
GET /api/book:filtro
```

前端可据此动态渲染查询面板。

---

## 查询意图与默认操作符

| QueryIntent | Java 推断源 | 默认操作符 | 典型 `component` |
|-------------|------------|-----------|------------------|
| `SEARCH` | `String`（fallback） | `=contains=`, `=nocontains=`, `=null=`, `=nonull=` | `TEXT` |
| `EXACT` | `Boolean` / `Enum`；或显式声明（如 SKU） | `==`, `!=`, `=nullableneq=`, `=in=`, `=out=`, `=null=`, `=nonull=`（Boolean 无 IN） | `CHECKBOX` / `SELECT` / `TEXT` |
| `RANGE` | 数值、`BigDecimal`、日期时间 | `>` / `>=` / `<` / `<=`（+ ALT）+ `=null=` / `=nonull=`；整型/`BigDecimal`/日期额外带 `==`/`!=`/`=nullableneq=`；`Float`/`Double` 默认无 EQ | `NUMBER` / `DATE` / `DATETIME` |

`IN` / `NOT_IN` 归属 **EXACT**（离散精确匹配），不在 RANGE 默认集中。

### 覆盖默认操作符（减法模式）

```java
// SEARCH 默认模糊操作符 → 只留 CONTAINS
@Filtro(operators = {FiltroOperator.CONTAINS})
private String email;
```

`operators` 会与 Intent（及 Java 类型微调后）的默认集合做交集（`retainAll`），自动过滤无效操作符，并补全 ALT 形式（如声明了 `LT` 则自动带 `ALT_LT`）。

---

## 支持的操作符

### RSQL 原生

| 操作符 | 符号 | 多值 | 说明 |
|--------|------|------|------|
| 等于 | `==` | | 严格等值比较 |
| 不等于 | `!=` | | 不等于 |
| 大于 | `>`, `=gt=` | | 大于 |
| 大于等于 | `>=`, `=ge=` | | 大于等于 |
| 小于 | `<`, `=lt=` | | 小于 |
| 小于等于 | `<=`, `=le=` | | 小于等于 |
| IN | `=in=` | ✓ | 包含在列表中 |
| NOT IN | `=out=` | ✓ | 不包含在列表中 |

### 扩展

| 操作符 | 符号 | 多值 | 说明 |
|--------|------|------|------|
| 为空 | `=null=` | | 字段为 NULL |
| 非空 | `=nonull=` | | 字段非 NULL |
| nullable 不等 | `=nullableneq=` | | `field != ? OR field IS NULL` |
| 包含 | `=contains=` | | 字符串包含/模糊匹配 |
| 不包含 | `=nocontains=` | | 字符串不包含 |

---

## 注解说明

### `@FiltroQuery`（控制器参数）

| 属性 | 类型 | 说明 |
|------|------|------|
| `value()` | `Class<?>` | 查询实体类型 |
| `group()` | `Class<?>` | 分组，默认 `void.class`（匹配无分组字段） |

### `@Filtro`（实体字段）

| 属性 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `value()` | `String` | `""` | 字段描述，用于元数据文档 |
| `tooltip()` | `String` | `""` | 悬停提示文案，供前端作为 tooltip 使用 |
| `field()` | `String` | `""` | RSQL 字段名，默认取属性名 |
| `key()` | `String` | `""` | 数据库列名，默认驼峰转下划线 |
| `intent()` | `QueryIntent` | `AUTO` | 查询意图，AUTO 时根据 Java 类型推断 |
| `operators()` | `FiltroOperator[]` | `{}` | 操作符白名单，在 Intent 默认集中做减法 |
| `groups()` | `Class<?>[]` | `{}` | 适用分组 |

---

## 配置

```yaml
filtro:
  controller-packages:           # Controller 扫描包路径
    - com.example.controller
  max-depth: 20                  # RSQL 嵌套深度上限
  enable-metadata-endpoint: true # 是否注册元数据端点
  metadata-endpoint-suffix: ":filtro"  # 元数据端点路径后缀
```

| 属性 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `filtro.controller-packages` | `String[]` | `{}` | Controller 扫描包路径 |
| `filtro.max-depth` | `int` | `20` | RSQL 表达式最大嵌套深度 |
| `filtro.enable-metadata-endpoint` | `boolean` | `true` | 是否自动注册元数据查询端点 |
| `filtro.metadata-endpoint-suffix` | `String` | `:filtro` | 元数据端点路径后缀 |

---

## 元数据端点

系统自动为每个 `@FiltroQuery` 标注的 GET 接口注册元数据端点。例如 `GET /api/book` → `GET /api/book:filtro`：

```json
[
  {
    "field": "title",
    "queryIntent": "SEARCH",
    "component": "TEXT",
    "supportedOperations": ["CONTAINS", "NOT_CONTAINS", "IS_NULL", "NOT_NULL"],
    "description": "书名",
    "tooltip": null,
    "dictionary": null
  },
  {
    "field": "catalog",
    "queryIntent": "EXACT",
    "component": "SELECT",
    "supportedOperations": ["EQ", "NEQ", "NULLABLE_NEQ", "IN", "NOT_IN", "IS_NULL", "NOT_NULL"],
    "description": "上架类目",
    "tooltip": null,
    "dictionary": {
      "小说": "FICTION",
      "非小说类 / 实用类": "NON_FICTION",
      "科学": "SCIENCE",
      "历史": "HISTORY"
    }
  }
]
```

前端可根据 `component` 选择控件，再结合 `queryIntent` / `supportedOperations` / `dictionary` 决定单选或多选、是否区间。

---

## 动态字段元数据（Provider）

`FiltroRegistry` 在解析 `q` 与访问 `:filtro` 时，按 `getOrder()` **升序**选取**第一个** `supports(criteriaType) == true` 的 `FiltroFieldMetaProvider`，取其完整字段列表（不合并多源）。

| Provider | 默认 order | 说明 |
|----------|------------|------|
| 用户自定义 | `0` | 未覆盖 `getOrder()` 时优先于内置 |
| `InMemoryFiltroFieldMetaProvider` | `100` | 命令式 `register`（无则创建、有则更新） |
| `AnnotatedClassFiltroFieldMetaProvider` | `Integer.MAX_VALUE` | `@Filtro` 扫描结果，兜底 |

**拉模式（推荐）：** 实现接口并注册为 Spring Bean。认领某类型后，Registry 不再询问后续 Provider——若还需注解字段，请在 Provider 内自行组合。

```java
@Component
public class AssetAttributeMetaProvider implements FiltroFieldMetaProvider {

    @Override
    public boolean supports(Class<?> t) {
        return Asset.class.equals(t);
    }

    @Override
    public List<FiltroFieldMeta> getFields(Class<?> t) {
        return attributeService.listFilterable().stream()
                .map(a -> FiltroFieldMetaFactory
                        .create(a.name(), a.intent(), a.javaType())
                        .key(a.path())
                        .label(a.label())
                        .build())
                .toList();
    }
}
```

**推模式：** 注入 `InMemoryFiltroFieldMetaProvider`，在属性变更后 `register(Asset.class, fullMetas)`（无则创建、有则覆盖）。InMemory 认领该类型后会挡住 Annotated 兜底。

`FiltroFieldMetaFactory` 用于无 Java `Field` 的 schemaless 构建，**必须**提供 `javaType`。

---

## 自定义扩展

实现 `RsqlNodeHandler<T>` 接口即可接入新的查询后端：

```java
public class MyCustomHandler implements RsqlNodeHandler<MyQueryType> {

    @Override
    public boolean supports(Class<?> targetType) {
        return MyQueryType.class.equals(targetType);
    }

    @Override
    public MyQueryType parse(Map<String, FiltroFieldMeta> metaMap, Node queryRoot) {
        // 遍历 AST，构建自定义查询对象
    }
}
```

将 Handler 注册为 Spring Bean 即可自动发现。
