# CONTEXT.md — Hệ thống Dịch Vụ Chuyển Nhà (SWP — FPT University)

> **Trang thai:** Pha 0 — Context Discovery — HOAN THANH (v2.0)
> **Ngay cap nhat:** 2026-05-29 (MAJOR bump tu v1.5 do thay yeu cau pivot) | **Nhom:** 5 nguoi | **Thoi gian code:** 6 tuan
> **Phuong phap:** Spec-Driven Development (Claude viet spec → Codex sinh code)
> **Mo hinh:** MARKETPLACE CO DIEU PHOI — Driver tu dang ky, Manager phan cong, cong ty thu commission 30%
> **Pham vi dia ly:** Noi thanh Ha Noi (~12 quan)
> **Vai tro:** 4 (KHONG con Porter — Driver kiem boc xep)

> ⚠️ **MAJOR PIVOT v1.5 → v2.0 (2026-05-29):**
> Theo yeu cau cua thay, du an chuyen tu mo hinh CONG TY NOI BO sang
> MARKETPLACE CO DIEU PHOI. Cac thay doi chinh:
> - Driver: tu nhan vien noi bo → doi tac dang ky tu do
> - Vehicle: tu tai san cong ty → Driver so huu
> - Bo vai tro Porter; Driver kiem nhiem boc xep
> - Them quy trinh duyet Driver + coc 3 trieu collateral
> - Doi luong tien: 100% qua VNPay, khong COD; commission 30%
> - Them Vi noi bo cho Driver + escrow 2 gio
> - Doi Maps API: Google Maps → OpenStreetMap + OSRM
> - Them Guest mode (xem website khong can login)
>
> File backup CONTEXT v1.5: `docs/CONTEXT_v1.5_archived.md` (de tham chieu khi can).
> ⚠️ Thay duyet: Cac quyet dinh chinh cua v2.0 da duoc thay xac nhan (marketplace, Driver tu dang ky, coc 3 trieu, commission 30%, 100% qua VNPay, escrow 2h, Driver boi thuong 100%, OSRM, Guest mode, luong AWAITING_FINAL_PAYMENT). Khong can review them.

---

## 1. PROBLEM STATEMENT

He thong giai quyet **3 noi dau cua 4 nhom nguoi dung:**

**Khach hang:**
- Khong co kenh dat dich vu chuyen nha ro rang, phai goi dien hoi gia
  thu cong tu nhieu cong ty
- Khong biet gia truoc khi dat → lo bi "het gia" khi xe da toi noi
- Khong theo doi duoc don dang o dau

**Driver (doi tac):**
- Co xe rieng nhung khong co nen tang nhan don deu dan
- Khong co khach hang on dinh, phai phu thuoc moi quan he ca nhan
- Khong co ket cau ho tro khi co tranh chap voi khach

**Cong ty (Manager — dieu phoi):**
- Khong tu so huu xe → giam von dau tu ban dau
- Nhan don tu khach roi phan cong cho Driver doi tac → an commission 30%
- Quan ly chat luong Driver qua quy trinh duyet + DamageReport + rating

**Guest (chua dang nhap):**
- Vao website xem dich vu, gia ca, uoc tinh chi phi truoc khi quyet dinh
  dang ky

**Boi canh:**
- Cong ty MOI thanh lap, khong co he thong cu (gia dinh do an)
- KHONG marketplace mo (kieu Grab tai xe tu nhan don) — Manager VAN
  phan cong thu cong, Driver chap nhan/tu choi
- Thanh toan 100% qua VNPay Sandbox (khong COD, khong tien mat)

**Success metric:**
- Khach tu dat don + nhan bao gia trong < 5 phut, khong can goi dien
- Driver tu dang ky + duoc duyet trong < 24 gio (Manager xem ho so)
- Manager phan don cho Driver phu hop (theo quan hoat dong) < 2 phut
- 100% don COMPLETED co audit trail tien (coc → 70% tra → commission →
  vi Driver)
- Guest co the xem 6 trang public ma khong can dang nhap

---

## 2. DOMAIN KNOWLEDGE

### Thuat ngu domain

| Thuat ngu | Giai thich |
|-----------|-----------|
| **Order (Don hang)** | Yeu cau chuyen nha cua khach. Goi tat ca thong tin: diem di, diem den, thoi gian, loai xe, do, co thue boc xep khong, cac phu thu. Thuc the trung tam. |
| **Trip (Chuyen di)** | Lan thuc thi mot Order, sinh ra khi Manager phan cong. Gan: 1 Order + 1 Driver + 1 Vehicle. (KHONG con Porter rieng — Driver kiem boc xep neu khach co dat). |
| **Driver (Tai xe)** | Doi tac dang ky tu do tu ben ngoai. So huu xe rieng. Co quy trinh duyet 4 buoc (xem Driver Onboarding luot 3). Sau khi ACTIVE moi nhan duoc don. |
| **Vehicle (Xe)** | Tai san CUA DRIVER, KHONG phai cong ty. Driver upload anh + dang ky xe khi onboarding. Moi Driver co the dang ky 1 hoac nhieu xe. |
| **Vehicle Type (Loai xe)** | 4 loai: Xe 3 gac (500kg) / Xe tai vua (700kg-1 tan) / Xe tai lon (1.5-2 tan) / Xe to (2.5-5 tan). Driver chon loai xe minh dang ky. |
| **Quote (Bao gia)** | Hai dang: (a) **Public Quote** Guest goi `/api/public/quote-estimate` ra bao gia, khong luu DB. (b) **Order Quote** Customer dat don, luu vao Order. Cong thuc giong nhau. |
| **Order Status** | Vong doi don — xem state machine (2b) ben duoi. Co 8 trang thai. |
| **Deposit (Coc)** | 30% bao gia, tra qua VNPay khi dat. Day cung chinh la commission cong ty thu (Quyet dinh: commission 30% tren total_quote). |
| **Final Payment (70%)** | Khach tra not 70% qua VNPay TAI CHO sau khi Driver chuyen do xong, TRUOC khi Driver bam Hoan thanh. KHONG co COD. |
| **Commission** | 30% tren total_quote. Trung khop voi so tien coc → cong ty giu coc luon, khong can chuyen di chuyen lai. |
| **Escrow Window** | 2 gio sau COMPLETED. Trong 2h khach co quyen tao DamageReport hoac rating. Het 2h: scheduled job tu chuyen 70% (= total_quote - commission 30%) vao vi Driver. |
| **Wallet (Vi Driver)** | So du tien cua Driver luu trong he thong. Cong them khi don COMPLETED + het escrow 2h khong khieu nai. Tru khi co DamageReport (Driver boi thuong 100%). |
| **Withdrawal (Don rut tien Driver)** | Driver tao yeu cau rut → Admin duyet thu cong + chuyen khoan ngoai he thong → danh dau PROCESSED. |
| **Driver Deposit (Coc Driver)** | 3 trieu dong, dong qua VNPay khi dang ky lam Driver. La COLLATERAL cho DamageReport (tru truoc tu coc, het coc thi tru vi, het vi thi khoa account). |
| **DamageReport** | Khach tao trong 2h escrow neu phat hien do bi hu hong/mat. Driver boi thuong 100% (KHONG con chia 50/50). Thu tu tru: coc 3 trieu → vi Driver → khoa account. |
| **DisputeReport (MOI)** | Driver tao tai cho khi khach khong chiu tra 70%. Don ve IN_DISPUTE. Manager goi dien hai ben → giai quyet thu cong. |
| **RefundRecord** | Khi cong ty huy don (loi cong ty): tao RefundRecord PENDING → Manager chat xin STK khach → chuyen khoan thu cong → danh dau PROCESSED. KHONG co vi noi bo cho Customer. |

