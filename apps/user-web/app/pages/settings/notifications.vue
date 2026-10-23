<template>
    <div class="flex flex-col gap-4">
        <h1 class="text-lg font-bold">{{ t('notification.settingsTitle') }}</h1>

        <div v-if="status === 'loading'" class="flex flex-col gap-4" role="status" :aria-label="t('notification.settingsLoading')" data-testid="notify-settings-loading">
            <div v-for="n in 2" :key="n">
                <div class="mb-2 h-3 w-24 animate-pulse rounded bg-surface-200 dark:bg-surface-800" />
                <div class="h-28 animate-pulse rounded-xl bg-surface-200 dark:bg-surface-800" />
            </div>
        </div>

        <ErrorState v-else-if="status === 'error'" :title="t('notification.settingsLoadFailed')" :description="loadError" retry data-testid="notify-settings-error" @retry="load" />

        <form v-else-if="view" class="flex flex-col gap-5" novalidate data-testid="notify-settings-form" @submit.prevent="save">
            <p class="text-sm leading-relaxed text-muted-color">{{ t('notification.settingsIntro') }}</p>

            <!-- 이메일을 받을 수 없는 계정 — 이메일 스위치를 잠그고 까닭을 알린다 -->
            <Message v-if="!view.emailReceivable && anyEmailOffered" severity="info" size="small" :closable="false" data-testid="notify-settings-email-blocked">
                {{ view.emailBlockedReason === 'NO_EMAIL' ? t('notification.emailNoAddress') : t('notification.emailNotVerified') }}
            </Message>

            <section v-for="group in groups" :key="group.area" :aria-labelledby="`${uid}-${group.area}`">
                <h2 :id="`${uid}-${group.area}`" class="mb-2 px-1 text-xs font-semibold text-muted-color">{{ t(group.titleKey) }}</h2>
                <ul class="m-0 list-none rounded-xl border border-surface bg-surface-0 p-0 dark:bg-surface-900">
                    <li v-for="row in group.rows" :key="row.code" class="flex flex-col gap-2 border-b border-surface px-4 py-3 last:border-b-0 sm:flex-row sm:items-center sm:gap-4" :data-code="row.code" data-testid="notify-settings-row">
                        <div class="min-w-0 flex-1">
                            <p :id="`${uid}-${row.code}-name`" class="text-sm font-medium">{{ row.name }}</p>
                            <p :id="`${uid}-${row.code}-desc`" class="mt-0.5 text-xs leading-snug text-muted-color">{{ row.description }}</p>
                        </div>

                        <!-- 끌 수 없는 알림 — 스위치 대신 잠금 안내 -->
                        <p v-if="!row.memberCanTurnOff" class="flex shrink-0 items-center gap-1.5 text-xs text-muted-color" data-testid="notify-settings-locked">
                            <i class="pi pi-lock text-xs" aria-hidden="true" />{{ row.lockReason === 'ACTION' ? t('notification.lockedAction') : t('notification.lockedAccount') }}
                        </p>

                        <div v-else class="flex shrink-0 items-center gap-5">
                            <label v-if="row.inAppOffered" class="flex items-center gap-2 text-sm">
                                <ToggleSwitch
                                    v-model="choices[row.code]!.inApp"
                                    :input-id="`${uid}-${row.code}-inapp`"
                                    :disabled="saving"
                                    :aria-labelledby="`${uid}-${row.code}-name ${uid}-${row.code}-inapp-label`"
                                    data-testid="notify-settings-inapp"
                                />
                                <span :id="`${uid}-${row.code}-inapp-label`">{{ t('notification.channelInApp') }}</span>
                            </label>
                            <label v-if="row.emailOffered" class="flex items-center gap-2 text-sm" :class="{ 'opacity-60': !view.emailReceivable }">
                                <ToggleSwitch
                                    v-model="choices[row.code]!.email"
                                    :input-id="`${uid}-${row.code}-email`"
                                    :disabled="saving || !view.emailReceivable"
                                    :aria-labelledby="`${uid}-${row.code}-name ${uid}-${row.code}-email-label`"
                                    data-testid="notify-settings-email"
                                />
                                <span :id="`${uid}-${row.code}-email-label`">{{ t('notification.channelEmail') }}</span>
                            </label>
                        </div>
                    </li>
                </ul>
            </section>

            <EmptyState v-if="groups.length === 0" :title="t('notification.settingsEmpty')" icon="pi pi-bell" data-testid="notify-settings-empty" />

            <StickySaveBar v-if="editable.length > 0" :dirty="dirty" :saving="saving" :count="changedCount" :error="formError" revertable submit @revert="revert" />
        </form>
    </div>
</template>

<script setup lang="ts">
import type { MemberNotificationType } from '~/composables/notification/api/useNotificationApi.types';
import type { MemberEventArea, MyNotificationSetting, MyNotificationSettings } from '~/composables/notification/api/useNotificationSettingsApi.types';
import UtilAuthMessages from '~/utils/util-auth-messages';

