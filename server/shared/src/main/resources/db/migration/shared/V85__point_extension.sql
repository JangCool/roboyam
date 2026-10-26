-- 커머스 1단계 1-1 포인트 확장 — docs/features/commerce-stage1-contract.md 「A. 포인트 확장」·「1-1 화면 확정과 서버 세부」, docs/domain/commerce.md 5절(5-2~5-5)·17-2.
-- ⚠ 합칠 때 번호 재확인(가번호 V85) — develop 에 합치기 직전에 shared 스트림의 마지막 번호를 다시 보고 정한다(계약 「마이그레이션 계획」).
-- ⚠ 합칠 때 합집합 확인 — 원장 사유 CHECK 를 다시 만든다(V80 목록 ∪ commerce.md 5-3 의 값 전부, 세션 정리 결정 4). 다른 세션이 이 CHECK 를 또 만들면 나중 쪽이 모든 값을 담아야 한다.
--
-- 무엇이 바뀌나
--   · 잔액: 무상(free_balance)·유상(paid_balance)으로 나누고 balance 는 DB 가 계산하는 합계 칸, 미회수(revoke_debt) 추가.
--   · 묶음(point_lots): 받은 포인트를 묶음 단위로 — 무상은 소멸일이 있고 유상은 없다(D12). 차감 순서·회수·사용 취소·소멸이 묶음에서 일어난다.
--   · 원장: 줄마다 무상·유상 몫(free_delta + paid_delta = delta), 종류 CANCEL_USE·EXPIRE·REFUND, 줄이 어느 묶음에 얼마 반영됐는지(point_ledger_lots).
--   · 미회수 기록(point_revoke_shortfalls): 잔액이 모자라 덜 회수한 양 — 같은 대상을 두 번 빚으로 올리지 않게 (사유, 대상) 유일.
--   · 회원 삭제가 잔액·원장을 함께 지우지 않게(17-2, 결정 5) — 회원 행은 지우지 않고 익명화하는 것이 원칙이다.
--   · 전환(D4): 잔액이 있는 회원마다 무상 묶음 하나(1년), 원장 줄 없음.

-- ── 정책: 묶음 유효기간·소멸 알림·전환 시각·큰 금액 조정 문턱 ───────────────────
-- free_valid_days: 무상 묶음 유효기간(적립·운영 지급·미회수 상계로 갚은 몫의 되돌림). restored_lot_valid_days: 만료된 묶음을 되돌릴 때 새 묶음의 유효기간(4절 「만료된 lot 복원 30일」).
-- expiry_notice_days: 소멸 예정 알림을 며칠 앞에 보내나(알림 배치 — 1-1 다음 작업). lot_tracking_started_at: 묶음 추적을 시작한 시각(이 마이그레이션 시각) —
--   불변식 「원장 줄의 묶음 반영 합 = delta」는 이 시각 이후 줄만 본다(5-5 「전환 이전 줄 제외」).
-- expiry_notice_interval_days: 한 회원에게 소멸 예정 알림을 다시 보내기까지 최소 며칠(사용자 확정 2026-10-26 — 기본 7, 0 = 간격 없음). 알림 기간보다 짧게 써야
--   모든 묶음이 소멸 전에 한 번은 알려진다 — 배치가 min(간격, 알림 일수 − 1)로 쓴다.
-- adjust_large_threshold: |운영 조정| 이 이 값을 넘으면 point.adjust.large 권한이 필요하다(0 = 모든 조정이 고액, 계약 「1-1 서버 세부」). 범위는 조정 최대값과 같은 0~100,000.
ALTER TABLE point_settings
    ADD COLUMN free_valid_days          integer      NOT NULL DEFAULT 365,
    ADD COLUMN restored_lot_valid_days  integer      NOT NULL DEFAULT 30,
    ADD COLUMN expiry_notice_days       integer      NOT NULL DEFAULT 30,
    ADD COLUMN expiry_notice_interval_days integer   NOT NULL DEFAULT 7,
    ADD COLUMN lot_tracking_started_at  timestamptz,
    ADD COLUMN adjust_large_threshold   integer      NOT NULL DEFAULT 10000;
