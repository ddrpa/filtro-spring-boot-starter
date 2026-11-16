package cc.ddrpa.filtro.core.dictionary;

/**
 * 按类型解析 {@link FiltroDictionarySource}，供元数据端点填充 dictionary。
 */
@FunctionalInterface
public interface FiltroDictionarySourceResolver {

    /**
     * @param sourceClass 声明的 source 类型（通常为具体实现类）
     * @return label → 查询值，不可为 null
     */
    java.util.Map<String, String> resolve(Class<? extends FiltroDictionarySource> sourceClass);
}
