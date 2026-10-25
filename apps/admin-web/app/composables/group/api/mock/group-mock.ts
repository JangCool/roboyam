// MOCK: 소모임 관리자 API(`/api/admin/groups/**` · `/api/admin/group-categories/**` · `/api/admin/group-settings/**`)의 메모리 목업 —
// `useGroupAdminApi`·`useGroupCategoryApi`·`useGroupSettingsApi` 의 `// MOCK:` 줄이 부른다. 서버가 붙으면 이 폴더(`group/api/mock/`)째 지운다.
// 모양·오류 코드는 community `docs/api-groups.md` 관리자 절(6-1 · 6-2 · 6-3 · 개설 규칙 · 5-1 매니저 규칙 · 11-10 공개 범위 · 운영 한도)을 따른다.
// 권한 오류는 로보얌 코드(`403 PERMISSION_DENIED`)다 — community 의 `MENU_FORBIDDEN`·`MENU_READ_ONLY`·`ADMIN_ONLY` 는 로보얌 권한 코드(`group.read`·`group.manage`·`group.settings`)로 바뀐다.
// 메모리 상태는 새로고침하면 처음으로 돌아간다(화면 전환 사이에는 남는다).
//
// ── 개발 전용 스위치 — 주소의 `?mock=이름` (쉼표로 여러 개) ─
//   groups-fail        소모임 목록 실패(500)            groups-empty      소모임이 하나도 없다
//   detail-fail        소모임 상세 실패(500)            deactivate-conflict  비활성화가 409 GROUP_ALREADY_INACTIVE(다른 운영자가 먼저)
//   requests-fail      개설 신청 목록 실패(500)         requests-empty    승인 대기 신청 없음
//   approve-gone       승인·반려가 404 GROUP_NOT_FOUND(다른 운영자가 먼저 처리)
//   categories-fail    분류 목록 실패(500)              categories-empty  분류가 하나도 없다
//   category-stale     분류 저장이 409 GROUP_CATEGORY_STALE(다른 관리자가 먼저 고침)
//   order-fail         분류 순서 저장 실패(500)
//   creation-fail · manager-fail · visibility-fail · policy-fail   그 구획 불러오기 실패(500)
//   settings-save-fail 설정 저장 실패(500)              history-fail      변경 이력 실패(500)
//   forbidden          모든 요청이 403 PERMISSION_DENIED(권한을 거둔 경우)
//
// ── 목업 가정 ─
//   · 문구(사유 거절·이름 규칙 등)는 서버 몫이라 한국어로만 준다. 등급(`limits.grades`)은 community 의 네 등급을 흉내 낸다 — 실제 서버는 등급 설정의 등급을 준다.
//   · 소모임 분류는 소모임 게시판(게시판 종류 `GROUP`)을 할당한다 — 목업의 게시판 목록은 실제 게시판 관리와 이어져 있지 않다.
//   · 변경 이력(설정)은 로보얌 후보 — 바뀐 칸의 전·후 값을 서버가 요청 언어의 표시 글자로 준다고 가정한다.

import { ApiError } from '~/composables/common/useApiClient';
import { MOCK_ERRORS, mockClone, mockDelay, mockFlags } from '~/composables/common/mock/mock-util';
import type {
    GroupBoardRef,
    GroupCategoryRef,
    GroupDetail,
    GroupListPage,
    GroupListQuery,
    GroupOpenRequest,
    GroupOpenRequestList,
    GroupPerson,
    GroupRecruitState,
    GroupRow,
    GroupStatus,
    GroupVisibility,
} from '~/composables/group/api/useGroupAdminApi.types';
import type { GroupCategoriesResponse, GroupCategoryCreateRequest, GroupCategoryItem, GroupCategoryOrderRequest, GroupCategorySaveRequest } from '~/composables/group/api/useGroupCategoryApi.types';
import type {
    GroupCreationRuleResponse,
    GroupCreationRuleValues,
    GroupManagerRuleResponse,
    GroupManagerRuleValues,
    GroupPolicyRuleResponse,
    GroupPolicyRuleValues,
    GroupRuleRange,
    GroupSettingHistoryItem,
    GroupSettingHistoryPage,
    GroupSettingHistoryQuery,
    GroupSettingSection,
    GroupVisibilityRuleResponse,
    GroupVisibilityRuleValues,
} from '~/composables/group/api/useGroupSettingsApi.types';

const DAY = 24 * 60 * 60 * 1000;
const ago = (days: number, hours = 0) => new Date(Date.now() - days * DAY - hours * 60 * 60 * 1000).toISOString();

// ── 공통 오류 ─

const notFound = () => new ApiError({ status: 404, code: 'GROUP_NOT_FOUND', message: '소모임을 찾을 수 없어요. 이미 처리됐거나 관리 대상이 아니에요.' });

async function gate(fail?: string): Promise<Set<string>> {
    await mockDelay();
    const on = mockFlags();
    if (on.has('forbidden')) throw MOCK_ERRORS.forbidden();
    if (fail && on.has(fail)) throw MOCK_ERRORS.internal();
    return on;
}

/** 글자 수 — 유니코드 한 글자씩(서버도 코드포인트로 센다) */
const len = (text: string) => Array.from(text).length;

// ── 사람 ─

const GRADES = {
    SEEDLING: { label: '새싹', color: 'green', icon: null },
    REGULAR: { label: '일반', color: 'blue', icon: null },
    LOYAL: { label: '단골', color: 'violet', icon: 'pi pi-star' },
    VETERAN: { label: '베테랑', color: 'amber', icon: 'pi pi-crown' },
} as const;

function person(id: string, nickname: string, grade: keyof typeof GRADES | null): GroupPerson {
    return { id, nickname, withdrawn: false, grade: grade ? { ...GRADES[grade] } : null };
}

