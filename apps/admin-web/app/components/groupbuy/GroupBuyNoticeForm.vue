<template>
    <!--
        고시 양식 한 장(결정 87) — 제목 · 품목(40개 중 찾아 고르기) · 적용 옵션 · 품목 항목 칸. `GroupBuyNoticeEditor` 가 줄마다 놓는다.
        기본 양식(`isBase`)은 적용 옵션이 없고(공구 전체) 지우거나 옮기지 못한다. 품목을 바꾸면 같은 키의 값은 살리고 항목 칸을 다시 만든다.
        항목마다 값 또는 「제공 불가」 + 사유, 날짜 항목은 대체 표기, KC 항목은 인증번호/면제 사실, 품질보증 항목은 안내 문구.
        `markedKeys`(서버 409 가 돌려준 빈 항목 중 아직 빈 것)는 빨간 테두리와 `data-notice-problem` 으로 표시한다 — 편집기가 그리로 이동한다.
    -->
    <section class="flex flex-col gap-3 rounded-lg border px-3 py-3" :class="optionsMarked && optionsMissing ? 'border-red-400 dark:border-red-700' : 'border-(--line)'" :aria-labelledby="headId">
        <!-- 머리: 종류 · 상태 배지 · 옮기기·지우기 -->
        <div class="flex flex-wrap items-center gap-2">
            <h3 :id="headId" class="m-0 inline-flex items-center gap-2 text-[13px] font-semibold">
                <span>{{ isBase ? t('groupBuy.noticeBase') : t('groupBuy.noticeExtra', { n: index }) }}</span>
                <span v-if="model.title.trim()" class="font-normal text-muted-color">· {{ model.title.trim() }}</span>
            </h3>
            <Tag v-if="isBase" :value="t('groupBuy.noticeBaseScope')" severity="secondary" />
            <Tag v-if="!items.length" :value="t('groupBuy.noticeItemsPending')" severity="secondary" />
            <Tag v-else-if="emptyKeys.length" :value="t('groupBuy.noticeBlankCount', { n: emptyKeys.length })" severity="warn" />
            <Tag v-else :value="t('groupBuy.noticeAllFilled')" severity="success" />
            <Tag v-if="optionsMissing" :value="t('groupBuy.noticeOptionsMissing')" severity="danger" />
            <div v-if="!isBase && !disabled" class="ml-auto flex items-center gap-1">
                <Button type="button" icon="pi pi-arrow-up" size="small" severity="secondary" variant="text" :aria-label="t('groupBuy.noticeMoveUp')" :disabled="index <= 1" @click="emit('move', -1)" />
                <Button type="button" icon="pi pi-arrow-down" size="small" severity="secondary" variant="text" :aria-label="t('groupBuy.noticeMoveDown')" :disabled="index >= count - 1" @click="emit('move', 1)" />
                <Button type="button" icon="pi pi-trash" size="small" severity="danger" variant="text" :aria-label="t('groupBuy.noticeRemove')" @click="emit('remove')" />
            </div>
        </div>

        <!-- 제목 · 품목 · 적용 옵션 -->
        <div class="grid gap-3 md:grid-cols-2">
            <LabeledField :label="t('groupBuy.noticeTitle')" :error="err('title')">
                <template #default="{ id, describedBy, invalid }">
                    <InputText :id="id" :model-value="model.title" :maxlength="UtilGroupBuy.NOTICE_TITLE_MAX" :placeholder="t('groupBuy.noticeTitlePlaceholder')" :readonly="disabled" :invalid="invalid" :aria-describedby="describedBy" fluid @update:model-value="(v: string | undefined) => patch({ title: v ?? '' })" />
                </template>
            </LabeledField>
            <LabeledField :label="t('groupBuy.noticeCategory')" :error="err('categoryNo')">
                <template #default="{ id, describedBy, invalid }">
                    <Select
                        :input-id="id"
                        :model-value="model.categoryNo"
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
                    >
                        <template #option="{ option }">
                            <span class="flex w-full items-center gap-2">
                                <span>{{ option.label }}</span>
                                <span v-if="option.provisional" class="text-[11px] text-amber-700 dark:text-amber-400">{{ t('groupBuy.noticeProvisional') }}</span>
                                <span v-if="!option.ready" class="ml-auto text-[11px] text-muted-color">{{ t('groupBuy.noticeItemsPending') }}</span>
                            </span>
                        </template>
                    </Select>
                </template>
            </LabeledField>

            <div class="flex flex-col gap-1.5 md:col-span-2">
                <span :id="optionsLabelId" class="text-sm font-medium">{{ t('groupBuy.noticeOptions') }}</span>
                <template v-if="isBase">
                    <p class="m-0 text-xs text-muted-color">{{ baseCovers.length ? t('groupBuy.noticeBaseCovers', { names: baseCovers.join(' · ') }) : t('groupBuy.noticeBaseCoversAll') }}</p>
                </template>
                <template v-else>
                    <p v-if="!hasOptions" class="m-0 text-xs text-muted-color">{{ t('groupBuy.noticeOptionsNone') }}</p>
                    <AppMultiSelect
                        v-else
                        :model-value="model.optionIds"
                        :options="optionChoices"
                        option-label="label"
                        option-value="value"
                        option-disabled="disabled"
                        :placeholder="t('groupBuy.noticeOptionsPlaceholder')"
                        :selected-items-label="t('groupBuy.noticeOptionsSelected', { count: '{0}' })"
                        :disabled="disabled"
                        :invalid="!!err('optionIds') || (optionsMarked && optionsMissing)"
                        :aria-label="t('groupBuy.noticeOptions')"
                        @update:model-value="(v: unknown[]) => patch({ optionIds: v as string[] })"
                    >
                        <template #option="{ option }">
                            <span class="flex w-full items-center gap-2">
                                <span>{{ option.label }}</span>
                                <span v-if="option.note" class="ml-auto text-[11px] text-muted-color">{{ option.note }}</span>
                            </span>
                        </template>
                    </AppMultiSelect>
                    <p v-if="err('optionIds')" role="alert" class="m-0 text-xs text-red-600 dark:text-red-400">{{ err('optionIds') }}</p>
                    <p v-else-if="optionsMissing && hasOptions" :data-notice-problem="optionsMarked ? '' : undefined" class="m-0 text-xs" :class="optionsMarked ? 'text-red-600 dark:text-red-400' : 'text-amber-700 dark:text-amber-400'">{{ t('groupBuy.noticeOptionsMissingHelp') }}</p>
                </template>
            </div>
        </div>

        <p v-if="model.catalogVersion && model.catalogVersion !== catalog.version" class="m-0 text-xs text-muted-color">{{ t('groupBuy.noticeVersionNote', { version: model.catalogVersion }) }}</p>
        <Message v-if="!items.length" severity="secondary" :closable="false" size="small">{{ t('groupBuy.noticeItemsPendingHelp') }}</Message>

        <!-- 항목 칸 -->
        <div v-else class="grid gap-3 md:grid-cols-2">
            <div
                v-for="def in items"
                :key="def.key"
                class="flex flex-col gap-1.5 rounded-md border px-3 py-2"
                :class="[isMarked(def.key) ? 'border-red-400 dark:border-red-700' : 'border-(--line)', def.kind === 'WARRANTY' || def.kind === 'KC' ? 'md:col-span-2' : '']"
                :data-notice-problem="isMarked(def.key) ? '' : undefined"
            >
                <div class="flex flex-wrap items-center gap-x-2 gap-y-1">
                    <label :for="itemId(def.key)" class="text-[13px] font-medium">{{ def.name }}</label>
                    <span v-if="!def.required" class="text-[11px] text-muted-color">{{ t('groupBuy.noticeOptional') }}</span>
                    <span v-if="UtilGroupBuy.noticeValueEmpty(def, valueOf(def.key))" class="text-[11px]" :class="isMarked(def.key) ? 'text-red-600 dark:text-red-400' : 'text-amber-700 dark:text-amber-400'">{{ valueOf(def.key)?.unavailable ? t('groupBuy.noticeEmptyReason') : t('groupBuy.noticeEmptyItem') }}</span>
                    <label class="ml-auto inline-flex items-center gap-1.5 text-xs" :class="disabled ? 'opacity-60' : 'cursor-pointer'">
                        <Checkbox :model-value="valueOf(def.key)?.unavailable ?? false" binary :disabled="disabled" @update:model-value="(v: boolean) => patchValue(def.key, { unavailable: v })" />
                        {{ t('groupBuy.noticeUnavailable') }}
                    </label>
                </div>

                <template v-if="!valueOf(def.key)?.unavailable">
                    <!-- 날짜 항목: 대체 표기(원칙 ④) -->
                    <Select
                        v-if="def.kind === 'DATE'"
                        :model-value="valueOf(def.key)?.dateMode ?? 'EXACT'"
                        :options="dateModeOptions"
                        option-label="label"
                        option-value="value"
                        size="small"
                        :disabled="disabled"
                        :aria-label="t('groupBuy.noticeDateModeLabel', { name: def.name })"
                        class="w-full sm:w-56"
                        @update:model-value="(v: NoticeDateMode) => patchValue(def.key, { dateMode: v })"
                    />
                    <!-- KC 항목: 인증번호 / 면제 사실(원칙 ⑥) -->
                    <div v-if="def.kind === 'KC'" role="radiogroup" :aria-label="def.name" class="flex flex-wrap gap-x-4 gap-y-1">
                        <label v-for="m in NOTICE_KC_MODES" :key="m" class="inline-flex items-center gap-1.5 text-xs" :class="disabled ? 'opacity-60' : 'cursor-pointer'">
                            <RadioButton :model-value="valueOf(def.key)?.kcMode ?? 'CERT'" :value="m" :name="itemId(def.key) + '-kc'" :disabled="disabled" @update:model-value="(v: NoticeKcMode) => patchValue(def.key, { kcMode: v })" />
                            {{ t(`groupBuy.noticeKcMode.${m}`) }}
                        </label>
                    </div>
                    <Textarea
                        v-if="def.kind === 'WARRANTY'"
                        :id="itemId(def.key)"
                        :model-value="valueOf(def.key)?.value ?? ''"
                        rows="2"
                        auto-resize
                        :maxlength="UtilGroupBuy.NOTICE_TEXT_MAX"
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
                <!-- 제공 불가: 구체적 사유(원칙 ③) -->
                <template v-else>
                    <Textarea
                        :id="itemId(def.key)"
                        :model-value="valueOf(def.key)?.reason ?? ''"
                        rows="2"
                        auto-resize
                        :maxlength="UtilGroupBuy.NOTICE_TEXT_MAX"
                        :placeholder="t('groupBuy.noticeReasonPlaceholder')"
                        :readonly="disabled"
                        :invalid="isMarked(def.key) || !!valueErr(def.key, 'reason')"
                        :aria-label="t('groupBuy.noticeReasonFor', { name: def.name })"
                        fluid
                        @update:model-value="(v: string | undefined) => patchValue(def.key, { reason: v ?? '' })"
                    />
                </template>

                <p v-if="valueErr(def.key, 'reason') || valueErr(def.key, 'value')" role="alert" class="m-0 text-xs text-red-600 dark:text-red-400">{{ valueErr(def.key, 'reason') || valueErr(def.key, 'value') }}</p>
                <p v-if="def.guide" class="m-0 text-xs leading-relaxed text-muted-color">{{ def.guide }}</p>
            </div>
        </div>
    </section>
