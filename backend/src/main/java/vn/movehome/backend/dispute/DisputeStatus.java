package vn.movehome.backend.dispute;

public final class DisputeStatus {

    public static final String OPEN = "OPEN";
    public static final String INVESTIGATING = "INVESTIGATING";
    public static final String RESOLVED_REFUND = "RESOLVED_REFUND";
    public static final String RESOLVED_DEDUCT = "RESOLVED_DEDUCT";
    public static final String CLOSED_NO_FAULT = "CLOSED_NO_FAULT";

    private DisputeStatus() {
    }
}
