/**
 * 닉네임 이미지(닉네임 이미지) API 가 주고받는 모양 — 서버 `server/api`(`/api/me/nickname-image`). 처음 모양은 community `docs/api-nickname-image.md` 2절에서
 * 로보얌에 없는 아이템샵 변경권 칸(`ticketCount`·`ticketRequired`)을 뺀 것이다. 계약 후보: `docs/features/member-social-contract.md`.
 * 신청은 관리자 심사 뒤 적용된다 — 심사 대기가 생기면 운영자 알림 `NICKNAME_IMAGE_PENDING`(이미 예약된 이벤트)이 나간다(서버 몫).
 */

/** 적용 중인 이미지 한 장 — `url`(240×60, GIF 는 움직인다) · `still`(GIF 일 때만 정지 이미지) · `id`(적용한 신청 ID) */
export interface NicknameImageView {
    id?: string;
    url: string;
    still: string | null;
}

/** 신청을 막는 까닭 — `REQUIREMENTS_NOT_MET` 관리자가 정한 신청 조건 미달 · `SUSPENDED` 정지 중 */
export type NicknameImageIneligibleReason = 'REQUIREMENTS_NOT_MET' | 'SUSPENDED';

/** 등급 한 칸 — `code` 는 등급 키, `label` 은 서버가 준 이름 */
export interface NicknameImageGrade {
    code: string;
    label: string;
}

/** 신청 조건 방식 — 관리자 설정(등급 기준: 최소 등급 + 이메일 인증 · 사용자 정의: 켜진 조건을 모두) */
export type NicknameImageRequirementMode = 'GRADE' | 'CUSTOM';

/**
 * 신청 조건 한 줄 — 켜진 조건만, `EMAIL_VERIFIED` · `GRADE` · `DAYS` · `ACTIVITY` · `POINTS` 차례로 온다. `met` = 지금 채웠는가.
 *  - `GRADE`: `required` 가 null 이면 관리자가 고른 등급이 지워졌다(조건 확인 중). `current` = 내 등급.
 *  - `DAYS`(가입 후 일수) · `ACTIVITY`(글·댓글 수 — `current` 는 필요한 수에서 멈출 수 있다) · `POINTS`(보유 포인트).
 */
export type NicknameImageRequirement =
    | { kind: 'EMAIL_VERIFIED'; met: boolean }
    | { kind: 'GRADE'; required: NicknameImageGrade | null; current: NicknameImageGrade; met: boolean }
    | { kind: 'DAYS' | 'ACTIVITY' | 'POINTS'; required: number; current: number; met: boolean };

/** 신청 조건 — 방식과 켜진 조건들 */
export interface NicknameImageRequirements {
    mode: NicknameImageRequirementMode;
    items: NicknameImageRequirement[];
}

/** 가장 최근의 대기·반려 신청 — 반려면 `rejectReason`·`canReapplyAt`(반려 + 재신청 대기 시간) */
export interface NicknameImageRequest {
    id: string;
    status: 'PENDING' | 'REJECTED';
    url: string;
    createdAt: string;
    rejectReason: string | null;
    canReapplyAt: string | null;
}

/** 올리기 한도 — 화면이 미리 검사할 값(판정은 서버) */
export interface NicknameImageLimits {
    maxBytes: number;
    width: number;
    height: number;
    /** "4:1" */
    ratio: string;
    maxGifFrames: number;
    formats: string[];
    /** 반려 뒤 다시 신청할 수 있기까지(시간) — 없으면 72 로 본다 */
    rejectedReapplyHours?: number;
}

/** `GET /api/me/nickname-image` — 화면 상태 한 벌(`POST` 의 `201` 도 같은 모양) */
export interface MyNicknameImage {
    eligible: boolean;
    ineligibleReason: NicknameImageIneligibleReason | null;
    /** 내 등급 */
    grade: NicknameImageGrade;
    /** 관리자가 정한 신청 조건과 내 상태 */
    requirements: NicknameImageRequirements;
    current: NicknameImageView | null;
    request: NicknameImageRequest | null;
    /** 지금 새 신청을 막는 시각 제한의 끝(반려 3일·운영 해제 7일), 없으면 null */
    reapplyAt?: string | null;
    limits: NicknameImageLimits;
}

/** 올리기 전에 화면이 알아낸 문제 — `message` 는 그릴 때의 글자(`t` 로 만든다) */
export interface NicknameImageProblem {
    code: 'FORMAT' | 'EMPTY' | 'TOO_LARGE' | 'UNREADABLE' | 'RATIO';
    message: string;
}

/** 올리기 선택 — 취소·진행률 */
export interface NicknameImageUploadOptions {
    signal?: AbortSignal;
    /** 0~1 */
    onProgress?: (fraction: number) => void;
}
