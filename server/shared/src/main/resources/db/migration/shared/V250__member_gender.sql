-- 회원 성별 (docs/features/site-logo-library-contract.md 「정책」)
--
-- 선택 항목 — MALE · FEMALE · NONE(밝히지 않음, 기본). 가입과 내 정보 수정에서 받는다. 기존 회원은 NONE.
-- 다른 회원·공개 프로필에 내보내지 않는다 — 사이트 로고 고르기(보이기 규칙의 성별 규칙)에만 쓴다.
-- 탈퇴 파기(WithdrawalEraseStore)가 닉네임·소개와 함께 NONE 으로 되돌린다.
ALTER TABLE profiles ADD COLUMN gender varchar(10) NOT NULL DEFAULT 'NONE';
ALTER TABLE profiles ADD CONSTRAINT ck_profiles_gender CHECK (gender IN ('MALE', 'FEMALE', 'NONE'));
