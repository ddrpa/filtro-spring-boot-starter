package cc.ddrpa.filtro.visitor.extension.meilisearch.autoconfigure;

import cc.ddrpa.filtro.core.FiltroRegistry;
import cc.ddrpa.filtro.core.rsql.RsqlNodeHandler;
import cc.ddrpa.filtro.visitor.extension.meilisearch.MeilisearchFilter;
import cc.ddrpa.filtro.visitor.extension.meilisearch.MeilisearchFilterNodeHandler;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@ConditionalOnClass(MeilisearchFilter.class)
public class MeilisearchFiltroAutoConfiguration {

    @Bean
    public RsqlNodeHandler<MeilisearchFilter> meilisearchFilterNodeHandler(FiltroRegistry filtroRegistry) {
        return new MeilisearchFilterNodeHandler(filtroRegistry);
    }
}
