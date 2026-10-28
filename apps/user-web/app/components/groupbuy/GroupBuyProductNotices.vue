<template>
    <!--
        상품정보제공고시 양식들(결정 87 — 공정위고시 제2022-15호). 공구 상세(넓은 폭은 그 자리에, 모바일은 시트)와 주문서(시트)가 같이 쓴다.
        양식마다 제목 · 품목 · 적용 옵션 · 항목 표. 「제공 불가」는 사유를 그대로, 날짜 대체 표기·KC 인증번호/면제 사실은 서버 값 그대로 보인다.
        옵션 칩을 누르면 그 옵션이 속한 양식(없으면 기본 양식)을 펼친다. 바깥 구획·제목은 부르는 쪽이 둔다.
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
            v-for="(notice, index) in sorted"
            :key="notice.id"
            class="overflow-hidden rounded-lg border"
            :class="highlighted.has(notice.id) ? 'border-primary' : 'border-surface'"
            :data-testid="`group-buy-notice-${notice.id}`"
        >
            <component :is="headingTag" class="m-0">
                <button
                    v-if="collapsible"
                    type="button"
                    class="flex w-full items-start gap-2 bg-surface-50 px-3 py-2.5 text-left hover:bg-surface-100 focus-visible:outline-2 focus-visible:-outline-offset-2 focus-visible:outline-primary dark:bg-surface-800 dark:hover:bg-surface-700"
                    :aria-expanded="openIds.has(notice.id)"
                    :aria-controls="regionId(index)"
                    @click="toggle(notice.id)"
                >
                    <span class="min-w-0 flex-1">
                        <span class="flex flex-wrap items-center gap-1.5">
                            <span class="text-sm font-semibold">{{ notice.title }}</span>
                            <PointShopTag v-if="priorityForms.has(notice.id)" tone="primary">{{ t('groupBuy.productInfoOrdering') }}</PointShopTag>
                        </span>
                        <span class="mt-0.5 block text-xs font-normal text-muted-color">{{ t('groupBuy.productInfoCategory', { no: notice.categoryNo, name: notice.categoryName }) }} · {{ appliesText(notice) }}</span>
                    </span>
                    <i class="pi mt-1 text-xs text-muted-color" :class="openIds.has(notice.id) ? 'pi-chevron-up' : 'pi-chevron-down'" aria-hidden="true" />
                </button>
                <span v-else class="block bg-surface-50 px-3 py-2.5 dark:bg-surface-800">
                    <span class="flex flex-wrap items-center gap-1.5">
                        <span class="text-sm font-semibold">{{ notice.title }}</span>
                        <PointShopTag v-if="priorityForms.has(notice.id)" tone="primary">{{ t('groupBuy.productInfoOrdering') }}</PointShopTag>
                    </span>
                    <span class="mt-0.5 block text-xs font-normal text-muted-color">{{ t('groupBuy.productInfoCategory', { no: notice.categoryNo, name: notice.categoryName }) }} · {{ appliesText(notice) }}</span>
                </span>
            </component>

            <div v-show="!collapsible || openIds.has(notice.id)" :id="regionId(index)">
                <table class="w-full table-fixed border-collapse text-sm">
                    <caption class="sr-only">{{ t('groupBuy.productInfoCaption', { title: notice.title }) }}</caption>
                    <thead class="sr-only">
                        <tr>
                            <th scope="col">{{ t('groupBuy.productInfoItem') }}</th>
                            <th scope="col">{{ t('groupBuy.productInfoValue') }}</th>
                        </tr>
                    </thead>
                    <tbody>
                        <tr v-for="row in notice.rows" :key="row.key" class="border-t border-surface">
                            <th scope="row" class="w-28 px-3 py-2 text-left align-top text-xs font-medium leading-relaxed text-muted-color sm:w-44 sm:text-sm">{{ row.name }}</th>
                            <td class="break-words px-3 py-2 align-top leading-relaxed" :data-testid="`group-buy-notice-row-${row.key}`">
                                <template v-if="row.unavailable">
                                    <span class="text-muted-color">{{ t('groupBuy.productInfoUnavailable', { reason: row.reason }) }}</span>
                                    <span v-if="row.value" class="mt-0.5 block whitespace-pre-line">{{ row.value }}</span>
                                </template>
                                <template v-else>
                                    <span v-if="row.kind === 'KC' && row.kcMode" class="mb-1 flex"><PointShopTag :tone="row.kcMode === 'EXEMPT' ? 'warn' : 'neutral'">{{ t(`groupBuy.productInfoKc.${row.kcMode}`) }}</PointShopTag></span>
                                    <span class="whitespace-pre-line">{{ row.value }}</span>
                                    <span v-if="row.kind === 'DATE' && row.dateMode && row.dateMode !== 'EXACT'" class="mt-0.5 block text-xs text-muted-color">{{ t(`groupBuy.productInfoDateMode.${row.dateMode}`) }}</span>
                                </template>
                            </td>
                        </tr>
                    </tbody>
                </table>
            </div>
        </article>

        <p v-for="source in sources" :key="source" class="m-0 text-xs text-muted-color" data-testid="group-buy-notice-source">{{ t('groupBuy.productInfoSource', { source }) }}</p>
    </div>
