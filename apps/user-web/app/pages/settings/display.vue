<template>
    <div class="flex flex-col gap-4">
        <h1 class="text-lg font-bold">{{ t('settings.display') }}</h1>

        <div v-if="status === 'loading'" class="h-96 animate-pulse rounded-xl bg-surface-200 dark:bg-surface-800" role="status" :aria-label="t('settings.displayLoading')" data-testid="display-loading" />

        <ErrorState v-else-if="status === 'error'" :title="t('settings.displayLoadFailed')" :description="loadError" retry data-testid="display-error" @retry="load" />

        <form v-else-if="mine" class="flex flex-col gap-4" novalidate data-testid="display-settings-form" @submit.prevent="save">
            <!-- 지금 무엇이 적용되는지 — 내 값 · 사이트 기본값 -->
            <div class="flex flex-wrap items-center justify-between gap-2 rounded-xl border border-surface bg-surface-0 px-4 py-3 text-sm dark:bg-surface-900" data-testid="display-source">
                <p class="flex items-center gap-2">
                    <Tag :value="mine.own ? t('settings.displayOwnTag') : t('settings.displayDefaultTag')" :severity="mine.own ? 'info' : 'secondary'" />
                    <span class="text-muted-color">{{ mine.own ? t('settings.displayOwnText') : t('settings.displayDefaultText') }}</span>
                </p>
                <Button
                    v-if="mine.own"
                    type="button"
                    :label="t('settings.displayReset')"
                    size="small"
                    severity="secondary"
                    variant="outlined"
                    :loading="resetting"
                    :disabled="saving"
                    data-testid="display-reset"
                    @click="onReset"
                />
            </div>

            <section class="rounded-xl border border-surface bg-surface-0 p-4 dark:bg-surface-900" :aria-label="t('settings.display')">
                <DisplayValueForm v-model="values" :errors="fieldErrors" :disabled="saving || resetting" />
            </section>

            <StickySaveBar :dirty="dirty" :saving="saving" :count="changedCount" :error="formError" :disabled="resetting" revertable submit @revert="revert" />
        </form>
    </div>
</template>

<script setup lang="ts">
import UtilDisplayForm, { type DisplayFieldErrors } from '#layers/display-time/utils/util-display-form';
import type { DisplayValues } from '#layers/display-time/utils/util-time';
import type { MyDisplayResponse } from '~/composables/display/api/useDisplayApi.types';
import UtilAuthMessages from '~/utils/util-auth-messages';

/**
 * 시간 표시 `/settings/display` — 로그인 필요. 실제 `GET/PUT/DELETE /api/me/display`(`display-contract.md`).
 * 언어·시간대·시간 형식·날짜 형식을 공용 레이어 `DisplayValueForm` 으로 고른다. 처음 값은 내 값(`own`), 없으면 지금 적용 중인 사이트 기본값(`effective`).
 * 저장(네 칸 통째로 — 칸별 상속 없음)하면 응답의 효과값이 바로 화면 전체에 적용되고(`useDisplaySettings().saveOwn`), 「기본값으로 되돌리기」는 내 값을 지운다(확인 대화상자).
 * 틀린 칸은 `400 VALIDATION fields.zoneId|timeFormat|dateFormat|language` 를 칸 아래에.
 */
definePageMeta({ middleware: 'auth' });
const { t } = useI18n();
const { siteLabel } = useSiteName();
useSeoMeta({ title: () => t('settings.display'), description: () => t('settings.displaySeo', { site: siteLabel.value }), robots: 'noindex' });

const display = useDisplaySettings();
const toast = useAppToast();
const { confirm } = useConfirmDialog();

const status = ref<'loading' | 'ready' | 'error'>('loading');
const loadError = ref('');
const mine = ref<MyDisplayResponse | null>(null);
/** 고르는 중인 값 */
const values = ref<DisplayValues>({ language: 'ko', zoneId: 'Asia/Seoul', timeFormat: 'H24', dateFormat: 'YMD_DASH' });
const saving = ref(false);
const resetting = ref(false);
const formError = ref('');
const fieldErrors = ref<DisplayFieldErrors>({});

const KEYS = ['language', 'zoneId', 'timeFormat', 'dateFormat'] as const;

function messageOf(e: unknown): string {
    return e instanceof ApiError ? e.message : UtilAuthMessages.fallback(t, 0, 'NETWORK');
}

/** 폼의 기준 값 — 내 값, 없으면 지금 적용 중인 값 */
const base = computed<DisplayValues | null>(() => (mine.value ? (mine.value.own ?? mine.value.effective) : null));

function fill(res: MyDisplayResponse) {
    mine.value = res;
    values.value = { ...(res.own ?? res.effective) };
    fieldErrors.value = {};
    formError.value = '';
}

async function load() {
    status.value = 'loading';
    loadError.value = '';
    try {
        fill(await display.loadMine());
        status.value = 'ready';
    } catch (e) {
        loadError.value = messageOf(e);
        status.value = 'error';
    }
}
void load();

const changedCount = computed(() => {
    const b = base.value;
    if (!b) return 0;
    return KEYS.filter((key) => values.value[key] !== b[key]).length;
});
/** 기본값을 따르는 중이면 같은 값이라도 저장해 「내 값」으로 만들 수 있게 하지 않는다 — 고친 칸이 있을 때만 저장 */
const dirty = computed(() => changedCount.value > 0);

// 칸을 바꾸면 그 칸의 서버 오류를 지운다
watch(
    values,
    (next, prev) => {
        if (!prev) return;
        const errors = { ...fieldErrors.value };
        for (const key of KEYS) if (next[key] !== prev[key]) delete errors[key];
        fieldErrors.value = errors;
    },
    { deep: true },
);

function revert() {
    if (mine.value) fill(mine.value);
}

async function save() {
    if (saving.value || !dirty.value) return;
    saving.value = true;
    formError.value = '';
    try {
        fill(await display.saveOwn({ ...values.value }));
        toast.success(t('common.saved'));
    } catch (e) {
        const errors = e instanceof ApiError && e.code === 'VALIDATION' ? UtilDisplayForm.fieldErrors(e) : {};
        if (Object.keys(errors).length > 0) fieldErrors.value = errors;
        else formError.value = messageOf(e);
    } finally {
        saving.value = false;
    }
}

async function onReset() {
    if (resetting.value) return;
    const ok = await confirm({ title: t('settings.displayResetTitle'), message: t('settings.displayResetMessage'), confirmLabel: t('settings.displayReset') });
    if (!ok) return;
    resetting.value = true;
    try {
        fill(await display.resetOwn());
        toast.success(t('settings.displayResetDone'));
    } catch (e) {
        toast.error(messageOf(e), 8000);
    } finally {
        resetting.value = false;
    }
}

onBeforeRouteLeave(async () => {
    if (!dirty.value || saving.value) return true;
    return await confirm({ title: t('settings.leaveTitle'), message: t('settings.leaveMessage'), confirmLabel: t('settings.leaveConfirm'), tone: 'danger' });
});
</script>
