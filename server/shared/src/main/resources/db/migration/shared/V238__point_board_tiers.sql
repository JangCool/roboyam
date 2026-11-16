-- 게시판 등급별 포인트(docs/features/point-board-tier-contract.md, 2026-11-17)
--
-- 1) point_settings — 게시판 등급(일반 NORMAL · 정보 INFO · 핵심 글 CORE)별 금액·조건 칸 + 스위치 두 칸. 범위는 모두 0~10,000 정수(기존 정책 칸과 같다).
--    일반 등급의 「쓰면 바로」·「최소 글자 수」는 새 칸이 아니라 기존 post_points · post_min_length 를 그대로 쓴다(화면이 그렇게 묶었다).
--    info_source_once: 정보 등급 글은 같은 출처 링크로 한 번만 적립(기본 켬) · exclude_ai_and_ops: AI 회원·운영팀 계정은 적립 안 함(기본 켬).
ALTER TABLE point_settings
    ADD COLUMN tier_normal_like_bonus_points integer NOT NULL DEFAULT 10,
    ADD COLUMN tier_normal_featured_points   integer NOT NULL DEFAULT 0,
    ADD COLUMN tier_normal_min_images        integer NOT NULL DEFAULT 0,
    ADD COLUMN tier_normal_hold_hours        integer NOT NULL DEFAULT 0,
    ADD COLUMN tier_info_post_points         integer NOT NULL DEFAULT 30,
    ADD COLUMN tier_info_like_bonus_points   integer NOT NULL DEFAULT 50,
    ADD COLUMN tier_info_featured_points     integer NOT NULL DEFAULT 100,
    ADD COLUMN tier_info_min_length          integer NOT NULL DEFAULT 100,
    ADD COLUMN tier_info_min_images          integer NOT NULL DEFAULT 0,
    ADD COLUMN tier_info_hold_hours          integer NOT NULL DEFAULT 24,
    ADD COLUMN tier_core_post_points         integer NOT NULL DEFAULT 50,
    ADD COLUMN tier_core_like_bonus_points   integer NOT NULL DEFAULT 150,
    ADD COLUMN tier_core_featured_points     integer NOT NULL DEFAULT 500,
    ADD COLUMN tier_core_min_length          integer NOT NULL DEFAULT 500,
    ADD COLUMN tier_core_min_images          integer NOT NULL DEFAULT 1,
    ADD COLUMN tier_core_hold_hours          integer NOT NULL DEFAULT 24,
    ADD COLUMN info_source_once              boolean NOT NULL DEFAULT true,
    ADD COLUMN exclude_ai_and_ops            boolean NOT NULL DEFAULT true;
ALTER TABLE point_settings ADD CONSTRAINT ck_point_settings_board_tiers CHECK (
    tier_normal_like_bonus_points BETWEEN 0 AND 10000 AND tier_normal_featured_points BETWEEN 0 AND 10000
    AND tier_normal_min_images BETWEEN 0 AND 10000 AND tier_normal_hold_hours BETWEEN 0 AND 10000
    AND tier_info_post_points BETWEEN 0 AND 10000 AND tier_info_like_bonus_points BETWEEN 0 AND 10000 AND tier_info_featured_points BETWEEN 0 AND 10000
    AND tier_info_min_length BETWEEN 0 AND 10000 AND tier_info_min_images BETWEEN 0 AND 10000 AND tier_info_hold_hours BETWEEN 0 AND 10000
    AND tier_core_post_points BETWEEN 0 AND 10000 AND tier_core_like_bonus_points BETWEEN 0 AND 10000 AND tier_core_featured_points BETWEEN 0 AND 10000
    AND tier_core_min_length BETWEEN 0 AND 10000 AND tier_core_min_images BETWEEN 0 AND 10000 AND tier_core_hold_hours BETWEEN 0 AND 10000);
-- 일반 등급의 추천 문턱 보너스는 지금 전역 추천 보너스 금액에서 시작한다(운영자가 바꾼 값이 있으면 그 값 — 소급 없음)
UPDATE point_settings SET tier_normal_like_bonus_points = like_bonus_points;

-- 2) 게시판 pointMode: ON · OFF → OFF · NORMAL · INFO · CORE(기존 ON = NORMAL). 게시판 값 NULL = 기본값 따름은 그대로.
ALTER TABLE board_defaults DROP CONSTRAINT ck_board_defaults_point_mode;
ALTER TABLE boards DROP CONSTRAINT ck_boards_point_mode;
UPDATE board_defaults SET point_mode = 'NORMAL' WHERE point_mode = 'ON';
UPDATE boards SET point_mode = 'NORMAL' WHERE point_mode = 'ON';
ALTER TABLE board_defaults ADD CONSTRAINT ck_board_defaults_point_mode CHECK (point_mode IN ('OFF', 'NORMAL', 'INFO', 'CORE'));
ALTER TABLE boards ADD CONSTRAINT ck_boards_point_mode CHECK (point_mode IN ('OFF', 'NORMAL', 'INFO', 'CORE'));
-- 기본 배정(계약 「기본 배정」): 정보 = 뉴스(slug news)·핫딜(종류 DEAL) · 핵심 글 = 팁·강좌(slug tip)·사용기(slug review). 그 밖은 기본값(일반) 그대로.
-- 운영자가 끈 게시판(OFF)·기본값이 꺼진 사이트에서 기본값을 따르는 게시판은 건드리지 않는다(포인트를 새로 켜지 않는다).
UPDATE boards b SET point_mode = 'INFO'
 WHERE (b.slug = 'news' OR b.kind = 'DEAL')
   AND (b.point_mode = 'NORMAL' OR (b.point_mode IS NULL AND (SELECT d.point_mode FROM board_defaults d WHERE d.id = 1) <> 'OFF'));
