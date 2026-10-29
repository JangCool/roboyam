-- 게시판 레이아웃 — docs/features/board-layout-contract.md(정본), gadget-page-contract.md 「가젯 설정」.
-- 번호: 계약은 V109 였으나 다른 브랜치(claude/gallant-williamson-64a83a, 주소 검색 2단계)가 V109__address_search.sql 을 이미 잡아 V110(2026-10-30 확인 — 합칠 때 다시 본다).
--
-- 무엇이 생기나
--   · board_layouts — 테마마다 한 행: 적용 화면(게시판 목록 list_enabled · 글 보기 post_enabled) + 배치(home_layouts 와 같은 모양 — items = [{gadgetId, x, y, w, h}]).
--     행이 없으면 코드의 기본 배치(BoardLayoutDefaults — 본문만 전체 폭, 두 화면 켬). 모든 게시판에 같이 쓴다(게시판별 덮어쓰기 없음).
--   · home_gadgets.board_layout — 게시판 레이아웃의 가젯(테마에 딸림, page_id 는 NULL). 홈 가젯 = page_id NULL 이고 board_layout false.
--     고정 칸 「게시판 본문」은 종류 BOARD_BODY 인 가젯 행이다(꼭 하나 — 검사는 서버).
-- 테마 id 는 home_layouts 처럼 CHECK 를 걸지 않는다(허용 값은 서버 코드 SiteThemes).

CREATE TABLE board_layouts (
    theme         varchar(32)  PRIMARY KEY,
    list_enabled  boolean      NOT NULL DEFAULT true,
    post_enabled  boolean      NOT NULL DEFAULT true,
    columns       smallint     NOT NULL,
    items         jsonb        NOT NULL DEFAULT '[]'::jsonb,
    updated_by    bigint,
    updated_at    timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT ck_board_layouts_columns CHECK (columns IN (12, 24))
);

ALTER TABLE home_gadgets
    ADD COLUMN board_layout boolean NOT NULL DEFAULT false,
    ADD CONSTRAINT ck_home_gadgets_board_layout CHECK (NOT board_layout OR page_id IS NULL);

DROP INDEX ix_home_gadgets_theme;
CREATE INDEX ix_home_gadgets_theme ON home_gadgets (theme, id) WHERE page_id IS NULL AND NOT board_layout;
CREATE INDEX ix_home_gadgets_board ON home_gadgets (theme, id) WHERE board_layout;
