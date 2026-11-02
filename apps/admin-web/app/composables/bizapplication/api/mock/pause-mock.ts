// MOCK: 광고·사업자 핫딜 「일시 정지 / 중단」 — 관리자 광고 심사(`/ad-reviews`)·핫딜 심사(`/hotdeal-reviews`) 목업. `useBizApplicationApi` 의 `// MOCK:` 줄이 부른다.
// 명세: 세션 노트 `ad-pause-stop-spec.md`(상태 모델 = 기존 TAKEN_DOWN + 까닭 ADVERTISER_PAUSE·ADVERTISER_STOP, 새 칸 `pause`·`takedownCause`·환불 STOP_PRORATED).
// 이 화면은 이미 실제 API(`/api/admin/biz-applications`)에 붙어 있어서, **`?mock=pause-…` 스위치가 있을 때만** 목업 건을 실제 목록 맨 위에 끼운다(스위치가 없으면 실제 응답 그대로).
// 목업 건 번호는 `mock-pause-<ad|deal>-<이름>`(광고 심사 = ad, 핫딜 심사 = deal)이라 실제 건과 겹치지 않고, 상세·처리는 스위치 없이도 그 번호면 목업이 받는다(광고 일정표·핫딜 글에서 링크로 올 때).
// 서버가 정지·재개·중단(`pause`·`resume`·`stop`)과 상세 `pause` 를 내려 주면 이 파일과 `useBizApplicationApi` 의 MOCK 줄을 지운다(`common/mock/mock-util.ts` 도 쓰는 곳이 없으면).
//
// ── 개발 전용 스위치 — 주소의 `?mock=이름` (쉼표로 여러 개) ─
//   pause-running    진행 중 · 정지 1번 썼다가 다시 켬(1/3)            → [대신 일시 정지]                     → 「예약·진행」 탭
//   pause-paused     정지 중 · 사업자가 직접 정지(2/3)                  → [대신 다시 켜기]·[대신 중단(환불 심사)] → 「예약·진행」
//   pause-paused-op  정지 중 · 운영자가 대신 정지(전화 · 메모, 1/3) — 핫딜 심사에서는 노출 없는 글(중단해도 「환불 없음」) → 「예약·진행」
//   pause-limit      진행 중 · 정지 3번 다 씀(3/3)                       → [대신 일시 정지] 비활성 + 이유         → 「예약·진행」
//   pause-stopped    중단 · 정지 중에 운영자가 대신 중단(메일) — 일할 환불 심사 대기(STOP_PRORATED, 멈춘 시각부터) → 「환불 심사」
//   pause-ended      정지한 채 기간이 끝남 — 환불 없이 종료                                                     → 「종료/반려/취소」
//   pause-all        위 전부
//   pause-limit-error  [대신 일시 정지]가 409 `BIZ_PAUSE_LIMIT`(그 사이 사업자가 마지막 정지를 썼다고 흉내 — 다시 읽으면 3/3)
// 광고 심사는 배너·한 줄 광고, 핫딜 심사는 사업자 핫딜 글(+「핫딜 게시판 상단」 노출 — pause-paused-op 만 노출 없음).
// 목업 건의 [대신 일시 정지]·[대신 다시 켜기]·[대신 중단]·환불 승인·거절은 메모리에서만 바뀐다(새로고침하면 처음으로). 실제 목록을 못 받으면(서버 꺼짐) 목업 건만 보인다.
//
// API 후보(관리자): `POST /api/admin/biz-applications/{id}/pause|resume|stop { channel, memo, reason? }` → `BizApplicationDetail`
//   권한 광고 `ad.manage` · 핫딜 `hotdeal.manage`, 감사 `BIZ_APP_PAUSE`·`BIZ_APP_RESUME`·`BIZ_APP_STOP`.
//   오류: 정지 횟수 초과 409 `BIZ_PAUSE_LIMIT` · 이미 끝남/상태 바뀜 409 `BIZ_APPLICATION_STATE_CHANGED` · 요청 경로·메모 400 `fields.channel`·`fields.memo`.

