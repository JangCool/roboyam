import type { HomeGadgetCardLook, HomeGadgetHeaderStyle, HomeGadgetMobileFold, HomeGadgetStyle } from '~/composables/home/api/useHomeLayoutApi.types';

const HEADER_STYLES: readonly HomeGadgetHeaderStyle[] = ['BAR', 'UNDERLINE', 'FILLED', 'PLAIN', 'HIDDEN'];
const TITLE_SIZES: readonly HomeGadgetStyle['titleSize'][] = ['S', 'M', 'L'];
const CARD_LOOKS: readonly HomeGadgetCardLook[] = ['DEFAULT', 'FLAT', 'OUTLINE', 'TINT'];
const MOBILE_FOLDS: readonly HomeGadgetMobileFold[] = ['NONE', 'OPEN', 'CLOSED'];
/** 색 프리셋 12색 — user-web `UtilColor.PRESET_NAMES` 와 같은 이름·차례(게시판 표시색 `UtilBoard.COLORS` 와도 같다) */
const COLOR_PRESETS: readonly string[] = ['BLUE', 'TEAL', 'AMBER', 'PURPLE', 'GREEN', 'ORANGE', 'SLATE', 'RED', 'PINK', 'INDIGO', 'SKY', 'BROWN'];
/** 머리 아이콘 후보(PrimeIcons 8 에 있는 것만) — 받은 값이 목록 밖이어도 모양(`pi-…`)이 맞으면 그대로 둔다 */
const ICONS: readonly string[] = [
    'pi-star',
    'pi-bolt',
    'pi-sparkles',
    'pi-trophy',
    'pi-crown',
    'pi-heart',
    'pi-thumbs-up',
    'pi-bookmark',
    'pi-megaphone',
    'pi-bell',
    'pi-info-circle',
    'pi-comments',
    'pi-list',
    'pi-th-large',
    'pi-images',
    'pi-video',
    'pi-users',
    'pi-shopping-bag',
    'pi-gift',
    'pi-tag',
    'pi-link',
    'pi-compass',
    'pi-chart-line',
    'pi-calendar',
    'pi-lightbulb',
    'pi-microchip',
    'pi-book',
    'pi-globe',
];
/** 비어도 자리를 남기는 종류(처음 값) — user-web `UtilHomeGadget.defaultKeep` 과 같다 */
const KEEP_KINDS: readonly string[] = ['POST_LIST', 'BOARD_CARDS', 'GALLERY', 'HIGHLIGHT', 'GROUPS'];

const ICON = /^pi-[a-z0-9-]{2,30}$/;
const HEX = /^#[0-9a-fA-F]{6}$/;

function obj(value: unknown): Record<string, unknown> {
    return typeof value === 'object' && value !== null && !Array.isArray(value) ? (value as Record<string, unknown>) : {};
}

function oneOf<T extends string>(value: unknown, list: readonly T[], fallback: T): T {
    return list.find((v) => v === value) ?? fallback;
}

function text(value: unknown, max: number): string {
    return typeof value === 'string' ? value.slice(0, max) : '';
}

/**
 * 가젯 공통 꾸미기(⚙ 설정 창 「모양」·「노출」)의 순수 계산 — 칸 값 목록·글자 한도·처음 값·읽기·비교. 칸·값·처음 값은 user-web `UtilHomeGadget` 의 `readStyle` 과 같다(공개 화면이 읽는 쪽에 맞춘다).
 * 제목(1~40자)·PC/모바일 노출도 여기서 본다(가젯 공통 설정 — 서버 `GadgetStyles`·`GadgetPatches` 와 같은 칸·한도).
 */
export default class UtilGadgetStyle {
    static readonly TITLE_MAX = 40;
    static readonly SUBTITLE_MAX = 40;
    static readonly EMPTY_TEXT_MAX = 40;
    static readonly HEADER_STYLES = HEADER_STYLES;
    static readonly TITLE_SIZES = TITLE_SIZES;
    static readonly CARD_LOOKS = CARD_LOOKS;
    static readonly MOBILE_FOLDS = MOBILE_FOLDS;
    static readonly COLOR_PRESETS = COLOR_PRESETS;
    static readonly ICONS = ICONS;

    /**
     * 처음 값 — user-web `readStyle` 이 빈 꾸미기를 읽은 값(테마 기본을 모를 때)
     * @param {string} type - 가젯 종류.
     * @returns {HomeGadgetStyle} 꾸미기.
     */
    static initial(type: string): HomeGadgetStyle {
        return {
            icon: null,
            headerStyle: 'BAR',
            titleSize: 'M',
            titleColor: null,
            subtitle: '',
            cardLook: 'DEFAULT',
            cardColor: null,
            mobileFold: 'NONE',
            empty: { keep: KEEP_KINDS.includes(type), message: true, text: '' },
        };
    }

