package vn.movehome.backend.service;

public final class NotificationType {

    public static final String ORDER_ACCEPTED = "ORDER_ACCEPTED";
    public static final String ORDER_IN_PROGRESS = "ORDER_IN_PROGRESS";
    public static final String ORDER_COMPLETED = "ORDER_COMPLETED";
    public static final String ORDER_CANCELLED = "ORDER_CANCELLED";
    public static final String DRIVER_APPROVED = "DRIVER_APPROVED";
    public static final String DRIVER_REJECTED = "DRIVER_REJECTED";
    public static final String WITHDRAWAL_PROCESSED = "WITHDRAWAL_PROCESSED";
    public static final String WITHDRAWAL_REJECTED = "WITHDRAWAL_REJECTED";

    private NotificationType() {
    }
}
