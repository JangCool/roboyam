-- AI 회원 — 모델 목록 표 · 결제·크레딧 부족 처리(docs/features/ai-members-contract.md 「모델 목록·결제 부족 (2026-11-11)」).
-- ⚠ 가번호 V189 — develop 최신 V188(2026-11-11). 합치기 직전에 shared 스트림의 마지막 번호를 다시 보고 정한다.
--
-- 무엇이 생기나
--   · ai_models                고를 수 있는 모델의 정본(옛 배포 설정 roboyam.ai-member.models.* 를 대신한다). 관리자 설정 화면 「모델 목록」이 켜고 끄기·기본·추가·지우기를 한다.
--                              enabled = 계정 상세에서 고를 수 있다 · is_default = 새 계정에 처음 들어가는 모델(제공사당 하나, 켜진 모델만).
--                              꺼졌거나 표에 없는 모델을 쓰는 계정은 쉰다(MODEL_UNAVAILABLE — 다시 골라야 돈다). 가격은 100만 토큰당 USD 참고값(2026-11-11 각 사 공식 문서).
--   · ai_provider_keys.status  OK | BILLING_REQUIRED | KEY_INVALID — 제공사가 결제·크레딧 부족이나 키 오류로 거절하면 실행기가 바꾸고 그 제공사의 계정을 쉬게 한다.
--                              키를 다시 저장하면 OK 로 돌아온다. status_detail = 제공사 메시지(앞 300자 — 키 모양·개인정보는 가린다), status_at = 바뀐 시각.
--   · ai_member_drafts.fail_detail  실패 줄의 제공사 메시지(앞 300자, 가린 것). 옛 실패 줄은 비어 있다.

-- ── 모델 목록 ───────────────────────────────────────────────────────────────
CREATE TABLE ai_models (
    id           bigint        PRIMARY KEY,
    provider     varchar(16)   NOT NULL,
    model_id     varchar(64)   NOT NULL,
    label        varchar(100),
    price_input  numeric(10, 4),
    price_output numeric(10, 4),
    free_tier    boolean       NOT NULL DEFAULT false,
    enabled      boolean       NOT NULL DEFAULT true,
    is_default   boolean       NOT NULL DEFAULT false,
    sort_order   integer       NOT NULL DEFAULT 0,
    created_at   timestamptz   NOT NULL,
    created_by   bigint        REFERENCES operators (id) ON DELETE SET NULL,
    updated_at   timestamptz   NOT NULL,
    updated_by   bigint        REFERENCES operators (id) ON DELETE SET NULL,
    CONSTRAINT ux_ai_models_provider_model UNIQUE (provider, model_id),
    CONSTRAINT ck_ai_models_provider CHECK (provider IN ('OPENAI', 'GOOGLE', 'ANTHROPIC')),
    CONSTRAINT ck_ai_models_model_id CHECK (model_id ~ '^[A-Za-z0-9._-]{1,64}$'),
    CONSTRAINT ck_ai_models_default_enabled CHECK (NOT is_default OR enabled),
    CONSTRAINT ck_ai_models_price CHECK ((price_input IS NULL OR price_input >= 0) AND (price_output IS NULL OR price_output >= 0))
);
-- 제공사당 기본 하나
CREATE UNIQUE INDEX ux_ai_models_default ON ai_models (provider) WHERE is_default;

-- 처음 행 — 2026-11-11 각 사 공식 문서 확인값. ID 는 그 시각의 Snowflake(EPOCH 2026-01-01 UTC, 노드 1023 — V86·V157·V183 과 같은 마이그레이션용 노드) + 순번.
DO $$
DECLARE
    v_now  timestamptz := clock_timestamp();
    v_base bigint := ((floor(extract(epoch FROM clock_timestamp()) * 1000)::bigint - 1767225600000) << 22) | (1023::bigint << 12);
