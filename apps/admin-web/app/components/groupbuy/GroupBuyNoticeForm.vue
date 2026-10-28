<template>
    <!--
        고시 양식 한 장(결정 87·88) — 제목 · 품목(40개 중 찾아 고르기) · 적용 단품 · 품목 항목 칸 · 판매자 추가 항목. `GroupBuyNoticeEditor` 가 줄마다 놓는다.
        적용 단품이 비면 기본 양식(MAIN 단품들). 추가 구성상품이 든 양식은 「추가 구성상품 양식」 딱지. 품목을 바꾸면 같은 키의 값은 살리고 항목 칸을 다시 만든다(추가 항목은 그대로).
        항목은 카탈로그 차례대로, 하위 번호 항목은 묶음 줄(`group`)을 소제목으로, 원문 괄호 설명(`description`)은 칸 아래 도움말로.
        항목마다 값 또는 「제공 불가」 + 사유. 판매자 추가 항목(`customItems`)은 선택 — 이름 1~40자·내용 1~1,000자, 20개까지, 고시 항목·다른 줄과 같은 이름은 안 된다.
        날짜 항목의 대체 표기·KC 항목의 인증번호/면제는 **저장하지 않는 입력 도우미**(자리표시 예시만 바꾼다).
        `markedKeys`(서버 409 가 돌려준 빈 항목 중 아직 빈 것)는 빨간 테두리와 `data-notice-problem` 으로 표시한다 — 편집기가 그리로 이동한다.
    -->
    <section class="flex flex-col gap-3 rounded-lg border px-3 py-3" :class="duplicateBase ? 'border-red-400 dark:border-red-700' : 'border-(--line)'" :aria-labelledby="headId">
        <!-- 머리: 차례 · 상태 배지 · 옮기기·지우기 -->
        <div class="flex flex-wrap items-center gap-2">
            <h3 :id="headId" class="m-0 inline-flex items-center gap-2 text-[13px] font-semibold">
                <span>{{ t('groupBuy.noticeNth', { n: index + 1 }) }}</span>
                <span v-if="model.title.trim()" class="font-normal text-muted-color">· {{ model.title.trim() }}</span>
            </h3>
            <Tag v-if="isBase" :value="t('groupBuy.noticeBaseScope')" severity="secondary" />
            <Tag v-else-if="addonName" :value="t('groupBuy.noticeAddonScope', { name: addonName })" severity="info" />
            <Tag v-if="emptyKeys.length" :value="t('groupBuy.noticeBlankCount', { n: emptyKeys.length })" severity="warn" />
            <Tag v-else-if="items.length" :value="t('groupBuy.noticeAllFilled')" severity="success" />
            <Tag v-if="duplicateBase" :value="t('groupBuy.noticeBaseDuplicate')" severity="danger" />
            <div v-if="!disabled" class="ml-auto flex items-center gap-1">
                <Button type="button" icon="pi pi-arrow-up" size="small" severity="secondary" variant="text" :aria-label="t('groupBuy.noticeMoveUp')" :disabled="index <= 0" @click="emit('move', -1)" />
                <Button type="button" icon="pi pi-arrow-down" size="small" severity="secondary" variant="text" :aria-label="t('groupBuy.noticeMoveDown')" :disabled="index >= count - 1" @click="emit('move', 1)" />
                <Button type="button" icon="pi pi-trash" size="small" severity="danger" variant="text" :aria-label="t('groupBuy.noticeRemove')" @click="emit('remove')" />
            </div>
        </div>

        <!-- 제목 · 품목 · 적용 옵션 -->
        <div class="flex flex-col gap-3">
            <LabeledField :label="t('groupBuy.noticeTitle')" :error="err('title')">
                <template #default="{ id, describedBy, invalid }">
                    <InputText :id="id" :model-value="model.title" :maxlength="UtilGroupBuy.NOTICE_TITLE_MAX" :placeholder="t('groupBuy.noticeTitlePlaceholder')" :readonly="disabled" :invalid="invalid" :aria-describedby="describedBy" fluid @update:model-value="(v: string | undefined) => patch({ title: v ?? '' })" />
                </template>
            </LabeledField>
            <LabeledField :label="t('groupBuy.noticeCategory')" :error="err('categoryCode')">
                <template #default="{ id, describedBy, invalid }">
                    <Select
                        :input-id="id"
                        :model-value="model.categoryCode"
                        :options="categoryOptions"
                        option-label="label"
                        option-value="value"
                        filter
                        :filter-placeholder="t('groupBuy.noticeAddFilter')"
                        :disabled="disabled"
                        :invalid="invalid"
                        :aria-describedby="describedBy"
                        fluid
                        @update:model-value="changeCategory"
                    />
                </template>
            </LabeledField>

            <div class="flex flex-col gap-1.5">
                <span class="text-sm font-medium">{{ t('groupBuy.noticeOptions') }}</span>
                <p v-if="!hasOptions" class="m-0 text-xs text-muted-color">{{ t('groupBuy.noticeOptionsNone') }}</p>
                <template v-else>
                    <AppMultiSelect
                        :model-value="model.skuKeys"
                        :options="optionChoices"
                        option-label="label"
                        option-value="value"
                        option-disabled="disabled"
                        :placeholder="t('groupBuy.noticeOptionsPlaceholder')"
                        :selected-items-label="t('groupBuy.noticeOptionsSelected', { count: '{0}' })"
                        :disabled="disabled"
                        :invalid="!!err('skuNames') || duplicateBase"
                        :aria-label="t('groupBuy.noticeOptions')"
                        @update:model-value="(v: unknown[]) => patch({ skuKeys: v as string[] })"
                    >
                        <template #option="{ option }">
                            <span class="flex w-full items-center gap-2">
                                <span>{{ option.label }}</span>
                                <span v-if="option.note" class="ml-auto text-[11px] text-muted-color">{{ option.note }}</span>
                            </span>
                        </template>
                    </AppMultiSelect>
                    <p v-if="err('skuNames')" role="alert" class="m-0 text-xs text-red-600 dark:text-red-400">{{ err('skuNames') }}</p>
                    <p v-else-if="duplicateBase" class="m-0 text-xs text-red-600 dark:text-red-400">{{ t('groupBuy.noticeBaseDuplicateHelp') }}</p>
                    <p v-else-if="isBase" class="m-0 text-xs text-muted-color">{{ baseCovers.length ? t('groupBuy.noticeBaseCovers', { names: baseCovers.join(' · ') }) : hasMainChoices ? t('groupBuy.noticeBaseCoversNone') : t('groupBuy.noticeBaseMain') }}</p>
                </template>
            </div>
        </div>

        <!-- 품목 안내(서버 데이터 원문) -->
        <Message v-if="category?.scope" severity="secondary" :closable="false" size="small">{{ t('groupBuy.noticeScope', { text: category.scope }) }}</Message>
        <Message v-if="category?.warrantyDisadvantageDisclosure" severity="info" :closable="false" size="small">{{ t('groupBuy.noticeWarrantyDisclosure', { text: category.warrantyDisadvantageDisclosure }) }}</Message>

        <!-- 고시 항목 칸 -->
        <div class="flex flex-col gap-3">
            <template v-for="block in blocks" :key="block.key">
                <p v-if="block.group" class="m-0 mt-1 text-xs font-semibold">{{ block.group }}</p>
                <div
                    v-for="def in block.items"
                    :key="def.key"
                    class="flex flex-col gap-1.5 rounded-md border px-3 py-2"
                    :class="[isMarked(def.key) ? 'border-red-400 dark:border-red-700' : 'border-(--line)']"
                    :data-notice-problem="isMarked(def.key) ? '' : undefined"
                >
                    <div class="flex flex-wrap items-center gap-x-2 gap-y-1">
                        <label :for="itemId(def.key)" class="text-[13px] font-medium"><span class="mr-1 text-muted-color">{{ def.no }}</span>{{ def.name }}</label>
                        <span v-if="UtilGroupBuy.noticeValueEmpty(def, valueOf(def.key))" class="text-[11px]" :class="isMarked(def.key) ? 'text-red-600 dark:text-red-400' : 'text-amber-700 dark:text-amber-400'">{{ valueOf(def.key)?.unavailable ? t('groupBuy.noticeEmptyReason') : t('groupBuy.noticeEmptyItem') }}</span>
                        <label class="ml-auto inline-flex items-center gap-1.5 text-xs" :class="disabled ? 'opacity-60' : 'cursor-pointer'">
                            <Checkbox :model-value="valueOf(def.key)?.unavailable ?? false" binary :disabled="disabled" @update:model-value="(v: boolean) => patchValue(def.key, { unavailable: v })" />
                            {{ t('groupBuy.noticeUnavailable') }}
                        </label>
                    </div>
                    <p v-if="def.description" class="m-0 text-xs leading-relaxed text-muted-color">{{ def.description }}</p>

                    <template v-if="!valueOf(def.key)?.unavailable">
                        <!-- 날짜 항목: 대체 표기 도우미(원칙 4 — 저장하지 않음) -->
                        <div v-if="def.kind === 'DATE_LIKE'" class="flex flex-wrap items-center gap-2">
                            <Select
                                :model-value="dateHelper[def.key] ?? 'EXACT'"
                                :options="dateHelperOptions"
                                option-label="label"
                                option-value="value"
                                size="small"
                                :disabled="disabled"
                                :aria-label="t('groupBuy.noticeDateHelperLabel', { name: def.name })"
                                class="w-full sm:w-56"
                                @update:model-value="(v: NoticeDateHelper) => (dateHelper = { ...dateHelper, [def.key]: v })"
                            />
                            <span class="text-[11px] text-muted-color">{{ t('groupBuy.noticeHelperNote') }}</span>
                        </div>
                        <!-- KC 항목: 인증번호 / 면제 사실 도우미(원칙 6 — 저장하지 않음) -->
                        <div v-if="def.kind === 'KC_CERT'" class="flex flex-wrap items-center gap-x-4 gap-y-1">
                            <div role="radiogroup" :aria-label="t('groupBuy.noticeKcHelperLabel', { name: def.name })" class="flex flex-wrap gap-x-4 gap-y-1">
                                <label v-for="m in KC_HELPERS" :key="m" class="inline-flex items-center gap-1.5 text-xs" :class="disabled ? 'opacity-60' : 'cursor-pointer'">
                                    <RadioButton :model-value="kcHelper[def.key] ?? 'CERT'" :value="m" :name="itemId(def.key) + '-kc'" :disabled="disabled" @update:model-value="(v: NoticeKcHelper) => (kcHelper = { ...kcHelper, [def.key]: v })" />
                                    {{ t(`groupBuy.noticeKcHelper.${m}`) }}
                                </label>
                            </div>
                            <span class="text-[11px] text-muted-color">{{ t('groupBuy.noticeHelperNote') }}</span>
                        </div>
                        <Textarea
                            v-if="def.kind === 'QUALITY_WARRANTY'"
                            :id="itemId(def.key)"
                            :model-value="valueOf(def.key)?.value ?? ''"
                            rows="2"
                            auto-resize
                            :maxlength="UtilGroupBuy.NOTICE_TEXT_MAX"
                            :placeholder="t('groupBuy.noticeWarrantyPlaceholder')"
                            :readonly="disabled"
                            :invalid="isMarked(def.key) || !!valueErr(def.key, 'value')"
                            fluid
                            @update:model-value="(v: string | undefined) => patchValue(def.key, { value: v ?? '' })"
                        />
                        <InputText
                            v-else
                            :id="itemId(def.key)"
                            :model-value="valueOf(def.key)?.value ?? ''"
                            :maxlength="UtilGroupBuy.NOTICE_TEXT_MAX"
                            :placeholder="placeholderOf(def)"
                            :readonly="disabled"
                            :invalid="isMarked(def.key) || !!valueErr(def.key, 'value')"
                            fluid
                            @update:model-value="(v: string | undefined) => patchValue(def.key, { value: v ?? '' })"
                        />
                        <p v-if="UtilGroupBuy.noticeRefersDetail(valueOf(def.key)?.value ?? '')" class="m-0 text-xs text-amber-700 dark:text-amber-400">{{ t('groupBuy.noticeRefersDetail') }}</p>
                    </template>
                    <!-- 제공 불가: 구체적 사유(원칙 3) -->
                    <Textarea
                        v-else
                        :id="itemId(def.key)"
                        :model-value="valueOf(def.key)?.unavailableReason ?? ''"
                        rows="2"
                        auto-resize
                        :maxlength="UtilGroupBuy.NOTICE_TEXT_MAX"
                        :placeholder="def.conditional ? t('groupBuy.noticeReasonNotApplicable') : t('groupBuy.noticeReasonPlaceholder')"
                        :readonly="disabled"
                        :invalid="isMarked(def.key) || !!valueErr(def.key, 'unavailableReason')"
                        :aria-label="t('groupBuy.noticeReasonFor', { name: def.name })"
                        fluid
                        @update:model-value="(v: string | undefined) => patchValue(def.key, { unavailableReason: v ?? '' })"
                    />

                    <p v-if="valueErr(def.key, 'unavailableReason') || valueErr(def.key, 'value')" role="alert" class="m-0 text-xs text-red-600 dark:text-red-400">{{ valueErr(def.key, 'unavailableReason') || valueErr(def.key, 'value') }}</p>
                    <p v-if="def.conditional" class="m-0 text-xs leading-relaxed text-muted-color">{{ t('groupBuy.noticeConditional') }}</p>
                    <p v-if="def.kind !== 'TEXT'" class="m-0 text-xs leading-relaxed text-muted-color">{{ catalog.itemKinds[def.kind] }}</p>
                </div>
            </template>
        </div>

        <!-- 판매자 추가 항목(선택 — 모집 시작 검사 대상 아님) -->
        <div class="flex flex-col gap-2 border-t border-(--line) pt-3">
            <div class="flex flex-wrap items-baseline gap-2">
                <span class="text-[13px] font-semibold">{{ t('groupBuy.noticeCustomTitle') }}</span>
                <span class="text-[11px] text-muted-color">{{ t('groupBuy.noticeCustomCount', { n: customs.length, max: UtilGroupBuy.NOTICE_CUSTOM_MAX }) }}</span>
            </div>
            <p class="m-0 text-xs leading-relaxed text-muted-color">{{ t('groupBuy.noticeCustomHelp') }}</p>
            <div v-for="(c, k) in customs" :key="`custom-${k}`" class="flex flex-col gap-1.5 rounded-md border border-(--line) px-3 py-2">
                <div class="flex flex-wrap items-start gap-2">
                    <InputText
                        :model-value="c.name"
                        :maxlength="UtilGroupBuy.NOTICE_CUSTOM_LABEL_MAX"
                        :placeholder="t('groupBuy.noticeCustomNamePlaceholder')"
                        :aria-label="t('groupBuy.noticeCustomName', { n: k + 1 })"
                        :readonly="disabled"
                        :invalid="!!customErr(k, 'name') || !!nameProblem(k)"
                        class="w-full sm:w-56"
                        @update:model-value="(v: string | undefined) => patchCustom(k, { name: v ?? '' })"
                    />
                    <Textarea
                        :model-value="c.value"
                        rows="1"
                        auto-resize
                        :maxlength="UtilGroupBuy.NOTICE_TEXT_MAX"
                        :placeholder="t('groupBuy.noticeCustomValuePlaceholder')"
                        :aria-label="t('groupBuy.noticeCustomValue', { n: k + 1 })"
                        :readonly="disabled"
                        :invalid="!!customErr(k, 'value')"
                        class="min-w-0 flex-1"
                        @update:model-value="(v: string | undefined) => patchCustom(k, { value: v ?? '' })"
                    />
                    <div v-if="!disabled" class="flex items-center gap-1">
                        <Button type="button" icon="pi pi-arrow-up" size="small" severity="secondary" variant="text" :aria-label="t('groupBuy.noticeCustomMoveUp')" :disabled="k === 0" @click="moveCustom(k, -1)" />
                        <Button type="button" icon="pi pi-arrow-down" size="small" severity="secondary" variant="text" :aria-label="t('groupBuy.noticeCustomMoveDown')" :disabled="k >= customs.length - 1" @click="moveCustom(k, 1)" />
                        <Button type="button" icon="pi pi-times" size="small" severity="danger" variant="text" :aria-label="t('groupBuy.noticeCustomRemove')" @click="removeCustom(k)" />
                    </div>
                </div>
                <p v-if="customErr(k, 'name') || customErr(k, 'value')" role="alert" class="m-0 text-xs text-red-600 dark:text-red-400">{{ customErr(k, 'name') || customErr(k, 'value') }}</p>
                <p v-else-if="nameProblem(k)" class="m-0 text-xs text-red-600 dark:text-red-400">{{ nameProblem(k) }}</p>
                <p v-else-if="!c.name.trim() !== !c.value.trim()" class="m-0 text-xs text-amber-700 dark:text-amber-400">{{ t('groupBuy.noticeCustomHalf') }}</p>
            </div>
            <div v-if="!disabled">
                <Button type="button" :label="t('groupBuy.noticeCustomAdd')" icon="pi pi-plus" size="small" severity="secondary" variant="text" :disabled="customs.length >= UtilGroupBuy.NOTICE_CUSTOM_MAX" @click="addCustom" />
            </div>
        </div>
    </section>
