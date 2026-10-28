import UtilMenuPosition, { type MenuStyle } from '~/utils/util-menu-position';
import UtilTheme from '~/utils/util-theme';

/**
 * 지금 쓸 메뉴 모양(사용자 결정 2026-10-28 — 예전에는 테마 선언에 고정, 이제 운영자 설정) — 규칙은 `UtilMenuPosition.resolveStyle` 한 곳(운영자 값 `THEME` 이면 테마 기본값 `UtilTheme.menuStyleDefault`).
 * 여기는 공유 상태(`useTheme` 의 운영자 값·테마)를 잇기만 한다. 셸·메뉴 부품은 테마 선언을 직접 읽지 않고 이 값을 쓴다(레이아웃에 테마 분기 금지).
 *  - `inlineGroups` 상단 메뉴 그룹을 둘째 줄 「이름 + 칸」으로(`menuGroupStyle: INLINE`) — 다음식 셸 `DaumMenuBar`
 *  - `pickSideGroups` 섞인 배치에서 사이드 그룹을 첫 줄 탭으로 올려 누른 그룹만 사이드에(`sideGroupStyle: PICK`) — 다음식 셸·`AppSideMenu`
 *  - `compactBar` 상단 메뉴 카드 한 단계 작게(`menuSize: COMPACT`)
 *  - `mobileMenuRows` 모바일 상단 메뉴를 메뉴 구조 두 줄로(`mobileMenu: MENU`, `AppMobileMenuRows`) — 아니면 운영자가 고른 모바일 탭 한 줄
 *  - `menuDescription` 최상위 항목(인기글·최신글·공지 …)에서 둘째 줄에 메뉴 설명(`useMenuDescription`)
 *  - `stickyMenu` 메뉴 줄 붙이기(데스크톱 다음식 셸 메뉴 카드 · 모바일 상단 탭 — 모바일은 루트 `data-sticky-menu` CSS)
 *  - `plainMobileHeader` 모바일 상단 바 흰 모양(`mobileHeader: PLAIN` — 루트 `data-mobile-header` CSS)
 * 루트 속성은 `plugins/01.site-theme.client.ts` 가 붙인다.
 *
 * @returns `style` 해석된 7칸 + 위 불리언들.
 */
export function useMenuStyle() {
    const { theme, siteMenuStyle } = useTheme();

    const style = computed<MenuStyle>(() => UtilMenuPosition.resolveStyle(siteMenuStyle.value, UtilTheme.menuStyleDefault(theme.value)));
    const inlineGroups = computed(() => style.value.menuGroupStyle === 'INLINE');
    const pickSideGroups = computed(() => style.value.sideGroupStyle === 'PICK');
    const compactBar = computed(() => style.value.menuSize === 'COMPACT');
    const mobileMenuRows = computed(() => style.value.mobileMenu === 'MENU');
    const menuDescription = computed(() => style.value.menuDescription === 'ON');
    const stickyMenu = computed(() => style.value.stickyMenu === 'ON');
    const plainMobileHeader = computed(() => style.value.mobileHeader === 'PLAIN');

    return { style, inlineGroups, pickSideGroups, compactBar, mobileMenuRows, menuDescription, stickyMenu, plainMobileHeader };
}
