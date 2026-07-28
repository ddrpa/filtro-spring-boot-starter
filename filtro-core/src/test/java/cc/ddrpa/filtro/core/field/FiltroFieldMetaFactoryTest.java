package cc.ddrpa.filtro.core.field;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FiltroFieldMetaFactoryTest {

    enum Color {
        RED, BLUE
    }

    @Test
    void createBuildsMetaWithJavaTypeAndDefaults() {
        FiltroFieldMeta meta = FiltroFieldMetaFactory
                .create("color", QueryIntent.EXACT, String.class)
                .key("attrs.color")
                .label("颜色")
                .tooltip("可选色值")
                .build();

        assertThat(meta.getField()).isEqualTo("color");
        assertThat(meta.getKey()).isEqualTo("attrs.color");
        assertThat(meta.getQueryIntent()).isEqualTo(QueryIntent.EXACT);
        assertThat(meta.getJavaType()).isEqualTo(String.class);
        assertThat(meta.getLabel()).isEqualTo("颜色");
        assertThat(meta.getTooltip()).isEqualTo("可选色值");
        assertThat(meta.getSupportedOperations()).contains(FiltroOperator.EQ, FiltroOperator.IN);
        assertThat(meta.getSupportedOperations()).doesNotContain(FiltroOperator.CONTAINS);
    }

    @Test
    void enumJavaTypeFillsEnumerationDictionary() {
        FiltroFieldMeta meta = FiltroFieldMetaFactory
                .create("color", QueryIntent.EXACT, Color.class)
                .build();

        assertThat(meta.isEnumeration()).isTrue();
        assertThat(meta.getEnumerationClass()).isEqualTo(Color.class);
        assertThat(meta.getEnumerationDictionary()).containsKeys("RED", "BLUE");
    }

    @Test
    void rejectsAutoIntent() {
        assertThatThrownBy(() -> FiltroFieldMetaFactory.create("x", QueryIntent.AUTO, String.class))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("AUTO");
    }

    @Test
    void rejectsBlankField() {
        assertThatThrownBy(() -> FiltroFieldMetaFactory.create("  ", QueryIntent.SEARCH, String.class))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void operatorsSubtractionKeepsIntersection() {
        FiltroFieldMeta meta = FiltroFieldMetaFactory
                .create("title", QueryIntent.SEARCH, String.class)
                .operators(FiltroOperator.CONTAINS, FiltroOperator.EQ)
                .build();

        assertThat(meta.getSupportedOperations()).containsExactly(FiltroOperator.CONTAINS);
    }
}
