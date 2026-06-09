# Feature Specification: Public Marketing Pages

**Feature Branch:** `017-public-marketing`
**Feature Number:** #17 of 18 — SUPPORT (visitor conversion)
**Created:** 2026-06-04
**Version:** 1.0.0
**Status:** Draft
**Sprint Target:** Sprint 6

**CONTEXT.md reference:** v2.0 §25 Public marketing
**Constitution reference:** v1.3.0 — HR-17 (public endpoints), HR-19 (brand
identity locked), HR-20 (Vietnamese diacritics mandatory), AC-16 (states)

---

## Goals

Move_home cần sáu trang public để visitor chưa đăng nhập hiểu dịch vụ, tin
tưởng thương hiệu và chuyển đổi thành Customer hoặc Driver. Landing page trình
bày lời hứa giá trị, capability đã được xác minh và CTA rõ ràng. Trang Giới
thiệu kể câu chuyện, sứ mệnh và đội ngũ. Trang Cách hoạt động giải thích quy
trình bốn bước. Trang Bảng giá cung cấp giá tham khảo và calculator client-side.
Trang Liên hệ kết hợp contact form với FAQ. Trang Điều khoản cung cấp nội dung
pháp lý và privacy dễ đọc.

Trải nghiệm phải mobile-first, tải nhanh, dễ truy cập và nhất quán với brand
Move_home: forest green, amber dùng tiết chế, Be Vietnam Pro, pill buttons và
tiếng Việt đầy đủ dấu. Header, footer, CTA và metadata SEO phải thống nhất trên
cả sáu trang. Nội dung marketing chỉ được mô tả capability thực sự tồn tại,
không quảng cáo GPS realtime, bảo hiểm hoặc cam kết pháp lý chưa được duyệt.

Pricing calculator là công cụ ước tính không cần đăng nhập và không gọi API.
Nó dùng public reference snapshot cùng shape công thức canonical của Spec 002,
nhưng không thay thế quote authoritative sau đăng ký. Contact form là backend
public duy nhất, có validation, rate limit, spam protection, persistence và
email async.

Mục tiêu business là visitor-to-register conversion đạt 5%, pricing-to-booking
CTA đạt 20% và how-it-works-to-register đạt 10%, nhưng analytics tracking thực
thi được defer đến Sprint 6+.

---

## Source-of-Truth Resolution

| Chủ đề | Quyết định canonical | Hệ quả |
|---|---|---|
| Public routes | Static HTML không cần JWT | Không redirect visitor sang login |
| Public API | Chỉ `/api/public/contact` | Tuân thủ HR-17 |
| Vehicle reference rates | `20k/30k/40k` VND/km theo Spec 002 | `15k/20k/25k` là legacy |
| Alley surcharge | `base_fare × 20%` | Không quảng cáo fixed 200k |
| Floor surcharge | Tier `0/20/30/50%` | Không quảng cáo fixed 50k/tầng |
| Porter fee | `150k/200k/300k` theo xe | Không dùng fixed 300k mọi xe |
| Calculator | Client-side reference estimate | Không phải quote/price guarantee |
| Location capability | Status updates, không GPS realtime | Gỡ claim theo dõi vị trí realtime |
| Damage capability | Dispute/compensation review | Không dùng claim “bảo hiểm hàng hóa” |
| Stats/team | Chỉ dùng số liệu được duyệt | Mock phải gắn nhãn demo, không publish như thật |
| Legal content | Cần owner/legal review | Không tự tuyên bố GDPR compliance/DPO |
| Analytics | Event contract only | Provider/tracking consent deferred |

---

## Scope Summary

**In scope:**

1. Sáu static HTML pages public.
2. Shared public header, mobile menu và footer.
3. Conversion-focused CTA links.
4. Canonical public pricing reference và client-side calculator.
5. Contact form qua `POST /api/public/contact`.
6. FAQ accordions.
7. SEO metadata và canonical URLs.
8. Responsive breakpoint chính `900px`.
9. Accessibility và print-friendly terms.
10. Contact rate limiting, persistence và email async.
11. Public analytics event naming contract.
12. Loading, success và error states cho contact form.

**Out of scope:**

