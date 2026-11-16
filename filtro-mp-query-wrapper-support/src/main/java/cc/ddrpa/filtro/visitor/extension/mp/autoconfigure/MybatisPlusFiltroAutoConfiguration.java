package cc.ddrpa.filtro.visitor.extension.mp.autoconfigure;

import cc.ddrpa.filtro.core.FiltroRegistry;
import cc.ddrpa.filtro.core.rsql.RsqlNodeHandler;
import cc.ddrpa.filtro.visitor.extension.mp.MybatisPlusQueryWrapperNodeHandler;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@ConditionalOnClass(QueryWrapper.class)
public class MybatisPlusFiltroAutoConfiguration {

    @Bean
    public RsqlNodeHandler<QueryWrapper<?>> mybatisPlusQueryWrapperNodeHandler(FiltroRegistry filtroRegistry) {
        return new MybatisPlusQueryWrapperNodeHandler(filtroRegistry);
    }
}
