package cc.ddrpa.filtro.springboot;

import cc.ddrpa.filtro.core.field.FiltroFieldMeta;
import cc.ddrpa.filtro.core.field.FiltroOperator;
import cc.ddrpa.filtro.core.field.FiltroValueType;

import java.util.Map;
import java.util.Set;

public class FiltroFieldMetaVO {
    // RSQL 字段名
    private String field;

    // 数据类型
    private FiltroValueType filtroValueType;

    // 支持的操作符
    private Set<FiltroOperator> supportedOperations;

    // 描述说明
    private String description;

    // 枚举选项
    private Map<String, String> dictionary;

    public static FiltroFieldMetaVO from(FiltroFieldMeta meta) {
        FiltroFieldMetaVO vo = new FiltroFieldMetaVO();
        vo.setField(meta.getField());
        vo.setFiltroValueType(meta.getFiltroValueType());
        vo.setSupportedOperations(meta.getSupportedOperations());
        vo.setDescription(meta.getDescription());
        if (meta.isEnumeration()) {
            vo.setDictionary(meta.getEnumerationDictionary());
        }
        return vo;
    }

    public String getField() {
        return field;
    }

    public FiltroFieldMetaVO setField(String field) {
        this.field = field;
        return this;
    }

    public FiltroValueType getFiltroValueType() {
        return filtroValueType;
    }

    public FiltroFieldMetaVO setFiltroValueType(FiltroValueType filtroValueType) {
        this.filtroValueType = filtroValueType;
        return this;
    }

    public Set<FiltroOperator> getSupportedOperations() {
        return supportedOperations;
    }

    public FiltroFieldMetaVO setSupportedOperations(Set<FiltroOperator> supportedOperations) {
        this.supportedOperations = supportedOperations;
        return this;
    }

    public String getDescription() {
        return description;
    }

    public FiltroFieldMetaVO setDescription(String description) {
        this.description = description;
        return this;
    }

    public Map<String, String> getDictionary() {
        return dictionary;
    }

    public FiltroFieldMetaVO setDictionary(Map<String, String> dictionary) {
        this.dictionary = dictionary;
        return this;
    }
}