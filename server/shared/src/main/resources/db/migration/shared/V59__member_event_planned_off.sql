-- 준비 중 회원 이벤트(아직 발행되지 않는 이벤트)는 처음 값이 「사용 꺼짐」이다(계약 「알림 템플릿·문구」 — 회원 이벤트 처음 값).
-- V58 이 옛 게시판 기본 설정(member_defaults)의 값을 그대로 옮기면서 준비 중 이벤트 5종이 켜진 채(일부는 이메일도 켜짐) 들어갔다 — 기능이 생길 때 운영자가 켠다.
-- 바뀐 사람·시각은 남기지 않는다(운영자가 바꾼 값이 아니라 처음 값을 바로잡는 것).
UPDATE member_event_settings
SET enabled = false,
    in_app = false,
    email = false
WHERE code IN ('MENTION', 'POST_POPULAR', 'OPERATOR_ACTION', 'STAFF_ACTION', 'REPORT_RESULT');
