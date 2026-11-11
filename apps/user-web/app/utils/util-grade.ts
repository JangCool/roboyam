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

/** 레벨 진행 — 프로필·내 정보의 경험치 막대(grade-level-contract 4절) */
export interface LevelProgress {
    level: number;
    xp: number;
    /** 이 레벨 안에서 채운 비율(0~100, 내림) — 막대 너비 */
    percent: number;
    /** 다음 레벨까지 남은 비율(100 - percent) — 「다음 레벨까지 60%」 */
    remainingPercent: number;
    /** 다음 레벨까지 남은 XP */
    left: number;
}

/** 레벨 칸 — 로그인 정보·공개 프로필·내 등급이 같이 준다 */
export interface LevelFields {
    level?: number | null;
    xp?: number | null;
    levelXp?: number | null;
    nextLevelXp?: number | null;
}

export default class UtilGrade {
    /**
     * 레벨 진행 — 레벨이 없으면(옛 응답·남의 비공개 프로필) null(화면은 레벨 표시를 숨긴다). XP 구간이 없거나 틀리면 막대는 0%.
     * @param {LevelFields | null | undefined} fields - 서버가 준 레벨 칸.
     * @returns {LevelProgress | null} 진행. 레벨이 없으면 null.
     */
    static levelProgress(fields: LevelFields | null | undefined): LevelProgress | null {
        const level = fields?.level;
        if (typeof level !== 'number' || !Number.isFinite(level) || level < 1) return null;
        const xp = typeof fields?.xp === 'number' && Number.isFinite(fields.xp) ? Math.max(0, fields.xp) : 0;
        const start = typeof fields?.levelXp === 'number' && Number.isFinite(fields.levelXp) ? fields.levelXp : null;
        const next = typeof fields?.nextLevelXp === 'number' && Number.isFinite(fields.nextLevelXp) ? fields.nextLevelXp : null;
        const span = start !== null && next !== null ? next - start : 0;
        const percent = span > 0 ? Math.min(100, Math.max(0, Math.floor(((xp - start!) / span) * 100))) : 0;
        const left = next !== null ? Math.max(0, Math.ceil(next - xp)) : 0;
        return { level: Math.floor(level), xp, percent, remainingPercent: 100 - percent, left };
    }

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
