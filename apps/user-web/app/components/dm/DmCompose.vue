<template>
    <!-- 대화방 입력창 — 글자가 늘면 최대 5줄까지 커지는 입력칸 + 보내기. 잠기면(`locked`) 입력칸 자리에 이유 한 줄이 나오고 보내기가 꺼진다 -->
    <form
        class="flex shrink-0 items-end gap-2 border-t border-surface bg-surface-0 px-3 pb-[max(0.5rem,env(safe-area-inset-bottom))] pt-2 dark:bg-surface-900"
        :aria-label="t('dm.composeLabel')"
        novalidate
        @submit.prevent="submit"
    >
        <div v-if="locked" class="flex min-h-10 min-w-0 flex-1 items-center rounded-2xl bg-surface-100 px-3 py-2 text-sm text-muted-color dark:bg-surface-800" role="status" data-testid="dm-lock">
            <span class="min-w-0 [overflow-wrap:anywhere]">
                {{ lockText }}
                <NuxtLink v-if="lockLink" :to="lockLink.to" class="ml-1 whitespace-nowrap underline focus-visible:outline-2 focus-visible:outline-primary">{{ lockLink.label }}</NuxtLink>
            </span>
        </div>

        <div v-else class="min-w-0 flex-1">
            <label :for="inputId" class="sr-only">{{ t('dm.bodyLabel') }}</label>
            <textarea
                :id="inputId"
                ref="input"
                :value="modelValue"
                rows="1"
                name="dm-body"
                autocomplete="off"
                :placeholder="placeholderText"
                :disabled="disabled"
                :aria-invalid="invalid || overLimit ? 'true' : undefined"
                :aria-describedby="hasHint ? hintId : undefined"
                class="block min-h-10 w-full resize-none rounded-2xl border bg-surface-0 px-3 py-2 text-base leading-6 text-color placeholder:text-muted-color focus-visible:outline-2 focus-visible:-outline-offset-1 focus-visible:outline-primary disabled:opacity-60 md:text-sm md:leading-5 dark:bg-surface-900"
                :class="invalid || overLimit ? 'border-red-500 dark:border-red-400' : 'border-surface-300 dark:border-surface-700'"
                data-testid="dm-input"
                @input="onInput"
                @keydown="onKeydown"
                @focus="$emit('focus')"
            />
            <!-- 450자부터 카운터가 보이고, 넘으면 붉게 + 이유 -->
            <div v-if="hasHint" :id="hintId" class="mt-1 flex items-center justify-between gap-2 px-1 text-[0.6875rem]">
                <span class="text-red-600 dark:text-red-400">{{ overLimit ? UtilDmMessages.bodyProblem(t, 'TOO_LONG', max) : '' }}</span>
                <span class="tabular-nums" :class="overLimit ? 'font-semibold text-red-600 dark:text-red-400' : 'text-muted-color'" data-testid="dm-counter">{{ t('dm.counter', { count, max }) }}</span>
            </div>
        </div>

        <Button type="submit" :label="t('dm.send')" class="shrink-0" :loading="sending" :disabled="!canSubmit" data-testid="dm-send" />
    </form>
</template>

<script setup lang="ts">
import type { DmNoticeLink } from '~/utils/util-dm-messages';
import UtilDm from '~/utils/util-dm';
import UtilDmMessages from '~/utils/util-dm-messages';
import UtilDmRules from '~/utils/util-dm-rules';

/**
 * 대화방 입력창 — 글자는 `v-model` 로 부모가 든다(보낸 뒤 부모가 비운다). 보내기를 누르면 `send` 만 올린다.
 *  - **Enter 로 보내기는 마우스·키보드가 주인 기기(데스크톱)만** — 모바일은 줄바꿈, Shift+Enter 는 어디서나 줄바꿈, 한글 조합 중 Enter 는 보내지 않는다.
 *  - 보내는 중·요청 제한 기다림·링크 감지(`invalid`)에는 보내기가 꺼진다. 입력칸 글자는 모바일 16px(iOS 확대 방지).
 */
interface Props {
    modelValue: string;
    locked?: boolean;
    lockText?: string;
    lockLink?: DmNoticeLink | null;
    disabled?: boolean;
    sending?: boolean;
    cooling?: boolean;
    invalid?: boolean;
    max?: number;
    placeholder?: string;
}

const props = withDefaults(defineProps<Props>(), {
    locked: false,
    lockText: '',
    lockLink: null,
    disabled: false,
    sending: false,
    cooling: false,
    invalid: false,
    max: 500,
    placeholder: '',
});

const emit = defineEmits<{ 'update:modelValue': [value: string]; send: []; focus: [] }>();

const { t } = useI18n();
const input = ref<HTMLTextAreaElement | null>(null);
const uid = useId();
const inputId = `${uid}-input`;
const hintId = `${uid}-hint`;
const placeholderText = computed(() => props.placeholder || t('dm.composePlaceholder'));

/** Enter 로 보낼 수 있는 기기 — 정밀한 포인터가 주인이면 키보드가 있다고 본다 */
const enterSends = useMediaQuery('(hover: hover) and (pointer: fine)');

const count = computed(() => UtilDmRules.count(props.modelValue));
const overLimit = computed(() => count.value > props.max);
const hasHint = computed(() => UtilDmRules.counterVisible(count.value, props.max));
const problem = computed(() => UtilDmRules.bodyProblem(props.modelValue, props.max));
const canSubmit = computed(() => !props.locked && !props.disabled && !props.sending && !props.cooling && !props.invalid && problem.value === null);

function onInput(event: Event) {
    emit('update:modelValue', (event.target as HTMLTextAreaElement).value);
}

function onKeydown(event: KeyboardEvent) {
    if (event.key !== 'Enter' || event.shiftKey || event.isComposing || event.keyCode === 229) return;
    if (!enterSends.value) return;
    event.preventDefault();
    submit();
}

function submit() {
    if (canSubmit.value) emit('send');
}

/** 글자 수에 맞춰 높이를 늘린다 — 최대 5줄 */
function resize() {
    const el = input.value;
    if (!el) return;
    el.style.height = 'auto';
    const style = getComputedStyle(el);
    const line = Number.parseFloat(style.lineHeight) || 20;
    const box = (name: string) => Number.parseFloat(style.getPropertyValue(name)) || 0;
    const padding = box('padding-top') + box('padding-bottom');
    const border = box('border-top-width') + box('border-bottom-width');
    const limit = line * UtilDm.COMPOSE_MAX_LINES + padding + border;
    const wanted = el.scrollHeight + border;
    el.style.height = `${Math.min(wanted, limit)}px`;
    el.style.overflowY = wanted > limit ? 'auto' : 'hidden';
}

watch(
    () => props.modelValue,
    () => nextTick(resize),
);
watch(
    () => props.locked,
    () => nextTick(resize),
);
onMounted(resize);

/** 입력칸에 초점 — 보낸 뒤 키보드를 닫지 않으려고 부모가 부른다 */
function focus() {
    input.value?.focus();
}

defineExpose({ focus });
</script>
