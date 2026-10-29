-- 공구 Q&A · 리뷰(2b-6) — docs/features/commerce-groupbuy-contract.md 「Q&A 규칙」·「리뷰 규칙」(사용자 확정 2026-10-29). V105 는 테마·레이아웃이 먼저 썼다.
--
-- Q&A
-- · group_buy_questions: 공구마다(차수마다 따로). 본문 평문 1~1,000자. 개인정보 모양이 보이면 서버가 비밀글로 바꾼다(forced_secret → secret).
--   상태 OPEN(답변 대기)/ANSWERED. 운영자 숨김(사유 5~200자) · 작성자 지움(deleted_at — 보관 기간 동안 감춘 채 남는다).
--   retain_until: 마지막 답변일(답변이 없으면 작성일) + 3년 — 파기 배치(R__batch_32 ④)가 지난 질문을 지운다(답변·이력은 함께).
-- · group_buy_answers: 질문 하나에 답변 하나(고치면 이전 내용은 group_buy_answer_revisions).
-- 리뷰
-- · group_buy_reviews: 주문 줄(group_buy_participation_items)마다 하나 — 지워도 줄은 남아(deleted_at) 다시 쓰지 못한다. 상품(상품 마스터)으로 모아 보인다(다른 차수 포함).
--   별점 1~5 · 글 10~1,000자 · 산 단품 이름 사본. 숨김은 사유 코드로만(낮은 별점은 사유가 아니다 — 표시광고법·공정위 소비자 리뷰 지침).
-- · group_buy_review_photos: 5장까지(먼저 올리고 붙인다 — 붙지 않은 사진은 정리 대상, 반품 요청 사진과 같은 방식).
-- · group_buy_review_revisions: 작성자 수정 이력(고치기 전 별점·글·사진 수). group_buy_review_replies: 판매자 공식 답글 하나.
-- 신고
-- · group_buy_ugc_reports: 질문·리뷰 신고(사유는 글·댓글 체계). 기존 reports 는 게시판(board_id) 이 꼭 있어야 해서 따로 둔다(세션 정리 결정 — 계약 「2b-6 결과」).
--   처리는 운영자 숨김과 같은 길(숨기면 열린 신고가 RESOLVED).

-- ── Q&A ─────────────────────────────────────────────────────────────────
CREATE TABLE group_buy_questions (
    id              bigint         PRIMARY KEY,
    group_buy_id    bigint         NOT NULL REFERENCES group_buys (id),
    user_id         bigint         NOT NULL,
    body            varchar(1000)  NOT NULL,
    secret          boolean        NOT NULL DEFAULT false,
    forced_secret   boolean        NOT NULL DEFAULT false,
    status          varchar(10)    NOT NULL DEFAULT 'OPEN',
    hidden_at       timestamptz,
    hidden_by       bigint,
    hidden_reason   varchar(200),
    deleted_at      timestamptz,
    updated_at      timestamptz,
    created_at      timestamptz    NOT NULL,
    retain_until    date           NOT NULL,
    CONSTRAINT ck_gbq_body CHECK (char_length(body) BETWEEN 1 AND 1000),
    CONSTRAINT ck_gbq_status CHECK (status IN ('OPEN', 'ANSWERED')),
    CONSTRAINT ck_gbq_forced CHECK (NOT forced_secret OR secret),
    CONSTRAINT ck_gbq_hidden CHECK ((hidden_at IS NULL) = (hidden_reason IS NULL) AND (hidden_reason IS NULL OR char_length(hidden_reason) BETWEEN 5 AND 200))
);
CREATE INDEX ix_gbq_group ON group_buy_questions (group_buy_id, created_at DESC);
CREATE INDEX ix_gbq_user ON group_buy_questions (user_id, created_at DESC);
CREATE INDEX ix_gbq_open ON group_buy_questions (created_at) WHERE status = 'OPEN' AND hidden_at IS NULL AND deleted_at IS NULL;
CREATE INDEX ix_gbq_retain ON group_buy_questions (retain_until);

CREATE TABLE group_buy_answers (
    question_id   bigint         PRIMARY KEY REFERENCES group_buy_questions (id) ON DELETE CASCADE,
    body          varchar(2000)  NOT NULL,
    answered_by   bigint         NOT NULL,
    answered_at   timestamptz    NOT NULL,
    updated_by    bigint,
    updated_at    timestamptz,
    CONSTRAINT ck_gba_body CHECK (char_length(body) BETWEEN 1 AND 2000),
    CONSTRAINT ck_gba_updated CHECK ((updated_at IS NULL) = (updated_by IS NULL))
);

CREATE TABLE group_buy_answer_revisions (
    id            bigint         PRIMARY KEY,
    question_id   bigint         NOT NULL REFERENCES group_buy_questions (id) ON DELETE CASCADE,
    body          varchar(2000)  NOT NULL,
    written_by    bigint         NOT NULL,
    written_at    timestamptz    NOT NULL,
    replaced_by   bigint         NOT NULL,
    replaced_at   timestamptz    NOT NULL
);
CREATE INDEX ix_gbar_question ON group_buy_answer_revisions (question_id, replaced_at DESC);

