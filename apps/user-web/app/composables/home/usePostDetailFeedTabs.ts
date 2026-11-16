import type { PostFeedRow } from '~/composables/board/api/usePostApi.types';
import type { PopularFills } from '~/composables/home/api/useHomeApi.types';
import UtilBoardMessages from '~/utils/util-board-messages';
import UtilGroup from '~/utils/util-group';
import UtilPopular from '~/utils/util-popular';

/** 글 상세 아래 탭 — 인기글(기본) · 최신글 */
export type PostDetailFeedTab = 'POPULAR' | 'LATEST';

export type PostDetailFeedState = 'idle' | 'loading' | 'ready' | 'error';

/** 탭 하나의 목록 */
export interface PostDetailFeedList {
    state: PostDetailFeedState;
    items: PostFeedRow[];
    /** 인기글만 — 다른 기간 글로 채운 줄(`UtilPopular.fillsOf`) */
    fills: PopularFills;
    errorMessage: string;
}

/**
 * 글 상세 맨 아래 탭 묶음 [인기글] [최신글](사용자 2026-11-16 — 디시처럼, 게시판 `detailList` 와 상관없이·소모임 글에도). 서버 변경 없이 기존 API.
 * - 인기글: `useHomeApi().popular({ period: 실시간(사이트 기본 기간 — `/popular` 첫 탭), page: 1, size: 10 })`.
 * - 최신글: `useHomeApi().latest({ size: 10 })`.
 * - **부르는 때**: 화면이 탭 묶음 근처에 처음 왔을 때 고른 탭(기본 인기글)만, 다른 탭은 처음 누를 때. 받은 결과는 이 화면 동안 기억한다(글을 옮겨도 다시 받지 않는다 — 글과 상관없는 목록).
 *
 * ⚠ setup 에서 부른다.
 */
export function usePostDetailFeedTabs() {
    const api = useHomeApi();
    const t = useTranslate();

    const active = ref<PostDetailFeedTab>('POPULAR');
    const lists = reactive<Record<PostDetailFeedTab, PostDetailFeedList>>({
        POPULAR: { state: 'idle', items: [], fills: {}, errorMessage: '' },
        LATEST: { state: 'idle', items: [], fills: {}, errorMessage: '' },
    });
    /** 화면이 근처에 왔다 — 그 뒤로 탭을 고르면 바로 받는다 */
    let near = false;
    const controllers: Partial<Record<PostDetailFeedTab, AbortController>> = {};

    async function load(tab: PostDetailFeedTab): Promise<void> {
        const list = lists[tab];
        controllers[tab]?.abort();
        const controller = new AbortController();
        controllers[tab] = controller;
        list.state = 'loading';
        list.errorMessage = '';
        try {
            if (tab === 'POPULAR') {
                const res = await api.popular({ period: UtilPopular.DEFAULT_PERIOD, page: 1, size: UtilPopular.DETAIL_TAB_SIZE }, controller.signal);
                if (controllers[tab] !== controller) return;
                list.items = res.items;
                list.fills = UtilPopular.fillsOf(res.fills);
            } else {
                const res = await api.latest({ size: UtilPopular.DETAIL_TAB_SIZE }, controller.signal);
                if (controllers[tab] !== controller) return;
                list.items = UtilPopular.appendNew([], res.items);
            }
            list.state = 'ready';
        } catch (e) {
            if (controllers[tab] !== controller || UtilGroup.isAborted(e)) return;
            list.errorMessage = UtilBoardMessages.of(t, e);
            list.state = 'error';
        }
    }

    /** 아직 안 받은 탭이면 받는다(받은 것은 기억) */
    function ensure(tab: PostDetailFeedTab): void {
        if (lists[tab].state === 'idle') void load(tab);
    }

    /** 탭 묶음이 화면 근처에 왔다 — 고른 탭을 받는다 */
    function onNear(): void {
        near = true;
        ensure(active.value);
    }

    /** 탭을 골랐다 — 근처에 온 뒤면 처음 고를 때 받는다 */
    function select(tab: PostDetailFeedTab): void {
        active.value = tab;
        if (near) ensure(tab);
    }

    onBeforeUnmount(() => {
        controllers.POPULAR?.abort();
        controllers.LATEST?.abort();
    });

    return { active, lists, onNear, select, retry: load };
}
