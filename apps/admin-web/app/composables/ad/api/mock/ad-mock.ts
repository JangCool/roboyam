// MOCK: 광고 일정표 API 목업 — 서버가 붙으면 이 폴더와 `useAdApi.ts` 의 `// MOCK:` 줄을 지운다.
// 값은 메모리에만 둔다(새로고침하면 처음으로). 자리(한도) 장부는 이 목업 안에서만 센다 — 사업자 신청 건은 실제 서버(V112)라 이 장부에 반영되지 않는다.
// 스위치(`?mock=`): ads-fail(목록 500) · ads-empty(광고 건 없음)
// 문구(서버 몫)는 번역하지 않는다(i18n.md 5절).

import type {
    AdAdvertiser,
    AdAdvertiserCreateRequest,
    AdAvailability,
    AdBookingDetail,
    AdBookingPage,
    AdBookingQuery,
    AdBookingRow,
    AdBookingStatus,
    AdCalendar,
    AdDaySlot,
    AdManualCreateRequest,
    AdRestoreRequest,
    AdTakedownRequest,
    AdType,
} from '~/composables/ad/api/useAdApi.types';
import type { BizContractFile, BizPaymentMethod, BizPeriod } from '~/composables/bizapplication/api/useBizApplicationApi.types';
import { MOCK_ERRORS, mockClone, mockDelay, mockFlags } from '~/composables/common/mock/mock-util';
import UtilBizApplication from '~/utils/util-biz-application';

const ME = '운영자 김민지';
const DAY_MS = 86_400_000;

/** 유형별 한도(설정값 가정 — 02-ad.md X8: 핫딜 홈 상단 3 · 말머리 상단 2) */
export const MOCK_AD_LIMITS: Record<AdType, number> = { WIDE_BANNER: 2, STRIP_BANNER: 3, SQUARE_BANNER: 4, SMALL_BANNER: 6, TEXT_LINE: 10, HOTDEAL_TOP: 3, HOTDEAL_CATEGORY: 2 };

const SIZES: Partial<Record<AdType, [number, number]>> = { WIDE_BANNER: [728, 120], STRIP_BANNER: [728, 60], SQUARE_BANNER: [300, 250], SMALL_BANNER: [200, 120] };

/** 배너 그림(목업) — 색 바탕 + 글자 */
export function mockBannerUrl(text: string, color: string, type: AdType = 'WIDE_BANNER'): string {
    const [w, h] = SIZES[type] ?? [728, 120];
    const svg = `<svg xmlns="http://www.w3.org/2000/svg" width="${w}" height="${h}" viewBox="0 0 ${w} ${h}"><rect width="${w}" height="${h}" fill="${color}"/><text x="${w / 2}" y="${h / 2 + 7}" font-size="${Math.min(28, h / 3)}" fill="#fff" text-anchor="middle" font-family="sans-serif" font-weight="bold">${text}</text></svg>`;
    return `data:image/svg+xml;charset=utf-8,${encodeURIComponent(svg)}`;
}

const today = () => UtilBizApplication.kstToday();
const at = (offsetDays: number) => UtilBizApplication.kstMidnight(UtilBizApplication.addDays(today(), offsetDays));
const span = (from: number, to: number): BizPeriod => ({ startAt: at(from), endAt: at(to) });
const ago = (ms: number) => new Date(Date.now() - ms).toISOString();

const ADVERTISERS: AdAdvertiser[] = [
    { id: 'adv-biz-5', kind: 'BUSINESS', name: '오늘의 반찬', businessId: 'biz-5', maskedRegNo: '211-**-***87', contactMasked: '010-****-**12' },
    { id: 'adv-biz-6', kind: 'BUSINESS', name: '캠핑하우스', businessId: 'biz-6', maskedRegNo: '608-**-***15', contactMasked: '010-****-**34' },
    { id: 'adv-biz-7', kind: 'BUSINESS', name: '디지털플랜트', businessId: 'biz-7', maskedRegNo: '129-**-***40', contactMasked: '010-****-**56' },
    { id: 'adv-biz-9', kind: 'BUSINESS', name: '빅세일 코리아', businessId: 'biz-9', maskedRegNo: '135-**-***33', contactMasked: '010-****-**78' },
    { id: 'adv-1', kind: 'ADVERTISER', name: '한빛손해보험', businessId: null, maskedRegNo: null, contactMasked: '02-****-**00' },
    { id: 'adv-2', kind: 'ADVERTISER', name: '그린에너지 캠페인', businessId: null, maskedRegNo: null, contactMasked: 'gr***@****.kr' },
];

