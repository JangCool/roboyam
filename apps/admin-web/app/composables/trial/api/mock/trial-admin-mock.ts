// MOCK: 체험단 관리 서버(trial-group-contract 「구현 결정」 7·10 — admin)를 붙이기 전까지 화면을 그리는 목업 — 이 탭 메모리에만 남는다(새로 고치면 처음 값). 모양은 서버 `TrialViews`·`ReviewQueueService` 와 같다.
// 서버가 붙으면 이 파일과 `useTrialApi.ts`·`useReviewQueueApi.ts` 의 `// MOCK:` 줄을 지운다(`TRIAL_ADMIN_MOCK` 분기 아래의 실제 호출만 남는다).
// 확인할 일 목록의 칭호 신청 줄은 목업이 아니라 이미 붙은 칭호 서버(`GET /api/admin/titles/applications`)에서 받아 합친다 — 칭호 심사가 목업 때문에 멈추지 않게.

import { ApiError } from '~/composables/common/useApiClient';
import type { ReviewQueueItem, ReviewQueueList, ReviewQueueStatus, ReviewQueueType } from '~/composables/review/api/useReviewQueueApi.types';
import type { AdminTitleApplication } from '~/composables/title/api/useTitleApi.types';
import type {
    AdminTrialApplicantPage,
    AdminTrialDamageReport,
    AdminTrialDecideRequest,
    AdminTrialDetail,
    AdminTrialParticipant,
    AdminTrialRequest,
    AdminTrialRow,
    TrialMember,
    TrialPhoto,
    TrialPhotoKind,
    TrialSettings,
    TrialStatus,
    TrialUploaded,
} from '~/composables/trial/api/useTrialApi.types';

/** 목업으로 그릴지 — 서버가 붙으면 이 상수와 쓰는 줄을 지운다 */
export const TRIAL_ADMIN_MOCK: boolean = true;

const HOUR = 3600_000;
const DAY = 24 * HOUR;
const at = (offset: number) => new Date(Date.now() + offset).toISOString();
const delay = () => new Promise<void>((resolve) => setTimeout(resolve, 250 + Math.random() * 300));
const clone = <T>(v: T): T => JSON.parse(JSON.stringify(v)) as T;
const photo = (seed: string, kind: TrialPhotoKind): TrialPhoto => ({ id: `ph-${seed}`, kind, url: `https://picsum.photos/seed/irobit-${seed}/480/360`, createdAt: at(-DAY) });
const member = (n: number, nickname: string): TrialMember => ({ id: `700000000000000000${n}`, nickname, avatarUrl: null });
const validation = (fields: Record<string, string>) => new ApiError({ status: 400, code: 'VALIDATION', message: '입력한 값을 확인해 주세요.', fields });
const notFound = () => new ApiError({ status: 404, code: 'TRIAL_NOT_FOUND', message: '체험단 모집을 찾을 수 없어요.' });

const ADVERTISERS = [
    { id: '5100000000000000001', name: '로보클린코리아' },
    { id: '5100000000000000002', name: '프린트펜' },
];
const BOARD = { id: '6000000000000000001', slug: 'review', name: '사용기' };
const COUNTS0 = { applied: 0, selected: 0, shipped: 0, received: 0, reviewed: 0, returned: 0, kept: 0, unreturned: 0 };

let settings: TrialSettings = {
    givePriceLimit: 50000,
    disclosureText: '이 글은 {광고주}로부터 제품을 무상으로 {제공/대여}받아 작성한 글입니다',
    sponsorDisclosureText: '이 글은 업체로부터 제품을 제공받아 작성한 글입니다',
    returnRemindFirstDays: 0,
    returnRemindSecondDays: 7,
    unreturnedDays: 14,
    reviewDays: 14,
    lateBanDays: 90,
    lateRevokeStrikes: 2,
    proofBonusPoints: 100,
    proofPhotoDays: 30,
    stockKeepDays: 60,
};

