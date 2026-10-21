// MOCK: 관리자 로그인 API(`POST /api/admin/auth/login`·`logout`, `GET /api/admin/auth/me`)의 메모리 목업 — `useAdminAuthApi.ts` 가 이 파일의 함수를 부른다.
// 서버가 붙으면 `auth/api/mock/` 와 함께 지운다.
//
// ── 목업 계정 (비밀번호는 모두 `MOCK_PASSWORD` — 데모 값이고 실제 계정이 아니다) ─
//   admin@example.com    최고 관리자(ADMIN, 루트 — `isSuper`, 모든 권한)로 로그인된다
//   staff@example.com    운영진(STAFF)으로 로그인된다 — 역할 둘(감사·배치 조회 + 운영자 조회)의 권한 합집합: audit.read · batch.read · operator.read · role.read, 그리고 회원 조회 `member.read`(전체 이메일 열람 `member.pii` 는 없다 → 회원 목록의 이메일이 가려져 보인다), 등급 설정 조회 `grade.read`(변경 `grade.manage` 는 없다 → 등급 설정이 읽기 전용으로 보인다), 사이트 설정 조회 `site.read`(변경 `site.manage` 는 없다 → 설정 메뉴의 「일반」이 읽기 전용으로 보인다)
//   user@example.com     일반 회원 — 비밀번호가 맞아도 `401 INVALID_CREDENTIALS` (관리자 앱은 운영진·최고 관리자만 들어온다)
//   locked@example.com   늘 `429 LOGIN_LOCKED`(10분) — 잠금 화면 확인용
//   그 밖의 주소 / 틀린 비밀번호   `401 INVALID_CREDENTIALS`. 같은 이메일로 5회 연속 틀리면 6번째부터 `429 LOGIN_LOCKED`(10분) — 새로고침하면 횟수가 지워진다(서버라면 DB)
//
// ── 개발 전용 스위치 — 주소의 `?mock=이름` (쉼표로 여러 개) ─
//   login-error     로그인 요청이 `500 INTERNAL` 로 실패
//   login-network   로그인 요청이 네트워크 끊김(`NETWORK`)으로 실패
//
// ── 세션 ─
//   서버라면 HttpOnly 쿠키(`ADMIN_SESSION`)가 들고 있을 로그인 상태를 목업은 `sessionStorage` 에 **계정 이메일만** 둔다(비밀번호는 저장하지 않는다) —
//   새로고침해도 로그인이 유지되고 탭을 닫으면 사라진다. 서버가 붙으면 이 저장도 함께 지운다.

import { ApiError } from '~/composables/common/useApiClient';
import { MOCK_ERRORS, mockDelay, mockFlags } from '~/composables/common/mock/mock-util';
import type { AdminLoginRequest, AdminMeResponse, AdminUser } from '~/composables/auth/api/useAdminAuthApi.types';
import { mockSiteLogoUrl, mockSiteName } from '~/composables/site/api/mock/site-settings-mock';
import UtilPermissionCatalog from '~/utils/util-permission-catalog';

/** 목업 계정 공통 비밀번호 — 데모 값 */
const MOCK_PASSWORD = 'demo1234';
/** 5회 연속 실패 → 10분 잠금 (공개 사이트 로그인과 같은 규칙) */
const MAX_FAILURES = 5;
const LOCK_SECONDS = 600;

const STORAGE_KEY = 'roboyam:mock-admin-session';

/** 비밀번호가 맞으면 관리자 앱에 들어올 수 있는 계정 */
const STAFF_ACCOUNTS: Record<string, AdminUser> = {
    // 최고 관리자는 역할이 아니라 루트 계정의 속성이다 — 서버가 모든 권한 코드를 합쳐 내려 준다고 본다
    'admin@example.com': { id: '1001', nickname: '김관리', role: 'ADMIN', email: 'admin@example.com', isSuper: true, permissions: UtilPermissionCatalog.codes() },
    'staff@example.com': { id: '1002', nickname: '박운영', role: 'STAFF', email: 'staff@example.com', isSuper: false, permissions: ['audit.read', 'batch.read', 'operator.read', 'role.read', 'member.read', 'grade.read', 'site.read'] },
};
/** 비밀번호가 맞아도 들어올 수 없는 일반 회원 */
const NON_STAFF_EMAIL = 'user@example.com';
/** 늘 잠겨 있는 계정 */
const LOCKED_EMAIL = 'locked@example.com';

/** 이메일별 연속 실패 횟수와 잠금이 풀리는 시각 — 새로고침하면 사라진다 */
const failures = new Map<string, { count: number; lockedUntil: number }>();

