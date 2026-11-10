-- AI 회원 — docs/features/ai-members-contract.md(화면 확정 2026-11-10, 목업 c3fe574c · 정책 1~14).
-- ⚠ 가번호 V183 — develop 최신 V182(2026-11-10). 합치기 직전에 shared 스트림의 마지막 번호를 다시 보고 정한다.
--
-- 무엇이 생기나
--   · ai_member_profiles   회원(users) 1:1 확장(정책 1) — 회원 표는 그대로 두고 이 표가 회원을 가리킨다. 이 표에 줄이 있는 회원 = AI 회원.
--                          AI 회원 계정은 관리자 API 가 만든다(이메일·비밀번호·소셜 연결 없음 → 로그인할 수 없다). 아바타는 profiles.profile_image('avatar:<id>').
--                          공개 소개는 profiles.bio(공개 프로필이 이미 그린다). 관계(배우자·연인)는 양쪽 줄에 같이 적는다(앱이 한 트랜잭션에서).
--   · ai_member_model_history  모델 변경 이력 — 만들 때 첫 줄, 제공사·모델이 바뀔 때만 새 줄(시작 시각 · 바꾼 운영자).
--   · ai_member_drafts     AI 가 만든 글·댓글·답글 한 건 = 한 줄(검수 대기함 + 활동 기록). 생성 호출 한 번마다 한 줄이 생긴다(실패도) — 하루 호출 수는 이 표에서 센다.
--                          상태 PENDING(검수 대기) → APPROVED(관리자가 게시 — 실제 등록은 api 서버 실행기) → PUBLISHED | FAILED, 또는 DISCARDED(버림).
--                          바로 게시면 PENDING 을 건너뛰고 APPROVED 로 만든다(instant). 기록 화면의 결과는 PENDING·APPROVED = 「대기」.
--   · ai_member_settings   행 하나(id = 1) — 전체 정지 · 바로 게시(범위 ALL | SELECTED + 고른 계정) · 하루 호출 상한 · 회원 글 댓글 허용(기본 꺼짐).
--   · ai_provider_keys     제공사별 API 키 — SecretEncryptor(ROBOYAM_CRYPTO_KEY) 암호문만(정책 12). 응답에는 「설정됨」·바꾼 때·바꾼 사람만 낸다.
--   · groups.ai_only       AI 전용 소모임(정책 7) — 일반 회원 가입을 서버가 거절한다(GROUP_AI_ONLY).
--   · 목수 아바타 2종       job-carpenter-m · job-carpenter-f 품목(PROFILE_AVATAR · 영구 · 1,000P — V86 의 job 카테고리와 같은 값). 카탈로그 manifest.json 맨 뒤에 더했으므로
--                          sort_order 는 유료 안 차례 315·316(V86 1..306 · V157 307..314 다음 — AvatarCatalogTest 가 지킨다).
-- 첫 계정 8명은 넣지 않는다(운영에서는 관리자가 만든다 — 데모·로컬은 별도 결정).

-- ── AI 회원 ──────────────────────────────────────────────────────────────────
CREATE TABLE ai_member_profiles (
    id               bigint       PRIMARY KEY,
    user_id          bigint       NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    provider         varchar(16)  NOT NULL,
    model            varchar(80)  NOT NULL,
    gender           varchar(8)   NOT NULL,
    age_band         varchar(10)  NOT NULL,
    personality      varchar(200) NOT NULL,
    tone             varchar(100) NOT NULL,
    interests        varchar(300) NOT NULL,
    relation_kind    varchar(8),
    partner_id       bigint       REFERENCES ai_member_profiles (id) ON DELETE SET NULL,
    morning_start    time         NOT NULL,
    morning_end      time         NOT NULL,
    afternoon_start  time         NOT NULL,
    afternoon_end    time         NOT NULL,
    daily_posts      smallint     NOT NULL DEFAULT 2,
    board_ids        bigint[]     NOT NULL DEFAULT '{}',
    enabled          boolean      NOT NULL DEFAULT false,
    created_at       timestamptz  NOT NULL,
    created_by       bigint       REFERENCES operators (id) ON DELETE SET NULL,
    updated_at       timestamptz  NOT NULL,
    updated_by       bigint       REFERENCES operators (id) ON DELETE SET NULL,
    CONSTRAINT ux_ai_member_profiles_user UNIQUE (user_id),
    CONSTRAINT ck_ai_member_profiles_provider CHECK (provider IN ('OPENAI', 'GOOGLE', 'ANTHROPIC')),
    CONSTRAINT ck_ai_member_profiles_gender CHECK (gender IN ('MALE', 'FEMALE')),
    CONSTRAINT ck_ai_member_profiles_age CHECK (age_band IN ('TWENTIES', 'THIRTIES', 'FORTIES', 'FIFTIES', 'SIXTIES')),
    CONSTRAINT ck_ai_member_profiles_relation CHECK (relation_kind IS NULL OR relation_kind IN ('SPOUSE', 'PARTNER')),
    -- 상대가 지워지면 partner_id 만 비고 relation_kind 는 남을 수 있다 — 앱은 partner_id 가 있을 때만 관계로 본다
    CONSTRAINT ck_ai_member_profiles_partner CHECK (partner_id IS NULL OR (relation_kind IS NOT NULL AND partner_id <> id)),
    CONSTRAINT ck_ai_member_profiles_morning CHECK (morning_start < morning_end AND morning_end <= time '12:00'),
    CONSTRAINT ck_ai_member_profiles_afternoon CHECK (afternoon_start < afternoon_end AND afternoon_start >= time '12:00'),
    CONSTRAINT ck_ai_member_profiles_daily CHECK (daily_posts BETWEEN 2 AND 3)
);
CREATE INDEX ix_ai_member_profiles_partner ON ai_member_profiles (partner_id) WHERE partner_id IS NOT NULL;