function participant(id: string, m: TrialMember, extra: Partial<AdminTrialParticipant>): AdminTrialParticipant {
    return {
        id,
        applicationId: `ap-${id}`,
        member: m,
        quota: 'REVIEWER',
        recipient: '김로봇',
        phone: '010-1234-5678',
        postcode: '06236',
        address1: '서울 강남구 테헤란로 123',
        address2: '4층',
        memo: null,
        addressPurged: false,
        carrier: null,
        trackingNo: null,
        shippedAt: null,
        shippedBy: null,
        receivedAt: null,
        receivedBy: null,
        reviewPost: null,
        reviewDueAt: null,
        returnDueAt: null,
        returnedAt: null,
        kept: null,
        reviewLateAt: null,
        unreturnedAt: null,
        contactProvidedAt: null,
        photos: [],
        ...extra,
    };
}

const DAMAGE: AdminTrialDamageReport = {
    id: 'td-1',
    participantId: 'tp-2',
    member: member(3, '공구덕후'),
    reporterType: 'OPERATOR',
    description: '반납받은 제품의 노즐 끝이 휘어 있어요.',
    status: 'OBJECTED',
    objectionDueAt: at(6 * DAY),
    objection: '받을 때부터 노즐이 약간 휘어 있었어요. 받은 날 찍은 사진이 있어요.',
    objectedAt: at(-2 * HOUR),
    verdict: null,
    decisionNote: null,
    decidedAt: null,
    photos: [photo('damage-1', 'DAMAGE'), photo('damage-2', 'DAMAGE')],
    createdAt: at(-1 * DAY),
};

let trials: AdminTrialDetail[] = [
    {
        id: '8100000000000000001',
        title: '로봇청소기 R7 물걸레 체험단 — 2주 써 보고 사용기 남겨 주세요',
        productName: '로보클린 R7 물걸레 로봇청소기',
        productPrice: 489000,
        form: 'LEND',
        shipper: 'OPERATOR',
        status: 'RECRUITING',
        advertiser: ADVERTISERS[0]!,
        bizApplication: { id: '9100000000000000001', name: 'TRIAL' },
        reviewBoard: BOARD,
        recruitCount: 5,
        reviewerShare: 60,
        reviewerSlots: 3,
        reviewerGoldHours: 48,
        reviewerSilverHours: 24,
        applyStartAt: at(-2 * DAY),
        applyEndAt: at(5 * DAY),
        reviewDays: 14,
        returnDays: 21,
        description: '물걸레 회전 패드와 자동 먼지 비움 스테이션이 있는 신제품입니다.',
        imageKey: 'trial/r7.jpg',
        imageUrl: 'https://picsum.photos/seed/irobit-trial-r7/640/400',
        priceUnit: 'PER_PERSON',
        unitPrice: 0,
        shippingFeePerPerson: 0,
        selectedCount: null,
        amount: null,
        counts: { ...COUNTS0, applied: 6 },
        stock: null,
        participants: [],
        damageReports: [],
        createdAt: at(-4 * DAY),
        updatedAt: at(-4 * DAY),
    },
    {
        id: '8100000000000000005',
        title: '휴대용 3D 펜 체험단(대여형)',
        productName: '프린트펜 3D',
        productPrice: 89000,
        form: 'LEND',
        shipper: 'OPERATOR',
        status: 'RUNNING',
        advertiser: ADVERTISERS[1]!,
        bizApplication: { id: '9100000000000000005', name: 'TRIAL' },
        reviewBoard: BOARD,
        recruitCount: 3,
        reviewerShare: 60,
        reviewerSlots: 2,
        reviewerGoldHours: 48,
        reviewerSilverHours: 24,
        applyStartAt: at(-40 * DAY),
        applyEndAt: at(-30 * DAY),
        reviewDays: 14,
        returnDays: 21,
        description: '어디서나 쓰는 휴대용 3D 펜입니다.',
        imageKey: null,
        imageUrl: null,
        priceUnit: 'PER_PERSON',
        unitPrice: 0,
        shippingFeePerPerson: 0,
        selectedCount: 3,
        amount: 0,
        counts: { applied: 22, selected: 3, shipped: 2, received: 2, reviewed: 1, returned: 1, kept: 0, unreturned: 0 },
        stock: { quantity: 4, note: '예비 1개 포함', receivedAt: at(-28 * DAY), keepUntil: at(32 * DAY), returnCarrier: null, returnTrackingNo: null, returnShippedAt: null, photos: [photo('stock-1', 'STOCK')] },
        participants: [
            participant('tp-1', member(1, '로봇청소왕'), {}),
            participant('tp-2', member(3, '공구덕후'), {
                quota: 'GENERAL',
                carrier: '우체국택배',
                trackingNo: '1234567890123',
                shippedAt: at(-20 * DAY),
                shippedBy: 'OPERATOR',
                receivedAt: at(-18 * DAY),
                receivedBy: 'MEMBER',
                reviewPost: { id: '7700000000000000001', name: '[체험단] 3D 펜 2주 써 본 솔직 후기' },
                reviewDueAt: at(-4 * DAY),
                returnDueAt: at(-2 * DAY),
                returnedAt: at(-1 * DAY),
                photos: [photo('return-1', 'RETURN')],
            }),
            participant('tp-3', member(2, '납땜장인'), {
                carrier: 'CJ대한통운',
                trackingNo: '6543210987',
                shippedAt: at(-20 * DAY),
                shippedBy: 'OPERATOR',
                receivedAt: at(-19 * DAY),
                receivedBy: 'AUTO',
                reviewDueAt: at(-5 * DAY),
                returnDueAt: at(-3 * DAY),
                kept: false,
                reviewLateAt: at(-4 * DAY),
            }),
        ],
        damageReports: [DAMAGE],
        createdAt: at(-45 * DAY),
        updatedAt: at(-1 * DAY),
    },
];

