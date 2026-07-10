package vn.movehome.backend.dispute;

import jakarta.validation.constraints.NotNull;

/**
 * Manager xu ly khieu nai DRIVER_MISMATCH (tai xe/xe khong khop tai diem don).
 * - accept=true  → cong ty hoan coc 30% cho khach + phat 500k tai xe (chuyen cho khach).
 * - accept=false → bac khieu nai (khong hoan, khong phat). Don van da huy.
 */
public record ResolveMismatchRequest(
        @NotNull(message = "Thieu quyet dinh xu ly (accept).") Boolean accept,
        String note
) {
}
