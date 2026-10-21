import type { ApiError } from '~/composables/common/useApiClient';
import type { NotificationEventView } from '~/composables/notification/api/useNotificationEventApi.types';

/**
 * 알림 이벤트 관리 화면(`/settings/notifications`)의 상태 — 이벤트 목록과, 스위치를 누를 때 **곧바로 저장**하는 동작. 이 화면에서만 쓰므로 화면이 setup 에서 부르고 `useState` 로 공유하지 않는다.
 * 서버와 말하는 것은 `useNotificationEventApi` 다.
 *
 * 스위치(사용 · 이메일)는 구획의 [저장] 없이 누르면 바로 저장한다(forms.md 2-4: 한 칸이 곧 저장 단위). **낙관적 갱신** — 화면을 먼저 바꾸고, 서버가 거절하면 그 줄만 되돌린다.
 * 거절 문구는 서버 `message` 를 그대로 토스트로 보인다(8초): 필수 이벤트를 끄려는 409 `NOTIFICATION_MANDATORY`, 준비 중 이벤트 409 `NOTIFICATION_EVENT_NOT_READY`, 이메일 불가 이벤트 400 `fields.emailEnabled` 등.
 * 목록이 낡았다는 신호(필수·준비 중·없음)면 뒤에서 조용히 다시 받는다. 저장 중인 줄은 두 스위치를 모두 잠근다(`isSaving`) — 같은 줄의 두 요청이 엇갈려 서로를 덮지 않게.
 */
export function useNotificationEvents() {
    const t = useTranslate();
    const api = useNotificationEventApi();
    const toast = useAppToast();
    const errorOf = useApiErrorOf();

    /** `undefined` = 아직 안 받았다 · 배열 = 받았다(비었으면 이벤트 없음) */
    const events = ref<NotificationEventView[] | undefined>(undefined);
    const loading = ref(false);
    /** 목록을 받지 못했을 때의 오류 — 화면 전체 `ErrorState`(다시 시도) */
    const error = ref<ApiError | null>(null);
    /** 저장 중인 이벤트 코드들 */
    const saving = ref<string[]>([]);

    const isSaving = (code: string): boolean => saving.value.includes(code);

    /** @param quiet true 면 스켈레톤·오류 화면을 거치지 않고 뒤에서 받는다(저장 거절 뒤 목록이 낡았을 때) */
    async function load(quiet = false): Promise<void> {
        if (loading.value) return;
        loading.value = !quiet;
        if (!quiet) error.value = null;
        try {
            events.value = await api.list();
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

    /**
     * 사용 여부 또는 이메일 전달을 바꾸고 곧바로 저장한다. 두 값은 한 요청(`PUT`)으로 같이 보낸다.
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
            toast.error(failure.message, 8000);
            if (['NOTIFICATION_MANDATORY', 'NOTIFICATION_EVENT_NOT_READY', 'NOTIFICATION_EVENT_NOT_FOUND'].includes(failure.code)) await load(true);
        } finally {
            saving.value = saving.value.filter((code) => code !== event.code);
        }
    }

    return { events, loading, error, isSaving, load, change };
}
