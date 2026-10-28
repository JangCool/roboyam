<template>
    <!--
        상품정보제공고시 양식들(결정 87). 공구 상세(넓은 폭은 그 자리에, 모바일은 시트)와 주문서(시트)가 같이 쓴다.
        양식마다 제목 · 품목 · 적용 단품 · 항목 표(묶음 줄이 있는 항목은 묶음 이름을 앞에) · 「판매자 추가 정보」. 「제공 불가」는 사유를 그대로, KC 인증 항목은 딱지.
        적용 대상은 단품(결정 88) — 단품 ID 로 양식을 찾는다(서버 `skuIds`·`skuNames`). 추가 구성상품은 단품마다 자기 양식, 적용 단품이 빈 기본 양식은 MAIN 만 덮는다.
        단품 칩을 누르면 그 단품이 속한 양식(없으면 기본 양식)을 펼친다. 바깥 구획·제목은 부르는 쪽이 둔다.
    -->
    <div class="flex flex-col gap-3" data-testid="group-buy-product-notices">
        <div v-if="showChips" class="flex flex-col gap-1.5">
            <span :id="chipsLabelId" class="text-xs font-medium text-muted-color">{{ t('groupBuy.productInfoBySku') }}</span>
            <div class="flex flex-wrap gap-1.5" role="group" :aria-labelledby="chipsLabelId">
                <Button
                    v-for="sku in skus"
                    :key="sku.skuId"
                    type="button"
                    size="small"
                    :label="sku.name"
                    :severity="selectedSkuId === sku.skuId ? undefined : 'secondary'"
                    :variant="selectedSkuId === sku.skuId ? undefined : 'outlined'"
                    :aria-pressed="selectedSkuId === sku.skuId"
                    :data-testid="`group-buy-notice-chip-${sku.skuId}`"
                    @click="pickSku(sku.skuId)"
                />
            </div>
        </div>

        <article
            v-for="entry in sorted"
            :key="entry.index"
            class="overflow-hidden rounded-lg border"
            :class="highlighted.has(entry.index) ? 'border-primary' : 'border-surface'"
            :data-testid="`group-buy-notice-${entry.index}`"
        >
            <component :is="headingTag" class="m-0">
                <button
                    v-if="collapsible"
                    type="button"
                    class="flex w-full items-start gap-2 bg-surface-50 px-3 py-2.5 text-left hover:bg-surface-100 focus-visible:outline-2 focus-visible:-outline-offset-2 focus-visible:outline-primary dark:bg-surface-800 dark:hover:bg-surface-700"
                    :aria-expanded="openIds.has(entry.index)"
                    :aria-controls="regionId(entry.index)"
                    @click="toggle(entry.index)"
                >
                    <span class="min-w-0 flex-1">
                        <span class="flex flex-wrap items-center gap-1.5">
                            <span class="text-sm font-semibold">{{ entry.notice.title }}</span>
                            <PointShopTag v-if="priorityForms.has(entry.index)" tone="primary">{{ t('groupBuy.productInfoOrdering') }}</PointShopTag>
                        </span>
                        <span class="mt-0.5 block text-xs font-normal text-muted-color">{{ t('groupBuy.productInfoCategory', { no: entry.notice.categoryNo, name: entry.notice.categoryName }) }} · {{ appliesText(entry.notice) }}</span>
                    </span>
                    <i class="pi mt-1 text-xs text-muted-color" :class="openIds.has(entry.index) ? 'pi-chevron-up' : 'pi-chevron-down'" aria-hidden="true" />
                </button>
                <span v-else class="block bg-surface-50 px-3 py-2.5 dark:bg-surface-800">
                    <span class="flex flex-wrap items-center gap-1.5">
                        <span class="text-sm font-semibold">{{ entry.notice.title }}</span>
                        <PointShopTag v-if="priorityForms.has(entry.index)" tone="primary">{{ t('groupBuy.productInfoOrdering') }}</PointShopTag>
                    </span>
                    <span class="mt-0.5 block text-xs font-normal text-muted-color">{{ t('groupBuy.productInfoCategory', { no: entry.notice.categoryNo, name: entry.notice.categoryName }) }} · {{ appliesText(entry.notice) }}</span>
                </span>
            </component>

            <div v-show="!collapsible || openIds.has(entry.index)" :id="regionId(entry.index)">
                <table class="w-full table-fixed border-collapse text-sm">
                    <caption class="sr-only">{{ t('groupBuy.productInfoCaption', { title: entry.notice.title }) }}</caption>
                    <thead class="sr-only">
                        <tr>
                            <th scope="col">{{ t('groupBuy.productInfoItem') }}</th>
                            <th scope="col">{{ t('groupBuy.productInfoValue') }}</th>
                        </tr>
                    </thead>
                    <tbody>
                        <tr v-for="row in entry.notice.rows" :key="row.itemKey" class="border-t border-surface">
                            <th scope="row" class="w-28 px-3 py-2 text-left align-top text-xs font-medium leading-relaxed text-muted-color sm:w-44 sm:text-sm">
                                <span v-if="row.group" class="block text-[11px] font-normal">{{ row.group }}</span>
                                {{ row.name }}
                            </th>
                            <td class="break-words px-3 py-2 align-top leading-relaxed" :data-testid="`group-buy-notice-row-${row.itemKey}`">
                                <span v-if="row.unavailable" class="text-muted-color">{{ t('groupBuy.productInfoUnavailable', { reason: row.unavailableReason }) }}</span>
                                <template v-else>
                                    <span v-if="row.kind === 'KC_CERT'" class="mb-1 flex"><PointShopTag tone="neutral">{{ t('groupBuy.productInfoKc') }}</PointShopTag></span>
                                    <span class="whitespace-pre-line">{{ row.value }}</span>
                                </template>
                            </td>
                        </tr>
                    </tbody>
                </table>

                <!-- 판매자 추가 정보(고시 항목 아님 — 판매자가 더한 것) -->
                <template v-if="entry.notice.customItems.length">
                    <p class="m-0 border-t border-surface bg-surface-50 px-3 py-1.5 text-xs font-semibold dark:bg-surface-800">{{ t('groupBuy.productInfoCustom') }}</p>
                    <table class="w-full table-fixed border-collapse text-sm">
                        <caption class="sr-only">{{ t('groupBuy.productInfoCustomCaption', { title: entry.notice.title }) }}</caption>
                        <tbody>
                            <tr v-for="item in customItemsOf(entry.notice)" :key="item.sortOrder" class="border-t border-surface">
                                <th scope="row" class="w-28 px-3 py-2 text-left align-top text-xs font-medium leading-relaxed text-muted-color sm:w-44 sm:text-sm">{{ item.name }}</th>
                                <td class="whitespace-pre-line break-words px-3 py-2 align-top leading-relaxed">{{ item.value }}</td>
                            </tr>
                        </tbody>
                    </table>
                </template>
            </div>
        </article>

        <p v-for="source in sources" :key="source" class="m-0 text-xs text-muted-color" data-testid="group-buy-notice-source">{{ t('groupBuy.productInfoSource', { source }) }}</p>
    </div>
