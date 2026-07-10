package vn.movehome.backend.service;

public final class NotificationType {

    public static final String ORDER_ACCEPTED = "ORDER_ACCEPTED";
    public static final String ORDER_CONFIRMED = "ORDER_CONFIRMED";
    public static final String ORDER_ASSIGNED = "ORDER_ASSIGNED";
    public static final String ORDER_IN_PROGRESS = "ORDER_IN_PROGRESS";
    public static final String ORDER_COMPLETED = "ORDER_COMPLETED";
    public static final String ORDER_CANCELLED = "ORDER_CANCELLED";
    public static final String ORDER_IN_DISPUTE = "ORDER_IN_DISPUTE";
    public static final String DRIVER_APPROVED = "DRIVER_APPROVED";
    public static final String DRIVER_REJECTED = "DRIVER_REJECTED";
    public static final String WITHDRAWAL_REQUESTED = "WITHDRAWAL_REQUESTED";
    public static final String WITHDRAWAL_PROCESSED = "WITHDRAWAL_PROCESSED";
    public static final String WITHDRAWAL_REJECTED = "WITHDRAWAL_REJECTED";
    public static final String DISPUTE_OPENED = "DISPUTE_OPENED";
    public static final String DISPUTE_RESOLVED = "DISPUTE_RESOLVED";
    public static final String DISPUTE_REJECTED = "DISPUTE_REJECTED";
    public static final String PENALTY_WALLET_DEDUCTED = "PENALTY_WALLET_DEDUCTED";
    public static final String PENALTY_TOP_UP_REQUIRED = "PENALTY_TOP_UP_REQUIRED";
    public static final String PENALTY_ACCOUNT_LOCKED = "PENALTY_ACCOUNT_LOCKED";
    public static final String PENALTY_SETTLED = "PENALTY_SETTLED";
    public static final String CHAT_MESSAGE = "CHAT_MESSAGE";
    public static final String DRIVER_ARRIVED = "DRIVER_ARRIVED";

    private NotificationType() {
    }
}
