package cc.ddrpa.filtro.springboot.dictionary;

import cc.ddrpa.filtro.core.dictionary.FiltroDictionarySource;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SpringFiltroDictionarySourceResolverTest {

    static class StatusDictSource implements FiltroDictionarySource {
        @Override
        public Map<String, String> dictionary() {
            Map<String, String> map = new LinkedHashMap<>();
            map.put("草稿", "DRAFT");
            map.put("已发布", "PUBLISHED");
            return map;
        }
    }

    @Configuration
    static class Ctx {
        @Bean
        StatusDictSource statusDictSource() {
            return new StatusDictSource();
        }
    }

    @Test
    void resolvesRegisteredBean() {
        try (AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext(Ctx.class)) {
            SpringFiltroDictionarySourceResolver resolver = new SpringFiltroDictionarySourceResolver(ctx);
            assertThat(resolver.resolve(StatusDictSource.class))
                    .containsEntry("草稿", "DRAFT")
                    .containsEntry("已发布", "PUBLISHED");
        }
    }

    @Test
    void failsWhenBeanMissingWithoutNoArgFallback() {
        try (AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext()) {
            ctx.refresh();
            SpringFiltroDictionarySourceResolver resolver = new SpringFiltroDictionarySourceResolver(ctx);
            assertThatThrownBy(() -> resolver.resolve(StatusDictSource.class))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("No Spring bean");
        }
    }
}
