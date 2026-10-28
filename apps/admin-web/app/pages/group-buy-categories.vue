<template>
    <!--
        공구 › 카테고리(`/group-buy-categories`) — 상품 카테고리 2단(결정 89). 위 카테고리 · 그 아래 카테고리, 차례 바꾸기 · 숨김 · 더하기 · 지우기(상품이 있으면 못 지운다).
        숨긴 카테고리는 회원 목록 필터에 안 보인다(상품은 그대로 팔린다). 상품은 아래 단을 고른다(아래 단이 없는 위 카테고리는 그대로).
        저장은 목록 통째로(PUT). 고치기 `commerce.product.manage`. 서버 호출은 `useGroupBuyProductApi`(목업 — `?mock=categories-fail`·`save-fail`·`forbidden`).
    -->
    <div class="flex min-w-0 flex-col gap-4">
        <PageTitle :title="t('groupBuy.categoryTitle')" :subtitle="t('groupBuy.categorySubtitle')" />

        <AppGadget v-if="loading && !rows.length" :title="t('groupBuy.categoryTitle')">
            <div class="flex flex-col gap-2" role="status" :aria-label="t('common.loading')"><Skeleton v-for="n in 5" :key="n" height="2.5rem" /></div>
        </AppGadget>
        <AppGadget v-else-if="loadError" :title="t('groupBuy.categoryTitle')">
            <ErrorState :code="loadError.status === 403 ? '403' : '!'" :title="t('groupBuy.categoryLoadFailed')" :description="loadError.message" :retry="loadError.status !== 403" :retrying="loading" @retry="load" />
        </AppGadget>

        <form v-else class="flex flex-col gap-4" novalidate @submit.prevent="save">
            <AppGadget :title="t('groupBuy.categoryTitle')" :changes="dirty ? 1 : 0">
                <div class="flex flex-col gap-3">
                    <p class="m-0 text-xs leading-relaxed text-muted-color">{{ t('groupBuy.categoryHelp') }}</p>
                    <p v-if="serverErrors.categories" role="alert" class="m-0 text-xs text-red-600 dark:text-red-400">{{ serverErrors.categories }}</p>
                    <EmptyState v-if="!tops.length" icon="pi pi-sitemap" :title="t('groupBuy.categoryEmpty')" />

                    <section v-for="(top, i) in tops" :key="top.key" class="flex flex-col gap-2 rounded-lg border border-(--line) px-3 py-2" :aria-label="top.name || t('groupBuy.categoryNew')">
                        <div class="flex flex-wrap items-center gap-2">
                            <InputText :model-value="top.name" :aria-label="t('groupBuy.categoryNameOf', { n: i + 1 })" maxlength="30" :readonly="ro" :invalid="!top.name.trim()" class="w-56 font-medium" @update:model-value="(v: string | undefined) => patch(top.key, { name: v ?? '' })" />
                            <label class="flex items-center gap-1.5 text-xs"><ToggleSwitch :model-value="top.hidden" :disabled="ro" @update:model-value="(v: boolean) => patch(top.key, { hidden: v })" />{{ t('groupBuy.categoryHidden') }}</label>
                            <span class="text-xs tabular-nums text-muted-color">{{ t('groupBuy.categoryProducts', { n: top.productCount }) }}</span>
                            <div v-if="!ro" class="ml-auto flex items-center gap-1">
                                <Button type="button" icon="pi pi-arrow-up" size="small" severity="secondary" variant="text" :aria-label="t('groupBuy.categoryMoveUp')" :disabled="i === 0" @click="move(top.key, -1)" />
                                <Button type="button" icon="pi pi-arrow-down" size="small" severity="secondary" variant="text" :aria-label="t('groupBuy.categoryMoveDown')" :disabled="i >= tops.length - 1" @click="move(top.key, 1)" />
                                <Button type="button" icon="pi pi-trash" size="small" severity="danger" variant="text" :aria-label="t('groupBuy.categoryRemove')" :disabled="top.productCount > 0" @click="remove(top.key)" />
                            </div>
                        </div>
                        <ul class="m-0 flex list-none flex-col gap-1.5 border-l-2 border-(--line) pl-3">
                            <li v-for="(child, j) in childrenOf(top.key)" :key="child.key" class="flex flex-wrap items-center gap-2">
                                <InputText :model-value="child.name" :aria-label="t('groupBuy.categoryChildNameOf', { parent: top.name, n: j + 1 })" maxlength="30" size="small" :readonly="ro" :invalid="!child.name.trim()" class="w-48" @update:model-value="(v: string | undefined) => patch(child.key, { name: v ?? '' })" />
                                <label class="flex items-center gap-1.5 text-xs"><ToggleSwitch :model-value="child.hidden" :disabled="ro" @update:model-value="(v: boolean) => patch(child.key, { hidden: v })" />{{ t('groupBuy.categoryHidden') }}</label>
                                <span class="text-xs tabular-nums text-muted-color">{{ t('groupBuy.categoryProducts', { n: child.productCount }) }}</span>
                                <div v-if="!ro" class="ml-auto flex items-center gap-1">
                                    <Button type="button" icon="pi pi-arrow-up" size="small" severity="secondary" variant="text" :aria-label="t('groupBuy.categoryMoveUp')" :disabled="j === 0" @click="move(child.key, -1)" />
                                    <Button type="button" icon="pi pi-arrow-down" size="small" severity="secondary" variant="text" :aria-label="t('groupBuy.categoryMoveDown')" :disabled="j >= childrenOf(top.key).length - 1" @click="move(child.key, 1)" />
                                    <Button type="button" icon="pi pi-trash" size="small" severity="danger" variant="text" :aria-label="t('groupBuy.categoryRemove')" :disabled="child.productCount > 0" @click="remove(child.key)" />
                                </div>
                            </li>
                            <li v-if="!ro"><Button type="button" :label="t('groupBuy.categoryAddChild')" icon="pi pi-plus" size="small" severity="secondary" variant="text" @click="add(top.key)" /></li>
                        </ul>
                    </section>

                    <div v-if="!ro"><Button type="button" :label="t('groupBuy.categoryAddTop')" icon="pi pi-plus" size="small" severity="secondary" variant="outlined" @click="add(null)" /></div>
                </div>
            </AppGadget>

            <StickySaveBar v-if="canManage" submit revertable :dirty="dirty" :saving="saving" :error-count="Object.keys(serverErrors).length" :error="formError" @revert="revert" />
        </form>
    </div>
