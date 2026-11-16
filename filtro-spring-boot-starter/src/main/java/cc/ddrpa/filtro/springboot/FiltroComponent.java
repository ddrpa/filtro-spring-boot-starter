package cc.ddrpa.filtro.springboot;

/**
 * 前端查询控件类型，由 Java 字段类型与枚举字典推断，供元数据接口使用。
 */
public enum FiltroComponent {
    /**
     * 文本 / 搜索输入框
     */
    TEXT,

    /**
     * 数字输入（含范围）
     */
    NUMBER,

    /**
     * 日期（含范围）
     */
    DATE,

    /**
     * 日期时间（含范围）
     */
    DATETIME,

    /**
     * 下拉框（含多选）
     */
    SELECT,

    /**
     * checkbox
     */
    CHECKBOX
}
