-- 반 폭 배너 HALF_BANNER(홈·가젯 페이지 본문 반 폭 칸에 맞춘 가로 띠 600×100 — 6:1) 상품을 더한다. docs/features/ad-contract.md 「반 폭 배너」(사용자 확정 2026-11-15).
--   · 요금표: 띠 배너와 같은 설치 기본값 — 하루 30,000원 · 동시 한도 3 · 최대 14일 · 60일 전부터 신청 · 받기 켬(사업자 신청 가능). 바꾸기는 관리자 가격 기간표.
--   · 상품 이름을 묶는 CHECK 는 biz_price_rules.ck_bpr_product 하나뿐이다(biz_application_items.product·biz_price_schedules.product 는 이 표의 외래키). V112·V116 은 고치지 않는다(적용됨).
--   · 애드센스: 광고 노출 자리 설정 한 행에 반 폭 배너 가젯 단위 ID 칸(숫자 10자리, 없으면 그 자리는 애드센스를 쓰지 않는다). V176 의 ck_aps_adsense_units 는 그대로 두고 칸 제약을 따로 단다.
--   · 쓰는 자리는 홈·가젯 페이지 광고 가젯(AD, config.adType)뿐 — 글 보기 인라인(ck_aps_inline_type)·게시판 목록에는 넣지 않는다.
ALTER TABLE biz_price_rules DROP CONSTRAINT ck_bpr_product;
ALTER TABLE biz_price_rules ADD CONSTRAINT ck_bpr_product CHECK (product IN ('WIDE_BANNER', 'STRIP_BANNER', 'SQUARE_BANNER', 'SMALL_BANNER', 'HALF_BANNER', 'WING_BANNER',
                                                                         'TEXT_LINE', 'HOTDEAL_TOP', 'HOTDEAL_CATEGORY', 'HOTDEAL_POST'));

INSERT INTO biz_price_rules (product, unit, unit_price, capacity, max_days, max_lead_days, accepting) VALUES
    ('HALF_BANNER', 'DAY', 30000, 3, 14, 60, true);

ALTER TABLE ad_placement_settings
    ADD COLUMN adsense_half_banner varchar(10),
    ADD CONSTRAINT ck_aps_adsense_half_banner CHECK (adsense_half_banner IS NULL OR adsense_half_banner ~ '^[0-9]{10}$');
