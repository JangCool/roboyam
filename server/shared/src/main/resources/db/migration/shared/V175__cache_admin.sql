-- 공개 사이트 캐시 관리(docs/features/cache-admin-contract.md) — 서버 메모리 캐시(Spring Cache + Caffeine)의 설정·세대 번호·서버별 보고·기록.
-- 캐시 정의(이름·기본 TTL·쓰이는 곳)는 코드(CacheCatalog)가 원천이다. 이 표에 행이 없는 캐시는 정의의 기본값(TTL 기본값 · 켜짐 · 세대 0)으로 본다.

-- 캐시별 설정. ttl_seconds NULL = 정의의 기본 TTL. generation = 「지금 갱신」·자동(저장)마다 1 오른다 — 각 서버가 몇 초마다 읽어 바뀌었으면 자기 캐시를 비운다.
-- changed_at = 마지막 갱신 요청·켜기/끄기 시각(「갱신 대기」가 시작된 시각).
CREATE TABLE cache_settings (
    cache_key   varchar(64) PRIMARY KEY,
    ttl_seconds integer     CHECK (ttl_seconds IS NULL OR ttl_seconds BETWEEN 1 AND 86400),
    enabled     boolean     NOT NULL DEFAULT true,
    generation  bigint      NOT NULL DEFAULT 0,
    changed_at  timestamptz,
    updated_at  timestamptz NOT NULL,
    updated_by  bigint
);
-- 지금 카탈로그 25개의 행을 미리 넣는다(값은 정의의 기본값 — 세대 번호를 올릴 때 행 잠금으로 줄 세운다). 나중에 생기는 캐시는 처음 바꿀 때 행을 만든다.
INSERT INTO cache_settings (cache_key, updated_at)
SELECT k, now() FROM unnest(ARRAY[
    'home.layout', 'pages.layout', 'boards.layout', 'home.latest', 'home.popular', 'menu.public',
    'gadget.POST_LIST', 'gadget.HIGHLIGHT', 'gadget.GALLERY', 'gadget.BOARD_CARDS', 'gadget.GROUPS', 'gadget.GROUP_BUY', 'gadget.HOT_DEALS',
    'site.info', 'site.noticeBar', 'site.popups', 'terms.public', 'notice.global', 'board.catalog', 'stats.collection',
    'commerce.rateLimits', 'hotdeal.rules', 'hotdeal.settings', 'notification.templates', 'point.policy']) AS k;

-- 전체 캐시 스위치(1행, id = 1). 실제 적용 = 전체 켜짐 AND 캐시 켜짐. 행이 없으면 켜짐.
CREATE TABLE cache_global_settings (
    id         smallint    PRIMARY KEY CHECK (id = 1),
    enabled    boolean     NOT NULL DEFAULT true,
    changed_at timestamptz,
    updated_at timestamptz NOT NULL,
    updated_by bigint
);
INSERT INTO cache_global_settings (id, enabled, updated_at) VALUES (1, true, now());

-- 서버(노드)별 · 캐시별 상태 보고 — 각 서버가 약 10초마다(바뀐 것을 반영하면 곧바로도) 한 번에 덮어쓴다(ON CONFLICT). 오래 보고가 없는 서버는 읽을 때 뺀다.
CREATE TABLE cache_node_states (
    node_id            varchar(64) NOT NULL,
    cache_key          varchar(64) NOT NULL,
    role               varchar(8)  NOT NULL CHECK (role IN ('API', 'ADMIN')),
    applied_generation bigint      NOT NULL,
    applied_enabled    boolean     NOT NULL,
    last_cleared_at    timestamptz,
    last_checked_at    timestamptz,
    entry_count        bigint      NOT NULL DEFAULT 0,
    hit_count          bigint      NOT NULL DEFAULT 0,
    miss_count         bigint      NOT NULL DEFAULT 0,
    reported_at        timestamptz NOT NULL,
    PRIMARY KEY (node_id, cache_key)
);
CREATE INDEX cache_node_states_reported_idx ON cache_node_states (reported_at);

-- 갱신 기록 — 수동(지금 갱신·전체 갱신) · 자동(관리자 화면에서 관련 데이터 저장). TTL 만료는 기록하지 않는다.
-- source_code = 자동일 때 무엇을 저장해서인지(메시지 cache.source.<코드>), source_detail = 그 대상 이름(게시판 이름 등, 없으면 NULL).
CREATE TABLE cache_refresh_logs (
    id            bigint       PRIMARY KEY,
    cache_key     varchar(64)  NOT NULL,
    trigger_kind  varchar(16)  NOT NULL CHECK (trigger_kind IN ('MANUAL', 'AUTO_SAVE')),
    all_caches    boolean      NOT NULL DEFAULT false,
    source_code   varchar(64),
    source_detail varchar(200),
    operator_id   bigint,
    generation    bigint       NOT NULL,
    requested_at  timestamptz  NOT NULL
);
CREATE INDEX cache_refresh_logs_key_idx ON cache_refresh_logs (cache_key, requested_at DESC, id DESC);

-- 설정 변경 이력 — TTL · 이 캐시 켜기/끄기 · 전체 켜기/끄기(cache_key NULL).
CREATE TABLE cache_setting_changes (
    id           bigint      PRIMARY KEY,
    cache_key    varchar(64),
    kind         varchar(16) NOT NULL CHECK (kind IN ('TTL', 'ENABLED', 'GLOBAL_ENABLED')),
    from_seconds integer,
    to_seconds   integer,
    enabled      boolean,
    operator_id  bigint,
    changed_at   timestamptz NOT NULL,
    CHECK ((kind = 'GLOBAL_ENABLED') = (cache_key IS NULL))
);
CREATE INDEX cache_setting_changes_key_idx ON cache_setting_changes (cache_key, changed_at DESC, id DESC);
CREATE INDEX cache_setting_changes_global_idx ON cache_setting_changes (changed_at DESC, id DESC) WHERE cache_key IS NULL;
