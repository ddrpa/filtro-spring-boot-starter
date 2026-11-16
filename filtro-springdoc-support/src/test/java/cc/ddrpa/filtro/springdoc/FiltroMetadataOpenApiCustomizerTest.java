package cc.ddrpa.filtro.springdoc;

import cc.ddrpa.filtro.springboot.FiltroMetadataEndpointInfo;
import cc.ddrpa.filtro.springboot.autoconfigure.FiltroMetadataCollector;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.parameters.Parameter;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FiltroMetadataOpenApiCustomizerTest {

    static class Book {
    }

    static class SysAdmin {
    }

    @Test
    void customise_derivesTagsAndSummaryFromOriginalOperation() {
        FiltroMetadataCollector collector = mock(FiltroMetadataCollector.class);
        when(collector.getRegisteredMetadataEndpoints()).thenReturn(Map.of(
                "/api/book:filtro",
                new FiltroMetadataEndpointInfo("/api/book:filtro", "/api/book", Book.class, void.class)
        ));

        OpenAPI openAPI = new OpenAPI().paths(new Paths()
                .addPathItem("/api/book", new PathItem().get(new Operation()
                        .operationId("listBooks")
                        .summary("List books")
                        .description("Paged book listing")
                        .tags(List.of("Book"))
                        .addParametersItem(new Parameter()
                                .in("path")
                                .name("unused")
                                .schema(new StringSchema()))
                        .addParametersItem(new Parameter()
                                .in("query")
                                .name("q")
                                .schema(new StringSchema())))));

        new FiltroMetadataOpenApiCustomizer(collector).customise(openAPI);

        assertThat(openAPI.getPaths()).containsKey("/api/book:filtro");
        assertThat(openAPI.getComponents().getSchemas()).containsKey(FiltroMetadataOpenApiCustomizer.SCHEMA_NAME);

        Operation meta = openAPI.getPaths().get("/api/book:filtro").getGet();
        assertThat(meta.getTags()).containsExactly("Book");
        assertThat(meta.getSummary()).isEqualTo("List books" + FiltroMetadataOpenApiCustomizer.SUMMARY_SUFFIX);
        assertThat(meta.getOperationId()).isEqualTo("listBooks" + FiltroMetadataOpenApiCustomizer.OPERATION_ID_SUFFIX);
        assertThat(meta.getDescription()).contains("Paged book listing");
        assertThat(meta.getDescription()).contains(Book.class.getName());
        assertThat(meta.getDescription()).contains("/api/book");
        assertThat(meta.getParameters()).hasSize(1);
        assertThat(meta.getParameters().get(0).getName()).isEqualTo("unused");
        assertThat(meta.getParameters().get(0).getIn()).isEqualTo("path");
        assertThat(meta.getResponses()).containsKey("200");
    }

    @Test
    void customise_fallsBackWhenOriginalOperationMissing() {
        FiltroMetadataCollector collector = mock(FiltroMetadataCollector.class);
        when(collector.getRegisteredMetadataEndpoints()).thenReturn(Map.of(
                "/api/admin/book:filtro",
                new FiltroMetadataEndpointInfo(
                        "/api/admin/book:filtro", "/api/admin/book", Book.class, SysAdmin.class)
        ));

        OpenAPI openAPI = new OpenAPI();
        new FiltroMetadataOpenApiCustomizer(collector).customise(openAPI);

        Operation meta = openAPI.getPaths().get("/api/admin/book:filtro").getGet();
        assertThat(meta.getTags()).isNull();
        assertThat(meta.getSummary()).isEqualTo("Filter metadata for Book");
        assertThat(meta.getDescription()).contains(SysAdmin.class.getName());
        assertThat(meta.getOperationId()).isEqualTo("filtroMetadata__api_admin_book_filtro");
    }

    @Test
    void customise_noopWhenNoEndpoints() {
        FiltroMetadataCollector collector = mock(FiltroMetadataCollector.class);
        when(collector.getRegisteredMetadataEndpoints()).thenReturn(Map.of());

        OpenAPI openAPI = new OpenAPI();
        new FiltroMetadataOpenApiCustomizer(collector).customise(openAPI);

        assertThat(openAPI.getPaths()).isNull();
    }

    @Test
    void sanitizeOperationId_replacesNonAlphanumeric() {
        assertThat(FiltroMetadataOpenApiCustomizer.sanitizeOperationId("/api/book:filtro"))
                .isEqualTo("_api_book_filtro");
    }
}
