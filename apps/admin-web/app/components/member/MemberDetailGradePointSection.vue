<template>
    <!--
        회원 상세 — 등급·포인트 구획(읽기 전용). 현재 등급·자동 승급 상태(정상 / 운영자 변경으로 중지 / 제재로 중지)·다음 등급까지의 진행도·등급 변경 이력, 포인트 잔액·닉네임 변경권·최근 포인트 내역.
        마일리지(그누보드7)는 roboyam 의 포인트로 대응한다. 등급 이름·색은 등급 설정 목록(`useGradeAppearance`)에서 읽는다. 변경·조정 같은 조치는 이번 범위 밖이라 단추가 없다.
        서버가 채우는 것: 자동 승급 상태·다음 등급 달성도·등급 변경 이력(최대 50건 — 최근 5건만 보이고 펼친다), 포인트 잔액(상세의 `point`)·최근 포인트 내역(원장 최근 10줄, 사유는 요청 언어).
        닉네임 변경권은 서버의 `nicknameTicketCount`(포인트 상점에서 사서 아직 안 쓰고 환불되지 않은 장수 — 커머스 1-2)를 「N장」으로 보인다. 회원 전체의 원장·수동 지급/차감은 「포인트 내역」 화면(`/points`)이 맡는다.
        커머스 1-1: 잔액을 「합계 · 적립 · 충전」으로 나누고 소멸 예정 · 미회수(있으면 경고 + 최근 미회수 기록 5건) · 포인트 묶음 목록(남은 것만 / 전체, 10개씩 쪽 넘김)을 더했다.
        묶음은 부모(회원 관리 화면)가 `useMemberPointLots` 로 받아 `pointLots` 로 내려 주고, 거르기·쪽·다시 시도는 이벤트로 올린다(`point.read` 가 없거나 받지 않는 화면은 null — 합계만).
        활동 수 조건은 서버의 자동 승급 엔진이 아직 활동 수를 0 으로 보므로 달성도가 차도 승급되지 않을 수 있어 짧은 보조 안내를 붙인다. 탈퇴 회원은 서버가 구획을 비우지만 구획은 남기고 탈퇴 전용 문구를 보인다.
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
                        <p v-if="UtilMember.activityPromotionNotApplied(r)" class="mt-1 text-xs text-muted-color">{{ t('member.detail.grade.activityNotApplied') }}</p>
                    </li>
                </ul>
            </template>
            <p v-else class="text-[13px] text-muted-color">{{ t('member.detail.grade.nextNone') }}</p>

            <h4 class="mb-1.5 mt-4 text-xs text-muted-color">{{ t('member.detail.grade.historyTitle') }}</h4>
            <p v-if="!detail.gradePoint.gradeHistory.length" class="text-[13px] text-muted-color">{{ t('member.detail.historyEmpty') }}</p>
            <div v-else class="flex flex-col">
                <ul class="m-0 flex list-none flex-col gap-1.5 p-0">
                    <li v-for="h in shownHistory" :key="h.changeId" class="flex flex-col gap-1 rounded-lg border border-(--line) px-3 py-2">
                        <div class="flex flex-wrap items-center gap-1.5">
                            <MemberDetailTag :value="kindLabel(h.kind)" :icon="UtilMember.gradeChangeKindIcon(h.kind)" :severity="UtilMember.gradeChangeKindSeverity(h.kind)" />
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
                <Button
                    v-if="hiddenHistoryCount > 0"
                    class="mt-1.5 self-start"
                    type="button"
                    size="small"
                    severity="secondary"
                    variant="text"
                    :icon="historyOpen ? 'pi pi-chevron-up' : 'pi pi-chevron-down'"
                    :label="historyOpen ? t('member.detail.grade.historyLess') : t('member.detail.grade.historyMore', { count: hiddenHistoryCount })"
                    :aria-expanded="historyOpen"
                    @click="historyOpen = !historyOpen"
                />
            </div>
        </section>

        <section :aria-labelledby="pointId">
            <h3 :id="pointId" class="mb-2 text-[13px] font-semibold">{{ t('member.detail.point.title') }}</h3>
            <!-- 잔액 — 관리자에게는 충전이 0 이어도 「합계 · 적립 · 충전」으로 나눠 보인다(안에서는 늘 나눠 관리 — commerce D13). 묶음을 받지 않는 화면(권한 없음 등)은 합계만 -->
            <dl class="m-0 grid grid-cols-2 gap-2" :class="{ '@xl:grid-cols-4': pointLots }">
                <div class="rounded-lg border border-(--line) px-3 py-2">
                    <dt class="text-xs text-muted-color">{{ pointLots ? t('member.detail.point.total') : t('member.detail.point.balance') }}</dt>
                    <dd class="m-0 mt-0.5 text-[13px] font-semibold tabular-nums">{{ points(detail.point) }}</dd>
                </div>
                <template v-if="pointLots">
                    <div class="rounded-lg border border-(--line) px-3 py-2">
                        <dt class="text-xs text-muted-color">{{ t('member.detail.point.free') }}</dt>
                        <dd class="m-0 mt-0.5 text-[13px] tabular-nums">
                            <Skeleton v-if="!lots" width="4rem" height="1.1rem" />
                            <template v-else>{{ points(lots.freeBalance) }}</template>
                        </dd>
                    </div>
                    <div class="rounded-lg border border-(--line) px-3 py-2">
                        <dt class="text-xs text-muted-color">{{ t('member.detail.point.paid') }}</dt>
                        <dd class="m-0 mt-0.5 text-[13px] tabular-nums" :class="{ 'text-muted-color': lots?.paidBalance === 0 }">
                            <Skeleton v-if="!lots" width="4rem" height="1.1rem" />
                            <template v-else>{{ points(lots.paidBalance) }}</template>
                        </dd>
                    </div>
                </template>
                <!-- 닉네임 변경권 — 포인트 상점에서 사서 아직 안 쓰고 환불되지 않은 장수(서버 `nicknameTicketCount`) -->
                <div class="rounded-lg border border-(--line) px-3 py-2">
                    <dt class="text-xs text-muted-color">{{ t('member.detail.point.tickets') }}</dt>
                    <dd class="m-0 mt-0.5 text-[13px] tabular-nums" :class="detail.gradePoint.nicknameTicketCount ? '' : 'text-muted-color'">{{ t('member.detail.point.ticketCount', { count: detail.gradePoint.nicknameTicketCount.toLocaleString(locale) }) }}</dd>
                </div>
            </dl>

            <!-- 소멸 예정 · 미회수 · 포인트 묶음(커머스 1-1) — 부모가 `useMemberPointLots` 로 받아 내려 준다 -->
            <template v-if="pointLots">
                <ErrorState v-if="pointLots.error" class="mt-3" :title="t('member.detail.point.lotsLoadFailed')" :description="pointLots.error.message" retry :retrying="pointLots.loading" @retry="emit('retry-lots')" />
                <div v-else-if="!lots" class="mt-3 flex flex-col gap-2" role="status" :aria-label="t('common.loading')">
                    <Skeleton v-for="n in 3" :key="n" height="2.5rem" />
                </div>
                <template v-else>
                    <dl class="m-0 mt-2 flex flex-col">
                        <MemberDetailItem :label="t('member.detail.point.expiring')">
                            <span v-if="lots.expiring" class="tabular-nums">{{ t('member.detail.point.expiringValue', { date: time.serviceDate(lots.expiring.date), amount: lots.expiring.amount.toLocaleString(locale) }) }}</span>
                            <span v-else class="text-muted-color">{{ t('member.detail.point.expiringNone') }}</span>
                        </MemberDetailItem>
                        <MemberDetailItem :label="t('member.detail.point.debt')">
                            <span v-if="lots.revokeDebt === 0" class="tabular-nums text-muted-color">{{ points(0) }}</span>
                            <span v-else class="inline-flex flex-wrap items-center gap-x-1.5 font-semibold text-amber-700 dark:text-amber-400">
                                <i class="pi pi-exclamation-triangle text-xs" aria-hidden="true" />
                                <span class="tabular-nums">{{ points(lots.revokeDebt) }}</span>
                                <span class="text-xs font-normal">{{ t('member.detail.point.debtNote') }}</span>
                            </span>
                        </MemberDetailItem>
                    </dl>

                    <!-- 최근 미회수 기록 — 덜 회수된 글·댓글(최대 5건) -->
                    <div v-if="lots.revokeDebt > 0 && lots.shortfalls.length" class="mt-2 rounded-lg border border-amber-300 px-3 py-2 dark:border-amber-800">
                        <h4 class="mb-1 text-xs text-muted-color">{{ t('member.detail.point.shortfallTitle') }}</h4>
                        <ul class="m-0 flex list-none flex-col gap-1 p-0">
                            <li v-for="s in lots.shortfalls" :key="s.id" class="flex flex-wrap items-baseline justify-between gap-x-3 text-[13px]">
                                <span class="min-w-0">
                                    {{ t(`member.detail.point.shortfallTarget.${s.refType}`, { id: s.refId }) }}
                                    <span class="ml-1 tabular-nums text-muted-color">{{ t('member.detail.point.shortfallLine', { requested: s.requested.toLocaleString(locale), applied: s.applied.toLocaleString(locale) }) }}</span>
                                </span>
                                <span class="shrink-0 text-xs tabular-nums text-muted-color">{{ time.full(s.createdAt) }}</span>
                            </li>
                        </ul>
                    </div>

                    <!-- 포인트 묶음 — 기본은 남은 묶음만(차감 차례), 「전체」는 최근 받은 순. 10개씩 쪽으로 나눈다 -->
                    <div class="mb-1.5 mt-3 flex flex-wrap items-center justify-between gap-2">
                        <h4 class="text-xs text-muted-color">
                            {{ t('member.detail.point.lotsTitle') }}
                            <span class="tabular-nums">{{ t('member.detail.point.lotsCount', { count: lots.lots.total.toLocaleString(locale) }) }}</span>
                        </h4>
                        <SelectButton
                            :model-value="pointLots.active"
                            :options="lotFilterOptions"
                            option-label="label"
                            option-value="value"
                            :allow-empty="false"
                            size="small"
                            :disabled="pointLots.loading"
                            :aria-label="t('member.detail.point.lotsFilter')"
                            @update:model-value="(v: boolean) => emit('lots-query', { active: v, page: 0 })"
                        />
                    </div>
                    <p v-if="!lots.lots.items.length" class="text-[13px] text-muted-color">{{ pointLots.active ? t('member.detail.point.lotsEmpty') : t('member.detail.point.lotsEmptyAll') }}</p>
                    <ul v-else class="m-0 flex list-none flex-col gap-1.5 p-0 transition-opacity" :class="{ 'opacity-60': pointLots.loading }" :aria-busy="pointLots.loading">
                        <li v-for="lot in lots.lots.items" :key="lot.lotId" class="flex flex-col gap-1 rounded-lg border border-(--line) px-3 py-2">
                            <div class="flex flex-wrap items-center gap-1.5">
                                <MemberDetailTag :value="t(`member.detail.point.lotKind.${lot.kind}`)" :severity="lot.kind === 'PAID' ? 'info' : 'success'" />
                                <span v-if="lot.restored" v-tooltip.top="t('member.detail.point.lotRestoredHint')" class="inline-flex">
                                    <MemberDetailTag :value="t('member.detail.point.lotRestored')" icon="pi pi-replay" severity="warn" />
                                </span>
                                <span class="min-w-0 truncate text-[13px]">{{ lot.sourceReason }}</span>
                                <b class="ml-auto shrink-0 text-[13px] tabular-nums" :class="{ 'font-normal text-muted-color': lot.remainingAmount === 0 }" :title="t('member.detail.point.lotAmountLabel')">
                                    {{ t('member.detail.point.lotAmount', { remaining: lot.remainingAmount.toLocaleString(locale), original: lot.originalAmount.toLocaleString(locale) }) }}
                                </b>
                            </div>
                            <p class="flex flex-wrap gap-x-2 text-xs tabular-nums text-muted-color">
                                <span :class="{ 'text-red-600 dark:text-red-400': lot.expired }">{{ lotExpiryText(lot) }}</span>
                                <span aria-hidden="true">·</span>
                                <span>{{ t('member.detail.point.lotReceived', { date: time.date(lot.createdAt) }) }}</span>
                            </p>
                        </li>
                    </ul>
                    <div v-if="lotPages > 1" class="mt-1.5 flex items-center justify-center gap-1">
                        <Button type="button" icon="pi pi-chevron-left" size="small" severity="secondary" variant="text" :disabled="pointLots.loading || pointLots.page === 0" :aria-label="t('member.detail.point.lotsPrev')" @click="emit('lots-query', { active: pointLots.active, page: pointLots.page - 1 })" />
                        <span class="text-xs tabular-nums text-muted-color">{{ t('member.detail.point.lotsPage', { page: pointLots.page + 1, pages: lotPages }) }}</span>
                        <Button type="button" icon="pi pi-chevron-right" size="small" severity="secondary" variant="text" :disabled="pointLots.loading || pointLots.page + 1 >= lotPages" :aria-label="t('member.detail.point.lotsNext')" @click="emit('lots-query', { active: pointLots.active, page: pointLots.page + 1 })" />
                    </div>
                </template>
            </template>

            <h4 class="mb-1.5 mt-3 text-xs text-muted-color">{{ t('member.detail.point.ledgerTitle') }}</h4>
            <p v-if="!detail.gradePoint.pointEntries.length" class="text-[13px] text-muted-color">{{ t('member.detail.point.ledgerEmpty') }}</p>
            <ul v-else class="m-0 flex list-none flex-col gap-1.5 p-0">
                <li v-for="e in detail.gradePoint.pointEntries" :key="e.entryId" class="flex items-baseline justify-between gap-3 rounded-lg border border-(--line) px-3 py-2">
                    <div class="min-w-0">
                        <p class="flex min-w-0 items-center gap-1.5 text-[13px]">
                            <!-- 사용 취소·소멸·환불(커머스 1-1)은 종류 딱지를 붙인다 -->
                            <MemberDetailTag v-if="TAGGED_KINDS.has(e.kind)" :value="t(`point.kind.${e.kind}`)" :severity="UtilPoint.kindSeverity(e.kind)" />
                            <span class="truncate">{{ e.reason }}</span>
                        </p>
                        <p class="text-xs text-muted-color tabular-nums">{{ time.full(e.createdAt) }}</p>
                    </div>
                    <b class="shrink-0 text-[13px] tabular-nums" :class="UtilPoint.deltaClass(e.kind, e.delta)">{{ UtilMember.signedNumber(e.delta, locale) }}</b>
                </li>
            </ul>
        </section>
    </div>
    <EmptyState v-else-if="detail.status === 'WITHDRAWN'" icon="pi pi-user-minus" :title="t('member.detail.withdrawnTitle')" :description="t('member.detail.withdrawnGradePoint')" />
    <EmptyState v-else icon="pi pi-star" :title="t('member.detail.notReady.title')" :description="t('member.detail.notReady.gradePoint')" />
