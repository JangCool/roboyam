-- 뉴스 게시판 글 규칙을 이미 설치된 사이트에도 켠다(사용자 2026-11-16).
-- 말머리 필수(V226)·태그 필수(V226)·출처 칸 쓰기(V232)는 칸을 만들 때 모든 게시판을 NULL(= 사이트 기본, 끔)로 두었고,
-- 뉴스 게시판을 켬으로 정한 곳은 설치 구조 파일(install-presets/irobit.structure.json)뿐이라 설치 마법사를 다시 돌리지 않은 사이트는 꺼진 채였다.
-- 운영자가 아직 손대지 않은 값(NULL)만 켠다 — 운영자가 일부러 끄거나 켠 값(true/false)은 그대로 둔다. 뉴스 게시판이 없는 사이트는 아무것도 바뀌지 않는다.
UPDATE boards SET prefix_required = true WHERE slug = 'news' AND prefix_required IS NULL;
UPDATE boards SET tag_required    = true WHERE slug = 'news' AND tag_required    IS NULL;
UPDATE boards SET sources_enabled = true WHERE slug = 'news' AND sources_enabled IS NULL;
