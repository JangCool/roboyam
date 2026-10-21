// MOCK: 회원 조회 API(`GET /api/admin/members`, `…/members/{memberId}`, `…/members/role-options`)의 메모리 목업 — `useMemberApi.ts` 가 이 파일의 함수를 부른다.
// 서버가 붙으면 `member/api/mock/` 와 함께 지운다. 회원은 공개 사이트 이용자다(운영자 계정이 아니다).
//
// ── 개발 전용 스위치 — 주소의 `?mock=이름` (쉼표로 여러 개) ─
//   member-error   조회 요청이 `500 INTERNAL` 로 실패(오류 상태와 다시 시도)
//   member-empty   회원이 하나도 없는 상태(조건과 상관없이 빈 결과 — 빈 상태 확인용)
//   member-detail-error   상세(`GET /api/admin/members/{memberId}`) 요청이 `500 INTERNAL` 로 실패(Drawer 안 오류 상태와 다시 시도)
//
// ── 흉내 ─
//   회원 120명(고정 시드 — 새로고침해도 같은 사람): 등급 4종 · 사업자 일부 · 상태(정상 다수, 기간 정지·영구 정지·탈퇴·휴면 일부) · 가입 방식 섞기 · 포인트 0~수만 ·
//   로그인 이력 없는 회원 5명 · 게시판 매니저·스텝·제재 이력 있는 회원 몇 명 · 회원 역할(일반 회원·사업자 회원 + 등급 역할). 역할 이름은 문자열 목업이다(회원 역할 정의는 별도 작업).
//   서버 규칙 흉내: 모든 조건이 먹는다(키워드·상태·역할·가입일·최근 로그인·로그인 이력 없음·등급·유형·이메일 인증·가입 방식·휴면 전환일·상태 변경일·포인트·매니저/스텝·제재 이력),
//   정렬(칸,asc|desc — 없는 값은 늘 뒤)·쪽 나누기. 기간은 UTC ISO 순간이고 양 끝을 포함한다.
//   권한: `member.read` 가 없으면 `403 PERMISSION_DENIED`. 이메일은 `member.pii` 가 없으면 `ab***@gmail.com` 으로 가려서 준다(`emailMasked: true`) —
//   ⚠ 가려진 값으로는 찾을 수 없어야 하므로 `member.pii` 가 없을 때 이메일 검색은 **전체 주소가 같을 때만** 맞는다(정책 미확정 — 후보).
//   오류: 기간이 거꾸로면 `400 MEMBER_PERIOD_REVERSED`, 포인트 범위가 거꾸로면 `400 MEMBER_POINT_RANGE_REVERSED`, 쪽 크기가 1~100 밖이면 `400 VALIDATION`.
//   "나"는 로그인 목업(`admin-auth-mock.ts`)의 계정이다.
//
// ── 상세 보강(후보 계약, 확정 전 — 근거는 `useMemberApi.types.ts` 머리 주석: community 문서 / 그누보드7 회원 상세(사용자 제시)) ─
//   상세(`mockMemberGet`)가 소개·소셜 연동·성인 확인·쪽지 수신·휴면 예정·닉네임 이력·등급·포인트·개인 설정·연락처·프로필·동의 이력·공동구매·제재/신고 요약을 시드 순번에서 파생해 채운다(새로고침해도 같다).
//   · 등급 이력의 등급 키와 다음 등급 조건은 등급 설정 목록(`mockGradeSnapshot()`)에서 만든다. 시간대·시간 형식·날짜 형식은 `settings.display` 한 곳이다(연락처 탭도 이 값).
//   · PII: `member.pii` 가 없으면 휴대폰·전화는 가운데 묶음을 `*` 로(`010-****-5678`), 주소는 앞 두 마디(시·구)만(`서울특별시 강남구 ***`) 가려서 주고 `piiMasked: true`, 닉네임 변경 이력은 주지 않는다(`nicknameHistory: null`).
//     ⚠ 이 규칙은 정책 문서(`docs/domain/operator-permissions.md`)에 이메일 외에는 없어 목업 가정이다. 이 상태는 `staff@example.com`(운영진 — `member.pii` 없음)으로 로그인하면 볼 수 있다.
//   · 탈퇴 회원: 새 칸이 모두 비고(`null`·빈 배열) `withdrawnAt` 만 채워진다. 휴면 예정은 정상 회원 일부(`i % 29 === 4`)에만 있고 마지막 로그인과는 맞추지 않았다(목업).
//   · 공동구매 주문 상태는 community `api-commerce.md` 의 주문 상태(PENDING·PAID·PREPARING·SHIPPED·DELIVERED·CANCELED·REFUNDED)를 따랐다. 건수·최근 주문·배송지 스냅샷 값은 목업이다.

import type { DisplayValues } from '#layers/display-time/utils/util-time';
import { ApiError } from '~/composables/common/useApiClient';
import { MOCK_ERRORS, mockDelay, mockFlags } from '~/composables/common/mock/mock-util';
import { mockCurrentAdmin } from '~/composables/auth/api/mock/admin-auth-mock';
import { mockGradeResolve, mockGradeSnapshot } from '~/composables/grade/api/mock/grade-mock';
import type {
    MemberAuthMethod,
    MemberBoardRole,
    MemberConsentChange,
    MemberConsents,
    MemberDetail,
    MemberGrade,
    MemberGradeChange,
    MemberGradePoint,
    MemberGroupBuy,
    MemberGroupBuyOrder,
    MemberNicknameChange,
    MemberOrderStatus,
    MemberPage,
    MemberPointEntry,
    MemberSettings,
    MemberQuery,
    MemberRoleDetail,
    MemberRoleGrantMode,
    MemberRoleOption,
    MemberRoleTypeId,
    MemberRow,
    MemberSanction,
    MemberStatus,
} from '~/composables/member/api/useMemberApi.types';
import UtilGrade from '~/utils/util-grade';
import UtilPermissionCatalog from '~/utils/util-permission-catalog';
import UtilRole from '~/utils/util-role';

