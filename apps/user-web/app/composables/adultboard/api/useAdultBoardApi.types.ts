/**
 * 19금 게시판 접근 설정·핀번호 — 서버와 주고받는 모양. 계약: `docs/features/adult-board-pin-contract.md` (서버 `MyAdultBoardController`).
 * 핀번호·계정 비밀번호는 요청 본문에만 실리고 응답에는 오지 않는다.
 */

/** 핀번호를 다시 묻는 기준 — `IDLE` 마지막으로 본 뒤 1시간(보는 동안 연장) · `FIXED` 핀번호를 넣은 뒤 1시간(보는 중이어도) */
export type AdultBoardRelockMode = 'IDLE' | 'FIXED';

export const ADULT_BOARD_RELOCK_MODES: readonly AdultBoardRelockMode[] = ['IDLE', 'FIXED'];

/** `GET /api/me/adult-board` — 내 접근 설정과 이 기기(로그인)의 핀 확인 상태 */
export interface AdultBoardStatus {
    /** 「19금 게시판 접근」 — 기본 꺼짐. 핀 없이 켜진 상태는 없다 */
    enabled: boolean;
    /** 핀번호를 정한 때(ISO) — 꺼져 있으면 null */
    pinSetAt: string | null;
    relockMode: AdultBoardRelockMode;
    /** 이 기기에서 핀 확인이 살아 있는가 */
    unlocked: boolean;
    /** 핀 확인이 끝나는 때(ISO) — 잠겨 있으면 null */
    unlockedUntil: string | null;
    /** 연속 5번 틀려 핀 입력이 막힌 끝 시각(ISO, 회원 단위) — 막히지 않았으면 null */
    lockedUntil: string | null;
    /** 계정 비밀번호가 있는 회원인가 — 소셜 로그인만 쓰면 false(본인 확인은 「최근 10분 안 로그인」, 요청에 `password` 를 싣지 않는다) */
    passwordAvailable: boolean;
}

/** `PUT /api/me/adult-board` — 켜기(계정 비밀번호 + 핀번호 6자리 — 비밀번호가 없는 회원은 `password` 없이) · 끄기(확인 없음) */
export type AdultBoardEnabledRequest = { enabled: true; password?: string; pin: string } | { enabled: false };

/** `PUT /api/me/adult-board/pin` — 바꾸기·잊었을 때 다시 정하기(틀린 횟수·잠금도 푼다) */
export interface AdultBoardPinRequest {
    /** 계정 비밀번호 — 비밀번호가 없는 회원은 싣지 않는다 */
    password?: string;
    pin: string;
}

/** `PUT /api/me/adult-board/relock-mode` */
export interface AdultBoardRelockModeRequest {
    relockMode: AdultBoardRelockMode;
}

/** `POST /api/me/adult-board/unlock` */
export interface AdultBoardUnlockRequest {
    pin: string;
}
