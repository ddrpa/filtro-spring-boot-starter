package cc.ddrpa.filtro.core.exception;

/**
 * 查询条件构建异常，替代裸 JDK 异常，包含字段名、原始值、构建阶段和根因。
 */
public class PredicateBuildException extends RuntimeException {

    private final String field;
    private final String rawValue;
    private final String stage;

    public PredicateBuildException(String field, String rawValue, String stage, Throwable cause) {
        super(String.format("Failed to build predicate for field '%s' (raw value: '%s') at stage '%s': %s",
                field, rawValue, stage, cause.getMessage()), cause);
        this.field = field;
        this.rawValue = rawValue;
        this.stage = stage;
    }

    public String getField() {
        return field;
    }

    public String getRawValue() {
        return rawValue;
    }

    public String getStage() {
        return stage;
    }
}
