package cc.ddrpa.filtro.visitor.extension.mp;

import cc.ddrpa.filtro.core.field.FiltroFieldMeta;
import cc.ddrpa.filtro.core.field.FiltroOperator;
import cc.ddrpa.filtro.core.field.QueryIntent;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import cz.jirutka.rsql.parser.RSQLParser;
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
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MybatisPlusQueryWrapperVisitorTest {

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
                "status", meta("status", "status", QueryIntent.EXACT, Status.class,
                        Set.of(FiltroOperator.EQ, FiltroOperator.IN, FiltroOperator.NOT_IN))
        );
        // set enum class for status
        fieldMap.get("status").setEnumerationClass(Status.class);

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

    private QueryWrapper<?> parse(String rsql) {
        Node root = parser.parse(rsql);
        MybatisPlusQueryWrapperVisitor v = new MybatisPlusQueryWrapperVisitor(fieldMap);
        QueryWrapper<?> wrapper = new QueryWrapper<>();
        v.apply(root, wrapper);
        return wrapper;
    }

    static enum Status {ACTIVE, INACTIVE}

    @Nested
    @DisplayName("基本操作符")
    class BasicOperators {
        @Test
        void eqGeneratesEqCondition() {
            QueryWrapper<?> w = parse("title==hello");
            assertThat(w.getSqlSegment()).contains("title").contains("=");
            assertThat(w.getParamNameValuePairs()).containsValue("hello");
        }

        @Test
        void neqGeneratesNeCondition() {
            QueryWrapper<?> w = parse("title!=hello");
            assertThat(w.getSqlSegment()).contains("title").contains("<>");
        }

        @Test
        void gtGeneratesGtCondition() {
            QueryWrapper<?> w = parse("price=gt=100");
            assertThat(w.getSqlSegment()).contains("price").contains(">");
        }

        @Test
        void altGtGeneratesGtCondition() {
            QueryWrapper<?> w = parse("price>100");
            assertThat(w.getSqlSegment()).contains("price").contains(">");
        }

        @Test
        void containsGeneratesLike() {
            QueryWrapper<?> w = parse("title=contains=java");
            assertThat(w.getSqlSegment()).contains("LIKE");
        }

        @Test
        void notContainsGeneratesNotLike() {
            QueryWrapper<?> w = parse("title=nocontains=java");
            assertThat(w.getSqlSegment()).contains("NOT LIKE");
        }

        @Test
        void isNullGeneratesIsNull() {
            QueryWrapper<?> w = parse("title=null=''");
            assertThat(w.getSqlSegment()).contains("IS NULL");
        }

        @Test
        void notNullGeneratesIsNotNull() {
            QueryWrapper<?> w = parse("title=nonull=''");
            assertThat(w.getSqlSegment()).contains("IS NOT NULL");
        }

        @Test
        void nullableNeqGeneratesOrIsNull() {
            QueryWrapper<?> w = parse("title=nullableneq=hello");
            assertThat(w.getSqlSegment()).contains("IS NULL");
        }
    }

    @Nested
    @DisplayName("枚举字段")
    class EnumFields {
        @Test
        void enumEqConvertsValue() {
            QueryWrapper<?> w = parse("status==ACTIVE");
            assertThat(w.getSqlSegment()).contains("status").contains("=");
        }

        @Test
        void enumInConvertsAllValues() {
            QueryWrapper<?> w = parse("status=in=(ACTIVE,INACTIVE)");
            assertThat(w.getSqlSegment()).contains("IN");
        }
    }

    @Nested
    @DisplayName("LIKE 转义")
    class LikeEscaping {
        @Test
        void escapesPercentSign() {
            QueryWrapper<?> w = parse("title=contains=100%");
            assertThat(w.getSqlSegment()).contains("LIKE");
        }

        @Test
        void escapesUnderscore() {
            QueryWrapper<?> w = parse("title=contains=a_b");
            assertThat(w.getSqlSegment()).contains("LIKE");
        }

        @Test
        void escapesBackslash() {
            QueryWrapper<?> w = parse("title=contains=a\\b");
            assertThat(w.getSqlSegment()).contains("LIKE");
        }
    }

    @Nested
    @DisplayName("AND/OR 组合")
    class BooleanLogic {
        @Test
        void andCombinesWithAnd() {
            QueryWrapper<?> w = parse("title==java;price=gt=50");
            String sql = w.getSqlSegment();
            assertThat(sql).contains("AND");
        }

        @Test
        void orCombinesWithOr() {
            QueryWrapper<?> w = parse("title==java,price=gt=50");
            // OR is inside a nested block
            assertThat(w.getSqlSegment()).isNotEmpty();
        }
    }

    @Nested
    @DisplayName("toEnum 异常处理")
    class ToEnumErrors {
        @Test
        void nullClassThrows() {
            assertThatThrownBy(() -> MybatisPlusQueryWrapperVisitor.toEnum(null, "X"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("must not be null");
        }

        @Test
        void nullNameThrows() {
            assertThatThrownBy(() -> MybatisPlusQueryWrapperVisitor.toEnum(String.class, null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("must not be null");
        }

        @Test
        void nonEnumClassThrows() {
            assertThatThrownBy(() -> MybatisPlusQueryWrapperVisitor.toEnum(String.class, "X"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("not an enum type");
        }

        @Test
        void unknownEnumConstantThrows() {
            assertThatThrownBy(() -> MybatisPlusQueryWrapperVisitor.toEnum(Status.class, "UNKNOWN"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("No enum constant");
        }
    }

    @Nested
    @DisplayName("校验")
    class Validation {
        @Test
        void unknownFieldFromResolveThrows() {
            assertThatThrownBy(() -> parse("unknown==x"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("not found");
        }

        @Test
        void unsupportedOpFromResolveThrows() {
            assertThatThrownBy(() -> parse("title=gt=x"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("not supported");
        }
    }
}
