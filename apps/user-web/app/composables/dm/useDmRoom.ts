import type { DmMessage, DmQuota, DmThreadHeader } from '~/composables/dm/api/useDmApi.types';
import UtilDm from '~/utils/util-dm';
import type { DmFailedMessage } from '~/utils/util-dm';
import UtilDmMessages from '~/utils/util-dm-messages';
import UtilDmRules from '~/utils/util-dm-rules';

/**
 * 대화방 한 개의 상태와 동작 — `/messages/{id}` 가 쓴다(community `useDmRoom`). 대화를 열 때 한 번 받고, 열려 있는 동안 주기 조회(`refresh`)가 새 쪽지를 합친다.
 *
 * - **읽음 처리는 받는 요청이 하지 않는다** — 화면에 보여 준 뒤 `acknowledge` 가 `markRead` 를 부른다(주기 새로고침이 읽음을 만들지 않게, 탭이 보일 때만).
 * - `send` — 서버가 **거절한** 오류는 입력창 위 안내(`notice`), 입력은 그대로. **연결 끊김·5xx** 는 붉은 말풍선(`failed`)으로 남아 다시 보내거나 지운다.
 * - 쪽지함(`useDmInbox`)의 줄과 안 읽은 대화 수(`useDmBadge`)도 함께 맞춘다.
 * ⚠ setup 에서 부른다.
 */
export type DmRoomStatus = 'loading' | 'ready' | 'error' | 'notfound';

/** 보내기를 끝낸 결과 — `sent` 저장됨 · `failed` 서버가 판정하지 못함(입력칸은 비운다) · `rejected` 거절·못 보냄(입력은 그대로) */
export type DmSendOutcome = 'sent' | 'failed' | 'rejected';

let failedSeq = 0;

