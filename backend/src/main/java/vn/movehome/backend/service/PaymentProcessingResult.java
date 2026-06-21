package vn.movehome.backend.service;

import vn.movehome.backend.entity.Transaction;

import java.util.Objects;

/**
 * Ket qua xu ly mot callback thanh toan VNPay.
 * Ban ghi transaction luon la ban ghi da duoc luu lan dau cho vnpayTxnRef.
 */
public record PaymentProcessingResult(Status status, Transaction transaction) {

    public PaymentProcessingResult {
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(transaction, "transaction must not be null");
    }

    public boolean processedNow() {
        return status == Status.PROCESSED;
    }

    public enum Status {
        PROCESSED,
        ALREADY_PROCESSED
    }
}
