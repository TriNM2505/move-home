# ADR-003: Tiền dùng BigDecimal scale=0, VND nguyên đồng

- **Status:** Accepted
- **Nguồn:** constitution Decision **D6** · PROJECT_KNOWLEDGE §1.6 · **AC-08**, **HR-18**, **AC-13**

## Context
Hệ thống là money-critical: cọc, thanh toán 70%, commission, bồi thường, ví Driver, rút tiền. Sai
số tiền = sai báo cáo doanh thu + rủi ro pháp lý (Driver kiện). VND không có đơn vị nhỏ hơn đồng.

## Decision
- Mọi field tiền: `java.math.BigDecimal` **scale=0**; DB `NUMERIC(15,0)`.
- **KHÔNG** `double`/`float` ở bất kỳ đâu (DTO, service, test).
- JSON serialize tiền = integer (`12500000`).
- Chia bồi thường 50/50: company `CEILING`, driver `FLOOR` (tổng = gốc).
- Wallet `balance` **≥ 0** (DB CHECK + service validate); mọi UPDATE wallet đi kèm 1 INSERT
  `transaction` trong cùng DB transaction (audit trail).

## Alternatives considered
1. **`double`/`float`:** ❌ IEEE 754 rounding error — không chính xác cho tiền.
2. **BigDecimal scale=2 (có xu):** thừa — VND không có xu, scale=0 đơn giản hơn.
3. **Lưu long (số nguyên đồng):** khả thi nhưng BigDecimal an toàn hơn khi chia/làm tròn.

## Consequences (Trade-off)
- ➕ Chính xác tuyệt đối, đúng chuẩn tài chính, audit được (balance_after snapshot).
- ➖ Verbose hơn primitive (phải `.multiply()`, `.setScale()` thay vì `*`).
- ➖ Dev phải nhớ RoundingMode đúng chỗ (đã quy định CEILING/FLOOR trong AC-08).
