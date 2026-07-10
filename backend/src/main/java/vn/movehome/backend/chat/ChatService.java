package vn.movehome.backend.chat;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Logic nghiep vu chat 3 cap (Customer/Manager/Driver).
 * Phan quyen tham gia (participant check) o MOI thao tac — HR-10 (trai quyen → 403).
 * Manager/Admin dong vai "quay ho tro chung": xem/tra loi moi hoi thoai CUSTOMER_MANAGER + MANAGER_DRIVER.
 */
@Service
@RequiredArgsConstructor
public class ChatService {

    private static final String MANAGER_LABEL = "Quản lý Move_home";
    private static final int PREVIEW_MAX = 200;

    private final ConversationRepository conversationRepository;
    private final ChatMessageRepository messageRepository;
    private final OrderRepository orderRepository;
    private final UserRepository userRepository;
    private final ChatRealtimePublisher realtimePublisher;
    private final ChatImageService chatImageService;

    // ===================== MO / LAY HOI THOAI =====================

    @Transactional
    public Conversation openConversation(User me, UUID orderId, UUID driverId, String type) {
        if (!ConversationType.isValid(type)) {
            throw badRequest("INVALID_CONVERSATION_TYPE", "Loại hội thoại không hợp lệ.");
        }

        return switch (type) {
            case ConversationType.CUSTOMER_MANAGER -> (orderId != null)
                    ? openCustomerManagerByOrder(me, orderId)
                    : openSupport(me);
            case ConversationType.CUSTOMER_DRIVER -> openCustomerDriver(me, requireOrderId(orderId));
            case ConversationType.MANAGER_DRIVER -> (orderId != null)
                    ? openManagerDriverByOrder(me, orderId)
                    : openManagerDriverGeneral(me, driverId);
            default -> throw badRequest("INVALID_CONVERSATION_TYPE", "Loại hội thoại không hợp lệ.");
        };
    }

    private Conversation openSupport(User me) {
        // Kenh ho tro chung: chi Customer mo (Manager tra loi tu danh sach). order_id NULL.
        if (me.getRole() != UserRole.CUSTOMER) {
            throw forbidden("Chỉ khách hàng mới mở kênh hỗ trợ với quản lý.");
        }
        return conversationRepository
                .findByCustomerIdAndTypeAndOrderIdIsNull(me.getId(), ConversationType.CUSTOMER_MANAGER)
                .orElseGet(() -> createConversation(null, ConversationType.CUSTOMER_MANAGER, me.getId(), null));
    }

    /** Manager/Admin (hoac chinh khach cua don) mo kenh Khach <-> Quan ly GAN THEO DON (vd xu ly khieu nai). */
    private Conversation openCustomerManagerByOrder(User me, UUID orderId) {
        ServiceOrder order = loadOrder(orderId);
        boolean isStaff = me.getRole() == UserRole.MANAGER || me.getRole() == UserRole.ADMIN;
        boolean allowed = isStaff || me.getId().equals(order.getCustomerId());
        if (!allowed) {
            throw forbidden("Bạn không có quyền mở hội thoại này.");
        }
        return conversationRepository
                .findByOrderIdAndType(orderId, ConversationType.CUSTOMER_MANAGER)
                .orElseGet(() -> createConversation(
                        orderId, ConversationType.CUSTOMER_MANAGER, order.getCustomerId(), null));
    }

    private Conversation openCustomerDriver(User me, UUID orderId) {
        ServiceOrder order = loadOrder(orderId);
        if (order.getDriverId() == null) {
            throw conflict("ORDER_NO_DRIVER", "Đơn chưa có tài xế nên chưa thể nhắn tin.");
        }
        boolean allowed = me.getId().equals(order.getCustomerId()) || me.getId().equals(order.getDriverId());
        if (!allowed) {
            throw forbidden("Bạn không thuộc đơn này.");
        }
        return conversationRepository
                .findByOrderIdAndType(orderId, ConversationType.CUSTOMER_DRIVER)
                .orElseGet(() -> createConversation(
                        orderId, ConversationType.CUSTOMER_DRIVER, order.getCustomerId(), order.getDriverId()));
    }

