import type {
    BannerAdType,
    ImageAdType,
    PublicAd,
    PublicAdDeal,
    PublicAdType,
    PostInlineAdType,
    PostInlinePosition,
    WingScreen,
} from '~/composables/ad/api/usePublicAdApi.types';
import type { AdGadgetData, AdPlacement } from '~/composables/ad/usePublicAds.types';
import type { HomeGadget } from '~/composables/home/api/useHomeGadgetApi.types';

/** 배너 틀 하나 — CSS `aspect-ratio` 값 · 최대 폭 */
export interface AdFrame {
    ratio: string;
    maxWidth: string;
}

/** 게시판 목록 한 줄 광고 자리 — 일반 글 `after` 번째 뒤 */
export interface BoardFeedPlacement {
    enabled: boolean;
    after: number;
}

const TYPES: readonly PublicAdType[] = ['WIDE_BANNER', 'STRIP_BANNER', 'SQUARE_BANNER', 'SMALL_BANNER', 'WING_BANNER', 'TEXT_LINE', 'HOTDEAL_TOP', 'HOTDEAL_CATEGORY'];
const BANNER_TYPES: readonly BannerAdType[] = ['WIDE_BANNER', 'STRIP_BANNER', 'SQUARE_BANNER', 'SMALL_BANNER'];
const PLACEMENTS: readonly AdPlacement[] = ['HOME', 'PAGE', 'BOARD', 'POST_INLINE', 'HOTDEAL_TOP', 'HOTDEAL_CATEGORY', 'WING_LEFT', 'WING_RIGHT', 'BOARD_LIST'];
const INLINE_TYPES: readonly PostInlineAdType[] = ['TEXT_LINE', 'STRIP_BANNER', 'SQUARE_BANNER'];
const WING_SCREENS: readonly WingScreen[] = ['HOME', 'PAGE', 'BOARD', 'POST'];

/**
 * 유형별 틀 — 비율은 규격(1200×300 · 1200×100 · 300×250 · 300×100, 사업자 신청 안내 `business.adTypeSize`)과 같다. 사업자 광고 미리보기(`BusinessAdPreview`)와 같은 값이다.
 * 비율을 고정해 이미지가 늦게 와도 자리가 흔들리지 않는다. 네모·작은 배너는 300px 까지만 넓어진다(옆 칸·글 중간에서 늘어지지 않게).
 * 날개 배너는 160×600(4:15) · 폭 160px 고정(본문 바깥 여백 전용 — `AdWings`).
 */
const FRAMES: Readonly<Record<ImageAdType, AdFrame>> = {
    WIDE_BANNER: { ratio: '4 / 1', maxWidth: '100%' },
    STRIP_BANNER: { ratio: '12 / 1', maxWidth: '100%' },
    SQUARE_BANNER: { ratio: '6 / 5', maxWidth: '300px' },
    SMALL_BANNER: { ratio: '3 / 1', maxWidth: '300px' },
    WING_BANNER: { ratio: '4 / 15', maxWidth: '160px' },
};

/** 날개 배너 폭(px) · 본문 칸과 날개 사이 · 날개와 창 끝 사이 — 이만큼 바깥 여백이 있어야 날개를 띄운다 */
const WING = { width: 160, gap: 16, edge: 16 } as const;
/** 게시판 목록 한 줄 광고 — 일반 글 몇 번째 뒤(1~20, 기본 5) */
const FEED_AFTER = { min: 1, max: 20, initial: 5 } as const;

/** 광고 가젯 종류 — 배너 자리 하나(`AD`) · 한 줄 광고 N줄(`AD_TEXT_LINE`) */
const GADGET_KINDS: readonly string[] = ['AD', 'AD_TEXT_LINE'];

/** 한 줄 광고 문구 한도(서버도 40자까지 받는다) · 한 줄 광고 가젯 줄 수 */
const TEXT_MAX = 40;
const LINES = { min: 1, max: 5, initial: 3 } as const;
/** 응답 한 번에 읽을 후보 상한 — 이상한 응답에 화면이 무거워지지 않게 */
const MAX_ADS = 100;

function obj(value: unknown): Record<string, unknown> {
    return typeof value === 'object' && value !== null && !Array.isArray(value) ? (value as Record<string, unknown>) : {};
}

function str(value: unknown, max: number): string | null {
    return typeof value === 'string' && value.trim() !== '' ? value.slice(0, max) : null;
}

