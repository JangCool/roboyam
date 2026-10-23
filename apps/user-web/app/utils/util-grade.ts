import type { AutoPromotion, GradeRequirement } from '~/composables/grade/api/useGradeApi.types';

/**
 * 등급 진행도 계산 — 막대의 값을 필요 값에서 자른다(100% 를 넘지 않는다). 상태도 요청도 없다. 문구는 `UtilGradeMessages`.
 * 수치(가입 일수·활동 수)는 운영자 설정이라 서버가 준 값으로만 그린다(화면에 숫자를 박지 않는다). 서버 판정은 `GradePromotionService`(관리자 회원 상세 `MemberGradePointAssembler` 와 같은 셈).
 */

/** 막대 하나 */
export interface RequirementBar {
    shown: number;
    required: number;
    percent: number;
    met: boolean;
}

export default class UtilGrade {
    /**
     * 조건 하나의 막대 — 현재 값은 서버가 상한 없이 주므로 필요 값에서 자른다. 필요 값이 0 이면 100%
     * @param {GradeRequirement} requirement - 조건.
     * @returns {RequirementBar} 막대 값.
     */
    static bar(requirement: GradeRequirement): RequirementBar {
        const required = Math.max(0, Number.isFinite(requirement.required) ? requirement.required : 0);
        const current = Math.max(0, Number.isFinite(requirement.current) ? requirement.current : 0);
        const shown = Math.min(current, required);
        const percent = required === 0 ? 100 : Math.floor((shown / required) * 100);
        return { shown, required, percent, met: requirement.met === true };
    }

    /**
     * 자동 승급이 멈춰 있는가 — 정지 중 보류 · 운영자 고정
     * @param {AutoPromotion | string | null | undefined} state - 자동 승급 상태.
     * @returns {boolean} 멈춰 있으면 true.
     */
    static isPaused(state: AutoPromotion | string | null | undefined): boolean {
        return state === 'PAUSED_SANCTION' || state === 'PAUSED_ADMIN';
    }
}
