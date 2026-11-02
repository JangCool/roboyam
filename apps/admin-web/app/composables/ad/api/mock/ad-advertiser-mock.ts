// MOCK: 광고주 관리 API(`/api/admin/ad-advertisers/**` 후보)의 목업 — `useAdAdvertiserApi.ts` 가 부른다. 서버가 붙으면 이 파일을 지운다.
// admin-web 에는 공용 `mock-util.ts` 가 없어 스위치 읽기·지연을 이 파일 안에 둔다(`ad-stats-mock` 때와 같은 방식).
//
// ── 개발 전용 스위치 — 주소의 `?mock=이름`(쉼표로 여럿) ─
//   adv-empty          광고주 0명(첫 화면 빈 상태 — [새 광고주])
//   adv-fail           목록 500(ErrorState → 다시 시도)
//   adv-detail-fail    서랍 상세 500
//   adv-stats-fail     서랍 「성과」 500
//   adv-name-taken     만들기·고치기 400 `fields.name`(같은 이름)
//   adv-reveal-fail    연락처 보기 403(권한 없음 문구)
//   adv-changed        보관·보관 풀기·삭제 409(다른 운영자가 먼저 바꿈 → 서랍 다시 읽기)
//
// ── 목업 규칙(가정) ─
//   · 광고주 18명(직접 등록 10 · 사업자 8 — 그중 광고주 줄이 붙은 사업자 3 `adv-…`, 줄 없는 사업자 5 `biz-…`). 광고 건은 광고주마다 씨앗을 둔 결정적 의사 난수.
//   · 만들기·고치기·보관·삭제는 이 탭의 메모리에만 남는다(새로 고치면 처음으로). 광고 건 ID 는 가짜라 일정표 서랍을 열면 404 가 난다.
//   · 연락처 평문은 지어낸 값이고 열람 기록에 「나」로 한 줄 더한다. 10분 뒤 가림.

import { ApiError } from '~/composables/common/useApiClient';
import type {
    AdAdvertiserBooking,
    AdAdvertiserBusiness,
    AdAdvertiserCreateBody,
    AdAdvertiserDetail,
    AdAdvertiserEvent,
    AdAdvertiserMasked,
    AdAdvertiserPage,
    AdAdvertiserQuery,
    AdAdvertiserRow,
    AdAdvertiserUpdateBody,
    AdAdvertiserView,
} from '~/composables/ad/api/useAdAdvertiserApi.types';
import type { AdAdvertiserRevealed, AdBookingStatus, AdStatRange, AdStatsOverview, AdType } from '~/composables/ad/api/useAdApi.types';
import UtilAd from '~/utils/util-ad';
import UtilBizApplication from '~/utils/util-biz-application';

const DAY_MS = 86_400_000;
const NOW = Date.now();

/* ── 작은 도구 ── */

function mockFlags(): Set<string> {
    const out = new Set<string>();
    if (typeof window === 'undefined') return out;
    for (const value of new URLSearchParams(window.location.search).getAll('mock')) {
        for (const name of value.split(',')) if (name.trim()) out.add(name.trim());
    }
    return out;
}

const on = (flag: string) => mockFlags().has(flag);

function mockDelay(): Promise<void> {
    return new Promise((resolve) => setTimeout(resolve, 250 + Math.random() * 350));
}

/** 씨앗 → 0~1 결정적 난수(mulberry32) */
function rng(seed: number): () => number {
    let a = seed >>> 0;
    return () => {
        a = (a + 0x6d2b79f5) >>> 0;
        let x = Math.imul(a ^ (a >>> 15), 1 | a);
        x = (x + Math.imul(x ^ (x >>> 7), 61 | x)) ^ x;
        return ((x ^ (x >>> 14)) >>> 0) / 4_294_967_296;
    };
}

const pick = <T>(r: () => number, list: readonly T[]): T => list[Math.floor(r() * list.length)] as T;

/** 오늘 0시(KST)에서 `days` 날 떨어진 시각 */
function kstMidnight(days: number): string {
    const today = UtilBizApplication.kstToday(NOW);
    return new Date(Date.parse(`${today}T00:00:00+09:00`) + days * DAY_MS).toISOString();
}

