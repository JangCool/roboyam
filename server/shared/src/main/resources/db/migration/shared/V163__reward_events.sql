-- 보상 이벤트(관리자 /reward-events — docs/features/event-admin-contract.md 「저장」절, 서버 구현 2026-11-07). 커머스 범위(docs/domain/commerce.md 4·5-4·7절).
-- ⚠ 가번호 V163 — develop 최신 V161, 사이트 팝업 세션(claude/site-popup)이 V162 를 쓴다. 합치기 직전에 shared 스트림의 마지막 번호와 그쪽 V162 가 다시 만드는 CHECK(원장 사유·대상·회원 알림 종류 등)의 합집합을 다시 본다.
--
--   · reward_events            이벤트 한 건(방식·상태·기간·선착순 기준·후보 방법·일괄 조건·취소 사유). 번호(no)는 시퀀스(사람이 읽는 차례).
--                              선착순 인원 칸(first_come_count)은 「한 문장 조건부 증가」로만 올린다 — 인원을 넘지 않는다(CHECK 가 끝까지 막는다).
--   · reward_event_tiers       등수(보상 묶음) — 선착순·일괄은 하나, 당첨자 선정은 여럿.
--   · reward_event_lines       보상 줄(등수마다 1~10줄) — 무상 포인트 · 포인트 상점 아이템 · 로보티콘 팩 · 직접 전달. 품목·팩 이름은 고를 때의 사본.
--   · reward_event_recipients  받는 사람(이벤트당 한 사람 한 번 — 유일 (event_id, user_id)). queued_run_id = 지금 그 사람을 처리할 지급 실행(처리하면 비운다).
--   · reward_event_line_results 받는 사람 × 보상 줄의 결과 — id 가 원장·보유 줄의 대상이다(원장 대상 REWARD_EVENT_RESULT · user_items.grant_result_id ·
--                              emoticon_acquisitions.reward_result_id). 같은 줄은 한 번만 지급된다(원장 유일 (사유, 대상) · 보유 유일 (줄 결과, 순번)).
--   · reward_event_draws       댓글 추첨 기록(추첨 번호 seed · 후보 수 · 뽑힌 회원 — 같은 후보와 번호면 같은 결과).
--   · reward_event_runs        지급 실행(GRANT·RETRY·REVOKE·AUTO) — 대량은 RUNNING 으로 나눠 처리(한 덩어리 = 한 트랜잭션, 실행 행 잠금으로 한 서버만).
--
-- 넓히는 것(⚠ 합칠 때 합집합 확인 — 다른 세션도 이 CHECK 들을 다시 만들 수 있다. 나중에 적용되는 쪽이 모든 값을 담아야 한다):
--   · 원장 사유 ck_point_ledger_reason = V142 목록 ∪ EVENT_REWARD · EVENT_REWARD_REVOKE, 사유·종류 짝 ck_point_ledger_reason_kind = V85 ∪ EVENT_REWARD_REVOKE(REVOKE)
--   · 원장 대상 ck_point_ledger_ref · 묶음 출처 ck_point_lots_source_ref = V142 목록 ∪ REWARD_EVENT_RESULT
--   · user_items: 지급 출처 — 구매(purchase_id) 또는 줄 결과(grant_result_id + grant_seq) 중 정확히 하나. 영구 아이템 부분 유일 색인(ux_user_items_permanent)은 그대로 작동한다.
--   · emoticon_acquisitions: 종류 ∪ EVENT + reward_result_id(이벤트로 받은 줄)
--   · 회원 알림 종류 ck_member_notifications_type = V157 목록 ∪ REWARD_EVENT_GRANTED · REWARD_EVENT_REVOKED
-- 새 표는 users 에 외래 키를 걸지 않는다(V142 과 같다). 운영자는 ON DELETE SET NULL.

CREATE SEQUENCE reward_event_no_seq START 1;

