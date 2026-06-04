---
version: alpha
name: Move-home-design
description: An inspired interpretation of Move_home's design language — a Vietnamese moving service platform whose web surface balances trust and warmth, framed by a forest-green primary that signals safety and care, an amber accent that hints at energy and craft, and a single signature pill shape (radius 999px) on every interactive element. Editorial illustrations of moving objects (boxes, sofas, families, trucks) ground the marketing without leaking decorative colour into the system. All user-facing copy MUST be Vietnamese with diacritics (có dấu).

language:
  primary: "vi-VN"
  copy-rule: "All UI text MUST be Vietnamese with full diacritics. Never use unaccented Vietnamese (telex form). Examples: 'Đặt đơn' NOT 'Dat don', 'Tài xế' NOT 'Tai xe', 'Đăng nhập' NOT 'Dang nhap'."
  encoding: "UTF-8 mandatory in <meta charset> and all source files"
  font-requirement: "Font MUST support Vietnamese diacritics (Be Vietnam Pro is native; Inter and Plus Jakarta Sans are acceptable fallbacks)"

colors:
  primary: "#1B4D3E"
  primary-soft: "#2A6B57"
  primary-strong: "#0F3329"
  on-primary: "#FFFFFF"
  accent: "#F5A623"
  accent-soft: "#FBC470"
  accent-strong: "#D88A0B"
  on-accent: "#1A1A1A"
  ink: "#1A1A1A"
  body: "#5E5E5E"
  mute: "#9CA3AF"
  hairline-mid: "#4B5563"
  canvas: "#FFFFFF"
  canvas-soft: "#F4F5F4"
  canvas-softer: "#FAFAF9"
  surface-pressed: "#E5E7EB"
  link: "#1B4D3E"
  on-dark: "#FFFFFF"
  green-elevated: "#264F40"
  success: "#16A34A"
  warning: "#F59E0B"
  danger: "#DC2626"
  info: "#0EA5E9"

typography:
  display-xxl:
    fontFamily: "Be Vietnam Pro, system-ui, Helvetica Neue, Arial, sans-serif"
    fontSize: 52px
    fontWeight: 700
    lineHeight: 60px
  display-xl:
    fontFamily: "Be Vietnam Pro, system-ui, Helvetica Neue, Arial, sans-serif"
    fontSize: 36px
    fontWeight: 700
    lineHeight: 44px
  display-lg:
    fontFamily: "Be Vietnam Pro, system-ui, Helvetica Neue, Arial, sans-serif"
    fontSize: 32px
    fontWeight: 700
    lineHeight: 40px
  display-md:
    fontFamily: "Be Vietnam Pro, system-ui, Helvetica Neue, Arial, sans-serif"
    fontSize: 24px
    fontWeight: 700
    lineHeight: 32px
  display-sm:
    fontFamily: "Be Vietnam Pro, system-ui, Helvetica Neue, Arial, sans-serif"
    fontSize: 20px
    fontWeight: 700
    lineHeight: 28px
  body-lg:
    fontFamily: "Be Vietnam Pro, system-ui, Helvetica Neue, Arial, sans-serif"
    fontSize: 18px
    fontWeight: 500
    lineHeight: 26px
  body-md:
    fontFamily: "Be Vietnam Pro, system-ui, Helvetica Neue, Arial, sans-serif"
    fontSize: 16px
    fontWeight: 400
    lineHeight: 24px
  body-md-strong:
    fontFamily: "Be Vietnam Pro, system-ui, Helvetica Neue, Arial, sans-serif"
    fontSize: 16px
    fontWeight: 500
    lineHeight: 20px
  body-sm:
    fontFamily: "Be Vietnam Pro, system-ui, Helvetica Neue, Arial, sans-serif"
    fontSize: 14px
    fontWeight: 400
    lineHeight: 20px
  body-sm-strong:
    fontFamily: "Be Vietnam Pro, system-ui, Helvetica Neue, Arial, sans-serif"
    fontSize: 14px
    fontWeight: 500
    lineHeight: 16px
  caption:
    fontFamily: "Be Vietnam Pro, system-ui, Helvetica Neue, Arial, sans-serif"
    fontSize: 12px
    fontWeight: 400
    lineHeight: 18px
  eyebrow:
    fontFamily: "Be Vietnam Pro, system-ui, sans-serif"
    fontSize: 12px
    fontWeight: 600
    lineHeight: 16px
    letterSpacing: "0.08em"
    textTransform: "uppercase"
  button-large:
    fontFamily: "Be Vietnam Pro, system-ui, sans-serif"
    fontSize: 18px
    fontWeight: 500
    lineHeight: 24px
  button-md:
    fontFamily: "Be Vietnam Pro, system-ui, sans-serif"
    fontSize: 16px
    fontWeight: 500
    lineHeight: 20px

rounded:
  none: 0px
  md: 8px
  lg: 12px
  xl: 16px
  2xl: 20px
  pill: 999px
  pill-tab: 36px
  full: 9999px

spacing:
  xxs: 4px
  xs: 6px
  sm: 8px
  md: 12px
  lg: 16px
  xl: 20px
  2xl: 24px
  3xl: 32px
  4xl: 48px
  5xl: 64px

