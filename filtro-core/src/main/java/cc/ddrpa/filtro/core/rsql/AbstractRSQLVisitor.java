package cc.ddrpa.filtro.core.rsql;

import cc.ddrpa.filtro.core.field.FiltroFieldMeta;
import cc.ddrpa.filtro.core.field.FiltroOperator;
import cz.jirutka.rsql.parser.ast.ComparisonNode;
import cz.jirutka.rsql.parser.ast.Node;

import java.util.List;
import java.util.Map;

/**
 * RSQL Visitor 抽象基类，提供字段校验、操作符解析、深度限制和 IN 参数上限校验。
 *
 * @param <T> 后端 DSL 类型（如 QueryWrapper、Criteria）
 */
public abstract class AbstractRSQLVisitor<T> {

    protected static final int DEFAULT_MAX_DEPTH = 20;

    protected final Map<String, FiltroFieldMeta> fieldSpecMap;
    protected final int maxDepth;

    protected AbstractRSQLVisitor(Map<String, FiltroFieldMeta> fieldSpecMap, int maxDepth) {
        this.fieldSpecMap = fieldSpecMap;
        this.maxDepth = maxDepth;
    }

    protected AbstractRSQLVisitor(Map<String, FiltroFieldMeta> fieldSpecMap) {
        this(fieldSpecMap, DEFAULT_MAX_DEPTH);
    }

    /**
     * 校验 AST 嵌套深度，防止栈溢出或后端嵌套超限。
     */
    protected void validateDepth(Node rootNode) {
        int depth = computeDepth(rootNode, 0);
        if (depth > maxDepth) {
            throw new IllegalArgumentException(
                    "RSQL nesting depth " + depth + " exceeds maximum " + maxDepth);
        }
    }

    private int computeDepth(Node node, int currentDepth) {
        if (node instanceof cz.jirutka.rsql.parser.ast.LogicalNode logicalNode) {
            int maxChild = currentDepth + 1;
            for (Node child : logicalNode.getChildren()) {
                maxChild = Math.max(maxChild, computeDepth(child, currentDepth + 1));
            }
            return maxChild;
        }
        return currentDepth;
    }

    /**
     * 解析 ComparisonNode，完成字段存在性、操作符合法性和支持性三部校验。
     *
     * @return ResolvedComparison 包含字段元数据、操作符和转换后的参数列表
     */
    protected ResolvedComparison resolve(ComparisonNode node) {
        String claimedField = node.getSelector();
        if (!fieldSpecMap.containsKey(claimedField)) {
            throw new IllegalArgumentException("Field " + claimedField + " not found in filtroFieldMeta");
        }

        FiltroOperator operator = FiltroOperator.of(node.getOperator().getSymbol());
        FiltroFieldMeta meta = fieldSpecMap.get(claimedField);

        if (!meta.getSupportedOperations().contains(operator)) {
            throw new IllegalArgumentException(
                    "FiltroOperator " + node.getOperator().getSymbol() + " not supported for field " + claimedField);
        }

        List<String> arguments = node.getArguments();

        return new ResolvedComparison(meta, operator, arguments);
    }

    /**
     * 解析后的比较节点信息。
     */
    public record ResolvedComparison(FiltroFieldMeta meta, FiltroOperator operator, List<String> arguments) {
    }
}