1. Blog, news, careers và press releases.
2. English hoặc multi-language version.
3. Live chat widget.
4. A/B testing.
5. Advanced schema.org markup.
6. Sitemap generation.
7. Analytics provider implementation.
8. Authenticated booking hoặc authoritative quote.
9. Contact-submission Admin management UI.
10. Legal approval workflow.

---

## User Stories

**P1:**

- **US1:** Là visitor, tôi xem landing page với CTA rõ ràng để bắt đầu đăng ký.
- **US2:** Là visitor, tôi đọc trang Giới thiệu để hiểu Move_home, sứ mệnh và
  đội ngũ.
- **US3:** Là visitor, tôi xem bốn bước hoạt động để hiểu quy trình dịch vụ.
- **US4:** Là visitor, tôi xem bảng giá tham khảo và tự tính giá ước lượng mà
  không cần đăng nhập.
- **US5:** Là visitor, tôi gửi liên hệ và xem FAQ thường gặp.
- **US6:** Là visitor, tôi đọc điều khoản và privacy policy trước khi đăng ký.

**P2:**

- **US7:** Là visitor, tôi chia sẻ landing page với Open Graph preview đúng →
  tracking defer Sprint 6+.
- **US8:** Là visitor, tôi chuyển sang English version → defer.

---

## Functional Requirements

> EARS notation: WHEN | WHILE | WHERE | IF/THEN

### Nhóm 1 — Landing Page (FR-001..FR-006)

**FR-001 — Landing content**

WHEN a visitor opens `/` or `/public/index.html`,
THE SYSTEM SHALL render a public landing page containing:

- Hero headline `Chuyển nhà dễ dàng, an toàn, đúng hẹn`.
- Supporting copy describing verified Drivers and transparent pricing.
- Primary CTA `Đặt đơn ngay` linking to Customer registration.
- Secondary CTA `Đã có tài khoản` linking to login.
- Six capability cards.
- Four-step workflow teaser.
- Three-vehicle pricing teaser.
- Final conversion CTA.
- Shared footer.

**FR-002 — Truthful capability cards**

WHEN landing features render,
THE SYSTEM SHALL show only supported claims:

- Đa dạng loại xe.
- Tài xế được xác minh.
- Báo giá minh bạch.
- Cập nhật trạng thái đơn.
- Đánh giá sau đơn.
- Quy trình khiếu nại rõ ràng.

WHERE copy claims GPS realtime, guaranteed insurance or unsupported discount,
THE content SHALL be rejected during review.

**FR-003 — Shared public header**

WHEN any public page renders,
THE SYSTEM SHALL show a sticky header with Move_home logo, links to
`Trang chủ`, `Về chúng tôi`, `Cách hoạt động`, `Bảng giá`, `Liên hệ`,
`Đăng nhập` and `Đăng ký`.

The active page SHALL be identifiable without color alone.

**FR-004 — Anchor and CTA behavior**

WHEN a visitor selects an in-page landing anchor,
THE frontend SHALL smooth-scroll while respecting reduced-motion preference.

WHEN a visitor selects registration or login CTA,
THE frontend SHALL navigate to the existing auth page and preserve an optional
safe `source` campaign parameter.

**FR-005 — Responsive landing**

WHILE viewport width is below `900px`,
THE frontend SHALL stack hero, features, steps, pricing cards and footer
columns without horizontal scrolling.

The mobile header SHALL expose keyboard-accessible navigation.

**FR-006 — Landing performance**

WHEN the landing page loads on a throttled 3G profile,
THE SYSTEM SHALL prioritize hero text and CTA, lazy-load below-fold images and
avoid render-blocking noncritical JavaScript.

The page SHALL meet NFR-001 and Core Web Vitals targets.

---

### Nhóm 2 — About Page (FR-007..FR-011)

**FR-007 — About page**

WHEN a visitor opens `/about.html`,
THE SYSTEM SHALL render hero `Về Move_home`, supporting copy and sections
`Sứ mệnh`, `Câu chuyện` and `Giá trị cốt lõi`.

**FR-008 — Mission and values**

WHEN mission and values render,
THE content SHALL explain safe, transparent and responsible moving service in
Vietnamese without unverifiable superlatives.