    /**
     * 색 값 읽기 — 프리셋 이름은 그대로, `#rrggbb` 는 소문자로, 그 밖은 null(자동)
     * @param {unknown} value - 색 값.
     * @returns {string | null} 색.
     */
    static color(value: unknown): string | null {
        if (typeof value !== 'string') return null;
        if (COLOR_PRESETS.includes(value)) return value;
        return HEX.test(value) ? value.toLowerCase() : null;
    }

    /**
     * 색이 `#rrggbb` 꼴인가(직접 고른 색)
     * @param {unknown} value - 색 값.
     * @returns {boolean} 직접 고른 색이면 true.
     */
    static isHex(value: unknown): boolean {
        return typeof value === 'string' && HEX.test(value);
    }

    /**
     * 읽기·바로잡기 — 없는 칸·모르는 값은 처음 값(user-web `readStyle` 과 같은 규칙)
     * @param {unknown} raw - 받은 꾸미기(관리자 응답 — 없으면 처음 값).
     * @param {string} type - 가젯 종류.
     * @returns {HomeGadgetStyle} 꾸미기.
     */
    static normalize(raw: unknown, type: string): HomeGadgetStyle {
        const s = obj(raw);
        const e = obj(s.empty);
        const base = UtilGadgetStyle.initial(type);
        return {
            icon: typeof s.icon === 'string' && ICON.test(s.icon) ? s.icon : null,
            headerStyle: oneOf(s.headerStyle, HEADER_STYLES, base.headerStyle),
            titleSize: oneOf(s.titleSize, TITLE_SIZES, base.titleSize),
            titleColor: UtilGadgetStyle.color(s.titleColor),
            subtitle: text(s.subtitle, UtilGadgetStyle.SUBTITLE_MAX),
            cardLook: oneOf(s.cardLook, CARD_LOOKS, base.cardLook),
            cardColor: UtilGadgetStyle.color(s.cardColor),
            mobileFold: oneOf(s.mobileFold, MOBILE_FOLDS, base.mobileFold),
            empty: {
                keep: typeof e.keep === 'boolean' ? e.keep : base.empty.keep,
                message: typeof e.message === 'boolean' ? e.message : base.empty.message,
                text: text(e.text, UtilGadgetStyle.EMPTY_TEXT_MAX),
            },
        };
    }

    /**
     * 저장할 모양으로 — 부제·안내 문구 앞뒤 공백을 뗀다
     * @param {HomeGadgetStyle} style - 편집 중 꾸미기.
     * @param {string} type - 가젯 종류.
     * @returns {HomeGadgetStyle} 꾸미기.
     */
    static finish(style: HomeGadgetStyle, type: string): HomeGadgetStyle {
        const s = UtilGadgetStyle.normalize(style, type);
        return { ...s, subtitle: s.subtitle.trim(), empty: { ...s.empty, text: s.empty.text.trim() } };
    }

    /**
     * 모바일 접기를 고를 수 있는가 — 머리를 숨기면 접을 단추가 없다(user-web `UtilHomeGadget.foldable`)
     * @param {HomeGadgetStyle} style - 꾸미기.
     * @returns {boolean} 고를 수 있으면 true.
     */
    static foldSelectable(style: HomeGadgetStyle): boolean {
        return style.headerStyle !== 'HIDDEN';
    }

    /**
     * 제목 문제 — 비었거나 40자를 넘으면 문구 키
     * @param {string} title - 제목.
     * @returns {'titleRequired' | 'titleTooLong' | null} 문구 키(`homeLayout.<키>`) 또는 null.
     */
    static titleProblem(title: string): 'titleRequired' | 'titleTooLong' | null {
        const t = title.trim();
        if (!t) return 'titleRequired';
        return t.length > UtilGadgetStyle.TITLE_MAX ? 'titleTooLong' : null;
    }

    /**
     * 바뀜 비교용 글자 — 칸 차례에 상관없이 같은 꾸미기면 같은 글자. 꾸미기가 없으면(서버가 주지 않음 · 고친 적 없음) 빈 글자
     * @param {unknown} style - 꾸미기.
     * @param {string} type - 가젯 종류.
     * @returns {string} 비교 글자.
     */
    static key(style: unknown, type: string): string {
        if (!style) return '';
        const s = UtilGadgetStyle.normalize(style, type);
        return JSON.stringify([s.icon, s.headerStyle, s.titleSize, s.titleColor, s.subtitle, s.cardLook, s.cardColor, s.mobileFold, s.empty.keep, s.empty.message, s.empty.text]);
    }
}