interface Stored {
    detail: AdBookingDetail;
    /** 저장 상태 — SCHEDULED 는 읽을 때 시각으로 RUNNING·ENDED 를 계산한다 */
    stored: AdBookingStatus;
}

let state: Stored[] | null = null;
let advertisers: AdAdvertiser[] = [];
let seq = 100;

function contract(name: string, daysAgo: number, replaced = false): BizContractFile {
    return { id: `cf-${++seq}`, fileName: name, mimeType: name.endsWith('.pdf') ? 'application/pdf' : 'image/jpeg', size: 820_000 + seq * 100, uploadedAt: ago(daysAgo * DAY_MS), uploadedBy: ME, replacedAt: replaced ? ago((daysAgo - 1) * DAY_MS) : null, replacedBy: replaced ? ME : null };
}

interface Seed {
    type: AdType;
    adv: string;
    period: BizPeriod;
    stored: AdBookingStatus;
    method: BizPaymentMethod;
    source: 'APPLICATION' | 'OPERATOR';
    color: string;
    text: string;
    weight?: number;
    noContract?: boolean;
    versions?: number;
}

function seeds(): Seed[] {
    return [
        { type: 'WIDE_BANNER', adv: 'adv-biz-5', period: span(-5, 5), stored: 'SCHEDULED', method: 'ONLINE', source: 'APPLICATION', color: '#d97706', text: '오늘의 반찬 첫 주문 30% 할인', versions: 2 },
        { type: 'WIDE_BANNER', adv: 'adv-1', period: span(-2, 3), stored: 'SCHEDULED', method: 'MANUAL', source: 'OPERATOR', color: '#1d4ed8', text: '한빛 다이렉트 자동차보험' },
        { type: 'WIDE_BANNER', adv: 'adv-biz-7', period: span(3, 10), stored: 'SCHEDULED', method: 'MANUAL', source: 'APPLICATION', color: '#059669', text: '디지털플랜트 스마트 화분' },
        { type: 'WIDE_BANNER', adv: 'adv-biz-6', period: span(12, 19), stored: 'AWAITING_PAYMENT', method: 'ONLINE', source: 'APPLICATION', color: '#7c3aed', text: '캠핑하우스 가을 텐트전' },
        { type: 'WIDE_BANNER', adv: 'adv-biz-9', period: span(-3, 7), stored: 'TAKEN_DOWN', method: 'ONLINE', source: 'APPLICATION', color: '#dc2626', text: '빅세일 전품목 90%' },
        { type: 'WIDE_BANNER', adv: 'adv-2', period: span(-20, -10), stored: 'SCHEDULED', method: 'MANUAL', source: 'OPERATOR', color: '#16a34a', text: '에너지 절약 캠페인' },
        { type: 'STRIP_BANNER', adv: 'adv-biz-5', period: span(-1, 13), stored: 'SCHEDULED', method: 'ONLINE', source: 'APPLICATION', color: '#ea580c', text: '반찬 정기배송' },
        { type: 'STRIP_BANNER', adv: 'adv-2', period: span(6, 20), stored: 'AWAITING_DEPOSIT', method: 'MANUAL', source: 'OPERATOR', color: '#15803d', text: '그린에너지 캠페인', noContract: true },
        { type: 'SQUARE_BANNER', adv: 'adv-biz-7', period: span(-7, 7), stored: 'SCHEDULED', method: 'MANUAL', source: 'APPLICATION', color: '#0f766e', text: '플랜트 정기구독' },
        { type: 'SMALL_BANNER', adv: 'adv-biz-6', period: span(1, 8), stored: 'SCHEDULED', method: 'ONLINE', source: 'APPLICATION', color: '#6d28d9', text: '캠핑의자 특가' },
        { type: 'TEXT_LINE', adv: 'adv-biz-5', period: span(-4, 10), stored: 'SCHEDULED', method: 'ONLINE', source: 'APPLICATION', color: '', text: '[반찬가게] 오늘 주문하면 내일 아침 도착' },
        { type: 'TEXT_LINE', adv: 'adv-biz-7', period: span(2, 16), stored: 'SCHEDULED', method: 'ONLINE', source: 'APPLICATION', color: '', text: '[디지털플랜트] 물 주기 알림 화분 1+1' },
        { type: 'HOTDEAL_TOP', adv: 'adv-biz-6', period: span(-1, 6), stored: 'SCHEDULED', method: 'ONLINE', source: 'APPLICATION', color: '', text: '캠핑하우스 원터치 텐트 49,000원' },
        { type: 'HOTDEAL_TOP', adv: 'adv-biz-5', period: span(4, 11), stored: 'AWAITING_PAYMENT', method: 'ONLINE', source: 'APPLICATION', color: '', text: '반찬 8종 세트 19,900원' },
        { type: 'HOTDEAL_CATEGORY', adv: 'adv-biz-7', period: span(0, 7), stored: 'SCHEDULED', method: 'ONLINE', source: 'APPLICATION', color: '', text: '[가전] 미니 가습기 12,900원' },
    ];
}

