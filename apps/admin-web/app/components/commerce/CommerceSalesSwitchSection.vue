<template>
    <!--
        커머스 설정 구획 「판매 스위치·오픈 체크리스트」(commerce.md 20절) — 지금 상태 · 자동 조건(사업자 정보·PG 키·택배사 1개 이상·본인인증 — 서버 판정) · 운영 체크 항목(수동, 누가·언제 체크).
        · **켜기는 최고 관리자만**(권한 코드로 위임하지 않는다), 자동 조건과 체크리스트가 모두 차야 켤 수 있다(`canEnable` — 서버 판정). 끄기는 최고 관리자 또는 `commerce.settings`.
        · 켜기·끄기 모두 사유 5~200자, 감사 `COMMERCE_SALES_TOGGLE` + 운영자 알림. 끄면 새 구매만 막히고 진행 중 주문은 그대로.
        · 체크 항목은 누르면 바로 저장한다(한 항목씩 — 저장 줄 없음). 서버 호출은 `useCommerceSettingsApi`(목업 — `?mock=sales-fail`·`sales-off`).
    -->
    <AppGadget :title="t('commerce.salesSection')" anchor="commerce-sales">
        <template v-if="view" #title-extra>
            <Tag :value="view.enabled ? t('commerce.salesOn') : t('commerce.salesOff')" :severity="view.enabled ? 'success' : 'secondary'" />
        </template>
        <div v-if="loading && !view" class="flex flex-col gap-2" role="status" :aria-label="t('common.loading')"><Skeleton v-for="n in 3" :key="n" height="2.5rem" /></div>
        <ErrorState v-else-if="!view" :title="t('commerce.salesLoadFailed')" :description="loadError?.message" retry :retrying="loading" @retry="load" />
        <div v-else class="flex flex-col gap-4">
            <p class="m-0 text-xs leading-relaxed text-muted-color">{{ t('commerce.salesLead') }}</p>

            <div class="grid gap-4 md:grid-cols-2">
                <div class="flex flex-col gap-1.5">
                    <span class="text-sm font-medium">{{ t('commerce.salesAuto') }}</span>
                    <ul class="m-0 flex list-none flex-col gap-1 p-0 text-[13px]">
                        <li v-for="c in view.autoChecks" :key="c.key" class="flex items-center gap-2">
                            <i :class="c.ok ? 'pi pi-check-circle text-green-600' : 'pi pi-times-circle text-red-600'" aria-hidden="true" />
                            <span>{{ t(`commerce.salesAutoKey.${c.key}`) }}</span>
                            <span class="sr-only">{{ c.ok ? t('commerce.salesOk') : t('commerce.salesMissing') }}</span>
                        </li>
                    </ul>
                </div>
                <div class="flex flex-col gap-1.5">
                    <span class="text-sm font-medium">{{ t('commerce.salesChecklist') }}</span>
                    <ul class="m-0 flex list-none flex-col gap-1.5 p-0">
                        <li v-for="c in view.checklist" :key="c.key" class="flex items-start gap-2">
                            <Checkbox :model-value="c.checked" binary :input-id="`${uid}-${c.key}`" :disabled="!canEdit || busyKey !== null" class="mt-0.5" @update:model-value="(v: boolean) => check(c.key, v)" />
                            <label :for="`${uid}-${c.key}`" class="text-[13px]">
                                {{ t(`commerce.salesCheckKey.${c.key}`) }}
                                <span v-if="c.checked && c.checkedAt" class="block text-[11px] text-muted-color">{{ t('commerce.salesCheckedBy', { by: c.checkedBy ?? '—', at: time.full(c.checkedAt) }) }}</span>
                            </label>
                        </li>
                    </ul>
                </div>
            </div>

            <div class="flex flex-wrap items-center gap-2 border-t border-(--line) pt-3">
                <Button v-if="!view.enabled" type="button" :label="t('commerce.salesTurnOn')" icon="pi pi-play" size="small" :disabled="!isSuper || !view.canEnable" @click="openToggle(true)" />
                <Button v-else type="button" :label="t('commerce.salesTurnOff')" icon="pi pi-pause" size="small" severity="danger" variant="outlined" :disabled="!(isSuper || canEdit)" @click="openToggle(false)" />
                <span v-if="!view.enabled && !isSuper" class="text-xs text-muted-color">{{ t('commerce.salesOnSuperOnly') }}</span>
                <span v-else-if="!view.enabled && !view.canEnable" class="text-xs text-amber-700 dark:text-amber-400">{{ t('commerce.salesNotReady') }}</span>
                <span v-if="view.changedAt" class="ml-auto text-xs text-muted-color">{{ t('commerce.salesChanged', { at: time.full(view.changedAt), by: view.changedBy ?? '—' }) }}</span>
            </div>
            <Message v-if="actionError" severity="error" variant="simple" size="small" :closable="false" role="alert">{{ actionError }}</Message>
        </div>

        <PaymentReasonDialog v-model:visible="toggleOpen" :title="toggleOn ? t('commerce.salesOnTitle') : t('commerce.salesOffTitle')" :description="toggleOn ? t('commerce.salesOnBody') : t('commerce.salesOffBody')" :confirm-label="toggleOn ? t('commerce.salesTurnOn') : t('commerce.salesTurnOff')" :tone="toggleOn ? 'primary' : 'danger'" :busy="toggling" :error="toggleError" :field-error="toggleFieldError" @submit="submitToggle" />
    </AppGadget>
