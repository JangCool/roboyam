-- 방문 통계 3단계 — docs/features/site-analytics-contract.md 「3단계」 결정 ④(주·월 순방문자 WAU·MAU — 방문자와 회원, 정확값) · 결정 ⑥(판정 표는 기간 파티션 + DROP).
--
-- ① 주·월 표시 네 표 — 「이 기간에 이미 셌다」(1분 몰아쓰기가 INSERT … ON CONFLICT DO NOTHING RETURNING 으로 넣고, 실제로 들어간 줄만 숫자 표에 +1 — 서버 2~3대여도 1명은 1명).
--    기간은 달력 기준 한국 날짜 — 주는 월요일 시작(period_start = 그 주 월요일), 달은 1일(period_start = 그 달 1일).
--    · 방문자: visitor_id = 방문자 해시(64자 16진 HMAC) 앞 16바이트를 uuid 로(substr(visitor_hash, 1, 32)::uuid — 겹칠 확률 사실상 0). 그날 처음 본 방문자
--      (하루 표시 analytics_visit_marks 에 새로 들어간 줄)만 넣어 본다.
--    · 회원: member_id = 회원 번호(로그인한 세션의 회원 — 운영자 회원 계정·AI 회원은 신호째 버린다). 서버마다 「이번 기간에 이미 넣은 회원」을 메모리에 두어 서버당·회원당·기간당 한 번만 넣어 본다.
--    처음부터 기간 범위 파티션 — 주 표시는 주 조각(analytics_*_week_marks_YYYYMMDD, 월요일), 달 표시는 달 조각(analytics_*_month_marks_YYYYMM).
--    조각은 DB 배치 `analytics-period-mark-partitions`(R__analytics_16)가 이번·다음 기간을 미리 만들고, 기간이 끝나고 며칠(정리 여유) 지난 조각을 통째로 DROP 한다(큰 DELETE 없음).
--    쓰는 쪽(api VisitFlusher)은 조각이 없다는 오류(23514)를 받으면 analytics_ensure_period_mark_partitions 를 그 묶음의 날로 한 번 부르고 다시 쓴다(하루 표시와 같은 방식).
--    기본 키 (period_start, …) 가 파티션 열을 담아 ON CONFLICT 를 그대로 쓴다. 파티션 표라 RETURNING 에 시스템 칸(xmax 등)을 쓰지 않는다(0A000).
--    삭제 요청(R__analytics_13)은 방문자 두 표에서 지운다(uuid 로 바꿔). 회원 두 표는 회원 탈퇴 때 지운다(shared WithdrawalCompletionService — 분석 연결로).
-- ② 숫자 표 analytics_unique_counts — (대상, 종류, 기간 시작, 날짜) 마다 그날 표시에 처음 들어간 수. 기간 순방문자 = 그 기간 줄의 합, 「같은 시점까지」 = 그 날짜까지의 합.
--    표시를 지운 뒤에도 비교된다. 하루 최대 4줄(대상 2 × 종류 2), 기한 없이 둔다(사람을 가리키지 않는다 — 삭제 요청·탈퇴에도 남는다).

CREATE TABLE analytics_visitor_week_marks (
    period_start date NOT NULL, -- 그 주 월요일(한국 날짜)
    visitor_id   uuid NOT NULL, -- 방문자 해시 앞 16바이트
    PRIMARY KEY (period_start, visitor_id)
) PARTITION BY RANGE (period_start);

CREATE TABLE analytics_visitor_month_marks (
    period_start date NOT NULL, -- 그 달 1일(한국 날짜)
    visitor_id   uuid NOT NULL,
    PRIMARY KEY (period_start, visitor_id)
) PARTITION BY RANGE (period_start);

CREATE TABLE analytics_member_week_marks (
    period_start date   NOT NULL, -- 그 주 월요일(한국 날짜)
    member_id    bigint NOT NULL, -- 회원 번호(users.id — 다른 DB 로 옮길 수 있게 외래 키는 두지 않는다)
    PRIMARY KEY (period_start, member_id)
) PARTITION BY RANGE (period_start);

CREATE TABLE analytics_member_month_marks (
    period_start date   NOT NULL, -- 그 달 1일(한국 날짜)
    member_id    bigint NOT NULL,
    PRIMARY KEY (period_start, member_id)
) PARTITION BY RANGE (period_start);

CREATE TABLE analytics_unique_counts (
    audience     varchar(7) NOT NULL, -- VISITOR · MEMBER
    period_kind  varchar(5) NOT NULL, -- WEEK · MONTH
    period_start date       NOT NULL, -- 주 = 월요일, 달 = 1일(한국 날짜)
    day          date       NOT NULL, -- 그 기간 표시에 처음 들어간 날(한국 날짜)
    added        bigint     NOT NULL DEFAULT 0,
    PRIMARY KEY (audience, period_kind, period_start, day),
    CONSTRAINT ck_analytics_unique_counts_audience CHECK (audience IN ('VISITOR', 'MEMBER')),
    CONSTRAINT ck_analytics_unique_counts_kind CHECK (period_kind IN ('WEEK', 'MONTH'))
);