---

### Vong doi don hang — Order State Machine

Don co **8 trang thai**. Bang transition hop le:

| Tu | Sang | Actor | Dieu kien |
|----|------|-------|-----------|
| (init) | `PENDING_PAYMENT` | SYSTEM | Khach bam "Dat don", he thong tao order va URL VNPay |
| `PENDING_PAYMENT` | `CONFIRMED` | SYSTEM | IPN VNPay xac nhan da nhan coc 30% (HR-04 verify hash) |
| `PENDING_PAYMENT` | `CANCELLED` | CUSTOMER | Khach huy o trang thai nay → khong mat gi (chua coc) |
| `PENDING_PAYMENT` | `CANCELLED` | SYSTEM | Sau 15 phut khong nhan IPN hop le → auto-cancel |
| `CONFIRMED` | `ASSIGNED` | MANAGER | Manager phan Driver phu hop (theo quan hoat dong, loai xe) |
| `CONFIRMED` | `CANCELLED` | CUSTOMER | Khach huy khi CHUA co tai xe (driver_id NULL) → tao yeu cau hoan coc (order_cancellation_refund PENDING), Manager duyet → hoan coc 30% ve vi khach (cap nhat 2026-07-13, xem HR-14 + §Huy don) |
| `CONFIRMED` | `CANCELLED` | COMPANY | Manager huy do loi cong ty → tao RefundRecord 30% |
| `ASSIGNED` | `ASSIGNED` | DRIVER | Driver tu choi → reset, Manager phan Driver khac (tinh vao quota tu choi) |
| `ASSIGNED` | `IN_PROGRESS` | DRIVER | Driver chap nhan + da toi noi + bat dau chuyen |
| `ASSIGNED` | `CANCELLED` | MANAGER | Manager huy (vi du khong tim duoc Driver phu hop) → RefundRecord |
| `IN_PROGRESS` | `AWAITING_FINAL_PAYMENT` | DRIVER | Driver chuyen do xong, bam "Yeu cau thanh toan" |
| `IN_PROGRESS` | `IN_DISPUTE` | DRIVER | Driver bam "Bao cao tranh chap tai cho" giua chung |
| `AWAITING_FINAL_PAYMENT` | `COMPLETED` | DRIVER | IPN xac nhan khach da tra 70% → Driver bam "Hoan thanh" |
| `AWAITING_FINAL_PAYMENT` | `IN_DISPUTE` | DRIVER | Driver bam "Bao cao tranh chap tai cho" (khach khong chiu tra) |
| `COMPLETED` | `IN_DISPUTE` | CUSTOMER | Khach tao DamageReport trong 2h escrow |
| `IN_DISPUTE` | `COMPLETED` | MANAGER | Manager giai quyet xong: hoac (a) DamageReport RESOLVED (Driver boi thuong 100%) hoac (b) DISMISSED (bao cao khong hop le) hoac (c) khach dong y tra 70% sau khi DisputeReport |
| `IN_DISPUTE` | `CANCELLED` | MANAGER | Manager quyet huy don do tranh chap khong giai quyet duoc → tuy loi ai ma quyet hoan coc hay khong |

**Moi transition khong nam trong bang nay → HTTP 409 (HR-03).**

**Metadata bat buoc khi CANCELLED:**
- `cancelled_by`: `CUSTOMER` | `COMPANY` | `SYSTEM`
- `cancelled_reason`: text
- `CUSTOMER` o `PENDING_PAYMENT` → khong mat gi, khong RefundRecord (chua coc)
- `CUSTOMER` huy o `CONFIRMED` khi CHUA co tai xe → tao yeu cau hoan coc (order_cancellation_refund), Manager duyet → hoan coc 30% ve customer_wallet (cap nhat 2026-07-13, HR-14). Tu `ASSIGNED` tro di khach KHONG huy duoc (coc thuoc cong ty)
- `COMPANY` (bat ky luc nao tu CONFIRMED tro di) → tao RefundRecord (PENDING, amount = 30% bao gia)
- `SYSTEM` (timeout 15 phut) → khong hoan, khong RefundRecord

**Quyen sua don sau khi dat (Cach 3 — han che pham vi):**
- Trang thai `CONFIRMED`: khach duoc sua **chi cac fields KHONG anh huong gia**:
  - So dien thoai lien he
  - Ghi chu / notes cho tai xe
  - Gio mong muon, **mien la gio moi van trong cung khung gia** (cung khung cao diem hoac cung khung thuong nhu gio cu)
- Khach **KHONG duoc sua** cac fields anh huong gia (yeu cau lien he Manager qua chat neu can):
  - Diem di / diem den (anh huong km)
  - Loai xe (anh huong don gia/km)
  - So boc xep (anh huong porter_fee)
  - Tang nha diem di / diem den (anh huong floor_surcharge)
  - Co thang may (anh huong floor_surcharge)
  - Co ngo hep (anh huong alley_surcharge)
  - Gio mong muon **neu nhay khung gia** (vd dang gio thuong, doi sang 7:30 → vao khung cao diem → cam, lien he Manager)
- Validation: API endpoint sua don kiem tra dieu kien tren; vi pham → HTTP 422 voi danh sach field vi pham. Sua hop le → KHONG tinh lai bao gia (vi gia khong doi).
- Trang thai `ASSIGNED` tro di: KHONG duoc sua bat ky truong nao. Lien he Manager qua chat.

---

### Loai xe & Bang gia

| Loai xe | Don gia/km | Gia boc xep/nguoi/chuyen | Tai trong toi da |
|---------|-----------|--------------------------|-----------------|
| Xe 3 gac | 20.000d | 150.000d | 500 kg |
| Xe tai vua | 30.000d | 200.000d | 700 kg |
| Xe tai lon | 40.000d | 300.000d | 1.500 kg (1,5 tan) |

- Tai trong chi dung de **hien thi thong tin** cho khach tham khao khi chon xe. KHONG phai validation cung chan dat don.
- Khong co cuoc co ban (flag-fall). Gia thuan theo km.
- Khoang cach km: lay tu OpenStreetMap + OSRM (xem muc Maps API & OSRM ben duoi).
- Fallback khi API loi/het quota: tra bang khoang cach quan → quan noi thanh Ha Noi; bao gia hien thi nhan "uoc tinh".

---

### Cong thuc tinh bao gia

```
Buoc 1 — Gia km co ban:
  base_price = (don gia/km theo loai xe) x (so km tu OSRM API)

Buoc 2 — Phu thu (tat ca tinh tren base_price, cong don, KHONG nhan cheo):
  + peak_surcharge   = base_price x 30%   neu gio xuat phat trong [7:00-9:00] hoac [17:00-19:00]
  + alley_surcharge  = base_price x 20%   neu khach tick "ngo hep, xe khong vao duoc"
  + floor_surcharge  = 0                  neu tang tret (ground floor, khong leo cau thang)
                     = base_price x 20%   neu tang 2-3, khong thang may
                     = base_price x 30%   neu tang 4-5, khong thang may
                     = base_price x 50%   neu tang 6-10, khong thang may
  (Chi ap dung 1 muc tang cao, lay muc cao hon giua diem di va diem den.
   Neu diem di/den co thang may → coi nhu tang tret cho diem do.)

Buoc 3 — Phu thu boc xep (doc lap, khong tinh % tren base_price):
  porter_fee = (gia boc xep/nguoi theo loai xe) x (so nguoi khach chon)
  (porter_fee = 0 neu khach khong chon boc xep)

Ket qua:
  total_quote = base_price + peak_surcharge + alley_surcharge + floor_surcharge + porter_fee
```

