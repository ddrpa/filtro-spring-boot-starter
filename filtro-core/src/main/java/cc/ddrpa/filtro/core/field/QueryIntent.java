package cc.ddrpa.filtro.core.field;

/**
 * 查询语义意图，决定字段的默认操作符集和前端控件类型。
 * <p>
 * {@link #AUTO} 仅在 {@link cc.ddrpa.filtro.core.annotation.Filtro#intent()} 中使用，
 * 表示根据 Java 字段类型自动推断。
 */
public enum QueryIntent {
    /**
     * 待推断（仅注解默认值，不出现在元数据中）
     */
    AUTO,

    /**
     * 模糊搜索 — 全量字符串操作符，前端搜索输入框
     */
    SEARCH,

    /**
     * 精准匹配 — EQ/NEQ/IN，前端精确输入框
     */
    EXACT,

    /**
     * 分类/枚举 — EQ/NEQ/IN，前端多选下拉
     */
    CATEGORY,

    /**
     * 数量/整数 — 全量数值比较，前端 min–max
     */
    QUANTITY,

    /**
     * 度量/浮点 — 范围比较（无 EQ/NEQ），前端 min–max
     */
    MEASURE,

    /**
     * 金额/高精度 — 全量数值比较，前端 min–max
     */
    AMOUNT,

    /**
     * 日期时间 — 全量比较，前端日期范围选择器
     */
    DATETIME,

    /**
     * 布尔 — EQ/NEQ/nullable，前端开关/三态
     */
    BOOLEAN
}