import { MOCK_ERRORS, mockClone, mockDelay, mockFlags } from '~/composables/common/mock/mock-util';
import type {
    BizAdType,
    BizApplicationContent,
    BizApplicationDetail,
    BizApplicationHistory,
    BizApplicationKind,
    BizApplicationList,
    BizApplicationQuery,
    BizApplicationQueue,
    BizApplicationRow,
    BizDealPost,
    BizDomain,
    BizPause,
    BizPauseActionRequest,
    BizQuoteLine,
    BizRefundRequest,
} from '~/composables/bizapplication/api/useBizApplicationApi.types';
import { BIZ_APPLICATION_QUEUES } from '~/composables/bizapplication/api/useBizApplicationApi.types';
import UtilBizApplication from '~/utils/util-biz-application';

const PREFIX = 'mock-pause-';
const CASES = ['running', 'paused', 'paused-op', 'limit', 'stopped', 'ended'] as const;
type Case = (typeof CASES)[number];
type Dom = 'ad' | 'deal';

const HOUR = 60 * 60 * 1000;
const DAY = 24 * HOUR;
const MIN = 60 * 1000;
const SYSTEM = '시스템';
const ME = '관리자(나)';
const LIMIT = 3;

const ago = (ms: number) => new Date(Date.now() - ms).toISOString();
/** KST 그날 0시(지금 + n일) */
function kstMidnight(days: number): string {
    return UtilBizApplication.kstMidnight(UtilBizApplication.addDays(UtilBizApplication.kstToday(), days));
}

/** 처리 뒤 상태 — 새로고침하면 처음으로 */
const overrides = new Map<string, BizApplicationDetail>();

function domOf(domain: BizDomain): Dom {
    return domain === 'AD' ? 'ad' : 'deal';
}

function on(): Set<Case> {
    const flags = mockFlags();
    const out = new Set<Case>();
    for (const c of CASES) if (flags.has('pause-all') || flags.has(`pause-${c}`)) out.add(c);
    return out;
}

function parse(id: string): { dom: Dom; c: Case } | null {
    if (!id.startsWith(PREFIX)) return null;
    const rest = id.slice(PREFIX.length);
    const dom = rest.startsWith('ad-') ? 'ad' : rest.startsWith('deal-') ? 'deal' : null;
    if (!dom) return null;
    const name = rest.slice(dom.length + 1);
    return (CASES as readonly string[]).includes(name) ? { dom, c: name as Case } : null;
}

/** 목업 건 번호인가(광고 일정표·핫딜 글 목업이 링크에 `?mock=` 를 실을지 볼 때) */
export function isMockPauseId(id: string): boolean {
    return parse(id) !== null;
}

/** 서버 `BizApplicationReader.queue` 와 같은 판정 — 열린 환불이 먼저, 내림은 끝 전이면 「예약·진행」 */
function queueOf(d: BizApplicationDetail, now = Date.now()): BizApplicationQueue {
    if (d.refund?.status === 'REQUESTED') return 'REFUND';
    switch (d.status) {
        case 'SCHEDULED':
        case 'RUNNING':
            return 'LIVE';
        case 'TAKEN_DOWN':
            return Date.parse(d.period.endAt) > now ? 'LIVE' : 'CLOSED';
        default:
            return 'CLOSED';
    }
}

const h = (at: string, action: string, actor: string, note: string | null = null): BizApplicationHistory => ({ at, action, actor, note });

const IMG = (fill: string) =>
    `data:image/svg+xml;utf8,${encodeURIComponent(`<svg xmlns="http://www.w3.org/2000/svg" width="728" height="180"><rect width="728" height="180" fill="${fill}"/><rect x="40" y="60" width="380" height="28" rx="6" fill="#ffffff" opacity=".85"/><rect x="40" y="104" width="220" height="18" rx="6" fill="#ffffff" opacity=".6"/></svg>`)}`;
const DEAL_IMG = `data:image/svg+xml;utf8,${encodeURIComponent('<svg xmlns="http://www.w3.org/2000/svg" width="240" height="240"><rect width="240" height="240" fill="#e2e8f0"/><circle cx="120" cy="110" r="56" fill="#94a3b8"/><rect x="70" y="180" width="100" height="18" rx="9" fill="#64748b"/></svg>')}`;

