package cc.ddrpa.filtro.visitor.extension.mp;

import cc.ddrpa.filtro.core.field.FiltroFieldMeta;
import cc.ddrpa.filtro.core.field.FiltroOperator;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import cz.jirutka.rsql.parser.ast.*;

import java.util.List;
import java.util.Map;

public class MybatisPlusQueryWrapperVisitor implements RSQLVisitor<QueryWrapper<?>, QueryWrapper<?>> {

    private final Map<String, FiltroFieldMeta> fieldSpecMap;

    public MybatisPlusQueryWrapperVisitor(Map<String, FiltroFieldMeta> fieldSpecMap) {
        this.fieldSpecMap = fieldSpecMap;
    }

    public static <T extends Enum<T>> T toEnum(Class<?> clazz, String name) {
        if (clazz == null || name == null) {
            return null;
        }
        if (!clazz.isEnum()) {
            throw new IllegalArgumentException(clazz + " 不是枚举类型");
        }

        @SuppressWarnings("unchecked")
        Class<? extends Enum> enumClass = (Class<? extends Enum>) clazz;

        try {
            return (T) Enum.valueOf(enumClass, name);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public void apply(Node rootNode, QueryWrapper<?> rootWrapper) {
        rootNode.accept(this, rootWrapper);
    }

    @Override
    public QueryWrapper<?> visit(AndNode node, QueryWrapper<?> param) {
        param.nested(w -> {
            for (Node child : node.getChildren()) {
                child.accept(this, w);
            }
        });
        return param;
    }

    @Override
    public QueryWrapper<?> visit(OrNode node, QueryWrapper<?> param) {
        param.nested(w -> {
            boolean first = true;
            for (Node child : node.getChildren()) {
                if (first) {
                    child.accept(this, w);
                    first = false;
                } else {
                    w.or();
                    child.accept(this, w);
                }
            }
        });
        return param;
    }

    @Override
    public QueryWrapper<?> visit(ComparisonNode node, QueryWrapper<?> param) {
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
        List arguments = node.getArguments();
        if (filtroFieldMeta.isEnumeration()) {
            // 将参数转换为枚举值
            arguments = arguments.stream().map(str -> toEnum(filtroFieldMeta.getEnumerationClass(), (String) str)).toList();
        }
        Object firstArgument = arguments.get(0);
        switch (claimedFiltroOperator) {
            case EQ -> param.eq(filtroFieldMeta.getKey(), firstArgument);
            case NEQ -> param.ne(filtroFieldMeta.getKey(), firstArgument);
            case GT, ALT_GT -> param.gt(filtroFieldMeta.getKey(), firstArgument);
            case GTE, ALT_GTE -> param.ge(filtroFieldMeta.getKey(), firstArgument);
            case LT, ALT_LT -> param.lt(filtroFieldMeta.getKey(), firstArgument);
            case LTE, ALT_LTE -> param.le(filtroFieldMeta.getKey(), firstArgument);
            case IN -> param.in(filtroFieldMeta.getKey(), arguments);
            case NOT_IN -> param.notIn(filtroFieldMeta.getKey(), arguments);

            case PREFIX -> param.likeRight(filtroFieldMeta.getKey(), firstArgument);
            case SUFFIX -> param.likeLeft(filtroFieldMeta.getKey(), firstArgument);
            case CONTAINS -> param.like(filtroFieldMeta.getKey(), firstArgument);
            case IS_NULL -> param.isNull(filtroFieldMeta.getKey());
            case NOT_NULL -> param.isNotNull(filtroFieldMeta.getKey());
            default ->
                    throw new IllegalArgumentException("FiltroOperator " + claimedComparisonOperator.getSymbol() + " is not supported in " + this.getClass().getSimpleName());
        }
        return param;
    }
}
