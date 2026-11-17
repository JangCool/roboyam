import type { Translate } from '#layers/i18n/utils/util-i18n';
import type { Grade, GradePace, GradePreviewResponse } from '~/composables/grade/api/useGradeApi.types';
import type { GradeField, GradeProblem } from '~/utils/util-grade';
import UtilGrade from '~/utils/util-grade';

/** 레벨 곡선(지수·기준값)의 변경 — 바뀌었는가, 완만해지는 쪽(지수나 기준값을 내림)인가 */
export interface GradeCurveChange {
    changed: boolean;
    easier: boolean;
}

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
        if (problem === 'ORDER') {
            const key = field === 'minLevel' ? 'grade.problemOrderLevel' : field === 'minDays' ? 'grade.problemOrderDays' : 'grade.problemOrderActivity';
            return t(key, { lower: lower?.label ?? '', value: lower ? UtilGrade.format(lower.value) : '' });
        }
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
     * @param {GradeCurveChange} curve - 레벨 곡선(지수·기준값)이 바뀌었는가, 완만해지는 쪽인가. 바뀌었으면 승급 인원이 없어도 레벨 재계산 안내가 들어간다.
     * @returns {string} 확인 대화상자 본문(줄바꿈 포함).
     */
    static impactConfirm(t: Translate, preview: GradePreviewResponse, lowered: Record<GradeField, boolean>, manualUnlocked: boolean, added: boolean, curve: GradeCurveChange): string {
        const lines: string[] = [];
        for (const move of preview.willMove) {
            lines.push(move.count > 0 ? t('grade.confirmMoveItem', { from: move.fromLabel, to: move.toLabel, count: UtilGrade.format(move.count) }) : t('grade.confirmRemoveEmpty', { from: move.fromLabel }));
        }
        if (preview.willMove.length) lines.push(t('grade.confirmMoveNote'));
        if (curve.changed) lines.push(UtilGradeMessages.curveWarning(t, curve));
        if (preview.total > 0) {
            const detail = preview.items
                .filter((item) => item.count > 0)
                .map((item) => t('grade.confirmDetailItem', { label: item.label, count: UtilGrade.format(item.count) }))
                .join(' · ');
            lines.push(t('grade.confirmSummary', { total: UtilGrade.format(preview.total), detail }));
            if (lowered.minLevel || curve.changed) lines.push(t('grade.confirmLevelNote'));
            if (lowered.minActivity) lines.push(t('grade.confirmActivityNote'));
            if (lowered.minDays) lines.push(t('grade.confirmDaysNote'));
            if (added) lines.push(t('grade.confirmAddedNote'));
            if (manualUnlocked) lines.push(t('grade.confirmManualNote'));
        } else if (curve.changed) {
            lines.push(t('grade.confirmCurveNoPromotion'));
        }
        return lines.join('\n');
    }

    /**
     * 곡선을 바꿨을 때의 경고 — 레벨 재계산·등급은 안 내려감, 완만해지는 쪽이면 승급이 일어나고 되돌아가지 않는다는 문장을 잇는다. 카드 아래 상자와 저장 확인 창이 같이 쓴다.
     * @param {Translate} t - 번역 함수.
     * @param {GradeCurveChange} curve - 곡선 변경.
     * @returns {string} 한 문단. 바뀌지 않았으면 빈 글자.
     */
    static curveWarning(t: Translate, curve: GradeCurveChange): string {
        if (!curve.changed) return '';
        return curve.easier ? `${t('grade.curveChangeWarn')} ${t('grade.curveChangeWarnEasier')}` : t('grade.curveChangeWarn');
    }

    /**
     * 레벨 곡선 안내 한 줄 — "필요한 누적 경험치 — Lv.10 810 · Lv.50 24,010 · Lv.100 98,010". 기준값이 맞지 않으면 빈 글자(칸 아래에 오류가 대신 선다).
     * @param {Translate} t - 번역 함수.
     * @param {string} curveText - 곡선 기준값 입력 글자.
     * @param {number} exponent - 곡선 지수.
     * @returns {string} 한 줄.
     */
    static curveSample(t: Translate, curveText: string, exponent: number): string {
        if (UtilGrade.curveProblem(curveText)) return '';
        const base = UtilGrade.parse(curveText)!;
        const items = UtilGrade.CURVE_SAMPLE_LEVELS.map((level) => t('grade.curveSampleItem', { level, xp: UtilGrade.format(UtilGrade.xpForLevel(base, level, exponent)) })).join(' · ');
        return t('grade.curveSample', { items });
    }

    /**
     * 등급별 미리보기의 안내 한 줄 — 「보통 = 출석 + 글 1개 + 댓글 3개(지금 하루 45) · 한도 = 활동 적립 하루 총 한도(지금 200)」. 한도가 0 이면 「지금 한도 없음」, 값을 모르면(옛 응답) 괄호 없이.
     * @param {Translate} t - 번역 함수.
     * @param {GradePace | null} pace - 하루 적립 값. 모르면 null.
     * @returns {string} 한 줄(링크 글자는 화면이 따로 붙인다).
     */
    static paceLead(t: Translate, pace: GradePace | null): string {
        if (!pace) return `${t('grade.paceTypicalUnknown')} · ${t('grade.paceCapUnknown')}`;
        const cap = pace.dailyTotalCap > 0 ? t('grade.paceCap', { cap: UtilGrade.format(pace.dailyTotalCap) }) : t('grade.paceCapNone');
        return `${t('grade.paceTypical', { typical: UtilGrade.format(pace.typicalDaily) })} · ${cap}`;
    }

    /**
     * 걸리는 날 글자 — 60일 미만 「N일」, 730일 미만 「N개월」(일 ÷ 30.4 반올림), 그 이상 「N.N년」(일 ÷ 365, 소수 한 자리).
     * @param {Translate} t - 번역 함수.
     * @param {number} days - 올림한 날 수.
     * @returns {string} 글자.
     */
    static duration(t: Translate, days: number): string {
        if (days < 60) return t('grade.paceDays', { n: UtilGrade.format(days) });
        if (days < 730) return t('grade.paceMonths', { n: Math.round(days / 30.4) });
        return t('grade.paceYears', { n: (Math.round(days / 36.5) / 10).toLocaleString('en-US', { minimumFractionDigits: 1, maximumFractionDigits: 1 }) });
    }

    /**
     * 승급 조건 한 줄 요약 — "Lv.10 이상 · 가입 후 7일 · 글·댓글 10개 이상". 조건이 모두 없으면(레벨 1 이하·0·0) "가입 즉시 (조건 없음)".
     * @param {Translate} t - 번역 함수.
     * @param {Pick<Grade, 'minDays' | 'minActivity' | 'minLevel'>} rule - 승급 조건(최소 레벨은 옛 응답에 없을 수 있다).
     * @returns {string} 한 줄.
     */
    static condition(t: Translate, rule: Pick<Grade, 'minDays' | 'minActivity' | 'minLevel'>): string {
        const parts: string[] = [];
        if ((rule.minLevel ?? 1) > 1) parts.push(t('grade.conditionLevel', { level: UtilGrade.format(rule.minLevel ?? 1) }));
        if (rule.minDays > 0) parts.push(t('grade.conditionDays', { count: UtilGrade.format(rule.minDays) }));
        if (rule.minActivity > 0) parts.push(t('grade.conditionActivity', { count: UtilGrade.format(rule.minActivity) }));
        return parts.length ? parts.join(' · ') : t('grade.noCondition');
    }
}