</template>

<script setup lang="ts">
import type { SalesChecklistKey, SalesSwitchView } from '~/composables/commerce/api/useCommerceSettingsApi.types';
import type { ApiError } from '~/composables/common/useApiClient';

interface Props {
    /** `commerce.settings` — 체크리스트·끄기 */
    canEdit?: boolean;
}

withDefaults(defineProps<Props>(), { canEdit: false });

const { t } = useI18n();
const api = useCommerceSettingsApi();
const toApiError = useApiErrorOf();
const toast = useAppToast();
const time = useDisplayTime();
const { isSuper } = useAdminPermission();
const uid = useId();

const view = ref<SalesSwitchView | null>(null);
const loading = ref(false);
const loadError = ref<ApiError | null>(null);
const busyKey = ref<SalesChecklistKey | null>(null);
const actionError = ref('');

async function load(): Promise<void> {
    loading.value = true;
    loadError.value = null;
    try {
        view.value = await api.salesSwitch();
    } catch (e) {
        loadError.value = toApiError(e);
    } finally {
        loading.value = false;
    }
}

async function check(key: SalesChecklistKey, checked: boolean): Promise<void> {
    busyKey.value = key;
    actionError.value = '';
    try {
        view.value = await api.setChecklist(key, checked);
    } catch (e) {
        actionError.value = toApiError(e).message;
    } finally {
        busyKey.value = null;
    }
}

const toggleOpen = ref(false);
const toggleOn = ref(true);
const toggling = ref(false);
const toggleError = ref('');
const toggleFieldError = ref('');
function openToggle(on: boolean): void {
    toggleOn.value = on;
    toggleError.value = '';
    toggleFieldError.value = '';
    toggleOpen.value = true;
}
async function submitToggle(reason: string): Promise<void> {
    toggling.value = true;
    toggleError.value = '';
    toggleFieldError.value = '';
    try {
        view.value = await api.toggleSales({ enabled: toggleOn.value, reason });
        toast.success(toggleOn.value ? t('commerce.salesTurnedOn') : t('commerce.salesTurnedOff'));
        toggleOpen.value = false;
    } catch (e) {
        const failure = toApiError(e);
        toggleFieldError.value = failure.field('reason') ?? '';
        toggleError.value = toggleFieldError.value ? '' : failure.message;
    } finally {
        toggling.value = false;
    }
}

/** 다른 구획(사업자 정보·택배사)이 저장되면 자동 조건을 다시 읽는다 */
defineExpose({ reload: load });

onMounted(load);
</script>
