/**
 * 본인인증 API 의 요청·응답 모양(계약 — `docs/features/commerce-foundation-contract.md` 「0-4」, 서버 `IdentityStatusView`·`IdentityStartView`·`IdentityConfirmRequest`).
 * 생년월일·CI·DI·이름·휴대폰 번호는 화면에 내려오지 않는다 — 만 19세 이상 여부(`adult`)와 19세 생일(`adultFrom`)만 온다.
 */

/** 지금 쓰는 본인인증 구현 — 서버 배포 설정(`roboyam.identity.provider`)이 정한다. `FAKE` 는 local·시험 전용(dev·prod 에서는 서버가 뜨지 않는다) */
export type IdentityProvider = 'PORTONE' | 'FAKE';

/** 내 본인인증 상태 — `GET /api/identity-verifications/me`, 인증 완료(`confirm`)의 응답도 같은 모양 */
export interface IdentityStatus {
    verified: boolean;
    /** 인증한 순간(ISO-8601 UTC). 미인증이면 null */
    verifiedAt: string | null;
    /** 지금(요청 시점, 서버가 생년월일로 계산한 만 나이) 만 19세 이상인가. 미인증이면 null */
    adult: boolean | null;
    /** 만 19세가 되는 날(서비스 날짜 `YYYY-MM-DD`). 미인증이면 null — 미성년 안내의 「이 날이 지나면」에 쓴다 */
    adultFrom: string | null;
}

/** 인증 시작 — `POST /api/identity-verifications`. 서버가 인증 번호를 먼저 발급한다(발급 후 30분 안에 끝내야 한다) */
export interface IdentityVerificationStart {
    verificationId: string;
    provider: IdentityProvider;
    /** 포트원 상점 ID — `PORTONE` 일 때만(`FAKE` 면 칸이 아예 없다) */
    storeId?: string;
    /** 포트원 본인인증 채널 키 — `PORTONE` 일 때만(`FAKE` 면 칸이 아예 없다) */
    channelKey?: string;
}

/** 가짜 본인인증(개발용) 입력 — 같은 `personKey` = 같은 사람(1인 1계정 시험용) */
export interface IdentityFakeInput {
    /** 생년월일 `YYYY-MM-DD`(서버: 1900-01-01 ~ 오늘) */
    birthDate: string;
    personKey: string;
}

/** 인증 완료 — `POST /api/identity-verifications/{verificationId}/confirm`. 포트원은 본문이 비고(서버가 포트원에 결과를 묻는다), 가짜는 `fake` */
export interface IdentityConfirmRequest {
    fake?: IdentityFakeInput;
}

/**
 * 인증 시작·완료가 실패할 때 화면이 자기 문구를 고르는 오류 코드(`ApiError.code`). 그 밖의 코드(`IDENTITY_NOT_CONFIGURED`·`INDEX_KEY_MISSING`·
 * `IDENTITY_VERIFICATION_NOT_FOUND` 등)는 서버 문구를 그대로 보인다. `IDENTITY_ALREADY_VERIFIED` 는 상태를 새로 받아 인증 완료로 넘긴다(`useIdentity`).
 */
export type IdentityErrorCode =
    /** 409 — 이미 다른 계정에 묶인 사람(1인 1계정) */
    | 'IDENTITY_ALREADY_LINKED'
    /** 409 — 탈퇴 후 30일 재인증 제한. 오류 본문 최상위 `retryAfter`(`ApiError.extras`, 서비스 날짜 `YYYY-MM-DD`)부터 다시 할 수 있다 */
    | 'IDENTITY_REVERIFY_BLOCKED'
    /** 502 — 인증 업체 오류 */
    | 'IDENTITY_PROVIDER_FAILED'
    /** 410 — 발급 후 30분이 지났거나 이미 끝난 인증 번호 */
    | 'IDENTITY_VERIFICATION_EXPIRED';

/** 화면 검사 상한 — 가짜 인증의 사람 번호 길이(서버 `FakeIdentityInput` 1~40자) */
export const IDENTITY_PERSON_KEY_MAX = 40;
