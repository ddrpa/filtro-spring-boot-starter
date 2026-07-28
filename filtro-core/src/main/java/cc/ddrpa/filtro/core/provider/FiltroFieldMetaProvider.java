package cc.ddrpa.filtro.core.provider;

import cc.ddrpa.filtro.core.field.FiltroFieldMeta;

import java.util.List;

/**
 * 字段元数据来源 SPI。{@link cc.ddrpa.filtro.core.FiltroRegistry} 按 {@link #getOrder()}
 * 升序选取第一个 {@link #supports(Class)} 为 true 的 Provider，取其完整字段列表（不合并多源）。
 */
public interface FiltroFieldMetaProvider {

    /**
     * 是否认领该 criteriaType；为 true 即视为有效结果来源（可短路后续 Provider）。
     */
    boolean supports(Class<?> criteriaType);

    /**
     * 返回该类型全部字段（含各 groups）；group 过滤由 Registry 负责。
     */
    List<FiltroFieldMeta> getFields(Class<?> criteriaType);

    /**
     * 越小优先级越高。用户自定义默认 {@code 0}；内置 Provider 使用更大的值。
     */
    default int getOrder() {
        return 0;
    }
}
