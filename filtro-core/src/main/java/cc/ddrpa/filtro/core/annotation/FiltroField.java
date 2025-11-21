package cc.ddrpa.filtro.core.annotation;

import cc.ddrpa.filtro.core.field.FiltroOperator;
import cc.ddrpa.filtro.core.field.FiltroValueType;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记查询实体类的字段，定义该字段的过滤规则
 */
@Target({ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
public @interface FiltroField {
    /**
     * 字段描述，将在元数据文档中体现
     */
    String value() default "";

    /**
     * RSQL 查询中使用的字段名，默认与属性名相同
     */
    String field() default "";

    /**
     * 数据库列名或嵌套字段路径，用于构造查询条件，默认使用属性名的下划线形式
     */
    String key() default "";

    /**
     * 字段类型，若不声明，则由系统根据数据类型自动推断
     */
    FiltroValueType type() default FiltroValueType.UNDECIDED;

    /**
     * 支持的操作符列表，若不声明，则由系统根据字段类型自动推断
     */
    FiltroOperator[] operators() default {};

    /**
     * 适用分组，参考 Jakarta Bean Validation 的 group 概念
     */
    Class<?>[] groups() default {};
}