function fail(status: number, code: string, message: string, fields?: Record<string, string>): never {
    throw new ApiError({ status, code, message, fields });
}

/* ── 씨앗 데이터 ── */

interface Seed {
    n: number;
    name: string;
    business?: Omit<AdAdvertiserBusiness, 'name'> & { linked: boolean };
    contactName: string | null;
    contactPhone: string | null;
    email: string | null;
    memo: string | null;
    /** 광고 건 수(0 = 건 없음 — 지울 수 있다) */
    bookings: number;
    archived?: boolean;
    /** 모든 건이 지난 것(보관된 광고주) */
    pastOnly?: boolean;
    createdDaysAgo: number;
}

const SEEDS: Seed[] = [
    { n: 101, name: '로봇공방 메이커스', contactName: '김하늘', contactPhone: '01023457788', email: 'ad@makers.example', memo: '분기마다 넓은 배너 2주. 세금계산서는 월말 발행 요청.', bookings: 7, createdDaysAgo: 190 },
    { n: 102, name: '한빛교육 코딩캠프', contactName: '이도윤', contactPhone: '0215885521', email: 'camp@hanbit.example', memo: null, bookings: 5, createdDaysAgo: 150 },
    { n: 103, name: '모션랩 스튜디오', contactName: '박서연', contactPhone: null, email: 'hello@motionlab.example', memo: '연락은 이메일로만.', bookings: 3, createdDaysAgo: 120 },
    { n: 104, name: '그린드론 아카데미', contactName: '최민준', contactPhone: '01077001234', email: null, memo: null, bookings: 4, createdDaysAgo: 98 },
    { n: 105, name: '케이로보 전시회 사무국', contactName: '정유나', contactPhone: '0269001100', email: 'expo@krobo.example', memo: '전시 4주 전부터 날개 배너.', bookings: 6, createdDaysAgo: 80 },
    { n: 106, name: '스마트팩토리 엑스포', contactName: null, contactPhone: '0317770000', email: null, memo: null, bookings: 2, createdDaysAgo: 60 },
    { n: 107, name: '로보펀 체험관', contactName: '한지우', contactPhone: '01055556666', email: 'fun@robofun.example', memo: '폐업 — 2026-09 보관.', bookings: 4, archived: true, pastOnly: true, createdDaysAgo: 300 },
    { n: 108, name: '대한로봇협회 행사팀', contactName: '오세훈', contactPhone: '0234561234', email: 'event@krobot.example', memo: '견적만 받고 아직 등록 전.', bookings: 0, createdDaysAgo: 12 },
    { n: 109, name: '아이씨유 센서', contactName: '윤채원', contactPhone: '01098765432', email: 'mkt@icu-sensor.example', memo: null, bookings: 3, createdDaysAgo: 45 },
    { n: 110, name: '새봄 출판사', contactName: null, contactPhone: null, email: 'book@saebom.example', memo: null, bookings: 0, createdDaysAgo: 3 },
    { n: 201, name: '(주)로보얌상사', business: { id: '9001', maskedRegNo: '***-**-*1234', status: 'APPROVED', adService: 'APPROVED', linked: true }, contactName: '장현우', contactPhone: '01011112222', email: 'ad@roboyam-trade.example', memo: '대행사 경유 — 광고 담당자는 대행사 직원.', bookings: 9, createdDaysAgo: 140 },
    { n: 202, name: '메카닉스코리아', business: { id: '9002', maskedRegNo: '***-**-*5521', status: 'APPROVED', adService: 'APPROVED', linked: false }, contactName: null, contactPhone: null, email: null, memo: null, bookings: 6, createdDaysAgo: 110 },
    { n: 203, name: '드론나라', business: { id: '9003', maskedRegNo: '***-**-*0087', status: 'APPROVED', adService: 'SUSPENDED', linked: false }, contactName: null, contactPhone: null, email: null, memo: null, bookings: 4, createdDaysAgo: 95 },
    { n: 204, name: '코딩로봇몰', business: { id: '9004', maskedRegNo: '***-**-*3310', status: 'SUSPENDED', adService: 'SUSPENDED', linked: true }, contactName: '서지민', contactPhone: '01033334444', email: null, memo: '사업자 정지(정책 위반) — 진행 건은 내림.', bookings: 5, createdDaysAgo: 130 },
    { n: 205, name: '파츠마켓', business: { id: '9005', maskedRegNo: '***-**-*7742', status: 'APPROVED', adService: 'OFF', linked: false }, contactName: null, contactPhone: null, email: null, memo: null, bookings: 3, createdDaysAgo: 70 },
    { n: 206, name: '에이아이키트', business: { id: '9006', maskedRegNo: '***-**-*6610', status: 'WITHDRAWN', adService: 'OFF', linked: false }, contactName: null, contactPhone: null, email: null, memo: null, bookings: 2, pastOnly: true, createdDaysAgo: 260 },
    { n: 207, name: '서보모터샵', business: { id: '9007', maskedRegNo: '***-**-*2299', status: 'APPROVED', adService: 'APPROVED', linked: false }, contactName: null, contactPhone: null, email: null, memo: null, bookings: 1, createdDaysAgo: 20 },
    { n: 208, name: '로봇청소기 리뷰랩', business: { id: '9008', maskedRegNo: '***-**-*4455', status: 'APPROVED', adService: 'APPROVED', linked: true }, contactName: '문가은', contactPhone: '01066667777', email: 'review@cleanlab.example', memo: null, bookings: 4, createdDaysAgo: 85 },
];

