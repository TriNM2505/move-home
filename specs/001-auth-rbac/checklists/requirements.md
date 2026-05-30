# Specification Quality Checklist: Authentication & Authorization (RBAC)

**Purpose**: Validate specification completeness and quality before proceeding to planning
**Created**: 2026-05-29
**Feature**: [spec.md](../spec.md) — Feature #1, Branch `001-auth-rbac`

## Content Quality

- [x] No implementation details (languages, frameworks, APIs)
- [x] Focused on user value and business needs
- [x] Written for non-technical stakeholders
- [x] All mandatory sections completed

## Requirement Completeness

- [x] No [NEEDS CLARIFICATION] markers remain
- [x] Requirements are testable and unambiguous
- [x] Success criteria are measurable (SC-001..SC-006)
- [x] Success criteria are technology-agnostic
- [x] All acceptance scenarios are defined (15 Given-When-Then)
- [x] Edge cases are identified (6 edge cases in spec)
- [x] Scope is clearly bounded (Out of Scope section lists 5 items)
- [x] Dependencies and assumptions identified (7 assumptions listed)

## Requirement Coverage by Flow

- [x] F1 Register Customer — FR-001..FR-008 + US1 (5 scenarios)
- [x] F2 Email Verification — FR-009..FR-014 + US2 (5 scenarios)
- [x] F3 Resend Verification — FR-013..FR-014
- [x] F4 Login — FR-015..FR-021 + US3 (5 scenarios)
- [x] F5 Refresh Token — FR-022..FR-024 + US4 (3 scenarios)
- [x] F6 Logout — FR-025 + US5 (2 scenarios)
- [x] F7 RBAC Middleware — FR-026..FR-028 + US6 (3 scenarios)
- [x] Audit Log — FR-029..FR-030

## EARS Notation Validation

- [x] >= 20 functional requirements (FR-001..FR-030 = 30 requirements) ✅
- [x] >= 30% UNWANTED pattern (WHERE clauses):
      FR-002, FR-003, FR-004, FR-005, FR-011, FR-012, FR-014, FR-016,
      FR-017, FR-018, FR-021, FR-023, FR-024, FR-026, FR-027, FR-028 = 16/30 = 53% ✅
- [x] Each flow F1..F7 has >= 2 UNWANTED requirements ✅

## Feature Readiness

- [x] All functional requirements have clear acceptance criteria
- [x] User scenarios cover 6 primary flows (US1..US6)
- [x] Feature meets measurable outcomes defined in Success Criteria
- [x] No implementation details leak into specification
- [x] Constitution Compliance Mapping covers 11 rules
- [x] Error Handling Matrix complete (15 scenarios)
- [x] Data Model defined (4 tables + indexes)
- [x] Non-Functional Requirements defined (NFR-01..NFR-06)
- [x] Open Questions documented (5 deferred items)

## Constitution Check (Self-Check per constitution v1.1.0)

- [x] HR-01 — No secrets in spec ✅
- [x] HR-02 — BCrypt explicitly required (FR-007, NFR-01) ✅
- [x] HR-10 — HTTP 403 for role violations (FR-026..FR-028, Error Matrix) ✅
- [x] HR-12 — Staff cannot self-register (FR-001, Out of Scope #3) ✅
- [x] HR-13 — Audit log mandated (FR-029..FR-030, 9 event types) ✅
- [x] HR-16 — Rate limit + lockout (FR-008, FR-014, FR-020, FR-021) ✅
- [x] AC-03 — JWT 15min/7d/rotation (FR-019, FR-022, FR-023, FR-025) ✅
- [x] AC-07 — TIMESTAMPTZ + UTC (Data Model, NFR-04) ✅
- [x] AC-09 — Soft delete on `user` table (Data Model, partial indexes) ✅
- [x] AC-11 — CORS noted in Compliance Mapping ✅
- [x] AC-12 — Flyway migration referenced in Data Model ✅

## Notes

All items pass. No spec updates required before proceeding to `/speckit-plan`.

**EARS unwanted coverage**: 16 out of 30 requirements (53%) use UNWANTED pattern — exceeds
the 30% minimum by a significant margin. This reflects the security-heavy nature of Auth.

**Deferred items** (tracked in Open Questions): forgot-password, forced-password-change,
token storage client-side recommendation.
