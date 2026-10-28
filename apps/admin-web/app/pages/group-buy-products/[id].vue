<template>
    <!--
        공구 › 상품 등록·수정(`/group-buy-products/new`·`/group-buy-products/{id}`) — 결정 89. 같은 상품을 차수·조건을 바꿔 반복해 판다.
        상품 = 이름 · 카테고리(아래 단, 필수) · 요약·사양 · 설명(편집기)·대표 이미지 · 정상가 근거 · 청약철회 제한 · 옵션 축/조합 표(기본 공급가 · 판매 여부) · 상품정보제공고시 양식(결정 87).
        설명·이미지·고시를 고치면 진행 중 공구에도 바로 보이고 그 공구 수정 이력에 사본이 남는다(서버 몫 — 화면은 안내만).
        모집 중 공구가 이 상품을 쓰면 옵션 축·단품 구성이 잠긴다(`structureLocked` — 새 단품은 다음 공구부터). 기본 공급가는 가격 권한(`commerce.product.price`)이 있을 때만 보이고 고친다.
        「이 상품으로 공구 만들기」 → `/group-buys/new?productId=`. 서버 호출은 `useGroupBuyProductApi`(목업 — `?mock=product-fail`·`product-locked`·`save-fail`·`forbidden`).
    -->
    <div class="flex min-w-0 flex-col gap-4">
        <PageTitle :title="pageTitle" :subtitle="t('groupBuy.productEditSubtitle')">
            <template #actions>
                <Button as="router-link" to="/group-buy-products" :label="t('groupBuy.productBackToList')" icon="pi pi-arrow-left" size="small" severity="secondary" variant="outlined" />
                <Button v-if="detail && canManage" as="router-link" :to="`/group-buys/new?productId=${encodeURIComponent(detail.id)}`" :label="t('groupBuy.productNewGroupBuy')" icon="pi pi-plus" size="small" />
            </template>
        </PageTitle>

        <AppGadget v-if="loading" :title="t('groupBuy.productSecBasic')">
            <div class="flex flex-col gap-3" role="status" :aria-label="t('common.loading')"><Skeleton v-for="n in 4" :key="n" height="3rem" /></div>
        </AppGadget>
        <AppGadget v-else-if="loadError" :title="t('groupBuy.productLoadFailed')">
            <ErrorState :code="loadError.status === 403 ? '403' : loadError.status === 404 ? '404' : '!'" :title="t('groupBuy.productLoadFailed')" :description="loadError.message" :retry="loadError.status >= 500" :retrying="loading" @retry="load" />
        </AppGadget>

        <form v-else :key="formKey" class="flex flex-col gap-4" novalidate @submit.prevent="save">
            <Message v-if="!canManage" severity="info" :closable="false" size="small">{{ t('groupBuy.readOnly') }}</Message>
            <Message v-if="detail?.structureLocked" severity="warn" :closable="false" size="small">{{ t('groupBuy.productStructureLocked') }}</Message>

            <!-- 이 상품을 쓰는 공구 -->
            <AppGadget v-if="detail" :title="t('groupBuy.productUsesTitle')">
                <p v-if="!detail.groupBuys.length" class="m-0 text-[13px] text-muted-color">{{ t('groupBuy.productUsesNone') }}</p>
                <ul v-else class="m-0 flex list-none flex-col gap-1 p-0 text-[13px]">
                    <li v-for="u in detail.groupBuys" :key="u.id" class="flex flex-wrap items-center gap-2">
                        <NuxtLink :to="`/group-buys/${encodeURIComponent(u.id)}`" class="font-medium text-primary hover:underline">{{ u.title }}</NuxtLink>
                        <Tag v-if="u.roundNo > 1" :value="t('groupBuy.roundTag', { n: u.roundNo })" severity="secondary" />
                        <Tag :value="t(`groupBuy.status.${u.status}`)" :severity="UtilGroupBuy.statusSeverity(u.status)" />
                    </li>
                </ul>
                <p class="m-0 mt-2 text-xs text-muted-color">{{ t('groupBuy.productLiveHelp') }}</p>
            </AppGadget>

            <!-- 기본 정보 -->
            <AppGadget :title="t('groupBuy.productSecBasic')" anchor="pd-basic">
                <div class="grid gap-4 md:grid-cols-2">
                    <LabeledField :label="t('groupBuy.productName')" :error="fe('name')">
                        <template #default="{ id, describedBy, invalid }">
                            <InputText :id="id" v-model="form.name" maxlength="100" :readonly="ro" :invalid="invalid" :aria-describedby="describedBy" fluid />
                        </template>
                    </LabeledField>
                    <LabeledField :label="t('groupBuy.productCategory')" :help="t('groupBuy.productCategoryHelp')" :error="fe('categoryId') || categoryError">
                        <template #default="{ id, describedBy, invalid }">
                            <Select :input-id="id" v-model="form.categoryId" :options="categoryChoices" option-label="label" option-value="value" filter :placeholder="t('groupBuy.productCategoryPlaceholder')" :disabled="ro" :invalid="invalid" :aria-describedby="describedBy" fluid />
                        </template>
                    </LabeledField>
                    <LabeledField :label="t('groupBuy.fieldSummary')" :hint="t('groupBuy.plainHint', { max: UtilGroupBuy.TEXT_MAX })" :error="fe('summary')" class="md:col-span-2">
                        <template #default="{ id, describedBy, invalid }">
                            <Textarea :id="id" v-model="form.summary" rows="2" auto-resize :maxlength="UtilGroupBuy.TEXT_MAX" :readonly="ro" :invalid="invalid" :aria-describedby="describedBy" fluid />
                        </template>
                    </LabeledField>
                    <LabeledField :label="t('groupBuy.fieldSpec')" :hint="t('groupBuy.plainHint', { max: UtilGroupBuy.TEXT_MAX })" :error="fe('spec')" class="md:col-span-2">
                        <template #default="{ id, describedBy, invalid }">
                            <Textarea :id="id" v-model="form.spec" rows="4" auto-resize :maxlength="UtilGroupBuy.TEXT_MAX" :readonly="ro" :invalid="invalid" :aria-describedby="describedBy" fluid />
                        </template>
                    </LabeledField>
                </div>
            </AppGadget>

            <!-- 설명·이미지 -->
            <AppGadget :title="t('groupBuy.secContent')" anchor="pd-content">
                <div class="flex flex-col gap-4">
                    <div class="flex flex-col gap-1.5">
                        <span :id="descLabelId" class="text-sm font-medium">{{ t('groupBuy.fieldDescription') }}</span>
                        <RichEditor :key="`pd-desc-${formKey}`" v-model="form.description" level="ADVANCED" image-upload :upload="richUpload" :disabled="ro" :labelledby="descLabelId" :invalid="!!fe('description')" :placeholder="t('groupBuy.descriptionPlaceholder')" />
                    </div>
                    <div class="flex flex-col gap-1.5">
                        <span class="text-sm font-medium">{{ t('groupBuy.fieldImages') }}</span>
                        <GroupBuyImagesEditor v-model="form.images" :label="t('groupBuy.fieldImages')" :upload="uploadImage" :disabled="ro" />
                    </div>
                </div>
            </AppGadget>

            <!-- 정상가 근거 · 청약철회 제한 -->
            <AppGadget :title="t('groupBuy.productSecPolicy')" anchor="pd-policy">
                <div class="grid gap-4 md:grid-cols-2">
                    <LabeledField :label="t('groupBuy.fieldBasis')" :error="fe('normalPriceBasis')">
                        <template #default="{ id, describedBy, invalid }">
                            <Select :input-id="id" v-model="form.normalPriceBasis" :options="basisOptions" option-label="label" option-value="value" show-clear :placeholder="t('groupBuy.basisPlaceholder')" :disabled="ro" :invalid="invalid" :aria-describedby="describedBy" fluid />
                        </template>
                    </LabeledField>
                    <LabeledField :label="t('groupBuy.fieldBasisSource')" :help="t('groupBuy.basisSourceHelp')" :error="fe('normalPriceSource')">
                        <template #default="{ id, describedBy, invalid }">
                            <InputText :id="id" v-model="form.normalPriceSource" maxlength="300" :readonly="ro" :invalid="invalid" :aria-describedby="describedBy" fluid />
                        </template>
                    </LabeledField>
                    <LabeledField :label="t('groupBuy.fieldWithdrawal')" :help="t('groupBuy.withdrawalHelp')" :error="fe('withdrawalRestriction')" class="md:col-span-2">
                        <template #default="{ id, describedBy, invalid }">
                            <Textarea :id="id" v-model="form.withdrawalRestriction" rows="3" auto-resize maxlength="1000" :readonly="ro" :invalid="invalid" :aria-describedby="describedBy" fluid />
                        </template>
                    </LabeledField>
                </div>
            </AppGadget>

            <!-- 옵션 축 · 단품(기본 공급가) -->
            <AppGadget :title="t('groupBuy.secOptions')" anchor="pd-options" :invalid="fe('axes') || fe('skus') ? 1 : 0">
                <template v-if="detail?.structureLocked" #title-extra><Tag :value="t('groupBuy.locked')" severity="secondary" /></template>
                <GroupBuyAxesEditor v-model:axes="form.axes" v-model:mains="form.mains" mode="PRODUCT" :locked="!!detail?.structureLocked" :disabled="ro" :errors="serverErrors" :show-supply="canSeeSupply" :supply-editable="canPrice && canManage" />
            </AppGadget>

            <!-- 상품정보제공고시 -->
            <AppGadget :title="t('groupBuy.secNotice')" anchor="pd-notice" :invalid="noticeErrorCount">
                <GroupBuyNoticeEditor v-if="catalog" v-model="form.notices" :catalog="catalog" :skus="skuRefs" :disabled="ro" :errors="serverErrors" :incomplete="null" />
                <ErrorState v-else-if="catalogError" :title="t('groupBuy.noticeCatalogFailed')" :description="catalogError.message" retry @retry="loadCatalog" />
                <Skeleton v-else height="8rem" />
            </AppGadget>

            <StickySaveBar v-if="canManage" submit revertable :dirty="dirty" :saving="saving" :error-count="Object.keys(serverErrors).length" :error="formError" @revert="revert" />
        </form>
    </div>
