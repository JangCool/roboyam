// MOCK: 회원 칭호 관리 서버(member-title-contract — admin)를 붙이기 전까지 화면을 그리는 목업 — 이 탭 메모리에만 남는다(새로 고치면 처음 값).
// 모양은 admin 서버 DTO(`AdminTitleView`·`AdminTitleGrantView`…)와 같다. 서버가 붙으면 이 파일과 `useTitleApi.ts`·`usePostApi.ts` 의 `// MOCK:` 줄을 지운다.

import { ApiError } from '~/composables/common/useApiClient';
import type { AdminPostDetail, AdminPostItem, AdminPostListView } from '~/composables/post/api/usePostApi.types';
import type {
    AdminTitle,
    AdminTitleApplication,
    AdminTitleRequest,
    MonthlyCandidates,
    TitleGrantPage,
    TitleGrantQuery,
    TitleGrantRequest,
    TitleGrantRow,
    TitleMember,
    TitleSettings,
    TitleSettingsRequest,
} from '~/composables/title/api/useTitleApi.types';

/** 목업으로 그릴지 — 서버가 붙으면 이 상수와 쓰는 줄을 지운다 */
export const TITLE_ADMIN_MOCK: boolean = true;

const NO_BENEFIT = { trialPriority: false, magazineCandidate: false, avatarFrame: false };
const BASE = { nameEn: null, reviewer: 'OPERATOR' as const, boardId: null, tiered: false, tierMetric: null, tiers: [], benefits: NO_BENEFIT, dormantHide: false, enabled: true, builtin: true, updatedAt: null };

let titles: AdminTitle[] = [
    { ...BASE, key: 'monthly-writer', name: '이달의 작가', icon: 'pen', chip: 'PURPLE', method: 'AWARD', conditions: [], benefits: { ...NO_BENEFIT, magazineCandidate: true }, sortOrder: 1, holders: 42, howTo: '매달 운영자가 뽑아요.' },
    { ...BASE, key: 'yearly-writer', name: '올해의 작가', icon: 'trophy', chip: 'PURPLE', method: 'AWARD', conditions: [], benefits: { ...NO_BENEFIT, magazineCandidate: true }, sortOrder: 2, holders: 3, howTo: '그해 이달의 작가 중에서 뽑아요.' },
    { ...BASE, key: 'honorary-writer', name: '명예 작가', icon: 'wreath', chip: 'GREEN', method: 'AUTO', conditions: [], sortOrder: 3, holders: 5, howTo: '이달의 작가를 3번 받으면 자동으로 받아요.' },
    {
        ...BASE,
        key: 'reviewer',
        name: '리뷰어',
        icon: 'mag',
        chip: 'PURPLE',
        method: 'APPLY',
        reviewer: 'OPERATOR_AND_BOARD_MANAGER',
        boardId: '6000000000000000001',
        conditions: [
            { type: 'GRADE_AT_LEAST', value: null, boardId: null, boardSlug: null, gradeKey: 'REGULAR', label: '정식 이상' },
            { type: 'POSTS', value: 3, boardId: null, boardSlug: 'reviews', gradeKey: null, label: '사용기 글 3편' },
        ],
        tiered: true,
        tierMetric: 'TRIAL_KEPT',
        tiers: [
            { tier: 'BRONZE', threshold: 3 },
            { tier: 'SILVER', threshold: 10 },
            { tier: 'GOLD', threshold: 30 },
        ],
        benefits: { ...NO_BENEFIT, trialPriority: true },
        dormantHide: true,
        sortOrder: 4,
        holders: 18,
        howTo: '정식 이상 · 사용기 3편을 쓰면 신청할 수 있어요.',
    },
    {
        ...BASE,
        key: 'reporter',
        name: '기자단',
        icon: 'mic',
        chip: 'PURPLE',
        method: 'APPLY',
        conditions: [
            { type: 'GRADE_AT_LEAST', value: null, boardId: null, boardSlug: null, gradeKey: 'REGULAR', label: '정식 이상' },
            { type: 'POSTS', value: 10, boardId: null, boardSlug: 'news', gradeKey: null, label: '뉴스 글 10편' },
            { type: 'NO_REPORT_HIDDEN', value: null, boardId: null, boardSlug: null, gradeKey: null, label: '신고로 숨겨진 글 없음' },
        ],
        dormantHide: true,
        sortOrder: 5,
        holders: 7,
        howTo: '정식 이상 · 뉴스 10편 · 신고로 숨겨진 글이 없으면 신청할 수 있어요.',
    },
    {
        ...BASE,
        key: 'popular',
        name: '인기글',
        icon: 'popular',
        chip: 'GREEN',
        method: 'AUTO',
        conditions: [{ type: 'POPULAR_DAYS', value: 10, boardId: null, boardSlug: null, gradeKey: null, label: '인기글 진입 10일' }],
        tiered: true,
        tierMetric: 'POPULAR_DAYS',
        tiers: [
            { tier: 'BRONZE', threshold: 10 },
            { tier: 'SILVER', threshold: 30 },
            { tier: 'GOLD', threshold: 100 },
        ],
        sortOrder: 6,
        holders: 120,
        howTo: '하루 인기글 상위에 10·30·100번 들면 받아요.',
    },
    { ...BASE, key: 'attendance-100', name: '출석 100일', icon: 'cal', chip: 'GREEN', method: 'AUTO', conditions: [{ type: 'ATTENDANCE_DAYS', value: 100, boardId: null, boardSlug: null, gradeKey: null, label: '출석 100일' }], sortOrder: 7, holders: 310, howTo: '출석한 날이 100일이면 받아요.' },
];

