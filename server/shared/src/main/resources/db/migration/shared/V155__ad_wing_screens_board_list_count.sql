-- 날개 배너를 보일 화면에 최신글·인기글(FEED)·공동구매(GROUP_BUY)·소모임(GROUPS)을 더한다(사용자 2026-11-06 「넓혀 줘」 — ad-contract 「날개 화면 넓힘」).
-- 저장된 설정 행의 값은 바꾸지 않는다(운영자가 「광고 기본 설정」에서 고른다). 저장한 적 없으면 기본값이 일곱 화면 모두다(AdPlacements.DEFAULTS).
ALTER TABLE ad_placement_settings DROP CONSTRAINT ck_aps_wing_screens;
ALTER TABLE ad_placement_settings
    ADD CONSTRAINT ck_aps_wing_screens CHECK (wing_screens <@ ARRAY['HOME', 'PAGE', 'BOARD', 'POST', 'FEED', 'GROUP_BUY', 'GROUPS']::text[]);

-- 게시판 목록 한 줄 광고 — 한 쪽 개수(1~2, 기본 1)와 두 광고 사이 일반 글 수(3~20, 기본 10)(사용자 2026-11-06 「2개까지, 사이사이 간격 유지」)
ALTER TABLE ad_placement_settings
    ADD COLUMN board_list_count smallint NOT NULL DEFAULT 1,
    ADD COLUMN board_list_gap   smallint NOT NULL DEFAULT 10,
    ADD CONSTRAINT ck_aps_board_list_count CHECK (board_list_count BETWEEN 1 AND 2),
    ADD CONSTRAINT ck_aps_board_list_gap CHECK (board_list_gap BETWEEN 3 AND 20);