UPDATE point_settings SET lot_tracking_started_at = now();
ALTER TABLE point_settings ALTER COLUMN lot_tracking_started_at SET NOT NULL;
ALTER TABLE point_settings ADD CONSTRAINT ck_point_settings_lots CHECK (free_valid_days BETWEEN 1 AND 3650 AND restored_lot_valid_days BETWEEN 1 AND 365
    AND expiry_notice_days BETWEEN 0 AND 365 AND expiry_notice_interval_days BETWEEN 0 AND 30 AND adjust_large_threshold BETWEEN 0 AND 100000);

-- ── 잔액: 무상·유상·미회수, balance 는 계산 칸 ─────────────────────────────────
-- 기존 칸을 계산 칸으로 바로 바꿀 수 없어서 값을 free_balance 로 옮기고 칸을 지운 뒤 다시 만든다(지금까지의 포인트는 모두 무상이다). 회원 목록의 정렬·범위 색인도 다시.
-- 앱은 balance 를 쓰지 않는다(읽기만) — 쓰기는 PointService 가 free_balance·paid_balance·revoke_debt 만 바꾼다.
ALTER TABLE point_balances
    ADD COLUMN free_balance  bigint NOT NULL DEFAULT 0,
    ADD COLUMN paid_balance  bigint NOT NULL DEFAULT 0,
    ADD COLUMN revoke_debt   bigint NOT NULL DEFAULT 0,
    -- 이 회원에게 마지막으로 소멸 예정 알림을 보낸(보내려 한) 시각 — 알림 배치만 쓴다(회원당 최소 간격)
    ADD COLUMN expiry_notice_sent_at timestamptz;
UPDATE point_balances SET free_balance = balance;
DROP INDEX ix_point_balances_balance;
ALTER TABLE point_balances DROP COLUMN balance; -- ck_point_balances_nonnegative 도 함께 없어진다
ALTER TABLE point_balances ADD COLUMN balance bigint NOT NULL GENERATED ALWAYS AS (free_balance + paid_balance) STORED;
ALTER TABLE point_balances ADD CONSTRAINT ck_point_balances_nonnegative CHECK (free_balance >= 0 AND paid_balance >= 0 AND revoke_debt >= 0);
CREATE INDEX ix_point_balances_balance ON point_balances (balance);

-- 회원 삭제 연쇄 제거(17-2, 결정 5) — 잔액·원장이 있는 회원 행은 지우지 못한다(익명화가 원칙). 원장은 보관 기간 동안 남는다.
ALTER TABLE point_balances DROP CONSTRAINT point_balances_user_id_fkey;
ALTER TABLE point_balances ADD CONSTRAINT fk_point_balances_user FOREIGN KEY (user_id) REFERENCES users (id);
ALTER TABLE point_ledger DROP CONSTRAINT point_ledger_user_id_fkey;
ALTER TABLE point_ledger ADD CONSTRAINT fk_point_ledger_user FOREIGN KEY (user_id) REFERENCES users (id);

-- ── 원장: 무상·유상 몫, 종류·사유·대상 넓힘 ─────────────────────────────────────
-- 기존 줄을 채우려면 수정 금지 트리거를 잠시 떼야 한다(같은 트랜잭션 — 이 마이그레이션 밖에서는 늘 붙어 있다).
DROP TRIGGER tr_point_ledger_append_only ON point_ledger;
ALTER TABLE point_ledger
    ADD COLUMN free_delta bigint,
    ADD COLUMN paid_delta bigint;
UPDATE point_ledger SET free_delta = delta, paid_delta = 0; -- 지금까지의 줄은 모두 무상
ALTER TABLE point_ledger ALTER COLUMN free_delta SET NOT NULL, ALTER COLUMN paid_delta SET NOT NULL;
ALTER TABLE point_ledger ALTER COLUMN reason TYPE varchar(30), ALTER COLUMN ref_type TYPE varchar(20);

