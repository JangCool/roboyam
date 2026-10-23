-- 회원 이벤트 설정 — docs/features/message-template-contract.md 「서버 구현 결과(회원 이벤트·이메일 확장)」
--
-- 회원 이벤트(공개 사이트 회원이 받는 알림의 일)의 정의는 코드(MemberEvent)가 원천이고, 이 표는 운영자가 바꾸는 값만 든다.
--   enabled             사용 여부
--   in_app              앱 알림(회원 알림함)으로 보낸다
--   email               이메일로도 보낸다 — 이번에는 저장만(회원 메일 발송은 다음 단계)
--   member_can_turn_off 회원이 개인 설정에서 끌 수 있다(코드가 잠근 이벤트는 늘 false — 앱이 지킨다)
-- 줄이 없으면 코드의 처음 값이다. 코드는 한 번 정하면 바꾸지 않는다.
CREATE TABLE member_event_settings (
    code                varchar(40) PRIMARY KEY,
    enabled             boolean     NOT NULL,
    in_app              boolean     NOT NULL,
    email               boolean     NOT NULL,
    member_can_turn_off boolean     NOT NULL,
    updated_at          timestamptz NOT NULL,
    updated_by          bigint      REFERENCES operators (id) ON DELETE SET NULL,
    CONSTRAINT ck_member_event_settings_code CHECK (code ~ '^[A-Z][A-Z0-9_]*$')
);

-- 데이터 이전: 게시판 기본 설정 「알림」(board_notification_settings.member_defaults — 옛 칸)의 종류별 값을 이 표로 옮긴다.
--   이름 대응 COMMENT ← COMMENT_ON_POST · REPLY ← REPLY_ON_COMMENT · MENTION · POST_POPULAR · OPERATOR_ACTION · STAFF_ACTION · REPORT_RESULT ← 같은 이름.
--   in_app ← site, email ← email, member_can_turn_off ← memberCanTurnOff(잠긴 이벤트는 false), enabled ← site OR email(둘 다 꺼져 있으면 나가는 것이 없었다).
--   옛 칸에 그 종류가 없거나 값이 boolean 이 아니면 코드의 처음 값. 옛 칸에 대응이 없는 이벤트(포인트 회수·글 숨김·댓글 숨김)는 처음 값.
--   바꾼 운영자·시각은 옛 행의 마지막 저장(없으면 지금).
-- 옛 칸 member_defaults 는 지우지 않는다(이미 배포된 앱과의 호환) — 앱은 더 이상 읽지도 쓰지도 않는다.
WITH catalog (code, board_kind, ready, locked) AS (
    VALUES ('COMMENT', 'COMMENT_ON_POST', true, false),
           ('REPLY', 'REPLY_ON_COMMENT', true, false),
           ('POINTS_REVOKED', NULL, true, true),
           ('POST_HIDDEN', NULL, true, true),
           ('COMMENT_HIDDEN', NULL, true, true),
           ('MENTION', 'MENTION', false, false),
           ('POST_POPULAR', 'POST_POPULAR', false, false),
           ('OPERATOR_ACTION', 'OPERATOR_ACTION', false, true),
           ('STAFF_ACTION', 'STAFF_ACTION', false, true),
           ('REPORT_RESULT', 'REPORT_RESULT', false, false)
),
old AS (
    SELECT r.value ->> 'kind' AS kind,
           CASE WHEN jsonb_typeof(r.value -> 'site') = 'boolean' THEN (r.value ->> 'site')::boolean END AS site,
           CASE WHEN jsonb_typeof(r.value -> 'email') = 'boolean' THEN (r.value ->> 'email')::boolean END AS email,
           CASE WHEN jsonb_typeof(r.value -> 'memberCanTurnOff') = 'boolean' THEN (r.value ->> 'memberCanTurnOff')::boolean END AS can_off
    FROM board_notification_settings s
             CROSS JOIN LATERAL jsonb_array_elements(CASE WHEN jsonb_typeof(s.member_defaults) = 'array' THEN s.member_defaults ELSE '[]'::jsonb END) r
    WHERE s.id = 1 AND jsonb_typeof(r.value) = 'object'
),
saved AS (
    SELECT updated_at, updated_by FROM board_notification_settings WHERE id = 1
)
INSERT INTO member_event_settings (code, enabled, in_app, email, member_can_turn_off, updated_at, updated_by)
SELECT c.code,
       CASE WHEN o.kind IS NULL OR o.site IS NULL OR o.email IS NULL THEN c.ready ELSE (o.site OR o.email) END,
       coalesce(o.site, true),
       coalesce(o.email, false),
       CASE WHEN c.locked THEN false ELSE coalesce(o.can_off, true) END,
       coalesce((SELECT updated_at FROM saved), now()),
       CASE WHEN o.kind IS NULL THEN NULL ELSE (SELECT updated_by FROM saved) END
FROM catalog c
         LEFT JOIN LATERAL (SELECT * FROM old WHERE old.kind = c.board_kind LIMIT 1) o ON true;
