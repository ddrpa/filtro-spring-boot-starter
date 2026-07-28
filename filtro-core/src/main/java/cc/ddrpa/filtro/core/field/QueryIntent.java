package cc.ddrpa.filtro.core.field;

/**
 * 查询语义意图，决定字段的默认操作符集和前端交互范式。
 * <p>
 * {@link #AUTO} 仅在 {@link cc.ddrpa.filtro.core.annotation.Filtro#intent()} 中使用，
 * 表示根据 Java 字段类型自动推断。值转型与控件细节由 Java 字段类型及枚举字典推导。
 */
public enum QueryIntent {
    /**
     * 待推断（仅注解默认值，不出现在元数据中）
     */
    AUTO,

    /**
     * 模糊搜索 — 字符串模糊匹配，前端搜索输入框
     */
    SEARCH,

    /**
     * 精准匹配 — EQ/NEQ/IN，前端精确输入框 / 下拉 / 开关
     */
    EXACT,

    /**
     * 范围匹配 — GT/LT 等比较，前端 range / 日期范围选择器
     */
    RANGE
}