Values SHALL be scannable on mobile and use editorial illustrations or icons.

**FR-009 — Team section**

WHEN the team section renders,
THE SYSTEM SHALL show up to five approved team members with accessible image,
name and role.

IF production-approved team content is unavailable,
THEN the section SHALL be hidden or clearly labeled demo content.

**FR-010 — Stats section**

WHEN company statistics render,
THE SYSTEM SHALL show only approved, sourced values with an `as_of` date.

WHERE values are mock or unverifiable,
THE production page SHALL omit them rather than present them as facts.

**FR-011 — About SEO and CTA**

WHEN `/about.html` renders,
THE SYSTEM SHALL set unique title
`Về Move_home - Dịch vụ chuyển nhà uy tín tại Hà Nội`, unique description and
a final `Đăng ký ngay` CTA.

---

### Nhóm 3 — How It Works (FR-012..FR-016)

**FR-012 — Workflow page**

WHEN a visitor opens `/how-it-works.html`,
THE SYSTEM SHALL render hero `Cách thức hoạt động` and four numbered steps:

1. Chọn loại xe phù hợp.
2. Nhập điểm đón và điểm trả.
3. Xem báo giá minh bạch.
4. Tài xế nhận đơn và hoàn thành.

**FR-013 — Step details**

WHEN a workflow step renders,
THE SYSTEM SHALL include a title, short description, expected user effort,
key benefit and accessible illustration.

Copy SHALL not promise a fixed Driver acceptance or completion time.

**FR-014 — Workflow boundary**

WHEN the page explains pricing and payment,
THE SYSTEM SHALL state that the visitor must register to receive an
authoritative quote and proceed with booking/payment.

It SHALL link to Customer registration and pricing reference.

**FR-015 — Process FAQ**

WHEN the process FAQ renders,
THE frontend SHALL provide keyboard-accessible accordion items covering
registration, quote, Driver assignment, cancellation and dispute basics.

Only one item MAY be open by default.

**FR-016 — Tutorial placeholder and CTA**

WHEN the tutorial section renders before a video is approved,
THE SYSTEM SHALL show an accessible `Video hướng dẫn sắp ra mắt` placeholder
without loading a third-party embed.

The page SHALL end with CTA `Thử ngay`.

---

### Nhóm 4 — Pricing Page và Calculator (FR-017..FR-024)

**FR-017 — Pricing page**

WHEN a visitor opens `/pricing.html`,
THE SYSTEM SHALL render hero `Bảng giá minh bạch`, three vehicle cards,
canonical surcharge explanation, calculator and CTA.

**FR-018 — Public vehicle rates**

WHEN vehicle pricing cards render,
THE SYSTEM SHALL display the versioned public reference snapshot:

| Vehicle | Reference rate |
|---|---:|
| Xe tải 500kg | Từ `20.000 VND/km` |
| Xe tải 1 tấn | Từ `30.000 VND/km` |
| Xe tải 1,5 tấn | Từ `40.000 VND/km` |

The page SHALL show snapshot effective date and estimate disclaimer.

**FR-019 — Public surcharge explanation**

WHEN surcharge information renders,
THE SYSTEM SHALL explain:

- Peak surcharge: base fare × `30%` in reference peak ranges.
- Alley surcharge: base fare × `20%`.
- Floor tiers: `20%`, `30%`, `50%` of base fare by effective floor.
- Porter fees: `150.000`, `200.000`, `300.000 VND/person` by vehicle.

The page SHALL not display legacy fixed-surcharge values.

**FR-020 — Calculator inputs**

WHEN a visitor uses the calculator,
THE frontend SHALL accept:

- Pickup district.
- Dropoff district.
- Vehicle type.
- Scheduled local date/time.
- Pickup/dropoff alley flags.
- Pickup/dropoff floor and elevator flags.
- Porter count `0..3`.

**FR-021 — Distance estimate**

WHEN both districts are selected,
THE calculator SHALL use a bundled, versioned, symmetric district-distance
reference matrix and label the distance `Ước tính theo quận`.

WHERE a pair is unavailable,
THE calculator SHALL show a graceful unavailable state and registration CTA.

**FR-022 — Calculator formula**