**Vi du minh hoa:**
```
Xe tai vua, 10km, gio cao diem, ngo hep, tang 4 (khong thang may), 2 boc xep:
  base_price    = 30.000 x 10         = 300.000d
  peak          = 300.000 x 30%       =  90.000d
  alley         = 300.000 x 20%       =  60.000d
  floor (4-5)   = 300.000 x 30%       =  90.000d
  porter        = 200.000 x 2         = 400.000d
  -----------------------------------------------
  total_quote                         = 940.000d
```

**Note (v2.0):** Boc xep = phu thu tra cho Driver (vi Driver kiem nhiem boc xep). Khach van chon so nguoi boc xep tu 0-N, gia van 200k/nguoi. Cong ty tinh commission 30% TREN TONG total_quote (bao gom ca phu thu boc xep).

---

### Thanh toan — 100% qua VNPay (khong COD)

**Luong tien moi (v2.0):**

**Buoc 1 — Khach dat don, coc 30%:**
- Khach bam "Dat" → he thong tao URL VNPay cho 30% × total_quote
- Khach tra → IPN verify hash (HR-04) → don ve `CONFIRMED`
- 30% nay nam trong dashboard cong ty (chua thuoc ve Driver)

**Buoc 2 — Manager phan + Driver chap nhan + chuyen do:**
- Khong co thay doi tien

**Buoc 3 — Driver chuyen xong, yeu cau khach tra 70%:**
- Driver bam "Yeu cau thanh toan" → don ve `AWAITING_FINAL_PAYMENT`
- Khach mo app → bam "Thanh toan" → URL VNPay cho 70% × total_quote
- Khach tra → IPN auto-confirm → Driver thay nut "Hoan thanh" sang xanh
- 70% nay van trong dashboard cong ty

**Buoc 4 — Driver bam "Hoan thanh":**
- Don ve `COMPLETED`
- BAT DAU dem escrow 2 gio
- Tien 100% van trong dashboard cong ty (chua chia)

**Buoc 5 — Het escrow 2h:**
- Scheduled job kiem tra: don nay co DamageReport (status OPEN/NEGOTIATING) khong?
- **KHONG co** → chuyen `70% × total_quote` vao vi Driver, log AUDIT. Commission 30% cong ty giu (chinh la khoan coc da nhan tu dau).
- **CO** → tien treo, doi Manager giai quyet DamageReport. Sau khi RESOLVED (Driver boi thuong) HOAC DISMISSED → chuyen tien theo quyet dinh.

**Edge case — Driver bao cao tranh chap tai cho (DisputeReport):**
- Khach khong chiu tra 70% → Driver bam "Bao cao tranh chap tai cho"
- Don ve `IN_DISPUTE` (cung status, nguon khac voi DamageReport)
- Manager goi dien hai ben → 2 quyet dinh:
  - (a) Khach dong y tra → quay lai luong thanh toan binh thuong
  - (b) Khong giai quyet duoc → Manager huy don → tuy loi ai ma quyet hoan coc hay khong (giong CANCELLED)

**IPN Luong (bat buoc — 3 luong):**
- **IPN (server-to-server callback):** nguon cap nhat DB duy nhat. Verify secure hash truoc khi xu ly.
- **Return URL:** CHI de hien thi ket qua cho khach. KHONG cap nhat DB.
- Ba luong IPN: (1) coc 30% khach, (2) thanh toan 70% khach, (3) coc Driver 3 trieu.
- KHONG cap nhat trang thai dua vao Return URL phia client — chong gia mao `?status=success`.

**IPN Timeout:**
- Neu sau **15 phut** ke tu khi tao don khong nhan IPN hop le → Scheduled Job tu dong chuyen don sang `CANCELLED` (cancelled_by: SYSTEM).
- [to do] Xem xet them nut "Kiem tra lai thanh toan" cho Manager.

---

### Huy don & Hoan tien (khong co vi noi bo)

- **Khach huy o `PENDING_PAYMENT`** (chua coc): → `CANCELLED` (cancelled_by: CUSTOMER). KHONG mat gi, KHONG tao RefundRecord (vi chua tra gi). Day la "huy khong dieu kien" — khach co the huy bat cu luc nao truoc khi thanh toan VNPay.
- **Khach huy o `CONFIRMED` khi CHUA co tai xe nhan** (da coc, driver_id NULL): → `CANCELLED` (cancelled_by: CUSTOMER). He thong tao **order_cancellation_refund** (status PENDING) kem ly do + anh (toi da 3, Cloudinary AC-10) → Manager duyet thu cong → hoan **coc 30%** ve **customer_wallet** (transaction REFUND, AC-13; vi khong am, HR-18) hoac tu choi kem ly do. *(Cap nhat 2026-07-13 — leader duyet; day la luong RIENG, KHONG phai RefundRecord. Truoc day khach huy tu CONFIRMED la mat coc; nay hoan coc khi chua co tai xe cam ket. Migration V41.)* Tu `ASSIGNED` tro di: khach KHONG huy duoc, coc thuoc cong ty (lien he Manager).
- **Cong ty huy / loi cong ty** (Manager xac dinh, bat ky trang thai nao tu CONFIRMED): → `CANCELLED` (cancelled_by: COMPANY) → he thong tao **RefundRecord**: order_id, amount = 30% bao gia, status = `PENDING`.
  - Luong hoan tien:
    1. He thong tao RefundRecord (PENDING) + gui email thong bao cho khach.
    2. Manager mo chat voi khach → xin so tai khoan (STK) ngan hang.
    3. Khach cung cap STK qua chat. Manager cap nhat STK vao RefundRecord.
    4. Manager thuc hien chuyen khoan ben ngoai he thong.
    5. Manager bam "Da hoan tien" tren panel → RefundRecord = `PROCESSED`.
    6. He thong gui email xac nhan hoan tien cho khach.
  - RefundRecord luu: order_id, amount, customer_bank_account (STK), status, processed_at, processed_by.
  - KHONG co vi noi bo, KHONG co VNPay refund tu dong.
  - Khach theo doi trang thai hoan tien qua trang lich su don.
- **He thong huy** (`SYSTEM`, do timeout 15 phut): → `CANCELLED` (cancelled_by: SYSTEM). KHONG hoan, KHONG tao RefundRecord (khach chua coc thanh cong).

---

### Bao cao hu hong (DamageReport)

**Khi nao su dung:** Khach phat hien do bi hu hong/mat sau khi chuyen xong, trong 2 gio escrow sau `COMPLETED`.

**Quy tac moi (v2.0 — Driver 100% trach nhiem):**
- Khach tao DamageReport trong 2h escrow sau COMPLETED
- Don ve `IN_DISPUTE`
- Driver boi thuong **100%** (bo 50/50 cua v1.5)
- **Thu tu tru tien boi thuong:**
  1. Tru tu **COC 3 TRIEU** cua Driver truoc
  2. Het coc → tru tu **VI Driver**
  3. Het ca hai → DamageReport van RESOLVED, nhung Driver account ve status `SUSPENDED` (khong nhan don nua) cho den khi Driver nap lai coc
- Driver phai nap lai coc du 3 trieu moi duoc nhan don tiep (sau khi bi tru cho DamageReport hoac CANCELLED)

