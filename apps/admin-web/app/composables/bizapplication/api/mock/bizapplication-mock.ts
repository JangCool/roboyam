// MOCK: 사업자 신청 건(광고·핫딜) 심사 API 목업 — 서버가 붙으면 이 폴더와 `useBizApplicationApi.ts` 의 `// MOCK:` 줄을 지운다.
// 값은 메모리에만 둔다(새로고침하면 처음으로). 처리 단추가 이 상태를 실제로 바꾸고, 광고 자리를 잡는 건은 광고 일정표 목업 장부에도 반영한다(`mockAdSyncFromApplication`).
// 스위치(`?mock=`): bizapp-fail(목록 500) · bizapp-empty(신청 건 없음)
// 문구(서버 몫)는 번역하지 않는다(i18n.md 5절).

import { mockAdSlotState, mockAdSyncFromApplication, mockBannerUrl } from '~/composables/ad/api/mock/ad-mock';
import type { AdBookingStatus } from '~/composables/ad/api/useAdApi.types';
import type {
    BizAdType,
    BizApplicationContent,
    BizApplicationDetail,
    BizApplicationKind,
    BizApplicationList,
    BizApplicationQuery,
    BizApplicationQueue,
    BizApplicationRow,
    BizApplicationStatus,
    BizApproveRequest,
    BizAvailability,
    BizDepositRequest,
    BizPaymentMethod,
    BizPeriod,
    BizRejectCode,
} from '~/composables/bizapplication/api/useBizApplicationApi.types';
import { BIZ_APPLICATION_QUEUES } from '~/composables/bizapplication/api/useBizApplicationApi.types';
import { MOCK_ERRORS, mockClone, mockDelay, mockFlags } from '~/composables/common/mock/mock-util';
import UtilBizApplication from '~/utils/util-biz-application';

const ME = '운영자 김민지';
const HOUR = 3_600_000;
const DAY = 24 * HOUR;

const BUSINESSES = {
    banchan: { id: 'biz-5', name: '오늘의 반찬', maskedRegNo: '211-**-***87', applicant: '반찬가게' },
    camping: { id: 'biz-6', name: '캠핑하우스', maskedRegNo: '608-**-***15', applicant: '캠핑좋아' },
    plant: { id: 'biz-7', name: '디지털플랜트', maskedRegNo: '129-**-***40', applicant: '플랜트맨' },
    bigsale: { id: 'biz-9', name: '빅세일 코리아', maskedRegNo: '135-**-***33', applicant: '빅세일' },
} as const;
type BizKey = keyof typeof BUSINESSES;

const today = () => UtilBizApplication.kstToday();
const at = (offsetDays: number) => UtilBizApplication.kstMidnight(UtilBizApplication.addDays(today(), offsetDays));
const span = (from: number, to: number): BizPeriod => ({ startAt: at(from), endAt: at(to) });
const ago = (ms: number) => new Date(Date.now() - ms).toISOString();
const ahead = (ms: number) => new Date(Date.now() + ms).toISOString();

function content(kind: BizApplicationKind, adType: BizAdType | null, label: string, color: string): BizApplicationContent {
    switch (kind) {
        case 'AD_BANNER':
            return { type: 'AD_BANNER', imageUrl: mockBannerUrl(label, color, adType ?? 'WIDE_BANNER'), linkUrl: `https://shop.example.com/promo/${encodeURIComponent(label.slice(0, 6))}`, altText: label };
        case 'AD_TEXT_LINE':
            return { type: 'AD_TEXT_LINE', text: label, linkUrl: 'https://shop.example.com/event' };
        case 'HOTDEAL_POST':
            return { type: 'HOTDEAL_POST', title: label, body: '한정 수량 특가입니다. 쿠폰 적용 시 추가 5% 할인되며, 무료배송(제주·도서 산간 제외)입니다.', mallName: '자사몰', dealUrl: 'https://shop.example.com/deal/123', price: 49_000, originalPrice: 89_000 };
        case 'HOTDEAL_PLACEMENT':
            return { type: 'HOTDEAL_PLACEMENT', postId: 'post-7781', postTitle: label, price: 12_900, originalPrice: 25_000, mallName: '스마트스토어', categoryName: adType === 'HOTDEAL_CATEGORY' ? '가전' : null };
    }
}

