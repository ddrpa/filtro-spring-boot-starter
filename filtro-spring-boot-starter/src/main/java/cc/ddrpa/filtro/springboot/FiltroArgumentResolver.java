package cc.ddrpa.filtro.springboot;

import cc.ddrpa.filtro.core.FiltroRegistry;
import cc.ddrpa.filtro.core.annotation.Filtro;
import cc.ddrpa.filtro.core.field.FiltroFieldMeta;
import cc.ddrpa.filtro.core.field.FiltroOperator;
import cc.ddrpa.filtro.core.rsql.RsqlNodeHandler;
import cz.jirutka.rsql.parser.RSQLParser;
import cz.jirutka.rsql.parser.ast.ComparisonOperator;
import cz.jirutka.rsql.parser.ast.Node;
import cz.jirutka.rsql.parser.ast.RSQLOperators;
import org.springframework.core.MethodParameter;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import java.util.*;
import java.util.stream.Stream;

public class FiltroArgumentResolver implements HandlerMethodArgumentResolver {

    private final RSQLParser rsqlParser;
    private final FiltroRegistry filtroRegistry;
    private final List<RsqlNodeHandler<?>> factories;

    public FiltroArgumentResolver(FiltroRegistry filtroRegistry, List<RsqlNodeHandler<?>> factories) {
        Set<ComparisonOperator> comparisonOperatorSet = new HashSet<>(RSQLOperators.defaultOperators());
        Stream.of(FiltroOperator.values())
                .filter(op -> !op.isRsqlOriginal())
                .map(op -> new ComparisonOperator(op.getSymbol(), op.isMultiValue()))
                .forEach(comparisonOperatorSet::add);
        this.rsqlParser = new RSQLParser(comparisonOperatorSet);
        this.filtroRegistry = filtroRegistry;
        this.factories = factories;
    }

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(Filtro.class);
    }

    @Override
    public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
                                  NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
        String query = webRequest.getParameter("q");
        if (!StringUtils.hasText(query)) {
            // NEED_CHECK 返回 null 合适还是根据类型返回空对象合适？
            return null;
        }
        Node queryRootNode = rsqlParser.parse(query);
        Filtro filtroAnno = parameter.getParameterAnnotation(Filtro.class);
        Class<?> entityType = filtroAnno.value();
        Class<?> entityGroup = filtroAnno.group();
        Map<String, FiltroFieldMeta> fieldMetaMap = filtroRegistry.getAsMap(entityType, entityGroup);

        Class<?> parameterType = parameter.getParameterType();
        Optional<RsqlNodeHandler<?>> optionalVisitorFactory = this.factories.stream()
                .filter(factory -> factory.supports(parameterType))
                .findFirst();
        if (optionalVisitorFactory.isPresent()) {
            return optionalVisitorFactory.get().parse(fieldMetaMap, queryRootNode);
        } else {
            return queryRootNode;
        }
    }
}