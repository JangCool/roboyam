-- 게시판 추천 글 — docs/features/board-recommend-contract.md 「규칙」.
-- ⚠ 가번호 V273 — develop 최신 V272. 합치기 직전에 shared 스트림의 마지막 번호를 다시 보고 정한다.
--
-- 하이라이트 곳(highlights.place)에 게시판 곳 'board-{boardId}' 가 생긴다(새 칸 없음 — 키 모양은 V161 의 검사 그대로).
-- 여기서는 게시판별 「모자라면 추천 많은 글로 자동 채우기」 스위치만 둔다. 행이 없으면 켬(기본) — 운영자가 바꾼 게시판만 행이 있다.
-- 게시판을 지우면 행도 지운다(ON DELETE CASCADE). 게시판 곳의 하이라이트 행은 남지만 공개 읽기는 없는 게시판을 404 로 읽는다.
CREATE TABLE highlight_board_settings (
    board_id   bigint      NOT NULL PRIMARY KEY REFERENCES boards (id) ON DELETE CASCADE,
    auto_fill  boolean     NOT NULL,
    updated_by bigint      NULL,
    updated_at timestamptz NOT NULL
);

COMMENT ON TABLE highlight_board_settings IS '게시판 추천 글 설정 — 행이 없으면 자동 채우기 켬(board-recommend-contract)';
COMMENT ON COLUMN highlight_board_settings.auto_fill IS '고른 글이 3개보다 적으면 그 게시판의 추천 많은 글로 채우는가';
