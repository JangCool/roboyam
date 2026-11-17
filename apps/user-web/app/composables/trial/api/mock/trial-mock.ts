// MOCK: 체험단 서버(trial-group-contract 「구현 결정」 8·9)를 붙이기 전까지 화면을 그리는 목업 — 이 탭 메모리에만 남는다(새로 고치면 처음 값). 모양은 서버 `TrialViews` 와 같다.
// 서버가 붙으면 이 파일과 `useTrialApi.ts`·`useBusinessTrialApi.ts`·`usePostApi.ts`(구매 인증 올리기) 의 `// MOCK:` 줄을 지운다(`TRIAL_MOCK` 분기 아래의 실제 호출만 남는다).
// 개발 스위치: `?mock=trial-error`(모든 체험단 요청 실패) · `?mock=trial-empty`(목록·내 체험단 비움) · `?mock=trial-identity`(신청 막힘 — 본인인증) · `?mock=trial-banned`(신청 금지 중)

import { ApiError } from '~/composables/common/useApiClient';
import { mockClone, mockDelay, mockFlags, MOCK_ERRORS } from '~/composables/common/mock/mock-util';
import type {
    BizTrialDamageRequest,
    BizTrialPhotoKind,
    BizTrialReturnedRequest,
    BizTrialRow,
    BizTrialShipmentRequest,
    BizTrialUploaded,
    BizTrialView,
} from '~/composables/business/api/useBusinessTrialApi.types';
import type {
    MyTrial,
    PurchaseProof,
    PurchaseProofResult,
    TrialApplyRequest,
    TrialCard,
    TrialListStatus,
    TrialMember,
    TrialMine,
    TrialPhoto,
    TrialPublicDetail,
    TrialWritable,
} from '~/composables/trial/api/useTrialApi.types';

/** 목업으로 그릴지 — 서버가 붙으면 이 상수와 쓰는 줄을 지운다 */
export const TRIAL_MOCK: boolean = true;

const HOUR = 3600_000;
const DAY = 24 * HOUR;
const at = (offsetMs: number) => new Date(Date.now() + offsetMs).toISOString();
const PHOTO = (seed: string, kind = 'DAMAGE'): TrialPhoto => ({ id: `ph-${seed}`, kind, url: `https://picsum.photos/seed/irobit-${seed}/480/360`, createdAt: at(-DAY) });
const REVIEW_BOARD = { id: '6000000000000000001', slug: 'review', name: '사용기' };
const member = (n: number, nickname: string): TrialMember => ({ id: `700000000000000000${n}`, nickname, avatarUrl: null });

function card(c: Omit<TrialCard, 'ad' | 'goldOpenAt' | 'silverOpenAt' | 'reviewBoard'>): TrialCard {
    const start = Date.parse(c.applyStartAt);
    return { ...c, ad: true, goldOpenAt: new Date(start - 48 * HOUR).toISOString(), silverOpenAt: new Date(start - 24 * HOUR).toISOString(), reviewBoard: REVIEW_BOARD };
}

