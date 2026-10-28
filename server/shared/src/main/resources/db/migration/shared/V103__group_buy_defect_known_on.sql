-- 커머스 2b-3 보강(사용자 지시 2026-10-28) — 업체 부담 반품의 「안 날부터 30일」(전자상거래법 제17조 ③).
-- 배송완료 + 3개월이 지난 업체 부담 반품은 회원이 적은 「하자를 안 날」로 받는다. 운영자는 이 날짜를 보고 거절할 수 있다.
ALTER TABLE group_buy_requests ADD COLUMN defect_known_on date;
ALTER TABLE group_buy_requests ADD CONSTRAINT ck_gbrq_defect_known CHECK (defect_known_on IS NULL OR (type = 'RETURN' AND seller_fault));