const PEOPLE: GroupPerson[] = [
    person('7001', '로봇덕후', 'VETERAN'),
    person('7002', '코딩하는곰', 'LOYAL'),
    person('7003', '산책러', 'REGULAR'),
    person('7004', '책벌레', 'LOYAL'),
    person('7005', '차트읽는밤', 'REGULAR'),
    person('7006', '납땜장인', 'VETERAN'),
    person('7007', '주말캠퍼', 'REGULAR'),
    person('7008', '필름한롤', 'SEEDLING'),
    person('7009', '모터돌려', 'LOYAL'),
    person('7010', '아침러너', 'REGULAR'),
];
const WITHDRAWN: GroupPerson = { id: null, nickname: '탈퇴한 회원', withdrawn: true, grade: null };
const OPERATOR: GroupPerson = { id: null, nickname: '운영자 김로보', withdrawn: false, grade: null };

// ── 게시판(소모임 게시판) ─

interface MockBoard {
    boardId: string;
    slug: string;
    name: string;
    active: boolean;
    investmentNotice: boolean;
}

interface MockCategory {
    id: string;
    name: string;
    boardIds: string[];
    updatedAt: string;
}

interface MockGroup extends GroupDetail {
    /** `PENDING` 이면 개설 신청, 그 밖은 관리 대상 */
    phase: 'PENDING' | 'OPEN';
    stockPurposeAgreed: boolean;
}

interface Store {
    boards: MockBoard[];
    categories: MockCategory[];
    groups: MockGroup[];
    nextGroupId: number;
    nextCategoryId: number;
    creation: { values: GroupCreationRuleValues | null; updatedAt: string | null; updatedBy: string | null };
    manager: { values: GroupManagerRuleValues | null; updatedAt: string | null; updatedBy: string | null };
    visibility: { values: GroupVisibilityRuleValues | null; updatedAt: string | null; updatedBy: string | null; noticeSentAt: string | null };
    policy: { values: GroupPolicyRuleValues | null; updatedAt: string | null; updatedBy: string | null };
    history: GroupSettingHistoryItem[];
    nextHistoryId: number;
}

const BOARDS: MockBoard[] = [
    { boardId: '301', slug: 'club-ai', name: 'AI·테크 모임', active: true, investmentNotice: false },
    { boardId: '302', slug: 'club-robot', name: '로봇 만들기', active: true, investmentNotice: false },
    { boardId: '303', slug: 'club-stock', name: '주식 공부', active: true, investmentNotice: true },
    { boardId: '304', slug: 'club-outdoor', name: '아웃도어', active: true, investmentNotice: false },
    { boardId: '305', slug: 'club-book', name: '독서 모임', active: true, investmentNotice: false },
    { boardId: '306', slug: 'club-photo', name: '사진 모임', active: true, investmentNotice: false },
    { boardId: '307', slug: 'club-legacy', name: '옛 모임 게시판', active: false, investmentNotice: false },
];

const CREATION_DEFAULTS: GroupCreationRuleValues = { creationMode: 'APPROVAL', minGrade: 'REGULAR', maxPerOwner: 3 };
const MANAGER_DEFAULTS: GroupManagerRuleValues = { managersEnabled: false, limitDefault: 5, limitMax: 10 };
const VISIBILITY_DEFAULTS: GroupVisibilityRuleValues = { visibilityEnabled: false, cooldownDays: 7, retryHours: 24, rejectLimit: 3, dailyLimit: 10, textMax: 100 };
const POLICY_DEFAULTS: GroupPolicyRuleValues = { reapplyDays: 7, capacityMin: 5, capacityMax: 200, trendingSize: 5, trendingActiveDays: 14, eventMaxUpcoming: 30, eventMaxDaysAhead: 366, eventMaxLengthDays: 7 };

const VISIBILITY_LIMITS: GroupVisibilityRuleResponse['limits'] = {
    cooldownDays: { min: 0, max: 365 },
    retryHours: { min: 0, max: 720 },
    rejectLimit: { min: 1, max: 3 },
    dailyLimit: { min: 1, max: 100 },
    textMax: { min: 10, max: 100 },
};
const POLICY_LIMITS: GroupPolicyRuleResponse['limits'] = {
    reapplyDays: { min: 0, max: 90 },
    capacityMin: { min: 5, max: 200 },
    capacityMax: { min: 5, max: 200 },
    trendingSize: { min: 1, max: 20 },
    trendingActiveDays: { min: 1, max: 90 },
    eventMaxUpcoming: { min: 1, max: 100 },
    eventMaxDaysAhead: { min: 1, max: 730 },
    eventMaxLengthDays: { min: 1, max: 30 },
};