interface Seed {
    kind: BizApplicationKind;
    adType: BizAdType | null;
    biz: BizKey;
    period: BizPeriod;
    status: BizApplicationStatus;
    method: BizPaymentMethod;
    label: string;
    color?: string;
    submittedAgo: number;
    deadline?: number;
    contracts?: number;
    deposit?: boolean;
    refund?: boolean;
    rejection?: { code: BizRejectCode; text: string };
    adjust?: number;
}

function seeds(): Seed[] {
    return [
        { kind: 'AD_BANNER', adType: 'WIDE_BANNER', biz: 'camping', period: span(5, 12), status: 'SUBMITTED', method: 'ONLINE', label: '캠핑하우스 가을 침낭 기획전', color: '#7c3aed', submittedAgo: 0.3 * DAY },
        { kind: 'AD_TEXT_LINE', adType: 'TEXT_LINE', biz: 'banchan', period: span(3, 17), status: 'SUBMITTED', method: 'MANUAL', label: '[오늘의 반찬] 김장 김치 사전예약 10% 할인', submittedAgo: 0.8 * DAY },
        { kind: 'HOTDEAL_POST', adType: null, biz: 'camping', period: span(1, 8), status: 'SUBMITTED', method: 'ONLINE', label: '원터치 텐트 4인용 49,000원 (45% 할인)', submittedAgo: 1.2 * DAY },
        { kind: 'HOTDEAL_PLACEMENT', adType: 'HOTDEAL_TOP', biz: 'plant', period: span(2, 9), status: 'SUBMITTED', method: 'ONLINE', label: '스마트 화분 12,900원 무료배송', submittedAgo: 1.9 * DAY },
        { kind: 'AD_BANNER', adType: 'SQUARE_BANNER', biz: 'banchan', period: span(-1, 6), status: 'SUBMITTED', method: 'ONLINE', label: '반찬 정기구독 첫 달 무료', color: '#ea580c', submittedAgo: 3.1 * DAY },
        { kind: 'AD_BANNER', adType: 'WIDE_BANNER', biz: 'plant', period: span(1, 4), status: 'SUBMITTED', method: 'MANUAL', label: '디지털플랜트 신제품 출시', color: '#059669', submittedAgo: 0.1 * DAY },
        { kind: 'AD_BANNER', adType: 'STRIP_BANNER', biz: 'plant', period: span(7, 14), status: 'AWAITING_PAYMENT', method: 'ONLINE', label: '물 주기 알림 화분', color: '#0f766e', submittedAgo: 2 * DAY, deadline: 50 * HOUR },
        { kind: 'HOTDEAL_PLACEMENT', adType: 'HOTDEAL_CATEGORY', biz: 'banchan', period: span(3, 10), status: 'AWAITING_PAYMENT', method: 'ONLINE', label: '반찬 8종 세트 19,900원', submittedAgo: 3 * DAY, deadline: 10 * HOUR },
        { kind: 'AD_BANNER', adType: 'SMALL_BANNER', biz: 'camping', period: span(9, 23), status: 'AWAITING_DEPOSIT', method: 'MANUAL', label: '캠핑의자 2+1', color: '#6d28d9', submittedAgo: 2 * DAY, deadline: 5 * DAY, contracts: 0, adjust: 180_000 },
        { kind: 'AD_TEXT_LINE', adType: 'TEXT_LINE', biz: 'plant', period: span(4, 18), status: 'AWAITING_DEPOSIT', method: 'MANUAL', label: '[디지털플랜트] 물 주기 알림 화분 1+1', submittedAgo: 4 * DAY, deadline: 3 * DAY, contracts: 2 },
        { kind: 'AD_BANNER', adType: 'SQUARE_BANNER', biz: 'banchan', period: span(2, 9), status: 'AWAITING_DEPOSIT', method: 'MANUAL', label: '반찬가게 추석 선물세트', color: '#b45309', submittedAgo: 10 * DAY, deadline: -1 * DAY, contracts: 1 },
        { kind: 'AD_BANNER', adType: 'SMALL_BANNER', biz: 'banchan', period: span(6, 13), status: 'SCHEDULED', method: 'ONLINE', label: '반찬 무료배송 주간', color: '#c2410c', submittedAgo: 6 * DAY },
        { kind: 'HOTDEAL_POST', adType: null, biz: 'plant', period: span(2, 9), status: 'SCHEDULED', method: 'MANUAL', label: '미니 가습기 12,900원', submittedAgo: 7 * DAY, contracts: 1, deposit: true },
        { kind: 'AD_TEXT_LINE', adType: 'TEXT_LINE', biz: 'camping', period: span(-3, 4), status: 'SCHEDULED', method: 'ONLINE', label: '[캠핑하우스] 랜턴 최대 50%', submittedAgo: 9 * DAY },
        { kind: 'AD_BANNER', adType: 'WIDE_BANNER', biz: 'bigsale', period: span(-3, 7), status: 'TAKEN_DOWN', method: 'ONLINE', label: '빅세일 전품목 90%', color: '#dc2626', submittedAgo: 12 * DAY },
        { kind: 'AD_BANNER', adType: 'STRIP_BANNER', biz: 'camping', period: span(-4, 10), status: 'SCHEDULED', method: 'ONLINE', label: '캠핑하우스 겨울 준비', color: '#4338ca', submittedAgo: 11 * DAY, refund: true },
        { kind: 'HOTDEAL_POST', adType: null, biz: 'banchan', period: span(-20, -13), status: 'SCHEDULED', method: 'ONLINE', label: '반찬 4종 9,900원', submittedAgo: 25 * DAY },
        { kind: 'AD_BANNER', adType: 'SMALL_BANNER', biz: 'camping', period: span(10, 17), status: 'REJECTED', method: 'ONLINE', label: '최저가 보장 100%', color: '#be123c', submittedAgo: 5 * DAY, rejection: { code: 'FALSE_PRICE', text: '「최저가 보장」 근거를 확인할 수 없어요. 표현을 바꿔 다시 신청해 주세요.' } },
        { kind: 'AD_TEXT_LINE', adType: 'TEXT_LINE', biz: 'plant', period: span(8, 15), status: 'CANCELED', method: 'ONLINE', label: '[디지털플랜트] 블랙프라이데이', submittedAgo: 6 * DAY },
        { kind: 'HOTDEAL_PLACEMENT', adType: 'HOTDEAL_TOP', biz: 'camping', period: span(5, 12), status: 'EXPIRED', method: 'ONLINE', label: '버너 세트 29,000원', submittedAgo: 8 * DAY },
    ];
}

