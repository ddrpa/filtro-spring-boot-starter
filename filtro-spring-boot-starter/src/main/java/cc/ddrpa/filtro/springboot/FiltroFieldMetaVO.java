package cc.ddrpa.filtro.springboot;

import cc.ddrpa.filtro.core.field.FiltroFieldMeta;
import cc.ddrpa.filtro.core.field.FiltroOperator;
import cc.ddrpa.filtro.core.field.QueryIntent;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Map;
import java.util.Set;

public class FiltroFieldMetaVO {
    // RSQL 字段名
    private String field;

    // 查询意图
    private QueryIntent queryIntent;

    // 前端控件类型
    private FiltroComponent component;

    // 支持的操作符
    private Set<FiltroOperator> supportedOperations;

    // 描述说明（展示名）
    private String label;

    // 悬停提示
    private String tooltip;

    // 枚举选项
    private Map<String, String> dictionary;

    public static FiltroFieldMetaVO from(FiltroFieldMeta meta) {
        FiltroFieldMetaVO vo = new FiltroFieldMetaVO();
        vo.setField(meta.getField());
        vo.setQueryIntent(meta.getQueryIntent());
        vo.setComponent(inferComponent(meta));
        vo.setSupportedOperations(meta.getSupportedOperations());
        vo.setLabel(meta.getLabel());
        vo.setTooltip(meta.getTooltip());
        if (meta.isEnumeration()) {
            vo.setDictionary(meta.getEnumerationDictionary());
        }
        return vo;
    }

    static FiltroComponent inferComponent(FiltroFieldMeta meta) {
        if (meta.isEnumeration()) {
            return FiltroComponent.SELECT;
        }
        Class<?> type = meta.getJavaType();
        if (type == null) {
            return FiltroComponent.TEXT;
        }
        if (Boolean.class.equals(type) || boolean.class.equals(type)) {
            return FiltroComponent.CHECKBOX;
        }
        if (LocalDate.class.equals(type)) {
            return FiltroComponent.DATE;
        }
        if (LocalDateTime.class.equals(type) || Instant.class.equals(type) || LocalTime.class.equals(type)) {
            return FiltroComponent.DATETIME;
        }
        if (Integer.class.equals(type) || int.class.equals(type)
                || Long.class.equals(type) || long.class.equals(type)
                || Short.class.equals(type) || short.class.equals(type)
                || Float.class.equals(type) || float.class.equals(type)
                || Double.class.equals(type) || double.class.equals(type)
                || BigDecimal.class.equals(type)
                || "org.bson.types.Decimal128".equals(type.getName())) {
            return FiltroComponent.NUMBER;
        }
        return FiltroComponent.TEXT;
    }

    public String getField() {
        return field;
    }

    public FiltroFieldMetaVO setField(String field) {
        this.field = field;
        return this;
    }

    public QueryIntent getQueryIntent() {
        return queryIntent;
    }

    public FiltroFieldMetaVO setQueryIntent(QueryIntent queryIntent) {
        this.queryIntent = queryIntent;
        return this;
    }

    public FiltroComponent getComponent() {
        return component;
    }

    public FiltroFieldMetaVO setComponent(FiltroComponent component) {
        this.component = component;
        return this;
    }

    public Set<FiltroOperator> getSupportedOperations() {
        return supportedOperations;
    }

    public FiltroFieldMetaVO setSupportedOperations(Set<FiltroOperator> supportedOperations) {
        this.supportedOperations = supportedOperations;
        return this;
    }

    public String getLabel() {
        return label;
    }

    public FiltroFieldMetaVO setLabel(String label) {
        this.label = label;
        return this;
    }

    public String getTooltip() {
        return tooltip;
    }

    public FiltroFieldMetaVO setTooltip(String tooltip) {
        this.tooltip = tooltip;
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