components:
  nav-bar:
    backgroundColor: "{colors.canvas}"
    textColor: "{colors.ink}"
    typography: "{typography.body-md-strong}"
    padding: "{spacing.lg} {spacing.3xl}"
  nav-link:
    textColor: "{colors.ink}"
    typography: "{typography.body-md-strong}"
  button-primary:
    backgroundColor: "{colors.primary}"
    textColor: "{colors.on-primary}"
    typography: "{typography.button-md}"
    rounded: "{rounded.pill}"
    padding: "{spacing.md} {spacing.xl}"
  button-primary-amber:
    backgroundColor: "{colors.accent}"
    textColor: "{colors.on-accent}"
    typography: "{typography.button-md}"
    rounded: "{rounded.pill}"
    padding: "{spacing.md} {spacing.xl}"
  button-secondary:
    backgroundColor: "{colors.canvas}"
    textColor: "{colors.ink}"
    borderColor: "{colors.ink}"
    typography: "{typography.button-md}"
    rounded: "{rounded.pill}"
    padding: "{spacing.md} {spacing.xl}"
  button-subtle:
    backgroundColor: "{colors.canvas-soft}"
    textColor: "{colors.ink}"
    typography: "{typography.button-md}"
    rounded: "{rounded.pill}"
    padding: "{spacing.md} {spacing.lg}"
  button-floating:
    backgroundColor: "{colors.canvas}"
    textColor: "{colors.ink}"
    typography: "{typography.button-md}"
    rounded: "{rounded.pill}"
    padding: "{spacing.md}"
  button-large-rounded:
    backgroundColor: "{colors.primary}"
    textColor: "{colors.on-primary}"
    typography: "{typography.button-large}"
    rounded: "{rounded.xl}"
    padding: "{spacing.lg} {spacing.xl}"
  button-danger:
    backgroundColor: "{colors.danger}"
    textColor: "{colors.on-primary}"
    typography: "{typography.button-md}"
    rounded: "{rounded.pill}"
    padding: "{spacing.md} {spacing.xl}"
  button-tab-translucent:
    backgroundColor: "{colors.canvas}"
    textColor: "{colors.ink}"
    typography: "{typography.body-md-strong}"
    rounded: "{rounded.pill-tab}"
  text-input:
    backgroundColor: "{colors.canvas-soft}"
    textColor: "{colors.ink}"
    typography: "{typography.body-md}"
    rounded: "{rounded.md}"
    padding: "{spacing.lg}"
    focusBorderColor: "{colors.primary}"
  text-input-on-soft:
    backgroundColor: "{colors.canvas-softer}"
    textColor: "{colors.ink}"
    typography: "{typography.body-md}"
    rounded: "{rounded.md}"
    padding: "{spacing.lg}"
  card-content:
    backgroundColor: "{colors.canvas}"
    textColor: "{colors.ink}"
    typography: "{typography.body-md}"
    rounded: "{rounded.xl}"
    padding: "{spacing.2xl}"
  card-elevated:
    backgroundColor: "{colors.canvas}"
    textColor: "{colors.ink}"
    typography: "{typography.body-md}"
    rounded: "{rounded.xl}"
    padding: "{spacing.2xl}"
  card-soft-tinted:
    backgroundColor: "{colors.canvas-soft}"
    textColor: "{colors.ink}"
    typography: "{typography.body-md}"
    rounded: "{rounded.xl}"
    padding: "{spacing.2xl}"
  promo-card-illustrated:
    backgroundColor: "{colors.canvas}"
    textColor: "{colors.ink}"
    typography: "{typography.display-md}"
    rounded: "{rounded.xl}"
    padding: "{spacing.2xl}"
  promo-card-on-dark:
    backgroundColor: "{colors.primary}"
    textColor: "{colors.on-dark}"
    typography: "{typography.display-md}"
    rounded: "{rounded.xl}"
    padding: "{spacing.2xl}"
  promo-card-amber:
    backgroundColor: "{colors.accent}"
    textColor: "{colors.on-accent}"
    typography: "{typography.display-md}"
    rounded: "{rounded.xl}"
    padding: "{spacing.2xl}"
  request-form-card:
    backgroundColor: "{colors.canvas}"
    textColor: "{colors.ink}"
    typography: "{typography.body-md}"
    rounded: "{rounded.xl}"
    padding: "{spacing.lg}"
  request-form-input-row:
    backgroundColor: "{colors.canvas-soft}"
    textColor: "{colors.ink}"
    typography: "{typography.body-md}"
    rounded: "{rounded.md}"
    padding: "{spacing.lg}"
  category-button:
    backgroundColor: "{colors.canvas-soft}"
    textColor: "{colors.ink}"
    typography: "{typography.body-sm-strong}"
    rounded: "{rounded.pill}"
    padding: "{spacing.sm} {spacing.lg}"
  faq-row:
    backgroundColor: "{colors.canvas}"
    textColor: "{colors.ink}"
    typography: "{typography.body-md-strong}"
    padding: "{spacing.lg} 0"
  app-download-pill:
    backgroundColor: "{colors.ink}"
    textColor: "{colors.on-dark}"
    typography: "{typography.body-md-strong}"
    rounded: "{rounded.pill}"
    padding: "{spacing.md} {spacing.xl}"
  hero-band-light:
    backgroundColor: "{colors.canvas}"
    textColor: "{colors.ink}"
    typography: "{typography.display-xxl}"
    padding: "{spacing.4xl} {spacing.3xl}"
  hero-band-dark:
    backgroundColor: "{colors.primary}"
    textColor: "{colors.on-dark}"
    typography: "{typography.display-xxl}"
    padding: "{spacing.4xl} {spacing.3xl}"
  showcase-image-card:
    backgroundColor: "{colors.primary}"
    textColor: "{colors.on-dark}"
    typography: "{typography.display-xxl}"
    rounded: "{rounded.xl}"
    padding: "{spacing.3xl}"
  status-badge-pending:
    backgroundColor: "#FEF3C7"
    textColor: "#92400E"
    typography: "{typography.body-sm-strong}"
    rounded: "{rounded.pill}"
    padding: "{spacing.xs} {spacing.md}"
  status-badge-active:
    backgroundColor: "#D1FAE5"
    textColor: "#065F46"
    typography: "{typography.body-sm-strong}"
    rounded: "{rounded.pill}"
    padding: "{spacing.xs} {spacing.md}"
  status-badge-cancelled:
    backgroundColor: "#FEE2E2"
    textColor: "#991B1B"
    typography: "{typography.body-sm-strong}"
    rounded: "{rounded.pill}"
    padding: "{spacing.xs} {spacing.md}"
  status-badge-in-progress:
    backgroundColor: "#DBEAFE"
    textColor: "#1E40AF"
    typography: "{typography.body-sm-strong}"
    rounded: "{rounded.pill}"
    padding: "{spacing.xs} {spacing.md}"
  link-primary:
    textColor: "{colors.primary}"
    typography: "{typography.body-md}"
  link-on-dark:
    textColor: "{colors.on-dark}"
    typography: "{typography.body-md}"
  link-mute:
    textColor: "{colors.hairline-mid}"
    typography: "{typography.body-md}"
  link-mute-soft:
    textColor: "{colors.mute}"
    typography: "{typography.body-md}"
  icon-button-circular:
    backgroundColor: "{colors.canvas-soft}"
    textColor: "{colors.ink}"
    rounded: "{rounded.full}"
  footer:
    backgroundColor: "{colors.primary-strong}"
    textColor: "{colors.on-dark}"
    typography: "{typography.body-sm}"
    padding: "{spacing.4xl} {spacing.3xl}"

  # ─── Examples (auto-derived demonstration surfaces) ───
  ex-pricing-tier:
    description: "Default tier card. Forest green border accent on hover."
    backgroundColor: "{colors.canvas-soft}"
    textColor: "{colors.ink}"
    borderColor: "{colors.surface-pressed}"
    rounded: "{rounded.xl}"
    padding: "{spacing.2xl}"
  ex-pricing-tier-featured:
    description: "Featured tier — polarity-flipped to forest green with white text + amber tag."
    backgroundColor: "{colors.primary}"
    textColor: "{colors.on-primary}"
    accentColor: "{colors.accent}"
    rounded: "{rounded.xl}"
    padding: "{spacing.2xl}"
  ex-app-shell-row:
    description: "Admin sidebar nav row. Active state uses forest green as left-edge indicator + amber dot."
    backgroundColor: "{colors.canvas}"
    activeIndicator: "{colors.primary}"
    activeDot: "{colors.accent}"
    rounded: "{rounded.md}"
    padding: "{spacing.md} {spacing.lg}"
  ex-data-table-cell:
    description: "Admin table cell. Header uses canvas-soft + body-sm-strong; sort active in primary."
    headerBackground: "{colors.canvas-soft}"
    headerTypography: "{typography.body-sm-strong}"
    bodyTypography: "{typography.body-sm}"
    cellPadding: "{spacing.md} {spacing.lg}"
    rowBorder: "{colors.surface-pressed}"
    sortActiveColor: "{colors.primary}"
  ex-auth-form-card:
    description: "Đăng nhập / Đăng ký card with forest-green large CTA at bottom."
    backgroundColor: "{colors.canvas}"
    rounded: "{rounded.xl}"
    padding: "{spacing.3xl}"
    shadowLevel: "Level 1"
  ex-modal-card:
    description: "Modal dialog surface — canvas chrome with Level 2 drop shadow."
    backgroundColor: "{colors.canvas}"
    rounded: "{rounded.xl}"
    padding: "{spacing.2xl}"
  ex-empty-state-card:
    description: "Empty-state with editorial illustration of empty box. Centred content on canvas-soft."
    backgroundColor: "{colors.canvas-soft}"
    rounded: "{rounded.xl}"
    padding: "{spacing.4xl}"
    captionTypography: "{typography.body-md}"
  ex-toast-success:
    description: "Success toast with amber accent stripe + success icon."
    backgroundColor: "{colors.canvas}"
    accentStripe: "{colors.success}"
    rounded: "{rounded.xl}"
    padding: "{spacing.md} {spacing.lg}"
    typography: "{typography.body-sm}"
  ex-toast-error:
    description: "Error toast with danger stripe."
    backgroundColor: "{colors.canvas}"
    accentStripe: "{colors.danger}"
    rounded: "{rounded.xl}"
    padding: "{spacing.md} {spacing.lg}"
    typography: "{typography.body-sm}"
  ex-kpi-card:
    description: "Dashboard KPI card with large number + label + delta indicator."
    backgroundColor: "{colors.canvas}"
    valueTypography: "{typography.display-lg}"
    labelTypography: "{typography.body-sm-strong}"
    deltaPositive: "{colors.success}"
    deltaNegative: "{colors.danger}"
    rounded: "{rounded.xl}"
    padding: "{spacing.2xl}"

---


## Overview