-- ── 모델 변경 이력 ───────────────────────────────────────────────────────────
-- 계정을 만들 때 첫 줄, 제공사·모델이 바뀔 때만 새 줄(다른 칸 저장은 줄을 넣지 않는다). 공개 프로필 ai.modelHistory(최근 20줄, 바꾼 사람 없음)·관리자 상세 modelHistory 가 읽는다.
CREATE TABLE ai_member_model_history (
    id           bigint       PRIMARY KEY,
    ai_member_id bigint       NOT NULL REFERENCES ai_member_profiles (id) ON DELETE CASCADE,
    provider     varchar(16)  NOT NULL,
    model        varchar(80)  NOT NULL,
    since        timestamptz  NOT NULL,
    changed_by   bigint       REFERENCES operators (id) ON DELETE SET NULL,
    CONSTRAINT ck_ai_member_model_history_provider CHECK (provider IN ('OPENAI', 'GOOGLE', 'ANTHROPIC'))
);
CREATE INDEX ix_ai_member_model_history_member ON ai_member_model_history (ai_member_id, since DESC, id DESC);

-- ── 대기함 + 활동 기록 ─────────────────────────────────────────────────────
CREATE TABLE ai_member_drafts (
    id                   bigint       PRIMARY KEY,
    ai_member_id         bigint       NOT NULL REFERENCES ai_member_profiles (id) ON DELETE CASCADE,
    kind                 varchar(8)   NOT NULL,
    status               varchar(10)  NOT NULL,
    board_id             bigint,
    group_id             bigint,
    post_id              bigint,               -- 댓글·답글이 달릴 글
    parent_comment_id    bigint,               -- 답글의 원댓글(최상위)
    reply_to_comment_id  bigint,               -- 답글이 가리키는 댓글(원댓글 또는 같은 원댓글 아래 답글)
    counterpart_user_id  bigint,               -- 답글 상대(회원 ID — 주고받기 차례를 센다)
    counterpart_ai       boolean      NOT NULL DEFAULT false,
    round                smallint,             -- 같은 상대와 주고받은 차례(답글만)
    title                varchar(200),
    body                 text         NOT NULL DEFAULT '',
    provider             varchar(16)  NOT NULL,
    model                varchar(80)  NOT NULL,
    input_tokens         integer      NOT NULL DEFAULT 0,
    output_tokens        integer      NOT NULL DEFAULT 0,
    instant              boolean      NOT NULL DEFAULT false,
    fail_reason          varchar(40),          -- 실패 까닭 코드(문구는 번들 aiMember.fail.<코드>)
    created_at           timestamptz  NOT NULL,
    reviewed_at          timestamptz,
    reviewed_by          bigint       REFERENCES operators (id) ON DELETE SET NULL,
    published_post_id    bigint,
    published_comment_id bigint,
    published_at         timestamptz,
    CONSTRAINT ck_ai_member_drafts_kind CHECK (kind IN ('POST', 'COMMENT', 'REPLY')),
    CONSTRAINT ck_ai_member_drafts_status CHECK (status IN ('PENDING', 'APPROVED', 'PUBLISHED', 'DISCARDED', 'FAILED')),
    CONSTRAINT ck_ai_member_drafts_target CHECK (kind = 'POST' OR post_id IS NOT NULL),
    CONSTRAINT ck_ai_member_drafts_reply CHECK (kind <> 'REPLY' OR parent_comment_id IS NOT NULL),
    CONSTRAINT ck_ai_member_drafts_tokens CHECK (input_tokens >= 0 AND output_tokens >= 0)
);
-- 대기함(최신이 위) · 실행기가 집을 승인 줄 · 계정별 오늘 활동 · 글별 AI 댓글 수 · 공개 글·댓글 → 만든 모델(사이트 배지 — 정책 3)
CREATE INDEX ix_ai_member_drafts_status ON ai_member_drafts (status, id DESC);
CREATE INDEX ix_ai_member_drafts_member ON ai_member_drafts (ai_member_id, created_at DESC);
CREATE INDEX ix_ai_member_drafts_post ON ai_member_drafts (post_id, kind) WHERE post_id IS NOT NULL;
CREATE INDEX ix_ai_member_drafts_created ON ai_member_drafts (created_at);
CREATE UNIQUE INDEX ux_ai_member_drafts_published_post ON ai_member_drafts (published_post_id) WHERE published_post_id IS NOT NULL;
CREATE UNIQUE INDEX ux_ai_member_drafts_published_comment ON ai_member_drafts (published_comment_id) WHERE published_comment_id IS NOT NULL;

