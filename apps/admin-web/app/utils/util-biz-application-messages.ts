import type { DisplayTime } from '#layers/display-time/utils/util-time';
import type { Translate } from '#layers/i18n/utils/util-i18n';
import type { BizPauseChannel, BizPeriod } from '~/composables/bizapplication/api/useBizApplicationApi.types';
import { BIZ_DEAL_FAIL_REASONS, BIZ_HISTORY_ACTIONS, BIZ_PAUSE_CHANNELS, BIZ_REJECT_CODES } from '~/composables/bizapplication/api/useBizApplicationApi.types';
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

    /** 처리 기록 코드 → 문구(서버 `BizEventAction`). 모르는 코드는 그대로 */
    static historyAction(t: Translate, action: string): string {
        return (BIZ_HISTORY_ACTIONS as readonly string[]).includes(action) ? t(`bizApplication.action.${action}`) : action;
    }

    /**
     * 처리 기록 덧붙임 — 서버가 코드별로 남긴 값(사유 코드·금액 숫자·`PERIOD_MOVED`·`BACK_TO_REVIEW`·`EXTENDED:<분>`·`SUSPENDED` 등)을 읽기 좋게.
     * 그 밖(운영자·사업자가 적은 사유 글)은 그대로 보인다.
     */
    static historyNote(t: Translate, action: string, note: string | null, locale: string): string {
        if (!note) return '';
        const code = (v: string, group: string, codes: readonly string[]) => (codes.includes(v) ? t(`bizApplication.${group}.${v}`) : v);
        const amount = () => (/^-?\d+$/.test(note) ? UtilBizApplicationMessages.won(t, Number(note), locale) : note);
        const advertiser = (v: string) => v === 'ADVERTISER_PAUSE' || v === 'ADVERTISER_STOP';
        switch (action) {
            case 'PAUSED':
            case 'RESUMED':
            case 'STOPPED':
                // 운영자 대신 처리 `PHONE|EMAIL|OTHER · 메모` · 정지 중 전환 `FROM_PAUSE` · 다시 켜기 `MIN:<멈춘 분>`(biz-pause-contract)
                return note
                    .split(' · ')
                    .map((part) => {
                        if ((BIZ_PAUSE_CHANNELS as readonly string[]).includes(part)) return t(`bizApplication.pauseChannel.${part}`);
                        if (part === 'FROM_PAUSE') return t('bizApplication.note.FROM_PAUSE');
                        if (part.startsWith('MIN:')) return t('bizApplication.note.PAUSED_FOR', { span: UtilBizApplicationMessages.minutes(t, Number(part.slice(4)) || 0) });
                        return part;
                    })
                    .join(' · ');
            case 'POST_HIDDEN':
            case 'POST_RESTORED':
                return advertiser(note) ? t(`bizApplication.post.hiddenCause.${note}`) : note;
            case 'POST_FAILED':
                return code(note, 'post.failReason', BIZ_DEAL_FAIL_REASONS);
            case 'POSTED_WITHOUT_PREFIX':
                return note === 'CATEGORY_TOP_OFF' ? t('bizApplication.note.CATEGORY_TOP_OFF') : note;
            case 'REJECTED':
                return code(note, 'rejectCode', BIZ_REJECT_CODES);
            case 'APPROVED_ONLINE':
            case 'APPROVED_MANUAL':
                return note
                    .split(' · ')
                    .map((part) => (part === 'PERIOD_MOVED' || part === 'SELF_APPROVED' ? t(`bizApplication.note.${part}`) : part))
                    .join(' · ');
            case 'CREATIVE_CHANGED':
                return note === 'BACK_TO_REVIEW' ? t('bizApplication.note.BACK_TO_REVIEW') : note;
            case 'RESTORED': {
                if (note === 'EXTEND_FAILED') return t('bizApplication.note.EXTEND_FAILED');
                // 정지 해제 `EXTENDED:<분>` · 긴급 내림 되돌리기 `EXTENDED:<분> · 사유` / `REFUND:<원> · 사유`(서버 `AdOperatorService.restore`)
                const [head = '', ...rest] = note.split(' · ');
                let lead = head;
                if (head.startsWith('EXTENDED:')) lead = t('bizApplication.note.EXTENDED', { span: UtilBizApplicationMessages.minutes(t, Number(head.slice(9)) || 0) });
                else if (head.startsWith('REFUND:')) lead = t('bizApplication.note.REFUND', { amount: UtilBizApplicationMessages.won(t, Number(head.slice(7)) || 0, locale) });
                return [lead, ...rest].join(' · ');
            }
            case 'DEPOSIT_CONFIRMED':
                return note.startsWith('MISMATCH · ') ? t('bizApplication.note.MISMATCH', { reason: note.slice(11) }) : note;
            case 'REFUND_APPROVED':
            case 'REFUNDED':
            case 'REFUND_TRANSFERRED':
            case 'CLAIM_REFUNDED':
                return amount();
            case 'TAKEN_DOWN':
                if (note === 'EMERGENCY' || advertiser(note)) return t(`bizApplication.takedownCause.${note}`);
                return note === 'SUSPENDED' || note === 'RESTORED' ? t(`bizApplication.note.${note}`) : note;
            case 'REFUND_REQUESTED':
            case 'REFUND_DENIED':
                if (advertiser(note)) return t(`bizApplication.takedownCause.${note}`);
                return note === 'SUSPENDED' || note === 'RESTORED' ? t(`bizApplication.note.${note}`) : note;
            default:
                return note;
        }
    }

    /**
     * 광고주 요청 정지·중단을 건 사람 — 사업자가 직접: 「{이름} · 사업자가 직접」 / 운영자 대신: 「{이름} · 운영자 대신(전화 요청)」 + 메모(관리자에게만).
     * 운영자 대신 처리는 요청 경로(`channel`)가 있는 것으로 가른다.
     */
    static pauseWho(t: Translate, by: string | null | undefined, channel: BizPauseChannel | null | undefined, memo?: string | null): string {
        const name = by || '—';
        const head = channel ? t('bizApplication.pause.whoOperator', { name, channel: t(`bizApplication.pauseChannel.${channel}`) }) : t('bizApplication.pause.whoBusiness', { name });
        return memo ? `${head} — ${memo}` : head;
    }

    /**
     * 중단 환불 내역 한 줄 — 「일할 {a} − 중도 해지 수수료({rate}%) {fee} = 환불 예상액 {b}」. 수수료율 0%(옛 규정 건)면 「일할 {a} · 수수료 없음」.
     */
    static stopBreakdown(t: Translate, prorated: number, feeRate: number, fee: number, amount: number, locale: string): string {
        const w = (n: number) => UtilBizApplicationMessages.won(t, n, locale);
        if (feeRate <= 0) return t('bizApplication.stopFee.breakdownNoFee', { prorated: w(prorated) });
        return t('bizApplication.stopFee.breakdown', { prorated: w(prorated), rate: feeRate, fee: w(fee), amount: w(amount) });
    }

    /** 분 → 「2일 3시간」·「3시간 10분」·「45분」 */
    static minutes(t: Translate, total: number): string {
        const d = Math.floor(total / 1440);
        const h = Math.floor((total % 1440) / 60);
        const m = total % 60;
        if (d > 0) return t('bizApplication.spanDH', { d, h });
        if (h > 0) return t('bizApplication.spanHM', { h, m });
        return t('bizApplication.spanM', { m });
    }
}
