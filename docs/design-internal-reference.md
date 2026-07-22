# Move_home — Design System v1.0

**Status:** Living document
**Created:** 2026-05-30
**Owner:** Frontend team (5 member SWP)
**Stack:** HTML + Vanilla JS + Vanilla CSS (theo Constitution AC-01)
**Reference:** docs/CONTEXT.md v2.0, .specify/memory/constitution.md v1.4.0

---

> ℹ️ **Brand & nguồn canonical:**
> - **Brand color:** primary **forest green `#1B4D3E`**, accent **amber `#F5A623`**, font
>   **Be Vietnam Pro** (theo `DESIGN.md` + constitution HR-19). KHÔNG dùng màu xanh/tím cho code mới —
>   các bảng màu cũ trong file này đã bị HR-19 ghi đè.
> - **Status mapping:** "8 trạng thái Order" nêu dưới là mô hình nghiệp vụ; constraint DB cho
>   **11 giá trị** (còn lẫn legacy+mới) — khi map badge, đối chiếu `service_order` CHECK (V21) + cần chốt
>   về 1 bộ canonical. Badge cho trạng thái mới: `dispute` (OPEN/INVESTIGATING/...),
>   `order_cancellation_refund` (PENDING/REFUNDED/REJECTED), notification, chat — xem §1.0.
> - Phần **typography (Be Vietnam Pro / Inter) và status-mapping business logic** của file này là
>   nguồn đúng theo CLAUDE.md §5.5 (design-internal-reference wins về typography + status mapping).

---

## 1.0. Badge trang thai (thuc the 019-026)

> Map trang thai cac thuc the moi (019-026) sang **intent** cua he thong badge (dung token
> `--color-success`/`--color-warning`/`--color-danger`/`--color-info`/`--color-muted` neu co; neu thieu:
> success = forest green `#1B4D3E`, warning/pending = amber `#F5A623`, danger = `#C0392B`, info =
> `#2E5AAC`, muted = neutral gray). Business status mapping van la nguon dung cua file nay (CLAUDE.md §5.5).

| Thuc the | Trang thai | Intent / mau | Nhan tieng Viet |
|----------|-----------|--------------|-----------------|
| `dispute` | `OPEN` | warning (amber) | "Cho xu ly" |
| `dispute` | `INVESTIGATING` | info (xanh duong) | "Dang xac minh" |
| `dispute` | `RESOLVED_REFUND` | success (green) | "Da hoan khach" |
| `dispute` | `RESOLVED_DEDUCT` | success (green) | "Da tru tai xe" |
| `dispute` | `CLOSED_NO_FAULT` | muted (gray) | "Dong — khong loi" |
| `order_cancellation_refund` | `PENDING` | warning | "Cho duyet" |
| `order_cancellation_refund` | `REFUNDED` | success | "Da hoan coc" |
| `order_cancellation_refund` | `REJECTED` | danger | "Tu choi" |
| `withdrawal_request` / `customer_withdrawal_request` | `PENDING` | warning | "Cho xu ly" |
| " | `PROCESSED` | success | "Da chuyen" |
| " | `REJECTED` | danger | "Bi tu choi" |
| " | `CANCELLED` | muted | "Da huy" |
| `notification` | `is_read=false` | warning (cham/bold) | (chua doc) |
| `notification` | `is_read=true` | muted | (da doc) |

> Order status (11 gia tri, V21) va Driver status van dung Status Mapping goc cua file nay — luu y cap
> legacy+moi (ASSIGNED/ACCEPTED, DISPUTED/IN_DISPUTE) can chot ve 1 bo canonical.

---

## 1. Triet ly thiet ke

### Mission Statement

Move_home la nen tang marketplace chuyen nha tin cay cho nguoi dan noi thanh Ha Noi. Thiet ke phai
phan anh su tin tuong do: khach hang cam thay an toan khi giao do dac cho cong ty, Driver cam thay
minh la doi tac chuyen nghiep, Manager thay ro moi don dang xu ly dau.

**Giao dien phai lam duoc 3 viec:**
- **Khach hang:** Dat don de dang va biet gia truoc — khong bi bat ngo.
- **Driver:** Biet nguyen tac ro rang, thao tac don gian ngay ca khi dang tren xe.
- **Manager / Admin:** Nhin mot luot la biet toan canh — khong phai boi trong du lieu.

---

### 5 Nguyen Tac Thiet Ke

**1. Clarity over cleverness — Ro nghia hon sang tao**
Moi nut, nhan, tieu de deu viet tieng Viet ro rang. Khong dung icon thay the hoan toan cho chu
(icon ho tro chu, khong thay chu). Nut "Xac nhan dat don" tot hon "OK". Trang thai "Dang giao do"
ro hon "In Progress".

**2. Mobile-friendly, khong fully responsive**
Focus desktop 1280px+. Tablet 768px+ van dung duoc (layout thu gon). Mobile < 768px: chi dam bao
cac luong cam lay di dong (Driver chap nhan don, Customer theo doi). Khong hy sinh desktop UX
de co mobile 100%.

**3. Generous whitespace — Khong nhoi nhet**
Trang quan ly nhieu du lieu (danh sach don, danh sach Driver) de bi qua tai. Whitespace giup mat
nghi va nao xu ly nhanh hon. Padding toi thieu giua cac khoi: var(--space-6) = 24px.

**4. Status-driven UI — Trang thai la trung tam**
8 trang thai Order, 7 trang thai Driver — moi trang thai phai hien thi ro bang mau sac va nhan.
Nguoi dung khong phai doc chu de biet don dang o buoc nao. Status pill mau sac + chu = thong tin
nhanh nhat tren giao dien.

**5. Trust signals manh — The hien su uy tin**
- Logo VNPay kem voi moi form thanh toan
- Icon khoa (lock) ben canh HTTPS, password field
- Badge "Da Manager duyet" tren Driver profile
- Lich su giao dich day du, co the tra cuu bat cu luc nao

---

### Anti-patterns Can Tranh

1. **Khong dung mau dam + xanh ngoc cung luc** — gay roi loan, mat tap trung vao CTA chinh.
2. **Khong de icon nho hon 24x24px** — kho bam tren man hinh cam ung va kho nhin.
3. **Khong dung font-size nho hon 14px cho noi dung chinh** — nguoi lon tuoi kho doc; 12px chi
   cho phu chu / meta info.
