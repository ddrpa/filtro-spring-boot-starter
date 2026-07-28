package cc.ddrpa.filtro.core.provider;

import cc.ddrpa.filtro.core.field.FiltroFieldMeta;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 可变内存 Provider：支持命令式 {@link #register(Class, List)}（无则创建、有则更新）与 clear。
 * 仅对已登记类型 {@link #supports(Class)} 为 true。
 */
public class InMemoryFiltroFieldMetaProvider implements FiltroFieldMetaProvider {

    public static final int ORDER = 100;

    private final ConcurrentMap<Class<?>, List<FiltroFieldMeta>> store = new ConcurrentHashMap<>();

    @Override
    public boolean supports(Class<?> criteriaType) {
        return store.containsKey(criteriaType);
    }

    @Override
    public List<FiltroFieldMeta> getFields(Class<?> criteriaType) {
        List<FiltroFieldMeta> fields = store.get(criteriaType);
        return fields == null ? Collections.emptyList() : List.copyOf(fields);
    }

    @Override
    public int getOrder() {
        return ORDER;
    }

    /**
     * 登记或更新该类型的完整字段列表（无则创建，有则覆盖）。
     */
    public void register(Class<?> criteriaType, List<FiltroFieldMeta> fields) {
        Objects.requireNonNull(criteriaType, "criteriaType");
        store.put(criteriaType, copyOf(fields));
    }

    public void remove(Class<?> criteriaType) {
        store.remove(criteriaType);
    }

    public int registeredTypeCount() {
        return store.size();
    }

    private static List<FiltroFieldMeta> copyOf(List<FiltroFieldMeta> fields) {
        return fields == null ? List.of() : List.copyOf(fields);
    }
}