const CARDS: TrialCard[] = [
    card({
        id: '8100000000000000001',
        title: '로봇청소기 R7 물걸레 체험단 — 2주 써 보고 사용기 남겨 주세요',
        productName: '로보클린 R7 물걸레 로봇청소기',
        productPrice: 489000,
        form: 'LEND',
        status: 'RECRUITING',
        advertiserName: '로보클린코리아',
        imageUrl: 'https://picsum.photos/seed/irobit-trial-r7/640/400',
        recruitCount: 5,
        applicantCount: 38,
        applyStartAt: at(-2 * DAY),
        applyEndAt: at(5 * DAY),
    }),
    card({
        id: '8100000000000000002',
        title: '무선 납땜 인두 미니 — 제공형 체험단',
        productName: '솔더핏 미니 무선 인두',
        productPrice: 39000,
        form: 'GIVE',
        status: 'RECRUITING',
        advertiserName: '솔더핏',
        imageUrl: null,
        recruitCount: 10,
        applicantCount: 12,
        applyStartAt: at(3 * DAY),
        applyEndAt: at(10 * DAY),
    }),
    card({
        id: '8100000000000000003',
        title: '코딩 로봇 키트 체험단(진행 중)',
        productName: '아이봇 코딩 키트 S',
        productPrice: 45000,
        form: 'GIVE',
        status: 'RUNNING',
        advertiserName: '아이봇에듀',
        imageUrl: 'https://picsum.photos/seed/irobit-trial-kit/640/400',
        recruitCount: 8,
        applicantCount: 64,
        applyStartAt: at(-20 * DAY),
        applyEndAt: at(-10 * DAY),
    }),
    card({
        id: '8100000000000000004',
        title: '스마트 플러그 4구 — 끝난 모집',
        productName: '홈봇 스마트 플러그 4구',
        productPrice: 29000,
        form: 'GIVE',
        status: 'CLOSED',
        advertiserName: '홈봇',
        imageUrl: 'https://picsum.photos/seed/irobit-trial-plug/640/400',
        recruitCount: 5,
        applicantCount: 41,
        applyStartAt: at(-60 * DAY),
        applyEndAt: at(-50 * DAY),
    }),
];
const SHIPPER: Record<string, 'ADVERTISER' | 'OPERATOR'> = { '8100000000000000001': 'OPERATOR', '8100000000000000002': 'ADVERTISER', '8100000000000000003': 'ADVERTISER', '8100000000000000004': 'OPERATOR' };
const DESCRIPTIONS: Record<string, string> = {
    '8100000000000000001': '물걸레 회전 패드와 자동 먼지 비움 스테이션이 있는 신제품입니다.\n2주 동안 집에서 써 보고 장단점을 솔직하게 남겨 주세요.\n\n대여형이라 사용기를 쓴 뒤 반납해 주셔야 해요(반납 배송비는 광고주 부담).',
    '8100000000000000002': '한 손에 들어오는 무선 인두입니다. 받은 제품은 가지셔도 돼요.',
    '8100000000000000003': '초등 고학년용 코딩 로봇 키트입니다.',
    '8100000000000000004': '앱으로 켜고 끄는 4구 스마트 플러그입니다.',
};

let applied = new Map<string, string>([['8100000000000000001', 'ta-3']]);

function find(id: string): TrialCard {
    const found = CARDS.find((c) => c.id === id);
    if (!found) throw new ApiError({ status: 404, code: 'TRIAL_NOT_FOUND', message: '체험단 모집을 찾을 수 없어요.' });
    return found;
}

function guard(): void {
    if (mockFlags().has('trial-error')) throw MOCK_ERRORS.internal();
}

function consentsOf(c: TrialCard): TrialPublicDetail['consents'] {
    const out: TrialPublicDetail['consents'] = [];
    if (SHIPPER[c.id] === 'ADVERTISER') out.push('PROVIDE_CONTACT');
    else if (c.form === 'LEND') out.push('UNRETURNED_CONTACT');
    if (c.form === 'LEND') out.push('PRODUCT_CHARGE', 'DAMAGE_CHARGE');
    return out;
}

function mineOf(c: TrialCard): TrialMine {
    const flags = mockFlags();
    const appId = applied.get(c.id) ?? null;
    const blockedReason = appId
        ? 'APPLIED'
        : flags.has('trial-identity')
          ? 'IDENTITY_REQUIRED'
          : flags.has('trial-banned')
            ? 'RESTRICTED'
            : c.status !== 'RECRUITING'
              ? 'NOT_RECRUITING'
              : Date.parse(c.goldOpenAt) > Date.now()
                ? 'NOT_OPEN'
                : null;
    return { applicationId: appId, applicationStatus: appId ? 'APPLIED' : null, canApply: blockedReason === null, canCancel: !!appId && c.status === 'RECRUITING', applyOpenAt: c.goldOpenAt, quota: 'REVIEWER', reviewerTier: 'GOLD', blockedReason };
}

