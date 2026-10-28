<template>
    <!--
        공구 › 옵션 종류(`/group-buy-option-types`) — 상품 옵션 이름의 코드 목록(사용자 결정 2026-10-28: 옵션 이름은 코드로 고르고, 값은 상품마다 자유 입력 + 자동완성).
        줄마다 코드(만든 뒤 바꾸지 않음) · 이름 · 숨김 · 쓰는 상품 수 · 이미 쓴 값 수. 차례 바꾸기 · 더하기 · 지우기(상품이 쓰면 못 지움). 저장은 통째로(PUT).
        이름을 바꾸면 그 종류를 쓰는 상품의 옵션 이름도 따라간다(서버 몫). 고치기 `commerce.product.manage`. 서버 호출은 `useGroupBuyProductApi`(목업 — `?mock=option-types-fail`·`save-fail`·`forbidden`).
    -->
    <div class="flex min-w-0 flex-col gap-4">
        <PageTitle :title="t('groupBuy.optionTypeTitle')" :subtitle="t('groupBuy.optionTypeSubtitle')" />

        <AppGadget v-if="loading && !rows.length" :title="t('groupBuy.optionTypeTitle')">
            <div class="flex flex-col gap-2" role="status" :aria-label="t('common.loading')"><Skeleton v-for="n in 5" :key="n" height="2.5rem" /></div>
        </AppGadget>
        <AppGadget v-else-if="loadError" :title="t('groupBuy.optionTypeTitle')">
            <ErrorState :code="loadError.status === 403 ? '403' : '!'" :title="t('groupBuy.optionTypeLoadFailed')" :description="loadError.message" :retry="loadError.status !== 403" :retrying="loading" @retry="load" />
        </AppGadget>

        <form v-else class="flex flex-col gap-4" novalidate @submit.prevent="save">
            <AppGadget :title="t('groupBuy.optionTypeTitle')" :changes="dirty ? 1 : 0">
                <div class="flex flex-col gap-3">
                    <p class="m-0 text-xs leading-relaxed text-muted-color">{{ t('groupBuy.optionTypeHelp') }}</p>
                    <p v-if="serverErrors.optionTypes" role="alert" class="m-0 text-xs text-red-600 dark:text-red-400">{{ serverErrors.optionTypes }}</p>
                    <EmptyState v-if="!rows.length" icon="pi pi-list-check" :title="t('groupBuy.optionTypeEmpty')" />
                    <ul v-else class="m-0 flex list-none flex-col divide-y divide-(--line) rounded-lg border border-(--line) p-0">
                        <li v-for="(row, i) in rows" :key="row.key" class="flex flex-wrap items-center gap-2 px-3 py-2">
                            <InputText :model-value="row.code" :aria-label="t('groupBuy.optionTypeCode')" maxlength="20" :readonly="ro || !row.isNew" :invalid="!/^[A-Z][A-Z0-9_]{1,19}$/.test(row.code)" size="small" class="w-36 font-mono uppercase" @update:model-value="(v: string | undefined) => patch(row.key, { code: (v ?? '').toUpperCase() })" />
                            <InputText :model-value="row.name" :aria-label="t('groupBuy.optionTypeName')" maxlength="20" :readonly="ro" :invalid="!row.name.trim()" size="small" class="w-40" @update:model-value="(v: string | undefined) => patch(row.key, { name: v ?? '' })" />
                            <label class="flex items-center gap-1.5 text-xs"><ToggleSwitch :model-value="row.hidden" :disabled="ro" @update:model-value="(v: boolean) => patch(row.key, { hidden: v })" />{{ t('groupBuy.categoryHidden') }}</label>
                            <span class="text-xs tabular-nums text-muted-color" :title="row.usedValues.join(', ')">{{ t('groupBuy.optionTypeUsed', { n: row.productCount, v: row.usedValues.length }) }}</span>
                            <div v-if="!ro" class="ml-auto flex items-center gap-1">
                                <Button type="button" icon="pi pi-arrow-up" size="small" severity="secondary" variant="text" :aria-label="t('groupBuy.categoryMoveUp')" :disabled="i === 0" @click="move(i, -1)" />
                                <Button type="button" icon="pi pi-arrow-down" size="small" severity="secondary" variant="text" :aria-label="t('groupBuy.categoryMoveDown')" :disabled="i >= rows.length - 1" @click="move(i, 1)" />
                                <Button type="button" icon="pi pi-trash" size="small" severity="danger" variant="text" :aria-label="t('groupBuy.categoryRemove')" :disabled="row.productCount > 0" @click="remove(row.key)" />
                            </div>
                        </li>
                    </ul>
                    <div v-if="!ro"><Button type="button" :label="t('groupBuy.optionTypeAdd')" icon="pi pi-plus" size="small" severity="secondary" variant="outlined" @click="add" /></div>
                </div>
            </AppGadget>

            <StickySaveBar v-if="canManage" submit revertable :dirty="dirty" :saving="saving" :error-count="Object.keys(serverErrors).length" :error="formError" @revert="revert" />
        </form>
    </div>
