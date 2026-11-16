package cc.ddrpa.filtro.visitor.extension.mp;

import cc.ddrpa.filtro.core.FiltroRegistry;
import cc.ddrpa.filtro.core.field.FiltroFieldMeta;
import cc.ddrpa.filtro.core.rsql.RsqlNodeHandler;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import cz.jirutka.rsql.parser.ast.Node;

import java.util.Map;

public class MybatisPlusQueryWrapperNodeHandler implements RsqlNodeHandler<QueryWrapper<?>> {

    private final FiltroRegistry filtroRegistry;

    public MybatisPlusQueryWrapperNodeHandler(FiltroRegistry filtroRegistry) {
        this.filtroRegistry = filtroRegistry;
    }

    @Override
    public boolean supports(Class<?> targetType) {
        return QueryWrapper.class.equals(targetType);
    }

    @Override
    public QueryWrapper<?> parse(Map<String, FiltroFieldMeta> metaMap, Node queryRoot) {
        QueryWrapper<?> queryWrapper = new QueryWrapper<>();
        new MybatisPlusQueryWrapperVisitor(metaMap, filtroRegistry.getMaxDepth())
                .apply(queryRoot, queryWrapper);
        return queryWrapper;
    }
}