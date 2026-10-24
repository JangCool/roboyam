<template>
    <!--
        투표 칸(post-poll-contract 「화면」) — 「투표 붙이기」 켬/끔, 켜면 질문(100자) · 선택지 2~6(추가·삭제·위아래, 각 50자) · 「복수 선택」 · 「마감」(display-time `AppDateTimePicker`, 비우면 마감 없음) ·
          「다시 투표 허용」(기본 끔, 끄면 「한 번 투표하면 바꿀 수 없어요」). 글쓰기·고치기 폼(`PostEditorForm` 의 `extra` 자리)이 찬반 토론 칸(`DebateSettingsFields`)과 같은 모양으로 쓴다.
        그리기만 하고 검사 문구·저장은 부르는 화면이 한다(`errors` — 서버 `fields."poll.*"` 또는 화면 검사). 같은 선택지(대소문자 무시) 칸은 빨간 테두리.
        `locked`(고치기 — 표가 들어와 서버 `editable false`)면 칸 대신 투표 요약 + 「표가 들어와 투표를 고칠 수 없어요」.
    -->
    <fieldset class="m-0 flex min-w-0 flex-col gap-3 rounded-lg border border-surface p-3.5" data-testid="poll-settings">
        <legend v-if="legend" class="px-1 text-sm font-semibold">{{ t('poll.legend') }}</legend>

        <!-- 표가 있어 고칠 수 없다 — 요약만 -->
        <template v-if="locked">
            <p class="m-0 flex items-start gap-1.5 text-xs leading-relaxed text-amber-700 dark:text-amber-300" data-testid="poll-settings-locked">
                <i class="pi pi-lock mt-0.5 text-[0.6875rem]" aria-hidden="true" />{{ t('poll.lockedNotice') }}
            </p>
            <div class="flex flex-col gap-1.5 rounded-md bg-surface-50 px-3 py-2.5 dark:bg-surface-800/60">
                <p class="m-0 break-words text-sm font-medium">{{ modelValue.question }}</p>
                <ol class="m-0 flex list-decimal flex-col gap-0.5 pl-5 text-sm">
                    <li v-for="(option, i) in filled" :key="i" class="break-words">{{ option }}</li>
                </ol>
                <p class="m-0 text-xs text-muted-color">{{ summaryLine }}</p>
            </div>
        </template>

        <template v-else>
            <div class="flex flex-col gap-1">
                <label class="flex items-center gap-2 text-sm font-medium">
                    <ToggleSwitch
                        :model-value="modelValue.enabled"
                        :input-id="`${uid}-enabled`"
                        :disabled="disabled"
                        :aria-describedby="`${uid}-enabled-help`"
                        data-testid="poll-settings-enabled"
                        @update:model-value="patch({ enabled: Boolean($event) })"
                    />
                    <span>{{ t('poll.enableLabel') }}</span>
                </label>
                <p :id="`${uid}-enabled-help`" class="m-0 text-xs leading-relaxed text-muted-color">{{ t('poll.enableHelp') }}</p>
            </div>

            <div v-if="modelValue.enabled" class="flex flex-col gap-4 border-l-2 border-surface pl-3.5">
                <!-- 질문 -->
                <div class="flex flex-col gap-1">
                    <div class="flex items-baseline justify-between gap-2">
                        <label :for="`${uid}-question`" class="text-sm font-medium">{{ t('poll.questionLabel') }}</label>
                        <span class="text-xs tabular-nums" :class="questionLength > UtilPoll.QUESTION_MAX ? 'text-red-600 dark:text-red-400' : 'text-muted-color'">
                            {{ t('poll.counter', { n: questionLength, max: UtilPoll.QUESTION_MAX }) }}
                        </span>
                    </div>
                    <InputText
                        :id="`${uid}-question`"
                        :model-value="modelValue.question"
                        :placeholder="t('poll.questionPlaceholder')"
                        autocomplete="off"
                        fluid
                        :disabled="disabled"
                        :invalid="Boolean(errors.question)"
                        :aria-describedby="errors.question ? `${uid}-question-error` : undefined"
                        data-testid="poll-question"
                        @update:model-value="patch({ question: String($event ?? '') })"
                        @keydown.enter.prevent
                    />
                    <p v-if="errors.question" :id="`${uid}-question-error`" role="alert" class="m-0 text-xs text-red-600 dark:text-red-400">{{ errors.question }}</p>
                </div>

                <!-- 선택지 -->
                <div class="flex flex-col gap-1.5" role="group" :aria-labelledby="`${uid}-options`" :aria-describedby="errors.options ? `${uid}-options-error` : undefined">
                    <p :id="`${uid}-options`" class="m-0 text-sm font-medium">
                        {{ t('poll.optionsLabel') }}
                        <small class="ml-1 text-xs font-normal text-muted-color">{{ t('poll.optionsHint', { min: UtilPoll.OPTIONS_MIN, max: UtilPoll.OPTIONS_MAX, n: filled.length }) }}</small>
                    </p>
                    <div v-for="(option, i) in modelValue.options" :key="i" class="flex items-start gap-1">
                        <div class="min-w-0 flex-1">
                            <InputText
                                :model-value="option"
                                :placeholder="t('poll.optionPlaceholder', { n: i + 1 })"
                                :aria-label="t('poll.optionAria', { n: i + 1 })"
                                autocomplete="off"
                                fluid
                                :disabled="disabled"
                                :invalid="optionInvalid(i, option)"
                                data-testid="poll-option"
                                @update:model-value="setOption(i, String($event ?? ''))"
                                @keydown.enter.prevent
                            />
                            <p v-if="UtilPoll.length(option) >= COUNTER_FROM" class="m-0 mt-0.5 text-right text-xs tabular-nums" :class="UtilPoll.length(option) > UtilPoll.OPTION_MAX ? 'text-red-600 dark:text-red-400' : 'text-muted-color'">
                                {{ t('poll.counter', { n: UtilPoll.length(option), max: UtilPoll.OPTION_MAX }) }}
                            </p>
                        </div>
                        <Button type="button" icon="pi pi-arrow-up" severity="secondary" variant="text" size="small" :aria-label="t('poll.optionUp', { n: i + 1 })" :disabled="disabled || i === 0" @click="move(i, -1)" />
                        <Button
                            type="button"
                            icon="pi pi-arrow-down"
                            severity="secondary"
                            variant="text"
                            size="small"
                            :aria-label="t('poll.optionDown', { n: i + 1 })"
                            :disabled="disabled || i === modelValue.options.length - 1"
                            @click="move(i, 1)"
                        />
                        <!-- 최소(2칸)만 남으면 지울 수 없다 -->
                        <Button
                            type="button"
                            icon="pi pi-times"
                            severity="secondary"
                            variant="text"
                            size="small"
                            :aria-label="t('poll.optionRemove', { n: i + 1 })"
                            :disabled="disabled || modelValue.options.length <= UtilPoll.OPTIONS_MIN"
                            data-testid="poll-option-remove"
                            @click="remove(i)"
                        />
                    </div>
                    <div class="flex flex-wrap items-center gap-2">
                        <Button
                            type="button"
                            :label="t('poll.optionAdd')"
                            icon="pi pi-plus"
                            severity="secondary"
                            variant="outlined"
                            size="small"
                            :disabled="disabled || atMax"
                            data-testid="poll-option-add"
                            @click="add"
                        />
                        <span v-if="atMax" class="text-xs text-muted-color">{{ t('poll.optionsMaxNote', { max: UtilPoll.OPTIONS_MAX }) }}</span>
                    </div>
                    <p v-if="errors.options" :id="`${uid}-options-error`" role="alert" class="m-0 text-xs text-red-600 dark:text-red-400">{{ errors.options }}</p>
                </div>

                <!-- 복수 선택 -->
                <div class="flex flex-col gap-1">
                    <label class="flex items-center gap-2 text-sm">
                        <ToggleSwitch
                            :model-value="modelValue.multiple"
                            :input-id="`${uid}-multiple`"
                            :disabled="disabled"
                            data-testid="poll-multiple"
                            @update:model-value="patch({ multiple: Boolean($event) })"
                        />
                        <span>{{ t('poll.multipleLabel') }}</span>
                    </label>
                    <p class="m-0 text-xs leading-relaxed text-muted-color">{{ modelValue.multiple ? t('poll.multipleOn') : t('poll.multipleOff') }}</p>
                </div>

                <!-- 마감 -->
                <div class="flex flex-col gap-1">
                    <div class="flex items-baseline justify-between gap-2">
                        <label :for="`${uid}-closes`" class="text-sm font-medium">{{ t('poll.closesAtLabel') }}</label>
                        <button
                            v-if="modelValue.closesAt"
                            type="button"
                            class="rounded px-1 text-xs text-muted-color underline hover:text-color focus-visible:outline-2 focus-visible:outline-primary disabled:opacity-50"
                            :disabled="disabled"
                            data-testid="poll-closes-clear"
                            @click="patch({ closesAt: '' })"
                        >
                            {{ t('poll.closesAtClear') }}
                        </button>
                    </div>
                    <AppDateTimePicker
                        :model-value="modelValue.closesAt"
                        :input-id="`${uid}-closes`"
                        :min="range.min"
                        :max="range.max"
                        :disabled="disabled"
                        :invalid="Boolean(errors.closesAt)"
                        :placeholder="t('poll.closesAtNone')"
                        :described-by="`${uid}-closes-help`"
                        class="max-w-xs"
                        data-testid="poll-closes"
                        @update:model-value="patch({ closesAt: $event })"
                    />
                    <p v-if="errors.closesAt" role="alert" class="m-0 text-xs text-red-600 dark:text-red-400">{{ errors.closesAt }}</p>
                    <p :id="`${uid}-closes-help`" class="m-0 text-xs leading-relaxed text-muted-color">{{ t('poll.closesAtHelp', { days: UtilPoll.CLOSE_MAX_DAYS }) }}</p>
                </div>

                <!-- 다시 투표 허용 -->
                <div class="flex flex-col gap-1">
                    <label class="flex items-center gap-2 text-sm">
                        <ToggleSwitch
                            :model-value="modelValue.changeAllowed"
                            :input-id="`${uid}-change`"
                            :disabled="disabled"
                            :aria-describedby="`${uid}-change-help`"
                            data-testid="poll-change-allowed"
                            @update:model-value="patch({ changeAllowed: Boolean($event) })"
                        />
                        <span>{{ t('poll.changeAllowedLabel') }}</span>
                    </label>
                    <p :id="`${uid}-change-help`" class="m-0 text-xs leading-relaxed" :class="modelValue.changeAllowed ? 'text-muted-color' : 'font-medium text-amber-700 dark:text-amber-300'">
                        {{ modelValue.changeAllowed ? t('poll.changeAllowedHelp') : t('poll.changeAllowedOff') }}
                    </p>
                </div>

                <p class="m-0 text-xs leading-relaxed text-muted-color">{{ t('poll.anonymousNote') }}</p>
            </div>
        </template>

        <p v-if="errors.poll" role="alert" class="m-0 text-xs text-red-600 dark:text-red-400">{{ errors.poll }}</p>
    </fieldset>
