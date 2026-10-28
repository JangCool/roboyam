<template>
    <!--
        커머스 설정 구획 「배송비 정책」(commerce.md 10-2·10-8, 계약 결정 51) — 정책 코드별 기간 줄 목록. 같은 코드의 기간은 겹치지 않고, **적용이 시작된 줄은 고치거나 지우지 못한다**(새 줄로만 바꾼다).
        공구는 모집 시작 때 그 시각에 유효한 줄을 복사해 끝날 때까지 쓴다. 이 구획의 저장 단위는 「새 줄 더하기」(줄 하나씩 POST)와 「시작 전 줄 지우기」(DELETE)다 — 고치는 칸이 없어 저장 줄을 두지 않는다.
        왕복(`ROUND_TRIP`)을 고르면 경고(법에 명시된 허용 규정 없음 · 금액까지 미리 고지). 감사 `COMMERCE_SHIPPING_POLICY_*`(돈 관련). 서버 호출은 `useCommerceSettingsApi`(목업 — `?mock=policies-fail`).
    -->
    <AppGadget :title="t('commerce.policySection')" anchor="commerce-shipping-policies">
        <div v-if="loading && !items" class="flex flex-col gap-2" role="status" :aria-label="t('common.loading')"><Skeleton v-for="n in 3" :key="n" height="2.5rem" /></div>
        <ErrorState v-else-if="!items" :title="t('commerce.policyLoadFailed')" :description="loadError?.message" retry :retrying="loading" @retry="load" />
        <div v-else class="flex flex-col gap-4">
            <p class="m-0 text-xs leading-relaxed text-muted-color">{{ t('commerce.policyLead') }}</p>

            <div v-for="group in groups" :key="group.code" class="flex flex-col gap-1.5">
                <span class="text-sm font-medium">{{ group.name }} <code class="text-xs text-muted-color">{{ group.code }}</code></span>
                <div class="overflow-x-auto rounded-lg border border-(--line)">
                    <table class="w-full min-w-[42rem] border-collapse text-[13px]">
                        <thead>
                            <tr class="border-b border-(--line) text-left text-xs text-muted-color">
                                <th class="px-3 py-1.5 font-normal">{{ t('commerce.policyPeriod') }}</th>
                                <th class="px-3 py-1.5 text-right font-normal">{{ t('commerce.policyBaseFee') }}</th>
                                <th class="px-3 py-1.5 text-right font-normal">{{ t('commerce.policyFreeOver') }}</th>
                                <th class="px-3 py-1.5 text-right font-normal">{{ t('commerce.policyReturnFee') }}</th>
                                <th class="px-3 py-1.5 font-normal">{{ t('commerce.policyMode') }}</th>
                                <th class="px-3 py-1.5 font-normal">{{ t('commerce.policyState') }}</th>
                                <th v-if="canEdit" class="px-3 py-1.5"><span class="sr-only">{{ t('common.delete') }}</span></th>
                            </tr>
                        </thead>
                        <tbody>
                            <tr v-for="line in group.lines" :key="line.id" class="border-b border-(--line) last:border-b-0">
                                <td class="px-3 py-1.5 text-[12px] tabular-nums">{{ time.shortDateTime(line.effectiveFrom) }} ~ {{ line.effectiveTo ? time.shortDateTime(line.effectiveTo) : t('commerce.policyOngoing') }}</td>
                                <td class="px-3 py-1.5 text-right tabular-nums">{{ won(line.baseFee) }}</td>
                                <td class="px-3 py-1.5 text-right tabular-nums">{{ line.freeShippingOver === null ? t('commerce.policyNoFree') : won(line.freeShippingOver) }}</td>
                                <td class="px-3 py-1.5 text-right tabular-nums">{{ won(line.returnFee) }}</td>
                                <td class="px-3 py-1.5">{{ t(`commerce.returnMode.${line.returnFeeMode}`) }}</td>
                                <td class="px-3 py-1.5">
                                    <Tag v-if="line.current" :value="t('commerce.policyCurrent')" severity="success" />
                                    <Tag v-else-if="!line.started" :value="t('commerce.policyUpcoming')" severity="info" />
                                    <Tag v-else :value="t('commerce.policyPast')" severity="secondary" />
                                </td>
                                <td v-if="canEdit" class="px-3 py-1.5 text-right">
                                    <Button v-if="!line.started" type="button" icon="pi pi-trash" size="small" severity="danger" variant="text" :aria-label="t('commerce.policyDelete')" :loading="deleting === line.id" @click="remove(line)" />
                                    <i v-else class="pi pi-lock text-xs text-muted-color" :title="t('commerce.policyLocked')" aria-hidden="true" />
                                </td>
                            </tr>
                        </tbody>
                    </table>
                </div>
            </div>

            <!-- 새 줄 -->
            <form v-if="canEdit" class="flex flex-col gap-3 rounded-lg border border-dashed border-(--line) p-3" novalidate @submit.prevent="add">
                <span class="text-sm font-medium">{{ t('commerce.policyAdd') }}</span>
                <div class="grid gap-3 md:grid-cols-3">
                    <LabeledField :label="t('commerce.policyCode')" :help="t('commerce.policyCodeHelp')" :error="fieldErrors.code">
                        <template #default="{ id, describedBy, invalid }">
                            <AutoComplete :input-id="id" v-model="draft.code" :suggestions="codeSuggestions" dropdown :invalid="invalid" :aria-describedby="describedBy" fluid @complete="onComplete" @update:model-value="onCode" />
                        </template>
                    </LabeledField>
                    <LabeledField :label="t('commerce.policyName')" :error="fieldErrors.name">
                        <template #default="{ id, describedBy, invalid }">
                            <InputText :id="id" v-model="draft.name" maxlength="50" :invalid="invalid" :aria-describedby="describedBy" fluid />
                        </template>
                    </LabeledField>
                    <LabeledField :label="t('commerce.policyFrom')" :help="t('commerce.policyFromHelp')" :error="fieldErrors.effectiveFrom">
                        <template #default="{ id, describedBy, invalid }">
                            <AppDateTimePicker v-model="draft.effectiveFrom" :input-id="id" :min="nowIso" :invalid="invalid" :described-by="describedBy" />
                        </template>
                    </LabeledField>
                    <LabeledField :label="t('commerce.policyBaseFee')" :error="fieldErrors.baseFee">
                        <template #default="{ id, describedBy, invalid }">
                            <InputText :id="id" v-model="draft.baseFee" inputmode="numeric" :invalid="invalid" :aria-describedby="describedBy" class="w-32 text-right tabular-nums" />
                        </template>
                    </LabeledField>
                    <LabeledField :label="t('commerce.policyFreeOver')" :help="t('commerce.policyFreeOverHelp')" :error="fieldErrors.freeShippingOver">
                        <template #default="{ id, describedBy, invalid }">
                            <InputText :id="id" v-model="draft.freeShippingOver" inputmode="numeric" :placeholder="t('commerce.policyNoFree')" :invalid="invalid" :aria-describedby="describedBy" class="w-32 text-right tabular-nums" />
                        </template>
                    </LabeledField>
                    <LabeledField :label="t('commerce.policyReturnFee')" :error="fieldErrors.returnFee">
                        <template #default="{ id, describedBy, invalid }">
                            <InputText :id="id" v-model="draft.returnFee" inputmode="numeric" :invalid="invalid" :aria-describedby="describedBy" class="w-32 text-right tabular-nums" />
                        </template>
                    </LabeledField>
                </div>
                <div class="flex flex-col gap-1.5">
                    <span :id="modeId" class="text-sm font-medium">{{ t('commerce.policyMode') }}</span>
                    <div role="radiogroup" :aria-labelledby="modeId" class="flex flex-wrap gap-4">
                        <label v-for="m in modes" :key="m" class="flex cursor-pointer items-center gap-2 text-[13px]">
                            <RadioButton v-model="draft.returnFeeMode" :value="m" :name="modeId" />
                            {{ t(`commerce.returnMode.${m}`) }}<span class="text-xs text-muted-color">{{ t(`commerce.returnModeHelp.${m}`) }}</span>
                        </label>
                    </div>
                    <Message v-if="draft.returnFeeMode === 'ROUND_TRIP'" severity="warn" :closable="false" size="small">{{ t('commerce.roundTripWarning') }}</Message>
                </div>
                <Message v-if="formError" severity="error" variant="simple" size="small" :closable="false" role="alert">{{ formError }}</Message>
                <div class="flex justify-end">
                    <Button type="submit" :label="t('commerce.policyAddSubmit')" icon="pi pi-plus" size="small" :loading="adding" />
                </div>
            </form>
        </div>
    </AppGadget>
