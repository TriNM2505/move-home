# Feature Specification: Chat Messaging (3 cấp Customer / Manager / Driver)

**Feature Branch:** `019-chat-messaging`  
**Feature Number:** #19 of 26 — SHELL (hỗ trợ vận hành)  
**Created:** 2026-06-04  
**Version:** 1.0.0  
**Status:** Draft  
**Sprint Target:** Sprint 6 — Chat 3 cấp (WebSocket STOMP + SockJS)

**CONTEXT.md reference:** v2.0 §2 Chat ho tro (⚠️ **đã lỗi thời** — xem Source-of-Truth Resolution),
§3 RBAC  
**Constitution reference:** v1.4.0 — **AC-05** (WebSocket STOMP + SockJS + fallback polling), AC-02,
AC-09, AC-10, AC-11, AC-12, AC-14, AC-15, AC-16, HR-01, HR-10, HR-20, HR-21, ES-02, ES-04  
**Screen reference:** `frontend/pages/messages.html` — cần bổ sung vào `docs/SCREEN_INVENTORY.md`
(xem DS-07)  
**Related specs:** Spec #001 Auth/RBAC (JWT cho WebSocket handshake); Spec #003 Customer Orders
(deep-link theo đơn); Spec #006 Driver Workflow; Spec #010 Manager Disputes (deep-link từ tranh chấp)

**Migration liên quan:** `V36__create_chat_tables.sql` (`conversation` + `chat_message`),
`V38__chat_message_image.sql` (`chat_message.image_public_id`)

---

## Goals

Đặc tả hệ thống **chat 3 cấp** giữa Customer, Manager và Driver — realtime qua WebSocket STOMP + SockJS,
lưu bền vững trong PostgreSQL.

Hệ thống hỗ trợ **3 loại hội thoại**: `CUSTOMER_MANAGER` (khách ↔ quản lý), `MANAGER_DRIVER`
(quản lý ↔ tài xế), `CUSTOMER_DRIVER` (khách ↔ tài xế). Mỗi loại có thể **gắn theo đơn**
(`order_id != NULL`) hoặc là **kênh hỗ trợ chung** (`order_id = NULL`, chỉ áp dụng cho
`CUSTOMER_MANAGER` và `MANAGER_DRIVER`). Manager và Admin đóng vai "quầy hỗ trợ chung": nhìn thấy và
trả lời **mọi** hội thoại `CUSTOMER_MANAGER` + `MANAGER_DRIVER`, không cần được gán riêng.

Tin nhắn hỗ trợ **text** hoặc **một ảnh** (Cloudinary signed upload, hiển thị qua signed URL).
Tin nhắn vừa đẩy realtime qua user-destination `/user/{id}/queue/messages` vừa lưu DB ngay lập tức.
Frontend có lưới an toàn polling khi WebSocket rớt (AC-05).

Spec định nghĩa REST contracts, ma trận phân quyền tham gia, luồng mở hội thoại, đánh dấu đã đọc,
đếm chưa đọc, upload ảnh, cấu hình WebSocket và frontend contract. Tiếng Việt có dấu (HR-20), thời gian
lưu UTC (AC-07), pagination default 30 (AC-15).

---

## Source-of-Truth Resolution

> Hierarchy áp dụng: `CONTEXT.md v2.0` → Constitution v1.4.0 → Specs → spec này → Code.

### Quyết định kiến trúc — mở rộng chat lên 3 cấp

Bản gốc `CONTEXT.md` v2.0 §2 quy định chat **một cấp**: chỉ Customer ↔ Manager, kênh hỗ trợ chung, không
gắn theo đơn, và "Driver KHONG tham gia chat". Quyết định của leader (2026-07) **mở rộng lên 3 cấp** và
cho Driver tham gia. Spec này đặc tả mô hình 3 cấp đó.

**Lý do mở rộng:**

| Vấn đề của mô hình 1 cấp | Cách 3 cấp giải quyết |
|--------------------------|------------------------|
| Khách cần hướng dẫn tài xế đường đi, số nhà, tầng — phải gọi điện hoặc nhờ Manager trung chuyển | `CUSTOMER_DRIVER` gắn theo đơn — hai bên nói trực tiếp |
| Tài xế gặp vướng mắc vận hành (xe hỏng, khách vắng) không có kênh hỗ trợ | `MANAGER_DRIVER` kênh chung + theo đơn |
| Manager phải làm tổng đài trung chuyển mọi thông tin giữa khách và tài xế | Manager chỉ can thiệp khi cần |
| Hội thoại không gắn đơn → không tra được ngữ cảnh khi có tranh chấp | `order_id` cho phép deep-link từ trang đơn/tranh chấp |

**Ranh giới quyền riêng tư được giữ:** Manager/Admin **không** đọc được hội thoại `CUSTOMER_DRIVER`
(FR-046) — mở rộng phạm vi chat nhưng không mở rộng quyền giám sát.

| Chủ đề | Quyết định canonical | Nguồn |
|--------|----------------------|-------|
| Số cấp chat | **3** — thay cho 1 cấp của CONTEXT §2 bản gốc | Leader 2026-07 |
| Driver tham gia chat | **Có** — thay cho "Driver KHONG tham gia chat" | Leader 2026-07 |
| Gắn theo đơn | **Có** `order_id` — thay cho "khong gan theo don cu the" | Leader 2026-07 |
| Kỹ thuật realtime | WebSocket STOMP + SockJS, broker in-memory, lưu DB ngay | **Giữ nguyên AC-05** |
| Fallback | Polling lưới an toàn phía FE | **Giữ nguyên AC-05** |

