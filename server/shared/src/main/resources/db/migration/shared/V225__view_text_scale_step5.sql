-- 보기 설정 글자 크기를 5 단위로(사용자 2026-11-16 — 「10 단위는 중간이 없이 너무 커지는 느낌」). 90·95·100·105·110·115·120·125.
-- 기존 값(90·100·110·125)은 그대로 허용된다. 기본값(PC 100 · 휴대폰 110)은 바꾸지 않는다.
ALTER TABLE member_view_settings
    DROP CONSTRAINT ck_member_view_settings_text_scale,
    DROP CONSTRAINT ck_member_view_settings_text_scale_mobile,
    ADD CONSTRAINT ck_member_view_settings_text_scale CHECK (text_scale IN (90, 95, 100, 105, 110, 115, 120, 125)),
    ADD CONSTRAINT ck_member_view_settings_text_scale_mobile CHECK (text_scale_mobile IN (90, 95, 100, 105, 110, 115, 120, 125));
