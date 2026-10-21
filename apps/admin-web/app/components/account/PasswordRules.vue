<template>
    <ul class="m-0 flex list-none flex-col gap-0.5 p-0 text-xs" :aria-label="t('account.rulesLabel')">
        <li v-for="rule in rules" :key="rule.key" :class="rule.cls">
            <span aria-hidden="true">{{ rule.mark }}</span>
            {{ rule.text }}
            <span class="sr-only">{{ rule.sr }}</span>
        </li>
    </ul>
</template>

<script setup lang="ts">
import UtilValidation from '~/utils/util-validation';

/** 비밀번호 규칙 안내 — 입력하는 동안 ○(아직) · ✓(충족) · ✕(어긋남)으로 알려 준다. 규칙은 `UtilValidation.password` 와 같다(공개 사이트 가입 규칙). */
interface Props {
    password: string;
}

const props = withDefaults(defineProps<Props>(), {});
const { t } = useI18n();

type State = 'idle' | 'ok' | 'no';

/** 상태별 표지·색과 화면 읽기용 문구의 키 — 글자는 그릴 때 `t` 로 얻는다 */
const STYLE: Record<State, { mark: string; cls: string; sr: string }> = {
    idle: { mark: '○', cls: 'text-muted-color', sr: 'account.ruleIdle' },
    ok: { mark: '✓', cls: 'text-green-700 dark:text-green-400', sr: 'account.ruleOk' },
    no: { mark: '✕', cls: 'text-red-600 dark:text-red-400', sr: 'account.ruleNo' },
};

const rules = computed(() => {
    const value = props.password;
    const check = UtilValidation.password(value);
    const typed = value.length > 0;
    const row = (ok: boolean) => {
        const style = STYLE[!typed ? 'idle' : ok ? 'ok' : 'no'];
        return { mark: style.mark, cls: style.cls, sr: t(style.sr) };
    };

    return [
        { key: 'length', text: check.length || !typed ? t('account.ruleLength') : t('account.ruleLengthNow', { count: value.length }), ...row(check.length) },
        { key: 'bytes', text: check.bytes || !typed ? t('account.ruleBytes') : t('account.ruleBytesNow', { count: UtilValidation.utf8Bytes(value) }), ...row(check.bytes) },
        { key: 'mix', text: t('account.ruleMix'), ...row(check.letterAndDigit) },
    ];
});
</script>