/** 사업자 신원 연락처(가린 값) — 광고 담당자가 비었을 때 보인다 */
const BUSINESS_CONTACT: Record<string, { phone: string; email: string; plainPhone: string; plainEmail: string; rep: string }> = {
    '9002': { phone: '010-****-5521', email: 'm***@mechanics.example', plainPhone: '010-4821-5521', plainEmail: 'manager@mechanics.example', rep: '강태오' },
    '9003': { phone: '02-****-0087', email: 'd***@dronenara.example', plainPhone: '02-338-0087', plainEmail: 'drone@dronenara.example', rep: '임수빈' },
    '9005': { phone: '010-****-7742', email: 'p***@partsmarket.example', plainPhone: '010-9921-7742', plainEmail: 'parts@partsmarket.example', rep: '조하린' },
    '9006': { phone: '010-****-6610', email: 'a***@aikit.example', plainPhone: '010-1188-6610', plainEmail: 'admin@aikit.example', rep: '백도현' },
    '9007': { phone: '031-****-2299', email: 's***@servo.example', plainPhone: '031-707-2299', plainEmail: 'shop@servo.example', rep: '남궁윤' },
};

/** 유형별 하루 단가(공급가, 원) — 목업 */
const DAILY: Record<AdType, number> = {
    WIDE_BANNER: 50_000,
    STRIP_BANNER: 30_000,
    SQUARE_BANNER: 25_000,
    SMALL_BANNER: 15_000,
    WING_BANNER: 35_000,
    TEXT_LINE: 10_000,
    HOTDEAL_TOP: 20_000,
    HOTDEAL_CATEGORY: 12_000,
};
const AD_TYPES = UtilAd.MANUAL_TYPES;
const HOTDEAL_TYPES: readonly AdType[] = ['HOTDEAL_TOP', 'HOTDEAL_CATEGORY'];

/* ── 메모리 상태 ── */

interface Record_ {
    seed: Seed;
    id: string;
    kind: 'BUSINESS' | 'ADVERTISER';
    plain: { contactName: string | null; contactPhone: string | null; email: string | null };
    memo: string | null;
    archived: boolean;
    createdAt: string;
    bookings: AdAdvertiserBooking[];
    views: AdAdvertiserView[];
    history: AdAdvertiserEvent[];
}

let store: Record_[] | null = null;
let nextId = 300;

function maskName(v: string | null): string | null {
    if (!v) return null;
    return v.length <= 1 ? '*' : `${v[0]}${'*'.repeat(Math.max(1, v.length - 2))}${v.length > 2 ? v[v.length - 1] : ''}`;
}