/** 광고 심사 목업 건의 종류·유형 */
const AD_SHAPE: Record<Case, { kind: BizApplicationKind; adType: BizAdType; unitPrice: number }> = {
    running: { kind: 'AD_BANNER', adType: 'WIDE_BANNER', unitPrice: 50_000 },
    paused: { kind: 'AD_TEXT_LINE', adType: 'TEXT_LINE', unitPrice: 5_000 },
    'paused-op': { kind: 'AD_BANNER', adType: 'STRIP_BANNER', unitPrice: 30_000 },
    limit: { kind: 'AD_BANNER', adType: 'SQUARE_BANNER', unitPrice: 20_000 },
    stopped: { kind: 'AD_BANNER', adType: 'WIDE_BANNER', unitPrice: 50_000 },
    ended: { kind: 'AD_TEXT_LINE', adType: 'TEXT_LINE', unitPrice: 5_000 },
};

const DEAL_TITLES: Record<Case, string> = {
    running: '[로봇마켓] 휴머노이드 키트 H1 런칭 특가',
    paused: '[로봇마켓] 로봇 팔 교육 세트 기간 특가',
    'paused-op': '[로봇마켓] 서보 모터 30개 묶음',
    limit: '[로봇마켓] 자율주행 RC카 키트 특가',
    stopped: '[로봇마켓] 라즈베리파이 로봇 세트',
    ended: '[로봇마켓] 센서 모듈 10종 묶음',
};

const POST_IDS: Record<Case, string> = { running: '990000101', paused: '990000102', 'paused-op': '990000103', limit: '990000104', stopped: '990000105', ended: '990000106' };