</template>

<script setup lang="ts">
import type { RichImageUploader } from '#layers/rich-editor/components/RichEditor.types';
import type { GroupBuyNoticeRow } from '~/components/groupbuy/GroupBuyNoticeEditor.types';
import type { GroupBuyAxisRow, GroupBuyMainSkuRow, GroupBuySkuRef } from '~/components/groupbuy/GroupBuySkuEditor.types';
import type { ApiError } from '~/composables/common/useApiClient';
import type { GroupBuyImage, GroupBuyNoticeInput, NormalPriceBasis, NoticeCatalog, NoticeCustomInput } from '~/composables/groupbuy/api/useGroupBuyAdminApi.types';
import { NORMAL_PRICE_BASES } from '~/composables/groupbuy/api/useGroupBuyAdminApi.types';
import type { GroupBuyCategory, GroupBuyProductDetail, GroupBuyProductSaveRequest } from '~/composables/groupbuy/api/useGroupBuyProductApi.types';
import UtilGroupBuy from '~/utils/util-groupbuy';

interface ProductForm {
    name: string;
    categoryId: string | null;
    summary: string;
    spec: string;
    description: string;
    images: GroupBuyImage[];
    normalPriceBasis: NormalPriceBasis | null;
    normalPriceSource: string;
    withdrawalRestriction: string;
    axes: GroupBuyAxisRow[];
    mains: GroupBuyMainSkuRow[];
    notices: GroupBuyNoticeRow[];
}

