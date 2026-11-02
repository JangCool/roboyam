// MOCK: 광고·사업자 핫딜 「일시 정지 / 중단」 목업(2026-11-02) — `useBusinessApi` 의 `// MOCK:` 분기와 글 보기 목업(`board/api/mock/bizdeal-post-mock.ts`)이 부른다.
// 이 화면들은 이미 실제 API 에 연결돼 있어서 **목업 신청 건 ID(`mock-ad-pause`·`mock-deal-pause`)일 때만** 목업을 쓴다. 다른 ID 는 실제 API 그대로다.
// 서버가 붙으면 이 파일과 분기를 지운다. 응답 문구는 서버 몫이라 번역 키로 옮기지 않는다(i18n.md 5절). 목업 상태는 SPA 메모리(새로 고침하면 처음으로).
//
// 목업 건: `mock-ad-pause` 넓은 배너 · 온라인 결제 · 14일 중 6일째쯤 / `mock-deal-pause` 사업자 핫딜 글 + 「핫딜 게시판 상단」 7일(게시비 30,000 + 노출 줄, 부가세 포함)
// 상태 스위치(`?mock=` — 아래 차례로 앞에 있는 것이 이긴다, 없으면 pause-running):
//   pause-running(진행 중 · 정지 0번) · pause-used2(정지 2번 씀) · pause-used3(다 씀 → 중단만) · pause-paused(내가 멈춤) · pause-paused-op(운영팀이 전화 요청으로 대신 멈춤) ·
//   pause-paused-ended(정지한 채 기간 끝 — 환불 없음) · pause-stopped(중단 · 환불 심사 중) · pause-stopped-from-pause(정지 중 전환 — 기준 시각 = 멈춘 시각) ·
//   pause-stopped-approved(→ CANCELED + 환불 승인) · pause-stopped-denied(환불 거절) · pause-upcoming(시작 전 — 정지 없음, 옛 [취소·환불 요청]만)
// 덧붙임: pause-no-placement(핫딜 글만 — 노출 자리 없음, 중단해도 돌려줄 금액 0 → 환불 줄 없음) · pause-limit-error([일시 정지] → 409 BIZ_PAUSE_LIMIT) ·
//   pause-state-error([다시 켜기] → 409 BIZ_APPLICATION_STATE_CHANGED — 그새 기간이 끝난 것으로 바뀐다)

import { ApiError } from '~/composables/common/useApiClient';
import { mockClone, mockDelay, mockFlags } from '~/composables/common/mock/mock-util';
import type {
    BizAmount,
    BizApplicationDetail,
    BizDeal,
    BizDealPost,
    BizHistoryEntry,
    BizPause,
    BizPauseChannel,
    BizRefundRequest,
} from '~/composables/business/api/useBusinessApi.types';
import UtilBusiness from '~/utils/util-business';

export const MOCK_AD_PAUSE_ID = 'mock-ad-pause';
export const MOCK_DEAL_PAUSE_ID = 'mock-deal-pause';
/** 목업 핫딜 글 — 글 보기 목업(`board/api/mock/bizdeal-post-mock.ts`)과 같은 ID */
const MOCK_POST_ID = 'mock-bizdeal';

const PAUSE_LIMIT = 3;
/** 광고 하루 단가(공급가) */
const AD_DAILY = 30_000;
/** 핫딜 게시비(부가세 포함) — 중단해도 돌려주지 않는다 */
const POST_FEE = 30_000;
/** 「핫딜 게시판 상단」 하루(부가세 포함) */
const TOP_DAILY = 22_000;

type InitState =
    | 'running'
    | 'used2'
    | 'used3'
    | 'paused'
    | 'paused-op'
    | 'paused-ended'
    | 'stopped'
    | 'stopped-from-pause'
    | 'stopped-approved'
    | 'stopped-denied'
    | 'upcoming';