function build(s: Seed, i: number): Stored {
    const advertiser = advertisers.find((a) => a.id === s.adv)!;
    const isText = !s.color;
    const created = Date.parse(s.period.startAt) - 6 * DAY_MS;
    const versions = s.versions ?? 1;
    const amount = UtilBizApplication.amountOf(UtilBizApplication.quote(s.type === 'HOTDEAL_TOP' || s.type === 'HOTDEAL_CATEGORY' ? 'HOTDEAL_PLACEMENT' : 'AD_BANNER', s.type, s.period).supply);
    const paid = s.stored !== 'AWAITING_PAYMENT' && s.stored !== 'AWAITING_DEPOSIT';
    const detail: AdBookingDetail = {
        id: `ad-${i + 1}`,
        type: s.type,
        advertiser,
        period: s.period,
        status: s.stored,
        paymentMethod: s.method,
        weight: s.weight ?? 5,
        source: s.source,
        applicationNo: s.source === 'APPLICATION' ? `BA-2026-${String(80 + i).padStart(4, '0')}` : null,
        lastActionAt: new Date(created + DAY_MS).toISOString(),
        amount,
        creatives: Array.from({ length: versions }, (_, v) => ({
            version: v + 1,
            imageUrl: isText ? null : mockBannerUrl(v === versions - 1 ? s.text : `${s.text} (이전)`, s.color, s.type),
            text: isText ? s.text : null,
            linkUrl: `https://shop.example.com/${s.adv}/promo${v + 1}`,
            altText: isText ? null : s.text,
            approvedAt: new Date(created + v * 2 * DAY_MS).toISOString(),
            approvedBy: ME,
            current: v === versions - 1,
        })),
        onlinePayment: s.method === 'ONLINE' && paid ? { paidAt: new Date(created + 2 * 3_600_000).toISOString(), pgTxId: `pay_mock_${1000 + i}` } : null,
        contracts: s.method === 'MANUAL' && !s.noContract ? (i % 2 ? [contract('광고계약서_v1.pdf', 9, true), contract('광고계약서_v2.pdf', 8)] : [contract('광고계약서.pdf', 8)]) : [],
        deposit: s.method === 'MANUAL' && paid ? { amount: amount.total, depositedOn: UtilBizApplication.kstDate(new Date(created + DAY_MS).toISOString()), depositorName: advertiser.name, mismatchReason: null, confirmedBy: ME, confirmedAt: new Date(created + DAY_MS).toISOString() } : null,
        takedown: s.stored === 'TAKEN_DOWN' ? { at: ago(DAY_MS), reason: '허위 할인율 표기 — 신고 다수', by: '최고 관리자', mailSent: true } : null,
        memo: null,
        history: [
            { at: new Date(created).toISOString(), action: s.source === 'APPLICATION' ? '신청 승인으로 생성' : '운영자 수동 등록', actor: ME, note: null },
            ...(paid ? [{ at: new Date(created + DAY_MS).toISOString(), action: s.method === 'ONLINE' ? '결제 완료 · 예약 확정' : '입금 확인 · 예약 확정', actor: s.method === 'ONLINE' ? '시스템' : ME, note: null }] : []),
            ...(s.stored === 'TAKEN_DOWN' ? [{ at: ago(DAY_MS), action: '긴급 내림', actor: '최고 관리자', note: '허위 할인율 표기 — 신고 다수' }] : []),
        ],
    };
    return { detail, stored: s.stored };
}

