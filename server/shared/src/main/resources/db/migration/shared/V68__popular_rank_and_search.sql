-- 회원 활동·둘러보기 서버(인기글 순위 스냅샷·통합 검색 색인·분 단위 DB 배치 주기) — docs/features/member-activity-contract.md
--
-- · batch_db_jobs.cadence 에 MINUTES(N분마다)를 더한다. 간격(분)은 kst_minute 칸을 다시 쓴다(1~59 — 시간대와 무관한 */N 식).
--   기본 cron 환산(batch_default_cron)과 정의 도우미(batch_define_every_minutes)는 R__batch_00_helpers.sql 에 있다.
-- · post_rank_snapshots: 인기글 기간 탭(REALTIME 실시간 · YESTERDAY 어제 · WEEK 이번 주)의 순위 스냅샷. DB 배치 popular-rank(R__batch_24_popular_rank.sql)가
--   기간마다 한 트랜잭션에서 통째로 지우고 다시 넣는다 — 읽는 쪽(GET /api/home/popular)은 늘 온전한 스냅샷을 본다. 글 표는 파티션이라 외래 키를 두지 않는다
--   (지운·숨긴 글은 읽을 때 다시 거른다). fill: 그 기간 글이 모자라 채운 줄의 출처(YESTERDAY 어제 글 · LAST_WEEK 지난주 글), 자기 기간 글이면 NULL.
-- · 통합 검색(GET /api/search) 색인 — pg_trgm(V1 이 확장을 만들었다) GIN. 질의는 lower(칸) LIKE '%단어%' 로 같은 식을 쓴다(식이 글자 그대로 같아야 색인을 탄다 —
--   server/api 의 PostSearchQueries 와 함께 고친다). 3글자 미만 단어는 트라이그램이 없어 색인을 쓰지 못한다(그래서 검색 한 번은 3초 제한이 있다).
--     · 제목, 본문 평문(coalesce(content_text, content) — HTML 글은 태그를 뗀 평문 칸, 게시판 안 검색과 같은 식), 비회원 이름(비회원 글만), 회원 닉네임(profiles).
--   잠금: 파티션 부모 표의 CREATE INDEX 는 CONCURRENTLY 를 쓸 수 없고 만드는 동안 글 쓰기를 막는다. 지금은 글이 적어 바로 끝난다(공개 게시판이 열린 지 며칠).
--   글이 많이 쌓인 설치에 비슷한 색인을 더할 때는 V45 머리말의 방식(ON ONLY → 조각마다 CONCURRENTLY → ATTACH)을 쓴다.
--   비용: 글을 쓰고 고칠 때 GIN 갱신이 붙는다(fastupdate 로 미뤄 모은다). 긴 본문일수록 트라이그램이 많다.

-- ── DB 배치 주기 종류 ────────────────────────────────────────────────────────
-- DAILY 매일 kst_hour:kst_minute(한국시간) · HOURLY 매시 kst_minute 분 · MINUTES kst_minute 분마다(kst_hour 는 쓰지 않는다 — 0)
ALTER TABLE batch_db_jobs DROP CONSTRAINT ck_batch_db_jobs_cadence;
ALTER TABLE batch_db_jobs ADD CONSTRAINT ck_batch_db_jobs_cadence CHECK (cadence IN ('DAILY', 'HOURLY', 'MINUTES'));
ALTER TABLE batch_db_jobs ADD CONSTRAINT ck_batch_db_jobs_minutes CHECK (cadence <> 'MINUTES' OR kst_minute BETWEEN 1 AND 59);

-- ── 인기글 순위 스냅샷 ───────────────────────────────────────────────────────
CREATE TABLE post_rank_snapshots (
    period      varchar(10)      NOT NULL,
    rank        smallint         NOT NULL,
    post_id     bigint           NOT NULL,
    score       double precision NOT NULL,
    fill        varchar(10),
    computed_at timestamptz      NOT NULL,
    PRIMARY KEY (period, rank),
    CONSTRAINT ck_post_rank_snapshots_period CHECK (period IN ('REALTIME', 'YESTERDAY', 'WEEK')),
    CONSTRAINT ck_post_rank_snapshots_rank CHECK (rank BETWEEN 1 AND 50),
    CONSTRAINT ck_post_rank_snapshots_fill CHECK (fill IS NULL OR fill IN ('YESTERDAY', 'LAST_WEEK'))
);

-- ── 통합 검색 색인(pg_trgm) ──────────────────────────────────────────────────
CREATE INDEX ix_posts_title_trgm ON posts USING gin (lower(title) gin_trgm_ops);
CREATE INDEX ix_posts_body_trgm ON posts USING gin (lower(coalesce(content_text, content)) gin_trgm_ops);
CREATE INDEX ix_posts_guest_name_trgm ON posts USING gin (lower(guest_name) gin_trgm_ops) WHERE author_id IS NULL;
CREATE INDEX ix_profiles_nickname_trgm ON profiles USING gin (lower(nickname) gin_trgm_ops);
