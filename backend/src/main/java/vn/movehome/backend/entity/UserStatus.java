package vn.movehome.backend.entity;

/**
 * Trang thai tai khoan nguoi dung trong he thong Move_home v2.0.
 * Spec #001 Data Model, Constitution AC-09 (soft delete), CONTEXT.md §2 Driver Onboarding.
 *
 * Mot so trang thai chi ap dung cho DRIVER (PENDING_DOCUMENTS, PENDING_DEPOSIT,
 * PENDING_APPROVAL, REJECTED). Customer chi co: PENDING_VERIFY → ACTIVE.
 *
 * QUAN TRONG: Gia tri enum.name() duoc luu truc tiep vao DB cot "status" (EnumType.STRING).
 * KHONG doi ten enum sau khi da co data — phai tao Flyway migration ALTER TABLE.
 */
public enum UserStatus {

    /**
     * Tai khoan dang hoat dong binh thuong.
     * Customer: da xac thuc email.
     * Driver: da duoc Manager APPROVE sau ky gui du giay to va dong coc 3 trieu.
     * Manager/Admin: vua duoc Admin tao (da ACTIVE ngay, must_change_password=true).
     */
    ACTIVE,

    /**
     * Dang cho xac thuc email. Ap dung cho ca Customer va Driver vua dang ky.
     * Chua the dang nhap thanh cong — he thong tra HTTP 403 EMAIL_NOT_VERIFIED (FR-020, FR-048).
     * Chuyen sang ACTIVE (Customer) hoac PENDING_DOCUMENTS (Driver) sau khi click link verify.
     */
    PENDING_VERIFY,

    /**
     * Driver da xac thuc email, chua upload du 3 loai giay to bat buoc:
     * GPLX (2 anh) + Dang ky xe (1 anh + metadata bien so) + Anh xe (3 anh).
     * CHI AP DUNG CHO DRIVER (Buoc 2 trong 4 buoc onboarding).
     * Chuyen sang PENDING_DEPOSIT sau khi upload du giay to (FR-056).
     */
    PENDING_DOCUMENTS,

    /**
     * Driver da upload du giay to, chua dong coc 3.000.000 VND qua VNPay.
     * Coc nay la COLLATERAL cho DamageReport (CONTEXT §2).
     * CHI AP DUNG CHO DRIVER (Buoc 3 trong 4 buoc onboarding).
     * Chuyen sang PENDING_APPROVAL sau khi IPN VNPay xac nhan coc (FR-061).
     */
    PENDING_DEPOSIT,

    /**
     * Driver da dong coc, dang cho Manager xem xet va duyet ho so.
     * Manager co the APPROVE (→ ACTIVE) hoac REJECT voi ly do (→ REJECTED).
     * CHI AP DUNG CHO DRIVER (Buoc 4 trong 4 buoc onboarding).
     * Đăng nhập vẫn được cấp token; workflow tài xế trả HTTP 403 ONBOARDING_PENDING_REVIEW.
     */
    PENDING_APPROVAL,

    /**
     * Tai khoan bi khoa tam thoi. Ap dung chu yeu cho DRIVER khi:
     * - DamageReport tru het ca coc 3 trieu VA vi Driver (CONTEXT §2 DamageReport).
     * Driver phai nap lai coc day du 3 trieu moi tiep tuc nhan don.
     * Admin cung co the SUSPEND bat ky tai khoan nao.
     */
    SUSPENDED,

    /**
     * Driver bi Manager tu choi sau khi xem xet ho so (FR-067).
     * Driver van dang nhap duoc nhung nhan thong bao bi tu choi kem ly do.
     * Driver co quyen re-submit giay to moi (status chuyen lai PENDING_DOCUMENTS).
     * CHI AP DUNG CHO DRIVER.
     */
    REJECTED
}
