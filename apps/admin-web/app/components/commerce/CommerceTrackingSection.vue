<template>
    <!--
        커머스 설정 구획 「배송 조회(스마트택배)」(계약 결정 90) — 택배사 구획 바로 아래. 스마트택배(스윗트래커) 조회 API 로 발송 줄의 배송 단계를 읽어 배송완료를 자동 반영한다.
        ① 설정(구획 맨 아래 저장 줄 — PUT 한 번): 자동 조회 켜기 · 조회 주기(시간 단위 선택, 범위는 서버 `ranges.intervalMinutes`) · 발송 뒤 N일 자동 배송완료 켜기 + N(범위 `ranges.autoDeliverDays`).
        ② API 키(따로 바로 저장): 값은 응답에 없다 — 「설정됨/없음」만. 입력칸은 password · autocomplete off, 저장 뒤 비운다. 지우기는 확인. 서버 암호화 키가 없으면(`cryptoConfigured=false`) 입력을 막고 안내.
        ③ 이용권: 외부 호출이라 [이용권 확인]을 눌러서만 부른다. 키 오류 알림(`keyAlertCode`)이 있으면 맨 위 경고.
        권한 `commerce.settings`(막는 것은 서버). 서버 칸 오류 `fields.values.<칸>`·`fields.apiKey` 는 그 칸 아래에 서버 문구 그대로.
    -->
    <AppGadget :title="t('commerce.trkSection')" anchor="commerce-tracking" :changes="changed.length" :invalid="problemCount">
        <div v-if="loading && !saved" class="flex flex-col gap-2" role="status" :aria-label="t('common.loading')"><Skeleton v-for="n in 4" :key="n" height="2.5rem" /></div>
        <ErrorState v-else-if="!saved" :title="t('commerce.trkLoadFailed')" :description="loadError?.message" retry :retrying="loading" @retry="load" />
        <div v-else class="flex flex-col gap-4">
            <p class="m-0 text-xs leading-relaxed text-muted-color">
                {{ t('commerce.trkLead') }}
                <a :href="SWEET_TRACKER_URL" target="_blank" rel="noopener noreferrer" class="font-medium text-primary underline">{{ t('commerce.trkSignupLink') }}<span class="sr-only"> {{ t('commerce.trkNewWindow') }}</span> <i class="pi pi-external-link text-[10px]" aria-hidden="true" /></a>
            </p>

            <!-- 키 오류 알림 — 자동 조회가 멈춘 상태 -->
            <Message v-if="saved.keyAlertCode" severity="warn" :closable="false" size="small">
                {{ keyAlertText(saved.keyAlertCode) }}<template v-if="saved.keyAlertAt"> ({{ time.full(saved.keyAlertAt) }})</template>
            </Message>

            <!-- ① 설정 -->
            <ul class="m-0 flex list-none flex-col divide-y divide-(--line) rounded-lg border border-(--line) p-0">
                <li class="flex flex-col gap-2 px-3 py-3 sm:flex-row sm:items-start sm:justify-between sm:gap-4">
                    <div class="min-w-0 flex-1">
                        <label :for="`${uid}-enabled`" class="text-sm font-medium">{{ t('commerce.trkEnabled') }}</label>
                        <p class="m-0 mt-0.5 text-xs leading-relaxed text-muted-color">{{ t('commerce.trkEnabledDesc') }}</p>
                    </div>
                    <ToggleSwitch :input-id="`${uid}-enabled`" :model-value="form.enabled" :disabled="!editable" @update:model-value="(v: boolean) => patch({ enabled: v })" />
                </li>

                <li class="flex flex-col gap-2 px-3 py-3 sm:flex-row sm:items-start sm:justify-between sm:gap-4">
                    <div class="min-w-0 flex-1">
                        <label :for="`${uid}-interval`" class="text-sm font-medium">{{ t('commerce.trkInterval') }}</label>
                        <p class="m-0 mt-0.5 text-xs leading-relaxed text-muted-color">{{ t('commerce.trkIntervalDesc') }}</p>
                    </div>
                    <div class="flex flex-col gap-1 sm:items-end">
                        <Select :input-id="`${uid}-interval`" :model-value="form.intervalMinutes" :options="intervalOptions" option-label="label" option-value="value" :disabled="!editable" :invalid="!!errorOf('intervalMinutes')" class="w-36" @update:model-value="(v: number) => patch({ intervalMinutes: v })" />
                        <p v-if="errorOf('intervalMinutes')" role="alert" class="m-0 text-xs text-red-600 dark:text-red-400">{{ errorOf('intervalMinutes') }}</p>
                        <p v-else class="m-0 text-xs text-muted-color">{{ t('commerce.trkDefault', { value: intervalLabel(saved.defaults.intervalMinutes) }) }}</p>
                    </div>
                </li>

                <li class="flex flex-col gap-2 px-3 py-3">
                    <div class="flex flex-col gap-2 sm:flex-row sm:items-start sm:justify-between sm:gap-4">
                        <div class="min-w-0 flex-1">
                            <label :for="`${uid}-auto`" class="text-sm font-medium">{{ t('commerce.trkAutoDeliver') }}</label>
                            <p class="m-0 mt-0.5 text-xs leading-relaxed text-muted-color">{{ t('commerce.trkAutoDeliverDesc') }}</p>
                        </div>
                        <div class="flex flex-col gap-1 sm:items-end">
                            <div class="flex items-center gap-2">
                                <ToggleSwitch :input-id="`${uid}-auto`" :model-value="form.autoDeliverEnabled" :disabled="!editable" @update:model-value="(v: boolean) => patch({ autoDeliverEnabled: v })" />
                                <InputText
                                    :id="`${uid}-days`"
                                    :model-value="form.autoDeliverDays"
                                    inputmode="numeric"
                                    autocomplete="off"
                                    :aria-label="t('commerce.trkAutoDeliverDays')"
                                    :readonly="!editable"
                                    :disabled="!form.autoDeliverEnabled"
                                    :invalid="!!errorOf('autoDeliverDays')"
                                    class="w-20 text-right tabular-nums"
                                    @update:model-value="(v: string | undefined) => patch({ autoDeliverDays: v ?? '' })"
                                />
                                <span class="text-sm whitespace-nowrap text-muted-color">{{ t('commerce.trkDaysUnit') }}</span>
                            </div>
                            <p v-if="errorOf('autoDeliverDays')" role="alert" class="m-0 text-xs text-red-600 dark:text-red-400">{{ errorOf('autoDeliverDays') }}</p>
                            <p v-else class="m-0 text-xs text-muted-color">{{ t('commerce.gbRange', { min: saved.ranges.autoDeliverDays.min, max: saved.ranges.autoDeliverDays.max, def: saved.defaults.autoDeliverDays }) }}</p>
                        </div>
                    </div>
                    <Message v-if="form.autoDeliverEnabled" severity="warn" :closable="false" size="small">{{ t('commerce.trkAutoDeliverWarn') }}</Message>
                </li>
            </ul>

            <p class="m-0 text-xs text-muted-color">{{ saved.updatedAt ? t('commerce.lastSaved', { at: time.full(saved.updatedAt), by: saved.updatedBy ?? '—' }) : t('commerce.neverSaved') }}</p>
            <SaveBar v-if="canEdit" :dirty="changed.length > 0" :count="changed.length" :saving="saving" :disabled="problemCount > 0" :error-count="problemCount" :error="formError" @save="save" @revert="applySaved(saved)" />

            <!-- ② API 키 — 바로 저장(값은 화면에 다시 보이지 않는다) -->
            <section class="flex flex-col gap-2 rounded-lg border border-(--line) px-3 py-3" :aria-labelledby="`${uid}-key-title`">
                <div class="flex flex-wrap items-center gap-2">
                    <h3 :id="`${uid}-key-title`" class="m-0 text-sm font-medium">{{ t('commerce.trkKey') }}</h3>
                    <Tag v-if="saved.apiKeyConfigured" :value="t('commerce.trkKeySet')" severity="success" icon="pi pi-check" />
                    <Tag v-else :value="t('commerce.trkKeyNone')" severity="secondary" />
                    <div v-if="canEdit && !keyEditing" class="ml-auto flex gap-1">
                        <Button type="button" :label="saved.apiKeyConfigured ? t('commerce.trkKeyChange') : t('commerce.trkKeyAdd')" icon="pi pi-key" size="small" severity="secondary" variant="outlined" :disabled="!saved.cryptoConfigured || keyBusy" @click="startKeyEdit" />
                        <Button v-if="saved.apiKeyConfigured" type="button" :label="t('commerce.trkKeyDelete')" icon="pi pi-trash" size="small" severity="danger" variant="text" :loading="keyBusy && !keyEditing" :disabled="keyBusy" @click="deleteKey" />
                    </div>
                </div>
                <p class="m-0 text-xs text-muted-color">
                    {{ saved.keyUpdatedAt ? t('commerce.trkKeyUpdated', { at: time.full(saved.keyUpdatedAt), by: saved.keyUpdatedBy ?? '—' }) : t('commerce.trkKeyHelp') }}
                </p>
                <Message v-if="!saved.cryptoConfigured" severity="warn" :closable="false" size="small">{{ t('commerce.trkCryptoMissing') }}</Message>

                <form v-if="keyEditing" class="flex flex-col gap-1" novalidate @submit.prevent="saveKey">
                    <label :for="`${uid}-key`" class="text-xs text-muted-color">{{ t('commerce.trkKeyInput') }}</label>
                    <div class="flex flex-wrap items-center gap-2">
                        <InputText :id="`${uid}-key`" v-model="keyInput" type="password" autocomplete="off" spellcheck="false" maxlength="200" :readonly="keyBusy" :invalid="!!keyError" :aria-describedby="`${uid}-key-msg`" class="min-w-0 flex-1 font-mono text-[12px]" />
                        <Button type="submit" :label="t('common.save')" size="small" :loading="keyBusy" :disabled="!keyInput.trim()" />
                        <Button type="button" :label="t('common.cancel')" size="small" severity="secondary" variant="text" :disabled="keyBusy" @click="cancelKeyEdit" />
                    </div>
                    <p :id="`${uid}-key-msg`" aria-live="polite" class="m-0 text-xs" :class="keyError ? 'text-red-600 dark:text-red-400' : 'text-muted-color'">{{ keyError || t('commerce.trkKeyInputHelp') }}</p>
                </form>
            </section>

            <!-- ③ 이용권 — 눌러서만 스마트택배를 부른다 -->
            <section class="flex flex-col gap-2 rounded-lg border border-(--line) px-3 py-3" :aria-labelledby="`${uid}-usage-title`">
                <div class="flex flex-wrap items-center gap-2">
                    <h3 :id="`${uid}-usage-title`" class="m-0 text-sm font-medium">{{ t('commerce.trkUsage') }}</h3>
                    <Button type="button" :label="t('commerce.trkUsageCheck')" icon="pi pi-refresh" size="small" severity="secondary" variant="outlined" class="ml-auto" :loading="usageBusy" :disabled="!saved.apiKeyConfigured" @click="checkUsage" />
                </div>
                <p v-if="!saved.apiKeyConfigured" class="m-0 text-xs text-muted-color">{{ t('commerce.trkUsageNeedKey') }}</p>
                <p v-else-if="!usage && !usageError" class="m-0 text-xs text-muted-color">{{ t('commerce.trkUsageHelp') }}</p>
                <p v-if="usageError" role="alert" class="m-0 text-xs text-red-600 dark:text-red-400">{{ usageError }}</p>
                <template v-if="usage">
                    <dl v-if="usage.outcome === 'OK'" class="m-0 grid grid-cols-[minmax(0,7rem)_minmax(0,1fr)] gap-x-3 gap-y-1 text-[13px]">
                        <dt class="text-muted-color">{{ t('commerce.trkUsageLeft') }}</dt>
                        <dd class="m-0 font-medium tabular-nums">{{ usage.leftAmount ?? '—' }} / {{ usage.totalAmount ?? '—' }}</dd>
                        <dt class="text-muted-color">{{ t('commerce.trkUsagePeriod') }}</dt>
                        <dd class="m-0 tabular-nums">{{ usage.startDate ?? '—' }} ~ {{ usage.endDate ?? '—' }}</dd>
                    </dl>
                    <Message v-else severity="warn" :closable="false" size="small">{{ usageFailText(usage.errorCode) }}</Message>
                    <p class="m-0 text-[11px] text-muted-color">{{ t('commerce.trkUsageCheckedAt', { at: time.full(usage.checkedAt) }) }}</p>
                </template>
            </section>
        </div>
    </AppGadget>
