import type { LocationQuery } from 'vue-router';
import type { BoardDetail, BoardView } from '~/composables/board/api/useBoardApi.types';
import type { DealPostListQuery } from '~/composables/board/api/usePostApi.types';
import type {
    AffiliateMode,
    DealEndReason,
    DealInput,
    DealLinkCheck,
    DealPrefill,
    DealPriceBasis,
    DealRules,
    DealSort,
    DealStock,
    DealView,
    PostDeal,
} from '~/composables/hotdeal/api/useHotdealApi.types';
import type { DealFormValues, DealShippingMode } from '~/components/hotdeal/HotdealDealFields.types';

/** 상품 링크 문제 — 비었음 · http(s) 주소가 아님 · 1000자 넘음 · 차단한 쇼핑몰 · 단축 주소 · 사이트 제휴 방식인데 개인 제휴 링크(D5 — 서버는 `fields."deal.productUrl"` 로 거절) */
export type DealUrlProblem = 'REQUIRED' | 'FORMAT' | 'TOO_LONG' | 'BLOCKED' | 'SHORTENER' | 'MEMBER_AFFILIATE_OFF';
/** 딜 칸별 문제(화면 검사 — 판정은 서버 `DealWriter`(server/api `com.community.hotdeal.service`)와 같은 규칙) */
export interface DealProblems {
    productUrl: DealUrlProblem | null;
    storeName: 'REQUIRED' | 'TOO_LONG' | null;
    /** 금액은 `DealRules.priceMaxWon` 까지 */
    dealPrice: 'REQUIRED' | 'INVALID' | 'TOO_HIGH' | null;
    /** 정상가는 딜가 이상 */
    normalPrice: 'INVALID' | 'BELOW_DEAL' | 'TOO_HIGH' | null;
    shippingFee: 'REQUIRED' | 'INVALID' | 'TOO_HIGH' | null;
    /** 가격 기준 필수 — 0원 딜 · 할인율 ≥ `DealRules.discountWarnPercent` */
    priceBasisKind: 'REQUIRED_FREE' | 'REQUIRED_DISCOUNT' | null;
    /** 마감은 지금 이후 ~ 365일 */
    endsAt: 'INVALID' | 'TOO_SOON' | 'TOO_LATE' | null;
    couponNote: 'TOO_LONG' | null;
    priceBasis: 'TOO_LONG' | null;
}

const NO_PROBLEMS: Readonly<DealProblems> = {
    productUrl: null,
    storeName: null,
    dealPrice: null,
    normalPrice: null,
    shippingFee: null,
    priceBasisKind: null,
    endsAt: null,
    couponNote: null,
    priceBasis: null,
};

const SORT_PARAM: Readonly<Record<DealSort, string>> = { LATEST: 'latest', POPULAR: 'popular', ENDING_SOON: 'ending' };

function one(value: unknown): string {
    return Array.isArray(value) ? String(value[0] ?? '') : typeof value === 'string' ? value : '';
}

function nonNegativeInt(value: number | null): boolean {
    return value !== null && Number.isInteger(value) && value >= 0;
}

/** 제목 앞 `[쇼핑몰]` · 제목 끝 `(…원)` — 목록이 몰·가격을 따로 보여 주므로 지울지 묻는다 */
const TITLE_BRACKET = /^\s*\[[^\]]*\]\s*/;
const TITLE_PRICE = /\s*[(（][^()（）]*원[)）]\s*$/;

/** 금액 하나의 문제 — 비었음은 부르는 쪽이 정한다 */
function amountProblem(value: number, max: number): 'INVALID' | 'TOO_HIGH' | null {
    if (!nonNegativeInt(value)) return 'INVALID';
    return value > max ? 'TOO_HIGH' : null;
}

/**
 * 회원 핫딜(1차)의 순수 계산 — 게시판 종류 판정, 링크 호스트, 할인율 미리보기, 딜 칸의 화면 검사·변환, 목록 주소 쿼리. 상태도 요청도 없다.
 * 규칙의 정본은 계약 `docs/features/hotdeal-contract.md` 「필드·검증」이고 판정은 서버 `DealWriter` 다(길이 상수 `STORE_NAME_MAX`·`NOTE_MAX`·`ENDS_MAX_DAYS` 는 서버와 같은 값).
 */