const DAY = 86_400_000;

interface RoleDef extends MemberRoleOption {
    typeId: MemberRoleTypeId;
    grantMode: MemberRoleGrantMode;
}

/** 등급 역할을 뺀 회원 역할 — 이름·아이디는 역할 관리 화면의 회원 역할 시드(`role-mock.ts`)와 같다. 정의는 별도 작업이라 이 목업이 문자열로 따로 들고 있다. 등급 역할은 등급 설정의 등급 목록에서 만든다(`roleDefs()`) */
const STATIC_ROLE_DEFS: RoleDef[] = [
    { roleId: 'ROLE_MEMBER_GENERAL', roleName: '일반 회원', typeId: UtilRole.TYPE_KIND, grantMode: 'AUTO' },
    { roleId: 'ROLE_MEMBER_BUSINESS', roleName: '사업자 회원', typeId: UtilRole.TYPE_KIND, grantMode: 'AUTO' },
    { roleId: 'ROLE_BOARD_MANAGER', roleName: '게시판 매니저', typeId: UtilRole.TYPE_BOARD, grantMode: 'APPOINTED' },
    { roleId: UtilPermissionCatalog.BOARD_STAFF_ROLE, roleName: '게시판 스텝', typeId: UtilRole.TYPE_BOARD, grantMode: 'APPOINTED' },
    { roleId: 'ROLE_BETA_TESTER', roleName: '베타 테스터', typeId: UtilRole.TYPE_CUSTOM, grantMode: 'MANUAL' },
    { roleId: 'ROLE_VIP_MEMBER', roleName: '우수 회원', typeId: UtilRole.TYPE_CUSTOM, grantMode: 'MANUAL' },
    { roleId: 'ROLE_EVENT_GUEST', roleName: '이벤트 참가자', typeId: UtilRole.TYPE_CUSTOM, grantMode: 'MANUAL' },
];
/** 등급 역할(`ROLE_GRADE_<gradeKey>`)까지 합친 회원 역할 정의 — 등급은 등급 설정의 목록에서 만들어진다(정적 시드가 아니다) */
function roleDefs(): RoleDef[] {
    return [...mockGradeSnapshot().map((g): RoleDef => ({ roleId: UtilGrade.roleIdOf(g.gradeKey), roleName: g.label, typeId: UtilRole.TYPE_GRADE, grantMode: 'AUTO' })), ...STATIC_ROLE_DEFS];
}
function roleOptions(): MemberRoleOption[] {
    return roleDefs().map(({ roleId, roleName }) => ({ roleId, roleName }));
}
const BOARDS = ['자유게시판', '질문과 답변', '핫딜 정보', '소모임 이야기', '공지 모음'];
/**
 * 등급 설정이 바뀐 뒤의 회원 — 지운 등급의 회원은 바로 아래 등급으로 옮겨 간 것으로 보고(`mockGradeResolve`), 등급 역할의 이름은 지금 등급 이름을 쓴다. 시드는 한 번 만들고 이 함수로 읽을 때마다 맞춘다.
 */
function live(m: MemberRow): MemberRow {
    const options = roleOptions();
    const roles = m.roles.map((r) => {
        const key = UtilGrade.gradeKeyOfRole(r.roleId, UtilRole.TYPE_GRADE);
        const id = key !== null ? UtilGrade.roleIdOf(mockGradeResolve(key)) : r.roleId;
        return options.find((o) => o.roleId === id) ?? r;
    });
    return { ...m, grade: mockGradeResolve(m.grade), roles };
}

/** 서열 — 등급 목록에서의 위치 */
function gradeRank(key: string): number {
    return Math.max(0, mockGradeSnapshot().findIndex((g) => g.gradeKey === key));
}
const STATUS_RANK: Record<MemberStatus, number> = { ACTIVE: 0, SUSPENDED: 1, BANNED: 2, WITHDRAWN: 3, DORMANT: 4 };

/** 고정 시드 난수 — 새로고침해도 같은 회원이 나온다 */
function mulberry32(seed: number): () => number {
    let a = seed;
    return () => {
        a = (a + 0x6d2b79f5) | 0;
        let t = Math.imul(a ^ (a >>> 15), 1 | a);
        t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t;
        return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
    };
}

const FIRST = ['달빛', '푸른', '하늘', '바다', '작은', '큰', '빠른', '조용한', '행복한', '즐거운', '맑은', '따뜻한', '용감한', '느긋한', '반짝'];
const SECOND = ['고양이', '여우', '토끼', '다람쥐', '거북이', '참새', '고래', '나무', '구름', '별', '바람', '여행자', '사과', '곰돌이', '수달'];
const DOMAINS = ['gmail.com', 'naver.com', 'daum.net', 'kakao.com', 'example.com'];

let cache: MemberRow[] | null = null;
/** 상세가 쓰는 덧붙임 — 시드 순번과 맡은 게시판 이름(게시판 매니저·스텝) */
const META = new Map<string, { index: number; boards: string[] }>();

