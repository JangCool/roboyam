-- 회원 보기 설정(공개 사이트) — docs/features/member-social-contract.md 「보기 설정」(원본 community docs/api-profile.md GET·PUT /api/me/settings, 칸 셋)
--
-- member_view_settings: 회원이 고른 보기 취향(야간 모드·글자 크기·댓글 기본 정렬). 회원마다 최대 한 행.
--    행이 없으면 기본값(AUTO·100·OLDEST)을 돌려준다 — 저장 전에도 GET 200. 세 칸은 늘 함께 있다(PUT 이 세 칸을 통째로 정한다).
--    서버는 값만 저장하고 적용은 화면 몫이다. 표시 형식(언어·시간대 — member_display_preferences)과는 다른 표다.
--    허용 값은 서비스(ViewSettingsPolicy)와 user-web UtilViewSettings 와 같다. 회원 행이 지워지면 함께 지워진다.
CREATE TABLE member_view_settings (
    user_id        bigint       PRIMARY KEY REFERENCES users (id) ON DELETE CASCADE,
    theme          varchar(5)   NOT NULL,
    text_scale     smallint     NOT NULL,
    comment_order  varchar(6)   NOT NULL,
    created_at     timestamptz  NOT NULL DEFAULT now(),
    updated_at     timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT ck_member_view_settings_theme CHECK (theme IN ('AUTO', 'LIGHT', 'DARK')),
    CONSTRAINT ck_member_view_settings_text_scale CHECK (text_scale IN (90, 100, 110, 125)),
    CONSTRAINT ck_member_view_settings_comment_order CHECK (comment_order IN ('OLDEST', 'LATEST'))
);
