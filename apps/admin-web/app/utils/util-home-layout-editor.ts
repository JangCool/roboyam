import type { HomeGridColumns, HomeLayout } from '#layers/home-layout/utils/util-home-layout';
import UtilHomeLayout from '#layers/home-layout/utils/util-home-layout';
import type { HomeGadgetKind } from '~/composables/home/api/useHomeLayoutApi.types';

/**
 * 홈 구성 편집(`/settings/theme` 의 `HomeLayoutEditor`)의 순수 계산 — 가젯 종류별 아이콘·최소/기본 크기, 편집기 자리와 저장 모양 사이 변환,
 * 실제 화면에서 온 옮기기·크기(`setBox`). 상태도 요청도 없다. 배치 자체의 규칙(읽기·위로 당기기·칸 수 바꾸기)은 공유 레이어 `UtilHomeLayout`.
 *
 * 편집기는 늘 위로 당긴 모양을 둔다(공개 홈도 같은 규칙으로 그린다).
 */

/** 종류 하나의 편집기 값 — 크기는 12칸 기준(24칸이면 폭을 2배) */
export interface HomeKindSpec {
    kind: HomeGadgetKind;
    /** PrimeIcons 이름(`pi-…`) */
    icon: string;
    minW: number;
    minH: number;
    /** 새로 넣을 때 폭·높이 */
    w: number;
    h: number;
}

/** 편집기 자리(`i` = 가젯 ID) */
export interface HomeEditorItem {
    i: string;
    x: number;
    y: number;
    w: number;
    h: number;
    minW: number;
    minH: number;
}

/** 가젯 종류 8종(보일 순서) — 로보얌에 없는 핫딜·직접홍보·광고는 없다 */
const KINDS: readonly HomeKindSpec[] = [
    { kind: 'HIGHLIGHT', icon: 'pi-sparkles', minW: 4, minH: 8, w: 8, h: 12 },
    { kind: 'POST_LIST', icon: 'pi-list', minW: 3, minH: 6, w: 6, h: 10 },
    { kind: 'BOARD_CARDS', icon: 'pi-th-large', minW: 4, minH: 8, w: 8, h: 20 },
    { kind: 'GALLERY', icon: 'pi-images', minW: 3, minH: 6, w: 8, h: 8 },
    { kind: 'GROUPS', icon: 'pi-users', minW: 3, minH: 6, w: 4, h: 9 },
    { kind: 'GROUP_BUY', icon: 'pi-shopping-bag', minW: 3, minH: 8, w: 4, h: 12 },
    { kind: 'QUICK_LINKS', icon: 'pi-link', minW: 3, minH: 2, w: 12, h: 2 },
    { kind: 'NOTICE_TEXT', icon: 'pi-megaphone', minW: 2, minH: 3, w: 4, h: 6 },
];

/**
 * 고정 칸 — 넣기 목록에는 없고 그 레이아웃에 꼭 하나 있는 칸(게시판 레이아웃의 「게시판 본문」 `BOARD_BODY`, 2026-10-30). 옮기기·크기만 되고 빼기·⚙ 는 없다.
 * 최소 크기는 12칸 기준 폭 6 · 높이 20(본문은 내용대로 늘어난다).
 */
const FIXED_KINDS: readonly (Omit<HomeKindSpec, 'kind'> & { kind: string })[] = [{ kind: 'BOARD_BODY', icon: 'pi-file', minW: 6, minH: 20, w: 12, h: 20 }];

/** 모르는 종류(서버가 먼저 보낸 새 종류) */
const UNKNOWN: Omit<HomeKindSpec, 'kind'> = { icon: 'pi-box', minW: 2, minH: 2, w: 6, h: 8 };

function toItems(items: readonly HomeEditorItem[]) {
    return items.map((it) => ({ ...it, gadgetId: it.i }));
}

/** 위로 당긴다 — `first` 를 주면 그 가젯이 같은 행에서 먼저 놓인다(옮긴·키운 가젯이 이기고 부딪친 가젯이 밀린다) */
function settle(items: readonly HomeEditorItem[], first?: string): HomeEditorItem[] {
    const prepared = toItems(items).map((it) => (it.i === first ? { ...it, y: it.y - 0.25 } : it));
    return UtilHomeLayout.compact(prepared).map(({ gadgetId: _gadgetId, ...rest }) => rest);
}

/** 저장 실패(`400 VALIDATION`)의 칸 오류를 가젯별로 나눈 것 — `byGadget` 가젯 ID → (`gadgets[i].` 뒤의 칸 경로 → 문구) · `other` 가젯에 붙지 않는 문구(배치·적용 화면·한도) */
export interface HomeSaveErrors {
    byGadget: Record<string, Record<string, string>>;
    other: string[];
}