function seed(): MemberRow[] {
    if (cache) return cache;
    const rnd = mulberry32(20261021);
    const now = Date.now();
    const iso = (ms: number) => new Date(ms).toISOString();
    /** 로그인 이력이 없는 회원(정상 상태에서만) */
    const NEVER_LOGGED_IN = new Set([20, 41, 46, 89, 111]);
    const rows: MemberRow[] = [];

    for (let i = 0; i < 120; i++) {
        const business = i % 9 === 4;
        const status: MemberStatus = i % 10 === 7 ? 'WITHDRAWN' : i % 8 === 2 ? 'DORMANT' : i % 15 === 3 ? 'SUSPENDED' : i % 24 === 5 ? 'BANNED' : 'ACTIVE';
        const pick = rnd();
        const authMethod: MemberAuthMethod = pick < 0.5 ? 'EMAIL' : pick < 0.75 ? 'KAKAO' : pick < 0.9 ? 'NAVER' : 'GOOGLE';
        const gradePick = rnd();
        const grade: MemberGrade = business ? 'REGULAR' : gradePick < 0.3 ? 'SEEDLING' : gradePick < 0.6 ? 'REGULAR' : gradePick < 0.85 ? 'LOYAL' : 'VETERAN';
        // 휴면은 1년 넘게 안 들어온 회원이라 가입이 그보다 오래됐다
        const joined = status === 'DORMANT' ? now - (430 + rnd() * 290) * DAY : now - (20 + rnd() * 700) * DAY;
        const withdrawn = status === 'WITHDRAWN';

        // 이메일: 탈퇴하면 지워진다(UserEntity.withdraw). 소셜 가입자 일부는 이메일을 안 줬다.
        const noEmail = withdrawn || (authMethod !== 'EMAIL' && i % 10 === 3);
        const local = `${['kim', 'lee', 'park', 'choi', 'jung', 'han', 'yoon', 'seo'][i % 8]}${i % 3 === 0 ? '' : '.'}${i * 7 + 11}`;
        const email = noEmail ? null : `${local}@${DOMAINS[i % DOMAINS.length]}`;
        const verified = email !== null && (authMethod !== 'EMAIL' || rnd() < 0.85);

        let lastLogin: number | null;
        let dormant: number | null = null;
        let statusChanged: number | null = null;
        if (status === 'DORMANT') {
            lastLogin = joined + rnd() * (now - 380 * DAY - joined);
            dormant = lastLogin + 365 * DAY;
            statusChanged = dormant;
        } else if (withdrawn) {
            lastLogin = joined + rnd() * 30 * DAY;
            statusChanged = Math.min(now - DAY, lastLogin + rnd() * 30 * DAY);
        } else if (NEVER_LOGGED_IN.has(i)) {
            lastLogin = null;
        } else {
            lastLogin = now - rnd() * 40 * DAY;
        }
        // 기간 정지는 아직 끝나기 전(14일 중)이어야 하고, 가입보다 앞설 수 없다
        if (status === 'SUSPENDED') statusChanged = now - (1 + rnd() * 12) * DAY;
        if (status === 'BANNED') statusChanged = Math.max(joined + DAY, now - (1 + rnd() * 60) * DAY);
        // 휴면에서 돌아온 정상 회원 — 휴면 전환 기록만 남아 있다
        if (status === 'ACTIVE' && i % 13 === 0) dormant = Math.max(joined + DAY, now - (60 + rnd() * 140) * DAY);
        if (lastLogin !== null && lastLogin < joined) lastLogin = joined;

        const point = i % 6 === 0 ? 0 : Math.floor(rnd() * rnd() * 42_000);
        const eligible = status === 'ACTIVE' && !business && grade !== 'SEEDLING' && verified;
        const boardRole: MemberBoardRole = eligible && i % 11 === 1 ? 'MANAGER' : eligible && i % 7 === 2 ? 'STAFF' : 'NONE';
        const sanctionCount = status === 'SUSPENDED' ? 1 + (i % 2) : status === 'BANNED' ? 2 + (i % 2) : status === 'ACTIVE' && i % 10 === 6 ? 1 : 0;
        // 역할: 유형 + 등급(자동), 게시판 단위(매니저·스텝), 사용자 정의(일부). 탈퇴하면 모두 잃는다
        const boards = boardRole === 'NONE' ? [] : BOARDS.filter((_, b) => (i + b) % 4 === 0 || (boardRole === 'MANAGER' && b === i % BOARDS.length)).slice(0, boardRole === 'MANAGER' ? 2 : 3);
        const roleIds = withdrawn
            ? []
            : [
                  business ? 'ROLE_MEMBER_BUSINESS' : 'ROLE_MEMBER_GENERAL',
                  UtilGrade.roleIdOf(grade),
                  ...(boardRole === 'MANAGER' ? ['ROLE_BOARD_MANAGER'] : boardRole === 'STAFF' ? [UtilPermissionCatalog.BOARD_STAFF_ROLE] : []),
                  ...(status === 'ACTIVE' && !business && i % 17 === 3 ? ['ROLE_BETA_TESTER'] : []),
                  ...(status === 'ACTIVE' && !business && i % 19 === 5 ? ['ROLE_VIP_MEMBER'] : []),
                  ...(status === 'ACTIVE' && !business && i % 23 === 7 ? ['ROLE_EVENT_GUEST'] : []),
              ];
        const memberNo = `18${String(i + 1).padStart(6, '0')}${String(Math.floor(rnd() * 1e9)).padStart(9, '0')}`;
        META.set(memberNo, { index: i, boards });

        rows.push({
            // 서버라면 Snowflake 문자열 — 숫자로 바꾸지 않는다
            memberNo,
            email,
            emailMasked: false,
            nickname: `${FIRST[i % FIRST.length]}${SECOND[(i * 7) % SECOND.length]}${i >= FIRST.length ? i : ''}`,
            grade,
            memberType: business ? 'BUSINESS' : 'GENERAL',
            status,
            authMethod,
            emailVerifiedAt: verified ? iso(joined + 10 * 60_000) : null,
            joinedAt: iso(joined),
            lastLoginAt: lastLogin === null ? null : iso(lastLogin),
            point,
            roles: roleIds.flatMap((id) => roleOptions().find((r) => r.roleId === id) ?? []),
            dormantAt: dormant === null ? null : iso(dormant),
            statusChangedAt: statusChanged === null ? null : iso(statusChanged),
            boardRole,
            sanctionCount,
        });
    }
    cache = rows;
    return rows;
}

/** 서버의 마스킹 흉내 — 앞 두 글자(짧으면 한 글자)만 남기고 `***` */
function maskEmail(email: string): string {
    const at = email.indexOf('@');
    if (at < 0) return '***';
    const local = email.slice(0, at);
    return `${local.slice(0, local.length <= 2 ? 1 : 2)}***${email.slice(at)}`;
}

function actorCan(code: string): boolean {
    const actor = mockCurrentAdmin();
    return actor !== null && (actor.isSuper || actor.permissions.includes(code));
}

