-- 게시판 서식 에디터(HTML 본문)·에디터 이미지 올리기·게시판별 에디터 설정 — docs/features/board-public-contract.md·board-contract.md 「에디터·이미지」
--
-- · boards.post_editor / comment_editor: 게시판별 정책 칸(NULL = 전체 기본값 따름). 글 NONE(평문)·SIMPLE(간단)·ADVANCED(고급), 댓글 NONE·SIMPLE.
--   이미지 올리기 허용 칸(image_upload_allowed)은 V42 에 이미 있다 — 유효값은 글 에디터가 ADVANCED 일 때만 참일 수 있다(앱이 계산한다).
-- · board_defaults: 같은 두 칸의 전체 기본값(글 SIMPLE, 댓글 NONE). 기존 게시판은 NULL 이라 곧바로 기본값(글 간단 에디터)을 따른다.
-- · posts / comments.content_format: 본문 형식 TEXT(평문) | HTML(서버가 허용 목록으로 정화한 HTML). 기존 행은 TEXT.
--   posts.content_text: HTML 글의 보이는 글자(태그를 뗀 평문) — 검색·같은 글 반복 판정이 읽는다. TEXT 글은 NULL(본문 자체가 평문이다).
--   파티션 표에 상수 기본값이 있는 칸을 더하는 것은 PG 11+ 의 「빠른 기본값」(pg_attribute.attmissingval)이라 표를 다시 쓰지 않는다(PG 18 에서 relfilenode
--   가 그대로임을 확인). CHECK 는 NOT VALID 로 걸어 기존 행을 훑지 않는다(기본값 'TEXT' 는 늘 맞고, 새로 쓰는 행은 검사된다).
-- · files: 회원이 올린 글 이미지(용도 POST_IMAGE)를 같은 장부에 둔다. 올린 회원(uploaded_by_user — 운영자 uploaded_by 와 따로)과 쓰는 글(post_id —
--   파티션 표라 외래 키 없음)을 더한다. 올리면 PENDING, 글에 쓰이면 IN_USE + post_id. 글을 고쳐 빠진 이미지는 PENDING 으로 되돌아가고, 지운 글·없어진 글의
--   이미지와 함께 「올리다 만 파일 정리」(file-orphan-cleanup)가 지운다.

-- ── 게시판 정책 칸 ───────────────────────────────────────────────────────────
ALTER TABLE boards ADD COLUMN post_editor varchar(8);
ALTER TABLE boards ADD COLUMN comment_editor varchar(8);
ALTER TABLE boards ADD CONSTRAINT ck_boards_post_editor CHECK (post_editor IS NULL OR post_editor IN ('NONE', 'SIMPLE', 'ADVANCED'));
ALTER TABLE boards ADD CONSTRAINT ck_boards_comment_editor CHECK (comment_editor IS NULL OR comment_editor IN ('NONE', 'SIMPLE'));

ALTER TABLE board_defaults ADD COLUMN post_editor varchar(8) NOT NULL DEFAULT 'SIMPLE';
ALTER TABLE board_defaults ADD COLUMN comment_editor varchar(8) NOT NULL DEFAULT 'NONE';
ALTER TABLE board_defaults ADD CONSTRAINT ck_board_defaults_post_editor CHECK (post_editor IN ('NONE', 'SIMPLE', 'ADVANCED'));
ALTER TABLE board_defaults ADD CONSTRAINT ck_board_defaults_comment_editor CHECK (comment_editor IN ('NONE', 'SIMPLE'));

-- ── 글·댓글 본문 형식 ─────────────────────────────────────────────────────────
ALTER TABLE posts ADD COLUMN content_format varchar(4) NOT NULL DEFAULT 'TEXT';
ALTER TABLE posts ADD COLUMN content_text text;
ALTER TABLE posts ADD CONSTRAINT ck_posts_content_format CHECK (content_format IN ('TEXT', 'HTML')) NOT VALID;

ALTER TABLE comments ADD COLUMN content_format varchar(4) NOT NULL DEFAULT 'TEXT';
ALTER TABLE comments ADD CONSTRAINT ck_comments_content_format CHECK (content_format IN ('TEXT', 'HTML')) NOT VALID;

-- ── 글 이미지 장부 ───────────────────────────────────────────────────────────
ALTER TABLE files ADD COLUMN uploaded_by_user bigint REFERENCES users (id) ON DELETE SET NULL;
ALTER TABLE files ADD COLUMN post_id bigint;
ALTER TABLE files DROP CONSTRAINT ck_files_purpose;
ALTER TABLE files ADD CONSTRAINT ck_files_purpose CHECK (purpose IN ('SITE_LOGO', 'POST_IMAGE'));
-- 글이 쓰는 이미지(고치기·정리 배치가 글 ID 로 찾는다)
CREATE INDEX ix_files_post ON files (post_id) WHERE post_id IS NOT NULL;
-- 회원의 최근 24시간 올리기 수(하루 한도)
CREATE INDEX ix_files_user_created ON files (uploaded_by_user, created_at) WHERE uploaded_by_user IS NOT NULL;