-- ── 이벤트 ────────────────────────────────────────────────────────────────
CREATE TABLE reward_events (
    id                  bigint       PRIMARY KEY,
    no                  bigint       NOT NULL,
    name                varchar(60)  NOT NULL,
    memo                varchar(500) NOT NULL DEFAULT '',
    notice_url          varchar(500) NOT NULL DEFAULT '',
    mode                varchar(12)  NOT NULL,
    status              varchar(10)  NOT NULL DEFAULT 'DRAFT',
    start_at            timestamptz  NOT NULL,
    end_at              timestamptz  NOT NULL,
    exclude_operators   boolean      NOT NULL DEFAULT true,
    -- 선착순(FIRST_COME)
    first_come_trigger  varchar(20),
    first_come_limit    integer,
    first_come_count    integer      NOT NULL DEFAULT 0,
    -- 당첨자 선정(SELECTION) — 후보 방법(직접 입력·댓글 추첨) · 댓글 추첨 설정
    source_manual       boolean      NOT NULL DEFAULT false,
    source_comment      boolean      NOT NULL DEFAULT false,
    draw_post_ref       varchar(300),
    draw_post_id        bigint,
    draw_from           timestamptz,
    draw_to             timestamptz,
    draw_exclude_author boolean      NOT NULL DEFAULT true,
    -- 일괄(BULK) — 전체 또는 조건(등급 서열 이상 · 가입일 범위(표시 시간대 날짜) · 본인인증만 · 최근 N일 로그인)
    bulk_scope          varchar(10),
    bulk_min_grade      integer,
    bulk_joined_from    date,
    bulk_joined_to      date,
    bulk_identity_only  boolean      NOT NULL DEFAULT false,
    bulk_active_days    integer,
    closed_at           timestamptz,
    done_at             timestamptz,
    cancel_reason       varchar(200),
    canceled_at         timestamptz,
    canceled_by         bigint       REFERENCES operators (id) ON DELETE SET NULL,
    created_by          bigint       REFERENCES operators (id) ON DELETE SET NULL,
    created_at          timestamptz  NOT NULL,
    updated_by          bigint       REFERENCES operators (id) ON DELETE SET NULL,
    updated_at          timestamptz  NOT NULL,
    CONSTRAINT ux_reward_events_no UNIQUE (no),
    CONSTRAINT ck_reward_events_mode CHECK (mode IN ('FIRST_COME', 'SELECTION', 'BULK')),
    CONSTRAINT ck_reward_events_status CHECK (status IN ('DRAFT', 'ACTIVE', 'CLOSED', 'DONE', 'CANCELED')),
    CONSTRAINT ck_reward_events_period CHECK (end_at > start_at),
    CONSTRAINT ck_reward_events_name CHECK (char_length(btrim(name)) BETWEEN 1 AND 60),
    -- 선착순 칸은 선착순에만, 인원 1~100,000 · 받은 수는 인원을 넘지 않는다
    CONSTRAINT ck_reward_events_first_come CHECK ((mode = 'FIRST_COME') = (first_come_trigger IS NOT NULL AND first_come_limit IS NOT NULL)
        AND (first_come_trigger IS NULL OR first_come_trigger IN ('IDENTITY_VERIFIED', 'EMAIL_VERIFIED'))
        AND (first_come_limit IS NULL OR first_come_limit BETWEEN 1 AND 100000)
        AND first_come_count >= 0 AND (first_come_limit IS NULL OR first_come_count <= first_come_limit)),
    -- 후보 방법은 당첨자 선정에만(하나 이상), 댓글 추첨이면 글·기간이 있다
    CONSTRAINT ck_reward_events_selection CHECK ((mode = 'SELECTION') = (source_manual OR source_comment)
        AND source_comment = (draw_post_id IS NOT NULL AND draw_post_ref IS NOT NULL AND draw_from IS NOT NULL AND draw_to IS NOT NULL)
        AND (draw_from IS NULL OR draw_to > draw_from)),
    CONSTRAINT ck_reward_events_bulk CHECK ((mode = 'BULK') = (bulk_scope IS NOT NULL)
        AND (bulk_scope IS NULL OR bulk_scope IN ('ALL', 'CONDITION'))
        AND (bulk_active_days IS NULL OR bulk_active_days BETWEEN 1 AND 365)
        AND (bulk_joined_from IS NULL OR bulk_joined_to IS NULL OR bulk_joined_from <= bulk_joined_to)),
    CONSTRAINT ck_reward_events_canceled CHECK ((status = 'CANCELED') = (canceled_at IS NOT NULL) AND (canceled_at IS NULL) = (cancel_reason IS NULL)
        AND (cancel_reason IS NULL OR char_length(btrim(cancel_reason)) BETWEEN 5 AND 200))
);
CREATE INDEX ix_reward_events_list ON reward_events (status, no DESC);
-- 선착순 자동 지급이 찾는 이벤트(진행 중 선착순 — 기준 사건마다)
CREATE INDEX ix_reward_events_first_come ON reward_events (first_come_trigger, start_at) WHERE status = 'ACTIVE' AND mode = 'FIRST_COME';