</template>

<script setup lang="ts">
import type { TrackingSettingsValues, TrackingSettingsView, TrackingUsageView } from '~/composables/commerce/api/useCommerceSettingsApi.types';
import type { ApiError } from '~/composables/common/useApiClient';
import UtilGroupBuy from '~/utils/util-groupbuy';

interface Props {
    canEdit?: boolean;
}

const props = withDefaults(defineProps<Props>(), { canEdit: false });
const emit = defineEmits<{ dirty: [dirty: boolean] }>();

/** 스마트택배 가입·키 발급(새 창) */
const SWEET_TRACKER_URL = 'https://tracking.sweettracker.co.kr/';
/** 조회 주기 고르기 — 시간 단위(서버 범위 안만 보인다) */
const INTERVAL_HOURS = [1, 2, 3, 4, 6, 8, 12, 24];

type NumberKey = 'intervalMinutes' | 'autoDeliverDays';
interface Form {
    enabled: boolean;
    intervalMinutes: number;
    autoDeliverEnabled: boolean;
    autoDeliverDays: string;
}

const { t, te } = useI18n();
const api = useCommerceSettingsApi();
const toApiError = useApiErrorOf();
const toast = useAppToast();
const time = useDisplayTime();
const { confirm } = useConfirmDialog();
const uid = useId();

