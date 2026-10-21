/**
 * 관리자 로그인 API 가 주고받는 모양 — 후보 계약(`POST /api/admin/auth/login`·`logout`, `GET /api/admin/auth/me`)이고 확정 전이다.
 * 참고: community `docs/api-admin-auth.md` (읽기 전용 참고). 사용자 객체는 공개 사이트 `UserView` 와 같다고 적혀 있으나, 화면이 쓰는 칸만 적었다.
 * ⚠ ID 는 문자열이다(Snowflake 64비트). 숫자로 바꾸지 않는다.
 */

/** 관리자 앱에 들어올 수 있는 역할 — `ADMIN` 최고 관리자 · `STAFF` 운영진. 일반 회원(`USER`)은 비밀번호가 맞아도 들어올 수 없다(서버가 `401 INVALID_CREDENTIALS`) */
export type AdminRole = 'ADMIN' | 'STAFF';

export interface AdminUser {
    id: string;
    /** 화면에 보이는 이름 */
    nickname: string;
    role: AdminRole;
    email: string | null;
    /**
     * 최고 관리자(루트 계정)인가 — 모든 권한을 갖는다(`docs/domain/operator-permissions.md` 4번). 서버가 내려 준다고 가정한 값이다.
     * `role`(`ADMIN`·`STAFF`)은 옛 표시이고, 정책상 직무 역할로 대체된다 — 권한 판정은 이 칸과 `permissions` 로 한다.
     */
    isSuper: boolean;
    /** 받은 역할들의 권한 코드 합집합(`영역.행위`, 예: `audit.read`). 최고 관리자는 모든 코드가 들어 있다 */
    permissions: string[];
}

/**
 * 로그인 상태(`me`)·로그인 응답 공통.
 * `siteName`: 사이트 이름 — 비로그인에도 준다(로그인 화면의 로고·탭 제목이 쓴다). 서버가 못 주면(설치 전 등) 없을 수 있어 선택으로 둔다 → 화면은 기본 이름을 쓴다.
 * `siteLogoUrl`: 일반 설정에서 올린 로고 — 이름과 같은 이유로 비로그인에도 준다. 이미지면 파일 주소, SVG 면 data URI(그대로 `<img src>`). 없으면 null → 이름 글자만 보인다.
 */
export interface AdminMeResponse {
    authenticated: boolean;
    user: AdminUser | null;
    siteName?: string | null;
    siteLogoUrl?: string | null;
}

export interface AdminLoginRequest {
    email: string;
    password: string;
}
