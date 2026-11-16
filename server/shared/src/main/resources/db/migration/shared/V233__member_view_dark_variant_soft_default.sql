-- 다크 종류 기본값을 「부드럽게」로(사용자 2026-11-16). V231 이 같은 날 DEEP 기본으로 넣어 지금 저장된 DEEP 은 모두 회원이 고른 값이 아니라 기본값이라 SOFT 로 옮긴다.
ALTER TABLE member_view_settings ALTER COLUMN dark_variant SET DEFAULT 'SOFT';
UPDATE member_view_settings SET dark_variant = 'SOFT' WHERE dark_variant = 'DEEP';
