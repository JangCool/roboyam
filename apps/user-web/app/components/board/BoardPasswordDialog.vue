<template>
    <!-- 비회원 글·댓글의 비밀번호 확인 — 고치기·지우기 전에 묻는다. 비밀번호는 이 대화상자의 메모리에만 있고 닫으면 지운다(forms.md 2-1). 틀리면 서버 문구를 그대로 보인다 -->
    <Dialog :visible="visible" modal :header="title" :style="{ width: 'min(24rem, calc(100vw - 2rem))' }" :draggable="false" @update:visible="onVisible">
        <form class="flex flex-col gap-3" @submit.prevent="submit">
            <p v-if="description" class="text-sm text-muted-color">{{ description }}</p>
            <PasswordField v-model="password" :label="t('post.guestPassword')" autocomplete="off" name="guest-password-check" :error="error" />
            <div class="flex justify-end gap-2">
                <Button type="button" :label="t('common.cancel')" severity="secondary" variant="text" @click="onVisible(false)" />
                <Button type="submit" :label="confirmLabel || t('common.confirm')" :severity="danger ? 'danger' : undefined" :loading="loading" :disabled="password.length === 0" />
            </div>
        </form>
    </Dialog>
</template>

<script setup lang="ts">
interface Props {
    visible: boolean;
    title: string;
    description?: string;
    confirmLabel?: string;
    /** 서버 문구(틀린 비밀번호 등) */
    error?: string;
    loading?: boolean;
    /** 지우기처럼 되돌릴 수 없는 동작 — 확인 단추가 붉다 */
    danger?: boolean;
}

const props = withDefaults(defineProps<Props>(), {
    description: '',
    confirmLabel: '',
    error: '',
    loading: false,
    danger: false,
});

const emit = defineEmits<{
    'update:visible': [value: boolean];
    submit: [password: string];
}>();

const { t } = useI18n();
const password = ref('');

function onVisible(value: boolean): void {
    if (!value) password.value = '';
    emit('update:visible', value);
}

function submit(): void {
    if (password.value.length === 0 || props.loading) return;
    emit('submit', password.value);
}

// 닫히면(부르는 쪽이 성공 후 닫아도) 비밀번호를 지운다
watch(
    () => props.visible,
    (open) => {
        if (!open) password.value = '';
    },
);
</script>
