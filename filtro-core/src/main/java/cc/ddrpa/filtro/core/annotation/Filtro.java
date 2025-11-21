package cc.ddrpa.filtro.core.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记控制器方法参数，用于从 RSQL 查询字符串解析查询条件
 * <p>
 * 仅支持 GET 接口（包括 Method 包含 GET 的 RequestMapping）
 */
@Target({ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface Filtro {

    /**
     * 查询实体类型，用于确定可过滤的字段
     */
    Class<?> value();

    /**
     * 分组类型，用于在不同场景下过滤字段（参考 Jakarta Bean Validation 的 group 概念）
     */
    Class<?> group() default void.class;
}