export function useDmRoom(threadId: () => string) {
    const api = useDmApi();
    const inbox = useDmInbox();
    const badge = useDmBadge();
    const t = useTranslate();

    const status = ref<DmRoomStatus>('loading');
    const errorMessage = ref('');
    const thread = ref<DmThreadHeader | null>(null);
    /** 오래된 것이 앞 */
    const messages = ref<DmMessage[]>([]);
    const quota = ref<DmQuota | null>(null);
    const hasOlder = ref(false);
    const olderCursor = ref<string | null>(null);
    const loadingOlder = ref(false);
    const olderError = ref('');
    const failed = ref<DmFailedMessage[]>([]);
    const sending = ref(false);
    const { notice, cooling, clear: clearNotice, rejected, failedLocally } = useDmNotice();

    let seq = 0;
    let alive = true;
    let refreshing = false;
    /** 읽음 요청을 낸 가장 최근 상대 쪽지 — 같은 쪽지로 두 번 부르지 않는다 */
    let markedFor: string | null = null;
    onBeforeUnmount(() => {
        alive = false;
    });

    /** 보낼 수 있는 상태인가(상대가 있고 서버가 막지 않았다) */
    const canSend = computed(() => Boolean(thread.value?.canSend) && Boolean(thread.value?.partner.id));

    async function load(): Promise<void> {
        const mine = ++seq;
        status.value = 'loading';
        errorMessage.value = '';
        try {
            const res = await api.thread(threadId(), { size: UtilDm.ROOM_PAGE_SIZE });
            if (mine !== seq || !alive) return;
            thread.value = res.thread;
            messages.value = UtilDm.chronological(res.items);
            quota.value = res.quota;
            hasOlder.value = res.hasNext && res.nextCursor !== null;
            olderCursor.value = res.nextCursor;
            olderError.value = '';
            status.value = 'ready';
            void acknowledge();
        } catch (e) {
            if (mine !== seq || !alive) return;
            if (UtilDmMessages.isThreadMissing(e)) status.value = 'notfound';
            else {
                errorMessage.value = UtilDmMessages.errorOf(t, e);
                status.value = 'error';
            }
        }
    }

    /** 화면에 보여 준 상대 쪽지를 읽음으로 — 탭이 보이고 새 상대 쪽지가 있을 때만. 실패하면 다음에 다시 */
    async function acknowledge(): Promise<void> {
        if (!alive || typeof document === 'undefined' || document.visibilityState !== 'visible') return;
        const latest = UtilDm.latestFromPartner(messages.value)?.id ?? null;
        if (!latest || latest === markedFor) return;
        const id = threadId();
        const wasUnread = inbox.unreadOf(id) > 0;
        const before = markedFor;
        markedFor = latest;
        try {
            await api.markRead(id);
        } catch {
            markedFor = before;
            return;
        }
        if (!alive) return;
        inbox.markedRead(id);
        badge.markedRead(wasUnread);
        void badge.refresh();
    }

    /** 첫 쪽을 조용히 다시 받아 합친다 — 새로 붙은 수와 통째로 바뀌었는지를 돌려준다. 실패는 삼킨다 */
    async function refresh(): Promise<{ added: number; replaced: boolean }> {
        const none = { added: 0, replaced: false };
        if (status.value !== 'ready' || refreshing || !alive) return none;
        refreshing = true;
        const mine = seq;
        try {
            const res = await api.thread(threadId(), { size: UtilDm.ROOM_PAGE_SIZE });
            if (mine !== seq || !alive) return none;
            thread.value = res.thread;
            quota.value = res.quota;
            const merged = UtilDm.mergeLatest(messages.value, res.items, res.hasNext);
            if (merged.replaced) {
                messages.value = merged.messages;
                hasOlder.value = res.hasNext && res.nextCursor !== null;
                olderCursor.value = res.nextCursor;
            } else if (merged.added > 0) {
                messages.value = merged.messages;
            }
            if (!res.thread.canSend) clearNotice();
            if (merged.added > 0) {
                const last = messages.value[messages.value.length - 1];
                if (last) inbox.touch(res.thread.id, res.thread.partner, last);
            }
            void acknowledge();
            return { added: merged.added, replaced: merged.replaced };
        } catch (e) {
            if (alive && mine === seq && UtilDmMessages.isThreadMissing(e)) status.value = 'notfound';
            return none;
        } finally {
            refreshing = false;
        }
    }

    /** 「이전 쪽지 더 보기」 — 받았으면 true */
    async function loadOlder(): Promise<boolean> {
        const cursor = olderCursor.value;
        if (status.value !== 'ready' || !hasOlder.value || !cursor || loadingOlder.value) return false;
        loadingOlder.value = true;
        olderError.value = '';
        const mine = seq;
        try {
            const res = await api.thread(threadId(), { size: UtilDm.ROOM_PAGE_SIZE, cursor });
            if (mine !== seq || !alive) return false;
            messages.value = UtilDm.mergeOlder(messages.value, res.items);
            hasOlder.value = res.hasNext && res.nextCursor !== null;
            olderCursor.value = res.nextCursor;
            return true;
        } catch (e) {
            if (mine === seq && alive) olderError.value = t('dm.olderFailed', { reason: UtilDmMessages.errorOf(t, e) });
            return false;
        } finally {
            loadingOlder.value = false;
        }
    }

    /** 쪽지를 보낸다 — `retryKey` 가 있으면 화면에 남은 실패 쪽지를 다시 보내는 것 */
    async function send(rawBody: string, retryKey: string | null = null): Promise<DmSendOutcome> {
        const current = thread.value;
        const partnerId = current?.partner.id ?? null;
        if (!current || !partnerId || !current.canSend || sending.value) return 'rejected';
        const body = UtilDmRules.normalize(rawBody);
        if (UtilDmRules.bodyProblem(body) !== null) return 'rejected';
        sending.value = true;
        clearNotice();
        const mine = seq;
        try {
            const res = await api.send(partnerId, body);
            if (retryKey) failed.value = failed.value.filter((f) => f.key !== retryKey);
            if (alive && mine === seq) {
                messages.value = UtilDm.appendMessage(messages.value, res.message);
                quota.value = res.quota;
            }
            inbox.touch(res.threadId, current.partner, res.message);
            return 'sent';
        } catch (e) {
            if (!alive || mine !== seq) return 'rejected';
            if (UtilDmMessages.isLocalFailure(e)) {
                if (!retryKey) failed.value = [...failed.value, { key: `f${++failedSeq}`, body, createdAt: new Date().toISOString(), retrying: false }];
                failedLocally(e);
                // 응답만 잃고 서버에는 저장됐을 수 있다 — 바로 확인한다
                void refresh();
                return 'failed';
            }
            rejected(e);
            if (UtilDmMessages.changesState(e)) void refresh();
            return 'rejected';
        } finally {
            sending.value = false;
        }
    }

    async function resend(key: string): Promise<DmSendOutcome> {
        const target = failed.value.find((f) => f.key === key);
        if (!target || sending.value) return 'rejected';
        failed.value = failed.value.map((f) => (f.key === key ? { ...f, retrying: true } : f));
        const outcome = await send(target.body, key);
        if (outcome !== 'sent') failed.value = failed.value.map((f) => (f.key === key ? { ...f, retrying: false } : f));
        return outcome;
    }

    function removeFailed(key: string): void {
        failed.value = failed.value.filter((f) => f.key !== key);
    }

    /** 이 대화를 내 쪽지함에서 지운다 — 실패하면 `ApiError` 를 던진다 */
    async function remove(): Promise<void> {
        const id = threadId();
        const wasUnread = inbox.unreadOf(id) > 0;
        await api.remove(id);
        inbox.removeThread(id);
        badge.markedRead(wasUnread);
        void badge.refresh();
    }

    return { status, errorMessage, thread, messages, quota, hasOlder, loadingOlder, olderError, failed, sending, notice, canSend, cooling, load, refresh, loadOlder, send, resend, removeFailed, remove, clearNotice };
}
