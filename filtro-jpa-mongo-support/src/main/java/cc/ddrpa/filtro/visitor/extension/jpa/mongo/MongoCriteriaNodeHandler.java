package cc.ddrpa.filtro.visitor.extension.jpa.mongo;

import cc.ddrpa.filtro.core.FiltroRegistry;
import cc.ddrpa.filtro.core.field.FiltroFieldMeta;
import cc.ddrpa.filtro.core.rsql.RsqlNodeHandler;
import cz.jirutka.rsql.parser.ast.Node;
import org.springframework.data.mongodb.core.query.Criteria;

import java.util.Map;

public class MongoCriteriaNodeHandler implements RsqlNodeHandler<Criteria> {

    private final FiltroRegistry filtroRegistry;

    public MongoCriteriaNodeHandler(FiltroRegistry filtroRegistry) {
        this.filtroRegistry = filtroRegistry;
    }

    @Override
    public boolean supports(Class<?> targetType) {
        return Criteria.class.equals(targetType);
    }

    @Override
    public Criteria parse(Map<String, FiltroFieldMeta> metaMap, Node queryRoot) {
        Criteria criteria = new Criteria();
        new MongoCriteriaVisitor(metaMap, filtroRegistry.getMaxDepth())
                .apply(queryRoot, criteria);
        return criteria;
    }
}