// MOCK: 광고·사업자 핫딜 「일시 정지 / 중단」 — 관리자 광고 일정표(`/ads`) 목업. `useAdApi` 의 `// MOCK:` 줄이 부른다.
// 이 화면은 이미 실제 API(`/api/admin/ads`)에 붙어 있어서, **`?mock=pause-all`(또는 `pause-paused`·`pause-paused-op`·`pause-stopped`) 일 때만**
// 그 달 달력·표에 광고주 요청 정지 중 건 2개 · 중단 건 1개를 실제 응답 위에 얹는다(스위치가 없으면 실제 응답 그대로).
// 건은 광고 심사·핫딜 심사 목업(`bizapplication/api/mock/pause-mock.ts`)과 같은 메모리 건이다 — 심사 화면에서 대신 정지·다시 켜기·중단하면 여기도 바뀐다.
// 보고 있는 유형 탭의 건으로 보이게 유형만 그 탭으로 바꿔 그린다(광고 유형 탭 = 광고 심사 건, 핫딜 노출 탭 = 핫딜 심사 건). 그 달에 걸친 건만, 날짜별 남은 칸에서 그 건 몫을 뺀다.
// 실제 달력을 못 받으면(서버 꺼짐) 한도 3짜리 빈 달력 위에 목업 건만 보인다. 서버가 `takedownCause`·`takedown.channel/memo`·상세 `pause` 를 내려 주면 이 파일과 MOCK 줄을 지운다.

import type { AdBookingDetail, AdBookingPage, AdBookingQuery, AdBookingRow, AdBookingStatus, AdCalendar, AdType } from '~/composables/ad/api/useAdApi.types';
import { isMockPauseId, mockPauseDetails, mockPauseDetailSync } from '~/composables/bizapplication/api/mock/pause-mock';
import type { BizApplicationDetail, BizDomain } from '~/composables/bizapplication/api/useBizApplicationApi.types';
import { mockDelay, mockFlags } from '~/composables/common/mock/mock-util';
import UtilBizApplication from '~/utils/util-biz-application';

/** 일정표에 얹는 목업 건 — 정지 중 2개 · 중단 1개 */
const CAL_CASES = ['paused', 'paused-op', 'stopped'] as const;

/** 목업 건 번호 → 마지막으로 그린 유형 탭(상세도 같은 유형으로) */
const lastType = new Map<string, AdType>();

function anyOn(): boolean {
    return [...mockFlags()].some((f) => f === 'pause-all' || (CAL_CASES as readonly string[]).includes(f.replace(/^pause-/, '')));
}

function domainOf(type: AdType): BizDomain {
    return type === 'HOTDEAL_TOP' || type === 'HOTDEAL_CATEGORY' ? 'HOTDEAL' : 'AD';
}

function toRow(d: BizApplicationDetail, type: AdType): AdBookingRow {
    lastType.set(d.id, type);
    return {
        id: d.id,
        type,
        advertiser: { id: `biz-${d.business.id}`, kind: 'BUSINESS', name: d.business.name, businessId: d.business.id, maskedRegNo: d.business.maskedRegNo, contactMasked: '010-****-5678' },
        period: d.period,
        status: d.status as AdBookingStatus,
        paymentMethod: d.approval?.method ?? d.preferredMethod,
        weight: d.weight,
        source: 'APPLICATION',
        applicationNo: d.no,
        lastActionAt: d.history.reduce((at, x) => (x.at > at ? x.at : at), d.submittedAt),
        domain: d.kind === 'HOTDEAL_POST' || d.kind === 'HOTDEAL_PLACEMENT' ? 'HOTDEAL' : 'AD',
        takedownCause: d.status === 'TAKEN_DOWN' ? (d.takedown?.cause ?? null) : null,
    };
}

function toDetail(d: BizApplicationDetail, type: AdType): AdBookingDetail {
    const c = d.content;
    const creative =
        c.type === 'AD_BANNER'
            ? { imageUrl: c.imageUrl, text: null, linkUrl: c.linkUrl, altText: c.altText }
            : c.type === 'AD_TEXT_LINE'
              ? { imageUrl: null, text: c.text, linkUrl: c.linkUrl, altText: null }
              : c.type === 'HOTDEAL_POST'
                ? { imageUrl: c.imageUrl ?? null, text: c.title, linkUrl: c.dealUrl, altText: c.title }
                : { imageUrl: null, text: c.postTitle, linkUrl: '', altText: null };
    return {
        ...toRow(d, type),
        amount: d.approval?.amount ?? { supply: d.quote.supply, vat: d.quote.vat, total: d.quote.total },
        proposedSupply: null,
        creatives: [{ version: 1, ...creative, approvedAt: d.approval?.approvedAt ?? null, approvedBy: d.approval?.approvedBy ?? null, current: true }],
        onlinePayment: d.onlinePayment,
        contracts: d.contracts,
        deposit: d.deposit,
        takedown: d.takedown
            ? { at: d.takedown.at, cause: d.takedown.cause, reason: d.takedown.text, by: d.takedown.by ?? null, mailSent: false, mail: null, channel: d.takedown.channel ?? null, memo: d.takedown.memo ?? null }
            : null,
        refund: d.refund,
        memo: null,
        registrant: null,
        history: d.history,
        pause: d.pause ?? null,
    };
}

