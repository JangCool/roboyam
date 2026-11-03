<template>
    <!--
        처리할 일(04-ia-review Q5 — 통합 「신청 심사」 큐 대신 1인 운영의 「한눈에」): 광고 심사 · 핫딜 심사 · 입금 확인 대기 · 입금 기한 지남 · 환불 심사 · 사업자 인증 대기
        · 회원 핫딜 확인 필요(`FLAGGED`) · 위장 홍보 의심(`OPEN`) — 핫딜 글·의심 후보 목록의 상태별 건수(`hotdeal.read`).
        신청 건 숫자는 권한(`ad.read`·`hotdeal.read`)이 있는 도메인마다 목록 API 를 한 번 불러 받은 큐별 건수(`counts`)이고, 두 도메인을 합친 칸은 도메인별 링크를 따로 단다(행의 숫자 앞).
        사업자 인증 대기는 사업자 목록의 상태별 건수 중 심사 중(처음·재신청 + 재심사, `business.read`). 권한이 있는 칸만 보이고, 칸을 누르면 그 화면·큐로 간다.
        (대시보드가 가젯형이 되며 페이지에서 옮겼다 — 부르는 API·규칙은 그대로)
    -->
    <DashboardGadgetFrame :title="title" :at="state.at.value" :loading="state.loading.value" :error="state.error.value" :ready="state.data.value !== null" :empty="!cards.length" @refresh="state.reload">
        <!-- 한 줄 행 두 열(사용자 결정 2026-11-03 「영역이 너무 크고 넓다」) — 설명은 마우스를 올리면 보인다 -->
        <template #skeleton><DashboardStatSkeleton :count="8" :min="13" height="2.125rem" /></template>
        <DashboardStatGrid :stats="cards" :min="13" row />
    </DashboardGadgetFrame>
</template>

<script setup lang="ts">
import type { DashboardStat, DashboardStatTone } from '~/components/dashboard/DashboardStatCard.types';
import type { BizApplicationQueue, BizDomain } from '~/composables/bizapplication/api/useBizApplicationApi.types';
import { BIZ_DOMAINS } from '~/composables/bizapplication/api/useBizApplicationApi.types';
import type { ApiError } from '~/composables/common/useApiClient';
import type { DashboardGadgetItem } from '~/composables/dashboard/api/useDashboardLayoutApi.types';
import UtilBizApplication from '~/utils/util-biz-application';

interface Props {
    gadget: DashboardGadgetItem;
    title: string;
}

const props = withDefaults(defineProps<Props>(), {});

/** 처리할 일 숫자 — 볼 권한이 있는 도메인의 큐별 건수 · 사업자 심사 중 건수 · 회원 핫딜 확인 필요·의심 후보 건수(권한 없으면 null) */
interface TodoView {
    domains: Partial<Record<BizDomain, Record<BizApplicationQueue, number>>>;
    businessPending: number | null;
    /** 종료 요청이 문턱을 넘어 운영자 확인을 기다리는 핫딜 글(`FLAGGED`, 사업자 글 포함) */
    hotdealFlagged: number | null;
    /** 위장 홍보 의심 후보 중 아직 처리하지 않은 것(`OPEN`, 회원·판매처) */
    hotdealSuspicions: number | null;
}

const { t } = useI18n();
const bizApi = useBizApplicationApi();
const businessApi = useBusinessApi();
const hotdealApi = useHotdealApi();
const suspicionApi = useHotdealSuspicionApi();
const toApiError = useApiErrorOf();
const { can } = useAdminPermission();

/** 신청 건을 볼 수 있는 도메인(조회 권한) */
const todoDomains = computed(() => BIZ_DOMAINS.filter((d) => can(UtilBizApplication.DOMAIN_SCREENS[d].read)));
const canBusiness = computed(() => can('business.read'));
/** 회원 핫딜 칸(확인 필요·의심 후보) — 핫딜 글·의심 후보 화면과 같은 조회 권한 */
const canHotdeal = computed(() => can('hotdeal.read'));