let applicants: AdminTrialApplicantPage = {
    recruitCount: 5,
    reviewerSlots: 3,
    generalSlots: 2,
    items: [
        { id: 'ap-1', member: member(1, '로봇청소왕'), status: 'APPLIED', quota: 'REVIEWER', reviewerTier: 'GOLD', recommended: true, stats: { reviewPosts: 34, averageLikes: 12.4, keptRate: 100, judged: 12 }, consents: ['UNRETURNED_CONTACT', 'PRODUCT_CHARGE', 'DAMAGE_CHARGE'], createdAt: at(-47 * HOUR) },
        { id: 'ap-2', member: member(2, '납땜장인'), status: 'APPLIED', quota: 'REVIEWER', reviewerTier: 'SILVER', recommended: false, stats: { reviewPosts: 12, averageLikes: 6.1, keptRate: 90, judged: 10 }, consents: [], createdAt: at(-30 * HOUR) },
        { id: 'ap-3', member: member(4, '새싹메이커'), status: 'APPLIED', quota: 'REVIEWER', reviewerTier: 'BRONZE', recommended: false, stats: { reviewPosts: 4, averageLikes: 3, keptRate: 75, judged: 4 }, consents: [], createdAt: at(-20 * HOUR) },
        { id: 'ap-4', member: member(3, '공구덕후'), status: 'APPLIED', quota: 'GENERAL', reviewerTier: null, recommended: true, stats: { reviewPosts: 2, averageLikes: 1.5, keptRate: null, judged: 0 }, consents: [], createdAt: at(-10 * HOUR) },
        { id: 'ap-5', member: member(5, '메카닉'), status: 'APPLIED', quota: 'GENERAL', reviewerTier: null, recommended: false, stats: { reviewPosts: 7, averageLikes: 9.2, keptRate: null, judged: 0 }, consents: [], createdAt: at(-5 * HOUR) },
        { id: 'ap-6', member: member(6, '드론러버'), status: 'APPLIED', quota: 'GENERAL', reviewerTier: null, recommended: false, stats: { reviewPosts: 0, averageLikes: 0, keptRate: null, judged: 0 }, consents: [], createdAt: at(-2 * HOUR) },
    ],
};

