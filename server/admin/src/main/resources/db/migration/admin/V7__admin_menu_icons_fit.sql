-- 관리자 메뉴 아이콘을 메뉴 이름에 어울리게 바꿨다(사용자 2026-11-10 「메뉴 이름과 어울리지 않는 아이콘」 — 원천은 AdminScreenRegistry · admin-web util-admin-menu.ts, 모두 PrimeIcons).
--
-- 관리자 메뉴 구성을 저장한 사이트는 노드마다 아이콘을 저장해 두고(admin_menu_nodes.icon — 메뉴 편집 창이 화면 아이콘을 미리 채운다) 셸은 그 값을 그리므로,
-- 기본값만 고치면 저장한 사이트에는 반영되지 않는다. 그래서 **이전 기본 아이콘을 그대로 둔 노드만** 새 아이콘으로 바꾼다 — 운영자가 바꾼 아이콘은 그대로 둔다.
--   · 화면 항목: target_type ADMIN_SCREEN + target_key · 묶음(SECTION)·부모 항목(FOLDER): label_key(운영자가 이름만 덮어써도 label_key 는 남는다).
-- 저장한 적 없는 설치(행 없음)에서는 아무 일도 하지 않는다(서버가 레지스트리 기본 구성을 준다). 구성 판(version)은 올리지 않는다 — 구조가 아니라 아이콘 값만 바뀐다.
UPDATE admin_menu_nodes n
   SET icon = v.new_icon
  FROM (VALUES
    ('visitTime', 'pi pi-clock', 'pi pi-calendar-clock'),
    ('avatarUploads', 'pi pi-user-plus', 'pi pi-upload'),
    ('boardSanctions', 'pi pi-list-check', 'pi pi-hammer'),
    ('staffActions', 'pi pi-user-edit', 'pi pi-eye'),
    ('highlights', 'pi pi-bookmark', 'pi pi-sparkles'),
    ('popularPosts', 'pi pi-chart-line', 'pi pi-thumbs-up'),
    ('emoticonSettlements', 'pi pi-wallet', 'pi pi-money-bill'),
    ('groupBuyStock', 'pi pi-box', 'pi pi-warehouse'),
    ('groupBuyOrders', 'pi pi-list', 'pi pi-truck'),
    ('paymentOrders', 'pi pi-list', 'pi pi-receipt'),
    ('paymentReconciliation', 'pi pi-check-square', 'pi pi-arrow-right-arrow-left'),
    ('businesses', 'pi pi-id-card', 'pi pi-building'),
    ('adAdvertisers', 'pi pi-id-card', 'pi pi-address-book'),
    ('socialLogin', 'pi pi-key', 'pi pi-sign-in'),
    ('publicData', 'pi pi-database', 'pi pi-building-columns'),
    ('loginSecurity', 'pi pi-shield', 'pi pi-lock'),
    ('terms', 'pi pi-file', 'pi pi-file-check'),
    ('batchLogs', 'pi pi-book', 'pi pi-list'),
    ('audit', 'pi pi-history', 'pi pi-eye')) AS v(target_key, old_icon, new_icon)
 WHERE n.kind = 'SCREEN'
   AND n.target_type = 'ADMIN_SCREEN'
   AND n.target_key = v.target_key
   AND n.icon = v.old_icon;

UPDATE admin_menu_nodes n
   SET icon = v.new_icon
  FROM (VALUES
    ('SECTION', 'menu.content', 'pi pi-star', 'pi pi-folder-open'),
    ('SECTION', 'menu.groupBuy', 'pi pi-truck', 'pi pi-shop'),
    ('SECTION', 'menu.operatorsAndPermissions', 'pi pi-users', 'pi pi-shield'),
    ('FOLDER', 'menu.roles', 'pi pi-id-card', 'pi pi-key')) AS v(kind, label_key, old_icon, new_icon)
 WHERE n.kind = v.kind
   AND n.label_key = v.label_key
   AND n.icon = v.old_icon;
