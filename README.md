# Filtro Spring Boot Starter

基于 RSQL（RESTful Service Query Language）的查询过滤与元数据框架，为 Spring Boot 应用提供声明式的查询参数解析功能。

## 场景

该项目最初旨在为某资产管理系统增强检索功能，使得用户在添加自定义属性后，能立即在界面上感知到，并能够使用该属性进行资产检索。

## 特性

- **零注解默认行为** — 大多数场景下字段无需注解，系统根据 Java 类型自动推断查询意图和操作符集
- **查询意图驱动** — 8 种 `QueryIntent`（SEARCH / EXACT / CATEGORY / QUANTITY / MEASURE / AMOUNT / DATETIME / BOOLEAN），而非按 Java 类型命名
- `@FiltroField(intent = ...)` 即给默认操作符集，`operators` 做减法
- 自动注册元数据接口，前端可凭 `queryIntent` 枚举名直接选择控件
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
```

Handler 会自动装配——匹配 classpath 中的 `QueryWrapper`、`Criteria` 或 `MeilisearchFilter`。

### 3. 配置扫描路径

```yaml
filtro:
  controller-packages:
    - com.example.controller
```

### 4. 定义查询实体

```java
public class Book {
    // 零注解：String → SEARCH（模糊搜索全量操作符）
    private String title;

    // 零注解：Enum → CATEGORY（EQ / IN 等）
    private Genre genre;

    // 零注解：Integer → QUANTITY（数值范围）
    private Integer price;

    // 显式声明：精确匹配，不允许模糊搜索
    @FiltroField(intent = QueryIntent.EXACT)
    private String isbn;

    // 减法模式：SEARCH 里只留 CONTAINS
    @FiltroField(value = "作者",
            operators = {FiltroOperator.CONTAINS})
    private List<String> authors;

    // DATETIME 自动推断，无需声明
    private LocalDate publishDate;

    // 分组隔离：仅 SysAdmin 角色可用
    @FiltroField(value = "ISBN", groups = {SysAdmin.class})
    private String adminIsbn;
}
```

> `email` 字段？String → SEARCH → 前端搜索输入框，自动带 PREFIX / SUFFIX / CONTAINS 等全套模糊操作符。

### 5. 控制器

```java
// MyBatis-Plus:
@GetMapping
public Page<Book> pageBooks(
        @Filtro(Book.class) QueryWrapper<Book> wrapper,
        PageRequest page) {
    return bookService.page(page, wrapper);
}

// Meilisearch
@GetMapping
public SearchResult searchBooks(@Filtro(Book.class) MeilisearchFilter filter) {
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

| QueryIntent | Java 推断源 | 默认操作符 | 前端控件 |
|-------------|------------|-----------|---------|
| `SEARCH` | `String`（fallback） | `==`, `!=`, `=nullable-neq=`, `=in=`, `=out=`, `=prefix=`, `=suffix=`, `=contains=`, `=null=`, `=nonull=` | 搜索输入框 |
| `EXACT` | 显式声明 | `==`, `!=`, `=nullable-neq=`, `=in=`, `=out=`, `=null=`, `=nonull=` | 精确输入框 |
| `CATEGORY` | `Enum`（自动） | `==`, `!=`, `=nullable-neq=`, `=in=`, `=out=`, `=null=`, `=nonull=` | 多选下拉 |
| `QUANTITY` | `Long`, `Integer`, `Short` | 全量数值 + `=null=`, `=nonull=` | min–max |
| `MEASURE` | `Float`, `Double` | 范围比较（无 `==`/`!=`）+ `=null=`, `=nonull=` | min–max |
| `AMOUNT` | `BigDecimal`, `Decimal128` | 全量数值 + `=null=`, `=nonull=` | min–max |
| `DATETIME` | `java.time.*` | 全量比较 + `=null=`, `=nonull=` | 日期范围 |
| `BOOLEAN` | `boolean`, `Boolean` | `==`, `!=`, `=null=`, `=nonull=` | 开关/三态 |

### 覆盖默认操作符（减法模式）

```java
// SEARCH 默认全量模糊操作符 → 只留 CONTAINS
@FiltroField(operators = {FiltroOperator.CONTAINS})
private String email;
```

`operators` 会与 Intent 的默认集合做交集（`retainAll`），自动过滤无效操作符，并补全 ALT 形式（如声明了 `LT` 则自动带 `ALT_LT`）。

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
| nullable 不等 | `=nullable-neq=` | | `field != ? OR field IS NULL` |
| 前缀匹配 | `=prefix=` | | 字符串前缀 |
| 后缀匹配 | `=suffix=` | | 字符串后缀（Meilisearch 后端不支持） |
| 包含 | `=contains=` | | 字符串包含/模糊匹配 |

---

## 注解说明

### `@Filtro`

| 属性 | 类型 | 说明 |
|------|------|------|
| `value()` | `Class<?>` | 查询实体类型 |
| `group()` | `Class<?>` | 分组，默认 `void.class`（匹配无分组字段） |

### `@FiltroField`

| 属性 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `value()` | `String` | `""` | 字段描述，用于元数据文档 |
| `tooltip()` | `String` | `""` | 悬停提示文案，供前端作为 tooltip 使用 |
| `field()` | `String` | `""` | RSQL 字段名，默认取属性名 |
| `key()` | `String` | `""` | 数据库列名，默认驼峰转下划线 |
| `intent()` | `QueryIntent` | `AUTO` | 查询意图，AUTO 时根据 Java 类型推断 |
| `operators()` | `FiltroOperator[]` | `{}` | 操作符白名单，在 Intent 默认集中做减法 |
| `maxInSize()` | `int` | `0` | IN/NOT_IN 参数上限，0 不限制 |
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

系统自动为每个 `@Filtro` 标注的 GET 接口注册元数据端点。例如 `GET /api/book` → `GET /api/book:filtro`：

```json
[
  {
    "field": "title",
    "queryIntent": "SEARCH",
    "supportedOperations": ["CONTAINS", "PREFIX", "EQ", "SUFFIX", "NEQ", "NULLABLE_NEQ", "IN", "NOT_IN", "IS_NULL", "NOT_NULL"],
    "label": "书名",
    "tooltip": null,
    "dictionary": null
  },
  {
    "field": "catalog",
    "queryIntent": "CATEGORY",
    "supportedOperations": ["EQ", "NEQ", "NULLABLE_NEQ", "IN", "NOT_IN", "IS_NULL", "NOT_NULL"],
    "label": "上架类目",
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

前端可根据 `queryIntent` 值直接选择控件——无需额外的元数据字段。

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
