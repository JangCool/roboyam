import type { HomeGridColumns, HomeLayoutItem } from '#layers/home-layout/utils/util-home-layout';
import UtilHomeLayout from '#layers/home-layout/utils/util-home-layout';

/**
 * 공개 홈 격자(`GadgetGrid`)의 순수 계산 — 운영자가 정한 배치(`HomeLayout`, 공유 레이어 `UtilHomeLayout`)를 이 화면 폭에 맞춰 그릴 자리로 바꾼다. 상태도 요청도 없다.
 *
 *  - **모양(mode)**: `full` 운영자 배치 그대로(데스크톱 lg 이상 + 홈 폭 52rem 이상) · `half` 태블릿 파생 배치(칸 수 반 — md 이상, 또는 데스크톱이지만 사이드 메뉴 때문에 홈 폭이 좁을 때) · `stack` 한 열((y, x) 순서 — md 미만, 또는 홈 폭 36rem 미만).
 *  - **px 배치**: 상자 높이 = `boxHeight`, 위 끝 = 읽는 순서에서 앞선 가젯 중 열이 겹치는 것들의 아래 끝 + 16px(`place`). 숨긴 가젯의 자리는 저절로 메워진다.
 *  - **같은 줄 맞추기**: 같은 y·같은 h 로 나란히 둔 가젯은 상자 높이 중 가장 큰 값으로 함께 맞춘다.
 *    (격자는 칸 높이가 아니라 카드 내용 높이를 재므로, 그 줄에서 가장 긴 가젯의 내용이 줄면 그 줄도 다시 준다)
 */

/** 홈 격자를 그리는 모양 */
export type GadgetGridMode = 'full' | 'half' | 'stack';

/** 그릴 자리 한 개 — `x`·`y`·`w`·`h` 는 운영자 자리(행·칸 — 끌기·순서), `top`·`height` 는 그릴 위치·높이(px), `aligned` 는 같은 줄 맞추기로 함께 늘린 가젯인가 */
export interface GadgetGridCell extends HomeLayoutItem {
    top: number;
    height: number;
    aligned?: boolean;
}

function overlapsX(a: { x: number; w: number }, b: { x: number; w: number }): boolean {
    return a.x < b.x + b.w && b.x < a.x + a.w;
}

/** 편집 꼬리표의 크기 알림 — h < need 면 `small`, h > max(need, minH) 면 `large` */
export interface GadgetGridSizeHint {
    kind: 'small' | 'large';
    need: number;
    /** 줄여도 되는 행 수(`small` 은 0) */
    spare: number;
}

/** 운영자 배치 그대로 그릴 홈 폭(px) — 52rem. 그보다 좁으면(사이드 메뉴 + 좁은 창) 태블릿 파생 배치 */
const FULL_MIN_PX = 832;
/** 격자로 그릴 가장 좁은 홈 폭(px) — 36rem. 그보다 좁으면 한 열 */
const HALF_MIN_PX = 576;

export default class UtilGadgetGrid {
    /** 태블릿 이상(Tailwind `md`, 768px) — 그보다 좁으면 한 열 */
    static readonly MD_QUERY = '(min-width: 768px)';

    /**
     * 그릴 모양을 고른다
     * @param {{ wide: boolean; md: boolean; width: number }} view - 데스크톱 폭(lg)인가 · 태블릿 폭(md) 이상인가 · 홈 폭(px, 아직 모르면 0 — 창 폭으로만 고른다).
     * @returns {GadgetGridMode} 모양.
     */
    static mode(view: { wide: boolean; md: boolean; width: number }): GadgetGridMode {
        if (!view.md) return 'stack';
        if (view.width > 0 && view.width < HALF_MIN_PX) return 'stack';
        if (view.wide && (view.width === 0 || view.width >= FULL_MIN_PX)) return 'full';
        return 'half';
    }

