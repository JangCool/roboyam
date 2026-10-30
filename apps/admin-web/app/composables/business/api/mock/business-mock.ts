// MOCK: 사업자 심사 API 목업 — 서버가 붙으면 이 폴더와 `useBusinessApi.ts` 의 `// MOCK:` 줄을 지운다.
// 값은 메모리에만 둔다(새로고침하면 처음으로). 처리 단추는 이 메모리 상태를 실제로 바꾼다. 스위치(`?mock=`):
//   biz-list-fail   목록을 못 읽는다(500)
//   biz-empty       사업자가 하나도 없다
//   nts-down        [다시 조회] 가 늘 「조회 불가」를 준다
// 문구(서버 몫)는 번역하지 않는다(i18n.md 5절).

import type {
    BusinessApproveRequest,
    BusinessDetail,
    BusinessHistory,
    BusinessPage,
    BusinessQuery,
    BusinessRejectRequest,
    BusinessReveal,
    BusinessStatus,
    BusinessSuspendRequest,
    NtsBizStatus,
    NtsCheck,
} from '~/composables/business/api/useBusinessApi.types';
import { BUSINESS_STATUSES } from '~/composables/business/api/useBusinessApi.types';
import { MOCK_ERRORS, mockClone, mockDelay, mockFlags } from '~/composables/common/mock/mock-util';

const HOUR = 3_600_000;
const DAY = 24 * HOUR;
const REVEAL_SECONDS = 600;
const ME = '운영자 김민지';

const ago = (ms: number) => new Date(Date.now() - ms).toISOString();

interface Seed {
    name: string;
    regNo: string;
    rep: string;
    status: BusinessStatus;
    nts: NtsBizStatus;
    services: BusinessDetail['services'];
    nickname: string;
    days: number;
    impact?: BusinessDetail['impact'];
    live?: number;
}

const SEEDS: Seed[] = [
    { name: '푸른숲 커피', regNo: '124-81-34598', rep: '김하늘', status: 'PENDING', nts: 'CONTINUING', services: [{ service: 'HOTDEAL', status: 'PENDING' }, { service: 'AD', status: 'PENDING' }], nickname: '숲속바리스타', days: 0.2 },
    { name: '모던가구 스튜디오', regNo: '220-87-11203', rep: '이도윤', status: 'PENDING', nts: 'PAUSED', services: [{ service: 'HOTDEAL', status: 'PENDING' }, { service: 'AD', status: 'OFF' }], nickname: '가구장인', days: 1.1 },
    { name: '하루펫샵', regNo: '105-12-88721', rep: '박서연', status: 'PENDING', nts: 'UNAVAILABLE', services: [{ service: 'HOTDEAL', status: 'OFF' }, { service: 'AD', status: 'PENDING' }], nickname: '하루집사', days: 2.3 },
    { name: '스마트기어 몰', regNo: '314-86-50012', rep: '최준호', status: 'PENDING', nts: 'CLOSED', services: [{ service: 'HOTDEAL', status: 'PENDING' }, { service: 'AD', status: 'PENDING' }], nickname: '기어매니아', days: 3.6 },
    { name: '오늘의 반찬', regNo: '211-15-90087', rep: '정유나', status: 'APPROVED', nts: 'CONTINUING', services: [{ service: 'HOTDEAL', status: 'APPROVED' }, { service: 'AD', status: 'APPROVED' }], nickname: '반찬가게', days: 40, impact: { awaitingPayment: 1, scheduled: 2, running: 1 }, live: 3 },
    { name: '캠핑하우스', regNo: '608-81-22715', rep: '한지민', status: 'APPROVED', nts: 'CONTINUING', services: [{ service: 'HOTDEAL', status: 'APPROVED' }, { service: 'AD', status: 'OFF' }], nickname: '캠핑좋아', days: 75, impact: { awaitingPayment: 0, scheduled: 1, running: 0 }, live: 1 },
    { name: '디지털플랜트', regNo: '129-86-71240', rep: '오세훈', status: 'APPROVED', nts: 'CONTINUING', services: [{ service: 'HOTDEAL', status: 'OFF' }, { service: 'AD', status: 'APPROVED' }], nickname: '플랜트맨', days: 120, impact: { awaitingPayment: 1, scheduled: 0, running: 2 }, live: 2 },
    { name: '알뜰마켓', regNo: '402-23-55190', rep: '윤가영', status: 'REJECTED', nts: 'CONTINUING', services: [{ service: 'HOTDEAL', status: 'REJECTED' }, { service: 'AD', status: 'OFF' }], nickname: '알뜰이', days: 12 },
    { name: '빅세일 코리아', regNo: '135-81-60033', rep: '장민석', status: 'SUSPENDED', nts: 'CONTINUING', services: [{ service: 'HOTDEAL', status: 'APPROVED' }, { service: 'AD', status: 'APPROVED' }], nickname: '빅세일', days: 200 },
    { name: '옛날공방', regNo: '617-05-33021', rep: '서지후', status: 'WITHDRAWN', nts: 'CLOSED', services: [{ service: 'HOTDEAL', status: 'OFF' }, { service: 'AD', status: 'OFF' }], nickname: '(탈퇴)', days: 300 },
];

