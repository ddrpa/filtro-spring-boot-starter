package cc.ddrpa.filtro.core.field;

import cc.ddrpa.filtro.core.annotation.Filtro;
import org.apache.commons.lang3.StringUtils;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 字段元数据构建器，根据注解和字段类型自动推断查询意图与操作符集。
 */
public class FiltroFieldMetaBuilder {

    // ──────────── QueryIntent → 默认操作符集 ────────────

    private static final Set<FiltroOperator> SEARCH_OPERATORS = Set.of(
            FiltroOperator.EQ, FiltroOperator.NEQ, FiltroOperator.NULLABLE_NEQ,
            FiltroOperator.IN, FiltroOperator.NOT_IN,
            FiltroOperator.PREFIX, FiltroOperator.SUFFIX, FiltroOperator.CONTAINS,
            FiltroOperator.IS_NULL, FiltroOperator.NOT_NULL
    );

    private static final Set<FiltroOperator> EXACT_OPERATORS = Set.of(
            FiltroOperator.EQ, FiltroOperator.NEQ, FiltroOperator.NULLABLE_NEQ,
            FiltroOperator.IN, FiltroOperator.NOT_IN,
            FiltroOperator.IS_NULL, FiltroOperator.NOT_NULL
    );

    private static final Set<FiltroOperator> CATEGORY_OPERATORS = Set.of(
            FiltroOperator.EQ, FiltroOperator.NEQ, FiltroOperator.NULLABLE_NEQ,
            FiltroOperator.IN, FiltroOperator.NOT_IN,
            FiltroOperator.IS_NULL, FiltroOperator.NOT_NULL
    );

    private static final Set<FiltroOperator> QUANTITY_OPERATORS = Set.of(
            FiltroOperator.EQ, FiltroOperator.NEQ, FiltroOperator.NULLABLE_NEQ,
            FiltroOperator.GT, FiltroOperator.GTE, FiltroOperator.LT, FiltroOperator.LTE,
            FiltroOperator.ALT_GT, FiltroOperator.ALT_GTE, FiltroOperator.ALT_LT, FiltroOperator.ALT_LTE,
            FiltroOperator.IN, FiltroOperator.NOT_IN,
            FiltroOperator.IS_NULL, FiltroOperator.NOT_NULL
    );

    private static final Set<FiltroOperator> MEASURE_OPERATORS = Set.of(
            FiltroOperator.GT, FiltroOperator.LT,
            FiltroOperator.ALT_GT, FiltroOperator.ALT_LT,
            FiltroOperator.IN, FiltroOperator.NOT_IN,
            FiltroOperator.IS_NULL, FiltroOperator.NOT_NULL
    );

    private static final Set<FiltroOperator> AMOUNT_OPERATORS = Set.of(
            FiltroOperator.EQ, FiltroOperator.NEQ, FiltroOperator.NULLABLE_NEQ,
            FiltroOperator.GT, FiltroOperator.GTE, FiltroOperator.LT, FiltroOperator.LTE,
            FiltroOperator.ALT_GT, FiltroOperator.ALT_GTE, FiltroOperator.ALT_LT, FiltroOperator.ALT_LTE,
            FiltroOperator.IN, FiltroOperator.NOT_IN,
            FiltroOperator.IS_NULL, FiltroOperator.NOT_NULL
    );

    private static final Set<FiltroOperator> DATETIME_OPERATORS = Set.of(
            FiltroOperator.EQ, FiltroOperator.NEQ, FiltroOperator.NULLABLE_NEQ,
            FiltroOperator.GT, FiltroOperator.GTE, FiltroOperator.LT, FiltroOperator.LTE,
            FiltroOperator.ALT_GT, FiltroOperator.ALT_GTE, FiltroOperator.ALT_LT, FiltroOperator.ALT_LTE,
            FiltroOperator.IN, FiltroOperator.NOT_IN,
            FiltroOperator.IS_NULL, FiltroOperator.NOT_NULL
    );

    private static final Set<FiltroOperator> BOOLEAN_OPERATORS = Set.of(
            FiltroOperator.EQ, FiltroOperator.NEQ,
            FiltroOperator.IS_NULL, FiltroOperator.NOT_NULL
    );

    // ──────────── 实例字段 ────────────

    private final Field field;
    private final Filtro filtroAnnotation;
    private Set<FiltroOperator> claimedFiltroOperators = Collections.emptySet();

    public FiltroFieldMetaBuilder(Field field, Filtro filtroAnnotation) {
        this.field = field;
        this.filtroAnnotation = filtroAnnotation;
    }

    // ──────────── 类型推断 ────────────

    /**
     * 根据 Java 类型推断默认 QueryIntent。
     */
    private static QueryIntent inferIntent(Class<?> clazz) {
        if (Integer.class.equals(clazz) || int.class.equals(clazz)
                || Long.class.equals(clazz) || long.class.equals(clazz)
                || Short.class.equals(clazz) || short.class.equals(clazz)) {
            return QueryIntent.QUANTITY;
        } else if (Float.class.equals(clazz) || float.class.equals(clazz)
                || Double.class.equals(clazz) || double.class.equals(clazz)) {
            return QueryIntent.MEASURE;
        } else if (BigDecimal.class.equals(clazz) || "org.bson.types.Decimal128".equals(clazz.getName())) {
            return QueryIntent.AMOUNT;
        } else if (Boolean.class.equals(clazz) || boolean.class.equals(clazz)) {
            return QueryIntent.BOOLEAN;
        } else if (java.time.LocalDate.class.equals(clazz) || java.time.LocalDateTime.class.equals(clazz)
                || java.time.Instant.class.equals(clazz) || java.time.LocalTime.class.equals(clazz)) {
            return QueryIntent.DATETIME;
        } else if (clazz.isEnum()) {
            return QueryIntent.CATEGORY;
        }
        return QueryIntent.SEARCH;
    }