/** `gadgets[i].<칸>` — 서버 저장 오류 키 */
const GADGET_FIELD = /^gadgets\[(\d+)\]\.(.+)$/;

export default class UtilHomeLayoutEditor {
    /** 가젯 종류 8종 */
    static readonly KINDS: readonly HomeKindSpec[] = KINDS;

    /**
     * 종류의 편집기 값(모르는 종류는 기본 아이콘·작은 최소 크기)
     * @param {string} type - 종류 키.
     * @returns {Omit<HomeKindSpec, 'kind'>} 값.
     */
    static spec(type: string): Omit<HomeKindSpec, 'kind'> {
        return KINDS.find((k) => k.kind === type) ?? FIXED_KINDS.find((k) => k.kind === type) ?? UNKNOWN;
    }

    /**
     * 고정 칸인가(게시판 본문) — 빼기·⚙·두 번째 넣기가 없다
     * @param {string} type - 종류 키.
     * @returns {boolean} 고정 칸이면 true.
     */
    static isFixed(type: string): boolean {
        return FIXED_KINDS.some((k) => k.kind === type);
    }

    /**
     * 아는 종류인가
     * @param {string} type - 종류 키.
     * @returns {boolean} 8종 중 하나면 true.
     */
    static isKnown(type: string): type is HomeGadgetKind {
        return KINDS.some((k) => k.kind === type);
    }

    /**
     * 칸 수에 맞춘 최소 폭(24칸이면 2배)
     * @param {string} type - 종류 키.
     * @param {HomeGridColumns} columns - 칸 수.
     * @returns {number} 칸 수 기준 최소 폭.
     */
    static minW(type: string, columns: HomeGridColumns): number {
        return Math.min(columns, UtilHomeLayoutEditor.spec(type).minW * (columns / 12));
    }

    /**
     * 저장 모양 → 편집기 자리 — 종류별 최소 크기를 지키고 위로 당긴다
     * @param {HomeLayout} layout - 배치.
     * @param {(id: string) => string} typeOf - 가젯 ID → 종류.
     * @returns {HomeEditorItem[]} 편집기 자리.
     */
    static toEditor(layout: HomeLayout, typeOf: (id: string) => string): HomeEditorItem[] {
        const items = layout.items.map((it) => {
            const type = typeOf(it.gadgetId);
            const minW = UtilHomeLayoutEditor.minW(type, layout.columns);
            const minH = UtilHomeLayoutEditor.spec(type).minH;
            const w = Math.min(layout.columns, Math.max(minW, it.w));
            return { i: it.gadgetId, x: Math.min(it.x, layout.columns - w), y: it.y, w, h: Math.max(minH, it.h), minW, minH };
        });
        return settle(items);
    }

    /**
     * 편집기 자리 → 저장 모양
     * @param {HomeGridColumns} columns - 칸 수.
     * @param {readonly HomeEditorItem[]} items - 편집기 자리.
     * @returns {HomeLayout} 배치.
     */
    static toLayout(columns: HomeGridColumns, items: readonly HomeEditorItem[]): HomeLayout {
        return { columns, items: UtilHomeLayout.byReadingOrder(toItems(items)).map(({ gadgetId, x, y, w, h }) => ({ gadgetId, x, y, w, h })) };
    }

    /**
     * 칸 수 바꾸기 — 위치·폭을 2배/반으로(`UtilHomeLayout.convert`, 반올림 · 종류별 최소 폭)
     * @param {readonly HomeEditorItem[]} items - 지금 자리.
     * @param {HomeGridColumns} from - 지금 칸 수.
     * @param {HomeGridColumns} to - 바꿀 칸 수.
     * @param {(id: string) => string} typeOf - 가젯 ID → 종류.
     * @returns {HomeEditorItem[]} 새 자리.
     */
    static convert(items: readonly HomeEditorItem[], from: HomeGridColumns, to: HomeGridColumns, typeOf: (id: string) => string): HomeEditorItem[] {
        const converted = UtilHomeLayout.convert(UtilHomeLayoutEditor.toLayout(from, items), to, (id) => UtilHomeLayoutEditor.minW(typeOf(id), to));
        return UtilHomeLayoutEditor.toEditor(converted, typeOf);
    }

