<template>
    <!--
        비밀 값 입력칸(봇 토큰 · 웹훅 주소) — **저장된 값은 화면에 되돌아오지 않는다.** 그래서 비어 있는 것이 정상이고, 서버에 이미 있으면 「입력됨」만 보인다. 비워 두면 저장된 값을 그대로 쓴다(다시 안 보낸다).
        「입력됨」을 알려 주지 않으면 사람은 값이 지워진 줄 알고 매번 다시 친다. 값은 이 칸의 메모리에만 있고(`forms.md` 2-1) 부모가 다이얼로그를 닫을 때 비운다.
        ⚠ 브라우저가 이 칸을 로그인 칸으로 보고 로그인 비밀번호를 채우지 않게 `autocomplete="new-password"` 를 건다.
    -->
    <LabeledField :label="label" :help="help || (saved ? t('notification.secretKeepHint') : '')" :error="error">
        <template #aside>
            <Tag v-if="saved" class="notification-secret-tag" :value="t('notification.secretSaved')" icon="pi pi-check" severity="success" />
        </template>
        <template #default="{ id, describedBy, invalid }">
            <div class="relative">
                <InputText
                    :id="id"
                    v-model="model"
                    :type="visible ? 'text' : 'password'"
                    :placeholder="saved ? t('notification.secretKeepPlaceholder') : placeholder"
                    :disabled="disabled"
                    :invalid="invalid"
                    :aria-describedby="describedBy"
                    :aria-required="!saved"
                    autocomplete="new-password"
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
/** 새로 입력한 값 — 비워 두면 서버에 저장된 값을 그대로 쓴다 */
const model = defineModel<string>({ required: true });

interface Props {
    label: string;
    /** 서버에 이미 저장돼 있는가 — 「입력됨」 표시와 「비워 두면 그대로」 안내 */
    saved?: boolean;
    placeholder?: string;
    help?: string;
    error?: string;
    disabled?: boolean;
}

withDefaults(defineProps<Props>(), { saved: false, placeholder: '', help: '', error: '', disabled: false });

const { t } = useI18n();
const visible = ref(false);
</script>

<style scoped>
/* 한글 글꼴 보정 — 알약 안에서 글자가 아래로 붙어 보이지 않게 글자만 올린다(components.md 2-4) */
.notification-secret-tag :deep(.p-tag-label) {
    position: relative;
    top: -0.09em;
    white-space: nowrap;
}
</style>
