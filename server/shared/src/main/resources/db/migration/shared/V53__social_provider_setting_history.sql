-- 소셜 로그인 공급자 설정 변경 이력 — docs/features/login-contract.md 「관리자 소셜 로그인 설정」
--
-- social_provider_settings(V3) 를 저장할 때마다 한 줄. 설치 마법사의 소셜 단계(source INSTALL)와 관리자 설정 화면(source ADMIN)이 남긴다.
--   * 같은 값으로 다시 저장하면 줄을 만들지 않는다(앱이 가린다).
--   * changes = [{field, before, after}] — field 는 clientId · clientSecret · enabled. 값은 모두 글자다.
--       clientId     원문 대신 끝 4자리만 보이게 가린 값('****' || 끝 4자리, 4자 이하면 '****')
--       clientSecret 값은 절대 없다. SET(처음 넣음) · CHANGED(바꿈) · CLEARED(지움, 설정 삭제) 표시만
--       enabled      'true' · 'false'
--     만들기는 before 가 null, 지우기는 after 가 null 이다.
--   * operator_id 는 바꾼 운영자(operators.id). 운영자가 지워져도 이력은 남도록 FK 를 걸지 않는다(audit_logs.actor_id 와 같다).
--   * 보관은 무기한(정리 배치 없음). 메일 설정 이력(mail_setting_history)과 같은 모양의 별도 표다.
CREATE TABLE social_provider_setting_history (
    id          bigint      PRIMARY KEY,                     -- Snowflake(앱 발급)
    provider    varchar(16) NOT NULL,                        -- KAKAO · NAVER · GOOGLE
    action      varchar(10) NOT NULL,                        -- CREATE · UPDATE · DELETE
    source      varchar(10) NOT NULL,                        -- INSTALL · ADMIN
    operator_id bigint,
    changed_at  timestamptz NOT NULL,
    changes     jsonb       NOT NULL,
    CONSTRAINT ck_social_provider_setting_history_provider CHECK (provider IN ('NAVER', 'KAKAO', 'GOOGLE')),
    CONSTRAINT ck_social_provider_setting_history_action CHECK (action IN ('CREATE', 'UPDATE', 'DELETE')),
    CONSTRAINT ck_social_provider_setting_history_source CHECK (source IN ('INSTALL', 'ADMIN'))
);

CREATE INDEX ix_social_provider_setting_history_changed ON social_provider_setting_history (changed_at DESC, id DESC);
CREATE INDEX ix_social_provider_setting_history_provider ON social_provider_setting_history (provider, changed_at DESC, id DESC);

-- 이미 설치된 DB: 지금 저장된 값을 「설치 때 넣은 값」으로 첫 줄(INSTALL · CREATE)에 남긴다. 시각은 설치 시각, 모르면 행의 저장 시각.
-- (이 마이그레이션 전에는 관리자 수정 API 가 없었으므로 지금 값이 곧 설치 때 값이다)
-- ID 는 그 시각의 Snowflake(EPOCH 2026-01-01 UTC, 노드 1023 — V30 과 같은 마이그레이션용 노드) + 공급자 순번.
INSERT INTO social_provider_setting_history (id, provider, action, source, operator_id, changed_at, changes)
SELECT ((greatest(0, (extract(epoch FROM t.at) * 1000)::bigint - 1767225600000)) << 22) | (1023::bigint << 12) | t.rn,
       t.provider, 'CREATE', 'INSTALL', t.updated_by, t.at,
       jsonb_build_array(
               jsonb_build_object('field', 'clientId', 'before', NULL,
                                  'after', CASE WHEN length(t.client_id) > 4 THEN '****' || right(t.client_id, 4) ELSE '****' END),
               jsonb_build_object('field', 'clientSecret', 'before', NULL, 'after', 'SET'),
               jsonb_build_object('field', 'enabled', 'before', NULL, 'after', CASE WHEN t.enabled THEN 'true' ELSE 'false' END))
FROM (SELECT s.provider, s.client_id, s.enabled, s.updated_by,
             coalesce((SELECT i.installed_at FROM installation i WHERE i.id = 1), s.updated_at) AS at,
             row_number() OVER (ORDER BY s.provider) - 1 AS rn
      FROM social_provider_settings s) t;
