import type { HomeLayoutItem } from './util-home-layout';
import UtilHomeLayout from './util-home-layout';

/**
 * 모바일 한 열 순서(가젯 배치의 `stack` 모양) — user-web 공개 격자(`GadgetGrid`)가 그리는 차례와 admin-web 레이아웃 편집기의 「모바일 순서」 목록이 같은 계산을 쓴다.
 * 순수 함수만 둔다(Vue·Nuxt·서버 호출 없음 — `layers.md`).
 *
 *  1. 계산 순서 — 템플릿 한 열 모양(`HomeStackRule`): `reading` (y, x) · `columns` 본문 열 먼저(`byColumns`) · `beside` 오른쪽 열을 본문 열 사이에 끼워 넣기(`besideColumns`).
 *     게시판 레이아웃이면 그 위에 본문 먼저(`bodyFirst`).
 *  2. 운영자가 정한 모바일 순서(`HomeLayout.mobileOrder`, home-news-contract 「모바일 순서」 2026-11-15)가 있으면 목록에 있는 가젯을 그 차례로 앞에,
 *     목록에 없는 가젯(새로 넣은 가젯 등)은 1 의 차례대로 그 뒤에(`byMobileOrder`). 목록이 null·비었으면 1 그대로.
 */

/** 템플릿의 한 열 모양 — user-web 테마 홈 템플릿의 `provideGadgetLook(…, { mobile: { order } })` */
export type HomeStackRule = 'reading' | 'columns' | 'beside';

/**
 * 테마 → 한 열 모양. **user-web 테마 홈 템플릿의 `mobile.order` 와 같게 둔다**(기본형·다음식·네이버식 `columns` · 아이로빗 `beside`).
 * 모르는 테마는 user-web 이 기본 테마의 홈 템플릿으로 그리므로(`UtilTheme.resolve`) 기본형과 같은 `columns`.
 */
const THEME_RULES: Readonly<Record<string, HomeStackRule>> = { default: 'columns', daum: 'columns', naver: 'columns', irobit: 'beside' };

/** 어떤 가젯도 가로지르지 않는 칸 경계(1 ~ columns - 1) */
function cutsOf(items: readonly HomeLayoutItem[], columns: number): number[] {
    const cuts: number[] = [];
    for (let b = 1; b < columns; b++) {
        if (items.every((it) => it.x >= b || it.x + it.w <= b)) cuts.push(b);
    }
    return cuts;
}

export default class UtilHomeStack {
    /**
     * 테마의 한 열 모양 — 관리자 편집기가 「PC 배치를 따를 때」의 차례를 보일 때 쓴다(공개 화면은 템플릿이 준 값을 쓴다)
     * @param {string} theme - 테마 id.
     * @returns {HomeStackRule} 한 열 모양.
     */
    static ruleOf(theme: string): HomeStackRule {
        return THEME_RULES[theme] ?? 'columns';
    }

    /**
     * 한 열 순서 「본문 열 먼저」(`columns` — 기본형·다음식·네이버식) — 어떤 가젯도 가로지르지 않는 칸 경계로 배치를 세로 열들로 나누고,
     * 왼쪽 열부터 열마다 (y, x) 순서로 잇는다. 나눌 경계가 없으면(전체 폭 가젯이 있는 등) (y, x) 순서 그대로다.
     * 예) 본문 17칸 + 오른쪽 7칸 → 하이라이트 · 인기글 · (모바일 전용) 공동구매 · 방금 올라온 글 · 영상·짤 → 인기 소모임.
     * @param {readonly T[]} items - 보이는 가젯의 자리(운영자 배치).
     * @param {number} columns - 운영자 배치의 칸 수.
     * @returns {T[]} 한 열 순서로 정렬한 새 배열.
     */
    static byColumns<T extends HomeLayoutItem>(items: readonly T[], columns: number): T[] {
        const cuts = cutsOf(items, columns);
        const order = UtilHomeLayout.byReadingOrder(items);
        if (cuts.length === 0) return order;
        const lane = (it: T) => cuts.filter((c) => it.x >= c).length;
        const rank = new Map(order.map((it, i) => [it.gadgetId, i]));
        return order.sort((a, b) => lane(a) - lane(b) || rank.get(a.gadgetId)! - rank.get(b.gadgetId)!);
    }