function build(dom: Dom, c: Case): BizApplicationDetail {
    const deal = dom === 'deal';
    // 기간 — 진행 중 건은 사흘 전 시작(광고 10일 · 핫딜 7일), 정지한 채 끝난 건은 이틀 전에 끝남
    const length = deal ? 7 : 10;
    const startDay = c === 'ended' ? -2 - length : -3;
    const period = { startAt: kstMidnight(startDay), endAt: kstMidnight(startDay + length) };
    const submittedAt = new Date(Date.parse(period.startAt) - 5 * DAY).toISOString();
    const approvedAt = new Date(Date.parse(submittedAt) + 6 * HOUR).toISOString();
    const paidAt = new Date(Date.parse(approvedAt) + 2 * HOUR).toISOString();
    const startAt = Date.parse(period.startAt);
    const businessName = deal ? '로봇마켓' : '하늘로봇';
    const BIZ = `${businessName} 담당자`;
    const placement = deal && c !== 'paused-op';

    // 금액 줄
    let lines: BizQuoteLine[];
    let adType: BizAdType | null;
    let kind: BizApplicationKind;
    if (deal) {
        kind = 'HOTDEAL_POST';
        adType = placement ? 'HOTDEAL_TOP' : null;
        lines = [
            { product: 'HOTDEAL_POST', unit: 'POST', unitPrice: 30_000, units: 1, supply: 30_000 },
            ...(placement ? [{ product: 'HOTDEAL_TOP' as const, unit: 'DAY' as const, unitPrice: 20_000, units: length, supply: 20_000 * length }] : []),
        ];
    } else {
        const shape = AD_SHAPE[c];
        kind = shape.kind;
        adType = shape.adType;
        lines = [{ product: shape.adType, unit: 'DAY', unitPrice: shape.unitPrice, units: length, supply: shape.unitPrice * length }];
    }
    const supply = lines.reduce((s, l) => s + l.supply, 0);
    const vat = Math.floor(supply * 0.1);
    const total = supply + vat;
    // 일할 대상 — 광고 = 확정 합계, 핫딜 글 = 노출 자리 줄 합계(게시비 제외, 부가세 포함)
    const placementSupply = lines.filter((l) => l.product !== 'HOTDEAL_POST').reduce((s, l) => s + l.supply, 0);
    const proratableTotal = deal ? placementSupply + Math.floor(placementSupply * 0.1) : total;

    const history: BizApplicationHistory[] = [h(submittedAt, 'SUBMITTED', BIZ), h(approvedAt, 'APPROVED_ONLINE', ME), h(paidAt, 'PAID', BIZ), h(paidAt, 'SCHEDULED', SYSTEM)];
    if (deal) history.push(h(period.startAt, 'POSTED', SYSTEM));
    /** 지난 정지 한 번(정지 → 다시 켜기) — 기록과 멈춘 분 */
    const pastPause = (from: number, minutes: number, who = BIZ, channelNote: string | null = null) => {
        const at = new Date(from).toISOString();
        const back = new Date(from + minutes * MIN).toISOString();
        history.push(h(at, 'PAUSED', who, channelNote));
        if (deal) history.push(h(at, 'POST_HIDDEN', SYSTEM, 'ADVERTISER_PAUSE'));
        history.push(h(back, 'RESUMED', who, channelNote ? `${channelNote.split(' · ')[0]} · MIN:${minutes}` : `MIN:${minutes}`));
        if (deal) history.push(h(back, 'POST_RESTORED', SYSTEM, 'ADVERTISER_PAUSE'));
        return minutes;
    };

    let status: BizApplicationDetail['status'] = 'RUNNING';
    let pause: BizPause = { used: 0, limit: LIMIT, pausedAt: null, actor: null, byOperator: false, channel: null, pausedMinutes: 0, stoppedAt: null, refundFrom: null, proratableTotal };
    let takedown: BizApplicationDetail['takedown'] = null;
    let refund: BizRefundRequest | null = null;
    let postStatus: BizDealPost['status'] = 'POSTED';
    let placementState: NonNullable<BizDealPost['placement']>['status'] = 'SHOWING';
    let stopReason: NonNullable<BizDealPost['placement']>['stopReason'] = null;

    switch (c) {
        case 'running': {
            const m = pastPause(startAt + DAY + 10 * HOUR, 95);
            pause = { ...pause, used: 1, pausedMinutes: m };
            break;
        }
        case 'limit': {
            let m = pastPause(startAt + 4 * HOUR, 30);
            m += pastPause(startAt + DAY + 2 * HOUR, 180, ME, 'PHONE · 행사 준비로 잠깐 내려 달라는 전화');
            m += pastPause(startAt + 2 * DAY + 9 * HOUR, 60);
            pause = { ...pause, used: 3, pausedMinutes: m };
            break;
        }
        case 'paused': {
            const m = pastPause(startAt + 5 * HOUR, 40);
            const at = ago(5 * HOUR);
            history.push(h(at, 'PAUSED', BIZ));
            if (deal) history.push(h(at, 'POST_HIDDEN', SYSTEM, 'ADVERTISER_PAUSE'));
            status = 'TAKEN_DOWN';
            takedown = { at, cause: 'ADVERTISER_PAUSE', text: null, by: BIZ, channel: null, memo: null };
            pause = { ...pause, used: 2, pausedAt: at, actor: BIZ, byOperator: false, pausedMinutes: m };
            postStatus = 'HIDDEN';
            placementState = 'STOPPED';
            stopReason = 'PAUSED';
            break;
        }
        case 'paused-op': {
            const at = ago(26 * HOUR);
            const memo = '매장 내부 공사로 이틀 쉬고 싶다는 전화 — 사업자 본인 확인함';
            history.push(h(at, 'PAUSED', ME, `PHONE · ${memo}`));
            if (deal) history.push(h(at, 'POST_HIDDEN', SYSTEM, 'ADVERTISER_PAUSE'));
            status = 'TAKEN_DOWN';
            takedown = { at, cause: 'ADVERTISER_PAUSE', text: null, by: ME, channel: 'PHONE', memo };
            pause = { ...pause, used: 1, pausedAt: at, actor: ME, byOperator: true, channel: 'PHONE' };
            postStatus = 'HIDDEN';
            placementState = 'STOPPED';
            stopReason = 'PAUSED';
            break;
        }
        case 'stopped': {
            const pausedAt = ago(20 * HOUR);
            const stoppedAt = ago(2 * HOUR);
            const memo = '메일로 중단·환불 요청 — 행사 취소';
            history.push(h(pausedAt, 'PAUSED', BIZ));
            if (deal) history.push(h(pausedAt, 'POST_HIDDEN', SYSTEM, 'ADVERTISER_PAUSE'));
            history.push(h(stoppedAt, 'STOPPED', ME, `FROM_PAUSE · EMAIL · ${memo}`));
            const paid = total;
            const r = UtilBizApplication.stopRefund(proratableTotal, period, pausedAt);
            if (r.amount > 0) history.push(h(stoppedAt, 'REFUND_REQUESTED', ME, 'ADVERTISER_STOP'));
            status = 'TAKEN_DOWN';
            takedown = { at: stoppedAt, cause: 'ADVERTISER_STOP', text: null, by: ME, channel: 'EMAIL', memo };
            pause = { ...pause, used: 1, pausedMinutes: Math.round((Date.parse(stoppedAt) - Date.parse(pausedAt)) / MIN), stoppedAt, refundFrom: pausedAt };
            refund =
                r.amount > 0
                    ? {
                          requestedAt: stoppedAt,
                          reason: memo,
                          paidAmount: paid,
                          proposedAmount: r.amount,
                          usedDays: r.usedDays,
                          totalDays: r.totalDays,
                          status: 'REQUESTED',
                          type: 'STOP_PRORATED',
                          method: 'ONLINE',
                          approvedAmount: null,
                          decisionReason: null,
                          transferStatus: null,
                          transferredAt: null,
                          completedAt: null,
                      }
                    : null;
            postStatus = 'HIDDEN';
            placementState = 'STOPPED';
            stopReason = 'POST_HIDDEN';
            break;
        }
        case 'ended': {
            const at = new Date(Date.parse(period.endAt) - 3 * DAY).toISOString();
            history.push(h(at, 'PAUSED', BIZ));
            if (deal) history.push(h(at, 'POST_HIDDEN', SYSTEM, 'ADVERTISER_PAUSE'));
            status = 'TAKEN_DOWN';
            takedown = { at, cause: 'ADVERTISER_PAUSE', text: null, by: BIZ, channel: null, memo: null };
            // 끝 시각에서 멈춘 시간 계산이 끝난다(서버 배치가 닫는다고 가정)
            pause = { ...pause, used: 1, pausedAt: at, actor: BIZ, pausedMinutes: 0 };
            postStatus = 'HIDDEN';
            placementState = 'ENDED';
            stopReason = null;
            break;
        }
    }

    const content: BizApplicationContent = deal
        ? {
              type: 'HOTDEAL_POST',
              title: DEAL_TITLES[c],
              body: '공식몰 단독 특가예요. 기간 안에만 이 가격이고 재고가 떨어지면 일찍 끝날 수 있어요.',
              mallName: '로봇마켓',
              dealUrl: `https://robotmarket.example/deal/${POST_IDS[c]}`,
              price: 159_000,
              originalPrice: 219_000,
              shippingFee: 0,
              endsOn: null,
              stock: 'IN_STOCK',
              placement: placement ? 'HOME_TOP' : 'NONE',
              affiliate: false,
              board: { id: '901', name: '핫딜' },
              prefix: { id: '9011', name: '로봇' },
              imageUrl: DEAL_IMG,
          }
        : kind === 'AD_TEXT_LINE'
          ? { type: 'AD_TEXT_LINE', text: '하늘로봇 코딩 교실 — 11월 무료 체험 수업 모집', linkUrl: 'https://skyrobot.example/class' }
          : { type: 'AD_BANNER', imageUrl: IMG(c === 'stopped' ? '#7c3aed' : c === 'limit' ? '#0f766e' : '#2563eb'), linkUrl: 'https://skyrobot.example/sale', altText: '하늘로봇 가을 할인전 — 교육용 로봇 최대 30%' };

    const post: BizDealPost | null = deal
        ? {
              status: postStatus,
              postId: POST_IDS[c],
              boardSlug: 'hotdeal',
              boardName: '핫딜',
              prefixName: '로봇',
              publishAt: period.startAt,
              view: c === 'ended' ? 'EXPIRED' : 'ACTIVE',
              endedBy: null,
              endRequests: { total: c === 'paused' ? 1 : 0, bySoldOut: c === 'paused' ? 1 : 0, byPriceChanged: 0, byLinkDead: 0 },
              failReason: null,
              placement: placement ? { product: 'HOTDEAL_TOP', status: placementState, stopReason } : null,
          }
        : null;

    return {
        id: `${PREFIX}${dom}-${c}`,
        no: `${deal ? 'HD' : 'AD'}-PAUSE-${String(CASES.indexOf(c) + 1).padStart(2, '0')}`,
        kind,
        adType,
        status,
        business: { id: deal ? 'mock-biz-1' : 'mock-biz-2', name: businessName, maskedRegNo: deal ? '123-45-*****' : '234-56-*****' },
        submittedAt,
        period,
        preferredMethod: 'ONLINE',
        total,
        deadlineAt: null,
        hasRefundRequest: refund?.status === 'REQUESTED',
        source: 'BUSINESS',
        takedownCause: status === 'TAKEN_DOWN' ? (takedown?.cause ?? null) : null,
        applicant: BIZ,
        content,
        memo: null,
        quote: { unitPrice: lines[0]!.unitPrice, units: lines[0]!.units, unit: lines[0]!.unit, supply, vat, total, lines },
        remainingSlots: 1,
        slotLimit: 3,
        approval: { method: 'ONLINE', amount: { supply, vat, total }, adjustReason: null, approvedAt, approvedBy: ME },
        onlinePayment: { paidAt, pgTxId: `mock_pg_pause_${dom}_${c}` },
        contracts: [],
        deposit: null,
        refund,
        rejection: null,
        takedown,
        policyAgreedVersion: '광고 정책 v1 · 환불 규정 v1',
        history,
        proposedSupply: null,
        weight: 5,
        registrant: null,
        selfApproval: 'ALLOW',
        post,
        pause,
    };
}

