package cc.ddrpa.filtro.visitor.extension.jpa.mongo;

import cc.ddrpa.filtro.core.field.FiltroFieldMeta;
import cc.ddrpa.filtro.core.rsql.RsqlNodeHandler;
import cz.jirutka.rsql.parser.ast.Node;
import org.springframework.data.mongodb.core.query.Criteria;

import java.util.Map;

public class MongoCriteriaNodeHandler implements RsqlNodeHandler<Criteria> {

    @Override
    public boolean supports(Class<?> targetType) {
        return Criteria.class.equals(targetType);
    }

    @Override
    public Criteria parse(Map<String, FiltroFieldMeta> metaMap, Node queryRoot) {
        Criteria criteria = new Criteria();
        new MongoCriteriaVisitor(metaMap)
                .apply(queryRoot, criteria);
        return criteria;
    }
}