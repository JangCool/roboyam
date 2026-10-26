import type { GroupDetail, GroupEventDetail, GroupManageBlockedReason } from '~/composables/group/api/useGroupApi.types';
import { ApiError } from '~/composables/common/useApiClient';
import UtilBoardMessages from '~/utils/util-board-messages';
import UtilGroup from '~/utils/util-group';
import UtilGroupEvent, { type GroupEventField, type GroupEventFormState, type GroupEventProblems } from '~/utils/util-group-event';
import UtilGroupEventMessages, { type GroupEventCopy, type GroupEventFormKind } from '~/utils/util-group-event-messages';

/**
 * 소모임 일정 만들기·수정 — 상태·입구 판정·검사·제출(community `useGroupEventForm`, `docs/api-groups.md` 9절). `/groups/{id}/events/new` 와 `…/{eventId}/edit` 가 같이 쓴다.
 * `eventId` 를 주면 수정이다.
 *
 * - 소모임 상세(`GET /api/groups/{id}`)로 입구를 가른다: 없음/비활성/오류 → 비회원 → 멤버 아님 → 일정 관리 권한 없음(`permissions.canManageEvents` — 모임장·`EVENTS` 매니저) →
 *   쓰기가 멈춤(`permissions.manageBlockedReason` — 이메일 미인증·이용 제한·사업자 계정) → (수정) 일정 받기 → 끝남/취소 → 폼. 판정은 서버다.
 * - 날짜 한도는 상세의 `limits.eventMaxDaysAhead`·`eventMaxLengthDays`(없으면 366·7일). 이미 시작한 일정은 시작 시각을 바꿀 수 없다(`lockedStartsAt` — 그대로 보낸다).
 * - 칸 오류는 처음 제출한 뒤부터 보이고(화면 검사), 서버가 지적한 칸(`400 fields.*`)은 그 칸을 고칠 때까지 서버 문구를 보인다. 칸에 붙지 않는 실패는 `failure` 다.
 * - `submit()` 결과: `{ invalid }`(화면 검사에 걸린 첫 칸) · `{ rejected }`(서버가 거절 — 칸이 있으면 그 칸) · `{ saved }`(저장된 일정) · null(이미 보내는 중·세션 끊김).
 *
 * ⚠ setup 에서 부른다.
 */
export type GroupEventFormView =
    | 'loading'
    | 'notfound'
    | 'inactive'
    | 'error'
    | 'login'
    | 'not-member'
    | 'not-manager'
    | 'blocked'
    | 'event-notfound'
    | 'event-error'
    | 'ended'
    | 'canceled'
    | 'form';

export type GroupEventSubmitResult = { invalid: GroupEventField } | { rejected: GroupEventField | null } | { saved: GroupEventDetail };

const GROUP_STALE_CODES = new Set(['GROUP_LEADER_ONLY', 'GROUP_MANAGER_PERMISSION_DENIED', 'GROUP_NOT_MEMBER', 'GROUP_NOT_FOUND', 'GROUP_INACTIVE', 'EMAIL_NOT_VERIFIED', 'ACCOUNT_RESTRICTED', 'BUSINESS_ACCOUNT_RESTRICTED']);
const EVENT_STALE_CODES = new Set(['GROUP_EVENT_ENDED', 'GROUP_EVENT_CANCELED', 'GROUP_EVENT_NOT_FOUND']);

