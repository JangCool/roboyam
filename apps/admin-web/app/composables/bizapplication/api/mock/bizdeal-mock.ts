// MOCK: 사업자 핫딜 게시(2차) — 관리자 핫딜 심사(`/hotdeal-reviews`) 화면 목업. `useBizApplicationApi` 의 `// MOCK:` 줄이 부른다.
// 이 화면은 이미 실제 API(`/api/admin/biz-applications`)에 붙어 있어서, **`?mock=bizdeal-…` 스위치가 있을 때만** 목업 건을 실제 목록 맨 위에 끼운다(스위치가 없으면 실제 응답 그대로).
// 목업 건 번호는 `mock-bizdeal-<이름>` 이라 실제 건과 겹치지 않고, 상세는 스위치 없이도 그 번호면 목업을 준다(핫딜 글 화면에서 [핫딜 심사에서 열기]로 올 때).
// 서버가 게시 기능을 내려 주면 이 파일과 `useBizApplicationApi` 의 MOCK 줄을 지운다(`common/mock/mock-util.ts` 도 쓰는 곳이 없으면).
//
// ── 개발 전용 스위치 — 주소의 `?mock=이름` (쉼표로 여러 개) ─
//   bizdeal-before      예약됨 · 게시 전(내일 0시 게시 예정 · 노출 대기)                       → 「예약·진행」 탭
//   bizdeal-posted      진행 중 · 게시됨(딜 진행 · 종료 요청 1 · 노출 중 · 지난 긴급 내림→되돌림 기록)  → 「예약·진행」
//   bizdeal-ended       진행 중 · 사업자가 딜 종료(환불 없음 · 노출 같이 빠짐)                  → 「예약·진행」
//   bizdeal-auto-ended  진행 중 · 종료 요청이 쌓여 자동 종료(환불 없음)                          → 「예약·진행」
//   bizdeal-expired     종료 · 기간 끝 = 딜 마감(옛 재고 값 LIMITED)                            → 「종료/반려/취소」
//   bizdeal-failed      진행 중 · 게시 실패(링크가 차단 규칙으로 바뀜) — [취소(전액 환불)]로 정리     → 「예약·진행」
//   bizdeal-hidden      내림(긴급) · 글 숨김 · 노출 멈춤                                       → 「예약·진행」
//   bizdeal-refunded    취소 · 진행 중 환불 승인 · 글 숨김                                      → 「종료/반려/취소」
//   bizdeal-all         위 전부
// 목업 건에서 [취소]만 흉내 낸다(취소 + 전액 환불 기록 — 메모리, 새로고침하면 처음으로).
// 실제 목록을 못 받으면(서버 꺼짐) 목업 건만 보인다.

import { MOCK_ERRORS, mockClone, mockDelay, mockFlags } from '~/composables/common/mock/mock-util';
import type {
    BizApplicationDetail,
    BizApplicationHistory,
    BizApplicationList,
    BizApplicationQuery,
    BizApplicationQueue,
    BizApplicationRow,
    BizApplicationStatus,
    BizDealPost,
} from '~/composables/bizapplication/api/useBizApplicationApi.types';
import { BIZ_APPLICATION_QUEUES } from '~/composables/bizapplication/api/useBizApplicationApi.types';

const PREFIX = 'mock-bizdeal-';
const CASES = ['before', 'posted', 'ended', 'auto-ended', 'expired', 'failed', 'hidden', 'refunded'] as const;
type Case = (typeof CASES)[number];

const HOUR = 60 * 60 * 1000;
const DAY = 24 * HOUR;
const SYSTEM = '시스템';
const ME = '관리자(나)';

/** KST 그날 0시(지금 + n일) */
function kstMidnight(days: number): string {
    const kst = new Date(Date.now() + 9 * HOUR + days * DAY);
    return new Date(Date.UTC(kst.getUTCFullYear(), kst.getUTCMonth(), kst.getUTCDate()) - 9 * HOUR).toISOString();
}
const ago = (ms: number) => new Date(Date.now() - ms).toISOString();

/** 처리 뒤 상태(취소한 목업 건) — 새로고침하면 처음으로 */
const overrides = new Map<string, BizApplicationDetail>();

function on(): Set<Case> {
    const flags = mockFlags();
    const out = new Set<Case>();
    for (const c of CASES) if (flags.has('bizdeal-all') || flags.has(`bizdeal-${c}`)) out.add(c);
    return out;
}

function caseOf(id: string): Case | null {
    const name = id.startsWith(PREFIX) ? id.slice(PREFIX.length) : '';
    return (CASES as readonly string[]).includes(name) ? (name as Case) : null;
}

