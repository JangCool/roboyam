import type { AttendanceMonth } from '~/composables/point/api/usePointApi.types';
import { ApiError } from '~/composables/common/useApiClient';
import UtilAuthMessages from '~/utils/util-auth-messages';
import UtilPoint, { type PointBlockedReason } from '~/utils/util-point';
import UtilPointMessages, { type PointCopy } from '~/utils/util-point-messages';

/**
 * 출석 달력과 「오늘 출석」 — 포인트 화면(`/me/points`)의 출석 구획이 쓰는 상태(커머스 1-3, community `usePointAttendance` 를 옮겼다).
 *
 * - 달력은 한 달씩 받는다(`month`). 앞뒤로 옮기면 그 달을 다시 받고, 늦게 온 옛 달의 응답은 요청 번호로 버린다(앞 요청은 끊는다).
 * - **오늘 출석**(`attend`)은 한 번에 하나만 보낸다. 성공하면 새 잔액을 요약 상태(`usePointSummary`)에 바로 반영하고 보고 있는 달이 이번 달이면 오늘 칸을 채운다.
 *   - `409 ATTENDANCE_ALREADY_DONE` 는 **실패가 아니다** — 다른 기기에서 했거나 두 번 눌린 것이니 「출석 완료」로 바꾸고 달력·잔액을 다시 받는다.
 *   - 정지 중·이메일 인증 전(`403 ACCOUNT_RESTRICTED`·`EMAIL_NOT_VERIFIED`)은 막는 까닭(`serverBlocked`)으로 굳혀 안내한다. 요청 제한(`429`)은 잠시 후 다시 안내한다.
 *   - 서버가 분명히 거절한 것(4xx)은 「포인트는 지급되지 않았어요」, 결과를 모르는 것(연결 끊김·5xx)은 「다시 불러와 확인할게요」 하고 실제로 다시 받는다.
 * - 「연속 N일」(`streak`)은 보는 달과 상관없이 서버가 준 마지막 값이다(달력 응답 또는 출석 응답).
 * - 날짜는 모두 서비스 하루(한국 시간)다 — 이번 달은 서버가 준 오늘로 정한다(자정 무렵에도 서버와 같은 달).
 */
export type AttendanceStatus = 'idle' | 'loading' | 'ready' | 'error';

export interface PointAttendanceOptions {
    /** 포인트가 바뀐 뒤(출석 성공·이미 출석함) 부른다 — 내역을 다시 받는 데 쓴다 */
    onChanged?: () => void;
}

