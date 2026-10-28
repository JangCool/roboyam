<template>
    <!--
        공구 「고시 양식」 구획 본문(상품정보제공고시, 계약 결정 87). 공구 하나에 양식 여러 장(서버 모양 `categoryCode`·`title`·`optionNames`·`values`):
        · 적용 옵션이 빈 양식 = 기본 양식(공구 전체). 옵션이 있는 공구는 기본 양식이 하나까지이고, 다른 양식에 들지 않은 옵션(부수 상품·색상 등)이 기본 양식을 따른다.
        · 기본 양식이 없으면 모든 옵션이 어느 양식에 들어야 한다 — 빠진 옵션은 구획 위에 이름으로 보인다(서버 409 `unassignedOptions`).
        · 별개 상품을 옵션으로 함께 팔면 그 옵션용 양식을 더한다 — 한 옵션은 양식 하나에만(다른 양식에 든 옵션은 고를 수 없음 표시).
        · 양식 = 품목(40개 중 찾아 고르기, 값 `code`) + 그 품목 항목 + 판매자 추가 항목(선택). 빈 항목 수는 양식마다 배지로, 구획 머리 배지는 부르는 쪽이 같은 규칙(`UtilGroupBuy.noticeProblems`)으로 센다.
        모집 뒤에도 고친다(정보 정정 — 수정 이력). 서버 409 `GROUP_BUY_NOTICE_INCOMPLETE` 의 extras 는 `incomplete` 로 받아 표시하고, `focusProblem()` 이 첫 문제로 옮겨 간다.
        카탈로그·옵션·서버 오류는 부르는 쪽이 준다(서버 호출 없음). 카탈로그 원문(품목·항목 이름·일반원칙)은 서버 데이터라 그대로 보인다.
    -->
    <div ref="root" class="flex flex-col gap-3">
        <p class="m-0 text-xs leading-relaxed text-muted-color">{{ t('groupBuy.noticeLead', { title: catalog.title, noticeNo: catalog.noticeNo, date: catalog.effectiveDate }) }}</p>
        <ul class="m-0 flex list-disc flex-col gap-1 pl-5 text-xs leading-relaxed text-muted-color">
            <li>{{ t('groupBuy.noticeGuideCategory') }}</li>
            <li>{{ t('groupBuy.noticeGuideOptions') }}</li>
            <li>{{ t('groupBuy.noticeGuideBlank') }}</li>
            <li>{{ t('groupBuy.noticeGuideCustom') }}</li>
        </ul>
        <!-- 일반원칙 원문(서버 데이터) — 접어 둔다 -->
        <details class="rounded-md border border-(--line) px-3 py-2 text-xs leading-relaxed">
            <summary class="cursor-pointer font-medium">{{ t('groupBuy.noticePrinciples', { noticeNo: catalog.noticeNo }) }}</summary>
            <dl class="m-0 mt-2 flex flex-col gap-2">
                <div v-for="p in principles" :key="p.key">
                    <dt class="font-medium">{{ t(`groupBuy.noticePrinciple.${p.key}`) }}</dt>
                    <dd class="m-0 mt-0.5 whitespace-pre-line text-muted-color">{{ p.text }}</dd>
                </div>
            </dl>
            <p class="m-0 mt-2 text-[11px] text-muted-color">{{ t('groupBuy.noticeCatalogSource', { date: catalog.transcribedAt }) }} <a :href="catalog.source" target="_blank" rel="noopener noreferrer" class="text-primary hover:underline">{{ t('groupBuy.noticeCatalogLink') }}</a></p>
        </details>

        <Message v-if="!model.length" :severity="incomplete?.noNotice ? 'error' : 'warn'" :closable="false" size="small" :data-notice-problem="incomplete?.noNotice ? '' : undefined">{{ t('groupBuy.noticeEmpty') }}</Message>
        <Message v-else-if="unassigned.length" :severity="unassignedMarked ? 'error' : 'warn'" :closable="false" size="small" :data-notice-problem="unassignedMarked ? '' : undefined">
            {{ t('groupBuy.noticeUnassigned', { names: unassigned.join(' · ') }) }}
        </Message>

        <GroupBuyNoticeForm
            v-for="(row, index) in model"
            :key="row.key"
            :model-value="row"
            :index="index"
            :count="model.length"
            :catalog="catalog"
            :option-choices="choicesFor(index)"
            :has-options="namedOptions.length > 0"
            :base-covers="baseCoversFor(index)"
            :duplicate-base="isDuplicateBase(index)"
            :disabled="disabled"
            :errors="errors"
            :marked-keys="problemOf(index)?.missingKeys ?? []"
            @update:model-value="(v: GroupBuyNoticeRow) => replace(index, v)"
            @remove="remove(index)"
            @move="(delta: number) => move(index, delta)"
        />

        <!-- 양식 더하기: 품목을 고르면 그 품목 항목 칸이 생긴다 -->
        <div v-if="!disabled" class="flex flex-wrap items-end gap-2">
            <div class="flex w-full flex-col gap-1.5 sm:w-96">
                <label :for="addId" class="text-sm font-medium">{{ t('groupBuy.noticeAddCategory') }}</label>
                <Select :input-id="addId" v-model="addCategory" :options="categoryOptions" option-label="label" option-value="value" filter :filter-placeholder="t('groupBuy.noticeAddFilter')" :placeholder="t('groupBuy.noticeAddPlaceholder')" fluid />
            </div>
            <Button type="button" :label="t('groupBuy.noticeAdd')" icon="pi pi-plus" size="small" severity="secondary" variant="outlined" :disabled="addCategory === null" @click="add" />
        </div>
    </div>
