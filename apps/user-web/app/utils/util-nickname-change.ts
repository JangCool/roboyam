import type { NicknameChange } from '~/composables/profile/api/useProfileApi.types';

/**
 * 닉네임 변경 화면의 상태 — 서버 `nicknameChange`(`member-mypage-contract.md` 「결정」 닉네임 변경, 서버 `UserPolicy` 후보)를 둘로 가른다. 상태도 요청도 없다.
 * 판정은 서버다(`409 NICKNAME_CHANGE_LIMIT`). 화면은 미리 알리고 칸을 잠근다.
 *
 * - `FREE`    : 지금 바꿀 수 있다(`allowed`) — 가입 뒤 1번, 날짜 대기 없음.
 * - `USED_UP` : 그 1번을 이미 썼다 — 더 바꿀 수 없다(변경권 없음).
 *
 * 원본(community) `UtilNicknameChange` 에서 기다림(`WAITING`)과 변경권(`PAID`·`ticketPrice`)을 뺐다.
 */
export type NicknameStage = 'FREE' | 'USED_UP';

export default class UtilNicknameChange {
    /**
     * 지금 닉네임 변경이 어느 상태인가
     * @param {NicknameChange | null | undefined} change - 프로필 응답의 `nicknameChange`. 없으면(아직 못 받았다) 바꿀 수 있다고 본다(판정은 서버).
     * @returns {NicknameStage} 바꿀 수 있음 · 이미 바꿈.
     */
    static stageOf(change: NicknameChange | null | undefined): NicknameStage {
        return !change || change.allowed ? 'FREE' : 'USED_UP';
    }
}
