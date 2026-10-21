-- 운영자 개인 설정 — docs/features/roles-contract.md 4단계 (내 정보 > 개인 설정)
--
-- 운영자마다 최대 한 행. 행은 처음 저장할 때 만든다(없으면 아래 처음 값으로 본다).
--   display_use          : 「내 표시 형식을 쓴다」. 꺼도 값(display_*)은 남는다. 처음 값 false → 사이트 표시 기본값(site_settings.display_*)을 따른다.
--   display_*            : 내가 정한 표시 형식(언어·시간대·시간 형식·날짜 형식). 한 번도 저장하지 않았으면 넷 다 NULL(넷이 함께 있거나 함께 없다).
--   email_notification   : 관리자 알림을 이메일로 받을지. 처음 값 true. (알림을 보내는 기능은 아직 없다 — 설정만 저장한다)
-- 허용 값은 site_settings 의 표시 기본값과 같다(공유 프론트 레이어 apps-packages/display-time).
CREATE TABLE operator_preferences (
    operator_id          bigint       PRIMARY KEY REFERENCES operators (id) ON DELETE CASCADE,
    display_use          boolean      NOT NULL DEFAULT false,
    display_language     varchar(2),
    display_zone_id      varchar(64),
    display_time_format  varchar(3),
    display_date_format  varchar(10),
    email_notification   boolean      NOT NULL DEFAULT true,
    updated_at           timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT ck_operator_preferences_display_all_or_none CHECK (
        (display_language IS NULL AND display_zone_id IS NULL AND display_time_format IS NULL AND display_date_format IS NULL)
        OR (display_language IS NOT NULL AND display_zone_id IS NOT NULL AND display_time_format IS NOT NULL AND display_date_format IS NOT NULL)),
    CONSTRAINT ck_operator_preferences_language CHECK (display_language IN ('ko', 'en')),
    CONSTRAINT ck_operator_preferences_time_format CHECK (display_time_format IN ('H24', 'H12')),
    CONSTRAINT ck_operator_preferences_date_format CHECK (display_date_format IN ('YMD_DASH', 'YMD_DOT', 'DMY_SLASH', 'MDY_SLASH')),
    CONSTRAINT ck_operator_preferences_use_has_values CHECK (NOT display_use OR display_language IS NOT NULL)
);
