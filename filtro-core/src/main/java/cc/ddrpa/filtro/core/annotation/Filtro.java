package cc.ddrpa.filtro.core.annotation;

import cc.ddrpa.filtro.core.field.FiltroOperator;
import cc.ddrpa.filtro.core.field.QueryIntent;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记查询实体类的字段，定义该字段的过滤规则
 */
@Target({ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
public @interface Filtro {
    /**
     * 字段描述，可用于渲染检索表单
     */
    String value() default "";

    /**
     * 提示文案，可用于渲染检索表单
     */
    String tooltip() default "";

    /**
     * RSQL 查询中使用的字段名，默认与属性名相同
     */
    String field() default "";

    /**
     * 数据库列名或嵌套字段路径，用于构造查询条件，默认使用属性名的下划线形式
     */
    String key() default "";

    /**
     * 查询意图，若不声明（{@code AUTO}），则由系统根据 Java 字段类型自动推断
     */
    QueryIntent intent() default QueryIntent.AUTO;

    /**
     * 支持的操作符列表，若不声明，则由系统根据字段类型自动推断
     */
    FiltroOperator[] operators() default {};

    /**
     * IN / NOT_IN 参数数量上限，0 表示不限制。默认 0。
     */
    int maxInSize() default 0;

    /**
     * 适用分组，参考 Jakarta Bean Validation 的 group 概念
     */
    Class<?>[] groups() default {};
}
