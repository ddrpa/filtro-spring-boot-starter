package cc.ddrpa.filtro.springdoc;

import cc.ddrpa.filtro.springboot.FiltroFieldMetaVO;
import cc.ddrpa.filtro.springboot.autoconfigure.FiltroMetadataCollector;
import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.core.converter.ResolvedSchema;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.oas.models.tags.Tag;
import org.apache.commons.lang3.tuple.Pair;
import org.springdoc.core.customizers.GlobalOpenApiCustomizer;

import java.util.List;
import java.util.Map;

/**
 * 将 {@link FiltroMetadataCollector} 动态注册的元数据端点写入 OpenAPI。
 */
public class FiltroMetadataOpenApiCustomizer implements GlobalOpenApiCustomizer {

    public static final String TAG_NAME = "Filtro";
    public static final String SCHEMA_NAME = "FiltroFieldMetaVO";

    private final FiltroMetadataCollector metadataCollector;

    public FiltroMetadataOpenApiCustomizer(FiltroMetadataCollector metadataCollector) {
        this.metadataCollector = metadataCollector;
    }

    @Override
    public void customise(OpenAPI openApi) {
        Map<String, Pair<Class<?>, Class<?>>> endpoints = metadataCollector.getRegisteredMetadataEndpoints();
        if (endpoints.isEmpty()) {
            return;
        }

        if (openApi.getComponents() == null) {
            openApi.setComponents(new Components());
        }
        ensureFieldMetaSchema(openApi.getComponents());

        if (openApi.getPaths() == null) {
            openApi.setPaths(new Paths());
        }
        ensureTag(openApi);

        for (Map.Entry<String, Pair<Class<?>, Class<?>>> entry : endpoints.entrySet()) {
            String path = entry.getKey();
            Class<?> criteriaType = entry.getValue().getLeft();
            Class<?> group = entry.getValue().getRight();
            openApi.getPaths().addPathItem(path, buildPathItem(path, criteriaType, group));
        }
    }

    private void ensureTag(OpenAPI openApi) {
        boolean exists = openApi.getTags() != null
                && openApi.getTags().stream().anyMatch(t -> TAG_NAME.equals(t.getName()));
        if (!exists) {
            openApi.addTagsItem(new Tag()
                    .name(TAG_NAME)
                    .description("Filtro filter field metadata endpoints"));
        }
    }

    private void ensureFieldMetaSchema(Components components) {
        if (components.getSchemas() != null && components.getSchemas().containsKey(SCHEMA_NAME)) {
            return;
        }
        ResolvedSchema resolved = ModelConverters.getInstance()
                .readAllAsResolvedSchema(FiltroFieldMetaVO.class);
        if (resolved.referencedSchemas != null) {
            resolved.referencedSchemas.forEach(components::addSchemas);
        }
        if (resolved.schema != null) {
            components.addSchemas(SCHEMA_NAME, resolved.schema);
        }
    }

    private PathItem buildPathItem(String path, Class<?> criteriaType, Class<?> group) {
        String operationId = "filtroMetadata_" + sanitizeOperationId(path);
        String summary = "Filtro field metadata for " + criteriaType.getSimpleName();
        String description = buildDescription(criteriaType, group);

        Schema<?> itemRef = new Schema<>().$ref("#/components/schemas/" + SCHEMA_NAME);
        ArraySchema arraySchema = new ArraySchema().items(itemRef);

        Operation operation = new Operation()
                .operationId(operationId)
                .summary(summary)
                .description(description)
                .tags(List.of(TAG_NAME))
                .responses(new ApiResponses()
                        .addApiResponse("200", new ApiResponse()
                                .description("Filterable field metadata")
                                .content(new Content()
                                        .addMediaType("application/json",
                                                new MediaType().schema(arraySchema)))));

        return new PathItem().get(operation);
    }

    private static String buildDescription(Class<?> criteriaType, Class<?> group) {
        StringBuilder sb = new StringBuilder();
        sb.append("Returns filterable field metadata for criteria type `")
                .append(criteriaType.getName())
                .append("`.");
        if (group != null && group != void.class) {
            sb.append(" Group: `").append(group.getName()).append("`.");
        }
        return sb.toString();
    }

    static String sanitizeOperationId(String path) {
        return path.replaceAll("[^a-zA-Z0-9]", "_");
    }
}
