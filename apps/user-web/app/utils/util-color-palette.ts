/**
 * 색상 테마(보기 설정 「색상」, 사용자 결정 2026-11-06) — 강조색(단추·링크·선택 표시)·페이지 바탕·PC 로고 줄·모바일 상단 바를 한 벌로 바꾼다.
 * 값은 `<html data-palette>` 로 걸고(`plugins/view-theme.client.ts`), 색은 `assets/css/palettes.css` 가 PrimeVue 변수를 다시 가리켜 칠한다.
 * ⚠ MOCK: 서버 보기 설정에 칸이 없어 이 브라우저에만 저장한다(`useColorPalette`) — 화면 확정 뒤 서버 칸을 더한다.
 * 상태도 요청도 없다.
 */
export type ColorPalette = 'DEFAULT' | 'PEACH' | 'HONEY' | 'SAGE' | 'SKY' | 'OCEAN' | 'BLUE' | 'NAVY';

/** 설정 화면의 견본 색 — 실제 색은 `palettes.css` 다(같은 값을 맞춰 둔다) */
export interface ColorPaletteSwatch {
    value: ColorPalette;
    /** 이름 문구 키(`view.palette…`) */
    labelKey: string;
    /** 로고 줄 바탕 */
    topbar: string;
    /** 로고 글자 */
    brand: string;
    /** 강조색(단추) */
    accent: string;
    /** 페이지 바탕 */
    page: string;
}

export default class UtilColorPalette {
    /** 이 브라우저에 저장해 두는 열쇠 */
    static readonly STORAGE_KEY = 'roboyam:color-palette';
    /** 상단 그라데이션 켜짐(`on`·`off`)을 저장해 두는 열쇠 */
    static readonly GRADIENT_STORAGE_KEY = 'roboyam:topbar-gradient';

    static readonly DEFAULT: ColorPalette = 'DEFAULT';

    static readonly SWATCHES: readonly ColorPaletteSwatch[] = [
        { value: 'DEFAULT', labelKey: 'view.paletteDefault', topbar: '#f2f4f7', brand: '#334155', accent: '#3b82f6', page: '#f2f4f7' },
        { value: 'PEACH', labelKey: 'view.palettePeach', topbar: '#ffe7d4', brand: '#334155', accent: '#ea580c', page: '#faf3eb' },
        { value: 'HONEY', labelKey: 'view.paletteHoney', topbar: '#ffefc0', brand: '#334155', accent: '#b45309', page: '#fbf6e8' },
        { value: 'SAGE', labelKey: 'view.paletteSage', topbar: '#deeee3', brand: '#334155', accent: '#047857', page: '#f1f6f2' },
        { value: 'SKY', labelKey: 'view.paletteSky', topbar: '#dcecff', brand: '#334155', accent: '#2563eb', page: '#f8f5ee' },
        { value: 'OCEAN', labelKey: 'view.paletteOcean', topbar: '#d6f0f3', brand: '#334155', accent: '#0f766e', page: '#f0f7f6' },
        { value: 'BLUE', labelKey: 'view.paletteBlue', topbar: '#2563eb', brand: '#ffffff', accent: '#2563eb', page: '#f2f4f7' },
        { value: 'NAVY', labelKey: 'view.paletteNavy', topbar: '#2646a2', brand: '#ffffff', accent: '#2646a2', page: '#f2f4f7' },
    ];

    /**
     * 저장해 둔 값을 읽는다 — 모르는 값은 기본.
     * @param {unknown} raw - 값.
     * @returns {ColorPalette} 색상 테마.
     */
    static sanitize(raw: unknown): ColorPalette {
        return UtilColorPalette.SWATCHES.find((s) => s.value === raw)?.value ?? UtilColorPalette.DEFAULT;
    }

    /**
     * `<html data-palette>` 값 — 기본이면 null(속성을 지운다)
     * @param {ColorPalette} palette - 색상 테마.
     * @returns {string | null} 소문자 이름.
     */
    static attr(palette: ColorPalette): string | null {
        return palette === UtilColorPalette.DEFAULT ? null : palette.toLowerCase();
    }
}