-- ── 등수 · 보상 줄 ────────────────────────────────────────────────────────
CREATE TABLE reward_event_tiers (
    id        bigint      PRIMARY KEY,
    event_id  bigint      NOT NULL REFERENCES reward_events (id),
    position  smallint    NOT NULL,
    name      varchar(20) NOT NULL,
    quota     integer     NOT NULL DEFAULT 0,
    CONSTRAINT ck_reward_event_tiers_quota CHECK (quota BETWEEN 0 AND 100000),
    CONSTRAINT ck_reward_event_tiers_name CHECK (char_length(btrim(name)) BETWEEN 1 AND 20)
);
CREATE INDEX ix_reward_event_tiers_event ON reward_event_tiers (event_id, position);

CREATE TABLE reward_event_lines (
    id                 bigint       PRIMARY KEY,
    event_id           bigint       NOT NULL REFERENCES reward_events (id),
    tier_id            bigint       NOT NULL REFERENCES reward_event_tiers (id),
    position           smallint     NOT NULL,
    kind               varchar(14)  NOT NULL,
    points             integer,
    item_id            bigint       REFERENCES point_shop_items (id),
    item_name          varchar(100),
    item_kind          varchar(30),
    item_usage         varchar(12),
    item_duration_days integer,
    quantity           smallint,
    pack_id            bigint       REFERENCES emoticon_packs (id),
    pack_name          varchar(40),
    manual_name        varchar(40),
    manual_method      varchar(200),
    CONSTRAINT ck_reward_event_lines_kind CHECK (kind IN ('POINT', 'SHOP_ITEM', 'EMOTICON_PACK', 'MANUAL')),
    -- 종류마다 쓰는 칸만(그 밖은 비운다). 포인트 1~100,000P · 소모형 수량 1~10(그 밖은 1) · 직접 전달 이름 1~40자
    CONSTRAINT ck_reward_event_lines_shape CHECK (
        (kind = 'POINT') = (points IS NOT NULL) AND (points IS NULL OR points BETWEEN 1 AND 100000)
        AND (kind = 'SHOP_ITEM') = (item_id IS NOT NULL) AND (item_id IS NULL OR (quantity BETWEEN 1 AND 10 AND item_name IS NOT NULL))
        AND (kind = 'EMOTICON_PACK') = (pack_id IS NOT NULL)
        AND (kind = 'MANUAL') = (manual_name IS NOT NULL) AND (manual_name IS NULL OR char_length(btrim(manual_name)) BETWEEN 1 AND 40))
);
CREATE INDEX ix_reward_event_lines_tier ON reward_event_lines (tier_id, position);
CREATE INDEX ix_reward_event_lines_event ON reward_event_lines (event_id);

-- ── 추첨 ──────────────────────────────────────────────────────────────────
CREATE TABLE reward_event_draws (
    id              bigint      PRIMARY KEY,
    event_id        bigint      NOT NULL REFERENCES reward_events (id),
    tier_id         bigint      NOT NULL REFERENCES reward_event_tiers (id),
    operator_id     bigint      REFERENCES operators (id) ON DELETE SET NULL,
    post_id         bigint      NOT NULL,
    candidate_count integer     NOT NULL,
    picked          integer     NOT NULL,
    seed            bigint      NOT NULL,
    picked_user_ids bigint[]    NOT NULL,
    created_at      timestamptz NOT NULL,
    CONSTRAINT ck_reward_event_draws_count CHECK (picked >= 1 AND picked <= candidate_count AND cardinality(picked_user_ids) = picked)
);
CREATE INDEX ix_reward_event_draws_event ON reward_event_draws (event_id, id DESC);

