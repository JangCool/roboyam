<template>
    <!-- 관리자 역할 뱃지 — 최고 관리자(ADMIN)·운영진(STAFF). 상단 바·프로필·드로어 어디서나 라이트·다크 모두 읽히도록 옅은 바탕 + 진한 글자(다크는 반대)를 쓴다 -->
    <span class="inline-block whitespace-nowrap rounded px-1.5 py-px text-xs font-semibold" :class="CLASS[role]">{{ label }}</span>
</template>

<script setup lang="ts">
import type { AdminRole } from '~/composables/auth/api/useAdminAuthApi.types';

interface Props {
    role: AdminRole;
}

const props = withDefaults(defineProps<Props>(), {});

const CLASS: Record<AdminRole, string> = {
    ADMIN: 'bg-amber-100 text-amber-900 dark:bg-amber-950 dark:text-amber-200',
    STAFF: 'bg-sky-100 text-sky-900 dark:bg-sky-950 dark:text-sky-200',
};

const { t } = useI18n();
const label = computed(() => (props.role === 'ADMIN' ? t('shell.roleAdmin') : t('shell.roleStaff')));
</script>