function formatPhone(digits: string): string {
    if (digits.startsWith('02')) return digits.length === 9 ? `02-${digits.slice(2, 5)}-${digits.slice(5)}` : `02-${digits.slice(2, 6)}-${digits.slice(6)}`;
    return digits.length === 10 ? `${digits.slice(0, 3)}-${digits.slice(3, 6)}-${digits.slice(6)}` : `${digits.slice(0, 3)}-${digits.slice(3, 7)}-${digits.slice(7)}`;
}

function maskPhone(v: string | null): string | null {
    if (!v) return null;
    const parts = formatPhone(v).split('-');
    return `${parts[0]}-****-${parts[2]}`;
}

function maskEmail(v: string | null): string | null {
    if (!v) return null;
    const [user, domain] = v.split('@');
    return `${(user ?? '').slice(0, 1)}***@${domain ?? ''}`;
}

function makeBookings(seed: Seed, id: string, name: string): AdAdvertiserBooking[] {
    const r = rng(seed.n * 7919);
    const out: AdAdvertiserBooking[] = [];
    const isBiz = Boolean(seed.business);
    for (let i = 0; i < seed.bookings; i++) {
        const hotdeal = isBiz && (seed.business?.adService === 'OFF' || r() < 0.25);
        const type = hotdeal ? pick(r, HOTDEAL_TYPES) : pick(r, AD_TYPES);
        const length = pick(r, [7, 14, 14, 30]);
        // 지난 건이 많고, 지금·앞으로 건이 조금
        const startDay = seed.pastOnly ? -60 - Math.floor(r() * 180) : Math.floor(r() * 170) - 130;
        const startAt = kstMidnight(startDay);
        const endAt = kstMidnight(startDay + length);
        const s = Date.parse(startAt);
        const e = Date.parse(endAt);
        let status: AdBookingStatus;
        if (s > NOW) status = r() < 0.3 ? pick(r, ['SUBMITTED', 'AWAITING_PAYMENT', 'AWAITING_DEPOSIT'] as const) : 'SCHEDULED';
        else if (e > NOW) status = 'RUNNING';
        else status = r() < 0.12 ? 'CANCELED' : 'ENDED';
        // 사업자 정지면 진행·예약 건은 내림(정지 훅)
        if (seed.business?.status === 'SUSPENDED' && (status === 'RUNNING' || status === 'SCHEDULED')) status = 'TAKEN_DOWN';
        const source = !isBiz || (seed.business?.linked && r() < 0.4) ? 'OPERATOR' : 'APPLICATION';
        const paymentMethod = source === 'OPERATOR' ? 'MANUAL' : r() < 0.8 ? 'ONLINE' : 'MANUAL';
        const total = Math.round(DAILY[type] * length * 1.1);
        const paidStates: AdBookingStatus[] = ['SCHEDULED', 'RUNNING', 'ENDED', 'TAKEN_DOWN'];
        const paidCanceled = status === 'CANCELED' && r() < 0.5;
        const paid = paidStates.includes(status) || paidCanceled ? total : 0;
        const refunded = status === 'TAKEN_DOWN' ? Math.round(total * 0.4) : paidCanceled ? total : 0;
        out.push({
            id: `mock-adv-${seed.n}-${i + 1}`,
            type,
            advertiser: { id, kind: isBiz ? 'BUSINESS' : 'ADVERTISER', name, businessId: seed.business?.id ?? null, maskedRegNo: seed.business?.maskedRegNo ?? null, contactMasked: null },
            period: { startAt, endAt },
            status,
            paymentMethod,
            weight: pick(r, [3, 5, 5, 5, 7]),
            source,
            applicationNo: `BA-2026${String(seed.n).padStart(4, '0')}${i + 1}`,
            lastActionAt: new Date(Math.min(NOW, s)).toISOString(),
            domain: HOTDEAL_TYPES.includes(type) ? 'HOTDEAL' : 'AD',
            takedownCause: status === 'TAKEN_DOWN' ? 'SUSPENDED' : null,
            paid,
            refunded,
        });
    }
    return out.sort((a, b) => Date.parse(b.period.startAt) - Date.parse(a.period.startAt));
}