function mask(regNo: string): string {
    const [a, , c] = regNo.split('-');
    return `${a}-**-***${(c ?? '').slice(-2)}`;
}

function ntsOf(status: NtsBizStatus, checkedAgoMs: number): NtsCheck {
    return {
        status,
        checkedAt: ago(checkedAgoMs),
        taxType: status === 'UNAVAILABLE' ? null : status === 'CLOSED' ? '간이과세자' : '일반과세자',
        closedOn: status === 'PAUSED' || status === 'CLOSED' ? new Date(Date.now() - 20 * DAY).toISOString().slice(0, 10) : null,
        message: status === 'UNAVAILABLE' ? '국세청 상태조회 서비스에 연결하지 못했어요(응답 시간 초과).' : null,
    };
}

interface Row {
    detail: BusinessDetail;
    plain: Omit<BusinessReveal, 'hideAt'>;
}

let state: Row[] | null = null;

function ensure(): Row[] {
    if (state) return state;
    if (mockFlags().has('biz-empty')) return (state = []);
    state = SEEDS.map((s, i) => {
        const appliedAt = ago(s.days * DAY);
        const history: BusinessHistory[] = [
            { at: appliedAt, action: 'APPLIED', actor: s.nickname, note: null },
            { at: ago(s.days * DAY - 60_000), action: 'NTS_CHECKED', actor: '시스템', note: null },
        ];
        if (s.status === 'APPROVED' || s.status === 'SUSPENDED') history.push({ at: ago((s.days - 1) * DAY), action: 'APPROVED', actor: ME, note: null });
        if (s.status === 'REJECTED') history.push({ at: ago((s.days - 1) * DAY), action: 'REJECTED', actor: ME, note: '사업자등록증 글자가 흐려 확인할 수 없어요.' });
        if (s.status === 'SUSPENDED') history.push({ at: ago(5 * DAY), action: 'SUSPENDED', actor: '최고 관리자', note: '허위 할인율 표기 반복' });
        const detail: BusinessDetail = {
            id: `biz-${i + 1}`,
            name: s.name,
            maskedRegNo: mask(s.regNo),
            status: s.status,
            services: s.services,
            applicant: { memberId: `m-${1000 + i}`, nickname: s.nickname },
            appliedAt,
            nts: ntsOf(s.nts, s.days * DAY - 60_000),
            masked: { representative: `${s.rep.slice(0, 1)}*${s.rep.slice(-1)}`, address: '서울특별시 ***구 ***로 **', phone: '010-****-**12', email: 'bi***@****.com' },
            certificate:
                s.status === 'WITHDRAWN'
                    ? null
                    : { fileName: `사업자등록증_${s.name}.pdf`, mimeType: 'application/pdf', size: 412_000 + i * 1300, uploadedAt: appliedAt, lastViewedBy: i % 2 ? ME : null, lastViewedAt: i % 2 ? ago(HOUR * 3) : null },
            serviceCards: s.services.map((sv) => ({ ...sv, liveCount: sv.status === 'APPROVED' ? (s.live ?? 0) : 0, pendingCount: sv.status === 'APPROVED' ? (s.impact?.awaitingPayment ?? 0) : 0 })),
            impact: s.impact ?? { awaitingPayment: 0, scheduled: 0, running: 0 },
            rejection: s.status === 'REJECTED' ? { code: 'DOC_UNREADABLE', text: '사업자등록증 글자가 흐려 확인할 수 없어요. 선명한 사본으로 다시 신청해 주세요.' } : null,
            suspension: s.status === 'SUSPENDED' ? { code: 'POLICY_VIOLATION', text: '허위 할인율 표기 반복', at: ago(5 * DAY) } : null,
            history,
        };
        return { detail, plain: { regNo: s.regNo, representative: s.rep, address: '서울특별시 마포구 월드컵북로 12, 3층', phone: '010-2345-6712', email: 'biz@example.com' } };
    });
    return state;
}

