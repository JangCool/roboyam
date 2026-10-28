<template>
    <!--
        커머스 설정 구획 「택배사」(계약 결정 11) — 6개 시드(`CJ`·`HANJIN`·`LOTTE`·`EPOST`·`LOGEN`·`KDEXP`) + 조회 주소 템플릿(`https://…{no}`, `{no}` 한 번), 켜기/끄기, 차례(위·아래).
        목록 통째로 저장(PUT 한 번 — 구획 맨 아래 저장 줄). 발송 이력이 있는 코드는 지우지 못하고 끄기만(서버 `409 COMMERCE_CARRIER_IN_USE`). 켜진 택배사가 하나는 있어야 판매를 켤 수 있다.
        서버 칸 오류는 `items[i].<칸>` 키로 그 칸 아래에. 서버 호출은 `useCommerceSettingsApi`(목업 — `?mock=carriers-fail`).
    -->
    <AppGadget :title="t('commerce.carrierSection')" anchor="commerce-carriers" :changes="dirty ? 1 : 0" :invalid="problemCount">
        <div v-if="loading && !saved" class="flex flex-col gap-2" role="status" :aria-label="t('common.loading')"><Skeleton v-for="n in 4" :key="n" height="2.5rem" /></div>
        <ErrorState v-else-if="!saved" :title="t('commerce.carrierLoadFailed')" :description="loadError?.message" retry :retrying="loading" @retry="load" />
        <div v-else class="flex flex-col gap-3">
            <p class="m-0 text-xs leading-relaxed text-muted-color">{{ t('commerce.carrierLead') }}</p>
            <div class="overflow-x-auto rounded-lg border border-(--line)">
                <table class="w-full min-w-[46rem] border-collapse text-[13px]">
                    <thead>
                        <tr class="border-b border-(--line) text-left text-xs text-muted-color">
                            <th class="w-28 px-2 py-1.5 font-normal">{{ t('commerce.carrierCode') }}</th>
                            <th class="w-40 px-2 py-1.5 font-normal">{{ t('commerce.carrierName') }}</th>
                            <th class="px-2 py-1.5 font-normal">{{ t('commerce.carrierTemplate') }}</th>
                            <th class="w-16 px-2 py-1.5 font-normal">{{ t('commerce.carrierActive') }}</th>
                            <th class="w-28 px-2 py-1.5"><span class="sr-only">{{ t('commerce.carrierOrder') }}</span></th>
                        </tr>
                    </thead>
                    <tbody>
                        <tr v-for="(row, i) in rows" :key="row.key" class="border-b border-(--line) align-top last:border-b-0">
                            <td class="px-2 py-1.5">
                                <InputText :model-value="row.code" :aria-label="t('commerce.carrierCodeN', { n: i + 1 })" maxlength="20" :readonly="!editable || !row.isNew" :invalid="!!fieldError(i, 'code')" fluid @update:model-value="(v: string | undefined) => patch(i, { code: (v ?? '').toUpperCase() })" />
                                <p v-if="fieldError(i, 'code')" class="m-0 mt-0.5 text-[11px] text-red-600 dark:text-red-400">{{ fieldError(i, 'code') }}</p>
                            </td>
                            <td class="px-2 py-1.5">
                                <InputText :model-value="row.name" :aria-label="t('commerce.carrierNameN', { n: i + 1 })" maxlength="30" :readonly="!editable" :invalid="!!fieldError(i, 'name')" fluid @update:model-value="(v: string | undefined) => patch(i, { name: v ?? '' })" />
                                <p v-if="fieldError(i, 'name')" class="m-0 mt-0.5 text-[11px] text-red-600 dark:text-red-400">{{ fieldError(i, 'name') }}</p>
                            </td>
                            <td class="px-2 py-1.5">
                                <InputText :model-value="row.trackingUrlTemplate" :aria-label="t('commerce.carrierTemplateN', { n: i + 1 })" maxlength="300" :readonly="!editable" :invalid="!!fieldError(i, 'trackingUrlTemplate')" class="font-mono text-[12px]" fluid @update:model-value="(v: string | undefined) => patch(i, { trackingUrlTemplate: v ?? '' })" />
                                <p v-if="fieldError(i, 'trackingUrlTemplate')" class="m-0 mt-0.5 text-[11px] text-red-600 dark:text-red-400">{{ fieldError(i, 'trackingUrlTemplate') }}</p>
                            </td>
                            <td class="px-2 py-1.5"><ToggleSwitch :model-value="row.active" :aria-label="t('commerce.carrierActiveN', { n: i + 1 })" :disabled="!editable" @update:model-value="(v: boolean) => patch(i, { active: v })" /></td>
                            <td class="px-2 py-1.5 text-right whitespace-nowrap">
                                <template v-if="editable">
                                    <Button type="button" icon="pi pi-arrow-up" size="small" severity="secondary" variant="text" :aria-label="t('commerce.carrierUp')" :disabled="i === 0" @click="move(i, -1)" />
                                    <Button type="button" icon="pi pi-arrow-down" size="small" severity="secondary" variant="text" :aria-label="t('commerce.carrierDown')" :disabled="i === rows.length - 1" @click="move(i, 1)" />
                                    <Button v-if="!row.used" type="button" icon="pi pi-trash" size="small" severity="danger" variant="text" :aria-label="t('commerce.carrierRemove')" @click="rows = rows.filter((_, j) => j !== i)" />
                                    <i v-else class="pi pi-lock ml-2 text-xs text-muted-color" :title="t('commerce.carrierUsed')" aria-hidden="true" />
                                </template>
                            </td>
                        </tr>
                    </tbody>
                </table>
            </div>
            <div v-if="editable"><Button type="button" :label="t('commerce.carrierAdd')" icon="pi pi-plus" size="small" severity="secondary" variant="outlined" @click="addRow" /></div>
            <p v-if="!rows.some((r) => r.active)" class="m-0 text-xs text-red-600 dark:text-red-400">{{ t('commerce.carrierNoneActive') }}</p>
            <p class="m-0 text-xs text-muted-color">{{ saved.updatedAt ? t('commerce.lastSaved', { at: time.full(saved.updatedAt), by: saved.updatedBy ?? '—' }) : t('commerce.neverSaved') }}</p>
            <SaveBar v-if="canEdit" :dirty="dirty" :saving="saving" :disabled="problemCount > 0" :error-count="problemCount" :error="formError" @save="save" @revert="applySaved(saved)" />
        </div>
    </AppGadget>
