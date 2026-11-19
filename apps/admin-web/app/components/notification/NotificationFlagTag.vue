<template>
    <!-- 이름 옆의 표시 딱지 — 필수(잠금 아이콘 · 끌 수 없다) · 준비 중(발행처가 아직 없는 예약 이벤트) · 기본 제공(알림 도구 — 이름·종류를 바꾸거나 지울 수 없다). 이벤트 관리·알림 도구·내 정보의 이벤트별 알림이 같은 모양을 쓴다 -->
    <Tag v-tooltip.top="hint" class="notification-flag-tag" :value="label" :icon="icon" :severity="kind === 'notReady' ? 'contrast' : 'secondary'" />
</template>

<script setup lang="ts">
interface Props {
    kind: 'mandatory' | 'notReady' | 'builtIn';
}

const props = withDefaults(defineProps<Props>(), {});

const { t } = useI18n();

const label = computed(() => t(`notification.${props.kind}`));
const hint = computed(() => t(`notification.${props.kind}Hint`));
const icon = computed(() => (props.kind === 'mandatory' ? 'pi pi-lock' : props.kind === 'notReady' ? 'pi pi-clock' : 'pi pi-box'));
</script>

<style scoped>
/* 뱃지 글자 세로 가운데 — 글자 상자를 대문자 높이~기준선으로 깎고 대칭 여백을 준다(`--chip-label-pad`, main.css · components.md 2-4) */
.notification-flag-tag :deep(.p-tag-label) {
    text-box: trim-both cap alphabetic;
    padding-block: var(--chip-label-pad);
    white-space: nowrap;
}
</style>
