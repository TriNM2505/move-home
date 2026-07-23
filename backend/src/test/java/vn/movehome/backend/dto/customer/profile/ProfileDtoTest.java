package vn.movehome.backend.dto.customer.profile;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ProfileDtoTest {

    @Test
    void changePasswordRequestExposesRawFields() {
        ChangePasswordRequest request = new ChangePasswordRequest("Old@123456", "New@123456");

        assertThat(request.currentPassword()).isEqualTo("Old@123456");
        assertThat(request.newPassword()).isEqualTo("New@123456");
    }

    @Test
    void changePasswordResponseExposesAllFields() {
        ChangePasswordResponse response = new ChangePasswordResponse(
                "Mật khẩu đã được thay đổi. Vui lòng đăng nhập lại.", true, true);

        assertThat(response.message()).isEqualTo("Mật khẩu đã được thay đổi. Vui lòng đăng nhập lại.");
        assertThat(response.forceRelogin()).isTrue();
        assertThat(response.sessionsRevoked()).isTrue();
    }

    @Test
    void customerProfileResponseExposesAllFields() {
        UUID id = UUID.randomUUID();
        Instant createdAt = Instant.parse("2026-01-01T00:00:00Z");
        CustomerProfileResponse response = new CustomerProfileResponse(
                id, "Nguyen Van A", "a@example.com", "+84912345678",
                "https://res.cloudinary.com/demo/image/upload/avatar.jpg", "ACTIVE", createdAt, 5L);

        assertThat(response.id()).isEqualTo(id);
        assertThat(response.fullName()).isEqualTo("Nguyen Van A");
        assertThat(response.email()).isEqualTo("a@example.com");
        assertThat(response.phone()).isEqualTo("+84912345678");
        assertThat(response.avatarUrl()).isEqualTo("https://res.cloudinary.com/demo/image/upload/avatar.jpg");
        assertThat(response.status()).isEqualTo("ACTIVE");
        assertThat(response.createdAt()).isEqualTo(createdAt);
        assertThat(response.totalOrders()).isEqualTo(5L);
    }

    @Test
    void updateProfileResponseExposesAllFields() {
        CustomerProfileResponse profile = new CustomerProfileResponse(
                UUID.randomUUID(), "Nguyen Van A", "a@example.com", "+84912345678",
                null, "ACTIVE", Instant.now(), 0L);
        UpdateProfileResponse response = new UpdateProfileResponse(profile, "Cập nhật thông tin cá nhân thành công.");

        assertThat(response.profile()).isEqualTo(profile);
        assertThat(response.message()).isEqualTo("Cập nhật thông tin cá nhân thành công.");
    }

    @Test
    void updateProfileRequestGettersAndSettersRoundTrip() {
        UpdateProfileRequest request = new UpdateProfileRequest();
        request.setFullName("Tran Thi B");
        request.setPhone("0987654321");

        assertThat(request.fullName()).isEqualTo("Tran Thi B");
        assertThat(request.phone()).isEqualTo("0987654321");
        assertThat(request.unknownFields()).isEmpty();
    }

    @Test
    void updateProfileRequestCollectsUnknownFieldsViaJsonAnySetter() throws Exception {
        ObjectMapper mapper = new ObjectMapper();

        UpdateProfileRequest request = mapper.readValue(
                "{\"fullName\":\"Tran Thi B\",\"phone\":\"0987654321\","
                        + "\"email\":\"hack@example.com\",\"role\":\"ADMIN\"}",
                UpdateProfileRequest.class);

        assertThat(request.fullName()).isEqualTo("Tran Thi B");
        assertThat(request.phone()).isEqualTo("0987654321");
        assertThat(request.unknownFields()).containsExactlyInAnyOrder("email", "role");
    }
}