let settings: TitleSettings = { monthlyMax: 3, honoraryCount: 3, reapplyDays: 30, updatedAt: null, honoraryGranted: 0 };

const MEMBERS: TitleMember[] = [
    { id: '7000000000000000001', nickname: '로봇청소왕' },
    { id: '7000000000000000002', nickname: '납땜장인' },
    { id: '7000000000000000003', nickname: '아두이노맘' },
    { id: '7000000000000000004', nickname: '센서덕후' },
    { id: '7000000000000000005', nickname: '모터돌리기' },
];

function row(id: string, key: string, member: TitleMember, extra: Partial<TitleGrantRow> = {}): TitleGrantRow {
    const t = titles.find((x) => x.key === key);
    const period = extra.period ?? null;
    const month = key === 'monthly-writer' && period ? Number(period.slice(5)) : null;
    return {
        id,
        key,
        label: month ? `${month}월의 작가` : (t?.name ?? key),
        icon: t?.icon ?? 'medal',
        tier: null,
        chip: t?.chip ?? 'PURPLE',
        period,
        member,
        source: 'AWARD',
        grantedAt: '2026-11-01T01:00:00Z',
        grantedBy: '1',
        grantedByName: '운영자 김',
        grantedByMember: null,
        reason: null,
        active: true,
        revokedAt: null,
        revokedBy: null,
        revokedByName: null,
        revokedReason: null,
        honorary: null,
        ...extra,
    };
}

let seq = 9000;
let grants: TitleGrantRow[] = [
    row('8300000000000000001', 'monthly-writer', MEMBERS[2]!, { period: '2026-10', reason: '10월 이달의 작가' }),
    row('8300000000000000002', 'monthly-writer', MEMBERS[3]!, { period: '2026-10', reason: '10월 이달의 작가' }),
    row('8300000000000000003', 'honorary-writer', MEMBERS[0]!, { source: 'AUTO', grantedAt: '2026-08-01T01:00:00Z', grantedBy: null, grantedByName: null }),
    row('8300000000000000004', 'popular', MEMBERS[0]!, { tier: 'SILVER', source: 'AUTO', grantedAt: '2026-07-12T15:10:00Z', grantedBy: null, grantedByName: null }),
    row('8300000000000000005', 'reporter', MEMBERS[4]!, {
        source: 'APPLICATION',
        grantedAt: '2026-06-02T03:00:00Z',
        active: false,
        revokedAt: '2026-09-10T02:00:00Z',
        revokedBy: '1',
        revokedByName: '운영자 김',
        revokedReason: '출처 없는 기사 반복',
    }),
];

let applications: AdminTitleApplication[] = [
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
        reviewedAt: null,
        reviewedBy: null,
        reviewedByName: null,
        reviewedByMember: null,
    },
    {
        id: '8200000000000000003',
        key: 'reporter',
        label: '기자단',
        icon: 'mic',
        chip: 'PURPLE',
        member: MEMBERS[1]!,
        status: 'PENDING',
        reason: null,
        reapplyAt: null,
        createdAt: '2026-11-15T22:40:00Z',
        reviewedAt: null,
        reviewedBy: null,
        reviewedByName: null,
        reviewedByMember: null,
    },
];

/** 「이달의 작가 후보에 넣기」로 넣은 글 ID */
const manualCandidates = new Set<string>();

function delay(): Promise<void> {
    return new Promise((resolve) => setTimeout(resolve, 300 + Math.random() * 400));
}

function clone<T>(value: T): T {
    return JSON.parse(JSON.stringify(value)) as T;
}

function validation(fields: Record<string, string>): ApiError {
    return new ApiError({ status: 400, code: 'VALIDATION', message: '입력한 값을 확인해 주세요.', fields });
}

export default class TitleAdminMock {
    static async list(): Promise<AdminTitle[]> {
        await delay();
        return clone([...titles].sort((a, b) => a.sortOrder - b.sortOrder));
    }

    static async save(body: AdminTitleRequest, key: string | null): Promise<AdminTitle> {
        await delay();
        if (Array.from(body.name.trim()).length > 6) throw validation({ name: '이름은 6자까지예요.' });
        const before = key ? titles.find((t) => t.key === key) : undefined;
        const saved: AdminTitle = {
            ...body,
            key: key ?? `title-${++seq}`,
            tiered: body.tierMetric !== null,
            builtin: before?.builtin ?? false,
            holders: before?.holders ?? 0,
            howTo: before?.howTo ?? '',
            updatedAt: new Date().toISOString(),
        };
        titles = before ? titles.map((t) => (t.key === key ? saved : t)) : [...titles, saved];
        return clone(saved);
    }

