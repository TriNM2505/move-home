package vn.movehome.backend.entity;

/**
 * Trang thai vong doi don hang chuyen nha (service_order).
 * Spec #028 Admin Dashboard tham chieu cac gia tri nay trong KPI queries.
 * Constitution HR-05: transition khong nam trong bang hop le → HTTP 409.
 *
 * Luu y: V21 cho phep 11 status, nhung AWAITING_FINAL_PAYMENT dang vuot
 * do dai cot service_order.status VARCHAR(20) nen chua dua vao runtime enum.
 */
public enum OrderStatus {

    /**
     * Don vua duoc tao, chua co Driver nao nhan hoac xac nhan.
     * Truong thai dau tien sau khi Customer dat don.
     */
    PENDING,

    /**
     * Don da tao va dang cho thanh toan coc qua VNPay.
     */
    PENDING_PAYMENT,

    /**
     * Don da thanh toan coc thanh cong, cho phan cong tai xe.
     */
    CONFIRMED,

    /**
     * Don da duoc phan cong tai xe.
     */
    ASSIGNED,

    /**
     * Driver da nhan va chap nhan don; dang tren duong den diem don hoac chuan bi.
     * Spec day du: tach thanh CONFIRMED (da coc) + ASSIGNED (da phan Driver).
     */
    ACCEPTED,

    /**
     * Chuyen di dang dien ra — Driver da den va bat dau van chuyen do.
     */
    IN_PROGRESS,

    /**
     * Don hoan thanh thanh cong. Customer da xac nhan, tien da duoc giai ngan.
     * Spec day du: co buoc AWAITING_FINAL_PAYMENT (cho 70%) truoc COMPLETED.
     */
    COMPLETED,

    /**
     * Don bi huy boi Customer, Driver, Manager, hoac he thong (timeout 15 phut).
     * Metadata: cancelled_by (CUSTOMER|DRIVER|COMPANY|SYSTEM) + cancellation_reason.
     */
    CANCELLED,

    /**
     * Don dang trong trang thai tranh chap — Customer bao cao hu hong/mat mat
     * hoac Driver bao cao tai cho khi khach khong chiu tra tien.
     * Spec day du / Spec #028 tham chieu: 'IN_DISPUTE'.
     * Chi Manager/Admin moi co quyen chuyen tu DISPUTED → COMPLETED (Constitution HR-07).
     */
    DISPUTED,

    /**
     * Ten canonical moi cho trang thai tranh chap; DISPUTED la alias legacy.
     */
    IN_DISPUTE
}