const { t } = useI18n();
const route = useRoute();
const router = useRouter();
const api = useGroupBuyProductApi();
const gbApi = useGroupBuyAdminApi();
const toApiError = useApiErrorOf();
const toast = useAppToast();
const { can } = useAdminPermission();
const { confirm } = useConfirmDialog();
const descLabelId = useId();

const idParam = computed(() => String(route.params.id ?? ''));
const isNew = computed(() => idParam.value === 'new');
const canManage = computed(() => can('commerce.product.manage'));
const canPrice = computed(() => can('commerce.product.price'));
const canSeeSupply = computed(() => canPrice.value || can('commerce.settlement.read'));

const detail = ref<GroupBuyProductDetail | null>(null);
const loading = ref(false);
const loadError = ref<ApiError | null>(null);
const saving = ref(false);
const formError = ref('');
const serverErrors = ref<Record<string, string>>({});
const formKey = ref(0);
const categories = ref<GroupBuyCategory[]>([]);
const categoryError = ref('');
const catalog = ref<NoticeCatalog | null>(null);
const catalogError = ref<ApiError | null>(null);

const pageTitle = computed(() => (isNew.value ? t('groupBuy.productNewTitle') : (detail.value?.name ?? t('groupBuy.productEditTitle'))));
useSeoMeta({ title: () => pageTitle.value });

