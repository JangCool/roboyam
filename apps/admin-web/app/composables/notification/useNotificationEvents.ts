import type { ApiError } from '~/composables/common/useApiClient';
import type { NotificationEventView } from '~/composables/notification/api/useNotificationEventApi.types';
import type { TeamView } from '~/composables/team/api/useTeamApi.types';

/**
 * 운영자 이벤트 화면(`/settings/notifications/operator`)의 상태 — 이벤트 목록과, 스위치·선택을 바꿀 때 **곧바로 저장**하는 동작. 이 화면에서만 쓰므로 화면이 setup 에서 부르고 `useState` 로 공유하지 않는다.
 * 서버와 말하는 것은 `useNotificationEventApi`(+ 팀 이름을 읽는 `useTeamApi`)다.
 *
 * 스위치(사용)와 **전달 도구 · 추가 수신 팀 선택**은 구획의 [저장] 없이 바꾸면 바로 저장한다(forms.md 2-4: 한 칸이 곧 저장 단위). **낙관적 갱신** — 화면을 먼저 바꾸고, 서버가 거절하면 그 줄만 되돌린다.
 * 요청은 `PUT {enabled, channels, teamIds}` 하나다 — `channels` 에 `email` 이 있는지가 곧 이메일 전달이다(서랍은 늘 켜져 있어 목록에 없다).
 * 거절 문구는 서버 문구를 그대로 토스트로 보인다(8초): 칸 오류(`fields.channels|teamIds|enabled`)가 있으면 그것을, 없으면 `message` — 필수 이벤트를 끄려는 409 `NOTIFICATION_MANDATORY`,
 * 준비 중 이벤트 409 `NOTIFICATION_EVENT_NOT_READY`, 이메일 불가 이벤트 400 `fields.channels`, 본인 전용 이벤트에 팀·Slack·Mattermost 400 `NOTIFICATION_BROADCAST_NOT_ALLOWED`,
 * 꺼진 도구·멈춘 팀 409 `NOTIFICATION_CHANNEL_DISABLED`·`TEAM_DISABLED`, `team.read` 없이 팀을 바꿈 403 등.
 * 목록이 낡았다는 신호(필수·준비 중·없음·꺼진/없는 도구·팀)면 뒤에서 조용히 다시 받는다. 저장 중인 줄은 모든 스위치·선택을 잠근다(`isSaving`) — 같은 줄의 두 요청이 엇갈려 서로를 덮지 않게.
 *
 * 팀 이름은 `team.read` 가 있어야 읽는다 — 없으면 팀 목록을 부르지 않고(불필요한 403 이 감사 기록에 남는다) `teams` 는 `undefined` 로 두며 화면은 개수만 보인다.
 */
