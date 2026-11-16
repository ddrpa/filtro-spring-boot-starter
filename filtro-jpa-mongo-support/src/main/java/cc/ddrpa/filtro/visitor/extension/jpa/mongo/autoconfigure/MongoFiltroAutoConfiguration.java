package cc.ddrpa.filtro.visitor.extension.jpa.mongo.autoconfigure;

import cc.ddrpa.filtro.core.FiltroRegistry;
import cc.ddrpa.filtro.core.rsql.RsqlNodeHandler;
import cc.ddrpa.filtro.visitor.extension.jpa.mongo.MongoCriteriaNodeHandler;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.core.query.Criteria;

@Configuration(proxyBeanMethods = false)
@ConditionalOnClass(Criteria.class)
public class MongoFiltroAutoConfiguration {

    @Bean
    public RsqlNodeHandler<Criteria> mongoCriteriaNodeHandler(FiltroRegistry filtroRegistry) {
        return new MongoCriteriaNodeHandler(filtroRegistry);
    }
}
