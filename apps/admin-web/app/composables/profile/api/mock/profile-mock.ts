// MOCK: 내 정보 API(`GET`·`PATCH /api/admin/profile`)의 메모리 목업 — `useProfileApi.ts` 가 이 파일의 함수를 부른다.
// 서버가 붙으면 `profile/api/mock/` 와 함께 지운다. 로그인 상태는 실제 로그인 상태(`auth/api/mock/admin-auth-mock.ts` 가 읽어 준다)를 따른다 — 같은 계정이다.
//
// ── 개발 전용 스위치 — 주소의 `?mock=이름` (쉼표로 여러 개) ─
//   profile-error        내 정보를 읽는 요청이 `500 INTERNAL` 로 실패(화면이 오류 상태와 다시 시도를 보인다)
//   profile-save-error   저장 요청이 `500 INTERNAL` 로 실패(폼 위에 오류가 뜬다)
//   login-history-error  로그인 이력 요청이 `500 INTERNAL` 로 실패(오류 상태와 다시 시도)
//   login-history-empty  로그인 이력이 하나도 없는 상태
//   profile-display-error / profile-display-save-error   내 표기를 읽는/저장하는 요청이 500 으로 실패
//   profile-notify-error  / profile-notify-save-error     알림 수신 설정을 읽는/저장하는 요청이 500 으로 실패
//
// ── 저장 흉내 ─
//   닉네임이 서버 규칙(2~12자, 한글·영문·숫자·밑줄, 단어 사이 공백 한 칸)에 안 맞으면 `400 VALIDATION`(+`fields.nickname`),
//   다른 운영자가 쓴다고 가정한 닉네임(`박운영`·`김관리`)이면 `409 NICKNAME_TAKEN` 이다. 바뀐 이름은 새로고침하면 처음으로 돌아간다(메모리).
//   가입·최근 로그인 시각은 계정별 고정 예시다.

import { ApiError } from '~/composables/common/useApiClient';
import { MOCK_ERRORS, mockDelay, mockFlags } from '~/composables/common/mock/mock-util';
import { mockCurrentAdmin, mockNicknameTaken, mockRenameAdmin } from '~/composables/auth/api/mock/admin-auth-mock';
import { mockSiteDisplayValues } from '~/composables/site/api/mock/site-settings-mock';
import type { DisplayValues, LoginHistoryItem, LoginHistoryResponse, MyDisplayRequest, MyDisplayResponse, MyNotificationRequest, MyNotificationResponse, ProfileResponse, ProfileUpdateRequest } from '~/composables/profile/api/useProfileApi.types';
import UtilDisplayValues from '#layers/display-time/utils/util-display-values';
import UtilValidation from '~/utils/util-validation';

const DAY = 86_400_000;

/** 계정별 가입·최근 로그인 예시 — 최근 로그인은 열 때마다 "조금 전"처럼 보이게 지금에서 센다 */
function times(id: string): Pick<ProfileResponse, 'createdAt' | 'lastLoginAt'> {
    if (id === '1002') return { createdAt: '2026-03-14T01:20:00Z', lastLoginAt: null };
    return { createdAt: '2026-01-02T00:30:00Z', lastLoginAt: new Date(Date.now() - 3 * DAY).toISOString() };
}

function unauthenticated(): ApiError {
    return MOCK_ERRORS.unauthenticated();
}

function toProfile(): ProfileResponse {
    const user = mockCurrentAdmin();
    if (!user) throw unauthenticated();
    return { id: user.id, email: user.email, nickname: user.nickname, role: user.role, status: 'ACTIVE', ...times(user.id) };
}

/** `GET /api/admin/profile` */
export async function mockProfileGet(): Promise<ProfileResponse> {
    await mockDelay();
    if (mockFlags().has('profile-error')) throw MOCK_ERRORS.internal();
    return toProfile();
}

const HOUR = 3_600_000;

