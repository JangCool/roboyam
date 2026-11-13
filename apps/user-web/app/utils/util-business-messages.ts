import type { Translate } from '#layers/i18n/utils/util-i18n';
import type { BizDealFailReason, BizHistoryEntry, BizScheduledPriceChange, BusinessRejectCode } from '~/composables/business/api/useBusinessApi.types';
import UtilBusiness from '~/utils/util-business';

/** 단가 단위 — `daily` 하루(광고·핫딜 노출) · `perPost` 게시 1건(핫딜 글) */
export type BizPriceUnit = 'daily' | 'perPost';

/** 지금 요금(예약 변경과 비교할 값) — 화면이 모르는 일수는 뺀다 */
export interface BizPriceRuleNow {
    price: number;
    maxDays?: number;
    maxLeadDays?: number;
}

/** 상품 카드의 예약 변경 안내 한 줄 — `direction` 은 단가가 오르나(`UP`)·내리나(`DOWN`), 단가가 그대로면 null */
export interface BizPriceChangeNote {
    direction: 'UP' | 'DOWN' | null;
    text: string;
}

/** 예상 금액 옆 안내에 넣을 고른 상품 하나 */
export interface BizPriceChangeItem {
    /** 상품 이름(「넓은 배너」·「핫딜 글」) */
    label: string;
    unit: BizPriceUnit;
    /** 지금 단가 */
    price: number;
    change: BizScheduledPriceChange;
}

/** 예상 금액 옆 안내 — 본문 한 문장 + 바뀌는 단가 줄들 */
export interface BizPriceChangeSummary {
    text: string;
    lines: string[];
}

/** 신청 건 반려 사유 코드(서버 `BizRejectCode`) */
const REJECT_CODES: readonly BusinessRejectCode[] = ['CREATIVE_SPEC', 'POLICY_VIOLATION', 'LINK_BROKEN', 'PERIOD_UNAVAILABLE', 'FALSE_PRICE', 'OTHER'];
/** 사업자 핫딜 게시 실패 사유(2차) — 처리 기록 `POST_FAILED` 의 note */
const FAIL_REASONS: readonly BizDealFailReason[] = ['BOARD_CLOSED', 'LINK_BLOCKED', 'BUSINESS_SUSPENDED', 'SERVICE_SUSPENDED', 'OTHER'];

/**
 * 사업자 화면의 계산이 낀 문구 — 상수 문구는 컴포넌트가 `t('business.…')` 로 바로 읽는다(`i18n/locales/{ko,en}/business.ts`).
 */
export default class UtilBusinessMessages {
    static readonly ENTRY_CENTER_ICON = 'pi pi-briefcase';

    /** 금액 — 「12,300원」 */
    static won(t: Translate, amount: number): string {
        return t('payment.won', { amount: amount.toLocaleString('en-US') });
    }

    /** 신청 화면의 환불 규정 동의 풀이 — 진행 중 중단 수수료율(`GET /api/business/refund-policy`)을 받았으면 넣고, 못 받았으면 비율 없는 문구 */
    static refundPolicyDetail(t: Translate, stopFeeRate: number | null): string {
        return stopFeeRate === null ? t('business.agree.refundPolicyDetailNoRate') : t('business.agree.refundPolicyDetail', { rate: stopFeeRate });
    }

