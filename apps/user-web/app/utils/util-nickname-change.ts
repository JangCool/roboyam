import type { NicknameChange } from '~/composables/profile/api/useProfileApi.types';

/**
 * 닉네임 변경 화면의 상태 — 서버 `nicknameChange`(`member-mypage-contract.md` 「결정」 닉네임 변경 · 「후속 작업: 닉네임 변경권」, 서버 `MyProfileService` 후보)를 셋으로 가른다. 상태도 요청도 없다.
 * 판정은 서버다(`409 NICKNAME_TICKET_REQUIRED`). 화면은 미리 알리고 칸을 잠그거나 변경권 사용을 묻는다.
 *
 * - `FREE`        : 지금 무료로 바꿀 수 있다(`allowed`) — 가입 뒤 1번, 날짜 대기 없음.
 * - `TICKET`      : 무료 1번을 썼고 변경권이 있다 — 칸이 열리고, 바꾸면 변경권 1장을 쓴다(`useTicket: true`).
 * - `NEED_TICKET` : 무료 1번을 썼고 변경권이 없다 — 칸을 잠그고 포인트 상점에서 변경권을 사는 링크를 보인다.
 *
 * 원본(community) `UtilNicknameChange` 에서 기다림(`WAITING`)을 뺐다(로보얌은 날짜 대기가 없다).
 */
export type NicknameStage = 'FREE' | 'TICKET' | 'NEED_TICKET';

export default class UtilNicknameChange {
    /**
     * 지금 닉네임 변경이 어느 상태인가
     * @param {NicknameChange | null | undefined} change - 프로필 응답의 `nicknameChange`. 없으면(아직 못 받았다) 바꿀 수 있다고 본다(판정은 서버).
     * @returns {NicknameStage} 무료로 바꿀 수 있음 · 변경권으로 바꿀 수 있음 · 변경권이 필요함.
     */
    static stageOf(change: NicknameChange | null | undefined): NicknameStage {
        if (!change || change.allowed) return 'FREE';
        return (change.ticketCount ?? 0) > 0 ? 'TICKET' : 'NEED_TICKET';
    }
}
