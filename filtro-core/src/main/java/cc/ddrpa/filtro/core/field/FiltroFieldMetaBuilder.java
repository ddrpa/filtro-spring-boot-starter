package cc.ddrpa.filtro.core.field;

import cc.ddrpa.filtro.core.annotation.FiltroField;
import org.apache.commons.lang3.StringUtils;
import org.bson.types.Decimal128;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 字段元数据构建器，根据注解和字段类型自动推断配置信息
 */
public class FiltroFieldMetaBuilder {
    private static final Set<FiltroOperator> INT_FILTRO_OPERATORS = Set.of(
            FiltroOperator.EQ, FiltroOperator.NEQ,
            FiltroOperator.GT, FiltroOperator.GTE, FiltroOperator.LT, FiltroOperator.LTE,
            FiltroOperator.ALT_GT, FiltroOperator.ALT_GTE, FiltroOperator.ALT_LT, FiltroOperator.ALT_LTE,
            FiltroOperator.IN, FiltroOperator.NOT_IN
    );
    private static final Set<FiltroOperator> FLOAT_FILTRO_OPERATORS = Set.of(
            FiltroOperator.GT, FiltroOperator.LT, FiltroOperator.ALT_GT, FiltroOperator.ALT_LT,
            FiltroOperator.IN, FiltroOperator.NOT_IN
    );
    private static final Set<FiltroOperator> DECIMAL_FILTRO_OPERATORS = Set.of(
            FiltroOperator.EQ, FiltroOperator.NEQ,
            FiltroOperator.GT, FiltroOperator.GTE, FiltroOperator.LT, FiltroOperator.LTE,
            FiltroOperator.ALT_GT, FiltroOperator.ALT_GTE, FiltroOperator.ALT_LT, FiltroOperator.ALT_LTE,
            FiltroOperator.IN, FiltroOperator.NOT_IN
    );
    private static final Set<FiltroOperator> STRING_FILTRO_OPERATORS = Set.of(
            FiltroOperator.EQ, FiltroOperator.NEQ,
            FiltroOperator.IN, FiltroOperator.NOT_IN,
            FiltroOperator.PREFIX, FiltroOperator.SUFFIX, FiltroOperator.CONTAINS
    );
    private static final Set<FiltroOperator> DATETIME_FILTRO_OPERATORS = Set.of(
            FiltroOperator.EQ, FiltroOperator.NEQ,
            FiltroOperator.GT, FiltroOperator.GTE, FiltroOperator.LT, FiltroOperator.LTE,
            FiltroOperator.ALT_GT, FiltroOperator.ALT_GTE, FiltroOperator.ALT_LT, FiltroOperator.ALT_LTE,
            FiltroOperator.IN, FiltroOperator.NOT_IN
    );
    private static final Set<FiltroOperator> BOOLEAN_FILTRO_OPERATORS = Set.of(
            FiltroOperator.EQ, FiltroOperator.NEQ
    );
    private static final Set<FiltroOperator> ENUMERATION_FILTRO_OPERATORS = Set.of(
            FiltroOperator.EQ, FiltroOperator.NEQ,
            FiltroOperator.IN, FiltroOperator.NOT_IN
    );
    private final Field field;
    private final FiltroField filtroFieldAnnotation;
    private Set<FiltroOperator> claimedFiltroOperators = Collections.emptySet();

    public FiltroFieldMetaBuilder(Field field, FiltroField filtroFieldAnnotation) {
        this.field = field;
        this.filtroFieldAnnotation = filtroFieldAnnotation;
    }

    /**
     * 根据 Java 类型推断 FiltroValueType
     */
    private static FiltroValueType inferType(Class<?> clazz) {
        if (Integer.class.equals(clazz) || int.class.equals(clazz)
                || Long.class.equals(clazz) || long.class.equals(clazz)
                || Short.class.equals(clazz) || short.class.equals(clazz)) {
            return FiltroValueType.INT;
        } else if (Float.class.equals(clazz) || float.class.equals(clazz)
                || Double.class.equals(clazz) || double.class.equals(clazz)) {
            return FiltroValueType.FLOAT;
        } else if (BigDecimal.class.equals(clazz) || Decimal128.class.equals(clazz)) {
            return FiltroValueType.DECIMAL;
        } else if (Boolean.class.equals(clazz) || boolean.class.equals(clazz)) {
            return FiltroValueType.BOOLEAN;
        } else if (java.time.LocalDate.class.equals(clazz) || java.time.LocalDateTime.class.equals(clazz)
                || java.time.Instant.class.equals(clazz) || java.time.LocalTime.class.equals(clazz)) {
            return FiltroValueType.DATETIME;
        } else if (clazz.isEnum()) {
            return FiltroValueType.ENUMERATION;
        }
        return FiltroValueType.STRING;
    }