const STATES: readonly InitState[] = ['running', 'used2', 'used3', 'paused', 'paused-op', 'paused-ended', 'stopped', 'stopped-from-pause', 'stopped-approved', 'stopped-denied', 'upcoming'];

/** 지금 목업 건의 처리 단계 */
type Phase = 'running' | 'deal-ended' | 'paused' | 'stopped' | 'stopped-approved' | 'stopped-denied' | 'upcoming' | 'canceled';

interface Runtime {
    /** 초기화한 상태 스위치 — 바뀌면 처음부터 다시 만든다 */
    key: string;
    start: string;
    end: string;
    phase: Phase;
    used: number;
    pausedAt: number | null;
    pausedBy: 'ME' | 'OPERATOR' | null;
    channel: BizPauseChannel | null;
    /** 끝난 정지들의 멈춘 시간 합(분) */
    closedMinutes: number;
    stoppedAt: number | null;
    refundFrom: number | null;
    stopReason: string | null;
    /** [품절로 종료] 시각(핫딜 글) */
    dealEndedAt: number | null;
    /** 시작 전 [취소·환불 요청] */
    canceled: { reason: string; at: number } | null;
    /** 기본 기록(신청·승인·결제·예약·게시) 뒤에 붙는 기록 */
    events: BizHistoryEntry[];
}

const runtimes = new Map<string, Runtime>();

/** 목업 신청 건 ID 인가 */
export function isMockPauseId(id: string): boolean {
    return id === MOCK_AD_PAUSE_ID || id === MOCK_DEAL_PAUSE_ID;
}

function isDeal(id: string): boolean {
    return id === MOCK_DEAL_PAUSE_ID;
}

function stateKey(): string {
    const flags = mockFlags();
    const state = STATES.find((s) => flags.has(`pause-${s}`)) ?? 'running';
    return `${state}${flags.has('pause-no-placement') ? '+no-placement' : ''}`;
}

function iso(ms: number): string {
    return new Date(ms).toISOString();
}

const H = 3_600_000;

function entry(at: number, actor: BizHistoryEntry['actor'], action: BizHistoryEntry['action'], note: string | null = null): BizHistoryEntry {
    return { at: iso(at), actor, action, note };
}

