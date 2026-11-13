import type { LocationQuery } from 'vue-router';
import type { PostBoardRef } from '~/composables/board/api/usePostApi.types';

/**
 * 팁·강좌·사용기 묶음 화면(`/tips?board=&page=`)의 주소 규칙 — 게시판 칩·쪽 쿼리. 상태도 요청도 없다.
 * 묶음에 든 게시판(칩)은 서버(지금은 목업)가 정해 주므로 여기에 slug 를 적지 않는다.
 */

/** 화면 상태 — 주소의 쿼리가 곧 상태다 */
export interface TipsState {
    /** 게시판 slug — 빈 글자 = 전체 */
    board: string;
    /** 1부터 */
    page: number;
}

function first(value: LocationQuery[string] | undefined): string {
    const v = Array.isArray(value) ? value[0] : value;
    return typeof v === 'string' ? v : '';
}

export default class UtilTips {
    static readonly PATH = '/tips';
    /** `/tips` 의 배치를 가진 시스템 가젯 페이지 키(서버 shared V209 — tips-contract 「/tips 레이아웃 편집」). 공개 데이터는 `GET /api/pages/{키}` */
    static readonly PAGE_KEY = 'tips-magazine';
    static readonly MAX_PAGE = 100;
    /** slug 로 쓸 수 있는 글자(게시판 주소 규칙 — 영소문자·숫자·`-`·`_`) */
    static readonly SLUG = /^[a-z0-9_-]{1,40}$/;

    /**
     * 주소의 쿼리 → 상태(틀린 게시판은 전체, 틀린 쪽은 1)
     * @param {LocationQuery} query - 라우트의 쿼리.
     * @returns {TipsState} 상태.
     */
    static parse(query: LocationQuery): TipsState {
        const board = first(query.board).trim().toLowerCase();
        const text = first(query.page).trim();
        const page = /^\d+$/.test(text) ? Number(text) : 1;
        return {
            board: UtilTips.SLUG.test(board) ? board : '',
            page: page >= 1 ? Math.min(page, UtilTips.MAX_PAGE) : 1,
        };
    }

    /**
     * 상태 → 주소의 쿼리(기본값은 뺀다)
     * @param {TipsState} state - 상태.
     * @returns {Record<string, string>} 쿼리.
     */
    static build(state: TipsState): Record<string, string> {
        const out: Record<string, string> = {};
        if (state.board) out.board = state.board;
        if (state.page > 1) out.page = String(state.page);
        return out;
    }

    /**
     * 받은 묶음에 있는 게시판만 남긴다 — 모르는 slug 는 전체(빈 글자)
     * @param {string} board - 주소의 게시판.
     * @param {PostBoardRef[] | null} boards - 묶음의 게시판(아직 못 받았으면 null — 그대로 둔다).
     * @returns {string} 게시판 slug 또는 빈 글자.
     */
    static knownBoard(board: string, boards: PostBoardRef[] | null): string {
        if (!board || !boards) return board;
        return boards.some((b) => b.slug === board) ? board : '';
    }

    /**
     * 시스템 페이지가 없을 때(옛 설치 구조 파일로 설치해 행이 빠진 경우) 그릴 처음 배치 — 서버 V209 의 처음 배치와 같다(24칸).
     * 이번 주 추천(전체 폭) / 최신글(17칸) + 오른쪽 7칸 많이 본 글 · 태그.
     * @param {(key: string) => string} t - 문구.
     * @returns {unknown} 가젯 응답 모양(`UtilGadget.sanitize` 가 읽는다).
     */
    static fallbackPage(t: (key: string) => string): unknown {
        const gadget = (id: string, type: string, title: string, icon: string) => ({
            id,
            type,
            title,
            desktop: true,
            mobile: true,
            empty: false,
            locked: false,
            style: { icon },
            config: {},
            more: null,
            data: null,
        });
        return {
            theme: '',
            layout: {
                columns: 24,
                items: [
                    { gadgetId: 'tips-1', x: 0, y: 0, w: 24, h: 20 },
                    { gadgetId: 'tips-2', x: 0, y: 20, w: 17, h: 40 },
                    { gadgetId: 'tips-3', x: 17, y: 20, w: 7, h: 14 },
                    { gadgetId: 'tips-4', x: 17, y: 34, w: 7, h: 8 },
                ],
            },
            gadgets: [
                gadget('tips-1', 'TIPS_HEADLINE', t('tips.headlineTitle'), 'pi-star'),
                gadget('tips-2', 'TIPS_LATEST', t('tips.latestTitle'), 'pi-clock'),
                gadget('tips-3', 'TIPS_POPULAR', t('tips.mostViewedTitle'), 'pi-eye'),
                gadget('tips-4', 'TIPS_TAGS', t('tips.tagsTitle'), 'pi-hashtag'),
            ],
        };
    }
}
