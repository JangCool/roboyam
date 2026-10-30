import type { DisplayTime } from '#layers/display-time/utils/util-time';
import type { Translate } from '#layers/i18n/utils/util-i18n';
import type { BizPeriod } from '~/composables/bizapplication/api/useBizApplicationApi.types';
import UtilBizApplication from '~/utils/util-biz-application';

/**
 * 사업자 신청 건·광고 화면의 계산이 낀 문구(i18n.md 3절 — `t` 를 첫 인자로).
 */
export default class UtilBizApplicationMessages {
    /** 반열림 기간 — 「10/3 00:00 ~10/10 00:00」(끝 시각은 포함하지 않는다) */
    static period(t: Translate, time: DisplayTime, period: BizPeriod): string {
        return t('bizApplication.period', { start: time.shortDateTime(period.startAt), end: time.shortDateTime(period.endAt) });
    }

    /** 기한까지 남은 시간 · 지난 시간 — 「2일 3시간 남음」·「5시간 지남」 */
    static remain(t: Translate, deadlineAt: string, now = Date.now()): string {
        const p = UtilBizApplication.remainParts(deadlineAt, now);
        const span = p.days > 0 ? t('bizApplication.spanDH', { d: p.days, h: p.hours }) : p.hours > 0 ? t('bizApplication.spanHM', { h: p.hours, m: p.minutes }) : t('bizApplication.spanM', { m: Math.max(1, p.minutes) });
        return p.overdue ? t('bizApplication.overdue', { span }) : t('bizApplication.left', { span });
    }

    /** 기한 Tag 색 — 지남 = danger, 24시간 안 = warn */
    static remainSeverity(deadlineAt: string, now = Date.now()): 'danger' | 'warn' | 'secondary' {
        const diff = Date.parse(deadlineAt) - now;
        if (diff < 0) return 'danger';
        if (diff < 24 * 3_600_000) return 'warn';
        return 'secondary';
    }

    /** 원 금액 — 「12,000원」 */
    static won(t: Translate, amount: number, locale: string): string {
        return t('bizApplication.won', { amount: UtilBizApplication.won(amount, locale) });
    }
}
