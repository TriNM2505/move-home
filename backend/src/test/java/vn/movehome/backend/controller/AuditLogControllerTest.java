package vn.movehome.backend.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import vn.movehome.backend.config.SecurityConfig;
import vn.movehome.backend.dto.admin.AuditLogResponse;
import vn.movehome.backend.entity.User;
import vn.movehome.backend.entity.UserRole;
import vn.movehome.backend.entity.UserStatus;
import vn.movehome.backend.repository.UserRepository;
import vn.movehome.backend.security.DriverWorkflowAccessService;
import vn.movehome.backend.security.JwtAuthenticationFilter;
import vn.movehome.backend.security.JwtTokenProvider;
import vn.movehome.backend.service.AuditLogQueryService;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuditLogController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, DriverWorkflowAccessService.class})
class AuditLogControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuditLogQueryService auditLogQueryService;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    private UserRepository userRepository;

    @Test
    void adminGetsSpringPageWithFrontendFieldNames() throws Exception {
        AuditLogResponse row = new AuditLogResponse(
                "admin@movehome.vn",
                "ORDER_ASSIGNED",
                "ORDER",
                "MH-001",
                "CONFIRMED -> ASSIGNED",
                Instant.parse("2026-06-22T03:00:00Z")
        );
        when(auditLogQueryService.findAuditLogs(
                isNull(), isNull(), isNull(), isNull(), any()))
                .thenReturn(new PageImpl<>(List.of(row), PageRequest.of(0, 10), 1));

        mockMvc.perform(get("/api/admin/audit-logs").with(user(userWithRole(UserRole.ADMIN))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].actorEmail").value("admin@movehome.vn"))
                .andExpect(jsonPath("$.content[0].action").value("ORDER_ASSIGNED"))
                .andExpect(jsonPath("$.content[0].entityType").value("ORDER"))
                .andExpect(jsonPath("$.content[0].entityId").value("MH-001"))
                .andExpect(jsonPath("$.content[0].detail").value("CONFIRMED -> ASSIGNED"))
                .andExpect(jsonPath("$.content[0].createdAt").value("2026-06-22T03:00:00Z"))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.number").value(0))
                .andExpect(jsonPath("$.size").value(10));
    }

    @Test
    void managerCanReadAuditLogs() throws Exception {
        when(auditLogQueryService.findAuditLogs(
                isNull(), isNull(), isNull(), isNull(), any()))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 10), 0));

        mockMvc.perform(get("/api/admin/audit-logs").with(user(userWithRole(UserRole.MANAGER))))
                .andExpect(status().isOk());
    }

    @Test
    void customerGetsForbiddenEs04Response() throws Exception {
        mockMvc.perform(get("/api/admin/audit-logs").with(user(userWithRole(UserRole.CUSTOMER))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error_code").value("FORBIDDEN"))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.details").isArray());
    }

    private User userWithRole(UserRole role) {
        return User.builder()
                .email(role.name().toLowerCase() + "@movehome.vn")
                .role(role)
                .status(UserStatus.ACTIVE)
                .emailVerified(true)
                .build();
    }
}