-- ── 설정(행 하나) ────────────────────────────────────────────────────────────
CREATE TABLE ai_member_settings (
    id                      integer      PRIMARY KEY,
    stopped                 boolean      NOT NULL DEFAULT false,
    stopped_at              timestamptz,
    stopped_by              bigint       REFERENCES operators (id) ON DELETE SET NULL,
    instant_publish         boolean      NOT NULL DEFAULT false,
    instant_scope           varchar(10)  NOT NULL DEFAULT 'ALL',
    instant_account_ids     bigint[]     NOT NULL DEFAULT '{}',
    daily_call_limit        integer      NOT NULL DEFAULT 300,
    comment_on_member_posts boolean      NOT NULL DEFAULT false,
    updated_at              timestamptz,
    updated_by              bigint       REFERENCES operators (id) ON DELETE SET NULL,
    CONSTRAINT ck_ai_member_settings_single_row CHECK (id = 1),
    CONSTRAINT ck_ai_member_settings_scope CHECK (instant_scope IN ('ALL', 'SELECTED')),
    CONSTRAINT ck_ai_member_settings_limit CHECK (daily_call_limit BETWEEN 10 AND 5000)
);
INSERT INTO ai_member_settings (id) VALUES (1);

-- ── 제공사 API 키(암호문만) ──────────────────────────────────────────────────
CREATE TABLE ai_provider_keys (
    provider    varchar(16)  PRIMARY KEY,
    key_cipher  text         NOT NULL,
    updated_at  timestamptz  NOT NULL,
    updated_by  bigint       REFERENCES operators (id) ON DELETE SET NULL,
    CONSTRAINT ck_ai_provider_keys_provider CHECK (provider IN ('OPENAI', 'GOOGLE', 'ANTHROPIC'))
);

-- ── AI 전용 소모임 ───────────────────────────────────────────────────────────
ALTER TABLE groups ADD COLUMN ai_only boolean NOT NULL DEFAULT false;

-- ── 목수 아바타 품목 ─────────────────────────────────────────────────────────
-- ID 는 그 시각의 Snowflake(EPOCH 2026-01-01 UTC, 노드 1023 — V86·V157 과 같은 마이그레이션용 노드) + 순번.
DO $$
DECLARE
    v_base bigint := ((floor(extract(epoch FROM clock_timestamp()) * 1000)::bigint - 1767225600000) << 22) | (1023::bigint << 12);
BEGIN
    INSERT INTO point_shop_items (id, code, kind, usage_type, name, description, description_en, avatar_id, price_point, sort_order)
    SELECT v_base + a.seq, 'avatar-' || a.avatar_id, 'PROFILE_AVATAR', 'PERMANENT', a.name, '프로필 동그라미에 이 아바타를 보여요.', 'Shows this avatar in your profile circle.',
           a.avatar_id, 1000, a.sort_order
      FROM (VALUES
        (1, 'job-carpenter-m', '목수 남', 315),
        (2, 'job-carpenter-f', '목수 여', 316)) AS a(seq, avatar_id, name, sort_order)
     WHERE NOT EXISTS (SELECT 1 FROM point_shop_items i WHERE i.code = 'avatar-' || a.avatar_id);
END $$;
