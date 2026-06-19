package vn.movehome.backend.security;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import vn.movehome.backend.entity.User;
import vn.movehome.backend.entity.UserRole;
import vn.movehome.backend.entity.UserStatus;

/**
 * Gate trạng thái dành riêng cho workflow tài xế.
 * Authentication chỉ xác nhận danh tính/email; workflow orders yêu cầu Driver ACTIVE.
 */
@Service
public class DriverWorkflowAccessService {

    public static final String PROFILE_NOT_APPROVED_MESSAGE = "Hồ sơ tài xế chưa được duyệt";

    public boolean isActiveDriver(Authentication authentication) {
        if (authentication == null
                || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof User user)
                || user.getRole() != UserRole.DRIVER) {
            return false;
        }

        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new AccessDeniedException(PROFILE_NOT_APPROVED_MESSAGE);
        }

        return true;
    }
}