</template>

<script setup lang="ts">
import type { MemberDetail, MemberPointKind } from '~/composables/member/api/useMemberApi.types';
import type { PointLot } from '~/composables/point/api/usePointApi.types';
import type { MemberPointLotsView } from '~/components/member/MemberDetailContent.types';
import UtilMember from '~/utils/util-member';
import UtilPoint from '~/utils/util-point';

interface Props {
    detail: MemberDetail;
    /** 포인트 묶음(커머스 1-1) — 부모가 받아 내려 준다. null 이면(권한 없음·받지 않는 화면) 합계만 보이고 묶음 부분을 그리지 않는다 */
    pointLots?: MemberPointLotsView | null;
}

const props = withDefaults(defineProps<Props>(), { pointLots: null });
const emit = defineEmits<{ 'lots-query': [query: { active: boolean; page: number }]; 'retry-lots': [] }>();

const { t, te, locale } = useI18n();
const time = useDisplayTime();
const gradeState = useGradeAppearance();
const gradeId = useId();
const pointId = useId();

/** 포인트 글자 — `1,280P` */
function points(value: number): string {
    return t('member.detail.point.amount', { amount: value.toLocaleString(locale.value) });
}

// ── 포인트 묶음(커머스 1-1) ─
const lots = computed(() => props.pointLots?.data ?? null);
const lotPages = computed(() => (lots.value && props.pointLots ? Math.ceil(lots.value.lots.total / props.pointLots.size) : 0));
const lotFilterOptions = computed(() => [
    { value: true, label: t('member.detail.point.lotsActive') },
    { value: false, label: t('member.detail.point.lotsAll') },
]);
/** 최근 포인트 내역에서 종류 딱지를 붙이는 종류 — 사용 취소·소멸·환불 */
const TAGGED_KINDS: ReadonlySet<MemberPointKind> = new Set(['CANCEL_USE', 'EXPIRE', 'REFUND']);

