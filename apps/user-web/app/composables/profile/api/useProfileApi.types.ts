/**
 * 내 프로필 API 가 주고받는 모양 — **서버에 아직 없다**(목업). 계약 후보: `docs/features/member-mypage-contract.md` 「프로필」.
 * ⚠ 시각은 UTC ISO. 회원 ID 는 경로·본문에 없다(본인 것만, 세션으로 안다).
 */

/**
 * 닉네임 변경 규칙의 지금 상태 — `member-mypage-contract.md` 「결정」 닉네임 변경.
 * 가입 때 정한 닉네임은 가입 뒤 `freeLimit`번(서버 상수 1)만 바꿀 수 있다. 그 1번은 언제든(날짜 대기 없음), 쓰고 나면 더 바꿀 수 없다(`409 NICKNAME_CHANGE_LIMIT`).
 * 모양은 community `docs/api-profile.md` 의 `nicknameChange` 에서 `nextChangeAt`·변경권 칸(`ticketCount`·`ticketPrice`)을 뺐다.
 */
export interface NicknameChange {
    /** 지금 바꿀 수 있는가 — `changeCount < freeLimit` */
    allowed: boolean;
    /** 지금까지 바꾼 횟수 — 가입 때 닉네임은 세지 않는다(0 에서 시작) */
    changeCount: number;
    /** 가입 뒤 바꿀 수 있는 횟수(서버 상수 1) */
    freeLimit: number;
}

/** `GET`·`PUT /api/me/profile` 응답 */
export interface MyProfile {
    nickname: string;
    /** 한 줄 소개 — 없으면 빈 글자 */
    bio: string;
    /** 가입 시각 */
    joinedAt: string;
    /** 닉네임 변경 규칙의 지금 상태 */
    nicknameChange: NicknameChange;
}

/** `PUT /api/me/profile` 요청 — 두 칸 모두 보낸다(닉네임이 그대로면 변경 횟수를 쓰지 않는다 — 소개만 고치는 저장) */
export interface MyProfileRequest {
    nickname: string;
    bio: string;
}

/** 한 줄 소개 최대 글자 수(서버 규칙 후보 — 가정) */
export const PROFILE_BIO_MAX = 100;

