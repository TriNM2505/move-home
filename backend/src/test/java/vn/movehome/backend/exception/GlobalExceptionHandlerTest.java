package vn.movehome.backend.exception;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.core.MethodParameter;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.server.ResponseStatusException;

import java.lang.reflect.Method;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Kiem tra GlobalExceptionHandler: moi @ExceptionHandler tra dung HTTP status,
 * error_code va message (tieng Viet) theo ES-04.
 */
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();
    private final ObjectMapper objectMapper = new ObjectMapper();

    /** Method "mau" chi de lay MethodParameter that cho MethodArgumentNotValidException (khong tu goi). */
    @SuppressWarnings("unused")
    private void dummyValidationTarget(String field) {
    }

    // ===== MethodArgumentNotValidException =====

    @Test
    void handleMethodArgumentNotValidReturns422WithFieldErrorsAndFallbackMessage() throws Exception {
        BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "dto");
        bindingResult.addError(new FieldError("dto", "email", "Email không đúng định dạng."));
        bindingResult.addError(new FieldError("dto", "phone", null));

        Method dummyMethod = getClass().getDeclaredMethod("dummyValidationTarget", String.class);
        MethodParameter parameter = new MethodParameter(dummyMethod, 0);
        MethodArgumentNotValidException ex = new MethodArgumentNotValidException(parameter, bindingResult);

        ResponseEntity<?> response = handler.handleMethodArgumentNotValid(ex);

        JsonNode body = objectMapper.valueToTree(response.getBody());
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(body.get("error_code").asText()).isEqualTo("VALIDATION_ERROR");
        assertThat(body.get("message").asText())
                .isEqualTo("Dữ liệu đầu vào không hợp lệ. Vui lòng kiểm tra lại.");
        assertThat(body.get("details")).hasSize(2);
        assertThat(body.get("details").get(0).get("field").asText()).isEqualTo("email");
        assertThat(body.get("details").get(0).get("message").asText())
                .isEqualTo("Email không đúng định dạng.");
        assertThat(body.get("details").get(1).get("field").asText()).isEqualTo("phone");
        assertThat(body.get("details").get(1).get("message").asText())
                .isEqualTo("Giá trị không hợp lệ.");
    }

    // ===== ConstraintViolationException =====

    @Test
    void handleConstraintViolationReturns422WithViolationsAndFallbackMessage() {
        ConstraintViolation<?> violation1 = mock(ConstraintViolation.class);
        Path path1 = mock(Path.class);
        when(path1.toString()).thenReturn("address");
        when(violation1.getPropertyPath()).thenReturn(path1);
        when(violation1.getMessage()).thenReturn("Địa chỉ không được để trống.");

        ConstraintViolation<?> violation2 = mock(ConstraintViolation.class);
        Path path2 = mock(Path.class);
        when(path2.toString()).thenReturn("phone");
        when(violation2.getPropertyPath()).thenReturn(path2);
        when(violation2.getMessage()).thenReturn(null);

        Set<ConstraintViolation<?>> violations = new LinkedHashSet<>();
        violations.add(violation1);
        violations.add(violation2);
        ConstraintViolationException ex = new ConstraintViolationException(violations);

        ResponseEntity<?> response = handler.handleConstraintViolation(ex);

        JsonNode body = objectMapper.valueToTree(response.getBody());
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(body.get("error_code").asText()).isEqualTo("VALIDATION_ERROR");
        assertThat(body.get("message").asText())
                .isEqualTo("Dữ liệu đầu vào không hợp lệ. Vui lòng kiểm tra lại.");
        assertThat(body.get("details")).hasSize(2);
        java.util.Map<String, String> byField = new java.util.HashMap<>();
        body.get("details").forEach(node -> byField.put(node.get("field").asText(), node.get("message").asText()));
        assertThat(byField)
                .containsEntry("address", "Địa chỉ không được để trống.")
                .containsEntry("phone", "Giá trị không hợp lệ.");
    }

    // ===== ResponseStatusException =====

    @Test
    void handleResponseStatusSplitsPipeSeparatedReasonIntoErrorCodeAndMessage() {
        ResponseStatusException ex = mock(ResponseStatusException.class);
        when(ex.getStatusCode()).thenReturn(HttpStatus.CONFLICT);
        when(ex.getReason()).thenReturn("DUPLICATE_EMAIL|Email đã tồn tại.");

        ResponseEntity<?> response = handler.handleResponseStatus(ex);

        JsonNode body = objectMapper.valueToTree(response.getBody());
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(body.get("error_code").asText()).isEqualTo("DUPLICATE_EMAIL");
        assertThat(body.get("message").asText()).isEqualTo("Email đã tồn tại.");
        assertThat(body.get("details")).isEmpty();
    }

    @Test
    void handleResponseStatusUsesReasonAsMessageWhenNoPipePresent() {
        ResponseStatusException ex = mock(ResponseStatusException.class);
        when(ex.getStatusCode()).thenReturn(HttpStatus.NOT_FOUND);
        when(ex.getReason()).thenReturn("Không tìm thấy đơn hàng.");

        ResponseEntity<?> response = handler.handleResponseStatus(ex);

        JsonNode body = objectMapper.valueToTree(response.getBody());
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(body.get("error_code").asText()).isEqualTo("NOT_FOUND");
        assertThat(body.get("message").asText()).isEqualTo("Không tìm thấy đơn hàng.");
    }

    @Test
    void handleResponseStatusFallsBackToExceptionMessageWhenReasonIsNull() {
        ResponseStatusException ex = mock(ResponseStatusException.class);
        when(ex.getStatusCode()).thenReturn(HttpStatus.BAD_REQUEST);
        when(ex.getReason()).thenReturn(null);
        when(ex.getMessage()).thenReturn("400 BAD_REQUEST");

        ResponseEntity<?> response = handler.handleResponseStatus(ex);

        JsonNode body = objectMapper.valueToTree(response.getBody());
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(body.get("error_code").asText()).isEqualTo("BAD_REQUEST");
        assertThat(body.get("message").asText()).isEqualTo("400 BAD_REQUEST");
    }

    @Test
    void handleResponseStatusFallsBackToDefaultMessageWhenReasonAndMessageAreNull() {
        ResponseStatusException ex = mock(ResponseStatusException.class);
        when(ex.getStatusCode()).thenReturn(HttpStatus.NOT_FOUND);
        when(ex.getReason()).thenReturn(null);
        when(ex.getMessage()).thenReturn(null);

        ResponseEntity<?> response = handler.handleResponseStatus(ex);

        JsonNode body = objectMapper.valueToTree(response.getBody());
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(body.get("error_code").asText()).isEqualTo("NOT_FOUND");
        assertThat(body.get("message").asText()).isEqualTo("Đã xảy ra lỗi.");
    }

    @ParameterizedTest
    @MethodSource("statusToErrorCode")
    void handleResponseStatusDerivesErrorCodeFromEachKnownStatus(HttpStatus status, String expectedErrorCode) {
        ResponseStatusException ex = mock(ResponseStatusException.class);
        when(ex.getStatusCode()).thenReturn(status);
        when(ex.getReason()).thenReturn(null);
        when(ex.getMessage()).thenReturn(null);

        ResponseEntity<?> response = handler.handleResponseStatus(ex);

        JsonNode body = objectMapper.valueToTree(response.getBody());
        assertThat(response.getStatusCode()).isEqualTo(status);
        assertThat(body.get("error_code").asText()).isEqualTo(expectedErrorCode);
    }

    static Stream<Arguments> statusToErrorCode() {
        return Stream.of(
                Arguments.of(HttpStatus.BAD_REQUEST, "BAD_REQUEST"),
                Arguments.of(HttpStatus.UNAUTHORIZED, "AUTHENTICATION_REQUIRED"),
                Arguments.of(HttpStatus.FORBIDDEN, "FORBIDDEN"),
                Arguments.of(HttpStatus.NOT_FOUND, "NOT_FOUND"),
                Arguments.of(HttpStatus.CONFLICT, "CONFLICT"),
                Arguments.of(HttpStatus.GONE, "GONE"),
                Arguments.of(HttpStatus.UNPROCESSABLE_ENTITY, "VALIDATION_ERROR"),
                Arguments.of(HttpStatus.LOCKED, "ACCOUNT_LOCKED"),
                Arguments.of(HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMITED"),
                Arguments.of(HttpStatus.BAD_GATEWAY, "ERROR")
        );
    }

    // ===== AuthenticationException =====

    @Test
    void handleAuthenticationReturns401WithFixedVietnameseMessage() {
        BadCredentialsException ex = new BadCredentialsException("Sai mat khau");

        ResponseEntity<?> response = handler.handleAuthentication(ex);

        JsonNode body = objectMapper.valueToTree(response.getBody());
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(body.get("error_code").asText()).isEqualTo("AUTHENTICATION_REQUIRED");
        assertThat(body.get("message").asText()).isEqualTo("Vui lòng đăng nhập để tiếp tục.");
        assertThat(body.get("details")).isEmpty();
    }

    // ===== AccessDeniedException =====

    @Test
    void handleAccessDeniedReturns403WithFixedVietnameseMessage() {
        AccessDeniedException ex = new AccessDeniedException("Access is denied");

        ResponseEntity<?> response = handler.handleAccessDenied(ex);

        JsonNode body = objectMapper.valueToTree(response.getBody());
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(body.get("error_code").asText()).isEqualTo("FORBIDDEN");
        assertThat(body.get("message").asText()).isEqualTo("Bạn không có quyền truy cập chức năng này.");
    }

    // ===== EntityNotFoundException =====

    @Test
    void handleEntityNotFoundReturns404WithFixedVietnameseMessage() {
        EntityNotFoundException ex = new EntityNotFoundException("Order 123 not found");

        ResponseEntity<?> response = handler.handleEntityNotFound(ex);

        JsonNode body = objectMapper.valueToTree(response.getBody());
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(body.get("error_code").asText()).isEqualTo("NOT_FOUND");
        assertThat(body.get("message").asText()).isEqualTo("Không tìm thấy dữ liệu được yêu cầu.");
    }

    // ===== IllegalStateException =====

    @Test
    void handleIllegalStateReturns409WithExceptionMessageWhenPresent() {
        IllegalStateException ex = new IllegalStateException("Đơn hàng đã bị huỷ, không thể cập nhật.");

        ResponseEntity<?> response = handler.handleIllegalState(ex);

        JsonNode body = objectMapper.valueToTree(response.getBody());
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(body.get("error_code").asText()).isEqualTo("CONFLICT");
        assertThat(body.get("message").asText()).isEqualTo("Đơn hàng đã bị huỷ, không thể cập nhật.");
    }

    @Test
    void handleIllegalStateReturns409WithFallbackMessageWhenExceptionMessageIsNull() {
        IllegalStateException ex = new IllegalStateException();

        ResponseEntity<?> response = handler.handleIllegalState(ex);

        JsonNode body = objectMapper.valueToTree(response.getBody());
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(body.get("error_code").asText()).isEqualTo("CONFLICT");
        assertThat(body.get("message").asText()).isEqualTo("Trạng thái hiện tại không hợp lệ.");
    }

    // ===== MaxUploadSizeExceededException =====

    @Test
    void handleMaxUploadSizeReturns422WithFixedVietnameseMessage() {
        MaxUploadSizeExceededException ex = new MaxUploadSizeExceededException(1_500_000L);

        ResponseEntity<?> response = handler.handleMaxUploadSize(ex);

        JsonNode body = objectMapper.valueToTree(response.getBody());
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(body.get("error_code").asText()).isEqualTo("INVALID_FILE");
        assertThat(body.get("message").asText())
                .isEqualTo("Ảnh vượt quá dung lượng cho phép. Vui lòng chọn ảnh dưới 1.5 MB.");
    }

    // ===== Generic Exception =====

    @Test
    void handleGenericReturns500WithFixedVietnameseMessage() {
        RuntimeException ex = new RuntimeException("Lỗi không xác định từ tầng service");

        ResponseEntity<?> response = handler.handleGeneric(ex);

        JsonNode body = objectMapper.valueToTree(response.getBody());
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(body.get("error_code").asText()).isEqualTo("INTERNAL_SERVER_ERROR");
        assertThat(body.get("message").asText())
                .isEqualTo("Đã xảy ra lỗi hệ thống. Vui lòng thử lại sau.");
        assertThat(body.get("details")).isEmpty();
    }

    // ===== Nested ErrorResponse record =====

    @Test
    void errorResponseRecordSerializesToExpectedJsonShape() throws Exception {
        ResponseEntity<?> response = handler.handleEntityNotFound(new EntityNotFoundException("x"));

        Object body = response.getBody();
        assertThat(body).isNotNull();

        String json = objectMapper.writeValueAsString(body);
        JsonNode node = objectMapper.readTree(json);

        assertThat(node.has("error_code")).isTrue();
        assertThat(node.has("message")).isTrue();
        assertThat(node.has("details")).isTrue();
        assertThat(node.get("error_code").asText()).isEqualTo("NOT_FOUND");
        assertThat(node.get("message").asText()).isEqualTo("Không tìm thấy dữ liệu được yêu cầu.");

        // Goi truc tiep cac accessor method sinh tu record (errorCode/message/details)
        // de dam bao code sinh tu compiler cung duoc bao phu.
        var errorCodeMethod = body.getClass().getDeclaredMethod("errorCode");
        errorCodeMethod.setAccessible(true);
        var messageMethod = body.getClass().getDeclaredMethod("message");
        messageMethod.setAccessible(true);
        var detailsMethod = body.getClass().getDeclaredMethod("details");
        detailsMethod.setAccessible(true);

        assertThat(errorCodeMethod.invoke(body)).isEqualTo("NOT_FOUND");
        assertThat(messageMethod.invoke(body)).isEqualTo("Không tìm thấy dữ liệu được yêu cầu.");
        assertThat(detailsMethod.invoke(body)).isEqualTo(List.of());

        // toString/equals/hashCode sinh tu record cung duoc bao phu.
        assertThat(body.toString()).contains("NOT_FOUND");
        assertThat(body).isEqualTo(body);
        assertThat(body.hashCode()).isEqualTo(body.hashCode());
    }
}
