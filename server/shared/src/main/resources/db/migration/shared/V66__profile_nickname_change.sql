-- 닉네임 변경 규칙·이력 — docs/features/member-mypage-contract.md 「결정」 닉네임 변경
--
-- 가입 때 정한 닉네임은 가입 뒤 1번만 바꿀 수 있다(횟수 1은 서버 상수, 날짜 대기 없음). 가입 때 닉네임은 세지 않는다(0 에서 시작).
--   profiles.nickname_change_count  지금까지 바꾼 횟수 — PUT /api/me/profile 이 프로필 행을 쓰기 잠금으로 읽은 뒤 센다
--   profiles.nickname_changed_at    마지막으로 바꾼 시각(V2 에 이미 있는 칸 — 이제 채운다)
--   profiles.bio                    한 줄 소개(V2 에 이미 있는 칸, 0~100자, 빈 값은 NULL)
-- 이전 닉네임은 어떤 공개 응답에도 싣지 않는다 — 이력은 관리자 회원 상세(member.pii)만 본다.
ALTER TABLE profiles
    ADD COLUMN nickname_change_count smallint NOT NULL DEFAULT 0,
    ADD CONSTRAINT ck_profiles_nickname_change_count CHECK (nickname_change_count >= 0);

-- ── 닉네임 변경 이력(비공개) ─────────────────────────────────────────────────
-- id: 앱이 발급하는 Snowflake(시각순). 회원 행이 지워지면 함께 지운다.
CREATE TABLE profile_nickname_history (
    id           bigint       PRIMARY KEY,
    user_id      bigint       NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    old_nickname varchar(12)  NOT NULL,
    new_nickname varchar(12)  NOT NULL,
    changed_at   timestamptz  NOT NULL
);
-- 회원별 최근 이력(관리자 회원 상세 — 최신이 위)
CREATE INDEX ix_profile_nickname_history_user ON profile_nickname_history (user_id, id DESC);
