import type { InjectionKey } from 'vue';
import type { TipsOverview, TipsPost, TipsPostPage } from '~/composables/tips/api/useTipsApi.types';
import type { TipsState } from '~/utils/util-tips';
import { ApiError } from '~/composables/common/useApiClient';
import UtilBoard from '~/utils/util-board';
import UtilBoardMessages from '~/utils/util-board-messages';
import UtilTips from '~/utils/util-tips';

/**
 * 팁·강좌·사용기 `/tips` 의 데이터 — 화면(`pages/tips.vue`)이 한 번 만들어(`provideTipsShared`) 네 가젯(`GadgetTips*` — 이번 주 추천·최신 글·많이 본 글·태그)이 나눠 쓴다(`useTipsShared`).
 * 가젯마다 따로 받지 않게 머리 몫(`GET /api/tips` — 칩·헤드라인·많이 본 글·⚠ MOCK 태그)과 최신 글(`GET /api/tips/posts`)을 여기서 한 번씩 받는다.
 * 주소의 `board`·`page` 가 상태다(틀린 값은 전체·1쪽, 기본값은 주소에서 뺀다). 게시판·보는 사람이 바뀌면 머리 몫도, 쪽만 바뀌면 최신 글만 새로 받는다. 늦게 온 옛 결과는 요청 번호로 버린다.
 * `/tips` 밖(홈·다른 가젯 페이지)에서는 제공되지 않아 `useTipsShared` 가 null — 그 가젯들은 아무것도 그리지 않는다(서버도 그 배치에만 받는다).
 */
export interface TipsShared {
    state: ComputedRef<TipsState>;
    /** 지금 고른 게시판 slug(전체 = '') — 묶음에 없는 slug 는 전체 */
    board: ComputedRef<string>;
    chips: ComputedRef<{ slug: string; label: string }[]>;
    boardsLoaded: ComputedRef<boolean>;
    overviewStatus: Ref<'loading' | 'ready' | 'error'>;
    overviewError: Ref<string>;
    listStatus: Ref<'loading' | 'ready' | 'error'>;
    list: Ref<TipsPostPage | null>;
    listError: Ref<string>;
    headline: ComputedRef<TipsPost[]>;
    mostViewed: ComputedRef<TipsPost[]>;
    tags: ComputedRef<{ name: string; count: number }[]>;
    loadOverview: () => Promise<void>;
    loadPosts: () => Promise<void>;
    /** 이 화면의 다른 상태 주소 */
    to: (next: TipsState) => { path: string; query: Record<string, string> };
    boardName: (row: TipsPost) => string;
    boardStyle: (row: TipsPost) => ReturnType<typeof UtilBoard.textStyle>;
    /** 접는 까닭 — 차단한 회원·단어의 글이면 접힌 줄(게시판 글 목록과 같다) */
    foldOf: (row: TipsPost) => ReturnType<ReturnType<typeof useBlockOverrides>['blockedOf']>;
}

const KEY: InjectionKey<TipsShared> = Symbol('tips-shared');

/** `/tips` 화면이 부른다 — 데이터를 받기 시작하고 가젯들에 내려 준다 */
export function provideTipsShared(): TipsShared {
    const { t } = useI18n();
    const route = useRoute();
    const api = useTipsApi();
    const { user } = useAuth();
    const { localizedName } = useLocalizedName();
    const overrides = useBlockOverrides();

    const state = computed<TipsState>(() => UtilTips.parse(route.query));

    const overviewStatus = ref<'loading' | 'ready' | 'error'>('loading');
    const overview = ref<TipsOverview | null>(null);
    const overviewError = ref('');
    const listStatus = ref<'loading' | 'ready' | 'error'>('loading');
    const list = ref<TipsPostPage | null>(null);
    const listError = ref('');

    const boards = computed(() => overview.value?.boards ?? null);
    const board = computed(() => UtilTips.knownBoard(state.value.board, boards.value));
    const chips = computed(() => [{ slug: '', label: t('common.all') }, ...(boards.value ?? []).map((b) => ({ slug: b.slug, label: localizedName(b.name, b.nameEn) }))]);

    let overviewSeq = 0;
    let overviewAbort: AbortController | null = null;
    let listSeq = 0;
    let listAbort: AbortController | null = null;

    async function loadOverview(): Promise<void> {
        const mine = ++overviewSeq;
        overviewAbort?.abort();
        const controller = new AbortController();
        overviewAbort = controller;
        overviewStatus.value = 'loading';
        try {
            const res = await api.overview(state.value.board, controller.signal);
            if (mine !== overviewSeq) return;
            overview.value = res;
            overviewStatus.value = 'ready';
        } catch (e) {
            if (mine !== overviewSeq) return;
            if (e instanceof ApiError && e.code === 'ABORTED') return;
            overviewError.value = UtilBoardMessages.of(t, e);
            overviewStatus.value = 'error';
        }
    }

    async function loadPosts(): Promise<void> {
        const mine = ++listSeq;
        listAbort?.abort();
        const controller = new AbortController();
        listAbort = controller;
        list.value = null;
        listStatus.value = 'loading';
        try {
            const res = await api.posts({ board: state.value.board, page: state.value.page }, controller.signal);
            if (mine !== listSeq) return;
            list.value = res;
            listStatus.value = 'ready';
        } catch (e) {
            if (mine !== listSeq) return;
            if (e instanceof ApiError && e.code === 'ABORTED') return;
            listError.value = UtilBoardMessages.of(t, e);
            listStatus.value = 'error';
        }
    }

    // 게시판·보는 사람이 바뀌면 머리 몫도, 쪽만 바뀌면 최신 글만 새로 받는다. 쪽을 바꾸면 맨 위로
    watch(
        () => [state.value.board, state.value.page, user.value?.id ?? null] as const,
        (now, before) => {
            if (!before || now[0] !== before[0] || now[2] !== before[2]) void loadOverview();
            void loadPosts();
            if (before && now[1] !== before[1] && typeof window !== 'undefined') window.scrollTo({ top: 0 });
        },
        { immediate: true },
    );
    onBeforeUnmount(() => {
        overviewAbort?.abort();
        listAbort?.abort();
    });

    const shared: TipsShared = {
        state,
        board,
        chips,
        boardsLoaded: computed(() => boards.value !== null),
        overviewStatus,
        overviewError,
        listStatus,
        list,
        listError,
        headline: computed(() => overview.value?.headline ?? []),
        mostViewed: computed(() => overview.value?.mostViewed ?? []),
        tags: computed(() => overview.value?.tags ?? []),
        loadOverview,
        loadPosts,
        to: (next) => ({ path: UtilTips.PATH, query: UtilTips.build(next) }),
        boardName: (row) => localizedName(row.board.name, row.board.nameEn),
        boardStyle: (row) => UtilBoard.textStyle(row.board.color),
        foldOf: (row) => overrides.blockedOf(row.author.memberId, row.blocked),
    };
    provide(KEY, shared);
    return shared;
}

/** 가젯이 부른다 — `/tips` 밖이면 null(그리지 않는다) */
export function useTipsShared(): TipsShared | null {
    return inject(KEY, null);
}
