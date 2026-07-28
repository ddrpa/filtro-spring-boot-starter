package cc.ddrpa.filtro.core.field;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FiltroOperatorTest {

    @ParameterizedTest
    @CsvSource({
            "==, EQ",
            "!=, NEQ",
            ">, GT",
            "=gt=, ALT_GT",
            ">=, GTE",
            "=ge=, ALT_GTE",
            "<, LT",
            "=lt=, ALT_LT",
            "<=, LTE",
            "=le=, ALT_LTE",
            "=in=, IN",
            "=out=, NOT_IN",
            "=null=, IS_NULL",
            "=nonull=, NOT_NULL",
            "=nullable-neq=, NULLABLE_NEQ",
            "=prefix=, PREFIX",
            "=suffix=, SUFFIX",
            "=contains=, CONTAINS",
    })
    void shouldParseAllKnownSymbols(String symbol, FiltroOperator expected) {
        assertThat(FiltroOperator.of(symbol)).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({
            "==, EQ",
            "!=, NEQ",
            "=gt=, ALT_GT",
            "=lt=, ALT_LT",
            "=in=, IN",
            "=out=, NOT_IN",
    })
    void shouldBeCaseInsensitive(String symbol, FiltroOperator expected) {
        assertThat(FiltroOperator.of(symbol.toUpperCase())).isEqualTo(expected);
    }

    @Test
    void shouldThrowForUnknownSymbol() {
        assertThatThrownBy(() -> FiltroOperator.of("=unknown="))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown RSQL operator")
                .hasMessageContaining("=unknown=");
    }

    @Test
    void shouldThrowForEmptySymbol() {
        assertThatThrownBy(() -> FiltroOperator.of(""))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldIdentifyRsqlOriginals() {
        assertThat(FiltroOperator.EQ.isRsqlOriginal()).isTrue();
        assertThat(FiltroOperator.NEQ.isRsqlOriginal()).isTrue();
        assertThat(FiltroOperator.GT.isRsqlOriginal()).isTrue();
        assertThat(FiltroOperator.IN.isRsqlOriginal()).isTrue();

        assertThat(FiltroOperator.IS_NULL.isRsqlOriginal()).isFalse();
        assertThat(FiltroOperator.PREFIX.isRsqlOriginal()).isFalse();
        assertThat(FiltroOperator.CONTAINS.isRsqlOriginal()).isFalse();
    }

    @Test
    void shouldIdentifyMultiValueOperators() {
        assertThat(FiltroOperator.IN.isMultiValue()).isTrue();
        assertThat(FiltroOperator.NOT_IN.isMultiValue()).isTrue();

        assertThat(FiltroOperator.EQ.isMultiValue()).isFalse();
        assertThat(FiltroOperator.GT.isMultiValue()).isFalse();
        assertThat(FiltroOperator.IS_NULL.isMultiValue()).isFalse();
        assertThat(FiltroOperator.CONTAINS.isMultiValue()).isFalse();
    }
}