export function useGroupEventForm(groupId: MaybeRef<string>, eventId?: MaybeRef<string>) {
    const api = useGroupApi();
    const t = useTranslate();
    const { user, loaded } = useAuth();
    const kind: GroupEventFormKind = eventId === undefined ? 'create' : 'edit';

    /* ── 소모임 ── */
    const detail = ref<GroupDetail | null>(null);
    const detailState = ref<'loading' | 'ready' | 'notfound' | 'error'>('loading');
    const errorMessage = ref('');
    let groupSeq = 0;

    async function loadGroup(silent = false): Promise<void> {
        const mine = ++groupSeq;
        if (!silent) detailState.value = 'loading';
        errorMessage.value = '';
        try {
            const result = await api.detail(toValue(groupId));
            if (mine !== groupSeq) return;
            detail.value = result;
            detailState.value = 'ready';
        } catch (e) {
            if (mine !== groupSeq) return;
            if (UtilGroup.isCode(e, 'GROUP_NOT_FOUND')) {
                detail.value = null;
                detailState.value = 'notfound';
                return;
            }
            if (silent && detail.value) return;
            errorMessage.value = UtilBoardMessages.of(t, e);
            detailState.value = 'error';
        }
    }

    const groupKind = computed(() => (detail.value ? UtilGroup.kindOf(detail.value) : null));
    const group = computed(() => (detailState.value === 'ready' && detail.value && groupKind.value === 'active' ? UtilGroup.active(detail.value) : null));
    const limits = computed(() => UtilGroupEvent.limitsOf(group.value?.limits));
    /** 쓰기가 멈춘 까닭 — 상세가 알려 준 것, 없으면 로그인 정보의 이메일 미인증 */
    const blocked = computed<GroupManageBlockedReason | null>(() => group.value?.permissions.manageBlockedReason ?? (user.value && !user.value.emailVerified ? 'EMAIL_NOT_VERIFIED' : null));
    /** 일정을 받아도 되는 사람 — 멤버이고 일정 관리 권한이 있고 쓰기가 멈추지 않았다 */
    const canManage = computed(() => Boolean(user.value && group.value && UtilGroup.isMember(group.value.myState) && group.value.permissions.canManageEvents && !blocked.value));

    /* ── 고칠 일정 ── */
    const event = ref<GroupEventDetail | null>(null);
    const eventState = ref<'idle' | 'loading' | 'ready' | 'notfound' | 'error'>('idle');
    const eventErrorMessage = ref('');
    let eventSeq = 0;

    const form = reactive<GroupEventFormState>(UtilGroupEvent.emptyForm());
    const original = ref<GroupEventFormState>(UtilGroupEvent.emptyForm());

    async function loadEvent(): Promise<void> {
        if (eventId === undefined) return;
        const mine = ++eventSeq;
        eventState.value = 'loading';
        eventErrorMessage.value = '';
        try {
            const result = await api.event(toValue(groupId), toValue(eventId));
            if (mine !== eventSeq) return;
            event.value = result;
            eventState.value = 'ready';
            const filled = UtilGroupEvent.formOf(result);
            Object.assign(form, filled);
            original.value = { ...filled };
        } catch (e) {
            if (mine !== eventSeq) return;
            if (UtilGroup.isCode(e, 'GROUP_EVENT_NOT_FOUND')) {
                eventState.value = 'notfound';
                return;
            }
            eventErrorMessage.value = UtilBoardMessages.of(t, e);
            eventState.value = 'error';
            if (UtilGroupEvent.isGone(e)) void loadGroup(true);
        }
    }

    const view = computed<GroupEventFormView>(() => {
        if (detailState.value === 'notfound') return 'notfound';
        if (detailState.value === 'error') return 'error';
        if (detailState.value === 'loading' || !loaded.value) return 'loading';
        if (groupKind.value === 'inactive') return 'inactive';
        const g = group.value;
        if (!g) return 'notfound';
        if (!user.value) return 'login';
        if (!UtilGroup.isMember(g.myState)) return 'not-member';
        if (!g.permissions.canManageEvents) return 'not-manager';
        if (blocked.value) return 'blocked';
        if (kind === 'create') return 'form';
        if (eventState.value === 'notfound') return 'event-notfound';
        if (eventState.value === 'error') return 'event-error';
        if (eventState.value !== 'ready' || !event.value) return 'loading';
        if (event.value.state === 'ENDED') return 'ended';
        if (event.value.state === 'CANCELED') return 'canceled';
        return 'form';
    });

    watch(
        () => `${toValue(groupId)}|${user.value?.id ?? ''}`,
        () => void loadGroup(),
        { immediate: true },
    );
    // 고칠 수 있는 사람이 되면(그리고 아직 안 받았으면) 일정을 받는다
    watch(
        canManage,
        (ok) => {
            if (ok && kind === 'edit' && eventState.value === 'idle') void loadEvent();
        },
        { immediate: true },
    );

    /* ── 시각 한도 ── */
    const now = ref(Date.now());
    let clock: ReturnType<typeof setInterval> | null = null;
    onMounted(() => {
        clock = setInterval(() => (now.value = Date.now()), 30_000);
    });

    /** 이미 시작한 일정의 시작 시각 — 바꿀 수 없어 그대로 보낸다 */
    const lockedStartsAt = computed(() => (kind === 'edit' && event.value?.state === 'ONGOING' ? event.value.startsAt : null));
    /** 개인 책임 확인 체크 — 오프라인으로 새로 만들 때 · 온라인 일정을 오프라인으로 바꿀 때 */
    const ackRequired = computed(() => form.mode === 'OFFLINE' && (kind === 'create' || event.value?.mode !== 'OFFLINE'));
    const minStart = computed(() => new Date(now.value).toISOString());
    const maxStart = computed(() => UtilGroupEvent.maxStart(now.value, limits.value));
    const startForEnd = computed(() => lockedStartsAt.value ?? form.startsAt);
    const minEnd = computed(() => startForEnd.value || minStart.value);
    const maxEnd = computed(() => UtilGroupEvent.maxEnd(startForEnd.value, limits.value));

    /* ── 검사·오류 ── */
    const tried = ref(false);
    const serverErrors = ref<Partial<Record<GroupEventField, string>>>({});
    const failure = ref<GroupEventCopy | null>(null);
    const submitting = ref(false);
    let saved = false;

    const problems = computed<GroupEventProblems>(() =>
        UtilGroupEvent.problems(form, { now: now.value, limits: limits.value, lockedStartsAt: lockedStartsAt.value, ackRequired: ackRequired.value }),
    );
    const errors = computed<Partial<Record<GroupEventField, string>>>(() => {
        const out: Partial<Record<GroupEventField, string>> = {};
        for (const field of UtilGroupEvent.FIELD_ORDER) {
            const server = serverErrors.value[field];
            const problem = tried.value ? problems.value[field] : undefined;
            if (server) out[field] = server;
            else if (problem) out[field] = UtilGroupEventMessages.problem(t, field, problem, form, limits.value);
        }
        return out;
    });

    // 고친 칸의 서버 오류는 지운다
    watch(
        () => ({ ...form }),
        (next, prev) => {
            const fields = (Object.keys(next) as GroupEventField[]).filter((field) => next[field] !== prev[field]);
            if (!fields.length || !fields.some((field) => serverErrors.value[field])) return;
            const rest = { ...serverErrors.value };
            for (const field of fields) delete rest[field];
            serverErrors.value = rest;
        },
    );

    const dirty = computed(() => UtilGroupEvent.isDirty(form, original.value));
    /** 나가기 확인을 물을까 — 저장을 마쳤으면 묻지 않는다 */
    const shouldConfirmLeave = (): boolean => !saved && view.value === 'form' && dirty.value;

    async function submit(): Promise<GroupEventSubmitResult | null> {
        if (submitting.value || view.value !== 'form') return null;
        now.value = Date.now();
        tried.value = true;
        failure.value = null;
        const first = UtilGroup.firstField(problems.value, UtilGroupEvent.FIELD_ORDER);
        if (first) return { invalid: first };
        serverErrors.value = {};
        submitting.value = true;
        try {
            const body = UtilGroupEvent.toRequest(form, lockedStartsAt.value);
            const result = eventId === undefined ? await api.createEvent(toValue(groupId), body) : await api.updateEvent(toValue(groupId), toValue(eventId), body);
            saved = true;
            return { saved: result };
        } catch (e) {
            if (UtilGroup.isSessionLost(e)) return null;
            if (e instanceof ApiError && e.code === 'VALIDATION' && e.fields && Object.keys(e.fields).length) {
                serverErrors.value = UtilGroup.serverFields(e, UtilGroupEvent.FIELD_ORDER);
                const field = UtilGroup.firstField(serverErrors.value, UtilGroupEvent.FIELD_ORDER);
                if (!field) failure.value = UtilGroupEventMessages.failure(t, kind, e);
                return { rejected: field };
            }
            failure.value = UtilGroupEventMessages.failure(t, kind, e);
            // 화면이 보던 상태가 낡았다 — 다시 받아 입구 안내로 바꾼다(쓴 내용은 남는다)
            if (e instanceof ApiError && EVENT_STALE_CODES.has(e.code)) void loadEvent();
            if (e instanceof ApiError && GROUP_STALE_CODES.has(e.code)) void loadGroup(true);
            return { rejected: null };
        } finally {
            submitting.value = false;
        }
    }

    onBeforeUnmount(() => {
        groupSeq++;
        eventSeq++;
        if (clock) clearInterval(clock);
    });

    return {
        kind,
        group,
        event,
        view,
        errorMessage,
        eventErrorMessage,
        blocked,
        limits,
        form,
        errors,
        failure,
        submitting,
        dirty,
        shouldConfirmLeave,
        lockedStartsAt,
        ackRequired,
        minStart,
        maxStart,
        minEnd,
        maxEnd,
        loadGroup: () => loadGroup(),
        loadEvent,
        submit,
    };
}
