import type { MenuOverflow, MenuPosition, MenuWidth, TopItemsPosition } from '~/utils/util-menu-position';

/**
 * 셸 슬롯(`shell`)의 props 계약 — 모든 테마의 셸 컴포넌트가 같은 계약을 지킨다(그래야 `<component :is>` 로 갈아끼워도 레이아웃이 그대로다). → `.claude/guides/frontend/themes.md`
 *
 * - emits: `open-menu` — 모바일 ☰ 를 눌렀다(드로어는 레이아웃이 연다).
 * - slots: 기본 = 본문(페이지) · `after-header` = 헤더(와 상단 메뉴) 아래에 놓을 안내 배너·모바일 상단 탭(테마와 무관해 레이아웃이 채운다).
 * - 모바일의 하단 탭·글쓰기 버튼·드로어는 테마와 무관해 레이아웃이 그린다. 셸은 데스크톱 구성(헤더·메뉴·본문 폭·푸터)을 정한다.
 * - 메뉴 **배치**는 셸이 정하지 않는다 — 레이아웃이 `useMenuPosition`(메뉴 위치: 회원 > 운영자 > 테마 기본, 최상위 항목 위치: 운영자 > 테마 기본)으로 정해 `menuPosition`·`topItems` 로 넘기고,
 *   셸은 다섯 위치(상단·사이드 왼쪽·사이드 오른쪽·상단+왼쪽·상단+오른쪽)와 두 최상위 항목 위치를 모두 자기 모양으로 그린다.
 *   어느 자리에 어떤 그룹을 그릴지는 메뉴 부품이 `useMenuItems(scope)`(`UtilMenu.split`)로 고른다 — 셸은 자리(로고 옆·상단 막대·사이드)만 놓는다.
 * - 상단 메뉴 **폭**(`menuWidth`: 운영자 > 테마 기본)도 레이아웃이 정해 넘긴다. `FULL` 이면 상단 메뉴 상자(바탕·테두리)가 창 좌우 끝까지 닿고 메뉴 항목의 시작은 본문 열 왼쪽 끝에 그대로 맞춘다.
 *   상단 메뉴가 없는 위치(`LEFT`·`RIGHT`)에서는 쓰지 않는다. 바탕이 이미 창 폭인 상단 메뉴(기본 테마 `AppTopMenu`·네이버식 탭 바)는 두 값의 모양이 같다.
 * - 가로 메뉴 줄이 **넘칠 때의 표시**(`menuOverflow`: 운영자 > 테마 기본)도 레이아웃이 정해 넘긴다. 셸은 자기 가로 메뉴 줄(상단 막대·메뉴 카드·탭 바·로고 옆 링크 줄)에 그대로 전하고,
 *   줄은 공통 `AppScrollRow` 가 그린다(`SCROLLBAR` 줄 아래 얇은 스크롤 표시 · `ARROWS` 양끝 화살표 단추). 예약 자리가 줄 높이 밖에 있는 막대(기본 테마 `AppTopMenu`·네이버식 탭 바)는
 *   `SCROLLBAR` 일 때 처음부터 그만큼 높다 — 붙는 사이드 메뉴(`AppSideMenu` 의 `barOverflow`)가 그 높이를 더한다.
 */
export interface ThemeShellProps {
    /** 안쪽 화면(글 상세·글쓰기)인가 — 모바일에서 헤더를 숨긴다 */
    inner?: boolean;
    /** 데스크톱 메뉴를 그리는가 — 글쓰기처럼 넓은 화면이 필요한 곳은 페이지가 끈다. 사이드 메뉴만 숨기고 상단 메뉴는 그대로 둔다 */
    sideMenu?: boolean;
    /** 데스크톱 메뉴 위치 — `TOP` 상단 · `LEFT`/`RIGHT` 사이드 · `TOP_LEFT`/`TOP_RIGHT` 상단(운영자가 고른 띠 그룹) + 사이드(나머지 그룹) */
    menuPosition: MenuPosition;
    /** 최상위 항목(홈·인기글 …) 위치 — `LOGO` 로고 옆 작은 링크 줄(`AppLogoLinks`) · `MENU` 메뉴 안 */
    topItems: TopItemsPosition;
    /** 상단 메뉴 폭 — `CONTENT` 본문 폭 · `FULL` 화면 좌우 끝까지(항목 시작은 본문 열 왼쪽 끝 그대로) */
    menuWidth: MenuWidth;
    /** 가로 메뉴 줄이 넘칠 때의 표시 — `SCROLLBAR` 줄 아래 얇은 스크롤 표시 · `ARROWS` 줄 양끝 화살표 단추(모바일은 손가락 밀기만) */
    menuOverflow: MenuOverflow;
}
