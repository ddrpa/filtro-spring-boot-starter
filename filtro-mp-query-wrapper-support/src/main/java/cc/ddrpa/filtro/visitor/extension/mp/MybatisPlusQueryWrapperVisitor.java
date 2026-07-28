package cc.ddrpa.filtro.visitor.extension.mp;

import cc.ddrpa.filtro.core.field.FiltroFieldMeta;
import cc.ddrpa.filtro.core.field.FiltroOperator;
import cc.ddrpa.filtro.core.rsql.AbstractRSQLVisitor;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import cz.jirutka.rsql.parser.ast.*;

import java.util.List;
import java.util.Map;

public class MybatisPlusQueryWrapperVisitor extends AbstractRSQLVisitor<QueryWrapper<?>>
        implements RSQLVisitor<QueryWrapper<?>, QueryWrapper<?>> {

    public MybatisPlusQueryWrapperVisitor(Map<String, FiltroFieldMeta> fieldSpecMap) {
        super(fieldSpecMap);
    }

    public MybatisPlusQueryWrapperVisitor(Map<String, FiltroFieldMeta> fieldSpecMap, int maxDepth) {
        super(fieldSpecMap, maxDepth);
    }

    public static <T extends Enum<T>> T toEnum(Class<?> clazz, String name) {
        if (clazz == null || name == null) {
            throw new IllegalArgumentException("Enum class and name must not be null");
        }
        if (!clazz.isEnum()) {
            throw new IllegalArgumentException(clazz + " is not an enum type");
        }

        @SuppressWarnings("unchecked")
        Class<? extends Enum> enumClass = (Class<? extends Enum>) clazz;

        try {
            return (T) Enum.valueOf(enumClass, name);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "No enum constant " + clazz.getCanonicalName() + "." + name, e);
        }
    }

    /**
     * 转义 MySQL LIKE 通配符 {@code %} 和 {@code _}。
     */
    private static String escapeLike(String input) {
        if (input == null) {
            return null;
        }
        return input.replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
    }

    public void apply(Node rootNode, QueryWrapper<?> rootWrapper) {
        validateDepth(rootNode);
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
        ResolvedComparison resolved = resolve(node);
        FiltroOperator claimedFiltroOperator = resolved.operator();
        FiltroFieldMeta filtroFieldMeta = resolved.meta();
        List arguments = resolved.arguments();

        if (filtroFieldMeta.isEnumeration()) {
            arguments = arguments.stream()
                    .map(str -> toEnum(filtroFieldMeta.getEnumerationClass(), (String) str))
                    .toList();
        }
        Object firstArgument = arguments.get(0);
        switch (claimedFiltroOperator) {
            case EQ -> param.eq(filtroFieldMeta.getKey(), firstArgument);
            case NEQ -> param.ne(filtroFieldMeta.getKey(), firstArgument);
            case NULLABLE_NEQ -> param.nested(w -> w.isNull(filtroFieldMeta.getKey())
                    .or().ne(filtroFieldMeta.getKey(), firstArgument));
            case GT, ALT_GT -> param.gt(filtroFieldMeta.getKey(), firstArgument);
            case GTE, ALT_GTE -> param.ge(filtroFieldMeta.getKey(), firstArgument);
            case LT, ALT_LT -> param.lt(filtroFieldMeta.getKey(), firstArgument);
            case LTE, ALT_LTE -> param.le(filtroFieldMeta.getKey(), firstArgument);
            case IN -> param.in(filtroFieldMeta.getKey(), arguments);
            case NOT_IN -> param.notIn(filtroFieldMeta.getKey(), arguments);

            case PREFIX -> param.likeRight(filtroFieldMeta.getKey(), escapeLike((String) firstArgument));
            case SUFFIX -> param.likeLeft(filtroFieldMeta.getKey(), escapeLike((String) firstArgument));
            case CONTAINS -> param.like(filtroFieldMeta.getKey(), escapeLike((String) firstArgument));
            case IS_NULL -> param.isNull(filtroFieldMeta.getKey());
            case NOT_NULL -> param.isNotNull(filtroFieldMeta.getKey());
            default ->
                    throw new IllegalArgumentException("FiltroOperator " + claimedFiltroOperator.getSymbol()
                            + " is not supported in " + this.getClass().getSimpleName());
        }
        return param;
    }
}
