package vn.movehome.backend.chat;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ConversationRepository extends JpaRepository<Conversation, UUID> {

    // Tim hoi thoai gan theo don theo loai (CUSTOMER_DRIVER / MANAGER_DRIVER)
    Optional<Conversation> findByOrderIdAndType(UUID orderId, String type);

    // Kenh ho tro chung cua khach (order_id NULL)
    Optional<Conversation> findByCustomerIdAndTypeAndOrderIdIsNull(UUID customerId, String type);

    // Kenh ho tro chung tai xe <-> quan ly (order_id NULL)
    Optional<Conversation> findByDriverIdAndTypeAndOrderIdIsNull(UUID driverId, String type);

    // Danh sach hoi thoai theo tung vai (sort o service de xu ly nulls-last)
    List<Conversation> findByCustomerId(UUID customerId);

    List<Conversation> findByDriverId(UUID driverId);

    // Quay ho tro (Manager/Admin): moi hoi thoai loai CUSTOMER_MANAGER + MANAGER_DRIVER
    List<Conversation> findByTypeIn(Collection<String> types);

    // Chi lay id de tinh badge chua doc (nhe)
    @Query("select c.id from Conversation c where c.customerId = :userId")
    List<UUID> findIdsByCustomerId(@Param("userId") UUID userId);

    @Query("select c.id from Conversation c where c.driverId = :userId")
    List<UUID> findIdsByDriverId(@Param("userId") UUID userId);

    @Query("select c.id from Conversation c where c.type in :types")
    List<UUID> findIdsByTypeIn(@Param("types") Collection<String> types);
}