WHEN valid calculator inputs change,
THE frontend SHALL deterministically calculate the Spec 002 reference formula
using integer VND rounding and display itemized base, peak, alley, floor,
porter and total estimate in under `100ms`.

The calculator SHALL not call any API.

**FR-023 — Estimate disclaimer**

WHEN an estimated result renders,
THE SYSTEM SHALL prominently display:
`Giá tham khảo. Giá thực tế được xác định sau khi tính chính xác quãng đường
và chụp cấu hình giá tại thời điểm báo giá.`

It SHALL not create a booking, payment intent or binding quote.

**FR-024 — Calculator fallback**

WHERE JavaScript is disabled or an input is invalid,
THE SYSTEM SHALL keep the pricing cards and surcharge explanation readable,
show that calculator requires JavaScript and provide CTA
`Đăng ký để xem báo giá chính xác`.

---

### Nhóm 5 — Contact Page và Form (FR-025..FR-031)

**FR-025 — Contact page**

WHEN a visitor opens `/contact.html`,
THE SYSTEM SHALL render hero `Liên hệ với chúng tôi`, approved support email,
hotline, office area, working hours, contact form, FAQ and optional map.

Mock contact values SHALL not be published without approval.

**FR-026 — Contact form contract**

WHEN a visitor submits the form,
THE frontend SHALL call `POST /api/public/contact` with:

```json
{
  "name": "Nguyễn Văn A",
  "email": "a@example.com",
  "phone": "0912345678",
  "message": "Tôi cần tư vấn chuyển nhà tại Cầu Giấy.",
  "website": ""
}
```

`website` SHALL be a hidden honeypot field.

**FR-027 — Contact validation**

WHEN the backend validates contact input,
THE SYSTEM SHALL enforce:

- Name: trimmed Unicode `2..100`.
- Email: valid format, maximum `254`.
- Phone: optional Vietnamese format.
- Message: trimmed `10..5000`.
- Honeypot: blank.

Invalid input SHALL return HTTP `422` field-specific errors.

**FR-028 — Rate limit and spam handling**

WHEN public contact submissions are received,
THE SYSTEM SHALL permit at most `3` accepted attempts per IP per hour and apply
additional safe spam heuristics.

WHERE the limit is exceeded,
THE SYSTEM SHALL return HTTP `429 CONTACT_RATE_LIMITED` without sending email.

**FR-029 — Contact transaction**

WHEN a contact submission is valid and accepted,
THE SYSTEM SHALL persist one `contact_submission`, enqueue one support-email
outbox event and return HTTP `202`:

```json
{"message":"Cảm ơn bạn đã liên hệ, chúng tôi sẽ phản hồi trong 24 giờ."}
```

Email failure SHALL not delete or duplicate the persisted submission.

**FR-030 — Contact UI states**

WHILE contact submit is pending,
THE frontend SHALL disable repeated submit and show progress.

WHEN accepted, it SHALL clear the form and show the Vietnamese success message.

WHERE submit fails, it SHALL preserve values and show an actionable error.

**FR-031 — FAQ and map**

WHEN contact FAQ renders,
THE frontend SHALL provide 10 approved, keyboard-accessible questions.

IF an OpenStreetMap embed is used,
THEN it SHALL have a text alternative, descriptive title and lazy loading;
failure SHALL not block contact information or form.

---

### Nhóm 6 — Terms and Privacy (FR-032..FR-035)

**FR-032 — Legal page**

WHEN a visitor opens `/terms.html`,
THE SYSTEM SHALL render two clearly separated parts:
`Điều khoản sử dụng` and `Chính sách bảo mật`, with a table of contents and
anchor links.

**FR-033 — Required legal topics**

WHEN legal content renders,
THE SYSTEM SHALL include approved sections for scope, Customer and Driver
responsibilities, payment/refund, disputes, personal-data collection/use,
user privacy rights, cookie policy and privacy contact.

The page SHALL not claim certifications or legal compliance not reviewed by
the project owner.

**FR-034 — Legal metadata**

WHEN legal content is published,
THE SYSTEM SHALL display version, effective date and last-updated date.

IF material terms change in future,
THEN the content SHALL receive a new version and preserve an audit/release
record outside this feature.

