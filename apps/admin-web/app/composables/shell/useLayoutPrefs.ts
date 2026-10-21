import { updatePrimaryPalette, updateSurfacePalette } from '@primeuix/themes';
import type { LayoutPrefs } from '~/composables/shell/useLayoutPrefs.types';
import UtilLayoutPrefs from '~/utils/util-layout-prefs';

/**
 * 관리자 셸의 화면 설정과 사이드바 상태 — 셸(레이아웃·상단 바·사이드바·설정 서랍)이 함께 보는 공유 상태(`useState`).
 *
 *  - 설정(`prefs`): 다크 모드 · 강조색(상단 네비 색 포함) · 배경 색 · 글자 크기 · 본문 폭 · 밀도 · 메뉴 위치 · 사이드바/서랍 위치 · 호버 확장 · 알림 방식.
 *    localStorage 에 두고(`UtilLayoutPrefs.STORAGE_KEY`), 앱이 뜨기 전에 `plugins/00.layout-prefs.client.ts` 가 `apply()` 로 화면에 건다(깜빡임 방지).
 *  - 사이드바: `expanded`(사람이 고른 펼침/접힘, 기억한다) · `hovered`(레일에 마우스가 올라와 있나) · `isMobile`(md 미만).
 *
 * 저장은 이 브라우저의 것이다(계정에 올리지 않는다). 계정 설정 API 가 생기면 그때 서버 동기화를 붙인다.
 * 서버와 말하지 않으므로 `useXxxApi` 는 없다.
 */

/** 모바일 판정 — Tailwind `md`(768px) 미만. CSS 의 `md:` 와 같은 경계다 */
const MOBILE_QUERY = '(max-width: 767px)';

/** 화면 폭 감시는 앱 전체에서 한 번만 건다 — 이 컴포저블은 셸 부품마다 부르므로 그냥 달면 부품 수만큼 쌓인다 */
let viewportBound = false;

/** 지금 화면에 건 강조색·배경 색 — 같은 색을 다시 걸지 않으려고 기억한다 */
let appliedPrimary: string = UtilLayoutPrefs.DEFAULT_PRIMARY;
let appliedSurface: string = UtilLayoutPrefs.DEFAULTS.surface;

function readStorage(key: string): string | null {
    try {
        return window.localStorage.getItem(key);
    } catch {
        return null; // 사생활 보호 모드 등 — 저장 없이 기본값으로 뜬다
    }
}

function writeStorage(key: string, value: string): void {
    try {
        window.localStorage.setItem(key, value);
    } catch {
        /* 못 적으면 이번 방문에서만 기억한다 */
    }
}

