<template>
    <!--
        공구 「고시 양식」 구획 본문(상품정보제공고시, 계약 결정 87). 공구 하나에 양식 여러 장:
        · 기본 양식 1장 필수 — 맨 앞, 적용 옵션 없음(공구 전체), 지우거나 옮기지 못한다. 다른 양식에 들지 않은 옵션(부수 상품·색상 등)은 이 양식을 따른다.
        · 별개 상품을 옵션으로 함께 팔면 그 옵션용 양식을 더한다 — 한 옵션은 양식 하나에만(다른 양식에 든 옵션은 고를 수 없음 표시). 적용 옵션이 빈 추가 양식은 「옵션 미배정」.
        · 양식 = 품목(40개 중 찾아 고르기) + 그 품목 항목. 빈 항목 수는 양식마다 배지로, 구획 머리 배지는 부르는 쪽(페이지)이 같은 규칙(`UtilGroupBuy.noticeProblems`)으로 센다.
        모집 뒤에도 고친다(정보 정정 — 수정 이력). 서버 409 `GROUP_BUY_NOTICE_INCOMPLETE` 의 목록은 `incomplete` 로 받아 표시하고, `focusProblem()` 이 첫 문제로 옮겨 간다.
        카탈로그·옵션·서버 오류는 부르는 쪽이 준다(서버 호출 없음).
    -->
    <div ref="root" class="flex flex-col gap-3">
        <p class="m-0 text-xs leading-relaxed text-muted-color">{{ t('groupBuy.noticeLead', { source: catalog.source, version: catalog.version }) }}</p>
        <ul class="m-0 flex list-disc flex-col gap-1 pl-5 text-xs leading-relaxed text-muted-color">
            <li>{{ t('groupBuy.noticeGuideCategory') }}</li>
            <li>{{ t('groupBuy.noticeGuideOptions') }}</li>
            <li>{{ t('groupBuy.noticeGuideBlank') }}</li>
            <li>{{ t('groupBuy.noticeGuideKc') }}</li>
        </ul>

        <Message v-if="!model.length" :severity="incomplete?.baseMissing ? 'error' : 'warn'" :closable="false" size="small" :data-notice-problem="incomplete?.baseMissing ? '' : undefined">{{ t('groupBuy.noticeEmpty') }}</Message>
        <Message v-else-if="!hasBase" severity="error" :closable="false" size="small" :data-notice-problem="incomplete?.baseMissing ? '' : undefined">{{ t('groupBuy.noticeBaseMissing') }}</Message>

        <GroupBuyNoticeForm
            v-for="(row, index) in model"
            :key="row.key"
            :model-value="row"
            :index="index"
            :count="model.length"
            :is-base="index === 0 && row.optionIds.length === 0"
            :catalog="catalog"
            :option-choices="choicesFor(index)"
            :has-options="options.length > 0"
            :base-covers="index === 0 ? baseCovers : []"
            :disabled="disabled"
            :errors="errors"
            :marked-keys="problemOf(index)?.emptyKeys ?? []"
            :options-marked="problemOf(index)?.optionsMissing ?? false"
            @update:model-value="(v: GroupBuyNoticeRow) => replace(index, v)"
            @remove="remove(index)"
            @move="(delta: number) => move(index, delta)"
        />

        <!-- 양식 더하기: 품목을 고르면 그 품목 항목 칸이 생긴다 -->
        <div v-if="!disabled" class="flex flex-wrap items-end gap-2">
            <div class="flex w-full flex-col gap-1.5 sm:w-96">
                <label :for="addId" class="text-sm font-medium">{{ model.length ? t('groupBuy.noticeAddCategory') : t('groupBuy.noticeAddBaseCategory') }}</label>
                <Select :input-id="addId" v-model="addCategory" :options="categoryOptions" option-label="label" option-value="value" filter :filter-placeholder="t('groupBuy.noticeAddFilter')" :placeholder="t('groupBuy.noticeAddPlaceholder')" fluid>
                    <template #option="{ option }">
                        <span class="flex w-full items-center gap-2">
                            <span>{{ option.label }}</span>
                            <span v-if="option.provisional" class="text-[11px] text-amber-700 dark:text-amber-400">{{ t('groupBuy.noticeProvisional') }}</span>
                            <span v-if="!option.ready" class="ml-auto text-[11px] text-muted-color">{{ t('groupBuy.noticeItemsPending') }}</span>
                        </span>
                    </template>
                </Select>
            </div>
            <Button type="button" :label="model.length ? t('groupBuy.noticeAdd') : t('groupBuy.noticeAddBase')" icon="pi pi-plus" size="small" severity="secondary" variant="outlined" :disabled="addCategory === null" @click="add" />
        </div>
        <p v-if="hasProvisional" class="m-0 text-[11px] text-muted-color">{{ t('groupBuy.noticeProvisionalHelp') }}</p>
    </div>
</template>

