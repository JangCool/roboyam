-- 회원 표시 설정(공개 사이트) + 사이트 테마 — docs/features/display-contract.md, docs/features/site-settings-contract.md 「테마」
--
-- ① member_display_preferences: 공개 사이트 회원이 고른 표시 형식(언어·시간대·시간 형식·날짜 형식). 회원마다 최대 한 행.
--    행이 있으면 그 값이 효과값(USER), 없으면 사이트 표시 기본값(site_settings.display_*)을 따른다. 「기본값으로 되돌리기」= 행 삭제.
--    네 칸은 늘 함께 있다(저장 때 모두 정해진다). 허용 값은 site_settings·operator_preferences 와 같다(공유 프론트 레이어 apps-packages/display-time).
--    시간대는 CHECK 로 막지 않는다 — 서비스(DisplayValuesPolicy)가 JDK 가 아는 IANA 이름인지 검사한다.
--    회원 행이 지워지면 함께 지워진다. 탈퇴(users 행을 남기는 상태 변경)는 그 흐름이 이 행을 지운다(MemberDisplayPreferenceRepository#deleteById).
-- ② site_settings.theme: 운영자가 고른 공개 사이트 테마 id. 허용 값은 서버 코드(SiteThemes)가 정한다 — 테마가 늘 때마다 마이그레이션이 필요 없게 CHECK 를 두지 않는다.
CREATE TABLE member_display_preferences (
    user_id      bigint       PRIMARY KEY REFERENCES users (id) ON DELETE CASCADE,
    language     varchar(2)   NOT NULL,
    zone_id      varchar(64)  NOT NULL,
    time_format  varchar(3)   NOT NULL,
    date_format  varchar(10)  NOT NULL,
    created_at   timestamptz  NOT NULL DEFAULT now(),
    updated_at   timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT ck_member_display_preferences_language CHECK (language IN ('ko', 'en')),
    CONSTRAINT ck_member_display_preferences_time_format CHECK (time_format IN ('H24', 'H12')),
    CONSTRAINT ck_member_display_preferences_date_format CHECK (date_format IN ('YMD_DASH', 'YMD_DOT', 'DMY_SLASH', 'MDY_SLASH'))
);

ALTER TABLE site_settings ADD COLUMN theme varchar(32) NOT NULL DEFAULT 'default';