export function useLayoutPrefs() {
    const prefs = useState<LayoutPrefs>('layout-prefs', () => UtilLayoutPrefs.parse(readStorage(UtilLayoutPrefs.STORAGE_KEY)));

    /** 사이드바 펼침 — 사람이 고른 값. 처음이면 펼침. 좁은 화면에서는 쓰지 않는다(그쪽은 햄버거 드로어) */
    const expanded = useState<boolean>('layout-expanded', () => readStorage(UtilLayoutPrefs.SIDEBAR_KEY) !== 'false');
    /** 접힌 사이드바(레일)에 마우스가 올라와 있는가 */
    const hovered = useState<boolean>('layout-hovered', () => false);
    /** 좁은 화면(md 미만)인가 — 사용자가 고르는 값이 아니라 화면이 정하는 사실이라 설정에 넣지 않는다 */
    const isMobile = useState<boolean>('layout-mobile', () => import.meta.client && window.matchMedia(MOBILE_QUERY).matches);

    if (import.meta.client && !viewportBound) {
        viewportBound = true;
        const mq = window.matchMedia(MOBILE_QUERY);
        mq.addEventListener('change', (e) => {
            isMobile.value = e.matches;
            hovered.value = false;
        });
    }

    /** 사이드바가 있는가 — 데스크톱이고 메뉴 위치가 사이드바일 때 */
    const hasSidebar = computed(() => !isMobile.value && prefs.value.menuPosition === 'sidebar');
    /** 상단 메뉴 줄이 있는가 — 데스크톱이고 메뉴 위치가 상단일 때 */
    const hasMenuTop = computed(() => !isMobile.value && prefs.value.menuPosition === 'top');
    /** 사이드바가 서는 쪽 */
    const sidebarSide = computed(() => prefs.value.sidebarPosition);
    /** 레일(아이콘만) 상태인가 — 사이드바가 있고 사람이 접어 둔 것 */
    const isRail = computed(() => hasSidebar.value && !expanded.value);
    /** 사이드바가 지금 펼쳐 보이는가 — 펼쳐 두었거나, 호버 확장이 켜져 있고 레일에 마우스가 올라와 있을 때 */
    const sidebarOpen = computed(() => expanded.value || (prefs.value.hoverExpand && hovered.value));
    /** 호버로 잠시 펼친 상태인가 — 이때는 자리(spacer)를 넓히지 않고 본문 위로 덮는다(본문 폭이 흔들리지 않게) */
    const hoverOverlay = computed(() => hasSidebar.value && !expanded.value && sidebarOpen.value);
    /** 브랜드 칸이 사이드바 폭에 묶이는가 — 왼쪽 사이드바일 때만(오른쪽이면 브랜드는 제 폭, 묶이는 쪽은 반대편이라 만들지 않는다) */
    const brandLinked = computed(() => hasSidebar.value && sidebarSide.value === 'left');
    /** 브랜드 칸 폭 — 사이드바 폭에 맞춰 세로 구분선이 한 줄로 이어진다. 묶이지 않으면 제 폭 */
    const brandWidth = computed(() => (brandLinked.value ? (expanded.value ? 'var(--w-sidebar)' : 'var(--w-rail)') : 'auto'));

    /** 설정을 화면에 건다 — `html[data-theme]`(다크) · `[data-density]` · 글자 크기 배율 · 상단 네비 색 · PrimeVue 강조색/배경 색 */
    function apply(): void {
        const el = document.documentElement;
        el.dataset.theme = prefs.value.theme;
        el.dataset.density = prefs.value.density;
        el.style.setProperty('--text-scale', String(prefs.value.textScale / 100));
        // 상단 네비 색 — 강조색을 따른다(밝은 색은 글자를 어둡게). `--p-primary-*` 토큰 참조라 팔레트가 바뀌면 같이 바뀐다
        const bar = UtilLayoutPrefs.barColors(prefs.value.primary, prefs.value.barTint);
        el.style.setProperty('--bar-bg', bar.bg);
        el.style.setProperty('--bar-fg', bar.fg);
        el.style.setProperty('--bar-shade', bar.shade);
        el.style.setProperty('--bar-line', bar.line);
        el.style.setProperty('--bar-avatar-bg', bar.avatarBg);
        el.style.setProperty('--bar-avatar-fg', bar.avatarFg);
        // PrimeVue 문서(Styled Mode › updatePrimaryPalette · updateSurfacePalette) 방식 — 실행 중에 팔레트만 바꾼다
        if (appliedPrimary !== prefs.value.primary) {
            updatePrimaryPalette(UtilLayoutPrefs.primaryPalette(prefs.value.primary));
            appliedPrimary = prefs.value.primary;
        }
        if (appliedSurface !== prefs.value.surface) {
            updateSurfacePalette(UtilLayoutPrefs.surfacePalette(prefs.value.surface));
            appliedSurface = prefs.value.surface;
        }
    }

    function set<K extends keyof LayoutPrefs>(key: K, value: LayoutPrefs[K]): void {
        prefs.value = { ...prefs.value, [key]: value };
        writeStorage(UtilLayoutPrefs.STORAGE_KEY, JSON.stringify(prefs.value));
        apply();
    }

    function toggleTheme(): void {
        set('theme', prefs.value.theme === 'dark' ? 'light' : 'dark');
    }

    /** 사이드바 접기/펴기 — 기억한다 */
    function toggleSidebar(): void {
        expanded.value = !expanded.value;
        hovered.value = false;
        writeStorage(UtilLayoutPrefs.SIDEBAR_KEY, String(expanded.value));
    }

    return {
        prefs,
        expanded,
        hovered,
        isMobile,
        hasSidebar,
        hasMenuTop,
        sidebarSide,
        isRail,
        sidebarOpen,
        hoverOverlay,
        brandLinked,
        brandWidth,
        apply,
        set,
        toggleTheme,
        toggleSidebar,
    };
}