/** 광고 링크로 쓸 수 있는 주소 — 광고주 주소는 `http(s)://`(스크립트 주소 금지), 핫딜 노출은 사이트 안 `/posts/{id}` */
function linkOf(value: unknown, type: PublicAdType): string | null {
    if (typeof value !== 'string' || value.length > 2000) return null;
    if (type === 'HOTDEAL_TOP' || type === 'HOTDEAL_CATEGORY') return /^\/posts\/[A-Za-z0-9_-]{1,40}$/.test(value) ? value : null;
    // eslint-disable-next-line no-control-regex
    if (/[\s\u0000-\u001f]/.test(value)) return null;
    try {
        const url = new URL(value);
        return url.protocol === 'https:' || url.protocol === 'http:' ? url.href : null;
    } catch {
        return null;
    }
}

/** 이미지 주소 — `https:`·`http:`·사이트 안 경로·`data:image/`(목업 자리 이미지) */
function imageOf(value: unknown): string | null {
    if (typeof value !== 'string' || value === '') return null;
    if (value.startsWith('data:image/')) return value;
    if (value.startsWith('/') && !value.startsWith('//')) return value;
    return /^https?:\/\//.test(value) ? value : null;
}

function readDeal(raw: unknown): PublicAdDeal | null {
    const d = obj(raw);
    if (typeof d.postId !== 'string' || typeof d.title !== 'string' || typeof d.dealPrice !== 'number') return null;
    return {
        postId: d.postId,
        title: d.title.slice(0, 200),
        shopName: typeof d.shopName === 'string' ? d.shopName.slice(0, 40) : '',
        dealPrice: d.dealPrice,
        listPrice: typeof d.listPrice === 'number' && d.listPrice > 0 ? d.listPrice : null,
        thumbnailUrl: imageOf(d.thumbnailUrl),
        prefixName: str(d.prefixName, 40),
    };
}

function readAd(raw: unknown): PublicAd | null {
    const a = obj(raw);
    const type = TYPES.find((t) => t === a.type);
    if (!type || typeof a.id !== 'string' || a.id === '') return null;
    const linkUrl = linkOf(a.linkUrl, type);
    if (!linkUrl) return null;
    const weight = typeof a.weight === 'number' && Number.isFinite(a.weight) ? Math.min(10, Math.max(1, Math.round(a.weight))) : 5;
    const version = typeof a.version === 'number' && Number.isInteger(a.version) ? a.version : 0;
    const imageUrl = imageOf(a.imageUrl);
    const text = str(a.text, 200);
    const deal = readDeal(a.deal);
    // 그릴 것이 없는 후보는 버린다 — 배너·날개는 이미지, 한 줄은 문구, 핫딜 노출은 글
    if (UtilAd.isImage(type) && !imageUrl) return null;
    if (type === 'TEXT_LINE' && !text) return null;
    if ((type === 'HOTDEAL_TOP' || type === 'HOTDEAL_CATEGORY') && !deal) return null;
    return { id: a.id, type, version, imageUrl, text, linkUrl, altText: str(a.altText, 200) ?? text ?? '', weight, deal, advertiserName: str(a.advertiserName, 40) };
}

/**
 * 광고 노출의 순수 계산 — 응답 읽기(틀린 링크·그릴 것 없는 후보는 버린다), 비중 가중 무작위 고르기, 유형별 틀, 가젯 설정 읽기. 상태도 요청도 없다.
 * 설계 정본 `docs/plans/biz-ad-hotdeal/02-ad.md` §7-7. 서버 판정(진행 중 · 승인된 소재)은 서버 몫이고 화면은 받은 후보 안에서만 고른다.
 */
export default class UtilAd {
    static readonly TYPES = TYPES;
    static readonly BANNER_TYPES = BANNER_TYPES;
    static readonly GADGET_KINDS = GADGET_KINDS;
    static readonly TEXT_MAX = TEXT_MAX;
    static readonly LINES = LINES;

    /**
     * 광고 링크의 `rel` — 광고(`sponsored`) · 검색 순위에 넘기지 않음(`nofollow`) · 새 탭이 이 창을 못 만지게(`noopener`).
     * `noreferrer` 는 붙이지 않는다: 광고주가 유입 집계에 리퍼러를 쓸 수 있다(02 문서는 `noreferrer` 도 적었다 — 리퍼러 정책은 법무·광고주 합의 뒤).
     */
    static readonly REL = 'sponsored nofollow noopener';

