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

    public static final String ONBOARDING_PENDING_REVIEW_MESSAGE =
            "Hồ sơ đang được Manager xem xét. Vui lòng đợi.";

    public boolean isActiveDriver(Authentication authentication) {
        if (authentication == null
                || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof User user)
                || user.getRole() != UserRole.DRIVER) {
            return false;
        }

        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new AccessDeniedException(ONBOARDING_PENDING_REVIEW_MESSAGE);
        }

        return true;
    }
}