</template>

<script setup lang="ts">
import type { GroupBuyNoticeOptionChoice, GroupBuyNoticeRow } from '~/components/groupbuy/GroupBuyNoticeEditor.types';
import type { NoticeCatalog, NoticeDateMode, NoticeItemDef, NoticeKcMode, NoticeValue } from '~/composables/groupbuy/api/useGroupBuyAdminApi.types';
import { NOTICE_DATE_MODES, NOTICE_KC_MODES } from '~/composables/groupbuy/api/useGroupBuyAdminApi.types';
import UtilGroupBuy from '~/utils/util-groupbuy';

const model = defineModel<GroupBuyNoticeRow>({ required: true });

interface Props {
    /** 화면 차례(0 = 기본 양식) */
    index: number;
    /** 양식 장수 */
    count: number;
    isBase: boolean;
    catalog: NoticeCatalog;
    optionChoices?: GroupBuyNoticeOptionChoice[];
    /** 공구에 옵션이 있나 */
    hasOptions?: boolean;
    /** 기본 양식을 따르는 옵션 이름(기본 양식에만) */
    baseCovers?: string[];
    disabled?: boolean;
    /** 서버 칸 오류 전부(`notices[i].…`) — 이 양식 몫만 본다 */
    errors?: Record<string, string>;
    /** 서버가 돌려준 빈 항목 키(409) */
    markedKeys?: string[];
    /** 서버가 「옵션 미배정」이라 했나(409) */
    optionsMarked?: boolean;
}