    static readonly WING = WING;
    static readonly FEED_AFTER = FEED_AFTER;

    /** 배너 넷인가(광고 배너 가젯이 고르는 유형 — 날개 배너는 아니다) */
    static isBanner(type: string): type is BannerAdType {
        return (BANNER_TYPES as readonly string[]).includes(type);
    }

    /** 이미지 광고인가(배너 넷 + 날개 배너) */
    static isImage(type: string): type is ImageAdType {
        return UtilAd.isBanner(type) || type === 'WING_BANNER';
    }

    /** 광고 가젯 종류인가(`AD`·`AD_TEXT_LINE`) */
    static isGadgetKind(type: string): boolean {
        return GADGET_KINDS.includes(type);
    }

    /**
     * 배너 틀
     * @param {ImageAdType} type - 이미지 광고 유형.
     * @returns {AdFrame} 비율·최대 폭.
     */
    static frameOf(type: ImageAdType): AdFrame {
        return FRAMES[type];
    }

    /**
     * 응답 읽기 — 모르는 유형·ID 없음·틀린 링크·그릴 것 없는 후보는 버리고, 같은 ID 는 한 번만
     * @param {unknown} raw - `GET /api/ads` 응답.
     * @returns {PublicAd[]} 후보.
     */
    static readAds(raw: unknown): PublicAd[] {
        const list = Array.isArray(obj(raw).items) ? (obj(raw).items as unknown[]) : [];
        const seen = new Set<string>();
        const out: PublicAd[] = [];
        for (const item of list) {
            if (out.length >= MAX_ADS) break;
            const ad = readAd(item);
            if (!ad || seen.has(`${ad.type}:${ad.id}`)) continue;
            seen.add(`${ad.type}:${ad.id}`);
            out.push(ad);
        }
        return out;
    }

    /**
     * 비중 가중 무작위로 한 건 — 비중 3 은 비중 1 보다 세 번 더 자주 나온다
     * @param {readonly PublicAd[]} ads - 후보.
     * @param {() => number} [random] - 0 이상 1 미만(시험용으로 바꿀 수 있다).
     * @returns {PublicAd | null} 고른 광고. 후보가 없으면 null.
     */
    static pick(ads: readonly PublicAd[], random: () => number = Math.random): PublicAd | null {
        if (!ads.length) return null;
        const total = ads.reduce((sum, ad) => sum + Math.max(1, ad.weight), 0);
        let r = random() * total;
        for (const ad of ads) {
            r -= Math.max(1, ad.weight);
            if (r < 0) return ad;
        }
        return ads[ads.length - 1]!;
    }

    /**
     * 비중 가중 무작위로 N건 — 중복 없이(뽑은 것은 빼고 다시 뽑는다). 후보가 N보다 적으면 모두(뽑힌 차례)
     * @param {readonly PublicAd[]} ads - 후보.
     * @param {number} count - 몇 건.
     * @param {() => number} [random] - 0 이상 1 미만.
     * @returns {PublicAd[]} 고른 광고.
     */
    static pickMany(ads: readonly PublicAd[], count: number, random: () => number = Math.random): PublicAd[] {
        const pool = [...ads];
        const out: PublicAd[] = [];
        while (out.length < count && pool.length) {
            const hit = UtilAd.pick(pool, random)!;
            out.push(hit);
            pool.splice(pool.indexOf(hit), 1);
        }
        return out;
    }

    /**
     * 고른 것을 그대로 둘까 — 후보가 새로 와도(사본 갱신) 같은 광고·같은 소재 버전이 아직 있으면 바꾸지 않는다(보는 중에 배너가 바뀌지 않게)
     * @param {PublicAd | null} current - 지금 보이는 광고.
     * @param {readonly PublicAd[]} ads - 새 후보.
     * @returns {boolean} 그대로 두면 true.
     */
    static stillIn(current: PublicAd | null, ads: readonly PublicAd[]): boolean {
        return current !== null && ads.some((ad) => ad.id === current.id && ad.version === current.version);
    }

    /**
     * 한 줄 광고 문구 — 40자까지(넘으면 말줄임표). 좁은 폭의 말줄임은 CSS(`truncate`)가 한다
     * @param {string | null} text - 문구.
     * @returns {string} 보일 문구.
     */
    static clipText(text: string | null): string {
        const s = (text ?? '').trim();
        return s.length > TEXT_MAX ? `${s.slice(0, TEXT_MAX)}…` : s;
    }