/** 그 유형 탭에 얹을 목업 건(취소된 건 제외 — 달력처럼) */
function bookingsFor(type: AdType): BizApplicationDetail[] {
    return mockPauseDetails(domainOf(type), CAL_CASES).filter((d) => d.status !== 'CANCELED');
}

/** 그 달 `[1일 0시, 다음 달 1일 0시)`(KST)와 겹치나 */
function inMonth(d: BizApplicationDetail, month: string): boolean {
    const from = Date.parse(UtilBizApplication.kstMidnight(`${month}-01`));
    const [y, m] = month.split('-').map(Number);
    const next = new Date(Date.UTC(y!, m!, 1)).toISOString().slice(0, 10);
    const to = Date.parse(UtilBizApplication.kstMidnight(next));
    return Date.parse(d.period.startAt) < to && Date.parse(d.period.endAt) > from;
}

/** 서버가 꺼져 있을 때의 빈 달력(한도 3) */
function emptyCalendar(type: AdType, month: string): AdCalendar {
    const [y, m] = month.split('-').map(Number);
    const daysIn = new Date(Date.UTC(y!, m!, 0)).getUTCDate();
    return {
        summary: { type, limit: 3, running: 0, scheduled: 0, awaitingPayment: 0 },
        days: Array.from({ length: daysIn }, (_, i) => ({ date: `${month}-${String(i + 1).padStart(2, '0')}`, remaining: 3, limit: 3 })),
        bookings: [],
    };
}

/** 달력 — 스위치가 없으면 실제 응답 그대로. 있으면 그 달에 걸친 목업 건을 얹고 그날 남은 칸에서 뺀다(정지·중단 건도 자리를 차지한다) */
export async function mockPauseCalendar(type: AdType, month: string, real: () => Promise<AdCalendar>): Promise<AdCalendar> {
    if (!anyOn()) return real();
    let found: AdCalendar;
    try {
        found = await real();
    } catch {
        await mockDelay();
        found = emptyCalendar(type, month);
    }
    const mine = bookingsFor(type).filter((d) => inMonth(d, month));
    const days = found.days.map((day) => {
        const taken = mine.filter((d) => d.status === 'TAKEN_DOWN' && UtilBizApplication.overlapsDay(d.period, day.date)).length;
        return taken ? { ...day, remaining: Math.max(0, day.remaining - taken) } : day;
    });
    return { ...found, days, bookings: [...mine.map((d) => toRow(d, type)), ...found.bookings] };
}

/** 표 — 스위치가 없거나 상태 거르기가 내림이 아니면 실제 응답 그대로. 있으면 1쪽 맨 위에 목업 건 */
export async function mockPauseBookings(query: AdBookingQuery, real: () => Promise<AdBookingPage>): Promise<AdBookingPage> {
    if (!anyOn() || (query.status && query.status !== 'TAKEN_DOWN')) return real();
    let found: AdBookingPage;
    try {
        found = await real();
    } catch {
        found = { items: [], total: 0 };
    }
    const q = query.q?.trim().toLowerCase() ?? '';
    const mine = bookingsFor(query.type)
        .filter((d) => !query.status || d.status === query.status)
        .filter((d) => !q || d.business.name.toLowerCase().includes(q) || d.no.toLowerCase().includes(q))
        .map((d) => toRow(d, query.type));
    return { items: query.page === 0 ? [...mine, ...found.items] : found.items, total: found.total + mine.length };
}

/** 상세 — 목업 건 번호면 목업(스위치 없이도), 아니면 null(실제 API 로) */
export function mockPauseBooking(id: string): Promise<AdBookingDetail> | null {
    if (!isMockPauseId(id)) return null;
    const d = mockPauseDetailSync(id);
    if (!d) return null;
    const type = lastType.get(id) ?? d.adType ?? 'WIDE_BANNER';
    return mockDelay().then(() => toDetail(d, type));
}
