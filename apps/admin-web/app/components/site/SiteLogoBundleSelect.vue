<template>
    <!--
        로고 묶음 고르기 — 보관함의 묶음을 작은 미리보기(밝은 바탕 한글 칸)와 이름으로 보인다. 보이기 규칙의 「평소 로고」·기념일 줄·성별 칸이 같이 쓴다.
        묶음이 많아(평소 2 + 기념일 56 + 올린 것) 세 그룹(평소 로고 / 기념일 로고 / 올린 로고)으로 나누고 검색을 켠다.
        `dayUnit`(기념일 규칙 줄 창): 기념일 로고를 **날 단위**로 고른다 — 날마다 하나만 보이고 값은 그날의 캐릭터 벌 id 다
          (같은 날의 두 벌 중 어느 것을 가리켜도 실제로 보이는 벌은 평소 로고를 따른다 — `UtilSiteLogoRule.matchStyle`). 이미 글자 벌 id 가 저장돼 있으면 그날로 보이기만 하고 값은 건드리지 않는다.
        `allowEmpty` 면 맨 위에 「고르지 않음」이 있고 그때 값은 null 이다. 서버는 부르지 않는다.
    -->
    <Select
        :model-value="selectedValue"
        :options="groups"
        option-label="label"
        option-value="value"
        option-group-label="label"
        option-group-children="items"
        filter
        :filter-placeholder="t('siteLogo.bundleSearch')"
        :empty-filter-message="t('common.emptyFiltered')"
        scroll-height="18rem"
        :input-id="inputId"
        :disabled="disabled"
        :invalid="invalid"
        :aria-describedby="describedBy"
        size="small"
        class="w-full max-w-sm"
        @update:model-value="onChange"
    >
        <template #value>
            <span class="flex min-w-0 items-center gap-2">
                <span v-if="selected?.preview" class="flex h-6 w-20 shrink-0 items-center justify-center rounded border border-(--line) bg-surface-0 px-1">
                    <img :src="selected.preview" alt="" class="block h-4 w-auto max-w-full object-contain" />
                </span>
                <span class="min-w-0 truncate">{{ selected?.label ?? t('siteLogo.bundlePick') }}</span>
            </span>
        </template>
        <template #optiongroup="{ option }">
            <span v-if="option.label" class="text-xs font-semibold text-muted-color">{{ option.label }}</span>
        </template>
        <template #option="{ option }">
            <span class="flex min-w-0 items-center gap-2">
                <span v-if="option.preview" class="flex h-6 w-20 shrink-0 items-center justify-center rounded border border-(--line) bg-surface-0 px-1">
                    <img :src="option.preview" alt="" loading="lazy" class="block h-4 w-auto max-w-full object-contain" />
                </span>
                <span class="min-w-0 truncate">{{ option.label }}</span>
            </span>
        </template>
    </Select>
</template>

<script setup lang="ts">
import type { SiteLogoBundle } from '~/composables/site/api/useSiteLogoLibraryApi.types';
import UtilSiteLogoRule from '~/utils/util-site-logo-rule';

interface Props {
    /** 고른 묶음 id — 고르지 않았으면 null */
    modelValue: string | null;
    /** 보관함의 묶음 */
    bundles: SiteLogoBundle[];
    /** 「고르지 않음」을 고를 수 있는가 */
    allowEmpty?: boolean;
    /** 기념일 로고를 날 단위로 고른다(날마다 하나 — 값은 그날의 캐릭터 벌 id) */
    dayUnit?: boolean;
    inputId?: string;
    disabled?: boolean;
    invalid?: boolean;
    describedBy?: string;
}

const props = withDefaults(defineProps<Props>(), { allowEmpty: false, dayUnit: false, inputId: undefined, disabled: false, invalid: false, describedBy: undefined });
const emit = defineEmits<{ 'update:modelValue': [value: string | null] }>();

const { t } = useI18n();

/** 「고르지 않음」의 고르개 값 — PrimeVue Select 는 null 값을 항목으로 다루지 못한다 */
const NONE = '';

interface BundleOption {
    label: string;
    value: string;
    preview: string | null;
}

interface BundleOptionGroup {
    /** 그룹 이름 — 「고르지 않음」만 든 맨 위 그룹은 빈 글자(머리를 그리지 않는다) */
    label: string;
    items: BundleOption[];
}

function optionOf(bundle: SiteLogoBundle, label = bundle.name): BundleOption {
    return { label, value: bundle.id, preview: UtilSiteLogoRule.cardPreview(bundle.logos) };
}

const parts = computed(() => UtilSiteLogoRule.groups(props.bundles));

const groups = computed<BundleOptionGroup[]>(() => {
    const out: BundleOptionGroup[] = [];
    if (props.allowEmpty) out.push({ label: '', items: [{ label: t('siteLogo.bundleNone'), value: NONE, preview: null }] });
    if (parts.value.usual.length) out.push({ label: t('siteLogo.groupUsual'), items: parts.value.usual.map((bundle) => optionOf(bundle)) });
    const days: BundleOption[] = [];
    for (const pair of parts.value.days) {
        if (props.dayUnit) {
            // 날마다 하나 — 캐릭터 벌(없으면 글자 벌)을 대표로
            const lead = pair.character ?? pair.wordmark;
            if (lead) days.push(optionOf(lead, pair.name));
        } else {
            if (pair.character) days.push(optionOf(pair.character));
            if (pair.wordmark) days.push(optionOf(pair.wordmark));
        }
    }
    if (days.length) out.push({ label: t('siteLogo.groupDays'), items: days });
    if (parts.value.uploaded.length) out.push({ label: t('siteLogo.groupUploaded'), items: parts.value.uploaded.map((bundle) => optionOf(bundle)) });
    return out;
});

/** 고르개에 걸 값 — 날 단위에서는 글자 벌 id 도 그날의 대표(캐릭터 벌) 항목으로 보인다(값을 바꿔 올리지는 않는다) */
const selectedValue = computed(() => {
    const value = props.modelValue ?? NONE;
    if (!props.dayUnit || value === NONE) return value;
    const bundle = props.bundles.find((item) => item.id === value);
    if (!bundle?.dayKey) return value;
    const pair = parts.value.days.find((item) => item.dayKey === bundle.dayKey);
    return (pair?.character ?? pair?.wordmark)?.id ?? value;
});

const selected = computed<BundleOption | null>(() => {
    for (const group of groups.value) {
        const hit = group.items.find((option) => option.value === selectedValue.value);
        if (hit) return hit;
    }
    return null;
});

function onChange(value: string | null): void {
    emit('update:modelValue', value ? value : null);
}
</script>
