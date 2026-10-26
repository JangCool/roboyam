import type { GroupEventCounts, GroupEventDetail, GroupEventResponse } from '~/composables/group/api/useGroupApi.types';
import { ApiError } from '~/composables/common/useApiClient';
import UtilBoardMessages from '~/utils/util-board-messages';
import UtilGroup from '~/utils/util-group';
import UtilGroupEvent, { type GroupEventFailure } from '~/utils/util-group-event';
import UtilGroupEventMessages from '~/utils/util-group-event-messages';

/**
 * 소모임 일정 상세 — `GET /api/groups/{id}/events/{eventId}` · 내 참석 응답(`PUT`·`DELETE …/response`) · 일정 취소(`POST …/cancel`) (community `useGroupEventDetail`, `docs/api-groups.md` 9절).
 * 일정 상세 화면 하나가 쓰는 상태라 부를 때마다 새 `ref` 다. **일정은 멤버에게만 보인다** — 로그인 전이면 요청하지 않고 `view` 가 `login` 이다.
 *
 * - `view`: `loading` · `login` · `ready` · `locked`(`403 GROUP_NOT_MEMBER`) · `event-notfound` · `group-notfound` · `group-inactive` · `error`(다시 시도).
 * - 조용한 새로고침(`silent`): 응답 마감·취소처럼 서버 상태가 바뀐 뒤 단추·안내를 맞춘다. 못 받으면 지금 화면을 둔다(멤버가 아니게 됐다 같은 갈래는 반영한다).
 *
 * **참석 응답** (`respond`): 서버 답 전에 먼저 바꿔 보이고(내 응답·집계), 성공하면 서버가 준 값으로 맞추고, 실패하면 되돌린 뒤 서버 문구를 토스트로 알린다.
 * 저장 중에는 다른 단추를 받지 않는다(`saving`). 바꾼 뒤 「불참으로 표시했어요 · 실행 취소」 토스트 — 실행 취소는 이전 응답으로(없었으면 응답 지우기). 그 뒤 또 바꿨으면 앞 토스트의 되돌리기는 받지 않는다.
 * `onResponseChanged` 는 응답이 실제로 저장된 뒤(되돌리기 포함) 부른다 — 참석자 목록이 다시 받는다.
 *
 * **취소** (`cancel`): 결과 `done`(취소됨) · `failed`(시트에 `cancelError` 가 남는다) · `stale`(그 사이 상태·권한이 달라졌다 — 토스트로 알리고 상세를 다시 받았다).
 *
 * ⚠ setup 에서 부른다.
 */
export type GroupEventDetailView = 'loading' | 'login' | 'ready' | GroupEventFailure;
export type GroupEventCancelResult = 'done' | 'failed' | 'stale';

export interface GroupEventDetailOptions {
    groupId: MaybeRef<string>;
    eventId: MaybeRef<string>;
    onResponseChanged?: (prev: GroupEventResponse | null, next: GroupEventResponse | null) => void;
}

