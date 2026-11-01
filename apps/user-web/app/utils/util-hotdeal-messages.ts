import type { Translate } from '#layers/i18n/utils/util-i18n';
import type { WriteBlockedReason } from '~/composables/board/api/useBoardApi.types';
import type { DealEndReason, DealEndRequests, DealStock, PostDeal } from '~/composables/hotdeal/api/useHotdealApi.types';
import type { DealFieldErrors } from '~/components/hotdeal/HotdealDealFields.types';
import UtilBoardMessages from '~/utils/util-board-messages';
import UtilHotdeal, { type DealProblems } from '~/utils/util-hotdeal';

const STOCK_KEY: Readonly<Record<DealStock, string>> = {
    UNKNOWN: 'hotdeal.stockUnknown',
    IN_STOCK: 'hotdeal.stockInStock',
    LOW: 'hotdeal.stockLow',
    SOLD_OUT: 'hotdeal.stockSoldOut',
};

const REASON_KEY: Readonly<Record<DealEndReason, string>> = {
    SOLD_OUT: 'hotdeal.reasonSoldOut',
    PRICE_CHANGED: 'hotdeal.reasonPriceChanged',
    LINK_DEAD: 'hotdeal.reasonLinkDead',
};

/** 딜 칸 → 서버 `fields` 키 */
const FIELD_KEYS: ReadonlyArray<keyof Omit<DealFieldErrors, 'deal'>> = ['productUrl', 'storeName', 'dealPrice', 'normalPrice', 'shippingFee', 'endsAt', 'couponNote', 'priceBasis', 'image'];

/** 핫딜 화면의 계산이 낀 문구 — 상수 문구는 컴포넌트가 `t('hotdeal.…')` 로 부른다(`i18n.md`) */
export default class UtilHotdealMessages {
    /**
     * 원 금액 한 줄 — `12,900원` · `₩12,900`
     * @param {Translate} t - 번역 함수.
     * @param {number} won - 금액.
     * @returns {string} 글자.
     */
    static won(t: Translate, won: number): string {
        return t('hotdeal.won', { n: won.toLocaleString() });
    }

    /**
     * 배송비 한 줄 — 무료배송 · 배송비 모름 · 배송비 3,000원
     * @param {Translate} t - 번역 함수.
     * @param {number | null} fee - 0 무료, null 모름.
     * @returns {string} 글자.
     */
    static shipping(t: Translate, fee: number | null): string {
        if (fee === null) return t('hotdeal.shippingUnknown');
        if (fee === 0) return t('hotdeal.shippingFree');
        return t('hotdeal.shippingFee', { fee: UtilHotdealMessages.won(t, fee) });
    }

    /**
     * 재고 표시
     * @param {Translate} t - 번역 함수.
     * @param {DealStock} stock - 재고.
     * @returns {string} 글자.
     */
    static stock(t: Translate, stock: DealStock): string {
        return t(STOCK_KEY[stock] ?? STOCK_KEY.UNKNOWN);
    }

    /**
     * 종료 요청 사유
     * @param {Translate} t - 번역 함수.
     * @param {DealEndReason} reason - 사유.
     * @returns {string} 글자.
     */
    static endReason(t: Translate, reason: DealEndReason): string {
        return t(REASON_KEY[reason] ?? REASON_KEY.SOLD_OUT);
    }

    /**
     * 사유별 알림 수 한 줄(0 인 사유는 뺀다) — 「품절 2 · 가격 변경 1」
     * @param {Translate} t - 번역 함수.
     * @param {DealEndRequests} requests - 종료 요청 집계.
     * @returns {string} 글자. 알림이 없으면 빈 글자.
     */
    static requestCounts(t: Translate, requests: DealEndRequests): string {
        return [
            requests.bySoldOut > 0 ? t('hotdeal.countSoldOut', { n: requests.bySoldOut }) : '',
            requests.byPriceChanged > 0 ? t('hotdeal.countPriceChanged', { n: requests.byPriceChanged }) : '',
            requests.byLinkDead > 0 ? t('hotdeal.countLinkDead', { n: requests.byLinkDead }) : '',
        ]
            .filter(Boolean)
            .join(' · ');
    }

    /**
     * 끝난 딜의 까닭 한 줄 — 작성자가 종료 · 운영진이 종료 · 회원 알림으로 자동 종료 · 기간 종료
     * @param {Translate} t - 번역 함수.
     * @param {Pick<PostDeal, 'view' | 'endedBy'>} deal - 딜.
     * @returns {string} 글자. 끝나지 않았으면 빈 글자.
     */
    static overText(t: Translate, deal: Pick<PostDeal, 'view' | 'endedBy'>): string {
        if (deal.view === 'EXPIRED') return t('hotdeal.overExpired');
        if (deal.view !== 'ENDED') return '';
        if (deal.endedBy === 'AUTHOR') return t('hotdeal.overAuthor');
        if (deal.endedBy === 'MANAGER') return t('hotdeal.overManager');
        return t('hotdeal.overAuto');
    }

