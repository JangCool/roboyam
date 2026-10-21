<template>
    <!--
        알림 도구 — 알림을 보낼 곳(도구)을 정한다. 이벤트마다 어느 도구로 보낼지는 `/settings/notifications`(알림)에서 고른다.
        서랍(항상 켜진 기본 도구) · 이메일(설정 > 메일 서버 설정으로 발송, 설정 칸 없음) · 브라우저 알림(설정 칸 없는 기본 제공 도구)과 사용자가 만드는 Slack · Mattermost 를 한 표로 보인다(`NotificationChannelTable`).
        추가·수정은 다이얼로그(종류별 입력칸, 비밀 값은 「입력됨」만 — `NotificationChannelDialog`). 카카오톡 · SMS 는 종류 선택지에 「준비 중」으로 흐리게만 보이고 만들 수 없다.
        연결 시험(목업)은 누를 때마다 성공 ↔ 실패를 번갈아 낸다. 사용 켜기/끄기는 누르면 즉시 저장(낙관적 갱신)이다. 서랍은 끌 수 없다. 도구를 지우면 그 도구를 고른 이벤트에서도 함께 빠진다(확인창이 개수를 말한다).
        도구 목록은 이벤트 관리 화면과 공유하는 상태(`useNotificationChannels`)다. 구획이 하나뿐이라 우측 구역 내비는 없다.
        권한: 화면 `notification.read` (메뉴 정의가 걸러 낸다) · 변경·시험 `notification.manage` — 없으면 단추가 숨고 스위치가 잠긴다.
        ⚠ 서버 API 는 아직 없다(목업, `useNotificationChannelApi`). 주소의 `?mock=notification-channel-error|-save-error|-test-error|-no-read` 로 상태를 확인한다(`notification-channel-mock.ts` 머리말).
    -->
    <div class="flex flex-col gap-4">
        <PageTitle :title="t('notification.channelsTitle')" :subtitle="t('notification.channelsPageSubtitle')">
            <template #actions>
                <Button :label="t('notification.adminReload')" icon="pi pi-refresh" size="small" severity="secondary" variant="outlined" :loading="loading" @click="load()" />
            </template>
        </PageTitle>

        <Message v-if="!canManage" severity="info" :closable="false" size="small">{{ t('notification.channelsReadOnly') }}</Message>

        <AppGadget :title="t('notification.channelsListTitle')">
            <template v-if="canManage" #actions>
                <Button :label="t('notification.channelAdd')" icon="pi pi-plus" size="small" :disabled="!channels" @click="openCreate" />
            </template>

            <p class="m-0 mb-3 text-xs leading-relaxed text-muted-color">
                {{ t('notification.channelsHint') }}
                <NuxtLink to="/settings/notifications" class="text-primary underline-offset-2 hover:underline">{{ t('notification.channelsToEvents') }}</NuxtLink>
            </p>

            <div v-if="loading && !channels" class="flex flex-col gap-2" aria-busy="true">
                <span class="sr-only">{{ t('common.loading') }}</span>
                <Skeleton v-for="n in 4" :key="n" height="2.75rem" />
            </div>

            <ErrorState v-else-if="error" :title="t('notification.channelsLoadFailed')" :description="error.message" retry :retrying="loading" @retry="load()" />

            <template v-else-if="channels">
                <NotificationChannelTable :channels="channels" :can-manage="canManage" :busy-ids="busy" :compact="prefs.density === 'compact'" @toggle="setEnabled" @test="test" @edit="openEdit" @remove="removeChannel" />
                <p v-if="!hasCustom" class="m-0 mt-3 text-xs text-muted-color">{{ t('notification.channelsNoCustom') }}</p>
            </template>
        </AppGadget>

        <NotificationChannelDialog v-model:visible="dialogOpen" :channel="dialogTarget" :busy="saving" :error="dialogError" :field-errors="dialogFieldErrors" @submit="submit" />
    </div>
</template>

<script setup lang="ts">
import type { ApiError } from '~/composables/common/useApiClient';
import type { NotificationChannelSaveRequest, NotificationChannelView } from '~/composables/notification/api/useNotificationChannelApi.types';
import UtilNotificationChannelMessages from '~/utils/util-notification-channel-messages';

const { t } = useI18n();
useSeoMeta({ title: () => t('notification.channelsTitle') });

const { can } = useAdminPermission();
const { confirm } = useConfirmDialog();
const toast = useAppToast();
// ⚠ 통째로 받아 `prefs.density` 를 쓰지 않으면 조밀 모드가 이 표에만 안 먹는다 — 템플릿에서 `prefs.density` 로 읽는다
const { prefs } = useLayoutPrefs();
const { channels, loading, error, saving, busy, load, create, update, setEnabled, remove, test } = useNotificationChannels();

/** 이 화면에서 바꾸는 데 필요한 권한(정의: util-permission-catalog.ts) — 숨기는 것은 편의이고 막는 것은 서버다 */
const canManage = computed(() => can('notification.manage'));
const hasCustom = computed(() => (channels.value ?? []).some((channel) => !channel.builtIn));

const dialogOpen = ref(false);
/** 수정 대상 — null 이면 추가 */
const dialogTarget = ref<NotificationChannelView | null>(null);
const dialogError = ref('');
const dialogFieldErrors = ref<Record<string, string>>({});

function openDialog(channel: NotificationChannelView | null): void {
    dialogTarget.value = channel;
    dialogError.value = '';
    dialogFieldErrors.value = {};
    dialogOpen.value = true;
}
const openCreate = (): void => openDialog(null);
const openEdit = (channel: NotificationChannelView): void => openDialog(channel);

async function submit(body: NotificationChannelSaveRequest): Promise<void> {
    dialogError.value = '';
    dialogFieldErrors.value = {};
    const target = dialogTarget.value;
    try {
        if (target) await update(target.channelId, body);
        else await create(body);
        dialogOpen.value = false;
        toast.success(t(target ? 'notification.channelSaved' : 'notification.channelCreated'));
    } catch (e) {
        // 서버 거절 — 칸에 붙는 문구(`fields`)는 그 칸 아래에, 나머지는 다이얼로그 안에 서버 문구 그대로
        const failure = e as ApiError;
        dialogFieldErrors.value = failure.fields ?? {};
        dialogError.value = Object.keys(dialogFieldErrors.value).length ? '' : failure.message;
    }
}

async function removeChannel(channel: NotificationChannelView): Promise<void> {
    const ok = await confirm({
        title: t('notification.channelDeleteTitle'),
        message: UtilNotificationChannelMessages.deleteConfirm(t, UtilNotificationChannelMessages.displayName(t, channel), channel.usedByEventCount),
        confirmLabel: t('common.delete'),
        tone: 'danger',
    });
    if (ok) await remove(channel);
}

onMounted(() => void load());
</script>