export default class UtilHotdeal {
    static readonly STORE_NAME_MAX = 40;
    static readonly URL_MAX = 1000;
    static readonly NOTE_MAX = 100;
    /** 마감은 지금 이후 ~ 이 일수 안 */
    static readonly ENDS_MAX_DAYS = 365;
    /** 마감으로 고를 수 있는 가장 이른 때(1분 뒤) — 서버는 「지금 이후」만 본다. 고르는 칸의 최소값에만 쓴다 */
    static readonly ENDS_MIN_LEAD_MS = 60_000;
    /**
     * 핫딜 설정 공개 칸의 기본값(05 §3 — 서버 `deal_settings` 기본값과 같다) — 게시판 응답 `dealRules` 가 없을 때만 쓴다(`rulesOf`). 판정은 서버
     *  - `priceMaxWon` 금액 상한 · `discountWarnPercent` 할인율 경고(가격 기준 필수) · `autoExpireDays` 마감 없는 딜의 기간 종료 · `hotLikeThreshold` HOT 문턱
     */
    static readonly DEFAULT_RULES: Readonly<DealRules> = { priceMaxWon: 100_000_000, discountWarnPercent: 90, autoExpireDays: 30, hotLikeThreshold: 10 };
    /** 마감 임박 배지 — 남은 시간이 이 시간 안 */
    static readonly ENDING_SOON_HOURS = 48;
    /** 가격 기준 선택지 — 화면에 보일 차례 */
    static readonly PRICE_BASES: readonly DealPriceBasis[] = ['LISTED', 'COUPON', 'CARD', 'MEMBER', 'OVERSEAS'];
    /** 목록 정렬 — 화면에 보일 차례 */
    static readonly SORTS: readonly DealSort[] = ['LATEST', 'POPULAR', 'ENDING_SOON'];
    static readonly STOCKS: readonly DealStock[] = ['UNKNOWN', 'IN_STOCK', 'LOW', 'SOLD_OUT'];
    static readonly END_REASONS: readonly DealEndReason[] = ['SOLD_OUT', 'PRICE_CHANGED', 'LINK_DEAD'];
    static readonly SHIPPING_MODES: readonly DealShippingMode[] = ['FREE', 'UNKNOWN', 'PAID'];
    /** 링크를 다 붙여 넣은 뒤 판정을 부르기까지 기다리는 시간 */
    static readonly LINK_CHECK_DEBOUNCE_MS = 600;

    /**
     * 핫딜 게시판인가 — 게시판 종류 `DEAL`. 종류가 없는 옛 응답은 옛 칸 `dealEnabled` 로 본다
     * @param {Pick<BoardView, 'kind' | 'dealEnabled'> | null | undefined} board - 게시판.
     * @returns {boolean} 핫딜 게시판이면 true.
     */
    static isDealBoard(board: Pick<BoardView, 'kind' | 'dealEnabled'> | null | undefined): boolean {
        if (!board) return false;
        if (board.kind) return board.kind === 'DEAL';
        return board.dealEnabled === true;
    }

    /**
     * 핫딜 설정 공개 칸 — 게시판 응답 `dealRules` 가 있으면 그 값(빠진 칸·숫자가 아닌 칸만 기본값), 없으면 기본값(`DEFAULT_RULES`)
     * @param {Pick<BoardDetail, 'dealRules'> | null | undefined} board - 게시판.
     * @returns {DealRules} 설정 칸.
     */
    static rulesOf(board: Pick<BoardDetail, 'dealRules'> | null | undefined): DealRules {
        const d = UtilHotdeal.DEFAULT_RULES;
        const r = board?.dealRules;
        if (!r) return { ...d };
        const num = (v: unknown, fallback: number): number => (typeof v === 'number' && Number.isFinite(v) ? v : fallback);
        return {
            priceMaxWon: num(r.priceMaxWon, d.priceMaxWon),
            discountWarnPercent: num(r.discountWarnPercent, d.discountWarnPercent),
            autoExpireDays: num(r.autoExpireDays, d.autoExpireDays),
            hotLikeThreshold: num(r.hotLikeThreshold, d.hotLikeThreshold),
        };
    }