function makeRecord(seed: Seed): Record_ {
    const kind = seed.business ? 'BUSINESS' : 'ADVERTISER';
    const id = seed.business && !seed.business.linked ? `biz-${seed.business.id}` : `adv-${seed.n}`;
    const createdAt = kstMidnight(-seed.createdDaysAgo);
    const r = rng(seed.n * 104_729);
    const viewers = ['운영자 김민수', '운영자 박지영', '나'];
    const reasons = ['입금 확인 전화', '세금계산서 담당자 확인', '소재 교체 요청 회신', '긴급 내림 안내 연락'];
    const views: AdAdvertiserView[] =
        seed.contactPhone || seed.email || seed.business
            ? Array.from({ length: Math.floor(r() * 4) }, (_, i) => ({ at: new Date(NOW - (i * 9 + 2 + Math.floor(r() * 6)) * DAY_MS).toISOString(), actor: pick(r, viewers), reason: pick(r, reasons) }))
            : [];
    const history: AdAdvertiserEvent[] = [];
    if (seed.business?.linked || !seed.business) history.push({ at: createdAt, action: 'CREATE', actor: '운영자 김민수', fields: [] });
    if (seed.memo) history.push({ at: new Date(Date.parse(createdAt) + 6 * DAY_MS).toISOString(), action: 'UPDATE', actor: '운영자 박지영', fields: ['memo'] });
    if (seed.archived) history.push({ at: kstMidnight(-40), action: 'ARCHIVE', actor: '운영자 김민수', fields: [] });
    return {
        seed,
        id,
        kind,
        plain: { contactName: seed.contactName, contactPhone: seed.contactPhone, email: seed.email },
        memo: seed.memo,
        archived: Boolean(seed.archived),
        createdAt,
        bookings: makeBookings(seed, id, seed.name),
        views,
        history: history.sort((a, b) => Date.parse(b.at) - Date.parse(a.at)),
    };
}

function all(): Record_[] {
    if (!store) store = SEEDS.map(makeRecord);
    return on('adv-empty') ? [] : store;
}

function find(id: string): Record_ {
    const rec = all().find((x) => x.id === id);
    if (!rec) fail(404, 'AD_ADVERTISER_NOT_FOUND', '광고주를 찾을 수 없어요.');
    return rec;
}

/* ── 모양 만들기 ── */

function business(rec: Record_): AdAdvertiserBusiness | null {
    const b = rec.seed.business;
    return b ? { id: b.id, name: rec.seed.name, maskedRegNo: b.maskedRegNo, status: b.status, adService: b.adService } : null;
}

function masked(rec: Record_): { masked: AdAdvertiserMasked; source: 'ADVERTISER' | 'BUSINESS' | null } {
    const p = rec.plain;
    if (p.contactName || p.contactPhone || p.email) return { masked: { contactName: maskName(p.contactName), contactPhone: maskPhone(p.contactPhone), email: maskEmail(p.email) }, source: 'ADVERTISER' };
    const bc = rec.seed.business ? BUSINESS_CONTACT[rec.seed.business.id] : undefined;
    if (bc) return { masked: { contactName: maskName(bc.rep), contactPhone: bc.phone, email: bc.email }, source: 'BUSINESS' };
    return { masked: { contactName: null, contactPhone: null, email: null }, source: null };
}

const OPEN: AdBookingStatus[] = ['SUBMITTED', 'AWAITING_PAYMENT', 'AWAITING_DEPOSIT', 'SCHEDULED', 'RUNNING'];

