-- 보관 기간이 지난 거래 기록의 개인 칸 파기 — 개인정보처리방침 기본 문안(admin-web utils/terms-templates/privacy.ts 1-3·1-6·2절)과 실제 파기를 맞춘다(2026-11-24 사용자 결정).
--   ① 광고·사업자 핫딜 계약서 사본(biz_contract_files): 게재가 끝난 날부터 5년 뒤 내용(암호문)을 지운다 — R__batch_46. 줄(파일 이름·형식·크기·해시·올린 시각·대체 관계)은 남기고
--      내용만 비운다(종이 접수 기록의 스캔본 참조·신청 건의 사본 수 CHECK 를 깨지 않게). 사업자등록증 사본(business_documents)과 같은 모양 — content NULL + erased_at.
--   ② 입금 기록(biz_deposits): 같은 때 입금자명·불일치 사유를 지운다 — 입금액·입금일은 남긴다(금액 합계·대사).
--   ③ 공구 주문(group_buy_participations): 법정 보관 기간(전자상거래법 5년)이 지나 남은 개인 칸(배송지·현금영수증 번호·운영 메모)을 지운 시각 — R__batch_47.

ALTER TABLE biz_contract_files ALTER COLUMN content DROP NOT NULL;
ALTER TABLE biz_contract_files ADD COLUMN erased_at timestamptz;
ALTER TABLE biz_contract_files ADD CONSTRAINT ck_bcf_content CHECK (erased_at IS NOT NULL OR content IS NOT NULL);

ALTER TABLE biz_deposits ALTER COLUMN depositor_name DROP NOT NULL;
ALTER TABLE biz_deposits ADD COLUMN erased_at timestamptz;
ALTER TABLE biz_deposits ADD CONSTRAINT ck_bdep_depositor CHECK (erased_at IS NOT NULL OR depositor_name IS NOT NULL);

ALTER TABLE group_buy_participations ADD COLUMN privacy_purged_at timestamptz;
-- 파기 배치가 훑는 줄(끝난 주문 중 아직 파기하지 않은 것 — 만든 시각·id 키셋. 마지막 거래 시각은 만든 시각보다 앞서지 않으므로 만든 지 5년 안 된 주문은 훑지 않는다)
CREATE INDEX ix_gbp_privacy_purge ON group_buy_participations (created_at, id)
    WHERE privacy_purged_at IS NULL AND status IN ('DELIVERED', 'CANCELED', 'REFUNDED', 'EXPIRED');