const saved = ref<TrackingSettingsView | null>(null);
const form = ref<Form>({ enabled: false, intervalMinutes: 180, autoDeliverEnabled: false, autoDeliverDays: '' });
const loading = ref(false);
const loadError = ref<ApiError | null>(null);
const saving = ref(false);
const formError = ref('');
const serverErrors = ref<Record<string, string>>({});
const editable = computed(() => props.canEdit && !saving.value);

function intervalLabel(minutes: number): string {
    return minutes % 60 === 0 ? t('commerce.trkHours', { n: minutes / 60 }) : t('commerce.trkMinutes', { n: minutes });
}

/** 범위 안의 시간 단위 + 지금 값(시간 단위가 아니어도 그대로 고를 수 있게) */
const intervalOptions = computed(() => {
    const s = saved.value;
    if (!s) return [];
    const { min, max } = s.ranges.intervalMinutes;
    const values = INTERVAL_HOURS.map((h) => h * 60).filter((m) => m >= min && m <= max);
    for (const extra of [form.value.intervalMinutes, s.values.intervalMinutes]) if (!values.includes(extra)) values.push(extra);
    return values.sort((a, b) => a - b).map((value) => ({ value, label: intervalLabel(value) }));
});

const changed = computed<string[]>(() => {
    const s = saved.value;
    if (!s) return [];
    const out: string[] = [];
    if (form.value.enabled !== s.values.enabled) out.push('enabled');
    if (form.value.intervalMinutes !== s.values.intervalMinutes) out.push('intervalMinutes');
    if (form.value.autoDeliverEnabled !== s.values.autoDeliverEnabled) out.push('autoDeliverEnabled');
    if (UtilGroupBuy.int(form.value.autoDeliverDays) !== s.values.autoDeliverDays) out.push('autoDeliverDays');
    return out;
});
watch(
    () => changed.value.length > 0,
    (d) => emit('dirty', d),
);

