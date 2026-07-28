package cc.ddrpa.filtro.springboot.autoconfigure;

import cc.ddrpa.filtro.core.FiltroRegistry;
import cc.ddrpa.filtro.core.rsql.RsqlNodeHandler;
import cc.ddrpa.filtro.springboot.properties.FiltroProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.mvc.method.RequestMappingInfoHandlerMapping;

import java.util.List;

@Configuration(proxyBeanMethods = false)
@ConditionalOnWebApplication
@EnableConfigurationProperties(FiltroProperties.class)
public class FiltroAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(name = "filtroExceptionHandler")
    public FiltroExceptionHandler filtroExceptionHandler() {
        return new FiltroExceptionHandler();
    }

    @Bean
    public FiltroWebMvcConfigurer filtroWebMvcConfigurer(FiltroRegistry registry,
                                                        List<RsqlNodeHandler<?>> factories) {
        return new FiltroWebMvcConfigurer(registry, factories);
    }

    @Bean
    public FiltroRegistry filtroRegistry(FiltroProperties properties) {
        FiltroRegistry registry = new FiltroRegistry();
        registry.setMaxDepth(properties.getMaxDepth());
        return registry;
    }

    @Bean
    public FiltroMetadataCollector filtroMetadataCollector(FiltroProperties properties,
                                                           FiltroRegistry registry,
                                                           RequestMappingInfoHandlerMapping handlerMapping) {
        return new FiltroMetadataCollector(properties, registry, handlerMapping);
    }
}