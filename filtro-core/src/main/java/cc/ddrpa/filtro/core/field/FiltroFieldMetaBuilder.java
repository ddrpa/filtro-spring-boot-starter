package cc.ddrpa.filtro.core.field;

import cc.ddrpa.filtro.core.annotation.Filtro;
import cc.ddrpa.filtro.core.annotation.FiltroOneOf;
import cc.ddrpa.filtro.core.dictionary.FiltroDictionarySource;
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
            FiltroOperator.CONTAINS, FiltroOperator.NOT_CONTAINS,
            FiltroOperator.IS_NULL, FiltroOperator.NOT_NULL
    );

    private static final Set<FiltroOperator> EXACT_OPERATORS = Set.of(
            FiltroOperator.EQ, FiltroOperator.NEQ, FiltroOperator.NULLABLE_NEQ,
            FiltroOperator.IN, FiltroOperator.NOT_IN,
            FiltroOperator.IS_NULL, FiltroOperator.NOT_NULL
    );

    private static final Set<FiltroOperator> BOOLEAN_EXACT_OPERATORS = Set.of(
            FiltroOperator.EQ, FiltroOperator.NEQ,
            FiltroOperator.IS_NULL, FiltroOperator.NOT_NULL
    );

    private static final Set<FiltroOperator> RANGE_OPERATORS = Set.of(
            FiltroOperator.EQ, FiltroOperator.NEQ, FiltroOperator.NULLABLE_NEQ,
            FiltroOperator.GT, FiltroOperator.GTE, FiltroOperator.LT, FiltroOperator.LTE,
            FiltroOperator.ALT_GT, FiltroOperator.ALT_GTE, FiltroOperator.ALT_LT, FiltroOperator.ALT_LTE,
            FiltroOperator.IS_NULL, FiltroOperator.NOT_NULL
    );

    /** Float/Double：范围比较，默认无 EQ/NEQ */
    private static final Set<FiltroOperator> FLOAT_RANGE_OPERATORS = Set.of(
            FiltroOperator.GT, FiltroOperator.LT,
            FiltroOperator.ALT_GT, FiltroOperator.ALT_LT,
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
    public static QueryIntent inferIntent(Class<?> clazz) {
        if (Integer.class.equals(clazz) || int.class.equals(clazz)
                || Long.class.equals(clazz) || long.class.equals(clazz)
                || Short.class.equals(clazz) || short.class.equals(clazz)
                || Float.class.equals(clazz) || float.class.equals(clazz)
                || Double.class.equals(clazz) || double.class.equals(clazz)
                || BigDecimal.class.equals(clazz)
                || "org.bson.types.Decimal128".equals(clazz.getName())
                || java.time.LocalDate.class.equals(clazz)
                || java.time.LocalDateTime.class.equals(clazz)
                || java.time.Instant.class.equals(clazz)
                || java.time.LocalTime.class.equals(clazz)) {
            return QueryIntent.RANGE;
        } else if (Boolean.class.equals(clazz) || boolean.class.equals(clazz) || clazz.isEnum()) {
            return QueryIntent.EXACT;
        }
        return QueryIntent.SEARCH;
    }

    private static boolean isFloatType(Class<?> clazz) {
        return Float.class.equals(clazz) || float.class.equals(clazz)
                || Double.class.equals(clazz) || double.class.equals(clazz);
    }

    private static boolean isBooleanType(Class<?> clazz) {
        return Boolean.class.equals(clazz) || boolean.class.equals(clazz);
    }

    /**
     * 由 QueryIntent 定主集，个别用 Java Class 微调。
     */
    public static Set<FiltroOperator> operatorsFor(QueryIntent intent, Class<?> clazz) {
        return switch (intent) {
            case SEARCH -> SEARCH_OPERATORS;
            case EXACT -> isBooleanType(clazz) ? BOOLEAN_EXACT_OPERATORS : EXACT_OPERATORS;
            case RANGE -> isFloatType(clazz) ? FLOAT_RANGE_OPERATORS : RANGE_OPERATORS;
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

    /**
     * 将 {@code @FiltroOneOf} 值转为 identity 字典（label = value），过滤 blank、去重保序。
     */
    public static Map<String, String> toOneOfDict(String[] values) {
        if (values == null || values.length == 0) {
            return Collections.emptyMap();
        }
        Map<String, String> result = new LinkedHashMap<>();
        for (String value : values) {
            if (StringUtils.isBlank(value)) {
                continue;
            }
            result.putIfAbsent(value, value);
        }
        return result;
    }

    /**
     * 解析 {@link FiltroOneOf} 互斥来源。
     */
    static ResolvedOneOf resolveOneOf(FiltroOneOf anno, String fieldName) {
        if (anno == null) {
            return ResolvedOneOf.absent();
        }
        Map<String, String> valueDict = toOneOfDict(anno.value());
        boolean hasValue = !valueDict.isEmpty();
        boolean hasEnum = anno.asEnum() != null
                && anno.asEnum() != FiltroOneOf.Unspecified.class
                && anno.asEnum().isEnum();
        boolean hasSource = anno.source() != null
                && anno.source() != FiltroDictionarySource.None.class;

        int count = (hasValue ? 1 : 0) + (hasEnum ? 1 : 0) + (hasSource ? 1 : 0);
        if (count == 0) {
            return ResolvedOneOf.absent();
        }
        if (count > 1) {
            throw new IllegalArgumentException(
                    "Field '" + fieldName + "': @FiltroOneOf allows at most one of value / asEnum / source");
        }
        if (hasValue) {
            return ResolvedOneOf.staticDict(valueDict);
        }
        if (hasEnum) {
            @SuppressWarnings("unchecked")
            Class<? extends Enum<?>> enumClazz = (Class<? extends Enum<?>>) anno.asEnum();
            return ResolvedOneOf.staticDict(toDict(enumClazz));
        }
        return ResolvedOneOf.source(anno.source());
    }

    // ──────────── 构建 ────────────

    /**
     * 构建字段元数据对象。
     */
    public FiltroFieldMeta build() {
        String claimedFieldName = this.filtroAnnotation.field();
        String claimedKeyPath = this.filtroAnnotation.key();
        QueryIntent claimedIntent = this.filtroAnnotation.intent();
        Class<?> javaType = this.field.getType();

        FiltroOneOf oneOfAnno = this.field.getAnnotation(FiltroOneOf.class);
        ResolvedOneOf oneOf = resolveOneOf(oneOfAnno, this.field.getName());
        boolean hasOneOf = oneOf.present();

        if (hasOneOf && javaType.isEnum()) {
            throw new IllegalArgumentException(
                    "Field '" + this.field.getName() + "' is an enum and must not declare @FiltroOneOf");
        }
        if (hasOneOf && claimedIntent == QueryIntent.SEARCH) {
            throw new IllegalArgumentException(
                    "Field '" + this.field.getName() + "': @FiltroOneOf conflicts with QueryIntent.SEARCH");
        }

        QueryIntent queryIntent;
        if (claimedIntent == null || claimedIntent == QueryIntent.AUTO) {
            queryIntent = hasOneOf ? QueryIntent.EXACT : inferIntent(javaType);
        } else {
            queryIntent = claimedIntent;
        }

        FiltroFieldMeta filtroFieldMeta = new FiltroFieldMeta();
        filtroFieldMeta.setField(StringUtils.isNotBlank(claimedFieldName) ? claimedFieldName : this.field.getName())
                .setKey(StringUtils.isNotBlank(claimedKeyPath) ? claimedKeyPath
                        : this.field.getName().replaceAll("([a-z])([A-Z]+)", "$1_$2").toLowerCase())
                .setQueryIntent(queryIntent)
                .setJavaType(javaType)
                .setLabel(this.filtroAnnotation.value())
                .setTooltip(this.filtroAnnotation.tooltip());

        if (this.filtroAnnotation.groups().length < 1) {
            filtroFieldMeta.setGroups(Collections.emptySet());
        } else {
            filtroFieldMeta.setGroups(Arrays.stream(this.filtroAnnotation.groups()).collect(Collectors.toSet()));
        }

        Set<FiltroOperator> fullSet = operatorsFor(queryIntent, javaType);
        if (claimedFiltroOperators.isEmpty()) {
            filtroFieldMeta.setSupportedOperations(fullSet);
        } else {
            Set<FiltroOperator> selected = new HashSet<>(claimedFiltroOperators);
            selected.retainAll(fullSet);

            if (selected.contains(FiltroOperator.LT)) selected.add(FiltroOperator.ALT_LT);
            if (selected.contains(FiltroOperator.LTE)) selected.add(FiltroOperator.ALT_LTE);
            if (selected.contains(FiltroOperator.GT)) selected.add(FiltroOperator.ALT_GT);
            if (selected.contains(FiltroOperator.GTE)) selected.add(FiltroOperator.ALT_GTE);

            filtroFieldMeta.setSupportedOperations(Collections.unmodifiableSet(selected));
        }

        if (javaType.isEnum()) {
            @SuppressWarnings("unchecked")
            Class<? extends Enum<?>> enumClazz = (Class<? extends Enum<?>>) javaType;
            filtroFieldMeta.setEnumerationClass(enumClazz);
            filtroFieldMeta.setEnumerationDictionary(toDict(enumClazz));
        } else if (oneOf.staticDictionary != null) {
            filtroFieldMeta.setEnumerationDictionary(oneOf.staticDictionary);
        } else if (oneOf.sourceClass != null) {
            filtroFieldMeta.setDictionarySourceClass(oneOf.sourceClass);
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

    static final class ResolvedOneOf {
        final Map<String, String> staticDictionary;
        final Class<? extends FiltroDictionarySource> sourceClass;

        private ResolvedOneOf(Map<String, String> staticDictionary,
                              Class<? extends FiltroDictionarySource> sourceClass) {
            this.staticDictionary = staticDictionary;
            this.sourceClass = sourceClass;
        }

        static ResolvedOneOf absent() {
            return new ResolvedOneOf(null, null);
        }

        static ResolvedOneOf staticDict(Map<String, String> dict) {
            return new ResolvedOneOf(dict, null);
        }

        static ResolvedOneOf source(Class<? extends FiltroDictionarySource> sourceClass) {
            return new ResolvedOneOf(null, sourceClass);
        }

        boolean present() {
            return staticDictionary != null || sourceClass != null;
        }
    }
}
