-- 글 목록 썸네일 — docs/features/list-thumbnail-contract.md (1단계 + 「2단계 — 대표 썸네일 바꾸기」)
--
-- 1) files.thumbnail_key: 글 이미지(POST_IMAGE)·대표 썸네일 그림(POST_THUMBNAIL)을 올릴 때 서버가 함께 만든 목록용 작은 그림의 저장소 키
--    (post-image/{년월}/{원본과 같은 32자}-t.{jpg|png} — 800×450 상자 덮기 · 최대 세로 1200 · 투명 없으면 JPEG 0.80, 있으면 PNG).
--    NULL = 썸네일 없음(만들지 못했거나 예전 파일) → 목록은 원본 주소를 쓴다. 파일을 지우는 곳(올리다 만 파일 정리)이 둘 다 지운다.
--    예전 파일은 관리자 배치 「글 이미지 썸네일 만들기」(post-image-thumbnail-backfill, 기본 꺼짐)가 채운다 — 아래 부분 색인으로 대상만 훑는다.
ALTER TABLE files ADD COLUMN thumbnail_key varchar(200);
CREATE INDEX ix_files_thumbnail_missing ON files (id)
    WHERE thumbnail_key IS NULL AND status = 'IN_USE' AND purpose IN ('POST_IMAGE', 'POST_THUMBNAIL');

-- 2) 파일 용도 POST_THUMBNAIL — 글의 대표 썸네일로 따로 올린 그림(글쓴이 POST /api/images/thumbnails · 운영자 POST /api/admin/files purpose=POST_THUMBNAIL).
--    글 본문 이미지와 용도를 나눠 「첫 이미지」 결정·본문 이미지 동기화(본문에 없는 이미지를 PENDING 으로)가 건드리지 않게 한다. 키 post-thumb/{년월}/{32자}.{확장자}.
-- ⚠ 합칠 때 합집합 확인 — 다른 세션도 이 CHECK 를 다시 만들 수 있다(나중에 적용되는 쪽이 모든 용도를 담아야 한다). 직전 정의는 V178.
ALTER TABLE files DROP CONSTRAINT ck_files_purpose;
ALTER TABLE files ADD CONSTRAINT ck_files_purpose CHECK (purpose IN ('SITE_LOGO', 'POST_IMAGE', 'HIGHLIGHT_IMAGE', 'BIZ_AD_IMAGE', 'GROUP_IMAGE', 'SITE_POPUP_IMAGE', 'GADGET_ICON',
                                                                     'POST_THUMBNAIL'));

-- 3) post_thumbnails: 글의 대표 썸네일 선택 — 행이 없으면 자동(첫 본문 이미지, 없으면 첫 YouTube 영상 — 대부분의 글). 글 표는 파티션 표라 칸을 더하지 않고
--    글 ID 를 키로 하는 별도 표에 둔다(post_debates 와 같은 이유로 외래 키 없음 — 지운 글의 행은 남아도 목록에 나오지 않는다).
--    mode IMAGE(그 글 본문 이미지 하나 — files.id, POST_IMAGE) · UPLOAD(따로 올린 그림 — files.id, POST_THUMBNAIL) · NONE(목록에 그림 없음 — 파일 없음).
--    set_by AUTHOR(글쓴이 — 게시판 정책 author_thumbnail_enabled 일 때만 저장) · OPERATOR(운영자 — 그동안 글쓴이는 바꿀 수 없다, 409 THUMBNAIL_LOCKED).
--    운영자 「자동으로 되돌리기」 = 행 삭제(운영자·글쓴이 설정을 모두 지운다 — 사용자 결정 A안 2026-11-13). set_by_id = 회원 ID(AUTHOR) 또는 운영자 ID(OPERATOR).
--    file_id 의 파일이 그 글에 쓰이는 중(IN_USE + 같은 글)이 아니면 목록은 자동으로 본다(본문에서 뺀 이미지 등) — 파일 행이 지워져도 되게 외래 키를 두지 않는다.
CREATE TABLE post_thumbnails (
    post_id    bigint      PRIMARY KEY,
    mode       varchar(10) NOT NULL,
    file_id    bigint,
    set_by     varchar(10) NOT NULL,
    set_by_id  bigint,
    updated_at timestamptz NOT NULL,
    CONSTRAINT ck_post_thumbnails_mode CHECK (mode IN ('IMAGE', 'UPLOAD', 'NONE')),
    CONSTRAINT ck_post_thumbnails_set_by CHECK (set_by IN ('AUTHOR', 'OPERATOR')),
    CONSTRAINT ck_post_thumbnails_file CHECK ((mode = 'NONE') = (file_id IS NULL))
);

-- 4) 게시판 정책 칸 「글쓴이가 대표 이미지 고르기」(V79 debate_enabled 와 같은 구조 — boards 는 NULL = 기본값 따름, board_defaults 는 전체 기본값, 기본 끔).
--    켠 게시판에서만 글쓴이(회원) 글쓰기·고치기의 thumbnail 칸을 받는다(꺼져 있으면 칸을 무시). 나중에 꺼도 이미 고른 글은 그대로. 운영자 교체는 이 칸과 무관.
ALTER TABLE boards ADD COLUMN author_thumbnail_enabled boolean;
ALTER TABLE board_defaults ADD COLUMN author_thumbnail_enabled boolean NOT NULL DEFAULT false;