</template>

<script setup lang="ts">
import type { ApiError } from '~/composables/common/useApiClient';
import type { GroupBuyCategory } from '~/composables/groupbuy/api/useGroupBuyProductApi.types';

/** 편집 줄 — 불러온 줄은 키 = 서버 ID, 새 줄은 만든 때 키 */
interface Row {
    key: string;
    id: string | null;
    parentKey: string | null;
    name: string;
    hidden: boolean;
    productCount: number;
}

const { t } = useI18n();
useSeoMeta({ title: () => t('groupBuy.categoryTitle') });

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
const tops = computed(() => rows.value.filter((r) => r.parentKey === null));
const dirty = computed(() => initial.value !== '' && JSON.stringify(rows.value) !== initial.value);

function childrenOf(key: string): Row[] {
    return rows.value.filter((r) => r.parentKey === key);
}

function rowsOf(list: GroupBuyCategory[]): Row[] {
    const byOrder = (a: GroupBuyCategory, b: GroupBuyCategory) => a.sortOrder - b.sortOrder;
    return list
        .filter((c) => c.parentId === null)
        .sort(byOrder)
        .flatMap((top) => [top, ...list.filter((c) => c.parentId === top.id).sort(byOrder)])
        .map((c) => ({ key: c.id, id: c.id, parentKey: c.parentId, name: c.name, hidden: c.hidden, productCount: c.productCount }));
}

function apply(list: GroupBuyCategory[]): void {
    rows.value = rowsOf(list);
    initial.value = JSON.stringify(rows.value);
    serverErrors.value = {};
    formError.value = '';
}

async function load(): Promise<void> {
    loading.value = true;
    loadError.value = null;
    try {
        apply(await api.categories());
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

function add(parentKey: string | null): void {
    seq += 1;
    const row: Row = { key: `new-${Date.now()}-${seq}`, id: null, parentKey, name: '', hidden: false, productCount: 0 };
    if (parentKey === null) {
        rows.value = [...rows.value, row];
        return;
    }
    // 그 위 카테고리의 마지막 아래 줄 뒤에 넣는다
    const last = [...rows.value].reverse().find((r) => r.key === parentKey || r.parentKey === parentKey);
    const at = last ? rows.value.indexOf(last) + 1 : rows.value.length;
    rows.value = [...rows.value.slice(0, at), row, ...rows.value.slice(at)];
}

function remove(key: string): void {
    rows.value = rows.value.filter((r) => r.key !== key && r.parentKey !== key);
}

/** 같은 단 안에서 차례 바꾸기 — 위 단을 옮기면 아래 줄이 따라간다 */
function move(key: string, delta: number): void {
    const row = rows.value.find((r) => r.key === key);
    if (!row) return;
    if (row.parentKey === null) {
        const order = tops.value.map((r) => r.key);
        const i = order.indexOf(key);
        const to = i + delta;
        if (to < 0 || to >= order.length) return;
        [order[i], order[to]] = [order[to]!, order[i]!];
        rows.value = order.flatMap((k) => [rows.value.find((r) => r.key === k)!, ...childrenOf(k)]);
        return;
    }
    const siblings = childrenOf(row.parentKey).map((r) => r.key);
    const i = siblings.indexOf(key);
    const to = i + delta;
    if (to < 0 || to >= siblings.length) return;
    [siblings[i], siblings[to]] = [siblings[to]!, siblings[i]!];
    const parentKey = row.parentKey;
    rows.value = tops.value.flatMap((top) => [top, ...(top.key === parentKey ? siblings.map((k) => rows.value.find((r) => r.key === k)!) : childrenOf(top.key))]);
}

async function save(): Promise<void> {
    if (saving.value || !dirty.value) return;
    saving.value = true;
    formError.value = '';
    serverErrors.value = {};
    try {
        apply(await api.saveCategories(rows.value.map((r) => ({ id: r.id, tempKey: r.key, parent: r.parentKey, name: r.name, hidden: r.hidden }))));
        toast.success(t('groupBuy.categorySaved'));
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
    rows.value = JSON.parse(initial.value) as Row[];
}

onBeforeRouteLeave(async () => {
    if (!dirty.value) return true;
    return confirm({ title: t('groupBuy.leaveTitle'), message: t('groupBuy.leaveMessage'), confirmLabel: t('groupBuy.leaveConfirm'), cancelLabel: t('groupBuy.leaveCancel'), tone: 'danger' });
});
</script>
