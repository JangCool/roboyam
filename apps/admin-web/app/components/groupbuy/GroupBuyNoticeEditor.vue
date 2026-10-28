<template>
    <!--
        공구 「고시 양식」 구획 본문(상품정보제공고시, 계약 결정 87·88). 공구 하나에 양식 여러 장(서버 모양 `categoryCode`·`title`·`skuNames`(= 적용 단품 이름)·`values`):
        · 적용 단품은 단품 목록(판매하는 MAIN 조합 + 추가 구성상품)에서 고른다. 적용 단품이 빈 양식 = 기본 양식(MAIN 단품들). 축이 있는 공구는 기본 양식이 하나까지이고,
          다른 양식에 들지 않은 MAIN 단품이 기본 양식을 따른다. 기본 양식이 없으면 MAIN 단품이 모두 어느 양식에 들어야 한다.
        · 추가 구성상품(ADDON)은 상품마다 자기 양식이 필수이고 그 양식에는 그 상품 하나만(MAIN 단품·다른 추가 구성상품과 섞지 않는다) — 기본 양식이 덮지 않는다.
        · 빠진 단품은 구획 위에 이름으로 보인다(서버 409 `unassignedSkus`). 한 단품은 양식 하나에만(다른 양식에 든 단품은 고를 수 없음 표시).
        · 양식 = 품목(40개 중 찾아 고르기, 값 `code`) + 그 품목 항목 + 판매자 추가 항목(선택). 빈 항목 수는 양식마다 배지로, 구획 머리 배지는 부르는 쪽이 같은 규칙(`UtilGroupBuy.noticeProblems`)으로 센다.
        모집 뒤에도 고친다(정보 정정 — 수정 이력). 서버 409 `GROUP_BUY_NOTICE_INCOMPLETE` 의 extras 는 `incomplete` 로 받아 표시하고, `focusProblem()` 이 첫 문제로 옮겨 간다.
        카탈로그·단품·서버 오류는 부르는 쪽이 준다(서버 호출 없음). 카탈로그 원문(품목·항목 이름·일반원칙)은 서버 데이터라 그대로 보인다.
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
        <template v-else>
            <Message v-if="addonMissing.length" :severity="unassignedMarked ? 'error' : 'warn'" :closable="false" size="small" :data-notice-problem="unassignedMarked ? '' : undefined">
                {{ t('groupBuy.noticeAddonMissing', { names: addonMissing.join(' · ') }) }}
            </Message>
            <Message v-if="unassignedMains.length" :severity="unassignedMarked ? 'error' : 'warn'" :closable="false" size="small" :data-notice-problem="unassignedMarked && !addonMissing.length ? '' : undefined">
                {{ t('groupBuy.noticeUnassigned', { names: unassignedMains.join(' · ') }) }}
            </Message>
        </template>

        <GroupBuyNoticeForm
            v-for="(row, index) in model"
            :key="row.key"
            :model-value="row"
            :index="index"
            :count="model.length"
            :catalog="catalog"
            :option-choices="choicesFor(index)"
            :has-options="targets.length > 0"
            :has-main-choices="mainTargets.length > 0"
            :addon-name="addonOf(index)"
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
import type { GroupBuyNoticeOptionChoice, GroupBuyNoticeRow } from '~/components/groupbuy/GroupBuyNoticeEditor.types';
import type { GroupBuySkuRef } from '~/components/groupbuy/GroupBuySkuEditor.types';
import type { GroupBuyNoticeIncomplete, GroupBuyNoticeProblem, NoticeCatalog, NoticeCatalogPrinciples } from '~/composables/groupbuy/api/useGroupBuyAdminApi.types';
import UtilGroupBuy from '~/utils/util-groupbuy';

const model = defineModel<GroupBuyNoticeRow[]>({ required: true });

interface Props {
    catalog: NoticeCatalog;
    /** 공구 단품(편집 중인 줄 — MAIN 조합 + 추가 구성상품. 이름 없는 줄은 고를 수 없다) */
    skus?: GroupBuySkuRef[];
    disabled?: boolean;
    /** 서버 칸 오류(`notices[i].title` …) */
    errors?: Record<string, string>;
    /** 서버 409 `GROUP_BUY_NOTICE_INCOMPLETE` 의 extras */
    incomplete?: GroupBuyNoticeIncomplete | null;
}

const props = withDefaults(defineProps<Props>(), { skus: () => [], disabled: false, errors: () => ({}), incomplete: null });

const { t } = useI18n();
const addId = useId();
const root = ref<HTMLElement | null>(null);
const addCategory = ref<string | null>(null);
let seq = 0;

