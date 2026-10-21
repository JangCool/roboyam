import type { AdminRole } from '~/composables/auth/api/useAdminAuthApi.types';
import type { DisplayValues } from '#layers/display-time/utils/util-time';

/**
 * 내 정보 API 가 주고받는 모양 — 후보 계약(`GET`·`PATCH /api/admin/profile`)이고 확정 전이다.
 * 서버 사용자(`UserEntity`·`ProfileEntity`)에서 화면이 쓰는 칸만 적었다. ⚠ ID 는 문자열이다(Snowflake 64비트). 시각은 UTC ISO(`Instant`)다.
 */

/** 서버 `UserStatus` — 관리자 화면에 들어온 사람은 늘 `ACTIVE` 지만 값은 그대로 받는다 */
export type UserStatus = 'ACTIVE' | 'SUSPENDED' | 'BANNED' | 'WITHDRAWN';

export interface ProfileResponse {
    id: string;
    /** 로그인 아이디로 쓰는 이메일. 없을 수 있다 */
    email: string | null;
    nickname: string;
    role: AdminRole;
    status: UserStatus;
    /** 가입 시각 — UTC ISO */
    createdAt: string;
    /** 최근 로그인 시각 — UTC ISO. 한 번도 없으면 null */
    lastLoginAt: string | null;
}

/** 바꿀 수 있는 것은 닉네임뿐이다 */
export interface ProfileUpdateRequest {
    nickname: string;
}

/** 로그인 시도의 결과 — 성공 `OK` · 실패 `FAIL` */
export type LoginResult = 'OK' | 'FAIL';

/**
 * 로그인 이력 한 줄 — ⚠ 서버에 아직 없는 데이터다. 지금 서버가 가진 것은 이메일별 실패 횟수·잠금(`login_attempts`)과 `users.last_login_at` 뿐이라
 * 시도마다 시각·IP·User-Agent·결과를 남기는 저장소가 새로 필요하다(저장 항목·보관 기간은 정책 결정 사항).
 */
export interface LoginHistoryItem {
    id: string;
    /** 시도한 시각 — UTC ISO */
    at: string;
    /** 접속한 IP. 모르면 null */
    ip: string | null;
    /** 로그인 요청의 `User-Agent` 헤더 원문. 없으면 null. 화면이 브라우저·운영체제·기기 종류로 풀어 보인다(`UtilUserAgent`). 길이 제한은 서버 정책 미정(후보: 512자에서 자름) */
    agent: string | null;
    result: LoginResult;
    /** 실패 사유 코드(예: `INVALID_CREDENTIALS` · `LOGIN_LOCKED`). 성공이면 null. 화면은 아는 코드만 문구로 바꾸고 모르는 코드는 그대로 보인다 */
    reason: string | null;
}

export interface LoginHistoryResponse {
    /** 최근 것부터. 건수는 서버가 정한다(후보: 최근 20건 고정) */
    items: LoginHistoryItem[];
}

/**
 * 내 표기(표시 값) — 언어·시간대·시간 형식·날짜 형식. 칸 모양은 공유 레이어(`@roboyam/display-time`)의 `DisplayValues` 와 같다.
 * ⚠ 서버에 아직 없는 데이터다(관리자 표시 값 층: 제품 기준 ← 내 표기).
 */
export type { DisplayValues };

/** 지금 화면에 적용되는 값의 출처 — 제품 기준(`SYSTEM`) · 내 표기(`USER`) */
export type DisplaySource = 'SYSTEM' | 'USER';

export interface MyDisplayResponse {
    /** "내 표기를 쓴다" — 켜면 `values` 가 이기고, 꺼도 `values` 는 남는다 */
    use: boolean;
    /** 내가 정한 값(꺼 둔 동안에도 남아 있다). 한 번도 저장한 적이 없으면 제품 기준 값 */
    values: DisplayValues;
    /** 제품 기준 값 — 끄면 이 값으로 본다 */
    inherited: DisplayValues;
    /** 지금 적용되는 값의 출처 — `use` 면 `USER`, 아니면 `SYSTEM` */
    effectiveFrom: DisplaySource;
}

export interface MyDisplayRequest {
    use: boolean;
    values: DisplayValues;
}

/** 내 알림 수신 설정 — 관리자 알림의 이메일 수신 on/off 하나 */
export interface MyNotificationResponse {
    emailEnabled: boolean;
}

export interface MyNotificationRequest {
    emailEnabled: boolean;
}
