package cc.ddrpa.filtro.visitor.extension.mp;

import cc.ddrpa.filtro.core.field.FiltroFieldMeta;
import cc.ddrpa.filtro.core.rsql.RsqlNodeHandler;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import cz.jirutka.rsql.parser.ast.Node;

import java.util.Map;

public class MybatisPlusQueryWrapperNodeHandler implements RsqlNodeHandler<QueryWrapper<?>> {

    @Override
    public boolean supports(Class<?> targetType) {
        return QueryWrapper.class.equals(targetType);
    }

    @Override
    public QueryWrapper<?> parse(Map<String, FiltroFieldMeta> metaMap, Node queryRoot) {
        QueryWrapper<?> queryWrapper = new QueryWrapper<>();
        new MybatisPlusQueryWrapperVisitor(metaMap)
                .apply(queryRoot, queryWrapper);
        return queryWrapper;
    }
}