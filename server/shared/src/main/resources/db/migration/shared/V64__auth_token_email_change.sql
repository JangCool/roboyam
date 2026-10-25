-- 회원 계정 흐름(docs/features/member-account-contract.md) — 이메일 변경 링크를 auth_tokens 에 둔다.
-- 용도 CHANGE_EMAIL 을 더하고, 그 링크가 바꿀 새 주소(소문자로 정규화한 값)를 new_email 에 둔다. 새 주소는 링크를 누를 때 users.email 로 옮겨지며,
-- 그 전까지 지금 이메일이 그대로다. 토큰 원문은 여전히 저장하지 않는다(해시만).
-- 정리 배치(R__batch_10_auth_token_cleanup)는 용도와 상관없이 만료·사용된 행을 지우므로 고치지 않는다.

ALTER TABLE auth_tokens DROP CONSTRAINT ck_auth_tokens_purpose;
ALTER TABLE auth_tokens ADD CONSTRAINT ck_auth_tokens_purpose CHECK (purpose IN ('VERIFY_EMAIL', 'RESET_PASSWORD', 'CHANGE_EMAIL'));

ALTER TABLE auth_tokens ADD COLUMN new_email varchar(254);
-- 이메일 변경 링크에만 새 주소가 있다(다른 용도는 비어 있다)
ALTER TABLE auth_tokens ADD CONSTRAINT ck_auth_tokens_new_email CHECK ((purpose = 'CHANGE_EMAIL') = (new_email IS NOT NULL));
