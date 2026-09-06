package com.order.demo.adapter.inbound.rest.exception;

import com.order.demo.application.domain.DuplicateOrderException;
import com.order.demo.application.domain.InsufficientInventoryException;
import com.order.demo.application.domain.OrderNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.slf4j.MDC;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Global exception handler for REST controllers.
 *
 * <p>Maps domain exceptions to appropriate HTTP status codes:
 * <ul>
 *   <li>{@link DuplicateOrderException} → 409 CONFLICT</li>
 *   <li>{@link InsufficientInventoryException} → 422 UNPROCESSABLE_ENTITY</li>
 *   <li>{@link MethodArgumentNotValidException} → 400 BAD_REQUEST</li>
 *   <li>{@link OrderNotFoundException} → 404 NOT_FOUND</li>
 *   <li>All others → 500 INTERNAL_SERVER_ERROR</li>
 * </ul>
 *
 * <p>The generic handler intentionally does not expose stack traces to clients
 * for security reasons. Server-side logging captures the full exception details.
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RestExceptionHandler implements Ordered {

    private static final Logger log = LoggerFactory.getLogger(RestExceptionHandler.class);

    /**
     * Handles duplicate order requests.
     *
     * @param ex the duplicate order exception
     * @return 409 CONFLICT with the error message
     */
    @ExceptionHandler(DuplicateOrderException.class)
    public ResponseEntity<Map<String, Object>> handleDuplicate(DuplicateOrderException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("error", ex.getMessage()));
    }

    /**
     * Handles insufficient inventory during order placement.
     *
     * @param ex the insufficient inventory exception
     * @return 422 UNPROCESSABLE_ENTITY with the error message
     */
    @ExceptionHandler(InsufficientInventoryException.class)
    public ResponseEntity<Map<String, Object>> handleInsufficient(InsufficientInventoryException ex) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(Map.of("error", ex.getMessage()));
    }

    /**
     * Handles order not found in WMS callback.
     *
     * @param ex the order not found exception
     * @return 404 NOT_FOUND with the error message
     */
    @ExceptionHandler(OrderNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleOrderNotFound(OrderNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("error", ex.getMessage()));
    }

    /**
     * Handles illegal domain state transitions — e.g. cancelling an order that
     * is already dispatched/terminal, or cancelling an order the caller does not own.
     *
     * @param ex the illegal state exception
     * @return 409 CONFLICT with the error message
     */
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalState(IllegalStateException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("error", ex.getMessage()));
    }

    /**
     * Handles validation errors from {@code @Valid} annotated request bodies.
     *
     * @param ex the validation exception
     * @return 400 BAD_REQUEST with a semicolon-separated list of field errors
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> e.getField() + ": " + e.getDefaultMessage())
                .reduce((a, b) -> a + "; " + b)
                .orElse("Validation failed");
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("error", message));
    }

    /**
     * Fallback handler for all unhandled exceptions.
     *
     * <p>Logs the full stack trace server-side but returns a generic error
     * message to the client to avoid leaking implementation details.
     *
     * @param ex the unhandled exception
     * @param request the current HTTP request
     * @return 500 INTERNAL_SERVER_ERROR with a generic error message and trace ID
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGeneric(Exception ex, HttpServletRequest request) {
        log.error("Unhandled exception on request: " + request.getRequestURI(), ex);
        String traceId = MDC.get("traceId");
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of(
                        "error", "Internal server error",
                        "traceId", traceId == null ? "unknown" : traceId));
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
