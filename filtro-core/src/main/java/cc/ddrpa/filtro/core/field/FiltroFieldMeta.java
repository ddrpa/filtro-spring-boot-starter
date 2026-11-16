package cc.ddrpa.filtro.core.field;

import cc.ddrpa.filtro.core.dictionary.FiltroDictionarySource;

import java.util.Map;
import java.util.Set;

/**
 * 字段元数据，包含字段的过滤规则和配置信息
 */
public class FiltroFieldMeta {
    // RSQL 字段名
    private String field;

    // 数据库 字段名 or key-path
    private String key;

    // 查询意图
    private QueryIntent queryIntent;

    // Java 字段类型（用于值转型与控件提示）
    private Class<?> javaType;

    // 支持的操作符
    private Set<FiltroOperator> supportedOperations;

    // 描述说明
    private String label;

    // 提示
    private String tooltip;

    // 枚举相关
    private Class<?> enumerationClass;
    private Map<String, String> enumerationDictionary;

    /**
     * {@code @FiltroOneOf(source = ...)} 声明的码表 Bean 类型；元数据请求时再解析。
     */
    private Class<? extends FiltroDictionarySource> dictionarySourceClass;

    /**
     * 适用分组列表
     */
    private Set<Class<?>> groups;

    /**
     * 是否携带可选字典（静态 dict 或延迟 source），供前端 SELECT。
     */
    public boolean hasDictionary() {
        return (enumerationDictionary != null && !enumerationDictionary.isEmpty())
                || dictionarySourceClass != null;
    }

    /**
     * 字段 Java 类型是否为枚举（Visitor 值转型用）。
     * 与 {@link #hasDictionary()} 分离：{@code @FiltroOneOf} 仅有字典、不按枚举转型。
     */
    public boolean isEnumeration() {
        return javaType != null && javaType.isEnum();
    }

    public String getField() {
        return field;
    }

    public FiltroFieldMeta setField(String field) {
        this.field = field;
        return this;
    }

    public String getKey() {
        return key;
    }

    public FiltroFieldMeta setKey(String key) {
        this.key = key;
        return this;
    }

    public QueryIntent getQueryIntent() {
        return queryIntent;
    }

    public FiltroFieldMeta setQueryIntent(QueryIntent queryIntent) {
        this.queryIntent = queryIntent;
        return this;
    }

    public Class<?> getJavaType() {
        return javaType;
    }

    public FiltroFieldMeta setJavaType(Class<?> javaType) {
        this.javaType = javaType;
        return this;
    }

    public Set<FiltroOperator> getSupportedOperations() {
        return supportedOperations;
    }

    public FiltroFieldMeta setSupportedOperations(Set<FiltroOperator> supportedOperations) {
        this.supportedOperations = supportedOperations;
        return this;
    }

    public String getLabel() {
        return label;
    }

    public FiltroFieldMeta setLabel(String label) {
        this.label = label;
        return this;
    }

    public String getTooltip() {
        return tooltip;
    }

    public FiltroFieldMeta setTooltip(String tooltip) {
        this.tooltip = tooltip;
        return this;
    }

    public Class<?> getEnumerationClass() {
        return enumerationClass;
    }

    public FiltroFieldMeta setEnumerationClass(Class<?> enumerationClass) {
        this.enumerationClass = enumerationClass;
        return this;
    }

    public Map<String, String> getEnumerationDictionary() {
        return enumerationDictionary;
    }

    public FiltroFieldMeta setEnumerationDictionary(Map<String, String> enumerationDictionary) {
        this.enumerationDictionary = enumerationDictionary;
        return this;
    }

    public Class<? extends FiltroDictionarySource> getDictionarySourceClass() {
        return dictionarySourceClass;
    }

    public FiltroFieldMeta setDictionarySourceClass(Class<? extends FiltroDictionarySource> dictionarySourceClass) {
        this.dictionarySourceClass = dictionarySourceClass;
        return this;
    }

    public Set<Class<?>> getGroups() {
        return groups;
    }

    public FiltroFieldMeta setGroups(Set<Class<?>> groups) {
        this.groups = groups;
        return this;
    }
}
