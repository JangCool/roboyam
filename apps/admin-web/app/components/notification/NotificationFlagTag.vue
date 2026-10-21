<template>
    <!-- 이벤트 이름 옆의 표시 딱지 — 필수(잠금 아이콘 · 끌 수 없다) · 준비 중(발행처가 아직 없는 예약 이벤트). 이벤트 관리와 내 정보의 이벤트별 알림이 같은 모양을 쓴다 -->
    <Tag
        v-tooltip.top="hint"
        class="notification-flag-tag"
        :value="kind === 'mandatory' ? t('notification.mandatory') : t('notification.notReady')"
        :icon="kind === 'mandatory' ? 'pi pi-lock' : 'pi pi-clock'"
        :severity="kind === 'mandatory' ? 'secondary' : 'contrast'"
    />
</template>

<script setup lang="ts">
interface Props {
    kind: 'mandatory' | 'notReady';
}

const props = withDefaults(defineProps<Props>(), {});

const { t } = useI18n();
const hint = computed(() => (props.kind === 'mandatory' ? t('notification.mandatoryHint') : t('notification.notReadyHint')));
</script>

<style scoped>
/* 한글 글꼴은 글자가 줄 상자 중앙보다 약 0.09em 아래에 앉아 알약 안에서 아래로 붙어 보인다 — 글자만 올려 가운데에 맞춘다(`GradeBadge` 와 같은 보정, components.md 2-4) */
.notification-flag-tag :deep(.p-tag-label) {
    position: relative;
    top: -0.09em;
    white-space: nowrap;
}
</style>