ALTER TABLE point_ledger DROP CONSTRAINT ck_point_ledger_kind;
ALTER TABLE point_ledger DROP CONSTRAINT ck_point_ledger_reason;
ALTER TABLE point_ledger DROP CONSTRAINT ck_point_ledger_delta;
ALTER TABLE point_ledger DROP CONSTRAINT ck_point_ledger_ref;
-- 종류: EARN(지급) · USE(사용) · REVOKE(회수 — 무상만) · CANCEL_USE(사용 취소 — 원래 묶음으로) · EXPIRE(소멸) · REFUND(유상 환불). 부호: EARN·CANCEL_USE 면 +, 나머지 −.
ALTER TABLE point_ledger ADD CONSTRAINT ck_point_ledger_kind CHECK (kind IN ('EARN', 'USE', 'REVOKE', 'CANCEL_USE', 'EXPIRE', 'REFUND'));
ALTER TABLE point_ledger ADD CONSTRAINT ck_point_ledger_delta CHECK (delta <> 0 AND (kind IN ('EARN', 'CANCEL_USE')) = (delta > 0));
-- 몫: 합 = delta, 두 몫의 부호는 줄의 부호를 따른다. 회수는 무상에서만(유상 몫 0 — 5-4).
ALTER TABLE point_ledger ADD CONSTRAINT ck_point_ledger_split CHECK (free_delta + paid_delta = delta
    AND (CASE WHEN delta > 0 THEN free_delta >= 0 AND paid_delta >= 0 ELSE free_delta <= 0 AND paid_delta <= 0 END)
    AND (kind <> 'REVOKE' OR paid_delta = 0));
-- 사유: V80 목록 ∪ commerce.md 5-3(2a·3단계 것 포함) — 결정 4 「한 번에」. TICKET_PURCHASE 는 CHECK 에만 남고 쓰지 않는다(결정 3).
ALTER TABLE point_ledger ADD CONSTRAINT ck_point_ledger_reason CHECK (reason IN (
    'ATTENDANCE', 'ATTENDANCE_STREAK', 'POST', 'COMMENT', 'POPULAR', 'POST_REVOKE', 'COMMENT_REVOKE', 'POST_RESTORE', 'TICKET_PURCHASE', 'ADMIN_ADJUST',
    'DEAL_END_REQUEST', 'LIKE_BONUS', 'LIKE_BONUS_REVOKE', 'SIGNUP',
    'CHARGE', 'CHARGE_BONUS', 'CHARGE_BONUS_REVOKE', 'CHARGE_REFUND', 'CHARGE_REFUND_RESTORE', 'CHARGE_BONUS_RESTORE',
    'POINT_SHOP_PURCHASE', 'POINT_SHOP_REFUND', 'GB_POINT_USE', 'GB_POINT_RESTORE',
    'REVOKE_DEBT_OFFSET', 'EXPIRE', 'WITHDRAW_FORFEIT'));