function ensure(): Stored[] {
    if (state) return state;
    advertisers = mockClone(ADVERTISERS);
    state = mockFlags().has('ads-empty') ? [] : seeds().map(build);
    return state;
}

/** 지금 보이는 상태 — 예약은 시각으로 진행·종료를 계산한다 */
function shown(s: Stored, now = Date.now()): AdBookingStatus {
    if (s.stored !== 'SCHEDULED') return s.stored;
    if (Date.parse(s.detail.period.endAt) <= now) return 'ENDED';
    if (Date.parse(s.detail.period.startAt) <= now) return 'RUNNING';
    return 'SCHEDULED';
}

const OCCUPYING: readonly AdBookingStatus[] = ['AWAITING_PAYMENT', 'AWAITING_DEPOSIT', 'SCHEDULED', 'RUNNING'];

function usedOn(type: AdType, ymd: string, excludeId?: string): number {
    return ensure().filter((s) => s.detail.type === type && s.detail.id !== excludeId && OCCUPYING.includes(shown(s)) && UtilBizApplication.overlapsDay(s.detail.period, ymd)).length;
}

function datesOf(period: BizPeriod): string[] {
    const out: string[] = [];
    let d = UtilBizApplication.kstDate(period.startAt);
    while (Date.parse(UtilBizApplication.kstMidnight(d)) < Date.parse(period.endAt) && out.length < 400) {
        out.push(d);
        d = UtilBizApplication.addDays(d, 1);
    }
    return out;
}

/** 그 기간 가장 빠듯한 날의 남은 칸 */
function mockAdSlotState(type: AdType, period: BizPeriod, excludeId?: string): { remaining: number; limit: number; fullDates: string[] } {
    const limit = MOCK_AD_LIMITS[type];
    const per = datesOf(period).map((d) => ({ d, left: limit - usedOn(type, d, excludeId) }));
    return { remaining: Math.max(0, Math.min(limit, ...per.map((p) => p.left))), limit, fullDates: per.filter((p) => p.left <= 0).map((p) => p.d) };
}

function rowOf(s: Stored): AdBookingRow {
    const { amount: _a, creatives: _c, onlinePayment: _o, contracts: _k, deposit: _d, takedown: _t, memo: _m, history: _h, ...row } = s.detail;
    return { ...row, status: shown(s) };
}

function find(id: string): Stored {
    const s = ensure().find((x) => x.detail.id === id);
    if (!s) throw MOCK_ERRORS.notFound('AD_NOT_FOUND', '광고 건을 찾을 수 없어요.');
    return s;
}

function out(s: Stored): AdBookingDetail {
    return mockClone({ ...s.detail, status: shown(s) });
}