let queue: ReviewQueueItem[] = [
    { type: 'PURCHASE_PROOF', id: 'pp-1', status: 'PENDING', title: '로봇청소기 X5 한 달 사용기 — 내 돈 내 산', member: member(2, '납땜장인'), at: at(-3 * HOUR), note: null, refs: { postId: '7700000000000000011', boardSlug: 'review' } },
    { type: 'RETURN_CHECK', id: 'tp-3', status: 'PENDING', title: '휴대용 3D 펜 체험단(대여형)', member: member(2, '납땜장인'), at: at(-3 * DAY), note: null, refs: { trialId: '8100000000000000005' } },
    { type: 'DAMAGE_REPORT', id: 'td-1', status: 'OBJECTED', title: '휴대용 3D 펜 체험단(대여형)', member: member(3, '공구덕후'), at: at(-1 * DAY), note: DAMAGE.objection, refs: { trialId: '8100000000000000005', participantId: 'tp-2' } },
    { type: 'SPONSOR_HIDDEN', id: '7700000000000000031', status: 'PENDING', title: '무선 이어폰 내돈내산 후기', member: member(5, '메카닉'), at: at(-1 * HOUR), note: null, refs: { postId: '7700000000000000031', boardSlug: 'review', reportCount: '3' } },
];

const PENDING_STATES = new Set(['PENDING', 'REPORTED', 'OBJECTED', 'AWAITING']);

function find(id: string): AdminTrialDetail {
    const t = trials.find((x) => x.id === id);
    if (!t) throw notFound();
    return t;
}

function row(t: AdminTrialDetail): AdminTrialRow {
    const { id, title, productName, form, shipper, status, advertiser, recruitCount, reviewerShare, selectedCount, applyStartAt, applyEndAt, reviewBoard, amount, createdAt } = t;
    return { id, title, productName, form, shipper, status, advertiser, recruitCount, reviewerShare, applicantCount: t.counts.applied, selectedCount, applyStartAt, applyEndAt, reviewBoard, amount, createdAt };
}

function participantOf(id: string): { t: AdminTrialDetail; p: AdminTrialParticipant } {
    for (const t of trials) {
        const p = t.participants.find((x) => x.id === id);
        if (p) return { t, p };
    }
    throw new ApiError({ status: 404, code: 'TRIAL_PARTICIPANT_NOT_FOUND', message: '뽑힌 줄을 찾을 수 없어요.' });
}

function settle(type: ReviewQueueType, id: string, status: string): void {
    queue = queue.map((q) => (q.type === type && q.id === id ? { ...q, status, at: new Date().toISOString() } : q));
}

let photoSeq = 0;

export default class TrialAdminMock {
    static async list(status: TrialStatus | ''): Promise<AdminTrialRow[]> {
        await delay();
        return clone(trials.filter((t) => !status || t.status === status).map(row));
    }

    static async detail(id: string): Promise<AdminTrialDetail> {
        await delay();
        return clone(find(id));
    }

    static async save(id: string | null, body: AdminTrialRequest): Promise<AdminTrialDetail> {
        await delay();
        if (id && body.action) {
            const t = find(id);
            const next: Record<string, TrialStatus> = { PUBLISH: 'RECRUITING', CLOSE_APPLY: 'SELECTING', CLOSE: 'CLOSED', CANCEL: 'CANCELLED' };
            t.status = next[body.action] ?? t.status;
            return clone(t);
        }
        if (body.form === 'GIVE' && body.productPrice > settings.givePriceLimit) throw validation({ form: `제공형은 제품값 ${settings.givePriceLimit.toLocaleString('ko-KR')}원 이하만 열 수 있어요. 대여형으로 모집해 주세요.` });
        if (!body.title.trim()) throw validation({ title: '제목을 넣어 주세요.' });
        const advertiser = ADVERTISERS.find((a) => a.id === body.advertiserId) ?? { id: body.advertiserId, name: '광고주' };
        const base = id ? find(id) : null;
        const { action: _action, ...fields } = body;
        const next: AdminTrialDetail = {
            ...(base ?? {
                id: `81${Date.now()}`,
                status: 'DRAFT' as const,
                bizApplication: null,
                reviewerSlots: 0,
                imageUrl: null,
                priceUnit: 'PER_PERSON' as const,
                unitPrice: 0,
                shippingFeePerPerson: 0,
                selectedCount: null,
                amount: null,
                counts: COUNTS0,
                stock: null,
                participants: [],
                damageReports: [],
                createdAt: new Date().toISOString(),
            }),
            ...fields,
            advertiser,
            reviewBoard: base?.reviewBoard.id === body.reviewBoardId ? base.reviewBoard : { ...BOARD, id: body.reviewBoardId },
            reviewerSlots: Math.round((body.recruitCount * body.reviewerShare) / 100),
            returnDays: body.form === 'LEND' ? body.returnDays : null,
            updatedAt: new Date().toISOString(),
        };
        trials = base ? trials.map((t) => (t.id === base.id ? next : t)) : [next, ...trials];
        return clone(next);
    }

