package cc.ddrpa.filtro.springboot;

import cc.ddrpa.filtro.core.field.FiltroFieldMeta;
import cc.ddrpa.filtro.core.field.QueryIntent;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FiltroFieldMetaVOTest {

    enum Genre {FICTION, NON_FICTION}

    private static FiltroFieldMeta meta(Class<?> javaType, QueryIntent intent) {
        FiltroFieldMeta m = new FiltroFieldMeta();
        m.setField("f");
        m.setKey("f");
        m.setJavaType(javaType);
        m.setQueryIntent(intent);
        return m;
    }

    @Test
    void stringIsText() {
        assertThat(FiltroFieldMetaVO.inferComponent(meta(String.class, QueryIntent.SEARCH)))
                .isEqualTo(FiltroComponent.TEXT);
    }

    @Test
    void numberTypesAreNumber() {
        assertThat(FiltroFieldMetaVO.inferComponent(meta(Integer.class, QueryIntent.RANGE)))
                .isEqualTo(FiltroComponent.NUMBER);
        assertThat(FiltroFieldMetaVO.inferComponent(meta(Double.class, QueryIntent.RANGE)))
                .isEqualTo(FiltroComponent.NUMBER);
        assertThat(FiltroFieldMetaVO.inferComponent(meta(BigDecimal.class, QueryIntent.RANGE)))
                .isEqualTo(FiltroComponent.NUMBER);
    }

    @Test
    void localDateIsDate() {
        assertThat(FiltroFieldMetaVO.inferComponent(meta(LocalDate.class, QueryIntent.RANGE)))
                .isEqualTo(FiltroComponent.DATE);
    }

    @Test
    void dateTimeTypesAreDatetime() {
        assertThat(FiltroFieldMetaVO.inferComponent(meta(LocalDateTime.class, QueryIntent.RANGE)))
                .isEqualTo(FiltroComponent.DATETIME);
        assertThat(FiltroFieldMetaVO.inferComponent(meta(Instant.class, QueryIntent.RANGE)))
                .isEqualTo(FiltroComponent.DATETIME);
        assertThat(FiltroFieldMetaVO.inferComponent(meta(LocalTime.class, QueryIntent.RANGE)))
                .isEqualTo(FiltroComponent.DATETIME);
    }

    @Test
    void booleanIsBoolean() {
        assertThat(FiltroFieldMetaVO.inferComponent(meta(Boolean.class, QueryIntent.EXACT)))
                .isEqualTo(FiltroComponent.CHECKBOX);
    }

    @Test
    void enumerationIsSelect() {
        FiltroFieldMeta m = meta(Genre.class, QueryIntent.EXACT);
        m.setEnumerationClass(Genre.class);
        m.setEnumerationDictionary(Map.of("小说", "FICTION"));
        assertThat(FiltroFieldMetaVO.inferComponent(m)).isEqualTo(FiltroComponent.SELECT);
    }

    @Test
    void oneOfDictionaryIsSelectWithoutEnumType() {
        FiltroFieldMeta m = meta(String.class, QueryIntent.EXACT);
        m.setEnumerationDictionary(Map.of("DRAFT", "DRAFT", "PUBLISHED", "PUBLISHED"));
        assertThat(m.isEnumeration()).isFalse();
        assertThat(m.hasDictionary()).isTrue();
        assertThat(FiltroFieldMetaVO.inferComponent(m)).isEqualTo(FiltroComponent.SELECT);

        FiltroFieldMetaVO vo = FiltroFieldMetaVO.from(m);
        assertThat(vo.getComponent()).isEqualTo(FiltroComponent.SELECT);
        assertThat(vo.getDictionary()).containsEntry("DRAFT", "DRAFT");
    }

    @Test
    void sourceClassIsSelectAndResolvedViaResolver() {
        class Src implements cc.ddrpa.filtro.core.dictionary.FiltroDictionarySource {
            @Override
            public Map<String, String> dictionary() {
                return Map.of("L", "V");
            }
        }
        FiltroFieldMeta m = meta(String.class, QueryIntent.EXACT);
        m.setDictionarySourceClass(Src.class);
        assertThat(FiltroFieldMetaVO.inferComponent(m)).isEqualTo(FiltroComponent.SELECT);

        FiltroFieldMetaVO vo = FiltroFieldMetaVO.from(m, type -> Map.of("L", "V"));
        assertThat(vo.getDictionary()).containsEntry("L", "V");
    }

    @Test
    void sourceWithoutResolverThrows() {
        FiltroFieldMeta m = meta(String.class, QueryIntent.EXACT);
        class Src implements cc.ddrpa.filtro.core.dictionary.FiltroDictionarySource {
            @Override
            public Map<String, String> dictionary() {
                return Map.of();
            }
        }
        m.setDictionarySourceClass(Src.class);
        assertThatThrownBy(() -> FiltroFieldMetaVO.from(m))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("FiltroDictionarySourceResolver");
    }

    @Test
    void fromDoesNotExposeJavaType() {
        FiltroFieldMetaVO vo = FiltroFieldMetaVO.from(meta(String.class, QueryIntent.SEARCH));
        assertThat(vo.getComponent()).isEqualTo(FiltroComponent.TEXT);
        assertThat(vo.getQueryIntent()).isEqualTo(QueryIntent.SEARCH);
    }
}