const props = withDefaults(defineProps<Props>(), {
    optionChoices: () => [],
    hasOptions: false,
    baseCovers: () => [],
    disabled: false,
    errors: () => ({}),
    markedKeys: () => [],
    optionsMarked: false,
});
const emit = defineEmits<{ remove: []; move: [delta: number] }>();

const { t } = useI18n();
const headId = useId();
const optionsLabelId = useId();
const baseId = useId();

const category = computed(() => props.catalog.categories.find((c) => c.no === model.value.categoryNo) ?? null);
const items = computed<NoticeItemDef[]>(() => category.value?.items ?? []);
const emptyKeys = computed(() => UtilGroupBuy.noticeEmptyKeys(model.value, items.value));
/** 추가 양식인데 적용 옵션이 없다 */
const optionsMissing = computed(() => !props.isBase && model.value.optionIds.length === 0);

const categoryOptions = computed(() =>
    props.catalog.categories.map((c) => ({ value: c.no, label: t('groupBuy.noticeCategoryLabel', { no: c.no, name: c.name }), provisional: !!c.provisional, ready: c.items.length > 0 })),
);
const dateModeOptions = computed(() => NOTICE_DATE_MODES.map((m) => ({ value: m, label: t(`groupBuy.noticeDateMode.${m}`) })));

