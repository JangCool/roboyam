-- 관리자 알림(종 알림 서랍 + 이벤트 관리) — docs/features/notification-contract.md
--
-- 이벤트의 **정의(코드·영역·심각도·수신 규칙·필수 여부·링크)는 코드(AdminNotificationEvent)가 원천**이다. notification_events 는 운영자가 바꾸는 값(사용 여부·이메일 전달)만 들고,
-- 서버가 뜰 때 코드와 맞춘다(없는 이벤트만 기본값으로 넣고, 코드에서 사라진 이벤트의 행은 지운다 — 운영자가 바꾼 값은 덮어쓰지 않는다).
-- 알림은 이벤트가 일어난 시점에 수신 규칙으로 운영자를 정해 **운영자마다 한 행**(fan-out)을 만든다. 문구는 키 + 인자로 저장하고 읽을 때 요청 언어로 푼다(배치 실행 기록과 같은 방식).

-- ── 이벤트 설정(운영자가 바꾸는 값) ──────────────────────────────────────────
-- enabled: 이 이벤트를 발행할지(끄면 알림 행이 만들어지지 않는다 — 필수 이벤트는 끌 수 없다). email_enabled: 이메일로도 보낼지(이벤트가 emailable 일 때만 켤 수 있다).
CREATE TABLE notification_events (
    code          varchar(60)  PRIMARY KEY,
    enabled       boolean      NOT NULL,
    email_enabled boolean      NOT NULL,
    updated_at    timestamptz  NOT NULL DEFAULT now(),
    updated_by    bigint       REFERENCES operators (id) ON DELETE SET NULL,
    CONSTRAINT ck_notification_events_code CHECK (code ~ '^[A-Z][A-Z0-9_]{0,59}$')
);

-- ── 알림(수신자별 행) ───────────────────────────────────────────────────────
-- severity: INFO · SUCCESS · WARNING · CRITICAL (발행 때 값 — 이벤트가 상황에 따라 올릴 수 있다).
-- title_key/body_key/args: 문구 키 + 인자(JSON 배열 — 각 원소는 글자 또는 {"key": "문구 키"} 라서 읽을 때 요청 언어로 풀린다). link_path: 발행 때 인자를 채운 이동 경로(없으면 NULL).
-- dedupe_key: 같은 이벤트·같은 키를 N초 안에 한 건으로 묶는 데 쓰는 키(이벤트의 collapse 초). read_at: 읽은 시각(NULL 이면 안 읽음).
-- operator_id 에 외래키(ON DELETE CASCADE) — 운영자 행이 지워지면(반려한 신청) 알림도 함께 지운다. event_code 에는 외래키가 없다(코드에서 사라진 이벤트의 알림은 보관 정책이 지운다).
CREATE TABLE admin_notifications (
    id          bigint       PRIMARY KEY,
    operator_id bigint       NOT NULL REFERENCES operators (id) ON DELETE CASCADE,
    event_code  varchar(60)  NOT NULL,
    severity    varchar(8)   NOT NULL,
    title_key   varchar(120) NOT NULL,
    body_key    varchar(120) NOT NULL,
    args        jsonb,
    link_path   varchar(300),
    dedupe_key  varchar(120),
    created_at  timestamptz  NOT NULL DEFAULT now(),
    read_at     timestamptz,
    CONSTRAINT ck_admin_notifications_severity CHECK (severity IN ('INFO', 'SUCCESS', 'WARNING', 'CRITICAL'))
);
-- 서랍 목록(최근순, 커서 = id)
CREATE INDEX ix_admin_notifications_operator ON admin_notifications (operator_id, id DESC);
-- 안 읽은 수
CREATE INDEX ix_admin_notifications_unread ON admin_notifications (operator_id) WHERE read_at IS NULL;
-- 이벤트별 최근 발송 수(이벤트 관리 화면)와 묶기 조회
CREATE INDEX ix_admin_notifications_event ON admin_notifications (event_code, created_at DESC);
-- 정리 배치(notification-cleanup): 읽은 지 오래된 것 / 안 읽은 채 오래된 것
CREATE INDEX ix_admin_notifications_read_at ON admin_notifications (read_at) WHERE read_at IS NOT NULL;
CREATE INDEX ix_admin_notifications_unread_created ON admin_notifications (created_at) WHERE read_at IS NULL;

-- ── 개인별 끄기 ─────────────────────────────────────────────────────────────
-- 운영자가 이벤트별로 끈 것(행이 있으면 끔). 필수 이벤트는 끌 수 없어 행이 만들어지지 않고, 만들어져 있어도 무시한다. 운영자가 지워지면 함께 지운다.
CREATE TABLE operator_notification_mutes (
    operator_id bigint       NOT NULL REFERENCES operators (id) ON DELETE CASCADE,
    event_code  varchar(60)  NOT NULL,
    created_at  timestamptz  NOT NULL DEFAULT now(),
    PRIMARY KEY (operator_id, event_code)
);

-- ── 배치 실패·종료 알림 감시자 ─────────────────────────────────────────────
-- notified_at: 알림 감시자(관리자 서버, 30초 주기)가 이 실행 줄을 처리한 시각. 끝난(finished_at) 줄 중 NULL 인 것만 본다 — Java 배치와 DB 배치(저장 프로시저)가 같은 표를 쓰므로 한 경로다.
-- 이 마이그레이션 이전에 끝난 줄은 지금 시각으로 채운다 — 감시가 시작될 때 과거 실패가 한꺼번에 알림이 되지 않게(실행 중인 줄은 비워 둔다. 끝난 뒤 처리된다).
ALTER TABLE batch_runs ADD COLUMN notified_at timestamptz;
UPDATE batch_runs SET notified_at = now() WHERE finished_at IS NOT NULL;
CREATE INDEX ix_batch_runs_unnotified ON batch_runs (id) WHERE notified_at IS NULL;
