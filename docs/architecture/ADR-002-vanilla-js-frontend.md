# ADR-002: Frontend Vanilla JS (không framework)

- **Status:** Accepted
- **Nguồn:** PROJECT_KNOWLEDGE §1.3 · constitution **AC-01**

## Context
Frontend cần build nhanh ~80 màn hình trong 6 tuần với team 5 người (4 junior). Thầy yêu cầu stack
đơn giản, và curriculum FPT tập trung Java enterprise (không phải JS frontend nặng).

## Decision
Frontend = **HTML tĩnh + Vanilla JS + Vanilla CSS**, gọi REST API. Backend `@RestController` trả
JSON thuần. **KHÔNG** React/Vue/Angular/Svelte/Thymeleaf. Chart.js 4.x qua CDN.

## Alternatives considered
| Option | Time-to-MVP | Learn curve | Verdict |
|--------|-------------|-------------|---------|
| **Vanilla JS** | 1 ngày/page | Thấp | ✅ Chọn |
| React + Vite | 3 ngày setup + 2 ngày/page | Cao | ❌ |
| Vue 3 | 2 ngày setup + 1.5 ngày/page | Vừa | ❌ |
| Next.js | 1 tuần setup | Rất cao | ❌ Overkill |

## Consequences (Trade-off)
- ➕ Không build step, refresh ngay, 0 KB framework, debug đơn giản, team học DOM/fetch nền tảng.
- ➕ Tránh `node_modules` hell với team chưa quen npm.
- ➖ Không component reuse cao, state management thủ công, code lặp giữa các page.
- ➖ Khó migrate sang framework sau (nếu cần) — nhưng out of scope đồ án.