**Luong xu ly:**
1. Khach bam "Bao cao hu hong" → dien mo ta + upload hinh anh → submit.
2. Don → `IN_DISPUTE`. DamageReport.status = `OPEN`.
3. Manager thay DamageReport moi trong dashboard → tro chuyen voi khach (qua chat).
4. Manager re mot trong 2 nhanh:

   **Nhanh A — Bao cao hop le (boi thuong):**
   - Manager nhap so tien boi thuong de xuat → DamageReport.status = `NEGOTIATING`.
   - Khach xem de xuat → bam "Dong y boi thuong" → DamageReport.status = `CUSTOMER_AGREED`.
   - Manager bam "Xac nhan da giai quyet" → DamageReport.status = `RESOLVED`, don → `COMPLETED`.
   - He thong tru boi thuong tu coc Driver → vi Driver theo thu tu.

   **Nhanh B — Bao cao khong hop le (dismiss):**
   - Manager bam "Tu choi bao cao" + nhap ly do → DamageReport.status = `DISMISSED`.
   - Don → `COMPLETED`. Khong tru tien Driver.

**DamageReport — Trang thai:**

| Trang thai | Mo ta |
|-----------|-------|
| `OPEN` | Khach vua tao, Manager chua xu ly |
| `NEGOTIATING` | Manager da nhap so tien boi thuong, cho khach phan hoi |
| `CUSTOMER_AGREED` | Khach da chap nhan — Manager co the bam "Hoan thanh" |
| `DISMISSED` | Manager tu choi report (khong hop le) — don van co the COMPLETED |
| `RESOLVED` | Da ket thuc sau khi don COMPLETED |

**Rang buoc:**
- Mot don chi co toi da 1 DamageReport dang mo tai mot thoi diem.
- Chi Manager moi co quyen chuyen don tu `IN_DISPUTE` → `COMPLETED`.
- DamageReport gan voi Order, chua trang thai rieng cua minh.

---

### Phan cong & Dieu phoi (Driver Acceptance)

- Manager phan cong **thu cong** (giu nguyen — khong co AI phan cong tu dong)
- **He thong goi y Driver phu hop dua tren:**
  - Driver co quan hoat dong khop diem di hoac diem den
  - Driver co loai xe khop yeu cau cua don
  - Driver status = `ACTIVE` va availability = `FREE`
- Manager bam "Phan Driver X" → don ve `ASSIGNED`, Driver X nhan notification
- **Driver X co 5 PHUT** de bam "Chap nhan" hoac "Tu choi":
  - Chap nhan → don van `ASSIGNED`, Driver chuyen di
  - Tu choi → don quay lai `CONFIRMED`, Manager phan Driver khac
  - Khong tra loi sau 5 phut → he thong coi nhu tu choi
- **Quota tu choi:** Driver bi gioi han tu choi 3 don/ngay. Vuot → notification cho Manager. Manager co the cao buoc bao Admin → kha nang khoa account.
- **Invariant conflict check:** Chi assign Driver co `availability_status = FREE`. Enforce bang check trong transaction truoc INSERT Trip — neu status da BUSY → tra loi loi, khong tao Trip.

---

### Maps API & OSRM

- Su dung **OpenStreetMap + OSRM** (KHONG dung Google Maps)
- OSRM public demo endpoint: `https://router.project-osrm.org` (free, khong API key, co rate limit)
- Tinh khoang cach diem A-B bang OSRM table service
- **Fallback:** bang khoang cach quan→quan Ha Noi (~12 quan) khi OSRM loi/het quota; bao gia hien thi nhan "uoc tinh"
- Note: OSRM chat luong Vietnam thap hon Google Maps, nhung du cho do an noi thanh Ha Noi

---

### Tai khoan

- **Admin tao** tai khoan: Manager.
- **Driver tu dang ky** qua form onboarding (4 buoc — xem Driver Onboarding spec luot 3). Sau khi Manager duyet → ACTIVE.
- Sau khi tao tai khoan Manager → he thong gui email (Gmail SMTP async) chua username + mat khau tam thoi.
- Staff **duoc doi password** sau lan dang nhap dau. [to do] Co bat buoc khong?
- Customer co trang Forgot Password qua email (luong standard).
- [to do] Staff quen password: Admin reset thu cong hay co trang Forgot Password rieng?

---

### Driver Onboarding (v2.0 — chi tiet)

**Tong quan:** Driver tu dang ky qua form ngoai, di qua **4 buoc** truoc khi vao ACTIVE va co the nhan don. Manager la nguoi duyet o buoc cuoi.

**State machine cua Driver account:**

```
[NEW]
  ↓ POST /api/auth/register (Driver chon role)
[PENDING_VERIFY] — chua xac thuc email
  ↓ Click link verify trong email
[PENDING_DOCUMENTS] — da verify email, chua upload giay to
  ↓ Upload du 3 loai giay to qua Cloudinary
[PENDING_DEPOSIT] — da co giay to, chua coc 3 trieu
  ↓ Tra VNPay coc 3 trieu thanh cong (IPN xac nhan)
[PENDING_APPROVAL] — du dieu kien, cho Manager duyet
  ↓ Manager bam APPROVE
[ACTIVE] — co the nhan don
  hoac
  ↓ Manager bam REJECT (kem ly do)
[REJECTED] — cho Driver re-submit (sua giay to → quay lai PENDING_APPROVAL)
```

**4 buoc dang ky (chi tiet):**

**Buoc 1 — Dang ky account co ban:**
- Form: email + phone + password + ho ten + ngay sinh + dia chi + quan hoat dong (chon 1 hoac nhieu) + dong y dieu khoan
- Validate format giong Customer (email RFC, phone +84xxx, password bcrypt, age >= 18)
- user.role = DRIVER, user.status = PENDING_VERIFY
- Gui email verify (token 24h)

**Buoc 2 — Verify email:**
- Driver click link trong email → he thong update status = PENDING_DOCUMENTS
- Hien thi UI: "Vui long upload 3 loai giay to de tiep tuc"

**Buoc 3 — Upload giay to (qua Cloudinary signed upload theo AC-10):**
- **GPLX (Giay phep lai xe):** mat truoc + mat sau (2 anh)
- **Dang ky xe:** anh dang ky xe (1 anh) + nhap so bien so + loai xe + tai trong
- **Anh xe:** 3 anh thuc te cua xe (truoc / sau / hong)

Sau khi upload du → status = PENDING_DEPOSIT

**Buoc 4 — Coc 3 trieu qua VNPay:**
- Driver bam "Coc va hoan tat dang ky" → he thong tao URL VNPay
- Driver tra 3 trieu → IPN auto-confirm → status = PENDING_APPROVAL
- Driver chua dong tien KHONG the gui ho so cho Manager duyet

