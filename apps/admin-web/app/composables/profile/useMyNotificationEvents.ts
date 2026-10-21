import type { ApiError } from '~/composables/common/useApiClient';
import type { MyNotificationEventView } from '~/composables/notification/api/useNotificationEventApi.types';

/**
 * 내 정보 > 개인 설정 > 알림 설정의 「이벤트별 알림」 — 내가 볼 수 있는 이벤트와, 이벤트마다 받을지(끄기 = `muted`) 곧바로 저장하는 동작.
 * 이 화면에서만 쓰는 값이라 컴포넌트 안의 `ref` 로 둔다(공유 상태가 아니다). 서버와 말하는 것은 `useNotificationEventApi` 다.
 *
 * 스위치를 바꾸면 **곧바로 저장한다**(저장 단추 없음). 화면을 먼저 바꾸고 서버가 거절하면 그 줄만 되돌리고 서버 문구를 토스트로 보인다.
 * 필수(보안) 이벤트는 끌 수 없다 — 화면이 잠그지만, 서버가 409 `NOTIFICATION_MANDATORY` 로 거절하면 같은 방식으로 되돌린다.
 * 읽는 중·못 읽었을 때는 목록을 그리지 않는다(`ErrorState` 대신 구획 안 한 줄 오류와 다시 시도).
 */
export function useMyNotificationEvents() {
    const t = useTranslate();
    const api = useNotificationEventApi();
    const toast = useAppToast();
    const errorOf = useApiErrorOf();

    /** `undefined` = 아직 안 받았다 */
    const events = ref<MyNotificationEventView[] | undefined>(undefined);
    const loading = ref(false);
    const error = ref<ApiError | null>(null);
    /** 저장 중인 이벤트 코드들 */
    const saving = ref<string[]>([]);

    const isSaving = (code: string): boolean => saving.value.includes(code);

    async function load(): Promise<void> {
        if (loading.value) return;
        loading.value = true;
        error.value = null;
        try {
            events.value = await api.myEvents();
        } catch (e) {
            error.value = errorOf(e);
            events.value = undefined;
        } finally {
            loading.value = false;
        }
    }

    function replace(code: string, next: MyNotificationEventView): void {
        events.value = events.value?.map((event) => (event.code === code ? next : event));
    }

    /**
     * 이벤트를 끄거나 다시 켠다.
     * @param event 바꿀 줄(지금 화면의 값).
     * @param muted true 면 끈다(받지 않는다).
     */
    async function setMuted(event: MyNotificationEventView, muted: boolean): Promise<void> {
        if (isSaving(event.code)) return;
        replace(event.code, { ...event, muted });
        saving.value = [...saving.value, event.code];
        try {
            replace(event.code, await api.setMuted(event.code, muted));
            toast.success(t(muted ? 'profile.eventReceiveOff' : 'profile.eventReceiveOn', { name: event.name }));
        } catch (e) {
            replace(event.code, event);
            toast.error(errorOf(e).message, 8000);
        } finally {
            saving.value = saving.value.filter((code) => code !== event.code);
        }
    }

    return { events, loading, error, isSaving, load, setMuted };
}
