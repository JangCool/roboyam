<template>
    <!--
        회원 상세 — 제재 구획. 위에 제재 현황 요약(경고 n/2 · 진행 중 · 다음 단계 — 제재 API 현황 `preview` 를 부모가 받아 내려 준다) + [제재하기](`sanction.manage`)·진행 중 정지 [해제]·「제재 화면에서 보기」,
        가운데 신고 요약(받은 · 조치한 · 제기한 신고 수 — 회원 상세 API 값), 아래 제재 이력(단계 · 상태 · 기간 · 사유 · 근거 신고 사건 · 이의제기 — 범위 칸은 없다, 사이트 제재만).
        단계는 계약의 `WARNING|SUSPEND_3|SUSPEND_7|SUSPEND_30|BAN`, 상태 `ACTIVE|ENDED|LIFTED|VOIDED` — 글자와 아이콘을 함께(`SanctionTag`).
        제재하기·해제는 이벤트로 올리고 대화상자는 부모(회원 관리 화면)가 띄운다. 현황을 받지 않는 화면(`view` 없음 — 닉네임 이미지 심사·탈퇴 관리의 회원 서랍)은 제재 화면 링크만 보인다.
        탈퇴(진행 중 포함) 회원은 제재할 수 없다(서버 404) — 안내 한 줄.
    -->
    <div class="flex flex-col gap-5">
        <section :aria-labelledby="summaryId">
            <div class="mb-2 flex flex-wrap items-center justify-between gap-2">
                <h3 :id="summaryId" class="text-[13px] font-semibold">{{ t('member.detail.sanction.summaryTitle') }}</h3>
                <NuxtLink v-if="perm.canRead" :to="{ path: '/sanctions', query: { userId: detail.memberNo } }" class="inline-flex items-center gap-1 rounded px-1 text-xs text-primary hover:underline focus-visible:outline-2 focus-visible:outline-primary">
                    <i class="pi pi-shield text-[10px]" aria-hidden="true" />{{ t('sanction.openMemberSanctions') }}
                </NuxtLink>
            </div>

            <p v-if="withdrawn" class="m-0 flex items-center gap-1.5 rounded-md border border-dashed border-(--line) px-2.5 py-1.5 text-xs text-muted-color">
                <i class="pi pi-info-circle text-[11px]" aria-hidden="true" />{{ t('sanction.withdrawnNote') }}
            </p>
            <p v-else-if="!view" class="m-0 text-[13px] text-muted-color">{{ t('sanction.previewElsewhere') }}</p>
            <ErrorState v-else-if="view.error" :title="t('sanction.previewFailed')" :description="view.error.message" :code="view.error.status === 404 ? '404' : '!'" :retry="view.error.status !== 404" :retrying="view.loading" @retry="emit('retry-sanctions')" />
            <Skeleton v-else-if="!view.preview" height="5.5rem" />
            <SanctionPreviewSummary v-else :preview="view.preview">
                <template v-if="running || canIssue" #actions>
                    <Button v-if="running && UtilSanction.canLift(running, liftPerm)" type="button" :label="t('sanction.lift')" icon="pi pi-lock-open" size="small" severity="secondary" variant="outlined" @click="emit('lift-sanction', running)" />
                    <Button v-if="canIssue" type="button" :label="t('sanction.issue')" icon="pi pi-plus" size="small" @click="emit('sanction')" />
                </template>
            </SanctionPreviewSummary>
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

        <section v-if="view?.preview" :aria-labelledby="historyId">
            <h3 :id="historyId" class="mb-2 text-[13px] font-semibold">{{ t('member.detail.sanction.historyTitle') }}</h3>
            <SanctionHistoryList :lines="view.preview.history" :perm="liftPerm" link-sanction @lift="(line) => emit('lift-sanction', line)" />
        </section>
    </div>
</template>

<script setup lang="ts">
import type { MemberDetail } from '~/composables/member/api/useMemberApi.types';
import type { MemberSanctionsView } from '~/components/member/MemberDetailContent.types';
import type { SanctionLine, SanctionRunningLine } from '~/composables/sanction/api/useSanctionApi.types';
import UtilSanction from '~/utils/util-sanction';

interface Props {
    detail: MemberDetail;
    /** 제재 현황 — 부모가 받은 값. 받지 않는 화면은 null */
    view?: MemberSanctionsView | null;
}

const props = withDefaults(defineProps<Props>(), { view: null });
const emit = defineEmits<{ sanction: []; 'lift-sanction': [line: SanctionRunningLine]; 'retry-sanctions': [] }>();

const { t, locale } = useI18n();
const permission = useSanctionPermission();
const perm = computed(() => ({ canRead: permission.canRead.value, canManage: permission.canManage.value, canBan: permission.canBan.value }));
const liftPerm = computed(() => ({ canManage: perm.value.canManage, canBan: perm.value.canBan }));
const summaryId = useId();
const reportId = useId();
const historyId = useId();

const withdrawn = computed(() => props.detail.status === 'WITHDRAWN' || props.detail.status === 'WITHDRAWING');
const running = computed(() => props.view?.preview?.active ?? null);
/** [제재하기] — `sanction.manage` 가 있고 줄 수 있는 단계가 있을 때(영구 정지 회원은 없다) */
const canIssue = computed(() => UtilSanction.canIssue(liftPerm.value) && Boolean(props.view?.preview && UtilSanction.hasAnyOption(props.view.preview)));
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
