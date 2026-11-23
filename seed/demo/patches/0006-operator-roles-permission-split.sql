-- 데모 팩 패치 0006 — 권한 분리(shared V262, 2026-11-23) 뒤 데모 운영자 역할에 새 권한을 더한다
-- (docs/domain/operator-permissions.md 「site.* 나누기」 · 패치 0005-operator-roles.sql).
--
-- 팩의 운영자 역할(패치 0005)은 권한을 나누기 전 카탈로그로 굳어 있다. 설치는 마이그레이션을 다 돌린 뒤 팩을 복원하므로 V262 가 역할에 짝 권한을 주는 단계를 지나친다 —
-- 그래서 「사이트 연동」 역할로 방문자 통계(analytics.*)를, 「공지·주요글」 역할로 팝업(popup.*)을, 체험단 역할로 체험단 조회(trial.read)를 못 보게 된다.
-- 규칙은 0005 와 같다: 역할이 이미 가진 권한 분류(READ·MANAGE·CONFIG·SENSITIVE)와 같은 분류의, 같은 유형(op_general 이면 모든 유형) 카탈로그 권한 중 빠진 것을 더한다.
-- 그래서 뒤에 권한이 또 생기면 같은 꼴의 패치를 새 번호로 더하면 된다. 여러 번 돌려도 된다(있는 권한은 그대로).
--
-- 변수: :'demo_now' · :demo_id_base — 새 행 ID 는 demo_id_base + 6001 부터(0005 의 + 4001… 과 겹치지 않게 — 복원은 ID 와 기준 시각을 같은 차이로 옮기므로 같은 오프셋은 부딪친다).

WITH role_cat AS (
    SELECT r.role_id, r.role_type_id, array_agg(DISTINCT p.category) AS cats
      FROM roles r
      JOIN role_permissions rp ON rp.role_id = r.role_id
      JOIN permissions p ON p.code = rp.permission_code
     WHERE r.axis = 'OPERATOR' AND NOT r.builtin AND r.role_id <> 'ROLE_DEMO_SEED'
     GROUP BY r.role_id, r.role_type_id
), want AS (
    SELECT rc.role_id, p.code
      FROM role_cat rc
      JOIN permissions p ON p.axis = 'OPERATOR' AND p.category = ANY (rc.cats)
       AND (rc.role_type_id = 'op_general' OR rc.role_type_id = 'op_' || p.type_id)
     WHERE NOT EXISTS (SELECT 1 FROM role_permissions x WHERE x.role_id = rc.role_id AND x.permission_code = p.code)
)
INSERT INTO role_permissions (id, role_id, permission_code, granted_at)
SELECT :demo_id_base + 6000 + row_number() OVER (ORDER BY w.role_id, w.code), w.role_id, w.code, :'demo_now'::timestamptz
  FROM want w;
