-- 운영자를 회원(users)에서 분리한다 — docs/domain/operator-permissions.md 확정 규칙 1 ("회원 users 에는 운영자 역할(STAFF·ADMIN)을 두지 않는다").
--
-- 1) 이미 있는 ADMIN·STAFF 회원을 operators 로 옮긴다. ID 를 그대로 쓴다 — site_settings 등의 updated_by 가 가리키는 값이 그대로 유효하다.
--    · 루트(최고 관리자): installation.root_user_id, 없으면 가장 먼저 만든 비밀번호 있는 ADMIN. 나머지는 역할 없는 운영자가 된다(권한 없음 — 역할을 줘야 쓴다).
--    · 이메일이나 비밀번호가 없는 계정(소셜 전용)은 로그인할 수 없으므로 옮기지 않는다.
-- 2) users.role 의 운영자 값을 없앤다: 모두 USER 로 되돌리고 CHECK 를 USER 만 허용하게 조인다. (users 행·프로필은 일반 회원으로 남는다.)
-- 3) 사이트·메일·소셜 설정의 updated_by 와 installation.root_user_id 는 이제 운영자를 가리킨다 — 외래키를 users 에서 operators 로 옮긴다.

-- 1) 옮기기
INSERT INTO operators (id, email, operator_name, password_hash, is_root, approved, locked, expired, enabled, last_login_at, created_at, updated_at)
SELECT u.id,
       u.email,
       left(coalesce(p.nickname, split_part(u.email, '@', 1)), 20),
       u.password_hash,
       coalesce(u.id = (SELECT coalesce(
                          (SELECT i.root_user_id FROM installation i
                            WHERE EXISTS (SELECT 1 FROM users r WHERE r.id = i.root_user_id AND r.email IS NOT NULL AND r.password_hash IS NOT NULL)),
                          (SELECT a.id FROM users a WHERE a.role = 'ADMIN' AND a.email IS NOT NULL AND a.password_hash IS NOT NULL
                            ORDER BY a.created_at, a.id LIMIT 1))), false),
       true,
       false,
       false,
       u.status = 'ACTIVE' AND u.deleted_at IS NULL,
       u.last_login_at,
       u.created_at,
       now()
  FROM users u
  LEFT JOIN profiles p ON p.user_id = u.id
 WHERE (u.role IN ('ADMIN', 'STAFF') OR u.id IN (SELECT root_user_id FROM installation WHERE root_user_id IS NOT NULL))
   AND u.email IS NOT NULL
   AND u.password_hash IS NOT NULL;

-- 루트는 늘 쓸 수 있는 상태여야 한다(ck_operators_root_state) — 옮기며 정지·탈퇴 상태였더라도 바로잡는다
UPDATE operators SET enabled = true WHERE is_root;

-- 2) users.role 정리
UPDATE users SET role = 'USER' WHERE role <> 'USER';
ALTER TABLE users DROP CONSTRAINT ck_users_role;
ALTER TABLE users ADD CONSTRAINT ck_users_role CHECK (role = 'USER');

-- 3) 외래키를 operators 로
ALTER TABLE site_settings            DROP CONSTRAINT site_settings_updated_by_fkey;
ALTER TABLE mail_settings            DROP CONSTRAINT mail_settings_updated_by_fkey;
ALTER TABLE social_provider_settings DROP CONSTRAINT social_provider_settings_updated_by_fkey;
ALTER TABLE installation             DROP CONSTRAINT installation_root_user_id_fkey;

ALTER TABLE installation RENAME COLUMN root_user_id TO root_operator_id;

ALTER TABLE site_settings            ADD CONSTRAINT site_settings_updated_by_fkey            FOREIGN KEY (updated_by) REFERENCES operators (id);
ALTER TABLE mail_settings            ADD CONSTRAINT mail_settings_updated_by_fkey            FOREIGN KEY (updated_by) REFERENCES operators (id);
ALTER TABLE social_provider_settings ADD CONSTRAINT social_provider_settings_updated_by_fkey FOREIGN KEY (updated_by) REFERENCES operators (id);
ALTER TABLE installation             ADD CONSTRAINT installation_root_operator_id_fkey       FOREIGN KEY (root_operator_id) REFERENCES operators (id);
