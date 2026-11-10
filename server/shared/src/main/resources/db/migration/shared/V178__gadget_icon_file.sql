-- 가젯 제목 아이콘 직접 올리기(docs/features/gadget-page-contract.md 「가젯 제목 아이콘」, 사용자 2026-11-10) — 파일 용도 GADGET_ICON 을 더한다.
-- 관리자 POST /api/admin/home-layouts/icons(권한 site.manage)로 올려 PENDING, 가젯 저장(꾸미기 style.iconUpload.fileId)에 쓰이면 IN_USE.
-- SVG(위험 요소가 있으면 거절 — 로고 SVG 검사와 같다) 또는 PNG·WEBP, 200KB 까지. 저장소 키 gadget-icon/{버전}/icon.{svg|png|webp}.
-- 쓰이지 않고 둔 파일은 「올리다 만 파일 정리」(file-orphan-cleanup)가 지운다. 가젯에서 바꿔 뺀 아이콘 파일(IN_USE)은 지금은 지우지 않는다(여러 테마·가젯이 같은 파일을 가리킬 수 있다).
-- ⚠ 합칠 때 합집합 확인 — 다른 세션도 이 CHECK 를 다시 만들 수 있다(나중에 적용되는 쪽이 모든 용도를 담아야 한다).
ALTER TABLE files DROP CONSTRAINT ck_files_purpose;
ALTER TABLE files ADD CONSTRAINT ck_files_purpose CHECK (purpose IN ('SITE_LOGO', 'POST_IMAGE', 'HIGHLIGHT_IMAGE', 'BIZ_AD_IMAGE', 'GROUP_IMAGE', 'SITE_POPUP_IMAGE', 'GADGET_ICON'));
