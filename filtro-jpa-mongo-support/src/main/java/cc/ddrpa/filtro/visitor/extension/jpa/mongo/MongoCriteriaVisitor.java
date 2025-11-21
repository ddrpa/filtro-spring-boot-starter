package cc.ddrpa.filtro.visitor.extension.jpa.mongo;

import cc.ddrpa.filtro.core.field.FiltroFieldMeta;
import cc.ddrpa.filtro.core.field.FiltroOperator;
import cz.jirutka.rsql.parser.ast.*;
import org.bson.types.Decimal128;
import org.springframework.data.mongodb.core.query.Criteria;

import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Pattern;

public class MongoCriteriaVisitor implements RSQLVisitor<Criteria, Criteria> {

    private final Map<String, FiltroFieldMeta> fieldSpecMap;

    public MongoCriteriaVisitor(Map<String, FiltroFieldMeta> fieldSpecMap) {
        this.fieldSpecMap = fieldSpecMap;
    }

    public void apply(Node rootNode, Criteria criteria) {
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
        // 检查是否支持该列
        String claimedField = node.getSelector();
        if (!fieldSpecMap.containsKey(claimedField)) {
            throw new IllegalArgumentException("Field " + claimedField + " not found in filtroFieldMeta");
        }
        // 检查是否支持该操作符
        ComparisonOperator claimedComparisonOperator = node.getOperator();
        FiltroOperator claimedFiltroOperator = FiltroOperator.of(claimedComparisonOperator.getSymbol());
        FiltroFieldMeta filtroFieldMeta = fieldSpecMap.get(claimedField);
        if (!filtroFieldMeta.getSupportedOperations().contains(claimedFiltroOperator)) {
            throw new IllegalArgumentException("FiltroOperator " + claimedComparisonOperator.getSymbol() + " not supported for field " + claimedField);
        }
        return switch (claimedFiltroOperator) {
            case EQ -> Criteria.where(filtroFieldMeta.getKey()).is(cast(filtroFieldMeta, node.getArguments().get(0)));
            case NEQ -> Criteria.where(filtroFieldMeta.getKey()).ne(cast(filtroFieldMeta, node.getArguments().get(0)));
            case GT, ALT_GT ->
                    Criteria.where(filtroFieldMeta.getKey()).gt(cast(filtroFieldMeta, node.getArguments().get(0)));
            case GTE, ALT_GTE ->
                    Criteria.where(filtroFieldMeta.getKey()).gte(cast(filtroFieldMeta, node.getArguments().get(0)));
            case LT, ALT_LT ->
                    Criteria.where(filtroFieldMeta.getKey()).lt(cast(filtroFieldMeta, node.getArguments().get(0)));
            case LTE, ALT_LTE ->
                    Criteria.where(filtroFieldMeta.getKey()).lte(cast(filtroFieldMeta, node.getArguments().get(0)));
            case IN -> Criteria.where(filtroFieldMeta.getKey()).in(cast(filtroFieldMeta, node.getArguments()));
            case NOT_IN -> Criteria.where(filtroFieldMeta.getKey()).nin(cast(filtroFieldMeta, node.getArguments()));

            case PREFIX ->
                    Criteria.where(filtroFieldMeta.getKey()).regex('^' + Pattern.quote(node.getArguments().get(0)));
            case SUFFIX ->
                    Criteria.where(filtroFieldMeta.getKey()).regex(Pattern.quote(node.getArguments().get(0)) + '$');
            case CONTAINS -> Criteria.where(filtroFieldMeta.getKey()).regex(Pattern.quote(node.getArguments().get(0)));
            case IS_NULL -> new Criteria().orOperator(
                    Criteria.where(filtroFieldMeta.getKey()).is(null),
                    Criteria.where(filtroFieldMeta.getKey()).exists(false)
            );
            case NOT_NULL -> new Criteria().andOperator(
                    Criteria.where(filtroFieldMeta.getKey()).ne(null),
                    Criteria.where(filtroFieldMeta.getKey()).exists(true)
            );
            default ->
                    throw new IllegalArgumentException("FiltroOperator " + claimedComparisonOperator.getSymbol() + " is not supported in " + this.getClass().getSimpleName());
        };
    }

    // MongoDB 由于 schema-less 导致类型敏感，因此 < 2000 和 < "2000" 是两种含义
    private List<Object> cast(FiltroFieldMeta filtroFieldMeta, List<String> arguments) {
        Function<String, Object> caster = switch (filtroFieldMeta.getFiltroValueType()) {
            case INT -> Long::parseLong;
            case FLOAT -> Double::parseDouble;
            case DECIMAL -> Decimal128::parse;
            case BOOLEAN -> Boolean::parseBoolean;
            case STRING -> a -> a;
            case DATETIME -> a -> Date.from(Instant.parse(a));
            default ->
                    throw new IllegalArgumentException("Field " + filtroFieldMeta.getFiltroValueType() + " is not supported in " + this.getClass().getSimpleName());
        };
        return arguments.stream().map(caster).toList();
    }

    // MongoDB 由于 schema-less 导致类型敏感，因此 < 2000 和 < "2000" 是两种含义
    private Object cast(FiltroFieldMeta filtroFieldMeta, String argument) {
        Function<String, Object> caster = switch (filtroFieldMeta.getFiltroValueType()) {
            case INT -> Long::parseLong;
            case FLOAT -> Double::parseDouble;
            case DECIMAL -> Decimal128::parse;
            case BOOLEAN -> Boolean::parseBoolean;
            case STRING -> a -> a;
            case DATETIME -> a -> Date.from(Instant.parse(a));
            default ->
                    throw new IllegalArgumentException("Field " + filtroFieldMeta.getFiltroValueType() + " is not supported in " + this.getClass().getSimpleName());
        };
        return caster.apply(argument);
    }
}