/**
 * 알림 설정 `/settings/notifications` — 로그인 필요. 회원 이벤트별로 앱 알림·이메일 받기를 끈다(서버 API 가 없어 목업 `GET/PUT /api/me/notification-settings`).
 * - 이벤트 목록·이름·잠금은 서버 회원 이벤트 카탈로그가 준다(준비 중 제외). 영역별(내 글·댓글 / 내 계정 / 신고·운영)로 묶는다.
 * - **운영자가 그 채널로 보내는 이벤트만** 스위치가 있다(`inAppOffered`·`emailOffered`). 회원이 끌 수 없는 이벤트(`memberCanTurnOff=false`)는 잠금 안내.
 * - 이메일 인증 전·이메일 없는 계정은 이메일 스위치가 잠긴다(`emailReceivable=false`).
 * - 한 번에 저장(PUT 하나) → 하단 떠 있는 저장 줄(`StickySaveBar`). 떠나기 전 저장 안 한 변경을 묻는다.
 */
definePageMeta({ middleware: 'auth' });
const { t } = useI18n();
const { siteLabel } = useSiteName();
useSeoMeta({ title: () => t('notification.settingsTitle'), description: () => t('notification.settingsSeo', { site: siteLabel.value }), robots: 'noindex' });

const uid = useId();
const api = useNotificationSettingsApi();
const toast = useAppToast();
const { confirm } = useConfirmDialog();

const AREA_TITLE: Record<MemberEventArea, string> = {
    content: 'notification.areaContent',
    member: 'notification.areaMember',
    safety: 'notification.areaSafety',
};
const AREA_ORDER: MemberEventArea[] = ['content', 'member', 'safety'];

const status = ref<'loading' | 'ready' | 'error'>('loading');
const loadError = ref('');
const view = ref<MyNotificationSettings | null>(null);
/** 고치는 중인 값 — 끌 수 있는 이벤트만 */
const choices = reactive<Partial<Record<MemberNotificationType, { inApp: boolean; email: boolean }>>>({});
const saving = ref(false);
const formError = ref('');

function messageOf(e: unknown): string {
    return e instanceof ApiError ? e.message : UtilAuthMessages.fallback(t, 0, 'NETWORK');
}

function fill(value: MyNotificationSettings) {
    view.value = value;
    for (const key of Object.keys(choices) as MemberNotificationType[]) delete choices[key];
    for (const row of value.events) if (row.memberCanTurnOff) choices[row.code] = { inApp: row.inApp, email: row.email };
    formError.value = '';
}

async function load() {
    status.value = 'loading';
    loadError.value = '';
    try {
        fill(await api.get());
        status.value = 'ready';
    } catch (e) {
        loadError.value = messageOf(e);
        status.value = 'error';
    }
}
void load();

/** 화면에 그릴 이벤트 — 운영자가 어느 채널로도 보내지 않는 이벤트는 감춘다(받을 것이 없다) */
const visible = computed(() => (view.value?.events ?? []).filter((row) => row.inAppOffered || row.emailOffered));
const groups = computed(() =>
    AREA_ORDER.map((area) => ({ area, titleKey: AREA_TITLE[area], rows: visible.value.filter((row) => row.area === area) })).filter((group) => group.rows.length > 0),
);
const anyEmailOffered = computed(() => visible.value.some((row) => row.emailOffered && row.memberCanTurnOff));

/** 회원이 끌 수 있는 이벤트 */
const editable = computed<MyNotificationSetting[]>(() => (view.value?.events ?? []).filter((row) => row.memberCanTurnOff));

/** 바뀐 스위치 수 — 그려진 스위치만 센다 */
const changedCount = computed(() => {
    let count = 0;
    for (const row of editable.value) {
        const choice = choices[row.code];
        if (!choice) continue;
        if (row.inAppOffered && choice.inApp !== row.inApp) count++;
        if (row.emailOffered && choice.email !== row.email) count++;
    }
    return count;
});
const dirty = computed(() => changedCount.value > 0);

function revert() {
    if (view.value) fill(view.value);
}

async function save() {
    if (saving.value || !dirty.value) return;
    saving.value = true;
    formError.value = '';
    try {
        const events = editable.value.map((row) => ({ code: row.code, inApp: choices[row.code]?.inApp ?? row.inApp, email: choices[row.code]?.email ?? row.email }));
        fill(await api.save({ events }));
        toast.success(t('common.saved'));
    } catch (e) {
        formError.value = messageOf(e);
    } finally {
        saving.value = false;
    }
}

onBeforeRouteLeave(async () => {
    if (!dirty.value || saving.value) return true;
    return await confirm({ title: t('settings.leaveTitle'), message: t('settings.leaveMessage'), confirmLabel: t('settings.leaveConfirm'), tone: 'danger' });
});
</script>