function row(rec: Record_): AdAdvertiserRow {
    const bs = rec.bookings;
    const running = bs.filter((b) => b.status === 'RUNNING' || (b.status === 'SCHEDULED' && Date.parse(b.period.startAt) <= NOW)).length;
    const scheduled = bs.filter((b) => b.status === 'SCHEDULED' && Date.parse(b.period.startAt) > NOW).length;
    const waiting = bs.filter((b) => b.status === 'SUBMITTED' || b.status === 'AWAITING_PAYMENT' || b.status === 'AWAITING_DEPOSIT').length;
    const dated = bs.filter((b) => ['SCHEDULED', 'RUNNING', 'ENDED', 'TAKEN_DOWN'].includes(b.status) && Date.parse(b.period.startAt) <= NOW);
    const m = masked(rec).masked;
    return {
        id: rec.id,
        kind: rec.kind,
        name: rec.seed.name,
        contactMasked: m.contactPhone ?? m.email,
        business: business(rec),
        state: rec.archived ? 'ARCHIVED' : 'ACTIVE',
        counts: { running, scheduled, waiting, total: bs.length },
        paidTotal: bs.reduce((s, b) => s + b.paid, 0),
        lastAdAt: dated.length ? dated.map((b) => b.period.startAt).sort().at(-1)! : null,
        createdAt: rec.createdAt,
    };
}

function detail(rec: Record_): AdAdvertiserDetail {
    const base = row(rec);
    const { masked: m, source } = masked(rec);
    const bs = rec.bookings;
    return {
        ...base,
        masked: m,
        contactSource: source,
        memo: rec.memo,
        bookings: bs.slice(0, 50),
        money: {
            paid: base.paidTotal,
            online: bs.filter((b) => b.paymentMethod === 'ONLINE').reduce((s, b) => s + b.paid, 0),
            manual: bs.filter((b) => b.paymentMethod === 'MANUAL').reduce((s, b) => s + b.paid, 0),
            refunded: bs.reduce((s, b) => s + b.refunded, 0),
        },
        views: rec.views.slice(0, 20),
        history: rec.history,
        deletable: rec.kind === 'ADVERTISER' && bs.length === 0,
        archivable: rec.kind === 'ADVERTISER' && !bs.some((b) => OPEN.includes(b.status)),
    };
}

/* ── 검사 ── */

function checkFields(body: { name?: string; contactPhone?: string; email?: string }, selfId: string | null): void {
    const fields: Record<string, string> = {};
    if (body.name !== undefined) {
        const name = body.name.trim();
        if (!name) fields.name = '광고주 이름을 입력해 주세요.';
        else if (on('adv-name-taken') || all().some((x) => x.id !== selfId && x.seed.name === name)) fields.name = '같은 이름의 광고주가 있어요.';
    }
    if (body.contactPhone) {
        const digits = body.contactPhone.replace(/[^0-9]/g, '');
        if (digits.length < 9 || digits.length > 11) fields.contactPhone = '연락처는 숫자 9~11자리로 입력해 주세요.';
    }
    if (body.email && !/^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(body.email)) fields.email = '이메일 형식이 아니에요.';
    if (Object.keys(fields).length) fail(400, 'VALIDATION', '입력값을 확인해 주세요.', fields);
}

/* ── 내보내는 함수 ── */

export async function mockAdvertiserList(query: AdAdvertiserQuery): Promise<AdAdvertiserPage> {
    await mockDelay();
    if (on('adv-fail')) fail(500, 'INTERNAL', '광고주 목록을 불러오지 못했어요.');
    const state = query.state ?? 'ACTIVE';
    const q = (query.q ?? '').trim().toLowerCase();
    const byState = all()
        .map(row)
        .filter((r) => state === 'ALL' || r.state === state)
        .filter((r) => !q || r.name.toLowerCase().includes(q) || (r.business?.maskedRegNo.endsWith(q) ?? false));
    const counts = { ALL: byState.length, BUSINESS: byState.filter((r) => r.kind === 'BUSINESS').length, ADVERTISER: byState.filter((r) => r.kind === 'ADVERTISER').length };
    const rows = byState.filter((r) => !query.kind || r.kind === query.kind);
    const sort = query.sort ?? 'lastAdAt';
    const dir = query.dir === 'asc' ? 1 : -1;
    rows.sort((a, b) => {
        let d = 0;
        if (sort === 'name') d = a.name.localeCompare(b.name, 'ko');
        else if (sort === 'paidTotal') d = a.paidTotal - b.paidTotal;
        // 광고 건 없는 광고주는 방향과 상관없이 뒤
        else if (!a.lastAdAt || !b.lastAdAt) return a.lastAdAt ? -1 : b.lastAdAt ? 1 : b.createdAt.localeCompare(a.createdAt);
        else d = a.lastAdAt.localeCompare(b.lastAdAt);
        return d * dir || b.createdAt.localeCompare(a.createdAt);
    });
    const start = query.page * query.size;
    return { items: rows.slice(start, start + query.size), total: rows.length, counts };
}

