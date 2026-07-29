package cc.ddrpa.filtro.springboot;

/**
 * 已注册的 Filtro 元数据端点信息，用于 OpenAPI 等扩展从原查询接口派生文档。
 */
public final class FiltroMetadataEndpointInfo {

    private final String metadataPath;
    private final String originalPath;
    private final Class<?> criteriaType;
    private final Class<?> group;

    public FiltroMetadataEndpointInfo(String metadataPath,
                                      String originalPath,
                                      Class<?> criteriaType,
                                      Class<?> group) {
        this.metadataPath = metadataPath;
        this.originalPath = originalPath;
        this.criteriaType = criteriaType;
        this.group = group;
    }

    public String getMetadataPath() {
        return metadataPath;
    }

    public String getOriginalPath() {
        return originalPath;
    }

    public Class<?> getCriteriaType() {
        return criteriaType;
    }

    public Class<?> getGroup() {
        return group;
    }
}
