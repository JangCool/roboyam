-- 메일 서버 설정 변경 이력 (docs/features/mail-settings-contract.md)
--
-- 설정을 저장할 때마다(값이 실제로 바뀐 때만) 한 줄. 설치 마법사의 첫 저장은 출처 INSTALL, 관리자 화면의 저장은 ADMIN 이다.
--   · changes: 바뀐 칸 목록(JSON 배열, 칸 차례 host · port · security · username · password · fromAddress).
--       보통 칸   {"field":"host","before":"이전 값"|null,"after":"새 값"|null}  — 값은 모두 글자(포트도)
--       비밀번호  {"field":"password","change":"SET"|"CHANGED"|"CLEARED"}      — 값(평문·암호문)은 싣지 않는다
--   · operator_id: 바꾼 운영자. 운영자가 지워지면 NULL(화면은 「삭제된 운영자」).
--   · 보관: 무기한(값이 실제로 바뀐 저장만 남고 행이 작다).
CREATE TABLE mail_setting_history (
    id          bigint      PRIMARY KEY,
    changed_at  timestamptz NOT NULL,
    operator_id bigint      REFERENCES operators (id) ON DELETE SET NULL,
    source      varchar(8)  NOT NULL,
    changes     jsonb       NOT NULL,
    CONSTRAINT ck_mail_setting_history_source CHECK (source IN ('INSTALL', 'ADMIN')),
    CONSTRAINT ck_mail_setting_history_changes CHECK (jsonb_typeof(changes) = 'array' AND jsonb_array_length(changes) > 0)
);

CREATE INDEX ix_mail_setting_history_changed ON mail_setting_history (changed_at DESC, id DESC);

-- 이미 설치된 DB: 지금 값(= 설치 마법사가 넣은 값 — 설치 뒤 바꾸는 길이 없었다)을 INSTALL 첫 줄로 남긴다. 시각·운영자는 그 행의 마지막 저장(설치 때)이다.
-- ID 는 앱의 Snowflake 와 같은 모양(시각 ms 41비트 | 서버 번호 10비트 | 순번 12비트)이고 서버 번호는 마이그레이션 전용 1023 이다(V30 과 같다).
INSERT INTO mail_setting_history (id, changed_at, operator_id, source, changes)
SELECT ((floor(extract(epoch FROM clock_timestamp()) * 1000)::bigint - 1767225600000) << 22) | (1023::bigint << 12),
       m.updated_at,
       m.updated_by,
       'INSTALL',
       jsonb_build_array(
               jsonb_build_object('field', 'host', 'before', NULL, 'after', m.host),
               jsonb_build_object('field', 'port', 'before', NULL, 'after', m.port::text),
               jsonb_build_object('field', 'security', 'before', NULL, 'after', m.security))
           || CASE WHEN m.username IS NULL THEN '[]'::jsonb
                   ELSE jsonb_build_array(jsonb_build_object('field', 'username', 'before', NULL, 'after', m.username)) END
           || CASE WHEN m.password_enc IS NULL THEN '[]'::jsonb
                   ELSE jsonb_build_array(jsonb_build_object('field', 'password', 'change', 'SET')) END
           || jsonb_build_array(jsonb_build_object('field', 'fromAddress', 'before', NULL, 'after', m.from_address))
  FROM mail_settings m
 WHERE m.id = 1;
