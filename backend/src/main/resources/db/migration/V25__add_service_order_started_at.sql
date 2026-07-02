-- V25: Add start timestamp for real order completion-time reporting.
-- This migration is intentionally out-of-order relative to V26/V27.

ALTER TABLE service_order
    ADD COLUMN IF NOT EXISTS started_at TIMESTAMPTZ;

COMMENT ON COLUMN service_order.started_at IS 'Time when the order transitions to IN_PROGRESS.';
