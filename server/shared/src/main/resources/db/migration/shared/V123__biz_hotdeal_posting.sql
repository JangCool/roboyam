-- 사업자 핫딜 게시(2차 — docs/features/biz-hotdeal-contract.md 규칙 1~11). 예약된 HOTDEAL_POST 신청 건이 시작 시각에 핫딜 게시판 글(post_deals source = BUSINESS)이 된다.
--
-- ① biz_deal_posts: 신청 건 하나에 게시 결과 한 줄(PK = 신청 건 — 배치가 다시 돌거나 결제 확정과 겹쳐도 두 번 만들지 않는다, 규칙 1).
--    status POSTED(글 있음 · post_id 유일) · FAILED(글 없음 · fail_reason — 규칙 3, 다시 시도하지 않는다 — 운영자 취소·전액 환불, 규칙 10).
--    hidden_at: 이 흐름(취소·환불·긴급 내림·정지 내림 — onClosed)이 글을 숨긴 시각(= 그 글의 posts.hidden_at). 되돌리기는 글의 숨김이 그대로 이 값일 때만 푼다 —
--    운영자가 따로 숨긴 글(이미 숨겨져 있었거나 그 뒤 다시 숨긴 글)은 풀지 않는다(규칙 4).
-- ② post_deals: 사업자 글의 신청 건 · 상호 스냅샷(공개 deal.business.name · 관리자 business). source = BUSINESS ⇔ 둘 다 있음, 신청 건 하나에 글 하나(유일).
-- ③ V121 이 닫은 세 상품(HOTDEAL_POST · HOTDEAL_TOP · HOTDEAL_CATEGORY)의 받기를 다시 켠다(규칙 9 — V121 은 고치지 않는다).
-- 처리 기록 action(biz_application_events)·회원 알림 kind(덧붙임)·관리자 알림 이벤트는 DB CHECK 가 없어(V112 · 이벤트는 기동 동기화) 고치지 않는다.

CREATE TABLE biz_deal_posts (
    application_id bigint      PRIMARY KEY REFERENCES biz_applications (id),
    post_id        bigint,
    status         varchar(10) NOT NULL,
    fail_reason    varchar(20),
    hidden_at      timestamptz,
    created_at     timestamptz NOT NULL,
    updated_at     timestamptz NOT NULL,
    CONSTRAINT ck_bdp_status CHECK (status IN ('POSTED', 'FAILED')),
    CONSTRAINT ck_bdp_shape CHECK ((status = 'POSTED' AND post_id IS NOT NULL AND fail_reason IS NULL)
        OR (status = 'FAILED' AND post_id IS NULL AND hidden_at IS NULL
            AND fail_reason IN ('BOARD_CLOSED', 'LINK_BLOCKED', 'BUSINESS_SUSPENDED', 'OTHER')))
);
CREATE UNIQUE INDEX ux_bdp_post ON biz_deal_posts (post_id) WHERE post_id IS NOT NULL;

ALTER TABLE post_deals
    ADD COLUMN biz_application_id bigint,
    ADD COLUMN business_name      varchar(60);
ALTER TABLE post_deals ADD CONSTRAINT ck_post_deals_business
    CHECK ((source = 'BUSINESS') = (biz_application_id IS NOT NULL AND business_name IS NOT NULL));
CREATE UNIQUE INDEX ux_post_deals_biz_application ON post_deals (biz_application_id) WHERE biz_application_id IS NOT NULL;

UPDATE biz_price_rules SET accepting = true, updated_at = now() WHERE product IN ('HOTDEAL_POST', 'HOTDEAL_TOP', 'HOTDEAL_CATEGORY');