/** 소모임 씨앗 — [이름, 게시판, 모임장, 멤버, 정원, 상태, 모집, 가입, 오프라인, 공개 범위, 개설 며칠 전, 최근 활동 며칠 전] */
type Seed = [string, string, number, number, number, GroupStatus, GroupRecruitState, 'OPEN' | 'APPROVAL', boolean, GroupVisibility, number, number];
const SEEDS: Seed[] = [
    ['코딩 에이전트 스터디', '301', 1, 42, 50, 'ACTIVE', 'OPEN', 'OPEN', false, 'PUBLIC', 120, 0],
    ['LLM 논문 읽기', '301', 2, 18, 30, 'ACTIVE', 'OPEN', 'APPROVAL', false, 'PRIVATE', 98, 1],
    ['주말 해커톤 크루', '301', 9, 30, 30, 'ACTIVE', 'FULL', 'APPROVAL', true, 'PRIVATE', 85, 2],
    ['로봇팔 DIY', '302', 6, 25, 40, 'ACTIVE', 'OPEN', 'OPEN', true, 'PUBLIC', 80, 0],
    ['아두이노 첫걸음', '302', 0, 61, 100, 'ACTIVE', 'OPEN', 'OPEN', false, 'PUBLIC', 76, 3],
    ['라즈베리파이 연구소', '302', 8, 12, 20, 'ACTIVE', 'CLOSED', 'APPROVAL', false, 'PRIVATE', 70, 9],
    ['산업 리포트 같이 읽기', '303', 4, 33, 60, 'ACTIVE', 'OPEN', 'APPROVAL', false, 'PRIVATE', 66, 1],
    ['배당주 공부방', '303', 3, 14, 30, 'INACTIVE', 'OPEN', 'OPEN', false, 'PRIVATE', 60, 21],
    ['북한산 둘레길', '304', 2, 48, 50, 'ACTIVE', 'OPEN', 'OPEN', true, 'PUBLIC', 55, 0],
    ['차박 캠핑 모임', '304', 6, 20, 20, 'ACTIVE', 'FULL', 'OPEN', true, 'PRIVATE', 50, 4],
    ['한 달 한 권', '305', 3, 27, 40, 'ACTIVE', 'OPEN', 'OPEN', false, 'PUBLIC', 47, 2],
    ['SF 소설 읽기', '305', 0, 9, 15, 'ACTIVE', 'CLOSED', 'APPROVAL', false, 'PRIVATE', 44, 12],
    ['필름 카메라 출사', '306', 7, 16, 30, 'ACTIVE', 'OPEN', 'OPEN', true, 'PUBLIC', 40, 1],
    ['스마트폰 사진 교실', '306', 4, 38, 80, 'ACTIVE', 'OPEN', 'OPEN', false, 'PRIVATE', 36, 6],
    ['옛 로봇 대회 준비반', '307', 5, 7, 20, 'INACTIVE', 'CLOSED', 'APPROVAL', true, 'PRIVATE', 300, 120],
    ['파이썬 자동화', '301', 1, 22, 30, 'ACTIVE', 'OPEN', 'OPEN', false, 'PRIVATE', 30, 0],
    ['드론 날리기', '302', 8, 11, 25, 'ACTIVE', 'OPEN', 'APPROVAL', true, 'PRIVATE', 28, 3],
    ['새벽 러닝 크루', '304', 9, 19, 30, 'ACTIVE', 'OPEN', 'OPEN', true, 'PUBLIC', 25, 0],
    ['고전 읽기', '305', 3, 6, 10, 'INACTIVE', 'OPEN', 'APPROVAL', false, 'PRIVATE', 22, 15],
    ['ROS 입문', '302', 5, 29, 50, 'ACTIVE', 'OPEN', 'APPROVAL', false, 'PRIVATE', 18, 1],
    ['AI 그림 연습', '301', 7, 35, 60, 'ACTIVE', 'OPEN', 'OPEN', false, 'PUBLIC', 15, 0],
    ['캠핑 요리', '304', 2, 13, 20, 'ACTIVE', 'OPEN', 'OPEN', true, 'PRIVATE', 12, 2],
    ['반도체 산업 공부', '303', 4, 21, 40, 'ACTIVE', 'OPEN', 'APPROVAL', false, 'PRIVATE', 9, 0],
    ['서울 야경 사진', '306', 7, 8, 20, 'ACTIVE', 'OPEN', 'OPEN', true, 'PUBLIC', 5, 1],
];

const DESCRIPTIONS = [
    '주 1회 온라인으로 모여 도구를 비교하고, 직접 만든 결과물을 나눠요. 처음 오신 분도 환영해요.',
    '한 달에 두 번 오프라인으로 만나 함께 만들어 봐요.\n장소는 매번 공지로 정해요.\n재료비는 각자 내요.',
    '서로의 진행 상황을 공유하고 막힌 곳을 같이 풀어요.',
];

function boardRef(boardId: string): GroupBoardRef {
    const board = BOARDS.find((b) => b.boardId === boardId);
    return { slug: board?.slug ?? '', name: board?.name ?? '' };
}

