-- 커머스 2a-3 대사 + 관리자 API — docs/features/commerce-payment-contract.md 「2a-1 화면 목업」(확정한 목업 가정 2)·「2a-3 구현 결과」, docs/domain/commerce.md 16-1.
-- 번호: develop(81f286b) 의 shared 마지막 V97(결제 모듈) 다음.
--
-- 무엇이 바뀌나
--   · 결제 이력(payment_events.kind)에 RECHECKED(운영자가 「포트원에서 다시 확인」을 눌렀다 — 결과는 detail.outcome)를 더한다.
--   · 대사한 날(payment_reconcile_days.status): V97 의 OK·FAIL 을 화면이 쓰는 RUNNING(도는 중)·DONE(불일치 없음)·MISMATCH(불일치 있음)·FAILED(포트원 장애 등)로 바꾼다.
--     「아직 안 한 날」(PENDING)은 줄이 없는 날이라 저장하지 않는다. V97 은 아직 어느 서버도 이 표에 쓰지 않았다(대사 배치는 이 단계에서 처음 생긴다) — 옛 값 옮기기는 안전망이다.
--   · 대사 배치가 「그 날 결제·취소된 우리 결제」를 찾는 색인, 결제 이상 화면이 멈췄던 취소를 찾는 색인, 대사 화면이 날짜로 이슈를 거르는 색인.

ALTER TABLE payment_events DROP CONSTRAINT ck_payment_events_kind;
ALTER TABLE payment_events ADD CONSTRAINT ck_payment_events_kind CHECK (kind IN ('CREATED', 'PAID', 'ANOMALY', 'EXPIRED', 'CANCEL_REQUESTED', 'CANCEL_SUCCEEDED',
    'CANCEL_RETRY', 'CANCEL_GAVE_UP', 'CANCEL_MANUAL', 'EXTERNAL_CANCEL', 'WEBHOOK', 'RECHECKED'));

ALTER TABLE payment_reconcile_days DROP CONSTRAINT ck_payment_reconcile_days_status;
UPDATE payment_reconcile_days SET status = CASE WHEN status = 'FAIL' THEN 'FAILED' WHEN issue_count > 0 THEN 'MISMATCH' ELSE 'DONE' END;
ALTER TABLE payment_reconcile_days ADD CONSTRAINT ck_payment_reconcile_days_status CHECK (status IN ('RUNNING', 'DONE', 'MISMATCH', 'FAILED'));

-- 그 날 끝난 우리 취소(대사가 포트원 목록에 없던 우리 쪽 변화를 단건 조회로 확인한다)
CREATE INDEX ix_payment_cancels_completed ON payment_cancels (completed_at) WHERE status = 'SUCCEEDED';
-- 결제 이상 화면 — 한 번이라도 멈췄던(GAVE_UP) 취소(다시 시도·수동 처리로 끝나도 「처리된 것」으로 남는다)
CREATE INDEX ix_payment_events_cancel_gave_up ON payment_events (created_at) WHERE kind = 'CANCEL_GAVE_UP';
-- 대사 불일치 화면의 날짜 거르기·날짜별 열린 이슈 수
CREATE INDEX ix_reconciliation_issues_day ON reconciliation_issues (detected_day, status);
