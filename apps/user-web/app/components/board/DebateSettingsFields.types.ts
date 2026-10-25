/** 찬반 토론 설정 세 칸의 값 — `DebateSettingsFields` 의 `v-model`. 글쓰기·고치기·글 상세 「토론 설정」 대화창이 같이 쓴다(판정·변환은 `UtilDebate`) */
export interface DebateSettingsValues {
    /** 「찬반 토론 열기」 */
    enabled: boolean;
    /** 「중립도 받기」(기본 켬) */
    neutralEnabled: boolean;
    /** 「입장 바꾸기 허용」(기본 켬) — 끄면 한 번 고른 입장을 바꿀 수 없다 */
    stanceChangeAllowed: boolean;
}

/** 새 토론의 처음 값 — 열기는 끔, 두 설정은 켬(계약 기본값). 쓰는 곳은 `{ ...DEFAULT_DEBATE_SETTINGS }` 로 복사해 쓴다 */
export const DEFAULT_DEBATE_SETTINGS: Readonly<DebateSettingsValues> = { enabled: false, neutralEnabled: true, stanceChangeAllowed: true };
