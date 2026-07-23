package vn.movehome.backend.payment;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;
import vn.movehome.backend.entity.User;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class VnPayControllerTest {

    private static final String DEFAULT_FRONTEND_URL = "http://localhost:5500/frontend/pages/vnpay-return.html";

    private final VnPayPaymentService service = mock(VnPayPaymentService.class);
    private final VnPayController controller = new VnPayController(service, DEFAULT_FRONTEND_URL);

    private HttpServletRequest requestWithRemoteAddr(String remoteAddr) {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader("X-Forwarded-For")).thenReturn(null);
        when(request.getHeader("X-Real-IP")).thenReturn(null);
        when(request.getRemoteAddr()).thenReturn(remoteAddr);
        return request;
    }

    @Test
    void createOrderPaymentUrlUsesAuthenticatedCustomerIdAndClientIp() {
        UUID customerId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        User customer = User.builder().id(customerId).build();
        HttpServletRequest request = requestWithRemoteAddr("203.0.113.10");
        VnPayPaymentUrlResponse expected = new VnPayPaymentUrlResponse(
                "https://sandbox.vnpayment.vn/pay?...", "ORD-abc", new BigDecimal("150000"), OffsetDateTime.now());
        when(service.createOrderPaymentUrl(customerId, orderId, "203.0.113.10")).thenReturn(expected);

        VnPayPaymentUrlResponse actual = controller.createOrderPaymentUrl(customer, orderId, request);

        assertThat(actual).isEqualTo(expected);
        verify(service).createOrderPaymentUrl(customerId, orderId, "203.0.113.10");
    }

    @Test
    void createFinalPaymentUrlUsesAuthenticatedCustomerIdAndClientIp() {
        UUID customerId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        User customer = User.builder().id(customerId).build();
        HttpServletRequest request = requestWithRemoteAddr("203.0.113.11");
        VnPayPaymentUrlResponse expected = new VnPayPaymentUrlResponse(
                "https://sandbox.vnpayment.vn/pay?...", "OFP-abc", new BigDecimal("700000"), OffsetDateTime.now());
        when(service.createFinalPaymentUrl(customerId, orderId, "203.0.113.11")).thenReturn(expected);

        VnPayPaymentUrlResponse actual = controller.createFinalPaymentUrl(customer, orderId, request);

        assertThat(actual).isEqualTo(expected);
        verify(service).createFinalPaymentUrl(customerId, orderId, "203.0.113.11");
    }

    @Test
    void createWalletTopUpUrlUsesAuthenticatedCustomerIdAmountAndClientIp() {
        UUID customerId = UUID.randomUUID();
        User customer = User.builder().id(customerId).build();
        HttpServletRequest request = requestWithRemoteAddr("203.0.113.12");
        CreateWalletTopUpRequest body = new CreateWalletTopUpRequest(new BigDecimal("300000"));
        VnPayPaymentUrlResponse expected = new VnPayPaymentUrlResponse(
                "https://sandbox.vnpayment.vn/pay?...", "WAL-abc", new BigDecimal("300000"), OffsetDateTime.now());
        when(service.createWalletTopUpUrl(customerId, new BigDecimal("300000"), "203.0.113.12"))
                .thenReturn(expected);

        VnPayPaymentUrlResponse actual = controller.createWalletTopUpUrl(customer, body, request);

        assertThat(actual).isEqualTo(expected);
        verify(service).createWalletTopUpUrl(customerId, new BigDecimal("300000"), "203.0.113.12");
    }

    @Test
    void createDriverDepositUrlUsesAuthenticatedDriverIdAndClientIp() {
        UUID driverId = UUID.randomUUID();
        User driver = User.builder().id(driverId).build();
        HttpServletRequest request = requestWithRemoteAddr("203.0.113.13");
        VnPayPaymentUrlResponse expected = new VnPayPaymentUrlResponse(
                "https://sandbox.vnpayment.vn/pay?...", "DDP-abc", new BigDecimal("3000000"), OffsetDateTime.now());
        when(service.createDriverDepositUrl(driverId, "203.0.113.13")).thenReturn(expected);

        VnPayPaymentUrlResponse actual = controller.createDriverDepositUrl(driver, request);

        assertThat(actual).isEqualTo(expected);
        verify(service).createDriverDepositUrl(driverId, "203.0.113.13");
    }

    @Test
    void clientIpPrefersXForwardedForHeaderOverEverythingElse() {
        UUID customerId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        User customer = User.builder().id(customerId).build();
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader("X-Forwarded-For")).thenReturn("198.51.100.5, 70.41.3.18");
        when(service.createOrderPaymentUrl(eq(customerId), eq(orderId), any()))
                .thenReturn(new VnPayPaymentUrlResponse("url", "ORD-abc", BigDecimal.TEN, OffsetDateTime.now()));

        controller.createOrderPaymentUrl(customer, orderId, request);

        verify(service).createOrderPaymentUrl(customerId, orderId, "198.51.100.5");
    }

    @Test
    void clientIpFallsBackToXRealIpHeaderWhenForwardedForMissing() {
        UUID customerId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        User customer = User.builder().id(customerId).build();
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader("X-Forwarded-For")).thenReturn("   ");
        when(request.getHeader("X-Real-IP")).thenReturn("192.0.2.9");
        when(service.createOrderPaymentUrl(eq(customerId), eq(orderId), any()))
                .thenReturn(new VnPayPaymentUrlResponse("url", "ORD-abc", BigDecimal.TEN, OffsetDateTime.now()));

        controller.createOrderPaymentUrl(customer, orderId, request);

        verify(service).createOrderPaymentUrl(customerId, orderId, "192.0.2.9");
    }

    @Test
    void clientIpFallsBackToRemoteAddrWhenNoProxyHeadersPresent() {
        UUID customerId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        User customer = User.builder().id(customerId).build();
        HttpServletRequest request = requestWithRemoteAddr("10.0.0.5");
        when(service.createOrderPaymentUrl(eq(customerId), eq(orderId), any()))
                .thenReturn(new VnPayPaymentUrlResponse("url", "ORD-abc", BigDecimal.TEN, OffsetDateTime.now()));

        controller.createOrderPaymentUrl(customer, orderId, request);

        verify(service).createOrderPaymentUrl(customerId, orderId, "10.0.0.5");
    }

    @Test
    void handleReturnRedirectsWithSuccessStatusAndEncodedTxnRef() {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("vnp_TxnRef", "ORD-abc def");
        when(service.handleReturn(params)).thenReturn(new VnPayReturnResponse(
                true, true, "ORD-abc def", "00", "Thanh toan VNPay thanh cong.", "PROCESSED"));

        ResponseEntity<Void> response = controller.handleReturn(params);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FOUND);
        assertThat(response.getHeaders().getLocation()).isNotNull();
        assertThat(response.getHeaders().getLocation().toString())
                .isEqualTo(DEFAULT_FRONTEND_URL + "?status=success&txn_ref=ORD-abc+def");
    }

    @Test
    void handleReturnRedirectsWithFailedStatusWhenPaymentUnsuccessful() {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("vnp_TxnRef", "ORD-abc");
        when(service.handleReturn(params)).thenReturn(new VnPayReturnResponse(
                true, false, "ORD-abc", "24", "Thanh toan VNPay khong thanh cong.", null));

        ResponseEntity<Void> response = controller.handleReturn(params);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FOUND);
        assertThat(response.getHeaders().getLocation().toString())
                .isEqualTo(DEFAULT_FRONTEND_URL + "?status=failed&txn_ref=ORD-abc");
    }

    @Test
    void handleReturnRedirectsWithFailedStatusWhenServiceThrowsResponseStatusException() {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("vnp_TxnRef", "ORD-abc");
        when(service.handleReturn(params)).thenThrow(new ResponseStatusException(
                HttpStatus.CONFLICT, "VNPAY_CALLBACK_REJECTED|Order already confirmed"));

        ResponseEntity<Void> response = controller.handleReturn(params);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FOUND);
        assertThat(response.getHeaders().getLocation().toString())
                .isEqualTo(DEFAULT_FRONTEND_URL + "?status=failed&txn_ref=ORD-abc");
    }

    @Test
    void handleReturnUsesEmptyTxnRefWhenParamMissing() {
        Map<String, String> params = new LinkedHashMap<>();
        when(service.handleReturn(params)).thenReturn(new VnPayReturnResponse(
                false, false, null, null, "Chu ky VNPay khong hop le.", null));

        ResponseEntity<Void> response = controller.handleReturn(params);

        assertThat(response.getHeaders().getLocation().toString())
                .isEqualTo(DEFAULT_FRONTEND_URL + "?status=failed&txn_ref=");
    }

    @Test
    void handleReturnAppendsQueryWithAmpersandWhenFrontendUrlAlreadyHasQueryString() {
        VnPayController controllerWithQueryUrl = new VnPayController(
                service, "http://localhost:5500/pages/vnpay-return.html?lang=vi");
        Map<String, String> params = new LinkedHashMap<>();
        params.put("vnp_TxnRef", "ORD-abc");
        when(service.handleReturn(params)).thenReturn(new VnPayReturnResponse(
                true, true, "ORD-abc", "00", "Thanh toan VNPay thanh cong.", "PROCESSED"));

        ResponseEntity<Void> response = controllerWithQueryUrl.handleReturn(params);

        assertThat(response.getHeaders().getLocation().toString())
                .isEqualTo("http://localhost:5500/pages/vnpay-return.html?lang=vi&status=success&txn_ref=ORD-abc");
    }

    @Test
    void handleIpnDelegatesToServiceAndReturnsItsResponse() {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("vnp_TxnRef", "ORD-abc");
        VnPayIpnResponse expected = new VnPayIpnResponse("00", "Confirm Success");
        when(service.handleIpn(params)).thenReturn(expected);

        VnPayIpnResponse actual = controller.handleIpn(params);

        assertThat(actual).isEqualTo(expected);
        verify(service).handleIpn(params);
    }
}
