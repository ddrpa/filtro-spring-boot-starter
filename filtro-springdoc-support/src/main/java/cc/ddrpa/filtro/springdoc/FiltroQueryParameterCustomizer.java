package cc.ddrpa.filtro.springdoc;

import io.swagger.v3.oas.models.parameters.Parameter;
import org.springdoc.core.customizers.ParameterCustomizer;
import org.springframework.core.MethodParameter;

/**
 * 隐藏 {@link cc.ddrpa.filtro.core.annotation.FiltroQuery} 方法参数，避免 Springdoc 将其文档化为
 * QueryWrapper / Criteria 等类型。实际查询参数由 {@link FiltroQueryOperationCustomizer} 以 {@code q} 形式补充。
 */
public class FiltroQueryParameterCustomizer implements ParameterCustomizer {

    @Override
    public Parameter customize(Parameter parameterModel, MethodParameter methodParameter) {
        if (FiltroQueryOperationCustomizer.isFiltroQueryParameter(methodParameter)) {
            return null;
        }
        return parameterModel;
    }
}
