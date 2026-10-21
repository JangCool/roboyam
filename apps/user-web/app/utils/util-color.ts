/**
 * 홈 가젯의 색 값(`ColorValue`) 규칙 — 프리셋 12색 이름 또는 `#rrggbb` 를 읽고, 라이트·다크 카드 바탕에서 읽히는 색으로 보정하는 순수 함수만 둔다 (docs/api-home-gadgets.md 1절).
 * 상태도 요청도 없다. 값의 모양을 검사하는 곳이 이 파일 하나이고, 가젯 꾸미기(타이틀 색·카드 색·배지 색·순위 색)가 모두 여기를 거쳐 그려진다.
 *
 * 보정 규칙: 자유 색(`#rrggbb`)은 카드 바탕(라이트 `#ffffff`·다크 `#202125`)과의 명도 대비가 4.5:1 에 못 미치면 **명도만** 조정한다 —
 * 라이트는 어둡게(각 채널 ×0.9), 다크는 밝게(흰색 쪽으로 12%) 4.5:1 이 될 때까지 최대 24번. 색상(hue)은 유지한다. 저장·API 값은 원색 그대로이고 그릴 때만 보정한다.
 * 이 계산은 관리자 앱의 같은 이름 사본(`admin/app/utils/util-color.ts`)과 **같아야** 한다 — 미리보기와 공개 화면의 색이 어긋나지 않게(시안 A9 의 `readable()` 과 같은 식이다).
 */

/** 프리셋 색 한 개의 라이트·다크 색 값 */
export interface ColorPreset {
    light: string;
    dark: string;
}

/** 한 `ColorValue` 를 라이트·다크 두 바탕에서 그릴 색으로 푼 결과 — `corrected*` 는 대비 때문에 원색에서 바뀌었는가(관리 화면의 "보정됨" 표시용) */
export interface ColorPair {
    light: string;
    dark: string;
    correctedLight: boolean;
    correctedDark: boolean;
}

/** 프리셋 12색의 이름 — 보여 주는 순서이기도 하다 */
const PRESET_NAMES = ['BLUE', 'TEAL', 'AMBER', 'PURPLE', 'GREEN', 'ORANGE', 'SLATE', 'RED', 'PINK', 'INDIGO', 'SKY', 'BROWN'] as const;

/** 프리셋 색값 표 — 계약서 1절(공개·관리자 화면이 같은 표를 쓴다). 프리셋은 이미 두 바탕에서 읽히는 값이라 보정하지 않는다 */
const PRESETS: Readonly<Record<string, ColorPreset>> = Object.freeze({
    BLUE: { light: '#1c5fcf', dark: '#6ea0f5' },
    TEAL: { light: '#0e7c86', dark: '#2dd4bf' },
    AMBER: { light: '#b45f06', dark: '#fbbf24' },
    PURPLE: { light: '#7a3fb5', dark: '#a78bfa' },
    GREEN: { light: '#1f7a3a', dark: '#4ade80' },
    ORANGE: { light: '#c2410c', dark: '#fb923c' },
    SLATE: { light: '#5b6270', dark: '#94a3b8' },
    RED: { light: '#d92b2b', dark: '#f87171' },
    PINK: { light: '#be185d', dark: '#f472b6' },
    INDIGO: { light: '#4338ca', dark: '#818cf8' },
    SKY: { light: '#0369a1', dark: '#38bdf8' },
    BROWN: { light: '#78350f', dark: '#d6a574' },
});

const HEX = /^#[0-9a-fA-F]{6}$/;
/** 보정 반복 한도 — 시안과 같다 */
const MAX_STEPS = 24;
/** 읽을 수 있다고 보는 최소 명도 대비(WCAG AA 본문) */
const MIN_RATIO = 4.5;

/** `#rrggbb` → [r, g, b] (0~255). 모양이 틀리면 null */
function parse(hex: string): [number, number, number] | null {
    if (!HEX.test(hex)) return null;
    const n = Number.parseInt(hex.slice(1), 16);
    return [(n >> 16) & 255, (n >> 8) & 255, n & 255];
}

/** [r, g, b] → `#rrggbb` (소문자, 범위 밖은 0~255 로 자른다) */
function format(r: number, g: number, b: number): string {
    return '#' + [r, g, b].map((v) => Math.min(255, Math.max(0, v)).toString(16).padStart(2, '0')).join('');
}

export default class UtilColor {
    /** 라이트 카드 바탕 */
    static readonly BG_LIGHT = '#ffffff';

    /** 다크 카드 바탕 */
    static readonly BG_DARK = '#202125';