**FR-035 — Legal accessibility and print**

WHEN the visitor uses table-of-contents links or prints `/terms.html`,
THE page SHALL preserve heading hierarchy, readable contrast and print-friendly
content while hiding navigation and conversion-only elements.

---

### Nhóm 7 — SEO + Performance (FR-036..FR-040)

**FR-036 — SEO metadata**

WHEN any public page renders,
THE SYSTEM SHALL provide unique `<title>`, Vietnamese meta description,
canonical URL, Open Graph title/description/image/url and Twitter Card tags.

Descriptions SHOULD remain approximately `150..160` characters.

**FR-037 — Crawlability**

WHEN a search-engine bot accesses public pages,
THE SYSTEM SHALL receive meaningful server-delivered HTML, valid heading
hierarchy and crawlable internal links.

`robots.txt` SHALL allow these six pages; sitemap generation is deferred.

**FR-038 — Image and font performance**

WHEN a public page loads,
THE SYSTEM SHALL use responsive optimized images with width/height, meaningful
alt text and `loading="lazy"` below the fold.

Be Vietnam Pro SHALL be preconnected/preloaded appropriately with Vietnamese
glyph support and a system fallback.

**FR-039 — Shared accessibility**

WHERE a visitor navigates by keyboard, screen reader or reduced-motion setting,
THE SYSTEM SHALL provide skip link, visible focus, semantic landmarks,
accessible names and motion-safe behavior consistent with basic WCAG 2.1 AA.

**FR-040 — Analytics and deferred controls**

WHEN conversion CTAs, calculator completion or contact acceptance occur,
THE frontend SHALL expose provider-neutral event names without personal data:
`PUBLIC_CTA_CLICKED`, `PUBLIC_CALCULATOR_COMPLETED`,
`PUBLIC_CONTACT_ACCEPTED`.

Until consent/provider implementation is approved, no third-party analytics
script or tracking cookie SHALL load.

---

## Non-Functional Requirements

| ID | Requirement |
|---|---|
| NFR-001 | Public page load under `2s` on target throttled 3G |
| NFR-002 | Contact API accepts valid submission under `3s`; email async |
| NFR-003 | Calculator update under `100ms` client-side |
| NFR-004 | Responsive without horizontal scroll from `360px..1920px` |
| NFR-005 | PageSpeed target above `90` mobile and desktop |
| NFR-006 | Basic WCAG 2.1 AA accessibility |
| NFR-007 | Full Vietnamese diacritics per HR-20 |
| NFR-008 | Brand tokens per DESIGN.md/HR-19 |
| NFR-009 | First Contentful Paint under `1.5s` target |
| NFR-010 | Largest Contentful Paint under `2.5s` target |
| NFR-011 | Contact email failure never loses accepted submission |
| NFR-012 | No PII in analytics event payloads |

---

## API Endpoints Summary

| Method | Endpoint | Body | Success | Auth |
|---|---|---|---|---|
| POST | `/api/public/contact` | `{name,email,phone,message,website}` | `202 {message}` | Public |

The endpoint SHALL be explicitly `permitAll()` under HR-17 while validation,
rate limiting and spam controls remain mandatory.

### Error Contract

| HTTP | Code | Meaning |
|---|---|---|
| 422 | `VALIDATION_ERROR` | Invalid fields |
| 429 | `CONTACT_RATE_LIMITED` | More than allowed attempts |
| 503 | `CONTACT_UNAVAILABLE` | Persistence/outbox unavailable |

Errors SHALL use ES-04 structured JSON and SHALL not expose SMTP or stack-trace
details.

---

## Data Model

```sql
CREATE TABLE contact_submission (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  full_name VARCHAR(100) NOT NULL,
  email VARCHAR(254) NOT NULL,
  phone VARCHAR(20),
  message TEXT NOT NULL,
  ip_address_hash CHAR(64),
  status VARCHAR(20) NOT NULL DEFAULT 'NEW'
    CHECK (status IN ('NEW', 'IN_PROGRESS', 'RESOLVED', 'SPAM')),
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  resolved_at TIMESTAMPTZ,
  CONSTRAINT ck_contact_message_length
    CHECK (char_length(message) BETWEEN 10 AND 5000)
);

CREATE INDEX idx_contact_status_created
  ON contact_submission(status, created_at DESC);

CREATE INDEX idx_contact_ip_hash_created
  ON contact_submission(ip_address_hash, created_at DESC)
  WHERE ip_address_hash IS NOT NULL;
```

