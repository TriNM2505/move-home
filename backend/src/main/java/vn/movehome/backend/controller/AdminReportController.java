package vn.movehome.backend.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.movehome.backend.dto.admin.report.CustomersReportResponse;
import vn.movehome.backend.dto.admin.report.DriversReportResponse;
import vn.movehome.backend.dto.admin.report.FinancialReportResponse;
import vn.movehome.backend.dto.admin.report.OperationsReportResponse;
import vn.movehome.backend.dto.admin.report.PeakHoursReportResponse;
import vn.movehome.backend.service.AdminReportService;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/admin/reports")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminReportController {

    private final AdminReportService adminReportService;

    @GetMapping("/financial")
    public FinancialReportResponse financial(
            @RequestParam(value = "period_start", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate periodStart,
            @RequestParam(value = "period_end", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate periodEnd,
            @RequestParam(value = "compare_with", required = false) String compareWith,
            @RequestParam(value = "group_by", required = false) String groupBy) {
        return adminReportService.financialReport(periodStart, periodEnd, compareWith, groupBy);
    }

    @GetMapping("/operations")
    public OperationsReportResponse operations(
            @RequestParam(value = "period_start", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate periodStart,
            @RequestParam(value = "period_end", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate periodEnd,
            @RequestParam(value = "compare_with", required = false) String compareWith,
            @RequestParam(value = "group_by", required = false) String groupBy) {
        return adminReportService.operationsReport(periodStart, periodEnd, compareWith, groupBy);
    }

    @GetMapping("/drivers")
    public DriversReportResponse drivers(
            @RequestParam(value = "period_start", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate periodStart,
            @RequestParam(value = "period_end", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate periodEnd,
            @RequestParam(value = "compare_with", required = false) String compareWith) {
        return adminReportService.driversReport(periodStart, periodEnd, compareWith);
    }

    @GetMapping("/customers")
    public CustomersReportResponse customers(
            @RequestParam(value = "period_start", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate periodStart,
            @RequestParam(value = "period_end", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate periodEnd,
            @RequestParam(value = "compare_with", required = false) String compareWith) {
        return adminReportService.customersReport(periodStart, periodEnd, compareWith);
    }

    @GetMapping("/peak-hours")
    public PeakHoursReportResponse peakHours(
            @RequestParam(value = "period_start", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate periodStart,
            @RequestParam(value = "period_end", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate periodEnd) {
        return adminReportService.peakHoursReport(periodStart, periodEnd);
    }
}
