-- 보관 기간 파기 두 번째 묶음(2026-11-24 메인 세션 결정 — 사용자 「다 처리해」).
--   ① 사업자 신원 익명화: 광고·핫딜 신청 건(biz_applications — 거래 기록)이 가리켜 지울 수 없는 반려·탈퇴 신원은 R__batch_45 가 줄을 남기고 식별 칸을 비운다.
--      상호는 NOT NULL 이라 「삭제된 사업자」, 등록번호 끝 4자리·개업일·사유 글은 NULL. 익명화한 시각 = anonymized_at. 신청 건의 거래 근거 스냅샷(상호·끝 4자리)도 같은 때 같은 값으로 바꾼다.
--   ② 공구 송장 번호: 받는 분을 알아볼 수 있는 연결 고리라 R__batch_47 이 마지막 거래 + 5년 뒤 비운다 — NOT NULL 을 푼다(형식 CHECK 는 NULL 을 통과시킨다).

ALTER TABLE business_profiles ADD COLUMN anonymized_at timestamptz;
ALTER TABLE business_profiles ALTER COLUMN biz_no_last4 DROP NOT NULL;
-- 끝 4자리가 비는 것은 익명화한 줄만(형식 CHECK ck_bp_last4 는 그대로 — NULL 은 통과)
ALTER TABLE business_profiles ADD CONSTRAINT ck_bp_last4_present CHECK (biz_no_last4 IS NOT NULL OR anonymized_at IS NOT NULL);
ALTER TABLE business_profiles ADD CONSTRAINT ck_bp_anonymized CHECK (anonymized_at IS NULL OR (status IN ('REJECTED', 'WITHDRAWN') AND erased_at IS NOT NULL));

-- 사업자 신청 건 스냅샷: 익명화한 신원의 건은 끝 4자리가 빌 수 있다(ck_ba_source_business 를 다시 만든다 — 나머지 조건은 V112 그대로)
ALTER TABLE biz_applications DROP CONSTRAINT ck_ba_source_business;
ALTER TABLE biz_applications ADD CONSTRAINT ck_ba_source_business CHECK (source <> 'BUSINESS' OR (business_profile_id IS NOT NULL AND user_id IS NOT NULL AND company_name IS NOT NULL
                                                                         AND ad_policy_version IS NOT NULL AND refund_policy_version IS NOT NULL AND agreed_at IS NOT NULL));

ALTER TABLE group_buy_shipments ALTER COLUMN tracking_no DROP NOT NULL;
