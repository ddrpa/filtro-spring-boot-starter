package cc.ddrpa.filtro.core.rsql;

import cc.ddrpa.filtro.core.field.FiltroFieldMeta;
import cz.jirutka.rsql.parser.ast.Node;

import java.util.Map;

public interface RsqlNodeHandler<T> {

    boolean supports(Class<?> targetType);

    T parse(Map<String, FiltroFieldMeta> metaMap, Node queryRoot);
}