let myRows: MyTrial[] = [
    {
        applicationId: 'ta-1',
        applicationStatus: 'SELECTED',
        quota: 'REVIEWER',
        appliedAt: at(-15 * DAY),
        canCancel: false,
        trial: CARDS[2]!,
        participation: {
            id: 'tp-1',
            carrier: 'CJ대한통운',
            trackingNo: '6543210987',
            shippedAt: at(-6 * DAY),
            receivedAt: null,
            receivedBy: null,
            canReceive: true,
            reviewDueAt: null,
            reviewPostId: null,
            canWrite: true,
            writeBoard: REVIEW_BOARD,
            returnDueAt: null,
            returnedAt: null,
            kept: null,
            unreturnedAt: null,
            damageReports: [],
        },
    },
    {
        applicationId: 'ta-2',
        applicationStatus: 'SELECTED',
        quota: 'GENERAL',
        appliedAt: at(-30 * DAY),
        canCancel: false,
        trial: card({ id: '8100000000000000005', title: '휴대용 3D 펜 체험단(대여형)', productName: '프린트펜 3D', productPrice: 89000, form: 'LEND', status: 'RUNNING', advertiserName: '프린트펜', imageUrl: null, recruitCount: 3, applicantCount: 22, applyStartAt: at(-40 * DAY), applyEndAt: at(-30 * DAY) }),
        participation: {
            id: 'tp-2',
            carrier: '우체국택배',
            trackingNo: '1234567890123',
            shippedAt: at(-20 * DAY),
            receivedAt: at(-18 * DAY),
            receivedBy: 'MEMBER',
            canReceive: false,
            reviewDueAt: at(-4 * DAY),
            reviewPostId: '7700000000000000001',
            canWrite: false,
            writeBoard: REVIEW_BOARD,
            returnDueAt: at(2 * DAY),
            returnedAt: null,
            kept: null,
            unreturnedAt: null,
            damageReports: [
                {
                    id: 'td-1',
                    description: '반납받은 제품의 노즐 끝이 휘어 있어요.',
                    status: 'REPORTED',
                    objectionDueAt: at(6 * DAY),
                    canObject: true,
                    objection: null,
                    verdict: null,
                    decisionNote: null,
                    photos: [PHOTO('damage-1'), PHOTO('damage-2')],
                    createdAt: at(-1 * DAY),
                },
            ],
        },
    },
    { applicationId: 'ta-3', applicationStatus: 'APPLIED', quota: 'REVIEWER', appliedAt: at(-1 * DAY), canCancel: true, trial: CARDS[0]!, participation: null },
    { applicationId: 'ta-4', applicationStatus: 'NOT_SELECTED', quota: 'GENERAL', appliedAt: at(-55 * DAY), canCancel: false, trial: CARDS[3]!, participation: null },
];

let proofs: PurchaseProof[] = [
    { id: 'pp-1', postId: '7700000000000000011', postTitle: '로봇청소기 X5 한 달 사용기 — 내 돈 내 산', board: REVIEW_BOARD, author: member(2, '납땜장인'), status: 'PENDING', rejectReason: null, purged: false, createdAt: at(-3 * HOUR), reviewedAt: null },
    { id: 'pp-2', postId: '7700000000000000012', postTitle: '무선 청소기 비교 사용기', board: REVIEW_BOARD, author: member(3, '공구덕후'), status: 'APPROVED', rejectReason: null, purged: true, createdAt: at(-5 * DAY), reviewedAt: at(-4 * DAY) },
];

const BIZ_ROW_BASE = { advertiser: { id: '5100000000000000002', name: '솔더핏' }, reviewerShare: 60, reviewBoard: REVIEW_BOARD, amount: null, createdAt: at(-5 * DAY) };

let bizRecruiting: BizTrialView = {
    trial: {
        id: '8100000000000000002',
        title: CARDS[1]!.title,
        productName: CARDS[1]!.productName,
        productPrice: 39000,
        form: 'GIVE',
        shipper: 'ADVERTISER',
        status: 'RECRUITING',
        advertiser: BIZ_ROW_BASE.advertiser,
        reviewBoard: REVIEW_BOARD,
        recruitCount: 10,
        reviewerShare: 60,
        reviewerSlots: 6,
        applyStartAt: CARDS[1]!.applyStartAt,
        applyEndAt: CARDS[1]!.applyEndAt,
        reviewDays: 14,
        returnDays: null,
        description: DESCRIPTIONS['8100000000000000002']!,
        imageUrl: null,
        selectedCount: null,
        participants: [],
        damageReports: [],
    },
    applicants: {
        recruitCount: 10,
        reviewerSlots: 6,
        generalSlots: 4,
        items: [
            { id: 'a-1', member: member(1, '로봇청소왕'), status: 'APPLIED', quota: 'REVIEWER', reviewerTier: 'GOLD', recommended: true, stats: { reviewPosts: 34, averageLikes: 12.4, keptRate: 100, judged: 12 }, consents: ['PROVIDE_CONTACT'], createdAt: at(-1 * HOUR) },
            { id: 'a-2', member: member(2, '납땜장인'), status: 'APPLIED', quota: 'REVIEWER', reviewerTier: 'SILVER', recommended: false, stats: { reviewPosts: 12, averageLikes: 6.1, keptRate: 90, judged: 10 }, consents: ['PROVIDE_CONTACT'], createdAt: at(-2 * HOUR) },
            { id: 'a-3', member: member(3, '공구덕후'), status: 'APPLIED', quota: 'GENERAL', reviewerTier: null, recommended: false, stats: { reviewPosts: 2, averageLikes: 1.5, keptRate: null, judged: 0 }, consents: ['PROVIDE_CONTACT'], createdAt: at(-3 * HOUR) },
        ],
    },
};