function build(): Store {
    const categories: MockCategory[] = [
        { id: '11', name: 'AI·테크', boardIds: ['301', '307'], updatedAt: ago(20) },
        { id: '12', name: '로봇', boardIds: ['302'], updatedAt: ago(20) },
        { id: '13', name: '재테크', boardIds: ['303'], updatedAt: ago(18) },
        { id: '14', name: '취미·야외', boardIds: ['304', '305'], updatedAt: ago(10) },
        { id: '15', name: '새 분류', boardIds: [], updatedAt: ago(1) },
    ];
    const groups: MockGroup[] = SEEDS.map((seed, i) => {
        const [name, boardId, leaderIndex, memberCount, capacity, status, recruitState, joinType, offline, visibility, openedDays, activeDays] = seed;
        const leader = PEOPLE[leaderIndex]!;
        const inactive = status === 'INACTIVE';
        const reportCase = i === 7 ? { id: '9001', no: 128 } : null;
        const managers = i % 3 === 0 ? [{ user: PEOPLE[(leaderIndex + 1) % PEOPLE.length]!, appointedAt: ago(openedDays - 3), permissions: ['APPLICATIONS', 'EVENTS'] }] : i === 4 ? [{ user: PEOPLE[2]!, appointedAt: ago(40), permissions: ['APPLICATIONS', 'KICK', 'RECRUITING', 'EVENTS', 'UNPUBLISH'] }, { user: WITHDRAWN, appointedAt: ago(30), permissions: ['EVENTS'] }] : [];
        return {
            phase: 'OPEN',
            stockPurposeAgreed: boardId === '303',
            id: String(5001 + i),
            name,
            description: DESCRIPTIONS[i % DESCRIPTIONS.length]!,
            category: null,
            board: boardRef(boardId),
            status,
            joinType,
            recruiting: recruitState !== 'CLOSED',
            recruitState,
            capacity,
            memberCount,
            offline,
            stockNotice: boardId === '303',
            chat: { kakao: i % 2 === 0, discord: i % 4 === 1 },
            leader,
            creator: leader,
            reviewer: OPERATOR,
            requestedAt: ago(openedDays + 1),
            openedAt: ago(openedDays),
            lastActivityAt: ago(activeDays, i % 5),
            leaderLoad: { total: 0, active: 0, inactive: 0, max: 3 },
            postCount: memberCount * 3 + i,
            publicPostCount: visibility === 'PUBLIC' ? memberCount : i % 3,
            reportCaseCount: reportCase ? 3 : i % 4 === 0 ? 1 : 0,
            deactivation: inactive
                ? { at: ago(activeDays - 1), by: OPERATOR, reason: reportCase ? '신고된 홍보 글이 반복돼 운영 정책에 따라 비활성으로 바꿨어요.' : '오래 활동이 없어 비활성으로 바꿨어요.', reportCase, reactivatedAt: null, reactivatedBy: null, memo: null }
                : i === 3
                  ? { at: ago(30), by: OPERATOR, reason: '소개 글의 외부 링크 확인이 필요했어요.', reportCase: null, reactivatedAt: ago(28), reactivatedBy: OPERATOR, memo: '모임장이 링크를 지워서 다시 열었어요.' }
                  : null,
            managers,
            managerLimit: i === 4 ? 8 : 5,
            managerLimitMax: 10,
            visibility,
            visibilityChangedAt: i % 4 === 0 ? ago(Math.max(1, openedDays - 10)) : null,
            visibilityChangedBy: i % 4 === 0 ? leader : null,
        };
    });
    // 승인 대기 신청 셋 — 하나는 투자 고지 게시판
    const pending: [string, string, number, number, 'OPEN' | 'APPROVAL', boolean, GroupVisibility, number][] = [
        ['ROS2 실습 모임', '302', 9, 20, 'APPROVAL', true, 'PRIVATE', 3],
        ['ETF 공부 노트', '303', 3, 30, 'OPEN', false, 'PRIVATE', 2],
        ['동네 사진 산책', '306', 7, 15, 'OPEN', true, 'PUBLIC', 0],
    ];
    pending.forEach(([name, boardId, leaderIndex, capacity, joinType, offline, visibility, days], j) => {
        const leader = PEOPLE[leaderIndex]!;
        groups.push({
            ...groups[0]!,
            phase: 'PENDING',
            stockPurposeAgreed: boardId === '303',
            id: String(6001 + j),
            name,
            description:
                j === 0
                    ? '주말마다 모여 ROS2 로 작은 로봇을 움직여 봐요.\n장비는 함께 쓰고, 처음 오시는 분을 위한 준비 자료도 나눠요.\n모임 장소는 서울 성수동 근처예요.\n참가비는 없고 재료비만 나눠 내요.'
                    : DESCRIPTIONS[j % DESCRIPTIONS.length]!,
            board: boardRef(boardId),
            status: 'ACTIVE',
            joinType,
            recruiting: true,
            recruitState: 'OPEN',
            capacity,
            memberCount: 0,
            offline,
            stockNotice: boardId === '303',
            leader,
            creator: leader,
            reviewer: null,
            requestedAt: ago(days, 3 + j),
            openedAt: null,
            lastActivityAt: null,
            postCount: 0,
            publicPostCount: 0,
            reportCaseCount: 0,
            deactivation: null,
            managers: [],
            visibility,
            visibilityChangedAt: null,
            visibilityChangedBy: null,
        });
    });
    return {
        boards: mockClone(BOARDS),
        categories,
        groups,
        nextGroupId: 7001,
        nextCategoryId: 16,
        creation: { values: null, updatedAt: null, updatedBy: null },
        manager: { values: { managersEnabled: true, limitDefault: 5, limitMax: 10 }, updatedAt: ago(12), updatedBy: '최고 관리자' },
        visibility: { values: null, updatedAt: null, updatedBy: null, noticeSentAt: null },
        policy: { values: null, updatedAt: null, updatedBy: null },
        history: [
            {
                id: '880001',
                section: 'MANAGER',
                action: 'UPDATE',
                source: 'ADMIN',
                operatorId: '1',
                operatorName: '최고 관리자',
                operatorDeleted: false,
                changedAt: ago(12),
                changes: [{ field: 'managersEnabled', labelKey: 'group.historyField.managersEnabled', before: '꺼짐', after: '켜짐' }],
            },
        ],
        nextHistoryId: 880002,
    };
}

let store: Store | null = null;
function db(): Store {
    if (!store) store = build();
    return store;
}

// ── 파생 값 ─

function categoryOfBoard(s: Store, slug: string): GroupCategoryRef | null {
    const board = s.boards.find((b) => b.slug === slug);
    if (!board) return null;
    const category = s.categories.find((c) => c.boardIds.includes(board.boardId));
    return category ? { id: category.id, name: category.name } : null;
}

function leaderLoadOf(s: Store, leaderId: string | null): GroupDetail['leaderLoad'] {
    const mine = s.groups.filter((g) => g.leader.id !== null && g.leader.id === leaderId);
    const active = mine.filter((g) => g.phase === 'OPEN' && g.status === 'ACTIVE').length;
    const inactive = mine.filter((g) => g.phase === 'OPEN' && g.status === 'INACTIVE').length;
    return { total: mine.length, active, inactive, max: (s.creation.values ?? CREATION_DEFAULTS).maxPerOwner };
}

function detailOf(s: Store, g: MockGroup): GroupDetail {
    const { phase: _phase, stockPurposeAgreed: _agreed, ...detail } = g;
    return mockClone({ ...detail, category: categoryOfBoard(s, g.board.slug), leaderLoad: leaderLoadOf(s, g.leader.id), managerLimitMax: (s.manager.values ?? MANAGER_DEFAULTS).limitMax });
}

function rowOf(s: Store, g: MockGroup): GroupRow {
    return {
        id: g.id,
        name: g.name,
        category: categoryOfBoard(s, g.board.slug),
        board: { ...g.board },
        leader: mockClone(g.leader),
        memberCount: g.memberCount,
        capacity: g.capacity,
        status: g.status,
        recruitState: g.recruitState,
        joinType: g.joinType,
        offline: g.offline,
        stockNotice: g.stockNotice,
        visibility: g.visibility,
        openedAt: g.openedAt,
        lastActivityAt: g.lastActivityAt,
        deactivatedAt: g.status === 'INACTIVE' ? (g.deactivation?.at ?? null) : null,
    };
}

function requestOf(s: Store, g: MockGroup): GroupOpenRequest {
    return {
        id: g.id,
        name: g.name,
        description: g.description,
        category: categoryOfBoard(s, g.board.slug),
        board: { ...g.board },
        joinType: g.joinType,
        capacity: g.capacity,
        offline: g.offline,
        requester: mockClone(g.leader),
        requestedAt: g.requestedAt ?? new Date().toISOString(),
        stockPurposeAgreed: g.stockPurposeAgreed,
        visibility: g.visibility,
    };
}

