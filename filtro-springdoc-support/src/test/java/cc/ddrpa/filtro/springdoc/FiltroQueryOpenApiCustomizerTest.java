package cc.ddrpa.filtro.springdoc;

import cc.ddrpa.filtro.core.annotation.FiltroQuery;
import cc.ddrpa.filtro.springboot.properties.FiltroProperties;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.parameters.Parameter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.HandlerMethod;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

class FiltroQueryOpenApiCustomizerTest {

    private FiltroQueryOperationCustomizer operationCustomizer;
    private FiltroQueryParameterCustomizer parameterCustomizer;

    @BeforeEach
    void setUp() {
        FiltroProperties properties = new FiltroProperties().setMetadataEndpointSuffix(":filtro");
        operationCustomizer = new FiltroQueryOperationCustomizer(properties);
        parameterCustomizer = new FiltroQueryParameterCustomizer();
    }

    @Test
    void operationCustomizer_addsQParameterForFiltroQueryHandler() throws Exception {
        HandlerMethod handlerMethod = handlerMethod("listWithFiltro");
        Operation operation = new Operation();

        Operation result = operationCustomizer.customize(operation, handlerMethod);

        assertThat(result.getParameters()).hasSize(1);
        Parameter q = result.getParameters().get(0);
        assertThat(q.getName()).isEqualTo("q");
        assertThat(q.getIn()).isEqualTo("query");
        assertThat(q.getRequired()).isFalse();
        assertThat(q.getDescription()).contains(":filtro");
        assertThat(q.getSchema()).isInstanceOf(StringSchema.class);
        assertThat(q.getSchema().getExample()).isEqualTo(FiltroQueryOperationCustomizer.EXAMPLE);
    }

    @Test
    void operationCustomizer_skipsWhenNoFiltroQuery() throws Exception {
        HandlerMethod handlerMethod = handlerMethod("listWithoutFiltro");
        Operation operation = new Operation();

        Operation result = operationCustomizer.customize(operation, handlerMethod);

        assertThat(result.getParameters()).isNull();
    }

    @Test
    void operationCustomizer_doesNotDuplicateExistingQ() throws Exception {
        HandlerMethod handlerMethod = handlerMethod("listWithFiltro");
        Operation operation = new Operation()
                .addParametersItem(new Parameter().in("query").name("q").description("existing"));

        Operation result = operationCustomizer.customize(operation, handlerMethod);

        assertThat(result.getParameters()).hasSize(1);
        assertThat(result.getParameters().get(0).getDescription()).isEqualTo("existing");
    }

    @Test
    void parameterCustomizer_hidesFiltroQueryParameter() throws Exception {
        Method method = SampleController.class.getDeclaredMethod("listWithFiltro", Object.class);
        MethodParameter filtroParam = new MethodParameter(method, 0);

        Parameter result = parameterCustomizer.customize(
                new Parameter().name("wrapper"), filtroParam);

        assertThat(result).isNull();
    }

    @Test
    void parameterCustomizer_keepsOtherParameters() throws Exception {
        Method method = SampleController.class.getDeclaredMethod("listWithoutFiltro", String.class);
        MethodParameter pageParam = new MethodParameter(method, 0);

        Parameter original = new Parameter().name("page");
        Parameter result = parameterCustomizer.customize(original, pageParam);

        assertThat(result).isSameAs(original);
    }

    private static HandlerMethod handlerMethod(String methodName) throws Exception {
        Method method = SampleController.class.getDeclaredMethod(methodName,
                methodName.equals("listWithFiltro") ? Object.class : String.class);
        return new HandlerMethod(new SampleController(), method);
    }

    @RestController
    static class SampleController {
        @GetMapping("/api/book")
        public Object listWithFiltro(@FiltroQuery(Book.class) Object wrapper) {
            return null;
        }

        @GetMapping("/api/other")
        public Object listWithoutFiltro(@RequestParam String page) {
            return null;
        }
    }

    static class Book {
    }
}
