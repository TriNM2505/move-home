package vn.movehome.backend.entity;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UserTest {

    @Test
    void verifiedOnboardingDriverIsEnabled() {
        User driver = User.builder()
                .role(UserRole.DRIVER)
                .status(UserStatus.PENDING_DOCUMENTS)
                .emailVerified(true)
                .build();

        assertThat(driver.isEnabled()).isTrue();
    }

    @Test
    void unverifiedUserIsNotEnabledEvenWhenStatusIsActive() {
        User user = User.builder()
                .role(UserRole.CUSTOMER)
                .status(UserStatus.ACTIVE)
                .emailVerified(false)
                .build();

        assertThat(user.isEnabled()).isFalse();
    }

    @Test
    void verifiedExistingRolesRemainEnabled() {
        User activeDriver = verifiedUser(UserRole.DRIVER, UserStatus.ACTIVE);
        User customer = verifiedUser(UserRole.CUSTOMER, UserStatus.ACTIVE);
        User admin = verifiedUser(UserRole.ADMIN, UserStatus.ACTIVE);

        assertThat(activeDriver.isEnabled()).isTrue();
        assertThat(customer.isEnabled()).isTrue();
        assertThat(admin.isEnabled()).isTrue();
    }

    private User verifiedUser(UserRole role, UserStatus status) {
        return User.builder()
                .role(role)
                .status(status)
                .emailVerified(true)
                .build();
    }
}
