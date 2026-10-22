-- 회원 활동·신고 요약 색인 정리 — docs/features/roles-contract.md 「세션 정리 — 서버 수정 결과」
--
-- · V45 의 작성자 부분 색인(ix_posts_author·ix_comments_author: (author_id, created_at DESC) WHERE … hidden_at IS NULL)을 지운다.
--   활동 수 집계(MemberActivityRepository)가 공개 게시판의 상태 모델(임시조치가 끝나면 다시 보임 · 예약 삭제 시각이 지나면 삭제)로 바뀌어 조건이
--   「hidden_at IS NULL」 과 같지 않으므로 더는 그 부분 색인을 탈 수 없고, 작성자 색인은 V46 의 ix_posts_author_recent·ix_comments_author_recent
--   ((author_id, id DESC) WHERE author_id IS NOT NULL)과 겹친다 — 파티션 조각마다 색인이 하나씩 더 붙어 글·댓글 쓰기 비용만 늘린다.
--   V45 파일은 개발 DB 에 이미 적용돼 지우지 않고 이 파일로 색인만 내린다(적용된 마이그레이션은 고치지 않는다).
--   부모 표의 색인을 지우면 모든 조각의 색인이 함께 지워진다. 지우는 동안 posts·comments 를 잠깐 배타 잠금한다(색인 삭제는 즉시 끝난다).
-- · 신고 요약(관리자 회원 상세 reports.receivedCount·actionedCount): 「이 회원이 대상 작성자인 신고 수」와 「그중 수용(ACCEPTED) 수」를 색인만 읽어 센다.
--   비회원 글·댓글의 신고(target_author_id NULL)는 색인에 넣지 않는다. 「이 회원이 한 신고 수」는 V46 의 ix_reports_reporter 를 쓴다.
--   일반 CREATE INDEX 는 만드는 동안 reports 쓰기를 막는다 — 신고 표는 아직 작아 바로 끝난다.

DROP INDEX IF EXISTS ix_posts_author;
DROP INDEX IF EXISTS ix_comments_author;

CREATE INDEX ix_reports_target_author ON reports (target_author_id, status) WHERE target_author_id IS NOT NULL;
