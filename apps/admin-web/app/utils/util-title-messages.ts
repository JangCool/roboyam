import type { Translate } from '#layers/i18n/utils/util-i18n';
import type { TitleTier } from '#layers/member-title/utils/util-title-icon';
import type { TitleCondition } from '~/composables/title/api/useTitleApi.types';
import UtilTitle, { type TitleFormProblem } from '~/utils/util-title';

/**
 * 회원 칭호 관리 화면의 계산이 낀 문구 — 문구 본문은 `i18n/locales/{ko,en}/memberTitle.ts`. 칭호 이름·신청 조건 글자는 서버 값을 그대로 쓴다.
 */
export default class UtilTitleMessages {
    /**
     * 조건 한 줄 — 서버 `label` 이 있으면 그것, 없으면(편집 중 새 조건) 「정식 이상」·「사용기 게시판 글 3편」·「인기글 진입 10일」·「출석 100일」·「신고로 숨겨진 글 없음」.
     * @param {Translate} t - 번역 함수.
     * @param {TitleCondition} c - 조건.
     * @param {(key: string) => string} gradeName - 등급 키 → 이름(모르면 키).
     * @param {(id: string) => string} boardName - 게시판 ID → 이름(모르면 ID).
     * @returns {string} 글자.
     */
    static condition(t: Translate, c: TitleCondition, gradeName: (key: string) => string, boardName: (id: string) => string): string {
        // 서버가 요청 언어로 만든 한 줄이 있으면 그것(응답 `ConditionView.label`)
        if (c.label) return c.label;
        const n = c.value ?? 0;
        if (c.type === 'GRADE_AT_LEAST') return t('memberTitle.cond.gradeAtLeast', { grade: c.gradeKey ? gradeName(c.gradeKey) : '—' });
        if (c.type === 'POSTS') return c.boardId ? t('memberTitle.cond.postsIn', { board: boardName(c.boardId), n }) : c.boardSlug ? t('memberTitle.cond.postsIn', { board: c.boardSlug, n }) : t('memberTitle.cond.posts', { n });
        if (c.type === 'POPULAR_DAYS') return t('memberTitle.cond.popularDays', { n });
        if (c.type === 'ATTENDANCE_DAYS') return t('memberTitle.cond.attendanceDays', { n });
        return t('memberTitle.cond.noReportHidden');
    }

    /**
     * 단계 이름 — 동·은·금(단계 없으면 빈 글자).
     * @param {Translate} t - 번역 함수.
     * @param {TitleTier | null} tier - 단계.
     * @returns {string} 글자.
     */
    static tier(t: Translate, tier: TitleTier | null): string {
        return tier ? t(`memberTitle.tier.${tier}`) : '';
    }

    /**
     * 받은 기간 — 「2026년 10월」·「2025년」(모양이 다르면 그대로, 없으면 빈 글자).
     * @param {Translate} t - 번역 함수.
     * @param {string | null} period - `YYYY-MM`·`YYYY`.
     * @returns {string} 글자.
     */
    static period(t: Translate, period: string | null): string {
        if (!period) return '';
        const m = /^(\d{4})(?:-(\d{2}))?$/.exec(period);
        if (!m) return period;
        return m[2] ? t('memberTitle.periodMonth', { year: m[1], month: Number(m[2]) }) : t('memberTitle.periodYear', { year: m[1] });
    }

    /**
     * 편집 서랍 입력 문제 → 칸 아래 문구.
     * @param {Translate} t - 번역 함수.
     * @param {TitleFormProblem | undefined} problem - 문제.
     * @returns {string} 문구(문제 없으면 빈 글자).
     */
    static problem(t: Translate, problem: TitleFormProblem | undefined): string {
        if (!problem) return '';
        if (problem === 'NAME_TOO_LONG') return t('memberTitle.problem.NAME_TOO_LONG', { max: UtilTitle.NAME_MAX });
        return t(`memberTitle.problem.${problem}`);
    }
}
