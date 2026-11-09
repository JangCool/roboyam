<template>
    <!-- 설치 완료 — 자동 로그인하지 않는다. 방금 만든 루트 계정으로 로그인 화면에서 들어간다 -->
    <StatusPanel icon="pi pi-check" tone="success" :title="t('install.doneTitle')" data-testid="install-done">
        <p>{{ t('install.doneText', { email }) }}</p>
        <p v-if="demo === 'RUNNING'" class="mt-2 flex items-start gap-2" role="status" data-testid="install-done-demo-running">
            <i class="pi pi-spin pi-spinner mt-1 text-sm" aria-hidden="true" />
            <span>{{ t('install.doneDemoRunning') }}</span>
        </p>
        <p v-else-if="demo === 'APPLIED'" class="mt-2" role="status" data-testid="install-done-demo">{{ t('install.doneDemoApplied') }}</p>
        <Message v-else-if="demo === 'FAILED'" severity="warn" :closable="false" size="small" class="mt-3" data-testid="install-done-demo-failed">{{ t('install.doneDemoFailed') }}</Message>
        <Message v-else-if="demo === 'UNKNOWN'" severity="warn" :closable="false" size="small" class="mt-3" data-testid="install-done-demo-unknown">{{ t('install.doneDemoUnknown') }}</Message>
        <template #actions>
            <Button type="button" :label="t('install.goLogin')" fluid size="large" data-testid="install-go-login" @click="emit('login')" />
        </template>
    </StatusPanel>
</template>

<script setup lang="ts">
import type { InstallDemoView } from '~/composables/install/api/useInstallApi.types';

/** 설치 완료 화면 — 어느 계정으로 로그인하는지 알리고 로그인 화면으로 보내는 버튼 하나. 데모 데이터를 골랐으면 넣는 중인지 · 넣었는지(건너뛰었는지) 알린다. 서버는 부르지 않는다(결과 기다리기는 페이지가 한다) */
interface Props {
    /** 방금 만든 루트 계정의 이메일(비밀번호는 이 화면에 오지 않는다) */
    email: string;
    /** 데모 데이터 상태(설치 응답의 `demo` → 페이지가 기다려 받은 결과) */
    demo?: InstallDemoView;
}

const props = withDefaults(defineProps<Props>(), { demo: 'NONE' });

const emit = defineEmits<{ login: [] }>();

const { t } = useI18n();
</script>