</template>

<script setup lang="ts">
import type { CarrierList } from '~/composables/commerce/api/useCommerceSettingsApi.types';
import type { ApiError } from '~/composables/common/useApiClient';
import UtilGroupBuy from '~/utils/util-groupbuy';

interface Props {
    canEdit?: boolean;
}

const props = withDefaults(defineProps<Props>(), { canEdit: false });
const emit = defineEmits<{ dirty: [dirty: boolean]; saved: [] }>();

interface Row {
    key: string;
    code: string;
    name: string;
    trackingUrlTemplate: string;
    active: boolean;
    used: boolean;
    isNew: boolean;
}

const { t } = useI18n();
const api = useCommerceSettingsApi();
const toApiError = useApiErrorOf();
const toast = useAppToast();
const time = useDisplayTime();

const saved = ref<CarrierList | null>(null);
const rows = ref<Row[]>([]);
const loading = ref(false);
const loadError = ref<ApiError | null>(null);
const saving = ref(false);
const formError = ref('');
const serverErrors = ref<Record<string, string>>({});
const editable = computed(() => props.canEdit && !saving.value);

const snap = (list: Row[]) => JSON.stringify(list.map((r) => [r.code, r.name, r.trackingUrlTemplate, r.active]));
const initial = ref('');
const dirty = computed(() => !!saved.value && snap(rows.value) !== initial.value);
watch(dirty, (d) => emit('dirty', d));

function localProblem(row: Row, field: 'code' | 'name' | 'trackingUrlTemplate'): string {
    if (field === 'code' && !/^[A-Z][A-Z0-9_]{1,19}$/.test(row.code)) return t('commerce.carrierCodeProblem');
    if (field === 'name' && !row.name.trim()) return t('commerce.carrierNameRequired');
    if (field === 'trackingUrlTemplate' && !UtilGroupBuy.carrierTemplateOk(row.trackingUrlTemplate)) return t('commerce.carrierTemplateProblem');
    return '';
}

function fieldError(i: number, field: 'code' | 'name' | 'trackingUrlTemplate'): string {
    const row = rows.value[i];
    return serverErrors.value[`items[${i}].${field}`] ?? (row ? localProblem(row, field) : '');
}

const problemCount = computed(() => rows.value.reduce((n, r) => n + (['code', 'name', 'trackingUrlTemplate'] as const).filter((f) => localProblem(r, f)).length, 0));

function applySaved(view: CarrierList): void {
    saved.value = view;
    rows.value = view.items.map((c) => ({ key: c.code, ...c, isNew: false }));
    initial.value = snap(rows.value);
    serverErrors.value = {};
    formError.value = '';
}

function patch(i: number, change: Partial<Row>): void {
    rows.value = rows.value.map((r, j) => (j === i ? { ...r, ...change } : r));
    serverErrors.value = {};
}

function move(i: number, delta: number): void {
    const next = [...rows.value];
    const [row] = next.splice(i, 1);
    if (!row) return;
    next.splice(i + delta, 0, row);
    rows.value = next;
}

function addRow(): void {
    rows.value = [...rows.value, { key: `new-${Date.now()}`, code: '', name: '', trackingUrlTemplate: 'https://', active: true, used: false, isNew: true }];
}

async function load(): Promise<void> {
    loading.value = true;
    loadError.value = null;
    try {
        applySaved(await api.carriers());
    } catch (e) {
        loadError.value = toApiError(e);
    } finally {
        loading.value = false;
    }
}

async function save(): Promise<void> {
    if (saving.value || !dirty.value || problemCount.value) return;
    saving.value = true;
    formError.value = '';
    serverErrors.value = {};
    try {
        applySaved(await api.saveCarriers({ items: rows.value.map((r) => ({ code: r.code, name: r.name.trim(), trackingUrlTemplate: r.trackingUrlTemplate.trim(), active: r.active })) }));
        toast.success(t('commerce.carrierSaved'));
        emit('saved');
    } catch (e) {
        const failure = toApiError(e);
        if (failure.code === 'VALIDATION' && failure.fields) {
            serverErrors.value = failure.fields;
            formError.value = failure.fields.items ?? t('commerce.saveFieldsRejected');
        } else formError.value = failure.message;
    } finally {
        saving.value = false;
    }
}

onMounted(load);
</script>
