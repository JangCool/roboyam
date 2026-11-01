-- 말머리 상단 노출(HOTDEAL_CATEGORY) 신청을 닫는다(사용자 결정 2026-11-02) — 새 신청은 409 BIZ_PRODUCT_CLOSED, 신청 화면은 「지금은 받지 않아요」.
-- 게시 글 HOTDEAL_POST · 게시판 상단 HOTDEAL_TOP 은 그대로 받는다. 이미 들어온 신청 건·노출 판정 코드는 건드리지 않는다.
-- 다시 열 때는 새 마이그레이션으로 accepting=true + user-web useHotdealAds 를 말머리 칩 → HOTDEAL_CATEGORY 로 되돌린다(V121·V123 은 고치지 않는다 — 적용됨).
UPDATE biz_price_rules SET accepting = false, updated_at = now() WHERE product = 'HOTDEAL_CATEGORY';
