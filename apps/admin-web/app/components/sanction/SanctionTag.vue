<template>
    <!--
        제재 알약 — 단계(경고·정지 N일·영구 정지) · 상태(유효·진행 중·끝남·해제·무효) · 이의제기 상태(대기·인용·기각)를 글자 + 아이콘으로(색만으로 가르지 않는다).
        제재 화면·이의제기 화면·회원 상세·신고 서랍이 같은 모양으로 쓴다. 한글 글자 중앙 보정은 `MemberDetailTag` 와 같다(`components.md` 2-4).
    -->
    <Tag class="sanction-tag text-[11px]!" :value="label" :icon="icon" :severity="severity" />
</template>

<script setup lang="ts">
import type { SanctionKind } from '~/composables/sanction/api/useSanctionApi.types';
import UtilSanction from '~/utils/util-sanction';
import UtilSanctionMessages from '~/utils/util-sanction-messages';

interface Props {
    /** 무엇을 그릴지 — 단계 · 제재 상태 · 이의제기 상태 */
    type: 'kind' | 'status' | 'appeal';
    /** 단계(`type=kind`) 또는 상태를 그릴 제재의 단계(`type=status` — 경고의 ACTIVE 는 「유효」) */
    kind?: SanctionKind | string;
    /** 경고 번호(1·2) — 「경고 2회째」 */
    warningNo?: number | null;
    /** 제재 상태(`type=status`) 또는 이의제기 상태(`type=appeal`) */
    status?: string;
}

const props = withDefaults(defineProps<Props>(), { kind: 'WARNING', warningNo: null, status: '' });

const { t } = useI18n();

const label = computed(() => {
    if (props.type === 'kind') return UtilSanctionMessages.kindName(t, props.kind, props.warningNo);
    if (props.type === 'status') return UtilSanctionMessages.statusName(t, props.status, props.kind);
    return UtilSanctionMessages.appealStatusName(t, props.status);
});
const icon = computed(() => {
    if (props.type === 'kind') return UtilSanction.kindIcon(props.kind);
    if (props.type === 'status') return UtilSanction.statusIcon(props.status);
    return UtilSanction.appealIcon(props.status);
});
const severity = computed(() => {
    if (props.type === 'kind') return UtilSanction.kindSeverity(props.kind);
    if (props.type === 'status') return UtilSanction.statusSeverity(props.status, props.kind);
    return UtilSanction.appealSeverity(props.status);
});
</script>

<style scoped>
/* 한글 글꼴은 글자가 줄 상자 중앙보다 약 0.09em 아래에 앉는다 — 글자만 올려 가운데에 맞춘다(`GradeBadge` 와 같은 보정) */
.sanction-tag :deep(.p-tag-label) {
    position: relative;
    top: -0.09em;
}
/* 상태의 「진행 중」 점 아이콘은 작게 */
.sanction-tag :deep(.p-tag-icon.pi-circle-fill) {
    font-size: 0.5rem;
}
</style>
