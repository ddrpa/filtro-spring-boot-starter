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
        private Integer count;            // QUANTITY
        @Filtro
        private int countPrimitive;       // QUANTITY
        @Filtro
        private Long bigCount;            // QUANTITY
        @Filtro
        private Short smallCount;         // QUANTITY
        @Filtro
        private Float ratio;              // MEASURE
        @Filtro
        private Double score;             // MEASURE
        @Filtro
        private BigDecimal amount;        // AMOUNT
        @Filtro
        private Boolean active;           // BOOLEAN
        @Filtro
        private boolean activePrimitive;  // BOOLEAN
        @Filtro
        private LocalDate date;           // DATETIME
        @Filtro
        private LocalDateTime dateTime;   // DATETIME
        @Filtro
        private Instant instant;          // DATETIME
        @Filtro
        private LocalTime time;           // DATETIME
        @Filtro
        private Genre genre;              // CATEGORY
    }

    // ─── helpers ───

    @SuppressWarnings("unused")
    static class OverrideFields {
        @Filtro(intent = QueryIntent.EXACT)
        private String isbn;                           // explicit EXACT on String

        @Filtro(field = "bookTitle", key = "t_book_title", value = "书名")
        private String title;                          // custom field/key/description

        @Filtro(tooltip = "支持多选，最多 10 个")
        private String labeledTag;                     // description + tooltip

        @Filtro(value = "旧描述", tooltip = "新描述")
        private String preferDescription;              // description 优先于 value

        @Filtro(maxInSize = 10)
        private String tag;                            // maxInSize

        @Filtro(groups = {AdminRole.class})
        private String secret;                         // group
    }

    @SuppressWarnings("unused")
    static class OperatorSubtraction {
        @Filtro(operators = {FiltroOperator.CONTAINS})
        private String email;                          // SEARCH ∩ {CONTAINS} = {CONTAINS}

        @Filtro(operators = {FiltroOperator.LT, FiltroOperator.GT})
        private Integer price;                         // QUANTITY ∩ {LT, GT} → +ALT_LT, ALT_GT

        @Filtro(operators = {FiltroOperator.CONTAINS})
        private Integer nonsense;                      // CONTAINS ∉ QUANTITY → empty
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
        void integerIsQuantity() {
            assertThat(build(PlainTypes.class, "count").getQueryIntent()).isEqualTo(QueryIntent.QUANTITY);
        }

        @Test
        void intPrimitiveIsQuantity() {
            assertThat(build(PlainTypes.class, "countPrimitive").getQueryIntent()).isEqualTo(QueryIntent.QUANTITY);
        }

        @Test
        void longIsQuantity() {
            assertThat(build(PlainTypes.class, "bigCount").getQueryIntent()).isEqualTo(QueryIntent.QUANTITY);
        }

        @Test
        void shortIsQuantity() {
            assertThat(build(PlainTypes.class, "smallCount").getQueryIntent()).isEqualTo(QueryIntent.QUANTITY);
        }

        @Test
        void floatIsMeasure() {
            assertThat(build(PlainTypes.class, "ratio").getQueryIntent()).isEqualTo(QueryIntent.MEASURE);
        }

        @Test
        void doubleIsMeasure() {
            assertThat(build(PlainTypes.class, "score").getQueryIntent()).isEqualTo(QueryIntent.MEASURE);
        }

        @Test
        void bigDecimalIsAmount() {
            assertThat(build(PlainTypes.class, "amount").getQueryIntent()).isEqualTo(QueryIntent.AMOUNT);
        }

        @Test
        void booleanIsBoolean() {
            assertThat(build(PlainTypes.class, "active").getQueryIntent()).isEqualTo(QueryIntent.BOOLEAN);
        }

        @Test
        void boolPrimitiveIsBoolean() {
            assertThat(build(PlainTypes.class, "activePrimitive").getQueryIntent()).isEqualTo(QueryIntent.BOOLEAN);
        }

        @Test
        void localDateIsDatetime() {
            assertThat(build(PlainTypes.class, "date").getQueryIntent()).isEqualTo(QueryIntent.DATETIME);
        }

        @Test
        void localDateTimeIsDatetime() {
            assertThat(build(PlainTypes.class, "dateTime").getQueryIntent()).isEqualTo(QueryIntent.DATETIME);
        }

        @Test
        void instantIsDatetime() {
            assertThat(build(PlainTypes.class, "instant").getQueryIntent()).isEqualTo(QueryIntent.DATETIME);
        }

        @Test
        void localTimeIsDatetime() {
            assertThat(build(PlainTypes.class, "time").getQueryIntent()).isEqualTo(QueryIntent.DATETIME);
        }

        @Test
        void enumIsCategory() {
            assertThat(build(PlainTypes.class, "genre").getQueryIntent()).isEqualTo(QueryIntent.CATEGORY);
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
        void searchHasFuzzyOps() {
            Set<FiltroOperator> o = ops("title");
            assertThat(o).contains(FiltroOperator.EQ, FiltroOperator.NEQ, FiltroOperator.NULLABLE_NEQ,
                    FiltroOperator.IN, FiltroOperator.NOT_IN,
                    FiltroOperator.PREFIX, FiltroOperator.SUFFIX, FiltroOperator.CONTAINS,
                    FiltroOperator.IS_NULL, FiltroOperator.NOT_NULL);
            assertThat(o).hasSize(10);
        }

        @Test
        void quantityHasFullNumericWithAlts() {
            Set<FiltroOperator> o = ops("count");
            assertThat(o).contains(FiltroOperator.EQ, FiltroOperator.NEQ,
                    FiltroOperator.GT, FiltroOperator.ALT_GT, FiltroOperator.GTE, FiltroOperator.ALT_GTE,
                    FiltroOperator.LT, FiltroOperator.ALT_LT, FiltroOperator.LTE, FiltroOperator.ALT_LTE,
                    FiltroOperator.IN, FiltroOperator.NOT_IN,
                    FiltroOperator.IS_NULL, FiltroOperator.NOT_NULL);
        }

        @Test
        void measureHasRangeNoEq() {
            Set<FiltroOperator> o = ops("ratio");
            assertThat(o).doesNotContain(FiltroOperator.EQ, FiltroOperator.NEQ, FiltroOperator.NULLABLE_NEQ);
            assertThat(o).contains(FiltroOperator.GT, FiltroOperator.LT,
                    FiltroOperator.ALT_GT, FiltroOperator.ALT_LT,
                    FiltroOperator.IN, FiltroOperator.NOT_IN,
                    FiltroOperator.IS_NULL, FiltroOperator.NOT_NULL);
        }

        @Test
        void booleanHasEqNeqOnly() {
            Set<FiltroOperator> o = ops("active");
            assertThat(o).containsExactlyInAnyOrder(
                    FiltroOperator.EQ, FiltroOperator.NEQ,
                    FiltroOperator.IS_NULL, FiltroOperator.NOT_NULL);
        }

        @Test
        void datetimeHasFullComparison() {
            Set<FiltroOperator> o = ops("date");
            assertThat(o).contains(FiltroOperator.EQ, FiltroOperator.NEQ, FiltroOperator.NULLABLE_NEQ,
                    FiltroOperator.GT, FiltroOperator.ALT_GT, FiltroOperator.GTE, FiltroOperator.ALT_GTE,
                    FiltroOperator.LT, FiltroOperator.ALT_LT, FiltroOperator.LTE, FiltroOperator.ALT_LTE,
                    FiltroOperator.IN, FiltroOperator.NOT_IN,
                    FiltroOperator.IS_NULL, FiltroOperator.NOT_NULL);
        }

        @Test
        void categoryHasEqInNoFuzzy() {
            Set<FiltroOperator> o = ops("genre");
            assertThat(o).contains(FiltroOperator.EQ, FiltroOperator.NEQ, FiltroOperator.NULLABLE_NEQ,
                    FiltroOperator.IN, FiltroOperator.NOT_IN,
                    FiltroOperator.IS_NULL, FiltroOperator.NOT_NULL);
            assertThat(o).doesNotContain(FiltroOperator.PREFIX, FiltroOperator.SUFFIX, FiltroOperator.CONTAINS);
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
            assertThat(meta.getSupportedOperations()).doesNotContain(FiltroOperator.PREFIX, FiltroOperator.CONTAINS);
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
        void descriptionTakesPrecedenceOverValue() {
            assertThat(build(OverrideFields.class, "preferDescription").getLabel())
                    .isEqualTo("新描述");
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
        void maxInSize() {
            assertThat(build(OverrideFields.class, "tag").getMaxInSize()).isEqualTo(10);
        }

        @Test
        void defaultMaxInSizeIsZero() {
            assertThat(build(PlainTypes.class, "title").getMaxInSize()).isZero();
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
            // CONTAINS is not in QUANTITY default set → retainedAll results in empty
            assertThat(meta.getSupportedOperations()).isEmpty();
        }

        @Test
        void emptyOperatorsUsesDefaultFullSet() {
            FiltroFieldMeta meta = build(PlainTypes.class, "title");
            assertThat(meta.getSupportedOperations()).isNotEmpty();
            assertThat(meta.getSupportedOperations()).contains(FiltroOperator.EQ, FiltroOperator.PREFIX);
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