let state: BizApplicationDetail[] | null = null;
let seq = 500;

function contractFile(n: number, daysAgo: number, replaced: boolean) {
    return { id: `bcf-${++seq}`, fileName: `광고계약서_${n}.pdf`, mimeType: 'application/pdf', size: 640_000 + seq * 10, uploadedAt: ago(daysAgo * DAY), uploadedBy: ME, replacedAt: replaced ? ago((daysAgo - 0.5) * DAY) : null, replacedBy: replaced ? ME : null };
}

function build(s: Seed, i: number): BizApplicationDetail {
    const b = BUSINESSES[s.biz];
    const submittedAt = ago(s.submittedAgo);
    const quote = UtilBizApplication.quote(s.kind, s.adType, s.period);
    const approved = s.status !== 'SUBMITTED' && s.status !== 'REJECTED' && !(s.status === 'CANCELED' && !s.deadline);
    const amount = UtilBizApplication.amountOf(s.adjust ?? quote.supply);
    const paid = s.status === 'SCHEDULED' || s.status === 'TAKEN_DOWN';
    const slot = s.adType ? mockAdSlotState(s.adType, s.period) : { remaining: 1, limit: 1 };
    const history: BizApplicationDetail['history'] = [{ at: submittedAt, action: '신청 접수', actor: b.applicant, note: null }];
    if (approved || s.status === 'EXPIRED') history.push({ at: ago(s.submittedAgo - 0.2 * DAY), action: s.method === 'ONLINE' ? '승인 · 온라인 결제 요청' : '승인 · 수동 결제(계약·입금)', actor: ME, note: s.adjust ? '장기 계약 할인' : null });
    if (paid) history.push({ at: ago(s.submittedAgo - 0.5 * DAY), action: s.method === 'ONLINE' ? '결제 완료 · 예약 확정' : '입금 확인 · 예약 확정', actor: s.method === 'ONLINE' ? '시스템' : ME, note: null });
    if (s.status === 'REJECTED') history.push({ at: ago(s.submittedAgo - 0.3 * DAY), action: '반려', actor: ME, note: s.rejection?.text ?? null });
    if (s.status === 'CANCELED') history.push({ at: ago(s.submittedAgo - 0.3 * DAY), action: '사업자 취소', actor: b.applicant, note: null });
    if (s.status === 'EXPIRED') history.push({ at: ago(s.submittedAgo - 3.2 * DAY), action: '결제 기한 지남 · 자동 취소 · 자리 반납', actor: '시스템', note: null });
    if (s.status === 'TAKEN_DOWN') history.push({ at: ago(DAY), action: '긴급 내림(사업자 정지)', actor: '최고 관리자', note: '허위 할인율 표기 반복' });
    const totalDays = UtilBizApplication.days(s.period);
    const usedDays = Math.max(0, Math.ceil((Date.now() - Date.parse(s.period.startAt)) / DAY));
    return {
        id: `ba-${i + 1}`,
        no: `BA-2026-${String(101 + i).padStart(4, '0')}`,
        kind: s.kind,
        adType: s.adType,
        status: s.status,
        business: { id: b.id, name: b.name, maskedRegNo: b.maskedRegNo },
        submittedAt,
        period: s.period,
        preferredMethod: s.method,
        total: approved ? amount.total : quote.total,
        deadlineAt: s.deadline !== undefined ? ahead(s.deadline) : null,
        hasRefundRequest: Boolean(s.refund),
        applicant: b.applicant,
        content: content(s.kind, s.adType, s.label, s.color ?? '#475569'),
        memo: i === 0 ? '홈 상단 첫 화면 노출을 원해요.' : null,
        quote,
        remainingSlots: slot.remaining,
        slotLimit: slot.limit,
        approval: approved || s.status === 'EXPIRED' ? { method: s.method, amount, adjustReason: s.adjust ? '장기 계약 할인(14일 이상 10%)' : null, approvedAt: ago(s.submittedAgo - 0.2 * DAY), approvedBy: ME } : null,
        onlinePayment: paid && s.method === 'ONLINE' ? { paidAt: ago(s.submittedAgo - 0.5 * DAY), pgTxId: `pay_mock_${2000 + i}` } : null,
        contracts: Array.from({ length: s.contracts ?? 0 }, (_, n) => contractFile(n + 1, s.submittedAgo / DAY - 0.3, n < (s.contracts ?? 0) - 1)),
        deposit: s.deposit ? { amount: amount.total, depositedOn: UtilBizApplication.kstDate(ago(s.submittedAgo - 0.4 * DAY)), depositorName: b.name, mismatchReason: null, confirmedBy: ME, confirmedAt: ago(s.submittedAgo - 0.4 * DAY) } : null,
        refund: s.refund ? { requestedAt: ago(5 * HOUR), reason: '매장 리뉴얼로 남은 기간 광고를 중단하고 싶어요.', paidAmount: amount.total, proposedAmount: UtilBizApplication.prorate(amount.total, usedDays, totalDays), usedDays, totalDays, status: 'PENDING' } : null,
        rejection: s.rejection ?? null,
        policyAgreedVersion: '광고 정책 v1 · 환불 규정 v1',
        history,
    };
}

