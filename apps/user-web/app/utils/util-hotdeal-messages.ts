import type { Translate } from '#layers/i18n/utils/util-i18n';
import type { WriteBlockedReason } from '~/composables/board/api/useBoardApi.types';
import { ApiError } from '~/composables/common/useApiClient';
import type { DealEndReason, DealEndRequests, DealPriceBasis, DealRevision, DealRules, DealStock, PostDeal } from '~/composables/hotdeal/api/useHotdealApi.types';
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

const BASIS_KEY: Readonly<Record<DealPriceBasis, string>> = {
    LISTED: 'hotdeal.basisListed',
    COUPON: 'hotdeal.basisCoupon',
    CARD: 'hotdeal.basisCard',
    MEMBER: 'hotdeal.basisMember',
    OVERSEAS: 'hotdeal.basisOverseas',
};

/** 딜 칸 → 서버 `fields` 키 */
const FIELD_KEYS: ReadonlyArray<keyof Omit<DealFieldErrors, 'deal'>> = ['productUrl', 'storeName', 'dealPrice', 'normalPrice', 'shippingFee', 'priceBasisKind', 'endsAt', 'couponNote', 'priceBasis', 'image'];

function extraNumber(e: ApiError, name: string): number | null {
    const text = e.extraText(name);
    const n = text === undefined ? Number.NaN : Number(text);
    return Number.isFinite(n) ? n : null;
}

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
     * 목록·카드 가격 줄의 배송비 — 무료배송 · +3,000원 · 배송비 모름
     * @param {Translate} t - 번역 함수.
     * @param {number | null} fee - 0 무료, null 모름.
     * @returns {string} 글자.
     */
    static shippingShort(t: Translate, fee: number | null): string {
        if (fee === null) return t('hotdeal.shippingUnknown');
        if (fee === 0) return t('hotdeal.shippingFree');
        return t('hotdeal.shippingPlus', { n: fee.toLocaleString() });
    }

    /**
     * 가격 기준 선택지 이름
     * @param {Translate} t - 번역 함수.
     * @param {DealPriceBasis} kind - 가격 기준.
     * @returns {string} 글자.
     */
    static priceBasis(t: Translate, kind: DealPriceBasis): string {
        return t(BASIS_KEY[kind] ?? BASIS_KEY.LISTED);
    }

    /**
     * 마감 임박 배지 — 「N시간 남음」 · 「내일 마감」
     * @param {Translate} t - 번역 함수.
     * @param {{ hours: number; tomorrow: boolean }} soon - `UtilHotdeal.endingSoon` 결과.
     * @returns {string} 글자.
     */
    static endingSoon(t: Translate, soon: { hours: number; tomorrow: boolean }): string {
        return soon.tomorrow ? t('hotdeal.badgeTomorrow') : t('hotdeal.badgeHoursLeft', { n: soon.hours });
    }

    /**
     * 남은 시간 — 「3시간 20분」 · 「45분」(1분 미만은 1분)
     * @param {Translate} t - 번역 함수.
     * @param {number} ms - 남은 밀리초.
     * @returns {string} 글자.
     */
    static remaining(t: Translate, ms: number): string {
        const minutes = Math.max(1, Math.ceil(ms / 60_000));
        const h = Math.floor(minutes / 60);
        const m = minutes % 60;
        if (h === 0) return t('hotdeal.leftMinutes', { m });
        return m === 0 ? t('hotdeal.leftHours', { h }) : t('hotdeal.leftHoursMinutes', { h, m });
    }

    /**
     * 같은 딜 줄의 가격 차 — 「이 글보다 3,000원 저렴」 · 「… 비쌈」 · 「같은 가격」(딜가만 비교 — 배송비는 따로)
     * @param {Translate} t - 번역 함수.
     * @param {number} other - 그 글의 딜가.
     * @param {number | null} base - 이 글(쓰는 중이면 지금 칸)의 딜가 — 없으면 빈 글자.
     * @returns {string} 글자.
     */
    static priceDiff(t: Translate, other: number, base: number | null): string {
        if (base === null) return '';
        const diff = other - base;
        if (diff === 0) return t('hotdeal.diffSame');
        return diff < 0 ? t('hotdeal.diffCheaper', { n: (-diff).toLocaleString() }) : t('hotdeal.diffPricier', { n: diff.toLocaleString() });
    }

    /**
     * 딜 칸 수정 이력 한 줄(D3) — 「가격 수정 2회 · 링크 바뀜」. 고친 적이 없으면 빈 글자
     * @param {Translate} t - 번역 함수.
     * @param {DealRevision} revision - 수정 이력 요약.
     * @returns {string} 글자.
     */
    static revision(t: Translate, revision: DealRevision): string {
        return [revision.priceEdits > 0 ? t('hotdeal.badgePriceEdited', { n: revision.priceEdits }) : '', revision.linkChanged ? t('hotdeal.badgeLinkChanged') : ''].filter(Boolean).join(' · ');
    }

    /**
     * 핫딜 한도·자격·되돌리기 오류의 안내 — 코드로 고른 문구에 서버가 실은 값(`extras`)을 넣는다. 핫딜 코드가 아니거나 값이 모자라면 null(부르는 쪽이 서버 문구를 쓴다)
     *  - `429 DEAL_DAILY_POST_LIMIT`(`limit`) · `429 DEAL_SAME_STORE_TOO_SOON`(`remainingMinutes` — 없으면 `retryAfterSeconds`) · `403 DEAL_END_REQUEST_NOT_ALLOWED`
     *  - `429 DEAL_END_REQUEST_AUTHOR_LIMIT`(`days`·`limit`) · `409 DEAL_KEEP_LIMIT` · `409 DEAL_REOPEN_EXPIRED` · `409 DEAL_REOPEN_USED`
     *  - `429 DEAL_LINK_CHECK_RATE_LIMIT`(링크 판정 분당 한도 — `retryAfterSeconds`) · `409 DEAL_NOT_ENDED`(끝난 딜에서 새 딜 — 아직 진행 중인 딜)
     * @param {Translate} t - 번역 함수.
     * @param {unknown} e - 오류.
     * @returns {string | null} 안내.
     */
    static dealError(t: Translate, e: unknown): string | null {
        if (!(e instanceof ApiError)) return null;
        switch (e.code) {
            case 'DEAL_LINK_CHECK_RATE_LIMIT':
                return e.retryAfterSeconds ? t('hotdeal.linkCheckRateLimitIn', { n: Math.max(1, Math.ceil(e.retryAfterSeconds)) }) : t('hotdeal.linkCheckRateLimit');
            case 'DEAL_NOT_ENDED':
                return t('hotdeal.prefillNotEnded');
            case 'DEAL_DAILY_POST_LIMIT': {
                const limit = extraNumber(e, 'limit');
                return limit !== null ? t('hotdeal.dailyLimit', { n: limit }) : null;
            }
            case 'DEAL_SAME_STORE_TOO_SOON': {
                const minutes = extraNumber(e, 'remainingMinutes') ?? (e.retryAfterSeconds ? Math.ceil(e.retryAfterSeconds / 60) : null);
                return minutes !== null ? t('hotdeal.sameStoreTooSoon', { n: Math.max(1, minutes) }) : null;
            }
            case 'DEAL_END_REQUEST_NOT_ALLOWED':
                return t('hotdeal.endRequestNotAllowed');
            case 'DEAL_END_REQUEST_AUTHOR_LIMIT': {
                const days = extraNumber(e, 'days');
                const limit = extraNumber(e, 'limit');
                return days !== null && limit !== null ? t('hotdeal.endRequestAuthorLimit', { days, n: limit }) : null;
            }
            case 'DEAL_KEEP_LIMIT':
                return t('hotdeal.keepLimit');
            case 'DEAL_REOPEN_EXPIRED':
                return t('hotdeal.reopenExpired');
            case 'DEAL_REOPEN_USED':
                return t('hotdeal.reopenUsed');
            default:
                return null;
        }
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
     * @param {DealRules} rules - 핫딜 설정 공개 칸(`UtilHotdeal.rulesOf`) — 문구의 상한·문턱.
     * @returns {DealFieldErrors} 칸별 오류.
     */
    static fieldErrors(t: Translate, server: Record<string, string>, problems: DealProblems | null, rules: DealRules): DealFieldErrors {
        const local = problems ? UtilHotdealMessages.problemTexts(t, problems, rules) : null;
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

    private static problemTexts(t: Translate, p: DealProblems, rules: DealRules): Partial<Record<keyof DealFieldErrors, string>> {
        const url: Record<NonNullable<DealProblems['productUrl']>, string> = {
            REQUIRED: t('hotdeal.urlRequired'),
            FORMAT: t('hotdeal.urlFormat'),
            TOO_LONG: t('hotdeal.urlTooLong', { max: UtilHotdeal.URL_MAX }),
            BLOCKED: t('hotdeal.urlBlocked'),
            SHORTENER: t('hotdeal.urlShortener'),
            MEMBER_AFFILIATE_OFF: t('hotdeal.urlMemberAffiliateOff'),
        };
        const noteTooLong = t('hotdeal.noteTooLong', { max: UtilHotdeal.NOTE_MAX });
        const tooHigh = t('hotdeal.priceTooHigh', { max: rules.priceMaxWon.toLocaleString() });
        const amount = (v: 'REQUIRED' | 'INVALID' | 'TOO_HIGH' | 'BELOW_DEAL' | null, required: string): string =>
            v === 'REQUIRED' ? required : v === 'TOO_HIGH' ? tooHigh : v === 'BELOW_DEAL' ? t('hotdeal.normalBelowDeal') : v ? t('hotdeal.priceInvalid') : '';
        return {
            productUrl: p.productUrl ? url[p.productUrl] : '',
            storeName: p.storeName === 'REQUIRED' ? t('hotdeal.storeRequired') : p.storeName === 'TOO_LONG' ? t('hotdeal.storeTooLong', { max: UtilHotdeal.STORE_NAME_MAX }) : '',
            dealPrice: amount(p.dealPrice, t('hotdeal.dealPriceRequired')),
            normalPrice: amount(p.normalPrice, ''),
            shippingFee: amount(p.shippingFee, t('hotdeal.shippingRequired')),
            priceBasisKind: p.priceBasisKind === 'REQUIRED_FREE' ? t('hotdeal.basisRequiredFree') : p.priceBasisKind === 'REQUIRED_DISCOUNT' ? t('hotdeal.basisRequiredDiscount', { n: rules.discountWarnPercent }) : '',
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