> Quyết định này đã được ghi nhận tường minh ở `CLAUDE.md` §4 ("Driver CO tham gia chat… Day la **CO
> CHU Y**… KHONG phai bug") và banner đầu mục `CONTEXT.md` §2 Chat. Phần thân CONTEXT §2 và AC-05 cần
> được đồng bộ theo — xem DS-08.
>
> **Về AC-05:** phần kỹ thuật của AC-05 (STOMP + SockJS + in-memory broker + lưu DB + fallback polling)
> được tuân thủ **đầy đủ**. Chỉ phạm vi người tham gia là mở rộng.

### Ranh giới còn lại

| Chủ đề | Quyết định |
|--------|-----------|
| Kênh hỗ trợ chung | `order_id = NULL` — chỉ áp dụng cho `CUSTOMER_MANAGER` và `MANAGER_DRIVER`; `CUSTOMER_DRIVER` luôn gắn đơn |
| Admin trong chat | Quyền ngang Manager — theo CONTEXT §3 RBAC ("Chat ho tro: Admin **Yes**") |
| Ảnh trong chat | 1 ảnh/tin nhắn, Cloudinary `type=authenticated` + signed URL (AC-10) |

---

## Scope Summary

**In scope:**

1. `GET /api/chat/conversations` — danh sách hội thoại theo vai trò.
2. `POST /api/chat/conversations/open` — mở/lấy hội thoại theo `type` + `orderId`/`driverId`.
3. `GET /api/chat/conversations/{id}/messages` — lịch sử tin nhắn, tự đánh dấu đã đọc.
4. `POST /api/chat/conversations/{id}/messages` — gửi tin nhắn text.
5. `POST /api/chat/conversations/{id}/images` — gửi tin nhắn kèm 1 ảnh.
6. `POST /api/chat/conversations/{id}/read` — đánh dấu đã đọc thủ công.
7. `GET /api/chat/unread-count` — badge tổng số chưa đọc.
8. `GET /api/chat/directory/drivers` — danh bạ tài xế ACTIVE cho Manager/Admin.
9. WebSocket STOMP `/ws` + SockJS, JWT auth ở CONNECT, user-destination `/user/queue/messages`.
10. Ma trận phân quyền tham gia (participant check).
11. Loading/Empty/Error states cho `messages.html`.

**Out of scope:**

1. Chat cho Guest (chưa đăng nhập) — CONTEXT §Guest Mode nói form liên hệ, không phải chat.
2. Nhóm chat > 2 bên.
3. Gửi nhiều ảnh/tin nhắn hoặc file đính kèm khác (PDF, video).
4. Xoá/sửa tin nhắn đã gửi.
5. Typing indicator, presence (online/offline).
6. Push notification mobile.
7. Cleanup `chat_message` > 90 ngày (AC-09 cho phép hard delete — xem DS-06).
8. Tìm kiếm trong lịch sử chat.
9. Chặn/báo cáo người dùng.

---

## User Stories

**P1:**

**US1:** Là Customer, tôi mở kênh hỗ trợ chung với quản lý để hỏi trước khi đặt đơn.

**US2:** Là Customer, tôi nhắn tin trực tiếp với tài xế của đơn để hướng dẫn đường đi.

**US3:** Là Driver, tôi nhắn tin với quản lý khi cần hỗ trợ vận hành.

**US4:** Là Driver, tôi nhắn tin với khách của đơn để liên lạc khi tới nơi.

**US5:** Là Manager, tôi thấy mọi hội thoại khách và tài xế trong một danh sách để trả lời tập trung.

**US6:** Là bất kỳ vai trò nào, tôi nhận tin nhắn **realtime** không cần refresh trang.

**US7:** Là bất kỳ vai trò nào, tôi thấy badge số tin chưa đọc trên thanh điều hướng.

**P2:**

**US8:** Là bất kỳ vai trò nào, tôi gửi ảnh trong chat (ảnh đồ đạc, hiện trường, biên nhận).

**US9:** Là Manager, tôi chọn tài xế từ danh bạ để chủ động nhắn tin không gắn đơn.

**US10:** Là Customer/Manager, tôi mở chat gắn theo đơn từ trang chi tiết đơn hoặc trang tranh chấp
(deep-link).

**US11:** Là bất kỳ vai trò nào, khi mở hội thoại thì tin nhắn tự động được đánh dấu đã đọc.

---

## Functional Requirements

> **EARS notation:** WHEN (event-driven) | WHILE (state-driven) | WHERE (error/unwanted) |
> IF/THEN (optional branch).
>
> Tổng: **52 FR**, trong đó **26 FR có mệnh đề WHERE** (50.0% ≥ 30% theo CLAUDE.md §5).

---

### Nhóm 1 — Danh sách hội thoại (FR-001..FR-007)

**FR-001**  
WHEN user gọi `GET /api/chat/conversations`, THE system SHALL trả `List<ConversationResponse>` gồm
`id`, `type`, `orderId`, `orderCode`, `counterpartName`, `lastMessageText`, `lastMessageAt`,
`unreadCount`.

**FR-002**  
WHILE user là `CUSTOMER`, THE system SHALL trả các hội thoại có `customer_id` = user id.

**FR-003**  
WHILE user là `DRIVER`, THE system SHALL trả các hội thoại có `driver_id` = user id.

**FR-004**  
WHILE user là `MANAGER` hoặc `ADMIN`, THE system SHALL trả **mọi** hội thoại type
`CUSTOMER_MANAGER` + `MANAGER_DRIVER` — không lọc theo id, vì Manager/Admin là quầy hỗ trợ chung;
hội thoại `CUSTOMER_DRIVER` SHALL **không** hiển thị cho Manager/Admin.

**FR-005**  
WHEN sắp xếp danh sách, THE system SHALL sort `last_message_at` giảm dần, **nulls last** — hội thoại
chưa có tin nhắn xuống cuối.

**FR-006**  
WHEN tính `counterpartName`, THE system SHALL: với `CUSTOMER_MANAGER` → khách thấy "Quản lý Move_home",
Manager thấy tên khách; với `MANAGER_DRIVER` → tài xế thấy "Quản lý Move_home", Manager thấy tên tài xế;
với `CUSTOMER_DRIVER` → khách thấy tên tài xế, tài xế thấy tên khách.

**FR-007**  
WHERE tên đối phương `null`/rỗng, THE system SHALL dùng fallback "Khách hàng" / "Tài xế" / "Hội thoại"
tuỳ ngữ cảnh.

---

### Nhóm 2 — Mở hội thoại: Customer ↔ Manager (FR-008..FR-013)

**FR-008**  
WHEN user gọi `POST /api/chat/conversations/open` với `type = CUSTOMER_MANAGER` và `orderId = null`,
THE system SHALL mở/lấy **kênh hỗ trợ chung** của chính khách đó.

**FR-009**  
WHERE user gọi kênh hỗ trợ chung mà role **khác** `CUSTOMER`, THE system SHALL trả HTTP 403 "Chỉ khách
hàng mới mở kênh hỗ trợ với quản lý." — Manager trả lời từ danh sách, không tự mở kênh chung.

**FR-010**  
WHILE bảng tồn tại, THE system SHALL enforce partial unique index `uq_conversation_support`
(`customer_id` WHERE `type = 'CUSTOMER_MANAGER' AND order_id IS NULL`) — mỗi khách **một** thread hỗ trợ.

**FR-011**  
WHEN user gọi với `type = CUSTOMER_MANAGER` và `orderId != null`, THE system SHALL mở/lấy hội thoại
**gắn theo đơn**; `customer_id` SHALL lấy từ `order.customerId` (không phải từ người gọi).

**FR-012**  
WHERE người gọi không phải Manager/Admin **và** không phải khách của đơn, THE system SHALL trả HTTP 403
"Bạn không có quyền mở hội thoại này." (HR-10).

**FR-013**  
WHERE đơn không tồn tại hoặc `deleted_at != NULL`, THE system SHALL trả HTTP 404 `ORDER_NOT_FOUND`
"Không tìm thấy đơn hàng."

---

### Nhóm 3 — Mở hội thoại: Customer ↔ Driver (FR-014..FR-017)

**FR-014**  
WHEN user gọi với `type = CUSTOMER_DRIVER`, THE system SHALL **bắt buộc** `orderId`; WHERE `orderId`
là `null`, SHALL trả HTTP 422 `ORDER_ID_REQUIRED` "Thiếu mã đơn cho hội thoại theo đơn." — kênh
Customer↔Driver **luôn** gắn theo đơn, không có kênh chung.

**FR-015**  
WHERE `order.driver_id` là `null`, THE system SHALL trả HTTP 409 `ORDER_NO_DRIVER` "Đơn chưa có tài xế
nên chưa thể nhắn tin."

**FR-016**  
WHERE người gọi không phải `order.customerId` **và** không phải `order.driverId`, THE system SHALL trả
HTTP 403 "Bạn không thuộc đơn này." — kể cả Manager/Admin **cũng bị chặn** khỏi kênh Customer↔Driver.

**FR-017**  
WHEN tạo hội thoại `CUSTOMER_DRIVER`, THE system SHALL set cả `customer_id` = `order.customerId` và
`driver_id` = `order.driverId`.

---

### Nhóm 4 — Mở hội thoại: Manager ↔ Driver (FR-018..FR-023)

**FR-018**  
WHEN user gọi với `type = MANAGER_DRIVER` và `orderId != null`, THE system SHALL mở/lấy hội thoại gắn
theo đơn; `driver_id` SHALL lấy từ `order.driverId`, `customer_id` SHALL là `null`.

**FR-019**  
WHERE `order.driver_id` là `null`, THE system SHALL trả HTTP 409 `ORDER_NO_DRIVER`.

**FR-020**  
WHERE người gọi không phải Manager/Admin **và** không phải `order.driverId`, THE system SHALL trả
HTTP 403.

**FR-021**  
WHEN user gọi với `type = MANAGER_DRIVER` và `orderId = null` **và** role là `DRIVER`, THE system SHALL
mở kênh chung của **chính tài xế đó**, bỏ qua `driverId` gửi lên — tài xế không thể mở kênh của người khác.

**FR-022**  
WHEN user gọi với `type = MANAGER_DRIVER` và `orderId = null` **và** role là `MANAGER`/`ADMIN`,
THE system SHALL yêu cầu `driverId`; WHERE `driverId` là `null`, SHALL trả HTTP 422
`DRIVER_ID_REQUIRED` "Vui lòng chọn tài xế để nhắn tin."; WHERE `driverId` không tồn tại hoặc không
phải role `DRIVER`, SHALL trả HTTP 404 `DRIVER_NOT_FOUND` "Không tìm thấy tài xế."

**FR-023**  
WHERE role là `CUSTOMER` gọi `MANAGER_DRIVER` với `orderId = null`, THE system SHALL trả HTTP 403
"Bạn không có quyền mở hội thoại này."

---

### Nhóm 5 — Chống tạo trùng hội thoại (FR-024..FR-027)

**FR-024**  
WHILE bảng tồn tại, THE system SHALL enforce partial unique index `uq_conversation_order_type`
(`order_id`, `type` WHERE `order_id IS NOT NULL`) — mỗi đơn **một** hội thoại cho mỗi loại.

**FR-025**  
WHERE hai bên cùng bấm mở hội thoại đồng thời và DB ném `DataIntegrityViolationException`, THE system
SHALL bắt exception, tìm lại bản ghi đã tồn tại và trả về — người dùng SHALL không thấy lỗi race
condition.

**FR-026**  
WHERE bắt được exception nhưng vẫn không tìm thấy bản ghi, THE system SHALL rethrow exception gốc.

**FR-027**  
WHERE `type` không thuộc `{CUSTOMER_MANAGER, MANAGER_DRIVER, CUSTOMER_DRIVER}`, THE system SHALL trả
HTTP 422 `INVALID_CONVERSATION_TYPE` "Loại hội thoại không hợp lệ."

---

### Nhóm 6 — Đọc tin nhắn (FR-028..FR-033)

**FR-028**  
WHEN user gọi `GET /api/chat/conversations/{id}/messages?page={p}&size={s}`, THE system SHALL trả
`Page<ChatMessageResponse>` sắp xếp `created_at` **giảm dần** (mới nhất trước).

**FR-029**  
WHEN `size` không truyền, THE system SHALL dùng default `30`; THE system SHALL clamp `page` về
`max(page, 0)` và `size` về `min(max(size,1), 100)` — **không** trả lỗi validation cho giá trị ngoài
khoảng (khác các endpoint khác trong dự án, xem DS-05).

**FR-030**  
WHEN user mở hội thoại, THE system SHALL **tự động** đánh dấu đã đọc mọi tin nhắn của người khác trong
hội thoại đó (`markConversationRead`) — mở = đã đọc.

**FR-031**  
WHEN trả mỗi tin nhắn, THE system SHALL bao gồm `mine = (senderId == me.id)` để frontend căn trái/phải
mà không cần so sánh id.

**FR-032**  
WHERE tin nhắn có `image_public_id`, THE system SHALL sinh **signed URL** cho ảnh; WHERE
`image_public_id` là `null`, SHALL trả `imageUrl = null`.

**FR-033**  
WHERE hội thoại không tồn tại, THE system SHALL trả HTTP 404 `CONVERSATION_NOT_FOUND` "Không tìm thấy
hội thoại."

---

### Nhóm 7 — Gửi tin nhắn (FR-034..FR-041)

**FR-034**  
WHEN user gọi `POST /api/chat/conversations/{id}/messages` với `content` hợp lệ, THE system SHALL lưu
`chat_message`, cập nhật `conversation.last_message_text` + `last_message_at`, đẩy realtime tới người
nhận, và trả `ChatMessageResponse` với `mine = true`.

**FR-035**  
WHERE `content` là `null` hoặc rỗng sau trim, THE system SHALL trả HTTP 422 `EMPTY_MESSAGE` "Nội dung
tin nhắn không được để trống."

**FR-036**  
WHEN lưu preview `last_message_text`, THE system SHALL cắt còn tối đa **200 ký tự**.

**FR-037**  
WHEN user gọi `POST /api/chat/conversations/{id}/images` với multipart `file`, THE system SHALL upload
Cloudinary signed upload, lưu tin nhắn với `content = ""` + `image_public_id`, set preview
`last_message_text = "🖼 Hình ảnh"`, và trả response kèm signed `imageUrl` (AC-10).

**FR-038**  
WHEN đẩy realtime, THE system SHALL gửi tới **user-destination** `/user/{userId}/queue/messages` của
từng người nhận; người gửi SHALL bị loại khỏi danh sách nhận.

**FR-039**  
WHEN xác định người nhận: với `CUSTOMER_DRIVER` → bên còn lại của đơn; với `MANAGER_DRIVER` → nếu tài xế
gửi thì **mọi Manager ACTIVE**, nếu quản lý gửi thì tài xế; với `CUSTOMER_MANAGER` → nếu khách gửi thì
**mọi Manager ACTIVE**, nếu quản lý gửi thì khách.

**FR-040**  
WHEN user gọi `POST /api/chat/conversations/{id}/read`, THE system SHALL đánh dấu đã đọc thủ công (cho
trường hợp user focus lại tab) và trả `{"success": true}`.

**FR-041**  
WHEN user gọi `GET /api/chat/unread-count`, THE system SHALL trả `{"unreadCount": N}` = tổng tin chưa
đọc trong mọi hội thoại user tham gia; WHERE user không có hội thoại nào, SHALL trả `0`.

---

### Nhóm 8 — Danh bạ tài xế (FR-042..FR-044)

**FR-042**  
WHEN Manager/Admin gọi `GET /api/chat/directory/drivers`, THE system SHALL trả
`List<DriverDirectoryItem>` gồm `id`, `fullName`, `phone` của mọi tài xế `ACTIVE` chưa xoá mềm.

**FR-043**  
WHEN sắp xếp danh bạ, THE system SHALL sort theo `fullName` tăng dần, **nulls last**.

**FR-044**  
WHERE role khác `MANAGER`/`ADMIN`, THE system SHALL trả HTTP 403 —
`@PreAuthorize("hasAnyRole('MANAGER','ADMIN')")` (HR-10).

---

### Nhóm 9 — Phân quyền tham gia (FR-045..FR-048)

**FR-045**  
WHILE mọi thao tác trên hội thoại (đọc, gửi, đánh dấu đã đọc) chạy, THE system SHALL kiểm tra
`assertParticipant` trước; WHERE không thoả, SHALL trả HTTP 403 "Bạn không có quyền truy cập hội thoại
này." (HR-10).

**FR-046**  
WHILE user là `MANAGER`/`ADMIN`, THE system SHALL cho phép tham gia hội thoại type `CUSTOMER_MANAGER`
hoặc `MANAGER_DRIVER` **bất kỳ**; WHERE hội thoại là `CUSTOMER_DRIVER`, SHALL từ chối — quản lý không
đọc được chat riêng khách↔tài xế.

**FR-047**  
WHILE user là `CUSTOMER`, THE system SHALL chỉ cho phép khi `conversation.customer_id` = user id;
WHILE user là `DRIVER`, chỉ khi `conversation.driver_id` = user id.

**FR-048**  
WHERE `@AuthenticationPrincipal` là `null` (JWT thiếu/không hợp lệ), THE system SHALL trả HTTP 401
`AUTHENTICATION_REQUIRED` "Vui lòng đăng nhập để tiếp tục."

---

### Nhóm 10 — Realtime WebSocket (FR-049..FR-052)

**FR-049**  
WHILE ứng dụng chạy, THE system SHALL đăng ký STOMP endpoint `/ws` với SockJS fallback, broker
in-memory prefix `/queue`, application prefix `/app`, user-destination prefix `/user` (AC-05).

**FR-050**  
WHEN client handshake WebSocket, THE system SHALL xác thực JWT tại STOMP `CONNECT` qua
`WebSocketAuthChannelInterceptor`; WHERE JWT không hợp lệ, SHALL từ chối kết nối.

**FR-051**  
WHILE cấu hình origin cho WebSocket, THE system SHALL dùng whitelist tường minh (`localhost:3000`,
`localhost:5500`, `127.0.0.1:5500`, `localhost:8080`) — SHALL **không** dùng `"*"` (AC-11).

**FR-052**  
WHILE tin nhắn được gửi, THE system SHALL **luôn** lưu DB trước rồi mới đẩy realtime — WHERE WebSocket
lỗi hoặc client offline, tin nhắn SHALL vẫn tồn tại và hiển thị khi client load lại lịch sử (AC-05).

---

## Non-Functional Requirements

| ID | Yêu cầu | Ngưỡng |
|----|---------|--------|
| NFR-001 | `GET /conversations` p95 | < 800 ms |
| NFR-002 | `GET /messages` (size=30) p95 | < 500 ms |
| NFR-003 | `POST /messages` p95 | < 400 ms |
| NFR-004 | Độ trễ realtime (gửi → nhận) | < 1000 ms trong LAN |
| NFR-005 | `GET /unread-count` p95 | < 300 ms (dùng partial index) |
| NFR-006 | Upload ảnh chat p95 | < 3000 ms |
| NFR-007 | Pagination | Default 30, max 100 (AC-15) |
| NFR-008 | Timestamp | `TIMESTAMPTZ` UTC, hiển thị `Asia/Ho_Chi_Minh` (AC-07) |
| NFR-009 | Empty/Loading/Error states | Bắt buộc (AC-16) |
| NFR-010 | Vietnamese diacritics | 100% text user-facing (HR-20) |
| NFR-011 | Fallback | FE có polling lưới an toàn khi WS rớt (AC-05) |
| NFR-012 | Broker | In-memory — chấp nhận mất realtime khi restart; DB là nguồn bền vững |

---

## API Endpoints Summary

| Method | Path | Auth | Request | Response | Ghi chú |
|--------|------|------|---------|----------|---------|
| GET | `/api/chat/conversations` | Any | — | 200 `List<ConversationResponse>` | Theo vai trò |
| POST | `/api/chat/conversations/open` | Any | `OpenConversationRequest{orderId, driverId, type}` | 200 `ConversationResponse` | Idempotent |
| GET | `/api/chat/conversations/{id}/messages` | Participant | `page`, `size` | 200 `Page<ChatMessageResponse>` | Auto mark read |
| POST | `/api/chat/conversations/{id}/messages` | Participant | `SendMessageRequest{content}` | 200 `ChatMessageResponse` | + realtime |
| POST | `/api/chat/conversations/{id}/images` | Participant | multipart `file` | 200 `ChatMessageResponse` | 1 ảnh/tin |
| POST | `/api/chat/conversations/{id}/read` | Participant | — | 200 `{success:true}` | |
| GET | `/api/chat/unread-count` | Any | — | 200 `{unreadCount:N}` | Badge |
| GET | `/api/chat/directory/drivers` | MANAGER, ADMIN | — | 200 `List<DriverDirectoryItem>` | |

### WebSocket

| Mục | Giá trị |
|-----|---------|
| Endpoint handshake | `/ws` (SockJS fallback) |
| Auth | JWT tại STOMP `CONNECT` |
| Broker | In-memory, prefix `/queue` |
| Client subscribe | `/user/queue/messages` |
| Server publish | `/user/{userId}/queue/messages` |

### Standard Error (ES-04)

```json
{
  "error_code": "ORDER_NO_DRIVER",
  "message": "Đơn chưa có tài xế nên chưa thể nhắn tin.",
  "details": []
}
```

---

## Data Model

### Schema Design

Chat cần 2 bảng mới, chia thành 2 migration theo thứ tự triển khai:

| Migration | Nội dung |
|-----------|----------|
| `V36__create_chat_tables.sql` | `conversation`, `chat_message`, 5 indexes |
| `V38__chat_message_image.sql` | `chat_message.image_public_id TEXT` (mở rộng gửi ảnh) |

Chat **không** tạo bảng ledger hay audit riêng — không có dữ liệu tiền (AC-08 N/A, AC-13 N/A).

### Table `conversation` (V36)

| Column | Type | Ghi chú |
|--------|------|---------|
| `id` | `UUID` PK | |
| `order_id` | `UUID` → `service_order(id)` | NULL = kênh hỗ trợ chung |
| `type` | `VARCHAR(20)` NOT NULL | `CHECK IN ('CUSTOMER_MANAGER','MANAGER_DRIVER','CUSTOMER_DRIVER')` — AC-14 |
| `customer_id` | `UUID` → `app_user(id)` | NULL với `MANAGER_DRIVER` |
| `driver_id` | `UUID` → `app_user(id)` | NULL với `CUSTOMER_MANAGER` |
| `last_message_text` | `TEXT` | Preview ≤ 200 ký tự |
| `last_message_at` | `TIMESTAMPTZ` | Dùng để sort |
| `created_at` | `TIMESTAMPTZ` NOT NULL | |

**Indexes:**
- `uq_conversation_order_type` — **partial UNIQUE** `(order_id, type) WHERE order_id IS NOT NULL`
- `uq_conversation_support` — **partial UNIQUE** `(customer_id) WHERE type='CUSTOMER_MANAGER' AND order_id IS NULL`
- `idx_conversation_customer` — `(customer_id, last_message_at DESC)`
- `idx_conversation_driver` — `(driver_id, last_message_at DESC)`
- `idx_conversation_type` — `(type, last_message_at DESC)`

> ⚠️ **Ràng buộc còn thiếu:** kênh chung `MANAGER_DRIVER` cần một partial unique index tương ứng
> (`driver_id` WHERE `type='MANAGER_DRIVER' AND order_id IS NULL`), song song với
> `uq_conversation_support` của khách. Xem DS-01.

### Table `chat_message` (V36 + V38)

| Column | Type | Ghi chú |
|--------|------|---------|
| `id` | `UUID` PK | |
| `conversation_id` | `UUID` NOT NULL → `conversation(id)` | |
| `sender_id` | `UUID` NOT NULL → `app_user(id)` | |
| `content` | `TEXT` NOT NULL | `""` với tin nhắn ảnh |
| `image_public_id` | `TEXT` | V38; NULL = tin text |
| `read_at` | `TIMESTAMPTZ` | NULL = chưa đọc |
| `created_at` | `TIMESTAMPTZ` NOT NULL | |

**Indexes:**
- `idx_chat_message_conversation` — `(conversation_id, created_at)`
- `idx_chat_message_unread` — **partial** `(conversation_id, sender_id) WHERE read_at IS NULL`

### Ma trận loại hội thoại

| Type | `order_id` | `customer_id` | `driver_id` | Ai mở được |
|------|-----------|---------------|-------------|------------|
| `CUSTOMER_MANAGER` (chung) | NULL | Khách | NULL | Chỉ CUSTOMER |
| `CUSTOMER_MANAGER` (theo đơn) | Đơn | `order.customerId` | NULL | Manager/Admin hoặc khách của đơn |
| `MANAGER_DRIVER` (chung) | NULL | NULL | Tài xế | Driver (của mình) hoặc Manager/Admin (chọn tài xế) |
| `MANAGER_DRIVER` (theo đơn) | Đơn | NULL | `order.driverId` | Manager/Admin hoặc tài xế của đơn |
| `CUSTOMER_DRIVER` | **Bắt buộc** | `order.customerId` | `order.driverId` | Chỉ khách/tài xế của đơn |

---

## Permission Matrix

| Hành động | CUSTOMER | DRIVER | MANAGER | ADMIN | GUEST |
|-----------|----------|--------|---------|-------|-------|
| Mở kênh hỗ trợ chung Customer↔Manager | ✅ (của mình) | ❌ | ❌ | ❌ | ❌ |
| Mở kênh chung Manager↔Driver | ❌ | ✅ (của mình) | ✅ (chọn tài xế) | ✅ | ❌ |
| Mở chat theo đơn Customer↔Manager | ✅ (đơn mình) | ❌ | ✅ | ✅ | ❌ |
| Mở chat theo đơn Manager↔Driver | ❌ | ✅ (đơn mình) | ✅ | ✅ | ❌ |
| Mở chat Customer↔Driver | ✅ (đơn mình) | ✅ (đơn mình) | ❌ | ❌ | ❌ |
| Xem mọi hội thoại C↔M và M↔D | ❌ | ❌ | ✅ | ✅ | ❌ |
| Xem hội thoại C↔D của người khác | ❌ | ❌ | ❌ | ❌ | ❌ |
| Danh bạ tài xế | ❌ | ❌ | ✅ | ✅ | ❌ |

> **Lưu ý quyền riêng tư:** Manager/Admin **không** đọc được chat riêng Customer↔Driver (FR-046).
> Đây là ranh giới có chủ ý.

---

## Transaction Boundaries

### Mở hội thoại

```
BEGIN  -- ChatService.openConversation @Transactional
  assert ConversationType.isValid(type)         -- 422 nếu sai
  switch (type):
    CUSTOMER_MANAGER + orderId==null → openSupport(me)
       assert me.role == CUSTOMER               -- 403
       findByCustomerIdAndTypeAndOrderIdIsNull() ?? createConversation()
    CUSTOMER_MANAGER + orderId!=null → openCustomerManagerByOrder(me, orderId)
       order = loadOrder(orderId)               -- 404 nếu không thấy
       assert isStaff || me.id == order.customerId   -- 403
       findByOrderIdAndType() ?? createConversation()
    CUSTOMER_DRIVER → openCustomerDriver(me, requireOrderId(orderId))
       assert order.driverId != null            -- 409 ORDER_NO_DRIVER
       assert me.id ∈ {order.customerId, order.driverId}   -- 403
    MANAGER_DRIVER + orderId!=null → openManagerDriverByOrder(...)
    MANAGER_DRIVER + orderId==null → openManagerDriverGeneral(me, driverId)

  createConversation():
    try: INSERT conversation
    catch DataIntegrityViolationException:      -- race: 2 bên cùng mở
        return existing ?? rethrow              -- FR-025/FR-026
COMMIT
```

### Gửi tin nhắn

```
BEGIN  -- ChatService.sendMessage @Transactional
  conv = loadConversation(id)                   -- 404
  assertParticipant(conv, me)                   -- 403
  content = trim(rawContent)
  assert !content.isEmpty()                     -- 422 EMPTY_MESSAGE
  INSERT chat_message(conversationId, senderId, content, createdAt)
  conv.lastMessageText = truncate(content, 200)
  conv.lastMessageAt   = NOW()
  UPDATE conversation
  realtimePublisher.publishNewMessage(resolveRecipients(conv, me.id), payload)
COMMIT  → trả response với mine=true
```

> ⚠️ **Ghi nhận:** realtime publish nằm **trong** transaction — nếu transaction rollback sau khi
> publish, người nhận đã thấy tin nhắn không tồn tại trong DB. Rủi ro thấp (publish gần cuối), nhưng
> xem DS-04.

### Gửi ảnh

```
BEGIN  -- ChatService.sendImage @Transactional
  conv = loadConversation(id); assertParticipant(conv, me)
  publicId = chatImageService.upload(conversationId, file)   -- Cloudinary, ngoài DB
  INSERT chat_message(content="", imagePublicId=publicId)
  conv.lastMessageText = "🖼 Hình ảnh"; conv.lastMessageAt = NOW()
  imageUrl = chatImageService.signUrl(publicId)
  publish realtime
COMMIT
```

> ⚠️ Upload Cloudinary xảy ra **trong** transaction DB — giữ transaction mở suốt thời gian upload
> (có thể vài giây). Xem DS-03.

### Đọc tin nhắn

```
BEGIN  -- ChatService.getMessages @Transactional (KHÔNG readOnly — vì có UPDATE read_at)
  conv = loadConversation(id); assertParticipant(conv, me)
  page = findByConversationIdOrderByCreatedAtDesc(id, pageable)
  markConversationRead(id, me.id, NOW())        -- UPDATE read_at
  map → ChatMessageResponse (kèm signUrl cho ảnh)
COMMIT
```

---

## Error Matrix

| HTTP | error_code | Khi nào | Message |
|------|-----------|---------|---------|
| 401 | `AUTHENTICATION_REQUIRED` | `@AuthenticationPrincipal` null | "Vui lòng đăng nhập để tiếp tục." |
| 403 | `FORBIDDEN` | Không phải participant | "Bạn không có quyền truy cập hội thoại này." |
| 403 | `FORBIDDEN` | Non-customer mở kênh hỗ trợ chung | "Chỉ khách hàng mới mở kênh hỗ trợ với quản lý." |
| 403 | `FORBIDDEN` | Không thuộc đơn (C↔D) | "Bạn không thuộc đơn này." |
| 403 | `FORBIDDEN` | Không có quyền mở hội thoại | "Bạn không có quyền mở hội thoại này." |
| 404 | `ORDER_NOT_FOUND` | Đơn không tồn tại/xoá mềm | "Không tìm thấy đơn hàng." |
| 404 | `CONVERSATION_NOT_FOUND` | Hội thoại không tồn tại | "Không tìm thấy hội thoại." |
| 404 | `DRIVER_NOT_FOUND` | `driverId` không tồn tại/không phải tài xế | "Không tìm thấy tài xế." |
| 409 | `ORDER_NO_DRIVER` | Đơn chưa có tài xế | "Đơn chưa có tài xế nên chưa thể nhắn tin." |
| 422 | `INVALID_CONVERSATION_TYPE` | `type` sai | "Loại hội thoại không hợp lệ." |
| 422 | `ORDER_ID_REQUIRED` | C↔D thiếu `orderId` | "Thiếu mã đơn cho hội thoại theo đơn." |
| 422 | `DRIVER_ID_REQUIRED` | Manager mở kênh chung thiếu `driverId` | "Vui lòng chọn tài xế để nhắn tin." |
| 422 | `EMPTY_MESSAGE` | `content` rỗng | "Nội dung tin nhắn không được để trống." |
| 422 | `INVALID_FILE` | Ảnh không hợp lệ | (từ `ChatImageService`) |

---

## Frontend Screen Contract

### `messages.html` — "Tin nhắn" (dùng chung cho cả 4 vai trò)

| Thành phần | Nguồn dữ liệu | Ghi chú |
|------------|---------------|---------|
| Danh sách hội thoại (trái) | `GET /api/chat/conversations` | Sort `lastMessageAt` DESC, nulls last |
| Badge chưa đọc/hội thoại | `unreadCount` trong item | |
| Tên đối phương | `counterpartName` | Khách/tài xế thấy "Quản lý Move_home" |
| Nhãn đơn | `orderCode` nếu `orderId != null` | Kênh chung không hiện |
| Khung tin nhắn (phải) | `GET /conversations/{id}/messages?page&size=30` | Mới nhất trước → FE đảo lại |
| Căn trái/phải | `mine` boolean | Không tự so id |
| Ảnh | `imageUrl` (signed) | Hết hạn → reload |
| Gửi text | `POST /conversations/{id}/messages` | |
| Gửi ảnh | `POST /conversations/{id}/images` (multipart) | |
| Realtime | STOMP subscribe `/user/queue/messages` | JWT ở CONNECT |
| Polling lưới an toàn | Gọi lại `/conversations` định kỳ khi WS rớt | AC-05 |
| Badge nav | `GET /api/chat/unread-count` | |
| Danh bạ tài xế (Manager) | `GET /api/chat/directory/drivers` | Mở kênh chung M↔D |
| Loading | "Đang tải..." | AC-16 |
| Empty | "Chưa có hội thoại nào" | AC-16 |
| Error | "Không thể tải dữ liệu" + "Thử lại" | AC-16 |

### Deep-link vào chat

| Từ màn | Loại hội thoại | Ghi chú |
|--------|----------------|---------|
| `customer/order-detail.html` | `CUSTOMER_DRIVER` hoặc `CUSTOMER_MANAGER` theo đơn | Nút "Nhắn tin tài xế" |
| `driver/order-detail.html` | `CUSTOMER_DRIVER` / `MANAGER_DRIVER` theo đơn | |
| `manager/dispute-detail.html` | `CUSTOMER_MANAGER` theo đơn | Xử lý tranh chấp |

---

## Security & Privacy

| Chủ đề | Quy tắc |
|--------|---------|
| Participant check | Mọi thao tác đều gọi `assertParticipant` trước (FR-045) |
| Quyền riêng tư C↔D | Manager/Admin **không** đọc được chat riêng khách↔tài xế (FR-046) |
| Ownership kênh chung | Tài xế mở kênh chung luôn là của chính mình — `driverId` gửi lên bị bỏ qua (FR-021) |
| WebSocket auth | JWT verify tại STOMP `CONNECT`, không phải chỉ ở handshake HTTP |
| User destination | `/user/{id}/queue/messages` — Spring định tuyến theo principal, user không sub được queue người khác |
| CORS/Origin | Whitelist tường minh, không `"*"` (AC-11, FR-051) |
| Ảnh Cloudinary | `type = "authenticated"` + signed URL (AC-10) |
| Secrets | Cloudinary + JWT secret qua env (HR-01) |
| PII | Danh bạ tài xế trả `phone` — chỉ Manager/Admin (FR-044) |

---

## Acceptance Criteria

| ID | Tiêu chí | Cách verify |
|----|----------|-------------|
| AC-019-01 | Customer mở kênh hỗ trợ chung → tạo 1 thread | E2E |
| AC-019-02 | Customer mở kênh chung lần 2 → trả thread cũ (không tạo mới) | DB check |
| AC-019-03 | Driver mở kênh hỗ trợ chung Customer↔Manager → 403 | Test |
| AC-019-04 | Customer mở C↔D khi đơn chưa có tài xế → 409 | Test |
| AC-019-05 | Manager mở C↔D → 403 | Test |
| AC-019-06 | Manager đọc hội thoại C↔D → 403 | Test |
| AC-019-07 | Manager thấy mọi hội thoại C↔M và M↔D | Test |
| AC-019-08 | Manager mở kênh chung M↔D không truyền `driverId` → 422 | Test |
| AC-019-09 | Driver mở kênh chung M↔D với `driverId` người khác → vẫn ra kênh của mình | Test |
| AC-019-10 | 2 bên cùng mở hội thoại theo đơn → chỉ 1 row, không lỗi | Concurrency test |
| AC-019-11 | Gửi tin rỗng → 422 | Test |
| AC-019-12 | Mở hội thoại → tin của người khác được `read_at` | DB check |
| AC-019-13 | Tin nhắn đẩy realtime tới đúng người nhận | E2E 2 browser |
| AC-019-14 | Tài xế gửi trong M↔D → mọi Manager ACTIVE nhận | E2E |
| AC-019-15 | Người gửi không nhận lại tin của chính mình qua WS | E2E |
| AC-019-16 | Gửi ảnh → `content=""`, preview "🖼 Hình ảnh", `imageUrl` signed | DB + response |
| AC-019-17 | Ảnh chỉ xem được qua signed URL | Thử URL công khai → 401 |
| AC-019-18 | `unread-count` khớp tổng badge từng hội thoại | Test |
| AC-019-19 | WS rớt → FE polling vẫn nhận tin | Manual |
| AC-019-20 | Non-Manager gọi danh bạ tài xế → 403 | Test RBAC |
| AC-019-21 | 100% text có dấu tiếng Việt | Manual |

---

## Edge Cases & Error Handling

1. **Hai bên cùng bấm mở hội thoại theo đơn** → unique index chặn, code bắt `DataIntegrityViolationException`
   và trả bản đã tồn tại (FR-025). Người dùng không thấy lỗi.
2. **Đơn bị xoá mềm sau khi hội thoại đã tạo** → hội thoại vẫn tồn tại; `orderCode` trong danh sách sẽ
   `null` vì `findAllById` không trả đơn đã xoá. Chat vẫn đọc được.
3. **Tài xế bị đổi giữa chừng** (sự cố → tài xế mới, Spec #023) → hội thoại `CUSTOMER_DRIVER` cũ vẫn giữ
   `driver_id` cũ; tài xế mới mở hội thoại thì unique `(order_id, type)` **chặn** → trả hội thoại cũ →
   **tài xế mới không nhắn được, tài xế cũ vẫn đọc được**. Xem DS-02 — cần giải quyết trước khi Spec #023 lên production.
4. **Manager gửi tin trong `CUSTOMER_MANAGER`** → khách nhận; nhưng **Manager khác không nhận realtime**
   (chỉ người gửi biết). Manager khác thấy khi load lại danh sách.
5. **Nhiều Manager cùng trả lời một khách** → không có cơ chế "đang được xử lý bởi ai"; tin nhắn hiển
   thị chung tên người gửi thật, nhưng khách luôn thấy "Quản lý Move_home".
6. **Broker in-memory restart** → mất kết nối WS, client SockJS tự reconnect; tin nhắn không mất vì đã
   lưu DB (FR-052).
7. **`size = 1000`** → clamp về 100, không lỗi (FR-029).
8. **`page = -5`** → clamp về 0, không lỗi (FR-029).
9. **Tin nhắn ảnh có `content = ""`** → cột `content` NOT NULL nên lưu chuỗi rỗng, không phải NULL.
10. **User bị SUSPENDED** → vẫn chat được nếu JWT còn hạn; không có guard trạng thái trong chat.

---

## Test Cases

| ID | Loại | Mô tả | Kỳ vọng |
|----|------|-------|---------|
| TC-019-01 | Unit | `ConversationType.isValid("FOO")` | false |
| TC-019-02 | Unit | `counterpartName` cho CUSTOMER xem C↔M | "Quản lý Move_home" |
| TC-019-03 | Unit | `counterpartName` cho MANAGER xem M↔D | Tên tài xế |
| TC-019-04 | Unit | `resolveRecipients` khi tài xế gửi M↔D | Mọi Manager ACTIVE |
| TC-019-05 | Unit | `resolveRecipients` loại người gửi | Không chứa senderId |
| TC-019-06 | Unit | Preview cắt 200 ký tự | length == 200 |
| TC-019-07 | Integration | `openSupport` bởi CUSTOMER | Tạo 1 thread |
| TC-019-08 | Integration | `openSupport` lần 2 | Trả thread cũ |
| TC-019-09 | Integration | `openSupport` bởi DRIVER | 403 |
| TC-019-10 | Integration | `openCustomerDriver` không `orderId` | 422 |
| TC-019-11 | Integration | `openCustomerDriver` đơn chưa có tài xế | 409 |
| TC-019-12 | Integration | `openCustomerDriver` bởi người ngoài đơn | 403 |
| TC-019-13 | Integration | `openManagerDriverGeneral` bởi MANAGER thiếu driverId | 422 |
| TC-019-14 | Integration | `openManagerDriverGeneral` với driverId không phải DRIVER | 404 |
| TC-019-15 | Integration | `openManagerDriverGeneral` bởi CUSTOMER | 403 |
| TC-019-16 | Integration | `assertParticipant` MANAGER với C↔D | 403 |
| TC-019-17 | Integration | `sendMessage` content rỗng | 422 |
| TC-019-18 | Integration | `getMessages` → `read_at` được set | DB check |
| TC-019-19 | Integration | `unreadCount` không tính tin của chính mình | Đúng số |
| TC-019-20 | Integration | Danh bạ tài xế bởi CUSTOMER | 403 |
| TC-019-21 | Concurrency | 2 thread cùng `openConversation` theo đơn | 1 row, không exception |
| TC-019-22 | Integration | `sendImage` | `content=""`, `imagePublicId` set |

---

## Deferred Scope

> Các hạng mục dưới đây **nằm ngoài phạm vi bản 1.0.0** và được hoãn có chủ ý sang bản sau, kèm lý do
> và hướng xử lý. Danh sách này là đầu vào cho backlog, không phải yêu cầu bắt buộc của bản này.

| ID | Hạng mục | Rủi ro nếu bỏ qua lâu | Hướng xử lý |
|----|----------|------------------------|-------------|
| DS-01 | Partial unique index cho kênh chung `MANAGER_DRIVER` (`driver_id` WHERE `type='MANAGER_DRIVER' AND order_id IS NULL`), song song `uq_conversation_support` | Hai request đồng thời tạo 2 kênh chung cho cùng tài xế; cơ chế bắt `DataIntegrityViolationException` ở FR-025 không kích hoạt vì không có index để vi phạm | Thêm index (cần migration mới) |
| DS-02 | **Xử lý hội thoại khi đơn đổi tài xế** (Spec #023: sự cố → tài xế mới). `conversation.driver_id` giữ giá trị cũ; unique `(order_id, type)` chặn tạo hội thoại mới → tài xế mới không nhắn được, tài xế cũ vẫn đọc được chat của đơn không còn thuộc mình | **Ưu tiên cao nhất** — vừa lỗi chức năng vừa lỗi quyền riêng tư | Khi reassign: cập nhật `conversation.driver_id` hoặc đóng hội thoại cũ + tạo mới. Xem OQ-1 |
| DS-03 | Đưa upload Cloudinary ra ngoài `@Transactional` của `sendImage` | Giữ transaction DB mở suốt thời gian upload → chiếm connection pool (max 5 theo `application.properties`) | Upload trước, chỉ mở TX khi INSERT |
| DS-04 | Chuyển `realtimePublisher.publishNewMessage` sang `TransactionSynchronization.afterCommit` | Nếu TX rollback sau khi publish, người nhận thấy tin nhắn không tồn tại trong DB | Đăng ký callback afterCommit |
| DS-05 | Thống nhất chính sách validate `page`/`size` toàn dự án — chat clamp, các endpoint tiền trả 422 | Contract API không nhất quán giữa các module | Chọn một chuẩn, áp cho tất cả |
| DS-06 | Cleanup `chat_message` > 90 ngày (AC-09 cho phép hard delete) | Bảng phình vô hạn; Neon free tier 0.5 GB | Scheduled job dọn định kỳ |
| DS-07 | Bổ sung `messages.html` vào `SCREEN_INVENTORY.md` (inventory hiện chỉ nhắc "chat - Sprint 6 nếu có" ở màn 3.10) | Số màn hình báo cáo thiếu so với thực tế | Cập nhật inventory |
| DS-08 | Đồng bộ `CONTEXT.md` §2 Chat và AC-05 với mô hình 3 cấp — phần thân CONTEXT vẫn còn mô tả 1 cấp kèm banner đính chính | Người đọc lướt qua banner sẽ hiểu sai phạm vi | Viết lại §2 Chat thay vì chỉ thêm banner. Xem OQ-2 |
| DS-09 | Cleanup ảnh chat trên Cloudinary (AC-10 yêu cầu cleanup asset) | Ảnh rác tích tụ trên free tier 25 GB | Job dọn theo `chat_message` đã xoá |
| DS-10 | Chính sách xoá cho `conversation` — AC-09 cho phép hard delete `chat_message` nhưng chưa quy định cho `conversation` | Không rõ vòng đời hội thoại khi dọn dữ liệu | Làm rõ trong AC-09 |
| DS-11 | Rate limit cho `POST /conversations/{id}/messages` theo HR-16 (60 req/IP/phút) | Chat có thể bị spam; hiện chỉ dựa vào participant check | Áp rate limit chung hoặc xin ngoại lệ HR-16 |

---

## Open Questions

| # | Câu hỏi | Block | Ưu tiên |
|---|---------|-------|---------|
| OQ-1 | **Khi đơn đổi tài xế, hội thoại `CUSTOMER_DRIVER` xử lý thế nào?** (DS-02) — cập nhật `driver_id`, đóng hội thoại cũ rồi tạo mới, hay giữ lịch sử và tạo hội thoại thứ hai? | DS-02 | **High** |
| OQ-2 | Viết lại `CONTEXT.md` §2 Chat cho khớp mô hình 3 cấp, hay giữ banner đính chính? (DS-08) | Tài liệu | High |
| OQ-3 | Admin có quyền chat ngang Manager không? CONTEXT §3 RBAC ghi "Chat ho tro: Admin **Yes**" — spec này theo đó (FR-004, FR-046) | — | Medium |
| OQ-4 | User `SUSPENDED` có được chat không? Spec này không quy định guard trạng thái | — | Medium |
| OQ-5 | Chu kỳ cleanup `chat_message` (AC-09 cho phép hard delete > 90 ngày)? | DS-06 | Medium |
| OQ-6 | Nhiều Manager cùng trả lời một khách — có cần cơ chế "claim" hội thoại không? | — | Low |

---

## Rollout Plan

**Thứ tự triển khai:**

1. `V36` — 2 bảng + 5 index. Không đụng bảng hiện có, không cần backfill.
2. Backend: package `chat` (entity, repository, service, controller) + `WebSocketConfig` +
   `WebSocketAuthChannelInterceptor`.
3. Frontend: `messages.html` + `js/chat.js` + SockJS/STOMP client. Polling lưới an toàn bật ngay từ
   đầu để chat dùng được kể cả khi WebSocket lỗi (AC-05).
4. `V38` — mở rộng gửi ảnh, triển khai sau khi luồng text đã ổn định.

**Rủi ro cần theo dõi khi rollout:**

- AC-05 xếp chat là feature rủi ro thời gian cao nhất; nếu chậm tiến độ → hạ xuống polling 30s và cắt
  `V38` (gửi ảnh), giữ luồng text.
- Broker in-memory: mất kết nối khi restart app; client SockJS tự reconnect, tin nhắn không mất vì đã
  lưu DB (FR-052).
- DS-02 (đơn đổi tài xế) phải được giải quyết **trước** khi Spec #023 lên production, vì hai tính năng
  giao nhau ở đúng chỗ này.

---

## Constitution Compliance

=== CONSTITUTION CHECK REPORT ===  
Feature  : #19 Chat Messaging (3 cấp)  
Artifact : spec  
Date     : 2026-06-04

**LAYER 1 — HARD RULES**

| Rule | Kết quả | Ghi chú |
|------|---------|---------|
| HR-01 Secrets không commit | PASS | JWT + Cloudinary qua env |
| HR-02 BCrypt | N/A | |
| HR-03/04 IPN | N/A | |
| HR-05 Transition sai → 409 | N/A | Chat không có state machine |
| HR-06/07 DamageReport | N/A | |
| HR-08 Driver concurrency | N/A | |
| HR-09 IPN timeout | N/A | |
| HR-10 Trái quyền → 403 | PASS | `assertParticipant` mọi thao tác (FR-045..FR-048) |
| HR-11 Email không rollback | N/A | |
| HR-12 Driver onboarding | N/A | |
| HR-13 Audit log | N/A | Chat không đổi state nghiệp vụ |
| HR-14 RefundRecord | N/A | |
| HR-15 Idempotency | PASS | Unique index + catch race (FR-024..FR-026) |
| HR-16 Rate limit | ⚠️ **EXCEPTION** | Spec này **không** đặc tả rate limit cho `POST /messages`, trong khi HR-16 quy định "Mọi POST endpoint khác áp dụng rate limit chung: 60 req/IP/phút". Cần leader duyệt ngoại lệ hoặc bổ sung — xem DS-11 |
| HR-17 Public vs Authenticated | PASS | Không endpoint public; `/ws` yêu cầu JWT ở CONNECT |
| HR-18 Wallet | N/A | |
| HR-19 Brand identity | PASS | |
| HR-20 Tiếng Việt có dấu | PASS | Toàn bộ message **có dấu đầy đủ** |
| HR-21 Tránh reserved words | PASS | `conversation`, `chat_message` — không phải reserved word |

**Layer 1 Result:** 1 gap — HR-16 (thiếu rate limit chat).

**LAYER 2 — ARCHITECTURAL CONSTRAINTS**

| Rule | Kết quả | Ghi chú |
|------|---------|---------|
| AC-01 Stack | PASS | Vanilla JS + SockJS client |
| AC-02 REST thuần + WebSocket chỉ cho chat | PASS | WS **chỉ** dùng cho chat, data fetching vẫn REST |
| AC-03 JWT | PASS | Cả REST và STOMP CONNECT |
| AC-04 Không nối chuỗi SQL | PASS | JPA |
| AC-05 Chat STOMP+SockJS + lưu DB + fallback | ✅ **PASS** | Đúng yêu cầu: STOMP+SockJS ✅, in-memory broker ✅, lưu DB ngay ✅, FE có polling ✅. **Lệch:** AC-05 mô tả chat là "Customer↔Manager" (Feature #20); thực tế 3 cấp — lệch **có chủ ý**, đã ghi ở `CLAUDE.md` §4 |
| AC-06 Maps | N/A | |
| AC-07 Timezone | PASS | `OffsetDateTime.now(ZoneOffset.UTC)` |
| AC-08 BigDecimal | N/A | Chat không có tiền |
| AC-09 Soft delete | ⚠️ **EXCEPTION** | `conversation`/`chat_message` không có `deleted_at`. AC-09 **cho phép** hard delete `chat_message` > 90 ngày, nhưng chưa implement cleanup (DS-06) |
| AC-10 Cloudinary signed upload | ⚠️ **PARTIAL** | Signed upload ✅, `authenticated` ✅, signed URL ✅. **Thiếu cleanup** (DS-09) |
| AC-11 CORS whitelist | PASS | WS origin whitelist tường minh, không `"*"` (FR-051) |
| AC-12 Flyway | PASS | V36, V38 |
| AC-13 Money audit | N/A | |
| AC-14 VARCHAR + CHECK | PASS | `conversation.type` |
| AC-15 Pagination | PASS | Default 30, max 100 |
| AC-16 Empty/Loading/Error | PASS | `messages.html` |

**Layer 2 Result:** 2 exception (AC-09, AC-10 partial); AC-05 lệch có chủ ý đã được duyệt.

**LAYER 3 — ENGINEERING STANDARDS**

| Rule | Kết quả | Ghi chú |
|------|---------|---------|
| ES-01 Naming | PASS | |
| ES-02 REST noun-based | PASS | `/conversations`, `/messages` |
| ES-03 Bean Validation + 422 | PASS | `@Valid` trên `OpenConversationRequest`, `SendMessageRequest` |
| ES-04 Error format | PARTIAL | `"CODE\|Message"` map qua advice chung |
| ES-05 Test coverage | ⚠️ **CHƯA VERIFY** | Chat là SHELL — AC-05/ES-05 chỉ yêu cầu integration test happy path |
| ES-06/07 Commits | PASS | |
| ES-08 Multi-AI | PASS | |

=== SUMMARY ===  
Layer 1 : 20/21 PASS, 1 gap (HR-16 rate limit)  
Layer 2 : 14/16 PASS, 2 exception (AC-09, AC-10); AC-05 lệch có chủ ý đã duyệt  
Layer 3 : 6/8 PASS, ES-04 partial, ES-05 chưa verify  
Status  : **CLEARED TO SUBMIT với điều kiện** — lệch 3 cấp đã được leader duyệt và ghi ở `CLAUDE.md`
§4 + banner CONTEXT §Chat, nên không bị block. Cần xử lý **DS-02** (chat không theo kịp khi đơn đổi
tài xế — bug quyền riêng tư) và OQ-1.
================================
