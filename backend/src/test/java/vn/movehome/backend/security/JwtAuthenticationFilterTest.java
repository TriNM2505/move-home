package vn.movehome.backend.security;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import vn.movehome.backend.entity.User;
import vn.movehome.backend.entity.UserRole;
import vn.movehome.backend.entity.UserStatus;
import vn.movehome.backend.repository.UserRepository;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @Mock
    private UserRepository userRepository;

    @Mock
    private FilterChain filterChain;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void authenticatesVerifiedDriverDuringOnboarding() throws Exception {
        UUID driverId = UUID.randomUUID();
        User driver = User.builder()
                .id(driverId)
                .email("driver-pending@movehome.vn")
                .role(UserRole.DRIVER)
                .status(UserStatus.PENDING_DOCUMENTS)
                .emailVerified(true)
                .build();
        when(jwtTokenProvider.validateAccessToken("valid-token")).thenReturn(Optional.of(driverId));
        when(userRepository.findById(driverId)).thenReturn(Optional.of(driver));

        MockHttpServletRequest request = bearerRequest("/api/driver/onboarding/status");
        MockHttpServletResponse response = new MockHttpServletResponse();

        new JwtAuthenticationFilter(jwtTokenProvider, userRepository)
                .doFilter(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication().getPrincipal()).isSameAs(driver);
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void doesNotAuthenticateUserWhoseEmailIsNotVerified() throws Exception {
        UUID driverId = UUID.randomUUID();
        User driver = User.builder()
                .id(driverId)
                .email("driver-unverified@movehome.vn")
                .role(UserRole.DRIVER)
                .status(UserStatus.PENDING_VERIFY)
                .emailVerified(false)
                .build();
        when(jwtTokenProvider.validateAccessToken("valid-token")).thenReturn(Optional.of(driverId));
        when(userRepository.findById(driverId)).thenReturn(Optional.of(driver));

        MockHttpServletRequest request = bearerRequest("/api/driver/onboarding/status");
        MockHttpServletResponse response = new MockHttpServletResponse();

        new JwtAuthenticationFilter(jwtTokenProvider, userRepository)
                .doFilter(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void doesNotAuthenticateAdminLockedUserWithExistingAccessToken() throws Exception {
        UUID userId = UUID.randomUUID();
        User user = User.builder()
                .id(userId)
                .email("locked@movehome.vn")
                .role(UserRole.CUSTOMER)
                .status(UserStatus.LOCKED)
                .emailVerified(true)
                .build();
        when(jwtTokenProvider.validateAccessToken("valid-token")).thenReturn(Optional.of(userId));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        MockHttpServletRequest request = bearerRequest("/api/customer/profile");
        MockHttpServletResponse response = new MockHttpServletResponse();

        new JwtAuthenticationFilter(jwtTokenProvider, userRepository)
                .doFilter(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(filterChain).doFilter(request, response);
    }

    private MockHttpServletRequest bearerRequest(String uri) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", uri);
        request.addHeader("Authorization", "Bearer valid-token");
        return request;
    }
}
