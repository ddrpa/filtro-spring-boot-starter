package cc.ddrpa.filtro.core.rsql;

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

import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AbstractRSQLVisitorTest {

    private static FiltroFieldMeta meta(String field, FiltroOperator... ops) {
        FiltroFieldMeta m = new FiltroFieldMeta();
        m.setField(field);
        m.setQueryIntent(QueryIntent.SEARCH);
        m.setSupportedOperations(Set.of(ops));
        m.setMaxInSize(0);
        return m;
    }

    /**
     * minimal concrete subclass for testing
     */
    static class TestVisitor extends AbstractRSQLVisitor<Void> {
        TestVisitor(Map<String, FiltroFieldMeta> fieldSpecMap) {
            super(fieldSpecMap);
        }

        TestVisitor(Map<String, FiltroFieldMeta> fieldSpecMap, int maxDepth) {
            super(fieldSpecMap, maxDepth);
        }
    }

    private Map<String, FiltroFieldMeta> fieldMap;
    private RSQLParser parser;

    @BeforeEach
    void setUp() {
        fieldMap = Map.of(
                "title", meta("title", FiltroOperator.EQ, FiltroOperator.NEQ, FiltroOperator.CONTAINS, FiltroOperator.IN),
                "price", meta("price", FiltroOperator.EQ, FiltroOperator.GT, FiltroOperator.LT,
                        FiltroOperator.ALT_GT, FiltroOperator.ALT_LT),
                "tags", meta("tags", FiltroOperator.IN, FiltroOperator.NOT_IN)
        );
        fieldMap.get("tags").setMaxInSize(5);

        // Build parser with compatible Filtro extension operators
        Set<ComparisonOperator> operators = new HashSet<>(RSQLOperators.defaultOperators());
        java.util.regex.Pattern symbolPattern = java.util.regex.Pattern.compile("=[a-zA-Z]*=|[><]=?|!=");
        Arrays.stream(FiltroOperator.values())
                .filter(op -> !op.isRsqlOriginal())
                .filter(op -> symbolPattern.matcher(op.getSymbol()).matches())
                .map(op -> new ComparisonOperator(op.getSymbol(), op.isMultiValue()))
                .forEach(operators::add);
        parser = new RSQLParser(operators);
    }

    @Nested
    @DisplayName("resolve — 字段校验")
    class ResolveValidation {

        /**
         * Extract a leaf ComparisonNode from a parse tree
         */
        private ComparisonNode cmp(String rsql) {
            Node root = parser.parse(rsql);
            // walk down to find the first ComparisonNode
            if (root instanceof ComparisonNode c) return c;
            if (root instanceof cz.jirutka.rsql.parser.ast.AndNode a) return (ComparisonNode) a.getChildren().get(0);
            if (root instanceof cz.jirutka.rsql.parser.ast.OrNode o) return (ComparisonNode) o.getChildren().get(0);
            throw new IllegalStateException("unexpected node type: " + root.getClass());
        }

        @Test
        void validFieldAndOpSucceeds() {
            TestVisitor v = new TestVisitor(fieldMap);
            AbstractRSQLVisitor.ResolvedComparison resolved = v.resolve(cmp("title==hello"));
            assertThat(resolved.meta().getField()).isEqualTo("title");
            assertThat(resolved.operator()).isEqualTo(FiltroOperator.EQ);
            assertThat(resolved.arguments()).containsExactly("hello");
        }

        @Test
        void unknownFieldThrows() {
            TestVisitor v = new TestVisitor(fieldMap);
            assertThatThrownBy(() -> v.resolve(cmp("unknown==x")))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("unknown")
                    .hasMessageContaining("not found");
        }

        @Test
        void unsupportedOperatorThrows() {
            TestVisitor v = new TestVisitor(fieldMap);
            assertThatThrownBy(() -> v.resolve(cmp("title=gt=x")))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("not supported");
        }

        @Test
        void inExceedsMaxInSizeThrows() {
            TestVisitor v = new TestVisitor(fieldMap);
            assertThatThrownBy(() -> v.resolve(cmp("tags=in=(a,b,c,d,e,f)")))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("IN/NOT_IN argument count");
        }

        @Test
        void inWithinMaxInSizeSucceeds() {
            TestVisitor v = new TestVisitor(fieldMap);
            AbstractRSQLVisitor.ResolvedComparison resolved = v.resolve(cmp("tags=in=(a,b,c,d,e)"));
            assertThat(resolved.arguments()).hasSize(5);
        }

        @Test
        void maxInSizeZeroMeansUnlimited() {
            // title has maxInSize=0 (unlimited)
            TestVisitor v = new TestVisitor(fieldMap);
            AbstractRSQLVisitor.ResolvedComparison resolved = v.resolve(cmp("title=in=(a,b,c,d,e,f,g,h)"));
            assertThat(resolved.arguments()).hasSize(8);
        }

        @Test
        void isNullNeedsNoArguments() {
            // need a field with IS_NULL support; RSQL parser requires =null='' syntax
            FiltroFieldMeta nullableMeta = meta("nullableField", FiltroOperator.IS_NULL);
            Map<String, FiltroFieldMeta> map = Map.of("nullableField", nullableMeta);
            TestVisitor v2 = new TestVisitor(map);
            assertThat(v2.resolve(cmp("nullableField=null=''")).operator())
                    .isEqualTo(FiltroOperator.IS_NULL);
        }
    }

    @Nested
    @DisplayName("validateDepth — 嵌套深度校验")
    class DepthValidation {

        @Test
        void shallowTreePasses() {
            TestVisitor v = new TestVisitor(fieldMap, 3);
            Node root = parser.parse("title==a;title==b");
            v.validateDepth(root); // should not throw
        }

        @Test
        void exceedingDepthThrows() {
            TestVisitor v = new TestVisitor(fieldMap, 1);
            // (title==a AND (title==b OR title==c)) → depth 2 > maxDepth 1
            Node root = parser.parse("title==a;(price==b,title==c)");
            assertThatThrownBy(() -> v.validateDepth(root))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("RSQL nesting depth")
                    .hasMessageContaining("exceeds maximum");
        }

        @Test
        void defaultMaxDepthIs20() {
            TestVisitor v = new TestVisitor(fieldMap);
            Node root = parser.parse("title==a");
            v.validateDepth(root); // should not throw
        }
    }
}
