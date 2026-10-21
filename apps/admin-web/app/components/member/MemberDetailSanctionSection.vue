<template>
    <!--
        회원 상세 — 제재 이력 구획(읽기 전용). 위에 제재 요약(경고 횟수 · 진행 중인 제재 · 다음 단계)과 신고 요약(받은 · 조치한 · 제기한 신고 수), 아래에 이력 표.
        사이트 제재(경고·기간 정지·영구 정지)와 게시판 한정 제재를 구분해 최근 것부터 보인다. 서버에 제재·신고 표가 아직 없어 요약·이력이 모두 비면 구획 전체를 「아직 연동되지 않았어요」 안내로 보인다(요약 둘 중 하나만 오면 그 블록만 그린다). 종류·범위·상태 모두 글자와 아이콘을 함께 쓴다.
    -->
    <div v-if="hasData" class="flex flex-col gap-5">
        <section v-if="detail.sanctionSummary" :aria-labelledby="summaryId">
            <h3 :id="summaryId" class="mb-2 text-[13px] font-semibold">{{ t('member.detail.sanction.summaryTitle') }}</h3>
            <dl class="m-0 flex flex-col">
                <MemberDetailItem :label="t('member.detail.sanction.warningCount')">
                    <span class="tabular-nums">{{ t('member.detail.sanction.warningValue', { count: detail.sanctionSummary.warningCount }) }}</span>
                </MemberDetailItem>
                <MemberDetailItem :label="t('member.detail.sanction.activeNow')" :empty="!active.length">
                    <div class="flex flex-col gap-1">
                        <div v-for="s in active" :key="s.sanctionId" class="flex flex-wrap items-center gap-1.5">
                            <MemberDetailTag :value="t(`member.detail.sanction.kind.${s.kind}`)" :icon="UtilMember.sanctionKindIcon(s.kind)" :severity="UtilMember.sanctionKindSeverity(s.kind)" />
                            <span v-if="s.endsAt" class="text-xs text-muted-color tabular-nums">{{ t('member.detail.sanction.endsAt', { at: time.full(s.endsAt) }) }}</span>
                        </div>
                    </div>
                </MemberDetailItem>
                <MemberDetailItem :label="t('member.detail.sanction.nextStep')" :empty="detail.sanctionSummary.next === null">
                    <MemberDetailTag v-if="detail.sanctionSummary.next" :value="t(`member.detail.sanction.kind.${detail.sanctionSummary.next}`)" :icon="UtilMember.sanctionKindIcon(detail.sanctionSummary.next)" :severity="UtilMember.sanctionKindSeverity(detail.sanctionSummary.next)" />
                </MemberDetailItem>
            </dl>
        </section>

        <section v-if="detail.reports" :aria-labelledby="reportId">
            <h3 :id="reportId" class="mb-2 text-[13px] font-semibold">{{ t('member.detail.reports.title') }}</h3>
            <dl class="m-0 grid grid-cols-3 gap-2">
                <div v-for="item in reportItems" :key="item.key" class="rounded-lg border border-(--line) px-3 py-2">
                    <dt class="text-xs text-muted-color">{{ item.label }}</dt>
                    <dd class="m-0 mt-0.5 text-base font-semibold tabular-nums">{{ item.value.toLocaleString(locale) }}</dd>
                </div>
            </dl>
        </section>

        <section :aria-labelledby="historyId">
            <h3 :id="historyId" class="mb-2 text-[13px] font-semibold">{{ t('member.detail.sanction.historyTitle') }}</h3>
            <EmptyState v-if="!detail.sanctions.length" icon="pi pi-shield" :title="t('member.detail.noSanctions')" :description="t('member.detail.noSanctionsHint')" />
            <ul v-else class="m-0 flex list-none flex-col gap-2 p-0">
                <li v-for="s in detail.sanctions" :key="s.sanctionId" class="flex flex-col gap-1.5 rounded-lg border border-(--line) px-3 py-2.5">
                    <div class="flex flex-wrap items-center gap-1.5">
                        <Tag :value="t(`member.detail.sanction.kind.${s.kind}`)" :icon="UtilMember.sanctionKindIcon(s.kind)" :severity="UtilMember.sanctionKindSeverity(s.kind)" />
                        <Tag :value="s.scope === 'BOARD' ? t('member.detail.sanction.board', { name: s.boardName ?? '' }) : t('member.detail.sanction.scope.SITE')" :icon="s.scope === 'BOARD' ? 'pi pi-comments' : 'pi pi-globe'" severity="secondary" />
                        <Tag class="ml-auto" :value="t(`member.detail.sanction.status.${s.status}`)" :icon="UtilMember.sanctionStatusIcon(s.status)" :severity="UtilMember.sanctionStatusSeverity(s.status)" />
                    </div>
                    <p class="text-[13px]">{{ s.reason }}</p>
                    <p class="text-xs text-muted-color tabular-nums">
                        {{ t('member.detail.sanction.occurredAt', { at: time.full(s.occurredAt) }) }}
                        <template v-if="s.endsAt"> · {{ t('member.detail.sanction.endsAt', { at: time.full(s.endsAt) }) }}</template>
                    </p>
                </li>
            </ul>
        </section>
    </div>
    <EmptyState v-else icon="pi pi-shield" :title="t('member.detail.notReady.title')" :description="t('member.detail.notReady.sanctions')" />
</template>

<script setup lang="ts">
import type { MemberDetail } from '~/composables/member/api/useMemberApi.types';
import UtilMember from '~/utils/util-member';

interface Props {
    detail: MemberDetail;
}

const props = defineProps<Props>();

const { t, locale } = useI18n();
const time = useDisplayTime();
const summaryId = useId();
const reportId = useId();
const historyId = useId();

const active = computed(() => UtilMember.activeSanctions(props.detail.sanctions));
/** 제재 요약·신고 요약·이력 중 하나라도 있으면 그린다. 모두 없으면(서버에 제재·신고 표가 아직 없다) 구획 전체를 안내로 대신한다 */
const hasData = computed(() => props.detail.sanctionSummary !== null || props.detail.reports !== null || props.detail.sanctions.length > 0);
const reportItems = computed(() => {
    const r = props.detail.reports;
    if (!r) return [];
    return [
        { key: 'received', label: t('member.detail.reports.received'), value: r.receivedCount },
        { key: 'actioned', label: t('member.detail.reports.actioned'), value: r.actionedCount },
        { key: 'filed', label: t('member.detail.reports.filed'), value: r.filedCount },
    ];
});
</script>
