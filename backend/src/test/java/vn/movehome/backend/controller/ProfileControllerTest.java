package vn.movehome.backend.controller;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import vn.movehome.backend.dto.customer.profile.ChangePasswordRequest;
import vn.movehome.backend.dto.customer.profile.ChangePasswordResponse;
import vn.movehome.backend.dto.customer.profile.CustomerProfileResponse;
import vn.movehome.backend.dto.customer.profile.UpdateProfileRequest;
import vn.movehome.backend.dto.customer.profile.UpdateProfileResponse;
import vn.movehome.backend.entity.User;
import vn.movehome.backend.service.CustomerProfileService;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProfileControllerTest {

    private final CustomerProfileService service = mock(CustomerProfileService.class);
    private final ProfileController controller = new ProfileController(service);

    @Test
    void getProfileUsesAuthenticatedPrincipalId() {
        UUID customerId = UUID.randomUUID();
        User currentUser = User.builder().id(customerId).build();
        CustomerProfileResponse expected = new CustomerProfileResponse(
                customerId, "Nguyen Van A", "a@example.com", "+84912345678", null, "ACTIVE", Instant.now(), 3L);
        when(service.getProfile(customerId)).thenReturn(expected);

        CustomerProfileResponse actual = controller.getProfile(currentUser);

        assertThat(actual).isEqualTo(expected);
        verify(service).getProfile(customerId);
    }

    @Test
    void updateProfileUsesAuthenticatedPrincipalId() {
        UUID customerId = UUID.randomUUID();
        User currentUser = User.builder().id(customerId).build();
        UpdateProfileRequest request = new UpdateProfileRequest();
        request.setFullName("Tran Thi B");
        request.setPhone("0987654321");
        CustomerProfileResponse profile = new CustomerProfileResponse(
                customerId, "Tran Thi B", "b@example.com", "+84987654321", null, "ACTIVE", Instant.now(), 0L);
        UpdateProfileResponse expected = new UpdateProfileResponse(profile, "Cập nhật thông tin cá nhân thành công.");
        when(service.updateProfile(customerId, request)).thenReturn(expected);

        UpdateProfileResponse actual = controller.updateProfile(currentUser, request);

        assertThat(actual).isEqualTo(expected);
        verify(service).updateProfile(customerId, request);
    }

    @Test
    void updateAvatarUsesAuthenticatedPrincipalId() {
        UUID customerId = UUID.randomUUID();
        User currentUser = User.builder().id(customerId).build();
        MockMultipartFile file = new MockMultipartFile("file", "avatar.jpg", "image/jpeg",
                new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF});
        CustomerProfileResponse profile = new CustomerProfileResponse(
                customerId, "Nguyen Van A", "a@example.com", "+84912345678",
                "https://res.cloudinary.com/demo/image/upload/avatar.jpg", "ACTIVE", Instant.now(), 0L);
        UpdateProfileResponse expected = new UpdateProfileResponse(profile, "Cập nhật ảnh đại diện thành công.");
        when(service.updateAvatar(customerId, file)).thenReturn(expected);

        UpdateProfileResponse actual = controller.updateAvatar(currentUser, file);

        assertThat(actual).isEqualTo(expected);
        verify(service).updateAvatar(customerId, file);
    }

    @Test
    void changePasswordUsesAuthenticatedPrincipalId() {
        UUID customerId = UUID.randomUUID();
        User currentUser = User.builder().id(customerId).build();
        ChangePasswordRequest request = new ChangePasswordRequest("Old@123456", "New@123456");
        ChangePasswordResponse expected = new ChangePasswordResponse(
                "Mật khẩu đã được thay đổi. Vui lòng đăng nhập lại.", true, true);
        when(service.changePassword(customerId, request)).thenReturn(expected);

        ChangePasswordResponse actual = controller.changePassword(currentUser, request);

        assertThat(actual).isEqualTo(expected);
        verify(service).changePassword(customerId, request);
    }
}
