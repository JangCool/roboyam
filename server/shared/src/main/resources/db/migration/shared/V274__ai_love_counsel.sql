-- AI 연애상담소 (docs/features/ai-love-counsel-contract.md — 연애·결혼 게시판 「AI 상담소」 말머리, 사용자 확정 2026-11-26).
-- V273 은 다른 브랜치(board-recommend)가 쓴다 — 이 파일은 V274 부터.
--
--   · board_prefixes.counsel          그 말머리가 AI 상담소인가(고유 말머리만 — 공통 말머리는 켜지 않는다). 공개 응답 PrefixView.counsel · PostPrefixRef.counsel.
--                                     관리자 게시판 저장은 이 칸을 건드리지 않는다(BoardPrefixEntity#change) — 켜고 끄기는 관리자 API PUT /api/admin/ai-members/counsel.
--   · ai_member_profiles.counselor    상담 전용 AI 계정(상담사) — 활동 시간대·「글 쓰는 때」·첫 댓글·답글 규칙과 무관하게 상담 답만 쓴다(보통 AI 회원 실행기는 이 계정을 건너뛴다).
--   · ai_member_profiles.counsel_style 상담 성향 한 마디(「직설형」·「공감형」·「현실 분석형」) — 공개 응답 CounselAiAnswer.style.
--   · ai_member_settings.counsel_member_daily  회원 한 명이 하루(한국 날짜) 올릴 수 있는 상담 사연 수(기본 1, 0 = 받지 않음). 글쓰기 때 본다.
--   · ai_member_settings.counsel_site_daily    사이트 전체 하루 상담 사연 상한(기본 100, 0 = 받지 않음) — 비용 보호(사연 하나에 제공사 호출 3~4번).
--   · installation.counsel_seeded_at  설치 구성의 상담소 기본값(「AI 상담소」 말머리 — 꺼진 채, 상담사 3계정 — 꺼진 채)을 넣은 시각(공개 서버 CounselPresetInstaller).
--   · counsel_posts                   상담 사연 하나(글 1:1) — 위험 판정 결과 · 답할 상담사 · 고른 답. 관리자 서버 실행기(AiMemberScheduler → CounselRunner)가 만든다.
--                                     줄이 없는 상담 글 = 아직 판정 전(공개 상태 WAITING).
--   · counsel_weekly_picks            주차(한국 시각 월요일 0시 ~ 일요일 24시)·게시판·상담사별 「가장 도움 된 조언」 고른 수 — 고를 때 +1(배치 없음). 사람 회원은 한 줄(ai_member_id = 0).
-- 순수 expand — 칸·표 추가뿐이라 이전 버전 앱은 그대로 돈다.

ALTER TABLE board_prefixes ADD COLUMN counsel boolean NOT NULL DEFAULT false;
ALTER TABLE board_prefixes ADD CONSTRAINT ck_board_prefixes_counsel CHECK (NOT counsel OR common_key IS NULL);

ALTER TABLE ai_member_profiles
    ADD COLUMN counselor boolean NOT NULL DEFAULT false,
    ADD COLUMN counsel_style varchar(20);
COMMENT ON COLUMN ai_member_profiles.counselor IS 'AI 연애상담소 상담사(V274) — 상담 답만 쓴다';
COMMENT ON COLUMN ai_member_profiles.counsel_style IS '상담 성향 한 마디(V274) — 공개 CounselAiAnswer.style';

ALTER TABLE ai_member_settings
    ADD COLUMN counsel_member_daily smallint NOT NULL DEFAULT 1,
    ADD COLUMN counsel_site_daily integer NOT NULL DEFAULT 100,
    ADD CONSTRAINT ck_ai_member_settings_counsel_member CHECK (counsel_member_daily BETWEEN 0 AND 10),
    ADD CONSTRAINT ck_ai_member_settings_counsel_site CHECK (counsel_site_daily BETWEEN 0 AND 5000);

ALTER TABLE installation ADD COLUMN counsel_seeded_at timestamptz;

CREATE TABLE counsel_posts (
    post_id             bigint      PRIMARY KEY,
    board_id            bigint      NOT NULL,
    author_id           bigint      NOT NULL,
    -- CHECKING = 낱말 목록은 지났고 AI 분류를 기다린다 · OPEN = 상담사가 답한다 · RISK = 위험 사연(AI 가 답하지 않는다)
    state               varchar(10) NOT NULL,
    -- 위험 판정 까닭 — WORD 낱말 목록 · AI 분류(RISK 일 때만)
    risk_reason         varchar(10),
    -- AI 분류 시도 수(실패 포함) — 한도를 넘으면 낱말 목록만으로 통과(OPEN)
    risk_checks         smallint    NOT NULL DEFAULT 0,
    -- 답할 상담사(ai_member_profiles.id, 판정 때 쉬지 않던 상담사 — 화면의 차례)
    counselor_ids       bigint[]    NOT NULL DEFAULT '{}',
    picked_comment_id   bigint,
    -- 고른 답의 상담사(ai_member_profiles.id) — 사람 회원 답이면 0
    picked_ai_member_id bigint,
    picked_at           timestamptz,
    -- 글을 쓴 시각(posts.created_at) — 답 시한(+10분)·그만두는 때의 기준
    post_created_at     timestamptz NOT NULL,
    created_at          timestamptz NOT NULL,
    -- 답 차례가 끝났다(모두 답했거나 시한이 지나 그만둠) — 실행기가 더 보지 않는다
    closed_at           timestamptz,
    CONSTRAINT ck_counsel_posts_state CHECK (state IN ('CHECKING', 'OPEN', 'RISK')),
    CONSTRAINT ck_counsel_posts_risk CHECK ((state = 'RISK') = (risk_reason IS NOT NULL)),
    CONSTRAINT ck_counsel_posts_risk_reason CHECK (risk_reason IS NULL OR risk_reason IN ('WORD', 'AI')),
    CONSTRAINT ck_counsel_posts_pick CHECK ((picked_comment_id IS NULL) = (picked_at IS NULL) AND (picked_comment_id IS NULL) = (picked_ai_member_id IS NULL))
);
-- 실행기 — 아직 끝나지 않은 사연(판정 대기 · 답하는 중)
CREATE INDEX ix_counsel_posts_open ON counsel_posts (created_at) WHERE closed_at IS NULL;
-- 회원별 오늘 사연 수 · 사이트 하루 상한
CREATE INDEX ix_counsel_posts_created ON counsel_posts (created_at);
COMMENT ON TABLE counsel_posts IS 'AI 연애상담소 사연(V274) — ai-love-counsel-contract';

CREATE TABLE counsel_weekly_picks (
    board_id     bigint  NOT NULL,
    -- 그 주 월요일(한국 날짜)
    week_start   date    NOT NULL,
    -- 상담사(ai_member_profiles.id) — 사람 회원 전체는 0
    ai_member_id bigint  NOT NULL,
    picks        integer NOT NULL DEFAULT 0,
    PRIMARY KEY (board_id, week_start, ai_member_id),
    CONSTRAINT ck_counsel_weekly_picks_picks CHECK (picks >= 0)
);
COMMENT ON TABLE counsel_weekly_picks IS 'AI 연애상담소 주간 「가장 도움 된 조언」 수(V274) — 고를 때 +1';
