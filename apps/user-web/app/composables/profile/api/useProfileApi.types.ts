/**
 * 내 프로필 API 가 주고받는 모양 — **서버 연결됨**(server/api `MyProfileView`·`NicknameChangeView`·`MyProfileRequest`). 계약: `docs/features/member-mypage-contract.md` 「프로필」.
 * 닉네임 변경권 칸(`ticketCount`·`ticketPrice`·요청 `useTicket`)과 `avatarUrl` 도 서버가 준다(포인트 상점 1-2).
 * ⚠ 시각은 UTC ISO. 회원 ID 는 경로·본문에 없다(본인 것만, 세션으로 안다).
 */

/**
 * 닉네임 변경 규칙의 지금 상태 — `member-mypage-contract.md` 「결정」 닉네임 변경.
 * 가입 때 정한 닉네임은 가입 뒤 `freeLimit`번(서버 상수 `MyProfileService.NICKNAME_FREE_LIMIT` = 1)만 무료로 바꿀 수 있다(날짜 대기 없음).
 * 그 뒤로는 바꿀 때마다 포인트 상점에서 산 **닉네임 변경권 1장**을 쓴다(`useTicket: true`). 변경권이 없으면 `409 NICKNAME_TICKET_REQUIRED`(최상위 `ticketCount`·`ticketPrice`).
 */
export interface NicknameChange {
    /** 지금 무료로 바꿀 수 있는가 — `changeCount < freeLimit` */
    allowed: boolean;
    /** 지금까지 바꾼 횟수 — 가입 때 닉네임은 세지 않는다(0 에서 시작) */
    changeCount: number;
    /** 가입 뒤 무료로 바꿀 수 있는 횟수(서버 상수 1) */
    freeLimit: number;
    /** 안 쓴 닉네임 변경권 장수 */
    ticketCount: number;
    /** 변경권 한 장 값(P) — 포인트 상점 품목 가격(결정 2) */
    ticketPrice: number;
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
    /**
     * 프로필 아바타 그림 주소 — 서버가 늘 채운다(고르지 않았으면 `/avatars/roboyam/basic-default.svg`). 옛 응답에 없을 수 있어 선택 칸으로 둔다.
     * 고르기는 `useAvatarApi().select`(`PUT /api/me/profile/avatar`).
     */
    avatarUrl?: string | null;
}

/** `PUT /api/me/profile` 요청 — 두 칸 모두 보낸다(닉네임이 그대로면 변경 횟수·변경권을 쓰지 않는다 — 소개만 고치는 저장) */
export interface MyProfileRequest {
    nickname: string;
    bio: string;
    /**
     * 무료 변경을 썼을 때 변경권 1장을 써서 바꾼다(선택, 기본 false). 무료로 바꿀 수 있거나 닉네임이 그대로면 보내도 쓰지 않는다.
     * 규칙 위반·중복·정지로 실패하면 쓰지 않는다(같은 트랜잭션). 동시 요청에 한 장이 두 번 쓰이지 않는다.
     */
    useTicket?: boolean;
}

/** 한 줄 소개 최대 글자 수 — 서버 `MyProfileService.BIO_MAX`(앞뒤 공백을 자른 뒤 0~100자, 서버는 코드 포인트로 센다) */
export const PROFILE_BIO_MAX = 100;