UPDATE boards b SET point_mode = 'CORE'
 WHERE b.slug IN ('tip', 'review') AND b.kind = 'GENERAL'
   AND (b.point_mode = 'NORMAL' OR (b.point_mode IS NULL AND (SELECT d.point_mode FROM board_defaults d WHERE d.id = 1) <> 'OFF'));

-- 3) 원장 사유 — V235 목록 ∪ POST_FEATURED(EARN — 우수 글 선정, 대상 글, 원장 유일로 글마다 한 번) · POST_FEATURED_REVOKE(REVOKE — 확정 뒤 글 삭제 회수)
ALTER TABLE point_ledger DROP CONSTRAINT ck_point_ledger_reason;
ALTER TABLE point_ledger ADD CONSTRAINT ck_point_ledger_reason CHECK (reason IN (
    'ATTENDANCE', 'ATTENDANCE_STREAK', 'POST', 'COMMENT', 'POPULAR', 'POST_REVOKE', 'COMMENT_REVOKE', 'POST_RESTORE', 'TICKET_PURCHASE', 'ADMIN_ADJUST',
    'DEAL_END_REQUEST', 'LIKE_BONUS', 'LIKE_BONUS_REVOKE', 'SIGNUP',
    'CHARGE', 'CHARGE_BONUS', 'CHARGE_BONUS_REVOKE', 'CHARGE_REFUND', 'CHARGE_REFUND_RESTORE', 'CHARGE_BONUS_RESTORE',
    'POINT_SHOP_PURCHASE', 'POINT_SHOP_REFUND', 'GB_POINT_USE', 'GB_POINT_RESTORE',
    'REVOKE_DEBT_OFFSET', 'EXPIRE', 'WITHDRAW_FORFEIT',
    'EMOTICON_CREATOR_REWARD',
    'EVENT_REWARD', 'EVENT_REWARD_REVOKE',
    'ROULETTE_SPIN', 'LOTTO_DRAW', 'ROULETTE_WIN',
    'POST_FEATURED', 'POST_FEATURED_REVOKE'));
ALTER TABLE point_ledger DROP CONSTRAINT ck_point_ledger_reason_kind;
ALTER TABLE point_ledger ADD CONSTRAINT ck_point_ledger_reason_kind CHECK (CASE kind
    WHEN 'USE' THEN reason IN ('TICKET_PURCHASE', 'POINT_SHOP_PURCHASE', 'GB_POINT_USE', 'ROULETTE_SPIN', 'LOTTO_DRAW')
    WHEN 'CANCEL_USE' THEN reason IN ('POINT_SHOP_REFUND', 'GB_POINT_RESTORE')
    WHEN 'EXPIRE' THEN reason IN ('EXPIRE', 'WITHDRAW_FORFEIT')
    WHEN 'REFUND' THEN reason IN ('CHARGE_REFUND')
    WHEN 'REVOKE' THEN reason IN ('POST_REVOKE', 'COMMENT_REVOKE', 'LIKE_BONUS_REVOKE', 'CHARGE_BONUS_REVOKE', 'ADMIN_ADJUST', 'REVOKE_DEBT_OFFSET', 'EVENT_REWARD_REVOKE',
                                  'POST_FEATURED_REVOKE')
    ELSE reason NOT IN ('TICKET_PURCHASE', 'POINT_SHOP_PURCHASE', 'GB_POINT_USE', 'POINT_SHOP_REFUND', 'GB_POINT_RESTORE', 'EXPIRE', 'WITHDRAW_FORFEIT',
                        'CHARGE_REFUND', 'POST_REVOKE', 'COMMENT_REVOKE', 'LIKE_BONUS_REVOKE', 'CHARGE_BONUS_REVOKE', 'REVOKE_DEBT_OFFSET', 'EVENT_REWARD_REVOKE',
                        'ROULETTE_SPIN', 'LOTTO_DRAW', 'POST_FEATURED_REVOKE')
END);