/** 묶음의 소멸 글자 — 충전은 「소멸 없음」, 지났으면 「소멸됨」, 아니면 소멸일 */
function lotExpiryText(lot: PointLot): string {
    if (!lot.expiresAt) return t('member.detail.point.lotNoExpiry');
    if (lot.expired) return t('member.detail.point.lotExpired');
    return t('member.detail.point.lotExpires', { date: time.value.date(lot.expiresAt) });
}

/** 변경 종류 글자 — 서버가 모르는 종류를 주면(새 종류가 먼저 배포된 경우) 키 글자 그대로 보인다 */
function kindLabel(kind: string): string {
    const key = `member.detail.grade.changeKind.${kind}`;
    return te(key) ? t(key) : kind;
}

/** 등급 변경 이력은 서버가 최대 50건을 주므로 처음엔 최근 몇 건만 보이고 나머지는 펼친다 */
const HISTORY_SHOWN = 5;
const historyOpen = ref(false);
const historyAll = computed(() => props.detail.gradePoint?.gradeHistory ?? []);
const shownHistory = computed(() => (historyOpen.value ? historyAll.value : historyAll.value.slice(0, HISTORY_SHOWN)));
const hiddenHistoryCount = computed(() => Math.max(0, historyAll.value.length - HISTORY_SHOWN));
</script>