</template>

<script setup lang="ts">
import type { GroupBuyNoticeOptionChoice, GroupBuyNoticeOptionRef, GroupBuyNoticeRow } from '~/components/groupbuy/GroupBuyNoticeEditor.types';
import type { GroupBuyNoticeIncomplete, GroupBuyNoticeProblem, NoticeCatalog, NoticeCatalogPrinciples } from '~/composables/groupbuy/api/useGroupBuyAdminApi.types';
import UtilGroupBuy from '~/utils/util-groupbuy';

const model = defineModel<GroupBuyNoticeRow[]>({ required: true });

interface Props {
    catalog: NoticeCatalog;
    /** 공구 옵션(편집 중인 줄 — 이름 없는 새 줄은 고를 수 없다) */
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
const addCategory = ref<string | null>(null);
let seq = 0;

const PRINCIPLE_KEYS: readonly (keyof NoticeCatalogPrinciples)[] = ['similarCategory', 'unavailableReason', 'dateAlternatives', 'qualityWarranty', 'kcCert', 'accessory'];
const principles = computed(() => PRINCIPLE_KEYS.map((key) => ({ key, text: props.catalog.principles[key] })));

const categoryOptions = computed(() => props.catalog.categories.map((c) => ({ value: c.code, label: t('groupBuy.noticeCategoryLabel', { no: c.no, name: c.name }) })));

/** 이름이 있는 옵션만 양식에 넣을 수 있다(서버는 이름으로 받는다) */
const namedOptions = computed(() => props.options.filter((o) => o.name.trim()));

/** 옵션 줄 키 → 그 옵션이 든 양식 차례 */
const ownerOf = computed(() => {
    const out = new Map<string, number>();
    model.value.forEach((row, index) => row.optionKeys.forEach((key) => out.has(key) || out.set(key, index)));
    return out;
});

const baseIndex = computed(() => model.value.findIndex((r) => r.optionKeys.length === 0));

/** 기본 양식이 없을 때 어느 양식에도 들지 않은 옵션 이름 */
const unassigned = computed(() => (model.value.length === 0 || baseIndex.value >= 0 ? [] : namedOptions.value.filter((o) => !ownerOf.value.has(o.key)).map((o) => o.name.trim())));
const unassignedMarked = computed(() => (props.incomplete?.unassignedOptions.length ?? 0) > 0);

function isDuplicateBase(index: number): boolean {
    return namedOptions.value.length > 0 && model.value[index]!.optionKeys.length === 0 && baseIndex.value >= 0 && baseIndex.value < index;
}

/** 기본 양식을 따르는 옵션(첫 기본 양식에만) */
function baseCoversFor(index: number): string[] {
    if (index !== baseIndex.value) return [];
    return namedOptions.value.filter((o) => !ownerOf.value.has(o.key)).map((o) => o.name.trim());
}

function titleOf(index: number): string {
    const row = model.value[index];
    return row?.title.trim() || t('groupBuy.noticeNth', { n: index + 1 });
}

function choicesFor(index: number): GroupBuyNoticeOptionChoice[] {
    return props.options.map((o) => {
        if (!o.name.trim()) return { value: o.key, label: t('groupBuy.noticeOptionNoName'), disabled: true, note: t('groupBuy.noticeOptionNameFirst') };
        const owner = ownerOf.value.get(o.key);
        const taken = owner !== undefined && owner !== index;
        return { value: o.key, label: o.name.trim(), disabled: taken, note: taken ? t('groupBuy.noticeOptionInOther', { title: titleOf(owner) }) : '' };
    });
}

/** 서버가 돌려준 문제 — 양식 차례(`index`, 0부터)로 맞춘다 */
function problemOf(index: number): GroupBuyNoticeProblem | undefined {
    return props.incomplete?.notices.find((p) => p.index === index);
}

function replace(index: number, row: GroupBuyNoticeRow): void {
    model.value = model.value.map((r, i) => (i === index ? row : r));
}

function remove(index: number): void {
    model.value = model.value.filter((_, i) => i !== index);
}

function move(index: number, delta: number): void {
    const to = index + delta;
    if (to < 0 || to >= model.value.length) return;
    const next = [...model.value];
    [next[index], next[to]] = [next[to]!, next[index]!];
    model.value = next;
}

function add(): void {
    const category = props.catalog.categories.find((c) => c.code === addCategory.value);
    if (!category) return;
    seq += 1;
    model.value = [
        ...model.value,
        {
            key: `new-notice-${Date.now()}-${seq}`,
            categoryCode: category.code,
            // 제목은 운영자가 고친다(예 「본체」·「충전기」) — 처음엔 품목 이름(서버 데이터라 화면 언어를 타지 않는다)
            title: category.name.slice(0, UtilGroupBuy.NOTICE_TITLE_MAX),
            optionKeys: [],
            values: UtilGroupBuy.noticeValuesFor(category.items),
            customItems: [],
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