</template>

<script setup lang="ts">
import type { GroupBuyNoticeOptionChoice, GroupBuyNoticeRow, NoticeDateHelper, NoticeKcHelper } from '~/components/groupbuy/GroupBuyNoticeEditor.types';
import type { NoticeCatalog, NoticeCustomItem, NoticeItemDef, NoticeItemValue } from '~/composables/groupbuy/api/useGroupBuyAdminApi.types';
import UtilGroupBuy from '~/utils/util-groupbuy';

const model = defineModel<GroupBuyNoticeRow>({ required: true });

interface Props {
    /** 화면 차례(0부터) */
    index: number;
    /** 양식 장수 */
    count: number;
    catalog: NoticeCatalog;
    optionChoices?: GroupBuyNoticeOptionChoice[];
    /** 고를 단품(이름 있는 MAIN 조합·추가 구성상품)이 있나 */
    hasOptions?: boolean;
    /** 고를 MAIN 조합이 있나(축이 있는 공구) */
    hasMainChoices?: boolean;
    /** 이 양식에 든 추가 구성상품 이름(없으면 빈 글자) */
    addonName?: string;
    /** 기본 양식을 따르는 MAIN 단품 이름(첫 기본 양식에만) */
    baseCovers?: string[];
    /** 축이 있는 공구에 적용 단품이 빈 양식이 둘 이상 — 이 양식이 뒤의 것 */
    duplicateBase?: boolean;
    disabled?: boolean;
    /** 서버 칸 오류 전부(`notices[i].…`) — 이 양식 몫만 본다 */
    errors?: Record<string, string>;
    /** 서버가 돌려준 빈 항목 키(409) */
    markedKeys?: string[];
}