function readSessionEmail(): string | null {
    try {
        return window.sessionStorage.getItem(STORAGE_KEY);
    } catch {
        return null;
    }
}

function writeSessionEmail(email: string | null): void {
    try {
        if (email) window.sessionStorage.setItem(STORAGE_KEY, email);
        else window.sessionStorage.removeItem(STORAGE_KEY);
    } catch {
        /* 사생활 보호 모드 등 — 이번 방문 동안만 로그인 상태가 메모리에 있다 */
    }
}

/** 저장소가 막혀 있어도 이번 방문 동안은 로그인 상태가 유지되게 메모리에도 둔다 */
let memoryEmail: string | null = null;

/** 지금 목업 세션의 관리자 — 내 정보 목업(`profile-mock.ts`)이 같은 계정을 보도록 내보낸다 */
export function mockCurrentAdmin(): AdminUser | null {
    return currentUser();
}

/** 내 정보 목업이 닉네임을 바꾸면 이 목업의 계정에도 반영한다(`me` 가 새 이름을 주도록). 새로고침하면 처음 이름으로 돌아간다 */
export function mockRenameAdmin(email: string, nickname: string): void {
    const account = STAFF_ACCOUNTS[email];
    if (account) account.nickname = nickname;
}

/** 다른 계정이 쓰는 닉네임인가(중복 검사 흉내) */
export function mockNicknameTaken(email: string, nickname: string): boolean {
    return Object.entries(STAFF_ACCOUNTS).some(([key, account]) => key !== email && account.nickname === nickname);
}

function currentUser(): AdminUser | null {
    const email = readSessionEmail() ?? memoryEmail;
    return email ? (STAFF_ACCOUNTS[email] ?? null) : null;
}

function invalidCredentials(): ApiError {
    return new ApiError({ status: 401, code: 'INVALID_CREDENTIALS', message: '이메일 또는 비밀번호가 맞지 않아요. 다시 확인해 주세요.' });
}

function locked(seconds: number): ApiError {
    return new ApiError({ status: 429, code: 'LOGIN_LOCKED', message: '로그인이 잠시 제한됐어요. 잠시 후 다시 시도해 주세요.', retryAfterSeconds: seconds });
}

/** `GET /api/admin/auth/me` — 항상 200. 사이트 이름은 비로그인에도 준다 */
export async function mockAdminMe(): Promise<AdminMeResponse> {
    await mockDelay();
    const user = currentUser();
    return { authenticated: user !== null, user, siteName: mockSiteName(), siteLogoUrl: mockSiteLogoUrl() };
}

/** `POST /api/admin/auth/login` */
export async function mockAdminLogin(body: AdminLoginRequest): Promise<AdminMeResponse> {
    await mockDelay();
    const flags = mockFlags();
    if (flags.has('login-network')) throw MOCK_ERRORS.network();
    if (flags.has('login-error')) throw MOCK_ERRORS.internal();

    const email = body.email.trim().toLowerCase();
    if (!email || !body.password) {
        throw new ApiError({
            status: 400,
            code: 'VALIDATION',
            message: '입력한 내용을 다시 확인해 주세요.',
            fields: { ...(email ? {} : { email: '이메일을 입력해 주세요.' }), ...(body.password ? {} : { password: '비밀번호를 입력해 주세요.' }) },
        });
    }

    if (email === LOCKED_EMAIL) throw locked(LOCK_SECONDS);

    const record = failures.get(email) ?? { count: 0, lockedUntil: 0 };
    const now = Date.now();
    if (record.lockedUntil > now) throw locked(Math.ceil((record.lockedUntil - now) / 1000));
    // 잠금이 풀렸으면 처음부터 다시 센다
    if (record.lockedUntil !== 0) {
        record.count = 0;
        record.lockedUntil = 0;
    }

    const account = STAFF_ACCOUNTS[email];
    // 일반 회원(NON_STAFF_EMAIL)은 비밀번호가 맞아도 틀린 것과 같다 — 응답만으로 어느 쪽인지 알 수 없다
    if (!account || email === NON_STAFF_EMAIL || body.password !== MOCK_PASSWORD) {
        record.count += 1;
        if (record.count >= MAX_FAILURES) record.lockedUntil = now + LOCK_SECONDS * 1000;
        failures.set(email, record);
        throw invalidCredentials();
    }

    failures.delete(email);
    memoryEmail = email;
    writeSessionEmail(email);
    return { authenticated: true, user: account, siteName: mockSiteName(), siteLogoUrl: mockSiteLogoUrl() };
}

/** `POST /api/admin/auth/logout` — 204, 비로그인이어도 성공 */
export async function mockAdminLogout(): Promise<void> {
    await mockDelay();
    memoryEmail = null;
    writeSessionEmail(null);
}