function reasonProblem(text: unknown, max: number, required: boolean): string | null {
    const value = typeof text === 'string' ? text.trim() : '';
    if (required && value === '') return '사유를 적어 주세요.';
    if (len(value) > max) return `${max}자 이하로 적어 주세요.`;
    return null;
}

// ══ 개설 승인(6-1) ══

export async function mockGroupRequests(): Promise<GroupOpenRequestList> {
    const on = await gate('requests-fail');
    if (on.has('requests-empty')) return { items: [] };
    const s = db();
    const items = s.groups
        .filter((g) => g.phase === 'PENDING')
        .sort((a, b) => (a.requestedAt ?? '').localeCompare(b.requestedAt ?? ''))
        .map((g) => requestOf(s, g));
    return { items };
}

function pendingOrThrow(on: Set<string>, id: string): MockGroup {
    const g = db().groups.find((x) => x.id === id && x.phase === 'PENDING');
    if (!g || on.has('approve-gone')) throw notFound();
    return g;
}

export async function mockGroupApprove(id: string): Promise<void> {
    const on = await gate();
    const g = pendingOrThrow(on, id);
    g.phase = 'OPEN';
    g.status = 'ACTIVE';
    g.memberCount = 1;
    g.openedAt = new Date().toISOString();
    g.lastActivityAt = g.openedAt;
    g.reviewer = OPERATOR;
}

export async function mockGroupReject(id: string, reason: string): Promise<void> {
    const on = await gate();
    const problem = reasonProblem(reason, 200, true);
    if (problem) throw MOCK_ERRORS.validation({ reason: problem });
    const g = pendingOrThrow(on, id);
    const s = db();
    s.groups = s.groups.filter((x) => x !== g); // 반려는 기록으로만 남고 이 화면들에 나오지 않는다
}

// ══ 소모임 관리(6-2) ══

export async function mockGroupList(query: GroupListQuery = {}): Promise<GroupListPage> {
    const on = await gate('groups-fail');
    const s = db();
    const fields: Record<string, string> = {};
    const size = query.size ?? 20;
    const page = query.page ?? 0;
    if (!Number.isInteger(size) || size < 1 || size > 100) fields.size = '1~100 사이로 보내 주세요.';
    if (!Number.isInteger(page) || page < 0) fields.page = '0 이상이어야 해요.';
    if (Object.keys(fields).length) throw MOCK_ERRORS.validation(fields);

    const pendingRequests = s.groups.filter((g) => g.phase === 'PENDING').length;
    const all = on.has('groups-empty') ? [] : s.groups.filter((g) => g.phase === 'OPEN');
    const q = (query.q ?? '').trim().toLowerCase();
    const searched = q ? all.filter((g) => g.name.toLowerCase().includes(q) || g.leader.nickname.toLowerCase().includes(q)) : all;
    const counts = {
        all: searched.length,
        active: searched.filter((g) => g.status === 'ACTIVE').length,
        inactive: searched.filter((g) => g.status === 'INACTIVE').length,
        closed: searched.filter((g) => g.recruitState === 'CLOSED').length,
        offline: searched.filter((g) => g.offline).length,
    };
    const filtered = searched
        .filter((g) => !query.status || g.status === query.status)
        .filter((g) => !query.closed || g.recruitState === 'CLOSED')
        .filter((g) => !query.offline || g.offline)
        .filter((g) => !query.visibility || g.visibility === query.visibility)
        .sort((a, b) => (b.openedAt ?? '').localeCompare(a.openedAt ?? '') || Number(b.id) - Number(a.id));
    const items = filtered.slice(page * size, page * size + size).map((g) => rowOf(s, g));
    return { items, page, size, hasNext: (page + 1) * size < filtered.length, total: filtered.length, counts, pendingRequests };
}

function managedOrThrow(id: string): MockGroup {
    const g = db().groups.find((x) => x.id === id && x.phase === 'OPEN');
    if (!g) throw notFound();
    return g;
}

export async function mockGroupDetail(id: string): Promise<GroupDetail> {
    await gate('detail-fail');
    return detailOf(db(), managedOrThrow(id));
}

export async function mockGroupDeactivate(id: string, reason: string): Promise<void> {
    const on = await gate();
    const problem = reasonProblem(reason, 200, true);
    if (problem) throw MOCK_ERRORS.validation({ reason: problem });
    const g = managedOrThrow(id);
    if (g.status === 'INACTIVE' || on.has('deactivate-conflict')) {
        if (on.has('deactivate-conflict')) g.status = 'INACTIVE';
        throw new ApiError({ status: 409, code: 'GROUP_ALREADY_INACTIVE', message: '이미 비활성인 소모임이에요. 그 사이 다른 운영자가 처리했어요.' });
    }
    g.status = 'INACTIVE';
    g.deactivation = { at: new Date().toISOString(), by: OPERATOR, reason: reason.trim(), reportCase: null, reactivatedAt: null, reactivatedBy: null, memo: null };
}

export async function mockGroupReactivate(id: string, memo: string): Promise<void> {
    await gate();
    const problem = reasonProblem(memo, 200, false);
    if (problem) throw MOCK_ERRORS.validation({ memo: problem });
    const g = managedOrThrow(id);
    if (g.status === 'ACTIVE') throw new ApiError({ status: 409, code: 'GROUP_NOT_INACTIVE', message: '이미 열려 있는 소모임이에요. 그 사이 다른 운영자가 처리했어요.' });
    g.status = 'ACTIVE';
    const record = g.deactivation ?? { at: null, by: null, reason: null, reportCase: null, reactivatedAt: null, reactivatedBy: null, memo: null };
    g.deactivation = { ...record, reactivatedAt: new Date().toISOString(), reactivatedBy: OPERATOR, memo: memo.trim() || null };
}

// ══ 소모임 분류(6-3) ══