    static async settings(): Promise<TitleSettings> {
        await delay();
        return clone(settings);
    }

    static async saveSettings(body: TitleSettingsRequest): Promise<TitleSettings> {
        await delay();
        const lowered = body.honoraryCount < settings.honoraryCount;
        settings = { ...body, updatedAt: new Date().toISOString(), honoraryGranted: lowered ? 2 : 0 };
        return clone(settings);
    }

    static async grant(key: string, body: TitleGrantRequest): Promise<TitleGrantRow> {
        await delay();
        const member = MEMBERS.find((m) => m.id === body.memberId) ?? { id: body.memberId, nickname: null };
        const granted = row(String(++seq), key, member, { period: body.period ?? null, tier: body.tier ?? null, reason: body.reason, grantedAt: new Date().toISOString(), grantedByName: '나' });
        grants = [granted, ...grants];
        return clone(granted);
    }

    static async revoke(grantId: string, reason: string): Promise<void> {
        await delay();
        grants = grants.map((g) => (g.id === grantId ? { ...g, active: false, revokedAt: new Date().toISOString(), revokedByName: '나', revokedReason: reason } : g));
    }

    static async grants(query: TitleGrantQuery): Promise<TitleGrantPage> {
        await delay();
        const all = grants.filter((g) => (!query.key || g.key === query.key) && (!query.memberId || g.member.id === query.memberId));
        return clone({ items: all.slice(query.page * query.size, (query.page + 1) * query.size), total: all.length, page: query.page, size: query.size });
    }

    static async memberTitles(memberId: string): Promise<TitleGrantRow[]> {
        await delay();
        const own = grants.filter((g) => g.member.id === memberId);
        return clone(own.length ? own : grants.filter((g) => g.member.id === MEMBERS[0]!.id));
    }

    static async applications(): Promise<AdminTitleApplication[]> {
        await delay();
        return clone(applications);
    }

    static async review(id: string, approve: boolean, reason: string): Promise<void> {
        await delay();
        const app = applications.find((a) => a.id === id);
        if (!app || app.status !== 'PENDING') throw new ApiError({ status: 409, code: 'TITLE_APPLICATION_STATE', message: '이미 심사한 신청이에요.' });
        applications = applications.map((a) =>
            a.id === id ? { ...a, status: approve ? 'APPROVED' : 'REJECTED', reason: approve ? null : reason, reapplyAt: approve ? null : new Date(Date.now() + settings.reapplyDays * 86_400_000).toISOString(), reviewedAt: new Date().toISOString(), reviewedByName: '나' } : a,
        );
        if (approve) grants = [row(String(++seq), app.key, app.member, { source: 'APPLICATION', grantedAt: new Date().toISOString(), grantedByName: '나' }), ...grants];
    }

    static async monthly(month: string): Promise<MonthlyCandidates> {
        await delay();
        const awarded = grants.filter((g) => g.key === 'monthly-writer' && g.period === month && g.active);
        const awardedIds = new Set(awarded.map((g) => g.member.id));
        const candidate = (member: TitleMember, posts: MonthlyCandidates['candidates'][number]['posts']) => ({ member: { ...member, avatarUrl: null }, awarded: awardedIds.has(member.id), posts });
        return clone({
            month,
            max: settings.monthlyMax,
            awarded,
            candidates: [
                candidate(MEMBERS[0]!, [
                    { id: '5100000000000000001', title: '로봇청소기 바퀴 교체 완전 정리', featured: true, manual: false },
                    { id: '5100000000000000002', title: '먼지통 3D 프린팅 개조기', featured: true, manual: false },
                ]),
                candidate(MEMBERS[1]!, [{ id: '5100000000000000003', title: '납땜 초보를 위한 인두 고르기', featured: true, manual: false }]),
                candidate(MEMBERS[2]!, [{ id: '5100000000000000005', title: '아두이노로 만든 화분 물주기', featured: true, manual: false }]),
                candidate(MEMBERS[4]!, [{ id: '5100000000000000004', title: '스텝 모터 소음 줄이는 법', featured: false, manual: true }, ...[...manualCandidates].map((postId) => ({ id: postId, title: `글 #${postId}`, featured: false, manual: true }))]),
            ],
        });
    }

    static async candidate(postId: string, on: boolean): Promise<void> {
        await delay();
        if (on) manualCandidates.add(postId);
        else manualCandidates.delete(postId);
    }

    /** 글 목록 줄·상세에 「이달의 작가 후보」 여부를 채운다(서버가 `monthlyCandidate` 를 주면 그대로) */
    static markList(view: AdminPostListView): AdminPostListView {
        return { ...view, items: view.items.map(TitleAdminMock.markItem) };
    }

    static markDetail(detail: AdminPostDetail): AdminPostDetail {
        return { ...detail, post: TitleAdminMock.markItem(detail.post) };
    }

    private static markItem(item: AdminPostItem): AdminPostItem {
        return item.monthlyCandidate === undefined ? { ...item, monthlyCandidate: manualCandidates.has(item.id) } : item;
    }
}