const ro = computed(() => !canManage.value || saving.value);
const categoryChoices = computed(() => UtilGroupBuy.categoryChoices(categories.value, false, t('groupBuy.categoryHiddenMark')));
const basisOptions = computed(() => NORMAL_PRICE_BASES.map((b) => ({ value: b, label: t(`groupBuy.basis.${b}`) })));

function singleMain(): GroupBuyMainSkuRow {
    return { key: 'main:', id: null, valueKeys: [], extraPrice: '0', maxQty: '', onSale: true, supplyPrice: '', currentQty: 0, reservedQty: 0 };
}

function emptyForm(): ProductForm {
    return { name: '', categoryId: null, summary: '', spec: '', description: '', images: [], normalPriceBasis: null, normalPriceSource: '', withdrawalRestriction: '', axes: [], mains: [singleMain()], notices: [] };
}

function formOf(p: GroupBuyProductDetail): ProductForm {
    const axes = p.axes.map((a) => ({ key: a.id, id: a.id, name: a.name, values: a.values.map((v) => ({ key: v.id, id: v.id, name: v.name })) }));
    const mains = p.skus.map((s): GroupBuyMainSkuRow => ({ key: `main:${s.valueIds.join('|')}`, id: s.skuId, valueKeys: [...s.valueIds], extraPrice: '0', maxQty: '', onSale: s.onSale, supplyPrice: s.baseSupplyPrice === null ? '' : String(s.baseSupplyPrice), currentQty: 0, reservedQty: 0 }));
    const keyOfName = new Map(p.skus.map((s, i) => [s.name, mains[i]!.key]));
    return {
        name: p.name,
        categoryId: p.categoryId,
        summary: p.summary,
        spec: p.spec,
        description: p.description,
        images: p.images.map((i) => ({ ...i })),
        normalPriceBasis: p.normalPriceBasis,
        normalPriceSource: p.normalPriceSource,
        withdrawalRestriction: p.withdrawalRestriction,
        axes,
        mains: mains.length ? mains : [singleMain()],
        notices: p.notices.map((n, i) => ({ key: `notice-${i}`, categoryCode: n.categoryCode, title: n.title, skuKeys: n.skuNames.flatMap((name) => (keyOfName.has(name) ? [keyOfName.get(name)!] : [])), values: n.values.map((v) => ({ ...v })), customItems: n.customItems.map((c) => ({ ...c })) })),
    };
}

const form = ref<ProductForm>(emptyForm());
const initial = ref('');
function snapshot(f: ProductForm): string {
    return JSON.stringify({ ...f, images: f.images.map((i) => i.mediaId), axes: f.axes.map((a) => [a.name, a.values.map((v) => v.name)]), mains: f.mains.map((m) => [m.key, m.onSale, m.supplyPrice]) });
}
const dirty = computed(() => initial.value !== '' && snapshot(form.value) !== initial.value);

/** 단품 목록(고시 적용 대상) — 축이 없으면 상품 이름 하나 */
const skuRefs = computed<GroupBuySkuRef[]>(() => {
    const valueName = new Map(form.value.axes.flatMap((a) => a.values.map((v) => [v.key, v.name] as const)));
    return form.value.mains.map((m) => ({ key: m.key, kind: 'MAIN', name: m.valueKeys.length ? UtilGroupBuy.skuMainName(m.valueKeys.map((k) => valueName.get(k) ?? '')) : form.value.name.trim(), onSale: m.onSale }));
});
const noticeErrorCount = computed(() => Object.keys(serverErrors.value).filter((k) => k.startsWith('notices[')).length);

function fe(field: string): string {
    return serverErrors.value[field] ?? '';
}

function apply(p: GroupBuyProductDetail): void {
    detail.value = p;
    form.value = formOf(p);
    initial.value = snapshot(form.value);
    serverErrors.value = {};
    formError.value = '';
    formKey.value += 1;
}

