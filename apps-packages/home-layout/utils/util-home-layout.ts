/**
 * 홈 가젯 배치(사용자 결정 2026-10-28) — **테마마다 따로** 저장하는 「격자 칸 수 + 가젯마다 위치·크기」. 홈 템플릿 = 테마라 배치도 테마가 주인이다.
 * user-web 공개 홈(`HomeGadgetGrid` — 라이브러리 없이 CSS grid)과 admin-web 홈 배치 편집기(`grid-layout-plus`)가 같은 값·같은 계산을 쓴다.
 *
 *  - 격자: 칸 수 `columns` 는 12 또는 24(운영자가 고른다). 행 높이 15px · 간격 16px(가로·세로, 두 칸 수 모두 같다) — 파이언월드 홈과 같은 값.
 *    간격이 같으면 12칸 ↔ 24칸 변환(x·w 2배/반)의 화면 위치가 픽셀까지 같다(그래서 24칸에서도 간격을 줄이지 않는다).
 *  - 가젯 하나 = `{ gadgetId, x, y, w, h }`(칸 단위, 0부터). `h` 는 **최소 높이**다 — 공개 홈은 내용이 길면 잘라내지 않고 늘리고, 같은 열 아래 가젯을 밀어 겹치지 않게 한다.
 *  - 배치는 늘 위로 당겨진(빈 줄 없는) 모양이다 — 편집기가 위로 당기며(`verticalCompact`) 저장하고, 공개 홈도 숨긴 가젯·늘어난 높이를 반영해 같은 규칙(`compact`)으로 다시 당긴다.
 *
 * 순수 함수만 둔다(Vue·Nuxt·서버 호출 없음 — `layers.md`). 한 앱만 쓰는 계산(공개 홈의 태블릿 파생 배치, 편집기의 종류별 최소 크기)은 그 앱에 둔다.
 */

/** 격자 칸 수 — 운영자가 고른다 */
export type HomeGridColumns = 12 | 24;

/** 가젯 한 개의 자리(칸 단위, 0부터) */
export interface HomeLayoutItem {
    /** 가젯 ID(문자열 Snowflake) */
    gadgetId: string;
    /** 왼쪽 칸(0 ~ columns - w) */
    x: number;
    /** 위 행(0부터) */
    y: number;
    /** 폭(칸 수, 1 ~ columns) */
    w: number;
    /** 최소 높이(행 수) — 내용이 길면 공개 홈이 늘린다 */
    h: number;
}

/** 테마 하나의 홈 배치 */
export interface HomeLayout {
    columns: HomeGridColumns;
    items: HomeLayoutItem[];
}

const COLUMN_CHOICES: readonly HomeGridColumns[] = [12, 24];
const ROW_HEIGHT = 15;
const GAP = 16;
const MAX_Y = 2000;
const MAX_H = 200;
/** 배치에 자리가 없는 가젯을 맨 아래에 붙일 때의 높이(행) */
const FALLBACK_H = 8;

function int(value: unknown, fallback: number, min: number, max: number): number {
    const n = typeof value === 'number' && Number.isFinite(value) ? Math.round(value) : fallback;
    return Math.min(max, Math.max(min, n));
}

function overlapsX(a: { x: number; w: number }, b: { x: number; w: number }): boolean {
    return a.x < b.x + b.w && b.x < a.x + a.w;
}

export default class UtilHomeLayout {
    /** 고를 수 있는 칸 수(보일 순서) */
    static readonly COLUMN_CHOICES: readonly HomeGridColumns[] = COLUMN_CHOICES;

    /** 저장한 적 없을 때·모르는 값일 때의 칸 수 */
    static readonly DEFAULT_COLUMNS: HomeGridColumns = 12;

    /** 행 높이(px) — 고정값 */
    static readonly ROW_HEIGHT = ROW_HEIGHT;

    /** 칸 사이 간격(px, 가로·세로) — 고정값 */
    static readonly GAP = GAP;

    /** 높이 상한(행) */
    static readonly MAX_H = MAX_H;

    /**
     * 칸 수로 읽을 수 있는 값인가
     * @param {unknown} value - 값.
     * @returns {boolean} 12 또는 24 이면 true.
     */
    static isColumns(value: unknown): value is HomeGridColumns {
        return value === 12 || value === 24;
    }

    /**
     * 행 수 → 높이(px) — `h` 행이 차지하는 높이(사이 간격 포함)
     * @param {number} h - 행 수.
     * @returns {number} px.
     */
    static px(h: number): number {
        const rows = Math.max(1, h);
        return rows * ROW_HEIGHT + (rows - 1) * GAP;
    }

    /**
     * 높이(px) → 그 높이를 담는 가장 적은 행 수
     * @param {number} px - 높이.
     * @returns {number} 행 수(1 이상).
     */
    static rowsFor(px: number): number {
        if (!Number.isFinite(px) || px <= 0) return 1;
        // 소수점 픽셀(1px 남짓)로 한 행이 더 붙지 않게 조금 봐준다
        return Math.max(1, Math.ceil((px + GAP) / (ROW_HEIGHT + GAP) - 0.05));
    }