function stored(dom: Dom, c: Case): BizApplicationDetail {
    return overrides.get(`${PREFIX}${dom}-${c}`) ?? build(dom, c);
}

/** 내보낼 모양 — 지금 정지 중이면 멈춘 시간에 지금까지를 더한다(끝 시각이 지났으면 끝 시각까지). 저장한 값(`overrides`)은 끝난 정지분만 가진다 */
function live(d: BizApplicationDetail): BizApplicationDetail {
    const out = mockClone(d);
    const p = out.pause;
    if (p?.pausedAt && out.status === 'TAKEN_DOWN') {
        const until = Math.min(Date.now(), Date.parse(out.period.endAt));
        p.pausedMinutes += Math.max(0, Math.round((until - Date.parse(p.pausedAt)) / MIN));
    }
    return out;
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
        takedownCause: d.takedownCause ?? null,
    };
}

/** 켠 목업 건(그 도메인) — 광고 일정표 목업도 쓴다. `only` 를 주면 그 이름만 */
export function mockPauseDetails(domain: BizDomain, only?: readonly string[]): BizApplicationDetail[] {
    const dom = domOf(domain);
    return [...on()].filter((c) => !only || only.includes(c)).map((c) => live(stored(dom, c)));
}

/** 목업 건 상세(번호로 — 스위치 없이도). 목업 건이 아니면 null */
export function mockPauseDetailSync(id: string): BizApplicationDetail | null {
    const p = parse(id);
    return p ? live(stored(p.dom, p.c)) : null;
}