function find(id: string): Row {
    const row = ensure().find((r) => r.detail.id === id);
    if (!row) throw MOCK_ERRORS.notFound('BUSINESS_NOT_FOUND', '사업자를 찾을 수 없어요.');
    return row;
}

function log(row: Row, action: BusinessHistory['action'], note: string | null = null): void {
    row.detail.history.push({ at: new Date().toISOString(), action, actor: ME, note });
}

function stale(): never {
    throw MOCK_ERRORS.conflict('BUSINESS_STATE_CHANGED', '이미 바뀌었어요. 새로 불러올게요.');
}

export async function mockBusinessList(query: BusinessQuery): Promise<BusinessPage> {
    await mockDelay();
    if (mockFlags().has('biz-list-fail')) throw MOCK_ERRORS.internal();
    const rows = ensure().map((r) => r.detail);
    const q = query.q?.trim().toLowerCase();
    const filtered = rows.filter((d) => {
        if (query.service && !d.services.some((s) => s.service === query.service && s.status !== 'OFF')) return false;
        if (q && !`${d.name} ${d.maskedRegNo} ${d.applicant.nickname}`.toLowerCase().includes(q)) return false;
        return true;
    });
    const counts = Object.fromEntries(BUSINESS_STATUSES.map((s) => [s, filtered.filter((d) => d.status === s).length])) as Record<BusinessStatus, number>;
    const inStatus = filtered.filter((d) => !query.status || d.status === query.status).sort((a, b) => a.appliedAt.localeCompare(b.appliedAt));
    const start = query.page * query.size;
    const items = inStatus.slice(start, start + query.size).map(({ masked: _m, certificate: _c, serviceCards: _s, impact: _i, rejection: _r, suspension: _u, history: _h, ...row }) => row);
    return mockClone({ items, total: inStatus.length, counts });
}

export async function mockBusinessDetail(id: string): Promise<BusinessDetail> {
    await mockDelay();
    return mockClone(find(id).detail);
}

export async function mockBusinessRecheckNts(id: string): Promise<BusinessDetail> {
    await mockDelay();
    const row = find(id);
    const next: NtsBizStatus = mockFlags().has('nts-down') ? 'UNAVAILABLE' : row.detail.nts.status === 'UNAVAILABLE' ? 'CONTINUING' : row.detail.nts.status;
    row.detail.nts = ntsOf(next, 0);
    log(row, 'NTS_CHECKED', null);
    return mockClone(row.detail);
}

export async function mockBusinessApprove(id: string, body: BusinessApproveRequest): Promise<BusinessDetail> {
    await mockDelay();
    const row = find(id);
    if (row.detail.status !== 'PENDING') stale();
    const nts = row.detail.nts.status;
    if (nts === 'PAUSED' || nts === 'CLOSED') throw MOCK_ERRORS.conflict('BUSINESS_NTS_NOT_ACTIVE', '휴업·폐업 사업자는 승인할 수 없어요.');
    if (nts === 'UNAVAILABLE' && !body.ntsOverrideReason?.trim()) throw MOCK_ERRORS.validation({ ntsOverrideReason: '국세청 조회가 안 될 때는 승인 사유를 적어 주세요.' });
    row.detail.status = 'APPROVED';
    row.detail.services = row.detail.services.map((s) => (s.status === 'PENDING' ? { ...s, status: 'APPROVED' } : s));
    row.detail.serviceCards = row.detail.serviceCards.map((s) => (s.status === 'PENDING' ? { ...s, status: 'APPROVED' } : s));
    log(row, 'APPROVED', body.ntsOverrideReason ?? null);
    return mockClone(row.detail);
}

