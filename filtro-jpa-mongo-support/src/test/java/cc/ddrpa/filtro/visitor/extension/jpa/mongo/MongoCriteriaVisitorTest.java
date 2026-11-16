package cc.ddrpa.filtro.visitor.extension.jpa.mongo;

import cc.ddrpa.filtro.core.field.FiltroFieldMeta;
import cc.ddrpa.filtro.core.field.FiltroOperator;
import cc.ddrpa.filtro.core.field.QueryIntent;
import cz.jirutka.rsql.parser.RSQLParser;
import cz.jirutka.rsql.parser.ast.ComparisonOperator;
import cz.jirutka.rsql.parser.ast.Node;
import cz.jirutka.rsql.parser.ast.RSQLOperators;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.query.Criteria;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MongoCriteriaVisitorTest {

    private Map<String, FiltroFieldMeta> fieldMap;
    private RSQLParser parser;

    private static FiltroFieldMeta meta(String field, String key, QueryIntent intent,
                                        Class<?> javaType, Set<FiltroOperator> ops) {
        FiltroFieldMeta m = new FiltroFieldMeta();
        m.setField(field);
        m.setKey(key);
        m.setQueryIntent(intent);
        m.setJavaType(javaType);
        m.setSupportedOperations(ops);
        return m;
    }

    @BeforeEach
    void setUp() {
        fieldMap = Map.of(
                "title", meta("title", "title", QueryIntent.SEARCH, String.class,
                        Set.of(FiltroOperator.EQ, FiltroOperator.NEQ,
                                FiltroOperator.CONTAINS, FiltroOperator.NOT_CONTAINS,
                                FiltroOperator.IS_NULL, FiltroOperator.NOT_NULL,
                                FiltroOperator.NULLABLE_NEQ)),
                "price", meta("price", "price", QueryIntent.RANGE, Integer.class,
                        Set.of(FiltroOperator.EQ, FiltroOperator.GT, FiltroOperator.ALT_GT,
                                FiltroOperator.LT, FiltroOperator.ALT_LT, FiltroOperator.IN)),
                "createdAt", meta("createdAt", "createdAt", QueryIntent.RANGE, java.time.LocalDateTime.class,
                        Set.of(FiltroOperator.EQ, FiltroOperator.GT, FiltroOperator.LT)),
                "amount", meta("amount", "amount", QueryIntent.RANGE, java.math.BigDecimal.class,
                        Set.of(FiltroOperator.EQ, FiltroOperator.GT, FiltroOperator.LT))
        );
        // Build parser with FiltroQuery extension operators
        Set<ComparisonOperator> operators = new HashSet<>(RSQLOperators.defaultOperators());
        Pattern symbolPattern = Pattern.compile("=[a-zA-Z]*=|[><]=?|!=");
        Arrays.stream(FiltroOperator.values())
                .filter(op -> !op.isRsqlOriginal())
                .filter(op -> symbolPattern.matcher(op.getSymbol()).matches())
                .map(op -> new ComparisonOperator(op.getSymbol(), op.isMultiValue()))
                .forEach(operators::add);
        parser = new RSQLParser(operators);
    }

    private Criteria parse(String rsql) {
        Node root = parser.parse(rsql);
        MongoCriteriaVisitor v = new MongoCriteriaVisitor(fieldMap);
        Criteria criteria = new Criteria();
        v.apply(root, criteria);
        return criteria;
    }

    private Document criteriaDoc(Criteria criteria) {
        return criteria.getCriteriaObject();
    }

    @Nested
    @DisplayName("基本操作符")
    class BasicOperators {
        @Test
        void eqGeneratesIs() {
            Criteria c = parse("title==hello");
            Document doc = criteriaDoc(c);
            assertThat(doc.toJson()).contains("title").contains("hello");
        }

        @Test
        void neqGeneratesNe() {
            Criteria c = parse("title!=hello");
            Document doc = criteriaDoc(c);
            assertThat(doc.toJson()).contains("$ne");
        }

        @Test
        void gtGeneratesGt() {
            Criteria c = parse("price=gt=100");
            Document doc = criteriaDoc(c);
            assertThat(doc.toJson()).contains("$gt");
        }

        @Test
        void altGtGeneratesGt() {
            Criteria c = parse("price>100");
            Document doc = criteriaDoc(c);
            assertThat(doc.toJson()).contains("$gt");
        }

        @Test
        void ltGeneratesLt() {
            Criteria c = parse("price<50");
            Document doc = criteriaDoc(c);
            assertThat(doc.toJson()).contains("$lt");
        }

        @Test
        void containsGeneratesRegex() {
            Criteria c = parse("title=contains=java");
            Document doc = criteriaDoc(c);
            assertThat(doc.toJson()).contains("$regularExpression");
        }

        @Test
        void notContainsGeneratesNotRegex() {
            Criteria c = parse("title=nocontains=java");
            Document doc = criteriaDoc(c);
            assertThat(doc.toJson()).contains("$not");
        }

        @Test
        void isNullGeneratesOrIsNullAndNotExists() {
            Criteria c = parse("title=null=''");
            Document doc = criteriaDoc(c);
            assertThat(doc.toJson()).contains("$or");
        }

        @Test
        void notNullGeneratesExistsTrue() {
            Criteria c = parse("title=nonull=''");
            Document doc = criteriaDoc(c);
            assertThat(doc.toJson()).contains("$exists");
        }
    }

    @Nested
    @DisplayName("类型转换")
    class TypeConversion {
        @Test
        void quantityFieldParsedAsLong() {
            Criteria c = parse("price==100");
            Document doc = criteriaDoc(c);
            assertThat(doc.toJson()).contains("price").contains("100");
        }

        @Test
        void amountFieldParsedAsDecimal128() {
            Criteria c = parse("amount==99.99");
            Document doc = criteriaDoc(c);
            assertThat(doc.toJson()).contains("amount").contains("99.99");
        }

        @Test
        void datetimeFieldParsedAsDate() {
            Criteria c = parse("createdAt==2024-01-15T00:00:00Z");
            Document doc = criteriaDoc(c);
            assertThat(doc.toJson()).contains("createdAt");
        }

        @Test
        void invalidDatetimeThrowsPredicateBuildException() {
            assertThatThrownBy(() -> parse("createdAt==not-a-date"))
                    .isInstanceOf(cc.ddrpa.filtro.core.exception.PredicateBuildException.class)
                    .hasMessageContaining("createdAt");
        }
    }

    @Nested
    @DisplayName("校验")
    class Validation {
        @Test
        void unknownFieldThrows() {
            assertThatThrownBy(() -> parse("unknown==x"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("not found");
        }

        @Test
        void unsupportedOperatorThrows() {
            assertThatThrownBy(() -> parse("title=in=(a,b)"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("not supported");
        }
    }
}