    /**
     * 태블릿 파생 배치 — 칸 수를 반으로 줄이고 읽는 순서((y, x))로 다시 놓는다. 반 폭 이하인 가젯이 둘 이어지면 한 줄에 반씩 나란히, 나머지는 전체 폭.
     * (운영자 배치의 비율을 그대로 반으로 줄이면 좁은 폭에서 오른쪽 열이 너무 좁아진다 — 파이언월드 홈의 태블릿 축소와 같은 생각)
     * @param {readonly HomeLayoutItem[]} items - 보이는 가젯의 자리(운영자 배치).
     * @param {HomeGridColumns} columns - 운영자 배치의 칸 수.
     * @returns {HomeLayoutItem[]} 칸 수 `columns / 2` 기준의 새 자리(`y` 는 순서 값 — `place` 가 당긴다).
     */
    static half(items: readonly HomeLayoutItem[], columns: HomeGridColumns): HomeLayoutItem[] {
        const cols = columns / 2;
        const halfW = cols / 2;
        const narrow = (it: HomeLayoutItem) => it.w * 2 <= columns;
        const order = UtilHomeLayout.byReadingOrder(items);
        const out: HomeLayoutItem[] = [];
        let row = 0;
        for (let i = 0; i < order.length; i++) {
            const cur = order[i]!;
            const next = order[i + 1];
            if (narrow(cur) && next && narrow(next)) {
                out.push({ ...cur, x: 0, y: row, w: halfW }, { ...next, x: halfW, y: row, w: halfW });
                i++;
            } else {
                out.push({ ...cur, x: 0, y: row, w: cols });
            }
            row++;
        }
        return out;
    }

    /**
     * 한 열 순서 「본문 열 먼저」(템플릿 한 열 모양 `mobile.order: 'columns'` — 아이로빗) — 어떤 가젯도 가로지르지 않는 칸 경계로 배치를 세로 열들로 나누고,
     * 왼쪽 열부터 열마다 (y, x) 순서로 잇는다. 나눌 경계가 없으면(전체 폭 가젯이 있는 등) (y, x) 순서 그대로다.
     * 예) 아이로빗 목업 = 본문 17칸 + 오른쪽 7칸 → 하이라이트 · 인기글 · (모바일 전용) 공동구매 · 방금 올라온 글 · 영상·짤 → 인기 소모임.
     * @param {readonly T[]} items - 보이는 가젯의 자리(운영자 배치).
     * @param {number} columns - 운영자 배치의 칸 수.
     * @returns {T[]} 한 열 순서로 정렬한 새 배열.
     */
    static byColumns<T extends HomeLayoutItem>(items: readonly T[], columns: number): T[] {
        const cuts: number[] = [];
        for (let b = 1; b < columns; b++) {
            if (items.every((it) => it.x >= b || it.x + it.w <= b)) cuts.push(b);
        }
        const order = UtilHomeLayout.byReadingOrder(items);
        if (cuts.length === 0) return order;
        const lane = (it: T) => cuts.filter((c) => it.x >= c).length;
        const rank = new Map(order.map((it, i) => [it.gadgetId, i]));
        return order.sort((a, b) => lane(a) - lane(b) || rank.get(a.gadgetId)! - rank.get(b.gadgetId)!);
    }