-- ── 지급 실행 ─────────────────────────────────────────────────────────────
CREATE TABLE reward_event_runs (
    id              bigint       PRIMARY KEY,
    event_id        bigint       NOT NULL REFERENCES reward_events (id),
    kind            varchar(8)   NOT NULL,
    status          varchar(8)   NOT NULL,
    -- 일괄 지급의 단계: TARGETS(대상 줄 만들기 — 회원 번호 cursor 뒤로 이어 감) → LINES(보상 지급). 그 밖은 LINES
    phase           varchar(8)   NOT NULL DEFAULT 'LINES',
    cursor_user_id  bigint,
    operator_id     bigint       REFERENCES operators (id) ON DELETE SET NULL,
    idempotency_key varchar(100),
    day             date,
    target_count    integer      NOT NULL DEFAULT 0,
    done_count      integer      NOT NULL DEFAULT 0,
    success         integer      NOT NULL DEFAULT 0,
    failed          integer      NOT NULL DEFAULT 0,
    skipped         integer      NOT NULL DEFAULT 0,
    points          bigint       NOT NULL DEFAULT 0,
    items           integer      NOT NULL DEFAULT 0,
    reason          varchar(200),
    created_at      timestamptz  NOT NULL,
    updated_at      timestamptz  NOT NULL,
    finished_at     timestamptz,
    CONSTRAINT ck_reward_event_runs_kind CHECK (kind IN ('GRANT', 'RETRY', 'REVOKE', 'AUTO')),
    CONSTRAINT ck_reward_event_runs_status CHECK (status IN ('RUNNING', 'DONE') AND (status = 'DONE') = (finished_at IS NOT NULL)),
    CONSTRAINT ck_reward_event_runs_phase CHECK (phase IN ('TARGETS', 'LINES')),
    -- 선착순 자동은 날마다 한 줄(운영자 없음), 그 밖은 날이 없다
    CONSTRAINT ck_reward_event_runs_day CHECK ((kind = 'AUTO') = (day IS NOT NULL)),
    CONSTRAINT ck_reward_event_runs_counts CHECK (target_count >= 0 AND done_count >= 0 AND success >= 0 AND failed >= 0 AND skipped >= 0 AND points >= 0 AND items >= 0)
);
CREATE INDEX ix_reward_event_runs_event ON reward_event_runs (event_id, id DESC);
-- 지급 멱등 키 — 이벤트마다 유일(같은 키는 처음 실행을 돌려준다)
CREATE UNIQUE INDEX ux_reward_event_runs_key ON reward_event_runs (event_id, idempotency_key) WHERE idempotency_key IS NOT NULL;
-- 선착순 자동 지급은 날마다 한 줄
CREATE UNIQUE INDEX ux_reward_event_runs_auto_day ON reward_event_runs (event_id, day) WHERE kind = 'AUTO';
-- 나눠 처리하는 실행(지급·재시도)은 이벤트마다 동시에 하나
CREATE UNIQUE INDEX ux_reward_event_runs_running ON reward_event_runs (event_id) WHERE status = 'RUNNING';
-- 워커가 집는 실행
CREATE INDEX ix_reward_event_runs_running ON reward_event_runs (id) WHERE status = 'RUNNING';