function inRange(value: string | null, from: string | undefined, to: string | undefined): boolean {
    if (from === undefined && to === undefined) return true;
    if (value === null) return false;
    const at = Date.parse(value);
    return (from === undefined || at >= Date.parse(from)) && (to === undefined || at <= Date.parse(to));
}

function reversed(from: string | undefined, to: string | undefined): boolean {
    return from !== undefined && to !== undefined && Date.parse(from) > Date.parse(to);
}

function matches(m: MemberRow, q: MemberQuery, canPii: boolean): boolean {
    const keyword = (q.keyword ?? '').trim().toLowerCase();
    if (keyword) {
        const field = q.searchField ?? 'ALL';
        const byNo = m.memberNo.includes(keyword);
        const byNick = m.nickname.toLowerCase().includes(keyword);
        // `member.pii` 가 없으면 가려진 값으로 짐작해 찾을 수 없게 — 전체 주소가 같을 때만 맞는다
        const email = m.email?.toLowerCase() ?? null;
        const byEmail = email !== null && (canPii ? email.includes(keyword) : email === keyword);
        const hit = field === 'EMAIL' ? byEmail : field === 'NICKNAME' ? byNick : field === 'MEMBER_NO' ? byNo : byNo || byNick || byEmail;
        if (!hit) return false;
    }
    if (q.statuses?.length && !q.statuses.includes(m.status)) return false;
    if (q.roleIds?.length && !live(m).roles.some((r) => q.roleIds!.includes(r.roleId))) return false;
    if (!inRange(m.joinedAt, q.joinedFrom, q.joinedTo)) return false;
    if (q.noLoginHistory) {
        if (m.lastLoginAt !== null) return false;
    } else if (!inRange(m.lastLoginAt, q.lastLoginFrom, q.lastLoginTo)) return false;
    if (q.grades?.length && !q.grades.includes(mockGradeResolve(m.grade))) return false;
    if (q.memberType && m.memberType !== q.memberType) return false;
    if (q.emailVerified !== undefined && (m.emailVerifiedAt !== null) !== q.emailVerified) return false;
    if (q.authMethods?.length && !q.authMethods.includes(m.authMethod)) return false;
    if (!inRange(m.dormantAt, q.dormantFrom, q.dormantTo)) return false;
    if (q.statusChangedKind) {
        const kindOk = q.statusChangedKind === 'SUSPENDED' ? m.status === 'SUSPENDED' || m.status === 'BANNED' : m.status === q.statusChangedKind;
        if (!kindOk) return false;
    }
    if (!inRange(m.statusChangedAt, q.statusChangedFrom, q.statusChangedTo)) return false;
    if (q.pointMin !== undefined && m.point < q.pointMin) return false;
    if (q.pointMax !== undefined && m.point > q.pointMax) return false;
    if (q.boardRole && m.boardRole !== q.boardRole) return false;
    if (q.hasSanction !== undefined && m.sanctionCount > 0 !== q.hasSanction) return false;
    return true;
}

/** 정렬 값 — 없는 값(null)은 방향과 상관없이 늘 뒤로 보낸다 */
function sortValue(m: MemberRow, field: string): string | number | null {
    switch (field) {
        case 'memberNo':
            return m.memberNo;
        case 'email':
            return m.email?.toLowerCase() ?? null;
        case 'nickname':
            return m.nickname;
        case 'grade':
            return gradeRank(mockGradeResolve(m.grade));
        case 'status':
            return STATUS_RANK[m.status];
        case 'lastLoginAt':
            return m.lastLoginAt === null ? null : Date.parse(m.lastLoginAt);
        case 'point':
            return m.point;
        case 'dormantAt':
            return m.dormantAt === null ? null : Date.parse(m.dormantAt);
        default:
            return Date.parse(m.joinedAt);
    }
}

function badRequest(code: string, message: string): ApiError {
    return new ApiError({ status: 400, code, message });
}

/** `GET /api/admin/members` */
export async function mockMemberSearch(query: MemberQuery): Promise<MemberPage> {
    await mockDelay();
    const flags = mockFlags();
    if (flags.has('member-error')) throw MOCK_ERRORS.internal();
    if (!actorCan('member.read')) throw new ApiError({ status: 403, code: 'PERMISSION_DENIED', message: '이 작업을 할 권한이 없어요.' });

    const size = query.size ?? 20;
    const page = query.page ?? 0;
    if (!Number.isInteger(size) || size < 1 || size > 100 || !Number.isInteger(page) || page < 0) throw badRequest('VALIDATION', '쪽 번호나 크기가 올바르지 않아요.');
    if (reversed(query.joinedFrom, query.joinedTo) || reversed(query.lastLoginFrom, query.lastLoginTo) || reversed(query.dormantFrom, query.dormantTo) || reversed(query.statusChangedFrom, query.statusChangedTo)) {
        throw badRequest('MEMBER_PERIOD_REVERSED', '기간의 시작이 끝보다 늦어요.');
    }
    if (query.pointMin !== undefined && query.pointMax !== undefined && query.pointMin > query.pointMax) throw badRequest('MEMBER_POINT_RANGE_REVERSED', '포인트 최소가 최대보다 커요.');

    if (flags.has('member-empty')) return { items: [], total: 0, page, size };

    const canPii = actorCan('member.pii');
    const found = seed().filter((m) => matches(m, query, canPii));

    const [field = 'joinedAt', dir = 'desc'] = (query.sort ?? 'joinedAt,desc').split(',');
    const sign = dir === 'asc' ? 1 : -1;
    found.sort((a, b) => {
        const av = sortValue(a, field);
        const bv = sortValue(b, field);
        if (av === null && bv === null) return 0;
        if (av === null) return 1;
        if (bv === null) return -1;
        return av < bv ? -sign : av > bv ? sign : 0;
    });

    const items = found.slice(page * size, page * size + size).map((m) => ({
        ...live(m),
        roles: live(m).roles.map((r) => ({ ...r })),
        email: m.email !== null && !canPii ? maskEmail(m.email) : m.email,
        emailMasked: m.email !== null && !canPii,
    }));
    return { items, total: found.length, page, size };
}