4. **Khong dung mau xam nhat (#999 tro xuat) cho text chinh** — contrast ratio phai >= 4.5:1
   (WCAG AA).
5. **Khong disabled nut ma khong co tooltip giai thich ly do** — nguoi dung khong biet phai lam
   gi tiep theo.
6. **Khong dung alert/confirm cua trinh duyet (window.alert, window.confirm)** — thay bang modal
   noi bo nhat quan voi design system.
7. **Khong de trang trong khong co empty state** — moi danh sach trong phai co illustration /
   message va CTA.

---

## 2. Design Tokens (CSS Variables)

> Dat trong file `css/tokens.css`, import vao moi trang truoc bat ky stylesheet nao khac.
> **Quy tac:** Moi value hardcode trong CSS la code smell — luon dung token.

```css
/* ============================================================
   MOVE_HOME DESIGN TOKENS v1.0
   Import: <link rel="stylesheet" href="/css/tokens.css">
   ============================================================ */

:root {

  /* ----------------------------------------------------------
     COLORS — Primary (Blue)
     Base hue: #2563EB (blue-600, tuong duong Tailwind)
     Dung cho: CTA chinh, link, focus ring, header
     ---------------------------------------------------------- */
  --color-primary-50:  #EFF6FF;   /* background nhac nhe, hover row */
  --color-primary-100: #DBEAFE;   /* badge background nhe */
  --color-primary-200: #BFDBFE;   /* border color nhe */
  --color-primary-300: #93C5FD;   /* disabled state */
  --color-primary-400: #60A5FA;   /* icon secondary */
  --color-primary-500: #3B82F6;   /* hover tren primary button */
  --color-primary-600: #2563EB;   /* PRIMARY — nut chinh, link */
  --color-primary-700: #1D4ED8;   /* active press state */
  --color-primary-800: #1E40AF;   /* heading accent */
  --color-primary-900: #1E3A8A;   /* dark mode chu (du phong) */

  /* ----------------------------------------------------------
     COLORS — Semantic
     ---------------------------------------------------------- */

  /* Success — ACTIVE, COMPLETED, RESOLVED */
  --color-success:        #16A34A;  /* text / icon */
  --color-success-bg:     #DCFCE7;  /* pill background */
  --color-success-border: #86EFAC;  /* pill border */

  /* Warning — PENDING_*, AWAITING_FINAL_PAYMENT, IN_DISPUTE */
  --color-warning:        #D97706;  /* text / icon */
  --color-warning-bg:     #FEF3C7;  /* pill background */
  --color-warning-border: #FCD34D;  /* pill border */

  /* Danger — REJECTED, CANCELLED, SUSPENDED, error */
  --color-danger:         #DC2626;  /* text / icon */
  --color-danger-bg:      #FEE2E2;  /* pill background, input error bg */
  --color-danger-border:  #FCA5A5;  /* input error border */

  /* Info — CONFIRMED, ASSIGNED, thong bao trung tinh */
  --color-info:           #0284C7;  /* text / icon */
  --color-info-bg:        #E0F2FE;  /* pill background */
  --color-info-border:    #7DD3FC;  /* pill border */

  /* ----------------------------------------------------------
     COLORS — Neutrals
     ---------------------------------------------------------- */
  --color-text-primary:   #111827;  /* heading, body text chinh */
  --color-text-secondary: #4B5563;  /* text phu (label, description) */
  --color-text-tertiary:  #9CA3AF;  /* placeholder, meta info */
  --color-text-disabled:  #D1D5DB;  /* text disabled */
  --color-text-inverse:   #FFFFFF;  /* text tren nen toi (button primary) */

  --color-bg-page:        #F9FAFB;  /* nen trang chinh */
  --color-bg-card:        #FFFFFF;  /* card, panel, modal */
  --color-bg-hover:       #F3F4F6;  /* hover row, hover menu item */
  --color-bg-disabled:    #E5E7EB;  /* input / button disabled bg */
  --color-bg-overlay:     rgba(0, 0, 0, 0.45); /* modal backdrop */

  --color-border:         #E5E7EB;  /* border mac dinh */
  --color-border-focus:   #2563EB;  /* border khi focus (= primary-600) */
  --color-border-error:   #DC2626;  /* border input loi */

  /* ----------------------------------------------------------
     SPACING — Scale 4px
     Dung nhat quan, tranh magic number
     ---------------------------------------------------------- */
  --space-1:   4px;
  --space-2:   8px;
  --space-3:  12px;
  --space-4:  16px;
  --space-5:  20px;
  --space-6:  24px;
  --space-8:  32px;
  --space-10: 40px;
  --space-12: 48px;
  --space-16: 64px;

  /* ----------------------------------------------------------
     TYPOGRAPHY
     Import Inter tu Google Fonts truoc khi dung
     <link href="https://fonts.googleapis.com/css2?family=Inter:wght@400;500;600;700&display=swap">
     ---------------------------------------------------------- */
  --font-family-base: "Inter", -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif;
  --font-family-mono: "JetBrains Mono", "Courier New", Courier, monospace;

  --font-size-xs:   12px;   /* phu chu, meta, char counter */
  --font-size-sm:   14px;   /* label, badge, button-sm */
  --font-size-base: 16px;   /* body text mac dinh */
  --font-size-md:   18px;   /* sub-heading */
  --font-size-lg:   20px;   /* heading card */
  --font-size-xl:   24px;   /* page section heading */
  --font-size-2xl:  28px;   /* page title */
  --font-size-3xl:  32px;   /* hero / KPI number */

  --font-weight-normal:   400;
  --font-weight-medium:   500;
  --font-weight-semibold: 600;
  --font-weight-bold:     700;

  --line-height-tight:   1.2;   /* heading ngan */
  --line-height-base:    1.5;   /* body text chuan */
  --line-height-relaxed: 1.75;  /* paragraph dai, de doc */

  /* ----------------------------------------------------------
     SHADOWS
     ---------------------------------------------------------- */
  --shadow-sm:    0 1px 3px rgba(0,0,0,0.08), 0 1px 2px rgba(0,0,0,0.06);
  --shadow-md:    0 4px 6px rgba(0,0,0,0.07), 0 2px 4px rgba(0,0,0,0.06);
  --shadow-lg:    0 10px 15px rgba(0,0,0,0.08), 0 4px 6px rgba(0,0,0,0.05);
  --shadow-focus: 0 0 0 3px rgba(37, 99, 235, 0.35); /* primary-600 ring */

  /* ----------------------------------------------------------
     BORDER RADII
     ---------------------------------------------------------- */
  --radius-sm:   4px;     /* button nho, badge */
  --radius-md:   8px;     /* input, card, button mac dinh */
  --radius-lg:  12px;     /* modal, large card */
  --radius-xl:  16px;     /* hero card, onboarding step card */
  --radius-full: 9999px;  /* pill status, avatar, tag */

  /* ----------------------------------------------------------
     TRANSITIONS
     ---------------------------------------------------------- */
  --transition-fast: 150ms ease;   /* hover, focus */
  --transition-base: 250ms ease;   /* slide, fade component */
  --transition-slow: 400ms ease;   /* page transition, accordion */

  /* ----------------------------------------------------------
     Z-INDEX SCALE
     ---------------------------------------------------------- */
  --z-base:           0;
  --z-sticky:       100;    /* header sticky */
  --z-dropdown:    1000;    /* dropdown menu, tooltip */
  --z-modal-backdrop: 1100;
  --z-modal:       1200;
  --z-toast:       1300;    /* toast luon tren modal */

}
```

---

### Status → Color Mapping

> **Quy tac:** Moi status pill dung class `.status-pill--<token>`. CSS token lay tu bang duoi.
> Codex: khi render status, tra bang nay, khong hardcode mau.

**Order Status (8 trang thai)**

| Status | Mau chu | Mau nen | Token |
|--------|---------|---------|-------|
| `PENDING_PAYMENT` | `--color-warning` | `--color-warning-bg` | `warning` |
| `CONFIRMED` | `--color-info` | `--color-info-bg` | `info` |
| `ASSIGNED` | `--color-info` | `--color-info-bg` | `info` |
| `IN_PROGRESS` | `--color-primary-600` | `--color-primary-50` | `primary` |
| `AWAITING_FINAL_PAYMENT` | `--color-warning` | `--color-warning-bg` | `warning` |
| `COMPLETED` | `--color-success` | `--color-success-bg` | `success` |
| `CANCELLED` | `--color-danger` | `--color-danger-bg` | `danger` |
| `IN_DISPUTE` | `--color-danger` | `--color-danger-bg` | `danger` |

**Driver Status (7 trang thai)**

| Status | Mau chu | Mau nen | Token |
|--------|---------|---------|-------|
| `PENDING_VERIFY` | `--color-warning` | `--color-warning-bg` | `warning` |
| `PENDING_DOCUMENTS` | `--color-warning` | `--color-warning-bg` | `warning` |
| `PENDING_DEPOSIT` | `--color-warning` | `--color-warning-bg` | `warning` |
| `PENDING_APPROVAL` | `--color-info` | `--color-info-bg` | `info` |
| `ACTIVE` | `--color-success` | `--color-success-bg` | `success` |
| `REJECTED` | `--color-danger` | `--color-danger-bg` | `danger` |
| `SUSPENDED` | `--color-danger` | `--color-danger-bg` | `danger` |

```css
/* Status pill — su dung voi cac variant tuong ung */
.status-pill {
  display: inline-flex;
  align-items: center;
  gap: var(--space-1);
  padding: 2px var(--space-2);
  border-radius: var(--radius-full);
  font-size: var(--font-size-xs);
  font-weight: var(--font-weight-semibold);
  font-family: var(--font-family-base);
  line-height: 1.5;
  white-space: nowrap;
}
.status-pill--success  { color: var(--color-success); background: var(--color-success-bg); }
.status-pill--warning  { color: var(--color-warning); background: var(--color-warning-bg); }
.status-pill--danger   { color: var(--color-danger);  background: var(--color-danger-bg);  }
.status-pill--info     { color: var(--color-info);    background: var(--color-info-bg);    }
.status-pill--primary  { color: var(--color-primary-600); background: var(--color-primary-50); }
```

---

## 3. Layout System

> File: `css/layout.css`

### Breakpoints

| Ten | Gia tri | Mo ta |
|-----|---------|-------|
| mobile | < 768px | Chi dam bao luong Driver/Customer cam tay |
| tablet | 768px – 1023px | Layout thu gon, sidebar an |
| desktop | >= 1024px | Layout day du, focus chinh |

### Container

```css
/* ============================================================
   LAYOUT SYSTEM
   ============================================================ */

/* Container chinh — max 1280px, can giua */
.container {
  width: 100%;
  max-width: 1280px;
  margin-inline: auto;
  padding-inline: var(--space-6);   /* 24px sides tren desktop */
}

/* Container hep — dung cho trang Login / Register / form 1 cot */
.container-narrow {
  width: 100%;
  max-width: 480px;
  margin-inline: auto;
  padding-inline: var(--space-4);
}

/* Container vua — dung cho trang onboarding step, confirm page */
.container-medium {
  width: 100%;
  max-width: 720px;
  margin-inline: auto;
  padding-inline: var(--space-4);
}

@media (max-width: 767px) {
  .container,
  .container-narrow,
  .container-medium {
    padding-inline: var(--space-4);  /* 16px tren mobile */
  }
}

/* ----------------------------------------------------------
   GRID UTILITIES
   ---------------------------------------------------------- */
.grid-2,
.grid-3,
.grid-4 {
  display: grid;
  gap: var(--space-6);
}

.grid-2 { grid-template-columns: repeat(2, 1fr); }
.grid-3 { grid-template-columns: repeat(3, 1fr); }
.grid-4 { grid-template-columns: repeat(4, 1fr); }

@media (max-width: 1023px) {
  .grid-4 { grid-template-columns: repeat(2, 1fr); }
  .grid-3 { grid-template-columns: repeat(2, 1fr); }
}

@media (max-width: 767px) {
  .grid-2,
  .grid-3,
  .grid-4 { grid-template-columns: 1fr; }
}

/* ----------------------------------------------------------
   FLEX UTILITIES
   ---------------------------------------------------------- */
.flex-row     { display: flex; flex-direction: row; }
.flex-col     { display: flex; flex-direction: column; }
.flex-center  { display: flex; align-items: center; justify-content: center; }
.flex-between { display: flex; align-items: center; justify-content: space-between; }
.flex-start   { display: flex; align-items: center; justify-content: flex-start; }
.flex-end     { display: flex; align-items: center; justify-content: flex-end; }
.flex-wrap    { flex-wrap: wrap; }

.flex-gap-1  { gap: var(--space-1); }
.flex-gap-2  { gap: var(--space-2); }
.flex-gap-3  { gap: var(--space-3); }
.flex-gap-4  { gap: var(--space-4); }
.flex-gap-6  { gap: var(--space-6); }

/* ----------------------------------------------------------
   PAGE STRUCTURE
   Tat ca trang dung cau truc: .page > .page-header + .page-content
   ---------------------------------------------------------- */

/* Nen trang toan man hinh */
.page {
  min-height: 100vh;
  background-color: var(--color-bg-page);
  display: flex;
  flex-direction: column;
}

/* Header sticky — 64px, logo + nav + user menu */
.page-header {
  position: sticky;
  top: 0;
  z-index: var(--z-sticky);
  height: 64px;
  background-color: var(--color-bg-card);
  border-bottom: 1px solid var(--color-border);
  box-shadow: var(--shadow-sm);
  display: flex;
  align-items: center;
}

/* Tieu de trang (h1 cua moi trang) */
.page-title {
  font-size: var(--font-size-2xl);
  font-weight: var(--font-weight-bold);
  color: var(--color-text-primary);
  line-height: var(--line-height-tight);
  margin: 0 0 var(--space-6) 0;
}

/* Vung noi dung chinh */
.page-content {
  flex: 1;
  padding-top: var(--space-6);
  padding-bottom: var(--space-12);
}

/* Footer optional */
.page-footer {
  height: 80px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-top: 1px solid var(--color-border);
  background-color: var(--color-bg-card);
  color: var(--color-text-secondary);
  font-size: var(--font-size-sm);
}
```

---

## 4. Form Components

> File: `css/forms.css`

### 4.1 Input Text

```html
<!-- HTML Template -->
<div class="form-group">
  <label class="form-label" for="username">
    Ten dang nhap <span class="form-required" aria-hidden="true">*</span>
  </label>
  <input
    class="form-input"
    type="text"
    id="username"
    name="username"
    placeholder="vd: nguyen_van_a"
    autocomplete="username"
    required
  />
  <!-- Hien thi khi co loi -->
  <span class="form-error" role="alert">Ten dang nhap da duoc su dung.</span>
</div>
```

```css
/* ----------------------------------------------------------
   FORM LABEL
   ---------------------------------------------------------- */
.form-label {
  display: block;
  margin-bottom: var(--space-2);
  font-size: var(--font-size-sm);
  font-weight: var(--font-weight-medium);
  color: var(--color-text-primary);
  font-family: var(--font-family-base);
}

.form-required {
  color: var(--color-danger);
  margin-left: 2px;
}

/* ----------------------------------------------------------
   FORM INPUT
   ---------------------------------------------------------- */
.form-input {
  display: block;
  width: 100%;
  padding: var(--space-3) var(--space-4);
  font-size: var(--font-size-base);
  font-family: var(--font-family-base);
  color: var(--color-text-primary);
  background-color: var(--color-bg-card);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  outline: none;
  transition: border-color var(--transition-fast), box-shadow var(--transition-fast);
  box-sizing: border-box;
}

.form-input::placeholder {
  color: var(--color-text-tertiary);
}

.form-input:hover:not(:disabled) {
  border-color: var(--color-primary-300);
}

.form-input:focus:not(:disabled) {
  border-color: var(--color-border-focus);
  box-shadow: var(--shadow-focus);
}

/* Error state */
.form-input--error,
.form-input.error {
  border-color: var(--color-border-error);
  background-color: #FFF8F8;
}

.form-input--error:focus {
  box-shadow: 0 0 0 3px rgba(220, 38, 38, 0.2);
}

/* Disabled state */
.form-input:disabled,
.form-input--disabled {
  background-color: var(--color-bg-disabled);
  color: var(--color-text-disabled);
  cursor: not-allowed;
  border-color: var(--color-border);
}

/* Loading state (vd: dang kiem tra username trung) */
.form-input--loading {
  background-image: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='20' height='20' viewBox='0 0 24 24' fill='none' stroke='%239CA3AF' stroke-width='2'%3E%3Ccircle cx='12' cy='12' r='10'/%3E%3Cpath d='M12 2a10 10 0 0 1 10 10'%3E%3CanimateTransform attributeName='transform' type='rotate' from='0 12 12' to='360 12 12' dur='1s' repeatCount='indefinite'/%3E%3C/path%3E%3C/svg%3E");
  background-repeat: no-repeat;
  background-position: right var(--space-3) center;
  padding-right: var(--space-10);
}

/* Error message */
.form-error {
  display: block;
  margin-top: var(--space-1);
  font-size: var(--font-size-xs);
  color: var(--color-danger);
  font-family: var(--font-family-base);
}

/* Helper text (khong phai loi) */
.form-hint {
  display: block;
  margin-top: var(--space-1);
  font-size: var(--font-size-xs);
  color: var(--color-text-tertiary);
}
```

### 4.2 Input Password + Strength Indicator

```html
<!-- HTML Template -->
<div class="form-group">
  <label class="form-label" for="password">
    Mat khau <span class="form-required" aria-hidden="true">*</span>
  </label>
  <div class="input-password-wrapper">
    <input
      class="form-input"
      type="password"
      id="password"
      name="password"
      placeholder="Toi thieu 8 ky tu"
      autocomplete="new-password"
    />
    <button
      type="button"
      class="input-password-toggle"
      aria-label="Hien/an mat khau"
      onclick="togglePassword(this)"
    >
      <!-- Icon eye / eye-off thay doi qua JS -->
      <svg class="icon-eye" ...></svg>
    </button>
  </div>
  <!-- Strength indicator (hien sau khi user bat dau go) -->
  <div class="password-strength" aria-live="polite">
    <div class="password-strength-bar">
      <div class="password-strength-fill" data-level="0"></div>
    </div>
    <span class="password-strength-label">Chua nhap mat khau</span>
  </div>
  <span class="form-error" role="alert" style="display:none"></span>
</div>
```

```css
.input-password-wrapper {
  position: relative;
  display: flex;
  align-items: center;
}

.input-password-wrapper .form-input {
  padding-right: var(--space-12); /* cho button toggle */
}

.input-password-toggle {
  position: absolute;
  right: var(--space-3);
  background: none;
  border: none;
  cursor: pointer;
  padding: var(--space-1);
  color: var(--color-text-tertiary);
  display: flex;
  align-items: center;
  transition: color var(--transition-fast);
}

.input-password-toggle:hover {
  color: var(--color-text-secondary);
}

/* Password strength */
.password-strength {
  margin-top: var(--space-2);
  display: flex;
  align-items: center;
  gap: var(--space-3);
}

.password-strength-bar {
  flex: 1;
  height: 4px;
  background-color: var(--color-border);
  border-radius: var(--radius-full);
  overflow: hidden;
}

.password-strength-fill {
  height: 100%;
  border-radius: var(--radius-full);
  transition: width var(--transition-base), background-color var(--transition-base);
  width: 0%;
}

/* data-level: 0=empty, 1=weak, 2=fair, 3=good, 4=strong */
.password-strength-fill[data-level="1"] { width: 25%; background-color: var(--color-danger); }
.password-strength-fill[data-level="2"] { width: 50%; background-color: var(--color-warning); }
.password-strength-fill[data-level="3"] { width: 75%; background-color: var(--color-info); }
.password-strength-fill[data-level="4"] { width: 100%; background-color: var(--color-success); }

.password-strength-label {
  font-size: var(--font-size-xs);
  color: var(--color-text-tertiary);
  white-space: nowrap;
  min-width: 80px;
}
```

### 4.3 Textarea

```html
<div class="form-group">
  <label class="form-label" for="notes">Ghi chu cho tai xe</label>
  <textarea
    class="form-textarea"
    id="notes"
    name="notes"
    rows="4"
    placeholder="Vd: Nha co cau thang hep, tren tang 3, can them nguoi ho tro..."
    maxlength="500"
  ></textarea>
  <span class="form-counter"><span id="notes-count">0</span>/500</span>
  <span class="form-hint">Ghi chu giup tai xe chuan bi tot hon cho chuyen di cua ban.</span>
</div>
```

```css
.form-textarea {
  display: block;
  width: 100%;
  min-height: 80px;
  padding: var(--space-3) var(--space-4);
  font-size: var(--font-size-base);
  font-family: var(--font-family-base);
  color: var(--color-text-primary);
  background-color: var(--color-bg-card);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  outline: none;
  resize: vertical;  /* chi resize theo chieu doc */
  transition: border-color var(--transition-fast), box-shadow var(--transition-fast);
  box-sizing: border-box;
  line-height: var(--line-height-relaxed);
}

.form-textarea::placeholder { color: var(--color-text-tertiary); }
.form-textarea:hover:not(:disabled) { border-color: var(--color-primary-300); }
.form-textarea:focus:not(:disabled) {
  border-color: var(--color-border-focus);
  box-shadow: var(--shadow-focus);
}
.form-textarea:disabled {
  background-color: var(--color-bg-disabled);
  cursor: not-allowed;
}

.form-counter {
  display: block;
  text-align: right;
  font-size: var(--font-size-xs);
  color: var(--color-text-tertiary);
  margin-top: var(--space-1);
}
```

### 4.4 Select

```html
<div class="form-group">
  <label class="form-label" for="vehicle-type">Loai xe <span class="form-required">*</span></label>
  <div class="form-select-wrapper">
    <select class="form-select" id="vehicle-type" name="vehicle_type">
      <option value="">-- Chon loai xe --</option>
      <option value="XE_3_GAC">Xe 3 gac (toi da 500kg)</option>
      <option value="XE_TAI_VUA">Xe tai vua (toi da 1 tan)</option>
      <option value="XE_TAI_LON">Xe tai lon (toi da 2 tan)</option>
      <option value="XE_TO">Xe to (toi da 5 tan)</option>
    </select>
    <!-- Arrow icon (CSS pseudo-element) -->
  </div>
</div>
```

```css
.form-select-wrapper {
  position: relative;
  display: block;
}

.form-select-wrapper::after {
  content: "";
  position: absolute;
  right: var(--space-4);
  top: 50%;
  transform: translateY(-50%);
  width: 0;
  height: 0;
  border-left: 5px solid transparent;
  border-right: 5px solid transparent;
  border-top: 6px solid var(--color-text-secondary);
  pointer-events: none;
}

.form-select {
  display: block;
  width: 100%;
  padding: var(--space-3) var(--space-10) var(--space-3) var(--space-4);
  font-size: var(--font-size-base);
  font-family: var(--font-family-base);
  color: var(--color-text-primary);
  background-color: var(--color-bg-card);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  outline: none;
  appearance: none;  /* an arrow mac dinh cua trinh duyet */
  cursor: pointer;
  transition: border-color var(--transition-fast), box-shadow var(--transition-fast);
  box-sizing: border-box;
}

.form-select:hover:not(:disabled) { border-color: var(--color-primary-300); }
.form-select:focus:not(:disabled) {
  border-color: var(--color-border-focus);
  box-shadow: var(--shadow-focus);
}
.form-select:disabled {
  background-color: var(--color-bg-disabled);
  cursor: not-allowed;
  color: var(--color-text-disabled);
}
```

### 4.5 Checkbox + Radio

```html
<!-- Checkbox -->
<label class="form-check">
  <input type="checkbox" class="form-check-input" id="terms" name="terms_accepted" value="true">
  <span class="form-check-box" aria-hidden="true"></span>
  <span class="form-check-label">
    Toi dong y voi <a href="/terms" target="_blank">Dieu khoan su dung</a> cua Move_home
  </span>
</label>

<!-- Radio -->
<label class="form-check">
  <input type="radio" class="form-check-input" name="district" value="Cau Giay">
  <span class="form-check-box form-check-box--radio" aria-hidden="true"></span>
  <span class="form-check-label">Cau Giay</span>
</label>
```

```css
/* An input goc, dung custom-styled box */
.form-check-input {
  position: absolute;
  opacity: 0;
  width: 0;
  height: 0;
  pointer-events: none;
}

.form-check {
  display: inline-flex;
  align-items: flex-start;
  gap: var(--space-3);
  cursor: pointer;
  user-select: none;
  font-size: var(--font-size-base);
  color: var(--color-text-primary);
  line-height: var(--line-height-base);
}

.form-check-box {
  flex-shrink: 0;
  width: 20px;
  height: 20px;
  border: 2px solid var(--color-border);
  border-radius: var(--radius-sm);
  background-color: var(--color-bg-card);
  transition:
    border-color var(--transition-fast),
    background-color var(--transition-fast);
  display: flex;
  align-items: center;
  justify-content: center;
  margin-top: 2px;  /* can thinh voi line-height */
}

/* Radio variant */
.form-check-box--radio {
  border-radius: var(--radius-full);
}

/* Checked state */
.form-check-input:checked + .form-check-box {
  background-color: var(--color-primary-600);
  border-color: var(--color-primary-600);
}

.form-check-input:checked + .form-check-box::after {
  content: "";
  display: block;
  width: 10px;
  height: 6px;
  border-left: 2px solid #fff;
  border-bottom: 2px solid #fff;
  transform: rotate(-45deg) translate(1px, -1px);
}

.form-check-input:checked + .form-check-box--radio::after {
  width: 8px;
  height: 8px;
  border-radius: var(--radius-full);
  background-color: #fff;
  border: none;
  transform: none;
}

/* Focus visible (keyboard navigation) */
.form-check-input:focus-visible + .form-check-box {
  box-shadow: var(--shadow-focus);
  border-color: var(--color-border-focus);
}

.form-check-label {
  line-height: var(--line-height-base);
}
```

### 4.6 Button

```html
<!-- Cac variant chinh -->
<button type="submit" class="btn btn-primary btn-md">Dat don ngay</button>
<button type="button" class="btn btn-secondary btn-md">Huy</button>
<button type="button" class="btn btn-danger btn-md">Xoa tai khoan</button>

<!-- Kich co -->
<button class="btn btn-primary btn-sm">Nho</button>
<button class="btn btn-primary btn-md">Vua (mac dinh)</button>
<button class="btn btn-primary btn-lg">Lon</button>

<!-- Trang thai loading -->
<button class="btn btn-primary btn-md btn-loading" disabled aria-busy="true">
  <span class="btn-spinner" aria-hidden="true"></span>
  Dang xu ly...
</button>

<!-- Icon button -->
<button class="btn btn-icon" aria-label="Tai moi">
  <svg ...></svg>
</button>

<!-- Button group -->
<div class="btn-group">
  <button class="btn btn-secondary btn-md">Xem</button>
  <button class="btn btn-primary btn-md">Duyet</button>
</div>
```

```css
/* ----------------------------------------------------------
   BUTTON BASE
   ---------------------------------------------------------- */
.btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: var(--space-2);
  font-family: var(--font-family-base);
  font-weight: var(--font-weight-semibold);
  border: 2px solid transparent;
  border-radius: var(--radius-md);
  cursor: pointer;
  text-decoration: none;
  white-space: nowrap;
  transition:
    background-color var(--transition-fast),
    border-color var(--transition-fast),
    color var(--transition-fast),
    box-shadow var(--transition-fast);
  position: relative;
  overflow: hidden;
}

.btn:focus-visible {
  box-shadow: var(--shadow-focus);
  outline: none;
}

/* Sizes */
.btn-sm  { padding: var(--space-1) var(--space-3);  font-size: var(--font-size-sm);  min-height: 32px; }
.btn-md  { padding: var(--space-3) var(--space-6);  font-size: var(--font-size-base); min-height: 44px; }
.btn-lg  { padding: var(--space-4) var(--space-8);  font-size: var(--font-size-md);  min-height: 52px; }

/* ----------------------------------------------------------
   PRIMARY — Filled blue
   ---------------------------------------------------------- */
.btn-primary {
  background-color: var(--color-primary-600);
  color: var(--color-text-inverse);
  border-color: var(--color-primary-600);
}
.btn-primary:hover:not(:disabled) {
  background-color: var(--color-primary-500);
  border-color: var(--color-primary-500);
}
.btn-primary:active:not(:disabled) {
  background-color: var(--color-primary-700);
  border-color: var(--color-primary-700);
}

/* ----------------------------------------------------------
   SECONDARY — Border only
   ---------------------------------------------------------- */
.btn-secondary {
  background-color: transparent;
  color: var(--color-primary-600);
  border-color: var(--color-primary-600);
}
.btn-secondary:hover:not(:disabled) {
  background-color: var(--color-primary-50);
}
.btn-secondary:active:not(:disabled) {
  background-color: var(--color-primary-100);
}

/* ----------------------------------------------------------
   DANGER — Destructive action
   ---------------------------------------------------------- */
.btn-danger {
  background-color: var(--color-danger);
  color: var(--color-text-inverse);
  border-color: var(--color-danger);
}
.btn-danger:hover:not(:disabled) {
  background-color: #B91C1C;
  border-color: #B91C1C;
}

/* ----------------------------------------------------------
   DISABLED — Tat ca variant
   KHONG chi opacity, phai doi mau + cursor
   ---------------------------------------------------------- */
.btn:disabled,
.btn[disabled] {
  background-color: var(--color-bg-disabled);
  color: var(--color-text-disabled);
  border-color: var(--color-bg-disabled);
  cursor: not-allowed;
  box-shadow: none;
}

/* ----------------------------------------------------------
   LOADING
   ---------------------------------------------------------- */
.btn-loading {
  cursor: not-allowed;
  pointer-events: none;
}

.btn-spinner {
  display: inline-block;
  width: 16px;
  height: 16px;
  border: 2px solid rgba(255, 255, 255, 0.4);
  border-top-color: #fff;
  border-radius: var(--radius-full);
  animation: btn-spin 0.7s linear infinite;
  flex-shrink: 0;
}

@keyframes btn-spin {
  to { transform: rotate(360deg); }
}

/* Secondary loading — spinner mau primary */
.btn-secondary.btn-loading .btn-spinner {
  border-color: rgba(37, 99, 235, 0.3);
  border-top-color: var(--color-primary-600);
}

/* ----------------------------------------------------------
   ICON BUTTON — Vuong, chi chua icon
   ---------------------------------------------------------- */
.btn-icon {
  padding: var(--space-2);
  width: 40px;
  height: 40px;
  border-radius: var(--radius-md);
  background-color: transparent;
  border: 1px solid var(--color-border);
  color: var(--color-text-secondary);
}
.btn-icon:hover:not(:disabled) {
  background-color: var(--color-bg-hover);
  color: var(--color-text-primary);
}

/* ----------------------------------------------------------
   BUTTON GROUP
   ---------------------------------------------------------- */
.btn-group {
  display: inline-flex;
  gap: var(--space-3);
  align-items: center;
  flex-wrap: wrap;
}
```

### 4.7 Form Group + Form Section

```css
/* ----------------------------------------------------------
   FORM GROUP — Boc label + input + error
   ---------------------------------------------------------- */
.form-group {
  display: flex;
  flex-direction: column;
  margin-bottom: var(--space-5);  /* 20px giua cac nhom */
}

.form-group:last-child {
  margin-bottom: 0;
}

/* ----------------------------------------------------------
   FORM SECTION — Nhom nhieu form-group, co heading
   ---------------------------------------------------------- */
.form-section {
  padding: var(--space-6);
  background-color: var(--color-bg-card);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  margin-bottom: var(--space-6);
}

.form-section-title {
  font-size: var(--font-size-lg);
  font-weight: var(--font-weight-semibold);
  color: var(--color-text-primary);
  margin: 0 0 var(--space-5) 0;
  padding-bottom: var(--space-4);
  border-bottom: 1px solid var(--color-border);
}

.form-actions {
  display: flex;
  justify-content: flex-end;
  gap: var(--space-3);
  margin-top: var(--space-6);
  padding-top: var(--space-5);
  border-top: 1px solid var(--color-border);
}
```

---

### Login Form — Code Example Hoan Chinh

> Code example nay la "source of truth" cho trang Login. Codex dung lam tham khao.

```html
<!DOCTYPE html>
<html lang="vi">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>Dang nhap — Move_home</title>
  <link rel="preconnect" href="https://fonts.googleapis.com">
  <link href="https://fonts.googleapis.com/css2?family=Inter:wght@400;500;600;700&display=swap" rel="stylesheet">
  <link rel="stylesheet" href="/css/tokens.css">
  <link rel="stylesheet" href="/css/layout.css">
  <link rel="stylesheet" href="/css/forms.css">
  <style>
    /* Page-specific override */
    body { margin: 0; font-family: var(--font-family-base); background: var(--color-bg-page); }

    .login-page {
      min-height: 100vh;
      display: flex;
      align-items: center;
      justify-content: center;
      padding: var(--space-6) var(--space-4);
    }

    .login-card {
      width: 100%;
      max-width: 440px;
      background: var(--color-bg-card);
      border: 1px solid var(--color-border);
      border-radius: var(--radius-lg);
      box-shadow: var(--shadow-md);
      padding: var(--space-10) var(--space-8);
    }

    .login-logo {
      display: flex;
      align-items: center;
      gap: var(--space-2);
      margin-bottom: var(--space-8);
    }

    .login-logo-text {
      font-size: var(--font-size-xl);
      font-weight: var(--font-weight-bold);
      color: var(--color-primary-600);
    }

    .login-heading {
      font-size: var(--font-size-2xl);
      font-weight: var(--font-weight-bold);
      color: var(--color-text-primary);
      margin: 0 0 var(--space-2) 0;
    }

    .login-subtext {
      font-size: var(--font-size-sm);
      color: var(--color-text-secondary);
      margin: 0 0 var(--space-8) 0;
    }

    /* Alert toan trang (error server hoac lockout) */
    .alert {
      padding: var(--space-3) var(--space-4);
      border-radius: var(--radius-md);
      font-size: var(--font-size-sm);
      margin-bottom: var(--space-5);
      display: none;
    }
    .alert--danger {
      background: var(--color-danger-bg);
      color: var(--color-danger);
      border: 1px solid var(--color-danger-border);
    }
    .alert--warning {
      background: var(--color-warning-bg);
      color: var(--color-warning);
      border: 1px solid var(--color-warning-border);
    }

    .login-footer {
      margin-top: var(--space-6);
      text-align: center;
      font-size: var(--font-size-sm);
      color: var(--color-text-secondary);
    }

    .login-footer a {
      color: var(--color-primary-600);
      text-decoration: none;
      font-weight: var(--font-weight-medium);
    }

    .login-footer a:hover { text-decoration: underline; }

    .login-divider {
      display: flex;
      align-items: center;
      gap: var(--space-4);
      margin: var(--space-5) 0;
      color: var(--color-text-tertiary);
      font-size: var(--font-size-xs);
    }
    .login-divider::before,
    .login-divider::after {
      content: "";
      flex: 1;
      height: 1px;
      background: var(--color-border);
    }
  </style>
</head>
<body>

<main class="login-page">
  <div class="login-card">

    <!-- Logo -->
    <div class="login-logo">
      <span style="font-size:28px">🏠</span>
      <span class="login-logo-text">Move_home</span>
    </div>

    <h1 class="login-heading">Dang nhap</h1>
    <p class="login-subtext">Nhap ten dang nhap va mat khau cua ban</p>

    <!-- Alert khu vuc: loi server, lockout, resend-verify -->
    <div class="alert alert--danger" id="alert-error" role="alert"></div>
    <div class="alert alert--warning" id="alert-warning" role="alert"></div>

    <form id="login-form" novalidate>
      <!-- Identifier -->
      <div class="form-group">
        <label class="form-label" for="identifier">
          Ten dang nhap hoac email <span class="form-required">*</span>
        </label>
        <input
          class="form-input"
          type="text"
          id="identifier"
          name="identifier"
          placeholder="Ten dang nhap (Customer) hoac email (Driver/Staff)"
          autocomplete="username"
          required
          autofocus
        />
        <span class="form-error" id="identifier-error" role="alert" style="display:none"></span>
        <span class="form-hint">
          Customer: dung ten dang nhap. Driver / Manager / Admin: dung email.
        </span>
      </div>

      <!-- Password -->
      <div class="form-group">
        <label class="form-label" for="password">
          Mat khau <span class="form-required">*</span>
        </label>
        <div class="input-password-wrapper">
          <input
            class="form-input"
            type="password"
            id="password"
            name="password"
            placeholder="Mat khau cua ban"
            autocomplete="current-password"
            required
          />
          <button type="button" class="input-password-toggle" aria-label="Hien hoac an mat khau">
            👁
          </button>
        </div>
        <span class="form-error" id="password-error" role="alert" style="display:none"></span>
      </div>

      <!-- Submit -->
      <div class="form-group" style="margin-bottom: 0;">
        <button type="submit" class="btn btn-primary btn-lg" id="submit-btn" style="width:100%">
          Dang nhap
        </button>
      </div>
    </form>

    <div class="login-divider">hoac</div>

    <!-- Footer links -->
    <div class="login-footer">
      Chua co tai khoan?
      <a href="/register">Dang ky ngay</a>
    </div>
    <div class="login-footer" style="margin-top: var(--space-2);">
      Ban la tai xe?
      <a href="/register/driver">Dang ky lam tai xe</a>
    </div>

  </div>
</main>

<script>
  // Toggle password show/hide
  function togglePassword(btn) {
    const input = btn.previousElementSibling;
    const isHidden = input.type === 'password';
    input.type = isHidden ? 'text' : 'password';
    btn.setAttribute('aria-label', isHidden ? 'An mat khau' : 'Hien mat khau');
    btn.textContent = isHidden ? '🙈' : '👁';
  }

  // Form submit
  document.getElementById('login-form').addEventListener('submit', async (e) => {
    e.preventDefault();
    const btn = document.getElementById('submit-btn');

    // Set loading
    btn.classList.add('btn-loading');
    btn.disabled = true;
    btn.innerHTML = '<span class="btn-spinner"></span> Dang dang nhap...';

    const body = {
      identifier: document.getElementById('identifier').value.trim(),
      password:   document.getElementById('password').value,
    };

    try {
      const res = await fetch('/api/auth/login', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        credentials: 'include',  // can cho httpOnly cookie
        body: JSON.stringify(body),
      });
      const data = await res.json();

      if (res.ok) {
        localStorage.setItem('access_token', data.access_token);
        if (data.user_info?.must_change_password) {
          window.location.href = '/change-password-first-time';
        } else {
          window.location.href = '/dashboard';
        }
      } else {
        showError(res.status, data);
      }
    } catch {
      showAlert('error', 'Loi ket noi. Vui long kiem tra mang va thu lai.');
    } finally {
      btn.classList.remove('btn-loading');
      btn.disabled = false;
      btn.textContent = 'Dang nhap';
    }
  });

  function showError(status, data) {
    const code = data?.error_code;
    if (code === 'ACCOUNT_LOCKED' || code === 'ACCOUNT_LOCKED_NOW') {
      showAlert('warning', `Tai khoan bi khoa tam ${data.minutes_remaining} phut do nhap sai mat khau nhieu lan.`);
    } else if (code === 'EMAIL_NOT_VERIFIED') {
      showAlert('warning', 'Email chua duoc xac thuc. <a href="/resend-verification">Gui lai email xac thuc</a>.');
    } else if (status === 429) {
      showAlert('error', `Qua nhieu lan thu. Vui long cho ${data.retry_after_seconds} giay.`);
    } else {
      showAlert('error', 'Ten dang nhap hoac mat khau khong dung.');
    }
  }

  function showAlert(type, msg) {
    const el = document.getElementById(type === 'error' ? 'alert-error' : 'alert-warning');
    el.innerHTML = msg;
    el.style.display = 'block';
  }
</script>

</body>
</html>
```

---

---

## 5. Data Display

> File: `css/data-display.css`

### 5.1 Card

```html
<!-- Base card -->
<div class="card">
  <div class="card-header">
    <h3 class="card-title">Thong tin don hang #12345</h3>
    <span class="card-meta">29/05/2026 — 10:32</span>
  </div>
  <div class="card-body">
    <!-- noi dung chinh -->
  </div>
  <div class="card-footer">
    <div class="btn-group">
      <button class="btn btn-secondary btn-sm">Chi tiet</button>
      <button class="btn btn-primary btn-sm">Phan cong</button>
    </div>
  </div>
</div>

<!-- Clickable card (toan bo la link) -->
<div class="card card-hover card-clickable" role="button" tabindex="0">
  ...
</div>
```

```css
/* ----------------------------------------------------------
   CARD
   ---------------------------------------------------------- */
.card {
  background-color: var(--color-bg-card);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  box-shadow: var(--shadow-sm);
  overflow: hidden;
}

.card-bordered {
  border: 2px solid var(--color-border);
  box-shadow: none;
}

.card-hover {
  transition: box-shadow var(--transition-fast), transform var(--transition-fast);
}
.card-hover:hover {
  box-shadow: var(--shadow-md);
  transform: translateY(-2px);
}

.card-clickable {
  cursor: pointer;
}
.card-clickable:focus-visible {
  outline: none;
  box-shadow: var(--shadow-focus);
}

.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: var(--space-4) var(--space-6);
  border-bottom: 1px solid var(--color-border);
  background-color: var(--color-bg-page);
}

.card-body {
  padding: var(--space-6);
}

.card-footer {
  padding: var(--space-4) var(--space-6);
  border-top: 1px solid var(--color-border);
  background-color: var(--color-bg-page);
  display: flex;
  align-items: center;
  justify-content: flex-end;
}

.card-title {
  font-size: var(--font-size-lg);
  font-weight: var(--font-weight-semibold);
  color: var(--color-text-primary);
  margin: 0;
  line-height: var(--line-height-tight);
}

.card-meta {
  font-size: var(--font-size-xs);
  color: var(--color-text-tertiary);
  white-space: nowrap;
}
```

---

### 5.2 Table

```html
<div class="table-wrapper">
  <table class="table table-hover">
    <thead>
      <tr>
        <th>Ma don</th>
        <th>Khach hang</th>
        <th>Dia chi di</th>
        <th>Trang thai</th>
        <th>Bao gia</th>
        <th class="table-col-action">Hanh dong</th>
      </tr>
    </thead>
    <tbody>
      <tr>
        <td>#1001</td>
        <td>Nguyen Van A</td>
        <td>12 Le Loi, Hoan Kiem</td>
        <td><span class="status-pill status-pill--warning">Cho xac nhan</span></td>
        <td>450.000đ</td>
        <td class="table-col-action">
          <div class="btn-group">
            <button class="btn btn-secondary btn-sm">Xem</button>
            <button class="btn btn-primary btn-sm">Phan cong</button>
          </div>
        </td>
      </tr>
      <!-- Empty state row khi khong co data -->
      <tr class="table-empty-row">
        <td colspan="6">
          <div class="table-empty">
            <span class="table-empty-icon">📋</span>
            <p class="table-empty-text">Khong co don hang nao.</p>
          </div>
        </td>
      </tr>
    </tbody>
  </table>
</div>

<!-- Pagination -->
<div class="pagination">
  <button class="pagination-btn" id="prev-btn" disabled>← Trang truoc</button>
  <div class="pagination-pages">
    <button class="pagination-page pagination-page--active">1</button>
    <button class="pagination-page">2</button>
    <button class="pagination-page">3</button>
    <span class="pagination-ellipsis">...</span>
    <button class="pagination-page">10</button>
  </div>
  <button class="pagination-btn" id="next-btn">Trang sau →</button>
</div>
```

```css
/* ----------------------------------------------------------
   TABLE
   ---------------------------------------------------------- */
.table-wrapper {
  width: 100%;
  overflow-x: auto;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  background: var(--color-bg-card);
}

.table {
  width: 100%;
  border-collapse: collapse;
  font-size: var(--font-size-sm);
  font-family: var(--font-family-base);
}

.table th {
  padding: var(--space-3) var(--space-4);
  text-align: left;
  font-size: var(--font-size-xs);
  font-weight: var(--font-weight-semibold);
  color: var(--color-text-secondary);
  text-transform: uppercase;
  letter-spacing: 0.04em;
  background-color: var(--color-bg-page);
  border-bottom: 1px solid var(--color-border);
  white-space: nowrap;
}

/* Sticky header */
.table-sticky thead th {
  position: sticky;
  top: 0;
  z-index: var(--z-base);
  box-shadow: 0 1px 0 var(--color-border);
}

.table td {
  padding: var(--space-3) var(--space-4);
  color: var(--color-text-primary);
  border-bottom: 1px solid var(--color-border);
  vertical-align: middle;
}

.table tbody tr:last-child td {
  border-bottom: none;
}

.table-striped tbody tr:nth-child(even) td {
  background-color: var(--color-bg-page);
}

.table-hover tbody tr:hover td {
  background-color: var(--color-primary-50);
}

.table-col-action {
  text-align: right;
  white-space: nowrap;
}

/* Empty state inside table */
.table-empty-row td {
  padding: var(--space-12) var(--space-6);
  text-align: center;
}

.table-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--space-3);
}

.table-empty-icon {
  font-size: 40px;
  line-height: 1;
}

.table-empty-text {
  margin: 0;
  color: var(--color-text-tertiary);
  font-size: var(--font-size-sm);
}

/* ----------------------------------------------------------
   PAGINATION
   ---------------------------------------------------------- */
.pagination {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: var(--space-2);
  margin-top: var(--space-5);
  flex-wrap: wrap;
}

.pagination-btn {
  padding: var(--space-2) var(--space-4);
  font-size: var(--font-size-sm);
  font-family: var(--font-family-base);
  font-weight: var(--font-weight-medium);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  background: var(--color-bg-card);
  color: var(--color-primary-600);
  cursor: pointer;
  transition: background-color var(--transition-fast);
}
.pagination-btn:hover:not(:disabled) { background-color: var(--color-primary-50); }
.pagination-btn:disabled {
  color: var(--color-text-disabled);
  cursor: not-allowed;
  border-color: var(--color-bg-disabled);
}

.pagination-pages { display: flex; gap: var(--space-1); }

.pagination-page {
  width: 36px;
  height: 36px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  background: var(--color-bg-card);
  color: var(--color-text-primary);
  font-size: var(--font-size-sm);
  cursor: pointer;
  transition: background-color var(--transition-fast);
}
.pagination-page:hover { background-color: var(--color-primary-50); }
.pagination-page--active {
  background-color: var(--color-primary-600);
  border-color: var(--color-primary-600);
  color: white;
  font-weight: var(--font-weight-semibold);
}

.pagination-ellipsis {
  width: 36px;
  height: 36px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--color-text-tertiary);
  font-size: var(--font-size-sm);
}
```

---

### 5.3 KPI Box (Admin Dashboard)

```html
<div class="kpi kpi-primary">
  <div class="kpi-icon" aria-hidden="true">💰</div>
  <div class="kpi-body">
    <div class="kpi-value">12.450.000đ</div>
    <div class="kpi-label">Doanh thu thang nay</div>
    <div class="kpi-trend kpi-trend--up">▲ 18% so voi thang truoc</div>
  </div>
</div>
```

```css
/* ----------------------------------------------------------
   KPI BOX
   ---------------------------------------------------------- */
.kpi {
  display: flex;
  align-items: flex-start;
  gap: var(--space-4);
  padding: var(--space-5) var(--space-6);
  background: var(--color-bg-card);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  box-shadow: var(--shadow-sm);
}

.kpi-icon {
  font-size: 32px;
  line-height: 1;
  flex-shrink: 0;
  margin-top: 2px;
}

.kpi-body {
  display: flex;
  flex-direction: column;
  gap: var(--space-1);
  min-width: 0;
}

.kpi-value {
  font-size: var(--font-size-2xl);
  font-weight: var(--font-weight-bold);
  color: var(--color-text-primary);
  line-height: var(--line-height-tight);
  white-space: nowrap;
}

.kpi-label {
  font-size: var(--font-size-sm);
  color: var(--color-text-secondary);
  font-weight: var(--font-weight-medium);
}

.kpi-trend {
  font-size: var(--font-size-xs);
  font-weight: var(--font-weight-medium);
}
.kpi-trend--up   { color: var(--color-success); }
.kpi-trend--down { color: var(--color-danger); }
.kpi-trend--flat { color: var(--color-text-tertiary); }

/* Variants — border-left accent */
.kpi-primary { border-left: 4px solid var(--color-primary-600); }
.kpi-success { border-left: 4px solid var(--color-success); }
.kpi-warning { border-left: 4px solid var(--color-warning); }
.kpi-danger  { border-left: 4px solid var(--color-danger); }
```

---

### 5.4 Badge / Status Pill

```html
<!-- Su dung class theo Status Mapping o Section 2 -->
<span class="badge badge-md badge-success">ACTIVE</span>
<span class="badge badge-md badge-warning">Cho duyet</span>
<span class="badge badge-md badge-danger">Da huy</span>
<span class="badge badge-md badge-info">Da xac nhan</span>
<span class="badge badge-md badge-primary">Dang giao</span>

<!-- Voi dot indicator -->
<span class="badge badge-md badge-success badge-with-dot">
  <span class="badge-dot" aria-hidden="true"></span>
  ACTIVE
</span>
```

```css
/* ----------------------------------------------------------
   BADGE (alias cho status-pill, them variant mau day du)
   ---------------------------------------------------------- */
.badge {
  display: inline-flex;
  align-items: center;
  gap: var(--space-1);
  font-family: var(--font-family-base);
  font-weight: var(--font-weight-semibold);
  border-radius: var(--radius-full);
  white-space: nowrap;
  line-height: 1;
}

.badge-sm { padding: 2px var(--space-2); font-size: var(--font-size-xs); }
.badge-md { padding: 4px var(--space-3); font-size: var(--font-size-sm); }

.badge-success { color: var(--color-success); background: var(--color-success-bg); }
.badge-warning { color: var(--color-warning); background: var(--color-warning-bg); }
.badge-danger  { color: var(--color-danger);  background: var(--color-danger-bg);  }
.badge-info    { color: var(--color-info);    background: var(--color-info-bg);    }
.badge-primary { color: var(--color-primary-600); background: var(--color-primary-50); }

.badge-dot { display: flex; }
.badge-dot::before {
  content: "";
  display: block;
  width: 6px;
  height: 6px;
  border-radius: var(--radius-full);
  background-color: currentColor;
}
```

---

### 5.5 Avatar

```html
<!-- Anh that -->
<div class="avatar avatar-md">
  <img src="/uploads/drivers/abc.jpg" alt="Nguyen Van A" class="avatar-img">
  <span class="avatar-status avatar-status--online" aria-label="Dang hoat dong"></span>
</div>

<!-- Fallback: Initials -->
<div class="avatar avatar-md avatar-initials" aria-label="Nguyen Van A">NV</div>

<!-- Sizes -->
<div class="avatar avatar-sm">...</div>   <!-- 32px -->
<div class="avatar avatar-md">...</div>   <!-- 40px -->
<div class="avatar avatar-lg">...</div>   <!-- 64px -->
```

```css
/* ----------------------------------------------------------
   AVATAR
   ---------------------------------------------------------- */
.avatar {
  position: relative;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: var(--radius-full);
  overflow: visible;
  flex-shrink: 0;
}

.avatar-sm { width: 32px; height: 32px; }
.avatar-md { width: 40px; height: 40px; }
.avatar-lg { width: 64px; height: 64px; }

.avatar-img {
  width: 100%;
  height: 100%;
  border-radius: var(--radius-full);
  object-fit: cover;
  display: block;
}

.avatar-initials {
  background-color: var(--color-primary-100);
  color: var(--color-primary-700);
  font-weight: var(--font-weight-semibold);
  font-family: var(--font-family-base);
  border-radius: var(--radius-full);
  overflow: hidden;
}
.avatar-sm.avatar-initials  { font-size: 12px; }
.avatar-md.avatar-initials  { font-size: 14px; }
.avatar-lg.avatar-initials  { font-size: 22px; }

/* Online/Offline status dot */
.avatar-status {
  position: absolute;
  bottom: 0;
  right: 0;
  width: 10px;
  height: 10px;
  border-radius: var(--radius-full);
  border: 2px solid var(--color-bg-card);
}
.avatar-status--online  { background-color: var(--color-success); }
.avatar-status--offline { background-color: var(--color-text-tertiary); }
.avatar-status--busy    { background-color: var(--color-warning); }
```

---

### 5.6 List

```html
<ul class="list" role="list">
  <li class="list-item list-item-clickable" tabindex="0">
    <div class="list-item-icon" aria-hidden="true">📦</div>
    <div class="list-item-content">
      <div class="list-item-title">Don hang #1001 — Cau Giay → Dong Da</div>
      <div class="list-item-meta">29/05/2026 — Khach: Nguyen Van A</div>
    </div>
    <span class="badge badge-sm badge-warning">Cho xac nhan</span>
  </li>
  <!-- ... -->

  <!-- Empty state -->
  <li class="list-empty">
    <span class="list-empty-icon">🗂️</span>
    <p class="list-empty-text">Chua co don nao trong danh sach nay.</p>
  </li>
</ul>
```

```css
/* ----------------------------------------------------------
   LIST
   ---------------------------------------------------------- */
.list {
  list-style: none;
  margin: 0;
  padding: 0;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  background: var(--color-bg-card);
  overflow: hidden;
}

.list-item {
  display: flex;
  align-items: center;
  gap: var(--space-4);
  padding: var(--space-4) var(--space-5);
  border-bottom: 1px solid var(--color-border);
  transition: background-color var(--transition-fast);
}
.list-item:last-child { border-bottom: none; }

.list-item-clickable {
  cursor: pointer;
}
.list-item-clickable:hover {
  background-color: var(--color-bg-hover);
}
.list-item-clickable:focus-visible {
  outline: none;
  box-shadow: inset 0 0 0 2px var(--color-primary-600);
}

.list-item-icon {
  font-size: 20px;
  flex-shrink: 0;
}

.list-item-content {
  flex: 1;
  min-width: 0;
}

.list-item-title {
  font-size: var(--font-size-base);
  font-weight: var(--font-weight-medium);
  color: var(--color-text-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.list-item-meta {
  font-size: var(--font-size-xs);
  color: var(--color-text-tertiary);
  margin-top: 2px;
}

.list-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: var(--space-10);
  gap: var(--space-3);
  text-align: center;
}

.list-empty-icon { font-size: 40px; line-height: 1; }
.list-empty-text { margin: 0; color: var(--color-text-tertiary); font-size: var(--font-size-sm); }
```

---

### Admin Dashboard KPI Section — Code Example

```html
<section class="page-content">
  <div class="container">
    <h1 class="page-title">Tong quan he thong</h1>

    <!-- 4 KPI boxes -->
    <div class="grid-4" style="margin-bottom: var(--space-8);">
      <div class="kpi kpi-primary">
        <div class="kpi-icon">💰</div>
        <div class="kpi-body">
          <div class="kpi-value">12.450.000đ</div>
          <div class="kpi-label">Doanh thu thang nay</div>
          <div class="kpi-trend kpi-trend--up">▲ 18% so thang truoc</div>
        </div>
      </div>
      <div class="kpi kpi-success">
        <div class="kpi-icon">✅</div>
        <div class="kpi-body">
          <div class="kpi-value">247</div>
          <div class="kpi-label">Don hoan thanh</div>
          <div class="kpi-trend kpi-trend--up">▲ 12 don</div>
        </div>
      </div>
      <div class="kpi kpi-warning">
        <div class="kpi-icon">🚗</div>
        <div class="kpi-body">
          <div class="kpi-value">38</div>
          <div class="kpi-label">Driver dang ACTIVE</div>
          <div class="kpi-trend kpi-trend--flat">= khong thay doi</div>
        </div>
      </div>
      <div class="kpi kpi-danger">
        <div class="kpi-icon">⚠️</div>
        <div class="kpi-body">
          <div class="kpi-value">3</div>
          <div class="kpi-label">Tranh chap dang mo</div>
          <div class="kpi-trend kpi-trend--down">▼ 2 so tuan truoc</div>
        </div>
      </div>
    </div>
  </div>
</section>
```

---

## 6. Navigation

> File: `css/nav.css`

### 6.1 Header (Top Nav)

```html
<header class="site-header" role="banner">
  <div class="container site-header-inner">
    <!-- Logo -->
    <a href="/" class="site-logo" aria-label="Move_home — Trang chu">
      <span class="site-logo-icon" aria-hidden="true">🏠</span>
      <span class="site-logo-text">Move_home</span>
    </a>

    <!-- Desktop nav -->
    <nav class="site-nav" aria-label="Menu chinh">
      <a href="/orders"  class="site-nav-link">Don hang</a>
      <a href="/drivers" class="site-nav-link">Tai xe</a>
      <a href="/reports" class="site-nav-link site-nav-link--active">Bao cao</a>
    </nav>

    <!-- User actions -->
    <div class="site-header-actions">
      <!-- Notification bell -->
      <button class="btn btn-icon site-header-notif" aria-label="Thong bao">
        🔔
        <span class="notif-badge" aria-label="3 thong bao chua doc">3</span>
      </button>
      <!-- User menu trigger -->
      <button class="user-menu-trigger" id="user-menu-trigger" aria-haspopup="true" aria-expanded="false">
        <div class="avatar avatar-sm avatar-initials">NV</div>
        <span class="user-menu-name">Nguyen Manager</span>
        <span class="user-menu-chevron" aria-hidden="true">▾</span>
      </button>
    </div>

    <!-- Mobile hamburger -->
    <button class="hamburger" id="hamburger" aria-label="Mo menu" aria-expanded="false">
      <span class="hamburger-line"></span>
      <span class="hamburger-line"></span>
      <span class="hamburger-line"></span>
    </button>
  </div>
</header>
```

```css
/* ----------------------------------------------------------
   SITE HEADER
   ---------------------------------------------------------- */
.site-header {
  position: sticky;
  top: 0;
  z-index: var(--z-sticky);
  height: 64px;
  background-color: var(--color-bg-card);
  border-bottom: 1px solid var(--color-border);
  box-shadow: var(--shadow-sm);
}

.site-header-inner {
  height: 100%;
  display: flex;
  align-items: center;
  gap: var(--space-6);
}

/* Logo */
.site-logo {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  text-decoration: none;
  flex-shrink: 0;
}

.site-logo-icon { font-size: 24px; }

.site-logo-text {
  font-size: var(--font-size-lg);
  font-weight: var(--font-weight-bold);
  color: var(--color-primary-600);
}

/* Desktop nav links */
.site-nav {
  display: flex;
  align-items: center;
  gap: var(--space-1);
  flex: 1;
}

.site-nav-link {
  padding: var(--space-2) var(--space-3);
  font-size: var(--font-size-sm);
  font-weight: var(--font-weight-medium);
  color: var(--color-text-secondary);
  text-decoration: none;
  border-radius: var(--radius-md);
  transition: background-color var(--transition-fast), color var(--transition-fast);
}
.site-nav-link:hover { background: var(--color-bg-hover); color: var(--color-text-primary); }
.site-nav-link--active { background: var(--color-primary-50); color: var(--color-primary-600); font-weight: var(--font-weight-semibold); }

/* Header right */
.site-header-actions {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  margin-left: auto;
}

/* Notification badge */
.site-header-notif { position: relative; }
.notif-badge {
  position: absolute;
  top: -4px;
  right: -4px;
  min-width: 18px;
  height: 18px;
  padding: 0 4px;
  background: var(--color-danger);
  color: white;
  font-size: 10px;
  font-weight: var(--font-weight-bold);
  border-radius: var(--radius-full);
  display: flex;
  align-items: center;
  justify-content: center;
  border: 2px solid var(--color-bg-card);
}

/* User menu trigger */
.user-menu-trigger {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  padding: var(--space-1) var(--space-2);
  border: none;
  background: none;
  border-radius: var(--radius-md);
  cursor: pointer;
  transition: background-color var(--transition-fast);
}
.user-menu-trigger:hover { background: var(--color-bg-hover); }

.user-menu-name {
  font-size: var(--font-size-sm);
  font-weight: var(--font-weight-medium);
  color: var(--color-text-primary);
}

.user-menu-chevron {
  font-size: 10px;
  color: var(--color-text-tertiary);
  transition: transform var(--transition-fast);
}
.user-menu-trigger[aria-expanded="true"] .user-menu-chevron {
  transform: rotate(180deg);
}

/* Hamburger — mobile only */
.hamburger {
  display: none;
  flex-direction: column;
  gap: 5px;
  padding: var(--space-2);
  border: none;
  background: none;
  cursor: pointer;
  margin-left: auto;
}
.hamburger-line {
  display: block;
  width: 22px;
  height: 2px;
  background: var(--color-text-primary);
  border-radius: var(--radius-full);
  transition: transform var(--transition-fast), opacity var(--transition-fast);
}

@media (max-width: 767px) {
  .site-nav { display: none; }
  .site-header-actions .user-menu-name { display: none; }
  .hamburger { display: flex; }
}
```

---

### 6.2 User Menu (Dropdown)

```html
<div class="dropdown" id="user-dropdown" aria-hidden="true">
  <div class="dropdown-header">
    <div class="avatar avatar-md avatar-initials">NV</div>
    <div>
      <div class="dropdown-user-name">Nguyen Manager</div>
      <div class="dropdown-user-role">Manager</div>
    </div>
  </div>
  <hr class="dropdown-divider">
  <a href="/profile" class="dropdown-item">👤 Thong tin ca nhan</a>
  <a href="/settings" class="dropdown-item">⚙️ Cai dat</a>
  <hr class="dropdown-divider">
  <button class="dropdown-item dropdown-item--danger" id="logout-btn">🚪 Dang xuat</button>
</div>
```

```css
/* ----------------------------------------------------------
   DROPDOWN
   ---------------------------------------------------------- */
.dropdown {
  position: absolute;
  top: calc(100% + 8px);
  right: 0;
  min-width: 240px;
  background: var(--color-bg-card);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-lg);
  z-index: var(--z-dropdown);
  overflow: hidden;
  transform-origin: top right;
  animation: dropdown-in var(--transition-fast) ease;
}

@keyframes dropdown-in {
  from { opacity: 0; transform: scale(0.95) translateY(-4px); }
  to   { opacity: 1; transform: scale(1)    translateY(0); }
}

.dropdown[aria-hidden="true"] { display: none; }

.dropdown-header {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  padding: var(--space-4) var(--space-4);
}

.dropdown-user-name {
  font-size: var(--font-size-sm);
  font-weight: var(--font-weight-semibold);
  color: var(--color-text-primary);
}
.dropdown-user-role {
  font-size: var(--font-size-xs);
  color: var(--color-text-tertiary);
}

.dropdown-divider {
  margin: 0;
  border: none;
  border-top: 1px solid var(--color-border);
}

.dropdown-item {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  padding: var(--space-3) var(--space-4);
  font-size: var(--font-size-sm);
  color: var(--color-text-primary);
  text-decoration: none;
  background: none;
  border: none;
  width: 100%;
  text-align: left;
  cursor: pointer;
  font-family: var(--font-family-base);
  transition: background-color var(--transition-fast);
}
.dropdown-item:hover { background: var(--color-bg-hover); }
.dropdown-item--danger { color: var(--color-danger); }
.dropdown-item--danger:hover { background: var(--color-danger-bg); }
```

---

### 6.3 Breadcrumb

```html
<nav class="breadcrumb" aria-label="Breadcrumb">
  <ol class="breadcrumb-list">
    <li class="breadcrumb-item">
      <a href="/dashboard" class="breadcrumb-link">Dashboard</a>
    </li>
    <li class="breadcrumb-item" aria-hidden="true">
      <span class="breadcrumb-sep">/</span>
    </li>
    <li class="breadcrumb-item">
      <a href="/drivers" class="breadcrumb-link">Tai xe</a>
    </li>
    <li class="breadcrumb-item" aria-hidden="true">
      <span class="breadcrumb-sep">/</span>
    </li>
    <!-- Current page — khong phai link -->
    <li class="breadcrumb-item breadcrumb-item--current" aria-current="page">
      Nguyen Van A
    </li>
  </ol>
</nav>
```

```css
.breadcrumb { margin-bottom: var(--space-4); }
.breadcrumb-list {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: var(--space-1);
  list-style: none;
  margin: 0;
  padding: 0;
}
.breadcrumb-item { display: flex; align-items: center; gap: var(--space-1); }
.breadcrumb-link {
  font-size: var(--font-size-sm);
  color: var(--color-primary-600);
  text-decoration: none;
}
.breadcrumb-link:hover { text-decoration: underline; }
.breadcrumb-sep { color: var(--color-text-tertiary); font-size: var(--font-size-sm); }
.breadcrumb-item--current {
  font-size: var(--font-size-sm);
  font-weight: var(--font-weight-semibold);
  color: var(--color-text-primary);
}
```

---

### 6.4 Tabs

```html
<div class="tabs" role="tablist" aria-label="Tab xem thong tin">
  <button class="tab tab--active" role="tab" aria-selected="true"  aria-controls="tab-overview">Tong quan</button>
  <button class="tab"             role="tab" aria-selected="false" aria-controls="tab-history">Lich su don</button>
  <button class="tab"             role="tab" aria-selected="false" aria-controls="tab-docs">Giay to</button>
</div>
<div class="tab-panels">
  <div id="tab-overview" role="tabpanel" class="tab-panel tab-panel--active">...</div>
  <div id="tab-history"  role="tabpanel" class="tab-panel" hidden>...</div>
  <div id="tab-docs"     role="tabpanel" class="tab-panel" hidden>...</div>
</div>
```

```css
.tabs {
  display: flex;
  gap: 0;
  border-bottom: 2px solid var(--color-border);
  margin-bottom: var(--space-5);
  overflow-x: auto;
}

.tab {
  padding: var(--space-3) var(--space-5);
  font-size: var(--font-size-sm);
  font-weight: var(--font-weight-medium);
  color: var(--color-text-secondary);
  background: none;
  border: none;
  border-bottom: 2px solid transparent;
  margin-bottom: -2px;  /* overlap container border */
  cursor: pointer;
  white-space: nowrap;
  transition: color var(--transition-fast), border-color var(--transition-fast);
}
.tab:hover { color: var(--color-text-primary); background: var(--color-bg-hover); }
.tab--active {
  color: var(--color-primary-600);
  border-bottom-color: var(--color-primary-600);
  font-weight: var(--font-weight-semibold);
}
.tab:focus-visible { outline: none; box-shadow: inset var(--shadow-focus); }

.tab-panel { display: none; }
.tab-panel--active { display: block; }
```

---

### 6.5 Sidebar (Admin / Manager)

```html
<aside class="sidebar" id="sidebar" aria-label="Navigation chinh">
  <nav class="sidebar-nav">
    <a href="/dashboard" class="sidebar-item sidebar-item--active">
      <span class="sidebar-icon" aria-hidden="true">📊</span>
      <span class="sidebar-label">Tong quan</span>
    </a>
    <a href="/orders" class="sidebar-item">
      <span class="sidebar-icon" aria-hidden="true">📦</span>
      <span class="sidebar-label">Don hang</span>
    </a>
    <a href="/drivers" class="sidebar-item">
      <span class="sidebar-icon" aria-hidden="true">🚗</span>
      <span class="sidebar-label">Tai xe</span>
    </a>
    <a href="/reports" class="sidebar-item">
      <span class="sidebar-icon" aria-hidden="true">📈</span>
      <span class="sidebar-label">Bao cao</span>
    </a>
    <div class="sidebar-divider" aria-hidden="true"></div>
    <a href="/settings" class="sidebar-item">
      <span class="sidebar-icon" aria-hidden="true">⚙️</span>
      <span class="sidebar-label">Cai dat</span>
    </a>
  </nav>
</aside>
```

```css
/* ----------------------------------------------------------
   SIDEBAR
   ---------------------------------------------------------- */
.sidebar {
  width: 240px;
  flex-shrink: 0;
  height: calc(100vh - 64px);  /* tru header */
  position: sticky;
  top: 64px;
  background: var(--color-bg-card);
  border-right: 1px solid var(--color-border);
  overflow-y: auto;
  padding: var(--space-4) var(--space-3);
  display: flex;
  flex-direction: column;
}

.sidebar-nav { display: flex; flex-direction: column; gap: var(--space-1); }

.sidebar-item {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  padding: var(--space-3) var(--space-4);
  font-size: var(--font-size-sm);
  font-weight: var(--font-weight-medium);
  color: var(--color-text-secondary);
  text-decoration: none;
  border-radius: var(--radius-md);
  border-left: 3px solid transparent;
  transition:
    background-color var(--transition-fast),
    color var(--transition-fast),
    border-color var(--transition-fast);
}
.sidebar-item:hover {
  background: var(--color-bg-hover);
  color: var(--color-text-primary);
}
.sidebar-item--active {
  background: var(--color-primary-50);
  color: var(--color-primary-600);
  border-left-color: var(--color-primary-600);
  font-weight: var(--font-weight-semibold);
}

.sidebar-icon { font-size: 18px; flex-shrink: 0; }

.sidebar-divider {
  height: 1px;
  background: var(--color-border);
  margin: var(--space-3) 0;
}

/* Collapse on tablet */
@media (max-width: 1023px) {
  .sidebar { display: none; }
  .sidebar.sidebar--open { display: flex; position: fixed; z-index: var(--z-dropdown); }
}
```

#### Manager Dashboard Layout — Code Example

```html
<div class="site-layout">
  <header class="site-header"><!-- see 6.1 --></header>
  <div class="site-body">
    <aside class="sidebar"><!-- see 6.5 --></aside>
    <main class="site-main" id="main-content">
      <div class="container">
        <nav class="breadcrumb"><!-- 6.3 --></nav>
        <h1 class="page-title">Quan ly don hang</h1>
        <div class="page-content">
          <!-- table, cards... -->
        </div>
      </div>
    </main>
  </div>
</div>
```

```css
.site-layout { display: flex; flex-direction: column; min-height: 100vh; }
.site-body   { display: flex; flex: 1; overflow: hidden; }
.site-main   { flex: 1; overflow-y: auto; }
```

---

## 7. Feedback

> File: `css/feedback.css`

### 7.1 Alert (Inline Banner)

```html
<!-- 4 variants -->
<div class="alert alert-info alert-dismissable" role="alert">
  <span class="alert-icon" aria-hidden="true">ℹ️</span>
  <div class="alert-body">
    <strong class="alert-title">Thong tin:</strong>
    <span class="alert-text">Don hang cua ban dang duoc xu ly.</span>
  </div>
  <button class="alert-close" aria-label="Dong thong bao">✕</button>
</div>

<div class="alert alert-success" role="alert">...</div>
<div class="alert alert-warning" role="alert">...</div>
<div class="alert alert-danger"  role="alert">...</div>
```

```css
/* ----------------------------------------------------------
   ALERT
   ---------------------------------------------------------- */
.alert {
  display: flex;
  align-items: flex-start;
  gap: var(--space-3);
  padding: var(--space-3) var(--space-4);
  border-radius: var(--radius-md);
  border: 1px solid;
  font-size: var(--font-size-sm);
  font-family: var(--font-family-base);
  line-height: var(--line-height-base);
}

.alert-info    { background: var(--color-info-bg);    color: var(--color-info);    border-color: var(--color-info-border);    }
.alert-success { background: var(--color-success-bg); color: var(--color-success); border-color: var(--color-success-border); }
.alert-warning { background: var(--color-warning-bg); color: var(--color-warning); border-color: var(--color-warning-border); }
.alert-danger  { background: var(--color-danger-bg);  color: var(--color-danger);  border-color: var(--color-danger-border);  }

.alert-icon { font-size: 18px; flex-shrink: 0; margin-top: 1px; }

.alert-body { flex: 1; min-width: 0; }
.alert-title { font-weight: var(--font-weight-semibold); }

.alert-close {
  flex-shrink: 0;
  background: none;
  border: none;
  cursor: pointer;
  color: currentColor;
  opacity: 0.6;
  font-size: 14px;
  padding: 2px;
  transition: opacity var(--transition-fast);
}
.alert-close:hover { opacity: 1; }
```

---

### 7.2 Toast (Floating Notification)

```html
<!-- Container — dat o cuoi body, truoc </body> -->
<div class="toast-container" id="toast-container" aria-live="polite" aria-atomic="false"></div>

<!-- Template cua mot toast (tao bang JS) -->
<div class="toast toast-success" role="status">
  <span class="toast-icon" aria-hidden="true">✅</span>
  <div class="toast-body">
    <div class="toast-title">Thanh cong!</div>
    <div class="toast-text">Don hang #1001 da duoc phan cong.</div>
  </div>
  <button class="toast-close" aria-label="Dong">✕</button>
</div>
```

```css
/* ----------------------------------------------------------
   TOAST
   ---------------------------------------------------------- */
.toast-container {
  position: fixed;
  top: var(--space-5);
  right: var(--space-5);
  z-index: var(--z-toast);
  display: flex;
  flex-direction: column;
  gap: var(--space-3);
  max-width: 380px;
  width: calc(100vw - var(--space-10));
}

.toast {
  display: flex;
  align-items: flex-start;
  gap: var(--space-3);
  padding: var(--space-4);
  background: var(--color-bg-card);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  box-shadow: var(--shadow-lg);
  animation: toast-slide-in var(--transition-base) ease;
}

@keyframes toast-slide-in {
  from { opacity: 0; transform: translateX(100%); }
  to   { opacity: 1; transform: translateX(0); }
}

.toast.toast-hiding {
  animation: toast-slide-out var(--transition-base) ease forwards;
}
@keyframes toast-slide-out {
  from { opacity: 1; transform: translateX(0); max-height: 200px; }
  to   { opacity: 0; transform: translateX(100%); max-height: 0; padding: 0; margin: 0; }
}

/* Variant accents */
.toast-success { border-left: 4px solid var(--color-success); }
.toast-warning { border-left: 4px solid var(--color-warning); }
.toast-danger  { border-left: 4px solid var(--color-danger); }
.toast-info    { border-left: 4px solid var(--color-info); }

.toast-icon { font-size: 20px; flex-shrink: 0; }

.toast-body { flex: 1; min-width: 0; }
.toast-title { font-size: var(--font-size-sm); font-weight: var(--font-weight-semibold); color: var(--color-text-primary); }
.toast-text  { font-size: var(--font-size-xs); color: var(--color-text-secondary); margin-top: 2px; }

.toast-close {
  background: none;
  border: none;
  color: var(--color-text-tertiary);
  cursor: pointer;
  font-size: 12px;
  flex-shrink: 0;
  padding: 2px;
  transition: color var(--transition-fast);
}
.toast-close:hover { color: var(--color-text-primary); }

/* Mobile: center top */
@media (max-width: 767px) {
  .toast-container { left: var(--space-4); right: var(--space-4); }
}
```

```javascript
// Toast JS helper
function showToast(type, title, text, durationMs = 5000) {
  const container = document.getElementById('toast-container');
  const toast = document.createElement('div');
  toast.className = `toast toast-${type}`;
  toast.setAttribute('role', 'status');
  toast.innerHTML = `
    <span class="toast-icon" aria-hidden="true">
      ${{ success: '✅', warning: '⚠️', danger: '❌', info: 'ℹ️' }[type]}
    </span>
    <div class="toast-body">
      <div class="toast-title">${title}</div>
      ${text ? `<div class="toast-text">${text}</div>` : ''}
    </div>
    <button class="toast-close" aria-label="Dong">✕</button>
  `;
  const close = () => {
    toast.classList.add('toast-hiding');
    setTimeout(() => toast.remove(), 300);
  };
  toast.querySelector('.toast-close').addEventListener('click', close);
  container.appendChild(toast);
  setTimeout(close, durationMs);
}
// Cach dung:
// showToast('success', 'Thanh cong!', 'Don hang da duoc tao.');
// showToast('danger',  'Loi!', 'Khong the ket noi. Thu lai sau.');
```

---

### 7.3 Modal

```html
<!-- Backdrop -->
<div class="modal-backdrop" id="confirm-modal-backdrop" aria-hidden="true"></div>

<!-- Modal box -->
<div class="modal" id="confirm-modal" role="dialog" aria-modal="true"
     aria-labelledby="modal-title" aria-hidden="true">
  <div class="modal-header">
    <h2 class="modal-title" id="modal-title">Xac nhan huy don</h2>
    <button class="modal-close" aria-label="Dong">✕</button>
  </div>
  <div class="modal-body">
    <p>Ban co chac muon huy don hang #1001 khong? Hanh dong nay khong the hoan tac.</p>
  </div>
  <div class="modal-footer">
    <button class="btn btn-secondary btn-md" id="modal-cancel">Khong, giu lai</button>
    <button class="btn btn-danger btn-md"    id="modal-confirm">Co, huy don</button>
  </div>
</div>
```

```css
/* ----------------------------------------------------------
   MODAL
   ---------------------------------------------------------- */
.modal-backdrop {
  position: fixed;
  inset: 0;
  background: var(--color-bg-overlay);
  z-index: var(--z-modal-backdrop);
  backdrop-filter: blur(2px);
  animation: fade-in var(--transition-base) ease;
}
.modal-backdrop[aria-hidden="true"] { display: none; }

.modal {
  position: fixed;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  z-index: var(--z-modal);
  width: 90%;
  max-width: 560px;
  background: var(--color-bg-card);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-lg);
  display: flex;
  flex-direction: column;
  animation: modal-in var(--transition-base) ease;
  max-height: calc(100vh - var(--space-16));
}
.modal[aria-hidden="true"] { display: none; }

@keyframes modal-in {
  from { opacity: 0; transform: translate(-50%, -48%) scale(0.96); }
  to   { opacity: 1; transform: translate(-50%, -50%) scale(1); }
}

.modal-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: var(--space-5) var(--space-6);
  border-bottom: 1px solid var(--color-border);
  flex-shrink: 0;
}

.modal-title {
  font-size: var(--font-size-lg);
  font-weight: var(--font-weight-semibold);
  color: var(--color-text-primary);
  margin: 0;
}

.modal-close {
  background: none;
  border: none;
  font-size: 18px;
  color: var(--color-text-tertiary);
  cursor: pointer;
  padding: var(--space-1);
  border-radius: var(--radius-sm);
  transition: color var(--transition-fast), background-color var(--transition-fast);
}
.modal-close:hover { color: var(--color-text-primary); background: var(--color-bg-hover); }

.modal-body {
  padding: var(--space-6);
  overflow-y: auto;
  flex: 1;
  font-size: var(--font-size-base);
  color: var(--color-text-secondary);
  line-height: var(--line-height-relaxed);
}

.modal-footer {
  display: flex;
  justify-content: flex-end;
  gap: var(--space-3);
  padding: var(--space-4) var(--space-6);
  border-top: 1px solid var(--color-border);
  flex-shrink: 0;
}

/* Mobile: full screen */
@media (max-width: 767px) {
  .modal {
    top: auto;
    bottom: 0;
    left: 0;
    right: 0;
    transform: none;
    width: 100%;
    max-width: 100%;
    border-bottom-left-radius: 0;
    border-bottom-right-radius: 0;
    max-height: 85vh;
    animation: modal-slide-up var(--transition-base) ease;
  }
  @keyframes modal-slide-up {
    from { transform: translateY(100%); }
    to   { transform: translateY(0); }
  }
}

@keyframes fade-in {
  from { opacity: 0; }
  to   { opacity: 1; }
}
```

---

### 7.4 Loading

```html
<!-- Inline spinner (16px) — tren button -->
<span class="spinner spinner-sm" aria-label="Dang tai..."></span>

<!-- Section spinner (32px) -->
<div class="spinner-section">
  <span class="spinner spinner-md" role="status" aria-label="Dang tai du lieu..."></span>
  <span class="spinner-label">Dang tai...</span>
</div>

<!-- Page spinner (64px) -->
<div class="spinner-page">
  <span class="spinner spinner-lg" role="status" aria-label="Dang tai trang..."></span>
</div>

<!-- Skeleton loader — thay card/list khi dang fetch -->
<div class="skeleton-card">
  <div class="skeleton skeleton-avatar"></div>
  <div class="skeleton-lines">
    <div class="skeleton skeleton-line skeleton-line--full"></div>
    <div class="skeleton skeleton-line skeleton-line--half"></div>
  </div>
</div>
```

```css
/* ----------------------------------------------------------
   SPINNER
   ---------------------------------------------------------- */
.spinner {
  display: inline-block;
  border-radius: var(--radius-full);
  border: 2px solid var(--color-primary-200);
  border-top-color: var(--color-primary-600);
  animation: spin 0.75s linear infinite;
  flex-shrink: 0;
}
.spinner-sm { width: 16px; height: 16px; }
.spinner-md { width: 32px; height: 32px; border-width: 3px; }
.spinner-lg { width: 64px; height: 64px; border-width: 4px; }

@keyframes spin { to { transform: rotate(360deg); } }

.spinner-section {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: var(--space-3);
  padding: var(--space-12);
}

.spinner-page {
  position: fixed;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(255,255,255,0.8);
  z-index: var(--z-modal);
}

.spinner-label {
  font-size: var(--font-size-sm);
  color: var(--color-text-tertiary);
}

/* ----------------------------------------------------------
   SKELETON LOADER (shimmer)
   ---------------------------------------------------------- */
@keyframes shimmer {
  from { background-position: -200% 0; }
  to   { background-position:  200% 0; }
}

.skeleton {
  background: linear-gradient(
    90deg,
    var(--color-bg-disabled) 25%,
    var(--color-bg-hover)    50%,
    var(--color-bg-disabled) 75%
  );
  background-size: 200% 100%;
  animation: shimmer 1.5s infinite;
  border-radius: var(--radius-md);
}

.skeleton-card {
  display: flex;
  align-items: flex-start;
  gap: var(--space-4);
  padding: var(--space-4);
}

.skeleton-avatar {
  width: 40px;
  height: 40px;
  border-radius: var(--radius-full);
  flex-shrink: 0;
}

.skeleton-lines {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
}

.skeleton-line {
  height: 14px;
  border-radius: var(--radius-sm);
}
.skeleton-line--full { width: 100%; }
.skeleton-line--half { width: 55%; }
.skeleton-line--third { width: 33%; }
```

---

### 7.5 Empty State

```html
<!-- Danh sach don trong (Customer) -->
<div class="empty-state">
  <div class="empty-state-icon" aria-hidden="true">📦</div>
  <h3 class="empty-state-title">Chua co don hang nao</h3>
  <p class="empty-state-desc">Dat don dich vu chuyen nha ngay de trai nghiem dich vu cua chung toi.</p>
  <a href="/orders/new" class="btn btn-primary btn-md">Dat don ngay</a>
</div>

<!-- Driver queue trong (Manager) -->
<div class="empty-state">
  <div class="empty-state-icon" aria-hidden="true">✅</div>
  <h3 class="empty-state-title">Khong co Driver cho duyet</h3>
  <p class="empty-state-desc">Tat ca ho so tai xe da duoc xu ly.</p>
</div>
```

```css
/* ----------------------------------------------------------
   EMPTY STATE
   ---------------------------------------------------------- */
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  text-align: center;
  padding: var(--space-16) var(--space-6);
  gap: var(--space-4);
}

.empty-state-icon {
  font-size: 56px;
  line-height: 1;
}

.empty-state-title {
  font-size: var(--font-size-xl);
  font-weight: var(--font-weight-semibold);
  color: var(--color-text-primary);
  margin: 0;
}

.empty-state-desc {
  font-size: var(--font-size-base);
  color: var(--color-text-secondary);
  line-height: var(--line-height-relaxed);
  max-width: 420px;
  margin: 0;
}
```

---

## 8. Charts Theme

> Thu vien: **Chart.js v4.x** (CDN hoac npm). File: `js/charts-config.js`

### 8.1 Chart.js Default Configuration

```javascript
// charts-config.js
// Import sau khi import Chart.js

// Lay CSS variables de dung trong Chart.js
const style = getComputedStyle(document.documentElement);
const C = (v) => style.getPropertyValue(v).trim();

// Default options ap dung cho tat ca chart
Chart.defaults.font.family   = C('--font-family-base');
Chart.defaults.font.size     = 13;
Chart.defaults.color         = C('--color-text-secondary');
Chart.defaults.borderColor   = C('--color-border');
Chart.defaults.backgroundColor = C('--color-primary-100');

// Tooltip theme chung
const tooltipDefaults = {
  backgroundColor: C('--color-text-primary'),
  titleColor:      '#ffffff',
  bodyColor:       '#ffffffcc',
  borderColor:     C('--color-border'),
  borderWidth:     0,
  padding:         10,
  cornerRadius:    6,
  displayColors:   true,
  titleFont:       { weight: '600', size: 13 },
  bodyFont:        { size: 12 },
  callbacks: {
    label: (ctx) => {
      const val = ctx.parsed.y ?? ctx.parsed;
      // Format so tien VND neu label chua "doanh thu" hoac "tien"
      return typeof val === 'number'
        ? ` ${val.toLocaleString('vi-VN')}đ`
        : ` ${val}`;
    }
  }
};

// Grid lines nhe
const gridDefaults = {
  color: `${C('--color-border')}66`,  // opacity ~40%
  drawBorder: false,
};

export { tooltipDefaults, gridDefaults, C };
```

---

### 8.2 Bar Chart — Doanh Thu Thang

```javascript
// doanh-thu-chart.js
import { tooltipDefaults, gridDefaults, C } from './charts-config.js';

function createDoanhThuChart(canvasId, labels, data) {
  const ctx = document.getElementById(canvasId).getContext('2d');
  return new Chart(ctx, {
    type: 'bar',
    data: {
      labels,
      datasets: [{
        label: 'Doanh thu (VND)',
        data,
        backgroundColor: C('--color-primary-500'),
        hoverBackgroundColor: C('--color-primary-700'),
        borderRadius: 6,
        borderSkipped: false,
      }]
    },
    options: {
      responsive: true,
      maintainAspectRatio: false,
      plugins: {
        legend: { display: false },
        tooltip: tooltipDefaults,
      },
      scales: {
        x: { grid: { display: false }, border: { display: false } },
        y: {
          grid: gridDefaults,
          border: { display: false },
          ticks: {
            callback: (v) => `${(v / 1_000_000).toFixed(0)}M`,
          }
        }
      }
    }
  });
}
```

```html
<!-- HTML container -->
<div class="card" style="padding: var(--space-5);">
  <div class="card-header" style="border: none; background: none; padding: 0 0 var(--space-4) 0;">
    <h3 class="card-title">Doanh thu theo thang</h3>
    <span class="card-meta">Nam 2026</span>
  </div>
  <div style="height: 280px; position: relative;">
    <canvas id="doanh-thu-chart"></canvas>
  </div>
</div>
<script type="module">
  import { createDoanhThuChart } from '/js/doanh-thu-chart.js';
  createDoanhThuChart('doanh-thu-chart',
    ['T1','T2','T3','T4','T5','T6'],
    [8500000, 11200000, 9800000, 13400000, 12450000, 14100000]
  );
</script>
```

---

### 8.3 Line Chart — Don Hang Theo Ngay

```javascript
function createOrderTrendChart(canvasId, labels, data) {
  const ctx = document.getElementById(canvasId).getContext('2d');
  return new Chart(ctx, {
    type: 'line',
    data: {
      labels,
      datasets: [{
        label: 'So don hang',
        data,
        borderColor:     C('--color-primary-500'),
        backgroundColor: `${C('--color-primary-100')}55`,  /* opacity 33% */
        borderWidth: 2,
        pointBackgroundColor: C('--color-primary-700'),
        pointRadius: 4,
        pointHoverRadius: 6,
        fill: true,
        tension: 0.35,
      }]
    },
    options: {
      responsive: true,
      maintainAspectRatio: false,
      plugins: { legend: { display: false }, tooltip: tooltipDefaults },
      scales: {
        x: { grid: { display: false }, border: { display: false } },
        y: { grid: gridDefaults, border: { display: false }, beginAtZero: true }
      }
    }
  });
}
```

---

### 8.4 Pie/Doughnut — Phan Bo Trang Thai Don

```javascript
function createStatusPieChart(canvasId, labels, data) {
  // 5-color palette lay tu CSS tokens
  const palette = [
    C('--color-primary-500'),  // IN_PROGRESS
    C('--color-success'),      // COMPLETED
    C('--color-warning'),      // PENDING_*
    C('--color-info'),         // CONFIRMED / ASSIGNED
    C('--color-danger'),       // CANCELLED / IN_DISPUTE
  ];

  const ctx = document.getElementById(canvasId).getContext('2d');
  return new Chart(ctx, {
    type: 'doughnut',
    data: {
      labels,
      datasets: [{
        data,
        backgroundColor: palette,
        hoverOffset: 6,
        borderWidth: 2,
        borderColor: C('--color-bg-card'),
      }]
    },
    options: {
      responsive: true,
      maintainAspectRatio: false,
      cutout: '60%',
      plugins: {
        legend: {
          position: window.innerWidth < 768 ? 'bottom' : 'right',
          labels: { padding: 16, usePointStyle: true, pointStyleWidth: 10 }
        },
        tooltip: tooltipDefaults,
      }
    }
  });
}
```

---

## 9. Page Patterns

> Day la layout template hoan chinh. Codex dung de sinh HTML page nhanh,
> giu nhat quan toan du an.

### 9.1 Auth Page Template (Login / Register)

> Xem: **Section 4 — Login Form example** da co day du.
> Pattern chung: `<div class="login-page"> → <div class="login-card"> → form`.
> Trang Register chi them form-section chia cac nhom field.

---

### 9.2 Dashboard Page Template (Admin / Manager)

```html
<!DOCTYPE html>
<html lang="vi">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>Dashboard — Move_home Manager</title>
  <link href="https://fonts.googleapis.com/css2?family=Inter:wght@400;500;600;700&display=swap" rel="stylesheet">
  <link rel="stylesheet" href="/css/tokens.css">
  <link rel="stylesheet" href="/css/layout.css">
  <link rel="stylesheet" href="/css/nav.css">
  <link rel="stylesheet" href="/css/data-display.css">
  <link rel="stylesheet" href="/css/feedback.css">
  <style>
    body { margin: 0; font-family: var(--font-family-base); background: var(--color-bg-page); }
  </style>
</head>
<body>

<!-- Toast container -->
<div class="toast-container" id="toast-container" aria-live="polite"></div>

<!-- Header -->
<header class="site-header">
  <div class="container site-header-inner">
    <a href="/" class="site-logo">
      <span class="site-logo-icon">🏠</span>
      <span class="site-logo-text">Move_home</span>
    </a>
    <div class="site-header-actions">
      <button class="btn btn-icon" aria-label="Thong bao">🔔</button>
      <button class="user-menu-trigger" id="user-menu-trigger" aria-haspopup="true" aria-expanded="false">
        <div class="avatar avatar-sm avatar-initials">MG</div>
        <span class="user-menu-name">Manager</span>
        <span class="user-menu-chevron">▾</span>
      </button>
    </div>
  </div>
</header>

<div class="site-body">
  <!-- Sidebar -->
  <aside class="sidebar">
    <nav class="sidebar-nav">
      <a href="/dashboard" class="sidebar-item sidebar-item--active">
        <span class="sidebar-icon">📊</span><span class="sidebar-label">Tong quan</span>
      </a>
      <a href="/orders" class="sidebar-item">
        <span class="sidebar-icon">📦</span><span class="sidebar-label">Don hang</span>
      </a>
      <a href="/drivers" class="sidebar-item">
        <span class="sidebar-icon">🚗</span><span class="sidebar-label">Tai xe</span>
      </a>
    </nav>
  </aside>

  <!-- Main content -->
  <main class="site-main" id="main-content" tabindex="-1">
    <div class="container" style="padding-block: var(--space-8);">
      <h1 class="page-title">Tong quan</h1>

      <!-- KPI Row -->
      <div class="grid-4" style="margin-bottom: var(--space-8);">
        <!-- 4x kpi box (see Section 5.3) -->
      </div>

      <!-- Chart Row -->
      <div class="grid-2" style="margin-bottom: var(--space-8);">
        <div class="card" style="padding: var(--space-5);">
          <div class="card-header" style="border:none;background:none;padding:0 0 var(--space-4) 0;">
            <h3 class="card-title">Don hang theo ngay</h3>
          </div>
          <div style="height:240px;position:relative;">
            <canvas id="order-trend-chart"></canvas>
          </div>
        </div>
        <div class="card" style="padding: var(--space-5);">
          <div class="card-header" style="border:none;background:none;padding:0 0 var(--space-4) 0;">
            <h3 class="card-title">Phan bo trang thai</h3>
          </div>
          <div style="height:240px;position:relative;">
            <canvas id="status-pie-chart"></canvas>
          </div>
        </div>
      </div>

      <!-- Recent orders table -->
      <div class="card">
        <div class="card-header">
          <h3 class="card-title">Don hang gan nhat</h3>
          <a href="/orders" class="btn btn-secondary btn-sm">Xem tat ca</a>
        </div>
        <div class="card-body" style="padding: 0;">
          <div class="table-wrapper" style="border: none; border-radius: 0;">
            <table class="table table-hover">
              <!-- thead + tbody -->
            </table>
          </div>
        </div>
      </div>
    </div>
  </main>
</div>

</body>
</html>
```

---

### 9.3 Form Page Template (Driver Onboarding / Settings)

```html
<!-- Key differences: container-medium, form-section dividers, sticky action bar -->
<main class="site-main">
  <div class="container-medium" style="padding-block: var(--space-8);">
    <nav class="breadcrumb"><!-- 6.3 --></nav>
    <h1 class="page-title">Dang ky lam tai xe — Buoc 3: Upload giay to</h1>

    <form id="docs-form" novalidate>
      <div class="form-section">
        <h2 class="form-section-title">Giay phep lai xe (GPLX)</h2>
        <!-- form-group cho GPLX front + back -->
      </div>

      <div class="form-section">
        <h2 class="form-section-title">Dang ky xe</h2>
        <!-- form-group cho registration photo + metadata -->
      </div>

      <div class="form-section">
        <h2 class="form-section-title">Anh xe thuc te</h2>
        <!-- form-group cho 3 vehicle photos -->
      </div>

      <!-- Sticky action bar -->
      <div class="form-actions">
        <a href="/driver/onboarding/step2" class="btn btn-secondary btn-md">← Quay lai</a>
        <button type="submit" class="btn btn-primary btn-md">Tiep theo →</button>
      </div>
    </form>
  </div>
</main>
```

---

### 9.4 List Page Template (Order List / Driver List)

```html
<main class="site-main">
  <div class="container" style="padding-block: var(--space-8);">
    <div class="flex-between" style="margin-bottom: var(--space-6);">
      <h1 class="page-title" style="margin: 0;">Danh sach don hang</h1>
      <a href="/orders/new" class="btn btn-primary btn-md">+ Tao don moi</a>
    </div>

    <!-- Filter bar -->
    <div class="flex-row flex-wrap flex-gap-3" style="margin-bottom: var(--space-5);">
      <input class="form-input" type="search" placeholder="Tim theo ma don, ten khach..."
             style="max-width: 280px;">
      <div class="form-select-wrapper" style="min-width: 160px;">
        <select class="form-select">
          <option value="">Trang thai: Tat ca</option>
          <option value="CONFIRMED">Da xac nhan</option>
          <option value="IN_PROGRESS">Dang giao</option>
        </select>
      </div>
      <input class="form-input" type="date" style="max-width: 160px;">
    </div>

    <!-- Table -->
    <div class="table-wrapper">
      <table class="table table-hover table-sticky">
        <thead>...</thead>
        <tbody>...</tbody>
      </table>
    </div>

    <!-- Pagination -->
    <div class="pagination">...</div>
  </div>
</main>
```

---

### 9.5 Detail Page Template (Order Detail / Driver Profile)

```html
<main class="site-main">
  <div class="container" style="padding-block: var(--space-8);">
    <!-- Back + breadcrumb -->
    <div class="flex-row flex-gap-4" style="margin-bottom: var(--space-5);">
      <a href="/orders" class="btn btn-secondary btn-sm">← Quay lai</a>
      <nav class="breadcrumb" style="margin: 0;">...</nav>
    </div>

    <!-- Hero section -->
    <div class="card" style="margin-bottom: var(--space-6);">
      <div class="card-body">
        <div class="flex-between">
          <div>
            <h1 class="page-title" style="margin-bottom: var(--space-2);">Don hang #1001</h1>
            <span class="badge badge-md badge-warning">Cho xac nhan</span>
          </div>
          <div class="btn-group">
            <button class="btn btn-secondary btn-md">In don</button>
            <button class="btn btn-primary btn-md">Phan cong tai xe</button>
          </div>
        </div>
      </div>
    </div>

    <!-- Tabs -->
    <div class="tabs" role="tablist">
      <button class="tab tab--active" role="tab" aria-selected="true">Tong quan</button>
      <button class="tab" role="tab" aria-selected="false">Lich su thay doi</button>
      <button class="tab" role="tab" aria-selected="false">Thanh toan</button>
    </div>

    <!-- Tab panels -->
    <div class="tab-panel tab-panel--active">
      <div class="grid-2">
        <!-- Thong tin don hang + Thong tin khach hang -->
      </div>
    </div>
  </div>
</main>
```

---

## 10. Accessibility & Responsive Notes

### 10.1 Accessibility Checklist

> Chay checklist nay truoc khi nop PR bao gom HTML.

| # | Yeu cau | Kiem tra nhu the nao |
|---|---------|---------------------|
| A1 | Moi `<input>`, `<select>`, `<textarea>` PHAI co `<label for="...">` tuong ung | Grep `<input` kem `id=`, xac nhan co `<label for=` cung `id` |
| A2 | Color contrast text chinh tren nen trang >= 4.5:1 (WCAG AA) | Chrome DevTools → Accessibility → Color contrast |
| A3 | Moi interactive element co `:focus-visible` ring ro rang | Tab qua trang, tat ca element phai co vien focus |
| A4 | Button chi chua icon PHAI co `aria-label` mo ta hanh dong | Grep `.btn-icon` kem `aria-label` |
| A5 | Modal PHAI co `role="dialog"`, `aria-modal="true"`, `aria-labelledby` tro vao `modal-title` | Inspect HTML khi modal mo |
| A6 | Modal PHAI bep focus vao trong khi dang mo (focus trap) | Tab trong modal: focus khong thoat ra ngoai |
| A7 | An modal bang ESC phai hoat dong | Press ESC khi modal dang mo |
| A8 | Status badge PHAI co text (khong chi mau) | Screen reader doc duoc text trong `.badge` |
| A9 | Toast container PHAI co `aria-live="polite"` | Screen reader thong bao khi toast xuat hien |
| A10 | `<img>` co noi dung PHAI co `alt` mo ta; icon `<img>` dung cho trang tri: `alt=""` | Grep `<img` kiem tra `alt` attribute |
| A11 | Heading hierarchy hop le: moi trang co duy nhat 1 `<h1>`, cac heading tiep theo theo thu tu | Inspect Outline trong browser DevTools |
| A12 | Link "Bo qua noi dung" (skip link) dat truoc header cho keyboard users | Xem element dau tien khi Tab vao trang |

---

### 10.2 Responsive Breakpoints

> Recap tu Section 3 — chi so de reference nhanh.

| Breakpoint | Gia tri | Thay doi layout chinh |
|-----------|---------|----------------------|
| Mobile | < 768px | 1 cot; sidebar an; hamburger hien; grid → 1 col; modal → bottom sheet |
| Tablet | 768px – 1023px | 2 cot; sidebar collapse (toggle); grid-4 → 2 col; chart legend → bottom |
| Desktop | >= 1024px | Layout day du; sidebar 240px co dinh; grid-4 day du; chart legend → right |

```css
/* Utility class an/hien theo breakpoint */
.show-mobile  { display: none; }
.hide-mobile  { display: block; }

@media (max-width: 767px) {
  .show-mobile { display: block; }
  .hide-mobile { display: none; }
}
```

---

### 10.3 Touch Targets

- **Kich thuoc toi thieu:** 44×44px cho moi element tuong tac tren mobile (Apple HIG / WCAG 2.5.5).
- `.btn-sm` tren mobile: them `min-height: 44px` bang utility class `.touch-target`.
- Khoang cach toi thieu giua 2 element tuong tac: >= 8px (tranh bam nham).

```css
/* Dung tren mobile khi button phai nho hon 44px ve mat hin thi */
@media (max-width: 767px) {
  .touch-target {
    min-height: 44px;
    min-width: 44px;
    display: inline-flex;
    align-items: center;
    justify-content: center;
  }
}
```

---

### 10.4 Loading Performance

| Technique | Mo ta | Ap dung cho |
|-----------|-------|-------------|
| **Inline critical CSS** | Dat tokens.css + layout.css critical path vao `<style>` inline trong `<head>` | Tat ca trang |
| **`font-display: swap`** | Tranh FOIT khi load Inter tu Google Fonts | `<link>` Google Fonts |
| **`loading="lazy"`** | Anh avatar, anh xe, anh DamageReport khong trong viewport | `<img class="avatar-img">` |
| **`defer` JS** | Script khong phai critical (chart, modal, dropdown) dat cuoi body + `defer` | charts-config.js, nav.js |
| **Skeleton truoc data** | Hien skeleton loader trong khi fetch API — tranh layout shift | List page, Dashboard KPI |
| **Debounce search** | Input tim kiem goi API sau 300ms khong go them | Filter bar tren List page |

```html
<!-- Google Fonts toi uu -->
<link rel="preconnect" href="https://fonts.googleapis.com">
<link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
<link href="https://fonts.googleapis.com/css2?family=Inter:wght@400;500;600;700&display=swap"
      rel="stylesheet">

<!-- Chart.js chi load khi can -->
<script defer src="https://cdn.jsdelivr.net/npm/chart.js@4/dist/chart.umd.min.js"></script>
<script defer type="module" src="/js/charts-config.js"></script>
```
