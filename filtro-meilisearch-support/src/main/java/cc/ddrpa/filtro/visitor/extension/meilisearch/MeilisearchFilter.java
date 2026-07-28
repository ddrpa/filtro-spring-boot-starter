package cc.ddrpa.filtro.visitor.extension.meilisearch;

import java.util.Objects;

/**
 * Meilisearch {@code filter} 表达式的不可变包装。
 * <p>
 * 将 {@link #expression()} 传给 Meilisearch 客户端的搜索参数即可，例如
 * {@code searchRequest.setFilter(filter.expression())}。
 */
public record MeilisearchFilter(String expression) {

    public MeilisearchFilter {
        expression = Objects.requireNonNullElse(expression, "");
    }

    public boolean isEmpty() {
        return expression.isBlank();
    }
}