function localProblem(key: NumberKey): string {
    const s = saved.value;
    if (!s) return '';
    const r = s.ranges[key];
    if (key === 'intervalMinutes') {
        const n = form.value.intervalMinutes;
        return n < r.min || n > r.max ? t('commerce.gbOutOfRange', { min: r.min, max: r.max }) : '';
    }
    const text = form.value.autoDeliverDays;
    if (!text.trim()) return t('commerce.limitRequired');
    const n = UtilGroupBuy.int(text);
    if (n === null) return t('commerce.limitNotInteger');
    return n < r.min || n > r.max ? t('commerce.gbOutOfRange', { min: r.min, max: r.max }) : '';
}

function errorOf(key: NumberKey): string {
    return localProblem(key) || serverErrors.value[`values.${key}`] || '';
}

const problemCount = computed(() => (['intervalMinutes', 'autoDeliverDays'] as const).filter((k) => localProblem(k)).length);

function patch(change: Partial<Form>): void {
    form.value = { ...form.value, ...change };
    serverErrors.value = {};
}

function applySaved(view: TrackingSettingsView): void {
    saved.value = view;
    form.value = { enabled: view.values.enabled, intervalMinutes: view.values.intervalMinutes, autoDeliverEnabled: view.values.autoDeliverEnabled, autoDeliverDays: String(view.values.autoDeliverDays) };
    serverErrors.value = {};
    formError.value = '';
}