async function load(): Promise<void> {
    if (isNew.value) {
        detail.value = null;
        form.value = emptyForm();
        initial.value = snapshot(form.value);
        formKey.value += 1;
        return;
    }
    loading.value = true;
    loadError.value = null;
    try {
        apply(await api.detail(idParam.value));
    } catch (e) {
        loadError.value = toApiError(e);
    } finally {
        loading.value = false;
    }
}

async function loadCatalog(): Promise<void> {
    catalogError.value = null;
    try {
        catalog.value = await gbApi.noticeCatalog();
    } catch (e) {
        catalogError.value = toApiError(e);
    }
}

onMounted(async () => {
    void loadCatalog();
    try {
        categories.value = await api.categories();
    } catch (e) {
        categoryError.value = toApiError(e).message;
    }
    await load();
});
watch(idParam, () => void load());

async function uploadImage(file: File): Promise<GroupBuyImage> {
    const r = await gbApi.uploadImage(file);
    return { mediaId: r.mediaId, url: r.url, thumbUrl: r.thumbUrl };
}
const richUpload: RichImageUploader = async (file, opts) => {
    const r = await gbApi.uploadImage(file);
    opts.onProgress(1);
    return { id: r.mediaId, url: r.url, width: r.width, height: r.height };
};

function toRequest(f: ProductForm): GroupBuyProductSaveRequest {
    const opt = (s: string) => (s.trim() === '' ? null : UtilGroupBuy.int(s));
    const active = f.axes.filter((a) => a.values.length > 0);
    const valueName = new Map(active.flatMap((a) => a.values.map((v) => [v.key, v.name.trim()] as const)));
    const nameOf = new Map(skuRefs.value.map((s) => [s.key, s.name]));
    const notices: GroupBuyNoticeInput[] = f.notices.map((n) => ({
        categoryCode: n.categoryCode,
        title: n.title,
        skuNames: n.skuKeys.flatMap((k) => (nameOf.get(k) ? [nameOf.get(k)!] : [])),
        values: [...n.values.map((v) => ({ ...v })), ...n.customItems.map((c, i): NoticeCustomInput => ({ custom: true, itemKey: null, name: c.name, value: c.value, sortOrder: i }))],
    }));
    return {
        name: f.name.trim(),
        categoryId: f.categoryId,
        summary: f.summary,
        spec: f.spec,
        description: f.description,
        imageIds: f.images.map((i) => i.mediaId),
        normalPriceBasis: f.normalPriceBasis,
        normalPriceSource: f.normalPriceSource.trim(),
        withdrawalRestriction: f.withdrawalRestriction.trim(),
        axes: active.map((a) => ({ name: a.name.trim(), values: a.values.map((v) => v.name.trim()) })),
        skus: f.mains.map((m) => ({ values: m.valueKeys.map((k) => valueName.get(k) ?? ''), baseSupplyPrice: canPrice.value ? opt(m.supplyPrice) : null, onSale: m.onSale })),
        notices,
        version: detail.value?.version ?? null,
    };
}

async function save(): Promise<void> {
    if (saving.value || !dirty.value) return;
    saving.value = true;
    formError.value = '';
    serverErrors.value = {};
    try {
        if (isNew.value) {
            const created = await api.create(toRequest(form.value));
            initial.value = snapshot(form.value);
            toast.success(t('groupBuy.productCreated'));
            await router.replace(`/group-buy-products/${encodeURIComponent(created.id)}`);
            return;
        }
        apply(await api.update(detail.value!.id, toRequest(form.value)));
        toast.success(t('groupBuy.productSaved'));
    } catch (e) {
        const failure = toApiError(e);
        if (failure.code === 'VALIDATION' && failure.fields && Object.keys(failure.fields).length) {
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
    if (detail.value) apply(detail.value);
    else {
        form.value = emptyForm();
        initial.value = snapshot(form.value);
        formKey.value += 1;
    }
}

onBeforeRouteLeave(async () => {
    if (!dirty.value) return true;
    return confirm({ title: t('groupBuy.leaveTitle'), message: t('groupBuy.leaveMessage'), confirmLabel: t('groupBuy.leaveConfirm'), cancelLabel: t('groupBuy.leaveCancel'), tone: 'danger' });
});
</script>
