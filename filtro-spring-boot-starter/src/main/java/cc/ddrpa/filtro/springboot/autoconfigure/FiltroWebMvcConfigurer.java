package cc.ddrpa.filtro.springboot.autoconfigure;

import cc.ddrpa.filtro.core.FiltroRegistry;
import cc.ddrpa.filtro.core.rsql.RsqlNodeHandler;
import cc.ddrpa.filtro.springboot.FiltroArgumentResolver;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

public class FiltroWebMvcConfigurer implements WebMvcConfigurer {

    private final FiltroRegistry filtroRegistry;
    private final List<RsqlNodeHandler<?>> factories;

    public FiltroWebMvcConfigurer(FiltroRegistry registry, List<RsqlNodeHandler<?>> factories) {
        this.filtroRegistry = registry;
        this.factories = factories;
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(new FiltroArgumentResolver(filtroRegistry, factories));
    }
}