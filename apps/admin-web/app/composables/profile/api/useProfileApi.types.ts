import type { AdminRole } from '~/composables/auth/api/useAdminAuthApi.types';
import type { DisplayValues } from '#layers/display-time/utils/util-time';

/**
 * 내 정보 API 가 주고받는 모양 — 서버 `com.community.profile.dto.*` 와 같다(계약: `docs/features/roles-contract.md` 「4단계 구현 결과」).
 * ⚠ ID 는 문자열이다(Snowflake 64비트). 시각은 UTC ISO(`Instant`)다.
 */

/** 서버 `status` — 관리자 앱에 닿은 사람은 늘 `ACTIVE` 다(호환용 칸이라 값은 그대로 받는다) */
export type UserStatus = 'ACTIVE' | 'SUSPENDED' | 'BANNED' | 'WITHDRAWN';

/** 내가 받은 역할 하나 — 이름은 운영자가 정한 값 그대로 보인다 */
export interface ProfileRoleRef {
    roleId: string;
    roleName: string;
}

export interface ProfileResponse {
    id: string;
    /** 로그인 아이디로 쓰는 이메일. 없을 수 있다 */
    email: string | null;
    nickname: string;
    /** 옛 표시 — 최고 관리자 `ADMIN`, 그 밖에 `STAFF`. 권한 판정은 `isSuper` 와 역할로 한다 */
    role: AdminRole;
    /** 최고 관리자(루트 계정)인가 */
    isSuper: boolean;
    /** 내가 받은 역할들(만든 순서). 비었을 수 있다 — 역할이 없어도 내 정보는 볼 수 있다 */
    roles: ProfileRoleRef[];
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
 * 로그인 이력 한 줄 — 서버가 감사 기록(`audit_logs`)에서 내 계정의 로그인 성공·실패 줄을 읽어 준다.
 * 내 계정을 노린 틀린 비밀번호 시도는 나오지만 없는 이메일로 한 시도와 잠금 중의 시도는 없다.
 */
export interface LoginHistoryItem {
    id: string;
    /** 시도한 시각 — UTC ISO */
    at: string;
    /** 접속한 IP. 모르면 null */
    ip: string | null;
    /** 로그인 요청의 접속 환경 **요약**(서버 6단계) — `Chrome / Windows / Desktop`(영문 고정 낱말), 못 알아내면 정제한 원문 앞 80글자. 화면은 그대로 보인다. UA 를 남기기 전의 옛 기록이나 헤더가 없던 요청은 null — 화면은 접속 환경 줄을 그리지 않는다 */
    agent: string | null;
    result: LoginResult;
    /** 실패 사유 코드 — 지금 서버는 실패면 늘 `INVALID_CREDENTIALS`(틀린 비밀번호와 계정 상태를 가르지 않는다). 성공이면 null. 화면은 아는 코드만 문구로 바꾸고 모르는 코드는 그대로 보인다 */
    reason: string | null;
}

export interface LoginHistoryResponse {
    /** 최근 것부터, 최근 20건 고정(쪽 나누기 없음) */
    items: LoginHistoryItem[];
}

/**
 * 내 표시 형식 — 언어·시간대·시간 형식·날짜 형식. 칸 모양은 공유 레이어(`@roboyam/display-time`)의 `DisplayValues` 와 같다.
 * 관리자 표시 값의 층: 사이트 표시 기본값 ← 내 표시 형식.
 */
export type { DisplayValues };

/** 지금 화면에 적용되는 값의 출처 — 사이트 표시 기본값(`SYSTEM`) · 내 표시 형식(`USER`) */
export type DisplaySource = 'SYSTEM' | 'USER';

export interface MyDisplayResponse {
    /** "내 표시 형식을 쓴다" — 켜면 `values` 가 이기고, 꺼도 `values` 는 남는다 */
    use: boolean;
    /** 내가 정한 값(꺼 둔 동안에도 남아 있다). 한 번도 저장한 적이 없으면 사이트 표시 기본값과 같다 */
    values: DisplayValues;
    /** 사이트 표시 기본값(일반 설정에서 저장한 값) — 끄면 이 값으로 본다. 요청마다 읽어 사이트 기본값을 바꾸면 다음 조회부터 따라온다 */
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
