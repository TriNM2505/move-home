package vn.movehome.backend.dto.customer.profile;

import com.fasterxml.jackson.annotation.JsonAnySetter;

import java.util.LinkedHashSet;
import java.util.Set;

public class UpdateProfileRequest {

    private String fullName;
    private String phone;
    private final Set<String> unknownFields = new LinkedHashSet<>();

    public String fullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String phone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public Set<String> unknownFields() {
        return Set.copyOf(unknownFields);
    }

    @JsonAnySetter
    void setUnknownField(String fieldName, Object ignoredValue) {
        unknownFields.add(fieldName);
    }
}
