-- 팝업 「여러 창 배치」(사용자 결정 2026-11-08, docs/features/site-popup-contract.md) — 사이트 전체 설정 하나.
--   popup_layout : HORIZONTAL(PC 에서 창을 가로로 나란히, 기본) · VERTICAL(위아래로 쌓기). 모바일은 어느 쪽이든 창을 하나씩 차례로 띄운다.
-- 순수 expand — 기본값이 있는 칸 추가라 이전 버전 앱은 이 칸을 모르고 그대로 돈다.
ALTER TABLE site_settings ADD COLUMN popup_layout varchar(12) NOT NULL DEFAULT 'HORIZONTAL';
ALTER TABLE site_settings ADD CONSTRAINT ck_site_settings_popup_layout CHECK (popup_layout IN ('HORIZONTAL', 'VERTICAL'));
