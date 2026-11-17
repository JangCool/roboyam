-- 체험단 광고비 선결제 기본(docs/features/trial-group-contract.md 「유료 결제 연결」, 2026-11-17 사용자 결정 — 「선결제 차액 환불 없어야지 … 선결제가 우선이되어야지」)
--
-- 모집마다 결제 방식(payment_timing)을 둔다.
--   · PREPAID(기본): 모집 인원으로 견적(인원당 단가 × 모집 인원 · 건당 단가, 운영자 대신 발송이면 + 1명당 발송비 × 모집 인원)을 저장 때 굳히고 광고 심사 → 결제가 끝나야
--     모집을 연다(공개 409 TRIAL_PREPAY_REQUIRED — 앱). 덜 모이거나 덜 뽑혀도·뽑힌 줄을 취소해도 금액 그대로(차액 환불 없음). 결제 뒤에는 금액에 닿는 칸을 바꿀 수 없다.
--     공개 전 결제 기한 만료·반려·취소면 모집은 초안 그대로 두고 「결제 다시 요청」(새 광고 신청 건)을 할 수 있다.
--   · POSTPAID: V242 그대로(뽑기 뒤 견적 · 결제 전 입고·발송 409).
-- 금액(amount)은 PREPAID 면 저장 때부터 있다(뽑기 전에도) — ck_trials_selected 를 그에 맞게 다시 만든다(POSTPAID 는 예전처럼 뽑힌 수와 함께).
--
-- 기존 행: 운영 DB 에 체험단이 없다(V241·V242 가 같은 날 — 로컬·개발 데이터뿐)라 모두 PREPAID 기본을 그대로 받는다. 금액이 아직 없는 초안은 결제 상태
-- 「견적 요청 전」(AWAITING_QUOTE_REQUEST)으로 보이고 저장하거나 「결제 요청」을 누르면 견적을 굳힌다. 이미 연(모집 중·뽑는 중) 금액 없는 모집은 뽑기 때 예전처럼(후불) 견적을 굳힌다.
-- 이미 뽑은 모집은 금액·신청 건 상태 그대로(결제가 끝나야 입고·발송인 것도 그대로).
ALTER TABLE trials ADD COLUMN payment_timing varchar(10) NOT NULL DEFAULT 'PREPAID';
ALTER TABLE trials ADD CONSTRAINT ck_trials_payment_timing CHECK (payment_timing IN ('PREPAID', 'POSTPAID'));
ALTER TABLE trials DROP CONSTRAINT ck_trials_selected;
ALTER TABLE trials ADD CONSTRAINT ck_trials_selected CHECK ((amount IS NULL OR amount >= 0) AND (selected_count IS NULL OR amount IS NOT NULL)
                                                          AND (payment_timing = 'PREPAID' OR (selected_count IS NULL) = (amount IS NULL)));

-- 모집 ↔ 광고 신청 건 이력. trials.biz_application_id 는 지금 결제 건이고, 선결제 「결제 다시 요청」이 새 건을 만들면 바뀐다 — 예전 건(만료·반려·취소)도 관리자·사업자 상세에서
-- 어느 모집의 건인지 보이도록 모든 건을 여기 남긴다(한 건은 한 모집에만). 줄은 지우지 않는다.
CREATE TABLE trial_biz_applications (
    biz_application_id  bigint       PRIMARY KEY REFERENCES biz_applications (id),
    trial_id            bigint       NOT NULL REFERENCES trials (id),
    created_at          timestamptz  NOT NULL
);
CREATE INDEX ix_trial_biz_applications_trial ON trial_biz_applications (trial_id, created_at);
INSERT INTO trial_biz_applications (biz_application_id, trial_id, created_at)
SELECT biz_application_id, id, created_at FROM trials;