</template>

<script setup lang="ts">
import type { GroupBuyNoticeView, GroupBuySku } from '~/composables/groupbuy/api/useGroupBuyApi.types';

type NoticeSku = Pick<GroupBuySku, 'skuId' | 'name' | 'kind'>;

interface Props {
    notices: GroupBuyNoticeView[];
    /** 공구의 판매하는 단품(칩·적용 범위 글자) */
    skus?: NoticeSku[];
    /** 주문서에서 고른 단품 ID — 그 단품들의 양식을 먼저 놓고 펼친다 */
    prioritySkuIds?: number[];
    /** 양식 제목 태그 — 바깥 제목이 h2 면 h3, 시트(제목 h3) 안이면 h4 */
    headingTag?: 'h3' | 'h4';
}

const props = withDefaults(defineProps<Props>(), {
    skus: () => [],
    prioritySkuIds: () => [],
    headingTag: 'h3',
});

const { t } = useI18n();
const uid = useId();
const chipsLabelId = `${uid}-chips`;
const regionId = (index: number) => `${uid}-notice-${index}`;

/** 양식 차례(배열 index)를 식별자로 쓴다(응답에 ID 가 없다) */
const entries = computed(() => props.notices.map((notice, index) => ({ notice, index })));

/** 단품 ID → 그 단품이 속한 양식 차례(서버 `skuIds` — 한 단품은 양식 하나에만, 결정 87·88) */
const formOfSku = computed(() => {
    const map = new Map<number, number>();
    props.notices.forEach((notice, index) => notice.skuIds.forEach((skuId) => map.has(skuId) || map.set(skuId, index)));
    return map;
});
/** 기본 양식(적용 단품이 빈 것 — MAIN 만 덮는다) */
const baseIndex = computed(() => {
    const i = props.notices.findIndex((n) => n.skuIds.length === 0);
    return i < 0 ? null : i;
});

