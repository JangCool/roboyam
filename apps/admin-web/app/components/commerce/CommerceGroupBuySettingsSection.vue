<template>
    <!--
        커머스 설정 구획 「공구 설정」(계약 결정 8·10·51·53) — 선점 시간(3~30분) · 기간 상한(성사형·선착순) · 연장 최대·판단 창 · 판정 여유 · 고객 택배비 보상 상한 · 고액 환불 문턱(0 = 끔)
        · 배송지 열람 화면 유지 · 만료 주문 배송지 보관 · 즉시 발송 기본 N영업일 · 공급 불가 선택 기한. 영업일 계산의 휴일은 여기서 정하지 않는다 — 휴일 관리(`/settings/holidays`, 사이트 휴일 표 하나)로 가는 링크만 둔다.
        범위·기본값은 서버가 준다(`ranges`·`defaults`). 칸은 `InputText` + `inputmode=numeric`. 이 구획만 저장한다(PUT 한 번 — 구획 맨 아래 저장 줄). 바꿀 때 감사(전→후).
        돈과 시각에 닿는 값은 서버가 트랜잭션 안에서 DB 의 지금 값을 읽는다(결정 19) — 저장하면 다음 요청부터 쓴다.
    -->
    <AppGadget :title="t('commerce.gbSection')" anchor="commerce-groupbuy" :changes="changed.length" :invalid="problemCount">
        <div v-if="loading && !saved" class="flex flex-col gap-2" role="status" :aria-label="t('common.loading')"><Skeleton v-for="n in 5" :key="n" height="2.5rem" /></div>
        <ErrorState v-else-if="!saved" :title="t('commerce.gbLoadFailed')" :description="loadError?.message" retry :retrying="loading" @retry="load" />
        <div v-else class="flex flex-col gap-4">
            <p class="m-0 text-xs leading-relaxed text-muted-color">{{ t('commerce.gbLead') }}</p>
            <ul class="m-0 flex list-none flex-col divide-y divide-(--line) rounded-lg border border-(--line) p-0">
                <li v-for="key in KEYS" :key="key" class="flex flex-col gap-2 px-3 py-3 sm:flex-row sm:items-start sm:justify-between sm:gap-4">
                    <div class="min-w-0 flex-1">
                        <label :for="`${uid}-${key}`" class="text-sm font-medium">{{ t(`commerce.gbField.${key}`) }}</label>
                        <p class="m-0 mt-0.5 text-xs leading-relaxed text-muted-color">{{ t(`commerce.gbFieldDesc.${key}`) }}</p>
                    </div>
                    <div class="flex flex-col gap-1 sm:items-end">
                        <div class="flex items-center gap-2">
                            <Tag v-if="key === 'largeRefundThreshold' && UtilGroupBuy.int(form[key]) === 0" :value="t('commerce.gbOff')" severity="warn" />
                            <InputText :id="`${uid}-${key}`" :model-value="form[key]" inputmode="numeric" autocomplete="off" :readonly="!editable" :invalid="!!errorOf(key)" class="w-28 text-right tabular-nums" @update:model-value="(v: string | undefined) => onInput(key, v ?? '')" />
                            <span class="w-14 text-sm whitespace-nowrap text-muted-color">{{ t(`commerce.gbUnit.${UNITS[key]}`) }}</span>
                        </div>
                        <p v-if="errorOf(key)" role="alert" class="m-0 text-xs text-red-600 dark:text-red-400">{{ errorOf(key) }}</p>
                        <p v-else class="m-0 text-xs text-muted-color">{{ t('commerce.gbRange', { min: saved.ranges[key].min, max: saved.ranges[key].max, def: saved.defaults[key] }) }}</p>
                    </div>
                </li>
            </ul>

            <!-- 휴일 — 영업일 계산의 쉬는 날은 사이트 휴일 표 하나(휴일 관리)에서 정한다 -->
            <p class="m-0 flex items-start gap-1.5 text-xs leading-relaxed text-muted-color">
                <i class="pi pi-calendar mt-0.5 text-xs" aria-hidden="true" />
                <span>{{ t('commerce.gbHolidaysMoved') }} <NuxtLink to="/settings/holidays" class="font-medium text-primary underline">{{ t('commerce.gbHolidaysLink') }}</NuxtLink></span>
            </p>

            <p class="m-0 text-xs text-muted-color">{{ saved.updatedAt ? t('commerce.lastSaved', { at: time.full(saved.updatedAt), by: saved.updatedBy ?? '—' }) : t('commerce.neverSaved') }}</p>
            <SaveBar v-if="canEdit" :dirty="changed.length > 0" :count="changed.length" :saving="saving" :disabled="problemCount > 0" :error-count="problemCount" :error="formError" @save="save" @revert="applySaved(saved)" />
        </div>
    </AppGadget>
</template>