function ensure(): BizApplicationDetail[] {
    if (state) return state;
    state = mockFlags().has('bizapp-empty') ? [] : seeds().map(build);
    return state;
}

/** 보이는 상태 — 예약은 시각으로 진행·종료를 계산한다 */
function shown(d: BizApplicationDetail, now = Date.now()): BizApplicationStatus {
    if (d.status !== 'SCHEDULED') return d.status;
    if (Date.parse(d.period.endAt) <= now) return 'ENDED';
    if (Date.parse(d.period.startAt) <= now) return 'RUNNING';
    return 'SCHEDULED';
}

function queueOf(d: BizApplicationDetail, now = Date.now()): BizApplicationQueue {
    const s = shown(d, now);
    if (d.refund?.status === 'PENDING') return 'REFUND';
    if (s === 'SUBMITTED') return 'REVIEW';
    if (s === 'AWAITING_PAYMENT') return 'PAYMENT';
    if (s === 'AWAITING_DEPOSIT') return d.deadlineAt && Date.parse(d.deadlineAt) < now ? 'DEPOSIT_OVERDUE' : 'DEPOSIT';
    if (s === 'SCHEDULED' || s === 'RUNNING' || s === 'TAKEN_DOWN') return 'LIVE';
    return 'CLOSED';
}

