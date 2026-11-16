-- AI 기자 뉴스(docs/features/ai-news-reporter-contract.md, 사용자 2026-11-16) — 키 없는 RSS 소재 수집 + 뉴스 글의 말머리·태그 + 계정별 추가 지시.
--   · ai_news_sources  뉴스 출처(관리자 AI 회원 설정 「뉴스 출처」). 종류 RSS(언론사 RSS 주소) · YOUTUBE(채널 ID) · KEYWORD(구글 뉴스 RSS 검색어 — 기본 꺼짐).
--                      수집 배치(admin 서버 Java 배치 ai-news-collect, 기본 30분마다)가 마지막 수집 시각·건수·오류를 적는다.
--   · ai_news_items    수집 항목 — 제목 · 원문 링크(고유) · 짧은 요약(RSS 설명을 자른 것) · 출처 이름 · 발행 시각 · 쓴 때·쓴 생성 줄. 본문 전체·사진은 받지 않는다.
--                      7일 지난 항목은 수집 배치가 지운다. 출처를 지우면 그 출처의 항목도 지운다.
--   · ai_member_drafts.news_item_id  소재로 쓴 수집 항목(항목이 지워져도 남는다 — FK 없음) · prefix_name 모델이 고르고 서버가 확인한 말머리 이름(등록할 때 그 게시판의
--                      같은 이름 말머리로 단다) · tags 서버가 거른 태그(쉼표로 이음 — 태그에는 쉼표가 없다, PostTags.valid).
--   · ai_member_profiles.extra_prompt  계정별 운영자 추가 지시(선택, 300자) — 프롬프트에 지시로 들어간다.
CREATE TABLE ai_news_sources
(
    id              bigint       NOT NULL PRIMARY KEY,
    kind            varchar(10)  NOT NULL,
    name            varchar(60)  NOT NULL,
    address         varchar(500) NOT NULL,
    enabled         boolean      NOT NULL DEFAULT true,
    sort_order      int          NOT NULL DEFAULT 0,
    last_fetched_at timestamptz  NULL,
    last_item_count int          NULL,
    last_error      varchar(300) NULL,
    last_error_at   timestamptz  NULL,
    created_at      timestamptz  NOT NULL,
    created_by      bigint       NULL,
    updated_at      timestamptz  NOT NULL,
    updated_by      bigint       NULL,
    CONSTRAINT ck_ai_news_sources_kind CHECK (kind IN ('RSS', 'YOUTUBE', 'KEYWORD')),
    CONSTRAINT ck_ai_news_sources_name CHECK (char_length(btrim(name)) BETWEEN 1 AND 60),
    CONSTRAINT ck_ai_news_sources_address CHECK (char_length(btrim(address)) BETWEEN 1 AND 500)
);

COMMENT ON TABLE ai_news_sources IS 'AI 기자 뉴스 출처(RSS·YOUTUBE 채널 ID·KEYWORD 구글 뉴스 검색어) — V228, ai-news-reporter-contract';
COMMENT ON COLUMN ai_news_sources.address IS 'RSS 는 피드 주소(http/https), YOUTUBE 는 채널 ID(UC…), KEYWORD 는 검색어';

CREATE TABLE ai_news_items
(
    id            bigint        NOT NULL PRIMARY KEY,
    source_id     bigint        NOT NULL REFERENCES ai_news_sources (id) ON DELETE CASCADE,
    title         varchar(300)  NOT NULL,
    link          varchar(1000) NOT NULL,
    summary       varchar(600)  NULL,
    source_name   varchar(100)  NOT NULL,
    published_at  timestamptz   NULL,
    fetched_at    timestamptz   NOT NULL,
    used_at       timestamptz   NULL,
    used_draft_id bigint        NULL
);

COMMENT ON TABLE ai_news_items IS 'AI 기자 뉴스 수집 항목(제목·링크·짧은 요약만 — 본문·사진 없음, 7일 보관) — V228';

CREATE UNIQUE INDEX ux_ai_news_items_link ON ai_news_items (link);
CREATE INDEX ix_ai_news_items_unused ON ai_news_items (fetched_at DESC) WHERE used_at IS NULL;
CREATE INDEX ix_ai_news_items_fetched ON ai_news_items (fetched_at);
CREATE INDEX ix_ai_news_items_source ON ai_news_items (source_id);

ALTER TABLE ai_member_drafts
    ADD COLUMN news_item_id bigint       NULL,
    ADD COLUMN prefix_name  varchar(30)  NULL,
    ADD COLUMN tags         varchar(200) NULL;

COMMENT ON COLUMN ai_member_drafts.news_item_id IS '소재로 쓴 뉴스 수집 항목(ai_news_items — 지워져도 남는다) — V228';
COMMENT ON COLUMN ai_member_drafts.prefix_name IS '뉴스 글 말머리 이름(서버가 확인한 것 — 등록 때 같은 이름 말머리) — V228';
COMMENT ON COLUMN ai_member_drafts.tags IS '뉴스 글 태그(서버가 거른 것, 쉼표로 이음) — V228';

ALTER TABLE ai_member_profiles
    ADD COLUMN extra_prompt varchar(300) NULL,
    ADD CONSTRAINT ck_ai_member_profiles_extra_prompt CHECK (extra_prompt IS NULL OR char_length(extra_prompt) <= 300);

COMMENT ON COLUMN ai_member_profiles.extra_prompt IS '계정별 운영자 추가 지시(선택, 300자 — 프롬프트에 지시로 들어간다) — V228';
