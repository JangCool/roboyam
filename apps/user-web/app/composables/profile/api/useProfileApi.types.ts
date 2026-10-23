/**
 * 내 프로필 API 가 주고받는 모양 — **서버에 아직 없다**(목업). 계약 후보: `docs/features/member-mypage-contract.md` 「프로필」.
 * ⚠ 시각은 UTC ISO. 회원 ID 는 경로·본문에 없다(본인 것만, 세션으로 안다).
 */

/** `GET`·`PUT /api/me/profile` 응답 */
export interface MyProfile {
    nickname: string;
    /** 한 줄 소개 — 없으면 빈 글자 */
    bio: string;
    /** 가입 시각 */
    joinedAt: string;
    /** 마지막으로 닉네임을 바꾼 시각 — 바꾼 적 없으면 null */
    nicknameChangedAt: string | null;
    /** 다음에 닉네임을 바꿀 수 있는 시각 — 지금 바꿀 수 있으면 null */
    nicknameChangeAvailableAt: string | null;
    /** 닉네임을 바꾼 뒤 다시 바꿀 수 있기까지의 날 수(사이트 규칙 — 안내 문구용) */
    nicknameChangeIntervalDays: number;
}

/** `PUT /api/me/profile` 요청 — 두 칸 모두 보낸다(닉네임이 그대로면 변경 주기를 세지 않는다) */
export interface MyProfileRequest {
    nickname: string;
    bio: string;
}

/** 한 줄 소개 최대 글자 수(서버 규칙 후보 — 가정) */
export const PROFILE_BIO_MAX = 100;