function queueOf(status: BizApplicationStatus): BizApplicationQueue {
    if (status === 'SCHEDULED' || status === 'RUNNING' || status === 'TAKEN_DOWN') return 'LIVE';
    return 'CLOSED';
}

const h = (at: string, action: string, actor: string, note: string | null = null): BizApplicationHistory => ({ at, action, actor, note });

function build(c: Case): BizApplicationDetail {
    // 기간 — 게시 전은 내일 0시부터 7일, 진행 중은 이틀 전부터 7일(실패는 오늘 0시 시작), 기간 지남·환불은 지난 7일
    const startDay = c === 'before' ? 1 : c === 'failed' ? 0 : c === 'expired' ? -9 : -2;
    const period = { startAt: kstMidnight(startDay), endAt: kstMidnight(startDay + 7) };
    const submittedAt = new Date(Date.parse(period.startAt) - 5 * DAY).toISOString();
    const approvedAt = new Date(Date.parse(submittedAt) + 6 * HOUR).toISOString();
    const paidAt = new Date(Date.parse(approvedAt) + 2 * HOUR).toISOString();
    const placement = c !== 'auto-ended';

    const status: BizApplicationStatus =
        c === 'before' ? 'SCHEDULED' : c === 'expired' ? 'ENDED' : c === 'hidden' ? 'TAKEN_DOWN' : c === 'refunded' ? 'CANCELED' : 'RUNNING';

    const lines = [
        { product: 'HOTDEAL_POST' as const, unit: 'POST' as const, unitPrice: 30_000, units: 1, supply: 30_000 },
        ...(placement ? [{ product: 'HOTDEAL_TOP' as const, unit: 'DAY' as const, unitPrice: 20_000, units: 7, supply: 140_000 }] : []),
    ];
    const supply = lines.reduce((s, l) => s + l.supply, 0);
    const vat = Math.floor(supply * 0.1);
    const total = supply + vat;

    const history: BizApplicationHistory[] = [
        h(submittedAt, 'SUBMITTED', '로봇마켓 담당자'),
        h(approvedAt, 'APPROVED_ONLINE', ME),
        h(paidAt, 'PAID', '로봇마켓 담당자'),
        h(paidAt, 'SCHEDULED', SYSTEM),
    ];
    const publishAt = period.startAt;
    if (c === 'failed') history.push(h(publishAt, 'POST_FAILED', SYSTEM, 'LINK_BLOCKED'));
    else if (c !== 'before') history.push(h(publishAt, 'POSTED', SYSTEM));
    if (c === 'posted') {
        history.push(h(ago(30 * HOUR), 'TAKEN_DOWN', ME, 'EMERGENCY'), h(ago(30 * HOUR), 'POST_HIDDEN', SYSTEM), h(ago(26 * HOUR), 'RESTORED', ME, 'EXTENDED:240 · 확인 끝'), h(ago(26 * HOUR), 'POST_RESTORED', SYSTEM));
    }
    if (c === 'ended') history.push(h(ago(5 * HOUR), 'DEAL_ENDED_BY_BUSINESS', '로봇마켓 담당자', '품절'));
    if (c === 'auto-ended') history.push(h(ago(3 * HOUR), 'DEAL_AUTO_ENDED', SYSTEM));
    if (c === 'hidden') history.push(h(ago(2 * HOUR), 'TAKEN_DOWN', ME, 'EMERGENCY'), h(ago(2 * HOUR), 'POST_HIDDEN', SYSTEM));
    if (c === 'refunded') {
        history.push(
            h(ago(3 * DAY), 'REFUND_REQUESTED', '로봇마켓 담당자', '행사 일정이 바뀌었어요'),
            h(ago(2 * DAY), 'REFUND_APPROVED', ME, '106857'),
            h(ago(2 * DAY), 'CANCELED', ME),
            h(ago(2 * DAY), 'POST_HIDDEN', SYSTEM),
            h(ago(2 * DAY - HOUR), 'REFUNDED', SYSTEM, '106857'),
        );
    }

    const postIds: Partial<Record<Case, string>> = { posted: '990000001', ended: '990000002', 'auto-ended': '990000003', expired: '990000004', hidden: '990000005', refunded: '990000006' };
    const post: BizDealPost = {
        status: c === 'before' ? 'WAITING' : c === 'failed' ? 'FAILED' : c === 'hidden' || c === 'refunded' ? 'HIDDEN' : 'POSTED',
        postId: postIds[c] ?? null,
        boardSlug: 'hotdeal',
        boardName: '핫딜',
        prefixName: placement ? '로봇' : null,
        publishAt,
        view: c === 'before' || c === 'failed' ? null : c === 'ended' || c === 'auto-ended' ? 'ENDED' : c === 'expired' ? 'EXPIRED' : 'ACTIVE',
        endedBy: c === 'ended' ? 'AUTHOR' : c === 'auto-ended' ? 'AUTO' : null,
        endRequests: c === 'before' || c === 'failed' ? null : c === 'auto-ended' ? { total: 3, bySoldOut: 2, byPriceChanged: 1, byLinkDead: 0 } : c === 'posted' ? { total: 1, bySoldOut: 1, byPriceChanged: 0, byLinkDead: 0 } : { total: 0, bySoldOut: 0, byPriceChanged: 0, byLinkDead: 0 },
        failReason: c === 'failed' ? 'LINK_BLOCKED' : null,
        placement: placement
            ? {
                  product: 'HOTDEAL_TOP',
                  status: c === 'before' || c === 'failed' ? 'WAITING' : c === 'posted' ? 'SHOWING' : c === 'expired' ? 'ENDED' : 'STOPPED',
                  stopReason: c === 'ended' ? 'DEAL_ENDED' : c === 'hidden' || c === 'refunded' ? 'POST_HIDDEN' : null,
              }
            : null,
    };

    const titles: Record<Case, string> = {
        before: '[로봇마켓] 교육용 코딩 로봇 키트 런칭 특가',
        posted: '[로봇마켓] 로봇청소기 R7 한정 수량 특가',
        ended: '[로봇마켓] 서보 모터 20개 묶음 특가',
        'auto-ended': '[로봇마켓] 3D 프린터 필라멘트 1+1',
        expired: '[로봇마켓] 아두이노 호환 보드 세트',
        failed: '[로봇마켓] 드론 입문 세트 기간 특가',
        hidden: '[로봇마켓] AI 스피커 개발 키트',
        refunded: '[로봇마켓] 라이다 센서 모듈 특가',
    };

    return {
        id: `${PREFIX}${c}`,
        no: `HD-MOCK-${String(CASES.indexOf(c) + 1).padStart(2, '0')}`,
        kind: 'HOTDEAL_POST',
        adType: placement ? 'HOTDEAL_TOP' : null,
        status,
        business: { id: 'mock-biz-1', name: '로봇마켓', maskedRegNo: '123-45-*****' },
        submittedAt,
        period,
        preferredMethod: 'ONLINE',
        total,
        deadlineAt: null,
        hasRefundRequest: false,
        source: 'BUSINESS',
        applicant: '로봇마켓 담당자',
        content: {
            type: 'HOTDEAL_POST',
            title: titles[c],
            body: '공식몰 단독 특가예요. 기간 안에만 이 가격이고 재고가 떨어지면 일찍 끝날 수 있어요.',
            mallName: '로봇마켓',
            dealUrl: 'https://robotmarket.example/deal/1001',
            price: 189_000,
            originalPrice: 259_000,
            shippingFee: 0,
            endsOn: null,
            stock: c === 'expired' ? 'LIMITED' : c === 'posted' ? 'LOW' : 'IN_STOCK',
            placement: placement ? 'HOME_TOP' : 'NONE',
            affiliate: false,
            board: { id: '901', name: '핫딜' },
            prefix: placement ? { id: '9011', name: '로봇' } : null,
            // 외부 요청 없이 보이게 그림 데이터 주소
            imageUrl: c === 'failed' ? null : `data:image/svg+xml;utf8,${encodeURIComponent('<svg xmlns="http://www.w3.org/2000/svg" width="240" height="240"><rect width="240" height="240" fill="#e2e8f0"/><circle cx="120" cy="110" r="56" fill="#94a3b8"/><rect x="70" y="180" width="100" height="18" rx="9" fill="#64748b"/></svg>')}`,
        },
        memo: null,
        quote: { unitPrice: lines[0]!.unitPrice, units: 1, unit: 'POST', supply, vat, total, lines },
        remainingSlots: 1,
        slotLimit: 3,
        approval: { method: 'ONLINE', amount: { supply, vat, total }, adjustReason: null, approvedAt, approvedBy: ME },
        onlinePayment: { paidAt, pgTxId: 'mock_pg_0001' },
        contracts: [],
        deposit: null,
        refund:
            c === 'refunded'
                ? {
                      requestedAt: ago(3 * DAY),
                      reason: '행사 일정이 바뀌었어요',
                      paidAmount: total,
                      proposedAmount: 106_857,
                      usedDays: 3,
                      totalDays: 7,
                      status: 'APPROVED',
                      type: 'PRORATED',
                      method: 'ONLINE',
                      approvedAmount: 106_857,
                      decisionReason: '일할 제안 그대로',
                      transferStatus: null,
                      transferredAt: null,
                      completedAt: ago(2 * DAY - HOUR),
                  }
                : null,
        rejection: null,
        takedown: c === 'hidden' ? { at: ago(2 * HOUR), cause: 'EMERGENCY', text: '상품 페이지가 다른 상품으로 바뀌었다는 신고 — 확인 중' } : null,
        policyAgreedVersion: '광고 정책 v1 · 환불 규정 v1',
        history,
        proposedSupply: null,
        weight: 5,
        registrant: null,
        selfApproval: 'ALLOW',
        post,
    };
}

