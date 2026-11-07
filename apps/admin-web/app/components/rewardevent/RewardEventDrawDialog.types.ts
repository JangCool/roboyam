/** 추첨 대화상자의 등수 하나 — 인원과 지금 당첨자 수(`RewardEventDrawDialog` 의 `tiers`) */
export interface RewardDrawTierChoice {
    id: string;
    name: string;
    quota: number;
    current: number;
}