export async function mockAdCalendar(type: AdType, month: string): Promise<AdCalendar> {
    await mockDelay();
    const rows = ensure().filter((s) => s.detail.type === type);
    const first = `${month}-01`;
    const days: AdDaySlot[] = [];
    let d = first;
    const [y, m] = month.split('-').map(Number);
    const stop = `${new Date(Date.UTC(y!, m! + 1, 1)).toISOString().slice(0, 7)}-01`;
    while (d < stop) {
        days.push({ date: d, remaining: Math.max(0, MOCK_AD_LIMITS[type] - usedOn(type, d)), limit: MOCK_AD_LIMITS[type] });
        d = UtilBizApplication.addDays(d, 1);
    }
    const count = (st: AdBookingStatus[]) => rows.filter((s) => st.includes(shown(s))).length;
    return { summary: { type, limit: MOCK_AD_LIMITS[type], running: count(['RUNNING']), scheduled: count(['SCHEDULED']), awaitingPayment: count(['AWAITING_PAYMENT', 'AWAITING_DEPOSIT']) }, days };
}

export async function mockAdList(query: AdBookingQuery): Promise<AdBookingPage> {
    await mockDelay();
    if (mockFlags().has('ads-fail')) throw MOCK_ERRORS.internal();
    const q = query.q?.trim().toLowerCase();
    const list = ensure()
        .filter((s) => s.detail.type === query.type)
        .map(rowOf)
        .filter((r) => (!query.status || r.status === query.status) && (!q || r.advertiser.name.toLowerCase().includes(q) || (r.applicationNo ?? '').toLowerCase().includes(q)))
        .sort((a, b) => b.period.startAt.localeCompare(a.period.startAt));
    const start = query.page * query.size;
    return mockClone({ items: list.slice(start, start + query.size), total: list.length });
}

export async function mockAdDetail(id: string): Promise<AdBookingDetail> {
    await mockDelay();
    return out(find(id));
}

export async function mockAdAdvertisers(q: string): Promise<AdAdvertiser[]> {
    await mockDelay();
    ensure();
    const k = q.trim().toLowerCase();
    return mockClone(advertisers.filter((a) => !k || a.name.toLowerCase().includes(k) || (a.maskedRegNo ?? '').includes(k)));
}

export async function mockAdCreateAdvertiser(body: AdAdvertiserCreateRequest): Promise<AdAdvertiser> {
    await mockDelay();
    ensure();
    if (!body.name.trim()) throw MOCK_ERRORS.validation({ name: '광고주 이름을 입력해 주세요.' });
    if (advertisers.some((a) => a.name === body.name.trim())) throw MOCK_ERRORS.validation({ name: '같은 이름의 광고주가 이미 있어요.' });
    const adv: AdAdvertiser = { id: `adv-${++seq}`, kind: 'ADVERTISER', name: body.name.trim(), businessId: null, maskedRegNo: null, contactMasked: body.contactPhone ? `${body.contactPhone.slice(0, 3)}-****-**${body.contactPhone.slice(-2)}` : null };
    advertisers.push(adv);
    return mockClone(adv);
}

export async function mockAdAvailability(type: AdType, period: BizPeriod): Promise<AdAvailability> {
    await mockDelay();
    if (Date.parse(period.endAt) <= Date.parse(period.startAt)) throw MOCK_ERRORS.validation({ period: '끝 시각은 시작 시각보다 늦어야 해요.' });
    const slot = mockAdSlotState(type, period);
    return { ...slot, quote: UtilBizApplication.quote(type === 'HOTDEAL_TOP' || type === 'HOTDEAL_CATEGORY' ? 'HOTDEAL_PLACEMENT' : type === 'TEXT_LINE' ? 'AD_TEXT_LINE' : 'AD_BANNER', type, period) };
}

/** 계약서 사본 올리기(수동 등록 작성 중 — 건이 생기기 전 임시 파일) */
export async function mockAdUploadContract(file: File): Promise<BizContractFile> {
    await mockDelay();
    if (UtilBizApplication.contractProblem(file)) throw MOCK_ERRORS.validation({ file: 'PDF·JPG·PNG 파일(한 장 10MB 이하)만 올릴 수 있어요.' });
    return { id: `cf-${++seq}`, fileName: file.name, mimeType: file.type, size: file.size, uploadedAt: new Date().toISOString(), uploadedBy: ME, replacedAt: null, replacedBy: null };
}

