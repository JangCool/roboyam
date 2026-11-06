-- 말머리 「성인 전용」 — docs/features/adult-content-contract.md (사용자 결정 2026-11-06)
--
-- · board_prefixes.adult_only: 켜면 그 말머리를 고른 글은 서버가 19금(posts.adult = true)으로 저장한다. 고유 말머리만 켤 수 있고
--   게시판 allow_adult 가 켜진 게시판에서만 켤 수 있다(관리자 게시판 저장이 검사한다). 공통 말머리는 늘 false.
-- · 어느 말머리를 켤지는 운영자 값이라 여기서 정하지 않는다(범용 앱 — 관리자 게시판 편집 화면에서 켜면 그 말머리의 기존 글도 19금이 된다).

ALTER TABLE board_prefixes ADD COLUMN adult_only boolean NOT NULL DEFAULT false;
