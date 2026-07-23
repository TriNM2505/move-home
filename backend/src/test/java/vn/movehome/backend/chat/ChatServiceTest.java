package vn.movehome.backend.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import vn.movehome.backend.chat.dto.ChatMessageResponse;
import vn.movehome.backend.chat.dto.ConversationResponse;
import vn.movehome.backend.chat.dto.DriverDirectoryItem;
import vn.movehome.backend.entity.User;
import vn.movehome.backend.entity.UserRole;
import vn.movehome.backend.entity.UserStatus;
import vn.movehome.backend.order.OrderRepository;
import vn.movehome.backend.order.ServiceOrder;
import vn.movehome.backend.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class ChatServiceTest {

    @Mock
    private ConversationRepository conversationRepository;

    @Mock
    private ChatMessageRepository messageRepository;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private ChatRealtimePublisher realtimePublisher;

    @Mock
    private ChatImageService chatImageService;

    @InjectMocks
    private ChatService chatService;

    // ===================== openConversation: dispatch & validation =====================

    @Test
    void openConversation_invalidType_throwsBadRequest() {
        User customer = user(UUID.randomUUID(), UserRole.CUSTOMER, "Khach A");

        assertThatThrownBy(() -> chatService.openConversation(customer, null, null, "BOGUS"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("INVALID_CONVERSATION_TYPE")
                .hasMessageContaining("Loại hội thoại không hợp lệ.");
        verify(conversationRepository, never()).save(any());
    }

    @Test
    void openConversation_customerDriver_withoutOrderId_throwsOrderIdRequired() {
        User customer = user(UUID.randomUUID(), UserRole.CUSTOMER, "Khach A");

        assertThatThrownBy(() -> chatService.openConversation(customer, null, null, ConversationType.CUSTOMER_DRIVER))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("ORDER_ID_REQUIRED")
                .hasMessageContaining("Thiếu mã đơn cho hội thoại theo đơn.");
    }

    @Test
    void openConversation_customerManager_withOrderId_delegatesToByOrder() {
        UUID orderId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        User customer = user(customerId, UserRole.CUSTOMER, "Khach A");
        ServiceOrder order = order(orderId, customerId, null);

        when(orderRepository.findByIdAndDeletedAtIsNull(orderId)).thenReturn(Optional.of(order));
        when(conversationRepository.findByOrderIdAndType(orderId, ConversationType.CUSTOMER_MANAGER))
                .thenReturn(Optional.of(conv(UUID.randomUUID(), orderId, ConversationType.CUSTOMER_MANAGER, customerId, null)));

        Conversation result = chatService.openConversation(customer, orderId, null, ConversationType.CUSTOMER_MANAGER);

        assertThat(result.getOrderId()).isEqualTo(orderId);
    }

    @Test
    void openConversation_customerManager_withoutOrderId_delegatesToSupport() {
        UUID customerId = UUID.randomUUID();
        User customer = user(customerId, UserRole.CUSTOMER, "Khach A");

        when(conversationRepository.findByCustomerIdAndTypeAndOrderIdIsNull(customerId, ConversationType.CUSTOMER_MANAGER))
                .thenReturn(Optional.of(conv(UUID.randomUUID(), null, ConversationType.CUSTOMER_MANAGER, customerId, null)));

        Conversation result = chatService.openConversation(customer, null, null, ConversationType.CUSTOMER_MANAGER);

        assertThat(result.getCustomerId()).isEqualTo(customerId);
    }

    @Test
    void openConversation_managerDriver_withOrderId_delegatesToByOrder() {
        UUID orderId = UUID.randomUUID();
        UUID driverId = UUID.randomUUID();
        User manager = user(UUID.randomUUID(), UserRole.MANAGER, "Quan ly A");
        ServiceOrder order = order(orderId, UUID.randomUUID(), driverId);

        when(orderRepository.findByIdAndDeletedAtIsNull(orderId)).thenReturn(Optional.of(order));
        when(conversationRepository.findByOrderIdAndType(orderId, ConversationType.MANAGER_DRIVER))
                .thenReturn(Optional.of(conv(UUID.randomUUID(), orderId, ConversationType.MANAGER_DRIVER, null, driverId)));

        Conversation result = chatService.openConversation(manager, orderId, null, ConversationType.MANAGER_DRIVER);

        assertThat(result.getDriverId()).isEqualTo(driverId);
    }

    @Test
    void openConversation_managerDriver_withoutOrderId_delegatesToGeneral() {
        UUID driverId = UUID.randomUUID();
        User driver = user(driverId, UserRole.DRIVER, "Tai xe A");

        when(conversationRepository.findByDriverIdAndTypeAndOrderIdIsNull(driverId, ConversationType.MANAGER_DRIVER))
                .thenReturn(Optional.of(conv(UUID.randomUUID(), null, ConversationType.MANAGER_DRIVER, null, driverId)));

        Conversation result = chatService.openConversation(driver, null, null, ConversationType.MANAGER_DRIVER);

        assertThat(result.getDriverId()).isEqualTo(driverId);
    }

    // ===================== openSupport =====================

    @Test
    void openSupport_nonCustomer_throwsForbidden() {
        User driver = user(UUID.randomUUID(), UserRole.DRIVER, "Tai xe A");

        assertThatThrownBy(() -> chatService.openConversation(driver, null, null, ConversationType.CUSTOMER_MANAGER))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("FORBIDDEN")
                .hasMessageContaining("Chỉ khách hàng mới mở kênh hỗ trợ với quản lý.");
    }

    @Test
    void openSupport_existingConversation_returnsIt() {
        UUID customerId = UUID.randomUUID();
        User customer = user(customerId, UserRole.CUSTOMER, "Khach A");
        Conversation existing = conv(UUID.randomUUID(), null, ConversationType.CUSTOMER_MANAGER, customerId, null);

        when(conversationRepository.findByCustomerIdAndTypeAndOrderIdIsNull(customerId, ConversationType.CUSTOMER_MANAGER))
                .thenReturn(Optional.of(existing));

        Conversation result = chatService.openConversation(customer, null, null, ConversationType.CUSTOMER_MANAGER);

        assertThat(result).isSameAs(existing);
        verify(conversationRepository, never()).save(any());
    }

    @Test
    void openSupport_noExisting_createsNew() {
        UUID customerId = UUID.randomUUID();
        User customer = user(customerId, UserRole.CUSTOMER, "Khach A");

        when(conversationRepository.findByCustomerIdAndTypeAndOrderIdIsNull(customerId, ConversationType.CUSTOMER_MANAGER))
                .thenReturn(Optional.empty());
        when(conversationRepository.save(any(Conversation.class))).thenAnswer(inv -> inv.getArgument(0));

        Conversation result = chatService.openConversation(customer, null, null, ConversationType.CUSTOMER_MANAGER);

        assertThat(result.getCustomerId()).isEqualTo(customerId);
        assertThat(result.getOrderId()).isNull();
        assertThat(result.getType()).isEqualTo(ConversationType.CUSTOMER_MANAGER);
        verify(conversationRepository, times(1)).save(any(Conversation.class));
    }

    // ===================== openCustomerManagerByOrder =====================

    @Test
    void openCustomerManagerByOrder_orderNotFound_throwsNotFound() {
        UUID orderId = UUID.randomUUID();
        User manager = user(UUID.randomUUID(), UserRole.MANAGER, "Quan ly A");
        when(orderRepository.findByIdAndDeletedAtIsNull(orderId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> chatService.openConversation(manager, orderId, null, ConversationType.CUSTOMER_MANAGER))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("ORDER_NOT_FOUND")
                .hasMessageContaining("Không tìm thấy đơn hàng.");
    }

    @Test
    void openCustomerManagerByOrder_notAllowed_throwsForbidden() {
        UUID orderId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        User otherCustomer = user(UUID.randomUUID(), UserRole.CUSTOMER, "Khach B");
        ServiceOrder order = order(orderId, customerId, null);
        when(orderRepository.findByIdAndDeletedAtIsNull(orderId)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> chatService.openConversation(otherCustomer, orderId, null, ConversationType.CUSTOMER_MANAGER))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("FORBIDDEN")
                .hasMessageContaining("Bạn không có quyền mở hội thoại này.");
    }

    @Test
    void openCustomerManagerByOrder_admin_allowed_createsNew() {
        UUID orderId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        User admin = user(UUID.randomUUID(), UserRole.ADMIN, "Admin A");
        ServiceOrder order = order(orderId, customerId, null);
        when(orderRepository.findByIdAndDeletedAtIsNull(orderId)).thenReturn(Optional.of(order));
        when(conversationRepository.findByOrderIdAndType(orderId, ConversationType.CUSTOMER_MANAGER))
                .thenReturn(Optional.empty());
        when(conversationRepository.save(any(Conversation.class))).thenAnswer(inv -> inv.getArgument(0));

        Conversation result = chatService.openConversation(admin, orderId, null, ConversationType.CUSTOMER_MANAGER);

        assertThat(result.getCustomerId()).isEqualTo(customerId);
        assertThat(result.getOrderId()).isEqualTo(orderId);
    }

    @Test
    void openCustomerManagerByOrder_ownerCustomer_allowed_returnsExisting() {
        UUID orderId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        User customer = user(customerId, UserRole.CUSTOMER, "Khach A");
        ServiceOrder order = order(orderId, customerId, null);
        Conversation existing = conv(UUID.randomUUID(), orderId, ConversationType.CUSTOMER_MANAGER, customerId, null);
        when(orderRepository.findByIdAndDeletedAtIsNull(orderId)).thenReturn(Optional.of(order));
        when(conversationRepository.findByOrderIdAndType(orderId, ConversationType.CUSTOMER_MANAGER))
                .thenReturn(Optional.of(existing));

        Conversation result = chatService.openConversation(customer, orderId, null, ConversationType.CUSTOMER_MANAGER);

        assertThat(result).isSameAs(existing);
    }

    // ===================== openCustomerDriver =====================

    @Test
    void openCustomerDriver_orderNotFound_throwsNotFound() {
        UUID orderId = UUID.randomUUID();
        User customer = user(UUID.randomUUID(), UserRole.CUSTOMER, "Khach A");
        when(orderRepository.findByIdAndDeletedAtIsNull(orderId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> chatService.openConversation(customer, orderId, null, ConversationType.CUSTOMER_DRIVER))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("ORDER_NOT_FOUND");
    }

    @Test
    void openCustomerDriver_noDriverAssigned_throwsConflict() {
        UUID orderId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        User customer = user(customerId, UserRole.CUSTOMER, "Khach A");
        ServiceOrder order = order(orderId, customerId, null);
        when(orderRepository.findByIdAndDeletedAtIsNull(orderId)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> chatService.openConversation(customer, orderId, null, ConversationType.CUSTOMER_DRIVER))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("ORDER_NO_DRIVER")
                .hasMessageContaining("Đơn chưa có tài xế nên chưa thể nhắn tin.");
    }

    @Test
    void openCustomerDriver_notParticipant_throwsForbidden() {
        UUID orderId = UUID.randomUUID();
        User stranger = user(UUID.randomUUID(), UserRole.CUSTOMER, "Khach la");
        ServiceOrder order = order(orderId, UUID.randomUUID(), UUID.randomUUID());
        when(orderRepository.findByIdAndDeletedAtIsNull(orderId)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> chatService.openConversation(stranger, orderId, null, ConversationType.CUSTOMER_DRIVER))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("FORBIDDEN")
                .hasMessageContaining("Bạn không thuộc đơn này.");
    }

    @Test
    void openCustomerDriver_driverParticipant_createsNew() {
        UUID orderId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        UUID driverId = UUID.randomUUID();
        User driver = user(driverId, UserRole.DRIVER, "Tai xe A");
        ServiceOrder order = order(orderId, customerId, driverId);
        when(orderRepository.findByIdAndDeletedAtIsNull(orderId)).thenReturn(Optional.of(order));
        when(conversationRepository.findByOrderIdAndType(orderId, ConversationType.CUSTOMER_DRIVER))
                .thenReturn(Optional.empty());
        when(conversationRepository.save(any(Conversation.class))).thenAnswer(inv -> inv.getArgument(0));

        Conversation result = chatService.openConversation(driver, orderId, null, ConversationType.CUSTOMER_DRIVER);

        assertThat(result.getCustomerId()).isEqualTo(customerId);
        assertThat(result.getDriverId()).isEqualTo(driverId);
    }

    // ===================== openManagerDriverByOrder =====================

    @Test
    void openManagerDriverByOrder_orderNotFound_throwsNotFound() {
        UUID orderId = UUID.randomUUID();
        User manager = user(UUID.randomUUID(), UserRole.MANAGER, "Quan ly A");
        when(orderRepository.findByIdAndDeletedAtIsNull(orderId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> chatService.openConversation(manager, orderId, null, ConversationType.MANAGER_DRIVER))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("ORDER_NOT_FOUND");
    }

    @Test
    void openManagerDriverByOrder_noDriverAssigned_throwsConflict() {
        UUID orderId = UUID.randomUUID();
        User manager = user(UUID.randomUUID(), UserRole.MANAGER, "Quan ly A");
        ServiceOrder order = order(orderId, UUID.randomUUID(), null);
        when(orderRepository.findByIdAndDeletedAtIsNull(orderId)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> chatService.openConversation(manager, orderId, null, ConversationType.MANAGER_DRIVER))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("ORDER_NO_DRIVER");
    }

    @Test
    void openManagerDriverByOrder_notAllowed_throwsForbidden() {
        UUID orderId = UUID.randomUUID();
        User strangerDriver = user(UUID.randomUUID(), UserRole.DRIVER, "Tai xe khac");
        ServiceOrder order = order(orderId, UUID.randomUUID(), UUID.randomUUID());
        when(orderRepository.findByIdAndDeletedAtIsNull(orderId)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> chatService.openConversation(strangerDriver, orderId, null, ConversationType.MANAGER_DRIVER))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("FORBIDDEN")
                .hasMessageContaining("Bạn không có quyền mở hội thoại này.");
    }

    @Test
    void openManagerDriverByOrder_driverAllowed_returnsExisting() {
        UUID orderId = UUID.randomUUID();
        UUID driverId = UUID.randomUUID();
        User driver = user(driverId, UserRole.DRIVER, "Tai xe A");
        ServiceOrder order = order(orderId, UUID.randomUUID(), driverId);
        Conversation existing = conv(UUID.randomUUID(), orderId, ConversationType.MANAGER_DRIVER, null, driverId);
        when(orderRepository.findByIdAndDeletedAtIsNull(orderId)).thenReturn(Optional.of(order));
        when(conversationRepository.findByOrderIdAndType(orderId, ConversationType.MANAGER_DRIVER))
                .thenReturn(Optional.of(existing));

        Conversation result = chatService.openConversation(driver, orderId, null, ConversationType.MANAGER_DRIVER);

        assertThat(result).isSameAs(existing);
    }

    @Test
    void openManagerDriverByOrder_noneExisting_createsNew() {
        UUID orderId = UUID.randomUUID();
        UUID driverId = UUID.randomUUID();
        User manager = user(UUID.randomUUID(), UserRole.MANAGER, "Quan ly A");
        ServiceOrder order = order(orderId, UUID.randomUUID(), driverId);
        when(orderRepository.findByIdAndDeletedAtIsNull(orderId)).thenReturn(Optional.of(order));
        when(conversationRepository.findByOrderIdAndType(orderId, ConversationType.MANAGER_DRIVER))
                .thenReturn(Optional.empty());
        when(conversationRepository.save(any(Conversation.class))).thenAnswer(inv -> inv.getArgument(0));

        Conversation result = chatService.openConversation(manager, orderId, null, ConversationType.MANAGER_DRIVER);

        assertThat(result.getOrderId()).isEqualTo(orderId);
        assertThat(result.getCustomerId()).isNull();
        assertThat(result.getDriverId()).isEqualTo(driverId);
        assertThat(result.getType()).isEqualTo(ConversationType.MANAGER_DRIVER);
    }

    // ===================== openManagerDriverGeneral =====================

    @Test
    void openManagerDriverGeneral_driverRole_usesOwnId_createsNew() {
        UUID driverId = UUID.randomUUID();
        User driver = user(driverId, UserRole.DRIVER, "Tai xe A");
        when(conversationRepository.findByDriverIdAndTypeAndOrderIdIsNull(driverId, ConversationType.MANAGER_DRIVER))
                .thenReturn(Optional.empty());
        when(conversationRepository.save(any(Conversation.class))).thenAnswer(inv -> inv.getArgument(0));

        Conversation result = chatService.openConversation(driver, null, UUID.randomUUID(), ConversationType.MANAGER_DRIVER);

        assertThat(result.getDriverId()).isEqualTo(driverId);
        assertThat(result.getOrderId()).isNull();
    }

    @Test
    void openManagerDriverGeneral_managerWithoutDriverId_throwsBadRequest() {
        User manager = user(UUID.randomUUID(), UserRole.MANAGER, "Quan ly A");

        assertThatThrownBy(() -> chatService.openConversation(manager, null, null, ConversationType.MANAGER_DRIVER))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("DRIVER_ID_REQUIRED")
                .hasMessageContaining("Vui lòng chọn tài xế để nhắn tin.");
    }

    @Test
    void openManagerDriverGeneral_managerWithUnknownDriverId_throwsNotFound() {
        UUID requestedId = UUID.randomUUID();
        User manager = user(UUID.randomUUID(), UserRole.MANAGER, "Quan ly A");
        when(userRepository.findById(requestedId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> chatService.openConversation(manager, null, requestedId, ConversationType.MANAGER_DRIVER))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("DRIVER_NOT_FOUND")
                .hasMessageContaining("Không tìm thấy tài xế.");
    }

    @Test
    void openManagerDriverGeneral_managerWithNonDriverUser_throwsNotFound() {
        UUID requestedId = UUID.randomUUID();
        User manager = user(UUID.randomUUID(), UserRole.MANAGER, "Quan ly A");
        User notADriver = user(requestedId, UserRole.CUSTOMER, "Khach nham");
        when(userRepository.findById(requestedId)).thenReturn(Optional.of(notADriver));

        assertThatThrownBy(() -> chatService.openConversation(manager, null, requestedId, ConversationType.MANAGER_DRIVER))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("DRIVER_NOT_FOUND");
    }

    @Test
    void openManagerDriverGeneral_adminWithValidDriverId_createsNew() {
        UUID requestedId = UUID.randomUUID();
        User admin = user(UUID.randomUUID(), UserRole.ADMIN, "Admin A");
        User driver = user(requestedId, UserRole.DRIVER, "Tai xe A");
        when(userRepository.findById(requestedId)).thenReturn(Optional.of(driver));
        when(conversationRepository.findByDriverIdAndTypeAndOrderIdIsNull(requestedId, ConversationType.MANAGER_DRIVER))
                .thenReturn(Optional.empty());
        when(conversationRepository.save(any(Conversation.class))).thenAnswer(inv -> inv.getArgument(0));

        Conversation result = chatService.openConversation(admin, null, requestedId, ConversationType.MANAGER_DRIVER);

        assertThat(result.getDriverId()).isEqualTo(requestedId);
    }

    @Test
    void openManagerDriverGeneral_customerRole_throwsForbidden() {
        User customer = user(UUID.randomUUID(), UserRole.CUSTOMER, "Khach A");

        assertThatThrownBy(() -> chatService.openConversation(customer, null, UUID.randomUUID(), ConversationType.MANAGER_DRIVER))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("FORBIDDEN")
                .hasMessageContaining("Bạn không có quyền mở hội thoại này.");
    }

    // ===================== createConversation race condition =====================

    @Test
    void createConversation_raceWithOrderId_returnsExistingAfterConflict() {
        UUID orderId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        UUID driverId = UUID.randomUUID();
        User driver = user(driverId, UserRole.DRIVER, "Tai xe A");
        ServiceOrder order = order(orderId, customerId, driverId);
        Conversation existingAfterRace = conv(UUID.randomUUID(), orderId, ConversationType.CUSTOMER_DRIVER, customerId, driverId);

        when(orderRepository.findByIdAndDeletedAtIsNull(orderId)).thenReturn(Optional.of(order));
        when(conversationRepository.findByOrderIdAndType(orderId, ConversationType.CUSTOMER_DRIVER))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(existingAfterRace));
        when(conversationRepository.save(any(Conversation.class)))
                .thenThrow(new DataIntegrityViolationException("unique violation"));

        Conversation result = chatService.openConversation(driver, orderId, null, ConversationType.CUSTOMER_DRIVER);

        assertThat(result).isSameAs(existingAfterRace);
    }

    @Test
    void createConversation_raceWithManagerDriverGeneral_returnsExistingAfterConflict() {
        UUID driverId = UUID.randomUUID();
        User driver = user(driverId, UserRole.DRIVER, "Tai xe A");
        Conversation existingAfterRace = conv(UUID.randomUUID(), null, ConversationType.MANAGER_DRIVER, null, driverId);

        when(conversationRepository.findByDriverIdAndTypeAndOrderIdIsNull(driverId, ConversationType.MANAGER_DRIVER))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(existingAfterRace));
        when(conversationRepository.save(any(Conversation.class)))
                .thenThrow(new DataIntegrityViolationException("unique violation"));

        Conversation result = chatService.openConversation(driver, null, null, ConversationType.MANAGER_DRIVER);

        assertThat(result).isSameAs(existingAfterRace);
    }

    @Test
    void createConversation_raceWithSupportChannel_returnsExistingAfterConflict() {
        UUID customerId = UUID.randomUUID();
        User customer = user(customerId, UserRole.CUSTOMER, "Khach A");
        Conversation existingAfterRace = conv(UUID.randomUUID(), null, ConversationType.CUSTOMER_MANAGER, customerId, null);

        when(conversationRepository.findByCustomerIdAndTypeAndOrderIdIsNull(customerId, ConversationType.CUSTOMER_MANAGER))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(existingAfterRace));
        when(conversationRepository.save(any(Conversation.class)))
                .thenThrow(new DataIntegrityViolationException("unique violation"));

        Conversation result = chatService.openConversation(customer, null, null, ConversationType.CUSTOMER_MANAGER);

        assertThat(result).isSameAs(existingAfterRace);
    }

    @Test
    void createConversation_raceWithNoExistingFound_rethrowsOriginal() {
        UUID customerId = UUID.randomUUID();
        User customer = user(customerId, UserRole.CUSTOMER, "Khach A");
        DataIntegrityViolationException raceException = new DataIntegrityViolationException("unique violation");

        when(conversationRepository.findByCustomerIdAndTypeAndOrderIdIsNull(customerId, ConversationType.CUSTOMER_MANAGER))
                .thenReturn(Optional.empty());
        when(conversationRepository.save(any(Conversation.class))).thenThrow(raceException);

        assertThatThrownBy(() -> chatService.openConversation(customer, null, null, ConversationType.CUSTOMER_MANAGER))
                .isSameAs(raceException);
    }

    // ===================== listActiveDrivers =====================

    @Test
    void listActiveDrivers_returnsSortedByFullName() {
        User driverB = user(UUID.randomUUID(), UserRole.DRIVER, "Nguyen Van B");
        driverB.setPhone("+84900000002");
        User driverA = user(UUID.randomUUID(), UserRole.DRIVER, "Anh Van A");
        driverA.setPhone("+84900000001");
        when(userRepository.findByRoleAndStatusAndDeletedAtIsNull(UserRole.DRIVER, UserStatus.ACTIVE))
                .thenReturn(List.of(driverB, driverA));

        List<DriverDirectoryItem> result = chatService.listActiveDrivers();

        assertThat(result).hasSize(2);
        assertThat(result.get(0).fullName()).isEqualTo("Anh Van A");
        assertThat(result.get(1).fullName()).isEqualTo("Nguyen Van B");
        assertThat(result.get(0).phone()).isEqualTo("+84900000001");
    }

    @Test
    void listActiveDrivers_nullFullName_sortsLast() {
        User driverWithName = user(UUID.randomUUID(), UserRole.DRIVER, "Co Ten");
        User driverNoName = User.builder()
                .id(UUID.randomUUID())
                .role(UserRole.DRIVER)
                .fullName(null)
                .phone("+84900000003")
                .build();
        when(userRepository.findByRoleAndStatusAndDeletedAtIsNull(UserRole.DRIVER, UserStatus.ACTIVE))
                .thenReturn(List.of(driverNoName, driverWithName));

        List<DriverDirectoryItem> result = chatService.listActiveDrivers();

        assertThat(result).hasSize(2);
        assertThat(result.get(0).fullName()).isEqualTo("Co Ten");
        assertThat(result.get(1).fullName()).isNull();
    }

    // ===================== listConversations =====================

    @Test
    void listConversations_customerRole_sortsAndMapsCorrectly() {
        UUID customerId = UUID.randomUUID();
        UUID driverId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        User customer = user(customerId, UserRole.CUSTOMER, "Khach A");

        Conversation supportConv = conv(UUID.randomUUID(), null, ConversationType.CUSTOMER_MANAGER, customerId, null);
        supportConv.setLastMessageAt(null);
        Conversation driverConv = conv(UUID.randomUUID(), orderId, ConversationType.CUSTOMER_DRIVER, customerId, driverId);
        driverConv.setLastMessageAt(OffsetDateTime.now(ZoneOffset.UTC));
        driverConv.setLastMessageText("Xin chao");

        when(conversationRepository.findByCustomerId(customerId)).thenReturn(new ArrayList<>(List.of(supportConv, driverConv)));
        User driverUser = user(driverId, UserRole.DRIVER, "Tai Xe A");
        when(userRepository.findAllById(anySet())).thenReturn(List.of(driverUser));
        ServiceOrder ord = order(orderId, customerId, driverId);
        when(orderRepository.findAllById(anySet())).thenReturn(List.of(ord));
        when(messageRepository.countByConversationIdAndSenderIdNotAndReadAtIsNull(supportConv.getId(), customerId))
                .thenReturn(0L);
        when(messageRepository.countByConversationIdAndSenderIdNotAndReadAtIsNull(driverConv.getId(), customerId))
                .thenReturn(3L);

        List<ConversationResponse> result = chatService.listConversations(customer);

        assertThat(result).hasSize(2);
        // Non-null lastMessageAt truoc, null xuong duoi
        assertThat(result.get(0).id()).isEqualTo(driverConv.getId());
        assertThat(result.get(0).counterpartName()).isEqualTo("Tai Xe A");
        assertThat(result.get(0).orderCode()).isEqualTo(ord.getOrderCode());
        assertThat(result.get(0).unreadCount()).isEqualTo(3L);
        assertThat(result.get(1).id()).isEqualTo(supportConv.getId());
        assertThat(result.get(1).counterpartName()).isEqualTo("Quản lý Move_home");
        assertThat(result.get(1).orderCode()).isNull();
        assertThat(result.get(1).unreadCount()).isEqualTo(0L);
    }

    @Test
    void listConversations_driverRole_mapsManagerAndCustomerCounterparts() {
        UUID driverId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        User driver = user(driverId, UserRole.DRIVER, "Tai Xe A");

        Conversation managerConv = conv(UUID.randomUUID(), null, ConversationType.MANAGER_DRIVER, null, driverId);
        Conversation custDriverConv = conv(UUID.randomUUID(), UUID.randomUUID(), ConversationType.CUSTOMER_DRIVER, customerId, driverId);

        when(conversationRepository.findByDriverId(driverId)).thenReturn(new ArrayList<>(List.of(managerConv, custDriverConv)));
        User customerUser = user(customerId, UserRole.CUSTOMER, "Khach B");
        when(userRepository.findAllById(anySet())).thenReturn(List.of(customerUser));
        when(orderRepository.findAllById(anySet())).thenReturn(List.of());
        when(messageRepository.countByConversationIdAndSenderIdNotAndReadAtIsNull(any(), eq(driverId))).thenReturn(0L);

        List<ConversationResponse> result = chatService.listConversations(driver);

        assertThat(result).hasSize(2);
        ConversationResponse managerResp = result.stream()
                .filter(r -> r.id().equals(managerConv.getId())).findFirst().orElseThrow();
        assertThat(managerResp.counterpartName()).isEqualTo("Quản lý Move_home");
        ConversationResponse custResp = result.stream()
                .filter(r -> r.id().equals(custDriverConv.getId())).findFirst().orElseThrow();
        assertThat(custResp.counterpartName()).isEqualTo("Khach B");
    }

    @Test
    void listConversations_managerRole_mapsCustomerAndDriverCounterparts_withFallbacksAndDefault() {
        UUID managerId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        UUID driverId = UUID.randomUUID();
        User manager = user(managerId, UserRole.MANAGER, "Quan ly A");

        Conversation custManagerConv = conv(UUID.randomUUID(), null, ConversationType.CUSTOMER_MANAGER, customerId, null);
        Conversation managerDriverConv = conv(UUID.randomUUID(), null, ConversationType.MANAGER_DRIVER, null, driverId);
        // Conversation voi customerId null -> nameOr fallback "Khách hàng"
        Conversation custManagerNullCustomer = conv(UUID.randomUUID(), null, ConversationType.CUSTOMER_MANAGER, null, null);
        // Conversation voi type khong xac dinh -> default "Hội thoại"
        Conversation unknownTypeConv = conv(UUID.randomUUID(), null, "UNKNOWN_TYPE", customerId, driverId);

        when(conversationRepository.findByTypeIn(List.of(ConversationType.CUSTOMER_MANAGER, ConversationType.MANAGER_DRIVER)))
                .thenReturn(new ArrayList<>(List.of(custManagerConv, managerDriverConv, custManagerNullCustomer, unknownTypeConv)));
        User customerUser = User.builder().id(customerId).role(UserRole.CUSTOMER).fullName("   ").phone("+8490").build();
        User driverUser = user(driverId, UserRole.DRIVER, "Tai Xe C");
        when(userRepository.findAllById(anySet())).thenReturn(List.of(customerUser, driverUser));
        when(messageRepository.countByConversationIdAndSenderIdNotAndReadAtIsNull(any(), eq(managerId))).thenReturn(0L);

        List<ConversationResponse> result = chatService.listConversations(manager);

        assertThat(result).hasSize(4);
        ConversationResponse custManagerResp = result.stream()
                .filter(r -> r.id().equals(custManagerConv.getId())).findFirst().orElseThrow();
        // Ten khach blank -> fallback "Khách hàng"
        assertThat(custManagerResp.counterpartName()).isEqualTo("Khách hàng");

        ConversationResponse managerDriverResp = result.stream()
                .filter(r -> r.id().equals(managerDriverConv.getId())).findFirst().orElseThrow();
        assertThat(managerDriverResp.counterpartName()).isEqualTo("Tai Xe C");

        ConversationResponse nullCustomerResp = result.stream()
                .filter(r -> r.id().equals(custManagerNullCustomer.getId())).findFirst().orElseThrow();
        assertThat(nullCustomerResp.counterpartName()).isEqualTo("Khách hàng");

        ConversationResponse unknownResp = result.stream()
                .filter(r -> r.id().equals(unknownTypeConv.getId())).findFirst().orElseThrow();
        assertThat(unknownResp.counterpartName()).isEqualTo("Hội thoại");
    }

    @Test
    void listConversations_adminRole_usesSameTypeInQuery() {
        UUID adminId = UUID.randomUUID();
        User admin = user(adminId, UserRole.ADMIN, "Admin A");
        when(conversationRepository.findByTypeIn(List.of(ConversationType.CUSTOMER_MANAGER, ConversationType.MANAGER_DRIVER)))
                .thenReturn(new ArrayList<>());

        List<ConversationResponse> result = chatService.listConversations(admin);

        assertThat(result).isEmpty();
    }

    @Test
    void listConversations_emptyIdCaches_returnEmptyMaps() {
        UUID customerId = UUID.randomUUID();
        User customer = user(customerId, UserRole.CUSTOMER, "Khach A");
        // Hoi thoai khong co orderId va khong co customerId/driverId khac -> caches rong
        Conversation onlyCustomerConv = conv(UUID.randomUUID(), null, ConversationType.CUSTOMER_MANAGER, customerId, null);

        when(conversationRepository.findByCustomerId(customerId)).thenReturn(new ArrayList<>(List.of(onlyCustomerConv)));
        when(userRepository.findAllById(anySet())).thenReturn(List.of());
        when(messageRepository.countByConversationIdAndSenderIdNotAndReadAtIsNull(any(), eq(customerId))).thenReturn(0L);

        List<ConversationResponse> result = chatService.listConversations(customer);

        assertThat(result).hasSize(1);
        // orderRepository.findAllById khong duoc goi vi khong co orderId nao
        verify(orderRepository, never()).findAllById(any());
        assertThat(result.get(0).counterpartName()).isEqualTo("Quản lý Move_home");
    }

    // ===================== getMessages =====================

    @Test
    void getMessages_conversationNotFound_throwsNotFound() {
        UUID convId = UUID.randomUUID();
        User customer = user(UUID.randomUUID(), UserRole.CUSTOMER, "Khach A");
        when(conversationRepository.findById(convId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> chatService.getMessages(customer, convId, PageRequest.of(0, 20)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("CONVERSATION_NOT_FOUND")
                .hasMessageContaining("Không tìm thấy hội thoại.");
    }

    @Test
    void getMessages_notParticipant_throwsForbidden() {
        UUID convId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        User stranger = user(UUID.randomUUID(), UserRole.CUSTOMER, "Khach la");
        Conversation conversation = conv(convId, null, ConversationType.CUSTOMER_MANAGER, customerId, null);
        when(conversationRepository.findById(convId)).thenReturn(Optional.of(conversation));

        assertThatThrownBy(() -> chatService.getMessages(stranger, convId, PageRequest.of(0, 20)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("FORBIDDEN")
                .hasMessageContaining("Bạn không có quyền truy cập hội thoại này.");
    }

    @Test
    void getMessages_success_mapsMineFlagAndImageUrl() {
        UUID convId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        UUID driverId = UUID.randomUUID();
        User customer = user(customerId, UserRole.CUSTOMER, "Khach A");
        Conversation conversation = conv(convId, UUID.randomUUID(), ConversationType.CUSTOMER_DRIVER, customerId, driverId);
        when(conversationRepository.findById(convId)).thenReturn(Optional.of(conversation));

        ChatMessage myMessage = ChatMessage.builder()
                .id(UUID.randomUUID())
                .conversationId(convId)
                .senderId(customerId)
                .content("Xin chao")
                .createdAt(OffsetDateTime.now(ZoneOffset.UTC))
                .build();
        ChatMessage otherMessage = ChatMessage.builder()
                .id(UUID.randomUUID())
                .conversationId(convId)
                .senderId(driverId)
                .content("")
                .imagePublicId("pub123")
                .createdAt(OffsetDateTime.now(ZoneOffset.UTC))
                .build();
        Pageable pageable = PageRequest.of(0, 20);
        Page<ChatMessage> page = new PageImpl<>(List.of(myMessage, otherMessage), pageable, 2);
        when(messageRepository.findByConversationIdOrderByCreatedAtDesc(convId, pageable)).thenReturn(page);
        when(userRepository.findAllById(anySet()))
                .thenReturn(List.of(user(customerId, UserRole.CUSTOMER, "Khach A"), user(driverId, UserRole.DRIVER, "Tai Xe A")));
        when(chatImageService.signUrl(null)).thenReturn(null);
        when(chatImageService.signUrl("pub123")).thenReturn("https://img/pub123");

        Page<ChatMessageResponse> result = chatService.getMessages(customer, convId, pageable);

        assertThat(result.getContent()).hasSize(2);
        ChatMessageResponse mine = result.getContent().stream()
                .filter(m -> m.senderId().equals(customerId)).findFirst().orElseThrow();
        assertThat(mine.mine()).isTrue();
        assertThat(mine.imageUrl()).isNull();
        ChatMessageResponse other = result.getContent().stream()
                .filter(m -> m.senderId().equals(driverId)).findFirst().orElseThrow();
        assertThat(other.mine()).isFalse();
        assertThat(other.imageUrl()).isEqualTo("https://img/pub123");

        ArgumentCaptor<OffsetDateTime> timeCaptor = ArgumentCaptor.forClass(OffsetDateTime.class);
        verify(messageRepository, times(1)).markConversationRead(eq(convId), eq(customerId), timeCaptor.capture());
        assertThat(timeCaptor.getValue()).isNotNull();
    }

    // ===================== sendMessage =====================

    @Test
    void sendMessage_conversationNotFound_throwsNotFound() {
        UUID convId = UUID.randomUUID();
        User customer = user(UUID.randomUUID(), UserRole.CUSTOMER, "Khach A");
        when(conversationRepository.findById(convId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> chatService.sendMessage(customer, convId, "Hello"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("CONVERSATION_NOT_FOUND");
    }

    @Test
    void sendMessage_notParticipant_throwsForbidden() {
        UUID convId = UUID.randomUUID();
        User stranger = user(UUID.randomUUID(), UserRole.DRIVER, "Tai xe la");
        Conversation conversation = conv(convId, UUID.randomUUID(), ConversationType.CUSTOMER_DRIVER, UUID.randomUUID(), UUID.randomUUID());
        when(conversationRepository.findById(convId)).thenReturn(Optional.of(conversation));

        assertThatThrownBy(() -> chatService.sendMessage(stranger, convId, "Hello"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("FORBIDDEN")
                .hasMessageContaining("Bạn không có quyền truy cập hội thoại này.");
    }

    @Test
    void sendMessage_emptyContent_throwsBadRequest() {
        UUID convId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        User customer = user(customerId, UserRole.CUSTOMER, "Khach A");
        Conversation conversation = conv(convId, null, ConversationType.CUSTOMER_MANAGER, customerId, null);
        when(conversationRepository.findById(convId)).thenReturn(Optional.of(conversation));

        assertThatThrownBy(() -> chatService.sendMessage(customer, convId, "   "))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("EMPTY_MESSAGE")
                .hasMessageContaining("Nội dung tin nhắn không được để trống.");
        verify(messageRepository, never()).save(any());
    }

    @Test
    void sendMessage_nullContent_throwsBadRequest() {
        UUID convId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        User customer = user(customerId, UserRole.CUSTOMER, "Khach A");
        Conversation conversation = conv(convId, null, ConversationType.CUSTOMER_MANAGER, customerId, null);
        when(conversationRepository.findById(convId)).thenReturn(Optional.of(conversation));

        assertThatThrownBy(() -> chatService.sendMessage(customer, convId, null))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("EMPTY_MESSAGE");
    }

    @Test
    void sendMessage_success_shortContent_customerToDriver_recipientIsDriver() {
        UUID convId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        UUID driverId = UUID.randomUUID();
        User customer = user(customerId, UserRole.CUSTOMER, "Khach A");
        Conversation conversation = conv(convId, UUID.randomUUID(), ConversationType.CUSTOMER_DRIVER, customerId, driverId);
        when(conversationRepository.findById(convId)).thenReturn(Optional.of(conversation));
        when(messageRepository.save(any(ChatMessage.class))).thenAnswer(inv -> {
            ChatMessage m = inv.getArgument(0);
            m.setId(UUID.randomUUID());
            return m;
        });

        ChatMessageResponse response = chatService.sendMessage(customer, convId, "  Xin chao  ");

        assertThat(response.content()).isEqualTo("Xin chao");
        assertThat(response.mine()).isTrue();
        assertThat(conversation.getLastMessageText()).isEqualTo("Xin chao");
        assertThat(conversation.getLastMessageAt()).isNotNull();
        verify(conversationRepository, times(1)).save(conversation);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Set<UUID>> recipientsCaptor = ArgumentCaptor.forClass(Set.class);
        verify(realtimePublisher, times(1)).publishNewMessage(recipientsCaptor.capture(), any());
        assertThat(recipientsCaptor.getValue()).containsExactly(driverId);
    }

    @Test
    void sendMessage_longContent_truncatesPreview() {
        UUID convId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        User customer = user(customerId, UserRole.CUSTOMER, "Khach A");
        Conversation conversation = conv(convId, null, ConversationType.CUSTOMER_MANAGER, customerId, null);
        when(conversationRepository.findById(convId)).thenReturn(Optional.of(conversation));
        when(messageRepository.save(any(ChatMessage.class))).thenAnswer(inv -> {
            ChatMessage m = inv.getArgument(0);
            m.setId(UUID.randomUUID());
            return m;
        });
        String longContent = "a".repeat(250);

        ChatMessageResponse response = chatService.sendMessage(customer, convId, longContent);

        assertThat(response.content()).hasSize(250);
        assertThat(conversation.getLastMessageText()).hasSize(200);
        assertThat(conversation.getLastMessageText()).isEqualTo("a".repeat(200));
    }

    @Test
    void sendMessage_driverToCustomer_recipientIsCustomer() {
        UUID convId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        UUID driverId = UUID.randomUUID();
        User driver = user(driverId, UserRole.DRIVER, "Tai xe A");
        Conversation conversation = conv(convId, UUID.randomUUID(), ConversationType.CUSTOMER_DRIVER, customerId, driverId);
        when(conversationRepository.findById(convId)).thenReturn(Optional.of(conversation));
        when(messageRepository.save(any(ChatMessage.class))).thenAnswer(inv -> {
            ChatMessage m = inv.getArgument(0);
            m.setId(UUID.randomUUID());
            return m;
        });

        chatService.sendMessage(driver, convId, "Chao khach");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Set<UUID>> recipientsCaptor = ArgumentCaptor.forClass(Set.class);
        verify(realtimePublisher, times(1)).publishNewMessage(recipientsCaptor.capture(), any());
        assertThat(recipientsCaptor.getValue()).containsExactly(customerId);
    }

    @Test
    void sendMessage_customerDriver_otherNull_recipientsEmpty() {
        UUID convId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        User customer = user(customerId, UserRole.CUSTOMER, "Khach A");
        // driverId null (truong hop du lieu khong chuan) -> "other" tinh ra null
        Conversation conversation = conv(convId, UUID.randomUUID(), ConversationType.CUSTOMER_DRIVER, customerId, null);
        when(conversationRepository.findById(convId)).thenReturn(Optional.of(conversation));
        when(messageRepository.save(any(ChatMessage.class))).thenAnswer(inv -> {
            ChatMessage m = inv.getArgument(0);
            m.setId(UUID.randomUUID());
            return m;
        });

        chatService.sendMessage(customer, convId, "Hello");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Set<UUID>> recipientsCaptor = ArgumentCaptor.forClass(Set.class);
        verify(realtimePublisher, times(1)).publishNewMessage(recipientsCaptor.capture(), any());
        assertThat(recipientsCaptor.getValue()).isEmpty();
    }

    @Test
    void sendMessage_managerDriver_driverSends_recipientsAreActiveManagers() {
        UUID convId = UUID.randomUUID();
        UUID driverId = UUID.randomUUID();
        UUID manager1Id = UUID.randomUUID();
        UUID manager2Id = UUID.randomUUID();
        User driver = user(driverId, UserRole.DRIVER, "Tai xe A");
        Conversation conversation = conv(convId, null, ConversationType.MANAGER_DRIVER, null, driverId);
        when(conversationRepository.findById(convId)).thenReturn(Optional.of(conversation));
        when(messageRepository.save(any(ChatMessage.class))).thenAnswer(inv -> {
            ChatMessage m = inv.getArgument(0);
            m.setId(UUID.randomUUID());
            return m;
        });
        when(userRepository.findByRoleAndStatusAndDeletedAtIsNull(UserRole.MANAGER, UserStatus.ACTIVE))
                .thenReturn(List.of(
                        user(manager1Id, UserRole.MANAGER, "Quan ly 1"),
                        user(manager2Id, UserRole.MANAGER, "Quan ly 2")));

        chatService.sendMessage(driver, convId, "Bao cao");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Set<UUID>> recipientsCaptor = ArgumentCaptor.forClass(Set.class);
        verify(realtimePublisher, times(1)).publishNewMessage(recipientsCaptor.capture(), any());
        assertThat(recipientsCaptor.getValue()).containsExactlyInAnyOrder(manager1Id, manager2Id);
    }

    @Test
    void sendMessage_managerDriver_managerSends_recipientIsDriver() {
        UUID convId = UUID.randomUUID();
        UUID driverId = UUID.randomUUID();
        User manager = user(UUID.randomUUID(), UserRole.MANAGER, "Quan ly A");
        Conversation conversation = conv(convId, null, ConversationType.MANAGER_DRIVER, null, driverId);
        when(conversationRepository.findById(convId)).thenReturn(Optional.of(conversation));
        when(messageRepository.save(any(ChatMessage.class))).thenAnswer(inv -> {
            ChatMessage m = inv.getArgument(0);
            m.setId(UUID.randomUUID());
            return m;
        });

        chatService.sendMessage(manager, convId, "Phan hoi");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Set<UUID>> recipientsCaptor = ArgumentCaptor.forClass(Set.class);
        verify(realtimePublisher, times(1)).publishNewMessage(recipientsCaptor.capture(), any());
        assertThat(recipientsCaptor.getValue()).containsExactly(driverId);
        verify(userRepository, never()).findByRoleAndStatusAndDeletedAtIsNull(eq(UserRole.MANAGER), any());
    }

    @Test
    void sendMessage_managerDriver_managerSends_driverIdNull_recipientsEmpty() {
        UUID convId = UUID.randomUUID();
        User manager = user(UUID.randomUUID(), UserRole.MANAGER, "Quan ly A");
        Conversation conversation = conv(convId, null, ConversationType.MANAGER_DRIVER, null, null);
        when(conversationRepository.findById(convId)).thenReturn(Optional.of(conversation));
        when(messageRepository.save(any(ChatMessage.class))).thenAnswer(inv -> {
            ChatMessage m = inv.getArgument(0);
            m.setId(UUID.randomUUID());
            return m;
        });

        chatService.sendMessage(manager, convId, "Phan hoi");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Set<UUID>> recipientsCaptor = ArgumentCaptor.forClass(Set.class);
        verify(realtimePublisher, times(1)).publishNewMessage(recipientsCaptor.capture(), any());
        assertThat(recipientsCaptor.getValue()).isEmpty();
    }

    @Test
    void sendMessage_customerManager_customerSends_recipientsAreActiveManagers() {
        UUID convId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        UUID manager1Id = UUID.randomUUID();
        User customer = user(customerId, UserRole.CUSTOMER, "Khach A");
        Conversation conversation = conv(convId, null, ConversationType.CUSTOMER_MANAGER, customerId, null);
        when(conversationRepository.findById(convId)).thenReturn(Optional.of(conversation));
        when(messageRepository.save(any(ChatMessage.class))).thenAnswer(inv -> {
            ChatMessage m = inv.getArgument(0);
            m.setId(UUID.randomUUID());
            return m;
        });
        when(userRepository.findByRoleAndStatusAndDeletedAtIsNull(UserRole.MANAGER, UserStatus.ACTIVE))
                .thenReturn(List.of(user(manager1Id, UserRole.MANAGER, "Quan ly 1")));

        chatService.sendMessage(customer, convId, "Can ho tro");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Set<UUID>> recipientsCaptor = ArgumentCaptor.forClass(Set.class);
        verify(realtimePublisher, times(1)).publishNewMessage(recipientsCaptor.capture(), any());
        assertThat(recipientsCaptor.getValue()).containsExactly(manager1Id);
    }

    @Test
    void sendMessage_customerManager_managerSends_recipientIsCustomer() {
        UUID convId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        User manager = user(UUID.randomUUID(), UserRole.MANAGER, "Quan ly A");
        Conversation conversation = conv(convId, null, ConversationType.CUSTOMER_MANAGER, customerId, null);
        when(conversationRepository.findById(convId)).thenReturn(Optional.of(conversation));
        when(messageRepository.save(any(ChatMessage.class))).thenAnswer(inv -> {
            ChatMessage m = inv.getArgument(0);
            m.setId(UUID.randomUUID());
            return m;
        });

        chatService.sendMessage(manager, convId, "Xin chao");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Set<UUID>> recipientsCaptor = ArgumentCaptor.forClass(Set.class);
        verify(realtimePublisher, times(1)).publishNewMessage(recipientsCaptor.capture(), any());
        assertThat(recipientsCaptor.getValue()).containsExactly(customerId);
    }

    @Test
    void sendMessage_customerManager_managerSends_customerIdNull_recipientsEmpty() {
        UUID convId = UUID.randomUUID();
        User manager = user(UUID.randomUUID(), UserRole.MANAGER, "Quan ly A");
        Conversation conversation = conv(convId, null, ConversationType.CUSTOMER_MANAGER, null, null);
        when(conversationRepository.findById(convId)).thenReturn(Optional.of(conversation));
        when(messageRepository.save(any(ChatMessage.class))).thenAnswer(inv -> {
            ChatMessage m = inv.getArgument(0);
            m.setId(UUID.randomUUID());
            return m;
        });

        chatService.sendMessage(manager, convId, "Xin chao");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Set<UUID>> recipientsCaptor = ArgumentCaptor.forClass(Set.class);
        verify(realtimePublisher, times(1)).publishNewMessage(recipientsCaptor.capture(), any());
        assertThat(recipientsCaptor.getValue()).isEmpty();
    }

    @Test
    void sendMessage_unknownConversationType_recipientsEmpty() {
        UUID convId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        User customer = user(customerId, UserRole.CUSTOMER, "Khach A");
        Conversation conversation = conv(convId, null, "UNKNOWN_TYPE", customerId, null);
        when(conversationRepository.findById(convId)).thenReturn(Optional.of(conversation));
        when(messageRepository.save(any(ChatMessage.class))).thenAnswer(inv -> {
            ChatMessage m = inv.getArgument(0);
            m.setId(UUID.randomUUID());
            return m;
        });

        // assertParticipant: role CUSTOMER -> allowed neu me.id == conv.customerId (dung)
        chatService.sendMessage(customer, convId, "Hello");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Set<UUID>> recipientsCaptor = ArgumentCaptor.forClass(Set.class);
        verify(realtimePublisher, times(1)).publishNewMessage(recipientsCaptor.capture(), any());
        assertThat(recipientsCaptor.getValue()).isEmpty();
    }

    // ===================== sendImage =====================

    @Test
    void sendImage_conversationNotFound_throwsNotFound() {
        UUID convId = UUID.randomUUID();
        User customer = user(UUID.randomUUID(), UserRole.CUSTOMER, "Khach A");
        MultipartFile file = new MockMultipartFile("file", "a.png", "image/png", new byte[] {1, 2, 3});
        when(conversationRepository.findById(convId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> chatService.sendImage(customer, convId, file))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("CONVERSATION_NOT_FOUND");
    }

    @Test
    void sendImage_notParticipant_throwsForbidden() {
        UUID convId = UUID.randomUUID();
        User stranger = user(UUID.randomUUID(), UserRole.CUSTOMER, "Khach la");
        Conversation conversation = conv(convId, null, ConversationType.CUSTOMER_MANAGER, UUID.randomUUID(), null);
        MultipartFile file = new MockMultipartFile("file", "a.png", "image/png", new byte[] {1, 2, 3});
        when(conversationRepository.findById(convId)).thenReturn(Optional.of(conversation));

        assertThatThrownBy(() -> chatService.sendImage(stranger, convId, file))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("FORBIDDEN");
        verify(chatImageService, never()).upload(any(), any());
    }

    @Test
    void sendImage_success_updatesConversationPreviewAndPublishes() {
        UUID convId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        UUID driverId = UUID.randomUUID();
        User customer = user(customerId, UserRole.CUSTOMER, "Khach A");
        Conversation conversation = conv(convId, UUID.randomUUID(), ConversationType.CUSTOMER_DRIVER, customerId, driverId);
        MultipartFile file = new MockMultipartFile("file", "a.png", "image/png", new byte[] {1, 2, 3});
        when(conversationRepository.findById(convId)).thenReturn(Optional.of(conversation));
        when(chatImageService.upload(convId, file)).thenReturn("pub456");
        when(chatImageService.signUrl("pub456")).thenReturn("https://img/pub456");
        when(messageRepository.save(any(ChatMessage.class))).thenAnswer(inv -> {
            ChatMessage m = inv.getArgument(0);
            m.setId(UUID.randomUUID());
            return m;
        });

        ChatMessageResponse response = chatService.sendImage(customer, convId, file);

        assertThat(response.imageUrl()).isEqualTo("https://img/pub456");
        assertThat(response.mine()).isTrue();
        assertThat(conversation.getLastMessageText()).isEqualTo("🖼 Hình ảnh");
        assertThat(conversation.getLastMessageAt()).isNotNull();
        verify(conversationRepository, times(1)).save(conversation);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Set<UUID>> recipientsCaptor = ArgumentCaptor.forClass(Set.class);
        verify(realtimePublisher, times(1)).publishNewMessage(recipientsCaptor.capture(), any());
        assertThat(recipientsCaptor.getValue()).containsExactly(driverId);
    }

    // ===================== markRead =====================

    @Test
    void markRead_conversationNotFound_throwsNotFound() {
        UUID convId = UUID.randomUUID();
        User customer = user(UUID.randomUUID(), UserRole.CUSTOMER, "Khach A");
        when(conversationRepository.findById(convId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> chatService.markRead(customer, convId))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("CONVERSATION_NOT_FOUND");
    }

    @Test
    void markRead_notParticipant_throwsForbidden() {
        UUID convId = UUID.randomUUID();
        User stranger = user(UUID.randomUUID(), UserRole.DRIVER, "Tai xe la");
        Conversation conversation = conv(convId, null, ConversationType.CUSTOMER_MANAGER, UUID.randomUUID(), null);
        when(conversationRepository.findById(convId)).thenReturn(Optional.of(conversation));

        assertThatThrownBy(() -> chatService.markRead(stranger, convId))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("FORBIDDEN");
        verify(messageRepository, never()).markConversationRead(any(), any(), any());
    }

    @Test
    void markRead_success_callsRepository() {
        UUID convId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        User customer = user(customerId, UserRole.CUSTOMER, "Khach A");
        Conversation conversation = conv(convId, null, ConversationType.CUSTOMER_MANAGER, customerId, null);
        when(conversationRepository.findById(convId)).thenReturn(Optional.of(conversation));

        chatService.markRead(customer, convId);

        verify(messageRepository, times(1)).markConversationRead(eq(convId), eq(customerId), any());
    }

    // ===================== unreadCount =====================

    @Test
    void unreadCount_noConversations_returnsZeroWithoutQuery() {
        UUID customerId = UUID.randomUUID();
        User customer = user(customerId, UserRole.CUSTOMER, "Khach A");
        when(conversationRepository.findIdsByCustomerId(customerId)).thenReturn(List.of());

        long result = chatService.unreadCount(customer);

        assertThat(result).isZero();
        verify(messageRepository, never()).countUnreadInConversations(any(), any());
    }

    @Test
    void unreadCount_withConversations_returnsRepositoryValue() {
        UUID customerId = UUID.randomUUID();
        UUID convId = UUID.randomUUID();
        User customer = user(customerId, UserRole.CUSTOMER, "Khach A");
        when(conversationRepository.findIdsByCustomerId(customerId)).thenReturn(List.of(convId));
        when(messageRepository.countUnreadInConversations(List.of(convId), customerId)).thenReturn(5L);

        long result = chatService.unreadCount(customer);

        assertThat(result).isEqualTo(5L);
    }

    @Test
    void unreadCount_driverRole_usesDriverIdsQuery() {
        UUID driverId = UUID.randomUUID();
        UUID convId = UUID.randomUUID();
        User driver = user(driverId, UserRole.DRIVER, "Tai xe A");
        when(conversationRepository.findIdsByDriverId(driverId)).thenReturn(List.of(convId));
        when(messageRepository.countUnreadInConversations(List.of(convId), driverId)).thenReturn(2L);

        long result = chatService.unreadCount(driver);

        assertThat(result).isEqualTo(2L);
    }

    @Test
    void unreadCount_managerRole_usesTypeInQuery() {
        UUID managerId = UUID.randomUUID();
        UUID convId = UUID.randomUUID();
        User manager = user(managerId, UserRole.MANAGER, "Quan ly A");
        when(conversationRepository.findIdsByTypeIn(List.of(ConversationType.CUSTOMER_MANAGER, ConversationType.MANAGER_DRIVER)))
                .thenReturn(List.of(convId));
        when(messageRepository.countUnreadInConversations(List.of(convId), managerId)).thenReturn(1L);

        long result = chatService.unreadCount(manager);

        assertThat(result).isEqualTo(1L);
    }

    // ===================== assertParticipant (via getMessages) role branches =====================

    @Test
    void assertParticipant_managerRole_allowedForCustomerManagerType() {
        UUID convId = UUID.randomUUID();
        User manager = user(UUID.randomUUID(), UserRole.MANAGER, "Quan ly A");
        Conversation conversation = conv(convId, null, ConversationType.CUSTOMER_MANAGER, UUID.randomUUID(), null);
        when(conversationRepository.findById(convId)).thenReturn(Optional.of(conversation));
        when(messageRepository.findByConversationIdOrderByCreatedAtDesc(eq(convId), any()))
                .thenReturn(new PageImpl<>(List.of()));

        Page<ChatMessageResponse> result = chatService.getMessages(manager, convId, PageRequest.of(0, 20));

        assertThat(result.getContent()).isEmpty();
    }

    @Test
    void assertParticipant_managerRole_notAllowedForCustomerDriverType() {
        UUID convId = UUID.randomUUID();
        User manager = user(UUID.randomUUID(), UserRole.MANAGER, "Quan ly A");
        Conversation conversation = conv(convId, UUID.randomUUID(), ConversationType.CUSTOMER_DRIVER, UUID.randomUUID(), UUID.randomUUID());
        when(conversationRepository.findById(convId)).thenReturn(Optional.of(conversation));

        assertThatThrownBy(() -> chatService.getMessages(manager, convId, PageRequest.of(0, 20)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("FORBIDDEN");
    }

    @Test
    void assertParticipant_driverRole_allowedWhenMatchesDriverId() {
        UUID convId = UUID.randomUUID();
        UUID driverId = UUID.randomUUID();
        User driver = user(driverId, UserRole.DRIVER, "Tai xe A");
        Conversation conversation = conv(convId, null, ConversationType.MANAGER_DRIVER, null, driverId);
        when(conversationRepository.findById(convId)).thenReturn(Optional.of(conversation));
        when(messageRepository.findByConversationIdOrderByCreatedAtDesc(eq(convId), any()))
                .thenReturn(new PageImpl<>(List.of()));

        Page<ChatMessageResponse> result = chatService.getMessages(driver, convId, PageRequest.of(0, 20));

        assertThat(result.getContent()).isEmpty();
    }

    @Test
    void assertParticipant_driverRole_notAllowedWhenDriverIdMismatch() {
        UUID convId = UUID.randomUUID();
        User driver = user(UUID.randomUUID(), UserRole.DRIVER, "Tai xe A");
        Conversation conversation = conv(convId, null, ConversationType.MANAGER_DRIVER, null, UUID.randomUUID());
        when(conversationRepository.findById(convId)).thenReturn(Optional.of(conversation));

        assertThatThrownBy(() -> chatService.getMessages(driver, convId, PageRequest.of(0, 20)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("FORBIDDEN");
    }

    // ===================== Helpers =====================

    // ===================== fetchNames / buildOrderCodeCache: merge function khi trung key =====================
    // Collectors.toMap merge (a, b) -> a chi chay khi userRepository.findAllById tra ve 2 ban ghi trung id
    // (truong hop du phong; khong xay ra trong flow that vi id la khoa chinh, nhung ta gia lap qua mock
    // de phu nhanh merge cua Collectors.toMap).

    @Test
    void listConversations_duplicateUserIdsFromRepository_mergeFunctionKeepsFirst() {
        UUID customerId = UUID.randomUUID();
        UUID driverId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        User customer = user(customerId, UserRole.CUSTOMER, "Khach A");

        Conversation driverConv = conv(UUID.randomUUID(), orderId, ConversationType.CUSTOMER_DRIVER, customerId, driverId);
        when(conversationRepository.findByCustomerId(customerId)).thenReturn(new ArrayList<>(List.of(driverConv)));

        User driverUser1 = user(driverId, UserRole.DRIVER, "Tai Xe A");
        User driverUser2 = user(driverId, UserRole.DRIVER, "Tai Xe A Trung Lap");
        when(userRepository.findAllById(anySet())).thenReturn(List.of(driverUser1, driverUser2));

        ServiceOrder ord1 = order(orderId, customerId, driverId);
        ServiceOrder ord2Duplicate = order(orderId, customerId, driverId);
        when(orderRepository.findAllById(anySet())).thenReturn(List.of(ord1, ord2Duplicate));

        when(messageRepository.countByConversationIdAndSenderIdNotAndReadAtIsNull(driverConv.getId(), customerId))
                .thenReturn(0L);

        List<ConversationResponse> result = chatService.listConversations(customer);

        assertThat(result).hasSize(1);
        // Merge (a, b) -> a: giu ban ghi dau tien khi trung key
        assertThat(result.get(0).counterpartName()).isEqualTo("Tai Xe A");
        assertThat(result.get(0).orderCode()).isEqualTo(ord1.getOrderCode());
    }

    private User user(UUID id, UserRole role, String fullName) {
        return User.builder()
                .id(id)
                .role(role)
                .fullName(fullName)
                .phone("+8490" + Math.abs(id.hashCode() % 1000000))
                .email(id + "@example.com")
                .status(UserStatus.ACTIVE)
                .build();
    }

    private ServiceOrder order(UUID id, UUID customerId, UUID driverId) {
        return ServiceOrder.builder()
                .id(id)
                .orderCode("ORD-" + id.toString().substring(0, 8))
                .customerId(customerId)
                .driverId(driverId)
                .build();
    }

    private Conversation conv(UUID id, UUID orderId, String type, UUID customerId, UUID driverId) {
        return Conversation.builder()
                .id(id)
                .orderId(orderId)
                .type(type)
                .customerId(customerId)
                .driverId(driverId)
                .createdAt(OffsetDateTime.now(ZoneOffset.UTC))
                .build();
    }
}