export async function mockAdCreateManual(body: AdManualCreateRequest): Promise<AdBookingDetail> {
    await mockDelay();
    ensure();
    const adv = advertisers.find((a) => a.id === body.advertiserId);
    if (!adv) throw MOCK_ERRORS.validation({ advertiserId: '광고주를 골라 주세요.' });
    if (!body.contractFileIds.length) throw MOCK_ERRORS.validation({ contracts: '계약서 사본을 1장 이상 올려 주세요.' });
    const slot = mockAdSlotState(body.type, body.period);
    if (slot.remaining <= 0) throw MOCK_ERRORS.conflict('AD_SLOT_FULL', `이 기간에 꽉 찬 날이 있어요(${slot.fullDates.slice(0, 3).join(', ')}).`);
    const amount = UtilBizApplication.amountOf(body.supply);
    const now = new Date().toISOString();
    const s: Stored = {
        stored: 'SCHEDULED',
        detail: {
            id: `ad-${++seq}`,
            type: body.type,
            advertiser: adv,
            period: body.period,
            status: 'SCHEDULED',
            paymentMethod: 'MANUAL',
            weight: body.weight,
            source: 'OPERATOR',
            applicationNo: null,
            lastActionAt: now,
            amount,
            creatives: [{ version: 1, ...body.creative, approvedAt: now, approvedBy: ME, current: true }],
            onlinePayment: null,
            contracts: body.contractFileIds.map((id, n) => ({ id, fileName: `계약서_${n + 1}.pdf`, mimeType: 'application/pdf', size: 700_000, uploadedAt: now, uploadedBy: ME, replacedAt: null, replacedBy: null })),
            deposit: { ...body.deposit, mismatchReason: body.deposit.mismatchReason ?? null, confirmedBy: ME, confirmedAt: now },
            takedown: null,
            memo: body.memo || null,
            history: [{ at: now, action: '운영자 수동 등록 · 계약서·입금 확인 · 예약 확정', actor: ME, note: body.adjustReason ?? null }],
        },
    };
    state!.push(s);
    return out(s);
}

export async function mockAdTakedown(id: string, body: AdTakedownRequest): Promise<AdBookingDetail> {
    await mockDelay();
    const s = find(id);
    if (shown(s) !== 'RUNNING' && shown(s) !== 'SCHEDULED') throw MOCK_ERRORS.conflict('AD_STATE_CHANGED', '이미 바뀌었어요. 새로 불러올게요.');
    const now = new Date().toISOString();
    s.stored = 'TAKEN_DOWN';
    s.detail.takedown = { at: now, reason: body.reason, by: ME, mailSent: body.notifyMail };
    s.detail.lastActionAt = now;
    s.detail.history.push({ at: now, action: body.notifyMail ? '긴급 내림 · 광고주 메일' : '긴급 내림', actor: ME, note: body.reason });
    return out(s);
}

export async function mockAdRestore(id: string, body: AdRestoreRequest): Promise<AdBookingDetail> {
    await mockDelay();
    const s = find(id);
    if (s.stored !== 'TAKEN_DOWN' || !s.detail.takedown) throw MOCK_ERRORS.conflict('AD_STATE_CHANGED', '이미 바뀌었어요. 새로 불러올게요.');
    const now = Date.now();
    const stopped = now - Date.parse(s.detail.takedown.at);
    let note = body.reason;
    if (body.compensation === 'EXTEND') {
        s.detail.period = { ...s.detail.period, endAt: new Date(Date.parse(s.detail.period.endAt) + stopped).toISOString() };
        note = `${body.reason} · 끝 시각 ${Math.round(stopped / 3_600_000)}시간 연장`;
    } else {
        const total = UtilBizApplication.days(s.detail.period);
        const refund = Math.floor((s.detail.amount.total * Math.ceil(stopped / DAY_MS)) / total);
        note = `${body.reason} · 중단 기간 일할 환불 ${refund.toLocaleString('ko-KR')}원`;
    }
    s.stored = 'SCHEDULED';
    s.detail.takedown = null;
    s.detail.lastActionAt = new Date(now).toISOString();
    s.detail.history.push({ at: new Date(now).toISOString(), action: '되돌리기', actor: ME, note });
    return out(s);
}