/** `GET /api/admin/members/role-options` — 역할 고르개 목록 */
export async function mockMemberRoleOptions(): Promise<MemberRoleOption[]> {
    await mockDelay();
    return roleOptions().map((r) => ({ ...r }));
}

const SANCTION_REASONS = ['스팸·광고 게시', '비방·인신공격', '투자 권유', '게시판 주제 불일치', '도배'];

/** 제재 이력 흉내 — 개수는 목록의 `sanctionCount` 와 같다. 가장 최근 것이 먼저이고, 현재 상태(기간·영구 정지)가 가장 최근 제재와 맞는다 */
function sanctionsOf(m: MemberRow, index: number): MemberSanction[] {
    const out: MemberSanction[] = [];
    const joined = Date.parse(m.joinedAt);
    const base = Date.parse(m.statusChangedAt ?? m.lastLoginAt ?? m.joinedAt);
    for (let k = 0; k < m.sanctionCount; k++) {
        const occurred = Math.max(joined, base - k * 40 * DAY - (k === 0 && m.statusChangedAt === null ? 12 * DAY : 0));
        const reason = SANCTION_REASONS[(index + k) % SANCTION_REASONS.length]!;
        const sanctionId = `${m.memberNo}-${k + 1}`;
        if (k === 0 && m.status === 'BANNED') {
            out.push({ sanctionId, kind: 'BAN', scope: 'SITE', boardName: null, occurredAt: new Date(occurred).toISOString(), endsAt: null, status: 'ACTIVE', reason });
        } else if (k === 0 && m.status === 'SUSPENDED') {
            out.push({ sanctionId, kind: 'SUSPENSION', scope: 'SITE', boardName: null, occurredAt: new Date(occurred).toISOString(), endsAt: new Date(occurred + 14 * DAY).toISOString(), status: 'ACTIVE', reason });
        } else if (k % 2 === 1) {
            out.push({ sanctionId, kind: 'WARNING', scope: 'SITE', boardName: null, occurredAt: new Date(occurred).toISOString(), endsAt: null, status: 'ENDED', reason });
        } else {
            // 게시판 한정 쓰기 제한(스텝·매니저가 준 것) — 기간이 지났거나 운영자가 풀었다
            out.push({ sanctionId, kind: 'SUSPENSION', scope: 'BOARD', boardName: BOARDS[(index + k) % BOARDS.length]!, occurredAt: new Date(occurred).toISOString(), endsAt: new Date(occurred + 3 * DAY).toISOString(), status: index % 2 === 0 ? 'ENDED' : 'RELEASED', reason });
        }
    }
    return out;
}

// ── 상세 보강 — 후보 계약, 확정 전(근거는 `useMemberApi.types.ts` 머리 주석). 값은 시드 순번(`meta.index`)과 회원 행에서 파생해 새로고침해도 같다 ─

/** 서버의 마스킹 흉내(휴대폰·전화) — 가운데 묶음을 `*` 로, 뒤 4자리만 남긴다. 목업 가정이다(정책 문서에 이메일 외에는 없다) */
function maskPhone(phone: string): string {
    const parts = phone.split('-');
    if (parts.length < 3) return '***';
    parts[1] = '*'.repeat(parts[1]!.length);
    return parts.join('-');
}

/** 서버의 마스킹 흉내(주소) — 앞 두 마디(시·구)만 남기고 `***`. 목업 가정이다 */
function maskAddress(address: string): string {
    return `${address.split(' ').slice(0, 2).join(' ')} ***`;
}

const ADDRESSES = ['서울특별시 강남구 테헤란로 152 4층', '경기도 성남시 분당구 판교역로 235', '부산광역시 해운대구 센텀중앙로 55', '대구광역시 수성구 달구벌대로 2300', '인천광역시 연수구 송도과학로 32', '광주광역시 서구 상무중앙로 110'];
const RECIPIENTS = ['김하늘', '이서준', '박지우', '최민서'];
const BIOS = ['로봇 만드는 걸 좋아해요', '주말엔 등산, 평일엔 코딩', '커피와 보드게임', '조용히 눈팅하는 편이에요'];
const SIGNATURES = ['로봇은 사랑입니다', '커뮤니티에서 만나요 :)', '천천히, 꾸준히.'];
const DEAL_TITLES = ['무선 청소기 공동구매', '제주 감귤 5kg 산지직송', '기계식 키보드 공구', '캠핑 의자 2종 세트', '핸드드립 원두 1kg', '휴대용 선풍기 3개입'];
const POINT_SAMPLES: { kind: 'EARN' | 'USE' | 'REVOKE'; delta: number; reason: string }[] = [
    { kind: 'EARN', delta: 10, reason: '출석체크' },
    { kind: 'EARN', delta: 20, reason: '글 작성' },
    { kind: 'EARN', delta: 5, reason: '댓글 작성' },
    { kind: 'EARN', delta: 100, reason: '인기글 보상' },
    { kind: 'EARN', delta: 10, reason: '추천 보너스' },
    { kind: 'USE', delta: -500, reason: '닉네임 변경권 구매' },
    { kind: 'REVOKE', delta: -20, reason: '삭제된 글의 포인트 회수' },
    { kind: 'REVOKE', delta: -100, reason: '운영 조정' },
];
const DISPLAY_OWN: DisplayValues[] = [
    { zoneId: 'America/New_York', timeFormat: 'H12', dateFormat: 'MDY_SLASH', language: 'en' },
    { zoneId: 'Asia/Tokyo', timeFormat: 'H24', dateFormat: 'YMD_DASH', language: 'ko' },
    { zoneId: 'Europe/London', timeFormat: 'H24', dateFormat: 'DMY_SLASH', language: 'en' },
];
const DISPLAY_DEFAULT: DisplayValues = { zoneId: 'Asia/Seoul', timeFormat: 'H24', dateFormat: 'YMD_DOT', language: 'ko' };