function categoriesBody(s: Store, empty = false): GroupCategoriesResponse {
    const groupCountOf = (boardId: string) => {
        const slug = s.boards.find((b) => b.boardId === boardId)?.slug;
        return s.groups.filter((g) => g.board.slug === slug).length;
    };
    const list = empty ? [] : s.categories;
    const categories: GroupCategoryItem[] = list.map((c, i) => {
        const boards = c.boardIds.map((boardId) => {
            const b = s.boards.find((x) => x.boardId === boardId)!;
            return { boardId, slug: b.slug, name: b.name, active: b.active, investmentNotice: b.investmentNotice, groupCount: groupCountOf(boardId) };
        });
        return {
            id: c.id,
            name: c.name,
            sortOrder: (i + 1) * 10,
            boardCount: boards.length,
            groupCount: boards.reduce((sum, b) => sum + b.groupCount, 0),
            investmentNotice: boards.some((b) => b.investmentNotice),
            boards,
            updatedAt: c.updatedAt,
        };
    });
    const available = s.boards
        .filter((b) => b.active)
        .map((b) => {
            const owner = empty ? undefined : s.categories.find((c) => c.boardIds.includes(b.boardId));
            return { boardId: b.boardId, slug: b.slug, name: b.name, categoryId: owner?.id ?? null, categoryName: owner?.name ?? null, investmentNotice: b.investmentNotice, groupCount: groupCountOf(b.boardId) };
        });
    return mockClone({ categories, available });
}

/** 이름 규칙 — 앞뒤·안쪽 공백을 다듬고 1~20자, `<`·`>`·줄바꿈·제어 문자 금지, 같은 이름 불가(공백·대소문자 무시) */
function normalizeName(raw: unknown): string {
    return typeof raw === 'string' ? raw.trim().replace(/\s+/g, ' ') : '';
}
function nameKey(name: string): string {
    return name.replace(/\s+/g, '').toLowerCase();
}
function checkName(s: Store, name: string, selfId: string | null): void {
    // eslint-disable-next-line no-control-regex
    if (name === '' || len(name) > 20 || /[<>\u0000-\u001f\u007f]/.test(name)) throw MOCK_ERRORS.validation({ name: '분류 이름은 1~20자로, < > 와 줄바꿈 없이 적어 주세요.' });
    if (s.categories.some((c) => c.id !== selfId && nameKey(c.name) === nameKey(name))) throw new ApiError({ status: 409, code: 'GROUP_CATEGORY_NAME_TAKEN', message: '같은 이름의 분류가 이미 있어요.' });
}

export async function mockGroupCategories(): Promise<GroupCategoriesResponse> {
    const on = await gate('categories-fail');
    return categoriesBody(db(), on.has('categories-empty'));
}

export async function mockGroupCategoryCreate(body: GroupCategoryCreateRequest): Promise<GroupCategoryItem> {
    await gate();
    const s = db();
    const name = normalizeName(body.name);
    checkName(s, name, null);
    const created: MockCategory = { id: String(s.nextCategoryId++), name, boardIds: [], updatedAt: new Date().toISOString() };
    s.categories.push(created);
    return categoriesBody(s).categories.find((c) => c.id === created.id)!;
}

export async function mockGroupCategorySave(id: string, body: GroupCategorySaveRequest): Promise<GroupCategoriesResponse> {
    const on = await gate();
    const s = db();
    const category = s.categories.find((c) => c.id === id);
    if (!category) throw new ApiError({ status: 404, code: 'GROUP_CATEGORY_NOT_FOUND', message: '분류를 찾을 수 없어요.' });
    if (on.has('category-stale') || (body.updatedAt && body.updatedAt < category.updatedAt)) {
        category.updatedAt = new Date().toISOString();
        throw new ApiError({ status: 409, code: 'GROUP_CATEGORY_STALE', message: '다른 관리자가 먼저 고쳤어요.' });
    }
    const name = normalizeName(body.name);
    if (name !== category.name) checkName(s, name, id);
    const ids = Array.isArray(body.boardIds) ? body.boardIds : [];
    const unique = new Set(ids);
    const bad = ids.some((boardId) => {
        const board = s.boards.find((b) => b.boardId === boardId);
        if (!board) return true;
        return !board.active && !category.boardIds.includes(boardId); // 새로 넣는 꺼진 게시판은 거절, 이미 있던 것은 그대로 받는다
    });
    if (bad || unique.size !== ids.length) throw MOCK_ERRORS.validation({ boardIds: '넣을 수 없는 게시판이 있어요. 켜진 소모임 게시판만 넣을 수 있어요.' });
    const now = new Date().toISOString();
    for (const other of s.categories) {
        if (other.id === id) continue;
        const before = other.boardIds.length;
        other.boardIds = other.boardIds.filter((boardId) => !unique.has(boardId)); // 옮겨진다
        if (other.boardIds.length !== before) other.updatedAt = now;
    }
    category.name = name;
    category.boardIds = [...ids];
    category.updatedAt = now;
    return categoriesBody(s);
}

export async function mockGroupCategoryOrder(body: GroupCategoryOrderRequest): Promise<GroupCategoriesResponse> {
    await gate('order-fail');
    const s = db();
    const ids = body.ids ?? [];
    const known = new Set(s.categories.map((c) => c.id));
    if (ids.length !== known.size || new Set(ids).size !== ids.length || ids.some((x) => !known.has(x))) throw MOCK_ERRORS.validation({ ids: '모든 분류를 한 번씩 보내 주세요.' });
    s.categories = ids.map((x) => s.categories.find((c) => c.id === x)!);
    return categoriesBody(s);
}

export async function mockGroupCategoryDelete(id: string): Promise<void> {
    await gate();
    const s = db();
    const body = categoriesBody(s).categories.find((c) => c.id === id);
    if (!body) throw new ApiError({ status: 404, code: 'GROUP_CATEGORY_NOT_FOUND', message: '분류를 찾을 수 없어요.' });
    if (body.groupCount > 0) throw new ApiError({ status: 409, code: 'GROUP_CATEGORY_NOT_EMPTY', message: '소모임이 있는 분류는 지울 수 없어요.', extras: { groupCount: body.groupCount } });
    s.categories = s.categories.filter((c) => c.id !== id);
}

// ══ 소모임 설정 ══

