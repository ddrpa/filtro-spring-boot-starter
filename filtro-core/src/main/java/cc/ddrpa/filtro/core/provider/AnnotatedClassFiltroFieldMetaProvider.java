package cc.ddrpa.filtro.core.provider;

import cc.ddrpa.filtro.core.field.FiltroFieldMeta;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 承接 ClassGraph / 反射扫描到的 {@code @Filtro} 字段元数据，作为最低优先级兜底。
 */
public class AnnotatedClassFiltroFieldMetaProvider implements FiltroFieldMetaProvider {

    public static final int ORDER = Integer.MAX_VALUE;

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

    /** 仅当类型尚未存在时写入 */
    public void register(Class<?> criteriaType, List<FiltroFieldMeta> fields) {
        Objects.requireNonNull(criteriaType, "criteriaType");
        store.putIfAbsent(criteriaType, fields == null ? List.of() : List.copyOf(fields));
    }

    public boolean hasType(Class<?> criteriaType) {
        return store.containsKey(criteriaType);
    }

    public int registeredTypeCount() {
        return store.size();
    }
}
