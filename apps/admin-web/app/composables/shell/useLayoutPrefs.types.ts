/** 화면 설정(보기 설정)의 모양 — `useLayoutPrefs` 가 들고, `utils/util-layout-prefs.ts` 가 저장값을 검사한다. */

export type ThemeMode = 'light' | 'dark';

/** 본문 폭 — `full` 꽉 채움 · `boxed` 가운데에 폭을 제한 */
export type ContentWidth = 'full' | 'boxed';

/** 글자 크기(%) — 본문 영역(`.app-content`) 안쪽만 같은 비율로 키우고 줄인다. 상단 네비·사이드바는 변하지 않는다 */
export type TextScale = 90 | 100 | 110 | 125;

/** 강조색 이름 — PrimeVue 기본 색 팔레트(`{blue.500}` 의 `blue`) 중 고른 것 */
export type PrimaryName = 'blue' | 'indigo' | 'violet' | 'pink' | 'red' | 'orange' | 'amber' | 'green' | 'teal' | 'cyan';

/** 배경(surface) 색 — `default` 는 PrimeVue Aura 의 기본(라이트 slate · 다크 zinc)이라 아무것도 바꾸지 않는다. `white` 는 라이트에서 순백 배경(다크는 neutral 로 폴백), 나머지는 라이트·다크 모두 그 계열로 바꾼다 */
export type SurfaceName = 'default' | 'white' | 'slate' | 'gray' | 'zinc' | 'neutral' | 'stone';

/** 밀도 — 메뉴 항목·툴바 높이와 본문 여백·글자 */
export type Density = 'normal' | 'compact';

/** 메뉴 위치 — `sidebar` 왼쪽/오른쪽 사이드바 · `top` 상단 네비 아래 한 줄 메뉴(사이드바 없음). 모바일(md 미만)은 이 설정과 무관하게 햄버거 드로어 */
export type MenuPosition = 'sidebar' | 'top';

/** 사이드바·서랍이 서는 쪽 */
export type SidePosition = 'left' | 'right';

export interface LayoutPrefs {
    theme: ThemeMode;
    primary: PrimaryName;
    surface: SurfaceName;
    /** 상단 네비를 강조색으로 칠한다. 끄면 색을 지정하지 않고 기본 면 색(라이트 흰색 · 다크 어두운 면)으로 둔다 */
    barTint: boolean;
    textScale: TextScale;
    contentWidth: ContentWidth;
    density: Density;
    menuPosition: MenuPosition;
    /** 메뉴 위치가 사이드바일 때 서는 쪽 */
    sidebarPosition: SidePosition;
    /** 알림·화면 설정 서랍이 열리는 쪽 */
    drawerPosition: SidePosition;
    /** 접힌 사이드바에 마우스를 올리면 잠시 펼친다 */
    hoverExpand: boolean;
    /** 새 알림을 토스트로 띄운다(화면을 보고 있을 때) */
    notifyToast: boolean;
    /** 창을 내려 두었거나 다른 창을 보고 있을 때 새 알림을 OS 알림으로 보낸다 */
    notifyOs: boolean;
}