-- 사유와 종류의 짝(5-3) — 사용·사용 취소·소멸·환불·회수 사유는 그 종류로만, ADMIN_ADJUST 는 부호로 EARN·REVOKE, 나머지는 EARN.
ALTER TABLE point_ledger ADD CONSTRAINT ck_point_ledger_reason_kind CHECK (CASE kind
    WHEN 'USE' THEN reason IN ('TICKET_PURCHASE', 'POINT_SHOP_PURCHASE', 'GB_POINT_USE')
    WHEN 'CANCEL_USE' THEN reason IN ('POINT_SHOP_REFUND', 'GB_POINT_RESTORE')
    WHEN 'EXPIRE' THEN reason IN ('EXPIRE', 'WITHDRAW_FORFEIT')
    WHEN 'REFUND' THEN reason IN ('CHARGE_REFUND')
    WHEN 'REVOKE' THEN reason IN ('POST_REVOKE', 'COMMENT_REVOKE', 'LIKE_BONUS_REVOKE', 'CHARGE_BONUS_REVOKE', 'ADMIN_ADJUST', 'REVOKE_DEBT_OFFSET')
    ELSE reason NOT IN ('TICKET_PURCHASE', 'POINT_SHOP_PURCHASE', 'GB_POINT_USE', 'POINT_SHOP_REFUND', 'GB_POINT_RESTORE', 'EXPIRE', 'WITHDRAW_FORFEIT',
                        'CHARGE_REFUND', 'POST_REVOKE', 'COMMENT_REVOKE', 'LIKE_BONUS_REVOKE', 'CHARGE_BONUS_REVOKE', 'REVOKE_DEBT_OFFSET')
END);
-- 대상: 글·댓글 + 포인트 상점 구매 · 충전 · 공구 참여 · 묶음(소멸) · 원장 줄(미회수 상계의 대상 = 적립 줄) · 회원(가입 포인트 멱등)
ALTER TABLE point_ledger ADD CONSTRAINT ck_point_ledger_ref CHECK (ref_type IS NULL OR ref_type IN ('POST', 'COMMENT', 'POINT_SHOP_PURCHASE', 'POINT_CHARGE',
    'GB_PARTICIPATION', 'LOT', 'LEDGER', 'USER'));

-- 원장은 추가만 — 새 칸(free_delta·paid_delta)까지 막는다. 운영자 행이 지워질 때의 actor_operator_id → NULL(외래 키 동작)만 허용, 보관 기간 파기(DELETE)는 막지 않는다
CREATE OR REPLACE FUNCTION point_ledger_append_only() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF (NEW.id, NEW.user_id, NEW.kind, NEW.reason, NEW.delta, NEW.free_delta, NEW.paid_delta, NEW.balance_after, NEW.note, NEW.ref_type, NEW.ref_id, NEW.created_at)
       IS DISTINCT FROM (OLD.id, OLD.user_id, OLD.kind, OLD.reason, OLD.delta, OLD.free_delta, OLD.paid_delta, OLD.balance_after, OLD.note, OLD.ref_type, OLD.ref_id,
                         OLD.created_at)
       OR NEW.actor_operator_id IS NOT NULL AND NEW.actor_operator_id IS DISTINCT FROM OLD.actor_operator_id THEN
        RAISE EXCEPTION 'point_ledger is append-only';
    END IF;
    RETURN NEW;
END
$$;
CREATE TRIGGER tr_point_ledger_append_only BEFORE UPDATE ON point_ledger FOR EACH ROW EXECUTE FUNCTION point_ledger_append_only();

