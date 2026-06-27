package vn.movehome.backend.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.movehome.backend.dto.admin.list.CustomerListItem;
import vn.movehome.backend.dto.admin.list.DriverListItem;
import vn.movehome.backend.dto.admin.list.OrderListItem;
import vn.movehome.backend.dto.admin.list.WithdrawalListItem;
import vn.movehome.backend.service.AdminListService;

@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminListController {

    private final AdminListService adminListService;

    @GetMapping("/orders")
    public Page<OrderListItem> listOrders(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "ALL") String status,
            @RequestParam(required = false) String search,
            @RequestParam(name = "date_from", required = false) String dateFrom,
            @RequestParam(name = "date_to", required = false) String dateTo,
            @RequestParam(defaultValue = "created_at,desc") String sort
    ) {
        return adminListService.listOrders(status, search, dateFrom, dateTo, page, size, sort);
    }

    @GetMapping("/drivers")
    public Page<DriverListItem> listDrivers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "ALL") String status,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "created_at,desc") String sort
    ) {
        return adminListService.listDrivers(status, search, page, size, sort);
    }

    @GetMapping("/customers")
    public Page<CustomerListItem> listCustomers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "ALL") String status,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "created_at,desc") String sort
    ) {
        return adminListService.listCustomers(status, search, page, size, sort);
    }

    @GetMapping("/withdrawals")
    public Page<WithdrawalListItem> listWithdrawals(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "ALL") String status,
            @RequestParam(required = false) String search,
            @RequestParam(name = "date_from", required = false) String dateFrom,
            @RequestParam(name = "date_to", required = false) String dateTo,
            @RequestParam(defaultValue = "requested_at,desc") String sort
    ) {
        return adminListService.listWithdrawals(status, search, dateFrom, dateTo, page, size, sort);
    }
}