    static async applications(): Promise<AdminTrialApplicantPage> {
        await delay();
        return clone(applicants);
    }

    static async recommend(applicationId: string, on: boolean): Promise<void> {
        await delay();
        applicants = { ...applicants, items: applicants.items.map((a) => (a.id === applicationId ? { ...a, recommended: on } : a)) };
    }

    static async select(id: string, applicationIds: string[]): Promise<AdminTrialDetail> {
        await delay();
        const t = find(id);
        if (applicationIds.length > t.recruitCount) throw new ApiError({ status: 400, code: 'TRIAL_SELECT_OVER', message: `인원(${t.recruitCount}명)보다 많이 뽑을 수 없어요.` });
        applicants = { ...applicants, items: applicants.items.map((a) => ({ ...a, status: applicationIds.includes(a.id) ? 'SELECTED' : 'NOT_SELECTED' })) };
        t.status = 'RUNNING';
        t.selectedCount = applicationIds.length;
        t.amount = t.priceUnit === 'PER_PERSON' ? applicationIds.length * t.unitPrice + (t.shipper === 'OPERATOR' ? applicationIds.length * t.shippingFeePerPerson : 0) : t.unitPrice;
        t.participants = applicants.items.filter((a) => applicationIds.includes(a.id)).map((a, i) => participant(`tp-new-${i}`, a.member, { quota: a.quota }));
        return clone(t);
    }

    static async uploadPhoto(kind: TrialPhotoKind | 'COVER', file: File): Promise<TrialUploaded> {
        await delay();
        photoSeq += 1;
        return { id: `up-${photoSeq}`, kind, key: `trial/mock-${photoSeq}.jpg`, url: URL.createObjectURL(file) };
    }

    static async stock(id: string, quantity: number, note: string | null, photoIds: string[]): Promise<AdminTrialDetail> {
        await delay();
        if (photoIds.length === 0) throw validation({ photoIds: '입고 상태 사진을 1장 이상 올려 주세요.' });
        const t = find(id);
        t.stock = { quantity, note, receivedAt: new Date().toISOString(), keepUntil: at(settings.stockKeepDays * DAY), returnCarrier: null, returnTrackingNo: null, returnShippedAt: null, photos: photoIds.map((p) => photo(p, 'STOCK')) };
        return clone(t);
    }

    static async shipment(participantId: string, carrier: string, trackingNo: string): Promise<void> {
        await delay();
        if (!trackingNo.trim()) throw validation({ trackingNo: '송장 번호를 넣어 주세요.' });
        Object.assign(participantOf(participantId).p, { carrier, trackingNo, shippedAt: new Date().toISOString(), shippedBy: 'OPERATOR' });
    }

    static async returned(participantId: string, photoIds: string[]): Promise<void> {
        await delay();
        const { p } = participantOf(participantId);
        Object.assign(p, { returnedAt: new Date().toISOString(), photos: [...p.photos, ...photoIds.map((x) => photo(x, 'RETURN'))] });
        settle('RETURN_CHECK', participantId, 'RETURNED');
    }

    static async returnShipment(id: string, carrier: string, trackingNo: string): Promise<AdminTrialDetail> {
        await delay();
        const t = find(id);
        if (!t.stock) throw new ApiError({ status: 409, code: 'TRIAL_NO_STOCK', message: '입고 기록이 없어요.' });
        Object.assign(t.stock, { returnCarrier: carrier, returnTrackingNo: trackingNo, returnShippedAt: new Date().toISOString() });
        return clone(t);
    }

