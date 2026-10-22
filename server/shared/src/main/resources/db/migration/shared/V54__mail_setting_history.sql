-- 메일 서버 설정 변경 이력 — docs/features/mail-settings-contract.md (소셜 로그인 설정 이력 V53 과 같은 모양의 별도 표)
--
-- mail_settings(V4) 를 저장할 때마다(값이 실제로 바뀐 때만) 한 줄. 설치 마법사의 첫 저장은 source INSTALL, 관리자 화면은 ADMIN.
--   * action: CREATE(행을 처음 만듦) · UPDATE(고침) · DELETE(지움 — 메일 설정 지우기는 아직 없다, 소셜 이력과 CHECK 를 같게 둔다)
--   * changes = [{field, before, after}] — field 차례 host · port · security · username · password · fromAddress. 값은 모두 글자다(포트도).
--       password 값은 절대 없다. 처음 넣음 null → SET, 바꿈 SET → CHANGED, 지움 SET → CLEARED 표시만
--     만들기는 before 가 null 이다.
--   * operator_id: 바꾼 운영자(operators.id). 운영자가 지워져도 이력은 남도록 FK 를 걸지 않는다(audit_logs.actor_id · V53 과 같다).
--   * 보관: 무기한(값이 실제로 바뀐 저장만 남고 행이 작다).
CREATE TABLE mail_setting_history (
    id          bigint      PRIMARY KEY,                     -- Snowflake(앱 발급)
    action      varchar(10) NOT NULL,
    source      varchar(10) NOT NULL,
    operator_id bigint,
    changed_at  timestamptz NOT NULL,
    changes     jsonb       NOT NULL,
    CONSTRAINT ck_mail_setting_history_action CHECK (action IN ('CREATE', 'UPDATE', 'DELETE')),
    CONSTRAINT ck_mail_setting_history_source CHECK (source IN ('INSTALL', 'ADMIN')),
    CONSTRAINT ck_mail_setting_history_changes CHECK (jsonb_typeof(changes) = 'array' AND jsonb_array_length(changes) > 0)
);

CREATE INDEX ix_mail_setting_history_changed ON mail_setting_history (changed_at DESC, id DESC);

-- 이미 설치된 DB: 지금 값(= 설치 마법사가 넣은 값 — 이 마이그레이션 전에는 설치 뒤 바꾸는 길이 없었다)을 INSTALL · CREATE 첫 줄로 남긴다.
-- 시각은 설치 시각, 모르면 행의 저장 시각. 운영자는 그 행을 저장한 운영자(설치 때 루트).
-- ID 는 그 시각의 Snowflake(EPOCH 2026-01-01 UTC, 노드 1023 — V30 · V53 과 같은 마이그레이션용 노드), 순번 0.
INSERT INTO mail_setting_history (id, action, source, operator_id, changed_at, changes)
SELECT ((greatest(0, (extract(epoch FROM t.at) * 1000)::bigint - 1767225600000)) << 22) | (1023::bigint << 12),
       'CREATE', 'INSTALL', t.updated_by, t.at,
       jsonb_build_array(
               jsonb_build_object('field', 'host', 'before', NULL, 'after', t.host),
               jsonb_build_object('field', 'port', 'before', NULL, 'after', t.port::text),
               jsonb_build_object('field', 'security', 'before', NULL, 'after', t.security))
           || CASE WHEN t.username IS NULL THEN '[]'::jsonb
                   ELSE jsonb_build_array(jsonb_build_object('field', 'username', 'before', NULL, 'after', t.username)) END
           || CASE WHEN t.password_enc IS NULL THEN '[]'::jsonb
                   ELSE jsonb_build_array(jsonb_build_object('field', 'password', 'before', NULL, 'after', 'SET')) END
           || jsonb_build_array(jsonb_build_object('field', 'fromAddress', 'before', NULL, 'after', t.from_address))
FROM (SELECT m.host, m.port, m.security, m.username, m.password_enc, m.from_address, m.updated_by,
             coalesce((SELECT i.installed_at FROM installation i WHERE i.id = 1), m.updated_at) AS at
      FROM mail_settings m
      WHERE m.id = 1) t;
