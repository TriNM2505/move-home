package vn.movehome.backend.chat.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.UUID;

/**
 * Yeu cau mo (hoac lay) 1 hoi thoai.
 * - orderId: bat buoc voi CUSTOMER_DRIVER; voi MANAGER_DRIVER neu co orderId thi gan theo don,
 *   neu khong thi la kenh ho tro chung tai xe <-> quan ly.
 * - driverId: chi dung khi Manager mo MANAGER_DRIVER khong theo don (chon 1 tai xe cu the).
 * - type: mot trong ConversationType.
 */
public record OpenConversationRequest(
        UUID orderId,
        UUID driverId,
        @NotBlank(message = "Loai hoi thoai khong duoc de trong.") String type
) {
}
