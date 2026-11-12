-- 코드 이름 바꿈 roboyam → irobit (사용자 결정 2026-11-12 「다 바꿔 아이로빗으로」).
-- 화면 테마 id 'roboyam' → 'irobit', 설치 프리셋 id 'ROBOYAM' → 'IROBIT' — 저장된 값만 옮긴다(코드는 새 id 만 안다).
-- 옛 마이그레이션(V180·V187 등)의 'roboyam' 은 이미 적용된 기록이라 고치지 않는다.

UPDATE site_settings        SET theme = 'irobit' WHERE theme = 'roboyam';
UPDATE site_theme_menus     SET theme = 'irobit' WHERE theme = 'roboyam';
UPDATE home_layouts         SET theme = 'irobit' WHERE theme = 'roboyam';
UPDATE home_gadgets         SET theme = 'irobit' WHERE theme = 'roboyam';
UPDATE gadget_page_layouts  SET theme = 'irobit' WHERE theme = 'roboyam';
UPDATE board_layouts        SET theme = 'irobit' WHERE theme = 'roboyam';

UPDATE installation SET board_preset = 'IROBIT' WHERE board_preset = 'ROBOYAM';
