import type { Translate } from '#layers/i18n/utils/util-i18n';
import UtilBusiness from '~/utils/util-business';

/**
 * 사업자 화면의 계산이 낀 문구 — 상수 문구는 컴포넌트가 `t('business.…')` 로 바로 읽는다(`i18n/locales/{ko,en}/business.ts`).
 */
export default class UtilBusinessMessages {
    static readonly ENTRY_CENTER_ICON = 'pi pi-briefcase';

    /** 금액 — 「12,300원」 */
    static won(t: Translate, amount: number): string {
        return t('payment.won', { amount: amount.toLocaleString('en-US') });
    }

    /**
     * 남은 시간 — 「2일 05:12:09」 · 「05:12:09」, 지났으면 「기한 지남」.
     * @param {Translate} t - 번역 함수.
     * @param {number} ms - 남은 밀리초.
     * @returns {string} 글자.
     */
    static remaining(t: Translate, ms: number): string {
        if (ms <= 0) return t('business.deadline.over');
        const total = Math.floor(ms / 1000);
        const days = Math.floor(total / 86400);
        const pad = (n: number) => String(n).padStart(2, '0');
        const clock = `${pad(Math.floor((total % 86400) / 3600))}:${pad(Math.floor((total % 3600) / 60))}:${pad(total % 60)}`;
        return days > 0 ? t('business.deadline.daysClock', { days, clock }) : clock;
    }

    /** 기간 — 「10.31 ~ 11.06 · 7일」(날짜 글자는 부르는 쪽이 표시 형식으로 만든다) */
    static period(t: Translate, startLabel: string, endLabel: string, start: string, end: string): string {
        return t('business.period', { start: startLabel, end: endLabel, days: UtilBusiness.daysBetween(start, end) });
    }

    /** 파일 크기 — 「1.2MB」 · 「320KB」 */
    static fileSize(bytes: number): string {
        if (bytes >= 1024 * 1024) return `${(bytes / 1024 / 1024).toFixed(1)}MB`;
        return `${Math.max(1, Math.round(bytes / 1024))}KB`;
    }
}