export function usePointAttendance(options: PointAttendanceOptions = {}) {
    const api = usePointApi();
    const toast = useAppToast();
    const points = usePointSummary();
    const t = useTranslate();

    const month = ref(UtilPoint.currentMonth());
    const data = ref<AttendanceMonth | null>(null);
    const status = ref<AttendanceStatus>('idle');
    const errorMessage = ref('');
    const attending = ref(false);
    /** 출석 요청이 실패했을 때의 안내(배너) */
    const notice = ref<PointCopy | null>(null);
    /** 서버가 출석을 막는다고 한 까닭 — 있으면 단추가 꺼지고 이유를 보인다 */
    const serverBlocked = ref<PointBlockedReason | null>(null);
    /** 연속 출석 일수 — 서버가 준 마지막 값. 모르면 null */
    const streak = ref<number | null>(null);

    let controller: AbortController | null = null;
    let request = 0;

    /** 한국 오늘 — 서버가 준 값이 먼저고, 없으면 요약의 값 */
    const todayDate = computed(() => data.value?.today.date ?? points.summary.value?.today.date ?? null);
    /** 이번 달 — 서버가 준 오늘로 정한다 */
    const currentMonth = computed(() => todayDate.value?.slice(0, 7) ?? UtilPoint.currentMonth());
    const attendedToday = computed(() => Boolean(data.value?.today.attended || points.summary.value?.today.attended));
    const canPrev = computed(() => UtilPoint.canGoPrev(month.value, currentMonth.value));
    const canNext = computed(() => UtilPoint.canGoNext(month.value, currentMonth.value));
    const cells = computed(() => (data.value ? UtilPoint.cells(data.value.month, data.value.days, todayDate.value) : []));
    /** 이번 달이 아닌 달에 출석 기록이 없다 — 안내 한 줄 */
    const emptyMonth = computed(() => Boolean(data.value) && data.value!.days.length === 0 && data.value!.month !== currentMonth.value);

    /** 이 달을 받는다. `silent` 면 이미 보이는 달력을 그대로 두고 뒤에서 바꾼다(출석 뒤 다시 맞출 때) */
    async function load(silent = false): Promise<void> {
        const mine = ++request;
        controller?.abort();
        controller = new AbortController();
        const asked = month.value;
        if (!silent || !data.value) status.value = 'loading';
        try {
            const result = await api.attendance(asked, controller.signal);
            if (mine !== request) return;
            data.value = result;
            streak.value = result.streak;
            status.value = 'ready';
        } catch (e) {
            if (mine !== request) return;
            if (silent && data.value) return; // 이미 보이는 달력은 그대로 둔다
            errorMessage.value = e instanceof ApiError ? e.message : UtilAuthMessages.fallback(t, 0, 'NETWORK');
            status.value = 'error';
        }
    }

    /** 아직 받은 적이 없을 때만 받는다 */
    function ensureLoaded(): void {
        if (status.value === 'idle') void load();
    }

    /** 앞뒤 달로 옮긴다 — 갈 수 없는 쪽이면 아무것도 하지 않는다 */
    function go(delta: -1 | 1): void {
        if (delta < 0 ? !canPrev.value : !canNext.value) return;
        month.value = UtilPoint.shiftMonth(month.value, delta);
        void load();
    }

    /** 요약과 달력을 조용히 다시 받는다 — 결과를 모르는 실패·이미 출석함 뒤에 서버 값으로 맞춘다 */
    async function refresh(): Promise<void> {
        await Promise.all([points.load(), load(true)]);
    }

    /** 오늘 출석 — 한 번에 하나만 */
    async function attend(): Promise<void> {
        if (attending.value || attendedToday.value) return;
        attending.value = true;
        notice.value = null;
        try {
            const result = await api.attend();
            points.applyAttendance(result);
            streak.value = result.streak;
            if (data.value) data.value = UtilPoint.withAttended(data.value, result.date, result.streak);
            toast.success(UtilPointMessages.attendedToast(t, result));
            options.onChanged?.();
        } catch (e) {
            await onFailed(e);
        } finally {
            attending.value = false;
        }
    }

    async function onFailed(e: unknown): Promise<void> {
        switch (UtilPoint.outcomeOf(e)) {
            case 'ALREADY_DONE':
                // 실패가 아니다 — 이미 오늘 출석했다. 완료 상태로 바꾸고 서버 값으로 맞춘다
                points.markAttended();
                if (data.value) data.value = { ...data.value, today: { ...data.value.today, attended: true } };
                toast.show(t('point.alreadyAttendedToast'));
                await refresh();
                options.onChanged?.();
                return;
            case 'BLOCKED':
                serverBlocked.value = e instanceof ApiError ? UtilPoint.blockedReasonOf(e.code) : null;
                return;
            case 'RATE_LIMITED':
                notice.value = UtilPointMessages.attendRateLimited(t);
                return;
            case 'UNAUTHENTICATED':
                return; // 요청 통로가 이미 로그인 화면으로 보낸다
            default: {
                const definite = UtilPoint.isDefiniteRefusal(e);
                notice.value = UtilPointMessages.attendFailed(t, definite);
                if (!definite) await refresh(); // 결과를 모른다 — 서버가 아는 값으로 맞춘다
            }
        }
    }

    /** 로그아웃·계정 전환 — 다른 사람의 달력이 남지 않게 처음으로 */
    function reset(): void {
        request++;
        controller?.abort();
        month.value = UtilPoint.currentMonth();
        data.value = null;
        status.value = 'idle';
        errorMessage.value = '';
        notice.value = null;
        serverBlocked.value = null;
        streak.value = null;
    }

    onBeforeUnmount(() => {
        request++;
        controller?.abort();
    });

    return {
        month,
        data,
        status,
        errorMessage,
        attending,
        notice,
        serverBlocked,
        streak,
        todayDate,
        currentMonth,
        attendedToday,
        canPrev,
        canNext,
        cells,
        emptyMonth,
        load,
        ensureLoaded,
        go,
        attend,
        refresh,
        reset,
    };
}
