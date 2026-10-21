import type { ApiError } from '~/composables/common/useApiClient';
import type { NotificationEventDelivery, NotificationEventView } from '~/composables/notification/api/useNotificationEventApi.types';
import type { TeamView } from '~/composables/team/api/useTeamApi.types';
import UtilNotificationChannel from '~/utils/util-notification-channel';

/**
 * 알림 이벤트 관리 화면(`/settings/notifications`)의 상태 — 이벤트 목록과, 스위치·선택을 바꿀 때 **곧바로 저장**하는 동작. 이 화면에서만 쓰므로 화면이 setup 에서 부르고 `useState` 로 공유하지 않는다.
 * 서버와 말하는 것은 `useNotificationEventApi`(+ 팀 이름을 읽는 `useTeamApi`)다.
 *
 * 스위치(사용)와 **전달 도구 · 추가 수신 팀 선택**은 구획의 [저장] 없이 바꾸면 바로 저장한다(forms.md 2-4: 한 칸이 곧 저장 단위). **낙관적 갱신** — 화면을 먼저 바꾸고, 서버가 거절하면 그 줄만 되돌린다.
 * 거절 문구는 서버 `message` 를 그대로 토스트로 보인다(8초): 필수 이벤트를 끄려는 409 `NOTIFICATION_MANDATORY`, 준비 중 이벤트 409 `NOTIFICATION_EVENT_NOT_READY`, 이메일 불가 이벤트 400 `fields.emailEnabled` 등.
 * 목록이 낡았다는 신호(필수·준비 중·없음)면 뒤에서 조용히 다시 받는다. 저장 중인 줄은 모든 스위치·선택을 잠근다(`isSaving`) — 같은 줄의 두 요청이 엇갈려 서로를 덮지 않게.
 *
 * ── 전달 도구(목업 단계) ─ 이메일은 서버가 이미 `emailEnabled` 로 갖고 있고(실제 서버), 그 밖의 도구·추가 수신 팀은 목업 저장소(`deliveries`)에 있다.
 * 화면은 둘을 **하나의 선택**으로 보인다: 고른 도구 = 이메일(`emailEnabled` 일 때 `UtilNotificationChannel.EMAIL_ID`) + 목업의 `channelIds`. 바꿀 때는 이메일 변화는 `update`(실제), 나머지는 `setDelivery`(목업)로 나눠 보낸다.
 * 서버가 `channels`·`teamIds` 를 이벤트에 합치면(후보 계약) 이 나눔과 `deliveries` 는 없어진다.
 * 팀 이름은 `team.read` 가 있어야 읽는다 — 못 읽으면 `teams` 는 `undefined` 로 두고 화면은 개수만 보인다(403 은 오류가 아니다).
 */