    /**
     * 할인율(정수 %) — 정상가가 없거나 딜가가 더 크면 null
     * @param {PublicAdDeal} deal - 핫딜 글.
     * @returns {number | null} 할인율.
     */
    static discount(deal: PublicAdDeal): number | null {
        if (!deal.listPrice || deal.dealPrice >= deal.listPrice) return null;
        return Math.round((1 - deal.dealPrice / deal.listPrice) * 100);
    }

    /* ───────────── 가젯 ───────────── */

    /**
     * 배너 가젯(`AD`)이 보일 배너 유형 — `config.adType`(배너 넷 중 하나, 처음 값 넓은 배너)
     * @param {Record<string, unknown>} config - 가젯 `config`.
     * @returns {BannerAdType} 유형.
     */
    static gadgetAdType(config: Record<string, unknown>): BannerAdType {
        return BANNER_TYPES.find((t) => t === obj(config).adType) ?? 'WIDE_BANNER';
    }

    /**
     * 한 줄 광고 가젯(`AD_TEXT_LINE`)의 줄 수 — `config.count` 1~5(처음 값 3)
     * @param {Record<string, unknown>} config - 가젯 `config`.
     * @returns {number} 줄 수.
     */
    static gadgetLines(config: Record<string, unknown>): number {
        const n = obj(config).count;
        return typeof n === 'number' && Number.isFinite(n) ? Math.min(LINES.max, Math.max(LINES.min, Math.round(n))) : LINES.initial;
    }

    /**
     * 광고 가젯이 받을 광고 유형
     * @param {HomeGadget} gadget - 가젯.
     * @returns {PublicAdType | null} 유형(광고 가젯이 아니면 null).
     */
    static gadgetSlotType(gadget: Pick<HomeGadget, 'type' | 'config'>): PublicAdType | null {
        if (gadget.type === 'AD') return UtilAd.gadgetAdType(gadget.config);
        if (gadget.type === 'AD_TEXT_LINE') return 'TEXT_LINE';
        return null;
    }

    /**
     * 가젯들이 받을 광고 유형(겹치지 않게, 유형 차례)
     * @param {readonly HomeGadget[]} gadgets - 가젯 목록.
     * @returns {PublicAdType[]} 유형.
     */
    static gadgetTypes(gadgets: readonly Pick<HomeGadget, 'type' | 'config'>[]): PublicAdType[] {
        const want = new Set(gadgets.map(UtilAd.gadgetSlotType).filter((t): t is PublicAdType => t !== null));
        return TYPES.filter((t) => want.has(t));
    }

    /**
     * 광고 가젯의 `data` 읽기(화면이 채운 값 — `useAdGadgets`). 모양이 아니면 받는 중으로 본다
     * @param {unknown} data - 가젯 `data`.
     * @returns {AdGadgetData} 후보·자리.
     */
    static gadgetData(data: unknown): AdGadgetData {
        const d = obj(data);
        return { ads: Array.isArray(d.ads) ? (d.ads as PublicAd[]) : null, placement: PLACEMENTS.find((p) => p === d.placement) ?? 'HOME' };
    }

    /* ───────────── 글 보기 ───────────── */

    /**
     * 글 보기 광고 유형 읽기 — 한 줄 광고 · 띠 배너 · 네모 배너(모르면 한 줄 광고 — 이미지 없이 가볍고, 띠 배너 12:1 은 모바일 360px 에서 높이 약 30px 라 읽기 어렵다)
     * @param {unknown} value - 자리 설정의 `adType`.
     * @returns {PostInlineAdType} 유형.
     */
    static inlineType(value: unknown): PostInlineAdType {
        return INLINE_TYPES.find((t) => t === value) ?? 'TEXT_LINE';
    }

    /**
     * 글 보기 광고 자리 읽기 — 두 카드 사이 틈(기본) · 본문 바로 아래
     * @param {unknown} value - 자리 설정의 `position`.
     * @returns {PostInlinePosition} 자리.
     */
    static inlinePosition(value: unknown): PostInlinePosition {
        return value === 'AFTER_BODY' ? 'AFTER_BODY' : 'BETWEEN_CARDS';
    }

    /* ───────────── 날개 배너 ───────────── */