const props = withDefaults(defineProps<Props>(), {
    optionChoices: () => [],
    hasOptions: false,
    hasMainChoices: false,
    addonName: '',
    baseCovers: () => [],
    duplicateBase: false,
    disabled: false,
    errors: () => ({}),
    markedKeys: () => [],
});
const emit = defineEmits<{ remove: []; move: [delta: number] }>();

const DATE_HELPERS: readonly NoticeDateHelper[] = ['EXACT', 'EARLIEST', 'RANGE', 'ORDER_BASED'];
const KC_HELPERS: readonly NoticeKcHelper[] = ['CERT', 'EXEMPT'];

const { t } = useI18n();
const headId = useId();
const baseId = useId();

const isBase = computed(() => model.value.skuKeys.length === 0);
const category = computed(() => props.catalog.categories.find((c) => c.code === model.value.categoryCode) ?? null);
const items = computed<NoticeItemDef[]>(() => category.value?.items ?? []);
const emptyKeys = computed(() => UtilGroupBuy.noticeEmptyKeys(model.value.values, items.value));
const customs = computed(() => model.value.customItems);

/** 항목을 묶음 줄(`group`) 단위로 — 이어진 같은 묶음끼리 */
const blocks = computed(() => {
    const out: { key: string; group: string | null; items: NoticeItemDef[] }[] = [];
    for (const def of items.value) {
        const group = def.group ?? null;
        const last = out[out.length - 1];
        if (last && last.group === group) last.items.push(def);
        else out.push({ key: `${out.length}-${def.key}`, group, items: [def] });
    }
    return out;
});

