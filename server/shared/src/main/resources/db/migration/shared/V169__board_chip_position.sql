-- 글 목록 말머리·상태 칩 위치(docs/features/post-chip-position-contract.md) — ABOVE(제목 위, 기본) · BEFORE(제목 앞) · BELOW(닉네임 아래).
--
-- · board_defaults.list_chip_position: 사이트 기본(관리자 게시판 > 기본 설정 > 일반). 게시판이 따로 고르지 않으면 이 값이고,
--   섞인 목록(최신글·인기글·검색·홈 가젯·팁 등)도 이 값을 쓴다(공개 /api/site/info 의 boardDisplay.listChipPosition).
-- · boards.chip_position: 게시판별 값. NULL = 사이트 기본 따름(유효값 = 게시판 값 ?? 기본값 — 앱 BoardCatalog 가 계산한다, V42 정책 칸과 같은 뜻).
-- 상수 기본값이 있는 칸 더하기는 표를 다시 쓰지 않는다. 두 표 모두 작아(행 하나 · 게시판 수) CHECK 는 바로 건다.

ALTER TABLE board_defaults ADD COLUMN list_chip_position varchar(8) NOT NULL DEFAULT 'ABOVE';
ALTER TABLE board_defaults ADD CONSTRAINT ck_board_defaults_list_chip_position CHECK (list_chip_position IN ('ABOVE', 'BEFORE', 'BELOW'));

ALTER TABLE boards ADD COLUMN chip_position varchar(8);
ALTER TABLE boards ADD CONSTRAINT ck_boards_chip_position CHECK (chip_position IS NULL OR chip_position IN ('ABOVE', 'BEFORE', 'BELOW'));
