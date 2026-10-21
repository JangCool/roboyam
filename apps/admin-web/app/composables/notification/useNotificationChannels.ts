import type { ApiError } from '~/composables/common/useApiClient';
import type { NotificationChannelSaveRequest, NotificationChannelView } from '~/composables/notification/api/useNotificationChannelApi.types';
import UtilNotificationChannelMessages from '~/utils/util-notification-channel-messages';

/**
 * 알림 도구 구획(`/settings/notifications` 의 「알림 도구」)의 상태 — 도구 목록과 만들기·고치기·지우기·사용 켜기/끄기·연결 시험. 이 화면에서만 쓰므로 화면이 setup 에서 부르고 `useState` 로 공유하지 않는다.
 * 서버와 말하는 것은 `useNotificationChannelApi` 다.
 *
 * 만들기·고치기(`create`·`update`)는 다이얼로그가 서버 오류(`message`·`fields`)를 칸 아래에 붙여야 하므로 **`ApiError` 를 그대로 던진다**(`forms.md`). 성공하면 목록을 갈아 끼운다.
 * 사용 켜기/끄기·지우기·연결 시험은 목록의 한 줄 동작이라 **여기서 토스트로 알린다**(성공 3초 · 실패는 서버 문구 그대로 8초). 사용 켜기/끄기는 낙관적 갱신이고 서버가 거절하면 그 줄만 되돌린다.
 * 줄마다 하는 중인 일을 따로 센다(`isBusy`) — 같은 줄의 요청이 엇갈려 서로를 덮지 않게 스위치·단추를 잠근다.
 */
export function useNotificationChannels() {
    const t = useTranslate();
    const api = useNotificationChannelApi();
    const toast = useAppToast();
    const errorOf = useApiErrorOf();

    /** `undefined` = 아직 안 받았다 · 배열 = 받았다(기본 제공 도구가 있어 비는 일은 없다) */
    const channels = ref<NotificationChannelView[] | undefined>(undefined);
    const loading = ref(false);
    /** 목록을 받지 못했을 때의 오류 — 구획 안 `ErrorState`(다시 시도) */
    const error = ref<ApiError | null>(null);
    /** 쓰기·시험을 하는 중인 도구 id 들 */
    const busy = ref<string[]>([]);
    /** 만들기·고치기 요청 중 */
    const saving = ref(false);

    const isBusy = (channelId: string): boolean => busy.value.includes(channelId);

    async function load(): Promise<void> {
        if (loading.value) return;
        loading.value = true;
        error.value = null;
        try {
            channels.value = await api.list();
        } catch (e) {
            error.value = errorOf(e);
            channels.value = undefined;
        } finally {
            loading.value = false;
        }
    }

    function replace(channelId: string, next: NotificationChannelView): void {
        channels.value = channels.value?.map((channel) => (channel.channelId === channelId ? next : channel));
    }

    function mark(channelId: string, on: boolean): void {
        busy.value = on ? [...busy.value, channelId] : busy.value.filter((id) => id !== channelId);
    }

    /** 도구를 만든다 — 실패는 `ApiError` 로 던진다. 만든 도구를 돌려받는다 */
    async function create(body: NotificationChannelSaveRequest): Promise<NotificationChannelView> {
        saving.value = true;
        try {
            const channel = await api.create(body);
            channels.value = [...(channels.value ?? []), channel];
            return channel;
        } catch (e) {
            throw errorOf(e);
        } finally {
            saving.value = false;
        }
    }

    /** 도구를 고친다 — 실패는 `ApiError` 로 던진다 */
    async function update(channelId: string, body: NotificationChannelSaveRequest): Promise<NotificationChannelView> {
        saving.value = true;
        try {
            const channel = await api.update(channelId, body);
            replace(channelId, channel);
            return channel;
        } catch (e) {
            throw errorOf(e);
        } finally {
            saving.value = false;
        }
    }

    /** 사용 켜기/끄기 — 화면을 먼저 바꾸고 서버가 거절하면 그 줄만 되돌린다 */
    async function setEnabled(channel: NotificationChannelView, enabled: boolean): Promise<void> {
        if (isBusy(channel.channelId)) return;
        replace(channel.channelId, { ...channel, enabled });
        mark(channel.channelId, true);
        try {
            replace(channel.channelId, await api.setEnabled(channel.channelId, { enabled }));
            toast.success(t(enabled ? 'notification.channelEnabledOn' : 'notification.channelEnabledOff', { name: UtilNotificationChannelMessages.displayName(t, channel) }));
        } catch (e) {
            replace(channel.channelId, channel);
            toast.error(errorOf(e).message, 8000);
        } finally {
            mark(channel.channelId, false);
        }
    }

    /** 도구를 지운다. 지웠으면 true — 이 도구를 고른 이벤트에서도 서버가 뺐으니 부르는 화면이 전달 설정을 다시 받는다 */
    async function remove(channel: NotificationChannelView): Promise<boolean> {
        if (isBusy(channel.channelId)) return false;
        mark(channel.channelId, true);
        try {
            await api.remove(channel.channelId);
            channels.value = channels.value?.filter((item) => item.channelId !== channel.channelId);
            toast.success(t('notification.channelDeleted', { name: UtilNotificationChannelMessages.displayName(t, channel) }));
            return true;
        } catch (e) {
            toast.error(errorOf(e).message, 8000);
            return false;
        } finally {
            mark(channel.channelId, false);
        }
    }

    /** 연결을 시험한다 — 결과(성공·실패 모두)는 도구에 남고 토스트로도 알린다. 요청 자체가 실패한 것만 오류 토스트 */
    async function test(channel: NotificationChannelView): Promise<void> {
        if (isBusy(channel.channelId)) return;
        mark(channel.channelId, true);
        try {
            const result = await api.test(channel.channelId);
            replace(channel.channelId, { ...(channels.value?.find((item) => item.channelId === channel.channelId) ?? channel), lastTest: result });
            const name = UtilNotificationChannelMessages.displayName(t, channel);
            if (result.ok) toast.success(t('notification.channelTestOk', { name }));
            else toast.error(t('notification.channelTestFailed', { name, reason: result.message }), 8000);
        } catch (e) {
            toast.error(errorOf(e).message, 8000);
        } finally {
            mark(channel.channelId, false);
        }
    }

    return { channels, loading, error, saving, isBusy, load, create, update, setEnabled, remove, test };
}
