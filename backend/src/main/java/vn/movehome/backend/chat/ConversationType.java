package vn.movehome.backend.chat;

/**
 * Loai hoi thoai chat — 3 cap theo yeu cau nghiep vu (gan theo don, tru CUSTOMER_MANAGER ho tro chung).
 * Luu duoi dang String + CHECK constraint o DB (Constitution AC-14, khong dung PostgreSQL ENUM).
 */
public final class ConversationType {

    // Khach <-> Quan ly: kenh ho tro chung, order_id = NULL
    public static final String CUSTOMER_MANAGER = "CUSTOMER_MANAGER";

    // Quan ly <-> Tai xe: gan theo 1 don cu the
    public static final String MANAGER_DRIVER = "MANAGER_DRIVER";

    // Khach <-> Tai xe: gan theo 1 don cu the
    public static final String CUSTOMER_DRIVER = "CUSTOMER_DRIVER";

    private ConversationType() {
    }

    public static boolean isValid(String type) {
        return CUSTOMER_MANAGER.equals(type)
                || MANAGER_DRIVER.equals(type)
                || CUSTOMER_DRIVER.equals(type);
    }
}