function rowOf(d: BizApplicationDetail): BizApplicationRow {
    return { id: d.id, no: d.no, kind: d.kind, adType: d.adType, status: shown(d), business: d.business, submittedAt: d.submittedAt, period: d.period, preferredMethod: d.preferredMethod, total: d.total, deadlineAt: d.deadlineAt, hasRefundRequest: d.refund?.status === 'PENDING' };
}

function out(d: BizApplicationDetail): BizApplicationDetail {
    if (d.adType && d.status === 'SUBMITTED') {
        const slot = mockAdSlotState(d.adType, d.period);
        d.remainingSlots = slot.remaining;
        d.slotLimit = slot.limit;
    }
    return mockClone({ ...d, status: shown(d), hasRefundRequest: d.refund?.status === 'PENDING' });
}

function find(id: string): BizApplicationDetail {
    const d = ensure().find((x) => x.id === id);
    if (!d) throw MOCK_ERRORS.notFound('BIZ_APPLICATION_NOT_FOUND', '신청 건을 찾을 수 없어요.');
    return d;
}

function stale(): never {
    throw MOCK_ERRORS.conflict('BIZ_APPLICATION_STATE_CHANGED', '이미 바뀌었어요. 새로 불러올게요.');
}

function log(d: BizApplicationDetail, action: string, note: string | null = null, actor = ME): void {
    d.history.push({ at: new Date().toISOString(), action, actor, note });
}

/** 광고 자리를 잡는 건이면 광고 일정표 장부에 반영 */
function sync(d: BizApplicationDetail, note: string): void {
    if (!d.adType || !d.approval) return;
    const map: Partial<Record<BizApplicationStatus, AdBookingStatus>> = { AWAITING_PAYMENT: 'AWAITING_PAYMENT', AWAITING_DEPOSIT: 'AWAITING_DEPOSIT', SCHEDULED: 'SCHEDULED', CANCELED: 'CANCELED', EXPIRED: 'CANCELED' };
    const status = map[d.status];
    if (!status) return;
    const c = d.content;
    mockAdSyncFromApplication({
        no: d.no,
        type: d.adType,
        businessId: d.business.id,
        businessName: d.business.name,
        maskedRegNo: d.business.maskedRegNo,
        period: d.period,
        status,
        method: d.approval.method,
        amount: d.approval.amount,
        imageUrl: c.type === 'AD_BANNER' ? c.imageUrl : null,
        text: c.type === 'AD_TEXT_LINE' ? c.text : c.type === 'HOTDEAL_PLACEMENT' ? c.postTitle : null,
        linkUrl: c.type === 'AD_BANNER' || c.type === 'AD_TEXT_LINE' ? c.linkUrl : `/deal/${c.type === 'HOTDEAL_PLACEMENT' ? c.postId : ''}`,
        contracts: d.contracts,
        deposit: d.deposit,
        paidAt: d.onlinePayment?.paidAt ?? null,
        note: `${d.no} ${note}`,
    });
}

export async function mockBizAppList(query: BizApplicationQuery): Promise<BizApplicationList> {
    await mockDelay();
    if (mockFlags().has('bizapp-fail')) throw MOCK_ERRORS.internal();
    const q = query.q?.trim().toLowerCase();
    const scoped = ensure().filter((d) => (!query.kind || d.kind === query.kind) && (!q || `${d.no} ${d.business.name}`.toLowerCase().includes(q)));
    const counts = Object.fromEntries(BIZ_APPLICATION_QUEUES.map((k) => [k, scoped.filter((d) => queueOf(d) === k).length])) as Record<BizApplicationQueue, number>;
    const items = scoped.filter((d) => queueOf(d) === query.queue).map(rowOf);
    if (query.queue === 'REVIEW') items.sort((a, b) => a.submittedAt.localeCompare(b.submittedAt));
    else if (query.queue === 'PAYMENT' || query.queue === 'DEPOSIT' || query.queue === 'DEPOSIT_OVERDUE') items.sort((a, b) => (a.deadlineAt ?? '').localeCompare(b.deadlineAt ?? ''));
    else items.sort((a, b) => b.submittedAt.localeCompare(a.submittedAt));
    return mockClone({ items, counts });
}

export async function mockBizAppDetail(id: string): Promise<BizApplicationDetail> {
    await mockDelay();
    return out(find(id));
}

