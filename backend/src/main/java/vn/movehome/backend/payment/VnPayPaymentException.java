package vn.movehome.backend.payment;

class VnPayPaymentException extends RuntimeException {

    private final String rspCode;

    VnPayPaymentException(String rspCode, String message) {
        super(message);
        this.rspCode = rspCode;
    }

    String rspCode() {
        return rspCode;
    }
}