</template>

<script setup lang="ts">
import { EMPTY_POLL_FIELD_ERRORS, type PollFieldErrors, type PollFormValues } from '~/components/board/PollSettingsFields.types';
import UtilPoll from '~/utils/util-poll';

interface Props {
    modelValue: PollFormValues;
    /** 묶음 제목 「투표」를 보인다 */
    legend?: boolean;
    /** 표가 들어와 고칠 수 없다(서버 `editable false`) — 요약만 보인다 */
    locked?: boolean;
    disabled?: boolean;
    /** 칸별 오류(서버 문구 또는 화면 검사 문구) */
    errors?: PollFieldErrors;
}

const props = withDefaults(defineProps<Props>(), { legend: true, locked: false, disabled: false, errors: () => ({ ...EMPTY_POLL_FIELD_ERRORS }) });
const emit = defineEmits<{ 'update:modelValue': [value: PollFormValues] }>();

/** 선택지 글자 수는 이만큼부터 보인다(평소에는 칸이 지저분하지 않게) */
const COUNTER_FROM = 40;

const { t } = useI18n();
const time = useDisplayTime();
const uid = useId();

/** 마감으로 고를 수 있는 범위 — 칸을 연 때 기준(정확한 판정은 화면 검사·서버) */
const range = UtilPoll.closeRange(Date.now());

