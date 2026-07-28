package cc.ddrpa.filtro.visitor.extension.meilisearch;

import cc.ddrpa.filtro.core.exception.PredicateBuildException;
import cc.ddrpa.filtro.core.field.FiltroFieldMeta;
import cc.ddrpa.filtro.core.field.FiltroOperator;
import cc.ddrpa.filtro.core.field.QueryIntent;
import cz.jirutka.rsql.parser.RSQLParser;
import cz.jirutka.rsql.parser.ast.ComparisonNode;
import cz.jirutka.rsql.parser.ast.ComparisonOperator;
import cz.jirutka.rsql.parser.ast.Node;
import cz.jirutka.rsql.parser.ast.RSQLOperators;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MeilisearchFilterVisitorTest {

    enum Status {ACTIVE, INACTIVE}

    private static FiltroFieldMeta meta(String field, String key, QueryIntent intent, Set<FiltroOperator> ops) {
        FiltroFieldMeta m = new FiltroFieldMeta();
        m.setField(field);
        m.setKey(key);
        m.setQueryIntent(intent);
        m.setSupportedOperations(ops);
        return m;
    }

    private Map<String, FiltroFieldMeta> fieldMap;
    private RSQLParser parser;

    @BeforeEach
    void setUp() {
        FiltroFieldMeta status = meta("status", "status", QueryIntent.CATEGORY,
                Set.of(FiltroOperator.EQ, FiltroOperator.IN, FiltroOperator.NOT_IN));
        status.setEnumerationClass(Status.class);

        fieldMap = new HashMap<>();
        fieldMap.put("title", meta("title", "title", QueryIntent.SEARCH,
                Set.of(FiltroOperator.EQ, FiltroOperator.NEQ, FiltroOperator.NULLABLE_NEQ,
                        FiltroOperator.CONTAINS, FiltroOperator.PREFIX, FiltroOperator.SUFFIX,
                        FiltroOperator.IS_NULL, FiltroOperator.NOT_NULL, FiltroOperator.IN)));
        fieldMap.put("price", meta("price", "price", QueryIntent.QUANTITY,
                Set.of(FiltroOperator.EQ, FiltroOperator.GT, FiltroOperator.ALT_GT,
                        FiltroOperator.GTE, FiltroOperator.ALT_GTE,
                        FiltroOperator.LT, FiltroOperator.ALT_LT,
                        FiltroOperator.LTE, FiltroOperator.ALT_LTE, FiltroOperator.IN)));
        fieldMap.put("rating", meta("rating", "rating", QueryIntent.MEASURE,
                Set.of(FiltroOperator.GT, FiltroOperator.ALT_GT, FiltroOperator.LT, FiltroOperator.ALT_LT)));
        fieldMap.put("active", meta("active", "active", QueryIntent.BOOLEAN,
                Set.of(FiltroOperator.EQ, FiltroOperator.NEQ)));
        fieldMap.put("publishedAt", meta("publishedAt", "published_at", QueryIntent.DATETIME,
                Set.of(FiltroOperator.GT, FiltroOperator.ALT_GT, FiltroOperator.EQ)));
        fieldMap.put("status", status);

        Set<ComparisonOperator> operators = new HashSet<>(RSQLOperators.defaultOperators());
        Pattern symbolPattern = Pattern.compile("=[a-zA-Z]*=|[><]=?|!=");
        Arrays.stream(FiltroOperator.values())
                .filter(op -> !op.isRsqlOriginal())
                .filter(op -> symbolPattern.matcher(op.getSymbol()).matches())
                .map(op -> new ComparisonOperator(op.getSymbol(), op.isMultiValue()))
                .forEach(operators::add);
        parser = new RSQLParser(operators);
    }

    private String parse(String rsql) {
        Node root = parser.parse(rsql);
        return new MeilisearchFilterVisitor(fieldMap).apply(root);
    }

    @Nested
    @DisplayName("基本操作符")
    class BasicOperators {
        @Test
        void eqQuotesString() {
            assertThat(parse("title==hello")).isEqualTo("title = \"hello\"");
        }

        @Test
        void eqQuotesSpaces() {
            assertThat(parse("title=='hello world'")).isEqualTo("title = \"hello world\"");
        }

        @Test
        void eqEscapesQuotesInArgument() {
            MeilisearchFilterVisitor visitor = new MeilisearchFilterVisitor(fieldMap) {
                @Override
                protected ResolvedComparison resolve(ComparisonNode node) {
                    return new ResolvedComparison(
                            fieldMap.get("title"), FiltroOperator.EQ, List.of("he\"llo"));
                }
            };
            ComparisonNode node = new ComparisonNode(
                    RSQLOperators.EQUAL, "title", List.of("placeholder"));
            assertThat(visitor.visit(node, null)).isEqualTo("title = \"he\\\"llo\"");
        }

        @Test
        void neq() {
            assertThat(parse("title!=hello")).isEqualTo("title != \"hello\"");
        }

        @Test
        void gtNumericUnquoted() {
            assertThat(parse("price=gt=100")).isEqualTo("price > 100");
        }

        @Test
        void altGt() {
            assertThat(parse("price>100")).isEqualTo("price > 100");
        }

        @Test
        void gte() {
            assertThat(parse("price>=10")).isEqualTo("price >= 10");
        }

        @Test
        void lt() {
            assertThat(parse("price<40")).isEqualTo("price < 40");
        }

        @Test
        void lte() {
            assertThat(parse("price<=40")).isEqualTo("price <= 40");
        }

        @Test
        void measureUnquoted() {
            assertThat(parse("rating>4.5")).isEqualTo("rating > 4.5");
        }

        @Test
        void booleanUnquoted() {
            assertThat(parse("active==true")).isEqualTo("active = true");
        }

        @Test
        void datetimeQuoted() {
            assertThat(parse("publishedAt==2024-01-01")).isEqualTo("published_at = \"2024-01-01\"");
        }

        @Test
        void invalidQuantityThrows() {
            assertThatThrownBy(() -> parse("price==abc"))
                    .isInstanceOf(PredicateBuildException.class);
        }
    }

    @Nested
    @DisplayName("集合与空值")
    class CollectionAndNull {
        @Test
        void in() {
            assertThat(parse("price=in=(10,20)")).isEqualTo("price IN [10, 20]");
        }

        @Test
        void notInEnum() {
            assertThat(parse("status=out=(ACTIVE,INACTIVE)"))
                    .isEqualTo("status NOT IN [\"ACTIVE\", \"INACTIVE\"]");
        }

        @Test
        void isNull() {
            assertThat(parse("title=null=''")).isEqualTo("title IS NULL");
        }

        @Test
        void notNull() {
            assertThat(parse("title=nonull=''")).isEqualTo("title IS NOT NULL");
        }

        @Test
        void nullableNeq() {
            MeilisearchFilterVisitor visitor = new MeilisearchFilterVisitor(fieldMap) {
                @Override
                protected ResolvedComparison resolve(ComparisonNode node) {
                    return new ResolvedComparison(
                            fieldMap.get("title"), FiltroOperator.NULLABLE_NEQ, List.of("hello"));
                }
            };
            ComparisonNode node = new ComparisonNode(
                    RSQLOperators.EQUAL, "title", List.of("hello"));
            assertThat(visitor.visit(node, null))
                    .isEqualTo("(title != \"hello\" OR title IS NULL)");
        }
    }

    @Nested
    @DisplayName("字符串模糊匹配")
    class StringFuzzy {
        @Test
        void contains() {
            assertThat(parse("title=contains=java")).isEqualTo("title CONTAINS \"java\"");
        }

        @Test
        void prefix() {
            assertThat(parse("title=prefix=ja")).isEqualTo("title STARTS WITH \"ja\"");
        }

        @Test
        void suffixUnsupported() {
            assertThatThrownBy(() -> parse("title=suffix=va"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("ENDS WITH");
        }
    }

    @Nested
    @DisplayName("逻辑组合")
    class Logic {
        @Test
        void and() {
            assertThat(parse("title=contains=java;price<40"))
                    .isEqualTo("(title CONTAINS \"java\" AND price < 40)");
        }

        @Test
        void or() {
            assertThat(parse("title=contains=java,title=contains=python"))
                    .isEqualTo("(title CONTAINS \"java\" OR title CONTAINS \"python\")");
        }

        @Test
        void nested() {
            assertThat(parse("title=contains=java,(title=contains=python;price<40)"))
                    .isEqualTo("(title CONTAINS \"java\" OR (title CONTAINS \"python\" AND price < 40))");
        }
    }

    @Nested
    @DisplayName("枚举")
    class Enumeration {
        @Test
        void eqEnum() {
            assertThat(parse("status==ACTIVE")).isEqualTo("status = \"ACTIVE\"");
        }

        @Test
        void invalidEnumThrows() {
            assertThatThrownBy(() -> parse("status==UNKNOWN"))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }
}
