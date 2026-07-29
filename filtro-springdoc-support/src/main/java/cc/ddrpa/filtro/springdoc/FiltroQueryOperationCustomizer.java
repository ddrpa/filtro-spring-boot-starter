package cc.ddrpa.filtro.springdoc;

import cc.ddrpa.filtro.core.annotation.FiltroQuery;
import cc.ddrpa.filtro.springboot.properties.FiltroProperties;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.parameters.Parameter;
import org.springdoc.core.customizers.GlobalOperationCustomizer;
import org.springframework.core.MethodParameter;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.web.method.HandlerMethod;

import java.util.Arrays;
import java.util.List;

/**
 * 为带 {@link FiltroQuery} 的 handler 补充 OpenAPI 查询参数 {@code q}（RSQL）。
 */
public class FiltroQueryOperationCustomizer implements GlobalOperationCustomizer {

    public static final String QUERY_PARAM_NAME = "q";
    public static final String EXAMPLE = "title=like=Spring;price=gt=10";

    private final FiltroProperties filtroProperties;

    public FiltroQueryOperationCustomizer(FiltroProperties filtroProperties) {
        this.filtroProperties = filtroProperties;
    }

    @Override
    public Operation customize(Operation operation, HandlerMethod handlerMethod) {
        if (operation == null || !hasFiltroQueryParameter(handlerMethod)) {
            return operation;
        }
        if (hasQueryParameterNamedQ(operation)) {
            return operation;
        }
        String suffix = filtroProperties.getMetadataEndpointSuffix();
        String description = "RSQL filter expression. "
                + "Omit to skip filtering. "
                + "See the corresponding metadata endpoint (same path + `" + suffix + "`) for available fields and operators.";

        operation.addParametersItem(new Parameter()
                .in("query")
                .name(QUERY_PARAM_NAME)
                .required(false)
                .description(description)
                .schema(new StringSchema().example(EXAMPLE)));
        return operation;
    }

    static boolean hasFiltroQueryParameter(HandlerMethod handlerMethod) {
        return Arrays.stream(handlerMethod.getMethodParameters())
                .anyMatch(FiltroQueryOperationCustomizer::isFiltroQueryParameter);
    }

    static boolean isFiltroQueryParameter(MethodParameter parameter) {
        return AnnotationUtils.findAnnotation(parameter.getParameter(), FiltroQuery.class) != null
                || parameter.hasParameterAnnotation(FiltroQuery.class);
    }

    private static boolean hasQueryParameterNamedQ(Operation operation) {
        List<Parameter> parameters = operation.getParameters();
        if (parameters == null) {
            return false;
        }
        return parameters.stream()
                .anyMatch(p -> QUERY_PARAM_NAME.equals(p.getName()) && "query".equals(p.getIn()));
    }
}
