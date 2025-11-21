# Filtro Spring Boot Starter

基于 RSQL（RESTful Service Query Language）的查询过滤框架，为 Spring Boot 应用提供声明式的查询参数解析功能。

## 场景

该项目最初旨在为某资产管理系统增强检索功能，使得用户在添加自定义属性后，能立即使用该属性进行资产检索。

## 特性

- 通过注解声明可用于过滤的字段与适用方法
- 自动解析和验证 RSQL 查询字符串，支持生成 MyBatis Plus QueryWrapper 和 JPA/MongoDB Criteria（测试中），支持注入其他扩展
- 基于 Java 类型系统，没有额外引入注入风险
- 类似 Jakarta Bean Validation 的分组概念，支持不同场景下的字段过滤

## 快速开始

1. 添加依赖 `cc.ddrpa.filtro:filtro-spring-boot-starter:0.0.1-SNAPSHOT`
2. 添加 MyBatis Plus QueryWrapper 支持 `cc.ddrpa.filtro：filtro-mp-query-wrapper-support：0.0.1-SNAPSHOT`
3. 在代码中注册 MyBatis Plus QueryWrapper 支持

```java
@Bean
public MybatisPlusQueryWrapperNodeHandler mybatisPlusQueryWrapperNodeHandler() {
    return new MybatisPlusQueryWrapperNodeHandler();
}
```

4. 定义查询类，使用 `@FiltroField` 注解标记可过滤的字段：

```java
public class Book {
    @FiltroField(value = "书名", operators = {FiltroOperator.CONTAINS, FiltroOperator.PREFIX, FiltroOperator.SUFFIX, FiltroOperator.EQ})
    private String title;

    @FiltroField(value = "作者", type = FiltroValueType.STRING, operators = {FiltroOperator.CONTAINS})
    @TableField(typeHandler = CommaSeparatedStringArrayTypeHandler.class)
    private List<String> authors;

    @FiltroField(value = "出版日期", operators = {FiltroOperator.GTE, FiltroOperator.LTE})
    private LocalDate publishDate;

    @FiltroField(value = "isbn", operators = {FiltroOperator.EQ}, groups = {SysAdmin.class})
    private String isbn;

    @FiltroField(value = "上架分类", field = "catalog", operators = {FiltroOperator.EQ})
    private Genre genre;
    // ...
```

5. 在控制器方法参数上使用 `@Filtro` 注解，当前仅支持 GET 方法

```java
@RestController
@RequestMapping("/api/book")
public class BookController {
    @GetMapping
    public Page<Book> pageBooks(
            @Filtro(Book.class) QueryWrapper<Book> wrapper, PageRequest page) {
        return bookService.list(queryWrapper, page);
    }
    // ...
```

6. 通过 HTTP 请求传递 RSQL 查询字符串

```
GET /api/book?q=title=contains=java,(title=contains=python;price<40)
```

查找书名包含 "java" 或者书名包含 "python" 且价格小于 40 的图书。

## 支持的操作符

### RSQL 原生操作符

| 操作符 | 符号 | 说明 | 示例                           |
|--------|------|------|------------------------------|
| 等于 | `==` | 严格等值比较 | `isbn==9787111583882`        |
| 不等于 | `!=` | 不等于 | `isbn!=9787111583882`        |
| 大于 | `>` 或 `=gt=` | 大于 | `price>20`                   |
| 大于等于 | `>=` 或 `=ge=` | 大于等于 | `price>=20`                  |
| 小于 | `<` 或 `=lt=` | 小于 | `price<300`                  |
| 小于等于 | `<=` 或 `=le=` | 小于等于 | `price<=300`                 |
| IN | `=in=` | 包含在列表中 | `genre=in=(HISTORY,FICTION)` |
| NOT IN | `=out=` | 不包含在列表中 | `genre=out=(HISTORY)`        |

### 扩展操作符

| 操作符 | 符号 | 说明 | 示例                    |
|--------|------|------|-----------------------|
| 为空 | `=null=` | 检查字段是否为空 | `price=null=`         |
| 非空 | `=nonull=` | 检查字段是否非空 | `price=nonull=`       |
| 前缀匹配 | `=prefix=` | 字符串前缀匹配 | `title=prefix=数据库`    |
| 后缀匹配 | `=suffix=` | 字符串后缀匹配 | `title=suffix=指南`     |
| 包含 | `=contains=` | 字符串包含匹配 | `title=contains=java` |

## 元数据文档接口

Filtro 会尝试自动为所有使用 `@Filtro` 注解的接口创建对应的元数据查询接口。

例如为 `GET /api/book` 创建 `GET /api/book:filtro` 接口，请求该接口返回下方示例内容。该响应说明相关接口支持书名的模糊和精准匹配，以及按照上架类目进行筛选。

这在一些需要动态构建查询界面的场景下非常有用。

```json
[
  {
    "field": "title",
    "filtroValueType": "STRING",
    "supportedOperations": [
      "CONTAINS",
      "PREFIX",
      "EQ",
      "SUFFIX"
    ],
    "description": "书名",
    "dictionary": null
  },
  {
    "field": "catalog",
    "filtroValueType": "ENUMERATION",
    "supportedOperations": [
      "EQ",
      "NEQ",
      "NOT_IN",
      "IN"
    ],
    "description": "上架类目",
    "dictionary": {
      "小说": "FICTION",
      "非小说类 / 实用类": "NON_FICTION",
      "科学": "SCIENCE",
      "历史": "HISTORY",
      "奇幻 / 幻想": "FANTASY"
    }
  }
]
```

## 注解说明

### `@Filtro`

用于控制器方法参数，标记该参数需要从 RSQL 查询字符串解析。

- `value()`：查询实体类型
- `group()`：分组类型

### `@FiltroField`

用于查询实体类的字段，定义该字段的过滤规则。

- `value()`：字段描述，用于元数据文档
- `field()`：RSQL 查询中使用的字段名（可选，默认为属性名）
- `key()`：数据库列名或嵌套字段路径（可选，默认为属性名的下划线形式）
- `type()`：字段类型（可选，系统会自动推断）
- `operators()`：支持的操作符列表（可选，系统会根据类型自动推断）
- `groups()`：适用分组（可选，参考 Jakarta Bean Validation 的 group 概念）

```java
@FiltroField(
    value = "ISBN 编目",
    field = "isbn",
    operators = {FiltroOperator.EQ},
    groups = {SysAdmin.class}
)
private String idCard;
```

该示例说明只有 `@Filtro(value = Book.class, group = SysAdmin.class)` 标注的接口支持使用 `isbn` 精确匹配。

## 配置

在 `application.yml` 或 `application.properties` 中配置：

```yaml
filtro:
  controller-packages:  # Controller 扫描包路径
    - com.example.controller
    - com.example.api
```

```properties
filtro.controller-packages[0]=com.example.controller
filtro.controller-packages[1]=com.example.api
```

