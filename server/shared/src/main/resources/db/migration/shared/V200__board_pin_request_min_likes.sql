-- 토론 의견 [고정 요청]의 최소 추천 수(사용자 결정 2026-11-12 「모든 사람이 고정 요청을 보내면 너무 많다 — 추천을 일정 이상 받으면 나오게」) — 사이트 전체 값 하나(게시판별 덮어쓰기 없음).
-- 의견의 추천 수(comments.like_count)가 이 값보다 적으면 공개 화면이 [고정 요청] 단추를 감추고, 서버는 409 PIN_REQUEST_NOT_ENOUGH_LIKES. 0 = 제한 없음(예전과 같다).
-- 이미 대기 중인 요청은 추천이 줄어도 그대로 둔다. 관리자 GET·PUT /api/admin/boards/defaults 의 defaults.pinRequestMinLikes,
-- 공개 글 상세 debate.pinRequestMinLikes · 댓글 목록 debate.pinRequestMinLikes. 범위의 원천은 앱 BoardFields(PIN_REQUEST_MIN_LIKES_MAX)다.

ALTER TABLE board_defaults ADD COLUMN pin_request_min_likes integer NOT NULL DEFAULT 10;
ALTER TABLE board_defaults ADD CONSTRAINT ck_board_defaults_pin_request_min_likes CHECK (pin_request_min_likes BETWEEN 0 AND 1000);
