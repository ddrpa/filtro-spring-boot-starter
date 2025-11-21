package cc.ddrpa.filtro.core.field;

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

    // 数据类型
    private FiltroValueType filtroValueType;

    // 支持的操作符
    private Set<FiltroOperator> supportedOperations;

    // 描述说明
    private String description;

    // 枚举相关
    private Class<?> enumerationClass;
    private Map<String, String> enumerationDictionary;

    /**
     * 适用分组列表
     */
    private Set<Class<?>> groups;

    /**
     * 判断是否为枚举类型
     */
    public boolean isEnumeration() {
        return FiltroValueType.ENUMERATION.equals(filtroValueType);
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

    public FiltroValueType getFiltroValueType() {
        return filtroValueType;
    }

    public FiltroFieldMeta setFiltroValueType(FiltroValueType filtroValueType) {
        this.filtroValueType = filtroValueType;
        return this;
    }

    public Set<FiltroOperator> getSupportedOperations() {
        return supportedOperations;
    }

    public FiltroFieldMeta setSupportedOperations(Set<FiltroOperator> supportedOperations) {
        this.supportedOperations = supportedOperations;
        return this;
    }

    public String getDescription() {
        return description;
    }

    public FiltroFieldMeta setDescription(String description) {
        this.description = description;
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

    public Set<Class<?>> getGroups() {
        return groups;
    }

    public FiltroFieldMeta setGroups(Set<Class<?>> groups) {
        this.groups = groups;
        return this;
    }
}