    /**
     * 지금 화면이 날개 배너를 둘 수 있는 화면인가 — 주소로 판정한다: `/` 홈 · `/p/{key}` 가젯 페이지 · `/boards/{slug}` 게시판 목록 · `/posts/{id}` 글 보기.
     * 로그인·가입·설정·마이페이지·쪽지·결제·글쓰기·수정 같은 화면은 null(두지 않는다).
     * @param {string} path - 라우트 경로.
     * @returns {WingScreen | null} 화면.
     */
    static wingScreen(path: string): WingScreen | null {
        if (path === '/' || path === '') return 'HOME';
        if (/^\/p\/[^/]+\/?$/.test(path)) return 'PAGE';
        if (/^\/boards\/[^/]+\/?$/.test(path)) return 'BOARD';
        if (/^\/posts\/[^/]+\/?$/.test(path)) return 'POST';
        return null;
    }

    /**
     * 날개 화면 목록 읽기 — 아는 화면만
     * @param {unknown} value - 자리 설정의 `screens`.
     * @returns {WingScreen[]} 화면.
     */
    static wingScreens(value: unknown): WingScreen[] {
        return Array.isArray(value) ? WING_SCREENS.filter((s) => value.includes(s)) : [];
    }

    /**
     * 날개 둘 — 켠 쪽마다 비중 가중 무작위로 고르되 **양쪽에 같은 광고를 두지 않는다**(왼쪽부터 고르고 남은 후보에서 오른쪽). 후보가 1건이면 한쪽만
     * @param {readonly PublicAd[]} ads - 날개 배너 후보.
     * @param {{ left: boolean; right: boolean }} sides - 켠 쪽.
     * @param {() => number} [random] - 0 이상 1 미만.
     * @returns {{ left: PublicAd | null; right: PublicAd | null }} 쪽마다 광고.
     */
    static pickWings(ads: readonly PublicAd[], sides: { left: boolean; right: boolean }, random: () => number = Math.random): { left: PublicAd | null; right: PublicAd | null } {
        const left = sides.left ? UtilAd.pick(ads, random) : null;
        const rest = left ? ads.filter((ad) => ad.id !== left.id) : ads;
        const right = sides.right ? UtilAd.pick(rest, random) : null;
        return { left, right };
    }

    /* ───────────── 게시판 목록 한 줄 광고 ───────────── */

    /**
     * 게시판 목록 한 줄 광고 자리 읽기 — 꺼짐 · 일반 글 몇 번째 뒤(1~20, 기본 5)
     * @param {unknown} value - 자리 설정의 `boardList`.
     * @returns {BoardFeedPlacement} 자리.
     */
    static feedPlacement(value: unknown): BoardFeedPlacement {
        const v = obj(value);
        const after = typeof v.after === 'number' && Number.isFinite(v.after) ? Math.min(FEED_AFTER.max, Math.max(FEED_AFTER.min, Math.round(v.after))) : FEED_AFTER.initial;
        return { enabled: v.enabled === true, after };
    }

    /**
     * 한 줄 광고를 넣을 차례 — 일반 글(공지·고정 제외) `after` 번째 뒤, 글이 그보다 적으면 맨 끝. 글이 없으면 null(넣지 않는다)
     * @param {number} rows - 일반 글 수.
     * @param {number} after - 몇 번째 뒤.
     * @returns {number | null} 이 차례의 일반 글 **뒤에** 넣는다(0부터 — `rows - 1` 이면 맨 끝).
     */
    static feedIndex(rows: number, after: number): number | null {
        if (rows <= 0) return null;
        return Math.min(rows, after) - 1;
    }

    /**
     * 게시판이 광고를 숨기는가 — 게시판 설정 `adsHidden`(19금 게시판 기본 켬, 02 §3). **아직 서버에 없는 칸**이라 응답에 있을 때만 읽는다(없으면 보인다).
     * 글 보기 광고와 게시판 목록 한 줄 광고가 읽는다(가젯은 읽지 않는다 — 게시판 레이아웃 광고 가젯도 읽을지는 서버 연결 때 정한다).
     * @param {object | null} board - 게시판 정보.
     * @returns {boolean} 숨기면 true.
     */
    static boardAdsHidden(board: object | null): boolean {
        return board !== null && 'adsHidden' in board && (board as { adsHidden?: unknown }).adsHidden === true;
    }
}
