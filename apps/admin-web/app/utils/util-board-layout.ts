import type { HomeGridColumns } from '#layers/home-layout/utils/util-home-layout';
import UtilHomeLayout from '#layers/home-layout/utils/util-home-layout';
import type { BoardLayoutBody, BoardLayoutScreen, BoardLayoutScreens } from '~/composables/board/api/useBoardLayoutApi.types';
import type { HomeLayoutGadget } from '~/composables/home/api/useHomeLayoutApi.types';

/**
 * 게시판 레이아웃(설정 › 테마 › 레이아웃 › 게시판, board-layout-contract)의 순수 계산 — 고정 칸 「게시판 본문」, 추천 배치(본문 + 오른쪽 열), 본문 하나 지키기, 적용 화면.
 * 기본 배치(본문만 전체 폭)는 서버가 준다(`GET …/defaults`). 본문은 **종류가 `BOARD_BODY` 인 가젯**으로 찾는다 — ID 는 서버가 정한다(기본 배치는 `seed-1`, 저장하면 Snowflake).
 * 테마마다 하나의 레이아웃을 모든 게시판에 같이 쓴다. 배치·가젯 모양은 홈과 같다(`HomeLayoutEditor` 가 편집, `UtilHomeLayoutEditor` 가 크기 계산 — 본문 최소 크기는 거기 `FIXED_KINDS`).
 * 상태도 요청도 없다.
 */

/** 「게시판 본문」 칸의 종류 키 — 공개 화면이 그 자리에 게시판 목록(·글 보기) 화면을 그린다. user-web `UtilAdminPreview.BOARD_BODY` 와 같은 값 */
const BODY = 'BOARD_BODY';
/** 본문이 없는 배치에 화면이 새로 넣는 본문의 ID — 서버는 모르는 ID 의 `BOARD_BODY` 를 새 본문으로 받는다 */
const NEW_BODY_ID = 'new-body';
/** 본문 처음 높이(행) = 최소 높이 */
const BODY_H = 20;

/** 적용 화면 차례 */
const SCREENS: readonly BoardLayoutScreen[] = ['LIST', 'POST'];

/** 추천 배치의 가젯 제목 — 운영자가 고칠 처음 데이터(화면이 언어에 맞춰 준다) */
export interface BoardLayoutTitles {
    body: string;
    popular: string;
    groupBuy: string;
    groups: string;
}

export default class UtilBoardLayout {
    static readonly BODY = BODY;
    static readonly NEW_BODY_ID = NEW_BODY_ID;
    static readonly SCREENS = SCREENS;

    /**
     * 「게시판 본문」 칸인가
     * @param {string} type - 가젯 종류.
     * @returns {boolean} 본문이면 true.
     */
    static isBody(type: string): boolean {
        return type === BODY;
    }

    /**
     * 본문 가젯(종류가 `BOARD_BODY` 인 첫 가젯)
     * @param {readonly HomeLayoutGadget[]} gadgets - 가젯 목록.
     * @returns {HomeLayoutGadget | undefined} 본문(없으면 undefined).
     */
    static bodyOf(gadgets: readonly HomeLayoutGadget[]): HomeLayoutGadget | undefined {
        return gadgets.find((g) => g.type === BODY);
    }

    /**
     * 적용 화면 처음 값 — 게시판 목록 · 글 보기 둘 다 켬
     * @returns {BoardLayoutScreens} 적용 화면.
     */
    static defaultScreens(): BoardLayoutScreens {
        return { list: true, post: true };
    }

    /**
     * 적용 화면 하나가 켜져 있는가
     * @param {BoardLayoutScreens} screens - 적용 화면.
     * @param {BoardLayoutScreen} screen - 화면.
     * @returns {boolean} 켜져 있으면 true.
     */
    static applies(screens: BoardLayoutScreens, screen: BoardLayoutScreen): boolean {
        return screen === 'LIST' ? screens.list : screens.post;
    }

    /**
     * 새 본문 가젯(본문이 없는 배치에 넣는다 — 꾸미기·설정 없음, 노출은 서버가 늘 둘 다 켬)
     * @param {string} title - 제목(공개 화면에는 보이지 않는다 — 편집기 안내용).
     * @param {string} [id] - 가젯 ID(기본 `NEW_BODY_ID`).
     * @returns {HomeLayoutGadget} 가젯.
     */
    static bodyGadget(title: string, id: string = NEW_BODY_ID): HomeLayoutGadget {
        return { id, type: BODY, title, desktop: true, mobile: true, style: null, config: null };
    }