/** 실제 모양의 User-Agent 예시 — 화면이 브라우저·운영체제·기기 종류로 풀어 보인다 */
const UA = {
    chromeWindows: 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36',
    safariMac: 'Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.5 Safari/605.1.15',
    safariIphone: 'Mozilla/5.0 (iPhone; CPU iPhone OS 17_5 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.5 Mobile/15E148 Safari/604.1',
    chromeAndroid: 'Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.6478.122 Mobile Safari/537.36',
    firefoxLinux: 'Mozilla/5.0 (X11; Linux x86_64; rv:127.0) Gecko/20100101 Firefox/127.0',
    edgeWindows: 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36 Edg/126.0.2592.87',
    safariIpad: 'Mozilla/5.0 (iPad; CPU OS 17_5 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.5 Mobile/15E148 Safari/604.1',
    unknown: 'RoboyamHealthCheck/2.1 (+https://example.com/monitor)',
};

/** 로그인 이력 예시 — 성공과 실패를 섞어 12건. 시각은 열 때마다 지금에서 센다(최근 것부터) */
function loginHistoryItems(): LoginHistoryItem[] {
    const at = (hoursAgo: number) => new Date(Date.now() - hoursAgo * HOUR).toISOString();
    const rows: [number, string | null, string | null, LoginHistoryItem['result'], string | null][] = [
        [0.5, '10.10.32.104', UA.chromeWindows, 'OK', null],
        [22, '10.10.32.104', UA.chromeWindows, 'OK', null],
        [30, '203.0.113.42', UA.safariIphone, 'FAIL', 'INVALID_CREDENTIALS'],
        [31, '203.0.113.42', UA.safariIphone, 'FAIL', 'INVALID_CREDENTIALS'],
        [47, '10.10.32.77', UA.safariMac, 'OK', null],
        [70, null, null, 'OK', null],
        [95, '198.51.100.7', UA.firefoxLinux, 'FAIL', 'LOGIN_LOCKED'],
        [96, '198.51.100.7', UA.firefoxLinux, 'FAIL', 'INVALID_CREDENTIALS'],
        [120, '10.10.32.90', UA.edgeWindows, 'OK', null],
        [150, '172.16.4.21', UA.chromeAndroid, 'OK', null],
        [200, '203.0.113.42', UA.unknown, 'FAIL', 'SOMETHING_ELSE'],
        [240, '10.10.32.55', UA.safariIpad, 'OK', null],
    ];
    return rows.map(([hoursAgo, ip, agent, result, reason], index) => ({ id: String(7000 - index), at: at(hoursAgo), ip, agent, result, reason }));
}

/** `GET /api/admin/profile/login-history` — 본인 것만, 최근 것부터 최근 20건 */
export async function mockLoginHistory(): Promise<LoginHistoryResponse> {
    await mockDelay();
    const flags = mockFlags();
    if (flags.has('login-history-error')) throw MOCK_ERRORS.internal();
    if (!mockCurrentAdmin()) throw unauthenticated();
    if (flags.has('login-history-empty')) return { items: [] };
    return { items: loginHistoryItems().slice(0, 20) };
}

/** `PATCH /api/admin/profile` — 갱신된 프로필을 돌려준다 */
export async function mockProfileUpdate(body: ProfileUpdateRequest): Promise<ProfileResponse> {
    await mockDelay();
    if (mockFlags().has('profile-save-error')) throw MOCK_ERRORS.internal();
    const user = mockCurrentAdmin();
    if (!user) throw unauthenticated();

    if (UtilValidation.nickname(body.nickname)) {
        throw new ApiError({
            status: 400,
            code: 'VALIDATION',
            message: '입력한 내용을 다시 확인해 주세요.',
            fields: { nickname: `닉네임은 ${UtilValidation.NICKNAME_MIN}~${UtilValidation.NICKNAME_MAX}자의 한글, 영문, 숫자, 밑줄(_)로 입력해 주세요.` },
        });
    }
    if (mockNicknameTaken(body.nickname)) {
        throw new ApiError({ status: 409, code: 'NICKNAME_TAKEN', message: '이미 사용 중인 닉네임이에요.' });
    }
    mockRenameAdmin(body.nickname);
    return toProfile();
}

