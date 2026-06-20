package vn.movehome.backend.dto.driver.onboarding;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.Set;

public class UpdateDriverProfileRequest {

    @NotBlank(message = "Số giấy phép lái xe không được để trống.")
    private String licenseNumber;

    @NotBlank(message = "Hạng giấy phép lái xe không được để trống.")
    private String licenseClass;

    @NotNull(message = "Ngày hết hạn giấy phép lái xe không được để trống.")
    private LocalDate licenseExpiryDate;

    @NotBlank(message = "Biển số xe không được để trống.")
    private String vehiclePlate;

    @NotBlank(message = "Loại xe không được để trống.")
    private String vehicleType;

    @NotNull(message = "Tải trọng xe không được để trống.")
    @Min(value = 1, message = "Tải trọng xe phải lớn hơn 0 kg.")
    private Integer vehicleCapacityKg;

    private final Set<String> unknownFields = new LinkedHashSet<>();

    public String licenseNumber() {
        return licenseNumber;
    }

    public void setLicenseNumber(String licenseNumber) {
        this.licenseNumber = licenseNumber;
    }

    public String licenseClass() {
        return licenseClass;
    }

    public void setLicenseClass(String licenseClass) {
        this.licenseClass = licenseClass;
    }

    public LocalDate licenseExpiryDate() {
        return licenseExpiryDate;
    }

    public void setLicenseExpiryDate(LocalDate licenseExpiryDate) {
        this.licenseExpiryDate = licenseExpiryDate;
    }

    public String vehiclePlate() {
        return vehiclePlate;
    }

    public void setVehiclePlate(String vehiclePlate) {
        this.vehiclePlate = vehiclePlate;
    }

    public String vehicleType() {
        return vehicleType;
    }

    public void setVehicleType(String vehicleType) {
        this.vehicleType = vehicleType;
    }

    public Integer vehicleCapacityKg() {
        return vehicleCapacityKg;
    }

    public void setVehicleCapacityKg(Integer vehicleCapacityKg) {
        this.vehicleCapacityKg = vehicleCapacityKg;
    }

    public Set<String> unknownFields() {
        return Set.copyOf(unknownFields);
    }

    @JsonAnySetter
    void setUnknownField(String fieldName, Object ignoredValue) {
        unknownFields.add(fieldName);
    }
}