export async function mockBusinessReject(id: string, body: BusinessRejectRequest): Promise<BusinessDetail> {
    await mockDelay();
    const row = find(id);
    if (row.detail.status !== 'PENDING') stale();
    row.detail.status = 'REJECTED';
    row.detail.rejection = { ...body };
    row.detail.services = row.detail.services.map((s) => (s.status === 'PENDING' ? { ...s, status: 'REJECTED' } : s));
    row.detail.serviceCards = row.detail.serviceCards.map((s) => (s.status === 'PENDING' ? { ...s, status: 'REJECTED' } : s));
    log(row, 'REJECTED', body.text);
    return mockClone(row.detail);
}

export async function mockBusinessSuspend(id: string, body: BusinessSuspendRequest): Promise<BusinessDetail> {
    await mockDelay();
    const row = find(id);
    if (row.detail.status !== 'APPROVED') stale();
    row.detail.status = 'SUSPENDED';
    row.detail.suspension = { code: body.code, text: body.text, at: new Date().toISOString() };
    const i = row.detail.impact;
    const done = [
        body.pendingAction === 'CANCEL' && i.awaitingPayment ? `결제·입금 대기 ${i.awaitingPayment}건 취소` : '',
        body.scheduledAction === 'REFUND_FULL' && i.scheduled ? `시작 전 ${i.scheduled}건 전액 환불` : '',
        i.running ? `진행 중 ${i.running}건 긴급 내림${body.runningAction === 'TAKEDOWN_PRORATE' ? ' + 일할 환불 심사' : ''}` : '',
    ].filter(Boolean);
    row.detail.impact = { awaitingPayment: body.pendingAction === 'CANCEL' ? 0 : i.awaitingPayment, scheduled: body.scheduledAction === 'REFUND_FULL' ? 0 : i.scheduled, running: 0 };
    log(row, 'SUSPENDED', [body.text, ...done].join(' · '));
    return mockClone(row.detail);
}

export async function mockBusinessReveal(id: string, reason: string): Promise<BusinessReveal> {
    await mockDelay();
    const row = find(id);
    log(row, 'REVEALED', reason);
    return { ...row.plain, hideAt: new Date(Date.now() + REVEAL_SECONDS * 1000).toISOString() };
}

/** 사업자등록증 열기 — 열람 기록을 남기고 볼 주소를 준다(목업은 그림 한 장) */
export async function mockBusinessCertificate(id: string): Promise<{ url: string; mimeType: string }> {
    await mockDelay();
    const row = find(id);
    if (!row.detail.certificate) throw MOCK_ERRORS.notFound('BUSINESS_CERT_NOT_FOUND', '사업자등록증 사본이 없어요(파기됨).');
    row.detail.certificate.lastViewedBy = ME;
    row.detail.certificate.lastViewedAt = new Date().toISOString();
    log(row, 'CERT_VIEWED');
    const svg = `<svg xmlns="http://www.w3.org/2000/svg" width="420" height="594" viewBox="0 0 420 594"><rect width="420" height="594" fill="#fdfcf7" stroke="#bbb"/><text x="210" y="70" font-size="26" text-anchor="middle" font-family="sans-serif">사업자등록증</text><text x="210" y="100" font-size="12" text-anchor="middle" fill="#888" font-family="sans-serif">(목업 미리보기)</text>${[
        `상호: ${row.detail.name}`,
        `등록번호: ${row.detail.maskedRegNo}`,
        `대표자: ${row.detail.masked.representative}`,
        '개업연월일: 2021년 03월 02일',
        '사업장 소재지: 서울특별시 ***구',
        '업태: 도소매 · 종목: 전자상거래',
    ]
        .map((line, n) => `<text x="40" y="${170 + n * 36}" font-size="15" font-family="sans-serif">${line}</text>`)
        .join('')}<circle cx="330" cy="500" r="40" fill="none" stroke="#c33" stroke-width="3"/><text x="330" y="505" font-size="13" fill="#c33" text-anchor="middle" font-family="sans-serif">세무서장</text></svg>`;
    return { url: `data:image/svg+xml;charset=utf-8,${encodeURIComponent(svg)}`, mimeType: 'image/svg+xml' };
}