const questionLength = computed(() => UtilPoll.length(UtilPoll.cleanQuestion(props.modelValue.question)));
const filled = computed(() => UtilPoll.filledOptions(props.modelValue.options));
const atMax = computed(() => props.modelValue.options.length >= UtilPoll.OPTIONS_MAX);
const duplicates = computed(() => UtilPoll.duplicateIndexes(props.modelValue.options));

/** 잠긴 투표의 요약 한 줄 — 복수/단일 · 마감 · 다시 투표 */
const summaryLine = computed(() => {
    const v = props.modelValue;
    return [
        v.multiple ? t('poll.multipleTag') : t('poll.singleTag'),
        v.closesAt ? t('poll.closesAtTag', { at: time.value.full(v.closesAt) }) : t('poll.noCloseTag'),
        v.changeAllowed ? t('poll.changeAllowedTag') : t('poll.changeLockedTag'),
    ].join(' · ');
});

function patch(change: Partial<PollFormValues>): void {
    emit('update:modelValue', { ...props.modelValue, ...change });
}

function optionInvalid(i: number, option: string): boolean {
    return duplicates.value.has(i) || UtilPoll.length(option.trim()) > UtilPoll.OPTION_MAX;
}

function setOption(i: number, value: string): void {
    const options = [...props.modelValue.options];
    options[i] = value;
    patch({ options });
}

function add(): void {
    if (atMax.value) return;
    patch({ options: [...props.modelValue.options, ''] });
}

function remove(i: number): void {
    if (props.modelValue.options.length <= UtilPoll.OPTIONS_MIN) return;
    patch({ options: props.modelValue.options.filter((_, j) => j !== i) });
}

function move(i: number, delta: -1 | 1): void {
    const j = i + delta;
    const options = [...props.modelValue.options];
    if (j < 0 || j >= options.length) return;
    [options[i], options[j]] = [options[j] as string, options[i] as string];
    patch({ options });
}
</script>