-- ── 받는 사람 · 줄 결과 ───────────────────────────────────────────────────
CREATE TABLE reward_event_recipients (
    id            bigint       PRIMARY KEY,
    event_id      bigint       NOT NULL REFERENCES reward_events (id),
    tier_id       bigint       NOT NULL REFERENCES reward_event_tiers (id),
    user_id       bigint       NOT NULL,
    method        varchar(14)  NOT NULL,
    status        varchar(10)  NOT NULL DEFAULT 'PENDING',
    selected_at   timestamptz  NOT NULL,
    selected_by   bigint       REFERENCES operators (id) ON DELETE SET NULL,
    draw_id       bigint       REFERENCES reward_event_draws (id),
    queued_run_id bigint       REFERENCES reward_event_runs (id),
    revoke_reason varchar(200),
    revoked_at    timestamptz,
    revoked_by    bigint       REFERENCES operators (id) ON DELETE SET NULL,
    CONSTRAINT ux_reward_event_recipients_member UNIQUE (event_id, user_id),
    CONSTRAINT ck_reward_event_recipients_method CHECK (method IN ('FIRST_COME', 'MANUAL', 'COMMENT_DRAW', 'CONDITION', 'ALL')),
    CONSTRAINT ck_reward_event_recipients_status CHECK (status IN ('PENDING', 'GRANTED', 'PARTIAL', 'FAILED', 'SKIPPED', 'REVOKED')),
    CONSTRAINT ck_reward_event_recipients_revoked CHECK ((status = 'REVOKED') = (revoked_at IS NOT NULL) AND (revoked_at IS NULL) = (revoke_reason IS NULL))
);
CREATE INDEX ix_reward_event_recipients_event ON reward_event_recipients (event_id, status);
CREATE INDEX ix_reward_event_recipients_tier ON reward_event_recipients (tier_id);
CREATE INDEX ix_reward_event_recipients_queue ON reward_event_recipients (queued_run_id, id) WHERE queued_run_id IS NOT NULL;
CREATE INDEX ix_reward_event_recipients_draw ON reward_event_recipients (draw_id) WHERE draw_id IS NOT NULL;

CREATE TABLE reward_event_line_results (
    id             bigint       PRIMARY KEY,
    event_id       bigint       NOT NULL REFERENCES reward_events (id),
    recipient_id   bigint       NOT NULL REFERENCES reward_event_recipients (id),
    line_id        bigint       NOT NULL REFERENCES reward_event_lines (id),
    user_id        bigint       NOT NULL,
    kind           varchar(14)  NOT NULL,
    status         varchar(16)  NOT NULL DEFAULT 'PENDING',
    reason         varchar(20),
    at             timestamptz,
    -- 지급한 포인트(POINT) · 지급한 아이템 수(SHOP_ITEM 수량 · 팩 1) — 숫자·실행 합계의 원천
    points         integer      NOT NULL DEFAULT 0,
    items          integer      NOT NULL DEFAULT 0,
    revoked_points integer,
    delivered_at   timestamptz,
    delivered_by   bigint       REFERENCES operators (id) ON DELETE SET NULL,
    delivered_memo varchar(200),
    CONSTRAINT ux_reward_event_line_results_line UNIQUE (recipient_id, line_id),
    CONSTRAINT ck_reward_event_line_results_kind CHECK (kind IN ('POINT', 'SHOP_ITEM', 'EMOTICON_PACK', 'MANUAL')),
    CONSTRAINT ck_reward_event_line_results_status CHECK (status IN ('PENDING', 'SUCCESS', 'FAILED', 'SKIPPED', 'REVOKED', 'DELIVERY_PENDING', 'DELIVERED')),
    CONSTRAINT ck_reward_event_line_results_reason CHECK (reason IS NULL OR reason IN ('POINT_FROZEN', 'WITHDRAWN', 'SUSPENDED', 'ITEM_UNAVAILABLE', 'SERVER_ERROR',
        'ALREADY_OWNED', 'PACK_ALREADY_OWNED', 'ITEM_USED', 'POINT_PARTIAL', 'MANUAL_RECORD_ONLY')),
    -- 직접 전달 줄만 전달 대기·완료가 있고, 완료면 시각이 있다
    CONSTRAINT ck_reward_event_line_results_manual CHECK ((status IN ('DELIVERY_PENDING', 'DELIVERED')) <= (kind = 'MANUAL')
        AND (status = 'DELIVERED') = (delivered_at IS NOT NULL)),
    CONSTRAINT ck_reward_event_line_results_amounts CHECK (points >= 0 AND items >= 0 AND (revoked_points IS NULL OR revoked_points BETWEEN 0 AND points))
);
CREATE INDEX ix_reward_event_line_results_recipient ON reward_event_line_results (recipient_id);
CREATE INDEX ix_reward_event_line_results_event ON reward_event_line_results (event_id, status);

