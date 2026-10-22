-- 메뉴 시드 중 공개 사이트(user-web)에 아직 화면이 없는 항목을 숨긴다 — 규약 「없는 기능의 메뉴는 감춘다」(세션 정리, roles-contract.md 「세션 정리 — 서버 수정 결과」)
--
-- V43 시드의 mypage·group-buy 그룹 항목은 모두 앱 화면(BUILTIN)인데, 그 주소(/me·/me/grade·/me/points·/messages·/notifications·/scheduled·/settings/*·
-- /notifications/settings·/group-buys·/me/orders)의 화면이 user-web 에 없다(2026-10-22 기준 user-web 화면: / · /boards/[slug] · /posts/[id] · /posts/[id]/edit · /write · /login).
-- 그대로 두면 공개 GET /api/menu?set=… 가 없는 주소를 내려 준다 → 항목을 숨김(visible = false)으로 바꾼다. 항목은 지우지 않는다 — 화면이 생기면 운영자가 메뉴 관리에서 켠다.
-- 폴더(mypage 의 「활동」·「설정」)는 그대로 둔다 — 보이는 항목이 없는 폴더는 공개 응답에서 빠진다(MenuViewComposer 의 빈 폴더 제거).
-- main 그룹(HOME · 자동 추가 게시판 폴더)은 화면이 있어 그대로다.
-- 아직 보이는 항목만 바꾸고, 바뀐 그룹의 트리 버전을 1 올린다(관리자 저장의 MENU_VERSION_CONFLICT 판정 — 열려 있던 편집 화면이 옛 트리로 덮어쓰지 않게).

WITH hidden AS (
    UPDATE menu_nodes
       SET visible = false
     WHERE set_key IN ('mypage', 'group-buy')
       AND kind = 'ITEM'
       AND target_type = 'BUILTIN'
       AND target_key IN ('MY_HOME', 'MY_GRADE', 'MY_POINTS', 'MESSAGES', 'NOTIFICATIONS', 'MY_SCHEDULED',
                          'SETTINGS_PROFILE', 'SETTINGS_ACCOUNT', 'SETTINGS_VIEW', 'SETTINGS_BLOCKS', 'NOTIFICATION_SETTINGS',
                          'GROUP_BUYS', 'GROUP_BUY_ORDERS')
       AND visible
    RETURNING set_key
)
UPDATE menu_sets
   SET version = version + 1, updated_at = now()
 WHERE set_key IN (SELECT DISTINCT set_key FROM hidden);
