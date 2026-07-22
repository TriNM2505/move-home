# Tasks: Chat Messaging (3 cấp) — Spec #019

> Feature đã build; task liệt kê dạng as-built (✅ done) để
> hoàn tất bộ artifact SDD Pha 3. Mỗi task ghi spec ref. Nguồn: [`spec.md`](spec.md), [`plan.md`](plan.md).

Trạng thái: ✅ done · ⏳ deferred (Deferred Scope) · 🚫 blocked

| ID | Task | File chính | Spec ref | TT |
|----|------|-----------|----------|----|
| T-01 | Migration `conversation` + `chat_message` + 5 index | `V36__create_chat_tables.sql` | Data Model | ✅ |
| T-02 | Entity + `ConversationType` + repositories | `chat/*.java` | FR-024, FR-045 | ✅ |
| T-03 | `ChatService.openConversation` (5 nhánh type) + chống race | `chat/ChatService.java` | FR-008..027 | ✅ |
| T-04 | Danh sách hội thoại theo vai trò + counterpartName + sort | `ChatService`, `ChatController` | FR-001..007 | ✅ |
| T-05 | Đọc tin nhắn (pagination 30/clamp) + auto mark read + `mine` | `ChatService.getMessages` | FR-028..033 | ✅ |
| T-06 | Gửi text + update preview + resolve recipients + realtime | `ChatService.sendMessage` | FR-034..039 | ✅ |
| T-07 | Mark read thủ công + unread-count | `ChatController` | FR-040..041 | ✅ |
| T-08 | Danh bạ tài xế (Manager/Admin) | `ChatController` | FR-042..044 | ✅ |
| T-09 | Participant check + 401/403 | `ChatService.assertParticipant` | FR-045..048 | ✅ |
| T-10 | WebSocket `/ws` STOMP+SockJS + JWT CONNECT + origin whitelist | `config/WebSocketConfig.java` | FR-049..052 | ✅ |
| T-11 | FE `messages.html` + `js/chat.js` (STOMP client + polling + L/E/E states) | `frontend/pages/messages.html` | Frontend Contract | ✅ |
| T-12 | Migration ảnh + gửi ảnh (Cloudinary signed) | `V38`, `ChatImageService` | FR-037, AC-10 | ✅ |
| T-13 | Deep-link từ order-detail / dispute-detail | FE | Deep-link table | ✅ |
| T-14 | **Xử lý reassign tài xế cho hội thoại C↔D** | — | DS-02 / OQ-1 | 🚫 blocked |
| T-15 | Partial unique index kênh chung M↔D (cần migration mới) | — | DS-01 | ⏳ |
| T-16 | Đưa upload Cloudinary ra ngoài TX + publish afterCommit | `ChatService` | DS-03, DS-04 | ⏳ |
| T-17 | Rate limit `POST /messages` (HR-16) | — | DS-11 | ⏳ |
| T-18 | Cleanup job `chat_message`/ảnh > 90 ngày | — | DS-06, DS-09 | ⏳ |

**Định nghĩa Done (bản 1.0):** T-01..T-13 ✅. T-14 (🚫) phải xong **trước khi Spec #023 lên prod**.
