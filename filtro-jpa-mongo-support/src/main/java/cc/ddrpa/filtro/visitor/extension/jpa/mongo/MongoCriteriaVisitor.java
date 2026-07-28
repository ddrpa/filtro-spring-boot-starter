package cc.ddrpa.filtro.visitor.extension.jpa.mongo;

import cc.ddrpa.filtro.core.exception.PredicateBuildException;
import cc.ddrpa.filtro.core.field.FiltroFieldMeta;
import cc.ddrpa.filtro.core.field.FiltroOperator;
import cc.ddrpa.filtro.core.rsql.AbstractRSQLVisitor;
import cz.jirutka.rsql.parser.ast.*;
import org.bson.types.Decimal128;
import org.springframework.data.mongodb.core.query.Criteria;

import java.time.*;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Pattern;

public class MongoCriteriaVisitor extends AbstractRSQLVisitor<Criteria>
        implements RSQLVisitor<Criteria, Criteria> {

    public MongoCriteriaVisitor(Map<String, FiltroFieldMeta> fieldSpecMap) {
        super(fieldSpecMap);
    }

    public MongoCriteriaVisitor(Map<String, FiltroFieldMeta> fieldSpecMap, int maxDepth) {
        super(fieldSpecMap, maxDepth);
    }

    private static Function<String, Object> wrap(Function<String, Object> fn,
                                                 FiltroFieldMeta meta, String stage) {
        return raw -> {
            try {
                return fn.apply(raw);
            } catch (Exception e) {
                throw new PredicateBuildException(meta.getField(), raw, stage, e);
            }
        };
    }

    public void apply(Node rootNode, Criteria criteria) {
        validateDepth(rootNode);
        criteria.andOperator(rootNode.accept(this, criteria));
    }

    @Override
    public Criteria visit(AndNode node, Criteria param) {
        Criteria[] children = node.getChildren().stream()
                .map(n -> n.accept(this, null))
                .toArray(Criteria[]::new);
        return new Criteria().andOperator(children);
    }

    @Override
    public Criteria visit(OrNode node, Criteria param) {
        Criteria[] children = node.getChildren().stream()
                .map(n -> n.accept(this, null))
                .toArray(Criteria[]::new);
        return new Criteria().orOperator(children);
    }

    @Override
    public Criteria visit(ComparisonNode node, Criteria param) {
        ResolvedComparison resolved = resolve(node);
        FiltroOperator claimedFiltroOperator = resolved.operator();
        FiltroFieldMeta filtroFieldMeta = resolved.meta();
        List<String> arguments = resolved.arguments();

        return switch (claimedFiltroOperator) {
            case EQ -> Criteria.where(filtroFieldMeta.getKey()).is(cast(filtroFieldMeta, arguments.get(0)));
            case NEQ -> Criteria.where(filtroFieldMeta.getKey()).ne(cast(filtroFieldMeta, arguments.get(0)));
            case NULLABLE_NEQ -> new Criteria().orOperator(
                    Criteria.where(filtroFieldMeta.getKey()).is(null),
                    Criteria.where(filtroFieldMeta.getKey()).ne(cast(filtroFieldMeta, arguments.get(0)))
            );
            case GT, ALT_GT -> Criteria.where(filtroFieldMeta.getKey()).gt(cast(filtroFieldMeta, arguments.get(0)));
            case GTE, ALT_GTE -> Criteria.where(filtroFieldMeta.getKey()).gte(cast(filtroFieldMeta, arguments.get(0)));
            case LT, ALT_LT -> Criteria.where(filtroFieldMeta.getKey()).lt(cast(filtroFieldMeta, arguments.get(0)));
            case LTE, ALT_LTE -> Criteria.where(filtroFieldMeta.getKey()).lte(cast(filtroFieldMeta, arguments.get(0)));
            case IN -> Criteria.where(filtroFieldMeta.getKey()).in(cast(filtroFieldMeta, arguments));
            case NOT_IN -> Criteria.where(filtroFieldMeta.getKey()).nin(cast(filtroFieldMeta, arguments));
            case CONTAINS -> Criteria.where(filtroFieldMeta.getKey()).regex(Pattern.quote(arguments.get(0)));
            case NOT_CONTAINS -> Criteria.where(filtroFieldMeta.getKey()).not().regex(Pattern.quote(arguments.get(0)));
            case IS_NULL -> new Criteria().orOperator(
                    Criteria.where(filtroFieldMeta.getKey()).is(null),
                    Criteria.where(filtroFieldMeta.getKey()).exists(false)
            );
            case NOT_NULL -> new Criteria().andOperator(
                    Criteria.where(filtroFieldMeta.getKey()).ne(null),
                    Criteria.where(filtroFieldMeta.getKey()).exists(true)
            );
            default -> throw new IllegalArgumentException("FiltroOperator " + claimedFiltroOperator.getSymbol()
                    + " is not supported in " + this.getClass().getSimpleName());
        };
    }

    // MongoDB 由于 schema-less 导致类型敏感，因此 < 2000 和 < "2000" 是两种含义
    private List<Object> cast(FiltroFieldMeta filtroFieldMeta, List<String> arguments) {
        Function<String, Object> caster = casterFor(filtroFieldMeta);
        return arguments.stream().map(caster).toList();
    }

    // MongoDB 由于 schema-less 导致类型敏感，因此 < 2000 和 < "2000" 是两种含义
    private Object cast(FiltroFieldMeta filtroFieldMeta, String argument) {
        return casterFor(filtroFieldMeta).apply(argument);
    }

    private Function<String, Object> casterFor(FiltroFieldMeta filtroFieldMeta) {
        Class<?> type = filtroFieldMeta.getJavaType();
        if (type == null) {
            throw new IllegalArgumentException("FiltroFieldMeta.javaType is required for Mongo casting: "
                    + filtroFieldMeta.getField());
        }
        if (Integer.class.equals(type) || int.class.equals(type)
                || Long.class.equals(type) || long.class.equals(type)
                || Short.class.equals(type) || short.class.equals(type)) {
            return wrap(Long::parseLong, filtroFieldMeta, "INT");
        }
        if (Float.class.equals(type) || float.class.equals(type)
                || Double.class.equals(type) || double.class.equals(type)) {
            return wrap(Double::parseDouble, filtroFieldMeta, "FLOAT");
        }
        if (java.math.BigDecimal.class.equals(type) || Decimal128.class.equals(type)
                || "org.bson.types.Decimal128".equals(type.getName())) {
            return wrap(Decimal128::parse, filtroFieldMeta, "DECIMAL");
        }
        if (Boolean.class.equals(type) || boolean.class.equals(type)) {
            return wrap(Boolean::parseBoolean, filtroFieldMeta, "BOOLEAN");
        }
        if (LocalDate.class.equals(type) || LocalDateTime.class.equals(type)
                || Instant.class.equals(type) || LocalTime.class.equals(type)
                || Date.class.equals(type)) {
            return wrap(this::parseDateTime, filtroFieldMeta, "DATETIME");
        }
        return a -> a;
    }

    /**
     * 三层 fallback：Instant → LocalDateTime → LocalDate。
     */
    private Date parseDateTime(String raw) {
        try {
            return Date.from(Instant.parse(raw));
        } catch (DateTimeException e1) {
            try {
                return Date.from(LocalDateTime.parse(raw).atZone(ZoneOffset.UTC).toInstant());
            } catch (DateTimeException e2) {
                try {
                    return Date.from(LocalDate.parse(raw).atStartOfDay(ZoneOffset.UTC).toInstant());
                } catch (DateTimeException e3) {
                    throw new PredicateBuildException("DATETIME", raw, "parseDateTime", e3);
                }
            }
        }
    }
}
