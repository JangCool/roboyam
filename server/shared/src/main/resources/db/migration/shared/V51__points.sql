-- 포인트 — docs/features/point-contract.md. 정책(한 행) · 회원별 잔액 · 원장(추가만).
-- 모든 지급·회수·조정은 PointService 한 길을 지나고, 잔액 행을 잠근 뒤(FOR UPDATE) 원장 한 줄 + 잔액 갱신을 같은 트랜잭션에서 한다 — 잔액은 0 아래로 내려가지 않는다(CHECK).

-- ── 정책(행 하나) — 관리자 「포인트 정책」 14칸. 기본값은 화면 목업 기본값(community point_settings + signup_points). 범위는 화면 limits 와 같다 ──
CREATE TABLE point_settings (
    id                            integer      PRIMARY KEY,
    attendance_points             integer      NOT NULL DEFAULT 10,
    post_points                   integer      NOT NULL DEFAULT 20,
    post_daily_cap                integer      NOT NULL DEFAULT 5,
    comment_points                integer      NOT NULL DEFAULT 5,
    comment_daily_cap             integer      NOT NULL DEFAULT 20,
    popular_reward_points         integer      NOT NULL DEFAULT 100,
    popular_reward_top_n          integer      NOT NULL DEFAULT 5,
    like_bonus_threshold          integer      NOT NULL DEFAULT 5,
    like_bonus_points             integer      NOT NULL DEFAULT 10,
    like_bonus_daily_limit        integer      NOT NULL DEFAULT 3,
    deal_end_request_points       integer      NOT NULL DEFAULT 3,
    deal_end_request_daily_limit  integer      NOT NULL DEFAULT 5,
    signup_points                 integer      NOT NULL DEFAULT 0,
    nickname_ticket_price         integer      NOT NULL DEFAULT 1000,
    updated_at                    timestamptz,
    updated_by                    bigint       REFERENCES operators (id) ON DELETE SET NULL,
    CONSTRAINT ck_point_settings_single_row CHECK (id = 1),
    CONSTRAINT ck_point_settings_points CHECK (attendance_points BETWEEN 0 AND 10000 AND post_points BETWEEN 0 AND 10000 AND comment_points BETWEEN 0 AND 10000
        AND popular_reward_points BETWEEN 0 AND 10000 AND like_bonus_points BETWEEN 0 AND 10000 AND deal_end_request_points BETWEEN 0 AND 10000
        AND signup_points BETWEEN 0 AND 10000),
    CONSTRAINT ck_point_settings_caps CHECK (post_daily_cap BETWEEN 0 AND 1000 AND comment_daily_cap BETWEEN 0 AND 1000 AND like_bonus_daily_limit BETWEEN 0 AND 1000
        AND deal_end_request_daily_limit BETWEEN 0 AND 1000),
    CONSTRAINT ck_point_settings_other CHECK (popular_reward_top_n BETWEEN 0 AND 50 AND like_bonus_threshold BETWEEN 1 AND 1000
        AND nickname_ticket_price BETWEEN 1 AND 100000)
);
INSERT INTO point_settings (id) VALUES (1);

-- ── 회원별 잔액 — 지급·회수·조정 때 이 행을 잠그고 바꾼다(처음이면 0 으로 만든다). 행이 없으면 0P 다 ──
CREATE TABLE point_balances (
    user_id     bigint       PRIMARY KEY REFERENCES users (id) ON DELETE CASCADE,
    balance     bigint       NOT NULL DEFAULT 0,
    updated_at  timestamptz  NOT NULL,
    CONSTRAINT ck_point_balances_nonnegative CHECK (balance >= 0)
);
-- 회원 목록의 포인트 정렬·범위 조건
CREATE INDEX ix_point_balances_balance ON point_balances (balance);

-- ── 원장(추가만 — 고치지 않는다, 아래 트리거) ──
-- kind: EARN(지급) · USE(교환권 구매) · REVOKE(회수·차감). reason: 사유 코드(화면 PointReasonCode). delta: 실제 반영된 값(부호 포함, 0 은 줄을 만들지 않는다).
-- note: 운영 조정의 공개 사유(1~100자) — 그 밖은 NULL. ref_type·ref_id: 글·댓글 지급·회수·추천 보너스가 가리키는 대상(파티션 표라 외래 키 없음).
-- actor_operator_id: 운영 조정을 한 운영자(자동은 NULL). 같은 대상에 같은 사유로 두 번 지급·회수하지 않는다(ux_point_ledger_ref — 멱등의 마지막 방어).
CREATE TABLE point_ledger (
    id                 bigint        PRIMARY KEY,
    user_id            bigint        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    kind               varchar(10)   NOT NULL,
    reason             varchar(20)   NOT NULL,
    delta              integer       NOT NULL,
    balance_after      bigint        NOT NULL,
    note               varchar(100),
    ref_type           varchar(10),
    ref_id             bigint,
    actor_operator_id  bigint        REFERENCES operators (id) ON DELETE SET NULL,
    created_at         timestamptz   NOT NULL,
    CONSTRAINT ck_point_ledger_kind CHECK (kind IN ('EARN', 'USE', 'REVOKE')),
    CONSTRAINT ck_point_ledger_reason CHECK (reason IN ('ATTENDANCE', 'POST', 'COMMENT', 'POPULAR', 'POST_REVOKE', 'COMMENT_REVOKE', 'TICKET_PURCHASE',
        'ADMIN_ADJUST', 'DEAL_END_REQUEST', 'LIKE_BONUS', 'LIKE_BONUS_REVOKE', 'SIGNUP')),
    CONSTRAINT ck_point_ledger_delta CHECK (delta <> 0 AND (kind = 'EARN') = (delta > 0)),
    CONSTRAINT ck_point_ledger_balance CHECK (balance_after >= 0),
    CONSTRAINT ck_point_ledger_ref CHECK (ref_type IS NULL OR ref_type IN ('POST', 'COMMENT'))
);
CREATE UNIQUE INDEX ux_point_ledger_ref ON point_ledger (reason, ref_type, ref_id) WHERE ref_id IS NOT NULL;
-- 회원별 최근 내역 · 오늘 지급 횟수(id 는 시각순 Snowflake)
CREATE INDEX ix_point_ledger_user ON point_ledger (user_id, id DESC);
-- 관리자 전체 원장(최신이 위, 기간 조건)
CREATE INDEX ix_point_ledger_created ON point_ledger (created_at DESC, id DESC);

-- 원장은 추가만 한다 — 내용을 고치는 문장을 막는다. 운영자 행이 지워질 때의 actor_operator_id → NULL(외래 키 동작)만 허용하고, 회원 삭제의 CASCADE 는 DELETE 라 막지 않는다
CREATE FUNCTION point_ledger_append_only() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF (NEW.id, NEW.user_id, NEW.kind, NEW.reason, NEW.delta, NEW.balance_after, NEW.note, NEW.ref_type, NEW.ref_id, NEW.created_at)
       IS DISTINCT FROM (OLD.id, OLD.user_id, OLD.kind, OLD.reason, OLD.delta, OLD.balance_after, OLD.note, OLD.ref_type, OLD.ref_id, OLD.created_at)
       OR NEW.actor_operator_id IS NOT NULL AND NEW.actor_operator_id IS DISTINCT FROM OLD.actor_operator_id THEN
        RAISE EXCEPTION 'point_ledger is append-only';
    END IF;
    RETURN NEW;
END
$$;
CREATE TRIGGER tr_point_ledger_append_only BEFORE UPDATE ON point_ledger FOR EACH ROW EXECUTE FUNCTION point_ledger_append_only();
