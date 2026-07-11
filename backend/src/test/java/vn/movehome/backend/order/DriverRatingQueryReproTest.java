package vn.movehome.backend.order;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import vn.movehome.backend.dto.manager.DriverRatingItem;
import vn.movehome.backend.entity.User;
import vn.movehome.backend.entity.UserRole;
import vn.movehome.backend.entity.UserStatus;
import vn.movehome.backend.repository.UserRepository;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tai hien loi 500 cua GET /api/manager/driver-ratings: chay query searchForManager
 * tren H2 (MODE=PostgreSQL) voi du lieu seed, du cac to hop filter null/khong-null.
 * Neu HQL sai (cast, join, constructor expression, count query) → test nem exception.
 */
@DataJpaTest
class DriverRatingQueryReproTest {

    @Autowired private OrderRatingRepository orderRatingRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private OrderRepository orderRepository;

    @Test
    void searchForManager_allFilterCombos_shouldNotThrow() {
        User customer = userRepository.saveAndFlush(User.builder()
                .email("customer.rating@test.vn")
                .passwordHash("x")
                .fullName("Khach Test")
                .role(UserRole.CUSTOMER)
                .status(UserStatus.ACTIVE)
                .build());
        User driver = userRepository.saveAndFlush(User.builder()
                .email("driver.rating@test.vn")
                .passwordHash("x")
                .fullName("Tai Xe Test")
                .role(UserRole.DRIVER)
                .status(UserStatus.ACTIVE)
                .build());
        ServiceOrder order = orderRepository.saveAndFlush(ServiceOrder.builder()
                .orderCode("MH-RATE-1")
                .customerId(customer.getId())
                .driverId(driver.getId())
                .pickupAddress("1 Pho A")
                .dropoffAddress("2 Pho B")
                .scheduledAt(OffsetDateTime.now())
                .status("COMPLETED")
                .totalQuote(new BigDecimal("1000000"))
                .build());
        orderRatingRepository.saveAndFlush(OrderRating.builder()
                .orderId(order.getId())
                .customerId(customer.getId())
                .driverId(driver.getId())
                .stars(4)
                .comment("Tai xe than thien")
                .build());
        // Danh gia khong co tai xe (driver_id NULL — du lieu cu V9) de test left join
        ServiceOrder orphanOrder = orderRepository.saveAndFlush(ServiceOrder.builder()
                .orderCode("MH-RATE-2")
                .customerId(customer.getId())
                .pickupAddress("3 Pho C")
                .dropoffAddress("4 Pho D")
                .scheduledAt(OffsetDateTime.now())
                .status("COMPLETED")
                .totalQuote(new BigDecimal("500000"))
                .build());
        orderRatingRepository.saveAndFlush(OrderRating.builder()
                .orderId(orphanOrder.getId())
                .customerId(customer.getId())
                .stars(2)
                .build());

        Pageable page = PageRequest.of(0, 10);

        // 1. Khong filter (ca 3 param null) — case trang manager load lan dau
        Page<DriverRatingItem> all = orderRatingRepository.searchForManager(null, null, null, page);
        assertThat(all.getTotalElements()).isEqualTo(2);
        assertThat(all.getContent().get(0).orderCode()).isNotBlank();

        // 2. Filter theo tai xe
        Page<DriverRatingItem> byDriver = orderRatingRepository.searchForManager(
                driver.getId().toString(), null, null, page);
        assertThat(byDriver.getTotalElements()).isEqualTo(1);
        assertThat(byDriver.getContent().get(0).driverName()).isEqualTo("Tai Xe Test");

        // 3. Filter theo sao
        Page<DriverRatingItem> byStars = orderRatingRepository.searchForManager(null, "4", null, page);
        assertThat(byStars.getTotalElements()).isEqualTo(1);
        assertThat(byStars.getContent().get(0).stars()).isEqualTo(4);

        // 4. Filter theo keyword ten tai xe (pattern %...% da lowercase san — contract voi service)
        Page<DriverRatingItem> byKeyword = orderRatingRepository.searchForManager(
                null, null, "%tai xe%", page);
        assertThat(byKeyword.getTotalElements()).isEqualTo(1);

        // 5. Du ca 3 filter
        Page<DriverRatingItem> combined = orderRatingRepository.searchForManager(
                driver.getId().toString(), "4", "%tai%", page);
        assertThat(combined.getTotalElements()).isEqualTo(1);
    }
}