const bizRunning: BizTrialView = {
    trial: {
        id: '8100000000000000003',
        title: CARDS[2]!.title,
        productName: CARDS[2]!.productName,
        productPrice: 45000,
        form: 'GIVE',
        shipper: 'ADVERTISER',
        status: 'RUNNING',
        advertiser: { id: '5100000000000000003', name: '아이봇에듀' },
        reviewBoard: REVIEW_BOARD,
        recruitCount: 8,
        reviewerShare: 50,
        reviewerSlots: 4,
        applyStartAt: CARDS[2]!.applyStartAt,
        applyEndAt: CARDS[2]!.applyEndAt,
        reviewDays: 14,
        returnDays: null,
        description: DESCRIPTIONS['8100000000000000003']!,
        imageUrl: CARDS[2]!.imageUrl,
        selectedCount: 2,
        participants: [
            {
                id: 'bp-1',
                applicationId: 'a-11',
                member: member(1, '로봇청소왕'),
                quota: 'REVIEWER',
                recipient: '김로봇',
                phone: '010-1234-5678',
                postcode: '06236',
                address1: '서울 강남구 테헤란로 123',
                address2: '4층',
                memo: '문 앞에 놓아 주세요',
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
            },
            {
                id: 'bp-2',
                applicationId: 'a-12',
                member: member(2, '납땜장인'),
                quota: 'REVIEWER',
                recipient: null,
                phone: null,
                postcode: null,
                address1: null,
                address2: null,
                memo: null,
                addressPurged: true,
                carrier: 'CJ대한통운',
                trackingNo: '6543210987',
                shippedAt: at(-6 * DAY),
                shippedBy: 'ADVERTISER',
                receivedAt: at(-4 * DAY),
                receivedBy: 'MEMBER',
                reviewPost: { id: '7700000000000000021', name: '[체험단] 코딩 키트 아이와 해 본 후기' },
                reviewDueAt: at(10 * DAY),
                returnDueAt: null,
                returnedAt: null,
                kept: true,
                reviewLateAt: null,
                unreturnedAt: null,
                contactProvidedAt: null,
                photos: [PHOTO('preship-1', 'PRE_SHIP')],
            },
        ],
        damageReports: [],
    },
    applicants: { recruitCount: 8, reviewerSlots: 4, generalSlots: 4, items: [] },
};

function rowOf(v: BizTrialView): BizTrialRow {
    const t = v.trial;
    return { ...BIZ_ROW_BASE, id: t.id, title: t.title, productName: t.productName, form: t.form, shipper: t.shipper, status: t.status, advertiser: t.advertiser, recruitCount: t.recruitCount, applicantCount: v.applicants.items.length || 64, selectedCount: t.selectedCount, applyStartAt: t.applyStartAt, applyEndAt: t.applyEndAt };
}

function bizParticipant(id: string) {
    const p = bizRunning.trial.participants.find((x) => x.id === id);
    if (!p) throw new ApiError({ status: 404, code: 'TRIAL_PARTICIPANT_NOT_FOUND', message: '뽑힌 줄을 찾을 수 없어요.' });
    return p;
}

let photoSeq = 0;

export default class TrialMock {
    static async list(status: TrialListStatus): Promise<TrialCard[]> {
        await mockDelay();
        guard();
        if (mockFlags().has('trial-empty')) return [];
        return mockClone(CARDS.filter((c) => (status === 'RECRUITING' ? c.status === 'RECRUITING' || c.status === 'SELECTING' : c.status === status)));
    }

