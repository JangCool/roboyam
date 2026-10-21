<template>
    <LabeledField :label="label || t('login.passwordLabel')" :hint="hint" :help="help" :success="success" :error="error">
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
                    :aria-describedby="[describedBy, extraDescribedby].filter(Boolean).join(' ') || undefined"
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
        </template>
    </LabeledField>
</template>

<script setup lang="ts">
/** 비밀번호 입력칸 — 보기/숨기기 토글이 있는 LabeledField. */
const model = defineModel<string>({ required: true });

interface Props {
    /** 없으면 "비밀번호" (`defineProps` 기본값에서는 `t` 를 못 부르므로 템플릿에서 채운다) */
    label?: string;
    name?: string;
    autocomplete?: 'current-password' | 'new-password' | 'off';
    hint?: string;
    help?: string;
    success?: string;
    error?: string;
    /** 문구 없이 붉게만 (로그인 실패처럼 어느 쪽이 틀렸는지 알려 주지 않을 때) */
    forceInvalid?: boolean;
    disabled?: boolean;
    /** 칸 설명으로 더 이어 줄 요소의 id (예: 비밀번호 규칙 목록) — 칸 아래 안내·오류 뒤에 붙는다 */
    extraDescribedby?: string;
}

const props = withDefaults(defineProps<Props>(), {
    label: '',
    name: 'password',
    autocomplete: 'current-password',
    hint: '',
    help: '',
    success: '',
    error: '',
    forceInvalid: false,
    disabled: false,
    extraDescribedby: '',
});
const { t } = useI18n();

const visible = ref(false);
const input = ref<{ $el: HTMLInputElement } | null>(null);

defineExpose({ focus: () => input.value?.$el.focus() });
</script>
