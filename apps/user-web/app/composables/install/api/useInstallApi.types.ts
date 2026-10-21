/**
 * 설치 마법사 API 가 주고받는 모양 — docs/api-install.md · docs/features/install-contract.md 와 같다.
 * ⚠ 설치 토큰·루트 비밀번호·메일 비밀번호·소셜 시크릿은 **요청에만** 담긴다. 어떤 응답에도 없다.
 * 메일·사이트 입력(`MailInput`·`SiteInput`)과 소셜 공급자 코드(`SocialProviderCode`)는 이 요청의 일부라 여기에 둔다.
 */

/** 메일 서버의 보안 방식 — 없음(25) · STARTTLS(587) · SSL(465) */
export type MailSecurity = 'NONE' | 'STARTTLS' | 'SSL';

/** 메일 서버 입력 — 마법사 `mail`, 설정 화면 `PUT`, 연결 확인(`mail-check`·`…/check`)이 같은 모양이다 */
export interface MailInput {
    host: string;
    port: number;
    /** 인증이 필요 없으면 생략(비밀번호와 짝) */
    username?: string;
    /** 인증이 필요 없으면 생략. 설정 화면에서 이미 등록돼 있으면 **생략해 기존 값을 유지**한다(연결 확인도 같다) */
    password?: string;
    from: string;
    security: MailSecurity;
}

/** 연결 확인이 실패한 까닭 — 서버 응답 원문·자격 증명은 담기지 않는다 */
export type MailCheckReason = 'CONNECT_FAILED' | 'AUTH_FAILED' | 'TLS_FAILED' | 'TIMEOUT';

/** 연결 확인 응답 — SMTP 에 접속·인증까지만 해 보고 메일은 보내지 않는다 */
export interface MailCheckResult {
    ok: boolean;
    reason?: MailCheckReason;
}

/** 사이트 이름·공개 주소 입력 — 마법사 `site`, 설정 화면 `PUT` */
export interface SiteInput {
    /** 1~50자 */
    name: string;
    /** `http(s)://호스트[:포트]` — 끝 `/`·경로·쿼리 없음 */
    publicUrl: string;
    /** 한 줄 소개(슬로건) — 0~100자. 비우거나 null 이면 없음(로고 밑 줄을 그리지 않는다) */
    tagline?: string | null;
}

/** 공급자 코드 — 서버 응답은 늘 이 대문자 꼴이다(`GET /api/auth/providers` 의 id 는 소문자라 `UtilProvider` 가 대소문자를 가리지 않는다) */
export type SocialProviderCode = 'NAVER' | 'KAKAO' | 'GOOGLE';

/** `GET /api/system/installation/status` — 이 밖의 정보는 담기지 않는다 */
export interface InstallationStatus {
    installed: boolean;
    /** 이 세션이 설치 토큰을 맞혔는가(새로고침해도 세션이 유효하면 이어진다) */
    authorized: boolean;
}

/** 설치 점검 항목 하나 — `blocking: false` 인 실패는 경고일 뿐 설치를 막지 않는다(기본은 막는다) */
export interface InstallCheck {
    /** `DATABASE` · `MIGRATIONS` · `EXTENSIONS` · `CRYPTO_KEY` … */
    key: string;
    ok: boolean;
    label: string;
    /** 사람이 읽는 요약. 접속 문자열·사용자 이름·경로 같은 내부 정보는 없다 */
    detail: string;
    blocking?: boolean;
}

/** `GET /api/system/installation/checks` */
export interface InstallChecksView {
    ok: boolean;
    checks: InstallCheck[];
}

/** 루트 계정 입력 — 첫 ADMIN 이 된다 */
export interface InstallRootInput {
    email: string;
    nickname: string;
    password: string;
}

/** 소셜 로그인 키 한 곳 — 공급자는 최대 3개·중복 불가 */
export interface InstallSocialProviderInput {
    provider: SocialProviderCode;
    clientId: string;
    clientSecret: string;
    enabled: boolean;
}

/** 소셜 로그인 키 입력 — `null`(요청의 `social`)이면 건너뜀 */
export interface InstallSocialInput {
    providers: InstallSocialProviderInput[];
}

/**
 * `POST /api/system/installation/complete` 요청 — `mail`·`social` 이 `null` 이면 건너뜀.
 * `boardPreset` 을 빼면 서버가 기본(중립) 구성으로 설치한다.
 */
export interface InstallCompleteRequest {
    root: InstallRootInput;
    site: SiteInput;
    mail: MailInput | null;
    social: InstallSocialInput | null;
    boardPreset?: string;
}

/** 기본 게시판 구성(프리셋)의 게시판 한 칸 — 설정 값은 담기지 않는다 */
export interface InstallBoardPresetBoard {
    name: string;
    /** 말머리 이름들(순서대로) */
    prefixes: string[];
}

/** 기본 게시판 구성(프리셋) 하나 — 설치 화면에서 고른다 */
export interface InstallBoardPreset {
    /** `NEUTRAL` · `ROBOYAM` … 설치 요청의 `boardPreset` */
    id: string;
    name: string;
    description: string;
    boards: InstallBoardPresetBoard[];
}

/** `GET /api/system/installation/board-presets` — 토큰 인증 뒤 */
export interface InstallBoardPresetsView {
    /** 고르지 않았을 때 서버가 쓰는 프리셋 */
    defaultId: string;
    presets: InstallBoardPreset[];
}

/** `201` — 설치가 끝났다 */
export interface InstallCompleteResponse {
    installed: boolean;
}

/** `POST …/verify-token` 응답 */
export interface InstallTokenResponse {
    authorized: boolean;
}
