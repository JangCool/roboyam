<template>
    <!-- 개인 메모 — 평문(0~1000자, 이 가젯 설정 `config.text` 에 저장 — 서버 부담 없음). 쓰고 고치는 곳은 화면 편집의 ⚙ 설정. 여러 개 둘 수 있다 -->
    <DashboardGadgetFrame :title="title" :refreshable="false" ready :empty="!text" empty-icon="pi pi-pencil" :empty-title="t('dashboard.memoEmpty')" :empty-body="t('dashboard.memoEmptyBody')">
        <p class="m-0 text-sm leading-relaxed break-words whitespace-pre-wrap">{{ text }}</p>
    </DashboardGadgetFrame>
</template>

<script setup lang="ts">
import type { DashboardGadgetItem } from '~/composables/dashboard/api/useDashboardLayoutApi.types';

interface Props {
    gadget: DashboardGadgetItem;
    title: string;
}

const props = withDefaults(defineProps<Props>(), {});

const { t } = useI18n();
const text = computed(() => props.gadget.config.text?.trim() ?? '');
</script>