-- 4) 보류 지급 — 추천 문턱 보너스(LIKE_BONUS)·우수 글 선정(POST_FEATURED)을 게시판 등급의 보류 시간 뒤에 확정한다(계약 「보류 확정 방식」).
--    금액은 보류를 시작한 순간의 정책값으로 고정. 취소는 글·댓글 삭제(본인·운영자)·신고·운영자 숨김·선정 취소의 같은 트랜잭션에서, 확정은 admin 5분 배치(point-pending-release).
--    같은 (사유, 대상)에 PENDING 은 하나. users 외래키는 두지 않는다(포인트 새 표 규칙 — 탈퇴 정리는 회원 쪽이 한다).
CREATE TABLE point_pending_grants (
    id            bigint       PRIMARY KEY,
    user_id       bigint       NOT NULL,
    reason        varchar(32)  NOT NULL,
    ref_type      varchar(16)  NOT NULL,
    ref_id        bigint       NOT NULL,
    amount        integer      NOT NULL,
    release_at    timestamptz  NOT NULL,
    status        varchar(16)  NOT NULL DEFAULT 'PENDING',
    cancel_reason varchar(32),
    created_at    timestamptz  NOT NULL,
    settled_at    timestamptz,
    CONSTRAINT ck_point_pending_grants_reason CHECK (reason IN ('LIKE_BONUS', 'POST_FEATURED')),
    CONSTRAINT ck_point_pending_grants_ref CHECK (ref_type IN ('POST', 'COMMENT') AND (reason <> 'POST_FEATURED' OR ref_type = 'POST')),
    CONSTRAINT ck_point_pending_grants_amount CHECK (amount > 0),
    CONSTRAINT ck_point_pending_grants_status CHECK (status IN ('PENDING', 'RELEASED', 'CANCELLED')),
    CONSTRAINT ck_point_pending_grants_cancel CHECK ((status = 'CANCELLED') = (cancel_reason IS NOT NULL) AND (status = 'PENDING') = (settled_at IS NULL))
);
CREATE UNIQUE INDEX ux_point_pending_grants_pending ON point_pending_grants (reason, ref_type, ref_id) WHERE status = 'PENDING';
CREATE INDEX ix_point_pending_grants_due ON point_pending_grants (release_at, id) WHERE status = 'PENDING';
CREATE INDEX ix_point_pending_grants_user ON point_pending_grants (user_id, release_at) WHERE status = 'PENDING';
CREATE INDEX ix_point_pending_grants_ref ON point_pending_grants (ref_type, ref_id);

-- 5) 우수 글 선정 기록 — 운영자가 글을 우수 글로 뽑은 이력(취소해도 지우지 않는다). 한 글에 살아 있는 선정은 하나.
--    회원 칭호(member-title-contract 「이달의 작가 후보」)가 달마다 선정된 글(featured_at)을 이 표에서 읽는다 — 글쓴이는 선정 때의 글쓴이(author_id, 비회원 글 NULL).
CREATE TABLE post_features (
    id              bigint       PRIMARY KEY,
    post_id         bigint       NOT NULL,
    author_id       bigint,
    featured_by     bigint       NOT NULL,
    featured_at     timestamptz  NOT NULL,
    unfeatured_by   bigint,
    unfeatured_at   timestamptz,
    CONSTRAINT ck_post_features_unfeatured CHECK ((unfeatured_at IS NULL) = (unfeatured_by IS NULL))
);
CREATE UNIQUE INDEX ux_post_features_active ON post_features (post_id) WHERE unfeatured_at IS NULL;
CREATE INDEX ix_post_features_featured_at ON post_features (featured_at);

-- 6) 정보 등급 「같은 출처 링크는 한 번만 적립」 — 글 적립을 받은 출처 링크 열쇠(핫딜 링크의 같은 딜 키 · 글 출처 주소를 같은 규칙으로 정규화한 SHA-256).
--    사이트 전체에서 한 열쇠는 한 번(지운 글로 받았어도 남는다 — 하루 한도가 지운 글을 세는 것과 같다).
CREATE TABLE point_source_claims (
    source_key  varchar(64)  PRIMARY KEY,
    post_id     bigint       NOT NULL,
    user_id     bigint       NOT NULL,
    created_at  timestamptz  NOT NULL
);

-- 7) 인기글 보상 — 날짜(한국 시간)마다 보상 대상으로 뽑은 글(뽑은 순위 그대로 남긴다 — 다시 돌려도 다시 뽑지 않는다). 지급은 원장 POPULAR(대상 글, 원장 유일).
CREATE TABLE point_popular_rewards (
    day         date         NOT NULL,
    rank        integer      NOT NULL,
    post_id     bigint       NOT NULL,
    author_id   bigint,
    score       double precision NOT NULL,
    created_at  timestamptz  NOT NULL,
    CONSTRAINT pk_point_popular_rewards PRIMARY KEY (day, rank),
    CONSTRAINT ux_point_popular_rewards_post UNIQUE (day, post_id),
    CONSTRAINT ck_point_popular_rewards_rank CHECK (rank >= 1)
);

COMMENT ON TABLE point_pending_grants IS '포인트 보류 지급(추천 문턱 보너스·우수 글) — V238 point-board-tier-contract';
COMMENT ON TABLE post_features IS '우수 글 선정 이력 — V238';
COMMENT ON TABLE point_source_claims IS '정보 등급 같은 출처 링크 1회 적립 열쇠 — V238';
COMMENT ON TABLE point_popular_rewards IS '날짜별 인기글 보상 대상 — V238';
