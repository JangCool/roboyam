import type { DmMessage, DmPartner, DmThreadItem, MyDm } from '~/composables/dm/api/useDmApi.types';
import UtilDm from '~/utils/util-dm';
import UtilDmMessages from '~/utils/util-dm-messages';

/**
 * 쪽지함(대화 목록)의 **공유 상태** — 쪽지함 칸(`DmThreadList`)이 그리고, 대화방·새 쪽지가 고친다(community `useDmInbox`).
 * 쪽지함 칸은 `/messages` 아래 모든 화면에서 살아 있는 부모 라우트(`pages/messages.vue`)에 있어서 대화방을 드나들어도 다시 그려지지 않는다.
 *
 * - `load` 는 처음에는 로딩으로 받고, 이미 목록이 있으면 **조용히** 받아 합친다(주기 조회·다시 들어옴). 늦게 온 옛 응답은 요청 번호로 버린다.
 * - 안 읽은 대화 수는 `GET /api/me/dm` 의 값으로 `useDmBadge` 도 맞춘다. 로그인한 사람이 바뀌면 비우고 다시 받는다.
 * ⚠ setup 에서 부른다.
 */
export type DmInboxStatus = 'idle' | 'loading' | 'ready' | 'error';

interface InboxState {
    ownerId: string | null;
    status: DmInboxStatus;
    items: DmThreadItem[];
    hasNext: boolean;
    nextCursor: string | null;
    errorMessage: string;
    me: MyDm | null;
}

function emptyState(ownerId: string | null): InboxState {
    return { ownerId, status: 'idle', items: [], hasNext: false, nextCursor: null, errorMessage: '', me: null };
}

let seq = 0;

export function useDmInbox() {
    const api = useDmApi();
    const badge = useDmBadge();
    const { user } = useAuth();
    const t = useTranslate();
    const state = useState<InboxState>('dm-inbox', () => emptyState(null));

    const loadingMore = ref(false);
    const moreError = ref('');

    const status = computed(() => state.value.status);
    const items = computed(() => state.value.items);
    const hasNext = computed(() => state.value.hasNext);
    const errorMessage = computed(() => state.value.errorMessage);
    const me = computed(() => state.value.me);

    const owned = () => state.value.ownerId === (user.value?.id ?? null);

    function claim(): void {
        if (!owned()) {
            seq++;
            state.value = emptyState(user.value?.id ?? null);
        }
    }

    /** 첫 쪽과 내 쪽지 상태를 받는다 — 목록 요청이 실패해도 이미 보이는 목록은 둔다. `me` 만 실패하면 배너 없이 목록만 */
    async function load(): Promise<void> {
        if (!user.value) return;
        claim();
        const mine = ++seq;
        const first = state.value.status !== 'ready';
        if (first) {
            state.value.status = 'loading';
            state.value.errorMessage = '';
        }
        const [threads, mineState] = await Promise.allSettled([api.threads({ size: UtilDm.INBOX_PAGE_SIZE }), api.me()]);
        if (mine !== seq) return;
        if (mineState.status === 'fulfilled') {
            state.value.me = mineState.value;
            badge.sync(mineState.value.unreadThreads);
        }
        if (threads.status === 'fulfilled') {
            const res = threads.value;
            if (first) {
                state.value.items = res.items;
                state.value.hasNext = res.hasNext && res.nextCursor !== null;
                state.value.nextCursor = res.nextCursor;
            } else {
                const merged = UtilDm.mergeThreads(state.value.items, res.items, res.hasNext);
                state.value.items = merged.items;
                if (!merged.keptOlder) {
                    state.value.hasNext = res.hasNext && res.nextCursor !== null;
                    state.value.nextCursor = res.nextCursor;
                }
            }
            state.value.status = 'ready';
        } else if (first) {
            state.value.errorMessage = UtilDmMessages.errorOf(t, threads.reason);
            state.value.status = 'error';
        }
    }

    /** 이어서 받는다(「이전 대화 더 보기」) */
    async function loadMore(): Promise<void> {
        const cursor = state.value.nextCursor;
        if (state.value.status !== 'ready' || !state.value.hasNext || !cursor || loadingMore.value) return;
        loadingMore.value = true;
        moreError.value = '';
        const mine = seq;
        try {
            const res = await api.threads({ size: UtilDm.INBOX_PAGE_SIZE, cursor });
            if (mine !== seq) return;
            const have = new Set(state.value.items.map((x) => x.id));
            state.value.items = [...state.value.items, ...res.items.filter((x) => !have.has(x.id))];
            state.value.hasNext = res.hasNext && res.nextCursor !== null;
            state.value.nextCursor = res.nextCursor;
        } catch (e) {
            if (mine === seq) moreError.value = t('dm.moreFailed', { reason: UtilDmMessages.errorOf(t, e) });
        } finally {
            loadingMore.value = false;
        }
    }

    /** 이 대화의 안 읽은 수(모르면 0) */
    function unreadOf(threadId: string): number {
        if (!owned()) return 0;
        return state.value.items.find((x) => x.id === threadId)?.unreadCount ?? 0;
    }

    function markedRead(threadId: string): void {
        if (owned()) state.value.items = UtilDm.withRead(state.value.items, threadId);
    }

    function removeThread(threadId: string): void {
        if (owned()) state.value.items = state.value.items.filter((x) => x.id !== threadId);
    }

    /** 방금 오간 쪽지로 줄을 고치고 맨 위로 — 쪽지함을 아직 받지 않았으면 아무것도 안 한다 */
    function touch(threadId: string, partner: DmPartner, message: DmMessage): void {
        if (!owned() || state.value.status !== 'ready') return;
        const row: DmThreadItem = { id: threadId, partner, last: { id: message.id, mine: message.mine, preview: UtilDm.preview(message.body), createdAt: message.createdAt }, unreadCount: 0 };
        state.value.items = UtilDm.upsertThread(state.value.items, row);
    }

    function reset(): void {
        seq++;
        state.value = emptyState(null);
    }

    return { status, items, hasNext, errorMessage, me, loadingMore, moreError, load, refresh: load, loadMore, unreadOf, markedRead, removeThread, touch, reset };
}
