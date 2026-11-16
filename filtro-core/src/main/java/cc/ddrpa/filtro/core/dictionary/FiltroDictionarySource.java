package cc.ddrpa.filtro.core.dictionary;

import java.util.Map;

/**
 * 离散码表来源。与 {@code @FiltroOneOf(source = ...)} 配合使用，实现类须注册为 Spring Bean。
 */
public interface FiltroDictionarySource {

    /**
     * 返回 label → 查询值；建议 {@link java.util.LinkedHashMap} 保序。
     */
    Map<String, String> dictionary();

    /**
     * 注解默认哨兵，非真实数据源。
     */
    final class None implements FiltroDictionarySource {
        private None() {
        }

        @Override
        public Map<String, String> dictionary() {
            throw new UnsupportedOperationException("FiltroDictionarySource.None is not a real source");
        }
    }
}
