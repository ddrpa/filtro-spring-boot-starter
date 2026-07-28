package cc.ddrpa.filtro.springboot.autoconfigure;

import cc.ddrpa.filtro.core.FiltroRegistry;
import cc.ddrpa.filtro.core.provider.AnnotatedClassFiltroFieldMetaProvider;
import cc.ddrpa.filtro.core.provider.FiltroFieldMetaProvider;
import cc.ddrpa.filtro.core.provider.InMemoryFiltroFieldMetaProvider;
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
    @ConditionalOnMissingBean
    public AnnotatedClassFiltroFieldMetaProvider annotatedClassFiltroFieldMetaProvider() {
        return new AnnotatedClassFiltroFieldMetaProvider();
    }

    @Bean
    @ConditionalOnMissingBean
    public InMemoryFiltroFieldMetaProvider inMemoryFiltroFieldMetaProvider() {
        return new InMemoryFiltroFieldMetaProvider();
    }

    @Bean
    public FiltroRegistry filtroRegistry(FiltroProperties properties,
                                         List<FiltroFieldMetaProvider> providers) {
        FiltroRegistry registry = new FiltroRegistry(providers);
        registry.setMaxDepth(properties.getMaxDepth());
        return registry;
    }

    @Bean
    public FiltroMetadataCollector filtroMetadataCollector(FiltroProperties properties,
                                                           FiltroRegistry registry,
                                                           AnnotatedClassFiltroFieldMetaProvider annotatedProvider,
                                                           RequestMappingInfoHandlerMapping handlerMapping) {
        return new FiltroMetadataCollector(properties, registry, annotatedProvider, handlerMapping);
    }
}
