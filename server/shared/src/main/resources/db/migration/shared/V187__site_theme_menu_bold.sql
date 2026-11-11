-- 테마 메뉴 설정 「메뉴 글자 기본 굵게」(사용자 2026-11-11). docs/features/menu-contract.md 「메뉴 항목 글자 색·강조」
--
-- menu_bold : 켜면 그 테마의 메뉴 막대 글자가 기본으로 굵다. 끄면 얇고, 메뉴 항목의 「굵게」(menu_nodes.bold, V186)를 고른 것만 굵다.
--             다음·로보얌 테마(같은 막대 DaumMenuBar — 예전부터 굵게 보였다)만 기본으로 켠다. 행이 없는 테마의 처음 값도 서버(SiteThemeMenus.initialOf)가 같은 규칙으로 준다.
ALTER TABLE site_theme_menus
    ADD COLUMN menu_bold boolean NOT NULL DEFAULT false;

UPDATE site_theme_menus SET menu_bold = true WHERE theme IN ('daum', 'roboyam');
