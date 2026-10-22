import type { PointMemberCandidate } from '~/composables/point/api/usePointApi.types';
import type { PointAdjustMode } from '~/utils/util-point';

/** 조정 대화상자가 올리는 값 — 부르는 쪽이 확인 대화상자를 거쳐 서버로 보낸다 */
export interface PointAdjustSubmit {
    member: PointMemberCandidate;
    mode: PointAdjustMode;
    /** 금액(1~10000, 부호 없음) */
    amount: number;
    /** 보낼 delta(차감은 음수) */
    delta: number;
    /** 앞뒤 공백을 떼고 연속 공백을 한 칸으로 줄인 사유 */
    reason: string;
}
