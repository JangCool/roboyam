-- 회원 보기 설정 — 글자 크기를 PC·휴대폰 따로 저장한다(사용자 2026-11-11). docs/features/member-social-contract.md 「보기 설정」
--
-- text_scale        : 기존 칸 그대로 — PC(데스크톱) 글자 크기.
-- text_scale_mobile : 새 칸 — 휴대폰 글자 크기. 이미 저장한 회원도 휴대폰은 110 으로 시작한다(사용자 결정). 행이 없는 회원의 기본값은 서버 코드
--                     (MemberViewSettingEntity.DEFAULT_TEXT_SCALE_MOBILE = 110)가 정한다. 허용 값은 text_scale 과 같다.
ALTER TABLE member_view_settings
    ADD COLUMN text_scale_mobile smallint NOT NULL DEFAULT 110,
    ADD CONSTRAINT ck_member_view_settings_text_scale_mobile CHECK (text_scale_mobile IN (90, 100, 110, 125));
