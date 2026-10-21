import type { ContentWidth, Density, LayoutPrefs, MenuPosition, PrimaryName, SidePosition, SurfaceName, TextScale, ThemeMode } from '~/composables/shell/useLayoutPrefs.types';

/**
 * 화면 설정(보기 설정)의 기본값·고를 수 있는 값·저장값 검사. 상태도 저장도 없다(그건 `useLayoutPrefs`).
 *
 * 저장값은 사용자가 손댈 수 있는 글자(localStorage)라 읽을 때마다 칸별로 검사한다 — 모르는 값·없는 칸은 기본값으로 떨어진다
 * (그래서 칸을 새로 더해도 예전 저장값이 그대로 읽힌다).
 */

export interface PrimaryOption {
    name: PrimaryName;
    /** 이름 문구의 i18n 키 (`shell.color…`) */
    labelKey: string;
}

export interface BarColors {
    /** 상단 네비 배경 — CSS 값(PrimeVue 토큰 참조) */
    bg: string;
    /** 상단 네비 글자·아이콘 — CSS 값(PrimeVue 토큰 참조) */
    fg: string;
    /** 호버·입력 칸·아바타 면에 옅게 까는 색 — 글자 대비를 해치지 않는 쪽(흰 글자 바는 어둡게, 어두운 글자 바는 밝게) */
    shade: string;
    /** 상단 네비 아래 선·브랜드 칸 구분선 색 */
    line: string;
    /** 프로필 동그라미(이니셜 아바타) 배경 — 바를 칠했으면 바 위의 옅은 면, 안 칠했으면 강조색 */
    avatarBg: string;
    /** 프로필 동그라미 글자색 */
    avatarFg: string;
}

export interface SurfaceOption {
    name: SurfaceName;
    /** 이름 문구의 i18n 키 (`shell.surface…`) */
    labelKey: string;
    /** 견본에 칠하는 PrimeVue 색 변수 */
    swatch: string;
}

const DEFAULT_PRIMARY: PrimaryName = 'blue';
/** 밝은 강조색 — 상단 네비에서 흰 글자로는 대비(4.5:1)가 모자라 어두운 글자(surface-900)를 쓴다 */
const BRIGHT_PRIMARY: readonly PrimaryName[] = ['orange', 'amber', 'green', 'teal', 'cyan'];
/** 라이트에서 `primary-600` 으로도 흰 글자 대비가 얇은 색 — `primary-700` 을 쓴다(흐린 글자·입력 칸의 여유) */
const DEEP_PRIMARY: readonly PrimaryName[] = ['pink', 'red'];
/** 강조색으로 칠한 상단 네비의 구분선 — 글자색을 옅게 깐다(바 색 위에서 보이도록) */
const BAR_TINT_LINE = 'color-mix(in srgb, var(--bar-fg) 24%, transparent)';
const PALETTE_STEPS = [50, 100, 200, 300, 400, 500, 600, 700, 800, 900, 950] as const;

function oneOf<T extends string>(value: unknown, allowed: readonly T[]): value is T {
    return allowed.includes(value as T);
}

export default class UtilLayoutPrefs {
    /** 브라우저에 설정을 두는 자리 */
    static readonly STORAGE_KEY = 'roboyam.admin.layout';
    /** 사이드바 펼침(사람이 고른 것)을 두는 자리 — 설정과 따로 둔다(이 브라우저의 지금 상태이지 계정 설정이 아니다) */
    static readonly SIDEBAR_KEY = 'roboyam.admin.sidebar-expanded';

    /** 기본 강조색 — `nuxt.config.ts` 의 `RoboyamPreset.semantic.primary` 와 같다(파랑). 바꾸면 둘을 함께 바꾼다 */
    static readonly DEFAULT_PRIMARY: PrimaryName = DEFAULT_PRIMARY;

    static readonly TEXT_SCALES: readonly TextScale[] = [90, 100, 110, 125];

    static readonly PRIMARY_COLORS: readonly PrimaryOption[] = [
        { name: 'blue', labelKey: 'shell.colorBlue' },
        { name: 'indigo', labelKey: 'shell.colorIndigo' },
        { name: 'violet', labelKey: 'shell.colorViolet' },
        { name: 'pink', labelKey: 'shell.colorPink' },
        { name: 'red', labelKey: 'shell.colorRed' },
        { name: 'orange', labelKey: 'shell.colorOrange' },
        { name: 'amber', labelKey: 'shell.colorAmber' },
        { name: 'green', labelKey: 'shell.colorGreen' },
        { name: 'teal', labelKey: 'shell.colorTeal' },
        { name: 'cyan', labelKey: 'shell.colorCyan' },
    ];

