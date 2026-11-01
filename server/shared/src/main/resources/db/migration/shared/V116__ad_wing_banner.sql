-- 날개 배너 WING_BANNER(본문 바깥 좌우 세로 160×600 — 4:15) 상품을 더한다. docs/features/ad-contract.md 「공개 노출 목업」 확정 결정(사용자 2026-11-01).
-- 요금표: 하루 30,000원 · 동시 한도 4(양쪽 두 자리를 비중대로 돌린다) · 최대 30일 · 60일 전부터 신청 · 받기 켬. V112 는 고치지 않는다(적용됨).
-- 상품 이름을 묶는 CHECK 는 biz_price_rules.ck_bpr_product 하나뿐이다(biz_application_items.product 는 이 표의 외래키). 소재 비율 검사는 서버에 없다(다른 배너와 같다).
ALTER TABLE biz_price_rules DROP CONSTRAINT ck_bpr_product;
ALTER TABLE biz_price_rules ADD CONSTRAINT ck_bpr_product CHECK (product IN ('WIDE_BANNER', 'STRIP_BANNER', 'SQUARE_BANNER', 'SMALL_BANNER', 'WING_BANNER', 'TEXT_LINE',
                                                                         'HOTDEAL_TOP', 'HOTDEAL_CATEGORY', 'HOTDEAL_POST'));

INSERT INTO biz_price_rules (product, unit, unit_price, capacity, max_days, max_lead_days, accepting) VALUES
    ('WING_BANNER', 'DAY', 30000, 4, 30, 60, true);
