package cc.ddrpa.filtro.core.field;

import java.util.stream.Stream;

public enum FiltroOperator {
    // RSQL 原生支持的操作符

    // 许多 RSQL 扩展实现中允许 == + wildcard 实现 LIKE 功能，但是我们这里只有 TEXT 才支持 LIKE 匹配，
    // 因此这里 EQ 只允许严格的等值比较，
    EQ(true, "==", false),
    NEQ(true, "!=", false),
    GT(true, ">", false),
    ALT_GT(true, "=gt=", false),
    GTE(true, ">=", false),
    ALT_GTE(true, "=ge=", false),
    LT(true, "<", false),
    ALT_LT(true, "=lt=", false),
    LTE(true, "<=", false),
    ALT_LTE(true, "=le=", false),
    IN(true, "=in=", true),
    NOT_IN(true, "=out=", true),

    /* 自定义扩展的操作符 */
    IS_NULL(false, "=null=", false),
    NOT_NULL(false, "=nonull=", false),

    // 专门用于字符串的模糊匹配
    PREFIX(false, "=prefix=", false),
    SUFFIX(false, "=suffix=", false),
    CONTAINS(false, "=contains=", false);

    private final boolean rsqlOriginal;
    private final String symbol;
    private final boolean multiValue;

    FiltroOperator(boolean rsqlOriginal, String symbol, boolean multiValue) {
        this.rsqlOriginal = rsqlOriginal;
        this.symbol = symbol;
        this.multiValue = multiValue;
    }

    public boolean isRsqlOriginal() {
        return rsqlOriginal;
    }

    public String getSymbol() {
        return symbol;
    }

    public boolean isMultiValue() {
        return multiValue;
    }

    public static FiltroOperator of(String symbol) {
        return Stream.of(FiltroOperator.values())
                .filter(op -> op.symbol.equalsIgnoreCase(symbol))
                .findFirst()
                .orElseThrow(IllegalArgumentException::new);
    }
}