    static readonly SURFACE_COLORS: readonly SurfaceOption[] = [
        { name: 'default', labelKey: 'shell.surfaceDefault', swatch: 'var(--p-slate-200)' },
        { name: 'white', labelKey: 'shell.surfaceWhite', swatch: 'var(--p-surface-0)' },
        { name: 'slate', labelKey: 'shell.surfaceSlate', swatch: 'var(--p-slate-200)' },
        { name: 'gray', labelKey: 'shell.surfaceGray', swatch: 'var(--p-gray-200)' },
        { name: 'zinc', labelKey: 'shell.surfaceZinc', swatch: 'var(--p-zinc-200)' },
        { name: 'neutral', labelKey: 'shell.surfaceNeutral', swatch: 'var(--p-neutral-200)' },
        { name: 'stone', labelKey: 'shell.surfaceStone', swatch: 'var(--p-stone-200)' },
    ];

    static readonly DEFAULTS: LayoutPrefs = {
        theme: 'light',
        primary: DEFAULT_PRIMARY,
        surface: 'default',
        barTint: true,
        textScale: 100,
        contentWidth: 'full',
        density: 'normal',
        menuPosition: 'sidebar',
        sidebarPosition: 'left',
        drawerPosition: 'right',
        hoverExpand: false,
        notifyToast: true,
        // OS 알림은 사용자가 켤 때 브라우저 권한을 묻는다 — 기본은 꺼 둔다
        notifyOs: false,
    };

    /**
     * 저장된 글자를 설정으로 바꾼다. 비었거나 깨졌거나 모르는 값은 칸별로 기본값을 쓴다.
     * @param {string | null} raw - localStorage 에서 읽은 글자(JSON).
     * @returns {LayoutPrefs} 검사를 마친 설정.
     */
    static parse(raw: string | null): LayoutPrefs {
        const out: LayoutPrefs = { ...UtilLayoutPrefs.DEFAULTS };
        if (!raw) return out;
        let data: unknown;
        try {
            data = JSON.parse(raw);
        } catch {
            return out;
        }
        if (typeof data !== 'object' || data === null) return out;
        const d = data as Record<string, unknown>;

        if (oneOf<ThemeMode>(d.theme, ['light', 'dark'])) out.theme = d.theme;
        if (oneOf<PrimaryName>(d.primary, UtilLayoutPrefs.PRIMARY_COLORS.map((c) => c.name))) out.primary = d.primary;
        if (oneOf<SurfaceName>(d.surface, UtilLayoutPrefs.SURFACE_COLORS.map((c) => c.name))) out.surface = d.surface;
        if (typeof d.barTint === 'boolean') out.barTint = d.barTint;
        if (UtilLayoutPrefs.TEXT_SCALES.includes(d.textScale as TextScale)) out.textScale = d.textScale as TextScale;
        if (oneOf<ContentWidth>(d.contentWidth, ['full', 'boxed'])) out.contentWidth = d.contentWidth;
        if (oneOf<Density>(d.density, ['normal', 'compact'])) out.density = d.density;
        if (oneOf<MenuPosition>(d.menuPosition, ['sidebar', 'top'])) out.menuPosition = d.menuPosition;
        if (oneOf<SidePosition>(d.sidebarPosition, ['left', 'right'])) out.sidebarPosition = d.sidebarPosition;
        if (oneOf<SidePosition>(d.drawerPosition, ['left', 'right'])) out.drawerPosition = d.drawerPosition;
        if (typeof d.hoverExpand === 'boolean') out.hoverExpand = d.hoverExpand;
        if (typeof d.notifyToast === 'boolean') out.notifyToast = d.notifyToast;
        if (typeof d.notifyOs === 'boolean') out.notifyOs = d.notifyOs;
        return out;
    }

    /**
     * PrimeVue 의 `updatePrimaryPalette` 에 넘기는 팔레트 — 이름 하나를 50~950 단계의 토큰 참조(`{blue.500}`)로 푼다.
     * @param {PrimaryName} name - 강조색 이름.
     * @returns {Record<number, string>} 단계 → 토큰 참조.
     */
    static primaryPalette(name: PrimaryName): Record<number, string> {
        return Object.fromEntries(PALETTE_STEPS.map((step) => [step, `{${name}.${step}}`]));
    }

