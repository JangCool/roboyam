-- 회원 프로필 공개 범위(공개/비공개) — docs/features/profile-privacy-contract.md(화면 확정 2026-11-07, 「정책」·「API」).
-- ⚠ 가번호 V160 — develop 최신 V159. 합치기 직전에 shared 스트림의 마지막 번호를 다시 보고 정한다.
--
-- PUBLIC  : 지금처럼 다 보인다(기본값 — 이미 있는 회원도 PUBLIC 으로 시작한다).
-- PRIVATE : 본인이 아닌 사람(비회원·회원·게시판 매니저 포함)에게 한 줄 소개·가입일·활동 수치·작성글·댓글·소모임 목록·글 보기의 작성자 소개를 숨긴다.
--           닉네임·아바타·등급·뱃지와 게시판의 글·댓글 자체는 그대로 보인다. 운영자는 관리자 앱에서 늘 본다.
ALTER TABLE profiles
    ADD COLUMN profile_visibility varchar(16) NOT NULL DEFAULT 'PUBLIC',
    ADD CONSTRAINT ck_profiles_profile_visibility CHECK (profile_visibility IN ('PUBLIC', 'PRIVATE'));