    static async detail(id: string): Promise<TrialPublicDetail> {
        await mockDelay();
        guard();
        const c = find(id);
        const reviews =
            c.status === 'CLOSED' || c.status === 'RUNNING'
                ? Array.from({ length: c.status === 'CLOSED' ? 5 : 2 }, (_, i) => ({
                      postId: `77000000000000003${i}`,
                      title: `[체험단] ${c.productName} 써 보니 — ${['솔직 후기', '장단점 정리', '한 달 사용기', '아이와 함께', '설정 팁'][i % 5]}`,
                      boardSlug: 'review',
                      author: member(i + 1, ['로봇청소왕', '납땜장인', '공구덕후', '새싹메이커', '메카닉'][i % 5]!),
                      createdAt: at(-(40 - i) * DAY),
                  }))
                : [];
        return mockClone({
            card: c,
            description: DESCRIPTIONS[id] ?? '',
            reviewDays: 14,
            returnDays: c.form === 'LEND' ? 21 : null,
            shipper: SHIPPER[id] ?? 'OPERATOR',
            reviewerShare: 60,
            reviewerGoldHours: 48,
            reviewerSilverHours: 24,
            consents: consentsOf(c),
            damageChargeLimit: c.form === 'LEND' ? c.productPrice : null,
            mine: mineOf(c),
            reviews,
        });
    }

    static async apply(id: string, body: TrialApplyRequest): Promise<TrialMine> {
        await mockDelay();
        guard();
        const c = find(id);
        if (mockFlags().has('trial-identity')) throw new ApiError({ status: 403, code: 'IDENTITY_REQUIRED', message: '본인인증을 한 회원만 신청할 수 있어요.' });
        if (mockFlags().has('trial-banned')) throw new ApiError({ status: 403, code: 'FEATURE_RESTRICTED', message: '체험단 신청이 제한된 상태예요.' });
        if (!body.addressId) throw MOCK_ERRORS.validation({ addressId: '받을 곳을 골라 주세요.' });
        const appId = `ta-${Date.now()}`;
        applied.set(id, appId);
        c.applicantCount += 1;
        myRows = [{ applicationId: appId, applicationStatus: 'APPLIED', quota: 'REVIEWER', appliedAt: new Date().toISOString(), canCancel: true, trial: c, participation: null }, ...myRows.filter((r) => r.trial.id !== id)];
        return mockClone(mineOf(c));
    }

    static async cancel(id: string): Promise<void> {
        await mockDelay();
        guard();
        applied.delete(id);
        myRows = myRows.filter((r) => !(r.trial.id === id && r.applicationStatus === 'APPLIED'));
        const c = CARDS.find((x) => x.id === id);
        if (c) c.applicantCount = Math.max(0, c.applicantCount - 1);
    }

    static async mine(): Promise<MyTrial[]> {
        await mockDelay();
        guard();
        if (mockFlags().has('trial-empty')) return [];
        return mockClone(myRows);
    }

    static async writable(board: string): Promise<TrialWritable[]> {
        await mockDelay();
        guard();
        return mockClone(
            myRows
                .filter((r) => r.participation?.canWrite && r.participation.receivedAt && r.participation.writeBoard.slug === board)
                .map((r) => ({ participantId: r.participation!.id, trialId: r.trial.id, trialTitle: r.trial.title, board: r.participation!.writeBoard })),
        );
    }

    static async received(participantId: string): Promise<void> {
        await mockDelay();
        guard();
        for (const row of myRows) {
            const p = row.participation;
            if (p?.id === participantId) Object.assign(p, { receivedAt: new Date().toISOString(), receivedBy: 'MEMBER', canReceive: false, reviewDueAt: at(14 * DAY), returnDueAt: row.trial.form === 'LEND' ? at(21 * DAY) : null });
        }
    }

    static async objection(reportId: string, text: string): Promise<void> {
        await mockDelay();
        guard();
        if (!text.trim()) throw MOCK_ERRORS.validation({ text: '이의 내용을 적어 주세요.' });
        for (const row of myRows) {
            const report = row.participation?.damageReports.find((d) => d.id === reportId);
            if (report) Object.assign(report, { objection: text, status: 'OBJECTED', canObject: false });
        }
    }