function rangeProblems<V extends object>(values: V, limits: Record<string, GroupRuleRange>, keys: readonly string[]): Record<string, string> {
    const out: Record<string, string> = {};
    for (const key of keys) {
        const value = (values as Record<string, unknown>)[key];
        const range = limits[key]!;
        if (typeof value !== 'number' || !Number.isInteger(value) || value < range.min || value > range.max) out[key] = `${range.min}~${range.max} 사이의 정수로 정해 주세요.`;
    }
    return out;
}

/** 이력 한 줄을 남긴다 — 바뀐 칸만, 전·후는 표시 글자(목업 가정) */
function record<V extends object>(section: GroupSettingSection, before: V, after: V, show: (key: string, value: unknown) => string): void {
    const s = db();
    const changes = Object.keys(after)
        .filter((key) => (before as Record<string, unknown>)[key] !== (after as Record<string, unknown>)[key])
        .map((key) => ({ field: key, labelKey: `group.historyField.${key}`, before: show(key, (before as Record<string, unknown>)[key]), after: show(key, (after as Record<string, unknown>)[key]) }));
    if (!changes.length) return;
    s.history.unshift({ id: String(s.nextHistoryId++), section, action: 'UPDATE', source: 'ADMIN', operatorId: '1', operatorName: '최고 관리자', operatorDeleted: false, changedAt: new Date().toISOString(), changes });
}

const onOff = (value: unknown) => (value === true ? '켜짐' : value === false ? '꺼짐' : String(value));

async function saveGate(): Promise<void> {
    const on = await gate();
    if (on.has('settings-save-fail')) throw MOCK_ERRORS.internal();
}

// ── 개설 규칙 ─

const CREATION_MODES = [
    { value: 'APPROVAL' as const, label: '승인제' },
    { value: 'OPEN' as const, label: '자유 개설' },
];
const GRADE_OPTIONS = (Object.keys(GRADES) as (keyof typeof GRADES)[]).map((value) => ({ value, label: GRADES[value].label }));

function creationBody(s: Store): GroupCreationRuleResponse {
    return mockClone({
        values: s.creation.values ?? CREATION_DEFAULTS,
        defaults: CREATION_DEFAULTS,
        limits: { creationModes: CREATION_MODES, grades: GRADE_OPTIONS, maxPerOwner: { min: 1, max: 10 } },
        fromDefaults: s.creation.values === null,
        pendingRequestCount: s.groups.filter((g) => g.phase === 'PENDING').length,
        updatedAt: s.creation.updatedAt,
        updatedBy: s.creation.updatedBy,
    });
}

export async function mockCreationRules(): Promise<GroupCreationRuleResponse> {
    await gate('creation-fail');
    return creationBody(db());
}

export async function mockSaveCreationRules(body: GroupCreationRuleValues): Promise<GroupCreationRuleResponse> {
    await saveGate();
    const s = db();
    const fields: Record<string, string> = {};
    if (!CREATION_MODES.some((m) => m.value === body.creationMode)) fields.creationMode = '개설 방식을 골라 주세요.';
    if (!GRADE_OPTIONS.some((g) => g.value === body.minGrade)) fields.minGrade = '등급을 골라 주세요.';
    Object.assign(fields, rangeProblems(body, { maxPerOwner: { min: 1, max: 10 } }, ['maxPerOwner']));
    if (Object.keys(fields).length) throw MOCK_ERRORS.validation(fields);
    const before = s.creation.values ?? CREATION_DEFAULTS;
    const after = { creationMode: body.creationMode, minGrade: body.minGrade, maxPerOwner: body.maxPerOwner };
    record('CREATION', before, after, (key, value) => (key === 'creationMode' ? (CREATION_MODES.find((m) => m.value === value)?.label ?? String(value)) : key === 'minGrade' ? (GRADE_OPTIONS.find((g) => g.value === value)?.label ?? String(value)) : String(value)));
    s.creation = { values: after, updatedAt: new Date().toISOString(), updatedBy: '최고 관리자' };
    return creationBody(s);
}

// ── 매니저 규칙 ─

function managerBody(s: Store): GroupManagerRuleResponse {
    const open = s.groups.filter((g) => g.phase === 'OPEN' && g.status === 'ACTIVE');
    const withManagers = open.filter((g) => g.managers.length > 0);
    const byLimit = new Map<number, number>();
    for (const g of open) byLimit.set(g.managerLimit, (byLimit.get(g.managerLimit) ?? 0) + 1);
    return mockClone({
        values: s.manager.values ?? MANAGER_DEFAULTS,
        defaults: MANAGER_DEFAULTS,
        limits: { limitDefault: { min: 1, max: 10 }, limitMax: { min: 1, max: 10 } },
        fromDefaults: s.manager.values === null,
        usage: {
            managerCount: withManagers.reduce((sum, g) => sum + g.managers.filter((m) => !m.user.withdrawn).length, 0),
            groupCount: withManagers.length,
            limitCounts: [...byLimit.entries()].sort((a, b) => a[0] - b[0]).map(([limit, groupCount]) => ({ limit, groupCount })),
        },
        updatedAt: s.manager.updatedAt,
        updatedBy: s.manager.updatedBy,
    });
}

export async function mockManagerRules(): Promise<GroupManagerRuleResponse> {
    await gate('manager-fail');
    return managerBody(db());
}

export async function mockSaveManagerRules(body: GroupManagerRuleValues): Promise<GroupManagerRuleResponse> {
    await saveGate();
    const s = db();
    const fields: Record<string, string> = {};
    if (typeof body.managersEnabled !== 'boolean') fields.managersEnabled = '켜짐·꺼짐 중 하나로 보내 주세요.';
    Object.assign(fields, rangeProblems(body, { limitDefault: { min: 1, max: 10 }, limitMax: { min: 1, max: 10 } }, ['limitDefault', 'limitMax']));
    if (!fields.limitDefault && !fields.limitMax && body.limitDefault > body.limitMax) fields.limitDefault = '기본 인원은 최대 인원보다 클 수 없어요. 최대 인원을 늘리거나 기본 인원을 줄여 주세요.';
    if (Object.keys(fields).length) throw MOCK_ERRORS.validation(fields);
    const after = { managersEnabled: body.managersEnabled, limitDefault: body.limitDefault, limitMax: body.limitMax };
    record('MANAGER', s.manager.values ?? MANAGER_DEFAULTS, after, (key, value) => (key === 'managersEnabled' ? onOff(value) : `${String(value)}명`));
    s.manager = { values: after, updatedAt: new Date().toISOString(), updatedBy: '최고 관리자' };
    return managerBody(s);
}

