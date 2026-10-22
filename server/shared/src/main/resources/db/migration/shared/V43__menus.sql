-- 공개 사이트 메뉴 — docs/features/menu-contract.md (관리자 화면 확정 2026-10-22)
--
-- · menu_sets: 메뉴 그룹(메뉴 세트). set_key 가 PK 이고 만든 뒤 바꾸지 않는다. is_system = 기본 그룹(지우기·위치 변경 불가).
--   version: 그 그룹 트리의 버전 — 트리 저장·게시판 자동 추가/삭제마다 1 씩 오른다(관리자 저장의 409 MENU_VERSION_CONFLICT 판정).
-- · menu_nodes: 그룹별 트리(parent_id). FOLDER(폴더) · ITEM(항목). 항목의 연결 유형(target_type)은 코드 레지스트리(MenuTargetRegistry)가 정하므로
--   CHECK 를 두지 않는다 — 모르는 유형의 행은 공개 응답에서 빠지고 저장 때는 받은 그대로 보존한다. target_id 는 글자다(모르는 유형의 값도 보존한다).
--   깊이(폴더 ≤ 3 · 항목 ≤ 4)·노드 수(≤ 80)·모바일 탭 수(≤ 10)·자동 추가 폴더 하나·부모는 폴더만 은 앱(MenuTreeValidator)이 저장 한 번 단위로 검사한다.
--   한 그룹 안에서 같은 게시판은 한 번 — 마지막 방어로 부분 유일 색인을 둔다(앱은 트리 저장 때 그룹의 노드를 지우고 다시 넣는다).
-- · 시드: 기본 그룹 3개(main·mypage·group-buy). main 은 HOME 항목 + 자동 추가 폴더 「게시판」(지금 있는 게시판 전부, 게시판 순서대로).
--   새로 설치하는 곳은 이때 게시판이 없고, 설치 프리셋이 게시판을 만들 때 자동 추가 폴더에 들어간다(BoardCreator → MenuBoardLinker).
--   노드 ID 는 앱의 Snowflake 와 같은 모양(시각 ms 41비트 | 서버 번호 10비트 | 순번 12비트)이고 서버 번호는 마이그레이션 전용 1023 이다(V30 과 같다).

CREATE TABLE menu_sets (
    set_key     varchar(30)  PRIMARY KEY,
    name        varchar(20)  NOT NULL,
    name_en     varchar(40),
    placement   varchar(10)  NOT NULL,
    enabled     boolean      NOT NULL DEFAULT true,
    description varchar(100),
    is_system   boolean      NOT NULL DEFAULT false,
    version     bigint       NOT NULL DEFAULT 1,
    created_at  timestamptz  NOT NULL DEFAULT now(),
    created_by  bigint       REFERENCES operators (id) ON DELETE SET NULL,
    updated_at  timestamptz  NOT NULL DEFAULT now(),
    updated_by  bigint       REFERENCES operators (id) ON DELETE SET NULL,
    CONSTRAINT ck_menu_sets_key CHECK (set_key ~ '^[a-z][a-z0-9-]{1,29}$'),
    CONSTRAINT ck_menu_sets_name CHECK (char_length(name) BETWEEN 1 AND 20 AND name = btrim(name)),
    CONSTRAINT ck_menu_sets_name_en CHECK (name_en IS NULL OR (char_length(name_en) BETWEEN 1 AND 40 AND name_en = btrim(name_en))),
    CONSTRAINT ck_menu_sets_placement CHECK (placement IN ('SIDE', 'MYPAGE', 'GROUP_BUY', 'CUSTOM')),
    CONSTRAINT ck_menu_sets_description CHECK (description IS NULL OR char_length(description) <= 100)
);
CREATE INDEX ix_menu_sets_created ON menu_sets (created_at, set_key);