-- ── 리뷰 ─────────────────────────────────────────────────────────────────
CREATE TABLE group_buy_reviews (
    id                  bigint         PRIMARY KEY,
    product_id          bigint         NOT NULL REFERENCES group_buy_products (id),
    group_buy_id        bigint         NOT NULL REFERENCES group_buys (id),
    participation_id    bigint         NOT NULL REFERENCES group_buy_participations (id),
    item_id             bigint         NOT NULL REFERENCES group_buy_participation_items (id),
    user_id             bigint         NOT NULL,
    sku_name            varchar(200)   NOT NULL,
    rating              smallint       NOT NULL,
    body                varchar(1000)  NOT NULL,
    hidden_reason_code  varchar(20),
    hidden_memo         varchar(200),
    hidden_at           timestamptz,
    hidden_by           bigint,
    deleted_at          timestamptz,
    updated_at          timestamptz,
    created_at          timestamptz    NOT NULL,
    CONSTRAINT ux_gbr_item UNIQUE (item_id),
    CONSTRAINT ck_gbr_rating CHECK (rating BETWEEN 1 AND 5),
    CONSTRAINT ck_gbr_body CHECK (char_length(body) BETWEEN 10 AND 1000),
    CONSTRAINT ck_gbr_hide_reason CHECK (hidden_reason_code IS NULL OR hidden_reason_code IN ('ABUSE', 'PRIVACY', 'AD', 'IRRELEVANT', 'OTHER')),
    CONSTRAINT ck_gbr_hidden CHECK ((hidden_at IS NULL) = (hidden_reason_code IS NULL)
        AND (hidden_reason_code IS DISTINCT FROM 'OTHER' OR char_length(hidden_memo) BETWEEN 5 AND 200))
);
CREATE INDEX ix_gbr_product ON group_buy_reviews (product_id, created_at DESC) WHERE deleted_at IS NULL;
CREATE INDEX ix_gbr_user ON group_buy_reviews (user_id, created_at DESC) WHERE deleted_at IS NULL;

CREATE TABLE group_buy_review_photos (
    id           bigint        PRIMARY KEY,
    review_id    bigint        REFERENCES group_buy_reviews (id) ON DELETE CASCADE,
    user_id      bigint        NOT NULL,
    image_key    varchar(300)  NOT NULL,
    position     smallint      NOT NULL DEFAULT 0,
    created_at   timestamptz   NOT NULL,
    attached_at  timestamptz,
    CONSTRAINT ck_gbrph_attached CHECK ((review_id IS NULL) = (attached_at IS NULL))
);
CREATE INDEX ix_gbrph_review ON group_buy_review_photos (review_id, position);
CREATE INDEX ix_gbrph_loose ON group_buy_review_photos (created_at) WHERE review_id IS NULL;

CREATE TABLE group_buy_review_revisions (
    id            bigint         PRIMARY KEY,
    review_id     bigint         NOT NULL REFERENCES group_buy_reviews (id) ON DELETE CASCADE,
    rating        smallint       NOT NULL,
    body          varchar(1000)  NOT NULL,
    photo_count   smallint       NOT NULL,
    written_at    timestamptz    NOT NULL,
    replaced_at   timestamptz    NOT NULL
);
CREATE INDEX ix_gbrr_review ON group_buy_review_revisions (review_id, replaced_at DESC);

CREATE TABLE group_buy_review_replies (
    review_id     bigint         PRIMARY KEY REFERENCES group_buy_reviews (id) ON DELETE CASCADE,
    body          varchar(2000)  NOT NULL,
    replied_by    bigint         NOT NULL,
    replied_at    timestamptz    NOT NULL,
    updated_by    bigint,
    updated_at    timestamptz,
    CONSTRAINT ck_gbrrp_body CHECK (char_length(body) BETWEEN 1 AND 2000),
    CONSTRAINT ck_gbrrp_updated CHECK ((updated_at IS NULL) = (updated_by IS NULL))
);

-- ── 신고 ─────────────────────────────────────────────────────────────────
CREATE TABLE group_buy_ugc_reports (
    id            bigint        PRIMARY KEY,
    target_type   varchar(20)   NOT NULL,
    target_id     bigint        NOT NULL,
    reporter_id   bigint        NOT NULL,
    reason        varchar(30)   NOT NULL,
    detail        varchar(300),
    status        varchar(10)   NOT NULL DEFAULT 'OPEN',
    created_at    timestamptz   NOT NULL,
    resolved_at   timestamptz,
    resolved_by   bigint,
    CONSTRAINT ux_gbur_once UNIQUE (target_type, target_id, reporter_id),
    CONSTRAINT ck_gbur_type CHECK (target_type IN ('GROUP_BUY_QUESTION', 'GROUP_BUY_REVIEW')),
    CONSTRAINT ck_gbur_reason CHECK (reason IN ('SPAM_AD', 'ABUSE', 'OBSCENE', 'ILLEGAL_FILMING', 'RIGHTS', 'OTHER')),
    CONSTRAINT ck_gbur_status CHECK (status IN ('OPEN', 'RESOLVED')),
    CONSTRAINT ck_gbur_resolved CHECK ((status = 'RESOLVED') = (resolved_at IS NOT NULL))
);
CREATE INDEX ix_gbur_open ON group_buy_ugc_reports (target_type, target_id) WHERE status = 'OPEN';
