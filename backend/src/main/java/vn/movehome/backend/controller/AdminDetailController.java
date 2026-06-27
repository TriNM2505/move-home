package vn.movehome.backend.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.movehome.backend.dto.admin.detail.DriverDetailResponse;
import vn.movehome.backend.service.AdminDetailService;

import java.util.UUID;

@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminDetailController {

    private final AdminDetailService adminDetailService;

    @GetMapping("/drivers/{id}")
    public DriverDetailResponse driverDetail(@PathVariable("id") UUID id) {
        return adminDetailService.driverDetail(id);
    }
}