    /** 태그 바탕 틴트 비율 — 바탕 = 태그 색 14% + 카드 바탕 86% (`color-mix` 로 칠하는 식과 같다) */
    static readonly TAG_TINT = 0.14;

    /** 프리셋 12색의 이름(보여 주는 순서) */
    static readonly PRESET_NAMES: readonly string[] = PRESET_NAMES;

    /** 프리셋 색값 표(라이트 / 다크) */
    static readonly PRESETS: Readonly<Record<string, ColorPreset>> = PRESETS;

    /**
     * 프리셋 이름인가
     * @param {unknown} value - 검사할 값.
     * @returns {boolean} 프리셋 12색 중 하나(대문자 이름)이면 true.
     */
    static isPreset(value: unknown): boolean {
        return typeof value === 'string' && Object.prototype.hasOwnProperty.call(PRESETS, value);
    }

    /**
     * `#rrggbb` 모양인가(알파·`rgb()`·색 이름은 아니다)
     * @param {unknown} value - 검사할 값.
     * @returns {boolean} 6자리 16진수 색이면 true.
     */
    static isHex(value: unknown): boolean {
        return typeof value === 'string' && HEX.test(value);
    }

    /**
     * 색 값으로 쓸 수 있는가(프리셋 또는 `#rrggbb`)
     * @param {unknown} value - 검사할 값.
     * @returns {boolean} 쓸 수 있으면 true.
     */
    static isValid(value: unknown): boolean {
        return UtilColor.isPreset(value) || UtilColor.isHex(value);
    }

    /**
     * 화면이 받은 색 값을 읽는다 — 프리셋은 그대로, `#rrggbb` 는 소문자로, 그 밖(빈 값·모르는 이름·알파·`rgb()`)은 null(꾸미기를 건너뛴다)
     * @param {unknown} value - 서버가 준 색 값.
     * @returns {string | null} 정규화한 색 값 또는 null.
     */
    static normalize(value: unknown): string | null {
        if (typeof value !== 'string') return null;
        if (UtilColor.isPreset(value)) return value;
        return HEX.test(value) ? value.toLowerCase() : null;
    }

