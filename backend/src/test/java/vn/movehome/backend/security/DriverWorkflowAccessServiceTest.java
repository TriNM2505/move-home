package vn.movehome.backend.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import vn.movehome.backend.entity.User;
import vn.movehome.backend.entity.UserRole;
import vn.movehome.backend.entity.UserStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DriverWorkflowAccessServiceTest {

    private final DriverWorkflowAccessService service = new DriverWorkflowAccessService();

    @Test
    void allowsActiveDriverToUseWorkflow() {
        User driver = verifiedUser(UserRole.DRIVER, UserStatus.ACTIVE);

        assertThat(service.isActiveDriver(authentication(driver))).isTrue();
    }

    @Test
    void rejectsOnboardingDriverWithRequiredVietnameseMessage() {
        User driver = verifiedUser(UserRole.DRIVER, UserStatus.PENDING_APPROVAL);

        assertThatThrownBy(() -> service.isActiveDriver(authentication(driver)))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("Hồ sơ tài xế chưa được duyệt");
    }

    @Test
    void rejectsNonDriverRole() {
        User customer = verifiedUser(UserRole.CUSTOMER, UserStatus.ACTIVE);

        assertThat(service.isActiveDriver(authentication(customer))).isFalse();
    }

    private UsernamePasswordAuthenticationToken authentication(User user) {
        return new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());
    }

    private User verifiedUser(UserRole role, UserStatus status) {
        return User.builder()
                .role(role)
                .status(status)
                .emailVerified(true)
                .build();
    }
}