Raw IP SHALL not be stored when a keyed hash is sufficient for rate-limit
audit. Schema changes SHALL use Flyway.

---

## Public Pricing Reference Contract

The public calculator SHALL bundle a reviewed reference snapshot containing:

- Snapshot version and effective date.
- Three vehicle rates.
- Three porter rates.
- Peak rate and ranges.
- Alley rate.
- Floor tiers.
- Symmetric district-distance estimates.

It SHALL follow Spec 002 formula shape and rounding rules.

It SHALL not fetch private Admin commission settings or expose internal
configuration endpoints.

Updating the bundled snapshot requires content review and calculator fixtures.

---

## State Machine

```text
Contact Submission:
NEW → IN_PROGRESS → RESOLVED
NEW → SPAM

Terminal: RESOLVED, SPAM
Invalid transitions → HTTP 409 in a future Admin contact-management feature.
```

Spec 017 creates only `NEW` submissions. Processing transitions are outside
scope.

All six pages are public read-only content and have no business state machine.

---

## Shared Frontend Contract

Every public page SHALL:

1. Use `lang="vi"` and UTF-8.
2. Load shared `frontend/css/styles.css`.
3. Use Be Vietnam Pro as primary font.
4. Use forest green primary and amber sparingly.
5. Use pill shape for interactive controls and 16px cards.
6. Include skip link, shared header and primary-strong footer.
7. Mark current navigation item.
8. Provide responsive navigation below 900px.
9. Use real links rather than `href="#"` for page navigation.
10. Avoid inline marketing claims that conflict with domain specs.

---

## Page and Content Matrix

| Page | Primary purpose | Primary CTA | Required secondary content |
|---|---|---|---|
| `/public/index.html` | Explain value and convert visitor | `Đặt đơn ngay` | Features, workflow, pricing teaser |
| `/about.html` | Build trust | `Đăng ký ngay` | Mission, story, values, approved team |
| `/how-it-works.html` | Explain process | `Thử ngay` | Four steps, process FAQ |
| `/pricing.html` | Explain reference pricing | `Đăng ký để xem báo giá chính xác` | Calculator, surcharge explanation |
| `/contact.html` | Capture support inquiry | `Gửi liên hệ` | Contact info, FAQ, optional map |
| `/terms.html` | Present legal/privacy content | None required | Table of contents, print mode |

### Shared Navigation Rules

1. The logo SHALL link to `/public/index.html` or the deployed public root.
2. Public page links SHALL remain available without authentication.
3. `Đăng ký` SHALL link to the Customer registration entry point.
4. Driver recruitment CTA MAY link to Driver registration when explicitly
   labeled `Đăng ký tài xế`.
5. Current page SHALL use `aria-current="page"`.
6. Mobile menu SHALL close after a navigation selection.
7. External links SHALL identify that they open a new context.
8. Footer legal and contact links SHALL never use placeholder anchors.

### Content Review Checklist

Before production publish, the content owner SHALL verify:

- Support email, hotline, office area and working hours.
- Team names, roles and images.
- Company statistics and `as_of` dates.
- Vehicle prices, surcharge snapshot and effective date.
- Cancellation, refund and dispute descriptions.
- Terms version, privacy contact and effective date.
- Every marketing capability claim against current domain specs.
- Every CTA destination and campaign source parameter.

---

## Contact Processing Contract

Contact acceptance SHALL use this boundary:

```text
validate and normalize public payload
check honeypot and IP rate limit
BEGIN
  insert contact_submission(status=NEW)
  insert support-email outbox event
COMMIT
return HTTP 202
async worker sends email and retries failures
```

Normalization SHALL:

1. Unicode-normalize and trim name/message.
2. Lowercase email domain without rewriting local-part semantics.
3. Normalize Vietnamese phone for validation where supplied.
4. Remove control characters not required for normal text.
5. Preserve line breaks in the message.
6. Escape content when displayed or inserted into HTML email.