export async function mockBizAppAvailability(id: string, period: BizPeriod): Promise<BizAvailability> {
    await mockDelay();
    const d = find(id);
    const slot = d.adType ? mockAdSlotState(d.adType, period) : { remaining: 1, limit: 1 };
    return { quote: UtilBizApplication.quote(d.kind, d.adType, period), remainingSlots: slot.remaining, slotLimit: slot.limit };
}

export async function mockBizAppApprove(id: string, body: BizApproveRequest): Promise<BizApplicationDetail> {
    await mockDelay();
    const d = find(id);
    if (d.status !== 'SUBMITTED') stale();
    if (UtilBizApplication.started(body.period)) throw MOCK_ERRORS.validation({ period: '시작 시각이 이미 지났어요. 기간을 뒤로 옮겨 주세요.' });
    const quote = UtilBizApplication.quote(d.kind, d.adType, body.period);
    if (body.supply !== quote.supply && !body.adjustReason?.trim()) throw MOCK_ERRORS.validation({ adjustReason: '요금표와 다른 금액이면 조정 사유를 적어 주세요.' });
    if (d.adType && mockAdSlotState(d.adType, body.period).remaining <= 0) throw MOCK_ERRORS.conflict('AD_SLOT_FULL', '그 사이 자리가 찼어요. 기간을 바꿔 주세요.');
    const moved = body.period.startAt !== d.period.startAt;
    d.period = { ...body.period };
    d.status = body.method === 'ONLINE' ? 'AWAITING_PAYMENT' : 'AWAITING_DEPOSIT';
    d.deadlineAt = body.deadlineAt;
    const amount = UtilBizApplication.amountOf(body.supply);
    d.approval = { method: body.method, amount, adjustReason: body.adjustReason?.trim() || null, approvedAt: new Date().toISOString(), approvedBy: ME };
    d.total = amount.total;
    d.quote = quote;
    log(d, body.method === 'ONLINE' ? '승인 · 온라인 결제 요청(사업자 알림)' : '승인 · 수동 결제(계약·입금)', [moved ? '기간을 같은 길이로 뒤로 옮김(사업자 알림)' : '', body.adjustReason ?? ''].filter(Boolean).join(' · ') || null);
    sync(d, '승인');
    return out(d);
}

export async function mockBizAppReject(id: string, body: { code: BizRejectCode; text: string }): Promise<BizApplicationDetail> {
    await mockDelay();
    const d = find(id);
    if (d.status !== 'SUBMITTED') stale();
    d.status = 'REJECTED';
    d.rejection = { ...body };
    log(d, '반려(사업자 알림)', body.text);
    return out(d);
}

export async function mockBizAppSwitchToManual(id: string, body: { deadlineAt: string; reason: string }): Promise<BizApplicationDetail> {
    await mockDelay();
    const d = find(id);
    if (d.status !== 'AWAITING_PAYMENT' || !d.approval) stale();
    d.status = 'AWAITING_DEPOSIT';
    d.deadlineAt = body.deadlineAt;
    d.approval.method = 'MANUAL';
    log(d, '수동 결제로 전환', body.reason);
    sync(d, '수동 결제로 전환');
    return out(d);
}

/** 목업 전용 — 사업자가 온라인 결제를 마친 것처럼(실제 PG 호출 없음) */
export async function mockBizAppMockPay(id: string): Promise<BizApplicationDetail> {
    await mockDelay();
    const d = find(id);
    if (d.status !== 'AWAITING_PAYMENT') stale();
    d.status = 'SCHEDULED';
    d.onlinePayment = { paidAt: new Date().toISOString(), pgTxId: `pay_mock_${Date.now()}` };
    log(d, '결제 완료 · 예약 확정', null, '시스템');
    sync(d, '결제 완료');
    return out(d);
}

export async function mockBizAppUploadContracts(id: string, files: File[], replaceId?: string): Promise<BizApplicationDetail> {
    await mockDelay();
    const d = find(id);
    if (d.status !== 'AWAITING_DEPOSIT') stale();
    const bad = files.find((f) => UtilBizApplication.contractProblem(f));
    if (bad) throw MOCK_ERRORS.validation({ file: `${bad.name}: PDF·JPG·PNG 파일(한 장 10MB 이하)만 올릴 수 있어요.` });
    const now = new Date().toISOString();
    if (replaceId) {
        const old = d.contracts.find((c) => c.id === replaceId && !c.replacedAt);
        if (!old) stale();
        old.replacedAt = now;
        old.replacedBy = ME;
    }
    for (const f of files) d.contracts.push({ id: `bcf-${++seq}`, fileName: f.name, mimeType: f.type, size: f.size, uploadedAt: now, uploadedBy: ME, replacedAt: null, replacedBy: null });
    log(d, replaceId ? '계약서 사본 교체(옛 파일 보존)' : `계약서 사본 ${files.length}장 올림`);
    return out(d);
}

