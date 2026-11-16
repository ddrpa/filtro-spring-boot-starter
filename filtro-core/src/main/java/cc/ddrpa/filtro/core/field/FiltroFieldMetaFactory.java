package cc.ddrpa.filtro.core.field;

import cc.ddrpa.filtro.core.dictionary.FiltroDictionarySource;
import org.apache.commons.lang3.StringUtils;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 无 Java {@link java.lang.reflect.Field} 的元数据构建入口，用于 schemaless / 动态字段。
 */
public final class FiltroFieldMetaFactory {

    private FiltroFieldMetaFactory() {
    }

    /**
     * @param field    RSQL selector，必填
     * @param intent   查询意图，不可为 {@link QueryIntent#AUTO}
     * @param javaType Java 类型，必填（Visitor 值转型依赖）
     */
    public static Builder create(String field, QueryIntent intent, Class<?> javaType) {
        Objects.requireNonNull(field, "field");
        Objects.requireNonNull(intent, "intent");
        Objects.requireNonNull(javaType, "javaType");
        if (StringUtils.isBlank(field)) {
            throw new IllegalArgumentException("field must not be blank");
        }
        if (intent == QueryIntent.AUTO) {
            throw new IllegalArgumentException("QueryIntent.AUTO is only valid on @Filtro; resolve intent before create()");
        }
        return new Builder(field, intent, javaType);
    }

    public static final class Builder {
        private final String field;
        private final QueryIntent intent;
        private final Class<?> javaType;
        private String key;
        private String label = "";
        private String tooltip = "";
        private Set<FiltroOperator> claimedOperators = Collections.emptySet();
        private Set<Class<?>> groups = Collections.emptySet();
        private Class<?> enumerationClass;
        private java.util.Map<String, String> enumerationDictionary;
        private Class<? extends FiltroDictionarySource> dictionarySourceClass;

        private Builder(String field, QueryIntent intent, Class<?> javaType) {
            this.field = field;
            this.intent = intent;
            this.javaType = javaType;
        }

        public Builder key(String key) {
            this.key = key;
            return this;
        }

        public Builder label(String label) {
            this.label = label == null ? "" : label;
            return this;
        }

        public Builder tooltip(String tooltip) {
            this.tooltip = tooltip == null ? "" : tooltip;
            return this;
        }

        /** 相对 intent 默认集做减法 */
        public Builder operators(FiltroOperator... operators) {
            if (operators == null || operators.length == 0) {
                this.claimedOperators = Collections.emptySet();
            } else {
                this.claimedOperators = Set.of(operators);
            }
            return this;
        }

        public Builder groups(Class<?>... groups) {
            if (groups == null || groups.length == 0) {
                this.groups = Collections.emptySet();
            } else {
                this.groups = Arrays.stream(groups).collect(Collectors.toSet());
            }
            return this;
        }

        public Builder enumerationClass(Class<?> enumerationClass) {
            this.enumerationClass = enumerationClass;
            return this;
        }

        public Builder enumerationDictionary(java.util.Map<String, String> enumerationDictionary) {
            this.enumerationDictionary = enumerationDictionary;
            return this;
        }

        /**
         * 离散可选值（label = value），等同 {@code @FiltroOneOf(value = ...)}。
         */
        public Builder oneOf(String... values) {
            this.enumerationDictionary = FiltroFieldMetaBuilder.toOneOfDict(values);
            this.dictionarySourceClass = null;
            return this;
        }

        /**
         * 借用 Enum 字典，等同 {@code @FiltroOneOf(asEnum = ...)}。
         */
        public Builder oneOfEnum(Class<? extends Enum<?>> enumClass) {
            Objects.requireNonNull(enumClass, "enumClass");
            this.enumerationDictionary = FiltroFieldMetaBuilder.toDict(enumClass);
            this.dictionarySourceClass = null;
            return this;
        }

        /**
         * Spring Bean 码表类型，等同 {@code @FiltroOneOf(source = ...)}；元数据请求时再解析。
         */
        public Builder oneOfSource(Class<? extends FiltroDictionarySource> sourceClass) {
            Objects.requireNonNull(sourceClass, "sourceClass");
            if (sourceClass == FiltroDictionarySource.None.class) {
                throw new IllegalArgumentException("sourceClass must not be FiltroDictionarySource.None");
            }
            this.dictionarySourceClass = sourceClass;
            this.enumerationDictionary = null;
            return this;
        }

        public FiltroFieldMeta build() {
            FiltroFieldMeta meta = new FiltroFieldMeta();
            meta.setField(field)
                    .setKey(StringUtils.isNotBlank(key)
                            ? key
                            : field.replaceAll("([a-z])([A-Z]+)", "$1_$2").toLowerCase())
                    .setQueryIntent(intent)
                    .setJavaType(javaType)
                    .setLabel(label)
                    .setTooltip(tooltip)
                    .setGroups(groups);

            Set<FiltroOperator> fullSet = FiltroFieldMetaBuilder.operatorsFor(intent, javaType);
            if (claimedOperators.isEmpty()) {
                meta.setSupportedOperations(fullSet);
            } else {
                Set<FiltroOperator> selected = new HashSet<>(claimedOperators);
                selected.retainAll(fullSet);
                if (selected.contains(FiltroOperator.LT)) selected.add(FiltroOperator.ALT_LT);
                if (selected.contains(FiltroOperator.LTE)) selected.add(FiltroOperator.ALT_LTE);
                if (selected.contains(FiltroOperator.GT)) selected.add(FiltroOperator.ALT_GT);
                if (selected.contains(FiltroOperator.GTE)) selected.add(FiltroOperator.ALT_GTE);
                meta.setSupportedOperations(Collections.unmodifiableSet(selected));
            }

            if (javaType.isEnum()) {
                @SuppressWarnings("unchecked")
                Class<? extends Enum<?>> enumClazz = (Class<? extends Enum<?>>) javaType;
                meta.setEnumerationClass(enumClazz);
                if (enumerationDictionary != null) {
                    meta.setEnumerationDictionary(enumerationDictionary);
                } else {
                    meta.setEnumerationDictionary(FiltroFieldMetaBuilder.toDict(enumClazz));
                }
            } else if (enumerationClass != null) {
                meta.setEnumerationClass(enumerationClass);
                if (enumerationDictionary != null) {
                    meta.setEnumerationDictionary(enumerationDictionary);
                } else if (enumerationClass.isEnum()) {
                    @SuppressWarnings("unchecked")
                    Class<? extends Enum<?>> enumClazz = (Class<? extends Enum<?>>) enumerationClass;
                    meta.setEnumerationDictionary(FiltroFieldMetaBuilder.toDict(enumClazz));
                }
            } else if (dictionarySourceClass != null) {
                meta.setDictionarySourceClass(dictionarySourceClass);
            } else if (enumerationDictionary != null && !enumerationDictionary.isEmpty()) {
                meta.setEnumerationDictionary(enumerationDictionary);
            }

            return meta;
        }
    }
}
