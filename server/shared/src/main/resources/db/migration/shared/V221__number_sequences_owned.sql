-- 화면 번호 시퀀스 두 개를 그 번호 칸에 묶는다(OWNED BY — 제재·신고 번호 V82·V136·V137 과 같은 방식).
--   · biz_application_no_seq → biz_applications.application_no(BA-<연도>-<일련번호 5자리>, BizApplicationStore 가 nextval 로 만든다)
--   · reward_event_no_seq    → reward_events.no(RewardEventStore#nextNo)
-- 왜: 설치기(TablePackRepository#resetSequences)는 팩을 넣은 뒤 칸에 묶인 시퀀스(pg_get_serial_sequence)만 데이터의 최댓값에 맞춘다.
--     두 시퀀스는 칸에 묶이지 않아 데모 팩으로 설치하면 처음 값 그대로였고, 첫 새 건(광고 수동 등록·보상 이벤트 만들기)이 이미 있는 번호를 만들어 실패했다(2026-11-15 로컬).
--     묶어 두면 설치기가 같이 맞춘다(글자 번호는 끝의 숫자 부분 — resetSequences).
-- 이미 설치된 DB: 데이터의 가장 큰 번호가 시퀀스가 다음에 줄 값 이상이면 그 번호 다음으로 올린다(내리지는 않는다 — 이미 맞는 DB 는 그대로).

ALTER SEQUENCE biz_application_no_seq OWNED BY biz_applications.application_no;
ALTER SEQUENCE reward_event_no_seq OWNED BY reward_events.no;

SELECT setval('biz_application_no_seq', m.max_no)
FROM (SELECT max(substring(application_no FROM '([0-9]+)$')::bigint) AS max_no FROM biz_applications) m,
     (SELECT CASE WHEN is_called THEN last_value + 1 ELSE last_value END AS next_no FROM biz_application_no_seq) s
WHERE m.max_no IS NOT NULL AND m.max_no >= s.next_no;

SELECT setval('reward_event_no_seq', m.max_no)
FROM (SELECT max(no) AS max_no FROM reward_events) m,
     (SELECT CASE WHEN is_called THEN last_value + 1 ELSE last_value END AS next_no FROM reward_event_no_seq) s
WHERE m.max_no IS NOT NULL AND m.max_no >= s.next_no;
