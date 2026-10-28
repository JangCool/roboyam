-- 테마 · 레이아웃 · 메뉴 설정 서버 — docs/features/site-theme-layout-contract.md(정본), menu-contract.md 「구분선·설명」.
-- 번호: develop 의 shared 마지막 V102 다음, 다른 세션이 V103(공구 결함)·V104(커머스 판매 스위치)를 잡아 두어 V105(2026-10-29 확인 — 합칠 때 다시 본다).
--
-- 무엇이 생기나
--   · 공개 메뉴 노드의 상단 첫 줄 구분선(divider)·항목 설명(description).
--   · 테마별 메뉴 설정(site_theme_menus) — 테마마다 한 행, 행이 없으면 칸마다 THEME(테마 기본 따름).
--   · 테마별 홈 구성: 배치(home_layouts — 테마마다 한 행, 행이 없으면 코드의 테마 기본 배치) · 가젯(home_gadgets — 테마에 딸림).
--   · 회원 보기 설정의 데스크톱 메뉴 위치(member_view_settings.menu_position).
-- 테마 id 는 site_settings.theme(V52)과 같이 CHECK 를 걸지 않는다(테마가 늘 때 마이그레이션이 필요 없게 — 허용 값은 서버 코드 SiteThemes).

-- ── 메뉴 노드 구분선·설명 ─────────────────────────────────────────────────
ALTER TABLE menu_nodes
    ADD COLUMN divider     varchar(6)   NOT NULL DEFAULT 'NONE',
    ADD COLUMN description varchar(80),
    ADD CONSTRAINT ck_menu_nodes_divider CHECK (divider IN ('NONE', 'BEFORE', 'AFTER')),
    ADD CONSTRAINT ck_menu_nodes_description CHECK (description IS NULL OR kind = 'ITEM');

-- ── 테마별 메뉴 설정 — 값 검사는 서버(SiteThemeMenus)가 한다. 칸마다 THEME 또는 그 칸의 값 ─────────────
CREATE TABLE site_theme_menus (
    theme             varchar(32)  PRIMARY KEY,
    menu_position     varchar(10)  NOT NULL,
    top_items         varchar(10)  NOT NULL,
    -- 상단 띠 그룹(공개 메뉴 main 의 최상위 폴더 ID)을 쉼표로 이은 글자 — 빈 글자 = 첫 그룹
    bar_group_ids     varchar(420) NOT NULL DEFAULT '',
    menu_width        varchar(10)  NOT NULL,
    menu_overflow     varchar(10)  NOT NULL,
    menu_group_style  varchar(10)  NOT NULL,
    side_group_style  varchar(10)  NOT NULL,
    menu_size         varchar(10)  NOT NULL,
    mobile_menu       varchar(10)  NOT NULL,
    menu_description  varchar(10)  NOT NULL,
    sticky_menu       varchar(10)  NOT NULL,
    mobile_header     varchar(10)  NOT NULL,
    updated_by        bigint,
    updated_at        timestamptz  NOT NULL DEFAULT now()
);

-- ── 테마별 홈 배치 — items = [{gadgetId, x, y, w, h}](가젯 ID 는 같은 테마의 home_gadgets.id 글자) ─────────────
CREATE TABLE home_layouts (
    theme       varchar(32)  PRIMARY KEY,
    columns     smallint     NOT NULL,
    items       jsonb        NOT NULL DEFAULT '[]'::jsonb,
    updated_by  bigint,
    updated_at  timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT ck_home_layouts_columns CHECK (columns IN (12, 24))
);

-- ── 홈 가젯 — 종류 8종(HomeGadgetKinds), 꾸미기·설정·더보기는 화면이 읽는 JSON 그대로 ─────────────
CREATE TABLE home_gadgets (
    id          bigint        PRIMARY KEY,
    theme       varchar(32)   NOT NULL,
    type        varchar(32)   NOT NULL,
    title       varchar(40)   NOT NULL,
    desktop     boolean       NOT NULL DEFAULT true,
    mobile      boolean       NOT NULL DEFAULT true,
    style       jsonb         NOT NULL DEFAULT '{}'::jsonb,
    config      jsonb         NOT NULL DEFAULT '{}'::jsonb,
    more        jsonb,
    created_at  timestamptz   NOT NULL DEFAULT now()
);
CREATE INDEX ix_home_gadgets_theme ON home_gadgets (theme, id);

-- ── 회원 보기 설정 — 데스크톱 메뉴 위치(AUTO = 운영자 설정 > 테마 기본) ─────────────
ALTER TABLE member_view_settings
    ADD COLUMN menu_position varchar(9) NOT NULL DEFAULT 'AUTO',
    ADD CONSTRAINT ck_member_view_settings_menu_position CHECK (menu_position IN ('AUTO', 'TOP', 'LEFT', 'RIGHT', 'TOP_LEFT', 'TOP_RIGHT'));
