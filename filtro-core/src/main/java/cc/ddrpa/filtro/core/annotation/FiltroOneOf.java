package cc.ddrpa.filtro.core.annotation;

import cc.ddrpa.filtro.core.dictionary.FiltroDictionarySource;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 为 {@link Filtro} 字段声明离散可选值（码表），行为类似 Enum：
 * EXACT + {@code dictionary} + 前端 SELECT。
 * <p>
 * 三种来源互斥，至多指定一种：
 * <ul>
 *   <li>{@link #value()} — 简单码表，label = value</li>
 *   <li>{@link #asEnum()} — 借用 Enum 的展示名作 label，{@code name()} 作查询值</li>
 *   <li>{@link #source()} — Spring Bean（{@link FiltroDictionarySource}），可查库</li>
 * </ul>
 * 仅在 String 等非枚举字段需要固定选项时使用；Java Enum 字段无需本注解。
 */
@Target({ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
public @interface FiltroOneOf {

    /**
     * 离散可选值（label = value）。空数组表示未使用本属性。
     */
    String[] value() default {};

    /**
     * 借用 Enum 字典：label ← getDescription / getDesc / getName，value ← {@link Enum#name()}。
     * 默认 {@link Unspecified} 表示未使用。
     */
    Class<? extends Enum<?>> asEnum() default Unspecified.class;

    /**
     * Spring Bean 类型，须实现 {@link FiltroDictionarySource} 并已注册。
     * 默认 {@link FiltroDictionarySource.None} 表示未使用。
     */
    Class<? extends FiltroDictionarySource> source() default FiltroDictionarySource.None.class;

    /** {@link #asEnum()} 未指定时的哨兵。 */
    enum Unspecified {
    }
}
