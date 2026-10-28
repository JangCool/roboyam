import type { MenuLayout } from '~/utils/util-menu';
import UtilMenuPosition, { type MenuOverflow, type MenuPosition, type MenuWidth, type TopItemsPosition } from '~/utils/util-menu-position';
import UtilTheme from '~/utils/util-theme';

/**
 * 지금 쓸 데스크톱 메뉴 배치 — 규칙은 `UtilMenuPosition` 한 곳, 여기는 공유 상태를 잇기만 한다.
 *  - 메뉴 위치: **회원 보기 설정 > 운영자 설정 > 테마 기본값**(`TOP`·`LEFT`·`RIGHT`·`TOP_LEFT`·`TOP_RIGHT`)
 *  - 최상위 항목 위치: 운영자 설정 > 테마 기본값(`LOGO`·`MENU`, 회원은 고르지 않는다)
 *  - 상단 메뉴 폭: 운영자 설정 > 테마 기본값(`CONTENT`·`FULL`, 회원은 고르지 않는다) — 상단 메뉴가 없는 위치에서는 셸이 쓰지 않는다
 *  - 메뉴 넘침 표시: 운영자 설정 > 테마 기본값(`SCROLLBAR`·`ARROWS`, 회원은 고르지 않는다) — 셸이 가로 메뉴 줄(`AppScrollRow`)에 전한다
 *  - 메뉴 나누기 입력(`layout`): 최상위 항목 위치 + 상단 막대 범위(`TOP` 이면 모두, 사이드 단독이면 없음, 섞인 배치면 운영자가 고른 그룹) → `UtilMenu.split`
 * 회원 값은 보기 설정(`useViewSettingsValues`, 비로그인·서버 칸 없음이면 이 브라우저 사본), 운영자 값·테마는 `useTheme` 이 준다.
 * 레이아웃(`layouts/default.vue`)이 `position`·`topItems`·`menuWidth`·`menuOverflow` 를 셸에 넘기고, 메뉴 부품은 `useMenuItems(scope)` 가 `layout` 으로 자기 몫을 고른다.
 * 모바일(md 미만)은 이 값과 무관하다 — 셸이 데스크톱에서만 쓰고, 햄버거·모바일 탭은 전체(`all`)를 그린다.
 *
 * @returns `position` 지금 메뉴 위치 · `sitePosition` 회원 값이 「자동」일 때의 위치 · `topItems` 최상위 항목 위치 · `menuWidth` 상단 메뉴 폭 · `menuOverflow` 메뉴 넘침 표시 · `layout` 메뉴 나누기 입력
 */
export function useMenuPosition() {
    const { theme, siteMenu, siteTopItems, siteBarGroupIds, siteMenuWidth, siteMenuOverflow } = useTheme();
    const view = useViewSettingsValues();
    // 관리자 미리보기 안에서는 회원 보기 설정을 쓰지 않는다(운영자가 보는 값이 공개 기본 화면이어야 한다)
    const { active: previewing } = useAdminPreview();

    const sitePosition = computed<MenuPosition>(() => UtilMenuPosition.resolve('AUTO', siteMenu.value, UtilTheme.menuDefault(theme.value)));
    const position = computed<MenuPosition>(() => UtilMenuPosition.resolve(previewing.value ? 'AUTO' : view.value.menuPosition, siteMenu.value, UtilTheme.menuDefault(theme.value)));
    const topItems = computed<TopItemsPosition>(() => UtilMenuPosition.resolveTopItems(siteTopItems.value, UtilTheme.topItemsDefault(theme.value)));
    const menuWidth = computed<MenuWidth>(() => UtilMenuPosition.resolveWidth(siteMenuWidth.value, UtilTheme.menuWidthDefault(theme.value)));
    const menuOverflow = computed<MenuOverflow>(() => UtilMenuPosition.resolveOverflow(siteMenuOverflow.value, UtilTheme.menuOverflowDefault(theme.value)));
    const layout = computed<MenuLayout>(() => ({ topItems: topItems.value, bar: UtilMenuPosition.barScope(position.value, siteBarGroupIds.value) }));

    return { position, sitePosition, topItems, menuWidth, menuOverflow, layout };
}