    private static Set<FiltroOperator> operatorsFor(QueryIntent intent) {
        return switch (intent) {
            case SEARCH -> SEARCH_OPERATORS;
            case EXACT -> EXACT_OPERATORS;
            case CATEGORY -> CATEGORY_OPERATORS;
            case QUANTITY -> QUANTITY_OPERATORS;
            case MEASURE -> MEASURE_OPERATORS;
            case AMOUNT -> AMOUNT_OPERATORS;
            case DATETIME -> DATETIME_OPERATORS;
            case BOOLEAN -> BOOLEAN_OPERATORS;
            default -> throw new IllegalArgumentException("Unknown QueryIntent: " + intent);
        };
    }

    // ──────────── 枚举字典 ────────────

    /**
     * 将枚举类转换为字典映射（显示名称 → 枚举值名称）。
     */
    public static Map<String, String> toDict(Class<? extends Enum<?>> enumClass) {
        Map<String, String> result = new LinkedHashMap<>();
        try {
            Method getKey = null;
            for (Method method : enumClass.getDeclaredMethods()) {
                String methodName = method.getName();
                if (method.getParameterCount() == 0
                        && (methodName.equals("getName")
                        || methodName.equals("getDescription")
                        || methodName.equals("getDesc"))) {
                    getKey = method;
                }
            }
            Object[] constants = enumClass.getEnumConstants();
            for (Object constant : constants) {
                String key = Objects.nonNull(getKey)
                        ? String.valueOf(getKey.invoke(constant))
                        : ((Enum<?>) constant).name();
                String value = ((Enum<?>) constant).name();
                result.put(key, value);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to build enum map for " + enumClass, e);
        }
        return result;
    }

    // ──────────── 构建 ────────────

    /**
     * 构建字段元数据对象。
     */
    public FiltroFieldMeta build() {
        String claimedFieldName = this.filtroAnnotation.field();
        String claimedKeyPath = this.filtroAnnotation.key();
        QueryIntent claimedIntent = this.filtroAnnotation.intent();

        QueryIntent queryIntent = (claimedIntent == null || claimedIntent == QueryIntent.AUTO)
                ? inferIntent(this.field.getType())
                : claimedIntent;

        FiltroFieldMeta filtroFieldMeta = new FiltroFieldMeta();
        filtroFieldMeta.setField(StringUtils.isNotBlank(claimedFieldName) ? claimedFieldName : this.field.getName())
                .setKey(StringUtils.isNotBlank(claimedKeyPath) ? claimedKeyPath
                        : this.field.getName().replaceAll("([a-z])([A-Z]+)", "$1_$2").toLowerCase())
                .setQueryIntent(queryIntent)
                .setLabel(this.filtroAnnotation.value())
                .setTooltip(this.filtroAnnotation.tooltip());

        if (this.filtroAnnotation.groups().length < 1) {
            filtroFieldMeta.setGroups(Collections.emptySet());
        } else {
            filtroFieldMeta.setGroups(Arrays.stream(this.filtroAnnotation.groups()).collect(Collectors.toSet()));
        }

        filtroFieldMeta.setMaxInSize(this.filtroAnnotation.maxInSize());

        // intent 提供默认操作符集，operators 做减法
        Set<FiltroOperator> fullSet = operatorsFor(queryIntent);
        if (claimedFiltroOperators.isEmpty()) {
            filtroFieldMeta.setSupportedOperations(fullSet);
        } else {
            Set<FiltroOperator> selected = new HashSet<>(claimedFiltroOperators);
            selected.retainAll(fullSet);

            // 自动补 ALT 操作符（如声明了 LT 则自动带上 ALT_LT）
            if (selected.contains(FiltroOperator.LT)) selected.add(FiltroOperator.ALT_LT);
            if (selected.contains(FiltroOperator.LTE)) selected.add(FiltroOperator.ALT_LTE);
            if (selected.contains(FiltroOperator.GT)) selected.add(FiltroOperator.ALT_GT);
            if (selected.contains(FiltroOperator.GTE)) selected.add(FiltroOperator.ALT_GTE);

            filtroFieldMeta.setSupportedOperations(Collections.unmodifiableSet(selected));
        }

        if (queryIntent == QueryIntent.CATEGORY) {
            @SuppressWarnings("unchecked")
            Class<? extends Enum<?>> enumClazz = (Class<? extends Enum<?>>) field.getType();
            filtroFieldMeta.setEnumerationClass(enumClazz);
            filtroFieldMeta.setEnumerationDictionary(toDict(enumClazz));
        }

        return filtroFieldMeta;
    }

    /**
     * 设置声明支持的操作符（减法模式）。
     */
    public FiltroFieldMetaBuilder setClaimedOperators(Set<FiltroOperator> claimedFiltroOperators) {
        this.claimedFiltroOperators = claimedFiltroOperators;
        return this;
    }
}
