package vn.movehome.backend.chat;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import vn.movehome.backend.chat.dto.ChatMessageResponse;
import vn.movehome.backend.chat.dto.ConversationResponse;
import vn.movehome.backend.chat.dto.DriverDirectoryItem;
import vn.movehome.backend.chat.dto.OpenConversationRequest;
import vn.movehome.backend.chat.dto.SendMessageRequest;
import vn.movehome.backend.entity.User;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * REST chat (lich su + gui). Realtime delivery qua WebSocket STOMP (xem WebSocketConfig).
 * Tat ca endpoint yeu cau dang nhap (SecurityConfig: /api/chat/** authenticated).
 * Quyen so huu (participant) do ChatService kiem tra theo userId tu JWT (HR-10).
 */
@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatController {

    private static final int MAX_PAGE_SIZE = 100;

    private final ChatService chatService;

    /** Danh sach hoi thoai cua nguoi dang dang nhap. */
    @GetMapping("/conversations")
    public List<ConversationResponse> listConversations(@AuthenticationPrincipal User me) {
        return chatService.listConversations(require(me));
    }

    /** Mo (hoac lay) 1 hoi thoai theo don + loai. */
    @PostMapping("/conversations/open")
    public ConversationResponse openConversation(
            @AuthenticationPrincipal User me,
            @Valid @RequestBody OpenConversationRequest request) {
        Conversation conv = chatService.openConversation(
                require(me), request.orderId(), request.driverId(), request.type());
        // Tra ve dang ConversationResponse cho FE thong nhat (lay lai qua list de co unread/counterpart)
        return chatService.listConversations(me).stream()
                .filter(c -> c.id().equals(conv.getId()))
                .findFirst()
                .orElse(new ConversationResponse(
                        conv.getId(), conv.getType(), conv.getOrderId(), null, null, null, null, 0L));
    }

    /** Lich su tin nhan (moi nhat truoc). Mo hoi thoai = danh dau da doc. */
    @GetMapping("/conversations/{id}/messages")
    public Page<ChatMessageResponse> messages(
            @AuthenticationPrincipal User me,
            @PathVariable UUID id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "30") int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        Pageable pageable = PageRequest.of(safePage, safeSize);
        return chatService.getMessages(require(me), id, pageable);
    }

    /** Gui 1 tin nhan. */
    @PostMapping("/conversations/{id}/messages")
    public ChatMessageResponse send(
            @AuthenticationPrincipal User me,
            @PathVariable UUID id,
            @Valid @RequestBody SendMessageRequest request) {
        return chatService.sendMessage(require(me), id, request.content());
    }

    /** Gui 1 tin nhan kem 1 anh (multipart, field "file"). */
    @PostMapping(value = "/conversations/{id}/images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ChatMessageResponse sendImage(
            @AuthenticationPrincipal User me,
            @PathVariable UUID id,
            @RequestParam("file") MultipartFile file) {
        return chatService.sendImage(require(me), id, file);
    }

    /** Danh dau da doc thu cong (vd khi user focus lai tab). */
    @PostMapping("/conversations/{id}/read")
    public Map<String, Object> markRead(@AuthenticationPrincipal User me, @PathVariable UUID id) {
        chatService.markRead(require(me), id);
        return Map.of("success", true);
    }

    /** Tong so tin chua doc — cho badge tren thanh dieu huong. */
    @GetMapping("/unread-count")
    public Map<String, Long> unreadCount(@AuthenticationPrincipal User me) {
        return Map.of("unreadCount", chatService.unreadCount(require(me)));
    }

    /** Danh ba tai xe ACTIVE — Manager/Admin chon tai xe de mo hoi thoai khong theo don (HR-10). */
    @GetMapping("/directory/drivers")
    @PreAuthorize("hasAnyRole('MANAGER','ADMIN')")
    public List<DriverDirectoryItem> driverDirectory(@AuthenticationPrincipal User me) {
        require(me);
        return chatService.listActiveDrivers();
    }

    private User require(User me) {
        if (me == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                    "AUTHENTICATION_REQUIRED|Vui lòng đăng nhập để tiếp tục.");
        }
        return me;
    }
}
