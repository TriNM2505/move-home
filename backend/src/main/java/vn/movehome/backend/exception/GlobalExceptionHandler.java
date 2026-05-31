package vn.movehome.backend.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Xu ly tap trung tat ca exception tu Controller layer.
 * Format response theo ES-04: { error_code, message, details } + timestamp, status.
 * KHONG lo thong tin noi bo (stack trace, ten class) ra response (HR-17).
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    // ===== VALIDATION ERRORS (HTTP 400/422) =====

    /**
     * Xu ly loi validation tu @Valid/@Validated tren request body (ES-03).
     * Tra HTTP 422 voi danh sach tat ca field loi (khong fail-fast — FR-003).
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidationError(MethodArgumentNotValidException ex) {
        List<Map<String, String>> fieldErrors = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(err -> Map.of(
                    "field", err.getField(),
                    "message", err.getDefaultMessage() != null ? err.getDefaultMessage() : "Gia tri khong hop le."
                ))
                .toList();

        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(Map.of(
                    "timestamp", Instant.now().toString(),
                    "status", 422,
                    "error_code", "VALIDATION_ERROR",
                    "message", "Du lieu dau vao khong hop le. Vui long kiem tra lai.",
                    "details", fieldErrors
                ));
    }

    // ===== BUSINESS LOGIC ERRORS (ResponseStatusException) =====

    /**
     * Xu ly loi business logic tu Service layer (ResponseStatusException).
     * Service co the nem:
     *   ResponseStatusException(HttpStatus.CONFLICT, "EMAIL_ALREADY_EXISTS|Email da duoc su dung.")
     * Handler phan tach error_code va message qua dau phan cach "|".
     */
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, Object>> handleResponseStatus(ResponseStatusException ex) {
        int statusCode = ex.getStatusCode().value();
        String reason = ex.getReason() != null ? ex.getReason() : ex.getMessage();

        String errorCode;
        String message;

        // Tach error_code va message (format: "ERROR_CODE|message" hoac chi "message")
        if (reason != null && reason.contains("|")) {
            String[] parts = reason.split("\\|", 2);
            errorCode = parts[0];
            message = parts[1];
        } else {
            errorCode = deriveErrorCode(statusCode);
            message = reason != null ? reason : "Da xay ra loi.";
        }

        return ResponseEntity.status(statusCode)
                .body(Map.of(
                    "timestamp", Instant.now().toString(),
                    "status", statusCode,
                    "error_code", errorCode,
                    "message", message
                ));
    }

    // ===== SECURITY ERRORS =====

    /**
     * Xu ly loi chua xac thuc (khong co JWT / JWT sai).
     * Thuong bi SecurityConfig bat truoc, nhung co the den day neu filter bi bypass.
     */
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<Map<String, Object>> handleAuthException(AuthenticationException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of(
                    "timestamp", Instant.now().toString(),
                    "status", 401,
                    "error_code", "AUTHENTICATION_REQUIRED",
                    "message", "Vui long dang nhap de tiep tuc."
                ));
    }

    /**
     * Xu ly loi khong co quyen (da xac thuc nhung sai role — HR-10).
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, Object>> handleAccessDenied(AccessDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Map.of(
                    "timestamp", Instant.now().toString(),
                    "status", 403,
                    "error_code", "FORBIDDEN",
                    "message", "Khong co quyen truy cap chuc nang nay."
                ));
    }

    // ===== GENERIC FALLBACK (HTTP 500) =====

    /**
     * Xu ly moi exception khong duoc bat o cac handler tren.
     * Log full exception de debug nhung chi tra message chung ra ngoai (HR-17).
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGeneric(Exception ex) {
        log.error("Loi he thong khong xu ly duoc: {}", ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of(
                    "timestamp", Instant.now().toString(),
                    "status", 500,
                    "error_code", "INTERNAL_SERVER_ERROR",
                    "message", "Da xay ra loi he thong. Vui long thu lai sau."
                ));
    }

    // ===== PRIVATE HELPER =====

    /** Map HTTP status code sang error_code khi reason khong chua "|". */
    private String deriveErrorCode(int status) {
        return switch (status) {
            case 400 -> "BAD_REQUEST";
            case 401 -> "UNAUTHORIZED";
            case 403 -> "FORBIDDEN";
            case 404 -> "NOT_FOUND";
            case 409 -> "CONFLICT";
            case 410 -> "GONE";
            case 422 -> "VALIDATION_ERROR";
            case 423 -> "ACCOUNT_LOCKED";
            case 429 -> "RATE_LIMITED";
            default  -> "ERROR";
        };
    }
}