function current(c: Case): BizApplicationDetail {
    return overrides.get(`${PREFIX}${c}`) ?? build(c);
}

function toRow(d: BizApplicationDetail): BizApplicationRow {
    return {
        id: d.id,
        no: d.no,
        kind: d.kind,
        adType: d.adType,
        status: d.status,
        business: d.business,
        submittedAt: d.submittedAt,
        period: d.period,
        preferredMethod: d.preferredMethod,
        total: d.total,
        deadlineAt: d.deadlineAt,
        hasRefundRequest: d.hasRefundRequest,
        source: d.source,
    };
}

/** 목록 — 스위치가 없거나 광고 도메인이면 실제 응답 그대로. 있으면 그 큐의 목업 건을 맨 위에 끼우고 건수를 더한다 */
export async function mockBizDealList(query: BizApplicationQuery, real: () => Promise<BizApplicationList>): Promise<BizApplicationList> {
    const cases = on();
    if (query.domain !== 'HOTDEAL' || cases.size === 0) return real();
    let found: BizApplicationList;
    try {
        found = await real();
    } catch {
        // 서버가 꺼져 있어도 목업 건은 보이게
        found = { items: [], counts: Object.fromEntries(BIZ_APPLICATION_QUEUES.map((k) => [k, 0])) as Record<BizApplicationQueue, number> };
    }
    const q = query.q?.trim().toLowerCase() ?? '';
    const mine = [...cases]
        .map(current)
        .filter((d) => !query.kind || query.kind === d.kind)
        .filter((d) => !q || d.no.toLowerCase().includes(q) || d.business.name.toLowerCase().includes(q));
    const counts = { ...found.counts };
    for (const d of mine) counts[queueOf(d.status)] = (counts[queueOf(d.status)] ?? 0) + 1;
    const here = mine.filter((d) => queueOf(d.status) === query.queue).map(toRow);
    return { items: [...here, ...found.items], counts };
}