</template>

<script setup lang="ts">
import type { ShippingPolicyLine, ShippingReturnFeeMode } from '~/composables/commerce/api/useCommerceSettingsApi.types';
import type { ApiError } from '~/composables/common/useApiClient';
import UtilGroupBuy from '~/utils/util-groupbuy';

interface Props {
    /** `commerce.settings` */
    canEdit?: boolean;
}

withDefaults(defineProps<Props>(), { canEdit: false });
const emit = defineEmits<{ dirty: [dirty: boolean] }>();

const { t, locale } = useI18n();
const api = useCommerceSettingsApi();
const toApiError = useApiErrorOf();
const toast = useAppToast();
const time = useDisplayTime();
const { confirm } = useConfirmDialog();
const modeId = useId();
const nowIso = new Date().toISOString();
const modes: ShippingReturnFeeMode[] = ['ONE_WAY', 'ROUND_TRIP'];

const items = ref<ShippingPolicyLine[] | null>(null);
const loading = ref(false);
const loadError = ref<ApiError | null>(null);

function emptyDraft() {
    return { code: '', name: '', baseFee: '3000', freeShippingOver: '', returnFee: '3000', returnFeeMode: 'ONE_WAY' as ShippingReturnFeeMode, effectiveFrom: '' };
}
const draft = ref(emptyDraft());
const adding = ref(false);
const deleting = ref<string | null>(null);
const formError = ref('');
const fieldErrors = ref<Record<string, string>>({});
const codeSuggestions = ref<string[]>([]);

