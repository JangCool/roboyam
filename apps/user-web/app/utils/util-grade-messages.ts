import type { Translate } from '#layers/i18n/utils/util-i18n';
import type { AutoPromotion, GradeRequirement, GradeRule } from '~/composables/grade/api/useGradeApi.types';

/** 제목 + 설명 */
export interface GradeCopy {
    title: string;
    text: string;
}

/**
 * 등급 화면의 계산이 낀 문구 — 조건 글자·진행 글자·자동 승급이 멈춘 안내. 문구 본문은 `i18n/locales/{ko,en}/grade.ts`.
 * 원본(community) `UtilGradeMessages` 에서 인기글 조건·닉네임 이미지·공식 뱃지 안내·등급 알림 문구를 뺐다(로보얌에 없다).
 */
export default class UtilGradeMessages {
    /**
     * 조건표 한 줄 — 「가입 7일 + 글·댓글 합 10개」, 첫 등급은 「가입 즉시」
     * @param {Translate} t - 번역 함수.
     * @param {GradeRule} rule - 등급 조건.
     * @returns {string} 글자.
     */
    static ruleText(t: Translate, rule: GradeRule): string {
        const parts: string[] = [];
        if (rule.minDays > 0) parts.push(t('grade.ruleDays', { n: rule.minDays }));
        if (rule.minActivity > 0) parts.push(t('grade.ruleActivity', { n: rule.minActivity }));
        return parts.length ? parts.join(' + ') : t('grade.ruleInstant');
    }

    /**
     * 진행 막대 왼쪽의 조건 이름 — 「가입 30일」「글·댓글 50개」
     * @param {Translate} t - 번역 함수.
     * @param {GradeRequirement} r - 조건.
     * @returns {string} 글자.
     */
    static requirementLabel(t: Translate, r: GradeRequirement): string {
        if (r.kind === 'DAYS') return t('grade.reqDays', { n: r.required });
        if (r.kind === 'ACTIVITY') return t('grade.reqActivity', { n: r.required });
        return t('grade.reqOther', { n: r.required });
    }

    /**
     * 진행 막대 오른쪽 글자 — 「12일째 (18일 남음)」「31개 (19개 남음)」, 채웠으면 남은 수 없이
     * @param {Translate} t - 번역 함수.
     * @param {GradeRequirement} r - 조건.
     * @returns {string} 글자.
     */
    static progressText(t: Translate, r: GradeRequirement): string {
        const n = Math.max(0, Math.floor(r.current));
        const left = Math.max(0, Math.floor(r.required) - n);
        const remaining = left > 0 && !r.met;
        if (r.kind === 'DAYS') return remaining ? t('grade.progressDaysLeft', { n, left }) : t('grade.progressDays', { n });
        return remaining ? t('grade.progressCountLeft', { n, left }) : t('grade.progressCount', { n });
    }

    /**
     * 자동 승급이 멈춘 안내 — 정지 중 보류 · 운영자 고정. 운영자가 적은 사유는 보이지 않는다
     * @param {Translate} t - 번역 함수.
     * @param {AutoPromotion | string | null | undefined} state - 자동 승급 상태.
     * @returns {GradeCopy | null} 안내. 정상이면 null.
     */
    static pausedNotice(t: Translate, state: AutoPromotion | string | null | undefined): GradeCopy | null {
        if (state === 'PAUSED_SANCTION') return { title: t('grade.pausedSanctionTitle'), text: t('grade.pausedSanctionText') };
        if (state === 'PAUSED_ADMIN') return { title: t('grade.pausedAdminTitle'), text: t('grade.pausedAdminText') };
        return null;
    }
}
