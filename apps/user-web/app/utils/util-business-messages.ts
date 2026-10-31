import type { Translate } from '#layers/i18n/utils/util-i18n';
import type { BizHistoryEntry, BusinessRejectCode } from '~/composables/business/api/useBusinessApi.types';
import UtilBusiness from '~/utils/util-business';

/** 신청 건 반려 사유 코드(서버 `BizRejectCode`) */
const REJECT_CODES: readonly BusinessRejectCode[] = ['CREATIVE_SPEC', 'POLICY_VIOLATION', 'LINK_BROKEN', 'PERIOD_UNAVAILABLE', 'FALSE_PRICE', 'OTHER'];

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

    /**
     * 처리 기록 덧붙임 — 서버가 코드별로 남긴 값(반려 사유 코드·금액 숫자·`PERIOD_MOVED`·`BACK_TO_REVIEW`·`EXTENDED:<분>`·`SUSPENDED` 등)을 읽기 좋게.
     * 그 밖(운영팀·내가 적은 사유 글)은 그대로 보인다. 코드 묶음은 서버 `BizEventAction` 의 note 규칙.
     */
    static historyNote(t: Translate, action: BizHistoryEntry['action'], note: string | null): string {
        if (!note) return '';
        const amount = () => (/^-?\d+$/.test(note) ? UtilBusinessMessages.won(t, Number(note)) : note);
        switch (action) {
            case 'REJECTED':
                return (REJECT_CODES as readonly string[]).includes(note) ? t(`business.applicationRejectCode.${note}`) : note;
            case 'APPROVED_ONLINE':
            case 'APPROVED_MANUAL':
                return note
                    .split(' · ')
                    .map((part) => (part === 'PERIOD_MOVED' ? t('business.detail.note.PERIOD_MOVED') : part))
                    .join(' · ');
            case 'CREATIVE_CHANGED':
                return note === 'BACK_TO_REVIEW' ? t('business.detail.note.BACK_TO_REVIEW') : note;
            case 'RESTORED':
                if (note === 'EXTEND_FAILED') return t('business.detail.note.EXTEND_FAILED');
                if (note.startsWith('EXTENDED:')) return t('business.detail.note.EXTENDED', { span: UtilBusinessMessages.minutes(t, Number(note.slice(9)) || 0) });
                return note;
            case 'DEPOSIT_CONFIRMED':
                return note.startsWith('MISMATCH · ') ? t('business.detail.note.MISMATCH', { reason: note.slice(11) }) : note;
            case 'REFUND_APPROVED':
            case 'REFUNDED':
            case 'REFUND_TRANSFERRED':
                return amount();
            case 'REFUND_REQUESTED':
            case 'TAKEN_DOWN':
            case 'REFUND_DENIED':
                return note === 'SUSPENDED' || note === 'RESTORED' ? t(`business.detail.note.${note}`) : note;
            default:
                return note;
        }
    }

    /** 분 → 「2일 3시간」·「3시간 10분」·「45분」 */
    static minutes(t: Translate, total: number): string {
        const d = Math.floor(total / 1440);
        const h = Math.floor((total % 1440) / 60);
        const m = total % 60;
        if (d > 0) return t('business.detail.span.DH', { d, h });
        if (h > 0) return t('business.detail.span.HM', { h, m });
        return t('business.detail.span.M', { m });
    }

    /** 파일 크기 — 「1.2MB」 · 「320KB」 */
    static fileSize(bytes: number): string {
        if (bytes >= 1024 * 1024) return `${(bytes / 1024 / 1024).toFixed(1)}MB`;
        return `${Math.max(1, Math.round(bytes / 1024))}KB`;
    }
}
