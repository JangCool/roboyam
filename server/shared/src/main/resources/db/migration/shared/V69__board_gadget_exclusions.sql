-- 게시판 「가젯 노출 금지」 — docs/features/board-contract.md · docs/features/member-activity-contract.md 「서버 구현 결과」
--
-- · boards.gadget_exclusions: 이 게시판 글을 내지 않을 홈 가젯 이름들(com.community.board.entity.BoardGadget — POPULAR 인기글 · LATEST 최신글).
--   게시판마다의 값이다(board_defaults 에서 물려받는 정책 칸이 아니다 — NULL 없음, 기본 빈 배열). 순서·중복은 앱이 정리해 저장한다(enum 순서, 중복 없음).
-- · 가젯이 더 생기면 새 마이그레이션에서 ck_boards_gadget_exclusions 의 값 목록을 넓힌다.
-- · 기존 핫딜·직접홍보 게시판(kind DEAL·PROMO)은 인기글에서 빠지던 규칙(e0d260e, 코드 고정)을 이 칸으로 옮겨 {POPULAR} 로 채운다.
--   새로 만드는 DEAL·PROMO 게시판도 {POPULAR} 로 시작한다(BoardCreator — 관리자 만들기·설치 프리셋 같은 길).

ALTER TABLE boards ADD COLUMN gadget_exclusions varchar(16)[] NOT NULL DEFAULT '{}';
-- NULL 원소는 <@ 에서 참이 되지 않아 함께 막힌다
ALTER TABLE boards ADD CONSTRAINT ck_boards_gadget_exclusions CHECK (gadget_exclusions <@ ARRAY['POPULAR', 'LATEST']::varchar(16)[]);

UPDATE boards SET gadget_exclusions = ARRAY['POPULAR']::varchar(16)[] WHERE kind IN ('DEAL', 'PROMO');