const categoryOptions = computed(() => props.catalog.categories.map((c) => ({ value: c.code, label: t('groupBuy.noticeCategoryLabel', { no: c.no, name: c.name }) })));
const dateHelperOptions = computed(() => DATE_HELPERS.map((m) => ({ value: m, label: t(`groupBuy.noticeDateHelper.${m}`) })));

/** 입력 도우미 상태 — 저장하지 않는다(서버 모양에 없음) */
const dateHelper = ref<Record<string, NoticeDateHelper>>({});
const kcHelper = ref<Record<string, NoticeKcHelper>>({});

function itemId(key: string): string {
    return `${baseId}-${key}`;
}

function valueOf(key: string): NoticeItemValue | undefined {
    return UtilGroupBuy.noticeItemValue(model.value.values, key);
}

/** 서버 표시를 받은 뒤 고친 항목 — 고친 뒤에도 비었으면 계속 표시한다 */
const touched = ref(new Set<string>());
watch(
    () => props.markedKeys.join('\n'),
    () => (touched.value = new Set()),
);

function isMarked(key: string): boolean {
    if (!props.markedKeys.includes(key)) return false;
    const def = items.value.find((d) => d.key === key);
    return !!def && (!touched.value.has(key) || UtilGroupBuy.noticeValueEmpty(def, valueOf(key)));
}