/** 키 저장·지우기 뒤 — 설정 칸에 저장하지 않은 값이 있으면 입력은 그대로 두고 응답(키 상태)만 받는다 */
function applyKeyState(view: TrackingSettingsView): void {
    if (changed.value.length) saved.value = view;
    else applySaved(view);
}

async function load(): Promise<void> {
    loading.value = true;
    loadError.value = null;
    try {
        applySaved(await api.trackingSettings());
    } catch (e) {
        loadError.value = toApiError(e);
    } finally {
        loading.value = false;
    }
}

async function save(): Promise<void> {
    if (saving.value || !changed.value.length || problemCount.value) return;
    const days = UtilGroupBuy.int(form.value.autoDeliverDays);
    if (days === null) return;
    saving.value = true;
    formError.value = '';
    serverErrors.value = {};
    try {
        const values: TrackingSettingsValues = { enabled: form.value.enabled, intervalMinutes: form.value.intervalMinutes, autoDeliverEnabled: form.value.autoDeliverEnabled, autoDeliverDays: days };
        applySaved(await api.saveTrackingSettings({ values }));
        toast.success(t('commerce.trkSaved'));
    } catch (e) {
        const failure = toApiError(e);
        if (failure.code === 'VALIDATION' && failure.fields) {
            serverErrors.value = failure.fields;
            formError.value = failure.fields.values ?? t('commerce.saveFieldsRejected');
        } else formError.value = failure.message;
    } finally {
        saving.value = false;
    }
}

// ── API 키 ──
const keyEditing = ref(false);
const keyInput = ref('');
const keyError = ref('');
const keyBusy = ref(false);

function startKeyEdit(): void {
    keyInput.value = '';
    keyError.value = '';
    keyEditing.value = true;
}

function cancelKeyEdit(): void {
    keyInput.value = '';
    keyError.value = '';
    keyEditing.value = false;
}

async function saveKey(): Promise<void> {
    const value = keyInput.value.trim();
    if (keyBusy.value || !value) return;
    keyBusy.value = true;
    keyError.value = '';
    try {
        applyKeyState(await api.saveTrackingKey(value));
        cancelKeyEdit();
        usage.value = null;
        usageError.value = '';
        toast.success(t('commerce.trkKeySaved'));
    } catch (e) {
        const failure = toApiError(e);
        keyError.value = failure.field('apiKey') ?? failure.message;
    } finally {
        keyBusy.value = false;
    }
}

async function deleteKey(): Promise<void> {
    if (keyBusy.value) return;
    const ok = await confirm({ title: t('commerce.trkKeyDeleteTitle'), message: t('commerce.trkKeyDeleteMessage'), confirmLabel: t('commerce.trkKeyDelete'), tone: 'danger' });
    if (!ok) return;
    keyBusy.value = true;
    try {
        applyKeyState(await api.deleteTrackingKey());
        usage.value = null;
        usageError.value = '';
        toast.success(t('commerce.trkKeyDeleted'));
    } catch (e) {
        toast.error(toApiError(e).message, 8000);
    } finally {
        keyBusy.value = false;
    }
}

// ── 이용권 ──
const usage = ref<TrackingUsageView | null>(null);
const usageError = ref('');
const usageBusy = ref(false);

async function checkUsage(): Promise<void> {
    if (usageBusy.value) return;
    usageBusy.value = true;
    usageError.value = '';
    try {
        usage.value = await api.trackingUsage();
    } catch (e) {
        usage.value = null;
        usageError.value = toApiError(e).message;
    } finally {
        usageBusy.value = false;
    }
}

/** 키 오류 알림 — 101·102·103 은 뜻을, 그 밖은 코드와 함께 */
function keyAlertText(code: string): string {
    const key = `commerce.trkKeyAlert.${code}`;
    return te(key) ? t(key) : t('commerce.trkKeyAlertOther', { code });
}

function usageFailText(code: string | null): string {
    const key = `commerce.trkUsageFail.${code ?? ''}`;
    return code && te(key) ? t(key) : t('commerce.trkUsageFailOther', { code: code ?? '—' });
}

onMounted(load);
</script>