watch(
    draft,
    (d) => {
        const e = emptyDraft();
        emit('dirty', d.code !== e.code || d.name !== e.name || d.effectiveFrom !== '' || d.freeShippingOver !== '');
    },
    { deep: true },
);

const groups = computed(() => {
    const map = new Map<string, { code: string; name: string; lines: ShippingPolicyLine[] }>();
    for (const line of items.value ?? []) {
        const g = map.get(line.code) ?? { code: line.code, name: line.name, lines: [] };
        g.lines.push(line);
        map.set(line.code, g);
    }
    return [...map.values()];
});

function won(n: number): string {
    return t('commerce.won', { amount: UtilGroupBuy.won(n, locale.value) });
}

function onComplete(e: { query: string }): void {
    const codes = groups.value.map((g) => g.code);
    codeSuggestions.value = codes.filter((c) => c.includes(e.query.toUpperCase()));
}

/** 있는 코드를 고르면 이름을 채운다(같은 코드의 새 기간 줄) */
function onCode(value: unknown): void {
    const code = String(value ?? '').toUpperCase();
    draft.value.code = code;
    const found = groups.value.find((g) => g.code === code);
    if (found && !draft.value.name) draft.value.name = found.name;
}

async function load(): Promise<void> {
    loading.value = true;
    loadError.value = null;
    try {
        items.value = (await api.shippingPolicies()).items;
    } catch (e) {
        loadError.value = toApiError(e);
    } finally {
        loading.value = false;
    }
}

async function add(): Promise<void> {
    if (adding.value) return;
    formError.value = '';
    fieldErrors.value = {};
    const num = (s: string) => UtilGroupBuy.int(s);
    const baseFee = num(draft.value.baseFee);
    const returnFee = num(draft.value.returnFee);
    const free = draft.value.freeShippingOver.trim() === '' ? null : num(draft.value.freeShippingOver);
    const local: Record<string, string> = {};
    if (baseFee === null) local.baseFee = t('commerce.limitNotInteger');
    if (returnFee === null) local.returnFee = t('commerce.limitNotInteger');
    if (draft.value.freeShippingOver.trim() !== '' && free === null) local.freeShippingOver = t('commerce.limitNotInteger');
    if (!draft.value.effectiveFrom) local.effectiveFrom = t('commerce.policyFromRequired');
    if (Object.keys(local).length) {
        fieldErrors.value = local;
        return;
    }
    if (draft.value.returnFeeMode === 'ROUND_TRIP' && !(await confirm({ title: t('commerce.roundTripConfirmTitle'), message: t('commerce.roundTripWarning'), confirmLabel: t('commerce.policyAddSubmit') }))) return;
    adding.value = true;
    try {
        items.value = (await api.addShippingPolicy({ code: draft.value.code.trim(), name: draft.value.name.trim(), baseFee: baseFee!, freeShippingOver: free, returnFee: returnFee!, returnFeeMode: draft.value.returnFeeMode, effectiveFrom: draft.value.effectiveFrom })).items;
        draft.value = emptyDraft();
        toast.success(t('commerce.policyAdded'));
    } catch (e) {
        const failure = toApiError(e);
        if (failure.code === 'VALIDATION' && failure.fields) fieldErrors.value = failure.fields;
        else formError.value = failure.message;
    } finally {
        adding.value = false;
    }
}

async function remove(line: ShippingPolicyLine): Promise<void> {
    if (!(await confirm({ title: t('commerce.policyDeleteTitle'), message: t('commerce.policyDeleteBody', { code: line.code, at: time.value.full(line.effectiveFrom) }), confirmLabel: t('common.delete'), tone: 'danger' }))) return;
    deleting.value = line.id;
    try {
        items.value = (await api.deleteShippingPolicy(line.id)).items;
        toast.success(t('commerce.policyDeleted'));
    } catch (e) {
        toast.error(toApiError(e).message, 8000);
    } finally {
        deleting.value = null;
    }
}

onMounted(load);
</script>
