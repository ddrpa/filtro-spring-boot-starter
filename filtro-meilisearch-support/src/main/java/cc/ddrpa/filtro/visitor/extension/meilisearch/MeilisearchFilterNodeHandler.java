package cc.ddrpa.filtro.visitor.extension.meilisearch;

import cc.ddrpa.filtro.core.FiltroRegistry;
import cc.ddrpa.filtro.core.field.FiltroFieldMeta;
import cc.ddrpa.filtro.core.rsql.RsqlNodeHandler;
import cz.jirutka.rsql.parser.ast.Node;

import java.util.Map;

public class MeilisearchFilterNodeHandler implements RsqlNodeHandler<MeilisearchFilter> {

    private final FiltroRegistry filtroRegistry;

    public MeilisearchFilterNodeHandler(FiltroRegistry filtroRegistry) {
        this.filtroRegistry = filtroRegistry;
    }

    @Override
    public boolean supports(Class<?> targetType) {
        return MeilisearchFilter.class.equals(targetType);
    }

    @Override
    public MeilisearchFilter parse(Map<String, FiltroFieldMeta> metaMap, Node queryRoot) {
        String expression = new MeilisearchFilterVisitor(metaMap, filtroRegistry.getMaxDepth())
                .apply(queryRoot);
        return new MeilisearchFilter(expression);
    }
}
