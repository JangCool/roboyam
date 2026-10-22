-- 관리자 사이트 메뉴 구성 — docs/features/menu-contract.md 「관리자 사이트 메뉴」
--
-- admin 스트림에 두는 이유: 이 구성은 관리자 앱(admin 서버)만 읽고 쓴다 — 공개 사이트 서버(api)는 관리자 메뉴를 알 필요가 없다(admin_spring_session 과 같은 판단).
--
-- · admin_menu_state: 행 하나(id = 1). version 은 저장마다 1 씩 오른다(409 MENU_VERSION_CONFLICT 판정, 저장할 때 이 행을 잠근다).
--   updated_at 이 NULL 이면 아직 저장한 적이 없다 — 서버는 화면 레지스트리의 기본 구성(AdminScreenRegistry)을 돌려준다.
-- · admin_menu_nodes: 두 모드(normal·settings)의 트리. SECTION(최상위만) → SCREEN 또는 FOLDER → 하위 SCREEN. 구조 규칙·이름·아이콘·주소 검사는 앱이 한다.
--   ADMIN_SCREEN 화면 항목의 권한은 저장하지 않는다(화면 레지스트리가 정한다). permission 은 URL 항목이 고른 권한 코드뿐이다.
CREATE TABLE admin_menu_state (
    id         integer      PRIMARY KEY,
    version    bigint       NOT NULL DEFAULT 1,
    updated_at timestamptz,
    updated_by bigint       REFERENCES operators (id) ON DELETE SET NULL,
    CONSTRAINT ck_admin_menu_state_single_row CHECK (id = 1)
);
INSERT INTO admin_menu_state (id) VALUES (1);

CREATE TABLE admin_menu_nodes (
    id          bigint        PRIMARY KEY,
    mode        varchar(10)   NOT NULL,
    parent_id   bigint        REFERENCES admin_menu_nodes (id) ON DELETE CASCADE,
    kind        varchar(8)    NOT NULL,
    sort_order  integer       NOT NULL,
    label_key   varchar(60),
    label       varchar(20),
    label_en    varchar(40),
    icon        varchar(60),
    visible     boolean       NOT NULL DEFAULT true,
    target_type varchar(16),
    target_key  varchar(60),
    href        varchar(2000),
    new_tab     boolean       NOT NULL DEFAULT true,
    permission  varchar(60),
    CONSTRAINT ck_admin_menu_nodes_mode CHECK (mode IN ('normal', 'settings')),
    CONSTRAINT ck_admin_menu_nodes_kind CHECK (kind IN ('SECTION', 'FOLDER', 'SCREEN')),
    CONSTRAINT ck_admin_menu_nodes_target CHECK ((kind = 'SCREEN') = (target_type IS NOT NULL)),
    CONSTRAINT ck_admin_menu_nodes_target_type CHECK (target_type IS NULL OR target_type IN ('ADMIN_SCREEN', 'URL'))
);
CREATE INDEX ix_admin_menu_nodes_tree ON admin_menu_nodes (mode, parent_id, sort_order);