// ── 공개 범위 ─

const VISIBILITY_NUMBERS = ['cooldownDays', 'retryHours', 'rejectLimit', 'dailyLimit', 'textMax'] as const;
const VISIBILITY_UNITS: Record<string, string> = { cooldownDays: '일', retryHours: '시간', rejectLimit: '회', dailyLimit: '건', textMax: '자' };

function visibilityBody(s: Store, noticeSent = 0): GroupVisibilityRuleResponse {
    const open = s.groups.filter((g) => g.phase === 'OPEN' && g.status === 'ACTIVE');
    return mockClone({
        values: s.visibility.values ?? VISIBILITY_DEFAULTS,
        defaults: VISIBILITY_DEFAULTS,
        limits: VISIBILITY_LIMITS,
        fromDefaults: s.visibility.values === null,
        usage: {
            publicGroupCount: open.filter((g) => g.visibility === 'PUBLIC').length,
            privateGroupCount: open.filter((g) => g.visibility === 'PRIVATE').length,
            pendingRequestCount: 2,
            managerNoticeSentAt: s.visibility.noticeSentAt,
            managerNoticeTargetCount: open.reduce((sum, g) => sum + g.managers.filter((m) => !m.user.withdrawn && m.permissions.includes('UNPUBLISH')).length, 0),
        },
        noticeSent,
        updatedAt: s.visibility.updatedAt,
        updatedBy: s.visibility.updatedBy,
    });
}

export async function mockVisibilityRules(): Promise<GroupVisibilityRuleResponse> {
    await gate('visibility-fail');
    return visibilityBody(db());
}

export async function mockSaveVisibilityRules(body: GroupVisibilityRuleValues): Promise<GroupVisibilityRuleResponse> {
    await saveGate();
    const s = db();
    const fields: Record<string, string> = {};
    if (typeof body.visibilityEnabled !== 'boolean') fields.visibilityEnabled = '켜짐·꺼짐 중 하나로 보내 주세요.';
    Object.assign(fields, rangeProblems(body, VISIBILITY_LIMITS, VISIBILITY_NUMBERS));
    if (Object.keys(fields).length) throw MOCK_ERRORS.validation(fields);
    const before = s.visibility.values ?? VISIBILITY_DEFAULTS;
    const after: GroupVisibilityRuleValues = { visibilityEnabled: body.visibilityEnabled, cooldownDays: body.cooldownDays, retryHours: body.retryHours, rejectLimit: body.rejectLimit, dailyLimit: body.dailyLimit, textMax: body.textMax };
    record('VISIBILITY', before, after, (key, value) => (key === 'visibilityEnabled' ? onOff(value) : `${String(value)}${VISIBILITY_UNITS[key] ?? ''}`));
    // 켜짐으로 저장하면 같은 요청에서 매니저 안내 알림이 한 번 나간다(이미 보냈으면 다시 보내지 않는다)
    let noticeSent = 0;
    if (after.visibilityEnabled && !s.visibility.noticeSentAt) {
        noticeSent = visibilityBody(s).usage.managerNoticeTargetCount;
        s.visibility.noticeSentAt = new Date().toISOString();
    }
    s.visibility = { ...s.visibility, values: after, updatedAt: new Date().toISOString(), updatedBy: '최고 관리자' };
    return visibilityBody(s, noticeSent);
}

// ── 운영 한도 ─

const POLICY_KEYS = Object.keys(POLICY_DEFAULTS) as (keyof GroupPolicyRuleValues)[];
const POLICY_UNITS: Record<string, string> = { reapplyDays: '일', capacityMin: '명', capacityMax: '명', trendingSize: '곳', trendingActiveDays: '일', eventMaxUpcoming: '개', eventMaxDaysAhead: '일', eventMaxLengthDays: '일' };

function policyBody(s: Store): GroupPolicyRuleResponse {
    return mockClone({ values: s.policy.values ?? POLICY_DEFAULTS, defaults: POLICY_DEFAULTS, limits: POLICY_LIMITS, fromDefaults: s.policy.values === null, updatedAt: s.policy.updatedAt, updatedBy: s.policy.updatedBy });
}

export async function mockPolicyRules(): Promise<GroupPolicyRuleResponse> {
    await gate('policy-fail');
    return policyBody(db());
}

export async function mockSavePolicyRules(body: GroupPolicyRuleValues): Promise<GroupPolicyRuleResponse> {
    await saveGate();
    const s = db();
    const fields = rangeProblems(body, POLICY_LIMITS, POLICY_KEYS);
    if (!fields.capacityMin && !fields.capacityMax && body.capacityMax < body.capacityMin) fields.capacityMax = '최대 정원은 최소 정원 이상이어야 해요.';
    if (Object.keys(fields).length) throw MOCK_ERRORS.validation(fields);
    const after = Object.fromEntries(POLICY_KEYS.map((key) => [key, body[key]])) as unknown as GroupPolicyRuleValues;
    record('POLICY', s.policy.values ?? POLICY_DEFAULTS, after, (key, value) => `${String(value)}${POLICY_UNITS[key] ?? ''}`);
    s.policy = { values: after, updatedAt: new Date().toISOString(), updatedBy: '최고 관리자' };
    return policyBody(s);
}

// ── 변경 이력(로보얌 후보) ─

export async function mockGroupSettingHistory(query: GroupSettingHistoryQuery): Promise<GroupSettingHistoryPage> {
    await gate('history-fail');
    const rows = db().history.filter((row) => !query.section || row.section === query.section);
    const items = rows.slice(query.page * query.size, query.page * query.size + query.size);
    return mockClone({ items, total: rows.length, page: query.page, size: query.size });
}
