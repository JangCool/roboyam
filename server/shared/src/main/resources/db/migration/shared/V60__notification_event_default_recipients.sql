-- 운영자 이벤트의 「권한자 전원에게 보내기」(기본 수신 규칙) 사용 여부 — 끄면 고른 추가 수신 팀의 팀원에게만 보낸다(docs/features/notification-contract.md 「팀에만 보내기」).
-- 처음 값과 지금 있는 이벤트는 모두 켜짐(지금 동작 그대로). 본인 전용·필수 이벤트는 서버가 끌 수 없게 막는다(코드 broadcastAllowed·mandatory).
-- 팀에만 보내는데 고른 팀이 모두 멈췄거나 받을 사람이 없으면 발행기가 기본 수신으로 되돌려 보낸다(알림이 사라지지 않게).
ALTER TABLE notification_events ADD COLUMN default_recipients boolean NOT NULL DEFAULT true;
