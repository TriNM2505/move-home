package vn.movehome.backend.service;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import lombok.RequiredArgsConstructor;
import vn.movehome.backend.dto.manager.DriverRatingItem;
import vn.movehome.backend.order.OrderRatingRepository;

/**
 * Truy van danh gia tai xe cho Manager (kem comment) — man "Đánh giá tài xế".
 * AC-15: server-side pagination (danh sach co the > 50 dong).
 * Sort co dinh createdAt DESC trong query (moi nhat truoc) — khong nhan sort tu client.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ManagerDriverRatingService {

    private static final int MAX_PAGE_SIZE = 100;

    private final OrderRatingRepository orderRatingRepository;

    public Page<DriverRatingItem> search(UUID driverId, Integer stars, String keyword, int page, int size) {
        if (stars != null && (stars < 1 || stars > 5)) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "VALIDATION_ERROR|Bộ lọc số sao phải từ 1 đến 5.");
        }

        // Convert sang String truoc khi bind — tranh loi kieu du lieu khi param null (xem repository)
        String driverIdParam = driverId != null ? driverId.toString() : null;
        String starsParam = stars != null ? stars.toString() : null;
        // Lowercase o day thay vi lower(:param) trong query — xem ghi chu repository (loi lower(bytea))
        String keywordPattern = (keyword == null || keyword.isBlank())
                ? null : "%" + keyword.trim().toLowerCase() + "%";
        Pageable pageable = PageRequest.of(clampPage(page), clampSize(size));

        return orderRatingRepository.searchForManager(driverIdParam, starsParam, keywordPattern, pageable);
    }

    private int clampPage(int page) {
        return Math.max(page, 0);
    }

    private int clampSize(int size) {
        if (size < 1) {
            return 10;
        }
        return Math.min(size, MAX_PAGE_SIZE);
    }
}