export async function mockBizAppSaveDeposit(id: string, body: BizDepositRequest): Promise<BizApplicationDetail> {
    await mockDelay();
    const d = find(id);
    if (d.status !== 'AWAITING_DEPOSIT' || !d.approval) stale();
    const fields: Record<string, string> = {};
    if (!(body.amount > 0)) fields.amount = '입금액을 입력해 주세요.';
    if (!body.depositedOn) fields.depositedOn = '입금일을 골라 주세요.';
    if (!body.depositorName.trim()) fields.depositorName = '입금자명을 입력해 주세요.';
    if (Object.keys(fields).length) throw MOCK_ERRORS.validation(fields);
    d.deposit = { amount: body.amount, depositedOn: body.depositedOn, depositorName: body.depositorName.trim(), mismatchReason: body.mismatchReason?.trim() || null, confirmedBy: ME, confirmedAt: new Date().toISOString() };
    log(d, '입금 확인 기록', body.amount !== d.approval.amount.total ? `계약 금액과 다름: ${body.mismatchReason ?? ''}` : null);
    return out(d);
}

export async function mockBizAppConfirmBooking(id: string): Promise<BizApplicationDetail> {
    await mockDelay();
    const d = find(id);
    if (d.status !== 'AWAITING_DEPOSIT' || !d.approval) stale();
    if (!d.contracts.some((c) => !c.replacedAt) || !d.deposit) throw MOCK_ERRORS.conflict('BIZ_DEPOSIT_INCOMPLETE', '계약서 사본과 입금 확인이 모두 있어야 예약을 확정할 수 있어요.');
    if (d.deposit.amount !== d.approval.amount.total && !d.deposit.mismatchReason) throw MOCK_ERRORS.validation({ mismatchReason: '입금액이 계약 금액과 달라요. 사유를 적어 주세요.' });
    d.status = 'SCHEDULED';
    log(d, '예약 확정(입금 확인)');
    sync(d, '예약 확정');
    return out(d);
}

export async function mockBizAppExtendDeposit(id: string, deadlineAt: string): Promise<BizApplicationDetail> {
    await mockDelay();
    const d = find(id);
    if (d.status !== 'AWAITING_DEPOSIT') stale();
    d.deadlineAt = deadlineAt;
    log(d, '입금 기한 연장', UtilBizApplication.kstDate(deadlineAt));
    return out(d);
}

export async function mockBizAppCancel(id: string, reason: string): Promise<BizApplicationDetail> {
    await mockDelay();
    const d = find(id);
    if (d.status !== 'AWAITING_DEPOSIT' && d.status !== 'AWAITING_PAYMENT') stale();
    d.status = 'CANCELED';
    log(d, '운영자 취소 · 자리 반납', reason);
    sync(d, '운영자 취소');
    return out(d);
}

export async function mockBizAppApproveRefund(id: string, body: { amount: number; reason: string }): Promise<BizApplicationDetail> {
    await mockDelay();
    const d = find(id);
    if (!d.refund || d.refund.status !== 'PENDING') stale();
    if (body.amount < 0 || body.amount > d.refund.paidAmount) throw MOCK_ERRORS.validation({ amount: '환불 금액은 0원 이상, 결제 금액 이하여야 해요.' });
    d.refund.status = 'APPROVED';
    d.status = 'CANCELED';
    log(d, `환불 승인 ${body.amount.toLocaleString('ko-KR')}원 · 노출 중단`, body.reason);
    sync(d, '환불 승인');
    return out(d);
}

export async function mockBizAppDenyRefund(id: string, reason: string): Promise<BizApplicationDetail> {
    await mockDelay();
    const d = find(id);
    if (!d.refund || d.refund.status !== 'PENDING') stale();
    d.refund.status = 'DENIED';
    log(d, '환불 거절(사업자 알림)', reason);
    return out(d);
}