/** 단품이 속한 양식 — 따로 둔 양식에 없으면 기본 양식(본 상품만) */
function formFor(skuId: number): number | null {
    const own = formOfSku.value.get(skuId);
    if (own !== undefined) return own;
    return props.skus.find((s) => s.skuId === skuId)?.kind === 'ADDON' ? null : baseIndex.value;
}

const priorityForms = computed(() => {
    const out = new Set<number>();
    for (const skuId of props.prioritySkuIds) {
        const form = formFor(skuId);
        if (form !== null) out.add(form);
    }
    return out;
});

/** 배열 차례, 고른 단품의 양식이 있으면 그것을 먼저(고른 차례대로) */
const sorted = computed(() => {
    if (priorityForms.value.size === 0) return entries.value;
    const first = [...priorityForms.value].map((i) => entries.value[i]!).filter(Boolean);
    return [...first, ...entries.value.filter((e) => !priorityForms.value.has(e.index))];
});

const collapsible = computed(() => props.notices.length > 1);
/** 따로 둔 양식(추가 구성상품·종류별)이 있을 때만 단품 칩(기본 양식 하나뿐이면 고를 까닭이 없다) */
const showChips = computed(() => props.skus.length > 1 && props.notices.some((n) => n.skuIds.length > 0));

const openIds = ref(new Set<number>());
const selectedSkuId = ref<number | null>(null);

/** 처음 펼칠 양식 — 고른 단품의 양식, 없으면 기본 양식(없으면 첫 장) */
function resetOpen(): void {
    selectedSkuId.value = null;
    if (priorityForms.value.size > 0) openIds.value = new Set(priorityForms.value);
    else {
        const first = baseIndex.value ?? (props.notices.length ? 0 : null);
        openIds.value = new Set(first === null ? [] : [first]);
    }
}
watch([() => props.notices, () => props.prioritySkuIds.join(',')], resetOpen, { immediate: true });

const highlighted = computed(() => {
    if (selectedSkuId.value === null) return priorityForms.value;
    const form = formFor(selectedSkuId.value);
    return new Set(form === null ? [] : [form]);
});

function toggle(index: number): void {
    const next = new Set(openIds.value);
    if (next.has(index)) next.delete(index);
    else next.add(index);
    openIds.value = next;
}

function pickSku(skuId: number): void {
    selectedSkuId.value = skuId;
    const form = formFor(skuId);
    if (form === null) return;
    openIds.value = new Set([form]);
    void nextTick(() => document.getElementById(regionId(form))?.parentElement?.scrollIntoView({ block: 'nearest', behavior: 'smooth' }));
}

/**
 * 적용 범위 — 따로 둔 양식은 그 단품 이름들. 기본 양식(MAIN 만 덮는다)은 다른 양식에 속하지 않은 본 상품:
 * 추가 구성상품도 따로 양식도 없으면 「공구 전체」, 본 상품 전부면 「본 상품」, 그 밖이면 단품 이름들.
 */
function appliesText(notice: GroupBuyNoticeView): string {
    if (notice.skuIds.length > 0) return t('groupBuy.productInfoAppliesSkus', { names: notice.skuNames.join(', ') });
    const mains = props.skus.filter((s) => s.kind === 'MAIN');
    const rest = mains.filter((s) => !formOfSku.value.has(s.skuId));
    if (rest.length === props.skus.length) return t('groupBuy.productInfoAppliesAll');
    if (rest.length === mains.length) return t('groupBuy.productInfoAppliesMain');
    return t('groupBuy.productInfoAppliesSkus', { names: rest.map((s) => s.name).join(', ') });
}

function customItemsOf(notice: GroupBuyNoticeView) {
    return [...notice.customItems].sort((a, b) => a.sortOrder - b.sortOrder);
}

/** 출처 — 고시 번호가 양식마다 같으면 한 줄 */
const sources = computed(() => [...new Set(props.notices.map((n) => n.noticeNo).filter(Boolean))]);
</script>
