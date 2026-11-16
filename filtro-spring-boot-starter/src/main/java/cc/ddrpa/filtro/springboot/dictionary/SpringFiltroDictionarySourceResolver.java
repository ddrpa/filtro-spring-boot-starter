package cc.ddrpa.filtro.springboot.dictionary;

import cc.ddrpa.filtro.core.dictionary.FiltroDictionarySource;
import cc.ddrpa.filtro.core.dictionary.FiltroDictionarySourceResolver;
import org.springframework.context.ApplicationContext;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 仅通过 {@link ApplicationContext#getBean(Class)} 解析码表；无 Bean 则失败（不做无参 new）。
 */
public class SpringFiltroDictionarySourceResolver implements FiltroDictionarySourceResolver {

    private final ApplicationContext applicationContext;

    public SpringFiltroDictionarySourceResolver(ApplicationContext applicationContext) {
        this.applicationContext = Objects.requireNonNull(applicationContext, "applicationContext");
    }

    @Override
    public Map<String, String> resolve(Class<? extends FiltroDictionarySource> sourceClass) {
        Objects.requireNonNull(sourceClass, "sourceClass");
        if (sourceClass == FiltroDictionarySource.None.class) {
            throw new IllegalArgumentException("Cannot resolve FiltroDictionarySource.None");
        }
        FiltroDictionarySource source;
        try {
            source = applicationContext.getBean(sourceClass);
        } catch (Exception e) {
            throw new IllegalStateException(
                    "No Spring bean of type " + sourceClass.getName()
                            + " for @FiltroOneOf(source=...). Register the implementation as a bean.",
                    e);
        }
        Map<String, String> dict = source.dictionary();
        if (dict == null) {
            throw new IllegalStateException(
                    sourceClass.getName() + ".dictionary() returned null");
        }
        return new LinkedHashMap<>(dict);
    }
}