const PRINCIPLE_KEYS: readonly (keyof NoticeCatalogPrinciples)[] = ['similarCategory', 'unavailableReason', 'dateAlternatives', 'qualityWarranty', 'kcCert', 'accessory'];
const principles = computed(() => PRINCIPLE_KEYS.map((key) => ({ key, text: props.catalog.principles[key] })));

const categoryOptions = computed(() => props.catalog.categories.map((c) => ({ value: c.code, label: t('groupBuy.noticeCategoryLabel', { no: c.no, name: c.name }) })));

/** 고를 수 있는 단품 후보 — 판매하는 것만(판매 끈 조합은 고시 대상 아님). 축 없는 단품 하나(이름 빔)는 늘 기본 양식이라 뺀다 */
const candidates = computed(() => props.skus.filter((s) => s.onSale && (s.kind === 'ADDON' || s.name.trim())));
/** 이름이 있는 후보만 양식에 넣을 수 있다(서버는 이름으로 받는다) */
const targets = computed(() => candidates.value.filter((s) => s.name.trim()));
const mainTargets = computed(() => targets.value.filter((s) => s.kind === 'MAIN'));
const kindOf = computed(() => new Map(props.skus.map((s) => [s.key, s.kind])));

/** 단품 줄 키 → 그 단품이 든 양식 차례 */
const ownerOf = computed(() => {
    const out = new Map<string, number>();
    model.value.forEach((row, index) => row.skuKeys.forEach((key) => out.has(key) || out.set(key, index)));
    return out;
});

const baseIndex = computed(() => model.value.findIndex((r) => r.skuKeys.length === 0));

/** 자기 양식이 없는 추가 구성상품(기본 양식이 덮지 않는다) */
const addonMissing = computed(() => targets.value.filter((s) => s.kind === 'ADDON' && !ownerOf.value.has(s.key)).map((s) => s.name.trim()));
/** 기본 양식이 없을 때 어느 양식에도 들지 않은 MAIN 단품 이름 */
const unassignedMains = computed(() => (model.value.length === 0 || baseIndex.value >= 0 ? [] : mainTargets.value.filter((s) => !ownerOf.value.has(s.key)).map((s) => s.name.trim())));
const unassignedMarked = computed(() => (props.incomplete?.unassignedSkus.length ?? 0) > 0);

/** 축이 있는 공구(이름 있는 MAIN 단품)에서 적용 단품이 빈 양식이 둘 이상 — 뒤의 것 */
function isDuplicateBase(index: number): boolean {
    return mainTargets.value.length > 0 && model.value[index]!.skuKeys.length === 0 && baseIndex.value >= 0 && baseIndex.value < index;
}

/** 기본 양식을 따르는 MAIN 단품(첫 기본 양식에만) */
function baseCoversFor(index: number): string[] {
    if (index !== baseIndex.value) return [];
    return mainTargets.value.filter((s) => !ownerOf.value.has(s.key)).map((s) => s.name.trim());
}

/** 이 양식에 든 추가 구성상품 이름(없으면 빈 글자) */
function addonOf(index: number): string {
    const keys = model.value[index]?.skuKeys ?? [];
    return targets.value.find((s) => s.kind === 'ADDON' && keys.includes(s.key))?.name.trim() ?? '';
}

function titleOf(index: number): string {
    const row = model.value[index];
    return row?.title.trim() || t('groupBuy.noticeNth', { n: index + 1 });
}

/**
 * 양식 한 장의 적용 단품 고르기 — MAIN 조합 다음 추가 구성상품. 다른 양식에 든 단품 · 이름 없는 추가 구성상품은 고를 수 없고,
 * 추가 구성상품 양식에는 그 상품 하나만(MAIN 이나 다른 추가 구성상품과 섞지 않는다).
 */
function choicesFor(index: number): GroupBuyNoticeOptionChoice[] {
    const selected = model.value[index]?.skuKeys ?? [];
    const hasAddon = selected.some((k) => kindOf.value.get(k) === 'ADDON');
    const hasMain = selected.some((k) => kindOf.value.get(k) === 'MAIN');
    return candidates.value.map((s) => {
        const addon = s.kind === 'ADDON';
        const name = s.name.trim();
        const label = addon ? t('groupBuy.noticeAddonChoice', { name: name || t('groupBuy.noticeOptionNoName') }) : name;
        if (!name) return { value: s.key, label, disabled: true, note: t('groupBuy.noticeOptionNameFirst') };
        const owner = ownerOf.value.get(s.key);
        if (owner !== undefined && owner !== index) return { value: s.key, label, disabled: true, note: t('groupBuy.noticeOptionInOther', { title: titleOf(owner) }) };
        const mixed = !selected.includes(s.key) && (addon ? hasMain || hasAddon : hasAddon);
        return { value: s.key, label, disabled: mixed, note: mixed ? t('groupBuy.noticeAddonAlone') : '' };
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
            skuKeys: [],
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