BEGIN
    INSERT INTO ai_models (id, provider, model_id, label, price_input, price_output, free_tier, enabled, is_default, sort_order, created_at, updated_at)
    SELECT v_base + m.seq, m.provider, m.model_id, m.label, m.price_in, m.price_out, m.free_tier, m.enabled, m.is_default, m.seq * 10, v_now, v_now
      FROM (VALUES
        ( 1, 'OPENAI',    'gpt-6-luna',            '가장 저렴·빠름',     0.10,  0.50, false, true,  true),
        ( 2, 'OPENAI',    'gpt-6.1-terra',         '빠르고 효율적',      1.00,  5.00, false, true,  false),
        ( 3, 'OPENAI',    'gpt-6.1-sol',           '균형·고성능',        2.00, 10.00, false, true,  false),
        ( 4, 'OPENAI',    'gpt-6-astra',           '최고 성능·비쌈',    10.00, 50.00, false, false, false),
        ( 5, 'GOOGLE',    'gemini-3.1-flash-lite', NULL,                 0.25,  1.50, true,  true,  false),
        ( 6, 'GOOGLE',    'gemini-3.5-flash-lite', NULL,                 0.30,  2.50, true,  true,  true),
        ( 7, 'GOOGLE',    'gemini-3.6-flash',      NULL,                 0.75,  3.75, true,  true,  false),
        ( 8, 'GOOGLE',    'gemini-3.8-flash',      '새 모델',            0.75,  3.75, true,  true,  false),
        ( 9, 'GOOGLE',    'gemini-4-argon',        '유료 등급만',        2.00, 10.00, false, false, false),
        (10, 'GOOGLE',    'gemini-4-carbon',       '유료 등급만',        2.00, 10.00, false, false, false),
        (11, 'ANTHROPIC', 'claude-haiku-5-5',      NULL,                 0.10,  0.50, false, true,  true),
        (12, 'ANTHROPIC', 'claude-sonnet-5-5',     NULL,                 2.00, 10.00, false, true,  false),
        (13, 'ANTHROPIC', 'claude-opus-5-5',       NULL,                 4.00, 20.00, false, false, false),
        (14, 'ANTHROPIC', 'claude-fable-5-2',      NULL,                 8.00, 40.00, false, false, false))
        AS m(seq, provider, model_id, label, price_in, price_out, free_tier, enabled, is_default);

    -- 지금 계정이 쓰는 모델이 목록에 없으면 「사용」으로 넣는다(빠뜨리면 그 계정이 바로 쉰다). 형식에 맞지 않는 이름은 넣지 못한다 — 그 계정은 모델을 다시 골라야 한다
    INSERT INTO ai_models (id, provider, model_id, label, price_input, price_output, free_tier, enabled, is_default, sort_order, created_at, updated_at)
    SELECT v_base + 100 + row_number() OVER (ORDER BY u.provider, u.model), u.provider, u.model, NULL, NULL, NULL, false, true, false,
           1000 + row_number() OVER (ORDER BY u.provider, u.model), v_now, v_now
      FROM (SELECT DISTINCT provider, model FROM ai_member_profiles) u
     WHERE u.model ~ '^[A-Za-z0-9._-]{1,64}$'
       AND NOT EXISTS (SELECT 1 FROM ai_models m WHERE m.provider = u.provider AND m.model_id = u.model);
END $$;

-- ── 제공사 쉬게 하기 ─────────────────────────────────────────────────────────
ALTER TABLE ai_provider_keys
    ADD COLUMN status        varchar(20)  NOT NULL DEFAULT 'OK',
    ADD COLUMN status_detail varchar(300),
    ADD COLUMN status_at     timestamptz,
    ADD CONSTRAINT ck_ai_provider_keys_status CHECK (status IN ('OK', 'BILLING_REQUIRED', 'KEY_INVALID'));

-- ── 실패 상세 ────────────────────────────────────────────────────────────────
ALTER TABLE ai_member_drafts ADD COLUMN fail_detail varchar(300);