    static async damage(participantId: string, description: string, photoIds: string[]): Promise<AdminTrialDamageReport> {
        await delay();
        if (photoIds.length === 0) throw validation({ photoIds: '사진을 1장 이상 올려 주세요.' });
        const { t, p } = participantOf(participantId);
        const report: AdminTrialDamageReport = {
            ...DAMAGE,
            id: `td-${Date.now()}`,
            participantId,
            member: p.member,
            description,
            photos: photoIds.map((x) => photo(x, 'DAMAGE')),
            status: 'REPORTED',
            objection: null,
            objectedAt: null,
            createdAt: new Date().toISOString(),
            objectionDueAt: at(7 * DAY),
        };
        t.damageReports = [report, ...t.damageReports];
        return clone(report);
    }

    static async decide(reportId: string, body: AdminTrialDecideRequest): Promise<AdminTrialDamageReport> {
        await delay();
        for (const t of trials) {
            const r = t.damageReports.find((x) => x.id === reportId);
            if (r) {
                Object.assign(r, { verdict: body.verdict, decisionNote: body.note || null, status: 'DECIDED', decidedAt: new Date().toISOString() });
                settle('DAMAGE_REPORT', reportId, body.verdict);
                return clone(r);
            }
        }
        throw new ApiError({ status: 404, code: 'TRIAL_DAMAGE_NOT_FOUND', message: '파손 신고를 찾을 수 없어요.' });
    }

    static async reviewProof(id: string, approve: boolean, reason: string): Promise<void> {
        await delay();
        if (!approve && !reason.trim()) throw validation({ reason: '거절 사유를 적어 주세요.' });
        settle('PURCHASE_PROOF', id, approve ? 'APPROVED' : 'REJECTED');
    }

    static async sponsor(postId: string, confirm: boolean): Promise<void> {
        await delay();
        settle('SPONSOR_HIDDEN', postId, confirm ? 'CONFIRMED' : 'DISMISSED');
    }

    static async settings(): Promise<TrialSettings> {
        await delay();
        return clone(settings);
    }

    static async saveSettings(body: TrialSettings): Promise<TrialSettings> {
        await delay();
        if (!(body.returnRemindSecondDays > body.returnRemindFirstDays)) throw validation({ returnRemindSecondDays: '두 번째 알림은 첫 알림보다 늦어야 해요.' });
        if (!(body.unreturnedDays > body.returnRemindSecondDays)) throw validation({ unreturnedDays: '미반납 확정은 두 번째 알림보다 늦어야 해요.' });
        settings = { ...body };
        return clone(settings);
    }

    /** 확인할 일 — 체험단 줄은 목업, 칭호 신청 줄은 실제 칭호 서버(`titleApplications`)에서 받아 합친다 */
    static async reviewQueue(type: ReviewQueueType | null, status: ReviewQueueStatus, titleApplications: () => Promise<AdminTitleApplication[]>): Promise<ReviewQueueList> {
        await delay();
        let titles: ReviewQueueItem[] = [];
        try {
            titles = (await titleApplications()).map((a) => ({
                type: 'TITLE_APPLICATION' as const,
                id: a.id,
                status: a.status,
                title: a.title.label,
                member: { id: a.member.id, nickname: a.member.nickname, avatarUrl: null },
                at: a.status === 'PENDING' ? a.createdAt : a.reviewedAt,
                note: a.reason,
                refs: { titleKey: a.title.key },
            }));
        } catch (e) {
            // 칭호 심사 권한이 없으면 칭호 줄만 빠진다
            if (!(e instanceof ApiError && e.status === 403)) throw e;
        }
        const all = [...titles, ...queue];
        const pendingOf = (q: ReviewQueueItem) => PENDING_STATES.has(q.status);
        const items = all.filter((q) => (!type || q.type === type) && (status === 'ALL' || (status === 'PENDING' ? pendingOf(q) : !pendingOf(q))));
        items.sort((a, b) => Date.parse(b.at ?? '') - Date.parse(a.at ?? ''));
        const pending: Partial<Record<ReviewQueueType, number>> = {};
        for (const q of all) if (pendingOf(q)) pending[q.type] = (pending[q.type] ?? 0) + 1;
        return clone({ items, pending });
    }
}
