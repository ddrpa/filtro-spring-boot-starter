package cc.ddrpa.filtro.springboot.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "filtro")
public class FiltroProperties {
    /**
     * controller 类扫描路径
     */
    private String[] controllerPackages = {};

    public String[] getControllerPackages() {
        return controllerPackages;
    }

    public FiltroProperties setControllerPackages(String[] controllerPackages) {
        this.controllerPackages = controllerPackages;
        return this;
    }
}