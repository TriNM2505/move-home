-- =============================================================================
-- V21: Mo rong constraint cho VNPay order payment + customer wallet top-up.
-- AC-08: money van NUMERIC(15,0). AC-13: transaction append-only.
-- =============================================================================

ALTER TABLE service_order
    DROP CONSTRAINT IF EXISTS service_order_status_check;

ALTER TABLE service_order
    DROP CONSTRAINT IF EXISTS ck_service_order_status;

ALTER TABLE service_order
    ADD CONSTRAINT ck_service_order_status
    CHECK (status IN (
        'PENDING',
        'PENDING_PAYMENT',
        'CONFIRMED',
        'ASSIGNED',
        'ACCEPTED',
        'IN_PROGRESS',
        'AWAITING_FINAL_PAYMENT',
        'COMPLETED',
        'DISPUTED',
        'IN_DISPUTE',
        'CANCELLED'
    ));

ALTER TABLE transaction
    DROP CONSTRAINT IF EXISTS transaction_type_check;

ALTER TABLE transaction
    DROP CONSTRAINT IF EXISTS ck_transaction_type;

ALTER TABLE transaction
    ADD CONSTRAINT ck_transaction_type
    CHECK (type IN (
        'DEPOSIT_TOP_UP',
        'DEPOSIT_REFUND',
        'ORDER_PAYMENT',
        'WALLET_TOP_UP',
        'DRIVER_EARNING',
        'PLATFORM_FEE',
        'DAMAGE_DEDUCTION',
        'REFUND'
    ));