export function useGroupEventDetail(options: GroupEventDetailOptions) {
    const api = useGroupApi();
    const toast = useAppToast();
    const t = useTranslate();
    const { user, loaded } = useAuth();

    const detail = ref<GroupEventDetail | null>(null);
    const status = ref<'loading' | 'ready' | GroupEventFailure>('loading');
    const errorMessage = ref('');
    const saving = ref(false);
    const cancelBusy = ref(false);
    const cancelError = ref('');

    let controller: AbortController | null = null;
    let request = 0;
    /** 다른 일정·다른 사람으로 바뀔 때마다 오른다 — 날아가 있던 저장이 새 화면을 건드리지 않게 */
    let epoch = 0;
    /** 마지막으로 바꾼 번호 — 「실행 취소」는 자기 번호가 마지막일 때만 */
    let changeNo = 0;
    /** 떠 있는 되돌리기 토스트의 문구 — 새 응답을 하거나 화면을 떠나면 치운다 */
    let undoMessage = '';

    const view = computed<GroupEventDetailView>(() => {
        if (!loaded.value) return 'loading';
        if (!user.value) return 'login';
        return status.value;
    });

    async function load(loadOptions: { silent?: boolean } = {}): Promise<void> {
        const mine = ++request;
        controller?.abort();
        controller = new AbortController();
        if (!loadOptions.silent) {
            epoch++;
            detail.value = null;
            status.value = 'loading';
            errorMessage.value = '';
        }
        if (!user.value) return;
        try {
            const result = await api.event(toValue(options.groupId), toValue(options.eventId), controller.signal);
            if (mine !== request) return;
            detail.value = result;
            status.value = 'ready';
        } catch (e) {
            if (mine !== request || UtilGroup.isAborted(e)) return;
            const failure = UtilGroupEvent.failureOf(e);
            if (failure === 'error') {
                if (loadOptions.silent && detail.value) return;
                errorMessage.value = UtilBoardMessages.of(t, e);
                status.value = 'error';
                return;
            }
            detail.value = null;
            status.value = failure;
        }
    }

    function paint(next: GroupEventResponse | null, prev: GroupEventResponse | null, counts: GroupEventCounts): void {
        if (detail.value) detail.value = { ...detail.value, myResponse: next, counts: UtilGroupEvent.shiftCounts(counts, prev, next) };
    }

    function dismissUndoToast(): void {
        if (!undoMessage) return;
        const message = undoMessage;
        undoMessage = '';
        toast.toasts.value.filter((item) => item.message === message).forEach((item) => toast.dismiss(item.id));
    }

    /** 내 응답을 `next` 로 저장한다(null 은 지우기) — `saved` · `moved`(그 사이 다른 일정으로 옮겼다) · `failed` */
    async function save(next: GroupEventResponse | null): Promise<'saved' | 'moved' | 'failed'> {
        const current = detail.value;
        if (!current || saving.value) return 'failed';
        const groupId = toValue(options.groupId);
        const eventId = current.id;
        const prev = current.myResponse;
        const prevCounts = current.counts;
        const mine = epoch;
        saving.value = true;
        paint(next, prev, prevCounts);
        try {
            const result = next === null ? await api.clearEventResponse(groupId, eventId) : await api.respondEvent(groupId, eventId, next);
            if (mine !== epoch || !detail.value) return 'moved';
            detail.value = { ...detail.value, myResponse: result.myResponse, counts: result.counts, goingCount: result.counts.going };
            if (prev !== result.myResponse) options.onResponseChanged?.(prev, result.myResponse);
            return 'saved';
        } catch (e) {
            if (mine === epoch && detail.value) detail.value = { ...detail.value, myResponse: prev, counts: prevCounts };
            if (mine !== epoch || UtilGroup.isSessionLost(e)) return 'failed';
            toast.error(UtilBoardMessages.of(t, e), UtilGroupEvent.ERROR_LIFE_MS);
            if (UtilGroupEvent.isChanged(e)) void load({ silent: true });
            return 'failed';
        } finally {
            saving.value = false;
        }
    }

    async function undo(no: number, prev: GroupEventResponse | null): Promise<void> {
        if (no !== changeNo || saving.value) return;
        if ((await save(prev)) !== 'saved') return;
        changeNo++;
        undoMessage = '';
        toast.show(t('groupEvent.undone'));
    }

    /** 응답 단추를 눌렀다 — 이미 고른 응답이거나 저장 중이거나 응답이 닫힌 일정이면 아무 일도 없다 */
    async function respond(next: GroupEventResponse): Promise<void> {
        const current = detail.value;
        if (!current || !current.canRespond || saving.value || current.myResponse === next) return;
        const prev = current.myResponse;
        if ((await save(next)) !== 'saved') return;
        const no = ++changeNo;
        dismissUndoToast();
        undoMessage = UtilGroupEventMessages.respondedToast(t, next);
        toast.show(undoMessage, { life: UtilGroupEvent.UNDO_LIFE_MS, action: { label: t('groupEvent.undo'), run: () => void undo(no, prev) } });
    }

    /** 일정을 취소한다(모임장·`EVENTS` 매니저). 사유는 선택 — 빈 글이면 보내지 않는다 */
    async function cancel(reason: string): Promise<GroupEventCancelResult> {
        const current = detail.value;
        if (!current || cancelBusy.value) return 'failed';
        cancelBusy.value = true;
        cancelError.value = '';
        try {
            await api.cancelEvent(toValue(options.groupId), current.id, reason.trim() || undefined);
            toast.success(t('groupEvent.canceledToast'));
            await load({ silent: true });
            return 'done';
        } catch (e) {
            if (UtilGroup.isSessionLost(e)) return 'failed';
            if (UtilGroupEvent.isChanged(e)) {
                toast.error(UtilBoardMessages.of(t, e), UtilGroupEvent.ERROR_LIFE_MS);
                void load({ silent: true });
                return 'stale';
            }
            cancelError.value = e instanceof ApiError ? (e.field('reason') ?? e.message) : UtilBoardMessages.of(t, e);
            return 'failed';
        } finally {
            cancelBusy.value = false;
        }
    }

    // 사람·일정이 바뀌면 다시 받는다 — 로그인 상태를 다시 받아 `user` 객체만 바뀐 것은 무시하려고 글자로 비교한다
    watch(
        () => `${toValue(options.groupId)}|${toValue(options.eventId)}|${user.value?.id ?? ''}|${loaded.value}`,
        () => void load(),
        { immediate: true },
    );

    onBeforeUnmount(() => {
        request++;
        epoch++;
        controller?.abort();
        dismissUndoToast();
    });

    return { detail, view, errorMessage, saving, cancelBusy, cancelError, load: () => load(), reload: () => load({ silent: true }), respond, cancel };
}