/** 목록 — 스위치가 없으면 실제 응답 그대로. 있으면 그 큐의 목업 건을 맨 위에 끼우고 건수를 더한다 */
export async function mockPauseList(query: BizApplicationQuery, real: () => Promise<BizApplicationList>): Promise<BizApplicationList> {
    const cases = on();
    if (cases.size === 0) return real();
    let found: BizApplicationList;
    try {
        found = await real();
    } catch {
        // 서버가 꺼져 있어도 목업 건은 보이게
        found = { items: [], counts: Object.fromEntries(BIZ_APPLICATION_QUEUES.map((k) => [k, 0])) as Record<BizApplicationQueue, number> };
    }
    const q = query.q?.trim().toLowerCase() ?? '';
    const mine = mockPauseDetails(query.domain)
        .filter((d) => !query.kind || query.kind === d.kind)
        .filter((d) => !q || d.no.toLowerCase().includes(q) || d.business.name.toLowerCase().includes(q));
    const counts = { ...found.counts };
    for (const d of mine) counts[queueOf(d)] = (counts[queueOf(d)] ?? 0) + 1;
    const here = mine.filter((d) => queueOf(d) === query.queue).map(toRow);
    return { items: [...here, ...found.items], counts };
}

/** 상세 — 목업 건 번호면 목업(스위치 없이도), 아니면 null(실제 API 로) */
export function mockPauseDetail(id: string): Promise<BizApplicationDetail> | null {
    const d = mockPauseDetailSync(id);
    if (!d) return null;
    return mockDelay().then(() => d);
}

const stateChanged = () => MOCK_ERRORS.conflict('BIZ_APPLICATION_STATE_CHANGED', '그 사이 상태가 바뀌었어요. 최신 상태로 다시 불러올게요.');

