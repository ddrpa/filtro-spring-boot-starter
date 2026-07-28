package cc.ddrpa.filtro.visitor.extension.meilisearch;

import cc.ddrpa.filtro.core.exception.PredicateBuildException;
import cc.ddrpa.filtro.core.field.FiltroFieldMeta;
import cc.ddrpa.filtro.core.field.FiltroOperator;
import cc.ddrpa.filtro.core.rsql.AbstractRSQLVisitor;
import cz.jirutka.rsql.parser.ast.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 将 RSQL AST 转为 Meilisearch filter 表达式字符串。
 */
public class MeilisearchFilterVisitor extends AbstractRSQLVisitor<String>
        implements RSQLVisitor<String, Void> {

    public MeilisearchFilterVisitor(Map<String, FiltroFieldMeta> fieldSpecMap) {
        super(fieldSpecMap);
    }

    public MeilisearchFilterVisitor(Map<String, FiltroFieldMeta> fieldSpecMap, int maxDepth) {
        super(fieldSpecMap, maxDepth);
    }

    private static String condition(String attr, String op, String value) {
        return attr + " " + op + " " + value;
    }

    private static String quote(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    private static String toEnumName(Class<?> clazz, String name) {
        if (clazz == null || name == null) {
            throw new IllegalArgumentException("Enum class and name must not be null");
        }
        if (!clazz.isEnum()) {
            throw new IllegalArgumentException(clazz + " is not an enum type");
        }
        @SuppressWarnings({"unchecked", "rawtypes"})
        Enum<?> constant = Enum.valueOf((Class<? extends Enum>) clazz, name);
        return constant.name();
    }

    public String apply(Node rootNode) {
        validateDepth(rootNode);
        return rootNode.accept(this);
    }

    @Override
    public String visit(AndNode node, Void param) {
        return node.getChildren().stream()
                .map(child -> child.accept(this))
                .collect(Collectors.joining(" AND ", "(", ")"));
    }

    @Override
    public String visit(OrNode node, Void param) {
        return node.getChildren().stream()
                .map(child -> child.accept(this))
                .collect(Collectors.joining(" OR ", "(", ")"));
    }

    @Override
    public String visit(ComparisonNode node, Void param) {
        ResolvedComparison resolved = resolve(node);
        FiltroOperator operator = resolved.operator();
        FiltroFieldMeta meta = resolved.meta();
        List<String> arguments = resolved.arguments();
        String attr = meta.getKey();

        if (meta.isEnumeration()) {
            arguments = arguments.stream()
                    .map(name -> toEnumName(meta.getEnumerationClass(), name))
                    .toList();
        }

        return switch (operator) {
            case EQ -> condition(attr, "=", formatValue(meta, arguments.get(0)));
            case NEQ -> condition(attr, "!=", formatValue(meta, arguments.get(0)));
            case NULLABLE_NEQ -> "(" + condition(attr, "!=", formatValue(meta, arguments.get(0)))
                    + " OR " + attr + " IS NULL)";
            case GT, ALT_GT -> condition(attr, ">", formatValue(meta, arguments.get(0)));
            case GTE, ALT_GTE -> condition(attr, ">=", formatValue(meta, arguments.get(0)));
            case LT, ALT_LT -> condition(attr, "<", formatValue(meta, arguments.get(0)));
            case LTE, ALT_LTE -> condition(attr, "<=", formatValue(meta, arguments.get(0)));
            case IN -> attr + " IN [" + formatList(meta, arguments) + "]";
            case NOT_IN -> attr + " NOT IN [" + formatList(meta, arguments) + "]";
            case PREFIX -> condition(attr, "STARTS WITH", formatValue(meta, arguments.get(0)));
            case CONTAINS -> condition(attr, "CONTAINS", formatValue(meta, arguments.get(0)));
            case IS_NULL -> attr + " IS NULL";
            case NOT_NULL -> attr + " IS NOT NULL";
            case SUFFIX -> throw new IllegalArgumentException(
                    "FiltroOperator " + operator.getSymbol()
                            + " is not supported in MeilisearchFilterVisitor (no ENDS WITH)");
            default -> throw new IllegalArgumentException(
                    "FiltroOperator " + operator.getSymbol()
                            + " is not supported in " + this.getClass().getSimpleName());
        };
    }

    private String formatList(FiltroFieldMeta meta, List<String> arguments) {
        return arguments.stream()
                .map(a -> formatValue(meta, a))
                .collect(Collectors.joining(", "));
    }

    /**
     * 按 QueryIntent 格式化字面量：数值/布尔无引号，字符串加双引号。
     */
    private String formatValue(FiltroFieldMeta meta, String raw) {
        return switch (meta.getQueryIntent()) {
            case QUANTITY -> {
                try {
                    Long.parseLong(raw);
                    yield raw;
                } catch (NumberFormatException e) {
                    throw new PredicateBuildException(meta.getField(), raw, "QUANTITY", e);
                }
            }
            case MEASURE, AMOUNT -> {
                try {
                    Double.parseDouble(raw);
                    yield raw;
                } catch (NumberFormatException e) {
                    throw new PredicateBuildException(meta.getField(), raw,
                            meta.getQueryIntent().name(), e);
                }
            }
            case BOOLEAN -> {
                if (!"true".equalsIgnoreCase(raw) && !"false".equalsIgnoreCase(raw)) {
                    throw new PredicateBuildException(meta.getField(), raw, "BOOLEAN",
                            new IllegalArgumentException("not a boolean"));
                }
                yield Boolean.parseBoolean(raw) ? "true" : "false";
            }
            case SEARCH, EXACT, CATEGORY, DATETIME -> quote(raw);
            default -> throw new IllegalArgumentException(
                    "QueryIntent " + meta.getQueryIntent()
                            + " is not supported in " + this.getClass().getSimpleName());
        };
    }
}