The support email SHALL include submission ID and received timestamp, but SHALL
not include raw IP or rate-limit metadata.

---

## SEO Metadata Matrix

| Page | Title intent | Description intent |
|---|---|---|
| Landing | Move_home moving service in Hà Nội | Value proposition and verified Drivers |
| About | About Move_home | Mission, values and approved team |
| How it works | How Move_home works | Four-step transparent process |
| Pricing | Move_home reference pricing | Vehicle rates, surcharges and estimate |
| Contact | Contact Move_home | Support channels, form and FAQ |
| Terms | Terms and privacy | Usage terms and personal-data policy |

Every social preview image SHALL:

- Use an approved Move_home brand asset.
- Include meaningful Vietnamese alt/description metadata where supported.
- Avoid personal data or unapproved customer/Driver photos.
- Use an absolute production URL in Open Graph tags.

Canonical URLs SHALL not include tracking parameters.

---

## Analytics Event Contract

Provider-neutral events MAY be dispatched to an internal event layer after
consent implementation:

| Event | Allowed properties |
|---|---|
| `PUBLIC_CTA_CLICKED` | `page`, `cta_name`, `destination_type` |
| `PUBLIC_CALCULATOR_COMPLETED` | `vehicle_type`, `has_peak`, `has_surcharge` |
| `PUBLIC_CONTACT_ACCEPTED` | `page`, `submission_result` |

Events SHALL NOT contain:

- Name, email, phone or message.
- Exact pickup/dropoff district pair.
- Full URL query strings.
- IP address or auth token.
- Calculator money result tied to an identifier.

Until consent and provider are approved, the event layer MAY log only
development-safe diagnostics and SHALL not send third-party requests.

---

## Acceptance Criteria

**AC-01 — Six public pages**

GIVEN an anonymous visitor,
WHEN each public route opens,
THEN all six pages render meaningful content without JWT or auth redirect.

**AC-02 — Responsive UX**

GIVEN viewport widths from 360px to 1920px,
WHEN pages render,
THEN no required content or action overflows horizontally.

**AC-03 — SEO**

GIVEN each public page,
WHEN its document head is inspected,
THEN unique title, description, canonical, Open Graph and Twitter tags exist.

**AC-04 — Contact form**

GIVEN valid, invalid and rate-limited submissions,
WHEN contact is submitted,
THEN valid input persists once and returns 202; invalid is 422; excess is 429.

**AC-05 — Calculator determinism**

GIVEN identical valid inputs and the same public reference snapshot,
WHEN calculator runs repeatedly,
THEN every itemized VND estimate is identical.

**AC-06 — FAQ accessibility**

GIVEN keyboard-only navigation,
WHEN FAQ items are expanded or collapsed,
THEN state, focus and ARIA attributes remain correct.

**AC-07 — Brand**

GIVEN all six pages,
WHEN visually reviewed,
THEN forest green, restrained amber, Be Vietnam Pro, pills and 16px cards
match DESIGN.md.

**AC-08 — Vietnamese**

GIVEN every visible string,
WHEN reviewed,
THEN Vietnamese copy has full diacritics and unsupported claims are absent.

**AC-09 — Performance**

GIVEN target throttled 3G,
WHEN each page loads,
THEN page and Core Web Vitals targets are met.

**AC-10 — Navigation/footer**

GIVEN shared header/footer links,
WHEN selected,
THEN they navigate to correct public, auth or legal routes without placeholder
links.

---

## Edge Cases & Error Handling

