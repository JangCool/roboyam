<template>
    <!--
        표시 값 폼 — 언어 · 시간대 · 시간 형식 · 날짜 형식 + 미리보기("이렇게 보여요"). 값(`v-model`)은 부르는 쪽이 들고, **서버는 부르지 않는다** — 저장은 쓰는 앱의 화면이 한다.
        user-web 의 개인 설정 화면과 admin-web 의 「일반」 > 표시 기본값 구획이 이 폼을 같이 쓴다. 구획 제목·저장 줄·"지금 쓰는 값" 같은 층에 따라 다른 것은 쓰는 쪽이 감싼다.
        미리보기와 선택지 옆 견본은 **고르는 중인 값**으로 `UtilTime.of(values)` 를 만들어 그린다 — 저장 전 값이라 앱 전체의 표시 값에는 영향이 없다.
        (community admin `DisplayValueForm` 에 hamonica3 의 「언어」 칸을 더한 모양이다. 문구는 이 레이어의 `i18n/locales/{ko,en}/display.ts`. 앱 컴포넌트인 `LabeledField` 는 레이어가 쓸 수 없어 라벨·도움말·오류 줄을 이 파일 안에 같은 마크업으로 적었다.)
    -->
    <div class="flex flex-col gap-4" data-testid="display-value-form">
        <div class="flex flex-col gap-1.5">
            <label :for="languageInputId" class="text-sm font-medium">{{ t('display.language') }}</label>
            <Select
                :model-value="model.language"
                :options="languageChoices"
                option-label="label"
                option-value="value"
                :input-id="languageInputId"
                class="w-56"
                :disabled="disabled"
                :invalid="Boolean(errors?.language)"
                :aria-describedby="errors?.language ? languageErrorId : undefined"
                data-testid="display-language"
                @update:model-value="(value: DisplayValues['language']) => change({ language: value })"
            />
            <p v-if="errors?.language" :id="languageErrorId" role="alert" class="m-0 text-xs text-red-600 dark:text-red-400">{{ errors.language }}</p>
        </div>

        <div class="flex flex-col gap-1.5">
            <label :for="zoneInputId" class="text-sm font-medium">{{ t('display.zoneLabel') }}</label>
            <Select
                :model-value="model.zoneId"
                :options="zoneGroups"
                option-group-label="label"
                option-group-children="items"
                option-label="label"
                option-value="value"
                :input-id="zoneInputId"
                :placeholder="t('display.zonePlaceholder')"
                filter
                :filter-fields="['label', 'search']"
                auto-filter-focus
                :filter-placeholder="t('display.zoneFilter')"
                :empty-filter-message="t('display.zoneEmpty')"
                scroll-height="18rem"
                class="w-full max-w-xl"
                :disabled="disabled"
                :invalid="Boolean(errors?.zoneId)"
                :aria-describedby="zoneMessageId"
                data-testid="display-zone"
                @update:model-value="(value: string) => change({ zoneId: value })"
            />
            <div :id="zoneMessageId" aria-live="polite" class="text-xs leading-relaxed">
                <p v-if="errors?.zoneId" role="alert" class="m-0 text-red-600 dark:text-red-400">{{ errors.zoneId }}</p>
                <p v-else class="m-0 text-muted-color">{{ t('display.zoneHelp') }}</p>
            </div>
        </div>

        <fieldset class="m-0 min-w-0 border-0 p-0" :aria-describedby="errors?.timeFormat ? timeErrorId : undefined" data-testid="display-time-format">
            <legend class="mb-1.5 p-0 text-sm font-medium">{{ t('display.timeFormat') }}</legend>
            <div class="flex flex-wrap gap-x-5 gap-y-1.5">
                <div v-for="option in timeChoices" :key="option.value" class="flex items-center gap-2">
                    <RadioButton
                        :model-value="model.timeFormat"
                        :value="option.value"
                        :input-id="`${uid}-time-${option.value}`"
                        :name="`${uid}-time`"
                        :disabled="disabled"
                        :data-testid="`display-time-${option.value}`"
                        @update:model-value="(value: DisplayValues['timeFormat']) => change({ timeFormat: value })"
                    />
                    <label :for="`${uid}-time-${option.value}`" class="cursor-pointer text-sm">{{ option.label }}</label>
                </div>
            </div>
            <p v-if="errors?.timeFormat" :id="timeErrorId" role="alert" class="m-0 mt-1 text-xs text-red-600 dark:text-red-400" data-testid="display-time-error">{{ errors.timeFormat }}</p>
        </fieldset>

        <fieldset class="m-0 min-w-0 border-0 p-0" :aria-describedby="errors?.dateFormat ? dateErrorId : undefined" data-testid="display-date-format">
            <legend class="mb-1.5 p-0 text-sm font-medium">{{ t('display.dateFormat') }}</legend>
            <div class="flex flex-col gap-1.5">
                <div v-for="option in dateChoices" :key="option.value" class="flex items-center gap-2">
                    <RadioButton
                        :model-value="model.dateFormat"
                        :value="option.value"
                        :input-id="`${uid}-date-${option.value}`"
                        :name="`${uid}-date`"
                        :disabled="disabled"
                        :data-testid="`display-date-${option.value}`"
                        @update:model-value="(value: DisplayValues['dateFormat']) => change({ dateFormat: value })"
                    />
                    <label :for="`${uid}-date-${option.value}`" class="cursor-pointer text-sm">{{ option.label }}</label>
                </div>
            </div>
            <p v-if="errors?.dateFormat" :id="dateErrorId" role="alert" class="m-0 mt-1 text-xs text-red-600 dark:text-red-400" data-testid="display-date-error">{{ errors.dateFormat }}</p>
        </fieldset>

        <!-- 미리보기 — 값을 바꿀 때마다 바뀐 글자를 스크린리더가 읽는다(aria-live) -->
        <div class="rounded-lg bg-surface-100 p-3 dark:bg-surface-800" aria-live="polite" data-testid="display-preview">
            <p class="m-0 text-xs font-medium text-muted-color">{{ t('display.previewTitle') }}</p>
            <p class="m-0 mt-1 flex flex-wrap items-baseline gap-x-4 gap-y-1 text-sm">
                <b class="font-semibold" data-testid="display-preview-today">{{ preview.today }}</b>
                <span class="text-muted-color" data-testid="display-preview-past">{{ t('display.previewPast') }} {{ preview.past }}</span>
            </p>
        </div>
    </div>
