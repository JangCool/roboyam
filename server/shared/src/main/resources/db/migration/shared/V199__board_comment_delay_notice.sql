-- 비회원 댓글 공개 지연 안내 문구 방식(docs/features/board-public-contract.md 결정 2, 정정 2026-11-12) — 사이트 전체 값 하나(게시판별 덮어쓰기 없음).
-- · TIME(기본): 「N시간 후에 볼 수 있어요 · 지금 보려면 로그인」 — 화면이 댓글의 공개 시각 visibleAt(작성 시각 + 게시판 유효값 commentGuestDelayMinutes)으로 남은 시간을 센다.
-- · LOGIN: 「로그인하면 바로 볼 수 있어요」(예전 문구, 시간 숫자를 보이지 않음).
-- 관리자 GET·PUT /api/admin/boards/defaults 의 defaults.commentDelayNotice, 공개 GET /api/site/info 의 boardDisplay.commentDelayNotice.
-- 값 목록의 원천은 앱 BoardFields.COMMENT_DELAY_NOTICES 다(여기 CHECK 와 같다). 행 하나짜리 표라 CHECK 는 바로 건다.

ALTER TABLE board_defaults ADD COLUMN comment_delay_notice varchar(8) NOT NULL DEFAULT 'TIME';
ALTER TABLE board_defaults ADD CONSTRAINT ck_board_defaults_comment_delay_notice CHECK (comment_delay_notice IN ('TIME', 'LOGIN'));