export function useNotificationEvents() {
    const t = useTranslate();
    const api = useNotificationEventApi();
    const teamApi = useTeamApi();
    const toast = useAppToast();
    const errorOf = useApiErrorOf();

    /** `undefined` = 아직 안 받았다 · 배열 = 받았다(비었으면 이벤트 없음) */
    const events = ref<NotificationEventView[] | undefined>(undefined);
    /** 이벤트 코드 → 전달 도구·추가 수신 팀 */
    const deliveries = ref<Record<string, NotificationEventDelivery>>({});
    /** 추가 수신 팀으로 고를 수 있는 팀들. `undefined` = 못 받았다(권한 없음 포함) */
    const teams = ref<TeamView[] | undefined>(undefined);
    const loading = ref(false);
    /** 목록을 받지 못했을 때의 오류 — 화면 전체 `ErrorState`(다시 시도) */
    const error = ref<ApiError | null>(null);
    /** 저장 중인 이벤트 코드들 */
    const saving = ref<string[]>([]);

    const isSaving = (code: string): boolean => saving.value.includes(code);

    /** 이 이벤트의 전달 설정 — 아직 못 받았거나 모르는 이벤트는 비어 있는 것으로 본다 */
    function deliveryOf(code: string): NotificationEventDelivery | undefined {
        return deliveries.value[code];
    }

    /** 이 이벤트에서 고른 도구들 — 이메일(`emailEnabled`)과 그 밖의 도구를 하나로 합쳐 보인다 */
    function channelIdsOf(event: NotificationEventView): string[] {
        return [...(event.emailEnabled ? [UtilNotificationChannel.EMAIL_ID] : []), ...(deliveries.value[event.code]?.channelIds ?? [])];
    }

    function setDeliveries(list: readonly NotificationEventDelivery[]): void {
        deliveries.value = Object.fromEntries(list.map((delivery) => [delivery.code, delivery]));
    }

    /** 팀 이름만 읽는다 — 못 읽어도(권한 없음 등) 이벤트 표는 보인다 */
    async function loadTeams(): Promise<void> {
        try {
            teams.value = await teamApi.list();
        } catch {
            teams.value = undefined;
        }
    }

    /** @param quiet true 면 스켈레톤·오류 화면을 거치지 않고 뒤에서 받는다(저장 거절 뒤 목록이 낡았을 때) */
    async function load(quiet = false): Promise<void> {
        if (loading.value) return;
        loading.value = !quiet;
        if (!quiet) error.value = null;
        try {
            const [list, delivery] = await Promise.all([api.list(), api.deliveries(), loadTeams()]);
            events.value = list;
            setDeliveries(delivery);
        } catch (e) {
            if (!quiet) {
                error.value = errorOf(e);
                events.value = undefined;
            }
        } finally {
            loading.value = false;
        }
    }

    /** 도구·팀이 바뀐 뒤(도구를 지워서 이벤트에서 빠졌다 등) 전달 설정과 팀 이름만 조용히 다시 받는다 */
    async function reloadDelivery(): Promise<void> {
        try {
            const [delivery] = await Promise.all([api.deliveries(), loadTeams()]);
            setDeliveries(delivery);
        } catch {
            // 다음 조회 때 맞춰진다
        }
    }

    function replace(code: string, next: NotificationEventView): void {
        events.value = events.value?.map((event) => (event.code === code ? next : event));
    }

    /** 거절된 저장의 문구 — 400 VALIDATION 은 일반 문구(`message`)이고 구체적인 이유는 칸 오류(`fields`)에 있다 — 있으면 그것을 보인다 */
    function failureText(failure: ApiError): string {
        return failure.fields?.emailEnabled ?? failure.fields?.enabled ?? failure.fields?.channels ?? failure.message;
    }

    /** 목록이 낡았다는 신호의 오류인가 — 뒤에서 조용히 다시 받는다 */
    const isStale = (failure: ApiError): boolean => ['NOTIFICATION_MANDATORY', 'NOTIFICATION_EVENT_NOT_READY', 'NOTIFICATION_EVENT_NOT_FOUND'].includes(failure.code);

    /**
     * 사용 여부를 바꾸고 곧바로 저장한다. 사용 여부와 이메일 전달 두 값은 한 요청(`PUT`)으로 같이 보낸다.
     * @param event 바꿀 줄(지금 화면의 값).
     * @param patch 바꿀 값.
     */
    async function change(event: NotificationEventView, patch: Partial<Pick<NotificationEventView, 'enabled' | 'emailEnabled'>>): Promise<void> {
        if (isSaving(event.code)) return;
        const next: NotificationEventView = { ...event, ...patch };
        replace(event.code, next);
        saving.value = [...saving.value, event.code];
        try {
            replace(event.code, await api.update(event.code, { enabled: next.enabled, emailEnabled: next.emailEnabled }));
            toast.success(patch.enabled !== undefined ? t(patch.enabled ? 'notification.adminEnabledOn' : 'notification.adminEnabledOff', { name: event.name }) : t(next.emailEnabled ? 'notification.adminEmailOn' : 'notification.adminEmailOff', { name: event.name }));
        } catch (e) {
            // 그 줄만 되돌린다 — 서버 문구를 그대로 보인다
            replace(event.code, event);
            const failure = errorOf(e);
            toast.error(failureText(failure), 8000);
            if (isStale(failure)) await load(true);
        } finally {
            saving.value = saving.value.filter((code) => code !== event.code);
        }
    }

    /**
     * 전달 도구·추가 수신 팀을 바꾸고 곧바로 저장한다.
     * 이메일이 바뀌면 `update`(실제 서버의 `emailEnabled`), 그 밖의 도구·팀이 바뀌면 `setDelivery`(목업)로 나눠 보낸다 — 앞의 것이 저장됐는데 뒤의 것이 거절되면 앞의 것은 그대로 두고 뒤의 것만 되돌린다.
     * @param event 바꿀 줄(지금 화면의 값).
     * @param next 바꾼 뒤의 선택 — `channelIds` 는 이메일을 포함한 고른 도구 전부, `teamIds` 는 추가 수신 팀 전부.
     */
    async function changeDelivery(event: NotificationEventView, next: { channelIds: string[]; teamIds: string[] }): Promise<void> {
        if (isSaving(event.code)) return;
        const beforeDelivery = deliveries.value[event.code];
        const emailOn = next.channelIds.includes(UtilNotificationChannel.EMAIL_ID);
        const otherIds = next.channelIds.filter((id) => id !== UtilNotificationChannel.EMAIL_ID);
        const emailChanged = emailOn !== event.emailEnabled;
        const othersChanged = !sameSet(otherIds, beforeDelivery?.channelIds ?? []) || !sameSet(next.teamIds, beforeDelivery?.teamIds ?? []);
        if (!emailChanged && !othersChanged) return;

        // 화면을 먼저 바꾼다
        let current: NotificationEventView = { ...event, emailEnabled: emailOn };
        replace(event.code, current);
        const base: NotificationEventDelivery = beforeDelivery ?? { code: event.code, channelIds: [], teamIds: [], broadcastAllowed: true };
        deliveries.value = { ...deliveries.value, [event.code]: { ...base, channelIds: otherIds, teamIds: next.teamIds } };
        saving.value = [...saving.value, event.code];
        let emailSaved = false;
        try {
            if (emailChanged) {
                current = await api.update(event.code, { enabled: event.enabled, emailEnabled: emailOn });
                emailSaved = true;
                replace(event.code, current);
            }
            if (othersChanged) deliveries.value = { ...deliveries.value, [event.code]: await api.setDelivery(event.code, { channelIds: otherIds, teamIds: next.teamIds }) };
            toast.success(t('notification.adminDeliverySaved', { name: event.name }));
        } catch (e) {
            // 거절된 쪽만 되돌린다 — 이미 저장된 이메일 변경은 그대로 둔다
            replace(event.code, emailSaved ? current : event);
            if (beforeDelivery) deliveries.value = { ...deliveries.value, [event.code]: beforeDelivery };
            const failure = errorOf(e);
            toast.error(failureText(failure), 8000);
            if (isStale(failure)) await load(true);
            else if (failure.code.startsWith('NOTIFICATION_CHANNEL') || failure.code.startsWith('TEAM_')) await reloadDelivery();
        } finally {
            saving.value = saving.value.filter((code) => code !== event.code);
        }
    }

    return { events, deliveries, teams, loading, error, isSaving, deliveryOf, channelIdsOf, load, reloadDelivery, change, changeDelivery };
}

/** 두 목록이 같은 값들을 담았는가(차례 무시) */
function sameSet(a: readonly string[], b: readonly string[]): boolean {
    return a.length === b.length && a.every((value) => b.includes(value));
}
