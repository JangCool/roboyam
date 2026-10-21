/**
 * 가입·인증 API 의 주고받는 모양 — docs/api-auth.md 와 같다.
 * ⚠ ID 는 문자열이다(Snowflake 64비트). 숫자로 바꾸지 않는다.
 */

/** 로그인 정보(`/api/auth/me`)의 `businessStatus` — 탈퇴한 사업자는 로그인할 수 없어 `WITHDRAWN` 이 오지 않는다 */
export type BusinessUserStatus = 'NONE' | 'PENDING' | 'APPROVED' | 'REJECTED' | 'SUSPENDED';

/** 계정 상태 — 정상 · 기간 정지 중 · 영구 정지 */
export type AccountStatus = 'ACTIVE' | 'SUSPENDED' | 'BANNED';

/**
 * 로그인 정보(`/api/auth/me`·로그인·가입 응답)의 `restriction` — 정지·영구 정지 중이거나 확인하지 않은 경고가 있을 때만 값이 있다.
 *  - `status`: 계정 상태 · `until`: 진행 중인 기간 정지의 끝(그 외 null) · `pendingNotices`: 아직 확인하지 않은 경고 수
 * 화면은 이것으로 전역 배너와 로그인 직후 안내 이동을 정한다.
 */
export interface Restriction {
    status: AccountStatus;
    until: string | null;
    pendingNotices: number;
}

export type Grade = 'SEEDLING' | 'REGULAR' | 'LOYAL' | 'VETERAN';
/** `BUSINESS` 는 사업자 전용 계정(H-2) — 일반 게시판 글쓰기·소모임·쪽지 보내기·출석·포인트를 못 쓰고 사업자 센터를 쓴다. 운영진 판정(`role !== 'USER'`)에 쓰지 않는다(`UtilBusiness.isOperator`) */
export type Role = 'USER' | 'STAFF' | 'ADMIN' | 'BUSINESS';
export type AuthMethod = 'EMAIL' | 'KAKAO' | 'NAVER' | 'GOOGLE';

export interface AuthUser {
    id: string;
    nickname: string;
    grade: Grade;
    gradeLabel: string;
    role: Role;
    /** 소셜 가입자가 이메일을 안 줬으면 null */
    email: string | null;
    /** 이메일 가입자는 인증 전에 false, 소셜 가입자는 true */
    emailVerified: boolean;
    authMethods: AuthMethod[];
    /** 사업자 신청 상태(H-2) — 신청한 적이 없으면 `NONE`. 서버는 사업자 기능이 없는 동안 이 칸을 `null` 로 준다 — 칸이 없거나 `null` 이면 `NONE` 으로 읽는다 */
    businessStatus?: BusinessUserStatus | null;
}

/**
 * 로그인 상태(`me`)·로그인·가입 응답 공통.
 * `restriction`(7-2): 정지·영구 정지 중이거나 확인하지 않은 경고가 있을 때의 요약 — 정상이면 null. 서버가 아직 안 주면(옛 응답) 없을 수도 있어 선택으로 둔다
 * `adminUrl`(7-4): 관리자 앱 주소 — **관리자 앱에 들어갈 수 있는 사람**(정상 상태의 운영자, 메뉴 권한이 하나라도 `NONE` 이 아닌 운영진)에게만 값이 있고 그 밖에는 null.
 * 사용자 메뉴의 "관리자 앱" 링크가 쓴다(`useAuth().adminUrl` — http(s) 주소인지 한 번 더 거른 값). 옛 응답에는 칸 자체가 없을 수도 있어 선택으로 둔다
 */
export interface MeResponse {
    authenticated: boolean;
    user: AuthUser | null;
    restriction?: Restriction | null;
    adminUrl?: string | null;
}

export interface SocialProvider {
    /** `kakao` · `naver` · `google` — 소셜 로그인 시작 주소(useAuthApi 가 안다)에 붙는 id */
    id: string;
    name: string;
}

/** 닉네임을 쓸 수 없는 까닭. 서버 응답(`reason`)과 같은 값이다. */
export type NicknameReason = 'LENGTH' | 'CHARS' | 'DUPLICATE' | 'RESERVED';

/** 닉네임 입력칸의 상태 — 화면이 미리 검사한 것 + 서버 확인 결과 */
export type NicknameStatus = 'idle' | 'invalid' | 'checking' | 'available' | 'unavailable' | 'error';

export interface NicknameAvailability {
    available: boolean;
    reason: NicknameReason | null;
}

export interface EmailAvailability {
    available: boolean;
}

export interface LoginRequest {
    email: string;
    password: string;
}

/** 약관 동의 세 가지 — 모두 true 여야 가입된다. */
export interface Agreements {
    agreeTerms: boolean;
    agreePrivacy: boolean;
    ageConfirmed: boolean;
}

export interface SignupRequest extends Agreements {
    email: string;
    password: string;
    nickname: string;
}

export interface SocialSignupRequest extends Agreements {
    nickname: string;
}

/** 소셜 가입을 이어가는 중인 사람 (`useAuthApi().socialPending()`) */
export interface SocialPending {
    provider: string;
    providerName: string;
    suggestedNickname: string | null;
    emailMasked: string | null;
}

export interface PasswordResetRequest {
    token: string;
    newPassword: string;
}
