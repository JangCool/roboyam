-- 공개 광고 노출(docs/features/ad-contract.md 「공개 노출 목업」 확정 결정 2026-11-01) — 일별 노출·클릭 집계 · 하루 1번 판정 · 노출 자리 설정.
--   · ad_daily_stats — 신청 건 · 소재 버전 · 한국 날짜 · 자리마다 노출·클릭 수(00 D9 「1차 최소 일별 집계」, A2 「집계는 버전 단위」). 신청 건 장부(biz_applications)는 5년 보관이라
--     지우지 않으므로 외래키를 건다.
--   · ad_view_marks — 부풀림 거르기: 같은 방문자 · 같은 광고 버전 · 같은 자리는 한국 하루 노출 1번·클릭 1번. 방문자 = 회원 ID 또는 IP + User-Agent 를 서버 해시 키
--     (BlindIndex — ROBOYAM_INDEX_KEY, 용도 ad.visitor, 날짜를 섞어 날마다 다른 값)로 HMAC 한 64자 — 원본 IP·UA·회원 ID 를 저장하지 않는다. 키가 없으면 세지 않는다.
--     INSERT … ON CONFLICT DO NOTHING 이 들어갔을 때만 집계에 +1(한 문장 — PublicAdStatsStore). 2일이 지난 행은 DB 배치 ad-view-mark-cleanup(R__batch_33)이 지운다.
--   · ad_placement_settings — 글 보기 중간 광고 · 날개 배너 · 게시판 목록 한 줄의 켜기/끄기·유형·위치(한 행, id = 1). 행이 없으면 서버 기본값
--     (AdPlacementStore.DEFAULTS — 글 보기 켬·한 줄 광고·카드 사이, 날개 양쪽 켬·홈/가젯 페이지/게시판/글 보기, 목록 한 줄 켬·5번째 뒤).
-- ID·시각 규칙: 시각 timestamptz, 날짜 date(한국 날짜).

CREATE TABLE ad_daily_stats (
    application_id  bigint       NOT NULL REFERENCES biz_applications (id),
    version         integer      NOT NULL,
    day             date         NOT NULL,
    placement       varchar(20)  NOT NULL,
    impressions     bigint       NOT NULL DEFAULT 0,
    clicks          bigint       NOT NULL DEFAULT 0,
    updated_at      timestamptz  NOT NULL DEFAULT now(),
    PRIMARY KEY (application_id, version, day, placement),
    CONSTRAINT ck_ads_counts CHECK (impressions >= 0 AND clicks >= 0),
    CONSTRAINT ck_ads_placement CHECK (placement IN ('HOME', 'PAGE', 'BOARD', 'POST_INLINE', 'HOTDEAL_TOP', 'HOTDEAL_CATEGORY', 'WING_LEFT', 'WING_RIGHT', 'BOARD_LIST'))
);
CREATE INDEX ix_ads_day ON ad_daily_stats (day);

-- 하루 1번 판정 — PK 의 맨 앞이 날짜라 정리 배치가 날짜 범위로 지운다
CREATE TABLE ad_view_marks (
    day             date         NOT NULL,
    visitor_hash    char(64)     NOT NULL,
    kind            char(1)      NOT NULL,
    application_id  bigint       NOT NULL,
    version         integer      NOT NULL,
    placement       varchar(20)  NOT NULL,
    created_at      timestamptz  NOT NULL DEFAULT now(),
    PRIMARY KEY (day, visitor_hash, kind, application_id, version, placement),
    CONSTRAINT ck_avm_kind CHECK (kind IN ('I', 'C'))
);

CREATE TABLE ad_placement_settings (
    id                    smallint     PRIMARY KEY DEFAULT 1,
    post_inline_enabled   boolean      NOT NULL,
    post_inline_type      varchar(20)  NOT NULL,
    post_inline_position  varchar(20)  NOT NULL,
    wing_left             boolean      NOT NULL,
    wing_right            boolean      NOT NULL,
    wing_screens          text[]       NOT NULL,
    board_list_enabled    boolean      NOT NULL,
    board_list_after      smallint     NOT NULL,
    updated_at            timestamptz  NOT NULL,
    updated_by            bigint       NOT NULL,
    CONSTRAINT ck_aps_single CHECK (id = 1),
    CONSTRAINT ck_aps_inline_type CHECK (post_inline_type IN ('TEXT_LINE', 'STRIP_BANNER', 'SQUARE_BANNER')),
    CONSTRAINT ck_aps_inline_position CHECK (post_inline_position IN ('BETWEEN_CARDS', 'AFTER_BODY')),
    CONSTRAINT ck_aps_wing_screens CHECK (wing_screens <@ ARRAY['HOME', 'PAGE', 'BOARD', 'POST']::text[]),
    CONSTRAINT ck_aps_board_list_after CHECK (board_list_after BETWEEN 1 AND 20)
);
