package vn.movehome.backend.entity;

/**
 * 4 vai tro trong he thong Move_home v2.0.
 * Tuong ung voi bang RBAC trong CONTEXT.md §3 va Constitution HR-10.
 * TUYET DOI KHONG them vai tro Porter — da bo trong v2.0, Driver kiem nhiem boc xep.
 */
public enum UserRole {

    /** Khach hang dat dich vu chuyen nha. Tu dang ky qua /api/auth/register/customer. */
    CUSTOMER,

    /**
     * Tai xe doi tac tu dang ky tu ngoai. So huu xe rieng.
     * Phai di qua 4 buoc onboarding (PENDING_VERIFY → ACTIVE) truoc khi nhan don.
     */
    DRIVER,

    /**
     * Nhan vien dieu phoi van hanh cong ty.
     * Quyen: phan cong Driver, duyet Driver Onboarding, xu ly RefundRecord/DamageReport/DisputeReport.
     * Khong co quyen xem doanh thu, khong duoc tu dang ky — Admin tao tai khoan.
     */
    MANAGER,

    /**
     * Quan tri cao nhat cua he thong.
     * Quyen: tao tai khoan Staff, cau hinh gia/phu thu/commission, duyet Withdrawal cua Driver,
     * xem bao cao doanh thu. Admin cung do Admin tao (bootstrap ban dau qua DB seed).
     */
    ADMIN
}