    /**
     * 서버 배치를 읽는다 — 모르는 칸 수는 12, 모르는·겹친 가젯 ID 는 버리고, 범위 밖 값은 범위 안으로. 배치에 자리가 없는 가젯은 맨 아래에 전체 폭으로 붙인다(가젯이 사라지지 않게).
     * 위로 당기지는 않는다(보는 쪽이 숨김·높이를 반영해 `compact` 한다).
     * @param {unknown} raw - 서버 값(`{ columns, items }`).
     * @param {readonly string[]} gadgetIds - 응답에 있는 가젯 ID(정의 순서).
     * @returns {HomeLayout} 읽은 배치.
     */
    static read(raw: unknown, gadgetIds: readonly string[]): HomeLayout {
        const r = raw !== null && typeof raw === 'object' ? (raw as Record<string, unknown>) : {};
        const columns: HomeGridColumns = UtilHomeLayout.isColumns(r.columns) ? r.columns : UtilHomeLayout.DEFAULT_COLUMNS;
        const known = new Set(gadgetIds);
        const seen = new Set<string>();
        const items: HomeLayoutItem[] = [];
        for (const rawItem of Array.isArray(r.items) ? r.items : []) {
            const it = rawItem !== null && typeof rawItem === 'object' ? (rawItem as Record<string, unknown>) : {};
            const id = typeof it.gadgetId === 'string' ? it.gadgetId : typeof it.gadgetId === 'number' ? String(it.gadgetId) : '';
            if (!known.has(id) || seen.has(id)) continue;
            seen.add(id);
            const w = int(it.w, columns, 1, columns);
            items.push({ gadgetId: id, x: int(it.x, 0, 0, columns - w), y: int(it.y, 0, 0, MAX_Y), w, h: int(it.h, FALLBACK_H, 1, MAX_H) });
        }
        let bottom = UtilHomeLayout.bottom(items);
        for (const id of gadgetIds) {
            if (seen.has(id)) continue;
            seen.add(id);
            items.push({ gadgetId: id, x: 0, y: bottom, w: columns, h: FALLBACK_H });
            bottom += FALLBACK_H;
        }
        return { columns, items };
    }

    /**
     * 가장 아래 가젯의 아래 끝(행)
     * @param {readonly HomeLayoutItem[]} items - 자리들.
     * @returns {number} 행(가젯이 없으면 0).
     */
    static bottom(items: readonly { y: number; h: number }[]): number {
        return items.reduce((max, it) => Math.max(max, it.y + it.h), 0);
    }

    /**
     * 읽는 순서(위 → 아래, 같은 행이면 왼쪽 → 오른쪽) — 모바일 한 열·화면 읽기·Tab 순서
     * @param {readonly T[]} items - 자리들.
     * @returns {T[]} 정렬한 새 배열.
     */
    static byReadingOrder<T extends { gadgetId: string; x: number; y: number }>(items: readonly T[]): T[] {
        return [...items].sort((a, b) => a.y - b.y || a.x - b.x || (a.gadgetId < b.gadgetId ? -1 : a.gadgetId > b.gadgetId ? 1 : 0));
    }

    /**
     * 위로 당긴다(편집기의 `verticalCompact` 와 같은 규칙) — 읽는 순서대로, 가로로 겹치는 앞 가젯들의 아래 끝 바로 밑에 놓는다. 겹친 자리도 이것으로 풀린다.
     * @param {readonly T[]} items - 자리들(`h` 는 그 자리가 실제로 차지할 행 수).
     * @returns {T[]} 읽는 순서로 정렬된, `y` 를 고친 새 배열.
     */
    static compact<T extends HomeLayoutItem>(items: readonly T[]): T[] {
        const placed: T[] = [];
        for (const it of UtilHomeLayout.byReadingOrder(items)) {
            let y = 0;
            for (const p of placed) if (overlapsX(p, it)) y = Math.max(y, p.y + p.h);
            placed.push({ ...it, y });
        }
        return placed;
    }

    /**
     * 칸 수를 바꾼다 — 12 → 24 는 x·w 를 2배(그대로 같은 자리), 24 → 12 는 왼쪽 끝·오른쪽 끝을 각각 반으로 반올림(나란한 가젯이 겹치지 않게)하고 최소 폭을 지킨다. `y`·`h` 는 그대로 두고 마지막에 위로 당긴다.
     * @param {HomeLayout} layout - 지금 배치.
     * @param {HomeGridColumns} to - 바꿀 칸 수.
     * @param {(gadgetId: string) => number} [minW] - 가젯별 최소 폭(바꿀 칸 수 기준). 없으면 1.
     * @returns {HomeLayout} 새 배치.
     */
    static convert(layout: HomeLayout, to: HomeGridColumns, minW?: (gadgetId: string) => number): HomeLayout {
        if (layout.columns === to) return { columns: to, items: layout.items.map((it) => ({ ...it })) };
        const items = layout.items.map((it) => {
            let x: number;
            let w: number;
            if (to > layout.columns) {
                x = it.x * 2;
                w = it.w * 2;
            } else {
                x = Math.round(it.x / 2);
                w = Math.round((it.x + it.w) / 2) - x;
            }
            w = Math.min(to, Math.max(1, minW ? minW(it.gadgetId) : 1, w));
            x = Math.min(Math.max(0, x), to - w);
            return { ...it, x, w };
        });
        return { columns: to, items: UtilHomeLayout.compact(items) };
    }
}