    /**
     * 한 열 순서 「옆 열 끼워 넣기」(템플릿 한 열 모양 `mobile.order: 'beside'` — 아이로빗, 2026-11-06 사용자) — 열 나누기는 `byColumns` 와 같고,
     * 오른쪽 열의 가젯은 본문 열(맨 왼쪽 열)이 그 가젯의 아래 끝까지 내려온 자리에 끼운다 = 본문 열에서 위 끝(y)이 그 가젯의 아래 끝(y + h) 이하인 마지막 가젯 바로 뒤
     * (그런 가젯이 없으면 맨 앞). 같은 자리에 여럿이면 (y, x) 순서. 나눌 경계가 없으면 (y, x) 순서 그대로다.
     * 예) 아이로빗 = 하이라이트 · 방금 올라온 글 · 인기글(데스크톱에서 방금 올라온 글 옆까지 내려온다) · 팁과강좌 · 사용기 · 영상·짤 · 인기 소모임.
     * @param {readonly T[]} items - 보이는 가젯의 자리(운영자 배치).
     * @param {number} columns - 운영자 배치의 칸 수.
     * @returns {T[]} 한 열 순서로 정렬한 새 배열.
     */
    static besideColumns<T extends HomeLayoutItem>(items: readonly T[], columns: number): T[] {
        const lanes = UtilGadgetGrid.byColumns(items, columns);
        const cut = Math.min(...Array.from({ length: columns - 1 }, (_, i) => i + 1).filter((b) => items.every((it) => it.x >= b || it.x + it.w <= b)), Number.POSITIVE_INFINITY);
        if (!Number.isFinite(cut)) return lanes;
        const main = lanes.filter((it) => it.x + it.w <= cut);
        const out: T[] = [...main];
        for (const side of lanes.filter((it) => it.x >= cut)) {
            const bottom = side.y + side.h;
            let anchor = -1;
            main.forEach((it, i) => {
                if (it.y <= bottom) anchor = i;
            });
            // 앞서 같은 자리에 끼운 옆 가젯 뒤로(차례 유지)
            const after = anchor < 0 ? -1 : out.indexOf(main[anchor]!);
            let at = after + 1;
            while (at < out.length && !main.includes(out[at]!)) at++;
            out.splice(at, 0, side);
        }
        return out;
    }

    /**
     * 게시판 레이아웃의 한 열 순서 — 본문(`bodyId`)을 먼저 둔다(사용자 결정 2026-10-30: 모바일은 본문 먼저, 그 다음 가젯).
     * 단, 본문 위에 통째로 놓인 가젯(아래 끝 ≤ 본문 위 끝 — 본문 위의 전체 폭 띠 등)은 본문 위 그대로 둔다. 나머지 차례는 받은 차례(템플릿의 한 열 순서) 그대로.
     * 예) 추천 배치(본문 17 + 오른쪽 열 7) → 본문 → 이 게시판 인기글 → 공동구매 → 인기 소모임 / 본문 위에 바로가기 띠 → 바로가기 → 본문 → ….
     * @param {readonly T[]} order - 한 열 순서(`byColumns` 또는 (y, x)).
     * @param {string} bodyId - 본문 가젯 ID.
     * @returns {T[]} 새 배열(본문이 없으면 그대로).
     */
    static bodyFirst<T extends HomeLayoutItem>(order: readonly T[], bodyId: string): T[] {
        const body = order.find((it) => it.gadgetId === bodyId);
        if (!body) return [...order];
        const above = order.filter((it) => it !== body && it.y + it.h <= body.y);
        const rest = order.filter((it) => it !== body && !above.includes(it));
        return [...above, body, ...rest];
    }

    /**
     * 편집 꼬리표의 크기 알림 — 운영자 h 와 최적 칸 수(`needRows`)를 견준다.
     * @param {number} h - 운영자 h(끄는 중이면 놓일 h).
     * @param {number | undefined} contentPx - 잰 내용 높이(px). 아직 못 쟀으면 undefined.
     * @param {number} [minH] - 종류별 최소 높이(행). 모르면 1.
     * @returns {GadgetGridSizeHint | null} 알림(같거나 못 쟀으면 null).
     */
    static sizeHint(h: number, contentPx: number | undefined, minH = 1): GadgetGridSizeHint | null {
        if (contentPx === undefined || !(contentPx > 0)) return null;
        const need = UtilGadgetGrid.needRows(contentPx);
        if (h < need) return { kind: 'small', need, spare: 0 };
        const spare = h - Math.max(need, minH);
        if (spare > 0) return { kind: 'large', need, spare };
        return null;
    }

    /**
     * 최적 칸 수 — hPx 가 contentPx 이상이 되는 가장 작은 h. 경계에서 칸 수가 흔들리지 않게 contentPx 를 반올림한 뒤 견준다.
     * @param {number} contentPx - 잰 내용 높이(px — 내용 + 테마 위·아래 여백).
     * @returns {number} 행 수.
     */
    static needRows(contentPx: number): number {
        const unit = UtilHomeLayout.ROW_HEIGHT + UtilHomeLayout.GAP;
        return Math.max(1, Math.ceil((Math.round(contentPx) + UtilHomeLayout.GAP) / unit));
    }

