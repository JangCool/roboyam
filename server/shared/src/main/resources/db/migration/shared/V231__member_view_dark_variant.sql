-- 회원 보기 설정 — 다크 종류(사용자 확정 2026-11-16, 시안 A 회청색).
--
-- dark_variant : 어두운 화면(DARK, 또는 AUTO 로 어두워질 때)의 밝기. DEEP = 진하게(지금 모양, 기본) · SOFT = 부드럽게(회청색).
--                행이 없는 회원의 기본값은 서버 코드(MemberViewSettingEntity.DEFAULT_DARK_VARIANT = 'DEEP')가 정한다.
ALTER TABLE member_view_settings
    ADD COLUMN dark_variant varchar(8) NOT NULL DEFAULT 'DEEP',
    ADD CONSTRAINT ck_member_view_settings_dark_variant CHECK (dark_variant IN ('DEEP', 'SOFT'));