/** 스위치로 고른 처음 상태 */
function init(id: string, key: string): Runtime {
    const now = Date.now();
    const today = UtilBusiness.todayYmd(now);
    const state = (key.split('+')[0] ?? 'running') as InitState;
    const deal = isDeal(id);
    const span = deal ? 7 : 14;
    // 진행 중: 광고 14일 중 6일째 · 핫딜 7일 중 3일째 / 정지한 채 끝: 어제까지 / 시작 전: 사흘 뒤부터
    const start = state === 'paused-ended' ? UtilBusiness.addDays(today, -span) : state === 'upcoming' ? UtilBusiness.addDays(today, 3) : UtilBusiness.addDays(today, deal ? -2 : -5);
    const end = UtilBusiness.addDays(start, span - 1);
    const rt: Runtime = {
        key,
        start,
        end,
        phase: 'running',
        used: 0,
        pausedAt: null,
        pausedBy: null,
        channel: null,
        closedMinutes: 0,
        stoppedAt: null,
        refundFrom: null,
        stopReason: null,
        dealEndedAt: null,
        canceled: null,
        events: [],
    };
    const pair = (pauseAt: number, resumeAt: number, by: 'ME' | 'OPERATOR' = 'ME', channel: BizPauseChannel | null = null): void => {
        const minutes = Math.round((resumeAt - pauseAt) / 60_000);
        rt.events.push(entry(pauseAt, by, 'PAUSED', by === 'OPERATOR' ? channel : null), entry(resumeAt, 'ME', 'RESUMED', `MIN:${minutes}`));
        rt.used += 1;
        rt.closedMinutes += minutes;
    };
    const pauseNow = (at: number, by: 'ME' | 'OPERATOR' = 'ME', channel: BizPauseChannel | null = null): void => {
        rt.phase = 'paused';
        rt.used += 1;
        rt.pausedAt = at;
        rt.pausedBy = by;
        rt.channel = channel;
        rt.events.push(entry(at, by, 'PAUSED', channel));
    };
    const stopAt = (at: number, reason: string): void => {
        const fromPause = rt.pausedAt !== null;
        if (fromPause) rt.closedMinutes += Math.round((at - rt.pausedAt!) / 60_000);
        rt.phase = 'stopped';
        rt.stoppedAt = at;
        rt.refundFrom = fromPause ? rt.pausedAt : at;
        rt.stopReason = reason;
        rt.pausedAt = null;
        rt.pausedBy = null;
        rt.channel = null;
        rt.events.push(entry(at, 'ME', 'STOPPED', fromPause ? 'FROM_PAUSE' : null));
        if (refundAmountOf(id, rt) > 0) rt.events.push(entry(at, 'ME', 'REFUND_REQUESTED', reason));
    };
    switch (state) {
        case 'running':
            break;
        case 'used2':
        case 'used3':
            pair(now - 40 * H, now - 38 * H);
            pair(now - 20 * H, now - 14 * H, 'OPERATOR', 'EMAIL');
            if (state === 'used3') pair(now - 10 * H, now - 8 * H);
            break;
        case 'paused':
            pauseNow(now - 5 * H);
            break;
        case 'paused-op':
            pauseNow(now - 3 * H, 'OPERATOR', 'PHONE');
            break;
        case 'paused-ended':
            // 끝 날 저녁 6시(한국)에 멈추고 다시 켜지 않았다
            pauseNow(Date.parse(UtilBusiness.kstStartOf(end)) + 18 * H);
            break;
        case 'stopped':
            stopAt(now - 2 * H, '행사가 일찍 끝나 광고를 내리려고 해요.');
            break;
        case 'stopped-from-pause':
            pauseNow(now - 30 * H);
            stopAt(now - 2 * H, '재고가 바닥나 더 내보낼 수 없어요.');
            break;
        case 'stopped-approved':
        case 'stopped-denied': {
            stopAt(now - 28 * H, '행사가 일찍 끝나 광고를 내리려고 해요.');
            const amount = refundAmountOf(id, rt);
            if (amount <= 0) break;
            if (state === 'stopped-approved') {
                rt.phase = 'stopped-approved';
                rt.events.push(entry(now - 4 * H, 'OPERATOR', 'REFUND_APPROVED', String(amount)), entry(now - 4 * H, 'SYSTEM', 'REFUNDED', String(amount)));
            } else {
                rt.phase = 'stopped-denied';
                rt.events.push(entry(now - 4 * H, 'OPERATOR', 'REFUND_DENIED', DENY_REASON));
            }
            break;
        }
        case 'upcoming':
            rt.phase = 'upcoming';
            break;
    }
    return rt;
}

const DENY_REASON = '중단 요청 전 이틀 동안 노출 집계가 정상이었고, 남은 기간은 일할로 이미 다른 건에 옮겨 드렸어요(목업 문구).';

function runtimeOf(id: string): Runtime {
    const key = stateKey();
    let rt = runtimes.get(id);
    if (!rt || rt.key !== key) {
        rt = init(id, key);
        runtimes.set(id, rt);
    }
    return rt;
}

function noPlacement(id: string, rt: Runtime): boolean {
    return isDeal(id) && rt.key.endsWith('+no-placement');
}

/** 확정 합계(부가세 포함) */
function totalOf(id: string, rt: Runtime): number {
    const days = UtilBusiness.daysBetween(rt.start, rt.end);
    if (!isDeal(id)) return Math.round(AD_DAILY * days * (1 + UtilBusiness.VAT_RATE));
    return POST_FEE + (noPlacement(id, rt) ? 0 : TOP_DAILY * days);
}