/** 이 시각 이전으로 눌러 둔다(미래 시각이 나오지 않게) */
function before(ms: number, now: number): string {
    return new Date(Math.min(ms, now - 60_000)).toISOString();
}

function gradePointOf(m: MemberRow, i: number, sanctions: MemberSanction[], activityTotal: number, now: number): MemberGradePoint {
    const grades = mockGradeSnapshot();
    const current = mockGradeResolve(m.grade);
    const at = Math.max(0, grades.findIndex((g) => g.gradeKey === current));
    const joined = Date.parse(m.joinedAt);
    const adminPromoted = at >= 1 && i % 7 === 0;

    // 승급 이력 — 처음 등급부터 현재 등급까지 한 칸씩. 운영자가 올린 회원은 마지막 한 칸이 운영자 변경이다
    const history: MemberGradeChange[] = [];
    for (let level = 1; level <= at; level++) {
        const g = grades[level]!;
        const byAdmin = adminPromoted && level === at;
        history.push({
            changeId: `${m.memberNo}-g${level}`,
            fromGrade: grades[level - 1]!.gradeKey,
            toGrade: g.gradeKey,
            kind: byAdmin ? 'ADMIN' : 'AUTO',
            reason: byAdmin ? '이벤트 우수 활동 보상' : null,
            actorNickname: byAdmin ? '박운영' : null,
            // 등급 조건 일수를 채운 때 — 가입한 지 얼마 안 됐으면 가입~지금 사이에 고르게 놓는다
            changedAt: before(joined + Math.min(Math.max(g.minDays, 1) * DAY, ((now - joined) * level) / (at + 1)), now),
        });
    }
    history.reverse();

    const target = grades[at + 1];
    const next = target
        ? {
              gradeKey: target.gradeKey,
              requirements: [
                  { kind: 'DAYS' as const, current: Math.floor((now - joined) / DAY), required: target.minDays },
                  // 시드의 글·댓글 수는 포인트에서 부풀려져 있어 아직 승급 전인 회원이 조건을 다 채운 것처럼 보이지 않게 기준의 일부로 줄여 보인다(목업)
                  { kind: 'ACTIVITY' as const, current: activityTotal >= target.minActivity ? Math.floor(target.minActivity * (0.35 + (i % 5) * 0.12)) : activityTotal, required: target.minActivity },
              ],
          }
        : null;

    const paused = m.status === 'SUSPENDED' || m.status === 'BANNED' || sanctions.some((s) => s.status === 'ACTIVE');
    const autoPromotion = paused ? 'PAUSED_SANCTION' : adminPromoted ? 'PAUSED_ADMIN' : 'ACTIVE';

    const pointEntries: MemberPointEntry[] =
        m.point === 0
            ? []
            : POINT_SAMPLES.map((_, k) => {
                  const sample = POINT_SAMPLES[(i + k) % POINT_SAMPLES.length]!;
                  return { entryId: `${m.memberNo}-p${k}`, kind: sample.kind, delta: sample.delta, reason: sample.reason, createdAt: before(now - (k * 1.7 + (i % 5) * 0.3 + 0.2) * DAY, now) };
              });

    return { autoPromotion, next, gradeHistory: history, nicknameTicketCount: i % 4 === 0 ? 1 : i % 9 === 1 ? 2 : 0, pointEntries };
}

function settingsOf(m: MemberRow, i: number): MemberSettings {
    const own = i % 4 === 1;
    const total = 5 + ((i * 13) % 80);
    return {
        display: own ? { ...DISPLAY_OWN[i % DISPLAY_OWN.length]! } : { ...DISPLAY_DEFAULT },
        displayOwn: own,
        notifications: [
            { kind: 'COMMENT', enabled: i % 5 !== 0 },
            { kind: 'REPLY', enabled: i % 7 !== 0 },
            { kind: 'POPULAR_REWARD', enabled: i % 4 !== 0 },
            { kind: 'DM', enabled: i % 6 !== 1 },
            { kind: 'POST_DONE', enabled: null },
            { kind: 'POST_ANSWER', enabled: null },
        ],
        pushSubscribed: i % 3 !== 2 && m.status === 'ACTIVE',
        blockedUserCount: (i * 3) % 9,
        blockedWordCount: i % 5,
        memoCount: (i * 7) % 12,
        inbox: { totalCount: total, unreadCount: Math.min(total, (i * 5) % 12) },
    };
}

