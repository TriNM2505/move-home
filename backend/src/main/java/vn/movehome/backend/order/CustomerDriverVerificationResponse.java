package vn.movehome.backend.order;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Thong tin de khach doi chieu tai xe/xe khi tai xe da nhan don:
 * anh chan dung + anh xe (signed URL) + ten/bien so. Khach so voi nguoi/xe thuc te,
 * neu khong khop thi bao cao de huy chuyen.
 */
public record CustomerDriverVerificationResponse(
        @JsonProperty("driver_name") String driverName,
        @JsonProperty("driver_phone") String driverPhone,
        @JsonProperty("vehicle_type") String vehicleType,
        @JsonProperty("vehicle_plate") String vehiclePlate,
        @JsonProperty("face_photo_url") String facePhotoUrl,
        @JsonProperty("vehicle_photo_url") String vehiclePhotoUrl,
        // true khi don dang ACCEPTED (truoc khi van chuyen) — con cho phep bao cao khong khop de huy
        boolean cancellable
) {}