/** 일할 대상 — 광고 = 합계, 핫딜 글 = 노출 줄만(게시비 제외) */
function proratableOf(id: string, rt: Runtime): number {
    return isDeal(id) ? totalOf(id, rt) - POST_FEE : totalOf(id, rt);
}

function refundAmountOf(id: string, rt: Runtime): number {
    if (rt.refundFrom === null) return 0;
    return UtilBusiness.stopRefund(proratableOf(id, rt), rt.start, rt.end, iso(rt.refundFrom)).amount;
}

function amountOf(total: number): BizAmount {
    const supply = Math.round(total / (1 + UtilBusiness.VAT_RATE));
    return { supply, vat: total - supply, total };
}

function dealOf(id: string, rt: Runtime): BizDeal {
    return {
        linkUrl: 'https://smartstore.naver.com/roboparts/products/1234567',
        shopName: '로보파츠상회',
        listPrice: 89_000,
        dealPrice: 59_000,
        shippingFee: 0,
        endsOn: rt.end,
        stock: 'IN_STOCK',
        body: '서보모터 MG996R 10개 묶음이에요. 정상가 근거는 상품 상세의 정가 표시예요. 재고는 300세트 준비했어요.',
        placement: noPlacement(id, rt) ? 'NONE' : 'HOME_TOP',
        boardId: 'mock-board-hotdeal',
        prefixId: null,
        imageFileId: null,
        imageUrl: null,
    };
}

function postOf(id: string, rt: Runtime, ended: boolean): BizDealPost {
    const publishAt = UtilBusiness.kstStartOf(rt.start);
    const placement = (status: NonNullable<BizDealPost['placement']>['status'], stopReason: NonNullable<BizDealPost['placement']>['stopReason'] = null): BizDealPost['placement'] =>
        noPlacement(id, rt) ? null : { product: 'HOTDEAL_TOP', status, stopReason };
    const base: BizDealPost = {
        status: 'POSTED',
        postId: MOCK_POST_ID,
        boardSlug: 'hotdeal',
        boardName: '핫딜',
        prefixName: null,
        publishAt,
        view: 'ACTIVE',
        endedBy: null,
        endRequests: { total: 0, bySoldOut: 0, byPriceChanged: 0, byLinkDead: 0 },
        failReason: null,
        placement: placement('SHOWING'),
        autoEndAt: null,
        endedAt: null,
    };
    switch (rt.phase) {
        case 'upcoming':
            return { ...base, status: 'WAITING', postId: null, view: null, endRequests: null, placement: placement('WAITING') };
        case 'canceled':
            return { ...base, status: 'HIDDEN', postId: null, view: null, endRequests: null, placement: placement('STOPPED', 'POST_HIDDEN') };
        case 'running':
            return base;
        case 'deal-ended':
            return { ...base, view: 'ENDED', endedBy: 'AUTHOR', endedAt: rt.dealEndedAt !== null ? iso(rt.dealEndedAt) : null, placement: placement('STOPPED', 'DEAL_ENDED') };
        case 'paused':
            // 목록·검색·가젯에서 빠진다(주소로 오면 「판매자가 잠시 내린 딜이에요」) — 노출 자리도 같이 멈춘다
            return ended ? { ...base, status: 'HIDDEN', view: 'EXPIRED', placement: placement('ENDED') } : { ...base, status: 'HIDDEN', placement: placement('STOPPED', 'PAUSED') };
        default:
            return { ...base, status: 'HIDDEN', placement: placement('STOPPED', 'POST_HIDDEN') };
    }
}

