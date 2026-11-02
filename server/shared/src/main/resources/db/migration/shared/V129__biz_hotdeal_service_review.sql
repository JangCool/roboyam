-- 사업자 핫딜 미확정 세 가지(사용자 결정 2026-11-02 — biz-hotdeal-contract 결정 10~12).
--  · 게시 실패 사유 ∪ SERVICE_SUSPENDED(신원은 승인인데 서비스 줄 HOTDEAL 이 승인이 아님 — 줄이 다시 승인되면 다음 게시 배치가 다시 올린다). V123 은 고치지 않는다(적용됨).
--  · 사업자 글 종료 요청 「확인 필요」·운영자 유지·운영진 종료/재개 기록은 처리 기록 action·회원 알림 kind·관리자 알림 이벤트·감사 작업 이름이라 DB CHECK 가 없어 고치지 않는다.
ALTER TABLE biz_deal_posts DROP CONSTRAINT ck_bdp_shape;
ALTER TABLE biz_deal_posts ADD CONSTRAINT ck_bdp_shape CHECK ((status = 'POSTED' AND post_id IS NOT NULL AND fail_reason IS NULL)
    OR (status = 'FAILED' AND post_id IS NULL AND hidden_at IS NULL
        AND fail_reason IN ('BOARD_CLOSED', 'LINK_BLOCKED', 'BUSINESS_SUSPENDED', 'SERVICE_SUSPENDED', 'OTHER')));