    /**
     * 링크의 호스트 — 소문자·`www.` 뗌·포트·경로 없음. http(s) 주소가 아니면 null
     * @param {string} url - 상품 링크.
     * @returns {string | null} 호스트.
     */
    static hostOf(url: string): string | null {
        const trimmed = url.trim();
        if (!/^https?:\/\//i.test(trimmed)) return null;
        try {
            const host = new URL(trimmed).hostname.toLowerCase();
            if (!host.includes('.')) return null;
            return host.startsWith('www.') ? host.slice(4) : host;
        } catch {
            return null;
        }
    }

    /**
     * 링크 칸 형식 문제(판정 결과 제외) — 비었음 · 형식 · 길이
     * @param {string} url - 상품 링크.
     * @returns {DealUrlProblem | null} 문제.
     */
    static urlFormatProblem(url: string): DealUrlProblem | null {
        const trimmed = url.trim();
        if (!trimmed) return 'REQUIRED';
        if (trimmed.length > UtilHotdeal.URL_MAX) return 'TOO_LONG';
        return UtilHotdeal.hostOf(trimmed) === null ? 'FORMAT' : null;
    }

    /**
     * 할인율 미리보기 — 정상가가 딜가보다 클 때만 정수 %(내림). 서버 `DealViews.discountRate` 와 같은 식(서버는 0% 도 0 으로 주고, 화면 미리보기는 0% 를 보이지 않는다)
     * @param {number | null} normal - 정상가.
     * @param {number | null} deal - 딜가.
     * @returns {number | null} 할인율.
     */
    static discountRate(normal: number | null, deal: number | null): number | null {
        if (normal === null || deal === null || normal <= 0 || deal < 0 || normal <= deal) return null;
        const rate = Math.floor(((normal - deal) / normal) * 100);
        return rate > 0 ? rate : null;
    }

    /**
     * 끝난 딜인가 — 종료 · 기간 지남(목록에서 흐리게, 글 보기 카드 흐림)
     * @param {DealView} view - 읽을 때 계산한 상태.
     * @returns {boolean} 끝났으면 true.
     */
    static isOver(view: DealView): boolean {
        return view === 'ENDED' || view === 'EXPIRED';
    }

    /** 새 딜 칸의 처음 값 */
    static emptyForm(): DealFormValues {
        return {
            productUrl: '',
            storeName: '',
            storeNameTouched: false,
            dealPrice: null,
            normalPrice: null,
            shippingMode: 'UNKNOWN',
            shippingFee: null,
            endsAt: '',
            stockState: 'UNKNOWN',
            affiliate: false,
            couponNote: '',
            priceBasisKind: null,
            dutyIncluded: false,
            priceBasis: '',
            image: null,
            imageTouched: false,
        };
    }

    /**
     * 끝난 딜에서 새 딜 올리기 — 링크·쇼핑몰 이름·이미지를 채운다(가격·마감은 빈 값 그대로). 이름·이미지는 글쓴이가 고친 것으로 둬 링크 판정이 덮어쓰지 않게 한다
     * @param {DealPrefill} prefill - 끝난 딜에서 받은 값.
     * @returns {DealFormValues} 칸 값.
     */
    static formOfPrefill(prefill: DealPrefill): DealFormValues {
        return {
            ...UtilHotdeal.emptyForm(),
            productUrl: prefill.productUrl,
            storeName: prefill.storeName.slice(0, UtilHotdeal.STORE_NAME_MAX),
            storeNameTouched: true,
            image: prefill.image ? { ...prefill.image } : null,
            imageTouched: Boolean(prefill.image),
        };
    }

    /**
     * 글의 딜 → 칸 값(고치기)
     * @param {PostDeal | null | undefined} deal - 글의 딜.
     * @returns {DealFormValues} 칸 값. 딜이 없으면 빈 값.
     */
    static formOf(deal: PostDeal | null | undefined): DealFormValues {
        if (!deal) return UtilHotdeal.emptyForm();
        const mode: DealShippingMode = deal.shippingFee === null ? 'UNKNOWN' : deal.shippingFee === 0 ? 'FREE' : 'PAID';
        return {
            productUrl: deal.productUrl,
            storeName: deal.storeName,
            storeNameTouched: true,
            dealPrice: deal.dealPrice,
            normalPrice: deal.normalPrice,
            shippingMode: mode,
            shippingFee: mode === 'PAID' ? deal.shippingFee : null,
            endsAt: deal.endsAt ?? '',
            stockState: deal.stockState,
            affiliate: deal.affiliate,
            couponNote: deal.couponNote ?? '',
            priceBasisKind: deal.priceBasisKind ?? null,
            dutyIncluded: deal.dutyIncluded === true,
            priceBasis: deal.priceBasis ?? '',
            image: deal.image ? { ...deal.image } : null,
            imageTouched: true,
        };
    }

    /**
     * 칸 값 → 보낼 딜(`DealInput`) — 앞뒤 공백을 떼고, 빈 메모는 null
     * @param {DealFormValues} form - 칸 값.
     * @returns {DealInput} 보낼 딜.
     */
    static inputOf(form: DealFormValues): DealInput {
        const shippingFee = form.shippingMode === 'FREE' ? 0 : form.shippingMode === 'UNKNOWN' ? null : form.shippingFee;
        return {
            storeName: form.storeName.trim(),
            productUrl: form.productUrl.trim(),
            normalPrice: form.normalPrice,
            dealPrice: form.dealPrice ?? 0,
            shippingFee,
            couponNote: form.couponNote.trim() || null,
            priceBasisKind: form.priceBasisKind,
            dutyIncluded: form.priceBasisKind === 'OVERSEAS' && form.dutyIncluded,
            priceBasis: form.priceBasis.trim() || null,
            endsAt: form.endsAt || null,
            stockState: form.stockState,
            affiliate: form.affiliate,
            image: form.image ? { ...form.image } : null,
        };
    }

    /**
     * 링크 판정 결과로 딜 칸을 채운다 — 쇼핑몰 이름(글쓴이가 고치지 않았으면 표 이름 → OG 이름) · 이미지(글쓴이가 지우거나 올리지 않았으면 OG, 없음·실패면 비움) · 제휴(AFFILIATE 면 켬)
     * @param {DealFormValues} form - 지금 칸 값.
     * @param {DealLinkCheck} check - 판정 결과.
     * @returns {DealFormValues} 채운 칸 값(새 객체).
     */
    static applyLinkCheck(form: DealFormValues, check: DealLinkCheck): DealFormValues {
        const next = { ...form };
        const name = check.storeName ?? check.og.storeName;
        if (!form.storeNameTouched && name) next.storeName = name.slice(0, UtilHotdeal.STORE_NAME_MAX);
        if (!form.imageTouched) next.image = check.og.status === 'OK' && check.og.imageUrl ? { url: check.og.imageUrl, source: 'OG' } : null;
        // 사이트 제휴 방식(D5)이면 개인 제휴 표시를 받지 않는다(체크를 숨긴다) — 끈다
        if (check.affiliateMode === 'SITE') next.affiliate = false;
        else if (check.rule === 'AFFILIATE') next.affiliate = true;
        return next;
    }

    /**
     * 지금 제휴 링크 방식(D5) — 판정 응답의 값이 먼저(더 새것), 없으면 게시판 응답, 둘 다 없으면 `MEMBER`(기본)
     * @param {Pick<BoardDetail, 'dealAffiliateMode'> | null | undefined} board - 게시판.
     * @param {Pick<DealLinkCheck, 'affiliateMode'> | null | undefined} check - 지금 링크의 판정 결과.
     * @returns {AffiliateMode} 방식.
     */
    static affiliateModeOf(board: Pick<BoardDetail, 'dealAffiliateMode'> | null | undefined, check: Pick<DealLinkCheck, 'affiliateMode'> | null | undefined): AffiliateMode {
        return check?.affiliateMode ?? board?.dealAffiliateMode ?? 'MEMBER';
    }

    /**
     * 사이트 제휴 방식인데 개인 제휴 링크인가(D5 — 등록 불가). 판정이 지금 링크의 것일 때만
     * @param {DealLinkCheck | null} check - 판정 결과.
     * @param {string} url - 지금 링크 칸.
     * @returns {boolean} 막을 링크면 true.
     */
    static memberAffiliateOff(check: DealLinkCheck | null, url: string): boolean {
        return Boolean(check && check.affiliateMode === 'SITE' && check.rule === 'AFFILIATE' && check.host === UtilHotdeal.hostOf(url));
    }

    /**
     * 두 칸 값이 보낼 내용으로 같은가(고치기의 「바꿨는가」)
     * @param {DealFormValues} a - 칸 값.
     * @param {DealFormValues} b - 칸 값.
     * @returns {boolean} 같으면 true.
     */
    static same(a: DealFormValues, b: DealFormValues): boolean {
        return JSON.stringify(UtilHotdeal.inputOf(a)) === JSON.stringify(UtilHotdeal.inputOf(b));
    }

    /**
     * 마감으로 고를 수 있는 범위(칸을 연 때 기준)
     * @param {number} now - 지금(밀리초).
     * @returns {{ min: string; max: string }} ISO 범위.
     */
    static endsRange(now: number): { min: string; max: string } {
        return { min: new Date(now + UtilHotdeal.ENDS_MIN_LEAD_MS).toISOString(), max: new Date(now + UtilHotdeal.ENDS_MAX_DAYS * 86_400_000).toISOString() };
    }

    /**
     * 칸 값의 문제 — 링크 판정이 차단이면 링크 칸 문제. 고치기에서 처음 값과 같은 마감은 다시 검사하지 않는다(서버 `DealWriter` 와 같은 규칙)
     * @param {DealFormValues} form - 칸 값.
     * @param {DealLinkCheck | null} check - 지금 링크의 판정 결과(없으면 null).
     * @param {number} now - 지금(밀리초).
     * @param {DealRules} rules - 핫딜 설정 공개 칸(`rulesOf`).
     * @param {string} initialEndsAt - 고치기의 처음 마감(글쓰기는 '').
     * @returns {DealProblems} 칸별 문제.
     */
    static problems(form: DealFormValues, check: DealLinkCheck | null, now: number, rules: DealRules, initialEndsAt = ''): DealProblems {
        const out: DealProblems = { ...NO_PROBLEMS };
        out.productUrl = UtilHotdeal.urlFormatProblem(form.productUrl);
        if (!out.productUrl && check && check.host === UtilHotdeal.hostOf(form.productUrl) && check.rule === 'BLOCK') out.productUrl = check.shortener ? 'SHORTENER' : 'BLOCKED';
        else if (!out.productUrl && UtilHotdeal.memberAffiliateOff(check, form.productUrl)) out.productUrl = 'MEMBER_AFFILIATE_OFF';

        const store = form.storeName.trim();
        out.storeName = !store ? 'REQUIRED' : store.length > UtilHotdeal.STORE_NAME_MAX ? 'TOO_LONG' : null;

        const max = rules.priceMaxWon;
        out.dealPrice = form.dealPrice === null ? 'REQUIRED' : amountProblem(form.dealPrice, max);
        if (form.normalPrice !== null) {
            out.normalPrice = amountProblem(form.normalPrice, max);
            if (!out.normalPrice && form.dealPrice !== null && form.normalPrice < form.dealPrice) out.normalPrice = 'BELOW_DEAL';
        }
        if (form.shippingMode === 'PAID') out.shippingFee = form.shippingFee === null ? 'REQUIRED' : amountProblem(form.shippingFee, max);
        if (!form.priceBasisKind) {
            if (form.dealPrice === 0) out.priceBasisKind = 'REQUIRED_FREE';
            else if (UtilHotdeal.discountWarn(form, rules)) out.priceBasisKind = 'REQUIRED_DISCOUNT';
        }

        if (form.endsAt && form.endsAt !== initialEndsAt) {
            const at = Date.parse(form.endsAt);
            if (Number.isNaN(at)) out.endsAt = 'INVALID';
            else if (at < now) out.endsAt = 'TOO_SOON';
            else if (at > now + UtilHotdeal.ENDS_MAX_DAYS * 86_400_000) out.endsAt = 'TOO_LATE';
        }
        if (form.couponNote.trim().length > UtilHotdeal.NOTE_MAX) out.couponNote = 'TOO_LONG';
        if (form.priceBasis.trim().length > UtilHotdeal.NOTE_MAX) out.priceBasis = 'TOO_LONG';
        return out;
    }

    /**
     * 할인율이 경고 문턱(`DealRules.discountWarnPercent`) 이상인가 — 경고 + 가격 기준 필수
     * @param {Pick<DealFormValues, 'normalPrice' | 'dealPrice'>} form - 칸 값.
     * @param {Pick<DealRules, 'discountWarnPercent'>} rules - 핫딜 설정 공개 칸.
     * @returns {boolean} 문턱 이상이면 true.
     */
    static discountWarn(form: Pick<DealFormValues, 'normalPrice' | 'dealPrice'>, rules: Pick<DealRules, 'discountWarnPercent'>): boolean {
        const rate = UtilHotdeal.discountRate(form.normalPrice, form.dealPrice);
        return rate !== null && rate >= rules.discountWarnPercent;
    }

    /**
     * 제목에 목록이 따로 보여 주는 말이 붙었는가 — 앞 `[쇼핑몰]` · 끝 `(…원)`
     * @param {string} title - 제목.
     * @returns {boolean} 붙었으면 true.
     */
    static titleHasAutoParts(title: string): boolean {
        return TITLE_BRACKET.test(title) || TITLE_PRICE.test(title);
    }

    /**
     * 제목에서 앞 `[…]`·끝 `(…원)` 을 뗀다
     * @param {string} title - 제목.
     * @returns {string} 뗀 제목(앞뒤 공백 없음).
     */
    static stripTitleAutoParts(title: string): string {
        return title.replace(TITLE_BRACKET, '').replace(TITLE_PRICE, '').trim();
    }

    /**
     * 마감 임박 — 진행 중이고 마감이 `ENDING_SOON_HOURS` 안이면 남은 시간(올림 시간, 최소 1). 마감이 내일(이 기기 달력)이고 12시간 넘게 남았으면 `tomorrow`
     * @param {Pick<PostDeal, 'view' | 'endsAt'>} deal - 딜.
     * @param {number} now - 지금(밀리초).
     * @returns {{ hours: number; tomorrow: boolean } | null} 임박이 아니면 null.
     */
    static endingSoon(deal: Pick<PostDeal, 'view' | 'endsAt'>, now: number): { hours: number; tomorrow: boolean } | null {
        if (!deal.endsAt || UtilHotdeal.isOver(deal.view)) return null;
        const at = Date.parse(deal.endsAt);
        const left = at - now;
        if (Number.isNaN(at) || left <= 0 || left > UtilHotdeal.ENDING_SOON_HOURS * 3_600_000) return null;
        const hours = Math.max(1, Math.ceil(left / 3_600_000));
        const tomorrow = new Date(now);
        tomorrow.setDate(tomorrow.getDate() + 1);
        return { hours, tomorrow: hours > 12 && new Date(at).toDateString() === tomorrow.toDateString() };
    }

    /**
     * 「아직 진행 중이에요」를 더 누를 수 있는가 — `keepMax` 0 은 제한 없음
     * @param {Pick<PostDeal, 'keepCount' | 'keepMax'>} deal - 딜.
     * @returns {boolean} 누를 수 있으면 true.
     */
    static canKeep(deal: Pick<PostDeal, 'keepCount' | 'keepMax'>): boolean {
        return deal.keepMax === 0 || deal.keepCount < deal.keepMax;
    }

    /**
     * 문제가 하나라도 있는가
     * @param {DealProblems} problems - 칸별 문제.
     * @returns {boolean} 있으면 true.
     */
    static hasProblem(problems: DealProblems): boolean {
        return Object.values(problems).some((v) => v !== null);
    }

    /**
     * 서버 오류 중 딜 칸 것(`deal`·`deal.*`)이 있는가
     * @param {Record<string, string>} fields - 서버 `fields`.
     * @returns {boolean} 있으면 true.
     */
    static hasDealFields(fields: Record<string, string>): boolean {
        return Object.keys(fields).some((k) => k === 'deal' || k.startsWith('deal.'));
    }

    /**
     * 칸 오류(`fields`)로 붙일 오류 코드인가 — `400 VALIDATION`(사이트 제휴 방식의 개인 제휴 링크도 `fields."deal.productUrl"` 로 이 코드 안에 온다)
     * @param {string} code - 오류 코드.
     * @returns {boolean} 칸 오류로 붙이면 true.
     */
    static isFieldErrorCode(code: string): boolean {
        return code === 'VALIDATION';
    }

    /**
     * 핫딜 쓰기 자격 오류인가 — 서버가 글 저장·링크 판정에서 핫딜 자격(이메일 인증·첫 등급 아님·정지 아님)으로 거절한 `403`
     * @param {string} code - 오류 코드.
     * @returns {boolean} 자격 오류면 true.
     */
    static isWriteBlockedCode(code: string): code is 'GRADE_TOO_LOW' | 'EMAIL_NOT_VERIFIED' | 'ACCOUNT_RESTRICTED' {
        return code === 'GRADE_TOO_LOW' || code === 'EMAIL_NOT_VERIFIED' || code === 'ACCOUNT_RESTRICTED';
    }

    /**
     * 서버 오류에서 딜 칸 것을 뺀다(딜 칸을 고치면 낡은 서버 오류를 지운다)
     * @param {Record<string, string>} fields - 서버 `fields`.
     * @returns {Record<string, string>} 딜 칸 오류를 뺀 것.
     */
    static withoutDealFields(fields: Record<string, string>): Record<string, string> {
        return Object.fromEntries(Object.entries(fields).filter(([k]) => k !== 'deal' && !k.startsWith('deal.')));
    }

    /**
     * 목록 주소의 쿼리 → 핫딜 목록 조건(`?prefix=&sort=latest|popular|ending&hideEnded=false&store=&q=&page=`). 기본값(최신·종료 숨김·1쪽)은 주소에 쓰지 않는다
     * @param {LocationQuery} query - `route.query`.
     * @returns {DealPostListQuery} 목록 조건.
     */
    static parseQuery(query: LocationQuery): DealPostListQuery {
        const sortParam = one(query.sort);
        const sort = (Object.entries(SORT_PARAM).find(([, v]) => v === sortParam)?.[0] as DealSort | undefined) ?? 'LATEST';
        const page = Number.parseInt(one(query.page), 10);
        return { prefix: one(query.prefix), sort, hideEnded: one(query.hideEnded) !== 'false', store: one(query.store).trim(), q: one(query.q).trim(), page: Number.isFinite(page) && page > 0 ? page : 1 };
    }

    /**
     * 핫딜 목록 조건 → 주소의 쿼리(기본값은 뺀다)
     * @param {Partial<DealPostListQuery>} value - 목록 조건.
     * @returns {Record<string, string>} 쿼리.
     */
    static buildQuery(value: Partial<DealPostListQuery>): Record<string, string> {
        const out: Record<string, string> = {};
        if (value.prefix) out.prefix = value.prefix;
        if (value.sort && value.sort !== 'LATEST') out.sort = SORT_PARAM[value.sort];
        if (value.hideEnded === false) out.hideEnded = 'false';
        if (value.store) out.store = value.store;
        if (value.q) out.q = value.q;
        if (value.page && value.page > 1) out.page = String(value.page);
        return out;
    }

    /**
     * 서버로 보낼 정렬 값
     * @param {DealSort} sort - 정렬.
     * @returns {string} `latest` · `popular` · `ending`.
     */
    static sortParam(sort: DealSort): string {
        return SORT_PARAM[sort];
    }
}
