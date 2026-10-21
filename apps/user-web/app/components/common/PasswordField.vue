<template>
    <LabeledField :label="label ?? t('login.passwordLabel')" :hint="hint" :help="help" :success="success" :error="error">
        <template #default="{ id, describedBy, invalid }">
            <div class="relative">
                <InputText
                    :id="id"
                    ref="input"
                    v-model="model"
                    :type="visible ? 'text' : 'password'"
                    :name="name"
                    :autocomplete="autocomplete"
                    :disabled="disabled"
                    :invalid="invalid || forceInvalid"
                    :aria-describedby="describedBy"
                    :placeholder="placeholder"
                    autocapitalize="off"
                    spellcheck="false"
                    fluid
                    class="pr-16"
                />
                <button
                    type="button"
                    class="absolute inset-y-0 right-0 rounded-e-md px-3 text-sm text-muted-color hover:text-color focus-visible:outline-2 focus-visible:outline-primary disabled:opacity-50"
                    :aria-label="visible ? t('login.hidePassword') : t('login.showPassword')"
                    :aria-pressed="visible"
                    :aria-controls="id"
                    :disabled="disabled"
                    @click="visible = !visible"
                >
                    {{ visible ? t('common.hide') : t('common.show') }}
                </button>
            </div>
            <slot name="under" />
        </template>
        <template v-if="$slots.error" #error><slot name="error" /></template>
    </LabeledField>
</template>

<script setup lang="ts">
/** 비밀번호 입력칸 — 보기/숨기기 토글이 있는 LabeledField. 로그인·가입·재설정이 같이 쓴다. */
const model = defineModel<string>({ required: true });

interface Props {
    label?: string;
    name?: string;
    /** `current-password`(로그인) · `new-password`(가입·재설정) · `off`(계정 비밀번호가 아닌 값 — 설치 토큰·메일 서버 비밀번호) */
    autocomplete?: 'current-password' | 'new-password' | 'off';
    placeholder?: string;
    hint?: string;
    help?: string;
    success?: string;
    error?: string;
    /** 문구 없이 붉게만 (로그인 실패처럼 어느 쪽이 틀렸는지 알려 주지 않을 때) */
    forceInvalid?: boolean;
    disabled?: boolean;
}

// label·placeholder 는 기본값을 두지 않는다 — label 은 템플릿에서 `t('login.passwordLabel')` 로 채우고(defineProps 기본값에서는 t 를 못 부른다), placeholder 는 없으면 속성 자체를 달지 않는다
const props = withDefaults(defineProps<Props>(), {
    name: 'password',
    autocomplete: 'current-password',
    hint: '',
    help: '',
    success: '',
    error: '',
    forceInvalid: false,
    disabled: false,
});
const { t } = useI18n();

const visible = ref(false);
const input = ref<{ $el: HTMLInputElement } | null>(null);

defineExpose({ focus: () => input.value?.$el.focus() });
</script>
