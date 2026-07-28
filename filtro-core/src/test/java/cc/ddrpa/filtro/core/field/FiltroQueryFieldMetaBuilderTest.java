package cc.ddrpa.filtro.core.field;

import cc.ddrpa.filtro.core.annotation.Filtro;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FiltroQueryFieldMetaBuilderTest {

    // ─── test fixtures ───

    private static FiltroFieldMeta build(Class<?> clazz, String fieldName) {
        try {
            Field f = clazz.getDeclaredField(fieldName);
            Filtro anno = f.getAnnotation(Filtro.class);
            return new FiltroFieldMetaBuilder(f, anno).build();
        } catch (NoSuchFieldException e) {
            throw new RuntimeException(e);
        }
    }

    private static FiltroFieldMeta buildWithOps(Class<?> clazz, String fieldName) {
        try {
            Field f = clazz.getDeclaredField(fieldName);
            Filtro anno = f.getAnnotation(Filtro.class);
            FiltroFieldMetaBuilder builder = new FiltroFieldMetaBuilder(f, anno);
            if (anno.operators().length > 0) {
                builder.setClaimedOperators(Set.of(anno.operators()));
            }
            return builder.build();
        } catch (NoSuchFieldException e) {
            throw new RuntimeException(e);
        }
    }

    enum Genre {FICTION, NON_FICTION, SCIENCE}

    interface AdminRole {
    }

    @SuppressWarnings("unused")
    static class PlainTypes {
        @Filtro
        private String title;            // SEARCH
        @Filtro
        private Integer count;            // RANGE
        @Filtro
        private int countPrimitive;       // RANGE
        @Filtro
        private Long bigCount;            // RANGE
        @Filtro
        private Short smallCount;         // RANGE
        @Filtro
        private Float ratio;              // RANGE (float, no EQ)
        @Filtro
        private Double score;             // RANGE (float, no EQ)
        @Filtro
        private BigDecimal amount;        // RANGE
        @Filtro
        private Boolean active;           // EXACT
        @Filtro
        private boolean activePrimitive;  // EXACT
        @Filtro
        private LocalDate date;           // RANGE
        @Filtro
        private LocalDateTime dateTime;   // RANGE
        @Filtro
        private Instant instant;          // RANGE
        @Filtro
        private LocalTime time;           // RANGE
        @Filtro
        private Genre genre;              // EXACT + enum dict
    }

    // ─── helpers ───

    @SuppressWarnings("unused")
    static class OverrideFields {
        @Filtro(intent = QueryIntent.EXACT)
        private String isbn;                           // explicit EXACT on String

        @Filtro(field = "bookTitle", key = "t_book_title", value = "书名")
        private String title;                          // custom field/key/description

        @Filtro(value = "标签", tooltip = "支持多选，最多 10 个")
        private String labeledTag;                     // description + tooltip

        @Filtro(value = "新描述", tooltip = "提示文案")
        private String preferDescription;              // value 作为 label

        @Filtro(groups = {AdminRole.class})
        private String secret;                         // group
    }

    @SuppressWarnings("unused")
    static class OperatorSubtraction {
        @Filtro(operators = {FiltroOperator.CONTAINS})
        private String email;                          // SEARCH ∩ {CONTAINS} = {CONTAINS}

        @Filtro(operators = {FiltroOperator.LT, FiltroOperator.GT})
        private Integer price;                         // RANGE ∩ {LT, GT} → +ALT_LT, ALT_GT

        @Filtro(operators = {FiltroOperator.CONTAINS})
        private Integer nonsense;                      // CONTAINS ∉ RANGE → empty
    }

    // ─── type inference ───

    @Nested
    @DisplayName("类型推断 — Java类型 → QueryIntent")
    class TypeInference {
        @Test
        void stringIsSearch() {
            assertThat(build(PlainTypes.class, "title").getQueryIntent()).isEqualTo(QueryIntent.SEARCH);
        }

        @Test
        void integerIsRange() {
            assertThat(build(PlainTypes.class, "count").getQueryIntent()).isEqualTo(QueryIntent.RANGE);
        }

        @Test
        void intPrimitiveIsRange() {
            assertThat(build(PlainTypes.class, "countPrimitive").getQueryIntent()).isEqualTo(QueryIntent.RANGE);
        }

        @Test
        void longIsRange() {
            assertThat(build(PlainTypes.class, "bigCount").getQueryIntent()).isEqualTo(QueryIntent.RANGE);
        }

        @Test
        void shortIsRange() {
            assertThat(build(PlainTypes.class, "smallCount").getQueryIntent()).isEqualTo(QueryIntent.RANGE);
        }

        @Test
        void floatIsRange() {
            assertThat(build(PlainTypes.class, "ratio").getQueryIntent()).isEqualTo(QueryIntent.RANGE);
        }

        @Test
        void doubleIsRange() {
            assertThat(build(PlainTypes.class, "score").getQueryIntent()).isEqualTo(QueryIntent.RANGE);
        }

        @Test
        void bigDecimalIsRange() {
            assertThat(build(PlainTypes.class, "amount").getQueryIntent()).isEqualTo(QueryIntent.RANGE);
        }

        @Test
        void booleanIsExact() {
            assertThat(build(PlainTypes.class, "active").getQueryIntent()).isEqualTo(QueryIntent.EXACT);
        }

        @Test
        void boolPrimitiveIsExact() {
            assertThat(build(PlainTypes.class, "activePrimitive").getQueryIntent()).isEqualTo(QueryIntent.EXACT);
        }

        @Test
        void localDateIsRange() {
            assertThat(build(PlainTypes.class, "date").getQueryIntent()).isEqualTo(QueryIntent.RANGE);
        }

        @Test
        void localDateTimeIsRange() {
            assertThat(build(PlainTypes.class, "dateTime").getQueryIntent()).isEqualTo(QueryIntent.RANGE);
        }

        @Test
        void instantIsRange() {
            assertThat(build(PlainTypes.class, "instant").getQueryIntent()).isEqualTo(QueryIntent.RANGE);
        }

        @Test
        void localTimeIsRange() {
            assertThat(build(PlainTypes.class, "time").getQueryIntent()).isEqualTo(QueryIntent.RANGE);
        }

        @Test
        void enumIsExact() {
            assertThat(build(PlainTypes.class, "genre").getQueryIntent()).isEqualTo(QueryIntent.EXACT);
        }

        @Test
        void javaTypeIsPreserved() {
            assertThat(build(PlainTypes.class, "count").getJavaType()).isEqualTo(Integer.class);
            assertThat(build(PlainTypes.class, "title").getJavaType()).isEqualTo(String.class);
        }
    }

    // ─── default operator sets per intent ───

    @Nested
    @DisplayName("默认操作符集")
    class DefaultOperators {
        private static Set<FiltroOperator> ops(String field) {
            return build(PlainTypes.class, field).getSupportedOperations();
        }

        @Test
        void searchHasContainsAndNullOps() {
            Set<FiltroOperator> o = ops("title");
            assertThat(o).containsExactlyInAnyOrder(
                    FiltroOperator.CONTAINS, FiltroOperator.NOT_CONTAINS,
                    FiltroOperator.IS_NULL, FiltroOperator.NOT_NULL);
        }

        @Test
        void rangeIntegerHasComparisonWithEqNoIn() {
            Set<FiltroOperator> o = ops("count");
            assertThat(o).containsExactlyInAnyOrder(
                    FiltroOperator.EQ, FiltroOperator.NEQ, FiltroOperator.NULLABLE_NEQ,
                    FiltroOperator.GT, FiltroOperator.ALT_GT, FiltroOperator.GTE, FiltroOperator.ALT_GTE,
                    FiltroOperator.LT, FiltroOperator.ALT_LT, FiltroOperator.LTE, FiltroOperator.ALT_LTE,
                    FiltroOperator.IS_NULL, FiltroOperator.NOT_NULL);
            assertThat(o).doesNotContain(FiltroOperator.IN, FiltroOperator.NOT_IN);
        }

        @Test
        void rangeFloatHasRangeNoEq() {
            Set<FiltroOperator> o = ops("ratio");
            assertThat(o).containsExactlyInAnyOrder(
                    FiltroOperator.GT, FiltroOperator.LT,
                    FiltroOperator.ALT_GT, FiltroOperator.ALT_LT,
                    FiltroOperator.IS_NULL, FiltroOperator.NOT_NULL);
        }

        @Test
        void booleanExactHasEqNeqAndNull() {
            Set<FiltroOperator> o = ops("active");
            assertThat(o).containsExactlyInAnyOrder(
                    FiltroOperator.EQ, FiltroOperator.NEQ,
                    FiltroOperator.IS_NULL, FiltroOperator.NOT_NULL);
        }

        @Test
        void rangeDatetimeHasFullComparisonNoIn() {
            Set<FiltroOperator> o = ops("date");
            assertThat(o).containsExactlyInAnyOrder(
                    FiltroOperator.EQ, FiltroOperator.NEQ, FiltroOperator.NULLABLE_NEQ,
                    FiltroOperator.GT, FiltroOperator.ALT_GT, FiltroOperator.GTE, FiltroOperator.ALT_GTE,
                    FiltroOperator.LT, FiltroOperator.ALT_LT, FiltroOperator.LTE, FiltroOperator.ALT_LTE,
                    FiltroOperator.IS_NULL, FiltroOperator.NOT_NULL);
            assertThat(o).doesNotContain(FiltroOperator.IN, FiltroOperator.NOT_IN);
        }

        @Test
        void enumExactHasEqInNoFuzzy() {
            Set<FiltroOperator> o = ops("genre");
            assertThat(o).containsExactlyInAnyOrder(
                    FiltroOperator.EQ, FiltroOperator.NEQ, FiltroOperator.NULLABLE_NEQ,
                    FiltroOperator.IN, FiltroOperator.NOT_IN,
                    FiltroOperator.IS_NULL, FiltroOperator.NOT_NULL);
            assertThat(o).doesNotContain(FiltroOperator.CONTAINS, FiltroOperator.NOT_CONTAINS);
        }
    }

    // ─── annotation overrides ───

    @Nested
    @DisplayName("@Filtro 注解覆盖")
    class AnnotationOverrides {
        @Test
        void explicitIntentOverridesInference() {
            FiltroFieldMeta meta = build(OverrideFields.class, "isbn");
            assertThat(meta.getQueryIntent()).isEqualTo(QueryIntent.EXACT);
            assertThat(meta.getSupportedOperations()).doesNotContain(FiltroOperator.CONTAINS, FiltroOperator.NOT_CONTAINS);
        }

        @Test
        void customFieldName() {
            assertThat(build(OverrideFields.class, "title").getField()).isEqualTo("bookTitle");
        }

        @Test
        void customKey() {
            assertThat(build(OverrideFields.class, "title").getKey()).isEqualTo("t_book_title");
        }

        @Test
        void customDescription() {
            assertThat(build(OverrideFields.class, "title").getLabel()).isEqualTo("书名");
        }

        @Test
        void descriptionAndTooltip() {
            FiltroFieldMeta meta = build(OverrideFields.class, "labeledTag");
            assertThat(meta.getLabel()).isEqualTo("标签");
            assertThat(meta.getTooltip()).isEqualTo("支持多选，最多 10 个");
        }

        @Test
        void valueSetsLabel() {
            assertThat(build(OverrideFields.class, "preferDescription").getLabel())
                    .isEqualTo("新描述");
            assertThat(build(OverrideFields.class, "preferDescription").getTooltip())
                    .isEqualTo("提示文案");
        }

        @Test
        void defaultTooltipIsEmpty() {
            assertThat(build(OverrideFields.class, "title").getTooltip()).isEmpty();
        }

        @Test
        void defaultFieldNameIsJavaFieldName() {
            assertThat(build(PlainTypes.class, "title").getField()).isEqualTo("title");
        }

        @Test
        void defaultKeyIsSnakeCase() {
            assertThat(build(PlainTypes.class, "bigCount").getKey()).isEqualTo("big_count");
        }

        @Test
        void groupsAreSet() {
            assertThat(build(OverrideFields.class, "secret").getGroups())
                    .containsExactly(AdminRole.class);
        }

        @Test
        void noGroupsDefaultsToEmpty() {
            assertThat(build(PlainTypes.class, "title").getGroups()).isEmpty();
        }
    }

    // ─── operator subtraction + ALT completion ───

    @Nested
    @DisplayName("操作符减法 + ALT 自动补全")
    class OperatorSubtractionTests {
        @Test
        void subtractToSingleOp() {
            FiltroFieldMeta meta = buildWithOps(OperatorSubtraction.class, "email");
            assertThat(meta.getSupportedOperations()).containsExactly(FiltroOperator.CONTAINS);
        }

        @Test
        void subtractAndAutoAlt() {
            FiltroFieldMeta meta = buildWithOps(OperatorSubtraction.class, "price");
            assertThat(meta.getSupportedOperations()).contains(
                    FiltroOperator.LT, FiltroOperator.ALT_LT,
                    FiltroOperator.GT, FiltroOperator.ALT_GT);
            assertThat(meta.getSupportedOperations()).doesNotContain(FiltroOperator.EQ, FiltroOperator.NEQ);
        }

        @Test
        void incompatibleOpGetsFilteredOut() {
            FiltroFieldMeta meta = buildWithOps(OperatorSubtraction.class, "nonsense");
            // CONTAINS is not in RANGE default set → retainedAll results in empty
            assertThat(meta.getSupportedOperations()).isEmpty();
        }

        @Test
        void emptyOperatorsUsesDefaultFullSet() {
            FiltroFieldMeta meta = build(PlainTypes.class, "title");
            assertThat(meta.getSupportedOperations()).isNotEmpty();
            assertThat(meta.getSupportedOperations()).contains(FiltroOperator.CONTAINS, FiltroOperator.NOT_CONTAINS);
            assertThat(meta.getSupportedOperations()).doesNotContain(FiltroOperator.EQ);
        }
    }

    // ─── enum toDict ───

    @Nested
    @DisplayName("枚举字典 toDict")
    class EnumDict {
        @Test
        void buildsDictFromEnumNames() {
            Map<String, String> dict = FiltroFieldMetaBuilder.toDict(Genre.class);
            assertThat(dict).containsEntry("FICTION", "FICTION")
                    .containsEntry("NON_FICTION", "NON_FICTION")
                    .containsEntry("SCIENCE", "SCIENCE");
        }

        @Test
        void enumFieldHasDictionary() {
            FiltroFieldMeta meta = build(PlainTypes.class, "genre");
            assertThat(meta.isEnumeration()).isTrue();
            assertThat(meta.getEnumerationClass()).isEqualTo(Genre.class);
            assertThat(meta.getEnumerationDictionary()).isNotEmpty();
        }

        @Test
        void nonEnumThrows() {
            @SuppressWarnings({"unchecked", "rawtypes"})
            Class<? extends Enum<?>> notEnum = (Class) String.class;
            assertThatThrownBy(() -> FiltroFieldMetaBuilder.toDict(notEnum))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Failed to build enum map");
        }
    }
}
