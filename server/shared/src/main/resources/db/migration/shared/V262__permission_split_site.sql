-- 권한 분리(2026-11-23 사용자 확정 — docs/domain/operator-permissions.md 「site.* 나누기」)
--
-- site.read · site.manage 가 맡던 일 가운데 업무가 다른 것을 뗀다. 화면 주소·메뉴는 그대로이고, 기존 역할은 아래 짝대로 새 권한을 함께 받아 보는 화면이 바뀌지 않는다.
--   방문자 통계        site.read → analytics.read, site.manage → analytics.settings   (사이트·연동 유형)
--   팝업               site.read → popup.read,     site.manage → popup.manage         (공지·주요글 유형)
--   화면 구성(레이아웃) site.read → layout.read,    site.manage → layout.settings      (사이트 화면 구성 유형 — 이미 있는 권한. 공개 사이트 메뉴 편집도 함께 열린다)
--   체험단 보기        trial.manage · trial.review → trial.read                        (광고·홍보 유형)
-- 역할은 자기 유형의 권한만 가질 수 있다(RoleService.checkCandidates — 종합 관리 op_general 은 제한 없음). 그래서 유형이 맞는 역할에만 준다:
-- 다른 유형 역할에 넣으면 그 역할을 다음에 저장할 때 RoleTypeMismatch 로 막힌다. 기본으로 만든 운영자 역할은 없다(운영자가 만든 역할만 해당).

-- ── 1) 권한 행 — admin 의 PermissionCatalogSynchronizer 가 맞추지만 이 파일이 먼저 돌 수 있어 여기서도 넣는다(있으면 그대로). 선례 V241 ──────────
INSERT INTO permissions (code, axis, type_id, category, feature_ready, synced_at) VALUES
    ('popup.read', 'OPERATOR', 'notice', 'READ', true, now()),
    ('popup.manage', 'OPERATOR', 'notice', 'MANAGE', true, now()),
    ('trial.read', 'OPERATOR', 'adPromo', 'READ', true, now()),
    ('analytics.read', 'OPERATOR', 'siteIntegration', 'READ', true, now()),
    ('analytics.settings', 'OPERATOR', 'siteIntegration', 'CONFIG', true, now())
ON CONFLICT (code) DO NOTHING;

-- ── 2) 기존 운영자 역할에 함께 주기 — 바탕 권한(base)을 가진 역할 중 새 권한(code)의 유형이 맞는 역할만. 이미 있으면 건너뛴다 ──────────────
INSERT INTO role_permissions (id, role_id, permission_code, granted_at)
SELECT ((greatest(0, (extract(epoch FROM clock_timestamp()) * 1000)::bigint - 1767225600000)) << 22) | (1023::bigint << 12) | (300 + row_number() OVER (ORDER BY g.role_id, g.code))::bigint,
       g.role_id, g.code, now()
  FROM (SELECT DISTINCT rp.role_id, c.code
          FROM role_permissions rp
          JOIN roles r ON r.role_id = rp.role_id AND r.axis = 'OPERATOR'
          JOIN (VALUES ('analytics.read', 'site.read', 'siteIntegration'),
                       ('analytics.settings', 'site.manage', 'siteIntegration'),
                       ('popup.read', 'site.read', 'notice'),
                       ('popup.manage', 'site.manage', 'notice'),
                       ('layout.read', 'site.read', 'siteLayout'),
                       ('layout.settings', 'site.manage', 'siteLayout'),
                       ('trial.read', 'trial.manage', 'adPromo'),
                       ('trial.read', 'trial.review', 'adPromo')) AS c (code, base, type_id) ON c.base = rp.permission_code
         WHERE r.role_type_id IN ('op_general', 'op_' || c.type_id)
           AND NOT EXISTS (SELECT 1 FROM role_permissions x WHERE x.role_id = rp.role_id AND x.permission_code = c.code)) AS g;
