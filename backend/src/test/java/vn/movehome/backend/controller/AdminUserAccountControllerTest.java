package vn.movehome.backend.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import vn.movehome.backend.config.SecurityConfig;
import vn.movehome.backend.dto.admin.UserAccountStatusResponse;
import vn.movehome.backend.dto.admin.UserSuspensionActionResponse;
import vn.movehome.backend.entity.User;
import vn.movehome.backend.entity.UserRole;
import vn.movehome.backend.entity.UserStatus;
import vn.movehome.backend.repository.UserRepository;
import vn.movehome.backend.security.DriverWorkflowAccessService;
import vn.movehome.backend.security.JwtAuthenticationFilter;
import vn.movehome.backend.security.JwtTokenProvider;
import vn.movehome.backend.service.AdminUserAccountService;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminUserAccountController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, DriverWorkflowAccessService.class})
class AdminUserAccountControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AdminUserAccountService adminUserAccountService;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    private UserRepository userRepository;

    @Test
    void adminCanLockUser() throws Exception {
        UUID userId = UUID.randomUUID();
        User admin = userWithRole(UserRole.ADMIN);
        when(adminUserAccountService.updateStatus(userId, UserStatus.LOCKED, admin))
                .thenReturn(new UserAccountStatusResponse(userId, UserRole.CUSTOMER, UserStatus.LOCKED));

        mockMvc.perform(patch("/api/admin/users/{userId}/status", userId)
                        .with(user(admin))
                        .contentType("application/json")
                        .content("{\"status\":\"LOCKED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(userId.toString()))
                .andExpect(jsonPath("$.role").value("CUSTOMER"))
                .andExpect(jsonPath("$.status").value("LOCKED"));

        verify(adminUserAccountService).updateStatus(userId, UserStatus.LOCKED, admin);
    }

    @Test
    void managerCannotChangeAccountStatus() throws Exception {
        UUID userId = UUID.randomUUID();

        mockMvc.perform(patch("/api/admin/users/{userId}/status", userId)
                        .with(user(userWithRole(UserRole.MANAGER)))
                        .contentType("application/json")
                        .content("{\"status\":\"LOCKED\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error_code").value("FORBIDDEN"));
    }

    @Test
    void getStatusReturnsCurrentAccountStatus() throws Exception {
        UUID userId = UUID.randomUUID();
        User admin = userWithRole(UserRole.ADMIN);
        when(adminUserAccountService.getStatus(userId))
                .thenReturn(new UserAccountStatusResponse(userId, UserRole.DRIVER, UserStatus.ACTIVE));

        mockMvc.perform(get("/api/admin/users/{userId}/status", userId).with(user(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(userId.toString()))
                .andExpect(jsonPath("$.role").value("DRIVER"))
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        verify(adminUserAccountService).getStatus(userId);
    }

    @Test
    void adminCanSuspendUser() throws Exception {
        UUID userId = UUID.randomUUID();
        User admin = userWithRole(UserRole.ADMIN);
        Instant suspendedAt = Instant.parse("2026-07-01T00:00:00Z");
        when(adminUserAccountService.suspend(eq(userId), any(), eq(admin)))
                .thenReturn(new UserSuspensionActionResponse(
                        "Da dinh chi tai khoan", userId, UserStatus.SUSPENDED, UserStatus.ACTIVE,
                        suspendedAt, null));

        mockMvc.perform(post("/api/admin/users/{userId}/suspend", userId)
                        .with(user(admin))
                        .contentType("application/json")
                        .content("{\"reason\":\"Suspicious payment activity requires manual account review.\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user_id").value(userId.toString()))
                .andExpect(jsonPath("$.status").value("SUSPENDED"))
                .andExpect(jsonPath("$.previous_status").value("ACTIVE"));

        verify(adminUserAccountService).suspend(eq(userId), any(), eq(admin));
    }

    @Test
    void adminCanActivateUser() throws Exception {
        UUID userId = UUID.randomUUID();
        User admin = userWithRole(UserRole.ADMIN);
        when(adminUserAccountService.activate(eq(userId), any(), eq(admin)))
                .thenReturn(new UserSuspensionActionResponse(
                        "Da kich hoat lai tai khoan", userId, UserStatus.ACTIVE, UserStatus.SUSPENDED,
                        null, null));

        mockMvc.perform(post("/api/admin/users/{userId}/activate", userId)
                        .with(user(admin))
                        .contentType("application/json")
                        .content("{\"note\":\"Manual review completed.\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user_id").value(userId.toString()))
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        verify(adminUserAccountService).activate(eq(userId), any(), eq(admin));
    }

    private User userWithRole(UserRole role) {
        return User.builder()
                .id(UUID.randomUUID())
                .email(role.name().toLowerCase() + "@movehome.vn")
                .role(role)
                .status(UserStatus.ACTIVE)
                .emailVerified(true)
                .build();
    }
}
