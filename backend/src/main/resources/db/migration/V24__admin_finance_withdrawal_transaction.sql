-- =============================================================================
-- V24: Admin finance M2 (withdrawal + transaction + commission API support).
-- Khong tao lai bang da co; chi ALTER ADD COLUMN/constraint/index.
-- Guard transaction.type truoc khi thay CHECK de them WITHDRAWAL.
-- =============================================================================

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM transaction
        WHERE type NOT IN (
            'DEPOSIT_TOP_UP',
            'DEPOSIT_REFUND',
            'ORDER_PAYMENT',
            'WALLET_TOP_UP',
            'DRIVER_EARNING',
            'PLATFORM_FEE',
            'DAMAGE_DEDUCTION',
            'REFUND',
            'WITHDRAWAL'
        )
    ) THEN
        RAISE EXCEPTION 'Cannot update transaction type CHECK: transaction contains unsupported type values';
    END IF;
END $$;

ALTER TABLE transaction
    ADD COLUMN IF NOT EXISTS related_withdrawal_id UUID
        REFERENCES withdrawal_request(id),
    ADD COLUMN IF NOT EXISTS balance_after NUMERIC(15,0);

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
        'REFUND',
        'WITHDRAWAL'
    ));

CREATE UNIQUE INDEX IF NOT EXISTS uq_transaction_withdrawal
    ON transaction (related_withdrawal_id)
    WHERE type = 'WITHDRAWAL'
      AND related_withdrawal_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_transaction_created_id
    ON transaction (created_at DESC, id DESC);

CREATE INDEX IF NOT EXISTS idx_transaction_type_created
    ON transaction (type, created_at DESC, id DESC);

CREATE INDEX IF NOT EXISTS idx_transaction_user_created
    ON transaction (user_id, created_at DESC, id DESC);

CREATE INDEX IF NOT EXISTS idx_transaction_withdrawal
    ON transaction (related_withdrawal_id, created_at DESC, id DESC)
    WHERE related_withdrawal_id IS NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uq_withdrawal_bank_txn_ref
    ON withdrawal_request (bank_txn_ref)
    WHERE bank_txn_ref IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_withdrawal_pending_fifo_v2
    ON withdrawal_request (requested_at ASC, id ASC)
    WHERE status = 'PENDING';

CREATE INDEX IF NOT EXISTS idx_withdrawal_history_processed
    ON withdrawal_request (processed_at DESC, id DESC)
    WHERE status IN ('PROCESSED', 'REJECTED', 'CANCELLED');

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'ck_withdrawal_terminal_fields'
          AND conrelid = 'withdrawal_request'::regclass
    ) THEN
        ALTER TABLE withdrawal_request
            ADD CONSTRAINT ck_withdrawal_terminal_fields
            CHECK (
                (
                    status = 'PENDING'
                    AND processed_by IS NULL
                    AND processed_at IS NULL
                    AND bank_txn_ref IS NULL
                    AND rejection_reason IS NULL
                )
                OR
                (
                    status = 'PROCESSED'
                    AND processed_by IS NOT NULL
                    AND processed_at IS NOT NULL
                    AND bank_txn_ref IS NOT NULL
                    AND rejection_reason IS NULL
                )
                OR
                (
                    status = 'REJECTED'
                    AND processed_by IS NOT NULL
                    AND processed_at IS NOT NULL
                    AND rejection_reason IS NOT NULL
                    AND bank_txn_ref IS NULL
                )
                OR
                (
                    status = 'CANCELLED'
                    AND cancelled_at IS NOT NULL
                    AND bank_txn_ref IS NULL
                )
            ) NOT VALID;
    END IF;
END $$;

COMMENT ON COLUMN transaction.related_withdrawal_id IS
    'Withdrawal request lien quan den transaction WITHDRAWAL.';
COMMENT ON COLUMN transaction.balance_after IS
    'So du vi Driver ngay sau giao dich co tac dong den vi.';
