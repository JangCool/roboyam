// MOCK: 회원 칭호 서버(member-title-contract)가 생기기 전까지 화면을 그리는 목업 — 이 탭 메모리에만 남는다(새로 고치면 처음 값).
// 서버가 붙으면 이 파일과 `useTitleApi.ts`·`useUserProfileApi.ts` 의 `// MOCK:` 줄을 지운다(`TITLE_MOCK` 분기 아래의 실제 호출만 남는다).
// 개발 스위치: `?mock=title-error`(모든 칭호 요청 실패) · `?mock=title-empty`(가진 칭호·명예의 전당 비움) · `?mock=title-unseen`(축하 창 띄우기)

import { mockClone, mockDelay, mockFlags, MOCK_ERRORS } from '~/composables/common/mock/mock-util';
import type {
    BoardTitleApplication,
    HallOfFame,
    MyTitles,
    ProfileTitle,
    RepresentativeTitleRequest,
    TitleCatalogItem,
} from '~/composables/title/api/useTitleApi.types';

/** 목업으로 그릴지 — 서버가 붙으면 이 상수와 쓰는 줄을 지운다 */
export const TITLE_MOCK: boolean = true;

const CATALOG: TitleCatalogItem[] = [
    { key: 'monthly-writer', label: '이달의 작가', icon: 'pen', chip: 'PURPLE', method: 'AWARD', tiered: false, howTo: '매달 운영자가 우수 글을 쓴 회원 중 최대 3명을 뽑아요.', holders: 42, ratio: 0.004, tiers: [] },
    { key: 'yearly-writer', label: '올해의 작가', icon: 'trophy', chip: 'PURPLE', method: 'AWARD', tiered: false, howTo: '그해 이달의 작가 중에서 운영자가 뽑아요.', holders: 3, ratio: 0.0003, tiers: [] },
    { key: 'honorary-writer', label: '명예 작가', icon: 'wreath', chip: 'GREEN', method: 'AUTO', tiered: false, howTo: '이달의 작가를 3번 받으면 자동으로 받아요. 기간 없이 명예의 전당에 남아요.', holders: 5, ratio: 0.0005, tiers: [] },
    {
        key: 'reviewer',
        label: '리뷰어',
        icon: 'mag',
        chip: 'PURPLE',
        method: 'APPLY',
        tiered: true,
        howTo: '정식 등급 이상 · 사용기 3편(구매 인증 1편 이상)을 쓰면 신청할 수 있어요.',
        holders: 18,
        ratio: 0.0018,
        tiers: [
            { tier: 'BRONZE', threshold: 3, holders: 12 },
            { tier: 'SILVER', threshold: 10, holders: 5 },
            { tier: 'GOLD', threshold: 30, holders: 1 },
        ],
    },
    { key: 'reporter', label: '기자단', icon: 'mic', chip: 'PURPLE', method: 'APPLY', tiered: false, howTo: '정식 등급 이상 · 뉴스 10편 · 신고로 숨겨진 글이 없으면 신청할 수 있어요.', holders: 7, ratio: 0.0007, tiers: [] },
    {
        key: 'popular',
        label: '인기글',
        icon: 'popular',
        chip: 'GREEN',
        method: 'AUTO',
        tiered: true,
        howTo: '하루 인기글 상위에 들어간 날이 10·30·100번이 되면 받아요.',
        holders: 120,
        ratio: 0.012,
        tiers: [
            { tier: 'BRONZE', threshold: 10, holders: 96 },
            { tier: 'SILVER', threshold: 30, holders: 21 },
            { tier: 'GOLD', threshold: 100, holders: 3 },
        ],
    },
    { key: 'attendance-100', label: '출석 100일', icon: 'cal', chip: 'GREEN', method: 'AUTO', tiered: false, howTo: '출석한 날이 100일이 되면 받아요.', holders: 310, ratio: 0.031, tiers: [] },
];

const MEMBERS = [
    { id: '7000000000000000001', nickname: '로봇청소왕', avatarUrl: null },
    { id: '7000000000000000002', nickname: '납땜장인', avatarUrl: null },
    { id: '7000000000000000003', nickname: '아두이노맘', avatarUrl: null },
    { id: '7000000000000000004', nickname: '센서덕후', avatarUrl: null },
    { id: '7000000000000000005', nickname: '모터돌리기', avatarUrl: null },
];

const HALL: HallOfFame = {
    honorary: [MEMBERS[0]!, MEMBERS[1]!],
    yearly: [{ year: '2025', members: [MEMBERS[0]!] }],
    monthly: [
        { month: '2026-10', members: [MEMBERS[2]!, MEMBERS[3]!, MEMBERS[0]!] },
        { month: '2026-09', members: [MEMBERS[1]!, MEMBERS[4]!] },
        { month: '2026-08', members: [MEMBERS[0]!, MEMBERS[3]!] },
    ],
};

const OWNED: ProfileTitle[] = [
    { key: 'monthly-writer', label: '이달의 작가', icon: 'pen', tier: null, chip: 'PURPLE', count: 4, periods: ['2026-10', '2026-08', '2026-05', '2026-02'] },
    { key: 'honorary-writer', label: '명예 작가', icon: 'wreath', tier: null, chip: 'GREEN', count: 1, periods: [] },
    { key: 'popular', label: '인기글 은', icon: 'popular', tier: 'SILVER', chip: 'GREEN', count: 1, periods: [] },
    { key: 'attendance-100', label: '출석 100일', icon: 'cal', tier: null, chip: 'GREEN', count: 1, periods: [] },
];

