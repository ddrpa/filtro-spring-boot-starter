package cc.ddrpa.filtro.springboot.autoconfigure;

import cc.ddrpa.filtro.core.exception.PredicateBuildException;
import cz.jirutka.rsql.parser.ParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;
import java.time.Instant;

/**
 * Filtro 全局异常处理，将内部异常转为 RFC 7807 ProblemDetail 响应。
 * <p>
 * 用户可通过声明同名 bean (name = "filtroExceptionHandler") 完全替换此实现。
 */
@RestControllerAdvice
public class FiltroExceptionHandler {
    private static final Logger logger = LoggerFactory.getLogger(FiltroExceptionHandler.class);
    private static final URI TYPE_INVALID_FILTER = URI.create("https://filtro.ddrpa.cc/problems/invalid-filter");

    /**
     * RSQL 语法解析异常。
     */
    @ExceptionHandler(ParseException.class)
    public ResponseEntity<ProblemDetail> handleParseException(ParseException ex) {
        logger.warn("RSQL parse error: {}", ex.getMessage());
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
                "Invalid filter syntax: " + ex.getMessage());
        problem.setTitle("Bad Filter Expression");
        problem.setType(TYPE_INVALID_FILTER);
        problem.setProperty("timestamp", Instant.now());
        return ResponseEntity.badRequest().body(problem);
    }

    /**
     * 字段不存在、操作符不支持、嵌套深度超限、IN 参数超量等校验异常。
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ProblemDetail> handleIllegalArgument(IllegalArgumentException ex) {
        String message = ex.getMessage();
        // 只拦截 Filtro 相关的 IllegalArgumentException（来自 visitor / resolve / validateDepth）
        if (message != null && (message.contains("filtroFieldMeta")
                || message.contains("FiltroOperator")
                || message.contains("RSQL nesting")
                || message.contains("IN/NOT_IN argument count"))) {
            logger.warn("Filtro validation rejected: {}", message);
            ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
                    "Invalid filter: " + message);
            problem.setTitle("Bad Filter Expression");
            problem.setType(TYPE_INVALID_FILTER);
            problem.setProperty("timestamp", Instant.now());
            return ResponseEntity.badRequest().body(problem);
        }
        // 非 Filtro 的 IllegalArgumentException 不拦截，让 Spring 默认处理
        throw ex;
    }

    /**
     * 类型转换异常（MongoDB DATETIME 解析失败等）。
     */
    @ExceptionHandler(PredicateBuildException.class)
    public ResponseEntity<ProblemDetail> handlePredicateBuild(PredicateBuildException ex) {
        logger.warn("Filtro type conversion failed for field '{}' at stage '{}': {}",
                ex.getField(), ex.getStage(), ex.getMessage());
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
                "Invalid value for field '" + ex.getField() + "'");
        problem.setTitle("Bad Filter Value");
        problem.setType(TYPE_INVALID_FILTER);
        problem.setProperty("timestamp", Instant.now());
        problem.setProperty("field", ex.getField());
        return ResponseEntity.badRequest().body(problem);
    }
}
