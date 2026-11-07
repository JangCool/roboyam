-- 팝업 「띄울 창」(사용자 결정 2026-11-08, docs/features/site-popup-contract.md) — 같은 창 번호끼리 한 창에서 슬라이드, 번호가 다르면 PC 는 나란히·모바일은 창을 하나씩 차례로.
--   window_no : 1~3(기본 1 — 이미 있는 팝업은 모두 창 1 이라 지금처럼 한 창에서 넘겨 본다). 창 안의 차례는 sort_order 그대로.
-- 순수 expand — 기본값이 있는 칸 추가라 이전 버전 앱은 이 칸을 모르고 그대로 돈다.
ALTER TABLE site_popups ADD COLUMN window_no smallint NOT NULL DEFAULT 1;
ALTER TABLE site_popups ADD CONSTRAINT ck_site_popups_window_no CHECK (window_no BETWEEN 1 AND 3);
