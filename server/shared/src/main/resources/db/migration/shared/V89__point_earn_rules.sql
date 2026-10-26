-- 커머스 1단계 1-3 적립 규칙 보강 + 가입 포인트 + 출석체크 — docs/features/commerce-stage1-contract.md 「1-3 화면 목업」·「1-3 출석체크」, docs/domain/commerce.md 6-2.
-- ⚠ 합칠 때 번호 재확인(가번호 V89 — develop 의 마지막 shared 는 V88) — develop 에 합치기 직전에 shared 스트림의 마지막 번호를 다시 보고 정한다.
-- ⚠ 합칠 때 합집합 확인 — 원장 대상 CHECK(ck_point_ledger_ref)·묶음 출처 대상 CHECK(ck_point_lots_source_ref)를 다시 만든다(V85 목록 ∪ ATTENDANCE).
--   다른 세션이 이 CHECK 를 또 만들면 나중 쪽이 모든 값을 담아야 한다.
--
-- 무엇이 바뀌나
--   · 정책(point_settings): 적립 조건 칸(6-2 — 0 = 끔) · 하루 총 한도 · 연속 출석 보너스. signup_points 의 지금 값은 바꾸지 않는다(확정 5 — 운영자가 정한다).
--   · 게시판: 「적립 최소 글자 수 적용」 스위치(기본값 켬, 게시판 NULL = 기본값 따름) — 끄면 글 본문 최소 글자 수만 빠진다(확정 1).
--   · 출석(point_attendances): 회원·한국 날짜마다 한 줄, 연속 일수(streak).
--   · 원장·묶음 대상에 ATTENDANCE(출석 줄) — 출석 포인트·연속 보너스의 대상 = 그 출석 줄.

-- ── 정책: 적립 조건 · 하루 총 한도 · 연속 출석 보너스 ───────────────────────────
-- post_min_length: 글 본문 실제 글자 수(서식 태그·공백 제외)가 이 값보다 작으면 적립 없음. comment_min_length: 댓글.
-- comment_own_post_excluded: 내 글에 단 댓글(답글 포함)은 적립 없음. comment_once_per_post: 같은 글에는 댓글 적립 한 번(지운 댓글로 받은 것도 센다).
-- write_interval_seconds: 직전에 포인트를 받은 글·댓글(둘 합쳐) 적립과의 간격이 이 값보다 짧으면 적립 없음.
-- daily_total_cap: 활동 적립(출석·연속 보너스·글·댓글·추천 보너스) 하루(한국 시간) 총 한도 — 걸치면 남은 만큼만. 인기글·운영 조정·충전 보너스·가입 포인트 제외.
-- attendance_streak_days·attendance_streak_bonus: 연속 출석이 이 일수의 배수인 날 보너스(둘 중 0 이면 끔).
-- 범위는 관리자 정책 화면(admin-web util-point.ts LIMITS)·서버 PointRules.LIMITS 와 같다.
ALTER TABLE point_settings
    ADD COLUMN post_min_length            integer  NOT NULL DEFAULT 50,
    ADD COLUMN comment_min_length         integer  NOT NULL DEFAULT 10,
    ADD COLUMN comment_own_post_excluded  boolean  NOT NULL DEFAULT true,
    ADD COLUMN comment_once_per_post      boolean  NOT NULL DEFAULT true,
    ADD COLUMN write_interval_seconds     integer  NOT NULL DEFAULT 30,
    ADD COLUMN daily_total_cap            integer  NOT NULL DEFAULT 200,
    ADD COLUMN attendance_streak_days     integer  NOT NULL DEFAULT 7,
    ADD COLUMN attendance_streak_bonus    integer  NOT NULL DEFAULT 20;
ALTER TABLE point_settings ADD CONSTRAINT ck_point_settings_earn_rules CHECK (post_min_length BETWEEN 0 AND 10000 AND comment_min_length BETWEEN 0 AND 1000
    AND write_interval_seconds BETWEEN 0 AND 3600 AND daily_total_cap BETWEEN 0 AND 100000 AND attendance_streak_days BETWEEN 0 AND 365
    AND attendance_streak_bonus BETWEEN 0 AND 10000);

-- ── 게시판: 적립 최소 글자 수 적용(pointMode 다음 칸) ─────────────────────────────
ALTER TABLE board_defaults ADD COLUMN point_min_length_applied boolean NOT NULL DEFAULT true;
ALTER TABLE boards ADD COLUMN point_min_length_applied boolean;

-- ── 출석 ────────────────────────────────────────────────────────────────────
-- id: Snowflake(앱). day: 출석한 한국 날짜. streak: 어제 줄의 streak + 1(없으면 1). 회원·날짜마다 한 줄 — 넣기는 insert … on conflict do nothing 한 문장(동시에 눌러도 하나).
-- users 외래키를 걸지 않는다(1단계 새 표 규칙 — commerce.md 3절). 달력 읽기는 유일 색인 (user_id, day) 를 쓴다.
CREATE TABLE point_attendances (
    id          bigint       PRIMARY KEY,
    user_id     bigint       NOT NULL,
    day         date         NOT NULL,
    streak      integer      NOT NULL,
    created_at  timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT ux_point_attendances_user_day UNIQUE (user_id, day),
    CONSTRAINT ck_point_attendances_streak CHECK (streak >= 1)
);

-- ── 원장·묶음 대상: + ATTENDANCE(V85 목록과의 합집합) ─────────────────────────────
ALTER TABLE point_ledger DROP CONSTRAINT ck_point_ledger_ref;
ALTER TABLE point_ledger ADD CONSTRAINT ck_point_ledger_ref CHECK (ref_type IS NULL OR ref_type IN ('POST', 'COMMENT', 'POINT_SHOP_PURCHASE', 'POINT_CHARGE',
    'GB_PARTICIPATION', 'LOT', 'LEDGER', 'USER', 'ATTENDANCE'));
ALTER TABLE point_lots DROP CONSTRAINT ck_point_lots_source_ref;
ALTER TABLE point_lots ADD CONSTRAINT ck_point_lots_source_ref CHECK (source_ref_type IS NULL OR source_ref_type IN ('POST', 'COMMENT', 'POINT_SHOP_PURCHASE',
    'POINT_CHARGE', 'GB_PARTICIPATION', 'LOT', 'LEDGER', 'USER', 'ATTENDANCE'));
