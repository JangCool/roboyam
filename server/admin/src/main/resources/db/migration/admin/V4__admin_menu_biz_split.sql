-- 관리자 메뉴 「신청 심사」(화면 키 bizApplications, /biz-applications)를 없애고 업무별 화면으로 나눴다 — docs/plans/biz-ad-hotdeal/04-ia-review.md S7
-- (사업자: businessReviews · businesses / 광고: adReviews · ads / 핫딜: hotdealReviews — 원천은 AdminScreenRegistry).
--
-- 저장한 구성(admin_menu_nodes)에 옛 화면 키가 남아 있으면 그 구성을 다시 저장할 때 400 adminMenu.screen.unknown 이 되므로, 옛 화면이 맡던 일의 첫 자리인
-- 「광고 심사」(adReviews)로 바꾼다(이름·아이콘·숨김·차례는 그대로). adReviews 는 이번에 생긴 키라 저장한 구성에 이미 있을 수 없다(두 모드 통틀어 한 번 — 중복 없음).
-- 다른 새 화면(인증 심사·광고 일정표·핫딜 심사)은 지금까지의 새 화면처럼 저장한 구성에 저절로 넣지 않는다(메뉴 설정에서 넣는다 — 지금 관리자 셸은 저장 구성을 읽지 않는다).
-- 저장한 적 없는 설치(행 없음)에서는 아무 일도 하지 않는다.
UPDATE admin_menu_nodes
   SET target_key = 'adReviews'
 WHERE target_type = 'ADMIN_SCREEN'
   AND target_key = 'bizApplications';