/** 권한 있는 도메인마다 목록 한 번(큐별 건수) + 사업자 목록 한 번(상태별 건수). 403 은 오류가 아니라 그 칸을 빼고, 다른 실패는 칸 전체를 오류로 */
async function fetchTodo(): Promise<{ generatedAt: string; data: TodoView }> {
    const domains = todoDomains.value;
    const [bizResults, businessResult, hotdealResults] = await Promise.all([
        Promise.allSettled(domains.map((domain) => bizApi.list({ domain, queue: 'REVIEW' }))),
        canBusiness.value ? Promise.allSettled([businessApi.list({ status: 'PENDING', page: 0, size: 1 })]).then((r) => r[0]!) : Promise.resolve(null),
        // 회원 핫딜 — 글 목록의 상태별 건수(`counts.FLAGGED`) · 의심 후보의 상태별 건수(`counts.OPEN`), 한 줄씩만 받는다
        canHotdeal.value ? Promise.allSettled([hotdealApi.list({ view: 'FLAGGED', page: 0, size: 1 }), suspicionApi.list('OPEN', 'ALL', 0, 1)]) : Promise.resolve(null),
    ]);
    let failure: ApiError | null = null;
    const view: TodoView = { domains: {}, businessPending: null, hotdealFlagged: null, hotdealSuspicions: null };
    bizResults.forEach((r, i) => {
        if (r.status === 'fulfilled') view.domains[domains[i]!] = r.value.counts;
        else {
            const e = toApiError(r.reason);
            if (e.status !== 403) failure ??= e;
        }
    });
    if (businessResult?.status === 'fulfilled') view.businessPending = businessResult.value.counts.PENDING;
    else if (businessResult?.status === 'rejected') {
        const e = toApiError(businessResult.reason);
        if (e.status !== 403) failure ??= e;
    }
    if (hotdealResults) {
        const [flagged, suspicions] = hotdealResults;
        if (flagged!.status === 'fulfilled') view.hotdealFlagged = flagged!.value.counts.FLAGGED;
        if (suspicions!.status === 'fulfilled') view.hotdealSuspicions = suspicions!.value.counts.OPEN;
        for (const r of hotdealResults) {
            if (r.status !== 'rejected') continue;
            const e = toApiError(r.reason);
            if (e.status !== 403) failure ??= e;
        }
    }
    if (failure) throw failure;
    return { generatedAt: new Date().toISOString(), data: view };
}

const state = useDashboardGadgetData(fetchTodo, { refresh: () => props.gadget.config.refresh ?? 0 });

/** 신청 건 큐 하나의 칸 — 볼 수 있는 도메인의 건수를 더하고 도메인마다 그 화면·큐로 가는 링크를 단다 */
function queueCard(view: TodoView, key: string, queue: BizApplicationQueue, label: string, note: string, icon: string, tone: (n: number) => DashboardStatTone, domains: readonly BizDomain[]): DashboardStat | null {
    const seen = domains.filter((d) => view.domains[d]);
    if (!seen.length) return null;
    const count = seen.reduce((sum, d) => sum + (view.domains[d]?.[queue] ?? 0), 0);
    const links = seen.map((d) => ({
        label: t('dashboard.todoDomainLink', { domain: t(`bizApplication.domain.${d}`), count: view.domains[d]?.[queue] ?? 0 }),
        to: `${UtilBizApplication.DOMAIN_SCREENS[d].path}?queue=${queue}`,
    }));
    return { key, label, count, note, icon, tone: tone(count), links };
}

const cards = computed<DashboardStat[]>(() => {
    const v = state.data.value;
    if (!v) return [];
    const normal = (): DashboardStatTone => 'normal';
    const out: (DashboardStat | null)[] = [
        queueCard(v, 'adReview', 'REVIEW', t('dashboard.todoAdReview'), t('dashboard.todoReviewNote'), 'pi pi-megaphone', normal, ['AD']),
        queueCard(v, 'hotdealReview', 'REVIEW', t('dashboard.todoHotdealReview'), t('dashboard.todoReviewNote'), 'pi pi-ticket', normal, ['HOTDEAL']),
        queueCard(v, 'deposit', 'DEPOSIT', t('dashboard.todoDeposit'), t('dashboard.todoDepositNote'), 'pi pi-wallet', normal, BIZ_DOMAINS),
        queueCard(v, 'depositOverdue', 'DEPOSIT_OVERDUE', t('dashboard.todoDepositOverdue'), t('dashboard.todoDepositOverdueNote'), 'pi pi-calendar-times', (n) => (n > 0 ? 'danger' : 'normal'), BIZ_DOMAINS),
        queueCard(v, 'refund', 'REFUND', t('dashboard.todoRefund'), t('dashboard.todoRefundNote'), 'pi pi-replay', (n) => (n > 0 ? 'warn' : 'normal'), BIZ_DOMAINS),
        v.businessPending === null
            ? null
            : { key: 'businessReview', label: t('dashboard.todoBusinessReview'), count: v.businessPending, note: t('dashboard.todoBusinessReviewNote'), icon: 'pi pi-briefcase', tone: 'normal', to: '/business-reviews' },
        v.hotdealFlagged === null
            ? null
            : { key: 'hotdealFlagged', label: t('dashboard.todoHotdealFlagged'), count: v.hotdealFlagged, note: t('dashboard.todoHotdealFlaggedNote'), icon: 'pi pi-flag', tone: v.hotdealFlagged > 0 ? 'warn' : 'normal', to: '/hotdeals?view=FLAGGED' },
        v.hotdealSuspicions === null
            ? null
            : { key: 'hotdealSuspicions', label: t('dashboard.todoHotdealSuspicions'), count: v.hotdealSuspicions, note: t('dashboard.todoHotdealSuspicionsNote'), icon: 'pi pi-exclamation-triangle', tone: 'normal', to: '/hotdeals/suspicions' },
    ];
    return out.filter((c): c is DashboardStat => c !== null);
});
</script>
