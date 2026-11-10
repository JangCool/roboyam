-- 구글 애드센스(docs/features/adsense-contract.md, 화면 확정 2026-11-10) — 광고 노출 자리 설정 한 행(ad_placement_settings)에 애드센스 칸을 더한다.
--   · adsense_mode — OFF 끔(기본) · VERIFY 사이트 확인만(index.html 머리 스크립트·메타 + /ads.txt, 광고 단위는 그리지 않음) · SHOW 광고 표시.
--   · adsense_client — 게시자 ID 'ca-pub-' + 숫자 16자리. VERIFY·SHOW 면 필수, OFF 여도 입력해 둔 값은 남긴다.
--   · adsense_adtest — 시험 표시(data-adtest="on"), 기본 끔.
--   · adsense_<자리> — 디스플레이 광고 단위 ID(숫자 10자리) — 배너 가젯 넷 · 날개 · 글 보기. 없으면 그 자리는 애드센스를 쓰지 않는다.
--   · adsense_board_list_slot · adsense_board_list_layout_key — 게시판 목록 한 줄 인피드 단위 ID + 레이아웃 키(둘 다 있거나 둘 다 없음).
-- 형식 검사는 관리자 서버(AdPlacementAdminService)가 하고, 여기 CHECK 는 마지막 지킴이다. 설치 프리셋·데모 팩은 칸을 적은 것만 쓰므로 새 칸은 기본값(끔)이 된다.

ALTER TABLE ad_placement_settings
    ADD COLUMN adsense_mode                  varchar(10)  NOT NULL DEFAULT 'OFF',
    ADD COLUMN adsense_client                varchar(30),
    ADD COLUMN adsense_adtest                boolean      NOT NULL DEFAULT false,
    ADD COLUMN adsense_wide_banner           varchar(10),
    ADD COLUMN adsense_strip_banner          varchar(10),
    ADD COLUMN adsense_square_banner         varchar(10),
    ADD COLUMN adsense_small_banner          varchar(10),
    ADD COLUMN adsense_wing_banner           varchar(10),
    ADD COLUMN adsense_post_inline           varchar(10),
    ADD COLUMN adsense_board_list_slot       varchar(10),
    ADD COLUMN adsense_board_list_layout_key varchar(64),
    ADD CONSTRAINT ck_aps_adsense_mode CHECK (adsense_mode IN ('OFF', 'VERIFY', 'SHOW')),
    ADD CONSTRAINT ck_aps_adsense_client CHECK (adsense_client IS NULL OR adsense_client ~ '^ca-pub-[0-9]{16}$'),
    ADD CONSTRAINT ck_aps_adsense_client_needed CHECK (adsense_mode = 'OFF' OR adsense_client IS NOT NULL),
    ADD CONSTRAINT ck_aps_adsense_units CHECK (
        (adsense_wide_banner IS NULL OR adsense_wide_banner ~ '^[0-9]{10}$')
        AND (adsense_strip_banner IS NULL OR adsense_strip_banner ~ '^[0-9]{10}$')
        AND (adsense_square_banner IS NULL OR adsense_square_banner ~ '^[0-9]{10}$')
        AND (adsense_small_banner IS NULL OR adsense_small_banner ~ '^[0-9]{10}$')
        AND (adsense_wing_banner IS NULL OR adsense_wing_banner ~ '^[0-9]{10}$')
        AND (adsense_post_inline IS NULL OR adsense_post_inline ~ '^[0-9]{10}$')
        AND (adsense_board_list_slot IS NULL OR adsense_board_list_slot ~ '^[0-9]{10}$')),
    ADD CONSTRAINT ck_aps_adsense_in_feed CHECK ((adsense_board_list_slot IS NULL) = (adsense_board_list_layout_key IS NULL));
