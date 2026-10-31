-- 내장 회원 역할 ROLE_MEMBER_BUSINESS 의 이름·설명 바꾸기(사용자 결정 2026-10-31) — 「사업자」는 일반 회원 + 사업자 인증(뱃지, business_profiles APPROVED)이고,
-- 이 역할은 「홍보 전용 계정」(따로 가입한 홍보용 계정 — 일반 활동 제한, 1차에는 가입 길이 없어 붙는 회원 없음)이다. 역할 ID·권한 묶음은 그대로다.
-- BuiltinRoleSeeder 는 이미 있는 역할을 건드리지 않으므로 이미 설치된 곳의 줄은 여기서 고친다(새 설치는 시더가 새 이름으로 넣는다 — 같은 글자).
-- roles 에는 운영자가 고쳤는지 알 칸이 없다 — 칸마다 「원래 시드 글자 그대로」인 것만 바꾼다(운영자가 바꾼 이름·설명은 그대로 둔다). 다시 돌아도 바뀌는 것이 없다.

UPDATE roles
   SET role_name  = '홍보 전용 계정',
       updated_at = now()
 WHERE role_id = 'ROLE_MEMBER_BUSINESS'
   AND role_name = '사업자 회원';

UPDATE roles
   SET description = '따로 가입한 홍보용 계정 — 일반 활동이 제한됩니다(1차에는 가입 길이 없어 붙는 회원 없음)',
       updated_at  = now()
 WHERE role_id = 'ROLE_MEMBER_BUSINESS'
   AND description = '사업자 승인을 받은 회원입니다. 읽기·프로필·허용 게시판 위주로 제한됩니다.';
