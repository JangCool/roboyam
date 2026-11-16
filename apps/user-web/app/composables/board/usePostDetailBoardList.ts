import type { LocationQuery, LocationQueryRaw, RouteLocationRaw } from 'vue-router';
import type { BoardDetail, BoardDetailList } from '~/composables/board/api/useBoardApi.types';
import type { PostDetail, PostListQuery, PostRow } from '~/composables/board/api/usePostApi.types';
import UtilBoard from '~/utils/util-board';
import UtilBoardMessages from '~/utils/util-board-messages';
import UtilBoardQuery from '~/utils/util-board-query';
import UtilGroup from '~/utils/util-group';
import UtilHotdeal from '~/utils/util-hotdeal';

/** 글 상세 아래 목록 한 쪽 — 게시판 목록(`PostListResponse`)·소모임 글 목록(`GroupPostPage`)을 같은 모양으로 */
export interface PostDetailBoardListPage {
    items: PostRow[];
    /** 고정 공지 — 서버가 그 쪽이 1쪽이고 거르기가 없을 때만 준다(소모임은 없음) */
    pinned: PostRow[];
    /** 지금 쪽(1부터) — `around` 로 받았으면 서버가 계산한 쪽 */
    page: number;
    totalPages: number;
}

export type PostDetailBoardListState = 'idle' | 'loading' | 'ready' | 'error';

export interface PostDetailBoardListOptions {
    /** 지금 보는 글 — 글 상세가 받은 것 */
    post: Ref<PostDetail | null>;
    /** 그 글의 게시판(소모임 글이면 소모임 게시판 정보) — `detailList`·`kind`·`layout` */
    board: Ref<BoardDetail | null>;
    /** 같은 글에서 목록 문맥(쪽·거르기)만 바뀌어 다시 받을 때 — 화면이 목록 카드 맨 위로 옮긴다 */
    onContextChange?: () => void;
}

/**
 * 글 상세 아래 게시판 목록(post-detail-board-list-contract) — 댓글 카드 다음 카드에 그 게시판 글 목록(쪽 번호 포함).
 *
 * - **문맥**: 주소의 목록 쿼리(`UtilBoardQuery` 와 같은 키 — 핫딜 게시판은 `UtilHotdeal.parseQuery`). 목록에서 들어왔으면 그 쪽·거르기 그대로,
 *   주소에 `page` 가 없으면(바로 들어옴) `around={글 ID}` 를 보내 서버가 그 글이 든 쪽을 정한다(응답 `page`).
 * - **부르는 때**: 화면이 카드 근처까지 내려왔을 때(`near` — 부품의 IntersectionObserver, 글마다 한 번). 같은 글에서 문맥만 바뀌면(쪽 번호) 바로 다시 받는다.
 *   글이 바뀌면(주소의 글 ID) 받은 것을 버리고 다시 근처에 올 때까지 기다린다. 글 상세는 쿼리가 바뀌어도 글을 다시 읽지 않는다(`watch(id)`).
 * - 일반 게시판 `GET /api/boards/{slug}/posts`, 핫딜 같은 주소의 핫딜 조건, 소모임 글 `GET /api/groups/{id}/posts`(쪽 방식).
 * - `detailList` 가 `OFF` 면 부르지 않는다(값이 없는 옛 응답은 `LIST`).
 *
 * ⚠ setup 에서 부른다.
 */