-- ── 원장 사유 · 대상 ──────────────────────────────────────────────────────
-- 사유: V142 목록 ∪ EVENT_REWARD(이벤트 보상 — EARN, 사유·종류 짝의 ELSE 갈래) · EVENT_REWARD_REVOKE(이벤트 보상 회수 — REVOKE, 무상에서만 0P 바닥)
ALTER TABLE point_ledger DROP CONSTRAINT ck_point_ledger_reason;
ALTER TABLE point_ledger ADD CONSTRAINT ck_point_ledger_reason CHECK (reason IN (
    'ATTENDANCE', 'ATTENDANCE_STREAK', 'POST', 'COMMENT', 'POPULAR', 'POST_REVOKE', 'COMMENT_REVOKE', 'POST_RESTORE', 'TICKET_PURCHASE', 'ADMIN_ADJUST',
    'DEAL_END_REQUEST', 'LIKE_BONUS', 'LIKE_BONUS_REVOKE', 'SIGNUP',
    'CHARGE', 'CHARGE_BONUS', 'CHARGE_BONUS_REVOKE', 'CHARGE_REFUND', 'CHARGE_REFUND_RESTORE', 'CHARGE_BONUS_RESTORE',
    'POINT_SHOP_PURCHASE', 'POINT_SHOP_REFUND', 'GB_POINT_USE', 'GB_POINT_RESTORE',
    'REVOKE_DEBT_OFFSET', 'EXPIRE', 'WITHDRAW_FORFEIT',
    'EMOTICON_CREATOR_REWARD',
    'EVENT_REWARD', 'EVENT_REWARD_REVOKE'));
-- 사유와 종류의 짝: V85 ∪ EVENT_REWARD_REVOKE(REVOKE 만 — ELSE 갈래에서도 뺀다)
ALTER TABLE point_ledger DROP CONSTRAINT ck_point_ledger_reason_kind;
ALTER TABLE point_ledger ADD CONSTRAINT ck_point_ledger_reason_kind CHECK (CASE kind
    WHEN 'USE' THEN reason IN ('TICKET_PURCHASE', 'POINT_SHOP_PURCHASE', 'GB_POINT_USE')
    WHEN 'CANCEL_USE' THEN reason IN ('POINT_SHOP_REFUND', 'GB_POINT_RESTORE')
    WHEN 'EXPIRE' THEN reason IN ('EXPIRE', 'WITHDRAW_FORFEIT')
    WHEN 'REFUND' THEN reason IN ('CHARGE_REFUND')
    WHEN 'REVOKE' THEN reason IN ('POST_REVOKE', 'COMMENT_REVOKE', 'LIKE_BONUS_REVOKE', 'CHARGE_BONUS_REVOKE', 'ADMIN_ADJUST', 'REVOKE_DEBT_OFFSET', 'EVENT_REWARD_REVOKE')
    ELSE reason NOT IN ('TICKET_PURCHASE', 'POINT_SHOP_PURCHASE', 'GB_POINT_USE', 'POINT_SHOP_REFUND', 'GB_POINT_RESTORE', 'EXPIRE', 'WITHDRAW_FORFEIT',
                        'CHARGE_REFUND', 'POST_REVOKE', 'COMMENT_REVOKE', 'LIKE_BONUS_REVOKE', 'CHARGE_BONUS_REVOKE', 'REVOKE_DEBT_OFFSET', 'EVENT_REWARD_REVOKE')
END);
-- 대상: V142 목록 ∪ REWARD_EVENT_RESULT(그 줄 결과 — 원장 유일 (사유, 대상)으로 같은 줄은 한 번만)
ALTER TABLE point_ledger DROP CONSTRAINT ck_point_ledger_ref;
ALTER TABLE point_ledger ADD CONSTRAINT ck_point_ledger_ref CHECK (ref_type IS NULL OR ref_type IN ('POST', 'COMMENT', 'POINT_SHOP_PURCHASE', 'POINT_CHARGE',
    'GB_PARTICIPATION', 'LOT', 'LEDGER', 'USER', 'ATTENDANCE', 'DEAL_END_REQUEST', 'EMOTICON_ACQUIRE', 'REWARD_EVENT_RESULT'));