    /**
     * 중단 환불 내역 한 줄 — 「일할 금액 {a} − 중도 해지 수수료({rate}%) {fee} = 환불 예상액 {b}」. 수수료율 0%(옛 규정 건)면 수수료 없이 「남은 기간 일할 {a}」.
     * 서버가 준 환불 줄(`refund.proratedAmount`·`feeRate`·`feeAmount`·`proposedAmount`)이 있으면 그 값을, 요청 전이면 화면 계산(`UtilBusiness.stopRefund`)을 넣는다.
     */
    static stopBreakdown(t: Translate, prorated: number, feeRate: number, fee: number, amount: number): string {
        const w = (n: number) => UtilBusinessMessages.won(t, n);
        if (feeRate <= 0) return t('business.pause.breakdownNoFee', { prorated: w(prorated) });
        return t('business.pause.breakdown', { prorated: w(prorated), rate: feeRate, fee: w(fee), amount: w(amount) });
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

    /**
     * 상품 카드의 요금 예약 변경 안내 한 줄 — 단가가 바뀌면 「6월 22일 (월) 00:00부터 하루 60,000원으로 올라요」(최대 일수도 바뀌면 뒤에 덧붙임),
     * 단가는 그대로고 최대 일수만이면 「… 최대 신청 일수가 14일로 바뀌어요」, 미리 신청 일수만이면 그 안내. 바뀌는 것이 없으면 null.
     * 오름·내림은 문구(「올라요」·「내려요」)와 아이콘으로 알린다(색만으로 가르지 않는다). ⚠ `scheduledChange` 는 가정(서버 미구현)이다.
     * @param {Translate} t - 번역 함수.
     * @param {string} when - 적용 시각 글자(부르는 쪽이 표시 형식으로 — `time.slot(effectiveAt)`).
     * @param {BizPriceUnit} unit - 단가 단위(`daily` 하루 · `perPost` 게시 1건).
     * @param {BizPriceRuleNow} current - 지금 값(최대·미리 신청 일수를 화면이 모르면 빼면 그 비교를 건너뛴다).
     * @param {BizScheduledPriceChange} change - 예약 변경.
     * @returns {BizPriceChangeNote | null} 안내 한 줄 또는 null.
     */
    static priceChangeNote(t: Translate, when: string, unit: BizPriceUnit, current: BizPriceRuleNow, change: BizScheduledPriceChange): BizPriceChangeNote | null {
        const daysChanged = current.maxDays !== undefined && change.maxDays !== current.maxDays;
        if (change.unitPrice !== current.price) {
            const up = change.unitPrice > current.price;
            const price = UtilBusinessMessages.unitPrice(t, unit, change.unitPrice);
            const head = up ? t('business.priceChange.up', { when, price }) : t('business.priceChange.down', { when, price });
            return { direction: up ? 'UP' : 'DOWN', text: daysChanged ? `${head} · ${t('business.priceChange.alsoMaxDays', { days: change.maxDays })}` : head };
        }
        if (daysChanged) return { direction: null, text: t('business.priceChange.maxDays', { when, days: change.maxDays }) };
        if (current.maxLeadDays !== undefined && change.maxLeadDays !== current.maxLeadDays) return { direction: null, text: t('business.priceChange.leadDays', { when, days: change.maxLeadDays }) };
        return null;
    }

    /**
     * 예상 금액 옆 안내 — 고른 상품 중 **단가가** 바뀔 예정인 것만(일수만 바뀌는 것은 금액과 무관해 뺀다). 없으면 null.
     * 적용 시각이 모두 같으면 「지금 신청하면 지금 가격 … {when} 이후 신청부터 새 가격」, 서로 다르면 시각 없는 문장 + 줄마다 시각.
     * @param {Translate} t - 번역 함수.
     * @param {BizPriceChangeItem[]} items - 고른 상품들(이름·단위·지금 단가·예약 변경).
     * @param {(iso: string) => string} whenOf - 적용 시각 글자 만들기(`time.slot`).
     * @returns {BizPriceChangeSummary | null} 안내 또는 null.
     */
    static priceChangeSummary(t: Translate, items: BizPriceChangeItem[], whenOf: (iso: string) => string): BizPriceChangeSummary | null {
        const changed = items.filter((i) => i.change.unitPrice !== i.price);
        if (!changed.length) return null;
        const single = new Set(changed.map((i) => Date.parse(i.change.effectiveAt))).size === 1;
        const lines = changed.map((i) => {
            const params = { label: i.label, from: UtilBusinessMessages.unitPrice(t, i.unit, i.price), to: UtilBusinessMessages.unitPrice(t, i.unit, i.change.unitPrice) };
            const line = i.change.unitPrice > i.price ? t('business.priceChange.lineUp', params) : t('business.priceChange.lineDown', params);
            return single ? line : t('business.priceChange.lineWhen', { line, when: whenOf(i.change.effectiveAt) });
        });
        const text = single ? t('business.priceChange.summaryText', { when: whenOf(changed[0]!.change.effectiveAt) }) : t('business.priceChange.summaryTextMulti');
        return { text, lines };
    }

    /** 단가 글자 — 「하루 60,000원」 · 「게시 1건 20,000원」 */
    static unitPrice(t: Translate, unit: BizPriceUnit, amount: number): string {
        const won = UtilBusinessMessages.won(t, amount);
        return unit === 'perPost' ? t('business.price.perPost', { amount: won }) : t('business.price.daily', { amount: won });
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
            case 'CLAIM_REFUNDED':
                return amount();
            case 'POST_FAILED':
                return (FAIL_REASONS as readonly string[]).includes(note) ? t(`business.post.failReason.${note}`) : note;
            case 'POSTED_WITHOUT_PREFIX':
                return note === 'CATEGORY_TOP_OFF' ? t('business.detail.note.CATEGORY_TOP_OFF') : note;
            case 'PAUSED':
            case 'RESUMED':
            case 'STOPPED':
            case 'POST_HIDDEN':
            case 'POST_RESTORED':
            case 'REFUND_REQUESTED':
            case 'TAKEN_DOWN':
            case 'REFUND_DENIED':
                return note
                    .split(' · ')
                    .map((part) => UtilBusinessMessages.pauseNoteToken(t, part, action))
                    .filter(Boolean)
                    .join(' · ');
            default:
                return note;
        }
    }

    /**
     * 정지·재개·중단·글 숨김·내림·환불 기록의 토큰 하나 — 요청 경로(`PHONE`·`EMAIL`·`OTHER` — 운영팀 대신 처리) · `FROM_PAUSE`(정지 중 중단) · `MIN:<분>`(멈춘 시간) ·
     * 내림 까닭(`ADVERTISER_PAUSE`·`ADVERTISER_STOP`·`EMERGENCY`·`SUSPENDED` — 정지·재개·중단 줄은 제목이 이미 말하므로 뺀다) · `RESTORED`.
     * 그 밖의 코드 모양 토큰(대문자·밑줄)은 사업자에게 뜻이 없어 빈 글자(부르는 쪽이 거른다), 사유 글은 그대로.
     */
    static pauseNoteToken(t: Translate, token: string, action?: BizHistoryEntry['action']): string {
        if (token === 'PHONE' || token === 'EMAIL' || token === 'OTHER') return t(`business.pause.channelNote.${token}`);
        if (token === 'FROM_PAUSE') return t('business.pause.fromPauseNote');
        if (token.startsWith('MIN:')) return t('business.pause.minutesNote', { span: UtilBusinessMessages.minutes(t, Number(token.slice(4)) || 0) });
        if (token === 'SUSPENDED' || token === 'RESTORED') return t(`business.detail.note.${token}`);
        if (token === 'ADVERTISER_PAUSE' || token === 'ADVERTISER_STOP' || token === 'EMERGENCY') {
            if (action === 'PAUSED' || action === 'RESUMED' || action === 'STOPPED') return '';
            if (action === 'POST_RESTORED' && token === 'ADVERTISER_PAUSE') return t('business.detail.note.cause.RESUMED');
            return t(`business.detail.note.cause.${token}`);
        }
        return /^[A-Z][A-Z0-9]*(?:_[A-Z0-9]+)+(?::\S*)?$/.test(token) ? '' : token;
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