export function usePostDetailBoardList(options: PostDetailBoardListOptions) {
    const route = useRoute();
    const boardApi = useBoardApi();
    const groupApi = useGroupApi();
    const t = useTranslate();
    const { post, board } = options;

    /** 그리는 방식 — 끄기 · 게시판 목록과 같게 · 휴대폰은 촘촘한 줄 */
    const mode = computed<BoardDetailList>(() => UtilBoard.detailList(board.value?.detailList));
    const isDeal = computed(() => !post.value?.group && UtilHotdeal.isDealBoard(board.value));
    const query = computed<PostListQuery>(() => UtilBoardQuery.parse(route.query));
    const dealQuery = computed(() => UtilHotdeal.parseQuery(route.query));
    /** 주소에 쪽이 있는가 — 없으면 서버가 그 글이 든 쪽을 정한다(`around`) */
    const pagePinned = computed(() => hasPage(route.query));

    /** 목록 문맥 쿼리(쪽 빼고) — 기본값은 뺀다 */
    const contextQuery = computed<Record<string, string>>(() => (isDeal.value ? UtilHotdeal.buildQuery({ ...dealQuery.value, page: 1 }) : UtilBoardQuery.build({ ...query.value, page: 1 })));

    const state = ref<PostDetailBoardListState>('idle');
    const list = ref<PostDetailBoardListPage | null>(null);
    const errorMessage = ref('');

    /** 보이는 쪽 — 받은 목록의 쪽, 받기 전에는 주소의 쪽(없으면 null) */
    const shownPage = computed<number | null>(() => list.value?.page ?? (pagePinned.value ? (isDeal.value ? dealQuery.value.page : query.value.page) : null));

    /**
     * 목록의 글 링크에 붙일 쿼리 — 같은 문맥 + 보이는 쪽. 쪽은 1쪽이어도 적는다(주소에 쪽이 없으면 서버가 그 글이 든 쪽으로 옮겨 버린다)
     */
    const linkQuery = computed<LocationQueryRaw>(() => ({ ...contextQuery.value, ...(shownPage.value ? { page: String(shownPage.value) } : {}) }));

    /** 쪽 번호 — 지금 글 그대로, 목록만 그 쪽으로(1쪽도 적는다) */
    function pageTo(page: number): RouteLocationRaw {
        return { path: route.path, query: { ...contextQuery.value, page: String(page) } };
    }

    /** [목록으로]·모바일 뒤로가 갈 목록 주소 — 같은 문맥 + 보이는 쪽(1쪽이면 뺀다). 소모임 글은 소모임 글 탭(해산했으면 소모임 목록) */
    const backTarget = computed<RouteLocationRaw>(() => {
        const p = post.value;
        const pageQuery = shownPage.value && shownPage.value > 1 ? { page: String(shownPage.value) } : {};
        if (p?.group) return p.group.dissolved ? UtilGroup.LIST_PATH : { path: UtilGroup.detailPath(p.group.id), query: { ...UtilBoardQuery.build({ ...query.value, page: 1 }), ...pageQuery } };
        const slug = p?.board.slug ?? board.value?.slug;
        return slug ? { path: `/boards/${slug}`, query: { ...contextQuery.value, ...pageQuery } } : '/';
    });

    let started = false;
    let request = 0;
    let controller: AbortController | null = null;

    async function load(): Promise<void> {
        const p = post.value;
        const b = board.value;
        if (!p || !b || mode.value === 'OFF') return;
        started = true;
        const mine = ++request;
        controller?.abort();
        controller = new AbortController();
        const signal = controller.signal;
        const around = pagePinned.value ? undefined : p.id;
        state.value = 'loading';
        errorMessage.value = '';
        try {
            let page: PostDetailBoardListPage;
            if (p.group) {
                const q = query.value;
                const res = await groupApi.posts(
                    p.group.id,
                    { ...(around ? { around } : { page: q.page }), sort: q.sort, q: q.q || undefined, prefix: q.prefix || undefined, status: q.status || undefined },
                    signal,
                );
                page = { items: res.items, pinned: [], page: res.page ?? (around ? 1 : q.page), totalPages: res.totalPages ?? 1 };
            } else {
                const res = isDeal.value ? await boardApi.dealPosts(p.board.slug, { ...dealQuery.value, around }, signal) : await boardApi.posts(p.board.slug, { ...query.value, around }, signal);
                page = { items: res.items, pinned: res.pinned, page: res.page, totalPages: res.totalPages };
            }
            if (mine !== request) return;
            list.value = page;
            state.value = 'ready';
        } catch (e) {
            if (mine !== request || UtilGroup.isAborted(e)) return;
            errorMessage.value = UtilBoardMessages.of(t, e);
            state.value = 'error';
        }
    }

    /** 카드가 화면 근처에 왔다 — 이 글에서 아직 안 받았으면 받는다 */
    function onNear(): void {
        if (!started) void load();
    }

    function reset(): void {
        request++;
        controller?.abort();
        controller = null;
        started = false;
        list.value = null;
        state.value = 'idle';
        errorMessage.value = '';
    }

    // 글이 바뀌면 버리고(다시 근처에 오면 받는다), 같은 글에서 문맥(쪽·거르기)만 바뀌면 바로 다시 받는다 — 한 감시로 순서를 정한다
    watch(
        () => [String(route.params.id ?? ''), JSON.stringify(contextQuery.value), pagePinned.value ? String(isDeal.value ? dealQuery.value.page : query.value.page) : ''] as const,
        (now, before) => {
            if (now[0] !== before[0]) return reset();
            if (!started) return;
            void load();
            options.onContextChange?.();
        },
    );

    onBeforeUnmount(() => {
        request++;
        controller?.abort();
    });

    return { mode, isDeal, state, list, errorMessage, linkQuery, backTarget, pageTo, onNear, retry: load };
}

/** 주소에 쓸 수 있는 쪽 번호(1 이상)가 있는가 */
function hasPage(query: LocationQuery): boolean {
    const raw = Array.isArray(query.page) ? query.page[0] : query.page;
    const page = Number.parseInt(typeof raw === 'string' ? raw : '', 10);
    return Number.isFinite(page) && page > 0;
}