</template>

<script setup lang="ts">
import UtilDisplayChoices from '#layers/display-time/utils/util-display-choices';
import UtilDisplayForm, { type DisplayFieldErrors } from '#layers/display-time/utils/util-display-form';
import type { DisplayValues } from '#layers/display-time/utils/util-time';

/**
 * 표시 값(언어·시간대·시간 형식·날짜 형식)을 고르는 폼 — 값은 `v-model`(네 칸을 한 객체로), 칸 아래 서버 문구는 `errors`, 저장 중 잠금은 `disabled`.
 * 시간대 목록은 "주요 시간대" 묶음 + 브라우저의 `Intl.supportedValuesOf('timeZone')` 이고(`UtilDisplayChoices`) 없는 환경은 주요 시간대뿐이다. 저장된 시간대가 목록에 없어도 선택 상자가 그 값을 보인다.
 * `now` 는 견본·미리보기의 "지금"(밀리초) — 안 주면 실제 지금이고 30초마다 다시 읽는다(시험·고정 표시용으로만 준다).
 */
const model = defineModel<DisplayValues>({ required: true });

interface Props {
    /** 칸 아래 서버 문구 */
    errors?: DisplayFieldErrors;
    /** 저장 중 — 칸을 잠근다 */
    disabled?: boolean;
    /** 고정할 "지금"(밀리초). 없으면 실제 지금 */
    now?: number;
}

// now 는 기본값을 두지 않는다 — 없으면(undefined) 실제 시계를 쓰는 분기가 있다
const props = withDefaults(defineProps<Props>(), {
    errors: () => ({}),
    disabled: false,
});

const { t, locale } = useI18n();
const uid = useId();
const languageInputId = `${uid}-language`;
const languageErrorId = `${uid}-language-error`;
const zoneInputId = `${uid}-zone`;
const zoneMessageId = `${uid}-zone-msg`;
const timeErrorId = `${uid}-time-error`;
const dateErrorId = `${uid}-date-error`;

/** 실제 지금 — 분이 바뀌어도 견본이 낡지 않게 30초마다 다시 읽는다(고정한 `now` 가 있으면 읽지 않는다) */
const tick = ref(Date.now());
let timer: ReturnType<typeof setInterval> | null = null;
onMounted(() => {
    if (props.now !== undefined) return;
    timer = setInterval(() => {
        tick.value = Date.now();
    }, 30_000);
});
onBeforeUnmount(() => {
    if (timer) clearInterval(timer);
    timer = null;
});
const clock = computed(() => props.now ?? tick.value);

/** 시간대 선택지 — 주요 시간대 묶음 + 브라우저 목록(옛 철자 별칭 포함). 브라우저 목록은 한 번만 얻고, 저장된 값이 목록에 없어도 선택 상자가 그 값을 보인다 */
const supportedZones = UtilDisplayChoices.browserZones();
/** 선택지 글자의 시차는 분 단위로만 바뀌므로 분이 바뀔 때만 다시 만든다 */
const clockMinute = computed(() => Math.floor(clock.value / 60_000));
/**
 * 선택지 글자(이름·묶음 제목·시간대 도시 이름·견본의 오전/AM)는 지금 화면 언어를 따른다 — 폼이 고르는 중인 `language` 가 아니라 이미 적용된 언어다.
 * 시간대 글자 찾기는 선택지의 `label` 과 두 언어의 도시 이름 `search`(`서울 Seoul`)를 함께 본다.
 */
const i18n = computed(() => ({ t, language: locale.value }));
const languageChoices = [...UtilDisplayChoices.LANGUAGE_CHOICES];
const zoneGroups = computed(() => UtilDisplayChoices.zoneGroups(supportedZones, model.value.zoneId, clockMinute.value * 60_000, i18n.value));

const preview = computed(() => UtilDisplayForm.preview(model.value, clock.value));

/** 시간 형식·날짜 형식 선택지 — 이름 뒤에 고른 시간대의 지금 견본을 괄호로 */
const timeChoices = computed(() => UtilDisplayChoices.timeFormatChoices(model.value.zoneId, clock.value, i18n.value));
const dateChoices = computed(() => UtilDisplayChoices.dateFormatChoices(model.value.zoneId, clock.value, i18n.value));

/** 바뀐 칸만 덮어 한 객체로 올린다 */
function change(patch: Partial<DisplayValues>): void {
    model.value = { ...model.value, ...patch };
}
</script>