    /**
     * 핫딜 게시판의 글쓰기가 막힌 까닭 — 등급·로그인·이메일은 핫딜 문구, 그 밖은 게시판 문구
     * @param {Translate} t - 번역 함수.
     * @param {WriteBlockedReason | null} reason - 서버가 준 까닭.
     * @param {string | null} gradeLabel - 필요한 등급 이름(`writeMinGradeLabel`).
     * @returns {string} 안내 문구. 까닭이 없으면 빈 글자.
     */
    static writeBlocked(t: Translate, reason: WriteBlockedReason | null, gradeLabel: string | null): string {
        if (reason === 'GRADE_TOO_LOW') return gradeLabel ? t('hotdeal.writeGrade', { grade: gradeLabel }) : t('hotdeal.writeGradeUnknown');
        if (reason === 'LOGIN_REQUIRED') return t('hotdeal.writeLogin');
        if (reason === 'EMAIL_NOT_VERIFIED') return t('hotdeal.writeEmail');
        return UtilBoardMessages.writeBlocked(t, reason, gradeLabel);
    }

    /**
     * 딜 칸별 오류 — 서버 `fields."deal.*"` 가 먼저, 없으면 화면 검사 문구(`problems` 가 null 이면 화면 검사는 아직 보이지 않는다)
     * @param {Translate} t - 번역 함수.
     * @param {Record<string, string>} server - 서버 `fields`.
     * @param {DealProblems | null} problems - 화면 검사 결과.
     * @returns {DealFieldErrors} 칸별 오류.
     */
    static fieldErrors(t: Translate, server: Record<string, string>, problems: DealProblems | null): DealFieldErrors {
        const local = problems ? UtilHotdealMessages.problemTexts(t, problems) : null;
        const out = { deal: server.deal ?? '' } as DealFieldErrors;
        for (const key of FIELD_KEYS) out[key] = server[`deal.${key}`] || (local?.[key] ?? '');
        return out;
    }

    /**
     * 오류가 있는 딜 칸 수(모바일 단계 입력의 「딜 정보에 확인할 칸 N개」)
     * @param {DealFieldErrors} errors - 칸별 오류.
     * @returns {number} 개수.
     */
    static errorCount(errors: DealFieldErrors): number {
        return Object.values(errors).filter(Boolean).length;
    }

    private static problemTexts(t: Translate, p: DealProblems): Partial<Record<keyof DealFieldErrors, string>> {
        const url: Record<NonNullable<DealProblems['productUrl']>, string> = {
            REQUIRED: t('hotdeal.urlRequired'),
            FORMAT: t('hotdeal.urlFormat'),
            TOO_LONG: t('hotdeal.urlTooLong', { max: UtilHotdeal.URL_MAX }),
            BLOCKED: t('hotdeal.urlBlocked'),
            SHORTENER: t('hotdeal.urlShortener'),
        };
        const noteTooLong = t('hotdeal.noteTooLong', { max: UtilHotdeal.NOTE_MAX });
        return {
            productUrl: p.productUrl ? url[p.productUrl] : '',
            storeName: p.storeName === 'REQUIRED' ? t('hotdeal.storeRequired') : p.storeName === 'TOO_LONG' ? t('hotdeal.storeTooLong', { max: UtilHotdeal.STORE_NAME_MAX }) : '',
            dealPrice: p.dealPrice === 'REQUIRED' ? t('hotdeal.dealPriceRequired') : p.dealPrice ? t('hotdeal.priceInvalid') : '',
            normalPrice: p.normalPrice === 'BELOW_DEAL' ? t('hotdeal.normalBelowDeal') : p.normalPrice ? t('hotdeal.priceInvalid') : '',
            shippingFee: p.shippingFee === 'REQUIRED' ? t('hotdeal.shippingRequired') : p.shippingFee ? t('hotdeal.priceInvalid') : '',
            endsAt:
                p.endsAt === 'TOO_SOON'
                    ? t('hotdeal.endsTooSoon')
                    : p.endsAt === 'TOO_LATE'
                      ? t('hotdeal.endsTooLate', { days: UtilHotdeal.ENDS_MAX_DAYS })
                      : p.endsAt
                        ? t('hotdeal.endsInvalid')
                        : '',
            couponNote: p.couponNote ? noteTooLong : '',
            priceBasis: p.priceBasis ? noteTooLong : '',
        };
    }
}