function err(field: string): string {
    return props.errors[`notices[${props.index}].${field}`] ?? '';
}

function valueErr(key: string, field: 'value' | 'unavailableReason'): string {
    const j = model.value.values.findIndex((v) => v.itemKey === key);
    return j < 0 ? '' : (props.errors[`notices[${props.index}].values[${j}].${field}`] ?? '');
}

/** 판매자 추가 항목 k 번째의 서버 오류(`notices[i].customItems[k].name|value` — k 는 보낸 추가 항목 차례라고 가정) */
function customErr(k: number, field: 'name' | 'value'): string {
    return props.errors[`notices[${props.index}].customItems[${k}].${field}`] ?? '';
}

/** 이름이 고시 항목과 같거나 앞선 추가 항목과 겹치면 그 안내 */
function nameProblem(k: number): string {
    const name = customs.value[k]?.name ?? '';
    if (UtilGroupBuy.noticeCustomNameTaken(name, items.value)) return t('groupBuy.noticeCustomTaken');
    if (UtilGroupBuy.noticeCustomNameRepeated(customs.value, k)) return t('groupBuy.noticeCustomRepeated');
    return '';
}

function placeholderOf(def: NoticeItemDef): string {
    if (def.kind === 'DATE_LIKE') return t(`groupBuy.noticeDatePlaceholder.${dateHelper.value[def.key] ?? 'EXACT'}`);
    if (def.kind === 'KC_CERT') return t(`groupBuy.noticeKcPlaceholder.${kcHelper.value[def.key] ?? 'CERT'}`);
    return '';
}