/** 지금 상태의 목업 신청 건 */
function build(id: string): BizApplicationDetail {
    const now = Date.now();
    const rt = runtimeOf(id);
    const deal = isDeal(id);
    const total = totalOf(id, rt);
    const amount = amountOf(total);
    const startAt = Date.parse(UtilBusiness.kstStartOf(rt.start));
    const submittedAt = startAt - 72 * H;
    const ended = UtilBusiness.periodOver(rt.end, now);

    const history: BizHistoryEntry[] = [
        entry(submittedAt, 'ME', 'SUBMITTED'),
        entry(submittedAt + 20 * H, 'OPERATOR', 'APPROVED_ONLINE'),
        entry(submittedAt + 22 * H, 'ME', 'PAID'),
        entry(submittedAt + 22 * H, 'SYSTEM', 'SCHEDULED'),
    ];
    if (deal && rt.phase !== 'upcoming' && rt.phase !== 'canceled') history.push(entry(startAt, 'SYSTEM', 'POSTED'));
    history.push(...rt.events);

    let status: BizApplicationDetail['status'] = 'SCHEDULED';
    let cause: BizApplicationDetail['takedownCause'] = null;
    let refund: BizRefundRequest | null = null;
    const stopRefund = refundAmountOf(id, rt);
    const stopRequest = (s: BizRefundRequest['status']): BizRefundRequest | null =>
        stopRefund > 0 && rt.stoppedAt !== null
            ? {
                  type: 'STOP_PRORATED',
                  status: s,
                  reason: rt.stopReason ?? '',
                  requestedAt: iso(rt.stoppedAt),
                  proposedAmount: stopRefund,
                  refundAmount: s === 'APPROVED' ? stopRefund : null,
                  decisionReason: s === 'DENIED' ? DENY_REASON : null,
              }
            : null;
    switch (rt.phase) {
        case 'running':
        case 'deal-ended':
        case 'upcoming':
            break;
        case 'canceled':
            status = 'CANCELED';
            refund = { type: 'FULL_AUTO', status: 'APPROVED', reason: rt.canceled?.reason ?? '', requestedAt: iso(rt.canceled?.at ?? now), proposedAmount: total, refundAmount: total, decisionReason: null };
            break;
        case 'paused':
            status = 'TAKEN_DOWN';
            cause = 'ADVERTISER_PAUSE';
            break;
        case 'stopped':
            status = 'TAKEN_DOWN';
            cause = 'ADVERTISER_STOP';
            refund = stopRequest('REQUESTED');
            break;
        case 'stopped-approved':
            // 승인 → 취소(CANCELED) — 까닭은 남긴다(「광고주 요청으로 중단」)
            status = 'CANCELED';
            cause = 'ADVERTISER_STOP';
            refund = stopRequest('APPROVED');
            break;
        case 'stopped-denied':
            status = 'TAKEN_DOWN';
            cause = 'ADVERTISER_STOP';
            refund = stopRequest('DENIED');
            break;
    }
    const phase = status === 'SCHEDULED' ? (rt.phase === 'upcoming' ? 'UPCOMING' : ended ? 'ENDED' : 'RUNNING') : null;

    const openMinutes = rt.pausedAt !== null ? Math.round((Math.min(now, ended ? Date.parse(UtilBusiness.kstStartOf(UtilBusiness.addDays(rt.end, 1))) : now) - rt.pausedAt) / 60_000) : 0;
    const pause: BizPause | null =
        rt.phase === 'canceled'
            ? null
            : {
                  used: rt.used,
                  limit: PAUSE_LIMIT,
                  pausedAt: rt.pausedAt !== null ? iso(rt.pausedAt) : null,
                  pausedBy: rt.pausedBy,
                  channel: rt.channel,
                  pausedMinutes: rt.closedMinutes + Math.max(0, openMinutes),
                  stoppedAt: rt.stoppedAt !== null ? iso(rt.stoppedAt) : null,
                  refundFrom: rt.refundFrom !== null ? iso(rt.refundFrom) : null,
                  proratableTotal: proratableOf(id, rt),
              };
    if (rt.canceled) history.push(entry(rt.canceled.at, 'ME', 'REFUND_REQUESTED', rt.canceled.reason), entry(rt.canceled.at, 'SYSTEM', 'REFUNDED', String(total)));

    return {
        id,
        kind: deal ? 'HOTDEAL_POST' : 'AD_BANNER',
        title: deal ? 'MG996R 서보모터 10개 묶음 특가' : '로보파츠 가을 할인 — 넓은 배너',
        adType: deal ? null : 'WIDE',
        placement: deal ? (noPlacement(id, rt) ? 'NONE' : 'HOME_TOP') : null,
        startDate: rt.start,
        endDate: rt.end,
        status,
        phase,
        paymentMethod: 'ONLINE',
        amount,
        amountConfirmed: true,
        paymentDueAt: null,
        depositDueAt: null,
        submittedAt: iso(submittedAt),
        refundRequested: refund !== null,
        takedownCause: cause,
        estimate: amount,
        confirmed: amount,
        adjustReason: null,
        creative: deal
            ? null
            : { version: 1, imageUrl: null, imageName: 'roboparts-wide.png', linkUrl: 'https://roboparts.example.com/sale', altText: '로보파츠 가을 할인', text: null, imageFileId: null },
        deal: deal ? dealOf(id, rt) : null,
        rejectCode: null,
        rejectReason: null,
        takedownReason: null,
        deposit: null,
        refund,
        history: history.sort((a, b) => Date.parse(a.at) - Date.parse(b.at)),
        post: deal ? postOf(id, rt, ended) : null,
        pause,
    };
}

