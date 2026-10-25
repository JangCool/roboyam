-- 인기글 설정(행 하나)·인기글에서 뺀 글 — docs/features/popular-highlight-contract.md 「서버 구현 결과」.
-- 지금까지 R__batch_24_popular_rank.sql 의 서버 상수였던 규칙 값을 이 표로 옮긴다(기본값은 그 상수와 같다). DB 배치 popular-rank 와 공개 GET /api/home/popular 가 이 행을 읽는다.
--   점수 = 추천 × like_weight + 댓글 × comment_weight + 조회 × view_weight, 실시간(REALTIME)만 ÷ (경과 시간(시) + age_offset_hours)^gravity.
--   후보: 추천 ≥ min_likes · 댓글 ≥ min_comments · 조회 ≥ min_views · 점수 > 0. exclude_staff_boards 면 운영진 전용 게시판(유효 글쓰기 자격 STAFF)의 글을 뺀다.
--   한 글쓴이는 한 탭에 max_per_author 개까지(채운 글 포함). week_start = 「이번 주」 탭의 시작 요일(MONDAY · SUNDAY).
-- 관리자 「인기글 설정」(admin, 조회 popular.read · 저장 popular.settings)이 PopularSettingsService 로만 바꾼다. 범위는 서버 PopularRules 와 같다.
CREATE TABLE popular_settings (
    id                   integer      PRIMARY KEY,
    like_weight          numeric(7,3) NOT NULL DEFAULT 3,
    comment_weight       numeric(7,3) NOT NULL DEFAULT 2,
    view_weight          numeric(7,3) NOT NULL DEFAULT 0.1,
    gravity              numeric(4,2) NOT NULL DEFAULT 1.5,
    age_offset_hours     numeric(5,2) NOT NULL DEFAULT 2,
    min_likes            integer      NOT NULL DEFAULT 3,
    min_comments         integer      NOT NULL DEFAULT 0,
    min_views            integer      NOT NULL DEFAULT 0,
    exclude_staff_boards boolean      NOT NULL DEFAULT true,
    max_per_author       integer      NOT NULL DEFAULT 2,
    week_start           varchar(6)   NOT NULL DEFAULT 'MONDAY',
    updated_at           timestamptz,
    updated_by           bigint       REFERENCES operators (id) ON DELETE SET NULL,
    CONSTRAINT ck_popular_settings_single_row CHECK (id = 1),
    CONSTRAINT ck_popular_settings_weights CHECK (like_weight BETWEEN 0 AND 100 AND comment_weight BETWEEN 0 AND 100 AND view_weight BETWEEN 0 AND 10
        AND like_weight + comment_weight + view_weight > 0),
    CONSTRAINT ck_popular_settings_decay CHECK (gravity BETWEEN 0.5 AND 3 AND age_offset_hours BETWEEN 0 AND 48),
    CONSTRAINT ck_popular_settings_minimums CHECK (min_likes BETWEEN 0 AND 100000 AND min_comments BETWEEN 0 AND 100000 AND min_views BETWEEN 0 AND 100000),
    CONSTRAINT ck_popular_settings_max_per_author CHECK (max_per_author BETWEEN 1 AND 10),
    CONSTRAINT ck_popular_settings_week_start CHECK (week_start IN ('MONDAY', 'SUNDAY'))
);
INSERT INTO popular_settings (id) VALUES (1);

-- 인기글에서 뺀 글 — 글마다 한 줄(다시 넣으면 줄을 지운다). 뺀 글은 세 기간(실시간·어제·이번 주)과 채우기에서 모두 빠지고(배치 후보 조건),
-- 공개 GET /api/home/popular 가 읽을 때도 다시 거른다. 글 자체(게시판·검색·최신글)는 그대로 보인다. 글쓴이에게 알리지 않는다.
-- 글 표는 파티션 표라 post_id 외래 키가 없다(글이 지워져도 줄은 남고 관리 화면에 available = false 로 보인다).
-- reason: INAPPROPRIATE(부적절한 내용) · PROMOTION(광고·홍보) · MANIPULATION(추천·조회 조작 의심) · DISPUTE(분쟁·논란) · OTHER(기타 — memo 필수).
CREATE TABLE popular_exclusions (
    post_id     bigint       PRIMARY KEY,
    reason      varchar(20)  NOT NULL,
    memo        varchar(100),
    excluded_by bigint       REFERENCES operators (id) ON DELETE SET NULL,
    excluded_at timestamptz  NOT NULL,
    CONSTRAINT ck_popular_exclusions_reason CHECK (reason IN ('INAPPROPRIATE', 'PROMOTION', 'MANIPULATION', 'DISPUTE', 'OTHER')),
    CONSTRAINT ck_popular_exclusions_memo CHECK (reason <> 'OTHER' OR memo IS NOT NULL)
);
-- 관리 화면의 「뺀 글」(최근에 뺀 것부터)
CREATE INDEX ix_popular_exclusions_recent ON popular_exclusions (excluded_at DESC, post_id DESC);
