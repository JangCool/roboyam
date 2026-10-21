-- 운영자 비밀번호 재설정 — 승인번호와 요청 한도 기록. docs/features/roles-contract.md 5단계
--
-- operator_cert_codes : 승인번호(6자리 숫자) 한 건. 이메일(해시 키)마다 최대 한 행 — 새로 받으면 같은 행을 새 번호로 바꾼다(이전 번호 무효).
--   * 숫자 6자리는 해시만으로 지킬 수 없다(100만 가지) — 보호는 짧은 유효(10분)·5번 틀리면 소진·요청 한도다. 원문은 어디에도 남기지 않는다(메일에만 있다).
--   * operator_id 가 NULL 인 행은 「가짜 행」이다. 없는 이메일·잠긴/미승인/기간 밖 계정에도 같은 모양의 행을 만들어(메일은 보내지 않는다) 승인번호 확인 응답이
--     계정 존재 여부를 알리지 않게 한다. 가짜 행의 번호는 아무에게도 전달되지 않아 맞출 수 없다.
--   * 이메일은 해시(sha256("admin-cert:"+정규화한 이메일))로만 둔다. 운영자가 지워지면 행도 함께 지워진다.
-- operator_cert_requests : 승인번호 요청 한도 기록. 요청이 받아들여질 때마다 이메일 키·IP 키 각각 한 줄(키는 접두어를 붙인 sha256 해시). 쿨다운(60초)과 시간당 한도를 센다.
--   login_attempts 를 재사용하지 않은 이유: 그 표는 「연속 실패 횟수 → 잠금」 한 행이라 시각별 요청을 세는 창(window)·쿨다운을 담을 수 없다.
CREATE TABLE operator_cert_codes (
    id          bigint      PRIMARY KEY,
    email_key   char(64)    NOT NULL,
    operator_id bigint      REFERENCES operators (id) ON DELETE CASCADE,
    code_salt   char(32)    NOT NULL,
    code_hash   char(64)    NOT NULL,
    expires_at  timestamptz NOT NULL,
    fail_count  int         NOT NULL DEFAULT 0,
    used_at     timestamptz,
    created_at  timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT ck_operator_cert_codes_fail CHECK (fail_count >= 0)
);
CREATE UNIQUE INDEX ux_operator_cert_codes_email ON operator_cert_codes (email_key);
CREATE INDEX ix_operator_cert_codes_expires ON operator_cert_codes (expires_at);
CREATE INDEX ix_operator_cert_codes_operator ON operator_cert_codes (operator_id) WHERE operator_id IS NOT NULL;

CREATE TABLE operator_cert_requests (
    id           bigint      PRIMARY KEY,
    key_hash     char(64)    NOT NULL,
    requested_at timestamptz NOT NULL
);
CREATE INDEX ix_operator_cert_requests_key ON operator_cert_requests (key_hash, requested_at DESC);
CREATE INDEX ix_operator_cert_requests_at ON operator_cert_requests (requested_at);
