-- 글·댓글 작성자별 집계 색인 — 관리자 회원 상세의 「활동 요약」(글 수·댓글 수·최근 7일). docs/features/roles-contract.md 「회원 상세 활동·등급 이력(서버) 구현 결과」
--
-- 집계 기준(색인의 WHERE 와 조회의 조건이 글자 그대로 같아야 부분 색인을 탄다 — MemberActivityRepository 와 함께 고친다):
--   · 글   : 회원 글(author_id 있음) · 공개(status = 'PUBLISHED', 임시저장 제외) · 삭제 안 됨(deleted_at IS NULL) · 숨김 아님(hidden_at IS NULL)
--   · 댓글 : 회원 댓글 · 삭제 안 됨 · 숨김 아님
-- 부분 색인이라 비회원 글·삭제·임시저장·숨김은 색인에 들어가지 않고(색인이 작다), 집계는 색인만 읽는다(index-only scan).
--
-- 색인 모양: (author_id, created_at DESC). 「전체 수」는 그 회원의 색인 항목만 세고, 「최근 N일」은 같은 색인의 created_at 범위만 읽는다 — 어느 쪽도 표 전체를 훑지 않는다.
--   · posts 는 id(Snowflake = 시각) 월 범위 파티션, comments 는 post_id 해시 64 파티션이다. 부모 표에 만든 색인은 모든 조각에 생기고, 새로 만드는 조각(board_ensure_post_partitions)에도 자동으로 붙는다.
--   · 작성자 조건으로는 두 표 모두 파티션 가지치기가 안 되므로 조각마다 색인을 한 번씩 찌른다(글은 달마다 1조각이라 연 12개, 댓글은 64개 — 조각당 몇 번의 페이지 읽기).
--   · 글의 id(Snowflake)로 최근 구간을 좁히지 않는다 — 임시저장했다가 나중에 공개한 글처럼 id 시각과 created_at 이 다를 수 있어 누락 위험이 있다.
--
-- 잠금: 일반 CREATE INDEX 는 만드는 동안 그 표의 쓰기를 막는다. 지금은 글·댓글을 쓰는 기능이 없어 표가 비어 있으므로(V42 는 표만 만들었다) 즉시 끝나 문제없다.
--   글·댓글이 쌓인 뒤에 비슷한 색인을 더해야 한다면 이 방식을 쓰지 않는다 — 부모에 CREATE INDEX ... ON ONLY, 조각마다 CREATE INDEX CONCURRENTLY, ALTER INDEX ... ATTACH PARTITION 순서로
--   (트랜잭션 밖에서) 만든다.
CREATE INDEX ix_posts_author ON posts (author_id, created_at DESC)
    WHERE author_id IS NOT NULL AND status = 'PUBLISHED' AND deleted_at IS NULL AND hidden_at IS NULL;

CREATE INDEX ix_comments_author ON comments (author_id, created_at DESC)
    WHERE author_id IS NOT NULL AND deleted_at IS NULL AND hidden_at IS NULL;
