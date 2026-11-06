-- 소모임 대표 이미지(docs/features/group-contract.md 「대표 이미지」, 2026-11-06)
--
-- · files: 새 용도 GROUP_IMAGE — 회원이 POST /api/groups/images 로 올리면 PENDING, 개설 신청·정보 수정에 붙으면 IN_USE.
--   바꾸거나 지우거나 신청 취소·해산으로 떨어지면 다시 PENDING 이 되어 「올리다 만 파일 정리」(file-orphan-cleanup)가 지운다.
-- · groups.image_file_id: 그 소모임(개설 신청도 같은 행)의 대표 이미지. 파일 행이 지워지면 NULL(이름 첫 글자로 돌아간다).
--   한 파일은 한 행만 쓴다(반려된 신청을 다시 낼 때는 새 행으로 옮긴다) — 고유 색인이 파일 쪽 삭제의 외래 키 확인도 맡는다.
ALTER TABLE files DROP CONSTRAINT ck_files_purpose;
ALTER TABLE files ADD CONSTRAINT ck_files_purpose CHECK (purpose IN ('SITE_LOGO', 'POST_IMAGE', 'HIGHLIGHT_IMAGE', 'BIZ_AD_IMAGE', 'GROUP_IMAGE'));

ALTER TABLE groups ADD COLUMN image_file_id bigint REFERENCES files (id) ON DELETE SET NULL;
CREATE UNIQUE INDEX ux_groups_image_file ON groups (image_file_id) WHERE image_file_id IS NOT NULL;
