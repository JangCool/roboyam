-- 게시판 옵션 「운영자가 숨긴 댓글임을 밝히기」 — docs/features/popular-highlight-contract.md 「결정」·「서버 구현 결과」, board-contract.md 정책 칸
--
-- · boards.staff_hidden_notice: 게시판별 정책 칸(NULL = 전체 기본값 따름).
-- · board_defaults.staff_hidden_notice: 전체 기본값(false). 기존 게시판은 NULL 이라 곧바로 기본값(끔)을 따른다 — 지금과 같은 공개 표시.
--   켜면 공개 댓글 응답이 운영자가 숨긴(hidden_reason = 'STAFF') 댓글에 hiddenBy = STAFF 를 싣는다. 끄면 까닭을 내리지 않아 신고 숨김과 구별되지 않는다.
ALTER TABLE boards ADD COLUMN staff_hidden_notice boolean;

ALTER TABLE board_defaults ADD COLUMN staff_hidden_notice boolean NOT NULL DEFAULT false;
