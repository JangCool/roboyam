-- 공개 게시판(글·댓글 쓰기·추천·조회수·신고) — docs/features/board-public-contract.md 「서버 구현 결과」
--
-- · posts·comments 에 공개 화면이 쓰는 칸을 더한다(V42 는 표만 만들었고 지금까지 행이 없다 — 칸 추가·색인은 바로 끝난다).
--   hidden_reason: 숨김 까닭 REPORTED(신고 누적) · ILLEGAL(불법촬영물 등 1건 즉시) · RIGHTS(권리침해 임시조치). hidden_until: 임시조치가 끝나는 시각(그 뒤로는 다시 보인다), 그 밖에는 NULL.
--   숨김 판정 = hidden_at IS NOT NULL AND (hidden_until IS NULL OR hidden_until > now()) — 앱(PostVisibility)과 같다.
--   delete_scheduled_at: 댓글 많은 글 보호의 예약 삭제 시각. 지나면 읽을 때 지운 글로 본다(앱이 거른다) — DB 배치 board-post-upkeep(R__batch_19)이 deleted_at 을 채워 확정한다.
--   edited_at: 작성자가 고친 시각(updated_at 은 배치도 바꾸므로 화면의 「수정됨」은 이 칸을 쓴다).
--   comment_count: 보이는 댓글 수(삭제·숨김 제외) — 댓글 쓰기·지우기·숨김이 같은 트랜잭션에서 고친다. like_count·view_count 도 같은 방식(원자적 UPDATE).
--   pinned_at: 고정 공지(고정 기능은 후속 — 지금은 읽기만).
-- · 추천 표 post_likes·comment_likes: 대상 ID 해시 16 조각(community 와 같은 방식). 한 회원이 한 대상에 한 번(PK), 취소 없음.
-- · 조회 기록 post_views: 조회수 중복 방지(보안 설정 view_count_dedup_hours). 보는 사람 열쇠 = 회원 m:<id> · 비회원 g:<IP 의 SHA-256> (IP 원문은 두지 않는다).
--   board-post-upkeep 배치가 168시간(설정 최대값)보다 오래된 줄을 지운다.
-- · 신고 reports: 신고자·대상·사유·설명·당시 내용(스냅샷). 같은 회원은 같은 대상을 한 번만. 처리(반려·수용)는 관리자 신고 처리 기능(후속)이 status 를 바꾼다.
--   counted: 신고 당시 자동 숨김 최소 등급 이상이었는가(셈에 넣는가). 개인정보 보유기간 배치(R__batch_18)의 reportRecord 축이 오래된 신고를 지운다.

-- ── 글 ──────────────────────────────────────────────────────────────────────
ALTER TABLE posts
    ADD COLUMN adult               boolean     NOT NULL DEFAULT false,
    ADD COLUMN has_image           boolean     NOT NULL DEFAULT false,
    ADD COLUMN view_count          integer     NOT NULL DEFAULT 0,
    ADD COLUMN like_count          integer     NOT NULL DEFAULT 0,
    ADD COLUMN comment_count       integer     NOT NULL DEFAULT 0,
    ADD COLUMN pinned_at           timestamptz,
    ADD COLUMN hidden_reason       varchar(16),
    ADD COLUMN hidden_until        timestamptz,
    ADD COLUMN delete_scheduled_at timestamptz,
    ADD COLUMN edited_at           timestamptz,
    ADD CONSTRAINT ck_posts_hidden_reason CHECK (hidden_reason IS NULL OR hidden_reason IN ('REPORTED', 'ILLEGAL', 'RIGHTS'));

-- 작성 속도·하루 한도·같은 글 반복(최근 24시간) — 지운 글도 센다(지우고 다시 쓰기로 한도를 피하지 못하게). id 가 시각 순이라 id 범위로 월 조각을 가지치기한다
CREATE INDEX ix_posts_author_recent ON posts (author_id, id DESC) WHERE author_id IS NOT NULL;
CREATE INDEX ix_posts_guest_ip_recent ON posts (author_ip, id DESC) WHERE author_id IS NULL AND author_ip IS NOT NULL;
-- 고정 공지
CREATE INDEX ix_posts_board_pinned ON posts (board_id, pinned_at DESC) WHERE pinned_at IS NOT NULL AND deleted_at IS NULL;
-- 예약 삭제 확정(배치)
CREATE INDEX ix_posts_delete_scheduled ON posts (delete_scheduled_at) WHERE delete_scheduled_at IS NOT NULL AND deleted_at IS NULL;

-- ── 댓글 ────────────────────────────────────────────────────────────────────
ALTER TABLE comments
    ADD COLUMN like_count    integer     NOT NULL DEFAULT 0,
    ADD COLUMN hidden_reason varchar(16),
    ADD COLUMN hidden_until  timestamptz,
    ADD COLUMN edited_at     timestamptz,
    ADD CONSTRAINT ck_comments_hidden_reason CHECK (hidden_reason IS NULL OR hidden_reason IN ('REPORTED', 'ILLEGAL', 'RIGHTS'));

