-- 사업자 핫딜 신청을 닫는다(사용자 2026-11-01 「닫아 둬」) — 예약 뒤 실제 게시 고리(BizHotdealPosting.onScheduled)와 핫딜 노출 자리의 공개 화면이 아직 없어,
-- 돈을 받고 서비스를 못 주는 길을 막는다. 게시 글 HOTDEAL_POST · 노출 자리 HOTDEAL_TOP·HOTDEAL_CATEGORY 의 받기를 끈다(새 신청은 409 BIZ_PRODUCT_CLOSED).
-- 이미 들어온 신청 건은 건드리지 않는다. 게시 기능을 구현할 때 새 마이그레이션으로 다시 연다(V112·V116 은 고치지 않는다 — 적용됨).
UPDATE biz_price_rules SET accepting = false, updated_at = now() WHERE product IN ('HOTDEAL_POST', 'HOTDEAL_TOP', 'HOTDEAL_CATEGORY');
