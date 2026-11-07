import type { AdminPostBoardOption, AdminPostItem } from '~/composables/post/api/usePostApi.types';
import type { PostActionKind, PostReasonKind } from '~/utils/util-post';
import UtilPostMessages from '~/utils/util-post-messages';

/**
 * 글 서랍(`PostDetailDrawer`)의 글 조치 흐름 — 사유 대화창(`PostReasonDialog`)·이동 대화창(`PostMoveDialog`)의 상태와 서버 호출(숨기기·숨김 해제·삭제·복구·이동·공지·고정·고정 해제·투표 지우기).
 * 게시글 관리(`pages/posts/index.vue`)의 「글 하나의 조치」와 같은 규칙이다(그 화면은 일괄 조치와 `acting` 을 함께 써서 아직 자기 코드를 쓴다 — 동작을 바꾸지 않으려고 옮기지 않았다).
 * 지금 쓰는 곳: 하이라이트 화면의 글 서랍. 조치가 끝나면(또는 그 사이 바뀐 글이면) `after(id)` 로 부른 화면이 목록·서랍을 새로 받는다.
 * 권한 판정은 부른 화면(`UtilPost.actionsOf` — `post.manage`·`post.restore`)과 서버(403)가 한다.
 */
export function usePostActionFlow(options: { after: (id: string) => Promise<void> }) {
    const api = usePostApi();
    const toast = useAppToast();
    const errorOf = useApiErrorOf();
    const t = useTranslate();

    /** 조치가 가는 중 */
    const acting = ref(false);

    const reasonOpen = ref(false);
    const reasonKind = ref<PostReasonKind | null>(null);
    const reasonTarget = ref<AdminPostItem | null>(null);
    const reasonError = ref('');
    const reasonFieldError = ref('');

    const moveOpen = ref(false);
    const moveTarget = ref<AdminPostItem | null>(null);
    const moveError = ref('');
    const moveFieldErrors = ref<Record<string, string>>({});

    /** 이동 대화창의 게시판·말머리 선택지(서랍의 조치 기록 이름 풀기에도 쓴다) */
    const boards = ref<AdminPostBoardOption[]>([]);
    const optionsLoading = ref(false);
    const optionsError = ref('');

    async function loadOptions(): Promise<void> {
        optionsLoading.value = true;
        optionsError.value = '';
        try {
            boards.value = (await api.boardOptions()).boards;
        } catch (e) {
            optionsError.value = `${t('post.optionsFailed')} ${errorOf(e).message}`;
        } finally {
            optionsLoading.value = false;
        }
    }

    function openAct(item: AdminPostItem, kind: PostActionKind): void {
        if (acting.value) return;
        if (kind === 'MOVE') {
            moveTarget.value = item;
            moveError.value = '';
            moveFieldErrors.value = {};
            if (boards.value.length === 0 && !optionsLoading.value) void loadOptions();
            moveOpen.value = true;
            return;
        }
        reasonTarget.value = item;
        reasonKind.value = kind;
        reasonError.value = '';
        reasonFieldError.value = '';
        reasonOpen.value = true;
    }

    /** 서랍의 [투표 지우기] — 사유 대화창을 연다 */
    function openPollDelete(item: AdminPostItem): void {
        if (acting.value) return;
        reasonTarget.value = item;
        reasonKind.value = 'POLL_DELETE';
        reasonError.value = '';
        reasonFieldError.value = '';
        reasonOpen.value = true;
    }

    /** 그 사이 다른 운영자가 바꿨거나 글·투표가 없어졌다 — 알리고 새로 받는다(대화창은 닫는다) */
    const STALE_CODES = new Set(['POST_STATE_INVALID', 'POST_NOT_FOUND', 'POST_PURGED']);
    const POLL_STALE_CODES = new Set(['POLL_NOT_FOUND', 'POST_NOT_FOUND']);

    type ActOutcome = { done: true } | { done: false; error: string; fields: Record<string, string> };

    async function run(item: AdminPostItem, kind: PostReasonKind | 'MOVE', input: { reason: string; notify?: boolean; boardId?: string; prefixId?: string | null }): Promise<ActOutcome> {
        if (acting.value) return { done: false, error: '', fields: {} };
        acting.value = true;
        const reason = input.reason;
        const notified = kind === 'HIDE' && input.notify === true;
        try {
            switch (kind) {
                case 'HIDE':
                    await api.hide(item.id, { reason, notifyAuthor: notified });
                    break;
                case 'UNHIDE':
                    await api.unhide(item.id, { reason });
                    break;
                case 'DELETE':
                    await api.remove(item.id, { reason });
                    break;
                case 'RESTORE':
                    await api.restore(item.id, { reason });
                    break;
                case 'MOVE':
                    await api.move(item.id, { boardId: input.boardId ?? '', prefixId: input.prefixId ?? null, reason });
                    break;
                case 'NOTICE':
                    await api.notice(item.id, { reason });
                    break;
                case 'PIN':
                    await api.pin(item.id, { reason });
                    break;
                case 'UNPIN':
                    await api.unpin(item.id, { reason });
                    break;
                case 'POLL_DELETE':
                    await api.deletePoll(item.id, { reason });
                    break;
            }
        } catch (e) {
            const error = errorOf(e);
            const stale = kind === 'POLL_DELETE' ? POLL_STALE_CODES : STALE_CODES;
            if (stale.has(error.code)) {
                toast.error(t('post.staleFailed', { reason: error.message }), 8000);
                await options.after(item.id);
                return { done: true };
            }
            // `PIN_LIMIT`·권한 없음 등은 대화창 안 한 줄(서버 문구 그대로) — 쓰던 사유는 그대로 둔다
            if (error.status === 400 && error.fields && Object.keys(error.fields).length > 0) return { done: false, error: '', fields: { ...error.fields } };
            return { done: false, error: error.message, fields: {} };
        } finally {
            acting.value = false;
        }
        toast.success(UtilPostMessages.done(t, kind, notified));
        await options.after(item.id);
        return { done: true };
    }

    async function submitReason(payload: { reason: string; notify: boolean }): Promise<void> {
        const item = reasonTarget.value;
        const kind = reasonKind.value;
        if (!item || !kind) return;
        reasonError.value = '';
        reasonFieldError.value = '';
        const outcome = await run(item, kind, payload);
        if (outcome.done) {
            reasonOpen.value = false;
            return;
        }
        reasonFieldError.value = outcome.fields.reason ?? '';
        reasonError.value = outcome.error || (outcome.fields.reason ? '' : Object.values(outcome.fields).join(' '));
    }

    async function submitMove(payload: { boardId: string; prefixId: string | null; reason: string }): Promise<void> {
        const item = moveTarget.value;
        if (!item) return;
        moveError.value = '';
        moveFieldErrors.value = {};
        const outcome = await run(item, 'MOVE', payload);
        if (outcome.done) {
            moveOpen.value = false;
            return;
        }
        moveFieldErrors.value = outcome.fields;
        const known = ['boardId', 'prefixId', 'reason'];
        const other = Object.entries(outcome.fields)
            .filter(([k]) => !known.includes(k))
            .map(([, v]) => v);
        moveError.value = outcome.error || other.join(' ');
    }

    return {
        acting,
        reasonOpen,
        reasonKind,
        reasonTarget,
        reasonError,
        reasonFieldError,
        moveOpen,
        moveTarget,
        moveError,
        moveFieldErrors,
        boards,
        optionsLoading,
        optionsError,
        loadOptions,
        openAct,
        openPollDelete,
        submitReason,
        submitMove,
    };
}
