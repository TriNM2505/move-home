package vn.movehome.backend.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.movehome.backend.entity.User;
import vn.movehome.backend.entity.UserRole;
import vn.movehome.backend.entity.UserStatus;
import vn.movehome.backend.repository.UserRepository;
import vn.movehome.backend.security.DriverWorkflowAccessService;
import vn.movehome.backend.security.JwtAuthenticationFilter;
import vn.movehome.backend.security.JwtTokenProvider;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = SecurityConfigTest.TestEndpoints.class)
@Import({
        SecurityConfig.class,
        JwtAuthenticationFilter.class,
        DriverWorkflowAccessService.class,
        SecurityConfigTest.TestEndpoints.class
})
class SecurityConfigTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserDetailsService userDetailsService;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    private UserRepository userRepository;

    @Test
    void userDetailsServiceThrowsWhenEmailNotFound() {
        String email = "khong-ton-tai@movehome.vn";
        when(userRepository.findByEmailAndDeletedAtIsNull(email)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userDetailsService.loadUserByUsername(email))
                .isInstanceOf(UsernameNotFoundException.class)
                .hasMessage("Nguoi dung khong tim thay voi email: " + email);
    }

    @Test
    void activeDriverCanAccessDriverOrders() throws Exception {
        mockMvc.perform(get("/api/driver/orders")
                        .with(user(verifiedUser(UserRole.DRIVER, UserStatus.ACTIVE))))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/driver/location")
                        .with(user(verifiedUser(UserRole.DRIVER, UserStatus.ACTIVE))))
                .andExpect(status().isOk());
    }

    @Test
    void verifiedOnboardingDriverCanAccessOnboarding() throws Exception {
        User pendingDriver = verifiedUser(UserRole.DRIVER, UserStatus.PENDING_DOCUMENTS);

        mockMvc.perform(get("/api/driver/profile").with(user(pendingDriver)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/driver/onboarding/documents").with(user(pendingDriver)))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/driver/onboarding/submit").with(user(pendingDriver)))
                .andExpect(status().isOk());
    }

    @Test
    void onboardingDriverCannotAccessDriverOrders() throws Exception {
        mockMvc.perform(get("/api/driver/orders")
                .with(user(verifiedUser(UserRole.DRIVER, UserStatus.PENDING_APPROVAL))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error_code").value("ONBOARDING_PENDING_REVIEW"))
                .andExpect(jsonPath("$.message").value("Hồ sơ đang được Manager xem xét. Vui lòng đợi."));
    }

    @Test
    void onboardingDriverCannotUpdateLocation() throws Exception {
        mockMvc.perform(post("/api/driver/location")
                        .with(user(verifiedUser(UserRole.DRIVER, UserStatus.PENDING_APPROVAL))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error_code").value("ONBOARDING_PENDING_REVIEW"));
    }

    @Test
    void customerAndAdminKeepTheirExistingAccess() throws Exception {
        mockMvc.perform(get("/api/customer/test")
                        .with(user(verifiedUser(UserRole.CUSTOMER, UserStatus.ACTIVE))))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/admin/test")
                        .with(user(verifiedUser(UserRole.ADMIN, UserStatus.ACTIVE))))
                .andExpect(status().isOk());
    }

    @Test
    void adminCanAccessManagerDisputesButNotOtherManagerRoutes() throws Exception {
        mockMvc.perform(get("/api/manager/disputes")
                        .with(user(verifiedUser(UserRole.ADMIN, UserStatus.ACTIVE))))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/manager/drivers/pending-approval")
                        .with(user(verifiedUser(UserRole.ADMIN, UserStatus.ACTIVE))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error_code").value("FORBIDDEN"));
    }

    @Test
    void unauthenticatedRequestToProtectedRouteReturns401WithJsonEntryPointBody() throws Exception {
        mockMvc.perform(get("/api/customer/test"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error_code").value("AUTHENTICATION_REQUIRED"))
                .andExpect(jsonPath("$.message").value("Vui lòng đăng nhập để tiếp tục."))
                .andExpect(jsonPath("$.details").isEmpty());
    }

    @Test
    void publicAuthAndVnpayAndWsRoutesArePermittedWithoutAuthentication() throws Exception {
        mockMvc.perform(get("/api/public/info")).andExpect(status().isOk());
        mockMvc.perform(get("/api/auth/info")).andExpect(status().isOk());
        mockMvc.perform(get("/api/vnpay/info")).andExpect(status().isOk());
        mockMvc.perform(get("/ws/info")).andExpect(status().isOk());
    }

    @Test
    void optionsPreflightRequestIsPermittedWithoutAuthenticationOnAnyRoute() throws Exception {
        mockMvc.perform(options("/api/customer/test"))
                .andExpect(status().isOk());
    }

    @Test
    void managerRoleCanAccessGenericManagerRouteAndGetAuditLogs() throws Exception {
        mockMvc.perform(get("/api/manager/settings")
                        .with(user(verifiedUser(UserRole.MANAGER, UserStatus.ACTIVE))))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/admin/audit-logs")
                        .with(user(verifiedUser(UserRole.MANAGER, UserStatus.ACTIVE))))
                .andExpect(status().isOk());
    }

    @Test
    void customerRoleCannotAccessAuditLogs() throws Exception {
        mockMvc.perform(get("/api/admin/audit-logs")
                        .with(user(verifiedUser(UserRole.CUSTOMER, UserStatus.ACTIVE))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error_code").value("FORBIDDEN"));
    }

    @Test
    void anyAuthenticatedRoleCanAccessNotificationsAndChatButAnonymousCannot() throws Exception {
        mockMvc.perform(get("/api/notifications/list")
                        .with(user(verifiedUser(UserRole.CUSTOMER, UserStatus.ACTIVE))))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/chat/list")
                        .with(user(verifiedUser(UserRole.DRIVER, UserStatus.ACTIVE))))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/notifications/list"))
                .andExpect(status().isUnauthorized());
    }

    private User verifiedUser(UserRole role, UserStatus status) {
        return User.builder()
                .email(role.name().toLowerCase() + "@movehome.vn")
                .role(role)
                .status(status)
                .emailVerified(true)
                .build();
    }

    @RestController
    public static class TestEndpoints {

        @GetMapping("/api/driver/orders")
        public Map<String, String> driverOrders() {
            return Map.of("status", "ok");
        }

        @org.springframework.web.bind.annotation.PostMapping("/api/driver/location")
        public Map<String, String> updateDriverLocation() {
            return Map.of("status", "ok");
        }

        @GetMapping("/api/driver/onboarding/status")
        public Map<String, String> onboardingStatus() {
            return Map.of("status", "ok");
        }

        @GetMapping("/api/driver/profile")
        public Map<String, String> driverProfile() {
            return Map.of("status", "ok");
        }

        @GetMapping("/api/driver/onboarding/documents")
        public Map<String, String> onboardingDocuments() {
            return Map.of("status", "ok");
        }

        @org.springframework.web.bind.annotation.PostMapping("/api/driver/onboarding/submit")
        public Map<String, String> submitOnboarding() {
            return Map.of("status", "ok");
        }

        @GetMapping("/api/customer/test")
        public Map<String, String> customerEndpoint() {
            return Map.of("status", "ok");
        }

        @GetMapping("/api/admin/test")
        public Map<String, String> adminEndpoint() {
            return Map.of("status", "ok");
        }

        @GetMapping("/api/manager/disputes")
        public Map<String, String> managerDisputes() {
            return Map.of("status", "ok");
        }

        @GetMapping("/api/manager/drivers/pending-approval")
        public Map<String, String> managerDrivers() {
            return Map.of("status", "ok");
        }

        @GetMapping("/api/manager/settings")
        public Map<String, String> managerSettings() {
            return Map.of("status", "ok");
        }

        @GetMapping("/api/admin/audit-logs")
        public Map<String, String> auditLogs() {
            return Map.of("status", "ok");
        }

        @GetMapping("/api/public/info")
        public Map<String, String> publicInfo() {
            return Map.of("status", "ok");
        }

        @GetMapping("/api/auth/info")
        public Map<String, String> authInfo() {
            return Map.of("status", "ok");
        }

        @GetMapping("/api/vnpay/info")
        public Map<String, String> vnpayInfo() {
            return Map.of("status", "ok");
        }

        @GetMapping("/ws/info")
        public Map<String, String> wsInfo() {
            return Map.of("status", "ok");
        }

        @GetMapping("/api/notifications/list")
        public Map<String, String> notificationsList() {
            return Map.of("status", "ok");
        }

        @GetMapping("/api/chat/list")
        public Map<String, String> chatList() {
            return Map.of("status", "ok");
        }
    }
}