    /**
     * 한 열 순서 「옆 열 끼워 넣기」(`beside` — 아이로빗, 2026-11-06 사용자) — 열 나누기는 `byColumns` 와 같고,
     * 오른쪽 열의 가젯은 본문 열(맨 왼쪽 열)이 그 가젯의 아래 끝까지 내려온 자리에 끼운다 = 본문 열에서 위 끝(y)이 그 가젯의 아래 끝(y + h) 이하인 마지막 가젯 바로 뒤
     * (그런 가젯이 없으면 맨 앞). 같은 자리에 여럿이면 (y, x) 순서. 나눌 경계가 없으면 (y, x) 순서 그대로다.
     * 예) 하이라이트 · 방금 올라온 글 · 인기글(데스크톱에서 방금 올라온 글 옆까지 내려온다) · 팁과강좌 · 사용기 · 영상·짤 · 인기 소모임.
     * @param {readonly T[]} items - 보이는 가젯의 자리(운영자 배치).
     * @param {number} columns - 운영자 배치의 칸 수.
     * @returns {T[]} 한 열 순서로 정렬한 새 배열.
     */
    static besideColumns<T extends HomeLayoutItem>(items: readonly T[], columns: number): T[] {
        const lanes = UtilHomeStack.byColumns(items, columns);
        const cut = Math.min(...cutsOf(items, columns), Number.POSITIVE_INFINITY);
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
     * 운영자가 정한 모바일 순서를 입힌다 — `mobileOrder` 에 있는 가젯을 그 차례로 앞에, 없는 가젯은 받은 차례(계산된 한 열 순서) 그대로 뒤에.
     * 목록의 ID 중 `order` 에 없는 것(숨긴·지운 가젯)은 건너뛴다. 목록이 null·비었으면 받은 차례 그대로.
     * @param {readonly T[]} order - 계산된 한 열 순서.
     * @param {readonly string[] | null | undefined} mobileOrder - 운영자가 정한 가젯 ID 차례.
     * @returns {T[]} 새 배열.
     */
    static byMobileOrder<T extends { gadgetId: string }>(order: readonly T[], mobileOrder: readonly string[] | null | undefined): T[] {
        if (!mobileOrder || mobileOrder.length === 0) return [...order];
        const byId = new Map(order.map((it) => [it.gadgetId, it]));
        const head: T[] = [];
        for (const id of mobileOrder) {
            const it = byId.get(id);
            if (it && !head.includes(it)) head.push(it);
        }
        return [...head, ...order.filter((it) => !head.includes(it))];
    }

    /**
     * 한 열 순서 전체 — 템플릿 한 열 모양으로 계산하고(게시판이면 본문 먼저), 운영자가 정한 모바일 순서를 입힌다.
     * @param {readonly T[]} items - 보이는 가젯의 자리(운영자 배치).
     * @param {number} columns - 운영자 배치의 칸 수.
     * @param {HomeStackRule} rule - 템플릿 한 열 모양.
     * @param {{ bodyId?: string | null; mobileOrder?: readonly string[] | null }} [options] - 게시판 본문 가젯 ID · 운영자가 정한 모바일 순서.
     * @returns {T[]} 한 열 순서로 정렬한 새 배열.
     */
    static order<T extends HomeLayoutItem>(items: readonly T[], columns: number, rule: HomeStackRule, options: { bodyId?: string | null; mobileOrder?: readonly string[] | null } = {}): T[] {
        const base = rule === 'columns' ? UtilHomeStack.byColumns(items, columns) : rule === 'beside' ? UtilHomeStack.besideColumns(items, columns) : UtilHomeLayout.byReadingOrder(items);
        const computed = options.bodyId ? UtilHomeStack.bodyFirst(base, options.bodyId) : base;
        return UtilHomeStack.byMobileOrder(computed, options.mobileOrder);
    }
}