function consentsOf(m: MemberRow, i: number, now: number): MemberConsents {
    const joined = Date.parse(m.joinedAt);
    const version = joined < now - 365 * DAY ? 'v1.0' : joined < now - 120 * DAY ? 'v1.1' : 'v1.2';
    const history: MemberConsentChange[] = [];
    let seq = 0;
    const add = (item: MemberConsentChange['item'], action: MemberConsentChange['action'], channel: MemberConsentChange['channel'], days: number) =>
        history.push({ changeId: `${m.memberNo}-c${seq++}`, item, action, channel, changedAt: new Date(joined + days * DAY).toISOString() });
    /** 가입 뒤 이만큼 지난 일이 이미 일어났는가 — 가입한 지 얼마 안 된 회원에게 미래 이력이 생기지 않게 한다 */
    const happened = (days: number) => joined + days * DAY < now - DAY;

    const marketing = i % 3 !== 0;
    const emailAd = marketing && i % 2 === 0;
    const thirdParty = i % 5 === 1;
    const profilePublic = i % 4 !== 3;
    // 가입 뒤에 마이페이지에서 켠 회원 · 켰다가 끈 회원(일부는 운영자가 철회 요청을 처리)
    if (marketing && i % 3 === 1 && happened(20)) add('MARKETING', 'AGREE', 'MYPAGE', 20);
    if (emailAd && happened(21)) add('EMAIL_AD', 'AGREE', 'MYPAGE', 21);
    if (!marketing && i % 2 === 0 && happened(40)) {
        add('MARKETING', 'AGREE', 'MYPAGE', 3);
        add('EMAIL_AD', 'AGREE', 'MYPAGE', 3);
        const channel = i % 6 === 0 ? 'ADMIN' : 'MYPAGE';
        add('MARKETING', 'WITHDRAW', channel, 40);
        add('EMAIL_AD', 'WITHDRAW', channel, 40);
    }
    if (thirdParty && happened(33)) add('THIRD_PARTY', 'AGREE', 'MYPAGE', 33);
    if (!profilePublic && happened(60)) {
        add('PROFILE_PUBLIC', 'AGREE', 'MYPAGE', 2);
        add('PROFILE_PUBLIC', 'WITHDRAW', 'MYPAGE', 60);
    }
    // 오래된 가입자는 약관이 바뀐 뒤 현재 버전에 다시 동의했다
    const reagreed = joined < now - 200 * DAY;
    if (reagreed) history.push({ changeId: `${m.memberNo}-c${seq++}`, item: 'TERMS', action: 'AGREE', channel: 'MYPAGE', changedAt: new Date(now - 90 * DAY).toISOString() });
    history.sort((a, b) => Date.parse(b.changedAt) - Date.parse(a.changedAt));

    const agreedAt = (item: MemberConsentChange['item']) => history.filter((h) => h.item === item && h.action === 'AGREE').sort((a, b) => Date.parse(b.changedAt) - Date.parse(a.changedAt))[0]?.changedAt ?? new Date(joined).toISOString();
    return {
        terms: reagreed ? { agreedAt: new Date(now - 90 * DAY).toISOString(), version: 'v1.2' } : { agreedAt: new Date(joined).toISOString(), version },
        privacy: { agreedAt: new Date(joined).toISOString(), version },
        ageConfirmedAt: new Date(joined).toISOString(),
        optional: [
            { key: 'MARKETING', agreed: marketing, agreedAt: marketing ? agreedAt('MARKETING') : null },
            { key: 'EMAIL_AD', agreed: emailAd, agreedAt: emailAd ? agreedAt('EMAIL_AD') : null },
            { key: 'THIRD_PARTY', agreed: thirdParty, agreedAt: thirdParty ? agreedAt('THIRD_PARTY') : null },
            { key: 'PROFILE_PUBLIC', agreed: profilePublic, agreedAt: profilePublic ? agreedAt('PROFILE_PUBLIC') : null },
        ],
        history,
    };
}

function groupBuyOf(m: MemberRow, i: number, canPii: boolean, now: number): MemberGroupBuy {
    const count = (i * 5) % 9;
    const statuses: MemberOrderStatus[] = ['DELIVERED', 'DELIVERED', 'SHIPPED', 'PREPARING', 'PAID', 'CANCELED', 'REFUNDED', 'PENDING', 'DELIVERED'];
    const orders: MemberGroupBuyOrder[] = Array.from({ length: count }, (_, k) => {
        const status = statuses[(i + k) % statuses.length]!;
        const quantity = 1 + ((i + k) % 3);
        const orderedAt = before(now - (3 + k * 17 + (i % 7)) * DAY, now);
        const erased = k === count - 1 && count >= 4 && Date.parse(orderedAt) < now - 190 * DAY;
        const address = ADDRESSES[(i + k) % ADDRESSES.length]!;
        const phone = `010-${String(1000 + ((i * 37 + k * 11) % 9000))}-${String(1000 + ((i * 53 + k * 7) % 9000))}`;
        return {
            orderNo: `G${new Date(orderedAt).toISOString().slice(0, 10).replaceAll('-', '')}${String(1 + ((i + k) % 90)).padStart(4, '0')}`,
            dealTitle: DEAL_TITLES[(i + k) % DEAL_TITLES.length]!,
            quantity,
            totalAmount: quantity * (12_000 + ((i + k) % 6) * 6_500) + 3_000,
            status,
            orderedAt,
            shipping: erased || status === 'PENDING' ? null : { recipient: RECIPIENTS[(i + k) % RECIPIENTS.length]!, address: canPii ? address : maskAddress(address), phone: canPii ? phone : maskPhone(phone) },
        };
    });
    const total = count + (i % 4);
    const inState = (...s: MemberOrderStatus[]) => Math.round((total * orders.filter((o) => s.includes(o.status)).length) / Math.max(1, orders.length));
    const inProgressCount = inState('PENDING', 'PAID', 'PREPARING');
    const shippingCount = inState('SHIPPED');
    const canceledCount = inState('CANCELED', 'REFUNDED');
    return {
        dealCount: Math.min(total, 1 + (i % 5)),
        orderCount: total,
        inProgressCount,
        shippingCount,
        completedCount: Math.max(0, total - inProgressCount - shippingCount - canceledCount),
        canceledCount,
        recentOrders: orders.slice(0, 5),
    };
}