    /**
     * 추천 배치 — 24칸 기준 본문 17 + 오른쪽 열 7(12칸이면 8 + 4): 「이 게시판 인기글」(글 목록 인기·지금 보는 게시판·순위 줄·10개) · 공동구매(5개) · 인기 소모임(3개).
     * 오른쪽 열 가젯은 PC 전용(모바일 끔). 본문은 지금 배치의 본문 ID 를 그대로 쓴다(저장한 본문을 새로 만들지 않게). 새 가젯 ID 는 `new-<n>`(`nextId` 가 준다).
     * @param {BoardLayoutTitles} titles - 가젯 제목.
     * @param {HomeGridColumns} columns - 칸 수.
     * @param {string} bodyId - 본문 가젯 ID.
     * @param {() => string} nextId - 새 가젯 ID 만들기.
     * @param {BoardLayoutScreens} screens - 적용 화면(그대로 둔다).
     * @returns {BoardLayoutBody} 배치.
     */
    static recommended(titles: BoardLayoutTitles, columns: HomeGridColumns, bodyId: string, nextId: () => string, screens: BoardLayoutScreens): BoardLayoutBody {
        const side = columns === 24 ? 7 : 4;
        const main = columns - side;
        const popular = nextId();
        const groupBuy = nextId();
        const groups = nextId();
        return {
            screens: { ...screens },
            layout: {
                columns,
                items: [
                    { gadgetId: bodyId, x: 0, y: 0, w: main, h: BODY_H },
                    { gadgetId: popular, x: main, y: 0, w: side, h: 14 },
                    { gadgetId: groupBuy, x: main, y: 14, w: side, h: 12 },
                    { gadgetId: groups, x: main, y: 26, w: side, h: 9 },
                ],
            },
            gadgets: [
                UtilBoardLayout.bodyGadget(titles.body, bodyId),
                {
                    id: popular,
                    type: 'POST_LIST',
                    title: titles.popular,
                    desktop: true,
                    mobile: false,
                    config: {
                        boardScope: 'BOARD',
                        boardIds: [],
                        count: 10,
                        sort: 'POPULAR',
                        periodTabs: false,
                        rowStyle: 'RANK',
                        columns: 1,
                        thumbnail: 'AUTO',
                        titleLines: 0,
                        lead: 'NONE',
                        meta: ['COMMENTS'],
                        dividers: true,
                        newBadgeHours: 0,
                        hotBadgeLikes: 0,
                    },
                },
                { id: groupBuy, type: 'GROUP_BUY', title: titles.groupBuy, desktop: true, mobile: false, config: { count: 5, sort: 'DEADLINE' } },
                { id: groups, type: 'GROUPS', title: titles.groups, desktop: true, mobile: false, config: { count: 3 } },
            ],
        };
    }

    /**
     * 본문을 꼭 하나로 — 둘 이상이면 첫 것만 남기고, 없으면 맨 위 전체 폭에 넣는다(다른 가젯은 그 아래로). 서버가 같은 규칙으로 막을 자리(`400 fields.layout.items`).
     * @param {BoardLayoutBody} body - 받은 배치.
     * @param {string} bodyTitle - 새로 넣을 때의 본문 제목.
     * @returns {BoardLayoutBody} 고친 배치(이미 맞으면 같은 값의 사본).
     */
    static ensureBody(body: BoardLayoutBody, bodyTitle: string): BoardLayoutBody {
        const bodies = body.gadgets.filter((g) => g.type === BODY);
        const keep = bodies[0];
        const drop = new Set(bodies.slice(1).map((g) => g.id));
        const gadgets = body.gadgets.filter((g) => !drop.has(g.id));
        let items = body.layout.items.filter((it) => !drop.has(it.gadgetId));
        if (keep && items.some((it) => it.gadgetId === keep.id)) return { screens: { ...body.screens }, layout: { columns: body.layout.columns, items }, gadgets };
        const id = keep?.id ?? NEW_BODY_ID;
        items = [{ gadgetId: id, x: 0, y: 0, w: body.layout.columns, h: BODY_H }, ...items.filter((it) => it.gadgetId !== id).map((it) => ({ ...it, y: it.y + BODY_H }))];
        return {
            screens: { ...body.screens },
            layout: { columns: body.layout.columns, items: UtilHomeLayout.compact(items) },
            gadgets: keep ? gadgets : [UtilBoardLayout.bodyGadget(bodyTitle, id), ...gadgets],
        };
    }

    /**
     * 적용 화면 읽기 — 칸이 없거나 참·거짓이 아니면 켬
     * @param {unknown} value - 받은 값.
     * @returns {BoardLayoutScreens} 적용 화면.
     */
    static readScreens(value: unknown): BoardLayoutScreens {
        const v = typeof value === 'object' && value !== null ? (value as Record<string, unknown>) : {};
        return { list: v.list !== false, post: v.post !== false };
    }
}
