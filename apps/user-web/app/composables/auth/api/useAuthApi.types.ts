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

/**
 * 소셜 가입을 이어가는 중인 사람 (`GET /api/auth/social/pending` — 서버 `SocialPendingResponse`).
 * 서버 세션에만 있고 15분 뒤 사라진다(`404 NO_PENDING`).
 */
export interface SocialPending {
    /** 공급자 id(`kakao` · `naver` · `google`) */
    provider: string;
    /** 요청 언어의 공급자 이름 */
    providerName: string;
    /** 공급자가 준 닉네임 중 닉네임 규칙에 맞는 것 — 없으면 null(구글은 늘 null) */
    suggestedNickname: string | null;
    /** `a***@example.com` 모양. 공급자가 이메일을 안 줬으면 null */
    emailMasked: string | null;
    /** 공급자가 이메일을 확인했다고 했는가 — false 면 가입 뒤 서버가 인증 메일을 보낸다(이메일이 없어도 false) */
    emailVerified: boolean;
    /** 가입 뒤 돌아갈 같은 사이트 상대 경로 — 없으면 null(`/`) */
    next: string | null;
}

/** 서버가 콜백 결과에 싣는 사유 코드(`reason=`) — docs/features/login-contract.md 「소셜 로그인」, 서버 `SocialFailure` */
export type SocialFailureReason =
    | 'cancelled'
    | 'invalid_state'
    | 'expired'
    | 'provider_error'
    | 'account_unavailable'
    | 'session_changed'
    | 'already_linked';

/** `GET /api/me/social` 의 한 줄 — 서버 `MeSocialProviderView` */
export interface MeSocialProvider {
    /** 공급자 id(`kakao` · `naver` · `google`) */
    id: string;
    /** 요청 언어의 공급자 이름 */
    name: string;
    /** 이 계정에 연결돼 있는가 */
    linked: boolean;
    /** 지금 연결을 시작할 수 있는가(운영자가 켜 두고 키가 있음 + 아직 연결 안 됨) */
    linkable: boolean;
    /** 연결돼 있고 해제해도 다른 로그인 수단(비밀번호 또는 다른 소셜)이 남는가 */
    canUnlink: boolean;
}

/** `GET /api/me/social` — 서버 `MeSocialResponse`. 공급자는 카카오·네이버·구글 순 3개 */
export interface MeSocialResponse {
    hasPassword: boolean;
    providers: MeSocialProvider[];
}

/** `POST /api/me/social/{provider}/link` — 공급자 인가 주소. 화면이 그리로 페이지 이동한다 */
export interface SocialLinkStartResponse {
    redirectUrl: string;
}

export interface PasswordResetRequest {
    token: string;
    newPassword: string;
}

/**
 * 메일 링크 확인(`POST /api/auth/email/verify`) 결과 — community `docs/api-auth.md`·`api-account.md`.
 * `changed: true` 면 이메일 **변경** 링크였고 로그인 이메일이 새 주소로 바뀌었다. 인증 링크는 `changed: false`.
 */
export interface EmailVerifyResult {
    verified: boolean;
    changed: boolean;
}

/** 비밀번호 재설정 링크가 아직 쓸 수 있는가(`GET /api/auth/password/reset/check`) — 쓸 수 없으면 `400 TOKEN_INVALID`·`410 TOKEN_EXPIRED` 로 온다 */
export interface PasswordResetCheck {
    valid: boolean;
}