/** 처리 하나 — 목업 건이 아니면 null(실제 API 로). 바꾼 건을 메모리에 두고 지금 모양을 돌려준다 */
function act(id: string, change: (d: BizApplicationDetail, now: string) => BizApplicationDetail): Promise<BizApplicationDetail> | null {
    const p = parse(id);
    if (!p) return null;
    return mockDelay().then(() => {
        const next = change(live(stored(p.dom, p.c)), new Date().toISOString());
        overrides.set(next.id, next);
        return live(next);
    });
}

/** 핫딜 글 건이면 글·노출 줄도 같이 */
function withPost(d: BizApplicationDetail, status: BizDealPost['status'], placement: { status: NonNullable<BizDealPost['placement']>['status']; stopReason: NonNullable<BizDealPost['placement']>['stopReason'] } | null): BizDealPost | null | undefined {
    if (!d.post) return d.post;
    return { ...d.post, status, placement: d.post.placement && placement ? { ...d.post.placement, ...placement } : d.post.placement };
}

/** [대신 일시 정지] — 진행 중만, 정지 횟수 안에서. `?mock=pause-limit-error` 면 409 `BIZ_PAUSE_LIMIT`(다시 읽으면 3/3) */
export function mockPause(id: string, body: BizPauseActionRequest): Promise<BizApplicationDetail> | null {
    const p = parse(id);
    if (!p) return null;
    if (mockFlags().has('pause-limit-error')) {
        return mockDelay().then(() => {
            const d = live(stored(p.dom, p.c));
            if (d.pause) overrides.set(d.id, { ...d, pause: { ...d.pause, used: d.pause.limit } });
            throw MOCK_ERRORS.conflict('BIZ_PAUSE_LIMIT', '이 광고는 정지를 3번 모두 썼어요. 더 멈추려면 중단하고 환불을 요청해 주세요.');
        });
    }
    return act(id, (d, now) => {
        if (d.status !== 'RUNNING' || !d.pause || UtilBizApplication.ended(d.period)) throw stateChanged();
        if (UtilBizApplication.pauseLimitReached(d.pause)) throw MOCK_ERRORS.conflict('BIZ_PAUSE_LIMIT', '이 광고는 정지를 3번 모두 썼어요. 더 멈추려면 중단하고 환불을 요청해 주세요.');
        const hide = d.post ? [h(now, 'POST_HIDDEN', SYSTEM, 'ADVERTISER_PAUSE')] : [];
        return {
            ...d,
            status: 'TAKEN_DOWN',
            takedownCause: 'ADVERTISER_PAUSE',
            takedown: { at: now, cause: 'ADVERTISER_PAUSE', text: null, by: ME, channel: body.channel, memo: body.memo },
            pause: { ...d.pause, used: d.pause.used + 1, pausedAt: now, actor: ME, byOperator: true, channel: body.channel },
            post: withPost(d, 'HIDDEN', { status: 'STOPPED', stopReason: 'PAUSED' }),
            history: [...d.history, h(now, 'PAUSED', ME, `${body.channel} · ${body.memo}`), ...hide],
        };
    });
}

/** [대신 다시 켜기] — 정지 중이고 끝 전만. 승인 없이 바로 재개(끝 시각 그대로 — 연장 없음) */
export function mockResume(id: string, body: BizPauseActionRequest): Promise<BizApplicationDetail> | null {
    return act(id, (d, now) => {
        const p = d.pause;
        if (UtilBizApplication.pauseState(d.status, d.takedown?.cause) !== 'PAUSED' || !p?.pausedAt || UtilBizApplication.ended(d.period)) throw stateChanged();
        const minutes = Math.max(0, Math.round((Date.parse(now) - Date.parse(p.pausedAt)) / MIN));
        const show = d.post ? [h(now, 'POST_RESTORED', SYSTEM, 'ADVERTISER_PAUSE')] : [];
        return {
            ...d,
            status: 'RUNNING',
            takedownCause: null,
            takedown: null,
            // `live` 가 이미 지금 정지분을 더해 두었다 — 그대로 굳힌다
            pause: { ...p, pausedAt: null, actor: null, byOperator: false, channel: null },
            post: withPost(d, 'POSTED', { status: 'SHOWING', stopReason: null }),
            history: [...d.history, h(now, 'RESUMED', ME, `${body.channel} · MIN:${minutes} · ${body.memo}`), ...show],
        };
    });
}