const stateChanged = () => new ApiError({ status: 409, code: 'BIZ_APPLICATION_STATE_CHANGED', message: '이미 처리 상태가 바뀌었어요.' });

/** `GET /api/business/applications/mock-*-pause` */
export async function mockPauseApplication(id: string): Promise<BizApplicationDetail> {
    await mockDelay();
    return mockClone(build(id));
}

/** `POST …/{id}/pause` — 바로 멈춘다(자리는 계속 내 몫). 다 썼으면 409 `BIZ_PAUSE_LIMIT` */
export async function mockPause(id: string): Promise<BizApplicationDetail> {
    await mockDelay();
    const rt = runtimeOf(id);
    if (mockFlags().has('pause-limit-error')) {
        // 그새 다른 창·운영팀이 횟수를 다 썼다 — 다시 읽으면 3/3
        rt.used = PAUSE_LIMIT;
        throw new ApiError({ status: 409, code: 'BIZ_PAUSE_LIMIT', message: `이 건은 일시 정지를 ${PAUSE_LIMIT}번 모두 썼어요. 중단만 할 수 있어요.` });
    }
    if (!UtilBusiness.runningPausable(build(id))) throw stateChanged();
    if (rt.used >= PAUSE_LIMIT) throw new ApiError({ status: 409, code: 'BIZ_PAUSE_LIMIT', message: `이 건은 일시 정지를 ${PAUSE_LIMIT}번 모두 썼어요. 중단만 할 수 있어요.` });
    const now = Date.now();
    rt.phase = 'paused';
    rt.used += 1;
    rt.pausedAt = now;
    rt.pausedBy = 'ME';
    rt.channel = null;
    rt.events.push(entry(now, 'ME', 'PAUSED'));
    return mockClone(build(id));
}

/** `POST …/{id}/resume` — 승인 없이 바로 다시 나간다(끝 시각 그대로) */
export async function mockResume(id: string): Promise<BizApplicationDetail> {
    await mockDelay();
    const rt = runtimeOf(id);
    if (mockFlags().has('pause-state-error') && rt.phase === 'paused') {
        // 그새 원래 기간이 끝났다 — 다시 읽으면 「정지한 채 기간이 끝났어요」
        const span = UtilBusiness.daysBetween(rt.start, rt.end);
        rt.end = UtilBusiness.addDays(UtilBusiness.todayYmd(), -1);
        rt.start = UtilBusiness.addDays(rt.end, -(span - 1));
        rt.pausedAt = Date.parse(UtilBusiness.kstStartOf(rt.end)) + 18 * H;
        throw stateChanged();
    }
    const now = Date.now();
    if (rt.phase !== 'paused' || rt.pausedAt === null || UtilBusiness.periodOver(rt.end, now)) throw stateChanged();
    const minutes = Math.round((now - rt.pausedAt) / 60_000);
    rt.phase = 'running';
    rt.closedMinutes += minutes;
    rt.pausedAt = null;
    rt.pausedBy = null;
    rt.channel = null;
    rt.events.push(entry(now, 'ME', 'RESUMED', `MIN:${minutes}`));
    return mockClone(build(id));
}