CREATE TABLE menu_nodes (
    id          bigint       PRIMARY KEY,
    set_key     varchar(30)  NOT NULL REFERENCES menu_sets (set_key) ON DELETE CASCADE,
    parent_id   bigint       REFERENCES menu_nodes (id) ON DELETE CASCADE,
    kind        varchar(8)   NOT NULL,
    sort_order  integer      NOT NULL,
    label       varchar(20),
    name_en     varchar(40),
    visible     boolean      NOT NULL DEFAULT true,
    audience    varchar(10)  NOT NULL DEFAULT 'ALL',
    min_grade   varchar(19),
    collapsible boolean      NOT NULL DEFAULT false,
    collapsed   boolean      NOT NULL DEFAULT false,
    auto_add    boolean      NOT NULL DEFAULT false,
    target_type varchar(30),
    target_id   varchar(64),
    target_key  varchar(64),
    settings    jsonb,
    mobile_tab  boolean      NOT NULL DEFAULT false,
    CONSTRAINT ck_menu_nodes_kind CHECK (kind IN ('FOLDER', 'ITEM')),
    CONSTRAINT ck_menu_nodes_folder CHECK (kind <> 'FOLDER' OR (label IS NOT NULL AND target_type IS NULL AND NOT mobile_tab)),
    CONSTRAINT ck_menu_nodes_item CHECK (kind <> 'ITEM' OR (target_type IS NOT NULL AND NOT auto_add AND NOT collapsible)),
    CONSTRAINT ck_menu_nodes_label CHECK (label IS NULL OR (char_length(label) BETWEEN 1 AND 20 AND label = btrim(label))),
    CONSTRAINT ck_menu_nodes_name_en CHECK (name_en IS NULL OR (char_length(name_en) BETWEEN 1 AND 40 AND name_en = btrim(name_en))),
    CONSTRAINT ck_menu_nodes_audience CHECK (audience IN ('ALL', 'GUEST', 'MEMBER', 'GRADE_MIN', 'STAFF')),
    CONSTRAINT ck_menu_nodes_min_grade CHECK ((audience = 'GRADE_MIN') = (min_grade IS NOT NULL))
);
CREATE INDEX ix_menu_nodes_set ON menu_nodes (set_key, parent_id, sort_order);
CREATE INDEX ix_menu_nodes_target ON menu_nodes (target_type, target_id) WHERE target_id IS NOT NULL;
CREATE UNIQUE INDEX ux_menu_nodes_board ON menu_nodes (set_key, target_id) WHERE target_type = 'BOARD';

-- ── 시드: 기본 그룹 3개 ──────────────────────────────────────────────────────
INSERT INTO menu_sets (set_key, name, name_en, placement, enabled, description, is_system) VALUES
    ('main', '좌측 사이드 메뉴', 'Side menu', 'SIDE', true, '공개 사이트의 기본 내비게이션이에요.', true),
    ('mypage', '마이페이지 메뉴', 'My page menu', 'MYPAGE', true, '마이페이지 왼쪽 메뉴예요.', true),
    ('group-buy', '공동구매 메뉴', 'Group buy menu', 'GROUP_BUY', true, '공동구매 영역의 사이드 메뉴예요.', true);

-- ── 시드: 기본 트리 ─────────────────────────────────────────────────────────
-- main: HOME(모바일 탭) · 「게시판」 자동 추가 폴더(게시판 순서대로, 노드 한도 80 안에서 최대 78개)
-- mypage: 내 정보·내 등급·포인트 내역 · 「활동」(쪽지·알림·예약 글) · 「설정」(접기 — 프로필·계정·보기·차단 목록·알림 설정). 모두 로그인(MEMBER)
-- group-buy: 공동구매(「진행 중인 공구」) · 공동구매 주문 내역(MEMBER)
DO $$
DECLARE
    v_base   bigint := ((floor(extract(epoch FROM clock_timestamp()) * 1000)::bigint - 1767225600000) << 22) | (1023::bigint << 12);
    v_n      int := 0;
    v_folder bigint;