**Buoc 5 — Manager duyet (xu ly o spec #3):**
- Manager dashboard hien danh sach Driver PENDING_APPROVAL
- Manager xem 3 loai giay to + thong tin Driver + bien so xe
- Bam APPROVE → status = ACTIVE, gui email "Da duoc duyet"
- Bam REJECT + nhap ly do → status = REJECTED, gui email "Bi tu choi" va ly do, cho phep Driver re-upload tu PENDING_DOCUMENTS

**Edge case — Driver bi REJECTED:**
- Driver login → he thong hien message "Tai khoan bi tu choi. Ly do: ..."
- Co nut "Sua giay to va gui lai" → status quay lai PENDING_DOCUMENTS
- Driver upload giay to moi → he thong tu set status PENDING_APPROVAL ngay (khong can coc lai vi coc cu van con)

**Coc 3 trieu — quy tac:**
- La COLLATERAL cho DamageReport (xem §2 DamageReport)
- Khi Driver muon nghi viec → tao Withdrawal Request rut 3 trieu, Admin duyet → tra ve (khong tinh phi)
- Khi DamageReport tru het coc → Driver SUSPENDED → phai nap lai du 3 trieu moi tiep tuc lam

---

### Wallet & Commission (v2.0 — chi tiet)

**Tong quan:** He thong co 2 luong tien tach biet:
- **Vi cong ty (dashboard):** noi tat ca tien chay vao tu khach.
- **Vi Driver (wallet):** noi nhan 70% sau escrow 2h.

KHONG co vi cho Customer (RefundRecord chuyen khoan thu cong).

**Cong thuc tinh tien moi don:**

```
total_quote        = base + phu thu + porter_fee  (xem §2 Cong thuc gia)
company_commission = 30% × total_quote  (configurable qua Admin panel)
driver_earning     = total_quote − company_commission  (= 70%)

VI DU: don 1.000.000d
  → company giu coc 30%        = 300.000d (= commission)
  → khach tra not 70%          = 700.000d
  → het escrow 2h               → chuyen 700.000d vao vi Driver
  → company net commission     = 300.000d
```

**Wallet schema (data model dac trung):**

```
wallet:
  id (PK)
  driver_id (FK, UNIQUE)
  balance (BigDecimal scale=0, default 0, NEVER NEGATIVE)
  deposit_balance (BigDecimal scale=0, default 0) — coc 3 trieu rieng
  created_at, updated_at

wallet_transaction (audit log moi giao dich):
  id (PK)
  wallet_id (FK)
  type (ENUM):
    EARNING       — nhan 70% sau escrow (+)
    DEPOSIT_PAID  — Driver nap coc 3 trieu (+)
    DAMAGE_DEDUCT — tru cho DamageReport (-)
    WITHDRAWAL    — Driver rut tien (-)
    DEPOSIT_REFUND — Admin tra lai coc khi Driver nghi (+)
    ADJUSTMENT    — Admin chinh sua manual (+/-)
  amount (BigDecimal scale=0)
  balance_after (BigDecimal — snapshot sau giao dich, audit)
  ref_order_id (FK, NULL neu khong lien quan don)
  ref_damage_id (FK, NULL neu khong lien quan DamageReport)
  ref_withdrawal_id (FK, NULL neu khong lien quan Withdrawal)
  note (text)
  created_at
```

**Invariants (bat buoc):**
- balance >= 0 LUON LUON (chong am)
- deposit_balance >= 0 LUON LUON
- Moi UPDATE wallet PHAI di kem 1 INSERT wallet_transaction (audit trail)
- Transaction trong DB de dam bao consistency (UPDATE wallet + INSERT wallet_transaction trong cung BEGIN/COMMIT)

**Luong EARNING (escrow 2h job):**

```
Moi 5 phut, scheduled job chay:
  SELECT order WHERE
    status = 'COMPLETED'
    AND completed_at <= NOW() - 2 hours
    AND escrow_processed = false
    AND NOT EXISTS (SELECT 1 FROM damage_report WHERE order_id = order.id AND status IN ('OPEN', 'NEGOTIATING'))

For each order:
  driver_earning = total_quote × 70%
  UPDATE wallet SET balance = balance + driver_earning WHERE driver_id = ...
  INSERT wallet_transaction (type=EARNING, amount=driver_earning, ref_order_id=...)
  UPDATE order SET escrow_processed = true
COMMIT
```

**Luong DAMAGE_DEDUCT (khi Manager bam "Xac nhan da giai quyet"):**

```
compensation = so tien da thoa thuan (Customer da AGREED)
deposit_part = MIN(compensation, wallet.deposit_balance)
wallet_part  = compensation - deposit_part

IF wallet.balance < wallet_part THEN
  // Driver khong du tien → bi SUSPENDED
  driver.status = SUSPENDED
  // Van tru toi da co the
  wallet_part = wallet.balance

UPDATE wallet SET
  deposit_balance = deposit_balance - deposit_part,
  balance = balance - wallet_part
INSERT wallet_transaction (type=DAMAGE_DEDUCT, amount=-(deposit_part+wallet_part), ref_damage_id=...)
```

**Luong WITHDRAWAL (Driver rut tien):**

```
Driver: POST /api/driver/withdrawals
  Body: { amount, bank_account_number, bank_name, account_holder_name }
  Validate: amount <= wallet.balance, amount > 0, [to do] min/max?

He thong tao withdrawal_request (status = PENDING)

Admin dashboard:
  Hien danh sach withdrawal PENDING
  Admin xem thong tin → chuyen khoan ngoai he thong → bam "Da chuyen"
  → status = PROCESSED
  UPDATE wallet SET balance = balance - amount
  INSERT wallet_transaction (type=WITHDRAWAL, amount=-amount, ref_withdrawal_id=...)

Hoac Admin bam "Tu choi":
  → status = REJECTED
  → tien khong tru, ghi note ly do
```

**Schema withdrawal_request:**

```
withdrawal_request:
  id (PK)
  driver_id (FK)
  amount (BigDecimal scale=0)
  bank_account_number
  bank_name
  account_holder_name
  status (ENUM: PENDING, PROCESSED, REJECTED)
  rejection_reason (text, NULL neu PROCESSED)
  processed_by (FK admin_user_id, NULL neu PENDING)
  processed_at (NULL neu PENDING)
  created_at, updated_at
```

**Commission config (Admin):**

```
pricing_config (1 row duy nhat, Admin edit qua UI):
  id (PK, always = 1)
  commission_rate (DECIMAL, default 0.30)
  base_price_per_km (cho moi loai xe — JSON?)
  ... cac config khac ve gia
  updated_at, updated_by

KHI tinh tien cho don MOI:
  commission_rate = SELECT commission_rate FROM pricing_config

KHI don da CREATED:
  SNAPSHOT commission_rate vao order.commission_rate_snapshot
  → Sau nay du Admin doi commission, don cu van dung rate cu
```

---

### Guest Mode (v2.0 — MOI)

**Guest = nguoi chua dang nhap. Co quyen xem 6 trang public:**

| # | Trang | URL | Noi dung |
|---|-------|-----|---------|
| 1 | Landing page | `/` | Gioi thieu dich vu, CTA dang ky |
| 2 | Bang gia tham khao | `/pricing` | Bang gia 4 loai xe + 4 phu thu |
| 3 | Form uoc tinh gia | `/quote` | Nhap diem di/den/xe → ra gia (khong luu) |
| 4 | Tro thanh tai xe | `/become-driver` | Marketing cho Driver dang ky |
| 5 | FAQ | `/faq` | Cau hoi thuong gap |
| 6 | Dieu khoan + Bao mat | `/terms`, `/privacy` | Bat buoc theo luat |

**API tach:**
- `POST /api/public/quote-estimate` — Guest goi, tra ra gia + breakdown, KHONG luu DB
- `POST /api/orders` — Customer authenticated moi goi duoc, tao Order

**Form lien he Guest:** gui den Manager (qua chat hoac email — chi tiet spec sau)

**Khi Guest bam vao tinh nang can login:** modal "Dang nhap / Dang ky de tiep tuc".

---

### Chat ho tro

> ⚠️ **CAP NHAT 2026-07 (doc truoc):** Tinh nang chat da duoc mo rong thanh **3 cap** —
> Customer↔Manager, **Manager↔Driver**, **Customer↔Driver** — tuc **Driver HIEN CO tham gia chat**,
> gan theo don (kenh ho tro chung Driver↔Manager va Customer↔Manager dung `order_id = NULL`).
> Day la **CO CHU Y** theo yeu cau leader, **lech voi mo ta cu ben duoi** (va AC-05). **KHONG phai bug,
> KHONG go bo Driver chat.** Code: package backend `chat`, migration **V36**, FE `pages/messages.html` +
> `js/chat.js`. Realtime dung WebSocket STOMP+SockJS (dung AC-05), user-destination, co polling luoi an toan.

- Kenh realtime **Khach ↔ Manager**, kenh ho tro chung, khong gan theo don cu the.
- **1 tai khoan Manager duy nhat** → khong can routing logic.
- Giao dien Manager: danh sach hoi thoai (moi khach 1 thread), bam vao tung khach de tra loi.
- Tin nhan luu DB (PostgreSQL) — lich su ben vung, mo lai thay hoi thoai cu.
- Driver KHONG tham gia chat. *(⚠️ LOI THOI 2026-07 — xem ghi chu dau muc: Driver HIEN CO chat.)*
- Ky thuat: **WebSocket STOMP + SockJS** (Spring built-in), in-memory broker. Tin nhan day WebSocket dong thoi luu DB.
- **Fallback:** Neu tuan 5 khong kip → ha xuong polling 30s. Khong de chat lam cham CORE.

---

### Email (Async)

- Gui bat dong bo: **Spring `@Async`** + dedicated thread pool.
- Loi email **KHONG** rollback giao dich chinh.
- Chap nhan mat email neu server crash trong luc gui (khong dung persistent queue).
- Trigger email: dang ky (xac thuc link), dat don thanh cong, thanh toan coc, phan cong tai xe (gui khach + driver), hoan thanh chuyen, tao tai khoan Manager (gui credentials).
- API key Gmail (App Password) KHONG commit git — bien moi truong.

---

## 3. STAKEHOLDERS

| Actor | Mo ta | Quyen / Hanh dong chinh | Trong scope? |
|-------|-------|------------------------|--------------|
| **Guest** | Khong dang nhap | Xem 6 trang public, uoc tinh gia, gui form lien he Manager | ✅ MOI |
| **Customer** | Khach dat dich vu | Dang ky/dang nhap (email verify), dat don, coc + tra 70% VNPay, theo doi don, lich su, danh gia, sua don han che, tao DamageReport trong 2h escrow | ✅ Yes |
| **Driver** | Doi tac dang ky tu do | Dang ky (4 buoc onboarding), upload giay to, coc 3 trieu, chon quan hoat dong, chap nhan/tu choi don, cap nhat trang thai chuyen, bam yeu cau thanh toan + hoan thanh, bao cao tranh chap tai cho, vi noi bo + rut tien | ✅ Yes |
| **Manager** | Dieu phoi van hanh | Duyet Driver onboarding, phan cong Driver+Xe, xu ly RefundRecord (xin STK + chuyen khoan thu cong), xu ly DamageReport va DisputeReport, quan ly Driver/Xe, chat ho tro khach + Guest | ✅ Yes |
| **Admin** | Chu / quan tri cao nhat | Xem doanh thu + bao cao commission, set role, cau hinh gia + phu thu + commission %, duyet Withdrawal cua Driver, quan ly cau hinh he thong | ✅ Yes |
| **VNPay** | Cong thanh toan | Coc 30%, thanh toan 70%, coc Driver 3 trieu — 3 luong IPN khac nhau | ✅ Sandbox |
| **OSRM** | Tinh khoang cach | OpenStreetMap distance routing | ✅ Public demo + fallback |
| **Gmail SMTP** | Gui email | Verify, thong bao | ✅ |
| **Cloudinary** | Luu anh | DamageReport photos + Driver documents (GPLX, dang ky xe, anh xe) | ✅ |

**RBAC — Ranh gioi quyen tuong minh:**

| Quyen | Admin | Manager | Driver | Customer | Guest |
|-------|-------|---------|--------|----------|-------|
| Tao tai khoan / set role | Yes | No | No | No | No |
| Cau hinh gia & phu thu | Yes | No | No | No | No |
| Xem doanh thu / bao cao | Yes | No | No | No | No |
| Duyet Withdrawal cua Driver | Yes | No | No | No | No |
| Duyet Driver onboarding | No | Yes | No | No | No |
| Phan cong Trip | Yes | Yes | No | No | No |
| Xu ly RefundRecord / DamageReport / DisputeReport | Yes | Yes | No | No | No |
| Quan ly thong tin Driver/Xe | Yes | Yes | No | No | No |
| Xem va cap nhat Trip cua minh | No | No | Yes | No | No |
| Vi noi bo + rut tien | No | No | Yes | No | No |
| Tao don / xem lich su don cua minh | No | No | No | Yes | No |
| Tao DamageReport trong 2h escrow | No | No | No | Yes | No |
| Chat ho tro | Yes | Yes | No | Yes | No |
| Xem 6 trang public + uoc tinh gia | Yes | Yes | Yes | Yes | Yes |

**Ranh gioi quyen (cap nhat):**
- Guest: KHONG can JWT, chi truy cap `/api/public/*`
- Customer: CAN JWT role=CUSTOMER, KHONG xem don nguoi khac
- Driver: CAN JWT role=DRIVER, chi xem don duoc giao cho minh, vi cua minh
- Manager: KHONG xem doanh thu, KHONG set role; CO xem moi don, xu ly Driver + DamageReport + RefundRecord
- Admin: cap cao nhat, xem doanh thu, set role, cau hinh

**Endpoint truy cap trai quyen → HTTP 403 Forbidden.**

**Nguoi ra quyet dinh cuoi khi conflict yeu cau:** [to do] (dien ten PM/leader nhom)

---

## 4. CONSTRAINTS

### Tech Stack (da chot)

- **Backend:** Spring Boot, REST API thuan (`@RestController` tra JSON)
- **Frontend:** HTML tinh + Vanilla JS (goi REST API). Giao dien dinh nghia trong file `design.md` (yeu cau cua thay — dung getdesign.md). KHONG dung React/Angular/Thymeleaf
- **Database:** PostgreSQL (cloud) — [to do] Chot provider: Supabase / Neon / Aiven / Azure
- **Authentication:** Spring Security + JWT
  - **Access token:** 15 phut (ngan de giam rui ro neu token bi steal)
  - **Refresh token:** 7 ngay (luu DB)
  - **Refresh token rotation:** moi lan dung refresh token → cap moi access + refresh; refresh cu invalidate (chong tai su dung)
  - **Logout:** xoa refresh token khoi DB (server-side invalidation)
- **External services:** OpenStreetMap + OSRM (thay Google Maps), VNPay Sandbox (3 luong IPN: coc 30%, thanh toan 70%, coc Driver 3M), Gmail SMTP (App Password), Cloudinary (anh DamageReport + Driver docs)
- **Realtime:** WebSocket STOMP + SockJS (Spring built-in), in-memory broker
- **Email async:** Spring `@Async` + dedicated thread pool
- **Deploy:** PostgreSQL va backend len cloud (yeu cau cua thay) — [to do] Chot provider

**Yeu cau quy trinh cua thay (bat buoc):**
- **Spec:** Dung Claude viet spec theo SDD playbook (file nay la buoc dau)
- **Code:** Dung Codex sinh code tu spec da duyet — KHONG vibe code tay
- **Database:** Phai deploy len cloud (khong chi localhost)
- **UI:** Giao dien dinh nghia qua file `design.md` (getdesign.md format)

**Guest endpoints (bat buoc):**
- Toan bo Guest endpoints PHAI o prefix `/api/public/*`
- Spring Security filter: prefix nay bypass JWT check
- KHONG endpoint nao khac duoc miss authentication

### Team

- **So thanh vien:** 5
- **Module CORE tien bac (vi, VNPay, rut tien):** Do thanh vien vai Admin cua nhom phu trach
- **Kinh nghiem:** [to do]

### Timeline

- **Thoi gian code:** 6 tuan
- **Ngay demo/nop cuoi:** [to do]
- **Milestone giua ky:** [to do]

### Rang buoc khac

- [to do] Yeu cau bat buoc tu thay ve so man hinh / so API / cong nghe?
- API keys (VNPay, Gmail, Cloudinary) KHONG commit git — dung bien moi truong
- Email loi KHONG rollback giao dich chinh
- OSRM rate limit: neu qua, dung fallback bang quan→quan truoc khi block request

---

## 5. ASSUMPTIONS

| # | Gia dinh | Rui ro neu sai |
|---|---------|----------------|
| A1 | Marketplace co dieu phoi: Driver tu dang ky, Manager phan cong thu cong (KHONG tu dong matching nhu Grab) | Neu thay yeu cau auto-matching → them thuat toan phuc tap |
| A2 | Bo vai tro Porter — Driver kiem nhiem boc xep khi khach co dat | Neu nhom khong dam bao Driver du suc → can them flow thue tam Porter |
| A3 | Driver coc 3 trieu khi dang ky qua VNPay — la collateral cho DamageReport | Driver moi co the nan ne; thiet ke phai cho phep nap them coc khi het |
| A4 | Commission cong ty = 30% tren total_quote (cau hinh duoc qua Admin panel) | Neu thay yeu cau commission tier (xe nho 15%, xe lon 25%) → can them logic |
| A5 | Thanh toan 100% qua VNPay (KHONG COD): 30% coc + 70% tra tai noi truoc khi Driver bam Hoan thanh | Neu khach khong tra 70% tai cho → Driver bam DisputeReport → Manager goi giai quyet |
| A6 | Escrow 2 gio sau COMPLETED de khach khieu nai. Het 2h khong khieu nai → scheduled job tu chuyen 70% vao vi Driver | Neu scheduled job bi loi → tien treo, phai co alert va manual trigger |
| A7 | Driver boi thuong 100% DamageReport (bo 50/50 cu) | Driver co the bo cuoc neu boi thuong qua cao; co quy trinh khang nghi qua Manager |
| A8 | DisputeReport (Driver bao cao tai cho) la flow MOI, khac voi DamageReport (khach bao cao) | Co the nham flow neu UI khong tach ro |
| A9 | Quan hoat dong Ha Noi noi thanh (~12 quan); Driver chon 1 hoac nhieu quan khi onboarding | Neu thay rong sang ngoai thanh → can mo rong bang quan |
| A10 | Maps API = OpenStreetMap + OSRM public demo. Fallback bang quan→quan khi loi | OSRM public co rate limit, neu nhieu user → can self-host hoac fallback |
| A11 | Anh upload (DamageReport + Driver documents) qua Cloudinary signed upload (theo Constitution AC-10) | Cloudinary free tier 25GB du; vuot phai nang cap |
| A12 | Cong ty san sang chap nhan "rui ro kinh doanh" cho cac edge case (vd VNPay sandbox loi → manual handle) | Tat ca edge case manual → phai co dashboard quan ly tot cho Manager |
| A13 | Email verification bat buoc voi Customer; Staff KHONG co email verify (Admin tao co cap email + password tam) | Neu thay muon Staff cung verify → can spec flow rieng |
| A14 | Guest co the xem 6 trang public ma khong can dang nhap | Phai dam bao endpoint public KHONG lo du lieu nhay cam (vd khong tra ve thong tin Driver cu the) |
| A15 | JWT: access 15 phut + refresh 7 ngay + rotation + hybrid storage (access localStorage, refresh httpOnly cookie) | Theo Spec #001 amend OQ-5 |
| A16 | Driver duoc tu choi don, gioi han 3 lan/ngay. Vuot → notification cho Manager → kha nang Admin can thiep | Neu Driver lien tuc tu choi → he thong can canh bao + bao cao |
| A17 | Withdrawal Driver = Admin duyet thu cong (KHONG tu dong chuyen khoan) | Admin can dashboard rieng cho Withdrawal queue |
| A18 | RefundRecord = Manager xin STK qua chat + chuyen khoan thu cong (KHONG co vi noi bo Customer) | Customer khong xem duoc so du; chi xem trang thai RefundRecord |

---

## 6. OPEN QUESTIONS — Can chot truoc khi viet spec chi tiet

| # | Cau hoi | Block feature | Uu tien |
|---|---------|---------------|---------|
| Q1 | PostgreSQL provider + noi deploy? (Supabase / Neon / Aiven / Render) | Setup moi truong | Medium |
| Q2 | Forgot Password cho Customer va Staff: spec rieng bao gio? | Auth feature #1 (deferred) | Low |
| Q3 | Staff bat buoc doi password sau lan dang nhap dau khong? | Auth feature #1 | Low (default: CO, theo OQ-3 amend) |
| Q4 | Driver cu the upload nhung giay to gi (GPLX, dang ky xe, anh xe, CCCD)? | Driver Onboarding | High — can chot truoc Luot 3 |
| Q5 | Driver onboarding mat bao nhieu buoc cu the? Co flow tu re-submit khi REJECTED khong? | Driver Onboarding | High — can chot truoc Luot 3 |
| Q6 | Commission tier: 30% cho moi loai xe hay khac nhau? | Commission feature | Low (default: 30% phang) |
| Q7 | Withdrawal Driver: gioi han min/max moi lan rut khong? Co phi rut khong? | Wallet feature | Medium |
| Q8 | Rating Driver (Feature #30) co lam trong sprint chinh hay defer? | Scope | Low |
| Q9 | DisputeReport (Driver bao cao tai cho): co flow tach voi DamageReport hay gop chung? | DamageReport feature | Medium |
| Q10 | Trong AWAITING_FINAL_PAYMENT, neu khach im lang (khong tra, khong DisputeReport): timeout bao lau? | State machine | Medium |
| Q11 | Khi Driver SUSPENDED (het coc va vi am), co cho phep nap lai coc qua VNPay de tiep tuc khong? | Driver Wallet | Medium |
| Q12 | Form lien he Guest gui o dau? (Email Manager? Vao chat? Tao chat thread anonymous?) | Guest mode | Low |
| Q13 | Quan hoat dong: Driver chon toi da bao nhieu quan? | Driver Onboarding | Low (default: khong gioi han) |

DA DONG (tu Pha 0 v1.x va decisions trong v2.0 pivot):
  - V1-Marketplace model, V2-Bo Porter, V3-Driver coc 3M, V4-Commission 30%,
    V5-100% VNPay khong COD, V6-Escrow 2h, V7-Driver 100% DamageReport,
    V8-OSRM, V9-Guest 6 trang public, V10-Driver tu choi 3 don/ngay,
    V11-Driver bam yeu cau thanh toan truoc khi hoan thanh, V12-Auto confirm
    sau IPN khong can Manager bam, V13-DisputeReport (Driver bao cao tai cho)

---

## 7. DANH SACH FEATURE — Phan loai CORE / SHELL

> 🔴 CORE = rui ro cao / business-critical → Full hoac Formal Spec
> 🟡 SHELL = phu tro → Light/Standard Spec, vibe code nhanh
> 🟢 Nice-to-have → lam neu kip

**4 VAI TRO:** Customer / Driver / Manager / Admin (BO Porter — Driver kiem boc xep). Guest la "vai tro phu" khong co account.

| # | Feature | Actor | Loai | Ghi chu |
|---|---------|-------|------|---------|
| 1 | Auth + RBAC (4 vai tro: Customer/Driver/Manager/Admin) + Guest mode | Tat ca | 🔴 CORE | Customer dang ky bang username; Staff bang email; Guest khong can login |
| 2 | Driver Onboarding (dang ky + upload giay to + coc 3 trieu) | Driver | 🔴 CORE | 4 buoc, status: PENDING_VERIFY → PENDING_DOCUMENTS → PENDING_DEPOSIT → PENDING_APPROVAL → ACTIVE |
| 3 | Manager duyet Driver Onboarding | Manager | 🔴 CORE | APPROVE → ACTIVE / REJECT → cho re-submit |
| 4 | Customer tao don chuyen nha | Customer | 🔴 CORE | Diem di/den, loai xe, do, so boc xep, phu thu, ngay gio |
| 5 | Tinh & hien thi bao gia (Public Quote + Order Quote) | Customer + Guest | 🔴 CORE | Cong thuc da bien; 2 endpoint tach (Guest /api/public/quote-estimate, Customer /api/orders) |
| 6 | Maps API (OSRM + fallback quan→quan) | System | 🔴 CORE | OpenStreetMap, fallback khi loi |
| 7 | VNPay coc 30% + IPN verify | Customer / System | 🔴 CORE | Theo Constitution HR-04, HR-15 |
| 8 | VNPay tra not 70% + IPN verify (tai cho) | Customer / System | 🔴 CORE | Trong AWAITING_FINAL_PAYMENT |
| 9 | VNPay coc Driver 3 trieu + IPN verify | Driver / System | 🔴 CORE | Trong Driver Onboarding |
| 10 | State Machine Order (8 trang thai) | System | 🔴 CORE | Formal Spec — state machine da dinh nghia o §2 |
| 11 | Manager phan cong Driver + Vehicle (filter theo quan + loai xe) | Manager | 🔴 CORE | Goi y dua tren quan hoat dong + availability |
| 12 | Driver chap nhan/tu choi don (quota 3 lan/ngay) | Driver | 🔴 CORE | 5 phut de quyet dinh, khong tra loi = tu choi |
| 13 | Driver bam "Yeu cau thanh toan" (chuyen sang AWAITING_FINAL_PAYMENT) | Driver | 🔴 CORE | Truoc khi bam Hoan thanh |
| 14 | DisputeReport (Driver bao cao tranh chap tai cho) | Driver / Manager | 🔴 CORE | Don ve IN_DISPUTE; Manager goi giai quyet |
| 15 | DamageReport (Customer bao cao trong 2h escrow) | Customer / Manager | 🔴 CORE | Driver boi thuong 100%; thu tu tru: coc → vi → SUSPENDED |
| 16 | Huy don + RefundRecord (Manager xin STK + chuyen khoan thu cong) | Customer / Manager | 🔴 CORE | KHONG co vi Customer |
| 17 | Escrow 2h + scheduled job chuyen tien vao vi Driver | System | 🔴 CORE | Auto-release neu khong khieu nai |
| 18 | Driver Wallet (vi noi bo) | Driver | 🔴 CORE | Audit log moi giao dich, khong am |
| 19 | Driver Withdrawal Request | Driver / Admin | 🔴 CORE | Admin duyet thu cong |
| 20 | Cau hinh gia + phu thu + commission % (Admin) | Admin | 🟡 SHELL | UI form don gian |
| 21 | Driver xem Trip duoc giao + lich su | Driver | 🟡 SHELL | Dashboard ca nhan |
| 22 | Customer theo doi trang thai don | Customer | 🟡 SHELL | Timeline status; thay GPS realtime |
| 23 | Customer lich su don | Customer | 🟡 SHELL | Bao gom RefundRecord lien quan |
| 24 | Customer sua don (han che, chi CONFIRMED, fields khong anh huong gia) | Customer | 🟡 SHELL | Theo OQ-V4 v1.5 cap nhat cho v2.0 |
| 25 | Quan ly Driver (Manager xem, khoa, mo) | Manager | 🟡 SHELL | KHONG xoa, chi suspend |
| 26 | Quan ly Vehicle (Driver thay anh xe / Manager xem) | Driver / Manager | 🟡 SHELL | Driver chu so huu xe |
| 27 | Email thong bao (dang ky, verify, dat don, phan cong, hoan thanh, refund...) | System | 🟡 SHELL | Spring @Async, khong rollback main flow |
| 28 | Admin Dashboard (doanh thu, commission, Driver count, Withdrawal queue) | Admin | 🟡 SHELL | Bao cao co ban |
| 29 | Chat realtime Khach ↔ Manager (kenh ho tro chung) + Guest contact form | Customer / Manager / Guest | 🟡 SHELL (uu tien thap) | WebSocket STOMP, fallback polling 30s |
| 30 | Rating + feedback Driver sau khi hoan thanh | Customer | 🟢 Nice-to-have | Trong escrow 2h |

**Backlog — Out of Scope sprint chinh (phase 2):**
GPS realtime track Driver, auto-matching Driver-Order (giong Grab), VNPay auto-refund, Maps autocomplete + ban do tuong tac, push notification mobile, commission tier theo loai xe, Driver re-cooperation flow sau khi bi suspend lau.

---

## TONG KET PHA 0 — v2.0 MARKETPLACE PIVOT

- **Mo hinh:** MARKETPLACE CO DIEU PHOI (Driver tu dang ky, Manager phan cong thu cong, commission 30%)
- **4 vai tro:** Customer / Driver / Manager / Admin (+ Guest mode)
- **30 feature:** 19 CORE + 10 SHELL + 1 nice-to-have
- **4 external services:** OpenStreetMap+OSRM + VNPay Sandbox + Gmail SMTP + Cloudinary
- **1 realtime:** WebSocket STOMP (in-memory), fallback polling

**Cum rui ro cao nhat (Formal Spec bat buoc):**
- Feature #10 State Machine (8 trang thai, 17 transitions)
- Feature #5 Bao gia + #6 Maps API (cong thuc da bien + fallback)
- Feature #7/8/9 VNPay 3 luong IPN (idempotency HR-15)
- Feature #2/3 Driver Onboarding (4 buoc + coc 3M + duyet)
- Feature #14/15 DisputeReport + DamageReport (2 nguon IN_DISPUTE)
- Feature #17/18/19 Escrow + Wallet + Withdrawal (tien bac)

**Thu tu viet spec (theo dependency):**

```
#1  Auth/RBAC + Guest mode     (nen tang phan quyen, bao gom /api/public/*)
  → #10 State Machine          (xuong song — 8 trang thai, moi spec sau deu tham chieu)
  → #2  Driver Onboarding      (entity Driver + Vehicle, coc 3M qua VNPay)
  → #3  Manager duyet Driver   (phu thuoc #2)
  → #4  Customer tao don       (dinh nghia Order entity + input fields)
  → #5  Bao gia + #6 Maps API  (can Order fields tu #4; OSRM gop vao day)
  → #7  VNPay coc 30%          (can Order + bao gia tu #4/#5)
  → #11 Phan cong              (can #4 Order + #10 State Machine + Driver FREE)
  → #12 Driver chap nhan       (can #11, quota tu choi)
  → #13 Driver yeu cau TT      (can #10 AWAITING_FINAL_PAYMENT)
  → #8  VNPay tra not 70%      (can #13)
  → #14 DisputeReport          (can #13, nguon IN_DISPUTE thu nhat)
  → #15 DamageReport           (can #10 COMPLETED + escrow, nguon IN_DISPUTE thu hai)
  → #16 Huy don + RefundRecord (can #10 + #7)
  → #17 Escrow + #18 Wallet + #19 Withdrawal  (can #8 VNPay 70% + #15 DamageReport)
  → #9  VNPay coc Driver 3M    (co the song song voi #7 — cung loai IPN)
  → SHELL features (#20..#28, theo nhu cau, song song duoc)
  → #29 Chat                   (CUOI CUNG — rui ro thoi gian cao nhat)
```

**Driver Onboarding va Wallet/Commission detail:** xem chi tiet o §2 (data model + state machine + luong giao dich).

**CANH BAO:** Chat (#29) la feature rui ro thoi gian cao nhat.
Lam SAU khi toan bo CORE da chay duoc. Neu tuan 5 thay khong kip → cat hoac ha polling.
