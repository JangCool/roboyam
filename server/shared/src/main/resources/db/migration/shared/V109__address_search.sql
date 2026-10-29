-- 주소 검색(자체 주소 DB) — docs/features/address-search-contract.md(정본). pionworld2 core.address(V139~V142)를 옮겨 한 파일로 합쳤다.
-- 번호: shared 마지막 V108(가젯 페이지) 다음. 다른 브랜치·worktree 에 V109 가 없음을 2026-10-30 확인(합칠 때 다시 본다).
--
-- 무엇이 생기나
--   · address_entries — 행정안전부 「도로명주소 한글 전체분」을 관리자가 올려 통째로 넣은 주소 한 줄씩(검색 전용 — JPA 엔티티 없음, 앱이 JDBC 로 읽고 쓴다).
--     search_text(도로명 주소 + 읍면동 + 건물 이름 + 지번 + 영문)에 pg_trgm GIN 색인 — 부분 일치(ILIKE '%낱말%') 검색. pg_trgm 은 V1 이 만들었다.
--   · address_entries_staging — 같은 모양. 새 파일은 여기에 다 넣은 뒤 이름 세 번 바꾸기(RENAME)로 address_entries 와 맞바꾼다 — 적재하는 몇 분 동안에도
--     회원 검색은 앞 데이터로 된다. 맞바꿀 때 색인도 표를 따라가므로 색인 이름은 표 이름과 맞지 않을 수 있다(앱이 적재마다 스테이징 색인을 지우고 새로 만든다).
--   · address_jibun_staging · address_english_staging — 관련지번·영문 파일을 잠깐 담는 칸(적재 중에만 쓰고 비운다). 다시 만들 수 있는 값이라 UNLOGGED.
--   · address_search_settings — 사이트에 한 줄(id = 1): 제공처(DAUM | INTERNAL | DAUM_FALLBACK_INTERNAL) · 다음 스크립트 주소(NULL = 공식 주소) · 갱신 권고 일수.
--   · address_db_loads — 적재 한 번에 한 줄(진행 중 RUNNING → DONE | FAILED). 돌고 있는 적재는 사이트에 하나(부분 유일 색인). 적재 이력 = DONE 줄.

CREATE TABLE address_entries (
    id                bigserial     PRIMARY KEY,
    road_addr_mgmt_no varchar(30)   NOT NULL,
    zipcode           varchar(5)    NOT NULL,
    sido              varchar(40)   NOT NULL,
    sigungu           varchar(60)   NOT NULL DEFAULT '',
    eupmyeondong      varchar(80)   NOT NULL DEFAULT '',
    road_name         varchar(120)  NOT NULL,
    building_no_main  integer       NOT NULL,
    building_no_sub   integer       NOT NULL DEFAULT 0,
    building_name     varchar(200)  NOT NULL DEFAULT '',
    road_address      text          NOT NULL,
    jibun_address     text,
    road_address_en   text,
    search_text       text          NOT NULL
);

CREATE INDEX ix_address_entries_a_search_trgm ON address_entries USING gin (search_text gin_trgm_ops);
CREATE INDEX ix_address_entries_a_mgmt_no ON address_entries (road_addr_mgmt_no);

CREATE TABLE address_entries_staging (
    id                bigserial     PRIMARY KEY,
    road_addr_mgmt_no varchar(30)   NOT NULL,
    zipcode           varchar(5)    NOT NULL,
    sido              varchar(40)   NOT NULL,
    sigungu           varchar(60)   NOT NULL DEFAULT '',
    eupmyeondong      varchar(80)   NOT NULL DEFAULT '',
    road_name         varchar(120)  NOT NULL,
    building_no_main  integer       NOT NULL,
    building_no_sub   integer       NOT NULL DEFAULT 0,
    building_name     varchar(200)  NOT NULL DEFAULT '',
    road_address      text          NOT NULL,
    jibun_address     text,
    road_address_en   text,
    search_text       text          NOT NULL
);

CREATE UNLOGGED TABLE address_jibun_staging (
    road_addr_mgmt_no varchar(30)  NOT NULL,
    beopjeong_dong    varchar(40)  NOT NULL DEFAULT '',
    beopjeong_ri      varchar(40)  NOT NULL DEFAULT '',
    is_mountain       boolean      NOT NULL DEFAULT false,
    jibun_main        integer      NOT NULL DEFAULT 0,
    jibun_sub         integer      NOT NULL DEFAULT 0
);

CREATE UNLOGGED TABLE address_english_staging (
    road_addr_mgmt_no varchar(30)  NOT NULL,
    road_address_en   text         NOT NULL
);

CREATE TABLE address_search_settings (
    id               smallint     PRIMARY KEY,
    provider         varchar(30)  NOT NULL DEFAULT 'DAUM',
    script_url       varchar(500),
    stale_warn_days  integer      NOT NULL DEFAULT 60,
    updated_at       timestamptz,
    updated_by       bigint       REFERENCES operators (id) ON DELETE SET NULL,
    CONSTRAINT ck_address_search_settings_single CHECK (id = 1),
    CONSTRAINT ck_address_search_settings_provider CHECK (provider IN ('DAUM', 'INTERNAL', 'DAUM_FALLBACK_INTERNAL')),
    CONSTRAINT ck_address_search_settings_days CHECK (stale_warn_days BETWEEN 7 AND 365)
);

INSERT INTO address_search_settings (id, provider, script_url, stale_warn_days) VALUES (1, 'DAUM', NULL, 60);

CREATE TABLE address_db_loads (
    id             bigint        PRIMARY KEY,
    status         varchar(10)   NOT NULL,
    phase          varchar(10)   NOT NULL,
    file_names     text          NOT NULL,
    charset        varchar(10)   NOT NULL,
    node_id        integer       NOT NULL,
    started_at     timestamptz   NOT NULL,
    started_by     bigint        REFERENCES operators (id) ON DELETE SET NULL,
    finished_at    timestamptz,
    entry_count    bigint,
    jibun_count    bigint,
    english_count  bigint,
    message_key    varchar(80),
    message_args   text,
    CONSTRAINT ck_address_db_loads_status CHECK (status IN ('RUNNING', 'DONE', 'FAILED')),
    CONSTRAINT ck_address_db_loads_phase CHECK (phase IN ('COUNTING', 'PARSING', 'MERGING', 'SWAPPING', 'DONE', 'FAILED'))
);

-- 돌고 있는 적재는 사이트에 하나 — 두 번째 시작은 이 색인에 걸려 409 ADDRESS_IMPORT_RUNNING
CREATE UNIQUE INDEX ux_address_db_loads_running ON address_db_loads ((true)) WHERE status = 'RUNNING';
CREATE INDEX ix_address_db_loads_done ON address_db_loads (finished_at DESC) WHERE status = 'DONE';
