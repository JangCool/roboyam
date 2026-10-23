<template>
    <!--
        2차 인증 코드 입력 — OTP(숫자 6칸, PrimeVue `InputOtp`) 또는 복구 코드(`XXXXX-XXXXX` 한 칸). 로그인 2단계·등록 확인·복구 코드 새로 만들기가 같이 쓴다.
        OTP: 숫자만 받고, 붙여넣기는 숫자만 골라 6칸에 나눠 넣는다(「123 456」도 된다). 6자리가 차면 `complete` 를 낸다(부르는 쪽이 자동 제출).
        복구 코드: 대소문자를 가리지 않고 영문·숫자만 남겨 5자 뒤에 하이픈을 넣는다(`UtilMfa.cleanRecovery`).
        입력값은 부르는 쪽 메모리에만 있다(저장소·로그 금지).
    -->
    <div class="flex flex-col gap-1.5">
        <span :id="labelId" class="text-sm font-medium">{{ label }}</span>
        <div v-if="mode === 'otp'" ref="otpRoot" role="group" :aria-labelledby="labelId" :aria-describedby="messageId" @paste.capture="onPaste">
            <InputOtp
                :model-value="modelValue"
                :length="UtilMfa.OTP_LENGTH"
                integer-only
                :disabled="disabled"
                :invalid="invalid || !!error"
                :pt="{ pcInputText: { root: { inputmode: 'numeric', autocomplete: 'one-time-code', class: 'tabular-nums' } } }"
                @update:model-value="(value: string | boolean | null | undefined) => onOtp(typeof value === 'string' ? value : '')"
            />
        </div>
        <InputText
            v-else
            ref="recoveryInput"
            :model-value="modelValue"
            :aria-labelledby="labelId"
            :aria-describedby="messageId"
            name="recovery-code"
            autocomplete="off"
            autocapitalize="characters"
            spellcheck="false"
            maxlength="11"
            placeholder="XXXXX-XXXXX"
            class="max-w-56 font-mono tracking-wider"
            :disabled="disabled"
            :invalid="invalid || !!error"
            @update:model-value="onRecovery"
        />
        <small :id="messageId" :class="error ? 'text-red-600 dark:text-red-400' : 'text-muted-color'" :role="error ? 'alert' : undefined">{{ error || help }}</small>
    </div>
</template>

<script setup lang="ts">
import UtilMfa from '~/utils/util-mfa';

interface Props {
    modelValue: string;
    mode?: 'otp' | 'recovery';
    label: string;
    help?: string;
    error?: string;
    invalid?: boolean;
    disabled?: boolean;
}

const props = withDefaults(defineProps<Props>(), { mode: 'otp', help: '', error: '', invalid: false, disabled: false });
const emit = defineEmits<{ 'update:modelValue': [value: string]; complete: [value: string] }>();

const labelId = useId();
const messageId = `${labelId}-msg`;
const otpRoot = ref<HTMLElement | null>(null);
const recoveryInput = ref<{ $el: HTMLInputElement } | null>(null);

function onOtp(value: string): void {
    const cleaned = UtilMfa.cleanOtp(value);
    emit('update:modelValue', cleaned);
    if (UtilMfa.isOtp(cleaned) && cleaned !== props.modelValue) emit('complete', cleaned);
}

function onRecovery(value: string | undefined): void {
    emit('update:modelValue', UtilMfa.cleanRecovery(value ?? ''));
}

/** 붙여넣기 — 숫자만 골라 넣는다(PrimeVue 기본은 숫자 아닌 글자가 섞이면 통째로 버린다) */
function onPaste(event: ClipboardEvent): void {
    const text = event.clipboardData?.getData('text') ?? '';
    const digits = UtilMfa.cleanOtp(text);
    if (!digits) return;
    event.preventDefault();
    event.stopPropagation();
    onOtp(digits);
    void nextTick(() => {
        const inputs = otpRoot.value?.querySelectorAll<HTMLInputElement>('input');
        inputs?.[Math.min(digits.length, UtilMfa.OTP_LENGTH) - 1]?.focus();
    });
}

/** 첫 칸(복구 코드면 그 칸)에 초점 — 틀린 뒤 다시 칠 때도 부른다 */
function focus(): void {
    if (props.mode === 'otp') otpRoot.value?.querySelector<HTMLInputElement>('input')?.focus();
    else recoveryInput.value?.$el.focus();
}

defineExpose({ focus });
</script>
