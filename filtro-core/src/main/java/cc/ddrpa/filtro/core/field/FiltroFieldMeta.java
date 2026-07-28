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

    // 查询意图
    private QueryIntent queryIntent;

    // 支持的操作符
    private Set<FiltroOperator> supportedOperations;

    // 描述说明
    private String description;

    // 枚举相关
    private Class<?> enumerationClass;
    private Map<String, String> enumerationDictionary;

    /**
     * IN / NOT_IN 参数数量上限，0 表示不限制
     */
    private int maxInSize = 0;

    /**
     * 适用分组列表
     */
    private Set<Class<?>> groups;

    /**
     * 判断是否为枚举类型
     */
    public boolean isEnumeration() {
        return QueryIntent.CATEGORY.equals(queryIntent);
    }

    public String getField() {
        return field;
    }

    FiltroFieldMeta setField(String field) {
        this.field = field;
        return this;
    }

    public String getKey() {
        return key;
    }

    FiltroFieldMeta setKey(String key) {
        this.key = key;
        return this;
    }

    public QueryIntent getQueryIntent() {
        return queryIntent;
    }

    FiltroFieldMeta setQueryIntent(QueryIntent queryIntent) {
        this.queryIntent = queryIntent;
        return this;
    }

    public Set<FiltroOperator> getSupportedOperations() {
        return supportedOperations;
    }

    FiltroFieldMeta setSupportedOperations(Set<FiltroOperator> supportedOperations) {
        this.supportedOperations = supportedOperations;
        return this;
    }

    public String getDescription() {
        return description;
    }

    FiltroFieldMeta setDescription(String description) {
        this.description = description;
        return this;
    }

    public Class<?> getEnumerationClass() {
        return enumerationClass;
    }

    FiltroFieldMeta setEnumerationClass(Class<?> enumerationClass) {
        this.enumerationClass = enumerationClass;
        return this;
    }

    public Map<String, String> getEnumerationDictionary() {
        return enumerationDictionary;
    }

    FiltroFieldMeta setEnumerationDictionary(Map<String, String> enumerationDictionary) {
        this.enumerationDictionary = enumerationDictionary;
        return this;
    }

    public Set<Class<?>> getGroups() {
        return groups;
    }

    FiltroFieldMeta setGroups(Set<Class<?>> groups) {
        this.groups = groups;
        return this;
    }

    public int getMaxInSize() {
        return maxInSize;
    }

    FiltroFieldMeta setMaxInSize(int maxInSize) {
        this.maxInSize = maxInSize;
        return this;
    }
}