BEGIN
    -- main
    INSERT INTO menu_nodes (id, set_key, parent_id, kind, sort_order, target_type, target_key, mobile_tab)
    VALUES (v_base + v_n, 'main', NULL, 'ITEM', 0, 'BUILTIN', 'HOME', true);
    v_n := v_n + 1;
    v_folder := v_base + v_n;
    INSERT INTO menu_nodes (id, set_key, parent_id, kind, sort_order, label, name_en, auto_add)
    VALUES (v_folder, 'main', NULL, 'FOLDER', 1, '게시판', 'Boards', true);
    v_n := v_n + 1;
    INSERT INTO menu_nodes (id, set_key, parent_id, kind, sort_order, target_type, target_id)
    SELECT v_base + v_n + b.rn, 'main', v_folder, 'ITEM', b.rn::int, 'BOARD', b.id::text
      FROM (SELECT id, row_number() OVER (ORDER BY sort_order, id) - 1 AS rn FROM boards ORDER BY sort_order, id LIMIT 78) b;
    v_n := v_n + 100;

    -- mypage
    INSERT INTO menu_nodes (id, set_key, parent_id, kind, sort_order, audience, target_type, target_key) VALUES
        (v_base + v_n,     'mypage', NULL, 'ITEM', 0, 'MEMBER', 'BUILTIN', 'MY_HOME'),
        (v_base + v_n + 1, 'mypage', NULL, 'ITEM', 1, 'MEMBER', 'BUILTIN', 'MY_GRADE'),
        (v_base + v_n + 2, 'mypage', NULL, 'ITEM', 2, 'MEMBER', 'BUILTIN', 'MY_POINTS');
    v_n := v_n + 3;
    v_folder := v_base + v_n;
    INSERT INTO menu_nodes (id, set_key, parent_id, kind, sort_order, label, name_en) VALUES (v_folder, 'mypage', NULL, 'FOLDER', 3, '활동', 'Activity');
    INSERT INTO menu_nodes (id, set_key, parent_id, kind, sort_order, audience, target_type, target_key) VALUES
        (v_base + v_n + 1, 'mypage', v_folder, 'ITEM', 0, 'MEMBER', 'BUILTIN', 'MESSAGES'),
        (v_base + v_n + 2, 'mypage', v_folder, 'ITEM', 1, 'MEMBER', 'BUILTIN', 'NOTIFICATIONS'),
        (v_base + v_n + 3, 'mypage', v_folder, 'ITEM', 2, 'MEMBER', 'BUILTIN', 'MY_SCHEDULED');
    v_n := v_n + 4;
    v_folder := v_base + v_n;
    INSERT INTO menu_nodes (id, set_key, parent_id, kind, sort_order, label, name_en, collapsible) VALUES (v_folder, 'mypage', NULL, 'FOLDER', 4, '설정', 'Settings', true);
    INSERT INTO menu_nodes (id, set_key, parent_id, kind, sort_order, audience, target_type, target_key) VALUES
        (v_base + v_n + 1, 'mypage', v_folder, 'ITEM', 0, 'MEMBER', 'BUILTIN', 'SETTINGS_PROFILE'),
        (v_base + v_n + 2, 'mypage', v_folder, 'ITEM', 1, 'MEMBER', 'BUILTIN', 'SETTINGS_ACCOUNT'),
        (v_base + v_n + 3, 'mypage', v_folder, 'ITEM', 2, 'MEMBER', 'BUILTIN', 'SETTINGS_VIEW'),
        (v_base + v_n + 4, 'mypage', v_folder, 'ITEM', 3, 'MEMBER', 'BUILTIN', 'SETTINGS_BLOCKS'),
        (v_base + v_n + 5, 'mypage', v_folder, 'ITEM', 4, 'MEMBER', 'BUILTIN', 'NOTIFICATION_SETTINGS');
    v_n := v_n + 6;

    -- group-buy
    INSERT INTO menu_nodes (id, set_key, parent_id, kind, sort_order, label, name_en, target_type, target_key)
    VALUES (v_base + v_n, 'group-buy', NULL, 'ITEM', 0, '진행 중인 공구', 'Ongoing deals', 'BUILTIN', 'GROUP_BUYS');
    INSERT INTO menu_nodes (id, set_key, parent_id, kind, sort_order, audience, target_type, target_key)
    VALUES (v_base + v_n + 1, 'group-buy', NULL, 'ITEM', 1, 'MEMBER', 'BUILTIN', 'GROUP_BUY_ORDERS');
END
$$;