| ID | Edge case | Required behavior |
|---|---|---|
| EC-01 | More than 3 contact attempts/hour | HTTP 429, no email |
| EC-02 | Invalid email or phone | HTTP 422 field errors |
| EC-03 | Message over 5.000 characters | HTTP 422 |
| EC-04 | Honeypot populated | Reject/mark spam without support email |
| EC-05 | Persistence fails | HTTP 503, no false success |
| EC-06 | Email async fails | Keep submission and retry |
| EC-07 | Slow connection | Hero/CTA first; below-fold remains lazy |
| EC-08 | JavaScript disabled | Content works; calculator/contact explain limitation |
| EC-09 | District pair missing | Calculator unavailable state + CTA |
| EC-10 | Invalid calculator numbers | Inline validation; no NaN result |
| EC-11 | Search bot crawl | Meaningful HTML and internal links |
| EC-12 | Image fails | Alt text preserves meaning and layout |
| EC-13 | Reduced-motion enabled | No forced smooth animation |
| EC-14 | Mock stats/team not approved | Hide or label demo, never publish as fact |
| EC-15 | Third-party map fails | Contact info/form remain usable |
| EC-16 | Terms content not approved | Do not publish as final legal text |
| EC-17 | Duplicate contact double-click | Button disabled; at most one accepted request |
| EC-18 | Unsupported tracking consent | No third-party analytics loaded |

---

## Test Cases

| ID | Test | Expected result |
|---|---|---|
| TC-01 | Open six pages anonymously | All render without auth redirect |
| TC-02 | Check 360px, 900px, 1920px layouts | No overflow; navigation usable |
| TC-03 | Validate SEO head for every page | Unique required metadata present |
| TC-04 | Run canonical calculator fixture | Itemized total matches Spec 002 reference |
| TC-05 | Repeat calculator same inputs | Deterministic output under 100ms |
| TC-06 | Submit valid contact | One NEW row, one outbox event, HTTP 202 |
| TC-07 | Submit invalid/spam/rate-limited contact | Structured 422/429, no support email |
| TC-08 | Simulate email failure | Submission remains; outbox retry |
| TC-09 | Keyboard/screen-reader audit | Header, FAQ, form and terms anchors accessible |
| TC-10 | Lighthouse/PageSpeed run | Performance/SEO/accessibility targets met |
| TC-11 | Disable JavaScript | Static content/legal/pricing cards remain usable |
| TC-12 | Content truth review | No GPS, insurance or unapproved stats claims |

---

## Required Automated Test Layers

1. Unit tests for calculator formula, rounding and district matrix.
2. API validation/rate-limit tests for contact.
3. Integration tests for contact persistence and email outbox.
4. Static checks for titles, descriptions, canonical and social tags.
5. Responsive visual tests at 360px, 900px and desktop.
6. Accessibility tests for navigation, FAQ, form and terms.
7. Performance tests with Lighthouse or equivalent.
8. Link checker for public header/footer/CTA routes.

---

## Security, Privacy and Content Governance

1. Contact endpoint is public but default-deny applies elsewhere.
2. Contact input SHALL be escaped and never rendered as trusted HTML.
3. Rate-limit keys SHALL avoid retaining raw IP longer than necessary.
4. Contact email, phone and message SHALL not enter analytics payloads.
5. Third-party maps/fonts SHALL follow approved privacy/CSP policy.
6. Legal, contact, team, stats and marketing claims require named content owner.
7. Terms SHALL identify a privacy contact only after approval.
8. Production page SHALL not expose mock addresses or phone numbers.

---

## Constitution Compliance

| Rule | Compliance |
|---|---|
| HR-11 | Contact email async; failure does not lose submission |
| HR-16 | Contact POST has explicit public rate limit |
| HR-17 | Only `/api/public/contact` is public API |
| HR-19 | Brand tokens and interaction geometry follow DESIGN.md |
| HR-20 | All user-facing text uses Vietnamese diacritics |
| HR-21 | `contact_submission` avoids reserved names |
| AC-07 | Contact timestamps use TIMESTAMPTZ |
| AC-12 | Contact schema through Flyway |
| AC-14 | Contact status uses VARCHAR + CHECK |
| AC-16 | Contact/calculator provide loading/error/fallback states |

---

## Definition of Done

1. Six public pages implement this content contract.
2. Exactly 40 EARS FR and eight User Stories are covered.
3. Shared navigation, footer and responsive behavior pass review.
4. Pricing calculator matches canonical public reference fixtures.
5. Contact endpoint validation, rate limit, persistence and email retry pass.
6. SEO metadata and crawlable links pass static checks.
7. Accessibility and performance targets pass.
8. Unsupported claims and unapproved mock content are absent.
9. Analytics provider, sitemap and export remain deferred.
10. Only Spec 017 artifact is changed by this task.