-- ── 묶음 ────────────────────────────────────────────────────────────────────
-- 회원 표에 외래 키를 걸지 않는다(commerce.md 3절). id: 앱이 만드는 묶음은 Snowflake, 전환 묶음은 아래 순번(Snowflake 는 매우 큰 수라 겹치지 않는다 — DB 배치 로그 줄과 같은 방식).
-- kind: FREE(무상 — 소멸일 필수) · PAID(유상 — 소멸 없음, D12). remaining_amount 는 차감·회수·소멸로 줄고 사용 취소·글 복구로 원래 묶음에 되돌아온다(원래 양을 넘지 않는다).
-- source_*: 어디서 생겼나(원장 사유·대상, 전환은 MIGRATION). source_ledger_id: 이 묶음을 만든 적립 줄(적립 하나 = 묶음 하나 — 회수는 이 묶음에서 먼저).
-- restored_from_lot_id: 만료된 묶음을 되돌려 새로 만든 묶음이면 원래 묶음. expiry_noticed_at: 소멸 예정 알림을 보낸 시각(알림 배치).
CREATE SEQUENCE point_lot_migration_id_seq;
CREATE TABLE point_lots (
    id                    bigint       PRIMARY KEY,
    user_id               bigint       NOT NULL,
    kind                  varchar(4)   NOT NULL,
    original_amount       bigint       NOT NULL,
    remaining_amount      bigint       NOT NULL,
    expires_at            timestamptz,
    source_reason         varchar(30)  NOT NULL,
    source_ref_type       varchar(20),
    source_ref_id         bigint,
    source_ledger_id      bigint,
    restored_from_lot_id  bigint       REFERENCES point_lots (id),
    expiry_noticed_at     timestamptz,
    created_at            timestamptz  NOT NULL,
    CONSTRAINT ck_point_lots_kind CHECK (kind IN ('FREE', 'PAID')),
    CONSTRAINT ck_point_lots_amount CHECK (original_amount > 0 AND remaining_amount >= 0 AND remaining_amount <= original_amount),
    CONSTRAINT ck_point_lots_expiry CHECK ((kind = 'FREE') = (expires_at IS NOT NULL)),
    CONSTRAINT ck_point_lots_source_ref CHECK (source_ref_type IS NULL OR source_ref_type IN ('POST', 'COMMENT', 'POINT_SHOP_PURCHASE', 'POINT_CHARGE',
        'GB_PARTICIPATION', 'LOT', 'LEDGER', 'USER'))
);
CREATE UNIQUE INDEX ux_point_lots_source_ledger ON point_lots (source_ledger_id) WHERE source_ledger_id IS NOT NULL;
-- 차감 순서(회원의 남은 묶음) · 소멸·알림 배치(소멸일 순)
CREATE INDEX ix_point_lots_deduction ON point_lots (user_id, kind, expires_at, id) WHERE remaining_amount > 0;
CREATE INDEX ix_point_lots_expiry ON point_lots (expires_at) WHERE kind = 'FREE' AND remaining_amount > 0;
-- 관리자 회원 상세 「전체」 목록(최근 받은 순)
CREATE INDEX ix_point_lots_user ON point_lots (user_id, created_at DESC, id DESC);

-- 묶음의 정체(누구 것·종류·양·소멸일·출처·만든 시각)는 바뀌지 않는다 — 남은 양과 알림 시각만 바뀐다
CREATE FUNCTION point_lots_identity_fixed() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF (NEW.id, NEW.user_id, NEW.kind, NEW.original_amount, NEW.expires_at, NEW.source_reason, NEW.source_ref_type, NEW.source_ref_id, NEW.source_ledger_id,
        NEW.restored_from_lot_id, NEW.created_at)
       IS DISTINCT FROM (OLD.id, OLD.user_id, OLD.kind, OLD.original_amount, OLD.expires_at, OLD.source_reason, OLD.source_ref_type, OLD.source_ref_id,
                         OLD.source_ledger_id, OLD.restored_from_lot_id, OLD.created_at) THEN
        RAISE EXCEPTION 'point_lots identity is fixed';
    END IF;
    RETURN NEW;
END
$$;
CREATE TRIGGER tr_point_lots_identity_fixed BEFORE UPDATE ON point_lots FOR EACH ROW EXECUTE FUNCTION point_lots_identity_fixed();

-- ── 원장 줄 ↔ 묶음 반영 ──────────────────────────────────────────────────────
-- 원장 한 줄이 어느 묶음에 얼마(±) 반영됐는지. 한 줄의 합 = 그 줄의 delta(전환 이전 줄 제외 — 5-5). 원장 줄이 보관 기간 파기로 지워지면 함께 지워지고, 묶음은 반영이 남아 있으면 지울 수 없다.
CREATE TABLE point_ledger_lots (
    ledger_id  bigint  NOT NULL REFERENCES point_ledger (id) ON DELETE CASCADE,
    lot_id     bigint  NOT NULL REFERENCES point_lots (id),
    amount     bigint  NOT NULL,
    PRIMARY KEY (ledger_id, lot_id),
    CONSTRAINT ck_point_ledger_lots_amount CHECK (amount <> 0)
);
CREATE INDEX ix_point_ledger_lots_lot ON point_ledger_lots (lot_id);

