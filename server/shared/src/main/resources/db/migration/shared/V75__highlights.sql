-- 하이라이트 — docs/features/popular-highlight-contract.md 「서버 구현 결과」(community docs/api-content.md 1절을 로보얌에 맞춘 것).
-- 운영자가 고른 글(글마다 하나)을 저장한 순서로 보인다. 공개 홈 표시는 아직 없다(홈 + 테마 작업 때) — 지금은 관리 화면과 토론(V76)만 이 표를 읽는다.
--   「노출 중」 = visible 이고 노출 기간 안(show_from ≤ 지금 < show_until, 비어 있으면 상시)이고 글이 지금 볼 수 있다.
--   title·summary: 비면 화면이 글 제목을 쓰고 요약은 그리지 않는다. image_file_id: 운영자가 올린 대표 이미지(files, purpose HIGHLIGHT_IMAGE) — 없으면 null.
--   debate: 「찬성·반대 의견 받기」 — 켜져 있고 노출 중일 때만 그 글에 토론(입장·구분 탭·배지)이 열린다(V76).
-- 글 표는 파티션 표라 post_id 외래 키가 없다(글이 지워지면 관리 화면에 상태 BLOCKED 로 보이고 공개 쪽은 건너뛴다).
CREATE TABLE highlights (
    id            bigint       PRIMARY KEY,
    post_id       bigint       NOT NULL,
    title         varchar(40),
    summary       varchar(100),
    image_file_id bigint       REFERENCES files (id) ON DELETE SET NULL,
    visible       boolean      NOT NULL DEFAULT true,
    show_from     timestamptz,
    show_until    timestamptz,
    debate        boolean      NOT NULL DEFAULT false,
    sort_order    integer      NOT NULL,
    created_by    bigint       REFERENCES operators (id) ON DELETE SET NULL,
    created_at    timestamptz  NOT NULL,
    updated_by    bigint       REFERENCES operators (id) ON DELETE SET NULL,
    updated_at    timestamptz  NOT NULL,
    CONSTRAINT ux_highlights_post UNIQUE (post_id),
    CONSTRAINT ck_highlights_period CHECK (show_from IS NULL OR show_until IS NULL OR show_until > show_from)
);
CREATE INDEX ix_highlights_order ON highlights (sort_order, id);

-- 하이라이트 대표 이미지 용도(FilePurpose.HIGHLIGHT_IMAGE) — 관리자 POST /api/admin/files(purpose HIGHLIGHT_IMAGE, 권한 highlight.manage)로 올리고
-- 하이라이트 만들기·고치기의 imageFileId 로 지정한다. 지정하지 않은 파일은 「올리다 만 파일 정리」(file-orphan-cleanup)가 지운다.
ALTER TABLE files DROP CONSTRAINT ck_files_purpose;
ALTER TABLE files ADD CONSTRAINT ck_files_purpose CHECK (purpose IN ('SITE_LOGO', 'POST_IMAGE', 'HIGHLIGHT_IMAGE'));