/** `POST …/{id}/stop { reason }` — 바로 내려가고 남은 기간 일할 환불을 운영팀이 심사(대상 0원이면 환불 줄 없음). 정지 중이면 멈춘 시각부터 */
export async function mockStop(id: string, reason: string): Promise<BizApplicationDetail> {
    await mockDelay();
    const rt = runtimeOf(id);
    const now = Date.now();
    const detail = build(id);
    const paused = rt.phase === 'paused' && rt.pausedAt !== null && !UtilBusiness.periodOver(rt.end, now);
    if (!paused && !UtilBusiness.runningPausable(detail)) throw stateChanged();
    if (paused) rt.closedMinutes += Math.round((now - rt.pausedAt!) / 60_000);
    rt.refundFrom = paused ? rt.pausedAt : now;
    rt.phase = 'stopped';
    rt.stoppedAt = now;
    rt.stopReason = reason;
    rt.pausedAt = null;
    rt.pausedBy = null;
    rt.channel = null;
    rt.events.push(entry(now, 'ME', 'STOPPED', paused ? 'FROM_PAUSE' : null));
    if (refundAmountOf(id, rt) > 0) rt.events.push(entry(now, 'ME', 'REFUND_REQUESTED', reason));
    return mockClone(build(id));
}

/** `POST …/{id}/refund-request` — 목업 건은 시작 전 전액 자동 취소만(진행 중은 [중단하고 환불 요청]) */
export async function mockPauseRefund(id: string, reason: string): Promise<BizApplicationDetail> {
    await mockDelay();
    const rt = runtimeOf(id);
    if (rt.phase !== 'upcoming') throw stateChanged();
    rt.phase = 'canceled';
    rt.canceled = { reason, at: Date.now() };
    return mockClone(build(id));
}

/** `POST …/{id}/deal/end` — [품절로 종료](환불 없음 · 노출도 멈춤 — 이 뒤로는 정지·중단 없음) */
export async function mockPauseEndDeal(id: string): Promise<BizDealPost> {
    await mockDelay();
    const rt = runtimeOf(id);
    if (!isDeal(id) || rt.phase !== 'running') throw new ApiError({ status: 409, code: 'DEAL_ENDED', message: '이미 끝난 딜이에요.' });
    const now = Date.now();
    rt.phase = 'deal-ended';
    rt.dealEndedAt = now;
    rt.events.push(entry(now, 'ME', 'DEAL_ENDED_BY_BUSINESS'));
    return mockClone(build(id).post!);
}

/**
 * 목업 핫딜 글(`mock-bizdeal`)이 지금 사업자 손으로 내려져 있나 — 글 보기 목업이 「판매자가 (잠시) 내린 딜이에요」를 고른다.
 * 신청 건 목업을 한 번도 안 열었으면 null(글 보기 스위치를 따른다).
 */
export function mockDealPauseState(): 'LIVE' | 'PAUSED' | 'STOPPED' | null {
    const rt = runtimes.get(MOCK_DEAL_PAUSE_ID);
    if (!rt) return null;
    if (rt.phase === 'paused') return 'PAUSED';
    if (rt.phase === 'stopped' || rt.phase === 'stopped-approved' || rt.phase === 'stopped-denied') return 'STOPPED';
    return 'LIVE';
}
