import type { HomeGridColumns, HomeLayoutItem } from '#layers/home-layout/utils/util-home-layout';
import UtilHomeLayout from '#layers/home-layout/utils/util-home-layout';

/**
 * 공개 홈 격자(`HomeGadgetGrid`)의 순수 계산 — 운영자가 정한 배치(`HomeLayout`, 공유 레이어 `UtilHomeLayout`)를 이 화면 폭에 맞춰 그릴 자리로 바꾼다. 상태도 요청도 없다.
 *
 *  - **모양(mode)**: `full` 운영자 배치 그대로(데스크톱 lg 이상 + 홈 폭 52rem 이상) · `half` 태블릿 파생 배치(칸 수 반 — md 이상, 또는 데스크톱이지만 사이드 메뉴 때문에 홈 폭이 좁을 때) · `stack` 한 열((y, x) 순서 — md 미만, 또는 홈 폭 36rem 미만).
 *  - **h 는 최소 높이**: 가젯 실제 높이(잰 값)가 h 행보다 크면 그만큼 행을 늘리고(`place`), 위로 당기기(`UtilHomeLayout.compact`)로 **가로로 겹치는 아래 가젯만** 밀어 겹치지 않게 한다(다른 열은 그대로).
 *    숨긴 가젯(이 폭에서 안 보임·비어서 감춤)의 자리도 같은 당기기로 메운다.
 *  - **같은 줄 맞추기**: 같은 행(y)에서 같은 높이(h)로 시작하는 가젯들은 그중 가장 큰 높이로 함께 늘린다 — 운영자가 나란히 맞춘 줄은 내용 길이가 달라도 아래 끝이 맞는다.
 *    (격자는 칸 높이가 아니라 카드 내용 높이를 재므로, 그 줄에서 가장 긴 가젯의 내용이 줄면 그 줄도 다시 준다)
 */

/** 홈 격자를 그리는 모양 */
export type HomeGridMode = 'full' | 'half' | 'stack';

/** 그릴 자리 한 개 — `h` 는 운영자가 정한 최소 높이(행), `rows` 는 실제로 차지하는 행(`h` 이상), `aligned` 는 같은 줄 맞추기로 함께 늘린 가젯인가(그 높이를 채운다) */
export interface HomeGridCell extends HomeLayoutItem {
    rows: number;
    aligned?: boolean;
}

/** 운영자 배치 그대로 그릴 홈 폭(px) — 52rem. 그보다 좁으면(사이드 메뉴 + 좁은 창) 태블릿 파생 배치 */
const FULL_MIN_PX = 832;
/** 격자로 그릴 가장 좁은 홈 폭(px) — 36rem. 그보다 좁으면 한 열 */
const HALF_MIN_PX = 576;

export default class UtilHomeGrid {
    /** 태블릿 이상(Tailwind `md`, 768px) — 그보다 좁으면 한 열 */
    static readonly MD_QUERY = '(min-width: 768px)';

    /**
     * 그릴 모양을 고른다
     * @param {{ wide: boolean; md: boolean; width: number }} view - 데스크톱 폭(lg)인가 · 태블릿 폭(md) 이상인가 · 홈 폭(px, 아직 모르면 0 — 창 폭으로만 고른다).
     * @returns {HomeGridMode} 모양.
     */
    static mode(view: { wide: boolean; md: boolean; width: number }): HomeGridMode {
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
     * 한 열 순서 「본문 열 먼저」(템플릿 한 열 모양 `mobile.order: 'columns'` — 로보얌) — 어떤 가젯도 가로지르지 않는 칸 경계로 배치를 세로 열들로 나누고,
     * 왼쪽 열부터 열마다 (y, x) 순서로 잇는다. 나눌 경계가 없으면(전체 폭 가젯이 있는 등) (y, x) 순서 그대로다.
     * 예) 로보얌 목업 = 본문 17칸 + 오른쪽 7칸 → 하이라이트 · 인기글 · (모바일 전용) 공동구매 · 영상·짤 · 방금 올라온 글 → 인기 소모임.
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
     * 그릴 자리 — 잰 높이로 행을 늘리고(`h` 보다 작아지지 않는다) 위로 당긴다. 결과 순서가 읽는 순서(화면 읽기·Tab 순서)다.
     * @param {readonly HomeLayoutItem[]} items - 자리.
     * @param {Readonly<Record<string, number>>} heights - 가젯 ID → 잰 높이(px). 아직 못 잰 가젯은 `h` 그대로.
     * @returns {HomeGridCell[]} 그릴 자리.
     */
    static place(items: readonly HomeLayoutItem[], heights: Readonly<Record<string, number>>): HomeGridCell[] {
        const measuredRows = (it: HomeLayoutItem) => {
            const measured = heights[it.gadgetId];
            return measured === undefined ? it.h : Math.max(it.h, UtilHomeLayout.rowsFor(measured));
        };
        // 같은 줄 맞추기 — 운영자가 같은 행(y)에 같은 높이(h)로 나란히 둔 가젯은 그중 가장 큰 높이로 함께 늘려 아래 끝을 맞춘다
        const rowKey = (it: HomeLayoutItem) => `${it.y}:${it.h}`;
        const groupRows = new Map<string, { rows: number; size: number }>();
        for (const it of items) {
            const g = groupRows.get(rowKey(it)) ?? { rows: 0, size: 0 };
            groupRows.set(rowKey(it), { rows: Math.max(g.rows, measuredRows(it)), size: g.size + 1 });
        }
        const grown = items.map((it) => {
            const group = groupRows.get(rowKey(it))!;
            const aligned = group.size > 1;
            // 당기기는 `h` 를 차지하는 행으로 본다 — 늘린 행으로 당긴 뒤 원래 `h` 를 되돌린다
            return { ...it, h: aligned ? group.rows : measuredRows(it), minH: it.h, aligned };
        });
        return UtilHomeLayout.compact(grown).map(({ minH, h, ...rest }) => ({ ...rest, h: minH, rows: h }));
    }
}
