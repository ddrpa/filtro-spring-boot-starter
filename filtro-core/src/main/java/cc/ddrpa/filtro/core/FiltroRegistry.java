package cc.ddrpa.filtro.core;

import cc.ddrpa.filtro.core.field.FiltroFieldMeta;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

public class FiltroRegistry {

    private final ConcurrentMap<Class<?>, List<FiltroFieldMeta>> metadataStore = new ConcurrentHashMap<>();

    public boolean hasType(Class<?> clazz) {
        return metadataStore.containsKey(clazz);
    }

    public void register(Class<?> criteriaType, List<FiltroFieldMeta> filtroFieldMetas) {
        metadataStore.putIfAbsent(criteriaType, filtroFieldMetas);
    }

    public List<FiltroFieldMeta> get(Class<?> criteriaType, Class<?> metaGroup) {
        if (!metadataStore.containsKey(criteriaType)) {
            return Collections.emptyList();
        } else if (Objects.isNull(metaGroup) || metaGroup.equals(void.class)) {
            return metadataStore.get(criteriaType)
                    .stream()
                    .filter(f -> f.getGroups().isEmpty())
                    .toList();
        } else {
            return metadataStore.get(criteriaType)
                    .stream()
                    // NEED_CHECK 检查会不会反了
                    .filter(f -> f.getGroups().isEmpty()
                            || f.getGroups().stream().anyMatch(g -> g.isAssignableFrom(metaGroup)))
                    .toList();
        }
    }

    public Map<String, FiltroFieldMeta> getAsMap(Class<?> criteriaType, Class<?> metaGroup) {
        return get(criteriaType, metaGroup)
                .stream()
                .collect(Collectors.toMap(FiltroFieldMeta::getField, f -> f));
    }
}