</template>

<script setup lang="ts">
import type { GroupBuyNoticeView, GroupBuyOption } from '~/composables/groupbuy/api/useGroupBuyApi.types';

interface Props {
    notices: GroupBuyNoticeView[];
    /** 공구의 옵션(이름을 보이려고) — 옵션 없는 공구는 빈 배열 */
    options?: Pick<GroupBuyOption, 'id' | 'name'>[];
    /** 주문서에서 고른 옵션 — 그 옵션들의 양식을 먼저 놓고 펼친다 */
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

/** 옵션 ID → 그 옵션이 속한 양식(한 옵션은 양식 하나에만 — 결정 87) */
const formOfOption = computed(() => {
    const map = new Map<number, GroupBuyNoticeView>();
    for (const notice of props.notices) for (const optionId of notice.optionIds) map.set(optionId, notice);
    return map;
});
const baseForm = computed(() => props.notices.find((n) => n.optionIds.length === 0) ?? null);

/** 옵션이 속한 양식 — 별개 상품 양식에 없으면 기본 양식 */
function formFor(optionId: number): GroupBuyNoticeView | null {
    return formOfOption.value.get(optionId) ?? baseForm.value;
}

const priorityForms = computed(() => {
    const out = new Set<string>();
    for (const optionId of props.priorityOptionIds) {
        const form = formFor(optionId);
        if (form) out.add(form.id);
    }
    return out;
});

/** `order` 차례, 고른 옵션의 양식이 있으면 그것을 먼저(고른 차례대로) */
const sorted = computed(() => {
    const byOrder = [...props.notices].sort((a, b) => a.order - b.order);
    if (priorityForms.value.size === 0) return byOrder;
    const first = [...priorityForms.value].map((id) => byOrder.find((n) => n.id === id)!).filter(Boolean);
    return [...first, ...byOrder.filter((n) => !priorityForms.value.has(n.id))];
});

const collapsible = computed(() => props.notices.length > 1);
/** 별개 상품 양식이 있을 때만 옵션 칩(기본 양식 하나뿐이면 고를 까닭이 없다) */
const showChips = computed(() => props.options.length > 0 && props.notices.some((n) => n.optionIds.length > 0));

const openIds = ref(new Set<string>());
const selectedOptionId = ref<number | null>(null);

/** 처음 펼칠 양식 — 고른 옵션의 양식, 없으면 기본 양식(없으면 첫 장) */
function resetOpen(): void {
    selectedOptionId.value = null;
    if (priorityForms.value.size > 0) openIds.value = new Set(priorityForms.value);
    else {
        const first = baseForm.value ?? sorted.value[0];
        openIds.value = new Set(first ? [first.id] : []);
    }
}
watch([() => props.notices, () => props.priorityOptionIds.join(',')], resetOpen, { immediate: true });

const highlighted = computed(() => {
    if (selectedOptionId.value === null) return priorityForms.value;
    const form = formFor(selectedOptionId.value);
    return new Set(form ? [form.id] : []);
});

function toggle(id: string): void {
    const next = new Set(openIds.value);
    if (next.has(id)) next.delete(id);
    else next.add(id);
    openIds.value = next;
}

function pickOption(optionId: number): void {
    selectedOptionId.value = optionId;
    const form = formFor(optionId);
    if (!form) return;
    openIds.value = new Set([form.id]);
    const index = sorted.value.findIndex((n) => n.id === form.id);
    void nextTick(() => document.getElementById(regionId(index))?.parentElement?.scrollIntoView({ block: 'nearest', behavior: 'smooth' }));
}

/** 적용 범위 — 별개 상품 양식은 그 옵션들, 기본 양식은 다른 양식에 속하지 않은 옵션(옵션 없는 공구면 공구 전체) */
function appliesText(notice: GroupBuyNoticeView): string {
    const nameOf = (id: number) => props.options.find((o) => o.id === id)?.name;
    if (notice.optionIds.length > 0) {
        const names = notice.optionIds.map(nameOf).filter((n): n is string => !!n);
        return t('groupBuy.productInfoAppliesOptions', { names: names.join(', ') });
    }
    const rest = props.options.filter((o) => !formOfOption.value.has(o.id)).map((o) => o.name);
    return rest.length > 0 && rest.length < props.options.length ? t('groupBuy.productInfoAppliesOptions', { names: rest.join(', ') }) : t('groupBuy.productInfoAppliesAll');
}

/** 카탈로그 출처 — 양식마다 같으면 한 줄 */
const sources = computed(() => [...new Set(props.notices.map((n) => n.catalogSource).filter(Boolean))]);
</script>
