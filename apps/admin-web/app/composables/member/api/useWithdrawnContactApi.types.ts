/**
 * 탈퇴 회원 연락처(분리 보관) API 의 요청·응답 모양(계약 「0-4」 — 서버 `WithdrawnContactView`·`WithdrawnContactRevealView`·`RevealedContact`, commerce.md 17-2).
 * 결제 이력이 있는 회원이 탈퇴하면 이메일 하나만 암호화해 따로 보관한다(분쟁·세무 문의 때 연락 수단). 열람은 `member.withdrawn.contact`(SENSITIVE) + 사유 + 감사.
 */

/** 열람 기록 한 줄 — 최근 것부터 */
export interface WithdrawnContactReveal {
    /** 열람한 운영자 이름 — 운영자가 지워졌으면 null */
    operatorName: string | null;
    revealedAt: string;
    reason: string;
}

/** `GET /api/admin/members/{memberId}/withdrawn-contact` — 이메일 값은 오지 않는다(열람해야 받는다) */
export interface WithdrawnContact {
    /** 분리 보관한 이메일이 있는가 — 결제 이력이 없는 탈퇴·탈퇴하지 않은 회원·파기된 것은 false */
    stored: boolean;
    /** 탈퇴 시각 — 보관했으면 보관 표의 값, 아니면 회원의 탈퇴 시각(탈퇴하지 않았으면 null) */
    withdrawnAt: string | null;
    /** 파기 예정일(서비스 날짜 `YYYY-MM-DD` — 마지막 거래일 + 5년). 보관한 것이 없으면 null */
    purgeAfter: string | null;
    reveals: WithdrawnContactReveal[];
}

/** 열람 — `POST …/withdrawn-contact/reveal` */
export interface WithdrawnContactRevealRequest {
    reason: string;
}

/** 열람 응답 — 화면은 `maskAt`(서버가 정한 순간, 기본 10분 뒤)이 지나면 다시 가린다 */
export interface WithdrawnContactRevealResult {
    email: string;
    maskAt: string;
}

/** 열람 사유 길이(서버 `WithdrawnContactStore` 와 같은 값 — 배송지 열람과 같다, commerce.md 10-9) */
export const WITHDRAWN_CONTACT_REASON_MIN = 5;
export const WITHDRAWN_CONTACT_REASON_MAX = 200;
