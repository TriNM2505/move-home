package vn.movehome.backend.chat;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Hoi thoai chat giua 2 ben. Gan theo don (order_id) tru kenh ho tro CUSTOMER_MANAGER (order_id NULL).
 * - customer_id: set cho CUSTOMER_MANAGER va CUSTOMER_DRIVER
 * - driver_id:   set cho MANAGER_DRIVER va CUSTOMER_DRIVER
 * Quan ly la "quay ho tro chung" (mo hinh 1 Manager cua CONTEXT): moi tai khoan MANAGER deu xu ly
 * duoc cac hoi thoai loai CUSTOMER_MANAGER / MANAGER_DRIVER (kiem tra o tang service, HR-10).
 * Constitution AC-07: timestamp TIMESTAMPTZ luu UTC. AC-14: type VARCHAR + CHECK. HR-21: bang khong dung reserved word.
 */
@Entity
@Table(name = "conversation")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Conversation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    // Don lien quan — NULL voi kenh ho tro chung CUSTOMER_MANAGER
    @Column(name = "order_id")
    private UUID orderId;

    @Column(name = "type", nullable = false, length = 20)
    private String type;

    @Column(name = "customer_id")
    private UUID customerId;

    @Column(name = "driver_id")
    private UUID driverId;

    // Snapshot tin nhan cuoi de render danh sach hoi thoai khong can query them (tranh N+1)
    @Column(name = "last_message_text", columnDefinition = "TEXT")
    private String lastMessageText;

    @Column(name = "last_message_at")
    private OffsetDateTime lastMessageAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;
}
