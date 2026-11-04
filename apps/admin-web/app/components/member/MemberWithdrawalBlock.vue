<template>
    <!--
        회원 상세 「기본 정보」 구획 아래의 탈퇴 블록 — 탈퇴 진행 중(`WITHDRAWING`)이면 「탈퇴 신청」(신청 시각 · 남은 일 칩 · 예상 완료 · 고른 것), 탈퇴 완료(`WITHDRAWN`)면 「탈퇴 완료」(완료 시각 · 신청 경로(회원/운영자 강제) · 사유).
        둘 다 「탈퇴 관리에서 보기」(`/members/withdrawals?id=`). 값은 부모(회원 관리 화면)가 `useMemberWithdrawal` 로 받아 내려 준다 — 서버 호출은 하지 않는다. 신청 기록이 없으면(옛 탈퇴 등) 「기록 없음」 한 줄.
        진행 중 + 회원 신청 건이면 주의 동작 [대리 취소](`canCancel` = `member.withdraw`, 회원이 고객센터·전화로 요청한 경우만 — 사용자 결정 2026-11-04). 운영자 강제 건은 「대리 취소 불가」 한 줄. 대리 취소가 끝나 기록이 취소로 바뀌면(회원은 신청 전 상태) 안내 한 줄만.
    -->
    <section :aria-labelledby="titleId" class="flex flex-col gap-3 rounded-lg border border-(--line) px-3 py-3">
        <h3 :id="titleId" class="m-0 flex flex-wrap items-center gap-1.5 text-[13px] font-semibold">
            <i :class="[scheduled ? 'pi pi-hourglass' : 'pi pi-sign-out', 'text-muted-color']" aria-hidden="true" />
            {{ scheduled ? t('memberWithdrawal.block.titleScheduled') : t('memberWithdrawal.block.titleCompleted') }}
            <MemberDetailTag v-if="view?.record" :value="t(`memberWithdrawal.source.${view.record.source}`)" :icon="view.record.source === 'OPERATOR' ? 'pi pi-user-minus' : 'pi pi-user'" :severity="view.record.source === 'OPERATOR' ? 'danger' : 'secondary'" />
        </h3>

        <ErrorState v-if="view?.error" :title="t('memberWithdrawal.block.loadFailed')" :description="view.error.message" retry :retrying="view.loading" @retry="emit('retry')" />

        <div v-else-if="!view || view.loading" class="flex flex-col gap-2" role="status" :aria-label="t('common.loading')">
            <Skeleton v-for="n in 3" :key="n" height="1rem" />
        </div>

        <p v-else-if="!view.record" class="m-0 text-[13px] text-muted-color">{{ t('memberWithdrawal.block.none') }}</p>

        <!-- 대리 취소가 끝나 기록이 취소로 바뀐 직후(목록은 아직 「탈퇴 진행 중」) -->
        <p v-else-if="scheduled && view.record.status === 'CANCELED'" class="m-0 inline-flex items-start gap-2 text-[13px] text-muted-color">
            <i class="pi pi-undo mt-0.5" aria-hidden="true" />
            <span>{{ t('memberWithdrawal.block.canceledNotice') }}</span>
        </p>

        <template v-else>
            <dl class="m-0 flex flex-col">
                <MemberDetailItem :label="t('memberWithdrawal.block.requestedAt')">
                    <span class="tabular-nums">{{ time.full(view.record.requestedAt) }}</span>
                </MemberDetailItem>
                <template v-if="scheduled">
                    <MemberDetailItem :label="t('memberWithdrawal.block.pending')">
                        <span v-if="!chips.length" class="text-muted-color">{{ t('memberWithdrawal.pendingAllDone') }}</span>
                        <span v-else class="flex flex-wrap gap-1">
                            <MemberDetailTag v-for="chip in chips" :key="chip.code" :value="`${t(`memberWithdrawal.pending.${chip.code}`)} ${chip.count}`" :icon="UtilMemberWithdrawal.pendingIcon(chip.code)" severity="warn" />
                        </span>
                    </MemberDetailItem>
                    <MemberDetailItem :label="t('memberWithdrawal.block.expectedCompleteAt')">
                        <span v-if="view.record.expectedCompleteAt" class="tabular-nums">{{ time.full(view.record.expectedCompleteAt) }}</span>
                        <span v-else class="text-muted-color">{{ t('memberWithdrawal.block.expectedUnknown') }}</span>
                    </MemberDetailItem>
                    <MemberDetailItem :label="t('memberWithdrawal.block.choices')">
                        <MemberWithdrawalChoices :choices="view.record.choices" />
                    </MemberDetailItem>
                </template>
                <template v-else>
                    <MemberDetailItem :label="t('memberWithdrawal.block.completedAt')" :empty="!view.record.completedAt">
                        <span class="tabular-nums">{{ time.full(view.record.completedAt) }}</span>
                    </MemberDetailItem>
                    <MemberDetailItem :label="t('memberWithdrawal.block.reason')">
                        <div v-if="view.record.force" class="flex flex-col gap-0.5">
                            <span class="inline-flex items-center gap-1.5"><i :class="UtilMemberWithdrawal.forceReasonIcon(view.record.force.reasonKind)" aria-hidden="true" />{{ t(`memberWithdrawal.force.reasonKind.${view.record.force.reasonKind}`) }}</span>
                            <span class="text-xs break-words text-muted-color">{{ view.record.force.memo }}</span>
                        </div>
                        <div v-else class="flex flex-col gap-0.5">
                            <span :class="view.record.reason ? '' : 'text-muted-color'">{{ t(`memberWithdrawal.reason.${UtilMemberWithdrawal.reasonKey(view.record.reason)}`) }}</span>
                            <span v-if="view.record.reasonNote" class="text-xs break-words text-muted-color">{{ view.record.reasonNote }}</span>
                        </div>
                    </MemberDetailItem>
                </template>
            </dl>
            <div class="flex flex-wrap items-center justify-between gap-2">
                <NuxtLink :to="{ path: '/members/withdrawals', query: { status: view.record.status, id: view.record.id } }" class="inline-flex items-center gap-1 text-xs font-medium text-(--p-primary-color) hover:underline">
                    <i class="pi pi-arrow-right text-[10px]" aria-hidden="true" />
                    {{ t('memberWithdrawal.block.openWithdrawals') }}
                </NuxtLink>
                <!-- 주의 동작 — 진행 중 + 회원 신청 건만 대리 취소(권한 없으면 숨김), 운영자 강제 건은 불가 이유 -->
                <template v-if="scheduled">
                    <Button v-if="canOperatorCancel && canCancel" type="button" :label="t('memberWithdrawal.cancel.action')" icon="pi pi-undo" size="small" severity="warn" variant="outlined" data-testid="member-withdrawal-cancel" @click="emit('cancel')" />
                    <span v-else-if="!canOperatorCancel" class="inline-flex items-center gap-1 text-xs text-muted-color"><i class="pi pi-lock" aria-hidden="true" />{{ t('memberWithdrawal.cancelNotMemberRequested') }}</span>
                </template>
            </div>
        </template>
    </section>
</template>

<script setup lang="ts">
import type { MemberWithdrawalView } from '~/components/member/MemberDetailContent.types';
import UtilMemberWithdrawal from '~/utils/util-member-withdrawal';

interface Props {
    view: MemberWithdrawalView | null;
    /** 회원의 지금 상태 — 진행 중이면 신청 블록, 아니면 완료 블록 */
    scheduled: boolean;
    /** [대리 취소]를 보일 권한(`member.withdraw`) */
    canCancel?: boolean;
}

const props = withDefaults(defineProps<Props>(), { canCancel: false });
const emit = defineEmits<{ retry: []; cancel: [] }>();

const { t } = useI18n();
const time = useDisplayTime();
const titleId = useId();

const chips = computed(() => (props.view?.record ? UtilMemberWithdrawal.pendingChips(props.view.record.pendingCounts) : []));
/** 운영자가 대리 취소할 수 있는 건인가(진행 중 + 회원 신청) */
const canOperatorCancel = computed(() => (props.view?.record ? UtilMemberWithdrawal.canOperatorCancel(props.view.record.status, props.view.record.source) : false));
</script>