<script setup lang="ts">
import type { GroupBuySettingsNumberKey, GroupBuySettingsView } from '~/composables/commerce/api/useCommerceSettingsApi.types';
import type { ApiError } from '~/composables/common/useApiClient';
import UtilGroupBuy from '~/utils/util-groupbuy';

interface Props {
    canEdit?: boolean;
}

const props = withDefaults(defineProps<Props>(), { canEdit: false });
const emit = defineEmits<{ dirty: [dirty: boolean] }>();

const KEYS: GroupBuySettingsNumberKey[] = ['holdMinutes', 'thresholdPeriodMaxDays', 'firstComePeriodMaxDays', 'extensionMaxMinutes', 'extensionWindowMinutes', 'judgeGraceMinutes', 'compensationCap', 'largeRefundThreshold', 'addressRevealMinutes', 'expiredAddressRetentionDays', 'shipWithinBusinessDays', 'supplyChoiceBusinessDays'];
const UNITS: Record<GroupBuySettingsNumberKey, 'minute' | 'day' | 'won' | 'businessDay'> = {
    holdMinutes: 'minute',
    thresholdPeriodMaxDays: 'day',
    firstComePeriodMaxDays: 'day',
    extensionMaxMinutes: 'minute',
    extensionWindowMinutes: 'minute',
    judgeGraceMinutes: 'minute',
    compensationCap: 'won',
    largeRefundThreshold: 'won',
    addressRevealMinutes: 'minute',
    expiredAddressRetentionDays: 'day',
    shipWithinBusinessDays: 'businessDay',
    supplyChoiceBusinessDays: 'businessDay',
};

const { t } = useI18n();
const api = useCommerceSettingsApi();
const toApiError = useApiErrorOf();
const toast = useAppToast();
const time = useDisplayTime();
const uid = useId();

const saved = ref<GroupBuySettingsView | null>(null);
const form = ref<Record<GroupBuySettingsNumberKey, string>>(Object.fromEntries(KEYS.map((k) => [k, ''])) as Record<GroupBuySettingsNumberKey, string>);
const loading = ref(false);
const loadError = ref<ApiError | null>(null);
const saving = ref(false);
const formError = ref('');
const serverErrors = ref<Record<string, string>>({});
const editable = computed(() => props.canEdit && !saving.value);

const changed = computed<string[]>(() => {
    const s = saved.value;
    if (!s) return [];
    return KEYS.filter((k) => UtilGroupBuy.int(form.value[k]) !== s.values[k]);
});
watch(
    () => changed.value.length > 0,
    (d) => emit('dirty', d),
);

function localProblem(key: GroupBuySettingsNumberKey): string {
    const s = saved.value;
    if (!s) return '';
    const text = form.value[key];
    if (!text.trim()) return t('commerce.limitRequired');
    const n = UtilGroupBuy.int(text);
    if (n === null) return t('commerce.limitNotInteger');
    const r = s.ranges[key];
    return n < r.min || n > r.max ? t('commerce.gbOutOfRange', { min: r.min, max: r.max }) : '';
}

function errorOf(key: GroupBuySettingsNumberKey): string {
    return localProblem(key) || serverErrors.value[`values.${key}`] || '';
}

const problemCount = computed(() => KEYS.filter((k) => localProblem(k)).length);

function onInput(key: GroupBuySettingsNumberKey, value: string): void {
    form.value = { ...form.value, [key]: value };
    const field = `values.${key}`;
    if (serverErrors.value[field]) {
        const { [field]: _drop, ...rest } = serverErrors.value;
        serverErrors.value = rest;
    }
}

function applySaved(view: GroupBuySettingsView): void {
    saved.value = view;
    form.value = Object.fromEntries(KEYS.map((k) => [k, String(view.values[k])])) as Record<GroupBuySettingsNumberKey, string>;
    serverErrors.value = {};
    formError.value = '';
}

async function load(): Promise<void> {
    loading.value = true;
    loadError.value = null;
    try {
        applySaved(await api.groupBuySettings());
    } catch (e) {
        loadError.value = toApiError(e);
    } finally {
        loading.value = false;
    }
}

async function save(): Promise<void> {
    if (saving.value || !changed.value.length || problemCount.value) return;
    saving.value = true;
    formError.value = '';
    serverErrors.value = {};
    try {
        const numbers = Object.fromEntries(KEYS.map((k) => [k, UtilGroupBuy.int(form.value[k]) ?? 0])) as Record<GroupBuySettingsNumberKey, number>;
        applySaved(await api.saveGroupBuySettings({ values: numbers }));
        toast.success(t('commerce.gbSaved'));
    } catch (e) {
        const failure = toApiError(e);
        if (failure.code === 'VALIDATION' && failure.fields) {
            serverErrors.value = failure.fields;
            formError.value = t('commerce.saveFieldsRejected');
        } else formError.value = failure.message;
    } finally {
        saving.value = false;
    }
}

onMounted(load);
</script>