    /**
     * 상대 휘도(WCAG)
     * @param {string} hex - `#rrggbb`.
     * @returns {number} 0(검정)~1(흰색). 모양이 틀리면 0.
     */
    static luminance(hex: string): number {
        const rgb = parse(hex);
        if (!rgb) return 0;
        const [r, g, b] = rgb.map((v) => {
            const c = v / 255;
            return c <= 0.03928 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4);
        }) as [number, number, number];
        return 0.2126 * r + 0.7152 * g + 0.0722 * b;
    }

    /**
     * 두 색의 명도 대비(WCAG, 1~21)
     * @param {string} a - `#rrggbb`.
     * @param {string} b - `#rrggbb`.
     * @returns {number} 대비 비율.
     */
    static contrast(a: string, b: string): number {
        const x = UtilColor.luminance(a);
        const y = UtilColor.luminance(b);
        const [hi, lo] = x > y ? [x, y] : [y, x];
        return (hi + 0.05) / (lo + 0.05);
    }

    /**
     * 바탕 위에서 읽히는 색으로 보정한다 — 대비가 4.5:1 이상이면 그대로, 아니면 명도만 조정(어두운 바탕이면 밝게, 밝은 바탕이면 어둡게)한다.
     * @param {string} hex - 원색 `#rrggbb`. 모양이 틀리면 그대로 돌려준다.
     * @param {string} bg - 바탕 `#rrggbb`(라이트 `#ffffff`·다크 `#202125`).
     * @returns {string} 보정한 색(소문자 `#rrggbb`).
     * @example UtilColor.readable('#f5d90a', '#ffffff') // 노랑은 흰 바탕에서 어둡게 눌러 4.5:1 이 될 때까지 바뀐다
     */
    static readable(hex: string, bg: string): string {
        const rgb = parse(hex);
        if (!rgb) return hex;
        const darkBg = UtilColor.luminance(bg) < 0.5;
        let [r, g, b] = rgb;
        let color = format(r, g, b);
        let step = 0;
        while (UtilColor.contrast(color, bg) < MIN_RATIO && step < MAX_STEPS) {
            if (darkBg) {
                r = Math.round(r + (255 - r) * 0.12);
                g = Math.round(g + (255 - g) * 0.12);
                b = Math.round(b + (255 - b) * 0.12);
            } else {
                r = Math.round(r * 0.9);
                g = Math.round(g * 0.9);
                b = Math.round(b * 0.9);
            }
            color = format(r, g, b);
            step++;
        }
        return color;
    }

    /**
     * 색 값을 한 모드에서 그릴 색으로 푼다 — 프리셋은 표의 값, 자유 색은 그 모드의 카드 바탕에서 읽히게 보정한 값
     * @param {unknown} color - 색 값(프리셋 이름 또는 `#rrggbb`).
     * @param {boolean} dark - 다크 모드인가.
     * @returns {string | null} `#rrggbb`. 색 값이 틀리면 null.
     */
    static resolve(color: unknown, dark: boolean): string | null {
        const value = UtilColor.normalize(color);
        if (value === null) return null;
        if (UtilColor.isPreset(value)) return dark ? PRESETS[value]!.dark : PRESETS[value]!.light;
        return UtilColor.readable(value, dark ? UtilColor.BG_DARK : UtilColor.BG_LIGHT);
    }

    /**
     * 색 값을 라이트·다크 두 바탕의 색으로 한 번에 푼다(CSS `light-dark()` 에 넣을 두 값)
     * @param {unknown} color - 색 값.
     * @returns {ColorPair | null} 두 색과 보정 여부. 색 값이 틀리면 null.
     */
    static pair(color: unknown): ColorPair | null {
        const value = UtilColor.normalize(color);
        if (value === null) return null;
        const light = UtilColor.resolve(value, false)!;
        const dark = UtilColor.resolve(value, true)!;
        return {
            light,
            dark,
            correctedLight: !UtilColor.isPreset(value) && light !== value,
            correctedDark: !UtilColor.isPreset(value) && dark !== value,
        };
    }

    /**
     * 라이트·다크 두 색을 CSS 한 값으로 — 모드에 따라 브라우저가 고른다(`color-scheme` 이 `<html>` 에 걸려 있다)
     * @param {unknown} color - 색 값.
     * @returns {string | null} `light-dark(#…, #…)`. 색 값이 틀리면 null.
     */
    static cssPair(color: unknown): string | null {
        const pair = UtilColor.pair(color);
        return pair ? `light-dark(${pair.light}, ${pair.dark})` : null;
    }

    /**
     * 태그(말머리·게시판 이름 알약) 글자색 — 태그 바탕(같은 색 14% + 카드 바탕 86%) 위에서 4.5:1 이 되도록 **명도만** 보정한 색.
     * 색상은 유지한다(라이트는 어둡게, 다크는 밝게). 이미 4.5:1 이상이면 원색 그대로. 바탕·테두리 색 값 자체는 바꾸지 않는다(D-13).
     * @param {string} hex - 그 모드의 원색 `#rrggbb`(프리셋 표의 라이트 또는 다크 값).
     * @param {boolean} dark - 다크 모드인가.
     * @returns {string} 글자에 칠할 `#rrggbb`.
     */
    static tagText(hex: string, dark: boolean): string {
        const rgb = parse(hex);
        if (!rgb) return hex;
        const bg = dark ? UtilColor.BG_DARK : UtilColor.BG_LIGHT;
        const base = parse(bg)!;
        const tint = format(...(rgb.map((v, i) => Math.round(v * UtilColor.TAG_TINT + base[i]! * (1 - UtilColor.TAG_TINT))) as [number, number, number]));
        return UtilColor.readable(hex, tint);
    }

    /**
     * 태그 글자색을 라이트·다크 두 모드로 푼 CSS 값
     * @param {unknown} color - 색 값(프리셋 이름 또는 `#rrggbb`).
     * @returns {string | null} `light-dark(#…, #…)`. 색 값이 틀리면 null.
     */
    static cssTagText(color: unknown): string | null {
        const pair = UtilColor.pair(color);
        return pair ? `light-dark(${UtilColor.tagText(pair.light, false)}, ${UtilColor.tagText(pair.dark, true)})` : null;
    }

    /**
     * 색을 칠한 바탕 위에 올릴 글자색 — 흰색과 거의 검정 중 대비가 큰 쪽
     * @param {string} bg - 바탕 `#rrggbb`.
     * @returns {string} `#ffffff` 또는 `#111318`.
     */
    static onColor(bg: string): string {
        return UtilColor.contrast('#ffffff', bg) >= UtilColor.contrast('#111318', bg) ? '#ffffff' : '#111318';
    }

    /**
     * 색을 칠한 바탕 위의 글자색을 라이트·다크 두 모드로 푼 CSS 값
     * @param {unknown} color - 바탕이 될 색 값.
     * @returns {string | null} `light-dark(#…, #…)`. 색 값이 틀리면 null.
     */
    static cssOnPair(color: unknown): string | null {
        const pair = UtilColor.pair(color);
        return pair ? `light-dark(${UtilColor.onColor(pair.light)}, ${UtilColor.onColor(pair.dark)})` : null;
    }
}
