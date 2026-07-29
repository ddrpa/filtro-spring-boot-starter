package cc.ddrpa.filtro.springdoc.autoconfigure;

import cc.ddrpa.filtro.springboot.autoconfigure.FiltroMetadataCollector;
import cc.ddrpa.filtro.springboot.properties.FiltroProperties;
import cc.ddrpa.filtro.springdoc.FiltroMetadataOpenApiCustomizer;
import cc.ddrpa.filtro.springdoc.FiltroQueryOperationCustomizer;
import cc.ddrpa.filtro.springdoc.FiltroQueryParameterCustomizer;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@ConditionalOnClass(OpenApiCustomizer.class)
@ConditionalOnBean(FiltroMetadataCollector.class)
public class FiltroSpringdocAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public FiltroMetadataOpenApiCustomizer filtroMetadataOpenApiCustomizer(FiltroMetadataCollector metadataCollector) {
        return new FiltroMetadataOpenApiCustomizer(metadataCollector);
    }

    @Bean
    @ConditionalOnMissingBean
    public FiltroQueryOperationCustomizer filtroQueryOperationCustomizer(FiltroProperties filtroProperties) {
        return new FiltroQueryOperationCustomizer(filtroProperties);
    }

    @Bean
    @ConditionalOnMissingBean
    public FiltroQueryParameterCustomizer filtroQueryParameterCustomizer() {
        return new FiltroQueryParameterCustomizer();
    }
}