CREATE FUNCTION point_ledger_lots_append_only() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'point_ledger_lots is append-only';
END
$$;
CREATE TRIGGER tr_point_ledger_lots_append_only BEFORE UPDATE ON point_ledger_lots FOR EACH ROW EXECUTE FUNCTION point_ledger_lots_append_only();

-- ── 미회수 기록 ─────────────────────────────────────────────────────────────
-- 회수할 양(requested)보다 무상 잔액이 모자라 덜 회수했다(applied) — 모자란 양은 point_balances.revoke_debt 에 더해 다음 적립에서 갚는다(5-4). 어뷰징 리포트용.
-- 유일 (reason, ref_type, ref_id): 반영 0 이라 원장 줄이 없어도 같은 대상을 두 번 빚으로 올리지 않는다(회수 멱등). 그 글을 복구하면 이 줄을 지우고 빚을 줄인다(결정 7).
CREATE TABLE point_revoke_shortfalls (
    id          bigint       PRIMARY KEY,
    user_id     bigint       NOT NULL,
    reason      varchar(30)  NOT NULL,
    ref_type    varchar(20)  NOT NULL,
    ref_id      bigint       NOT NULL,
    requested   bigint       NOT NULL,
    applied     bigint       NOT NULL,
    created_at  timestamptz  NOT NULL,
    CONSTRAINT ck_point_revoke_shortfalls_amount CHECK (requested > 0 AND applied >= 0 AND applied < requested)
);
CREATE UNIQUE INDEX ux_point_revoke_shortfalls_ref ON point_revoke_shortfalls (reason, ref_type, ref_id);
CREATE INDEX ix_point_revoke_shortfalls_user ON point_revoke_shortfalls (user_id, created_at);

-- ── 전환(D4) ────────────────────────────────────────────────────────────────
-- 잔액이 있는 회원마다 무상 묶음 하나(남은 양 = 잔액, 소멸 = 지금 + 1년, 출처 MIGRATION). 원장 줄은 만들지 않는다(원장 합 = 잔액은 이미 성립).
-- 전환 이전 글의 회수는 원래 묶음을 몰라 「다른 무상 묶음」 규칙으로 처리한다(5-4). now() 는 이 트랜잭션의 시각이라 lot_tracking_started_at 과 같다.
INSERT INTO point_lots (id, user_id, kind, original_amount, remaining_amount, expires_at, source_reason, created_at)
SELECT nextval('point_lot_migration_id_seq'), b.user_id, 'FREE', b.free_balance, b.free_balance, now() + interval '1 year', 'MIGRATION', now()
FROM point_balances b
WHERE b.free_balance > 0
ORDER BY b.user_id;

-- ── 회원 알림 종류: 포인트 소멸 예정 (POINTS_EXPIRING) — 1-1 B(소멸 예정 알림 배치 point-expiry-notice) ─────────
-- ⚠ 합칠 때 합집합 확인 — 이 CHECK 는 V84 의 목록 ∪ POINTS_EXPIRING 이다. develop 이나 다른 세션이 그 뒤에 이 CHECK 를 다시 만들었으면(종류 추가) 나중에 적용되는 쪽이 모든 값을 담아야 한다.
-- 알림 설정 행(member_event_settings)은 넣지 않는다 — 줄이 없으면 코드의 처음 값(MemberEvent: 사용·앱 알림 켬, 이메일 끔)을 쓴다(V84 FEATURE_RESTRICTED 와 같다).
ALTER TABLE member_notifications DROP CONSTRAINT ck_member_notifications_type;
ALTER TABLE member_notifications ADD CONSTRAINT ck_member_notifications_type
    CHECK (type IN ('COMMENT', 'REPLY', 'POINTS_REVOKED', 'POST_HIDDEN', 'COMMENT_HIDDEN', 'DM', 'NICKNAME_IMAGE_REVIEWED', 'OPINION_PIN_REQUESTED',
                    'OPINION_PIN_REVIEWED', 'POST_REMOVED', 'COMMENT_REMOVED', 'REPORT_RESULT', 'FEATURE_RESTRICTED', 'POINTS_EXPIRING'));
