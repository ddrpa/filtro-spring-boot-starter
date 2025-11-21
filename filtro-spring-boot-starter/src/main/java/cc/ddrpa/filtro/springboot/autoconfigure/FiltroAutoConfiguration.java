package cc.ddrpa.filtro.springboot.autoconfigure;

import cc.ddrpa.filtro.core.FiltroRegistry;
import cc.ddrpa.filtro.core.rsql.RsqlNodeHandler;
import cc.ddrpa.filtro.springboot.properties.FiltroProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.mvc.method.RequestMappingInfoHandlerMapping;

import java.util.List;

@Configuration
@EnableConfigurationProperties(FiltroProperties.class)
public class FiltroAutoConfiguration {

    private final List<RsqlNodeHandler<?>> factories;

    public FiltroAutoConfiguration(List<RsqlNodeHandler<?>> factories) {
        this.factories = factories;
    }

    @Bean
    public FiltroWebMvcConfigurer filtroWebMvcConfigurer(FiltroRegistry registry) {
        return new FiltroWebMvcConfigurer(registry, factories);
    }

    @Bean
    public FiltroRegistry filtroRegistry() {
        return new FiltroRegistry();
    }

    @Bean
    public FiltroMetadataCollector filtroMetadataCollector(FiltroProperties properties,
                                                           FiltroRegistry registry,
                                                           RequestMappingInfoHandlerMapping handlerMapping) {
        return new FiltroMetadataCollector(properties, registry, handlerMapping);
    }
}