    /**
     * 将枚举类转换为字典映射（显示名称 -> 枚举值名称）
     * 优先使用 getName()、getDescription() 或 getDesc() 方法获取显示名称
     */
    public static Map<String, String> toDict(Class<? extends Enum<?>> enumClass) {
        Map<String, String> result = new LinkedHashMap<>();

        try {
            Method getKey = null;

            // 尝试找到可用的方法
            for (Method method : enumClass.getDeclaredMethods()) {
                String methodName = method.getName();
                if (method.getParameterCount() == 0
                        && (
                        methodName.equals("getName")
                                || methodName.equals("getDescription")
                                || methodName.equals("getDesc"))
                ) {
                    getKey = method;
                }
            }

            Object[] constants = enumClass.getEnumConstants();
            for (Object constant : constants) {
                String key;
                String value;

                // 优先 description
                if (Objects.nonNull(getKey)) {
                    key = String.valueOf(getKey.invoke(constant));
                } else {
                    key = ((Enum<?>) constant).name();
                }
                value = ((Enum<?>) constant).name();
                result.put(key, value);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to build enum map for " + enumClass, e);
        }

        return result;
    }

    /**
     * 构建字段元数据对象
     */
    public FiltroFieldMeta build() {
        String claimedFieldName = this.filtroFieldAnnotation.field();
        String claimedKeyPath = this.filtroFieldAnnotation.key();
        FiltroValueType claimedFiltroValueType = this.filtroFieldAnnotation.type();

        FiltroValueType filtroValueType = Objects.isNull(claimedFiltroValueType) || claimedFiltroValueType.equals(FiltroValueType.UNDECIDED) ? inferType(this.field.getType()) : claimedFiltroValueType;

        FiltroFieldMeta filtroFieldMeta = new FiltroFieldMeta();
        filtroFieldMeta.setField(StringUtils.isNotBlank(claimedFieldName) ? claimedFieldName : this.field.getName())
                .setKey(StringUtils.isNotBlank(claimedKeyPath) ? claimedKeyPath : this.field.getName().replaceAll("([a-z])([A-Z]+)", "$1_$2").toLowerCase())
                .setFiltroValueType(filtroValueType)
                .setDescription(this.filtroFieldAnnotation.value());
        if (this.filtroFieldAnnotation.groups().length < 1) {
            filtroFieldMeta.setGroups(Collections.emptySet());
        } else {
            filtroFieldMeta.setGroups(Arrays.stream(this.filtroFieldAnnotation.groups()).collect(Collectors.toSet()));
        }

        if (Objects.isNull(claimedFiltroOperators) || claimedFiltroOperators.isEmpty()) {
            switch (filtroValueType) {
                case INT -> filtroFieldMeta.setSupportedOperations(INT_FILTRO_OPERATORS);
                case FLOAT -> filtroFieldMeta.setSupportedOperations(FLOAT_FILTRO_OPERATORS);
                case DECIMAL -> filtroFieldMeta.setSupportedOperations(DECIMAL_FILTRO_OPERATORS);
                case STRING -> filtroFieldMeta.setSupportedOperations(STRING_FILTRO_OPERATORS);
                case DATETIME -> filtroFieldMeta.setSupportedOperations(DATETIME_FILTRO_OPERATORS);
                case BOOLEAN -> filtroFieldMeta.setSupportedOperations(BOOLEAN_FILTRO_OPERATORS);
                case ENUMERATION -> filtroFieldMeta.setSupportedOperations(ENUMERATION_FILTRO_OPERATORS);
            }
        } else {
            Set<FiltroOperator> supportedFiltroOperators = new HashSet<>(claimedFiltroOperators);
            switch (filtroValueType) {
                case INT -> supportedFiltroOperators.retainAll(INT_FILTRO_OPERATORS);
                case FLOAT -> supportedFiltroOperators.retainAll(FLOAT_FILTRO_OPERATORS);
                case DECIMAL -> supportedFiltroOperators.retainAll(DECIMAL_FILTRO_OPERATORS);
                case STRING -> supportedFiltroOperators.retainAll(STRING_FILTRO_OPERATORS);
                case DATETIME -> supportedFiltroOperators.retainAll(DATETIME_FILTRO_OPERATORS);
                case BOOLEAN -> supportedFiltroOperators.retainAll(BOOLEAN_FILTRO_OPERATORS);
                case ENUMERATION -> supportedFiltroOperators.retainAll(ENUMERATION_FILTRO_OPERATORS);
            }
            // 自动添加替代比较操作符，需要更好的方法
            // 自动添加替代比较操作符（如 =gt= 是 > 的替代形式）
            if (supportedFiltroOperators.contains(FiltroOperator.LT)) {
                supportedFiltroOperators.add(FiltroOperator.ALT_LT);
            }
            if (supportedFiltroOperators.contains(FiltroOperator.LTE)) {
                supportedFiltroOperators.add(FiltroOperator.ALT_LTE);
            }
            if (supportedFiltroOperators.contains(FiltroOperator.GT)) {
                supportedFiltroOperators.add(FiltroOperator.ALT_GT);
            }
            if (supportedFiltroOperators.contains(FiltroOperator.GTE)) {
                supportedFiltroOperators.add(FiltroOperator.ALT_GTE);
            }
            filtroFieldMeta.setSupportedOperations(supportedFiltroOperators);
        }

        if (filtroValueType.equals(FiltroValueType.ENUMERATION)) {
            Class<? extends Enum<?>> enumClazz = (Class<? extends Enum<?>>) field.getType();
            filtroFieldMeta.setEnumerationClass(enumClazz);
            filtroFieldMeta.setEnumerationDictionary(toDict(enumClazz));
        }

        return filtroFieldMeta;
    }

    /**
     * 设置声明支持的操作符
     */
    public FiltroFieldMetaBuilder setClaimedOperators(Set<FiltroOperator> claimedFiltroOperators) {
        this.claimedFiltroOperators = claimedFiltroOperators;
        return this;
    }
}