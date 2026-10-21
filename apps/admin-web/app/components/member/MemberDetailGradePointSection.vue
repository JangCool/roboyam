<template>
    <!--
        회원 상세 — 등급·포인트 구획(읽기 전용). 현재 등급·자동 승급 상태(정상 / 운영자 변경으로 중지 / 제재로 중지)·다음 등급까지의 진행도·등급 변경 이력, 포인트 잔액·닉네임 변경권·최근 포인트 내역.
        마일리지(그누보드7)는 roboyam 의 포인트로 대응한다. 등급 이름·색은 등급 설정 목록(`useGradeAppearance`)에서 읽는다. 변경·조정 같은 조치는 이번 범위 밖이라 단추가 없다.
    -->
    <div v-if="detail.gradePoint" class="flex flex-col gap-5">
        <section :aria-labelledby="gradeId">
            <h3 :id="gradeId" class="mb-2 text-[13px] font-semibold">{{ t('member.detail.grade.title') }}</h3>
            <dl class="m-0 flex flex-col">
                <MemberDetailItem :label="t('member.detail.grade.current')">
                    <GradeBadge v-bind="gradeState.badgeOf(detail.grade)" />
                </MemberDetailItem>
                <MemberDetailItem :label="t('member.detail.grade.autoPromotion')">
                    <div class="flex flex-col gap-1">
                        <span>
                            <MemberDetailTag :value="t(`member.detail.grade.auto.${detail.gradePoint.autoPromotion}`)" :icon="UtilMember.autoPromotionIcon(detail.gradePoint.autoPromotion)" :severity="UtilMember.autoPromotionSeverity(detail.gradePoint.autoPromotion)" />
                        </span>
                        <span v-if="detail.gradePoint.autoPromotion !== 'ACTIVE'" class="text-xs text-muted-color">{{ t(`member.detail.grade.autoHelp.${detail.gradePoint.autoPromotion}`) }}</span>
                    </div>
                </MemberDetailItem>
            </dl>

            <h4 class="mb-1.5 mt-3 text-xs text-muted-color">{{ t('member.detail.grade.nextTitle') }}</h4>
            <template v-if="detail.gradePoint.next">
                <p class="mb-2 flex items-center gap-1.5 text-[13px]">
                    <GradeBadge v-bind="gradeState.badgeOf(detail.gradePoint.next.gradeKey)" size="small" />
                    <span class="text-muted-color">{{ t('member.detail.grade.nextRequirements') }}</span>
                </p>
                <ul class="m-0 flex list-none flex-col gap-2.5 p-0">
                    <li v-for="r in detail.gradePoint.next.requirements" :key="r.kind">
                        <div class="flex items-baseline justify-between gap-2 text-[13px]">
                            <span class="min-w-0 truncate">
                                <i :class="UtilMember.requirementMet(r) ? 'pi pi-check-circle text-green-600 dark:text-green-400' : 'pi pi-circle text-muted-color'" class="mr-1 text-xs" aria-hidden="true" />
                                {{ t(`member.detail.grade.req.${r.kind}`) }}
                            </span>
                            <span class="shrink-0 text-xs text-muted-color tabular-nums">{{ t(`member.detail.grade.reqValue.${r.kind}`, { current: r.current.toLocaleString(locale), required: r.required.toLocaleString(locale) }) }}</span>
                        </div>
                        <ProgressBar class="mt-1 h-1.5" :value="UtilMember.requirementPercent(r)" :show-value="false" :aria-label="t(`member.detail.grade.req.${r.kind}`)" />
                    </li>
                </ul>
            </template>
            <p v-else class="text-[13px] text-muted-color">{{ t('member.detail.grade.nextNone') }}</p>

            <h4 class="mb-1.5 mt-4 text-xs text-muted-color">{{ t('member.detail.grade.historyTitle') }}</h4>
            <p v-if="!detail.gradePoint.gradeHistory.length" class="text-[13px] text-muted-color">{{ t('member.detail.historyEmpty') }}</p>
            <ul v-else class="m-0 flex list-none flex-col gap-1.5 p-0">
                <li v-for="h in detail.gradePoint.gradeHistory" :key="h.changeId" class="flex flex-col gap-1 rounded-lg border border-(--line) px-3 py-2">
                    <div class="flex flex-wrap items-center gap-1.5">
                        <MemberDetailTag :value="t(`member.detail.grade.changeKind.${h.kind}`)" :icon="UtilMember.gradeChangeKindIcon(h.kind)" :severity="UtilMember.gradeChangeKindSeverity(h.kind)" />
                        <template v-if="h.fromGrade">
                            <GradeBadge v-bind="gradeState.badgeOf(h.fromGrade)" size="small" />
                            <span aria-hidden="true">→</span>
                            <span class="sr-only">{{ t('member.detail.grade.changedTo') }}</span>
                        </template>
                        <GradeBadge v-bind="gradeState.badgeOf(h.toGrade)" size="small" />
                    </div>
                    <p v-if="h.reason" class="text-[13px]">{{ h.reason }}</p>
                    <p class="text-xs text-muted-color tabular-nums">
                        {{ time.full(h.changedAt) }} · {{ h.actorNickname ?? t('member.detail.grade.actorSystem') }}
                    </p>
                </li>
            </ul>
        </section>

        <section :aria-labelledby="pointId">
            <h3 :id="pointId" class="mb-2 text-[13px] font-semibold">{{ t('member.detail.point.title') }}</h3>
            <dl class="m-0 grid grid-cols-2 gap-2">
                <div class="rounded-lg border border-(--line) px-3 py-2">
                    <dt class="text-xs text-muted-color">{{ t('member.detail.point.balance') }}</dt>
                    <dd class="m-0 mt-0.5 text-base font-semibold tabular-nums">{{ detail.point.toLocaleString(locale) }}</dd>
                </div>
                <div class="rounded-lg border border-(--line) px-3 py-2">
                    <dt class="text-xs text-muted-color">{{ t('member.detail.point.tickets') }}</dt>
                    <dd class="m-0 mt-0.5 text-base font-semibold tabular-nums">{{ t('member.detail.point.ticketCount', { count: detail.gradePoint.nicknameTicketCount }) }}</dd>
                </div>
            </dl>

            <h4 class="mb-1.5 mt-3 text-xs text-muted-color">{{ t('member.detail.point.ledgerTitle') }}</h4>
            <p v-if="!detail.gradePoint.pointEntries.length" class="text-[13px] text-muted-color">{{ t('member.detail.historyEmpty') }}</p>
            <ul v-else class="m-0 flex list-none flex-col gap-1.5 p-0">
                <li v-for="e in detail.gradePoint.pointEntries" :key="e.entryId" class="flex items-baseline justify-between gap-3 rounded-lg border border-(--line) px-3 py-2">
                    <div class="min-w-0">
                        <p class="truncate text-[13px]">{{ e.reason }}</p>
                        <p class="text-xs text-muted-color tabular-nums">{{ time.full(e.createdAt) }}</p>
                    </div>
                    <b class="shrink-0 text-[13px] tabular-nums" :class="e.delta < 0 ? 'text-red-600 dark:text-red-400' : 'text-green-700 dark:text-green-400'">{{ UtilMember.signedNumber(e.delta, locale) }}</b>
                </li>
            </ul>
        </section>
    </div>
    <EmptyState v-else icon="pi pi-star" :title="t('member.detail.notReady.title')" :description="t('member.detail.notReady.gradePoint')" />
</template>

<script setup lang="ts">
import type { MemberDetail } from '~/composables/member/api/useMemberApi.types';
import UtilMember from '~/utils/util-member';

interface Props {
    detail: MemberDetail;
}

defineProps<Props>();

const { t, locale } = useI18n();
const time = useDisplayTime();
const gradeState = useGradeAppearance();
const gradeId = useId();
const pointId = useId();
</script>