function patch(change: Partial<GroupBuyNoticeRow>): void {
    model.value = { ...model.value, ...change };
}

function patchValue(key: string, change: Partial<Omit<NoticeItemValue, 'itemKey'>>): void {
    touched.value = new Set(touched.value).add(key);
    const exists = !!valueOf(key);
    const values = exists ? model.value.values.map((v) => (v.itemKey === key ? { ...v, ...change } : v)) : [...model.value.values, { itemKey: key, value: '', unavailable: false, unavailableReason: '', ...change }];
    patch({ values });
}

// ── 판매자 추가 항목: 배열 차례가 `sortOrder`(0부터) ──

function setCustoms(next: NoticeCustomItem[]): void {
    patch({ customItems: next.map((c, i) => ({ ...c, sortOrder: i })) });
}

function addCustom(): void {
    if (customs.value.length >= UtilGroupBuy.NOTICE_CUSTOM_MAX) return;
    setCustoms([...customs.value, { name: '', value: '', sortOrder: customs.value.length }]);
}

function patchCustom(k: number, change: Partial<Pick<NoticeCustomItem, 'name' | 'value'>>): void {
    setCustoms(customs.value.map((c, i) => (i === k ? { ...c, ...change } : c)));
}

function moveCustom(k: number, delta: number): void {
    const to = k + delta;
    if (to < 0 || to >= customs.value.length) return;
    const next = [...customs.value];
    [next[k], next[to]] = [next[to]!, next[k]!];
    setCustoms(next);
}

function removeCustom(k: number): void {
    setCustoms(customs.value.filter((_, i) => i !== k));
}

/** 품목을 바꾸면 그 품목 항목으로 칸을 다시 만든다 — 같은 키 값은 살리고 추가 항목은 그대로 둔다 */
function changeCategory(code: string): void {
    const next = props.catalog.categories.find((c) => c.code === code);
    if (!next) return;
    patch({ categoryCode: code, values: UtilGroupBuy.noticeValuesFor(next.items, model.value.values) });
}
</script>
