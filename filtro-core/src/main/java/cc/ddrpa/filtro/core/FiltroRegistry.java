package cc.ddrpa.filtro.core;

import cc.ddrpa.filtro.core.field.FiltroFieldMeta;
import cc.ddrpa.filtro.core.provider.FiltroFieldMetaProvider;
import cc.ddrpa.filtro.core.provider.InMemoryFiltroFieldMetaProvider;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 字段元数据选择器：按 {@link FiltroFieldMetaProvider#getOrder()} 升序选取第一个
 * {@link FiltroFieldMetaProvider#supports(Class)} 为 true 的 Provider，取其完整字段列表后再做 group 过滤。
 */
public class FiltroRegistry {

    private List<FiltroFieldMetaProvider> providers = List.of();
    private int maxDepth = 20;

    public FiltroRegistry() {
    }

    public FiltroRegistry(List<FiltroFieldMetaProvider> providers) {
        setProviders(providers);
    }

    public void setProviders(List<FiltroFieldMetaProvider> providers) {
        if (providers == null || providers.isEmpty()) {
            this.providers = List.of();
            return;
        }
        // 稳定排序：order 升序，同 order 保留原相对顺序
        List<FiltroFieldMetaProvider> copy = new ArrayList<>(providers);
        copy.sort(Comparator.comparingInt(FiltroFieldMetaProvider::getOrder));
        this.providers = List.copyOf(copy);
    }

    public List<FiltroFieldMetaProvider> getProviders() {
        return providers;
    }

    /**
     * 是否有任意 Provider 认领该类型。
     */
    public boolean hasType(Class<?> clazz) {
        return providers.stream().anyMatch(p -> p.supports(clazz));
    }

    /**
     * 内置可计数 Provider（Annotated + InMemory）登记类型数之和（可能重复计数同一 Class）。
     * 扫描完成日志请优先使用 {@link cc.ddrpa.filtro.core.provider.AnnotatedClassFiltroFieldMetaProvider#registeredTypeCount()}。
     */
    public int registeredTypeCount() {
        int count = 0;
        for (FiltroFieldMetaProvider p : providers) {
            if (p instanceof cc.ddrpa.filtro.core.provider.AnnotatedClassFiltroFieldMetaProvider annotated) {
                count += annotated.registeredTypeCount();
            } else if (p instanceof InMemoryFiltroFieldMetaProvider inMemory) {
                count += inMemory.registeredTypeCount();
            }
        }
        return count;
    }

    public int getMaxDepth() {
        return maxDepth;
    }

    public void setMaxDepth(int maxDepth) {
        this.maxDepth = maxDepth;
    }

    /**
     * @deprecated 请使用 {@link InMemoryFiltroFieldMetaProvider#register(Class, List)}；
     * 本方法委托给 Registry 中的 InMemory Provider（若不存在则抛异常）。
     */
    @Deprecated
    public void register(Class<?> criteriaType, List<FiltroFieldMeta> filtroFieldMetas) {
        InMemoryFiltroFieldMetaProvider inMemory = providers.stream()
                .filter(InMemoryFiltroFieldMetaProvider.class::isInstance)
                .map(InMemoryFiltroFieldMetaProvider.class::cast)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "No InMemoryFiltroFieldMetaProvider configured; inject one or call InMemoryFiltroFieldMetaProvider.register directly"));
        inMemory.register(criteriaType, filtroFieldMetas);
    }

    public List<FiltroFieldMeta> get(Class<?> criteriaType, Class<?> metaGroup) {
        for (FiltroFieldMetaProvider provider : providers) {
            if (provider.supports(criteriaType)) {
                return filterByGroup(provider.getFields(criteriaType), metaGroup);
            }
        }
        return Collections.emptyList();
    }

    public Map<String, FiltroFieldMeta> getAsMap(Class<?> criteriaType, Class<?> metaGroup) {
        return get(criteriaType, metaGroup)
                .stream()
                .collect(Collectors.toMap(FiltroFieldMeta::getField, f -> f, (a, b) -> b));
    }

    private static List<FiltroFieldMeta> filterByGroup(List<FiltroFieldMeta> fields, Class<?> metaGroup) {
        if (fields == null || fields.isEmpty()) {
            return Collections.emptyList();
        }
        if (Objects.isNull(metaGroup) || metaGroup.equals(void.class)) {
            return fields.stream()
                    .filter(f -> f.getGroups() == null || f.getGroups().isEmpty())
                    .toList();
        }
        return fields.stream()
                .filter(f -> f.getGroups() == null || f.getGroups().isEmpty()
                        || f.getGroups().stream().anyMatch(g -> g.isAssignableFrom(metaGroup)))
                .toList();
    }
}
