/**
 * 관리자 2차 인증(TOTP) API 가 주고받는 모양 — 서버 `com.community.mfa.dto.request|response` 의 이름·칸 그대로(`docs/features/commerce-foundation-contract.md` 「0-3」). 정책: `docs/domain/commerce.md` 13-3.
 * ⚠ ID 는 문자열이다(Snowflake 64비트). 시각은 모두 UTC ISO-8601 이다. 빈 값은 null 로 온다(칸은 늘 있다 — 서버가 `NON_NULL` 로 빼는 칸만 선택).
 */
import type { AdminMeResponse } from '~/composables/auth/api/useAdminAuthApi.types';

/** 로그인 2단계에서 보내는 것 — OTP(`code`, 6자리) 또는 복구 코드(`recoveryCode`, `XXXXX-XXXXX`) 중 하나 */
export interface MfaVerifyRequest {
    code?: string;
    recoveryCode?: string;
    /** 「이 기기에서 N일 동안 다시 묻지 않기」 */
    rememberDevice: boolean;
}

/**
 * 2단계 통과 응답 — 서버는 로그인 응답과 같은 `AdminMeResponse` 를 준다. 복구 코드로 들어왔을 때만 `recoveryCodesLeft`(남은 개수)가 실린다.
 */
export type MfaVerifyResponse = AdminMeResponse;

/** 등록 시작 — 비밀키와 QR 에 넣을 otpauth 주소. 비밀키는 화면 메모리에만 둔다(로그·저장소 금지) */
export interface MfaEnrollmentView {
    /** base32 비밀키(대문자). 화면은 4자씩 띄어 보인다 */
    secret: string;
    /** `otpauth://totp/{issuer}:{accountName}?secret=…&issuer=…` — QR 로 그린다 */
    otpauthUri: string;
    issuer: string;
    accountName: string;
}

/** 등록 확정 응답 — 복구 코드 10개(`XXXXX-XXXXX`). 강제 등록(2차 대기 세션)이면 `me` 도 실려 로그인이 끝나고, 자발 등록·다시 등록이면 칸이 없다 */
export interface MfaEnrollmentConfirmResponse {
    recoveryCodes: string[];
    me?: AdminMeResponse;
}

/** 복구 코드 새로 만들기 응답 */
export interface MfaRecoveryCodesResponse {
    recoveryCodes: string[];
}

/** 내 2차 인증 상태 */
export interface MfaMeView {
    /** 사이트에서 2차 인증을 켰는가 */
    siteEnabled: boolean;
    /** 내가 OTP 를 등록했는가 */
    enrolled: boolean;
    enrolledAt: string | null;
    lastUsedAt: string | null;
    /** 남은 복구 코드(0~10) */
    recoveryCodesLeft: number;
    /** 켜져 있고 아직 등록하지 않았을 때의 유예 마감. 그 밖에는 null */
    graceEndsAt: string | null;
    /** 기기 신뢰 일수(설정의 개월 × 30) */
    trustDays: number;
}

/** 신뢰 기기 한 대 */
export interface MfaTrustedDeviceView {
    id: string;
    /** 접속 환경 요약 — `Chrome / Windows / Desktop`(낱말은 영문 고정, 로그인 이력과 같은 요약) */
    agent: string | null;
    ip: string | null;
    trustedAt: string;
    expiresAt: string;
    lastUsedAt: string | null;
    /** 지금 이 브라우저인가 */
    current: boolean;
}

/** 등록 현황의 미등록 운영자 한 명 */
export interface LoginSecurityPendingOperator {
    operatorId: string;
    name: string;
    /** 유예 마감(꺼져 있으면 null) */
    graceEndsAt: string | null;
}

export interface LoginSecuritySummary {
    /** 사용 중인 운영자 수(루트 포함) */
    operators: number;
    enrolled: number;
    inGrace: number;
    graceExpired: number;
    /** 미등록 운영자(유예 마감이 이른 차례, 최대 10명) */
    pending: LoginSecurityPendingOperator[];
}

/** 로그인 보안 설정(루트만) */
export interface LoginSecurityView {
    enabled: boolean;
    /** 마지막으로 켠 시각·켠 운영자 이름(켠 적 없으면 null) */
    enabledAt: string | null;
    enabledBy: string | null;
    /** 유예 일수(1~90, 기본 14) */
    graceDays: number;
    /** 기기 신뢰 개월(1~12, 기본 1 = 30일) */
    trustMonths: number;
    /** OTP 실패 한도(3~10, 기본 5) — 넘으면 10분 잠금(고정) */
    maxFailures: number;
    /** 루트 본인이 OTP 를 등록했는가 — 아니면 켤 수 없다(409 `MFA_ROOT_NOT_ENROLLED`) */
    rootEnrolled: boolean;
    summary: LoginSecuritySummary;
}

export interface LoginSecuritySaveRequest {
    enabled: boolean;
    graceDays: number;
    trustMonths: number;
    maxFailures: number;
}

/** 운영자 상세의 2차 인증 상태(읽기는 운영자 조회 권한자 모두) */
export interface OperatorMfaView {
    /** 사이트에서 켜져 있는가 — 꺼져 있으면 「사용 안 함」 */
    siteEnabled: boolean;
    enrolled: boolean;
    enrolledAt: string | null;
    /** 미등록이고 켜져 있을 때 유예 마감 */
    graceEndsAt: string | null;
    graceExpired: boolean;
    /** 신뢰 기기 수 */
    trustedDevices: number;
}
