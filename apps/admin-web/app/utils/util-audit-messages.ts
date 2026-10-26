import type { Translate } from '#layers/i18n/utils/util-i18n';
import type { AuditRetentionItem } from '~/composables/audit/api/useAuditRetentionApi.types';
import type { AuditRetentionProblem } from '~/utils/util-audit';
import UtilAudit from '~/utils/util-audit';

/** 감사 로그 화면의 계산이 낀 문구 — 상수 문구는 컴포넌트가 `t('audit.…')` 로 바로 부른다(i18n.md 3). */
export default class UtilAuditMessages {
    /**
     * 보관 일수를 사람이 읽기 쉬운 길이로 — 1년(365일) 이상은 「약 N년」(소수 첫째 자리까지, .0 은 뗀다), 그보다 짧으면 30일 이상 「약 N개월」, 나머지는 「N일」.
     * @param {Translate} t - 번역 함수.
     * @param {number | null} days - 일수. 읽을 수 없으면 null.
     * @returns {string} 환산 글. 읽을 수 없으면 빈 글.
     */
    static span(t: Translate, days: number | null): string {
        if (days === null || days <= 0) return '';
        if (days >= UtilAudit.DAYS_PER_YEAR) {
            const years = Math.round((days / UtilAudit.DAYS_PER_YEAR) * 10) / 10;
            return t('audit.retentionAboutYears', { years: String(years) });
        }
        if (days >= 30) return t('audit.retentionAboutMonths', { months: Math.round(days / 30) });
        return t('audit.retentionDays', { days });
    }

    /**
     * 보관 일수 칸의 오류 한 줄.
     * @param {Translate} t - 번역 함수.
     * @param {AuditRetentionProblem} problem - 문제.
     * @param {AuditRetentionItem} item - 분류(하한·상한).
     * @returns {string} 칸 아래에 보일 글.
     */
    static problem(t: Translate, problem: AuditRetentionProblem, item: Pick<AuditRetentionItem, 'minDays' | 'maxDays'>): string {
        if (problem === 'REQUIRED') return t('audit.retentionRequired');
        if (problem === 'NOT_INTEGER') return t('audit.retentionNotInteger');
        if (problem === 'BELOW_MIN') return t('audit.retentionBelowMin', { days: item.minDays });
        return t('audit.retentionAboveMax', { days: item.maxDays });
    }
}
