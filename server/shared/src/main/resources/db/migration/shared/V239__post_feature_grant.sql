-- 우수 글 선정 권한(point-board-tier-contract 「권한」 — 운영자만): 글 관리(`post.manage`)를 가진 운영자 역할에 `post.feature` 를 함께 준다.
-- 선례 V104(`pointshop.use` 가진 역할에 `commerce.purchase`). 권한 행은 admin 의 PermissionCatalogSynchronizer 가 맞추지만 이 파일이 먼저 돌 수 있어 여기서도 넣는다(있으면 그대로).
INSERT INTO permissions (code, axis, type_id, category, feature_ready, synced_at)
SELECT 'post.feature', p.axis, p.type_id, p.category, true, now()
  FROM permissions p
 WHERE p.code = 'post.manage'
ON CONFLICT (code) DO NOTHING;

INSERT INTO role_permissions (id, role_id, permission_code, granted_at)
SELECT ((greatest(0, (extract(epoch FROM clock_timestamp()) * 1000)::bigint - 1767225600000)) << 22) | (1023::bigint << 12) | (row_number() OVER (ORDER BY rp.role_id))::bigint,
       rp.role_id, 'post.feature', now()
  FROM role_permissions rp
  JOIN roles r ON r.role_id = rp.role_id AND r.axis = 'OPERATOR'
 WHERE rp.permission_code = 'post.manage'
   AND NOT EXISTS (SELECT 1 FROM role_permissions x WHERE x.role_id = rp.role_id AND x.permission_code = 'post.feature');