export async function mockAdvertiserDetail(id: string): Promise<AdAdvertiserDetail> {
    await mockDelay();
    if (on('adv-detail-fail')) fail(500, 'INTERNAL', '광고주 정보를 불러오지 못했어요.');
    return detail(find(id));
}

export async function mockAdvertiserCreate(body: AdAdvertiserCreateBody): Promise<AdAdvertiserDetail> {
    await mockDelay();
    checkFields(body, null);
    const n = nextId++;
    const seed: Seed = { n, name: body.name.trim(), contactName: body.contactName.trim() || null, contactPhone: body.contactPhone.replace(/[^0-9]/g, '') || null, email: body.email.trim() || null, memo: body.memo.trim() || null, bookings: 0, createdDaysAgo: 0 };
    const rec: Record_ = {
        seed,
        id: `adv-${n}`,
        kind: 'ADVERTISER',
        plain: { contactName: seed.contactName, contactPhone: seed.contactPhone, email: seed.email },
        memo: seed.memo,
        archived: false,
        createdAt: new Date().toISOString(),
        bookings: [],
        views: [],
        history: [{ at: new Date().toISOString(), action: 'CREATE', actor: '나', fields: [] }],
    };
    store = [rec, ...all()];
    return detail(rec);
}

export async function mockAdvertiserUpdate(id: string, body: AdAdvertiserUpdateBody): Promise<AdAdvertiserDetail> {
    await mockDelay();
    const rec = find(id);
    if (rec.kind === 'BUSINESS' && body.name !== undefined) fail(400, 'VALIDATION', '입력값을 확인해 주세요.', { name: '사업자 광고주의 이름은 상호예요. 사업자 정보에서 바뀌어요.' });
    checkFields(body, id);
    const changed: string[] = [];
    if (body.name !== undefined && body.name.trim() !== rec.seed.name) {
        rec.seed.name = body.name.trim();
        for (const b of rec.bookings) b.advertiser = { ...b.advertiser, name: rec.seed.name };
        changed.push('name');
    }
    for (const key of ['contactName', 'contactPhone', 'email'] as const) {
        const value = body[key];
        if (value === undefined) continue;
        const next = key === 'contactPhone' ? value.replace(/[^0-9]/g, '') || null : value.trim() || null;
        if (next !== rec.plain[key]) {
            rec.plain[key] = next;
            changed.push(key);
        }
    }
    if (body.memo !== undefined && (body.memo.trim() || null) !== rec.memo) {
        rec.memo = body.memo.trim() || null;
        changed.push('memo');
    }
    if (changed.length) rec.history.unshift({ at: new Date().toISOString(), action: 'UPDATE', actor: '나', fields: changed });
    return detail(rec);
}

export async function mockAdvertiserArchive(id: string, archived: boolean): Promise<AdAdvertiserDetail> {
    await mockDelay();
    if (on('adv-changed')) fail(409, 'AD_ADVERTISER_STATE_CHANGED', '다른 운영자가 먼저 바꿨어요. 다시 불러왔어요.');
    const rec = find(id);
    if (archived && !detail(rec).archivable) fail(409, 'AD_ADVERTISER_HAS_OPEN_BOOKINGS', '끝나지 않은 광고 건이 있어 보관할 수 없어요.');
    rec.archived = archived;
    rec.history.unshift({ at: new Date().toISOString(), action: archived ? 'ARCHIVE' : 'UNARCHIVE', actor: '나', fields: [] });
    return detail(rec);
}