    private Conversation openManagerDriverByOrder(User me, UUID orderId) {
        ServiceOrder order = loadOrder(orderId);
        if (order.getDriverId() == null) {
            throw conflict("ORDER_NO_DRIVER", "Đơn chưa có tài xế nên chưa thể nhắn tin.");
        }
        boolean isStaff = me.getRole() == UserRole.MANAGER || me.getRole() == UserRole.ADMIN;
        boolean allowed = isStaff || me.getId().equals(order.getDriverId());
        if (!allowed) {
            throw forbidden("Bạn không có quyền mở hội thoại này.");
        }
        return conversationRepository
                .findByOrderIdAndType(orderId, ConversationType.MANAGER_DRIVER)
                .orElseGet(() -> createConversation(
                        orderId, ConversationType.MANAGER_DRIVER, null, order.getDriverId()));
    }

    /**
     * Kenh ho tro chung Tai xe <-> Quan ly (order_id NULL):
     * - Tai xe mo → kenh cua chinh minh (bo qua driverId gui len).
     * - Manager/Admin mo → chon 1 tai xe cu the qua driverId.
     */
    private Conversation openManagerDriverGeneral(User me, UUID requestedDriverId) {
        UUID driverId;
        if (me.getRole() == UserRole.DRIVER) {
            driverId = me.getId();
        } else if (me.getRole() == UserRole.MANAGER || me.getRole() == UserRole.ADMIN) {
            if (requestedDriverId == null) {
                throw badRequest("DRIVER_ID_REQUIRED", "Vui lòng chọn tài xế để nhắn tin.");
            }
            User driver = userRepository.findById(requestedDriverId)
                    .filter(u -> u.getRole() == UserRole.DRIVER)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                            "DRIVER_NOT_FOUND|Không tìm thấy tài xế."));
            driverId = driver.getId();
        } else {
            throw forbidden("Bạn không có quyền mở hội thoại này.");
        }
        return conversationRepository
                .findByDriverIdAndTypeAndOrderIdIsNull(driverId, ConversationType.MANAGER_DRIVER)
                .orElseGet(() -> createConversation(
                        null, ConversationType.MANAGER_DRIVER, null, driverId));
    }

    /** Danh ba tai xe ACTIVE — cho Manager chon de mo hoi thoai khong theo don. */
    @Transactional(readOnly = true)
    public List<DriverDirectoryItem> listActiveDrivers() {
        return userRepository.findByRoleAndStatusAndDeletedAtIsNull(UserRole.DRIVER, UserStatus.ACTIVE)
                .stream()
                .sorted(Comparator.comparing(User::getFullName, Comparator.nullsLast(String::compareTo)))
                .map(u -> new DriverDirectoryItem(u.getId(), u.getFullName(), u.getPhone()))
                .collect(Collectors.toList());
    }

    private Conversation createConversation(UUID orderId, String type, UUID customerId, UUID driverId) {
        Conversation conv = Conversation.builder()
                .orderId(orderId)
                .type(type)
                .customerId(customerId)
                .driverId(driverId)
                .createdAt(OffsetDateTime.now(ZoneOffset.UTC))
                .build();
        try {
            return conversationRepository.save(conv);
        } catch (DataIntegrityViolationException race) {
            // 2 ben cung bam mo → unique index chan; lay lai ban da ton tai
            Optional<Conversation> existing;
            if (orderId != null) {
                existing = conversationRepository.findByOrderIdAndType(orderId, type);
            } else if (ConversationType.MANAGER_DRIVER.equals(type)) {
                existing = conversationRepository.findByDriverIdAndTypeAndOrderIdIsNull(driverId, type);
            } else {
                existing = conversationRepository.findByCustomerIdAndTypeAndOrderIdIsNull(customerId, type);
            }
            return existing.orElseThrow(() -> race);
        }
    }

    // ===================== DANH SACH HOI THOAI =====================

    @Transactional(readOnly = true)
    public List<ConversationResponse> listConversations(User me) {
        List<Conversation> conversations = conversationsFor(me);

        // Sort last_message_at desc, nulls last (hoi thoai chua co tin xuong duoi)
        conversations.sort(Comparator.comparing(
                Conversation::getLastMessageAt,
                Comparator.nullsLast(Comparator.reverseOrder())));

        Map<UUID, String> nameCache = buildUserNameCache(conversations);
        Map<UUID, String> orderCodeCache = buildOrderCodeCache(conversations);

        List<ConversationResponse> result = new ArrayList<>(conversations.size());
        for (Conversation c : conversations) {
            long unread = messageRepository
                    .countByConversationIdAndSenderIdNotAndReadAtIsNull(c.getId(), me.getId());
            result.add(new ConversationResponse(
                    c.getId(),
                    c.getType(),
                    c.getOrderId(),
                    c.getOrderId() == null ? null : orderCodeCache.get(c.getOrderId()),
                    counterpartName(c, me, nameCache),
                    c.getLastMessageText(),
                    c.getLastMessageAt(),
                    unread));
        }
        return result;
    }

    // ===================== TIN NHAN =====================

    @Transactional
    public Page<ChatMessageResponse> getMessages(User me, UUID conversationId, Pageable pageable) {
        Conversation conv = loadConversation(conversationId);
        assertParticipant(conv, me);

        Page<ChatMessage> page = messageRepository
                .findByConversationIdOrderByCreatedAtDesc(conversationId, pageable);

        // Mo hoi thoai = da doc cac tin cua nguoi khac
        messageRepository.markConversationRead(conversationId, me.getId(), OffsetDateTime.now(ZoneOffset.UTC));

        Map<UUID, String> nameCache = buildSenderNameCache(page.getContent());
        return page.map(m -> ChatMessageResponse.of(
                m,
                nameCache.getOrDefault(m.getSenderId(), "Người dùng"),
                m.getSenderId().equals(me.getId()),
                chatImageService.signUrl(m.getImagePublicId())));
    }

    @Transactional
    public ChatMessageResponse sendMessage(User me, UUID conversationId, String rawContent) {
        Conversation conv = loadConversation(conversationId);
        assertParticipant(conv, me);

        String content = rawContent == null ? "" : rawContent.trim();
        if (content.isEmpty()) {
            throw badRequest("EMPTY_MESSAGE", "Nội dung tin nhắn không được để trống.");
        }

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        ChatMessage saved = messageRepository.save(ChatMessage.builder()
                .conversationId(conversationId)
                .senderId(me.getId())
                .content(content)
                .createdAt(now)
                .build());

        conv.setLastMessageText(content.length() > PREVIEW_MAX ? content.substring(0, PREVIEW_MAX) : content);
        conv.setLastMessageAt(now);
        conversationRepository.save(conv);

        // Day realtime toi nguoi nhan (client tu tinh mine bang senderId, nen mine=false o day khong quan trong)
        ChatMessageResponse payload = ChatMessageResponse.of(saved, me.getFullName(), false, null);
        realtimePublisher.publishNewMessage(resolveRecipients(conv, me.getId()), payload);

        return ChatMessageResponse.of(saved, me.getFullName(), true, null);
    }

    /** Gui 1 tin nhan kem 1 anh (upload Cloudinary signed, AC-10). content = "" (anh khong co text). */
    @Transactional
    public ChatMessageResponse sendImage(User me, UUID conversationId, MultipartFile file) {
        Conversation conv = loadConversation(conversationId);
        assertParticipant(conv, me);

        String publicId = chatImageService.upload(conversationId, file);
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        ChatMessage saved = messageRepository.save(ChatMessage.builder()
                .conversationId(conversationId)
                .senderId(me.getId())
                .content("")
                .imagePublicId(publicId)
                .createdAt(now)
                .build());

        conv.setLastMessageText("🖼 Hình ảnh");
        conv.setLastMessageAt(now);
        conversationRepository.save(conv);

        String imageUrl = chatImageService.signUrl(publicId);
        ChatMessageResponse payload = ChatMessageResponse.of(saved, me.getFullName(), false, imageUrl);
        realtimePublisher.publishNewMessage(resolveRecipients(conv, me.getId()), payload);

        return ChatMessageResponse.of(saved, me.getFullName(), true, imageUrl);
    }

    @Transactional
    public void markRead(User me, UUID conversationId) {
        Conversation conv = loadConversation(conversationId);
        assertParticipant(conv, me);
        messageRepository.markConversationRead(conversationId, me.getId(), OffsetDateTime.now(ZoneOffset.UTC));
    }

    @Transactional(readOnly = true)
    public long unreadCount(User me) {
        List<UUID> convIds = conversationIdsFor(me);
        if (convIds.isEmpty()) {
            return 0L;
        }
        return messageRepository.countUnreadInConversations(convIds, me.getId());
    }

    // ===================== PHAN QUYEN & HELPERS =====================

    private List<Conversation> conversationsFor(User me) {
        return switch (me.getRole()) {
            case CUSTOMER -> conversationRepository.findByCustomerId(me.getId());
            case DRIVER -> conversationRepository.findByDriverId(me.getId());
            case MANAGER, ADMIN -> conversationRepository.findByTypeIn(
                    List.of(ConversationType.CUSTOMER_MANAGER, ConversationType.MANAGER_DRIVER));
        };
    }

    private List<UUID> conversationIdsFor(User me) {
        return switch (me.getRole()) {
            case CUSTOMER -> conversationRepository.findIdsByCustomerId(me.getId());
            case DRIVER -> conversationRepository.findIdsByDriverId(me.getId());
            case MANAGER, ADMIN -> conversationRepository.findIdsByTypeIn(
                    List.of(ConversationType.CUSTOMER_MANAGER, ConversationType.MANAGER_DRIVER));
        };
    }

    /** Kiem tra nguoi goi co thuoc hoi thoai khong. Manager/Admin la quay ho tro chung. */
    private void assertParticipant(Conversation conv, User me) {
        UserRole role = me.getRole();
        boolean allowed;
        if (role == UserRole.MANAGER || role == UserRole.ADMIN) {
            allowed = ConversationType.CUSTOMER_MANAGER.equals(conv.getType())
                    || ConversationType.MANAGER_DRIVER.equals(conv.getType());
        } else if (role == UserRole.CUSTOMER) {
            allowed = me.getId().equals(conv.getCustomerId());
        } else { // DRIVER
            allowed = me.getId().equals(conv.getDriverId());
        }
        if (!allowed) {
            throw forbidden("Bạn không có quyền truy cập hội thoại này.");
        }
    }

    /** Xac dinh nguoi nhan de day realtime (loai tru nguoi gui). */
    private Set<UUID> resolveRecipients(Conversation conv, UUID senderId) {
        Set<UUID> recipients = new LinkedHashSet<>();
        switch (conv.getType()) {
            case ConversationType.CUSTOMER_DRIVER -> {
                UUID other = senderId.equals(conv.getCustomerId()) ? conv.getDriverId() : conv.getCustomerId();
                if (other != null) {
                    recipients.add(other);
                }
            }
            case ConversationType.MANAGER_DRIVER -> {
                if (senderId.equals(conv.getDriverId())) {
                    recipients.addAll(activeManagerIds()); // tai xe gui → toan bo quay ho tro
                } else if (conv.getDriverId() != null) {
                    recipients.add(conv.getDriverId());     // quan ly gui → tai xe
                }
            }
            case ConversationType.CUSTOMER_MANAGER -> {
                if (senderId.equals(conv.getCustomerId())) {
                    recipients.addAll(activeManagerIds()); // khach gui → toan bo quay ho tro
                } else if (conv.getCustomerId() != null) {
                    recipients.add(conv.getCustomerId());   // quan ly gui → khach
                }
            }
            default -> { /* khong co */ }
        }
        recipients.remove(senderId);
        return recipients;
    }

    private List<UUID> activeManagerIds() {
        return userRepository
                .findByRoleAndStatusAndDeletedAtIsNull(UserRole.MANAGER, UserStatus.ACTIVE)
                .stream()
                .map(User::getId)
                .collect(Collectors.toList());
    }

    private String counterpartName(Conversation conv, User me, Map<UUID, String> nameCache) {
        UserRole role = me.getRole();
        return switch (conv.getType()) {
            case ConversationType.CUSTOMER_MANAGER ->
                    (role == UserRole.CUSTOMER) ? MANAGER_LABEL : nameOr(nameCache, conv.getCustomerId(), "Khách hàng");
            case ConversationType.MANAGER_DRIVER ->
                    (role == UserRole.DRIVER) ? MANAGER_LABEL : nameOr(nameCache, conv.getDriverId(), "Tài xế");
            case ConversationType.CUSTOMER_DRIVER ->
                    (role == UserRole.CUSTOMER)
                            ? nameOr(nameCache, conv.getDriverId(), "Tài xế")
                            : nameOr(nameCache, conv.getCustomerId(), "Khách hàng");
            default -> "Hội thoại";
        };
    }

    private String nameOr(Map<UUID, String> cache, UUID id, String fallback) {
        if (id == null) {
            return fallback;
        }
        String name = cache.get(id);
        return (name == null || name.isBlank()) ? fallback : name;
    }

    private Map<UUID, String> buildUserNameCache(List<Conversation> conversations) {
        Set<UUID> ids = new HashSet<>();
        for (Conversation c : conversations) {
            if (c.getCustomerId() != null) {
                ids.add(c.getCustomerId());
            }
            if (c.getDriverId() != null) {
                ids.add(c.getDriverId());
            }
        }
        return fetchNames(ids);
    }

    private Map<UUID, String> buildSenderNameCache(List<ChatMessage> messages) {
        Set<UUID> ids = messages.stream().map(ChatMessage::getSenderId).collect(Collectors.toSet());
        return fetchNames(ids);
    }

    private Map<UUID, String> fetchNames(Set<UUID> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        return userRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(User::getId, User::getFullName, (a, b) -> a));
    }

    private Map<UUID, String> buildOrderCodeCache(List<Conversation> conversations) {
        Set<UUID> orderIds = conversations.stream()
                .map(Conversation::getOrderId)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());
        if (orderIds.isEmpty()) {
            return Map.of();
        }
        return orderRepository.findAllById(orderIds).stream()
                .collect(Collectors.toMap(ServiceOrder::getId, ServiceOrder::getOrderCode, (a, b) -> a));
    }

    private ServiceOrder loadOrder(UUID orderId) {
        return orderRepository.findByIdAndDeletedAtIsNull(orderId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "ORDER_NOT_FOUND|Không tìm thấy đơn hàng."));
    }

    private Conversation loadConversation(UUID conversationId) {
        return conversationRepository.findById(conversationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "CONVERSATION_NOT_FOUND|Không tìm thấy hội thoại."));
    }

    private UUID requireOrderId(UUID orderId) {
        if (orderId == null) {
            throw badRequest("ORDER_ID_REQUIRED", "Thiếu mã đơn cho hội thoại theo đơn.");
        }
        return orderId;
    }

    private ResponseStatusException badRequest(String code, String msg) {
        return new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, code + "|" + msg);
    }

    private ResponseStatusException conflict(String code, String msg) {
        return new ResponseStatusException(HttpStatus.CONFLICT, code + "|" + msg);
    }

    private ResponseStatusException forbidden(String msg) {
        return new ResponseStatusException(HttpStatus.FORBIDDEN, "FORBIDDEN|" + msg);
    }
}