</template>

<script setup lang="ts">
import type { ApiError } from '~/composables/common/useApiClient';
import type { GroupBuyOptionType } from '~/composables/groupbuy/api/useGroupBuyProductApi.types';

interface Row {
    key: string;
    isNew: boolean;
    code: string;
    name: string;
    hidden: boolean;
    productCount: number;
    usedValues: string[];
}

const { t } = useI18n();
useSeoMeta({ title: () => t('groupBuy.optionTypeTitle') });

const api = useGroupBuyProductApi();
const toApiError = useApiErrorOf();
const toast = useAppToast();
const { can } = useAdminPermission();
const { confirm } = useConfirmDialog();

const canManage = computed(() => can('commerce.product.manage'));
const rows = ref<Row[]>([]);
const initial = ref('');
const loading = ref(false);
const loadError = ref<ApiError | null>(null);
const saving = ref(false);
const formError = ref('');
const serverErrors = ref<Record<string, string>>({});
let seq = 0;

const ro = computed(() => !canManage.value || saving.value);
const dirty = computed(() => initial.value !== '' && JSON.stringify(rows.value.map((r) => [r.code, r.name, r.hidden])) !== initial.value);

function apply(list: GroupBuyOptionType[]): void {
    rows.value = [...list].sort((a, b) => a.sortOrder - b.sortOrder).map((x) => ({ key: x.code, isNew: false, code: x.code, name: x.name, hidden: x.hidden, productCount: x.productCount, usedValues: x.usedValues }));
    initial.value = JSON.stringify(rows.value.map((r) => [r.code, r.name, r.hidden]));
    serverErrors.value = {};
    formError.value = '';
}

async function load(): Promise<void> {
    loading.value = true;
    loadError.value = null;
    try {
        apply(await api.optionTypes());
    } catch (e) {
        loadError.value = toApiError(e);
    } finally {
        loading.value = false;
    }
}
onMounted(load);

function patch(key: string, change: Partial<Row>): void {
    rows.value = rows.value.map((r) => (r.key === key ? { ...r, ...change } : r));
}

function add(): void {
    seq += 1;
    rows.value = [...rows.value, { key: `new-${Date.now()}-${seq}`, isNew: true, code: '', name: '', hidden: false, productCount: 0, usedValues: [] }];
}

function remove(key: string): void {
    rows.value = rows.value.filter((r) => r.key !== key);
}

function move(i: number, delta: number): void {
    const to = i + delta;
    if (to < 0 || to >= rows.value.length) return;
    const next = [...rows.value];
    [next[i], next[to]] = [next[to]!, next[i]!];
    rows.value = next;
}

async function save(): Promise<void> {
    if (saving.value || !dirty.value) return;
    saving.value = true;
    formError.value = '';
    serverErrors.value = {};
    try {
        apply(await api.saveOptionTypes(rows.value.map((r) => ({ code: r.code.trim(), name: r.name.trim(), hidden: r.hidden }))));
        toast.success(t('groupBuy.optionTypeSaved'));
    } catch (e) {
        const failure = toApiError(e);
        if (failure.code === 'VALIDATION' && failure.fields) {
            serverErrors.value = failure.fields;
            formError.value = t('groupBuy.saveFieldsRejected');
        } else {
            formError.value = failure.message;
        }
    } finally {
        saving.value = false;
    }
}

function revert(): void {
    void load();
}

onBeforeRouteLeave(async () => {
    if (!dirty.value) return true;
    return confirm({ title: t('groupBuy.leaveTitle'), message: t('groupBuy.leaveMessage'), confirmLabel: t('groupBuy.leaveConfirm'), cancelLabel: t('groupBuy.leaveCancel'), tone: 'danger' });
});
</script>