    /**
     * PrimeVue 의 `updateSurfacePalette` 에 넘기는 팔레트. `default` 는 Aura 기본(라이트 slate · 다크 zinc)의 원래 값(`light-dark(...)`)으로 되돌린다.
     * ⚠ Aura 의 기본 surface 정의가 바뀌면(`@primeuix/themes` 업그레이드) `default` 의 두 계열도 맞춰야 한다.
     * @param {SurfaceName} name - 배경 색 이름.
     * @returns {Record<number, string>} 단계 → 토큰 참조.
     */
    static surfacePalette(name: SurfaceName): Record<number, string> {
        if (name === 'white') {
            // 라이트: 배경(50)만 순백(surface.0 토큰)이고 나머지 면·선·글자는 중립 회색(neutral). 다크: 흰 배경은 뜻이 없어 neutral 계열 그대로
            return Object.fromEntries(PALETTE_STEPS.map((step) => [step, step === 50 ? 'light-dark({surface.0}, {neutral.50})' : `{neutral.${step}}`]));
        }
        return Object.fromEntries(PALETTE_STEPS.map((step) => [step, name === 'default' ? `light-dark({slate.${step}}, {zinc.${step}})` : `{${name}.${step}}`]));
    }

    /**
     * 상단 네비(브랜드 칸 포함)의 배경·글자 색 — 선택한 강조색을 따른다. 모두 PrimeVue 토큰의 조합이고 라이트/다크는 `light-dark()` 가 가른다.
     * 대비(글자 대 배경 4.5:1)를 맞추려고 강조색마다 다르게 고른다:
     *   · 밝은 색(orange·amber·green·teal·cyan): 라이트·다크 모두 배경 `primary-700` + 흰 글자(`surface-0`) — 어두운 글자는 읽기 힘들다는 사용자 요청
     *   · 나머지: 배경 라이트 `primary-600`(pink·red 는 `700`)(다크 `400`) + 라이트 흰 글자(`surface-0`) / 다크 어두운 글자(`surface-900`)
     * 값은 `useLayoutPrefs.apply` 가 `--bar-bg` · `--bar-fg` · `--bar-shade` 로 건다(`main.css` 의 `.app-topbar` 가 읽는다).
     * `tinted` 가 false 면 바에는 색을 지정하지 않는다 — 기본 면 색(라이트 흰색 · 다크 어두운 면)과 기본 글자색, 호버 면은 글자색을 옅게 깐다. 프로필 동그라미는 이 경우에도 강조색을 따른다.
     * @param {PrimaryName} name - 강조색 이름.
     * @param {boolean} tinted - 강조색으로 칠하는가.
     * @returns {BarColors} 배경·글자 CSS 값.
     */
    static barColors(name: PrimaryName, tinted: boolean): BarColors {
        // 강조색으로 칠했을 때의 배경·글자 — 대비(4.5:1)를 맞춘 조합
        let accent: { bg: string; fg: string; shade: string };
        if (BRIGHT_PRIMARY.includes(name)) {
            accent = { bg: 'var(--p-primary-700)', fg: 'var(--p-surface-0)', shade: 'var(--p-surface-900)' };
        } else {
            const light = DEEP_PRIMARY.includes(name) ? 700 : 600;
            accent = { bg: `light-dark(var(--p-primary-${light}), var(--p-primary-400))`, fg: 'light-dark(var(--p-surface-0), var(--p-surface-900))', shade: 'light-dark(var(--p-surface-900), var(--p-surface-0))' };
        }
        if (!tinted) {
            // 바는 색을 지정하지 않지만 프로필 동그라미는 강조색 그대로(바 색이 아니라 강조색을 따른다)
            return { bg: 'light-dark(var(--p-surface-0), var(--p-surface-900))', fg: 'light-dark(var(--p-surface-900), var(--p-surface-0))', shade: 'light-dark(var(--p-surface-900), var(--p-surface-0))', line: 'var(--line)', avatarBg: accent.bg, avatarFg: accent.fg };
        }
        return { ...accent, line: BAR_TINT_LINE, avatarBg: 'color-mix(in srgb, var(--bar-shade) 28%, transparent)', avatarFg: 'var(--bar-fg)' };
    }

    /**
     * 글자 크기를 한 칸 움직인다. 끝에서는 그대로다.
     * @param {TextScale} current - 지금 크기(%).
     * @param {number} by - +1(크게) 또는 -1(작게).
     * @returns {TextScale} 움직인 크기.
     */
    static stepScale(current: TextScale, by: number): TextScale {
        const at = UtilLayoutPrefs.TEXT_SCALES.indexOf(current);
        return UtilLayoutPrefs.TEXT_SCALES[at + by] ?? current;
    }
}