/**
 * [대신 중단(환불 심사)] — 진행 중·정지 중이고 끝 전만. 바로 내리고, 남은 기간 일할 환불을 심사 대기로(대상 금액이 0원이면 환불 줄 없음).
 * 기준 시각 = 정지 중 전환이면 멈춘 시각, 아니면 지금. 되돌리기 없음.
 */
export function mockStop(id: string, body: BizPauseActionRequest): Promise<BizApplicationDetail> | null {
    return act(id, (d, now) => {
        const p = d.pause;
        const paused = UtilBizApplication.pauseState(d.status, d.takedown?.cause) === 'PAUSED';
        if (!p || (!paused && d.status !== 'RUNNING') || UtilBizApplication.ended(d.period)) throw stateChanged();
        const from = paused && p.pausedAt ? p.pausedAt : now;
        const already = d.refund?.status === 'APPROVED' ? (d.refund.approvedAmount ?? 0) : 0;
        const r = UtilBizApplication.stopRefund(p.proratableTotal, d.period, from, already);
        const paid = d.approval?.amount.total ?? d.total;
        const extra: BizApplicationHistory[] = [];
        if (d.post && !paused) extra.push(h(now, 'POST_HIDDEN', SYSTEM, 'ADVERTISER_STOP'));
        if (r.amount > 0) extra.push(h(now, 'REFUND_REQUESTED', ME, 'ADVERTISER_STOP'));
        const refund: BizRefundRequest | null =
            r.amount > 0
                ? {
                      requestedAt: now,
                      reason: body.memo,
                      paidAmount: paid,
                      proposedAmount: r.amount,
                      usedDays: r.usedDays,
                      totalDays: r.totalDays,
                      status: 'REQUESTED',
                      type: 'STOP_PRORATED',
                      method: 'ONLINE',
                      approvedAmount: null,
                      decisionReason: null,
                      transferStatus: null,
                      transferredAt: null,
                      completedAt: null,
                  }
                : d.refund;
        return {
            ...d,
            status: 'TAKEN_DOWN',
            takedownCause: 'ADVERTISER_STOP',
            takedown: { at: now, cause: 'ADVERTISER_STOP', text: null, by: ME, channel: body.channel, memo: body.memo },
            pause: { ...p, pausedAt: null, actor: null, byOperator: false, channel: null, stoppedAt: now, refundFrom: from },
            refund,
            hasRefundRequest: refund?.status === 'REQUESTED',
            post: withPost(d, 'HIDDEN', { status: 'STOPPED', stopReason: 'POST_HIDDEN' }),
            history: [...d.history, h(now, 'STOPPED', ME, `${paused ? 'FROM_PAUSE · ' : ''}${body.channel} · ${body.memo}`), ...extra],
        };
    });
}

/** 환불 승인(목업 건만 — 아니면 null) — 중단 건은 승인하면 `CANCELED`(온라인 결제 부분 취소로 흉내) */
export function mockPauseApproveRefund(id: string, body: { amount: number; reason: string }): Promise<BizApplicationDetail> | null {
    return act(id, (d, now) => {
        if (d.refund?.status !== 'REQUESTED') throw stateChanged();
        return {
            ...d,
            status: 'CANCELED',
            takedownCause: null,
            hasRefundRequest: false,
            refund: { ...d.refund, status: 'APPROVED', approvedAmount: body.amount, decisionReason: body.reason, completedAt: now },
            history: [...d.history, h(now, 'REFUND_APPROVED', ME, String(body.amount)), h(now, 'CANCELED', ME), h(now, 'REFUNDED', SYSTEM, String(body.amount))],
        };
    });
}

/** 환불 거절(목업 건만) — 중단 건은 내림(`TAKEN_DOWN`) 그대로 */
export function mockPauseDenyRefund(id: string, reason: string): Promise<BizApplicationDetail> | null {
    return act(id, (d, now) => {
        if (d.refund?.status !== 'REQUESTED') throw stateChanged();
        return {
            ...d,
            hasRefundRequest: false,
            refund: { ...d.refund, status: 'DENIED', decisionReason: reason },
            history: [...d.history, h(now, 'REFUND_DENIED', ME, reason)],
        };
    });
}
