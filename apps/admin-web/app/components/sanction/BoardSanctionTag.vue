<template>
    <!--
        게시판 제재 알약 — 단계(경고·쓰기 제한 N일) · 상태(유효·진행 중·끝남·해제됨·무효 — 사이트 제재와 같은 이름) · 반복 검토 상태(열림·닫힘) · 닫은 결과(그대로 둠·사이트 제재 부여·게시판 제재 무효)를
        글자 + 아이콘으로(색만으로 가르지 않는다). 게시판 제재 화면·반복 검토·회원 상세·이의제기가 같은 모양으로 쓴다. 한글 글자 중앙 보정은 `SanctionTag` 와 같다.
    -->
    <Tag class="board-sanction-tag text-[11px]!" :value="label" :icon="icon" :severity="severity" />
</template>

<script setup lang="ts">
import UtilBoardSanction from '~/utils/util-board-sanction';
import UtilBoardSanctionMessages from '~/utils/util-board-sanction-messages';
import UtilSanction from '~/utils/util-sanction';

interface Props {
    /** 무엇을 그릴지 — 단계 · 제재 상태 · 반복 검토 상태 · 닫은 결과 */
    type: 'kind' | 'status' | 'review' | 'result';
    /** 단계(`type=kind`) 또는 상태를 그릴 제재의 단계(`type=status` — 경고의 ACTIVE 는 「유효」) */
    kind?: string;
    /** 제재 상태 · 검토 상태 · 닫은 결과 */
    status?: string;
}

const props = withDefaults(defineProps<Props>(), { kind: 'WARNING', status: '' });

const { t } = useI18n();

const label = computed(() => {
    if (props.type === 'kind') return UtilBoardSanctionMessages.kindName(t, props.kind);
    if (props.type === 'status') return UtilBoardSanctionMessages.statusName(t, props.status, props.kind);
    if (props.type === 'review') return UtilBoardSanctionMessages.reviewStatusName(t, props.status);
    return UtilBoardSanctionMessages.reviewResultName(t, props.status);
});
const icon = computed(() => {
    if (props.type === 'kind') return UtilBoardSanction.kindIcon(props.kind);
    if (props.type === 'status') return UtilSanction.statusIcon(props.status);
    if (props.type === 'review') return UtilBoardSanction.reviewStatusIcon(props.status);
    return UtilBoardSanction.reviewResultIcon(props.status);
});
const severity = computed(() => {
    if (props.type === 'kind') return UtilBoardSanction.kindSeverity(props.kind);
    if (props.type === 'status') return UtilSanction.statusSeverity(props.status, props.kind === 'WARNING' ? 'WARNING' : 'SUSPEND_3');
    if (props.type === 'review') return UtilBoardSanction.reviewStatusSeverity(props.status);
    return UtilBoardSanction.reviewResultSeverity(props.status);
});
</script>

<style scoped>
/* 한글 글꼴은 글자가 줄 상자 중앙보다 약 0.09em 아래에 앉는다 — 글자만 올려 가운데에 맞춘다(`SanctionTag` 와 같은 보정) */
.board-sanction-tag :deep(.p-tag-label) {
    position: relative;
    top: -0.09em;
}
.board-sanction-tag :deep(.p-tag-icon.pi-circle-fill) {
    font-size: 0.5rem;
}
</style>