export function useNotificationEvents() {
    const t = useTranslate();
    const api = useNotificationEventApi();
    const teamApi = useTeamApi();
    const { can } = useAdminPermission();
    // 도구 목록은 알림 도구 화면과 공유하는 상태다 — 선택지가 낡았다는 신호가 오면 여기서도 다시 받는다
    const { load: loadChannels } = useNotificationChannels();
    const toast = useAppToast();
    const errorOf = useApiErrorOf();

    /** `undefined` = 아직 안 받았다 · 배열 = 받았다(비었으면 이벤트 없음) */
    const events = ref<NotificationEventView[] | undefined>(undefined);
    /** 추가 수신 팀으로 고를 수 있는 팀들. `undefined` = 못 받았다(권한 없음 포함) */
    const teams = ref<TeamView[] | undefined>(undefined);
    const loading = ref(false);
    /** 목록을 받지 못했을 때의 오류 — 화면 전체 `ErrorState`(다시 시도) */
    const error = ref<ApiError | null>(null);
    /** 저장 중인 이벤트 코드들 */
    const saving = ref<string[]>([]);

    const isSaving = (code: string): boolean => saving.value.includes(code);

    /** 팀 이름만 읽는다 — 권한이 없거나 못 읽어도 이벤트 표는 보인다 */
    async function loadTeams(): Promise<void> {
        if (!can('team.read')) {
            teams.value = undefined;
            return;
        }
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
            const [list] = await Promise.all([api.list(), loadTeams()]);
            events.value = list;
        } catch (e) {
            if (!quiet) {
                error.value = errorOf(e);
                events.value = undefined;
            }
        } finally {
            loading.value = false;
        }
    }

    function replace(code: string, next: NotificationEventView): void {
        events.value = events.value?.map((event) => (event.code === code ? next : event));
    }

    /** 거절된 저장의 문구 — 400 VALIDATION 은 일반 문구(`message`)이고 구체적인 이유는 칸 오류(`fields`)에 있다 — 있으면 그것을 보인다 */
    function failureText(failure: ApiError): string {
        return failure.fields?.channels ?? failure.fields?.teamIds ?? failure.fields?.enabled ?? failure.message;
    }

    /** 목록이 낡았다는 신호의 오류인가 — 뒤에서 조용히 다시 받는다(이벤트 · 선택지로 쓰던 도구·팀) */
    const isStale = (failure: ApiError): boolean =>
        [
            'NOTIFICATION_MANDATORY',
            'NOTIFICATION_EVENT_NOT_READY',
            'NOTIFICATION_EVENT_NOT_FOUND',
            'NOTIFICATION_CHANNEL_NOT_FOUND',
            'NOTIFICATION_CHANNEL_DISABLED',
            'TEAM_NOT_FOUND',
            'TEAM_DISABLED',
        ].includes(failure.code);

    /** 저장 한 건을 돌린다. 성공하면 true — 서버가 돌려준 바뀐 한 줄로 그 줄을 갈아 끼운다 */
    async function save(event: NotificationEventView, next: Pick<NotificationEventView, 'enabled' | 'channels' | 'teamIds'>, optimistic: NotificationEventView, successText: string): Promise<boolean> {
        replace(event.code, optimistic);
        saving.value = [...saving.value, event.code];
        try {
            replace(event.code, await api.update(event.code, { enabled: next.enabled, channels: next.channels, teamIds: next.teamIds }));
            toast.success(successText);
            return true;
        } catch (e) {
            // 그 줄만 되돌린다 — 서버 문구를 그대로 보인다
            replace(event.code, event);
            const failure = errorOf(e);
            toast.error(failureText(failure), 8000);
            if (isStale(failure)) {
                // 도구가 꺼졌거나 지워졌다면 선택지(도구 목록)도 낡았다
                if (failure.code.startsWith('NOTIFICATION_CHANNEL')) void loadChannels();
                await load(true);
            }
            return false;
        } finally {
            saving.value = saving.value.filter((code) => code !== event.code);
        }
    }

    /**
     * 사용 여부를 바꾸고 곧바로 저장한다. 전달 도구·추가 수신 팀은 지금 선택 그대로 함께 보낸다(서버가 `channels` 를 보낸 요청의 목록을 전부로 본다).
     * @param event 바꿀 줄(지금 화면의 값).
     * @param enabled 바꿀 사용 여부.
     */
    async function change(event: NotificationEventView, enabled: boolean): Promise<void> {
        if (isSaving(event.code)) return;
        await save(event, { enabled, channels: event.channels, teamIds: event.teamIds }, { ...event, enabled }, t(enabled ? 'notification.adminEnabledOn' : 'notification.adminEnabledOff', { name: event.name }));
    }

    /**
     * 전달 도구·추가 수신 팀을 바꾸고 곧바로 저장한다.
     * @param event 바꿀 줄(지금 화면의 값).
     * @param next 바꾼 뒤의 선택 — `channels` 는 이메일·브라우저·Slack·Mattermost 도구 id 전부, `teamIds` 는 추가 수신 팀 전부.
     */
    async function changeDelivery(event: NotificationEventView, next: { channels: string[]; teamIds: string[] }): Promise<void> {
        if (isSaving(event.code)) return;
        if (sameSet(next.channels, event.channels) && sameSet(next.teamIds, event.teamIds)) return;
        const emailOn = next.channels.includes('email');
        await save(event, { enabled: event.enabled, channels: next.channels, teamIds: next.teamIds }, { ...event, channels: next.channels, teamIds: next.teamIds, emailEnabled: emailOn }, t('notification.adminDeliverySaved', { name: event.name }));
    }

    return { events, teams, loading, error, isSaving, load, change, changeDelivery };
}

/** 두 목록이 같은 값들을 담았는가(차례 무시) */
function sameSet(a: readonly string[], b: readonly string[]): boolean {
    return a.length === b.length && a.every((value) => b.includes(value));
}
