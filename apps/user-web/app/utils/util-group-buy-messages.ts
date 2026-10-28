import { ApiError } from '~/composables/common/useApiClient';
import type { GroupBuyListItem, GroupBuyShipping } from '~/composables/groupbuy/api/useGroupBuyApi.types';
import type { MyGroupBuyOrderSummary, MyGroupBuyRefund } from '~/composables/groupbuy/api/useGroupBuyOrderApi.types';
import UtilGroupBuy from '~/utils/util-group-buy';
import type { Translate } from '#layers/i18n/utils/util-i18n';
import type { DisplayTime } from '#layers/display-time/utils/util-time';

/**
 * 공구 화면의 계산이 낀 문구(금액 · 발송 예정 · 배송비·반품비 고지 · 마감 줄 · 환불 진행 · 오류 한 줄) — `t` 를 첫 인자로 받는다(`i18n.md` 3).
 * 상수 문구는 화면이 `t('groupBuy.*')` 를 직접 부른다. 시각은 표시 값(`useDisplayTime().value`)으로 그린다.
 */
export default class UtilGroupBuyMessages {
    /**
     * 금액 — `47,000원`(영어 `₩47,000`)
     * @param {Translate} t - 번역 함수.
     * @param {number} amount - 원.
     * @returns {string} 금액 글자.
     */
    static won(t: Translate, amount: number): string {
        return t('payment.won', { amount: UtilGroupBuy.amount(amount) });
    }

    /**
     * 부호 붙은 금액 — `+2,000원` · `−1,000원`(회수 방식별 환불 결과)
     * @param {Translate} t - 번역 함수.
     * @param {number} amount - 원(음수면 부족분).
     * @returns {string} 금액 글자.
     */
    static signedWon(t: Translate, amount: number): string {
        if (amount < 0) return `−${UtilGroupBuyMessages.won(t, -amount)}`;
        return `+${UtilGroupBuyMessages.won(t, amount)}`;
    }

    /**
     * 발송 예정 — 「발송 예정일 2026.11.11」 또는 「결제 후 3영업일 안에 발송」(결정 51)
     * @param {Translate} t - 번역 함수.
     * @param {DisplayTime} time - 시각 함수 모음.
     * @param {{ shipPlannedAt: string | null; shipWithinBusinessDays: number | null }} gb - 공구(또는 주문).
     * @returns {string} 한 줄. 둘 다 없으면 빈 글자.
     */
    static shipPlan(t: Translate, time: DisplayTime, gb: { shipPlannedAt: string | null; shipWithinBusinessDays: number | null }): string {
        if (gb.shipPlannedAt) return t('groupBuy.shipPlannedAt', { date: time.serviceDate(gb.shipPlannedAt) });
        if (gb.shipWithinBusinessDays) return t('groupBuy.shipWithin', { days: gb.shipWithinBusinessDays });
        return '';
    }

    /**
     * 배송비·반품비 금액 고지(commerce.md 10-8 끝 예시 — 시행령 제20조 1호: 금액까지) — 줄마다 한 문장
     * @param {Translate} t - 번역 함수.
     * @param {GroupBuyShipping} s - 모집 시작 때 고정한 배송비 사본.
     * @returns {string[]} 문장들.
     */
    static shippingLines(t: Translate, s: GroupBuyShipping): string[] {
        const won = (n: number) => UtilGroupBuyMessages.won(t, n);
        const lines: string[] = [];
        lines.push(
            s.freeShippingOver !== null
                ? t('groupBuy.noticeShippingFree', { fee: won(s.fee), over: won(s.freeShippingOver) })
                : t('groupBuy.noticeShipping', { fee: won(s.fee) }),
        );
        lines.push(t('groupBuy.noticeReturnFee', { fee: won(s.returnFee) }));
        if (s.freeShippingOver !== null) {
            lines.push(t('groupBuy.noticeReturnPartial', { over: won(s.freeShippingOver), fee: won(s.fee) }));
            if (s.returnFeeMode === 'ROUND_TRIP') lines.push(t('groupBuy.noticeRoundTrip', { total: won(s.fee + s.returnFee), fee: won(s.fee), returnFee: won(s.returnFee) }));
            else lines.push(t('groupBuy.noticeOneWay'));
        }
        lines.push(t('groupBuy.noticeCustomerSend'));
        lines.push(t('groupBuy.noticeDefect'));
        return lines;
    }

    /**
     * 목록·상세의 마감 줄 — 연장 중 · 모집 시작 · 마감 시각 · 집계 중 · 종료 사유
     * @param {Translate} t - 번역 함수.
     * @param {DisplayTime} time - 시각 함수 모음.
     * @param {GroupBuyListItem} gb - 공구.
     * @returns {string} 한 줄.
     */
    static deadline(t: Translate, time: DisplayTime, gb: GroupBuyListItem): string {
        if (gb.phase === 'UPCOMING') return t('groupBuy.startsAt', { at: time.shortDateTime(gb.startAt) });
        if (gb.status === 'RECRUITING' && gb.extendedUntil) return t('groupBuy.extendedUntil', { at: time.clock(gb.extendedUntil) });
        if (gb.status === 'RECRUITING') return t('groupBuy.endsAt', { at: time.shortDateTime(gb.endAt) });
        if (gb.status === 'CLOSING') return t('groupBuy.judging');
        if (gb.closeReason) return t(`groupBuy.closeReason.${gb.closeReason}`);
        return t(`groupBuy.status.${gb.status}`);
    }

    /**
     * 환불 진행(결정 16) — 진행 중 · 카드사 반영 3~7영업일 / 완료(날짜) / 지연 확인 중 / 운영자가 직접 처리
     * @param {Translate} t - 번역 함수.
     * @param {DisplayTime} time - 시각 함수 모음.
     * @param {MyGroupBuyRefund} refund - 환불 기록 한 줄.
     * @returns {string} 한 줄.
     */
    static refundProgress(t: Translate, time: DisplayTime, refund: MyGroupBuyRefund): string {
        if (refund.progress === 'COMPLETED') return t('groupBuy.refundCompleted', { date: time.date(refund.completedAt) });
        if (refund.progress === 'DELAYED') return t('groupBuy.refundDelayed');
        if (refund.progress === 'MANUAL') return t('groupBuy.refundManual');
        if (refund.method === 'BANK_TRANSFER') return t('groupBuy.refundBank');
        return t('groupBuy.refundInProgress');
    }

    /**
     * 내 주문 한 줄의 이름 — 「본체 세트 외 1건」(옵션이 없으면 공구 이름 뒤 생략)
     * @param {Translate} t - 번역 함수.
     * @param {MyGroupBuyOrderSummary} s - 목록 한 줄.
     * @returns {string} 줄 이름(옵션 없으면 빈 글자).
     */
    static itemsLabel(t: Translate, s: MyGroupBuyOrderSummary): string {
        if (!s.firstItemName) return '';
        return s.itemCount > 1 ? t('groupBuy.itemsMore', { name: s.firstItemName, count: s.itemCount - 1 }) : s.firstItemName;
    }

    /**
     * 오류 한 줄 — 서버 문구 그대로, 서버 오류가 아니면(네트워크 등) 공통 문구
     * @param {Translate} t - 번역 함수.
     * @param {unknown} e - 잡은 오류.
     * @returns {string} 한 줄.
     */
    static error(t: Translate, e: unknown): string {
        if (e instanceof ApiError && e.message) return e.message;
        return t('common.failed');
    }
}