function itemId(key: string): string {
    return `${baseId}-${key}`;
}

function valueOf(key: string): NoticeValue | undefined {
    return model.value.values.find((v) => v.key === key);
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

function valueErr(key: string, field: 'value' | 'reason'): string {
    const j = model.value.values.findIndex((v) => v.key === key);
    return j < 0 ? '' : (props.errors[`notices[${props.index}].values[${j}].${field}`] ?? '');
}

function placeholderOf(def: NoticeItemDef): string {
    const v = valueOf(def.key);
    if (def.kind === 'DATE') return t(`groupBuy.noticeDatePlaceholder.${v?.dateMode ?? 'EXACT'}`);
    if (def.kind === 'KC') return t(`groupBuy.noticeKcPlaceholder.${v?.kcMode ?? 'CERT'}`);
    return '';
}

function patch(change: Partial<GroupBuyNoticeRow>): void {
    model.value = { ...model.value, ...change };
}

function patchValue(key: string, change: Partial<NoticeValue>): void {
    touched.value = new Set(touched.value).add(key);
    const exists = model.value.values.some((v) => v.key === key);
    const values = exists ? model.value.values.map((v) => (v.key === key ? { ...v, ...change } : v)) : [...model.value.values, { key, value: '', unavailable: false, reason: '', ...change }];
    patch({ values });
}

/** 품목을 바꾸면 그 품목 항목으로 칸을 다시 만든다 — 같은 키 값은 살린다. 버전은 지금 카탈로그 */
function changeCategory(no: number): void {
    const next = props.catalog.categories.find((c) => c.no === no);
    if (!next) return;
    patch({ categoryNo: no, catalogVersion: props.catalog.version, values: UtilGroupBuy.noticeValuesFor(next.items, model.value.values) });
}
</script>