export async function mockAdvertiserRemove(id: string): Promise<void> {
    await mockDelay();
    if (on('adv-changed')) fail(409, 'AD_ADVERTISER_STATE_CHANGED', '다른 운영자가 먼저 바꿨어요. 다시 불러왔어요.');
    const rec = find(id);
    if (!detail(rec).deletable) fail(409, 'AD_ADVERTISER_HAS_BOOKINGS', '광고 건이 있는 광고주는 지울 수 없어요. 보관해 주세요.');
    store = all().filter((x) => x !== rec);
}

export async function mockAdvertiserReveal(id: string, reason: string): Promise<AdAdvertiserRevealed> {
    await mockDelay();
    if (on('adv-reveal-fail')) fail(403, 'FORBIDDEN', '연락처를 볼 권한이 없어요.');
    const rec = find(id);
    rec.views.unshift({ at: new Date().toISOString(), actor: '나', reason });
    const hideAt = new Date(Date.now() + 600_000).toISOString();
    const p = rec.plain;
    if (p.contactName || p.contactPhone || p.email) return { contactName: p.contactName, contactPhone: p.contactPhone ? formatPhone(p.contactPhone) : null, email: p.email, hideAt };
    const bc = rec.seed.business ? BUSINESS_CONTACT[rec.seed.business.id] : undefined;
    return { contactName: bc?.rep ?? null, contactPhone: bc?.plainPhone ?? null, email: bc?.plainEmail ?? null, hideAt };
}

/** 광고주 성과 — 실제 서버는 기존 `GET /api/admin/ads/stats?from&to&advertiserId` 를 그대로 쓴다(같은 모양) */
export async function mockAdvertiserStats(id: string, range: AdStatRange): Promise<AdStatsOverview> {
    await mockDelay();
    if (on('adv-stats-fail')) fail(500, 'INTERNAL', '성과를 불러오지 못했어요.');
    const rec = find(id);
    const from = Date.parse(`${range.from}T00:00:00+09:00`);
    const to = Date.parse(`${range.to}T00:00:00+09:00`);
    const today = UtilBizApplication.kstToday(NOW);
    const days: AdStatsOverview['days'] = [];
    const shown = new Set<string>();
    const byType = new Map<AdType, { impressions: number; clicks: number; ids: Set<string> }>();
    for (let t = from; t <= to; t += DAY_MS) {
        const date = UtilBizApplication.kstDate(new Date(t).toISOString());
        let impressions = 0;
        let clicks = 0;
        if (date <= today) {
            for (const b of rec.bookings) {
                if (!['RUNNING', 'ENDED', 'SCHEDULED'].includes(b.status)) continue;
                if (Date.parse(b.period.startAt) > t || Date.parse(b.period.endAt) <= t) continue;
                const r = rng(Number(b.id.replace(/[^0-9]/g, '')) * 31 + t / DAY_MS);
                const base = Math.round(DAILY[b.type] / 20) * (0.7 + r() * 0.6) * (date === today ? 0.45 : 1);
                const imp = Math.round(base);
                const clk = Math.round(imp * (0.004 + r() * 0.012));
                impressions += imp;
                clicks += clk;
                shown.add(b.id);
                const cur = byType.get(b.type) ?? { impressions: 0, clicks: 0, ids: new Set<string>() };
                cur.impressions += imp;
                cur.clicks += clk;
                cur.ids.add(b.id);
                byType.set(b.type, cur);
            }
        }
        days.push({ date, impressions, clicks });
    }
    const totals = days.reduce((s, d) => ({ impressions: s.impressions + d.impressions, clicks: s.clicks + d.clicks }), { impressions: 0, clicks: 0 });
    return {
        asOf: new Date().toISOString(),
        range,
        advertiser: { id: rec.id, name: rec.seed.name, kind: rec.kind },
        totals: { ...totals, bookings: shown.size },
        days,
        byType: [...byType.entries()].map(([type, v]) => ({ type, impressions: v.impressions, clicks: v.clicks, bookings: v.ids.size })).sort((a, b) => b.impressions - a.impressions),
        byPlacement: [],
        topAdvertisers: [],
    };
}