-- 댓글 ID 만으로 찾기(PUT·삭제·추천·신고 — 주소에 글 ID 가 없다). 조각마다 색인이 있어 64조각을 한 번씩 찌른다
CREATE INDEX ix_comments_id ON comments (id);
CREATE INDEX ix_comments_author_recent ON comments (author_id, id DESC) WHERE author_id IS NOT NULL;
CREATE INDEX ix_comments_guest_ip_recent ON comments (author_ip, id DESC) WHERE author_id IS NULL AND author_ip IS NOT NULL;

-- ── 추천 (대상 ID 해시 16 조각) ─────────────────────────────────────────────
CREATE TABLE post_likes (
    post_id    bigint      NOT NULL,
    user_id    bigint      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    created_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (post_id, user_id)
) PARTITION BY HASH (post_id);

CREATE TABLE comment_likes (
    comment_id bigint      NOT NULL,
    user_id    bigint      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    post_id    bigint      NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (comment_id, user_id)
) PARTITION BY HASH (comment_id);

-- ── 조회 기록 (글 ID 해시 16 조각) ───────────────────────────────────────────
CREATE TABLE post_views (
    post_id    bigint      NOT NULL,
    viewer_key varchar(70) NOT NULL,
    viewed_at  timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (post_id, viewer_key)
) PARTITION BY HASH (post_id);

DO
$$
BEGIN
    FOR i IN 0..15 LOOP
        EXECUTE format('CREATE TABLE post_likes_p%s PARTITION OF post_likes FOR VALUES WITH (MODULUS 16, REMAINDER %s)', i, i);
        EXECUTE format('CREATE TABLE comment_likes_p%s PARTITION OF comment_likes FOR VALUES WITH (MODULUS 16, REMAINDER %s)', i, i);
        EXECUTE format('CREATE TABLE post_views_p%s PARTITION OF post_views FOR VALUES WITH (MODULUS 16, REMAINDER %s)', i, i);
    END LOOP;
END
$$;
-- 글 하나에서 내가 추천한 댓글(댓글 목록의 liked)
CREATE INDEX ix_comment_likes_post_user ON comment_likes (post_id, user_id);
CREATE INDEX ix_post_views_viewed ON post_views (viewed_at);

-- ── 신고 ────────────────────────────────────────────────────────────────────
-- reason: SPAM_AD · ABUSE · OBSCENE · ILLEGAL_FILMING · RIGHTS · OTHER(설명 필수). status: PENDING(검토 대기) · ACCEPTED(수용) · REJECTED(반려).
-- post_id: 대상 글(댓글 신고면 그 댓글의 글). target_author_id: 대상 작성 회원(비회원 글이면 NULL). 파티션 표(posts·comments)를 가리키는 외래 키는 두지 않는다.
-- snapshot_*: 신고 당시의 제목·본문(앞 2,000자)·작성자 표시 이름 — 이후 고치거나 지워도 검토할 수 있게. 보유기간 배치가 행째 지운다.
CREATE TABLE reports (
    id                    bigint       PRIMARY KEY,
    target_type           varchar(8)   NOT NULL,
    target_id             bigint       NOT NULL,
    post_id               bigint       NOT NULL,
    board_id              bigint       NOT NULL,
    reporter_id           bigint       NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    target_author_id      bigint,
    reason                varchar(20)  NOT NULL,
    detail                varchar(300),
    snapshot_title        varchar(200),
    snapshot_content      text,
    snapshot_author_name  varchar(40),
    counted               boolean      NOT NULL,
    status                varchar(10)  NOT NULL DEFAULT 'PENDING',
    resolved_at           timestamptz,
    resolved_by           bigint       REFERENCES operators (id) ON DELETE SET NULL,
    created_at            timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT ck_reports_target_type CHECK (target_type IN ('POST', 'COMMENT')),
    CONSTRAINT ck_reports_reason CHECK (reason IN ('SPAM_AD', 'ABUSE', 'OBSCENE', 'ILLEGAL_FILMING', 'RIGHTS', 'OTHER')),
    CONSTRAINT ck_reports_detail CHECK (reason <> 'OTHER' OR (detail IS NOT NULL AND char_length(btrim(detail)) > 0)),
    CONSTRAINT ck_reports_status CHECK (status IN ('PENDING', 'ACCEPTED', 'REJECTED'))
);
CREATE UNIQUE INDEX ux_reports_target_reporter ON reports (target_type, target_id, reporter_id);
CREATE INDEX ix_reports_reporter ON reports (reporter_id, created_at DESC);
CREATE INDEX ix_reports_reporter_rejected ON reports (reporter_id, resolved_at DESC) WHERE status = 'REJECTED';
CREATE INDEX ix_reports_created ON reports (created_at);
CREATE INDEX ix_reports_pending ON reports (created_at DESC) WHERE status = 'PENDING';