let mine: MyTitles = {
    representative: { key: 'honorary-writer' },
    hidden: false,
    owned: OWNED,
    progress: [
        { key: 'popular', label: '인기글', labelEn: null, icon: 'popular', chip: 'GREEN', tier: 'GOLD', current: 47, target: 100 },
        { key: 'reviewer', label: '리뷰어', labelEn: null, icon: 'mag', chip: 'PURPLE', tier: 'BRONZE', current: 1, target: 3 },
    ],
    applicable: [
        {
            key: 'reviewer',
            label: '리뷰어',
            labelEn: null,
            icon: 'mag',
            chip: 'PURPLE',
            conditions: [
                { type: 'GRADE_AT_LEAST', label: '정식 이상', met: true, current: null, target: null },
                { type: 'POSTS', label: '사용기 글 3편', met: false, current: 2, target: 3 },
            ],
            canApply: false,
            reapplyAt: null,
            pending: false,
        },
        {
            key: 'reporter',
            label: '기자단',
            labelEn: null,
            icon: 'mic',
            chip: 'PURPLE',
            conditions: [
                { type: 'GRADE_AT_LEAST', label: '정식 이상', met: true, current: null, target: null },
                { type: 'POSTS', label: '뉴스 글 10편', met: true, current: 12, target: 10 },
                { type: 'NO_REPORT_HIDDEN', label: '신고로 숨겨진 글 없음', met: true, current: null, target: null },
            ],
            canApply: true,
            reapplyAt: null,
            pending: false,
        },
    ],
    applications: [{ id: '8100000000000000001', key: 'reporter', label: '기자단', status: 'REJECTED', reason: '뉴스 출처 표기가 빠진 글이 많아요. 출처를 밝혀 주세요.', reapplyAt: '2026-10-20T00:00:00Z', createdAt: '2026-09-20T03:00:00Z' }],
    unseen: [],
};

let staffRows: BoardTitleApplication[] = [
    {
        id: '8200000000000000001',
        key: 'reviewer',
        label: '리뷰어',
        icon: 'mag',
        chip: 'PURPLE',
        member: MEMBERS[3]!,
        status: 'PENDING',
        reason: null,
        reapplyAt: null,
        createdAt: '2026-11-16T09:12:00Z',
        mine: false,
        conditions: [
            { type: 'GRADE_AT_LEAST', label: '정식 이상', met: true, current: null, target: null },
            { type: 'POSTS', label: '사용기 글 3편', met: true, current: 4, target: 3 },
        ],
    },
    {
        id: '8200000000000000002',
        key: 'reviewer',
        label: '리뷰어',
        icon: 'mag',
        chip: 'PURPLE',
        member: MEMBERS[4]!,
        status: 'REJECTED',
        reason: '구매 인증 사용기가 없어요.',
        reapplyAt: '2026-12-03T00:00:00Z',
        createdAt: '2026-11-02T01:00:00Z',
        mine: false,
        conditions: [
            { type: 'GRADE_AT_LEAST', label: '정식 이상', met: true, current: null, target: null },
            { type: 'POSTS', label: '사용기 글 3편', met: true, current: 3, target: 3 },
        ],
    },
];

async function start(): Promise<Set<string>> {
    await mockDelay();
    const flags = mockFlags();
    if (flags.has('title-error')) throw MOCK_ERRORS.internal();
    return flags;
}

export default class TitleMock {
    static async catalog(): Promise<TitleCatalogItem[]> {
        await start();
        return mockClone(CATALOG);
    }

    static async hallOfFame(): Promise<HallOfFame> {
        const flags = await start();
        return flags.has('title-empty') ? { honorary: [], yearly: [], monthly: [] } : mockClone(HALL);
    }

    static async mine(): Promise<MyTitles> {
        const flags = await start();
        const value = mockClone(mine);
        if (flags.has('title-empty')) value.owned = [];
        if (flags.has('title-unseen') && value.unseen.length === 0) value.unseen = [{ key: 'monthly-writer', label: '11월의 작가', labelEn: 'November Writer', icon: 'pen', tier: null, chip: 'PURPLE', ratio: 0.004 }];
        return value;
    }

    static async setRepresentative(body: RepresentativeTitleRequest): Promise<MyTitles> {
        await start();
        mine = { ...mine, representative: body.hidden || !body.key ? (body.hidden ? null : { key: 'honorary-writer' }) : { key: body.key }, hidden: body.hidden };
        return mockClone(mine);
    }

    static async apply(key: string): Promise<void> {
        await start();
        const target = mine.applicable.find((a) => a.key === key);
        mine = {
            ...mine,
            applicable: mine.applicable.map((a) => (a.key === key ? { ...a, canApply: false, pending: true } : a)),
            applications: [{ id: String(Date.now()), key, label: target?.label ?? key, status: 'PENDING', reason: null, reapplyAt: null, createdAt: new Date().toISOString() }, ...mine.applications],
        };
    }

    static async markSeen(): Promise<void> {
        await start();
        mine = { ...mine, unseen: [] };
    }

    /** 공개 프로필의 칭호 — 서버가 `titles` 를 주기 전까지 */
    static profileTitles(): ProfileTitle[] {
        return mockFlags().has('title-empty') ? [] : mockClone(OWNED);
    }

    static async staffApplications(): Promise<BoardTitleApplication[]> {
        await start();
        return mockClone(staffRows);
    }

    static async staffReview(id: string, approve: boolean, reason: string): Promise<void> {
        await start();
        staffRows = staffRows.map((row) => (row.id === id ? { ...row, status: approve ? 'APPROVED' : 'REJECTED', reason: approve ? null : reason || null } : row));
    }
}