    static async staffProofs(status: 'PENDING' | 'ALL'): Promise<PurchaseProof[]> {
        await mockDelay();
        guard();
        return mockClone(status === 'PENDING' ? proofs.filter((p) => p.status === 'PENDING') : proofs);
    }

    static async staffReview(id: string, approve: boolean, reason: string): Promise<void> {
        await mockDelay();
        guard();
        if (!approve && !reason.trim()) throw MOCK_ERRORS.validation({ reason: '거절 사유를 적어 주세요.' });
        proofs = proofs.map((p) => (p.id === id ? { ...p, status: approve ? 'APPROVED' : 'REJECTED', rejectReason: approve ? null : reason, reviewedAt: new Date().toISOString() } : p));
    }

    static async uploadProof(): Promise<PurchaseProofResult> {
        await mockDelay();
        guard();
        return { id: `pp-${Date.now()}`, status: 'PENDING' };
    }

    /* ── 사업자 센터(광고주) ── */

    static async bizList(): Promise<BizTrialRow[]> {
        await mockDelay();
        guard();
        if (mockFlags().has('trial-empty')) return [];
        return mockClone([rowOf(bizRecruiting), rowOf(bizRunning)]);
    }

    static async bizDetail(id: string): Promise<BizTrialView> {
        await mockDelay();
        guard();
        if (id === bizRecruiting.trial.id) return mockClone(bizRecruiting);
        if (id === bizRunning.trial.id) return mockClone(bizRunning);
        throw new ApiError({ status: 404, code: 'TRIAL_NOT_FOUND', message: '체험단 모집을 찾을 수 없어요.' });
    }

    static async bizRecommend(applicationId: string, on: boolean): Promise<void> {
        await mockDelay();
        guard();
        bizRecruiting = { ...bizRecruiting, applicants: { ...bizRecruiting.applicants, items: bizRecruiting.applicants.items.map((a) => (a.id === applicationId ? { ...a, recommended: on } : a)) } };
    }

    static async bizUploadPhoto(kind: BizTrialPhotoKind, file: File): Promise<BizTrialUploaded> {
        await mockDelay();
        guard();
        photoSeq += 1;
        return { id: `up-${photoSeq}`, kind, key: `trial/mock-${photoSeq}.jpg`, url: URL.createObjectURL(file) };
    }

    static async bizShipment(participantId: string, body: BizTrialShipmentRequest): Promise<void> {
        await mockDelay();
        guard();
        if (body.photoIds.length === 0) throw MOCK_ERRORS.validation({ photoIds: '발송 전 사진을 1장 이상 올려 주세요.' });
        if (!body.trackingNo.trim()) throw MOCK_ERRORS.validation({ trackingNo: '송장 번호를 넣어 주세요.' });
        Object.assign(bizParticipant(participantId), { carrier: body.carrier, trackingNo: body.trackingNo, shippedAt: new Date().toISOString(), shippedBy: 'ADVERTISER' });
    }

    static async bizReturned(participantId: string, body: BizTrialReturnedRequest): Promise<void> {
        await mockDelay();
        guard();
        const p = bizParticipant(participantId);
        Object.assign(p, { returnedAt: new Date().toISOString(), photos: [...p.photos, ...body.photoIds.map((id) => PHOTO(id, 'RETURN'))] });
    }

    static async bizDamage(participantId: string, body: BizTrialDamageRequest): Promise<void> {
        await mockDelay();
        guard();
        if (body.photoIds.length === 0) throw MOCK_ERRORS.validation({ photoIds: '사진을 1장 이상 올려 주세요.' });
        if (!body.description.trim()) throw MOCK_ERRORS.validation({ description: '어디가 어떻게 파손됐는지 적어 주세요.' });
        const p = bizParticipant(participantId);
        bizRunning.trial.damageReports = [
            ...bizRunning.trial.damageReports,
            { id: `bd-${Date.now()}`, participantId, member: p.member, reporterType: 'ADVERTISER', description: body.description, status: 'REPORTED', objectionDueAt: at(7 * DAY), objection: null, objectedAt: null, verdict: null, decisionNote: null, decidedAt: null, photos: [], createdAt: new Date().toISOString() },
        ];
    }
}
