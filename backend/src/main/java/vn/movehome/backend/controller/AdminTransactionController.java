package vn.movehome.backend.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.movehome.backend.dto.admin.finance.AdminTransactionResponse;
import vn.movehome.backend.service.AdminTransactionService;

import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/transactions")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminTransactionController {

    private final AdminTransactionService adminTransactionService;

    @GetMapping
    public Page<AdminTransactionResponse> findTransactions(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(required = false) Integer size,
            @RequestParam(defaultValue = "ALL") String type,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(name = "date_from", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant dateFrom,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(name = "date_to", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant dateTo,
            @RequestParam(required = false) UUID userId,
            @RequestParam(name = "user_id", required = false) UUID userIdSnake
    ) {
        int pageSize = size != null ? size : adminTransactionService.defaultPageSize();
        return adminTransactionService.findTransactions(
                type,
                from != null ? from : dateFrom,
                to != null ? to : dateTo,
                userId != null ? userId : userIdSnake,
                page,
                pageSize
        );
    }
}