// ── 내 표기(표시 값)와 알림 수신 설정 ──────────────────────────────────────────────
// 서버라면 사용자별 행에 있을 값을 이 브라우저의 localStorage 에 계정별로 둔다(`roboyam:mock-admin-display:<id>` · `roboyam:mock-admin-email-notify:<id>`) —
// 새로고침해도 남고 다른 계정과는 섞이지 않는다. 서버가 붙으면 이 저장도 함께 지운다.

/** 제품 기준 값(개인 설정 탭의 "기본 설정") — 사이트 설정 목업(`site-settings-mock.ts`)의 표시 기본값을 읽는다. 설정 메뉴의 「일반」에서 저장하면 여기도 바뀐다(처음 값은 한국어 · 서울 · 24시간 · `YYYY-MM-DD`) */
function productDisplay(): DisplayValues {
    return mockSiteDisplayValues();
}

function readJson<T>(key: string): T | null {
    try {
        const raw = window.localStorage.getItem(key);
        return raw ? (JSON.parse(raw) as T) : null;
    } catch {
        return null;
    }
}

function writeJson(key: string, value: unknown): void {
    try {
        window.localStorage.setItem(key, JSON.stringify(value));
    } catch {
        /* 사생활 보호 모드 등 — 이번 방문 동안은 메모리에만 */
    }
}

const memory = new Map<string, unknown>();

function load<T>(key: string): T | null {
    return (memory.get(key) as T | undefined) ?? readJson<T>(key);
}

function save(key: string, value: unknown): void {
    memory.set(key, value);
    writeJson(key, value);
}

function displayView(stored: { use: boolean; values: DisplayValues } | null): MyDisplayResponse {
    const use = stored?.use ?? false;
    const values = stored ? { ...stored.values } : productDisplay();
    return { use, values, inherited: productDisplay(), effectiveFrom: use ? 'USER' : 'SYSTEM' };
}

/** `GET /api/admin/profile/display` */
export async function mockMyDisplay(): Promise<MyDisplayResponse> {
    await mockDelay();
    if (mockFlags().has('profile-display-error')) throw MOCK_ERRORS.internal();
    const user = mockCurrentAdmin();
    if (!user) throw unauthenticated();
    return displayView(load(`roboyam:mock-admin-display:${user.id}`));
}

/** `PUT /api/admin/profile/display` — 저장하고 저장된 모양을 돌려준다 */
export async function mockSaveMyDisplay(body: MyDisplayRequest): Promise<MyDisplayResponse> {
    await mockDelay();
    if (mockFlags().has('profile-display-save-error')) throw MOCK_ERRORS.internal();
    const user = mockCurrentAdmin();
    if (!user) throw unauthenticated();
    const values = UtilDisplayValues.sanitize(body.values);
    if (!values) {
        throw new ApiError({ status: 400, code: 'VALIDATION', message: '입력한 내용을 다시 확인해 주세요.', fields: { zoneId: '쓸 수 없는 시간대예요.' } });
    }
    const stored = { use: body.use === true, values };
    save(`roboyam:mock-admin-display:${user.id}`, stored);
    return displayView(stored);
}

/** `GET /api/admin/profile/notification` — 처음 상태는 켜짐 */
export async function mockMyNotification(): Promise<MyNotificationResponse> {
    await mockDelay();
    if (mockFlags().has('profile-notify-error')) throw MOCK_ERRORS.internal();
    const user = mockCurrentAdmin();
    if (!user) throw unauthenticated();
    return { emailEnabled: load<boolean>(`roboyam:mock-admin-email-notify:${user.id}`) ?? true };
}

/** `PUT /api/admin/profile/notification` */
export async function mockSaveMyNotification(body: MyNotificationRequest): Promise<MyNotificationResponse> {
    await mockDelay();
    if (mockFlags().has('profile-notify-save-error')) throw MOCK_ERRORS.internal();
    const user = mockCurrentAdmin();
    if (!user) throw unauthenticated();
    save(`roboyam:mock-admin-email-notify:${user.id}`, body.emailEnabled === true);
    return { emailEnabled: body.emailEnabled === true };
}
