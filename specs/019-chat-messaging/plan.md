# Implementation Plan: Chat Messaging (3 cấp) — Spec #019

> **Loại:** Reconstructed plan (spec-after) — tái dựng từ code đã có + `spec.md` v1.0.0.
> Nguồn chi tiết: [`spec.md`](spec.md).
> **Status:** As-built · **Migration:** V36, V38.

---

## 1. Architectural Approach

Chat realtime **3 cấp** (Customer/Manager/Driver) trên **WebSocket STOMP + SockJS** (AC-05), broker
in-memory, lưu DB ngay (nguồn bền vững) + polling lưới an toàn phía FE. REST cho fetch/gửi; WS chỉ đẩy
realtime tới `/user/{id}/queue/messages`. Phân quyền qua `assertParticipant` (HR-10); quyền riêng tư
Customer↔Driver không cho Manager/Admin đọc (FR-046).

## 2. Components (as-built)

| Thành phần | Trách nhiệm | File (package `chat`) |
|------------|-------------|------------------------|
| `Conversation`, `ChatMessage` | Entity | `chat/Conversation.java`, `chat/ChatMessage.java` |
| `ConversationType` | Enum-as-String 3 loại | `chat/ConversationType.java` |
| `ConversationRepository`, `ChatMessageRepository` | JPA + partial index queries, unread count | `chat/*Repository.java` |
| `ChatService` | Mở hội thoại, gửi text/ảnh, đọc, unread, participant check | `chat/ChatService.java` |
| `ChatController` | 8 REST endpoint `/api/chat/**` | `chat/ChatController.java` |
| `ChatImageService` | Cloudinary signed upload + sign URL (AC-10) | `chat/ChatImageService.java` |
| `ChatRealtimePublisher` | Đẩy STOMP tới user-destination | `chat/ChatRealtimePublisher.java` |
| `WebSocketConfig` + `WebSocketAuthChannelInterceptor` | STOMP `/ws`, JWT ở CONNECT, origin whitelist | `config/WebSocketConfig.java` |
| FE `messages.html` + `js/chat.js` | UI 4 vai trò + SockJS/STOMP client + polling | `frontend/pages/messages.html` |

## 3. Data Flow

```
FE messages.html ──REST──> ChatController ──> ChatService ──@Transactional──> DB (conversation, chat_message)
                                                     └──> ChatRealtimePublisher ──STOMP──> /user/{id}/queue/messages ──> FE (client khác)
FE STOMP CONNECT ──JWT──> WebSocketAuthChannelInterceptor (verify) ──> subscribe /user/queue/messages
```

## 4. Dependencies (thứ tự triển khai — Rollout)

1. `V36` (2 bảng + 5 index) → 2. package `chat` BE + WebSocketConfig → 3. FE `messages.html`+`chat.js` (polling bật ngay) → 4. `V38` (gửi ảnh).

## 5. Risks & Mitigations (từ Deferred Scope)

| Rủi ro | Mức | Mitigation | Ref |
|--------|-----|-----------|-----|
| Đơn đổi tài xế → hội thoại C↔D kẹt driver cũ (bug quyền riêng tư) | **Cao** | Cập nhật `driver_id`/đóng+tạo mới khi reassign — **phải xong trước Spec #023 prod** | DS-02 / OQ-1 |
| Upload Cloudinary trong `@Transactional` chiếm connection pool (max 5) | TB | Đưa upload ra ngoài TX | DS-03 |
| Realtime publish trong TX → rollback sau publish | Thấp | `afterCommit` callback | DS-04 |
| Thiếu partial unique index kênh chung M↔D | TB | Thêm index (migration mới) | DS-01 |
| HR-16 rate limit chat chưa áp | TB | Áp rate limit chung/ngoại lệ | DS-11 |
| Broker in-memory mất realtime khi restart | Thấp (chấp nhận) | DB bền + SockJS reconnect | FR-052 |

## 6. Questions for Human (từ Open Questions của spec)

- **OQ-1 (High):** Đơn đổi tài xế → hội thoại C↔D xử lý sao? (blocker DS-02)
- **OQ-2 (High):** Viết lại CONTEXT §2 Chat cho khớp 3 cấp hay giữ banner?
- OQ-4: User SUSPENDED có được chat không? (chưa có guard)

## 7. Constitution Check (tóm tắt — chi tiết ở spec §Constitution Compliance)

PASS phần lớn; **gap HR-16** (rate limit chat), **exception AC-09/AC-10 partial** (cleanup), **AC-05 lệch 3 cấp có chủ ý đã duyệt** (`CLAUDE.md §4`). Không block.
