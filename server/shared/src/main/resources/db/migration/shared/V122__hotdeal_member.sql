-- 회원 핫딜 1차 — docs/features/hotdeal-contract.md(D1~D5·확정 가정·설정 표·서버 단계 고정 규칙)
-- 1단계(기반 + 글쓰기·읽기·링크 판정·구매 링크)가 쓰는 표와, 2·3단계(종료 요청·keep·보상·의심 후보·배치·관리자)가 쓸 표를 이 파일에서 한 번에 만든다.
-- 글 표는 파티션 표라 post_id 외래 키가 없다(post_polls 와 같은 방식 — 글이 지워지면 공개 쪽은 글을 읽지 못해 딜도 보이지 않는다).

-- ── 글에 붙은 딜(글 하나에 하나) ─────────────────────────────────────────────
-- source: MEMBER(1차) · BUSINESS(2차 자리). product_host = 판정한 호스트(소문자·www. 뗌), final_host = 리디렉션을 따라간 최종 호스트(같거나 모르면 NULL).
-- product_url_key = 같은 딜 키(규칙 3 — SHA-256 hex). 가격은 원 정수(배송비 0 무료 · NULL 모름).
-- status: ACTIVE · ENDED(저장). FLAGGED·EXPIRED 는 읽을 때 계산한다(flagged_at · ends_at/created_at + autoExpireDays).
-- ended_by: AUTHOR · MANAGER · AUTO, ended_by_id = 종료한 회원(AUTHOR)·운영자(MANAGER) ID, end_note = 운영진 종료 사유(작성자에게 가는 글).
-- reopen_used: 작성자 되돌리기(D2)를 썼다. keep_count: 「아직 진행 중」 누른 수. price_edit_count: 딜가·정상가를 바꾼 수정 줄 수(D3 — 배송비 제외).
-- link_changed: 호스트가 바뀐 적이 있다. click_count: 구매 링크 클릭(하루 같은 방문자 1번 — deal_click_daily). expiry_notified_at: 기간 종료 알림 1회(2단계 배치).
-- 이미지: image_source OG(우리 서버 사본 deal_og_images.id = og_image_id) · UPLOAD(글 이미지 files.id = image_id), image_url = 우리 서버 주소.
CREATE TABLE post_deals (
    post_id            bigint        PRIMARY KEY,
    board_id           bigint        NOT NULL,
    author_id          bigint,
    source             varchar(10)   NOT NULL DEFAULT 'MEMBER',
    store_name         varchar(40)   NOT NULL,
    product_url        varchar(1000) NOT NULL,
    product_host       varchar(253)  NOT NULL,
    final_host         varchar(253),
    product_url_key    char(64)      NOT NULL,
    normal_price       bigint,
    deal_price         bigint        NOT NULL,
    shipping_fee       bigint,
    coupon_note        varchar(100),
    price_basis_kind   varchar(10),
    duty_included      boolean       NOT NULL DEFAULT false,
    price_basis        varchar(100),
    ends_at            timestamptz,
    stock_state        varchar(10)   NOT NULL DEFAULT 'UNKNOWN',
    affiliate          boolean       NOT NULL DEFAULT false,
    image_source       varchar(10),
    og_image_id        bigint,
    image_id           bigint,
    image_url          varchar(1000),
    status             varchar(10)   NOT NULL DEFAULT 'ACTIVE',
    ended_by           varchar(10),
    ended_by_id        bigint,
    ended_at           timestamptz,
    end_note           varchar(200),
    reopen_used        boolean       NOT NULL DEFAULT false,
    reopened_at        timestamptz,
    flagged_at         timestamptz,
    kept_at            timestamptz,
    keep_count         int           NOT NULL DEFAULT 0,
    price_edit_count   int           NOT NULL DEFAULT 0,
    link_changed       boolean       NOT NULL DEFAULT false,
    last_edited_at     timestamptz,
    click_count        bigint        NOT NULL DEFAULT 0,
    expiry_notified_at timestamptz,
    created_at         timestamptz   NOT NULL,
    updated_at         timestamptz   NOT NULL,
    CONSTRAINT ck_post_deals_source CHECK (source IN ('MEMBER', 'BUSINESS')),
    CONSTRAINT ck_post_deals_store CHECK (length(btrim(store_name)) BETWEEN 1 AND 40),
    CONSTRAINT ck_post_deals_url CHECK (product_url ~* '^https?://'),
    CONSTRAINT ck_post_deals_key CHECK (product_url_key ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_post_deals_prices CHECK (deal_price >= 0 AND (normal_price IS NULL OR normal_price >= deal_price) AND (shipping_fee IS NULL OR shipping_fee >= 0)),
    CONSTRAINT ck_post_deals_basis CHECK (price_basis_kind IS NULL OR price_basis_kind IN ('LISTED', 'COUPON', 'CARD', 'MEMBER', 'OVERSEAS')),
    CONSTRAINT ck_post_deals_duty CHECK (NOT duty_included OR price_basis_kind = 'OVERSEAS'),
    CONSTRAINT ck_post_deals_stock CHECK (stock_state IN ('UNKNOWN', 'IN_STOCK', 'LOW', 'SOLD_OUT')),
    CONSTRAINT ck_post_deals_image CHECK ((image_source IS NULL AND og_image_id IS NULL AND image_id IS NULL AND image_url IS NULL)
        OR (image_source = 'OG' AND og_image_id IS NOT NULL AND image_id IS NULL AND image_url IS NOT NULL)
        OR (image_source = 'UPLOAD' AND image_id IS NOT NULL AND og_image_id IS NULL AND image_url IS NOT NULL)),
    CONSTRAINT ck_post_deals_status CHECK (status IN ('ACTIVE', 'ENDED')),
    CONSTRAINT ck_post_deals_ended CHECK ((status = 'ENDED') = (ended_by IS NOT NULL AND ended_at IS NOT NULL)),
    CONSTRAINT ck_post_deals_ended_by CHECK (ended_by IS NULL OR ended_by IN ('AUTHOR', 'MANAGER', 'AUTO')),
    CONSTRAINT ck_post_deals_counts CHECK (keep_count >= 0 AND price_edit_count >= 0 AND click_count >= 0)
);
-- 같은 딜(공개·진행 중만 — 종료는 status 로, 기간 종료·의심은 읽을 때 거른다)
CREATE INDEX ix_post_deals_key ON post_deals (product_url_key, post_id DESC) WHERE status = 'ACTIVE';
-- 회원 하루 글 수 · 같은 판매처 간격 · 위장 홍보 회원 후보(같은 회원 · 같은 판매처 · 기간)
CREATE INDEX ix_post_deals_author ON post_deals (author_id, created_at DESC);
CREATE INDEX ix_post_deals_author_host ON post_deals (author_id, product_host, created_at DESC);
-- 판매처 후보 · 관리자 「차단 도메인 진행 중」 거르기
CREATE INDEX ix_post_deals_host ON post_deals (product_host, created_at DESC);
CREATE INDEX ix_post_deals_final_host ON post_deals (final_host) WHERE final_host IS NOT NULL AND status = 'ACTIVE';
-- 쇼핑몰 칩(게시판 · 이름)
CREATE INDEX ix_post_deals_board_store ON post_deals (board_id, store_name);
-- 배치: 의심 유예 지남 → 자동 종료 · 마감 있는 딜의 기간 종료 알림 · 마감 없는 딜의 기간 종료 알림
CREATE INDEX ix_post_deals_flagged ON post_deals (flagged_at) WHERE status = 'ACTIVE' AND flagged_at IS NOT NULL;
CREATE INDEX ix_post_deals_ends ON post_deals (ends_at) WHERE status = 'ACTIVE' AND ends_at IS NOT NULL AND expiry_notified_at IS NULL;
CREATE INDEX ix_post_deals_open_ended ON post_deals (created_at) WHERE status = 'ACTIVE' AND ends_at IS NULL AND expiry_notified_at IS NULL;
-- 관리자 목록(게시판 · 최신)
CREATE INDEX ix_post_deals_board ON post_deals (board_id, post_id DESC);
-- OG 사본 정리(2단계 배치 — 그 사본을 쓰는 다른 딜이 있는지)
CREATE INDEX ix_post_deals_og ON post_deals (og_image_id) WHERE og_image_id IS NOT NULL;

-- ── 딜 칸 수정 이력(D3 — 6개월 보존, 정리는 2단계 배치) ──────────────────────────
-- actor_kind: AUTHOR(작성자 고치기) · MANAGER(운영진). changes = [{field, before, after}] (바뀐 딜 칸만, 값은 글자). host_changed = 링크 호스트가 바뀐 수정.
-- price_edit = 딜가·정상가를 바꾼 줄(「가격 수정 N회」의 셈 — 배송비만 바꾼 줄은 false).
CREATE TABLE post_deal_revisions (
    id           bigint      PRIMARY KEY,
    post_id      bigint      NOT NULL,
    actor_kind   varchar(10) NOT NULL,
    actor_id     bigint,
    changes      jsonb       NOT NULL,
    host_changed boolean     NOT NULL DEFAULT false,
    price_edit   boolean     NOT NULL DEFAULT false,
    created_at   timestamptz NOT NULL,
    CONSTRAINT ck_post_deal_revisions_actor CHECK (actor_kind IN ('AUTHOR', 'MANAGER')),
    CONSTRAINT ck_post_deal_revisions_changes CHECK (jsonb_typeof(changes) = 'array' AND jsonb_array_length(changes) > 0)
);
CREATE INDEX ix_post_deal_revisions_post ON post_deal_revisions (post_id, created_at DESC, id DESC);
CREATE INDEX ix_post_deal_revisions_created ON post_deal_revisions (created_at);

-- ── 링크 규칙 · 쇼핑몰 이름 표 ──────────────────────────────────────────────
-- domain: 정규화한 호스트(소문자·www. 뗌 — 접미사 일치, 더 구체적인 규칙 우선). rule: BLOCK · AFFILIATE · NULL(대형몰·이름만).
-- common: 대형몰(위장 홍보 후보 계산에서 빠짐). shortener: 단축 주소 차단(화면이 「원래 주소로 바꿔 주세요」를 따로 보인다).
-- key_params: 같은 딜 키에 남길 쿼리 이름(있으면 그 쿼리만 — 규칙 3), store_name: 판정 때 자동으로 채울 쇼핑몰 이름.
CREATE TABLE deal_domains (
    id         bigint        PRIMARY KEY,
    domain     varchar(253)  NOT NULL,
    rule       varchar(10),
    common     boolean       NOT NULL DEFAULT false,
    shortener  boolean       NOT NULL DEFAULT false,
    key_params varchar(40)[] NOT NULL DEFAULT '{}',
    store_name varchar(40),
    note       varchar(200),
    created_at timestamptz   NOT NULL,
    updated_at timestamptz   NOT NULL,
    updated_by bigint,
    CONSTRAINT uq_deal_domains_domain UNIQUE (domain),
    CONSTRAINT ck_deal_domains_domain CHECK (domain ~ '^[a-z0-9]([a-z0-9-]*[a-z0-9])?(\.[a-z0-9]([a-z0-9-]*[a-z0-9])?)+$'),
    CONSTRAINT ck_deal_domains_rule CHECK (rule IS NULL OR rule IN ('BLOCK', 'AFFILIATE')),
    CONSTRAINT ck_deal_domains_shortener CHECK (NOT shortener OR rule = 'BLOCK'),
    CONSTRAINT ck_deal_domains_key_params CHECK (cardinality(key_params) <= 10)
);

-- ── 종료 요청(2단계) ─────────────────────────────────────────────────────────
-- 글 · 회원마다 한 번(post+member 유일). reason: SOLD_OUT · PRICE_CHANGED · LINK_DEAD. author_id = 그 글의 작성자(같은 작성자 기간 한도).
-- flagged_period: 의심 구간의 요청(보상 대상 — 규칙 5). from_report: 가격 허위 신고(D4)로 접수된 요청. 요청자 신원은 어떤 응답에도 내지 않는다.
CREATE TABLE deal_end_requests (
    id             bigint      PRIMARY KEY,
    post_id        bigint      NOT NULL,
    member_id      bigint      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    author_id      bigint,
    reason         varchar(16) NOT NULL,
    flagged_period boolean     NOT NULL DEFAULT false,
    from_report    boolean     NOT NULL DEFAULT false,
    created_at     timestamptz NOT NULL,
    CONSTRAINT uq_deal_end_requests UNIQUE (post_id, member_id),
    CONSTRAINT ck_deal_end_requests_reason CHECK (reason IN ('SOLD_OUT', 'PRICE_CHANGED', 'LINK_DEAD'))
);
CREATE INDEX ix_deal_end_requests_member ON deal_end_requests (member_id, created_at DESC);
CREATE INDEX ix_deal_end_requests_member_author ON deal_end_requests (member_id, author_id, created_at DESC);

-- ── 위장 홍보 의심 후보(2단계 집계 · 3단계 관리자) ────────────────────────────────
-- kind: MEMBER(같은 회원이 기간 안 같은 비대형몰 N건) · STORE(같은 비대형몰을 서로 다른 회원 N명, 다른 핫딜 비율 낮음). status: OPEN · DISMISSED. 자동 제재 없음.
CREATE TABLE deal_suspicions (
    id                  bigint       PRIMARY KEY,
    kind                varchar(10)  NOT NULL,
    status              varchar(10)  NOT NULL DEFAULT 'OPEN',
    member_id           bigint,
    host                varchar(253) NOT NULL,
    store_name          varchar(40),
    post_count          int          NOT NULL DEFAULT 0,
    member_count        int          NOT NULL DEFAULT 0,
    other_ratio_percent int,
    window_days         int          NOT NULL,
    post_ids            bigint[]     NOT NULL DEFAULT '{}',
    detected_at         timestamptz  NOT NULL,
    updated_at          timestamptz  NOT NULL,
    dismissed_at        timestamptz,
    dismissed_by        bigint,
    dismiss_note        varchar(200),
    CONSTRAINT ck_deal_suspicions_kind CHECK (kind IN ('MEMBER', 'STORE')),
    CONSTRAINT ck_deal_suspicions_status CHECK (status IN ('OPEN', 'DISMISSED')),
    CONSTRAINT ck_deal_suspicions_member CHECK ((kind = 'MEMBER') = (member_id IS NOT NULL)),
    CONSTRAINT ck_deal_suspicions_dismissed CHECK ((status = 'DISMISSED') = (dismissed_at IS NOT NULL))
);
CREATE UNIQUE INDEX ux_deal_suspicions_open ON deal_suspicions (kind, host, coalesce(member_id, 0)) WHERE status = 'OPEN';
CREATE INDEX ix_deal_suspicions_list ON deal_suspicions (status, kind, detected_at DESC, id DESC);

-- ── 핫딜 설정(한 행) ─────────────────────────────────────────────────────────
-- 범위는 계약 「핫딜 설정」 표. 보상 포인트·하루 지급 횟수는 포인트 정책(point_settings.deal_end_request_*) 소유. OG 보안 값은 여기에 두지 않는다(서버 규칙 1).
CREATE TABLE deal_settings (
    id                                  int         PRIMARY KEY,
    end_request_threshold               int         NOT NULL DEFAULT 3,
    end_grace_hours                     int         NOT NULL DEFAULT 12,
    end_request_daily_limit             int         NOT NULL DEFAULT 20,
    end_request_per_author_limit        int         NOT NULL DEFAULT 3,
    end_request_per_author_days         int         NOT NULL DEFAULT 7,
    keep_max_count                      int         NOT NULL DEFAULT 2,
    member_daily_post_limit             int         NOT NULL DEFAULT 5,
    same_store_interval_minutes         int         NOT NULL DEFAULT 10,
    auto_expire_days                    int         NOT NULL DEFAULT 30,
    price_max_won                       bigint      NOT NULL DEFAULT 100000000,
    discount_warn_percent               int         NOT NULL DEFAULT 90,
    hot_like_threshold                  int         NOT NULL DEFAULT 10,
    suspicion_min_posts                 int         NOT NULL DEFAULT 3,
    suspicion_window_days               int         NOT NULL DEFAULT 7,
    store_suspicion_members             int         NOT NULL DEFAULT 3,
    store_suspicion_window_days         int         NOT NULL DEFAULT 30,
    store_suspicion_other_ratio_percent int         NOT NULL DEFAULT 20,
    og_fetch_enabled                    boolean     NOT NULL DEFAULT true,
    affiliate_mode                      varchar(10) NOT NULL DEFAULT 'MEMBER',
    updated_at                          timestamptz NOT NULL,
    updated_by                          bigint,
    CONSTRAINT ck_deal_settings_row CHECK (id = 1),
    CONSTRAINT ck_deal_settings_end CHECK (end_request_threshold BETWEEN 1 AND 20 AND end_grace_hours BETWEEN 0 AND 168
        AND end_request_daily_limit BETWEEN 1 AND 100 AND end_request_per_author_limit BETWEEN 0 AND 20 AND end_request_per_author_days BETWEEN 1 AND 30
        AND keep_max_count BETWEEN 0 AND 10),
    CONSTRAINT ck_deal_settings_write CHECK (member_daily_post_limit BETWEEN 0 AND 50 AND same_store_interval_minutes BETWEEN 0 AND 1440
        AND auto_expire_days BETWEEN 0 AND 365),
    CONSTRAINT ck_deal_settings_price CHECK (price_max_won BETWEEN 1000000 AND 1000000000 AND discount_warn_percent BETWEEN 50 AND 99),
    CONSTRAINT ck_deal_settings_hot CHECK (hot_like_threshold BETWEEN 1 AND 100),
    CONSTRAINT ck_deal_settings_suspicion CHECK (suspicion_min_posts BETWEEN 0 AND 50 AND suspicion_window_days BETWEEN 1 AND 90
        AND store_suspicion_members BETWEEN 0 AND 20 AND store_suspicion_window_days BETWEEN 1 AND 90 AND store_suspicion_other_ratio_percent BETWEEN 0 AND 100),
    CONSTRAINT ck_deal_settings_affiliate CHECK (affiliate_mode IN ('MEMBER', 'SITE'))
);
INSERT INTO deal_settings (id, updated_at) VALUES (1, now());

-- ── 사이트 제휴 몰(D5 — SITE 모드에서 구매 링크 변환) ─────────────────────────────
-- transform_type TEMPLATE: template 의 {url} 자리에 encodeURIComponent(원래 주소) · QUERY: query(k=v&…)를 붙이되 같은 이름은 바꾼다. 시드 없음(운영하며 채움).
CREATE TABLE deal_site_affiliates (
    id             bigint        PRIMARY KEY,
    domain         varchar(253)  NOT NULL,
    program_name   varchar(40)   NOT NULL,
    transform_type varchar(10)   NOT NULL,
    template       varchar(1000),
    query          varchar(300),
    enabled        boolean       NOT NULL DEFAULT true,
    created_at     timestamptz   NOT NULL,
    updated_at     timestamptz   NOT NULL,
    updated_by     bigint,
    CONSTRAINT uq_deal_site_affiliates_domain UNIQUE (domain),
    CONSTRAINT ck_deal_site_affiliates_domain CHECK (domain ~ '^[a-z0-9]([a-z0-9-]*[a-z0-9])?(\.[a-z0-9]([a-z0-9-]*[a-z0-9])?)+$'),
    CONSTRAINT ck_deal_site_affiliates_name CHECK (length(btrim(program_name)) BETWEEN 1 AND 40),
    CONSTRAINT ck_deal_site_affiliates_transform CHECK ((transform_type = 'TEMPLATE' AND template IS NOT NULL AND position('{url}' IN template) > 0 AND query IS NULL)
        OR (transform_type = 'QUERY' AND query IS NOT NULL AND length(btrim(query)) > 0 AND template IS NULL))
);

-- ── OG 이미지 사본(D1) ──────────────────────────────────────────────────────
-- link-check 가 가져와 우리 저장소에 둔 사본 한 장. origin_url = 원본 이미지 주소(저작권 요청 때 지운다 — 공개 응답에 싣지 않는다).
-- created_by = 판정을 부른 회원, post_id = 붙은 글(글 저장 때). removed_at = 파일을 지운 시각(딜 종료·글 삭제 90일 뒤 · 붙지 않은 사본 정리 — 2단계 배치).
CREATE TABLE deal_og_images (
    id           bigint        PRIMARY KEY,
    origin_url   varchar(2000),
    origin_host  varchar(253),
    storage_key  varchar(300)  NOT NULL,
    public_url   varchar(1000) NOT NULL,
    content_type varchar(40)   NOT NULL,
    size_bytes   int           NOT NULL,
    width        int           NOT NULL,
    height       int           NOT NULL,
    created_by   bigint,
    post_id      bigint,
    created_at   timestamptz   NOT NULL,
    attached_at  timestamptz,
    removed_at   timestamptz,
    CONSTRAINT uq_deal_og_images_key UNIQUE (storage_key),
    CONSTRAINT uq_deal_og_images_url UNIQUE (public_url)
);
CREATE INDEX ix_deal_og_images_post ON deal_og_images (post_id) WHERE post_id IS NOT NULL;
CREATE INDEX ix_deal_og_images_orphan ON deal_og_images (created_at) WHERE post_id IS NULL AND removed_at IS NULL;

-- ── 구매 링크 클릭 — 하루(한국 날짜) 같은 방문자 · 같은 글 1번(원본 IP 저장 없음 — 회원 ID 또는 IP+UA 의 HMAC) ──
CREATE TABLE deal_click_daily (
    post_id      bigint      NOT NULL,
    day          date        NOT NULL,
    visitor_hash varchar(64) NOT NULL,
    created_at   timestamptz NOT NULL,
    PRIMARY KEY (post_id, day, visitor_hash)
);
CREATE INDEX ix_deal_click_daily_day ON deal_click_daily (day);

-- ── 링크 규칙 시드 ──────────────────────────────────────────────────────────
-- ID 는 그 시각의 Snowflake(EPOCH 2026-01-01 UTC, 노드 1023 — 마이그레이션용 노드, V43·V104 와 같은 방식) + 순번.
-- 단축 주소 8개 = BLOCK + shortener(메모 「단축 주소」). coupa.ng · 제휴 네트워크(링크프라이스·애드픽·텐핑·링크마인·쿠팡 파트너스·알리 제휴) = AFFILIATE.
-- 대형몰 = common + 쇼핑몰 이름(key_params 는 상품 번호가 쿼리에 있는 몰만 — 나머지는 운영하며 채운다).
INSERT INTO deal_domains (id, domain, rule, common, shortener, key_params, store_name, note, created_at, updated_at)
SELECT ((greatest(0, (extract(epoch FROM clock_timestamp()) * 1000)::bigint - 1767225600000)) << 22) | (1023::bigint << 12) | s.n,
       s.domain, s.rule, s.common, s.shortener, s.key_params::varchar(40)[], s.store_name, s.note, now(), now()
  FROM (VALUES
        (1, 'bit.ly', 'BLOCK', false, true, '{}', NULL, '단축 주소'),
        (2, 'han.gl', 'BLOCK', false, true, '{}', NULL, '단축 주소'),
        (3, 'vo.la', 'BLOCK', false, true, '{}', NULL, '단축 주소'),
        (4, 'me2.do', 'BLOCK', false, true, '{}', NULL, '단축 주소'),
        (5, 't.co', 'BLOCK', false, true, '{}', NULL, '단축 주소'),
        (6, 'goo.gl', 'BLOCK', false, true, '{}', NULL, '단축 주소'),
        (7, 'tinyurl.com', 'BLOCK', false, true, '{}', NULL, '단축 주소'),
        (8, 'url.kr', 'BLOCK', false, true, '{}', NULL, '단축 주소'),
        (20, 'coupa.ng', 'AFFILIATE', false, false, '{}', '쿠팡', '쿠팡 파트너스 단축 주소(공식 단축 여부 미확정)'),
        (21, 'link.coupang.com', 'AFFILIATE', false, false, '{}', '쿠팡', '쿠팡 파트너스'),
        (22, 'linkprice.com', 'AFFILIATE', false, false, '{}', NULL, '링크프라이스'),
        (23, 'adpick.co.kr', 'AFFILIATE', false, false, '{}', NULL, '애드픽'),
        (24, 'tenping.kr', 'AFFILIATE', false, false, '{}', NULL, '텐핑'),
        (25, 'linkmine.co.kr', 'AFFILIATE', false, false, '{}', NULL, '링크마인'),
        (26, 's.click.aliexpress.com', 'AFFILIATE', false, false, '{}', '알리익스프레스', '알리익스프레스 제휴'),
        (40, 'coupang.com', NULL, true, false, '{}', '쿠팡', NULL),
        (41, '11st.co.kr', NULL, true, false, '{prdNo}', '11번가', NULL),
        (42, 'gmarket.co.kr', NULL, true, false, '{goodscode}', 'G마켓', NULL),
        (43, 'auction.co.kr', NULL, true, false, '{itemno}', '옥션', NULL),
        (44, 'ssg.com', NULL, true, false, '{itemId}', 'SSG.COM', NULL),
        (45, 'shopping.naver.com', NULL, true, false, '{}', '네이버 쇼핑', NULL),
        (46, 'smartstore.naver.com', NULL, true, false, '{}', '네이버 스마트스토어', NULL),
        (47, 'brand.naver.com', NULL, true, false, '{}', '네이버 브랜드스토어', NULL),
        (48, 'lotteon.com', NULL, true, false, '{}', '롯데ON', NULL),
        (49, 'kurly.com', NULL, true, false, '{}', '컬리', NULL),
        (50, 'tmon.co.kr', NULL, true, false, '{}', '티몬', NULL),
        (51, 'wemakeprice.com', NULL, true, false, '{}', '위메프', NULL),
        (52, 'interpark.com', NULL, true, false, '{}', '인터파크', NULL),
        (53, 'musinsa.com', NULL, true, false, '{}', '무신사', NULL),
        (54, 'oliveyoung.co.kr', NULL, true, false, '{}', '올리브영', NULL),
        (55, 'aliexpress.com', NULL, true, false, '{}', '알리익스프레스', NULL),
        (56, 'temu.com', NULL, true, false, '{}', '테무', NULL),
        (57, 'amazon.com', NULL, true, false, '{}', '아마존', NULL)
       ) AS s (n, domain, rule, common, shortener, key_params, store_name, note);

-- ── 신고 사유 2개(D4 — 핫딜 글에서만, 앱이 막는다). V95 목록 ∪ DEAL_FALSE_PRICE · DEAL_HIDDEN_AD ──
-- ⚠ 합칠 때 합집합 확인 — 다른 세션도 이 CHECK 를 다시 만들 수 있다(나중에 적용되는 쪽이 모든 사유를 담아야 한다).
ALTER TABLE reports DROP CONSTRAINT ck_reports_reason;
ALTER TABLE reports ADD CONSTRAINT ck_reports_reason
    CHECK (reason IN ('SPAM_AD', 'ABUSE', 'OBSCENE', 'ILLEGAL_FILMING', 'RIGHTS', 'PROMOTION', 'OFFLINE_RISK', 'STOCK_RECOMMENDATION', 'OTHER',
                      'DEAL_FALSE_PRICE', 'DEAL_HIDDEN_AD'));
ALTER TABLE report_cases DROP CONSTRAINT ck_report_cases_reason_code;
ALTER TABLE report_cases ADD CONSTRAINT ck_report_cases_reason_code
    CHECK (reason_code IS NULL OR reason_code IN ('SPAM_AD', 'ABUSE', 'OBSCENE', 'ILLEGAL_FILMING', 'RIGHTS', 'PROMOTION', 'OFFLINE_RISK', 'STOCK_RECOMMENDATION',
                                                  'OTHER', 'DEAL_FALSE_PRICE', 'DEAL_HIDDEN_AD'));

-- ── 회원 알림 종류(메모 1-7 — 2단계가 발행). V112 목록 ∪ DEAL_FLAGGED · DEAL_AUTO_ENDED · DEAL_MANAGER_ENDED · DEAL_EXPIRED · DEAL_REOPENED_FIX_ENDS ──
-- ⚠ 합칠 때 합집합 확인 — 다른 세션도 이 CHECK 를 다시 만들 수 있다(나중에 적용되는 쪽이 모든 종류를 담아야 한다).
ALTER TABLE member_notifications DROP CONSTRAINT ck_member_notifications_type;
ALTER TABLE member_notifications ADD CONSTRAINT ck_member_notifications_type
    CHECK (type IN ('COMMENT', 'REPLY', 'POINTS_REVOKED', 'POST_HIDDEN', 'COMMENT_HIDDEN', 'DM', 'NICKNAME_IMAGE_REVIEWED', 'OPINION_PIN_REQUESTED',
                    'OPINION_PIN_REVIEWED', 'POST_REMOVED', 'COMMENT_REMOVED', 'REPORT_RESULT', 'FEATURE_RESTRICTED', 'POINTS_EXPIRING',
                    'POINT_SHOP_REFUND_APPROVED', 'POINT_SHOP_REFUND_REJECTED',
                    'GROUP_APPROVED', 'GROUP_REJECTED', 'GROUP_JOIN_REQUEST', 'GROUP_JOIN_APPROVED', 'GROUP_JOIN_REJECTED', 'GROUP_KICKED',
                    'GROUP_DISSOLVED', 'GROUP_LEADER_TRANSFERRED', 'GROUP_DEACTIVATED', 'GROUP_REACTIVATED',
                    'GROUP_POST_UNPUBLISHED',
                    'GROUP_MANAGER_APPOINTED', 'GROUP_MANAGER_PERMISSIONS_CHANGED', 'GROUP_MANAGER_REMOVED', 'GROUP_MANAGER_RESIGNED',
                    'GROUP_KICKED_BY_MANAGER',
                    'GROUP_EVENT',
                    'GROUP_PUBLISH_REQUESTED', 'GROUP_PUBLISH_APPROVED', 'GROUP_PUBLISH_REJECTED', 'GROUP_POST_PUBLISHED', 'GROUP_VISIBILITY_CHANGED',
                    'GROUP_POST_MEMBERS_ONLY', 'GROUP_POST_RELEASED',
                    'GROUP_BUY',
                    'BUSINESS',
                    'BIZ_APPLICATION',
                    'DEAL_FLAGGED', 'DEAL_AUTO_ENDED', 'DEAL_MANAGER_ENDED', 'DEAL_EXPIRED', 'DEAL_REOPENED_FIX_ENDS'));

-- ── 포인트 원장·묶음 대상 + DEAL_END_REQUEST(종료 요청 보상 — 대상 = 그 요청 줄 deal_end_requests.id, 원장 유일 (reason, ref_type, ref_id) 로 요청마다 한 번).
--    V89 목록 ∪ DEAL_END_REQUEST. ⚠ 합칠 때 합집합 확인 — 다른 세션도 이 CHECK 를 다시 만들 수 있다 ──
ALTER TABLE point_ledger DROP CONSTRAINT ck_point_ledger_ref;
ALTER TABLE point_ledger ADD CONSTRAINT ck_point_ledger_ref CHECK (ref_type IS NULL OR ref_type IN ('POST', 'COMMENT', 'POINT_SHOP_PURCHASE', 'POINT_CHARGE',
    'GB_PARTICIPATION', 'LOT', 'LEDGER', 'USER', 'ATTENDANCE', 'DEAL_END_REQUEST'));
ALTER TABLE point_lots DROP CONSTRAINT ck_point_lots_source_ref;
ALTER TABLE point_lots ADD CONSTRAINT ck_point_lots_source_ref CHECK (source_ref_type IS NULL OR source_ref_type IN ('POST', 'COMMENT', 'POINT_SHOP_PURCHASE',
    'POINT_CHARGE', 'GB_PARTICIPATION', 'LOT', 'LEDGER', 'USER', 'ATTENDANCE', 'DEAL_END_REQUEST'));

-- ── 핫딜 게시판은 19금 끔 고정(서버 규칙 8) — 이미 켠 핫딜 게시판이 있으면 끈다(앱도 저장·응답에서 막는다) ──
UPDATE boards SET allow_adult = false WHERE kind = 'DEAL' AND allow_adult;
