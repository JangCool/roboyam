<template>
    <!-- 설치 완료 — 자동 로그인하지 않는다. 방금 만든 루트 계정으로 로그인 화면에서 들어간다 -->
    <StatusPanel icon="pi pi-check" tone="success" :title="t('install.doneTitle')" data-testid="install-done">
        <p>{{ t('install.doneText', { email }) }}</p>
        <template #actions>
            <Button type="button" :label="t('install.goLogin')" fluid size="large" data-testid="install-go-login" @click="emit('login')" />
        </template>
    </StatusPanel>
</template>

<script setup lang="ts">
/** 설치 완료 화면 — 어느 계정으로 로그인하는지 알리고 로그인 화면으로 보내는 버튼 하나. 서버는 부르지 않는다 */
interface Props {
    /** 방금 만든 루트 계정의 이메일(비밀번호는 이 화면에 오지 않는다) */
    email: string;
}

const props = withDefaults(defineProps<Props>(), {});

const emit = defineEmits<{ login: [] }>();

const { t } = useI18n();
</script>
