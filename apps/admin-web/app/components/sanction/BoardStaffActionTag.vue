<template>
    <!--
        스텝 처리 알약 — 종류(글 숨기기·글 삭제·댓글 삭제) · 대상의 지금 상태(공개·숨김·삭제됨) · 되돌림(되돌림·그대로)을 글자 + 아이콘으로(색만으로 가르지 않는다).
        스텝 처리 감독 표·서랍이 같은 모양으로 쓴다. 한글 글자 중앙 보정은 `BoardSanctionTag` 와 같다.
    -->
    <Tag class="board-staff-action-tag text-[11px]!" :value="label" :icon="icon" :severity="severity" />
</template>

<script setup lang="ts">
import UtilBoardStaffAction from '~/utils/util-board-staff-action';
import UtilBoardStaffActionMessages from '~/utils/util-board-staff-action-messages';

interface Props {
    /** 무엇을 그릴지 — 종류 · 대상 상태 · 되돌림 여부 */
    type: 'kind' | 'state' | 'restored';
    /** 종류(`type=kind`) 또는 대상 상태(`type=state`) */
    value?: string;
    /** 되돌렸는지(`type=restored`) */
    restored?: boolean;
}

const props = withDefaults(defineProps<Props>(), { value: '', restored: false });

const { t } = useI18n();

const label = computed(() => {
    if (props.type === 'kind') return UtilBoardStaffActionMessages.kindName(t, props.value);
    if (props.type === 'state') return UtilBoardStaffActionMessages.stateName(t, props.value);
    return t(props.restored ? 'staffAction.restoredTag' : 'staffAction.openTag');
});
const icon = computed(() => {
    if (props.type === 'kind') return UtilBoardStaffAction.kindIcon(props.value);
    if (props.type === 'state') return UtilBoardStaffAction.stateIcon(props.value);
    return props.restored ? 'pi pi-undo' : 'pi pi-minus';
});
const severity = computed(() => {
    if (props.type === 'kind') return UtilBoardStaffAction.kindSeverity(props.value);
    if (props.type === 'state') return UtilBoardStaffAction.stateSeverity(props.value);
    return props.restored ? 'info' : 'secondary';
});
</script>

<style scoped>
/* 뱃지 글자 세로 가운데 — 글자 상자를 대문자 높이~기준선으로 깎고 대칭 여백을 준다(`--chip-label-pad`, main.css · components.md 2-4) */
.board-staff-action-tag :deep(.p-tag-label) {
    text-box: trim-both cap alphabetic;
    padding-block: var(--chip-label-pad);
}
</style>
