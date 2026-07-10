package vn.movehome.backend.chat.dto;

import java.util.UUID;

/**
 * 1 dong trong danh ba tai xe (cho Manager chon tai xe de nhan tin, khong gan theo don).
 */
public record DriverDirectoryItem(
        UUID id,
        String fullName,
        String phone
) {
}
