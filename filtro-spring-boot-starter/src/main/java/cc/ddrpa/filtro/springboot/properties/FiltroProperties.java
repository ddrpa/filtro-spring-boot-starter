package cc.ddrpa.filtro.springboot.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "filtro")
public class FiltroProperties {
    /**
     * controller 类扫描路径
     */
    private String[] controllerPackages = {};

    /**
     * RSQL 表达式最大嵌套深度，默认 20。
     */
    private int maxDepth = 20;

    /**
     * 是否自动注册元数据查询端点，默认 true。
     */
    private boolean enableMetadataEndpoint = true;

    /**
     * 元数据端点路径后缀，默认 {@code :filtro}。
     */
    private String metadataEndpointSuffix = ":filtro";

    public String[] getControllerPackages() {
        return controllerPackages;
    }

    public FiltroProperties setControllerPackages(String[] controllerPackages) {
        this.controllerPackages = controllerPackages;
        return this;
    }

    public int getMaxDepth() {
        return maxDepth;
    }

    public FiltroProperties setMaxDepth(int maxDepth) {
        this.maxDepth = maxDepth;
        return this;
    }

    public boolean isEnableMetadataEndpoint() {
        return enableMetadataEndpoint;
    }

    public FiltroProperties setEnableMetadataEndpoint(boolean enableMetadataEndpoint) {
        this.enableMetadataEndpoint = enableMetadataEndpoint;
        return this;
    }

    public String getMetadataEndpointSuffix() {
        return metadataEndpointSuffix;
    }

    public FiltroProperties setMetadataEndpointSuffix(String metadataEndpointSuffix) {
        this.metadataEndpointSuffix = metadataEndpointSuffix;
        return this;
    }
}