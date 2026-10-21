<template>
    <!-- 새 비밀번호 + 규칙 체크 목록 + 확인 칸 — 초대 수락 · 비밀번호 재설정 · 내 정보 비밀번호 변경이 같은 모양을 쓴다. 입력한 값은 부모의 메모리에만 있다(`forms.md` 2-1) -->
    <div class="flex flex-col gap-3">
        <div class="flex flex-col gap-2">
            <PasswordField
                ref="passwordField"
                v-model="password"
                name="new-password"
                autocomplete="new-password"
                :label="label || t('account.newPasswordLabel')"
                :disabled="disabled"
                :error="passwordError"
                :extra-describedby="rulesId"
            />
            <PasswordRules :id="rulesId" :password="password" />
        </div>

        <!-- 확인 칸을 떠났을 때부터 서로 다르다고 알린다(입력하는 도중에는 붉게 하지 않는다). 같아지면 바로 알린다 -->
        <div @focusout="touched = true">
            <PasswordField
                ref="confirmField"
                v-model="confirm"
                name="confirm-password"
                autocomplete="new-password"
                :label="t('account.confirmPasswordLabel')"
                :disabled="disabled"
                :error="shownConfirmError"
                :success="matched ? t('account.confirmMatch') : ''"
            />
        </div>
    </div>
</template>

<script setup lang="ts">
const password = defineModel<string>('password', { required: true });
const confirm = defineModel<string>('confirm', { required: true });

interface Props {
    /** 새 비밀번호 칸의 이름표 — 없으면 "새 비밀번호" (`defineProps` 기본값에서는 `t` 를 못 부르므로 템플릿에서 채운다) */
    label?: string;
    /** 새 비밀번호 칸 아래 오류(서버 문구 또는 미리 검사한 문구) */
    passwordError?: string;
    /** 확인 칸 아래 오류 — 있으면 화면이 계산한 "서로 달라요"보다 앞선다 */
    confirmError?: string;
    disabled?: boolean;
}

const props = withDefaults(defineProps<Props>(), { label: '', passwordError: '', confirmError: '', disabled: false });
const { t } = useI18n();

const rulesId = useId();
const passwordField = ref<{ focus: () => void } | null>(null);
const confirmField = ref<{ focus: () => void } | null>(null);

const touched = ref(false);
// 부모가 비우면(제출이 끝났거나 되돌렸을 때) 처음 상태로
watch(confirm, (value) => {
    if (!value) touched.value = false;
});

const matched = computed(() => confirm.value.length > 0 && confirm.value === password.value);
const shownConfirmError = computed(() => props.confirmError || (touched.value && confirm.value.length > 0 && confirm.value !== password.value ? t('account.confirmMismatch') : ''));

defineExpose({
    focusPassword: () => passwordField.value?.focus(),
    focusConfirm: () => confirmField.value?.focus(),
});
</script>
