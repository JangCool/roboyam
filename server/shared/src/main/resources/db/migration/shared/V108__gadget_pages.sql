-- 가젯 페이지 — docs/features/gadget-page-contract.md(정본), menu-contract.md 「가젯 페이지 연결」.
-- 번호: shared 마지막 V107(메뉴 폴더 연결) 다음. 다른 브랜치·worktree 에 V108 이 없음을 2026-10-29 확인(합칠 때 다시 본다).
--
-- 무엇이 생기나
--   · gadget_pages — 홈처럼 가젯 격자로 꾸미는 공개 페이지(/p/{page_key}). 기준 폴더(folder_id = 공개 메뉴 main 의 폴더 노드 ID)는 FK 를 걸지 않는다
--     (메뉴 저장은 트리를 지우고 다시 넣으므로 — 없는 폴더는 앱이 「기준 폴더 없음」으로 읽는다).
--     initial_layout = 만들 때 기준 폴더의 게시판으로 만든 처음 배치 {columns, items[{gadgetId,x,y,w,h}], gadgets[{id,type,title,config}]}(가젯 ID seed-<n>).
--   · gadget_page_layouts — 페이지 · 테마마다 배치 한 벌(행이 없으면 처음 배치).
--   · home_gadgets.page_id — 가젯 페이지의 가젯(페이지 · 테마에 딸림). NULL 이면 홈 가젯이다. 페이지를 지우면 함께 지워진다.
--   · 메뉴 연결 유형 GADGET_PAGE 는 menu_nodes 의 기존 칸(target_type·target_id)을 쓴다 — 표에 유형 CHECK 가 없어 바꿀 것이 없다.

CREATE TABLE gadget_pages (
    id              bigint        PRIMARY KEY,
    page_key        varchar(30)   NOT NULL,
    name            varchar(40)   NOT NULL,
    name_en         varchar(40),
    description     varchar(200)  NOT NULL DEFAULT '',
    folder_id       bigint,
    write_button    boolean       NOT NULL DEFAULT true,
    initial_layout  jsonb         NOT NULL DEFAULT '{}'::jsonb,
    created_by      bigint,
    created_at      timestamptz   NOT NULL DEFAULT now(),
    updated_by      bigint,
    updated_at      timestamptz   NOT NULL DEFAULT now(),
    CONSTRAINT ux_gadget_pages_key UNIQUE (page_key),
    CONSTRAINT ck_gadget_pages_key CHECK (page_key ~ '^[a-z][a-z0-9-]{1,29}$')
);

CREATE TABLE gadget_page_layouts (
    page_id     bigint       NOT NULL REFERENCES gadget_pages (id) ON DELETE CASCADE,
    theme       varchar(32)  NOT NULL,
    columns     smallint     NOT NULL,
    items       jsonb        NOT NULL DEFAULT '[]'::jsonb,
    updated_by  bigint,
    updated_at  timestamptz  NOT NULL DEFAULT now(),
    PRIMARY KEY (page_id, theme),
    CONSTRAINT ck_gadget_page_layouts_columns CHECK (columns IN (12, 24))
);

ALTER TABLE home_gadgets
    ADD COLUMN page_id bigint REFERENCES gadget_pages (id) ON DELETE CASCADE;

DROP INDEX ix_home_gadgets_theme;
CREATE INDEX ix_home_gadgets_theme ON home_gadgets (theme, id) WHERE page_id IS NULL;
CREATE INDEX ix_home_gadgets_page ON home_gadgets (page_id, theme, id) WHERE page_id IS NOT NULL;
