<template>
    <!-- 최고 관리자·운영진 공식 뱃지 — 이미지로 만들 수 없는 시스템 뱃지 (features 8). 글자는 운영자가 정한 공식 뱃지 이름이다(ADM-001, 기본 "운영자"·"운영진"). 사업자 전용 계정(H-2)은 공식 뱃지처럼 어둡지 않은 "사업자" 뱃지로 구별한다 -->
    <span class="inline-block whitespace-nowrap rounded px-1.5 py-px text-xs font-semibold" :class="business ? BUSINESS_CLASS : OFFICIAL_CLASS">{{ label }}</span>
</template>

<script setup lang="ts">
import type { Role } from '~/composables/auth/api/useAuthApi.types';

interface Props {
    role: Role;
}

const props = withDefaults(defineProps<Props>(), {});

const OFFICIAL_CLASS = 'bg-surface-900 text-surface-0 dark:bg-surface-100 dark:text-surface-900';
const BUSINESS_CLASS = 'bg-teal-100 text-teal-800 dark:bg-teal-950 dark:text-teal-300';

const business = computed(() => props.role === 'BUSINESS');
const { t } = useI18n();
const { term } = useSiteTerms();
const label = computed(() => (props.role === 'ADMIN' ? term('ADMIN_BADGE') : props.role === 'BUSINESS' ? t('business.badge') : term('STAFF_BADGE')));
</script>