    /**
     * 위로 당기고 읽는 순서로 정렬한다 — 끌기·크기 바꾸기가 끝난 뒤(요소 순서 = 화면 읽기·Tab 순서)
     * @param {readonly HomeEditorItem[]} items - 지금 자리.
     * @returns {HomeEditorItem[]} 새 자리.
     */
    static normalize(items: readonly HomeEditorItem[]): HomeEditorItem[] {
        return settle(items);
    }

    /**
     * 위치·크기를 숫자로 정한다 — 범위 밖 값은 범위 안으로. `y` 는 「그 행에 있는 가젯 앞」으로 놓고 당긴다
     * @param {readonly HomeEditorItem[]} items - 지금 자리.
     * @param {string} id - 가젯 ID.
     * @param {Partial<Pick<HomeEditorItem, 'x' | 'y' | 'w' | 'h'>>} box - 바꿀 값.
     * @param {HomeGridColumns} columns - 칸 수.
     * @returns {HomeEditorItem[]} 새 자리.
     */
    static setBox(items: readonly HomeEditorItem[], id: string, box: Partial<Pick<HomeEditorItem, 'x' | 'y' | 'w' | 'h'>>, columns: HomeGridColumns): HomeEditorItem[] {
        const me = items.find((it) => it.i === id);
        if (!me) return [...items];
        const w = Math.min(columns, Math.max(me.minW, Math.round(box.w ?? me.w)));
        const x = Math.min(columns - w, Math.max(0, Math.round(box.x ?? me.x)));
        const h = Math.min(UtilHomeLayout.MAX_H, Math.max(me.minH, Math.round(box.h ?? me.h)));
        const y = box.y === undefined ? me.y : Math.max(0, Math.round(box.y)) - 0.5;
        return settle(
            items.map((it) => (it.i === id ? { ...it, x, y, w, h } : it)),
            box.y === undefined ? id : undefined,
        );
    }

    /**
     * 가젯을 맨 아래에 넣는다(종류의 기본 크기)
     * @param {readonly HomeEditorItem[]} items - 지금 자리.
     * @param {string} id - 새 가젯 ID.
     * @param {string} type - 종류 키.
     * @param {HomeGridColumns} columns - 칸 수.
     * @returns {HomeEditorItem[]} 새 자리.
     */
    static add(items: readonly HomeEditorItem[], id: string, type: string, columns: HomeGridColumns): HomeEditorItem[] {
        const spec = UtilHomeLayoutEditor.spec(type);
        const minW = UtilHomeLayoutEditor.minW(type, columns);
        const w = Math.min(columns, Math.max(minW, spec.w * (columns / 12)));
        return [...items, { i: id, x: 0, y: UtilHomeLayout.bottom(items), w, h: spec.h, minW, minH: spec.minH }];
    }

    /**
     * 두 배치가 같은가(가젯 순서 무관) — 저장하지 않은 변경 표시
     * @param {HomeLayout} a - 배치.
     * @param {HomeLayout} b - 배치.
     * @returns {boolean} 같으면 true.
     */
    static same(a: HomeLayout, b: HomeLayout): boolean {
        if (a.columns !== b.columns || a.items.length !== b.items.length) return false;
        const key = (l: HomeLayout) =>
            [...l.items]
                .map((it) => `${it.gadgetId}:${it.x},${it.y},${it.w},${it.h}`)
                .sort()
                .join('|');
        return key(a) === key(b);
    }

    /**
     * 저장 실패의 칸 오류(`ApiError.fields`)를 가젯별로 나눈다 — `gadgets[i].title` 은 보낸 차례 `i` 의 가젯 ID 로, 나머지(`layout.items` · `screens.list` · `gadgets` 등)와
     * ⚙ 가 없는 가젯(`settable` 이 거짓 — 게시판 본문)의 오류는 `other` 로.
     * @param {Record<string, string> | undefined} fields - 칸 경로 → 문구.
     * @param {readonly string[]} ids - 보낸 가젯 ID(보낸 차례).
     * @param {(id: string) => boolean} settable - ⚙ 설정 창을 열 수 있는 가젯인가.
     * @returns {HomeSaveErrors} 나눈 오류.
     */
    static saveErrors(fields: Record<string, string> | undefined, ids: readonly string[], settable: (id: string) => boolean): HomeSaveErrors {
        const out: HomeSaveErrors = { byGadget: {}, other: [] };
        for (const [key, message] of Object.entries(fields ?? {})) {
            const m = GADGET_FIELD.exec(key);
            const id = m ? ids[Number(m[1])] : undefined;
            if (m && id && settable(id)) (out.byGadget[id] ??= {})[m[2]!] = message;
            else if (!out.other.includes(message)) out.other.push(message);
        }
        return out;
    }
}