Move_home is a Vietnamese moving service platform — connecting customers who need transportation with verified drivers across Hanoi's inner districts. The brand's web surface signals trust through restraint: forest green `{colors.primary}` as the conversion anchor that whispers "an toàn, có trách nhiệm" (safety, responsibility), and amber `{colors.accent}` as a warm hint of human energy reserved for moments of celebration or invitation. The page is structurally a duet of canvas-white and forest-green, where amber appears as a single accent note — never as a competing voice.

Type is the second decisive voice. **Be Vietnam Pro** carries every page — a Vietnamese-native geometric sans designed specifically for diacritics (dấu). Weight 700 carries display headlines (32 – 52 px with 1.15 – 1.20 line-height for compact poured-on-the-page feel), weight 500 carries buttons and emphasis, weight 400 carries paragraph body. The face renders Vietnamese tones (huyền, sắc, hỏi, ngã, nặng) without collision — critical for "Đặt đơn", "Tài xế", "Phụ phí giờ cao điểm" to read clean.

**LANGUAGE RULE — STRICT:**
- **All user-facing copy MUST be Vietnamese with full diacritics.** Never strip dấu for "convenience".
- ❌ "Dat don", "Tai xe", "Phu phi" — UNACCEPTABLE
- ✅ "Đặt đơn", "Tài xế", "Phụ phí" — REQUIRED
- Java internal naming stays English (`OrderService`, `driverProfile`) per project Constitution.
- Database content (seed data) follows the same rule: customer names "Nguyễn Văn A" not "Nguyen Van A".