ALTER TABLE point_lots DROP CONSTRAINT ck_point_lots_source_ref;
ALTER TABLE point_lots ADD CONSTRAINT ck_point_lots_source_ref CHECK (source_ref_type IS NULL OR source_ref_type IN ('POST', 'COMMENT', 'POINT_SHOP_PURCHASE',
    'POINT_CHARGE', 'GB_PARTICIPATION', 'LOT', 'LEDGER', 'USER', 'ATTENDANCE', 'DEAL_END_REQUEST', 'EMOTICON_ACQUIRE', 'REWARD_EVENT_RESULT'));

-- ── 보유 아이템: 지급 출처(구매 또는 이벤트 줄 결과 중 정확히 하나) ──────────
-- 소모형 수량 N 은 N 줄 — 같은 줄 결과에 순번 1..N(유일 — 같은 줄을 두 번 지급하지 않는다). 영구 아이템 부분 유일 색인은 그대로라 이미 가진 영구 아이템은 넣지 못한다.
-- 지급 줄은 구매가 없어 셀프·예외 환불 경로(구매 기준)에 걸리지 않는다.
ALTER TABLE user_items ALTER COLUMN purchase_id DROP NOT NULL;
ALTER TABLE user_items
    ADD COLUMN grant_result_id bigint REFERENCES reward_event_line_results (id),
    ADD COLUMN grant_seq       smallint;
ALTER TABLE user_items ADD CONSTRAINT ck_user_items_source CHECK ((purchase_id IS NULL) <> (grant_result_id IS NULL)
                                                               AND (grant_result_id IS NULL) = (grant_seq IS NULL) AND (grant_seq IS NULL OR grant_seq BETWEEN 1 AND 10));
CREATE UNIQUE INDEX ux_user_items_grant ON user_items (grant_result_id, grant_seq) WHERE grant_result_id IS NOT NULL;

-- V157 의 보호 + 지급 출처도 고치지 않는다
CREATE OR REPLACE FUNCTION user_items_guard() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF NEW.user_id IS DISTINCT FROM OLD.user_id OR NEW.item_id IS DISTINCT FROM OLD.item_id OR NEW.purchase_id IS DISTINCT FROM OLD.purchase_id
       OR NEW.grant_result_id IS DISTINCT FROM OLD.grant_result_id OR NEW.grant_seq IS DISTINCT FROM OLD.grant_seq
       OR NEW.usage_type IS DISTINCT FROM OLD.usage_type OR NEW.starts_at IS DISTINCT FROM OLD.starts_at OR NEW.expires_at IS DISTINCT FROM OLD.expires_at
       OR NEW.created_at IS DISTINCT FROM OLD.created_at THEN
        RAISE EXCEPTION 'user_items identity is fixed';
    END IF;
    IF OLD.first_used_at IS NOT NULL AND NEW.first_used_at IS DISTINCT FROM OLD.first_used_at THEN
        RAISE EXCEPTION 'user_items first_used_at is kept';
    END IF;
    IF NEW.status IS DISTINCT FROM OLD.status AND NOT ((OLD.status = 'OWNED' AND NEW.status IN ('USED', 'REVOKED')) OR (OLD.status = 'USED' AND NEW.status = 'REVOKED')) THEN
        RAISE EXCEPTION 'user_items status cannot go from % to %', OLD.status, NEW.status;
    END IF;
    IF OLD.status <> 'OWNED' AND (NEW.ref_type IS DISTINCT FROM OLD.ref_type OR NEW.ref_id IS DISTINCT FROM OLD.ref_id) THEN
        RAISE EXCEPTION 'user_items ref of a used ticket is kept';
    END IF;
    RETURN NEW;
