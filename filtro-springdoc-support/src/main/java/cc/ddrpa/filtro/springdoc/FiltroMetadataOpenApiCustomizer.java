package cc.ddrpa.filtro.springdoc;

import cc.ddrpa.filtro.springboot.FiltroFieldMetaVO;
import cc.ddrpa.filtro.springboot.FiltroMetadataEndpointInfo;
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
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import org.springdoc.core.customizers.GlobalOpenApiCustomizer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 将 {@link FiltroMetadataCollector} 动态注册的元数据端点写入 OpenAPI。
 * <p>
 * tags / summary / description / operationId / path 参数从原查询接口的 Operation 派生，
 * 与原接口处于同一文档分组，不另建 Tag。
 */
public class FiltroMetadataOpenApiCustomizer implements GlobalOpenApiCustomizer {

    public static final String SCHEMA_NAME = "FiltroFieldMetaVO";
    public static final String SUMMARY_SUFFIX = " — filter metadata";
    public static final String OPERATION_ID_SUFFIX = "FiltroMetadata";

    private final FiltroMetadataCollector metadataCollector;

    public FiltroMetadataOpenApiCustomizer(FiltroMetadataCollector metadataCollector) {
        this.metadataCollector = metadataCollector;
    }

    @Override
    public void customise(OpenAPI openApi) {
        Map<String, FiltroMetadataEndpointInfo> endpoints = metadataCollector.getRegisteredMetadataEndpoints();
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

        for (FiltroMetadataEndpointInfo info : endpoints.values()) {
            Operation source = findGetOperation(openApi, info.getOriginalPath());
            openApi.getPaths().addPathItem(info.getMetadataPath(), buildPathItem(info, source));
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

    private PathItem buildPathItem(FiltroMetadataEndpointInfo info, Operation source) {
        Schema<?> itemRef = new Schema<>().$ref("#/components/schemas/" + SCHEMA_NAME);
        ArraySchema arraySchema = new ArraySchema().items(itemRef);

        Operation operation = new Operation()
                .operationId(deriveOperationId(info, source))
                .summary(deriveSummary(info, source))
                .description(deriveDescription(info, source))
                .responses(new ApiResponses()
                        .addApiResponse("200", new ApiResponse()
                                .description("Filterable field metadata")
                                .content(new Content()
                                        .addMediaType("application/json",
                                                new MediaType().schema(arraySchema)))));

        if (source != null) {
            if (source.getTags() != null && !source.getTags().isEmpty()) {
                operation.setTags(new ArrayList<>(source.getTags()));
            }
            copyPathParameters(source, operation);
        }

        return new PathItem().get(operation);
    }

    private static void copyPathParameters(Operation source, Operation target) {
        if (source.getParameters() == null) {
            return;
        }
        for (Parameter parameter : source.getParameters()) {
            if (parameter != null && "path".equals(parameter.getIn())) {
                target.addParametersItem(parameter);
            }
        }
    }

    static Operation findGetOperation(OpenAPI openApi, String originalPath) {
        if (openApi.getPaths() == null || originalPath == null) {
            return null;
        }
        PathItem pathItem = openApi.getPaths().get(originalPath);
        if (pathItem == null) {
            return null;
        }
        return pathItem.getGet();
    }

    static String deriveOperationId(FiltroMetadataEndpointInfo info, Operation source) {
        if (source != null && source.getOperationId() != null && !source.getOperationId().isBlank()) {
            return source.getOperationId() + OPERATION_ID_SUFFIX;
        }
        return "filtroMetadata_" + sanitizeOperationId(info.getMetadataPath());
    }

    static String deriveSummary(FiltroMetadataEndpointInfo info, Operation source) {
        if (source != null && source.getSummary() != null && !source.getSummary().isBlank()) {
            return source.getSummary() + SUMMARY_SUFFIX;
        }
        return "Filter metadata for " + info.getCriteriaType().getSimpleName();
    }

    static String deriveDescription(FiltroMetadataEndpointInfo info, Operation source) {
        StringBuilder sb = new StringBuilder();
        if (source != null && source.getDescription() != null && !source.getDescription().isBlank()) {
            sb.append(source.getDescription().trim()).append("\n\n");
        }
        sb.append("Returns filterable field metadata for criteria type `")
                .append(info.getCriteriaType().getName())
                .append("` (companion of `")
                .append(info.getOriginalPath())
                .append("`).");
        if (info.getGroup() != null && info.getGroup() != void.class) {
            sb.append(" Group: `").append(info.getGroup().getName()).append("`.");
        }
        return sb.toString();
    }

    static String sanitizeOperationId(String path) {
        return path.replaceAll("[^a-zA-Z0-9]", "_");
    }
}
