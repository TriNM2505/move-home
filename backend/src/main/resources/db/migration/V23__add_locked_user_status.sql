-- Sprint 5: app_user.status already exists because it also carries the current
-- onboarding state. Extend the existing constraint with the account lock state.
-- Driver approval remains stored independently in driver_profile.approved_at.
ALTER TABLE app_user
    DROP CONSTRAINT IF EXISTS app_user_status_check;

ALTER TABLE app_user
    ADD CONSTRAINT app_user_status_check
    CHECK (status IN (
        'ACTIVE',
        'LOCKED',
        'PENDING_VERIFY',
        'PENDING_DOCUMENTS',
        'PENDING_DEPOSIT',
        'PENDING_APPROVAL',
        'SUSPENDED',
        'REJECTED'
    ));

COMMENT ON COLUMN app_user.status IS
    'Trang thai tai khoan/onboarding. LOCKED la khoa boi Admin; phe duyet Driver dung driver_profile.approved_at.';