/** 상세 — 목업 건 번호면 목업(스위치 없이도), 아니면 null(실제 API 로) */
export function mockBizDealDetail(id: string): Promise<BizApplicationDetail> | null {
    const c = caseOf(id);
    if (!c) return null;
    return mockDelay().then(() => mockClone(current(c)));
}

/**
 * [취소] — 목업 건 번호가 아니면 null(실제 API 로). 게시 실패·예약 건 취소 = 결제액 전액 환불(온라인 결제 취소)로 흉내 내고, 게시된 글은 숨김.
 * 목업 건에는 다른 처리 단추가 보이지 않는다(예약·진행·종료 상태라서).
 */
export function mockBizDealCancel(id: string, reason: string): Promise<BizApplicationDetail> | null {
    const c = caseOf(id);
    if (!c) return null;
    return mockDelay().then(() => {
        const d = current(c);
        if (d.status !== 'SCHEDULED' && d.status !== 'RUNNING' && d.status !== 'TAKEN_DOWN') {
            throw MOCK_ERRORS.conflict('BIZ_APPLICATION_STATE_CHANGED', '이미 끝났거나 취소된 건이에요. 최신 상태로 다시 불러올게요.');
        }
        const now = new Date().toISOString();
        const paid = d.approval?.amount.total ?? d.total;
        const next: BizApplicationDetail = {
            ...d,
            status: 'CANCELED',
            refund: {
                requestedAt: now,
                reason,
                paidAmount: paid,
                proposedAmount: paid,
                usedDays: 0,
                totalDays: 7,
                status: 'APPROVED',
                type: 'FULL_AUTO',
                method: 'ONLINE',
                approvedAmount: paid,
                decisionReason: reason,
                transferStatus: null,
                transferredAt: null,
                completedAt: now,
            },
            post: d.post ? { ...d.post, status: d.post.postId ? 'HIDDEN' : d.post.status, placement: d.post.placement ? { ...d.post.placement, status: 'STOPPED', stopReason: d.post.postId ? 'POST_HIDDEN' : null } : null } : null,
            history: [
                ...d.history,
                h(now, 'CANCELED', ME, reason),
                ...(d.post?.postId ? [h(now, 'POST_HIDDEN', SYSTEM)] : []),
                h(now, 'REFUNDED', SYSTEM, String(paid)),
            ],
        };
        overrides.set(d.id, next);
        return mockClone(next);
    });
}
