<template>
    <!--
        상품정보제공고시 양식들(결정 87). 공구 상세(넓은 폭은 그 자리에, 모바일은 시트)와 주문서(시트)가 같이 쓴다.
        양식마다 제목 · 품목 · 적용 옵션 · 항목 표(묶음 줄이 있는 항목은 묶음 이름을 앞에) · 「판매자 추가 정보」. 「제공 불가」는 사유를 그대로, KC 인증 항목은 딱지.
        옵션은 이름으로 양식을 찾는다(서버 `optionNames`). 옵션 칩을 누르면 그 옵션이 속한 양식(없으면 기본 양식)을 펼친다. 바깥 구획·제목은 부르는 쪽이 둔다.
    -->
    <div class="flex flex-col gap-3" data-testid="group-buy-product-notices">
        <div v-if="showChips" class="flex flex-col gap-1.5">
            <span :id="chipsLabelId" class="text-xs font-medium text-muted-color">{{ t('groupBuy.productInfoByOption') }}</span>
            <div class="flex flex-wrap gap-1.5" role="group" :aria-labelledby="chipsLabelId">
                <Button
                    v-for="option in options"
                    :key="option.id"
                    type="button"
                    size="small"
                    :label="option.name"
                    :severity="selectedOptionId === option.id ? undefined : 'secondary'"
                    :variant="selectedOptionId === option.id ? undefined : 'outlined'"
                    :aria-pressed="selectedOptionId === option.id"
                    :data-testid="`group-buy-notice-chip-${option.id}`"
                    @click="pickOption(option.id)"
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
import type { GroupBuyNoticeView, GroupBuyOption } from '~/composables/groupbuy/api/useGroupBuyApi.types';

interface Props {
    notices: GroupBuyNoticeView[];
    /** 공구의 옵션(이름으로 양식을 찾는다) — 옵션 없는 공구는 빈 배열 */
    options?: Pick<GroupBuyOption, 'id' | 'name'>[];
    /** 주문서에서 고른 옵션 ID — 그 옵션들의 양식을 먼저 놓고 펼친다 */
    priorityOptionIds?: number[];
    /** 양식 제목 태그 — 바깥 제목이 h2 면 h3, 시트(제목 h3) 안이면 h4 */
    headingTag?: 'h3' | 'h4';
}

const props = withDefaults(defineProps<Props>(), {
    options: () => [],
    priorityOptionIds: () => [],
    headingTag: 'h3',
});

const { t } = useI18n();
const uid = useId();
const chipsLabelId = `${uid}-chips`;
const regionId = (index: number) => `${uid}-notice-${index}`;

/** 양식 차례(배열 index)를 식별자로 쓴다(응답에 ID 가 없다) */
const entries = computed(() => props.notices.map((notice, index) => ({ notice, index })));

/** 옵션 이름 → 그 옵션이 속한 양식 차례(한 옵션은 양식 하나에만 — 결정 87) */
const formOfName = computed(() => {
    const map = new Map<string, number>();
    props.notices.forEach((notice, index) => notice.optionNames.forEach((name) => map.has(name) || map.set(name, index)));
    return map;
});
const baseIndex = computed(() => {
    const i = props.notices.findIndex((n) => n.optionNames.length === 0);
    return i < 0 ? null : i;
});

function nameOf(optionId: number): string | undefined {
    return props.options.find((o) => o.id === optionId)?.name;
}

/** 옵션이 속한 양식 — 별개 상품 양식에 없으면 기본 양식 */
function formFor(optionId: number): number | null {
    const name = nameOf(optionId);
    return (name !== undefined ? formOfName.value.get(name) : undefined) ?? baseIndex.value;
}

const priorityForms = computed(() => {
    const out = new Set<number>();
    for (const optionId of props.priorityOptionIds) {
        const form = formFor(optionId);
        if (form !== null) out.add(form);
    }
    return out;
});

/** 배열 차례, 고른 옵션의 양식이 있으면 그것을 먼저(고른 차례대로) */
const sorted = computed(() => {
    if (priorityForms.value.size === 0) return entries.value;
    const first = [...priorityForms.value].map((i) => entries.value[i]!).filter(Boolean);
    return [...first, ...entries.value.filter((e) => !priorityForms.value.has(e.index))];
});

const collapsible = computed(() => props.notices.length > 1);
/** 별개 상품 양식이 있을 때만 옵션 칩(기본 양식 하나뿐이면 고를 까닭이 없다) */
const showChips = computed(() => props.options.length > 0 && props.notices.some((n) => n.optionNames.length > 0));

const openIds = ref(new Set<number>());
const selectedOptionId = ref<number | null>(null);

/** 처음 펼칠 양식 — 고른 옵션의 양식, 없으면 기본 양식(없으면 첫 장) */
function resetOpen(): void {
    selectedOptionId.value = null;
    if (priorityForms.value.size > 0) openIds.value = new Set(priorityForms.value);
    else {
        const first = baseIndex.value ?? (props.notices.length ? 0 : null);
        openIds.value = new Set(first === null ? [] : [first]);
    }
}
watch([() => props.notices, () => props.priorityOptionIds.join(',')], resetOpen, { immediate: true });

const highlighted = computed(() => {
    if (selectedOptionId.value === null) return priorityForms.value;
    const form = formFor(selectedOptionId.value);
    return new Set(form === null ? [] : [form]);
});

function toggle(index: number): void {
    const next = new Set(openIds.value);
    if (next.has(index)) next.delete(index);
    else next.add(index);
    openIds.value = next;
}

function pickOption(optionId: number): void {
    selectedOptionId.value = optionId;
    const form = formFor(optionId);
    if (form === null) return;
    openIds.value = new Set([form]);
    void nextTick(() => document.getElementById(regionId(form))?.parentElement?.scrollIntoView({ block: 'nearest', behavior: 'smooth' }));
}

/** 적용 범위 — 별개 상품 양식은 그 옵션들, 기본 양식은 다른 양식에 속하지 않은 옵션(옵션 없는 공구면 공구 전체) */
function appliesText(notice: GroupBuyNoticeView): string {
    if (notice.optionNames.length > 0) return t('groupBuy.productInfoAppliesOptions', { names: notice.optionNames.join(', ') });
    const rest = props.options.filter((o) => !formOfName.value.has(o.name)).map((o) => o.name);
    return rest.length > 0 && rest.length < props.options.length ? t('groupBuy.productInfoAppliesOptions', { names: rest.join(', ') }) : t('groupBuy.productInfoAppliesAll');
}

function customItemsOf(notice: GroupBuyNoticeView) {
    return [...notice.customItems].sort((a, b) => a.sortOrder - b.sortOrder);
}

/** 출처 — 고시 번호가 양식마다 같으면 한 줄 */
const sources = computed(() => [...new Set(props.notices.map((n) => n.noticeNo).filter(Boolean))]);
</script>