/** `GET /api/admin/members/{memberId}` */
export async function mockMemberGet(memberId: string): Promise<MemberDetail> {
    await mockDelay();
    if (mockFlags().has('member-detail-error')) throw MOCK_ERRORS.internal();
    if (!actorCan('member.read')) throw new ApiError({ status: 403, code: 'PERMISSION_DENIED', message: '이 작업을 할 권한이 없어요.' });
    const m = seed().find((row) => row.memberNo === memberId);
    const meta = META.get(memberId);
    if (!m || !meta) throw new ApiError({ status: 404, code: 'MEMBER_NOT_FOUND', message: '없는 회원이에요.' });

    const now = Date.now();
    const i = meta.index;
    const canPii = actorCan('member.pii');
    const withdrawn = m.status === 'WITHDRAWN';
    const sanctions = sanctionsOf(m, i);
    const roles: MemberRoleDetail[] = live(m).roles.flatMap((r) => {
        const def = roleDefs().find((d) => d.roleId === r.roleId);
        return def ? [{ roleId: def.roleId, roleName: def.roleName, typeId: def.typeId, grantMode: def.grantMode, boardNames: def.typeId === UtilRole.TYPE_BOARD ? [...meta.boards] : [] }] : [];
    });
    const active = m.status === 'ACTIVE';
    const postCount = Math.floor(m.point / 35) + (i % 9);
    const commentCount = postCount * 3 + (i % 11);
    const statusReason =
        m.status === 'WITHDRAWN' ? '본인 탈퇴' : m.status === 'DORMANT' ? '1년 이상 접속하지 않아 휴면으로 전환' : m.status === 'SUSPENDED' || m.status === 'BANNED' ? (sanctions[0]?.reason ?? null) : null;

    // 제재 요약 — 유효한 경고(0~2)와 다음 단계. 영구 정지·탈퇴는 다음 단계가 없다
    const warningCount = Math.min(2, sanctions.filter((s) => s.kind === 'WARNING' && s.status !== 'RELEASED').length);
    const everSuspended = sanctions.some((s) => s.kind === 'SUSPENSION' && s.scope === 'SITE');
    const next = withdrawn || m.status === 'BANNED' ? null : m.status === 'SUSPENDED' ? 'BAN' : everSuspended || warningCount >= 2 ? 'SUSPENSION' : 'WARNING';

    // 소셜 연동 — 가입 방식 + 이메일 가입자는 일부 추가 연동
    const linked = (p: 'KAKAO' | 'NAVER' | 'GOOGLE') => m.authMethod === p || (m.authMethod === 'EMAIL' && ((p === 'KAKAO' && i % 5 === 0) || (p === 'NAVER' && i % 7 === 3) || (p === 'GOOGLE' && i % 11 === 4)));
    const bio = !withdrawn && i % 4 !== 1 ? BIOS[i % BIOS.length]! : null;

    // 연락처 — 값이 비어 있는 회원이 섞여 있다. 국가는 대부분 한국
    const mobile = i % 9 === 2 ? null : `010-${String(1000 + ((i * 41) % 9000))}-${String(1000 + ((i * 59) % 9000))}`;
    const phone = i % 3 === 0 ? `02-${String(100 + ((i * 13) % 900))}-${String(1000 + ((i * 29) % 9000))}` : null;
    const abroad = i % 19 === 6;
    const address = abroad ? '東京都 渋谷区 道玄坂 1-2-3' : i % 7 === 5 ? null : ADDRESSES[i % ADDRESSES.length]!;

    const base = {
        ...live(m),
        email: m.email !== null && !canPii ? maskEmail(m.email) : m.email,
        emailMasked: m.email !== null && !canPii,
        roles,
        statusReason,
        activity: {
            postCount,
            commentCount,
            likesReceived: Math.floor(postCount * 2.4) + (i % 13),
            popularCount: Math.floor(postCount / 12) + (i % 4 === 0 ? 1 : 0),
            recentPostCount: active ? i % 6 : 0,
            recentCommentCount: active ? (i * 3) % 14 : 0,
            recentLoginDays: active && m.lastLoginAt !== null ? 1 + (i % 7) : 0,
        },
        sanctions,
        sanctionSummary: { warningCount, next } as const,
        reports: { receivedCount: m.sanctionCount * 2 + (i % 3), actionedCount: Math.min(m.sanctionCount * 2 + (i % 3), m.sanctionCount + (i % 2)), filedCount: (i * 5) % 7 },
        piiMasked: !canPii,
    };

    if (withdrawn) {
        return { ...base, bio: null, social: [], adultConfirmed: null, acceptDm: null, dormancy: null, withdrawnAt: m.statusChangedAt, nicknameHistory: [], gradePoint: null, settings: null, contact: null, profile: null, consents: null, groupBuy: null };
    }

    // 닉네임 변경 이력 — `member.pii` 가 없으면 서버가 주지 않는다(null)
    const changes = i % 4;
    const nicknameHistory: MemberNicknameChange[] | null = !canPii
        ? null
        : Array.from({ length: changes }, (_, k) => ({
              oldNickname: `${FIRST[(i + k * 4 + 3) % FIRST.length]}${SECOND[(i * 3 + k * 5 + 1) % SECOND.length]}`,
              newNickname: k === 0 ? m.nickname : `${FIRST[(i + (k - 1) * 4 + 3) % FIRST.length]}${SECOND[(i * 3 + (k - 1) * 5 + 1) % SECOND.length]}`,
              changedAt: new Date(Math.max(Date.parse(m.joinedAt) + DAY, now - (20 + k * 55 + (i % 9)) * DAY)).toISOString(),
          }));

    const settings = settingsOf(m, i);
    const memoAt = before(now - 12 * DAY, now);
    return {
        ...base,
        bio,
        social: (['KAKAO', 'NAVER', 'GOOGLE'] as const).map((provider) => ({ provider, linked: linked(provider) })),
        adultConfirmed: i % 3 !== 0,
        acceptDm: i % 8 !== 3,
        dormancy: active && i % 29 === 4 ? { noticedAt: new Date(now - 5 * DAY).toISOString(), eraseOn: new Date(now + 25 * DAY).toISOString() } : null,
        withdrawnAt: null,
        nicknameHistory,
        gradePoint: gradePointOf(m, i, sanctions, postCount + commentCount, now),
        settings,
        contact: {
            homepage: i % 5 < 2 ? `https://blog.example.com/${m.nickname.length}${i}` : null,
            mobile: mobile === null ? null : canPii ? mobile : maskPhone(mobile),
            phone: phone === null ? null : canPii ? phone : maskPhone(phone),
            country: abroad ? 'JP' : 'KR',
            address: address === null ? null : canPii ? address : maskAddress(address),
        },
        profile: {
            signature: i % 3 === 1 ? null : SIGNATURES[i % SIGNATURES.length]!,
            adminMemo: i % 10 === 2 ? '허위 신고로 주의 안내 메일 발송(문의 #1234)' : null,
            adminMemoUpdatedAt: i % 10 === 2 ? memoAt : null,
            adminMemoUpdatedBy: i % 10 === 2 ? '김관리' : null,
        },
        consents: consentsOf(m, i, now),
        groupBuy: groupBuyOf(m, i, canPii, now),
    };
}
