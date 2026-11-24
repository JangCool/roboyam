-- 운영자 회원 계정 「새 계정 만들기」(docs/features/operator-member-account-contract.md 「새 계정 만들기(2026-11-24)」) — 관리자가 만든 계정에 보내는
-- 「비밀번호 정하기」 링크를 auth_tokens 에 둔다. 용도 SETUP_PASSWORD: 링크에서 비밀번호를 정하면 그 주소로 링크를 받았으므로 이메일 인증도 끝난다.
-- 화면은 비밀번호 재설정과 같은 주소({공개 주소}/password/reset?token=)로 온다. 토큰 원문은 저장하지 않는다(해시만).
-- 정리 배치(R__batch_10_auth_token_cleanup)는 용도와 상관없이 만료·사용된 행을 지우므로 고치지 않는다.

ALTER TABLE auth_tokens DROP CONSTRAINT ck_auth_tokens_purpose;
ALTER TABLE auth_tokens ADD CONSTRAINT ck_auth_tokens_purpose CHECK (purpose IN ('VERIFY_EMAIL', 'RESET_PASSWORD', 'CHANGE_EMAIL', 'SETUP_PASSWORD'));
