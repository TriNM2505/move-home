package vn.movehome.backend.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.movehome.backend.dto.admin.detail.AdminOrderDetailResponse;
import vn.movehome.backend.dto.admin.detail.AuditLogItem;
import vn.movehome.backend.dto.admin.detail.CustomerDetailResponse;
import vn.movehome.backend.dto.admin.detail.CustomerOrderItem;
import vn.movehome.backend.dto.admin.detail.DriverDetailResponse;
import vn.movehome.backend.dto.admin.detail.DriverOrderItem;
import vn.movehome.backend.service.AdminDetailService;
import vn.movehome.backend.service.AdminOrderDetailService;

import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminDetailController {

    private final AdminDetailService adminDetailService;
    private final AdminOrderDetailService adminOrderDetailService;

    @GetMapping("/orders/{id}")
    public AdminOrderDetailResponse orderDetail(@PathVariable("id") UUID id) {
        return adminOrderDetailService.orderDetail(id);
    }

    @GetMapping("/drivers/{id}")
    public DriverDetailResponse driverDetail(@PathVariable("id") UUID id) {
        return adminDetailService.driverDetail(id);
    }

    @GetMapping("/customers/{id}")
    public CustomerDetailResponse customerDetail(@PathVariable("id") UUID id) {
        return adminDetailService.customerDetail(id);
    }

    @GetMapping("/{entityType}/{id}/audit-log")
    public Page<AuditLogItem> entityAuditLog(
            @PathVariable("entityType") String entityType,
            @PathVariable("id") UUID id,
            @RequestParam(value = "event_type", required = false) String eventType,
            @RequestParam(value = "date_from", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(value = "date_to", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        return adminDetailService.entityAuditLog(entityType, id, eventType, dateFrom, dateTo, page, size);
    }

    @GetMapping("/drivers/{id}/orders-history")
    public Page<DriverOrderItem> driverOrderHistory(
            @PathVariable("id") UUID id,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        return adminDetailService.driverOrderHistory(id, status, page, size);
    }

    @GetMapping("/customers/{id}/orders-history")
    public Page<CustomerOrderItem> customerOrderHistory(
            @PathVariable("id") UUID id,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        return adminDetailService.customerOrderHistory(id, status, page, size);
    }
}