The single shape signature is the pill. Every interactive element rounds to `{rounded.pill}` 999 px — primary CTA, secondary CTA, subtle gray pill, category chip, status badge, app-download pill. Cards round to `{rounded.xl}` 16 px; form inputs to `{rounded.md}` 8 px (slightly softer than Uber's 0 px because Vietnamese long-form text reads better with inset radius).

**Key Characteristics:**
- A two-tone CTA hierarchy: forest green `{colors.primary}` pill for primary conversion ("Đặt đơn", "Đăng nhập", "Xác nhận"); white `{colors.canvas}` pill with ink border for secondary ("Hủy", "Quay lại"); amber `{colors.accent}` pill for celebratory or special CTAs ("Nhận ưu đãi", "Mời bạn bè").
- The pill is the signature shape — `{rounded.pill}` 999 px on every interactive element except form fields (`{rounded.md}` 8 px) and large form-context CTAs (`{rounded.xl}` 16 px).
- Every headline is sentence-case Vietnamese with diacritics in `{typography.display-*}` weight 700; no all-caps display, no anglicised "Move home" without underscore.
- Editorial illustrations of moving objects (carton boxes, sofas, families with bags, mini-trucks) are the only decorative system; no gradients, no atmospheric backdrops.
- A signature alternating-band rhythm: white feature band → forest-green promo band (with white text and amber CTA) → white feature band → primary-strong footer. The dark green bands are NOT hero-only; they appear mid-page as trust callouts ("Đảm bảo an toàn 100%").
- A signature ride-request form card on the hero: pickup district selector + dropoff district selector + scheduled time picker + forest-green "Xem báo giá" pill, all stacked inside a `{rounded.xl}` shadowed card.

## Colors

### Brand & Accent
- **Forest Green** (`{colors.primary}` — `#1B4D3E`): The brand's primary conversion colour. Every primary CTA pill, the footer fill, every dark promo band, every nav login button, active states in sidebar nav. The whisper: "trustworthy, safe, responsible."
- **Primary Soft** (`{colors.primary-soft}` — `#2A6B57`): Hover state for primary CTAs. One step lighter to signal interaction.
- **Primary Strong** (`{colors.primary-strong}` — `#0F3329`): Pressed state and deep footer. Anchors weight without going pure black.
- **Amber** (`{colors.accent}` — `#F5A623`): The single accent — used sparingly for special CTAs ("Nhận ưu đãi"), highlight badges, featured tier indicators, and decorative dots on active nav rows. NEVER use for default primary actions.
- **Accent Soft** (`{colors.accent-soft}` — `#FBC470`): Hover state for amber CTAs and soft accent tints.
- **Accent Strong** (`{colors.accent-strong}` — `#D88A0B`): Pressed state for amber and decorative emphasis.

### Surface
- **Canvas** (`{colors.canvas}` — `#FFFFFF`): The default page background — clinical white that lets forest green and amber stand confidently.
- **Canvas Soft** (`{colors.canvas-soft}` — `#F4F5F4`): Soft warm gray with hint of green undertone. Used for form-input fills, subtle pill buttons, table headers.
- **Canvas Softer** (`{colors.canvas-softer}` — `#FAFAF9`): Lightest tier for nested-input fills on white surfaces.
- **Surface Pressed** (`{colors.surface-pressed}` — `#E5E7EB`): Pressed state for white pills + table row dividers.

### Text
- **Ink** (`{colors.ink}` — `#1A1A1A`): Every heading and body paragraph on light surfaces. Slightly warmer than pure black for Vietnamese diacritic legibility.
- **Body** (`{colors.body}` — `#5E5E5E`): Secondary text — captions, sub-headings, supporting copy.
- **Hairline Mid** (`{colors.hairline-mid}` — `#4B5563`): Mid-gray for muted link text inside footer columns.
- **Mute** (`{colors.mute}` — `#9CA3AF`): Lightest text role — placeholder text, fine print, low-priority metadata.
- **On Dark** (`{colors.on-dark}` — `#FFFFFF`): All text on `{colors.primary}` surfaces.
- **On Primary** (`{colors.on-primary}` — `#FFFFFF`): Text on primary forest-green buttons.
- **On Accent** (`{colors.on-accent}` — `#1A1A1A`): Text on amber buttons (dark on light, NOT white on light — amber is bright, needs dark text for contrast).

### Semantic
- **Success** (`{colors.success}` — `#16A34A`): Order completed, payment success, driver approved. Used in toast stripes, badge backgrounds, status indicators.
- **Warning** (`{colors.warning}` — `#F59E0B`): Pending approval, expiring soon, attention needed.
- **Danger** (`{colors.danger}` — `#DC2626`): Order cancelled, payment failed, validation errors.
- **Info** (`{colors.info}` — `#0EA5E9`): In progress, informational toasts, neutral status.

Status badges use semantic-tinted backgrounds with deeper text colour for accessibility:
- `Đang chờ` (Pending) → `#FEF3C7` bg + `#92400E` text
- `Đang giao` (In progress) → `#DBEAFE` bg + `#1E40AF` text
- `Hoàn thành` (Completed) → `#D1FAE5` bg + `#065F46` text
- `Đã hủy` (Cancelled) → `#FEE2E2` bg + `#991B1B` text

## Typography

### Font Family
**Be Vietnam Pro** carries the entire system — a Vietnamese-native open-source geometric sans available free from Google Fonts. The face was designed specifically for Vietnamese diacritics, avoiding the tone-mark collisions that plague universal sans like Inter or Roboto when rendering "ờ", "ấ", "ặng".

Single family, three working weights:
- **Bold (700)**: Display headlines only — never body
- **Medium (500)**: Buttons, links, inline emphasis, captions
- **Regular (400)**: Paragraph body, metadata, descriptions

No italic. No condensed. No display variant.

### Hierarchy

| Token | Size | Weight | Line Height | Use |
|---|---|---|---|---|
| `{typography.display-xxl}` | 52px | 700 | 60px | Hero headline ("Chuyển nhà dễ dàng cùng Move_home") |
| `{typography.display-xl}` | 36px | 700 | 44px | Page section headlines ("Tại sao chọn chúng tôi", "Cách thức hoạt động") |
| `{typography.display-lg}` | 32px | 700 | 40px | Promo-card headlines, KPI numbers |
| `{typography.display-md}` | 24px | 700 | 32px | Card titles, feature card headings |
| `{typography.display-sm}` | 20px | 700 | 28px | Sub-card headings, form section titles |
| `{typography.body-lg}` | 18px | 500 | 26px | Lead paragraphs, larger body |
| `{typography.body-md}` | 16px | 400 | 24px | Default paragraph body |
| `{typography.body-md-strong}` | 16px | 500 | 20px | Bolded inline body, button labels |
| `{typography.body-sm}` | 14px | 400 | 20px | Captions, secondary metadata, table body |
| `{typography.body-sm-strong}` | 14px | 500 | 16px | Chip labels, table headers, status badges |
| `{typography.caption}` | 12px | 400 | 18px | Fine print, footer secondary lines |
| `{typography.eyebrow}` | 12px | 600 | 16px | UPPERCASE section eyebrow ("DỊCH VỤ"), letter-spaced 0.08em |
| `{typography.button-large}` | 18px | 500 | 24px | Large rounded buttons inside booking form |
| `{typography.button-md}` | 16px | 500 | 20px | Default button label |

### Principles
- **Sentence-case Vietnamese is the voice.** No all-caps headlines except eyebrow tags.
- **Diacritics MANDATORY.** Every dấu must render — "Tài xế" not "Tai xe".
- **Weight 700 for display; weight 500 for buttons and inline emphasis.** Never promote button labels to 700.
- **No tracking on body or display.** Only eyebrow tags get `letter-spacing: 0.08em`.
- **Line-height tightens at display sizes** (1.15 – 1.20) for poured-on-the-page feel.

### Note on Font Loading
```html
<!-- index.html / all pages -->
<link rel="preconnect" href="https://fonts.googleapis.com">
<link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
<link href="https://fonts.googleapis.com/css2?family=Be+Vietnam+Pro:wght@400;500;600;700&display=swap" rel="stylesheet">
```

CSS variable:
```css
:root {
  --font-family-base: 'Be Vietnam Pro', system-ui, 'Helvetica Neue', Arial, sans-serif;
}
body {
  font-family: var(--font-family-base);
}
```

### Vietnamese Copy Examples (REFERENCE for all UI generation)

**Buttons:**
- Primary action: "Đặt đơn", "Đăng nhập", "Đăng ký", "Xác nhận", "Lưu", "Tiếp tục"
- Secondary: "Hủy", "Quay lại", "Đóng", "Bỏ qua"
- Amber CTA: "Nhận ưu đãi", "Mời bạn bè", "Nâng cấp"
- Danger: "Xóa", "Hủy đơn", "Từ chối"

**Status:**
- `Đang chờ` (Pending) — vàng cam
- `Đã nhận đơn` (Accepted) — xanh dương nhạt
- `Đang giao` (In Progress) — xanh dương
- `Hoàn thành` (Completed) — xanh lá
- `Đã hủy` (Cancelled) — đỏ
- `Khiếu nại` (Disputed) — đỏ cam

**Roles:**
- `Khách hàng` (Customer)
- `Tài xế` (Driver)
- `Quản lý` (Manager)
- `Quản trị viên` (Admin)

**Common labels:**
- "Đăng nhập", "Đăng ký", "Quên mật khẩu?", "Đăng xuất"
- "Trang chủ", "Đơn hàng của tôi", "Tài khoản", "Cài đặt"
- "Điểm đón", "Điểm trả", "Thời gian hẹn", "Ghi chú"
- "Tổng tiền", "Phí hoa hồng", "Phụ phí giờ cao điểm", "Phí bốc xếp"
- "Tải xế của bạn", "Đánh giá", "Lịch sử đơn"

**Error messages (Vietnamese with proper punctuation):**
- "Email không hợp lệ"
- "Mật khẩu phải có ít nhất 8 ký tự"
- "Email đã được sử dụng"
- "Vui lòng xác thực email trước khi đăng nhập"
- "Sai email hoặc mật khẩu"

---

## Layout

### Spacing System
- **Base unit**: 4 px. Tất cả giá trị là bội số của 4.
- **Tokens**: `{spacing.xxs}` 4 px · `{spacing.xs}` 6 px · `{spacing.sm}` 8 px · `{spacing.md}` 12 px · `{spacing.lg}` 16 px · `{spacing.xl}` 20 px · `{spacing.2xl}` 24 px · `{spacing.3xl}` 32 px · `{spacing.4xl}` 48 px · `{spacing.5xl}` 64 px.
- **Section padding**: Marketing bands `{spacing.4xl}` 48 px top/bottom trên desktop, `{spacing.3xl}` 32 px trên mobile.
- **Card interior**: Content cards `{spacing.2xl}` 24 px; booking form cards `{spacing.lg}` 16 px (compact).
- **Inline gap**: Button rows, chip rows, app-download pills dùng `{spacing.md}` 12 px giữa siblings.
- **Stack gap** trong cards: title → body `{spacing.sm}` 8 px; section → section `{spacing.2xl}` 24 px.

### Grid & Container
- **Max width**: ~1200 px container; centered với horizontal gutters `{spacing.3xl}` 32 px desktop, `{spacing.lg}` 16 px mobile.
- **Column patterns**:
  - Promo cards: 2-up desktop (image trái + content phải, alternating), 1-up mobile.
  - Category chips ("Đặt nhanh", "Đặt trước", "Đặt theo giờ"): horizontal flex with wrap.
  - FAQ rows ("Câu hỏi thường gặp"): full-width single-column.
  - App-download pills ("Tải app Khách hàng", "Tải app Tài xế"): 2-up desktop, 1-up mobile.
  - Admin dashboard KPI cards: 4-up desktop, 2-up tablet, 1-up mobile.

### Whitespace Philosophy
Card-to-card spacing carries rhythm. Inside card, headline → paragraph → CTA stack tight (`{spacing.sm}` 8 px). Dark forest-green bands và amber promo cards không có internal hairlines — content sits on flat color với on-dark text.

### Responsive Strategy

#### Breakpoints

| Name | Width | Key Changes |
|---|---|---|
| Mobile | < 600px | Nav collapses to hamburger; promo cards stack; booking form full-width edge-to-edge. |
| Mobile-Large | 600–767px | Same as Mobile; chip rows enable horizontal scroll. |
| Tablet | 768–1119px | 2-up promo grid; nav horizontal until ≥ 1120 px; admin sidebar collapses to icons. |
| Desktop | 1120–1135px | Full nav row visible; admin sidebar expanded. |
| Desktop-Large | ≥ 1136px | Container caps ~1200 px; bands edge-to-edge while content centers. |

#### Touch Targets
- `button-primary` pill: ~44 px tall (`{spacing.md}` × 2 + 20 px line-height). WCAG AAA compliant.
- `button-large-rounded`: ~56 px. Used inside booking form.
- Category chips inflate to ≥ 44 px tall through extra padding on touch viewports.
- Status badges: 28 px tall — informational only, không tappable.

#### Collapsing Strategy
- **Nav**: full link row + "Đăng nhập" / "Đăng ký" pills desktop. Collapses to logo + hamburger mobile; menu overlay full-screen với link list dọc.
- **Booking form card**: desktop trong max-490-px `{rounded.xl}` shadow card. Mobile full-width edge-to-edge với rounded top corners only.
- **Promo cards**: desktop image-left + content-right (alternating). Mobile image trên content.
- **Admin sidebar**: desktop 240px fixed. Tablet 64px icon-only. Mobile bottom-nav 4 items.
- **Data tables**: desktop full table. Mobile chuyển sang card list (1 card per row).

#### Image Behavior
- **Editorial illustrations** (gia đình chuyển nhà, carton box, xe tải): 4:3 hoặc 16:9 hard-edge rectangles; never circle-cropped; aspect preserved.
- **Driver portraits**: 4:5 portrait crop trong `{rounded.xl}` 16 px card chrome.
- **Vehicle photos**: 16:9 landscape inside `{rounded.lg}` 12 px frame.
- **Maps trong booking flow**: full-bleed inside card, rounded corners follow parent.
- **Logo bar**: SVG vector, monochrome, consistent height ~32 px.

## Elevation & Depth

| Level | Treatment | Use |
|---|---|---|
| Level 0 — Flat | No shadow, no border (hoặc 1px hairline `{colors.surface-pressed}`) | Default — most cards lean on canvas contrast. |
| Level 1 — Subtle Drop | `rgba(0, 0, 0, 0.08) 0px 2px 8px 0px` | Promo cards trên light bands, KPI cards admin dashboard. |
| Level 2 — Card Drop | `rgba(0, 0, 0, 0.12) 0px 4px 16px 0px` | Booking form card hero; modals; auth forms. |
| Level 3 — Pill Float | `rgba(0, 0, 0, 0.16) 0px 2px 8px 0px` | Floating white pill button over hero photography. |
| Level 4 — Sticky Header | `rgba(0, 0, 0, 0.06) 0px 1px 4px 0px` | Sticky nav bar when scrolled. |

### Decorative Depth
- **Forest-green bands as polarity-flip depth**: brand uses `{colors.primary}` mid-page bands to break canvas-white rhythm. Polarity shift IS depth cue.
- **Amber promo cards as warmth accent**: occasional amber-tinted cards punctuate the white-and-green flow with human warmth.
- **Editorial illustrations as in-card depth**: every promo card có 1 illustration as left/right column. Illustration's visual weight is part of card's elevation read.
- **Pill geometry as micro-depth**: `{rounded.pill}` 999 px applied at varying button heights creates stack of nested pills reading as hierarchy.

## Shapes

### Border Radius Scale

| Token | Value | Use |
|---|---|---|
| `{rounded.none}` | 0px | Full-bleed hero bands, footer, raw image edges. |
| `{rounded.md}` | 8px | Form-input fields, request-form input rows. |
| `{rounded.lg}` | 12px | Smaller secondary card chrome, vehicle photos. |
| `{rounded.xl}` | 16px | Canonical card radius — promo cards, content cards, booking form, KPI cards, modals. |
| `{rounded.2xl}` | 20px | Extra-soft large cards (rare — only annual showcase). |
| `{rounded.pill}` | 999px | Signature interactive shape — buttons, chips, badges, app-download pills, icon buttons. |
| `{rounded.pill-tab}` | 36px | Tab-toggle pill on hero (Khách hàng / Tài xế selector). |
| `{rounded.full}` | 9999px | Identical to pill cho circular icon containers, avatars. |

## Components

### Pagination

**`pagination-bar`** — Container đầy đủ cho phân trang dữ liệu (table, list).
- Background: transparent
- Layout: flex horizontal, justify-between, align-center
- Padding: `{spacing.lg}` 16px top/bottom
- Border-top: 1px solid `{colors.surface-pressed}`
- Mobile: stack vertical, gap `{spacing.md}`

**`pagination-info`** — Text "Hiển thị X-Y trong Z đơn"
- Typography: `{typography.body-sm}` 14px
- Color: `{colors.body}` `#5E5E5E`
- Vietnamese examples:
  - "Hiển thị 1-10 trong 30 đơn"
  - "Hiển thị 11-20 trong 30 tài xế"
  - "Hiển thị 21-30 trong 30 khách hàng"
  - "Không có dữ liệu"

**`pagination-btn-page`** — Nút số trang.
- Background: `{colors.canvas-soft}` `#F4F5F4`
- Text: `{colors.ink}`
- Typography: `{typography.body-sm-strong}` 14px / 500
- Padding: `{spacing.sm} {spacing.md}` (8px × 12px)
- Shape: `{rounded.pill}` 999px
- Min-width: 36px, height: 36px
- Hover: `{colors.surface-pressed}` `#E5E7EB`
- Active (current page): background `{colors.primary}` `#1B4D3E`, text `{colors.on-primary}` white

**`pagination-btn-prev`** & **`pagination-btn-next`** — Nút điều hướng.
- Background: `{colors.canvas}` white
- Border: 1px solid `{colors.ink}`
- Text: `{colors.ink}`
- Typography: `{typography.body-sm-strong}`
- Padding: `{spacing.sm} {spacing.lg}`
- Shape: `{rounded.pill}`
- Icon trước/sau text:
  - Prev: `« Trước` hoặc icon arrow-left
  - Next: `Tiếp »` hoặc icon arrow-right
- Disabled: opacity 0.4, cursor not-allowed

**`pagination-ellipsis`** — Dấu "..." khi có nhiều trang.
- Color: `{colors.mute}` `#9CA3AF`
- Typography: `{typography.body-sm-strong}`
- Padding: `{spacing.sm}`
- Hiển thị giữa các page buttons khi page count > 7

**`pagination-size-select`** — Dropdown chọn số dòng/trang.
- Same chrome as `text-input` nhưng compact: padding `{spacing.xs} {spacing.md}`
- Width: ~80px
- Options: 10, 20, 50, 100
- Label trước dropdown: "Số dòng/trang:" (typography body-sm)

### Pagination — Layout Pattern

Desktop pattern (full row):
┌─────────────────────────────────────────────────────────────────┐
│  Hiển thị 1-10 trong 30 đơn          [« Trước] [1] [2] [3] [Tiếp »]  Số dòng/trang: [10 ▼]  │
└─────────────────────────────────────────────────────────────────┘

Mobile pattern (stacked):
┌──────────────────────────────────┐
│  Hiển thị 1-10 trong 30          │
│                                  │
│  [« Trước] [1] [2] [3] [Tiếp »]  │
│                                  │
│  Số dòng/trang: [10 ▼]           │
└──────────────────────────────────┘

### Pagination — Logic Rules

- **Page numbers visible:** Always show first, last, current ± 2 neighbors. Use `…` ellipsis to compress.
  - Ví dụ 10 trang, đang ở trang 5: `[1] [...] [3] [4] [5] [6] [7] [...] [10]`
  - Ví dụ 5 trang, đang ở trang 2: `[1] [2] [3] [4] [5]`
- **Disabled states:**
  - "« Trước" disabled khi current page = 1
  - "Tiếp »" disabled khi current page = last
- **Empty state:** Khi totalElements = 0, hiển thị empty state card, KHÔNG hiển thị pagination bar.
- **Single page:** Khi totalPages = 1, ẨN page number buttons, chỉ hiển thị info text.
- **Page size change:** Reset về page 1 khi user đổi size.

### Pagination — Backend API Integration

Backend Spring Data Page format:
```json
{
  "content": [...],
  "totalElements": 30,
  "totalPages": 3,
  "number": 0,        // current page (0-indexed)
  "size": 10,
  "first": true,
  "last": false
}
```

Frontend logic:
- Display page number: `number + 1` (convert 0-indexed → 1-indexed)
- Show range: `(number * size + 1) - min((number+1) * size, totalElements)`
- API call: `GET /api/admin/dashboard/orders?status=...&page=0&size=10`

### Pagination — Vietnamese Copy

| Element | Text |
|---------|------|
| Previous button | « Trước |
| Next button | Tiếp » |
| Page size label | Số dòng/trang: |
| Info template | Hiển thị {start}-{end} trong {total} {entity} |
| Entity examples | "đơn", "tài xế", "khách hàng", "giao dịch" |
| Empty state | Không có dữ liệu để hiển thị |
| Loading state | Đang tải... |
| Error state | Không thể tải dữ liệu, vui lòng thử lại |

### Buttons

**`button-primary`** — Forest-green pill, canonical conversion target.
- Background `{colors.primary}` `#1B4D3E`, text `{colors.on-primary}` `#FFFFFF`, label `{typography.button-md}`, padding `{spacing.md} {spacing.xl}`, shape `{rounded.pill}`.
- Hover: `{colors.primary-soft}` `#2A6B57`
- Pressed: `{colors.primary-strong}` `#0F3329`
- Disabled: `{colors.mute}` `#9CA3AF` bg + `{colors.on-dark}` text + cursor not-allowed.
- Vietnamese labels: "Đặt đơn", "Đăng nhập", "Xác nhận", "Lưu", "Tiếp tục"

**`button-primary-amber`** — Amber pill cho celebratory/special CTAs.
- Background `{colors.accent}` `#F5A623`, text `{colors.on-accent}` `#1A1A1A`, shape `{rounded.pill}`.
- Use sparingly: "Nhận ưu đãi", "Mời bạn bè", "Nâng cấp gói"
- ⚠️ NEVER replace `button-primary` for default CTAs.

**`button-secondary`** — White pill với ink border, paired với primary.
- Background `{colors.canvas}`, text `{colors.ink}`, border 1px solid `{colors.ink}`, shape `{rounded.pill}`.
- Vietnamese labels: "Hủy", "Quay lại", "Đóng"

**`button-subtle`** — Gray pill cho tertiary actions inside cards.
- Background `{colors.canvas-soft}` `#F4F5F4`, text `{colors.ink}`, shape `{rounded.pill}`.
- Vietnamese: "Xem thêm", "Tìm hiểu", "Chi tiết"

**`button-floating`** — White pill với Level 3 shadow floating over photo.
- Background `{colors.canvas}`, text `{colors.ink}`, shape `{rounded.pill}`, shadow Level 3.

**`button-large-rounded`** — Larger CTA inside booking form (exception to pill rule).
- Background `{colors.primary}`, text `{colors.on-primary}`, label `{typography.button-large}` 18px, padding `{spacing.lg} {spacing.xl}`, shape `{rounded.xl}` 16 px (not pill — exception cho larger form context).
- Vietnamese: "Xem báo giá", "Đặt đơn ngay", "Xác nhận thanh toán"

**`button-danger`** — Red pill cho destructive actions.
- Background `{colors.danger}` `#DC2626`, text `{colors.on-primary}`, shape `{rounded.pill}`.
- Vietnamese: "Xóa", "Hủy đơn", "Từ chối", "Đăng xuất tất cả thiết bị"

**`button-tab-translucent`** — Tab-toggle on hero (e.g., "Đặt nhanh" / "Đặt trước").
- Background `{colors.canvas}`, text `{colors.ink}`, label `{typography.body-md-strong}`, shape `{rounded.pill-tab}` 36 px (deliberately tighter than 999 px).

### Cards & Containers

**`card-content`** — Canonical content card.
- Background `{colors.canvas}`, text `{colors.ink}`, padding `{spacing.2xl}`, shape `{rounded.xl}` 16 px. No shadow default.

**`card-elevated`** — Content card với Level 1 subtle drop.
- Same as `card-content` + Level 1 shadow.

**`card-soft-tinted`** — Gray-tinted card cho sub-region.
- Background `{colors.canvas-soft}`, text `{colors.ink}`, padding `{spacing.2xl}`, shape `{rounded.xl}`.
- Used for "Bạn đã đặt 5 đơn tháng này" callouts.

**`promo-card-illustrated`** — 2-column promo card với illustration + copy.
- Background `{colors.canvas}`, text `{colors.ink}`, padding `{spacing.2xl}`, shape `{rounded.xl}`. Headline `{typography.display-md}` or larger.
- Vietnamese examples:
  - Headline: "Chuyển nhà nhanh, an toàn"
  - Body: "Tài xế được xác minh, xe tải đủ loại, giá minh bạch."
  - CTA: "Đặt đơn ngay"

**`promo-card-on-dark`** — Polarity-flipped forest-green promo.
- Background `{colors.primary}`, text `{colors.on-dark}`, padding `{spacing.2xl}`, shape `{rounded.xl}`. Used for mid-page trust callouts.
- Vietnamese: "Đảm bảo an toàn 100%", "Hỗ trợ 24/7"

**`promo-card-amber`** — Amber celebratory promo (rare).
- Background `{colors.accent}`, text `{colors.on-accent}`, padding `{spacing.2xl}`, shape `{rounded.xl}`.
- Vietnamese: "Ưu đãi 20% lần đầu", "Mời 5 bạn nhận voucher 500K"

**`request-form-card`** — Booking form chrome trên hero.
- Background `{colors.canvas}`, padding `{spacing.lg}`, shape `{rounded.xl}`, Level 2 shadow.
- Contains: pickup row + dropoff row + time row + "Xem báo giá" `button-large-rounded`.

**`request-form-input-row`** — Per-field row trong booking form.
- Background `{colors.canvas-soft}`, padding `{spacing.lg}`, shape `{rounded.md}` 8 px.
- Layout: icon + label ("Điểm đón") + value ("Quận Ba Đình") + chevron.

**`showcase-image-card`** — Giant brand showcase ("Move_home — Chuyển nhà đơn giản").
- Background `{colors.primary}`, text `{colors.on-dark}` overlay, padding `{spacing.3xl}`, shape `{rounded.xl}`. Display-xxl headline overlays bottom of illustration.

### Inputs & Forms

**`text-input`** — Canonical text input.
- Background `{colors.canvas-soft}`, text `{colors.ink}`, placeholder `{colors.mute}`, body `{typography.body-md}`, padding `{spacing.lg}`, shape `{rounded.md}` 8 px.
- Focus: 2px solid `{colors.primary}` border, NO box-shadow ring.
- Disabled: `{colors.canvas-softer}` bg + `{colors.mute}` text.
- Vietnamese placeholders: "Nhập email của bạn", "Nhập số điện thoại", "Mật khẩu"

**`text-input-on-soft`** — Nested input on white card (lighter fill).
- Background `{colors.canvas-softer}`, otherwise identical to `text-input`.

**`select-input`** — Dropdown selector (district picker, vehicle type).
- Same chrome as `text-input` + chevron icon right.
- Vietnamese examples: "Chọn quận điểm đón", "Loại xe", "Thời gian hẹn"

### Navigation

**`nav-bar`** — Sticky top nav.
- Background `{colors.canvas}` on light pages, switches to `{colors.primary}` on dark hero pages. Padding `{spacing.lg} {spacing.3xl}`.
- Scrolled: Level 4 subtle shadow.
- Vietnamese links: "Trang chủ", "Dịch vụ", "Tài xế", "Hỗ trợ"

**`nav-link`** — Link row inside `nav-bar`.
- Text `{colors.ink}`, `{typography.body-md-strong}` 500 weight.
- Active: 2px bottom border `{colors.primary}`.

**`admin-sidebar-row`** — Admin sidebar nav row.
- Background `{colors.canvas}`, padding `{spacing.md} {spacing.lg}`, shape `{rounded.md}`.
- Active: 4px left border `{colors.primary}` + amber dot `{colors.accent}` 8px circle on right.
- Vietnamese: "Tổng quan", "Đơn hàng", "Tài xế", "Khách hàng", "Rút tiền", "Cấu hình"

**`footer`** — Deep forest-green footer band.
- Background `{colors.primary-strong}` `#0F3329` (anchor weight without pure black), text `{colors.on-dark}`, padding `{spacing.4xl} {spacing.3xl}`.
- Column headers `{typography.body-md-strong}` 500 weight.
- Vietnamese: "Về chúng tôi", "Liên hệ", "Điều khoản", "Chính sách bảo mật"

### Signature Components

**`hero-band-light`** — White hero với booking form card.
- Background `{colors.canvas}`, text `{colors.ink}`, padding `{spacing.4xl} {spacing.3xl}`. Headline `{typography.display-xxl}` (52 px / 700) left; `request-form-card` right.
- Vietnamese hero headline: "Chuyển nhà dễ dàng, an toàn"
- Subline: "Đặt tài xế chuyên nghiệp chỉ trong vài bước."

**`hero-band-dark`** — Forest-green hero (rare, dùng cho Driver landing).
- Background `{colors.primary}`, text `{colors.on-dark}`, padding `{spacing.4xl} {spacing.3xl}`. Same display-xxl scale; CTA inverts to `button-secondary` (white pill) hoặc `button-primary-amber` (amber pill).
- Vietnamese: "Trở thành tài xế Move_home" + "Đăng ký ngay" (amber CTA)

**`category-button`** — Horizontal-scroll category row.
- Background `{colors.canvas-soft}`, text `{colors.ink}`, label `{typography.body-sm-strong}`, padding `{spacing.sm} {spacing.lg}`, shape `{rounded.pill}`. Icon precedes label.
- Vietnamese examples: "🚚 Xe tải 500kg", "🚛 Xe tải 1 tấn", "📦 Xe ben", "🏠 Chuyển nhà trọn gói"

**`faq-row`** — FAQ accordion item.
- Background `{colors.canvas}`, question `{typography.body-md-strong}`, padding `{spacing.lg}` 0. Hairline dividers between rows.
- Vietnamese: "Làm thế nào để đặt đơn?", "Tài xế có an toàn không?", "Tôi có thể hủy đơn không?"

**`app-download-pill`** — App download CTA.
- Background `{colors.ink}`, text `{colors.on-dark}`, label `{typography.body-md-strong}`, padding `{spacing.md} {spacing.xl}`, shape `{rounded.pill}`.
- Vietnamese: "Tải ứng dụng Khách hàng", "Tải ứng dụng Tài xế"

**`icon-button-circular`** — Round icon container.
- Background `{colors.canvas-soft}`, dark icon, shape `{rounded.full}`. No label. Hover: `{colors.surface-pressed}`.

**`status-badge-*`** — Order status badges.
- All shape `{rounded.pill}` + `{typography.body-sm-strong}` + padding `{spacing.xs} {spacing.md}`.
- `Đang chờ`: bg `#FEF3C7` text `#92400E`
- `Đã nhận đơn`: bg `#DBEAFE` text `#1E40AF`
- `Đang giao`: bg `#DBEAFE` text `#1E40AF`
- `Hoàn thành`: bg `#D1FAE5` text `#065F46`
- `Đã hủy`: bg `#FEE2E2` text `#991B1B`
- `Khiếu nại`: bg `#FED7AA` text `#9A3412`

### Links

**`link-primary`** — Forest-green link (replaces browser-blue).
- Text `{colors.primary}` `#1B4D3E`, body `{typography.body-md}`, underline on hover.
- Vietnamese: "Quên mật khẩu?", "Đăng ký ngay", "Xem chi tiết"

**`link-on-dark`** — White link inside forest-green bands.
- Text `{colors.on-dark}`, body `{typography.body-md}`, underline on hover.

**`link-mute`** — Muted gray link inside footer.
- Text `{colors.hairline-mid}`, body `{typography.body-md}`.

**`link-mute-soft`** — Lightest gray link.
- Text `{colors.mute}`, body `{typography.body-md}`.

### Admin Dashboard Components

**`ex-kpi-card`** — Dashboard KPI card.
- Background `{colors.canvas}`, padding `{spacing.2xl}`, shape `{rounded.xl}`, Level 1 shadow.
- Value `{typography.display-lg}` 32 px, label `{typography.body-sm-strong}`, delta indicator (`{colors.success}` green up arrow or `{colors.danger}` red down arrow).
- Vietnamese labels: "Tổng khách hàng", "Tài xế đang hoạt động", "Đơn hàng tháng này", "Doanh thu tháng này"

**`ex-data-table-cell`** — Admin table cells.
- Header bg `{colors.canvas-soft}`, header `{typography.body-sm-strong}`, body `{typography.body-sm}`, cell padding `{spacing.md} {spacing.lg}`, row border `{colors.surface-pressed}`.
- Sort active: `{colors.primary}` icon.
- Vietnamese column headers: "Mã đơn", "Khách hàng", "Tài xế", "Trạng thái", "Tổng tiền", "Ngày tạo"

## Do's and Don'ts

### Do
- **Reserve `{colors.primary}` `#1B4D3E` cho primary CTAs.** One forest-green pill per visible viewport = conversion focus.
- **Use `{rounded.pill}` 999 px on every interactive element** (buttons, chips, badges, app pills). Pill IS brand's geometric signature.
- **Render cards trong `{rounded.xl}` 16 px** — promo cards, content cards, booking form, KPI cards, modals.
- **Set every headline trong `{typography.display-*}` weight 700 sentence-case Vietnamese with diacritics.** Display face never carries body copy.
- **Use polarity-flipped forest-green promo bands mid-page** để break canvas-white rhythm. Polarity shift IS depth cue.
- **Anchor every promo card với 4:3 editorial illustration** (carton boxes, families, mini-trucks); never generic stock photo.
- **Use amber `{colors.accent}` sparingly** — reserved for celebratory CTAs, featured indicators, accent dots. NEVER as default primary.
- **Use Vietnamese diacritics EVERYWHERE in UI.** "Tài xế" not "Tai xe". "Đặt đơn" not "Dat don".
- **Load Be Vietnam Pro from Google Fonts** at start of every HTML page với preconnect hints.
- **Test rendering với long Vietnamese strings** ("Phụ phí giờ cao điểm và phụ phí ngõ nhỏ") — ensure no overflow.

### Don't
- **Don't introduce a third brand color** (red, blue, purple). Brand entirely forest-green + amber + grayscale; new accents flatten system.
- **Don't render primary CTA dưới dạng `{rounded.xl}` rectangle** ngoại trừ inside booking form (`button-large-rounded` exception).
- **Don't use all-caps display headlines.** Sentence-case Vietnamese is voice; uppercase chỉ restricted cho eyebrow tags.
- **Don't drop shadow trên every card.** Level 0 flat is default; shadow reserved cho floating pills và booking form.
- **Don't reduce brand to illustrations alone.** Pill geometry + forest-green/canvas duet carries brand even without illustrations.
- **Don't tighten or loosen letter-spacing** on display face. Brand never letter-spaces ngoại trừ eyebrow.
- **Don't use `{rounded.full}` 9999 px cho square cards** — pill 999 và full 9999 identical for interactive but cards stay at `{rounded.xl}` 16.
- **Don't strip Vietnamese diacritics.** "Dat don" / "Tai xe" / "Phu phi" are UNACCEPTABLE in user-facing UI.
- **Don't mix amber và forest-green within same CTA group.** Pick one accent per section.
- **Don't use Inter / Roboto / Arial as primary font** — they collision với Vietnamese tones at body sizes. Be Vietnam Pro is mandatory.

## Vietnamese Copy Guide — REQUIRED for ALL UI generation

### Buttons & Actions

| Action | Vietnamese | Component |
|--------|------------|-----------|
| Submit / Confirm | Xác nhận | `button-primary` |
| Login | Đăng nhập | `button-primary` |
| Register | Đăng ký | `button-primary` |
| Save | Lưu | `button-primary` |
| Continue | Tiếp tục | `button-primary` |
| Cancel | Hủy | `button-secondary` |
| Back | Quay lại | `button-secondary` |
| Close | Đóng | `button-secondary` |
| Skip | Bỏ qua | `button-subtle` |
| Learn more | Tìm hiểu thêm | `button-subtle` |
| View details | Xem chi tiết | `button-subtle` |
| Delete | Xóa | `button-danger` |
| Cancel order | Hủy đơn | `button-danger` |
| Logout | Đăng xuất | Link or `button-secondary` |
| Book now | Đặt đơn ngay | `button-primary` |
| Get quote | Xem báo giá | `button-large-rounded` |
| Accept order | Nhận đơn | `button-primary` |
| Special offer | Nhận ưu đãi | `button-primary-amber` |
| Invite friends | Mời bạn bè | `button-primary-amber` |

### Order Statuses

| Status DB | Vietnamese | Badge Color |
|-----------|------------|-------------|
| PENDING | Đang chờ | yellow `#FEF3C7` |
| ACCEPTED | Đã nhận đơn | blue `#DBEAFE` |
| IN_PROGRESS | Đang giao | blue `#DBEAFE` |
| COMPLETED | Hoàn thành | green `#D1FAE5` |
| CANCELLED | Đã hủy | red `#FEE2E2` |
| DISPUTED | Khiếu nại | orange `#FED7AA` |

### User Roles

| Role DB | Vietnamese |
|---------|------------|
| CUSTOMER | Khách hàng |
| DRIVER | Tài xế |
| MANAGER | Quản lý |
| ADMIN | Quản trị viên |

### User Statuses

| Status DB | Vietnamese |
|-----------|------------|
| ACTIVE | Đang hoạt động |
| PENDING_VERIFY | Chờ xác thực email |
| PENDING_DOCUMENTS | Chờ bổ sung giấy tờ |
| PENDING_DEPOSIT | Chờ đặt cọc |
| PENDING_APPROVAL | Chờ duyệt |
| SUSPENDED | Bị khóa |
| REJECTED | Bị từ chối |

### Common UI Labels

**Auth pages:**
- "Đăng nhập vào Move_home"
- "Tạo tài khoản mới"
- "Quên mật khẩu?"
- "Đặt lại mật khẩu"
- "Email", "Mật khẩu", "Xác nhận mật khẩu", "Số điện thoại"
- "Họ và tên", "Ngày sinh", "Giới tính"
- "Tôi đồng ý với điều khoản sử dụng"

**Booking form:**
- "Điểm đón", "Điểm trả", "Thời gian hẹn"
- "Quận Ba Đình", "Quận Hoàn Kiếm", "Quận Hai Bà Trưng", "Quận Đống Đa", "Quận Tây Hồ", "Quận Cầu Giấy", "Quận Thanh Xuân", "Quận Long Biên", "Quận Hà Đông"
- "Loại xe", "Xe tải 500kg", "Xe tải 1 tấn", "Xe tải 1.5 tấn"
- "Ghi chú thêm", "Số tầng", "Có thang máy", "Ngõ nhỏ", "Cần bốc xếp"

**Pricing breakdown:**
- "Giá cơ bản"
- "Phụ phí giờ cao điểm"
- "Phụ phí ngõ nhỏ"
- "Phụ phí tầng"
- "Phí bốc xếp"
- "Phí hoa hồng"
- "Tổng cộng"

**Customer dashboard:**
- "Đơn hàng của tôi"
- "Đơn đang chờ"
- "Lịch sử đơn"
- "Tài khoản của tôi"
- "Số dư"

**Driver dashboard:**
- "Đơn có sẵn"
- "Đơn đang giao"
- "Lịch sử nhận đơn"
- "Thu nhập tháng này"
- "Đánh giá trung bình"

**Admin dashboard:**
- "Tổng quan"
- "Tổng khách hàng"
- "Tài xế đang hoạt động"
- "Đơn hàng hôm nay"
- "Doanh thu tháng này"
- "Phí hoa hồng tháng này"
- "Top 5 tài xế"
- "Đơn hàng gần đây"
- "Phân bổ trạng thái đơn"

### Error Messages

| Context | Vietnamese |
|---------|------------|
| Email invalid | Email không hợp lệ |
| Email exists | Email đã được sử dụng |
| Password too short | Mật khẩu phải có ít nhất 8 ký tự |
| Password mismatch | Mật khẩu xác nhận không khớp |
| Phone invalid | Số điện thoại không đúng định dạng |
| Not verified | Vui lòng xác thực email trước khi đăng nhập |
| Wrong credentials | Sai email hoặc mật khẩu |
| Account locked | Tài khoản bị khóa, vui lòng thử lại sau 15 phút |
| Required field | Trường này bắt buộc |
| Terms not accepted | Vui lòng đồng ý với điều khoản |
| Server error | Lỗi hệ thống, vui lòng thử lại |
| Network error | Mất kết nối mạng |
| Session expired | Phiên đăng nhập hết hạn, vui lòng đăng nhập lại |

### Success Messages

| Context | Vietnamese |
|---------|------------|
| Login success | Đăng nhập thành công |
| Register success | Đăng ký thành công, vui lòng kiểm tra email |
| Order created | Đặt đơn thành công |
| Order accepted | Đã nhận đơn |
| Order completed | Hoàn thành đơn hàng |
| Profile updated | Cập nhật thông tin thành công |
| Password changed | Đổi mật khẩu thành công |

## Implementation Notes

### CSS Variables (paste vào `frontend/css/styles-v2.css`)

```css
:root {
  /* Colors */
  --color-primary: #1B4D3E;
  --color-primary-soft: #2A6B57;
  --color-primary-strong: #0F3329;
  --color-on-primary: #FFFFFF;
  --color-accent: #F5A623;
  --color-accent-soft: #FBC470;
  --color-accent-strong: #D88A0B;
  --color-on-accent: #1A1A1A;
  --color-ink: #1A1A1A;
  --color-body: #5E5E5E;
  --color-mute: #9CA3AF;
  --color-hairline-mid: #4B5563;
  --color-canvas: #FFFFFF;
  --color-canvas-soft: #F4F5F4;
  --color-canvas-softer: #FAFAF9;
  --color-surface-pressed: #E5E7EB;
  --color-link: #1B4D3E;
  --color-on-dark: #FFFFFF;
  --color-success: #16A34A;
  --color-warning: #F59E0B;
  --color-danger: #DC2626;
  --color-info: #0EA5E9;

  /* Typography */
  --font-family-base: 'Be Vietnam Pro', system-ui, 'Helvetica Neue', Arial, sans-serif;
  
  --font-size-display-xxl: 52px;
  --font-size-display-xl: 36px;
  --font-size-display-lg: 32px;
  --font-size-display-md: 24px;
  --font-size-display-sm: 20px;
  --font-size-body-lg: 18px;
  --font-size-body-md: 16px;
  --font-size-body-sm: 14px;
  --font-size-caption: 12px;

  --line-height-display-xxl: 60px;
  --line-height-display-xl: 44px;
  --line-height-display-lg: 40px;
  --line-height-display-md: 32px;
  --line-height-body-md: 24px;
  --line-height-body-sm: 20px;

  --font-weight-regular: 400;
  --font-weight-medium: 500;
  --font-weight-semibold: 600;
  --font-weight-bold: 700;

  /* Spacing */
  --spacing-xxs: 4px;
  --spacing-xs: 6px;
  --spacing-sm: 8px;
  --spacing-md: 12px;
  --spacing-lg: 16px;
  --spacing-xl: 20px;
  --spacing-2xl: 24px;
  --spacing-3xl: 32px;
  --spacing-4xl: 48px;
  --spacing-5xl: 64px;

  /* Rounded */
  --rounded-none: 0px;
  --rounded-md: 8px;
  --rounded-lg: 12px;
  --rounded-xl: 16px;
  --rounded-2xl: 20px;
  --rounded-pill: 999px;
  --rounded-full: 9999px;

  /* Shadows */
  --shadow-level-1: 0px 2px 8px 0px rgba(0, 0, 0, 0.08);
  --shadow-level-2: 0px 4px 16px 0px rgba(0, 0, 0, 0.12);
  --shadow-level-3: 0px 2px 8px 0px rgba(0, 0, 0, 0.16);
  --shadow-level-4: 0px 1px 4px 0px rgba(0, 0, 0, 0.06);
}

html {
  font-family: var(--font-family-base);
  color: var(--color-ink);
  background: var(--color-canvas);
  -webkit-font-smoothing: antialiased;
  -moz-osx-font-smoothing: grayscale;
}

body {
  margin: 0;
  font-size: var(--font-size-body-md);
  line-height: var(--line-height-body-md);
}
```

### HTML Head requirement

```html
<!DOCTYPE html>
<html lang="vi">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>Move_home — Chuyển nhà dễ dàng</title>
  
  <!-- Preconnect to Google Fonts -->
  <link rel="preconnect" href="https://fonts.googleapis.com">
  <link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
  
  <!-- Be Vietnam Pro font with Vietnamese subset -->
  <link href="https://fonts.googleapis.com/css2?family=Be+Vietnam+Pro:wght@400;500;600;700&display=swap&subset=vietnamese" rel="stylesheet">
  
  <link rel="stylesheet" href="/frontend/css/styles-v2.css">
</head>
```

⚠️ **Critical:**
- `lang="vi"` ở `<html>` — browser biết content tiếng Việt
- `charset="UTF-8"` mandatory — không có dấu sẽ vỡ
- `subset=vietnamese` trong Google Fonts URL — load Vietnamese glyph subset chính xác

## End of file

This design system is the source of truth for all UI generation cho dự án Move_home Sprint 2 onwards. Any AI generating HTML/CSS/components MUST:
1. Use exact color values, typography tokens, spacing values defined above
2. Use Vietnamese with diacritics for ALL user-facing strings
3. Reference component patterns (`button-primary`, `card-content`, etc.) by exact name
4. Follow the Do's and Don'ts strictly
5. Test rendering with realistic long Vietnamese strings before declaring done

Version: 1.0 (Move_home Sprint 2 — replaces DESIGN.md v1 Stripi brand)