    /**
     * 상자 높이(px) — hPx − contentPx 가 한 칸(31px) 이상이면 운영자가 일부러 키운 것이라 hPx, 아니면 contentPx(한 칸 미만은 칸 단위 반올림일 뿐).
     * @param {number} h - 운영자 h(행).
     * @param {number} contentPx - 잰 내용 높이(px).
     * @returns {number} px.
     */
    static boxHeight(h: number, contentPx: number): number {
        const hPx = UtilHomeLayout.px(h);
        const content = Math.round(contentPx);
        return hPx - content >= UtilHomeLayout.ROW_HEIGHT + UtilHomeLayout.GAP ? hPx : content;
    }

    /**
     * 그릴 자리(px) — 아직 못 잰 가젯은 hPx. 결과 순서가 읽는 순서(화면 읽기·Tab 순서)다.
     * @param {readonly HomeLayoutItem[]} items - 자리.
     * @param {Readonly<Record<string, number>>} heights - 가젯 ID → contentPx.
     * @param {(gadgetId: string) => boolean} [contentOnly] - 내용 높이만 쓰는 칸(게시판 본문).
     * @returns {GadgetGridCell[]} 그릴 자리.
     */
    static place(items: readonly HomeLayoutItem[], heights: Readonly<Record<string, number>>, contentOnly?: (gadgetId: string) => boolean): GadgetGridCell[] {
        const own = (it: HomeLayoutItem) => {
            const content = heights[it.gadgetId];
            if (content === undefined) return UtilHomeLayout.px(it.h);
            return contentOnly?.(it.gadgetId) ? content : UtilGadgetGrid.boxHeight(it.h, content);
        };
        const rowKey = (it: HomeLayoutItem) => `${it.y}:${it.h}`;
        const groups = new Map<string, { height: number; size: number }>();
        for (const it of items) {
            const g = groups.get(rowKey(it)) ?? { height: 0, size: 0 };
            groups.set(rowKey(it), { height: Math.max(g.height, own(it)), size: g.size + 1 });
        }
        const placed: GadgetGridCell[] = [];
        for (const it of UtilHomeLayout.byReadingOrder(items)) {
            const group = groups.get(rowKey(it))!;
            const aligned = group.size > 1;
            let top = 0;
            for (const p of placed) if (overlapsX(p, it)) top = Math.max(top, p.top + p.height + UtilHomeLayout.GAP);
            placed.push({ ...it, top, height: aligned ? group.height : own(it), aligned });
        }
        return placed;
    }

    /**
     * 격자 전체 높이(px) — 가장 아래 가젯의 아래 끝
     * @param {readonly GadgetGridCell[]} cells - 그릴 자리.
     * @returns {number} px.
     */
    static totalHeight(cells: readonly GadgetGridCell[]): number {
        return cells.reduce((max, c) => Math.max(max, c.top + c.height), 0);
    }

    /**
     * 끌어 놓은 위치(px) → 운영자 행(y) — 놓은 위 끝 이하의 가장 아래 겹치는 가젯의 위쪽 절반이면 그 y, 아래쪽 절반이면 y + h. 없으면 0.
     * @param {readonly GadgetGridCell[]} cells - 그릴 자리.
     * @param {{ id: string; x: number; w: number; top: number }} drop - 끄는 가젯 · 놓일 칸 · 놓일 위 끝(px).
     * @returns {number} 행.
     */
    static rowAt(cells: readonly GadgetGridCell[], drop: { id: string; x: number; w: number; top: number }): number {
        let hit: GadgetGridCell | null = null;
        for (const c of cells) {
            if (c.gadgetId === drop.id || !overlapsX(c, drop) || c.top > drop.top) continue;
            if (!hit || c.top > hit.top) hit = c;
        }
        if (!hit) return 0;
        return drop.top < hit.top + hit.height / 2 ? hit.y : hit.y + hit.h;
    }
}
