<template>
    <!--
        신고 대상(글·댓글)의 지금 상태 알약 — 공개(초록)·숨김(주황)·삭제(빨강). `lines` 면 아래에 숨김 까닭(자동 숨김·불법 즉시·권리침해 임시조치·운영자 숨김)과 임시조치 기한을 작은 줄로 붙인다.
        신고 관리의 목록 줄과 사건 서랍이 같이 쓴다.
    -->
    <div class="flex min-w-0 flex-col items-start gap-0.5">
        <Tag :value="t(`report.targetState.${target.state}`)" :icon="UtilReport.targetStateIcon(target.state)" :severity="UtilReport.targetStateSeverity(target.state)" class="text-[11px]!" />
        <template v-if="lines">
            <span v-for="(line, i) in stateLines" :key="i" class="text-[11px] leading-snug text-muted-color">{{ line }}</span>
        </template>
    </div>
</template>

<script setup lang="ts">
import type { ReportTarget } from '~/composables/report/api/useReportApi.types';
import UtilReport from '~/utils/util-report';
import UtilReportMessages from '~/utils/util-report-messages';

interface Props {
    target: ReportTarget;
    /** 알약 아래 작은 줄들을 붙인다 */
    lines?: boolean;
}

const props = withDefaults(defineProps<Props>(), { lines: false });

const { t } = useI18n();
const time = useDisplayTime();

const stateLines = computed(() => UtilReportMessages.targetLines(t, time.value, props.target));
</script>
