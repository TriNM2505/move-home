package vn.movehome.backend.exception;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

/**
 * Xử lý tập trung exception từ REST controller.
 * ES-04: mọi lỗi trả JSON { error_code, message, details }.
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleMethodArgumentNotValid(MethodArgumentNotValidException ex) {
        List<Map<String, String>> fieldErrors = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(err -> Map.of(
                    "field", err.getField(),
                    "message", err.getDefaultMessage() != null ? err.getDefaultMessage() : "Giá trị không hợp lệ."
                ))
                .toList();

        return build(HttpStatus.UNPROCESSABLE_ENTITY, "VALIDATION_ERROR",
                "Dữ liệu đầu vào không hợp lệ. Vui lòng kiểm tra lại.", fieldErrors);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolation(ConstraintViolationException ex) {
        List<Map<String, String>> violations = ex.getConstraintViolations()
                .stream()
                .map(violation -> Map.of(
                    "field", violation.getPropertyPath().toString(),
                    "message", violation.getMessage() != null ? violation.getMessage() : "Giá trị không hợp lệ."
                ))
                .toList();

        return build(HttpStatus.UNPROCESSABLE_ENTITY, "VALIDATION_ERROR",
                "Dữ liệu đầu vào không hợp lệ. Vui lòng kiểm tra lại.", violations);
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErrorResponse> handleResponseStatus(ResponseStatusException ex) {
        int statusCode = ex.getStatusCode().value();
        String reason = ex.getReason() != null ? ex.getReason() : ex.getMessage();

        String errorCode;
        String message;
        if (reason != null && reason.contains("|")) {
            String[] parts = reason.split("\\|", 2);
            errorCode = parts[0];
            message = parts[1];
        } else {
            errorCode = deriveErrorCode(statusCode);
            message = reason != null ? reason : "Đã xảy ra lỗi.";
        }

        return build(HttpStatus.valueOf(statusCode), errorCode, message, List.of());
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ErrorResponse> handleAuthentication(AuthenticationException ex) {
        return build(HttpStatus.UNAUTHORIZED, "AUTHENTICATION_REQUIRED",
                "Vui lòng đăng nhập để tiếp tục.", List.of());
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException ex) {
        return build(HttpStatus.FORBIDDEN, "FORBIDDEN",
                "Bạn không có quyền truy cập chức năng này.", List.of());
    }

    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleEntityNotFound(EntityNotFoundException ex) {
        return build(HttpStatus.NOT_FOUND, "NOT_FOUND",
                "Không tìm thấy dữ liệu được yêu cầu.", List.of());
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ErrorResponse> handleIllegalState(IllegalStateException ex) {
        return build(HttpStatus.CONFLICT, "CONFLICT",
                ex.getMessage() != null ? ex.getMessage() : "Trạng thái hiện tại không hợp lệ.", List.of());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneric(Exception ex) {
        log.error("Lỗi hệ thống chưa xử lý: {}", ex.getMessage(), ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_SERVER_ERROR",
                "Đã xảy ra lỗi hệ thống. Vui lòng thử lại sau.", List.of());
    }

    private ResponseEntity<ErrorResponse> build(
            HttpStatus status,
            String errorCode,
            String message,
            Object details
    ) {
        return ResponseEntity.status(status)
                .body(new ErrorResponse(errorCode, message, details));
    }

    private String deriveErrorCode(int status) {
        return switch (status) {
            case 400 -> "BAD_REQUEST";
            case 401 -> "AUTHENTICATION_REQUIRED";
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

    private record ErrorResponse(
            @JsonProperty("error_code") String errorCode,
            String message,
            Object details
    ) {
    }
}
