package cc.ddrpa.filtro.springdoc;

import cc.ddrpa.filtro.springboot.autoconfigure.FiltroMetadataCollector;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.junit.jupiter.api.Test;

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
    void customise_addsMetadataPathsAndSchema() {
        FiltroMetadataCollector collector = mock(FiltroMetadataCollector.class);
        when(collector.getRegisteredMetadataEndpoints()).thenReturn(Map.of(
                "/api/book:filtro", ImmutablePair.of(Book.class, void.class),
                "/api/admin/book:filtro", ImmutablePair.of(Book.class, SysAdmin.class)
        ));

        OpenAPI openAPI = new OpenAPI();
        new FiltroMetadataOpenApiCustomizer(collector).customise(openAPI);

        assertThat(openAPI.getPaths()).containsKeys("/api/book:filtro", "/api/admin/book:filtro");
        assertThat(openAPI.getComponents().getSchemas()).containsKey(FiltroMetadataOpenApiCustomizer.SCHEMA_NAME);

        PathItem bookMeta = openAPI.getPaths().get("/api/book:filtro");
        Operation get = bookMeta.getGet();
        assertThat(get.getTags()).contains(FiltroMetadataOpenApiCustomizer.TAG_NAME);
        assertThat(get.getSummary()).contains("Book");
        assertThat(get.getDescription()).doesNotContain("Group:");
        assertThat(get.getResponses()).containsKey("200");

        Operation adminGet = openAPI.getPaths().get("/api/admin/book:filtro").getGet();
        assertThat(adminGet.getDescription()).contains(SysAdmin.class.getName());
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