<script setup lang="ts">
import type { GroupBuyNoticeOptionChoice, GroupBuyNoticeOptionRef, GroupBuyNoticeRow } from '~/components/groupbuy/GroupBuyNoticeEditor.types';
import type { GroupBuyNoticeIncomplete, GroupBuyNoticeProblem, NoticeCatalog } from '~/composables/groupbuy/api/useGroupBuyAdminApi.types';
import UtilGroupBuy from '~/utils/util-groupbuy';

const model = defineModel<GroupBuyNoticeRow[]>({ required: true });

interface Props {
    catalog: NoticeCatalog;
    /** 공구 옵션(편집 중인 줄 — 저장 전 새 옵션은 고를 수 없다) */
    options?: GroupBuyNoticeOptionRef[];
    disabled?: boolean;
    /** 서버 칸 오류(`notices[i].title` …) */
    errors?: Record<string, string>;
    /** 서버 409 `GROUP_BUY_NOTICE_INCOMPLETE` 의 extras */
    incomplete?: GroupBuyNoticeIncomplete | null;
}

const props = withDefaults(defineProps<Props>(), { options: () => [], disabled: false, errors: () => ({}), incomplete: null });

const { t } = useI18n();
const addId = useId();
const root = ref<HTMLElement | null>(null);
const addCategory = ref<number | null>(null);
let seq = 0;

const hasBase = computed(() => model.value.length > 0 && model.value[0]!.optionIds.length === 0);
const hasProvisional = computed(() => props.catalog.categories.some((c) => c.provisional));

const categoryOptions = computed(() =>
    props.catalog.categories.map((c) => ({ value: c.no, label: t('groupBuy.noticeCategoryLabel', { no: c.no, name: c.name }), provisional: !!c.provisional, ready: c.items.length > 0 })),
);

/** 옵션 ID → 그 옵션이 든 양식 차례 */
const ownerOf = computed(() => {
    const out = new Map<string, number>();
    model.value.forEach((row, index) => row.optionIds.forEach((id) => out.has(id) || out.set(id, index)));
    return out;
});

/** 기본 양식을 따르는 옵션(다른 양식에 들지 않은 것) */
const baseCovers = computed(() => props.options.filter((o) => !o.id || !ownerOf.value.has(o.id)).map((o) => o.name.trim() || t('groupBuy.noticeOptionNoName')));

function titleOf(index: number): string {
    const row = model.value[index];
    return row?.title.trim() || t('groupBuy.noticeExtra', { n: index });
}

function choicesFor(index: number): GroupBuyNoticeOptionChoice[] {
    return props.options.map((o) => {
        if (!o.id) return { value: `unsaved:${o.key}`, label: o.name.trim() || t('groupBuy.noticeOptionNoName'), disabled: true, note: t('groupBuy.noticeOptionUnsaved') };
        const owner = ownerOf.value.get(o.id);
        const taken = owner !== undefined && owner !== index;
        return { value: o.id, label: o.name, disabled: taken, note: taken ? t('groupBuy.noticeOptionInOther', { title: titleOf(owner) }) : '' };
    });
}

/** 서버가 돌려준 문제 — 양식 `order`(1부터) 로 맞춘다 */
function problemOf(index: number): GroupBuyNoticeProblem | undefined {
    return props.incomplete?.notices.find((p) => p.order === index + 1);
}

function replace(index: number, row: GroupBuyNoticeRow): void {
    model.value = model.value.map((r, i) => (i === index ? row : r));
}

function remove(index: number): void {
    if (index === 0) return;
    model.value = model.value.filter((_, i) => i !== index);
}

/** 기본 양식(0)은 옮기지 않는다 — 추가 양식끼리만 */
function move(index: number, delta: number): void {
    const to = index + delta;
    if (index === 0 || to < 1 || to >= model.value.length) return;
    const next = [...model.value];
    [next[index], next[to]] = [next[to]!, next[index]!];
    model.value = next;
}

function add(): void {
    const category = props.catalog.categories.find((c) => c.no === addCategory.value);
    if (!category) return;
    seq += 1;
    model.value = [
        ...model.value,
        {
            key: `new-notice-${Date.now()}-${seq}`,
            id: null,
            order: model.value.length + 1,
            categoryNo: category.no,
            // 제목은 운영자가 고친다(예 「본체」·「충전기」) — 처음엔 품목 이름(서버 데이터라 화면 언어를 타지 않는다)
            title: category.name,
            optionIds: [],
            catalogVersion: props.catalog.version,
            values: UtilGroupBuy.noticeValuesFor(category.items),
        },
    ];
    addCategory.value = null;
}

/** 첫 문제(서버 409 표시)로 옮겨 가 그 칸에 초점을 둔다 */
async function focusProblem(): Promise<void> {
    await nextTick();
    const target = root.value?.querySelector<HTMLElement>('[data-notice-problem]') ?? root.value;
    if (!target) return;
    target.scrollIntoView({ behavior: 'smooth', block: 'center' });
    target.querySelector<HTMLElement>('input:not([type=checkbox]):not([type=radio]), textarea')?.focus({ preventScroll: true });
}

defineExpose({ focusProblem });
</script>
