<template>
    <ul class="flex flex-col gap-0.5 text-xs" :aria-label="t('login.rulesLabel')">
        <li v-for="rule in rules" :key="rule.key" :class="rule.cls">
            <span aria-hidden="true">{{ rule.mark }}</span>
            {{ rule.text }}
            <span class="sr-only">{{ rule.sr }}</span>
        </li>
    </ul>
</template>

<script setup lang="ts">
import UtilValidation from '~/utils/util-validation';

/** 비밀번호 규칙 안내 — 입력하는 동안 ○(아직) · ✓(충족) · ✕(어긋남)으로 알려 준다. 규칙은 UtilValidation 과 같다. */
interface Props {
    password: string;
}

const props = withDefaults(defineProps<Props>(), {});
const { t } = useI18n();

type State = 'idle' | 'ok' | 'no';

/** 상태별 표지·색과 화면 읽기용 문구의 키 — 글자는 그릴 때 `t` 로 얻는다 */
const STYLE: Record<State, { mark: string; cls: string; sr: string }> = {
    idle: { mark: '○', cls: 'text-muted-color', sr: 'login.ruleIdle' },
    ok: { mark: '✓', cls: 'text-green-700 dark:text-green-400', sr: 'login.ruleOk' },
    no: { mark: '✕', cls: 'text-red-600 dark:text-red-400', sr: 'login.ruleNo' },
};

const rules = computed(() => {
    const value = props.password;
    const check = UtilValidation.password(value);
    const typed = value.length > 0;
    const row = (ok: boolean) => {
        const style = STYLE[!typed ? 'idle' : ok ? 'ok' : 'no'];
        return { mark: style.mark, cls: style.cls, sr: t(style.sr) };
    };

    const limits = { min: UtilValidation.PASSWORD_MIN, max: UtilValidation.PASSWORD_MAX };
    const byteLimit = { max: UtilValidation.PASSWORD_MAX_BYTES, hangul: Math.floor(UtilValidation.PASSWORD_MAX_BYTES / 3) };
    return [
        { key: 'length', text: check.length || !typed ? t('login.ruleLength', limits) : t('login.ruleLengthNow', { ...limits, count: value.length }), ...row(check.length) },
        { key: 'bytes', text: check.bytes || !typed ? t('login.ruleBytes', byteLimit) : t('login.ruleBytesNow', { ...byteLimit, count: UtilValidation.utf8Bytes(value) }), ...row(check.bytes) },
        { key: 'mix', text: t('login.ruleMix'), ...row(check.letterAndDigit) },
    ];
});
</script>