END $$;

-- ── 로보티콘 받기: 이벤트 출처 ─────────────────────────────────────────────
-- (회원, 팩) 하나에 줄 하나 그대로. 이벤트로 받은 줄은 kind = EVENT + 그 줄 결과(reward_result_id). 회수하면 removed_at(이벤트로 받은 줄만).
ALTER TABLE emoticon_acquisitions ADD COLUMN reward_result_id bigint REFERENCES reward_event_line_results (id);
ALTER TABLE emoticon_acquisitions DROP CONSTRAINT ck_emoticon_acquisitions_kind;
ALTER TABLE emoticon_acquisitions ADD CONSTRAINT ck_emoticon_acquisitions_kind CHECK (kind IN ('FREE', 'PURCHASE', 'EVENT')
    AND (kind = 'PURCHASE') = (purchase_id IS NOT NULL) AND (kind = 'EVENT') = (reward_result_id IS NOT NULL));
CREATE UNIQUE INDEX ux_emoticon_acquisitions_reward ON emoticon_acquisitions (reward_result_id) WHERE reward_result_id IS NOT NULL;

-- ── 회원 알림 종류: V157 목록 ∪ REWARD_EVENT_GRANTED · REWARD_EVENT_REVOKED ──
ALTER TABLE member_notifications DROP CONSTRAINT ck_member_notifications_type;
ALTER TABLE member_notifications ADD CONSTRAINT ck_member_notifications_type
    CHECK (type IN ('COMMENT', 'REPLY', 'POINTS_REVOKED', 'POST_HIDDEN', 'COMMENT_HIDDEN', 'DM', 'NICKNAME_IMAGE_REVIEWED', 'OPINION_PIN_REQUESTED',
                    'OPINION_PIN_REVIEWED', 'POST_REMOVED', 'COMMENT_REMOVED', 'REPORT_RESULT', 'FEATURE_RESTRICTED', 'POINTS_EXPIRING',
                    'POINT_SHOP_REFUND_APPROVED', 'POINT_SHOP_REFUND_REJECTED',
                    'GROUP_APPROVED', 'GROUP_REJECTED', 'GROUP_JOIN_REQUEST', 'GROUP_JOIN_APPROVED', 'GROUP_JOIN_REJECTED', 'GROUP_KICKED',
                    'GROUP_DISSOLVED', 'GROUP_LEADER_TRANSFERRED', 'GROUP_DEACTIVATED', 'GROUP_REACTIVATED',
                    'GROUP_POST_UNPUBLISHED',
                    'GROUP_MANAGER_APPOINTED', 'GROUP_MANAGER_PERMISSIONS_CHANGED', 'GROUP_MANAGER_REMOVED', 'GROUP_MANAGER_RESIGNED',
                    'GROUP_KICKED_BY_MANAGER',
                    'GROUP_EVENT',
                    'GROUP_PUBLISH_REQUESTED', 'GROUP_PUBLISH_APPROVED', 'GROUP_PUBLISH_REJECTED', 'GROUP_POST_PUBLISHED', 'GROUP_VISIBILITY_CHANGED',
                    'GROUP_POST_MEMBERS_ONLY', 'GROUP_POST_RELEASED',
                    'GROUP_BUY',
                    'BUSINESS',
                    'BIZ_APPLICATION',
                    'DEAL_FLAGGED', 'DEAL_AUTO_ENDED', 'DEAL_MANAGER_ENDED', 'DEAL_EXPIRED', 'DEAL_REOPENED_FIX_ENDS',
                    'SANCTION_ISSUED', 'SANCTION_LIFTED', 'APPEAL_ANSWERED',
                    'BOARD_SANCTION_ISSUED',
                    'EMOTICON_PACK',
                    'EMOTICON_PURCHASE',
                    'AVATAR_UPLOAD_REVIEWED',
                    'REWARD_EVENT_GRANTED', 'REWARD_EVENT_REVOKED'));
