-- 사업자 신청·수정의 국세청 진위확인 하루 횟수(docs/features/business-contract.md 「국세청 진위확인 · 공공데이터 설정」, 사용자 결정 2026-11-02)
--   불일치는 저장하지 않으므로, 같은 회원이 남의 번호에 상호·대표자명·개업일을 바꿔 가며 국세청을 불러 맞는지 알아내지 못하게 회원 × 한국 날짜로 시도 수를 센다.
--   신청·수정 합산. 국세청을 실제로 부르게 되는 시도만 센다(결과와 무관). 운영자 [다시 조회]는 세지 않는다.
--   업무 트랜잭션과 따로 먼저 커밋한다(업무가 실패해도 시도 수는 남는다) — INSERT … ON CONFLICT … DO UPDATE … RETURNING count 한 문장.
--   한도(roboyam.business.nts.daily-validate-limit, 기본 5)에 닿은 줄은 더 올리지 않는다. 지난 날짜 줄은 DB 배치 business-nts-count-cleanup 이 지운다.
CREATE TABLE business_nts_daily_counts (
    user_id  bigint NOT NULL,
    day      date   NOT NULL,
    count    int    NOT NULL,
    PRIMARY KEY (user_id, day),
    CONSTRAINT ck_bndc_count CHECK (count > 0)
);
CREATE INDEX ix_business_nts_daily_counts_day ON business_nts_daily_counts (day);
