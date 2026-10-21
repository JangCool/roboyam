import type { Translate } from '#layers/i18n/utils/util-i18n';
import type { Grade, GradePreviewResponse } from '~/composables/grade/api/useGradeApi.types';
import type { GradeField, GradeProblem } from '~/utils/util-grade';
import UtilGrade from '~/utils/util-grade';

/**
 * 등급 설정 화면과 역할 관리의 "승급 조건" 요약에서 **계산이 낀 문구** — 문구 본문은 `i18n/locales/{ko,en}/grade.ts` 에 있고 여기는 "어느 키를 고를지"만 안다.
 * 번역 함수 `t` 를 **첫 인자로 받는다**(`.claude/guides/frontend/i18n.md`). 서버 오류 문구는 서버 `message` 를 그대로 쓰므로 여기에 없다.
 */
export default class UtilGradeMessages {
    /**
     * 화면이 미리 검사한 칸 문제를 문구로 바꾼다.
     * @param {Translate} t - 번역 함수.
     * @param {GradeProblem} problem - 미리 검사한 문제.
     * @param {'label' | GradeField} field - 칸 이름.
     * @param {{ label: string; value: number } | null} lower - 순서 문제일 때 비교하는 바로 아래 등급의 이름과 값.
     * @returns {string} 입력칸 아래에 보일 한 줄.
     */
    static problem(t: Translate, problem: GradeProblem, field: 'label' | GradeField, lower: { label: string; value: number } | null): string {
        if (problem === 'LABEL_REQUIRED') return t('grade.problemLabelRequired');
        if (problem === 'LABEL_LENGTH') return t('grade.problemLabelLength', { max: UtilGrade.LABEL_MAX });
        if (problem === 'LABEL_DUPLICATE') return t('grade.problemLabelDuplicate');
        if (problem === 'REQUIRED') return t('grade.problemRequired');
        if (problem === 'NOT_INTEGER') return t('grade.problemNotInteger');
        if (problem === 'ORDER') return t(field === 'minDays' ? 'grade.problemOrderDays' : 'grade.problemOrderActivity', { lower: lower?.label ?? '', value: lower ? UtilGrade.format(lower.value) : '' });
        const [min, max] = UtilGrade.limitsOf(field === 'label' ? 'minDays' : field);
        return t('grade.problemRange', { min: UtilGrade.format(min), max: UtilGrade.format(max) });
    }

    /**
     * 저장 전 영향 확인 문구 — 소급으로 지금 승급될 인원(등급별·합계), 지우는 등급의 회원 이동(「단골」 등급을 삭제하면 회원 N명이 「일반」 등급으로 이동돼요. 되돌릴 수 없어요.),
     * 그리고 어떤 일이 있었는지에 따라 시점 안내를 붙인다. 시점은 구현 가정이다: 활동 조건의 소급은 저장 직후 일괄, 일수 조건의 소급은 다음 배치, 이동한 회원도 자동 승급 판정 대상에 계속 포함.
     * @param {Translate} t - 번역 함수.
     * @param {GradePreviewResponse} preview - 미리보기(소급 승급 인원·이동할 회원).
     * @param {Record<GradeField, boolean>} lowered - 일수·글·댓글 중 낮아진 조건.
     * @param {boolean} manualUnlocked - 옵션을 "계속 자동 승급"으로 바꿨는가(수동으로 올린 회원이 새로 승급될 수 있다).
     * @param {boolean} added - 새 등급을 추가했는가.
     * @returns {string} 확인 대화상자 본문(줄바꿈 포함).
     */
    static impactConfirm(t: Translate, preview: GradePreviewResponse, lowered: Record<GradeField, boolean>, manualUnlocked: boolean, added: boolean): string {
        const lines: string[] = [];
        for (const move of preview.willMove) {
            lines.push(move.count > 0 ? t('grade.confirmMoveItem', { from: move.fromLabel, to: move.toLabel, count: UtilGrade.format(move.count) }) : t('grade.confirmRemoveEmpty', { from: move.fromLabel }));
        }
        if (preview.willMove.length) lines.push(t('grade.confirmMoveNote'));
        if (preview.total > 0) {
            const detail = preview.items
                .filter((item) => item.count > 0)
                .map((item) => t('grade.confirmDetailItem', { label: item.label, count: UtilGrade.format(item.count) }))
                .join(' · ');
            lines.push(t('grade.confirmSummary', { total: UtilGrade.format(preview.total), detail }));
            if (lowered.minActivity) lines.push(t('grade.confirmActivityNote'));
            if (lowered.minDays) lines.push(t('grade.confirmDaysNote'));
            if (added) lines.push(t('grade.confirmAddedNote'));
            if (manualUnlocked) lines.push(t('grade.confirmManualNote'));
        }
        return lines.join('\n');
    }

    /**
     * 승급 조건 한 줄 요약 — "가입 후 7일 · 글·댓글 10개 이상". 두 조건이 모두 0 이면 "가입 즉시 (조건 없음)".
     * @param {Translate} t - 번역 함수.
     * @param {Pick<Grade, 'minDays' | 'minActivity'>} rule - 승급 조건.
     * @returns {string} 한 줄.
     */
    static condition(t: Translate, rule: Pick<Grade, 'minDays' | 'minActivity'>): string {
        const parts: string[] = [];
        if (rule.minDays > 0) parts.push(t('grade.conditionDays', { count: UtilGrade.format(rule.minDays) }));
        if (rule.minActivity > 0) parts.push(t('grade.conditionActivity', { count: UtilGrade.format(rule.minActivity) }));
        return parts.length ? parts.join(' · ') : t('grade.noCondition');
    }
}
