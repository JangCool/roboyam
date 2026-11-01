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

export interface AdFrame {
    ratio: string;
    maxWidth: string;
}

export interface BoardFeedPlacement {
    enabled: boolean;
    after: number;
}

const TYPES: readonly PublicAdType[] = ['WIDE_BANNER', 'STRIP_BANNER', 'SQUARE_BANNER', 'SMALL_BANNER', 'WING_BANNER', 'TEXT_LINE', 'HOTDEAL_TOP', 'HOTDEAL_CATEGORY'];
const BANNER_TYPES: readonly BannerAdType[] = ['WIDE_BANNER', 'STRIP_BANNER', 'SQUARE_BANNER', 'SMALL_BANNER'];
const PLACEMENTS: readonly AdPlacement[] = ['HOME', 'PAGE', 'BOARD', 'POST_INLINE', 'HOTDEAL_TOP', 'HOTDEAL_CATEGORY', 'WING_LEFT', 'WING_RIGHT', 'BOARD_LIST'];
const INLINE_TYPES: readonly PostInlineAdType[] = ['TEXT_LINE', 'STRIP_BANNER', 'SQUARE_BANNER'];
const WING_SCREENS: readonly WingScreen[] = ['HOME', 'PAGE', 'BOARD', 'POST'];

/** 비율은 규격(1200×300 · 1200×100 · 300×250 · 300×100 · 160×600)과 같다(`BusinessAdPreview` 와 같은 값) — 이미지가 늦게 와도 자리가 흔들리지 않게 */
const FRAMES: Readonly<Record<ImageAdType, AdFrame>> = {
    WIDE_BANNER: { ratio: '4 / 1', maxWidth: '100%' },
    STRIP_BANNER: { ratio: '12 / 1', maxWidth: '100%' },
    SQUARE_BANNER: { ratio: '6 / 5', maxWidth: '300px' },
    SMALL_BANNER: { ratio: '3 / 1', maxWidth: '300px' },
    WING_BANNER: { ratio: '4 / 15', maxWidth: '160px' },
};

/** 날개 폭 · 본문과 날개 사이 · 날개와 창 끝 사이(px) */
const WING = { width: 160, gap: 16, edge: 16 } as const;
const FEED_AFTER = { min: 1, max: 20, initial: 5 } as const;
const GADGET_KINDS: readonly string[] = ['AD', 'AD_TEXT_LINE'];
const TEXT_MAX = 40;
const LINES = { min: 1, max: 5, initial: 3 } as const;
const MAX_ADS = 100;

function obj(value: unknown): Record<string, unknown> {
    return typeof value === 'object' && value !== null && !Array.isArray(value) ? (value as Record<string, unknown>) : {};
}

function str(value: unknown, max: number): string | null {
    return typeof value === 'string' && value.trim() !== '' ? value.slice(0, max) : null;
}

/** 광고주 주소는 http(s) 만(스크립트 주소 금지), 핫딜 노출은 사이트 안 `/posts/{id}` 만 */
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

/** 공개 media 주소(사이트 안 경로) 또는 http(s) */
function imageOf(value: unknown): string | null {
    if (typeof value !== 'string' || value === '') return null;
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
    if (UtilAd.isImage(type) && !imageUrl) return null;
    if (type === 'TEXT_LINE' && !text) return null;
    if ((type === 'HOTDEAL_TOP' || type === 'HOTDEAL_CATEGORY') && !deal) return null;
    return { id: a.id, type, version, imageUrl, text, linkUrl, altText: str(a.altText, 200) ?? text ?? '', weight, deal, advertiserName: str(a.advertiserName, 40) };
}

/** 공개 광고 노출의 순수 계산 — 설계 `docs/plans/biz-ad-hotdeal/02-ad.md` §7-7 */
export default class UtilAd {
    static readonly TYPES = TYPES;

    /** `noreferrer` 는 붙이지 않는다 — 광고주가 유입 집계에 리퍼러를 쓸 수 있다(리퍼러 정책은 법무·광고주 합의 뒤) */
    static readonly REL = 'sponsored nofollow noopener';

    static readonly WING = WING;

    static isBanner(type: string): type is BannerAdType {
        return (BANNER_TYPES as readonly string[]).includes(type);
    }

    static isImage(type: string): type is ImageAdType {
        return UtilAd.isBanner(type) || type === 'WING_BANNER';
    }

    static isGadgetKind(type: string): boolean {
        return GADGET_KINDS.includes(type);
    }

    static frameOf(type: ImageAdType): AdFrame {
        return FRAMES[type];
    }

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

    /** 비중 가중 무작위로 한 건 */
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

    /** 비중 가중 무작위로 N건(중복 없이) */
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

    /** 사본이 새로 와도 보이던 광고(같은 소재 버전)가 아직 있으면 바꾸지 않는다 — 보는 중에 배너가 바뀌지 않게 */
    static stillIn(current: PublicAd | null, ads: readonly PublicAd[]): boolean {
        return current !== null && ads.some((ad) => ad.id === current.id && ad.version === current.version);
    }

    static clipText(text: string | null): string {
        const s = (text ?? '').trim();
        return s.length > TEXT_MAX ? `${s.slice(0, TEXT_MAX)}…` : s;
    }

    static discount(deal: PublicAdDeal): number | null {
        if (!deal.listPrice || deal.dealPrice >= deal.listPrice) return null;
        return Math.round((1 - deal.dealPrice / deal.listPrice) * 100);
    }

    static gadgetAdType(config: Record<string, unknown>): BannerAdType {
        return BANNER_TYPES.find((t) => t === obj(config).adType) ?? 'WIDE_BANNER';
    }

    static gadgetLines(config: Record<string, unknown>): number {
        const n = obj(config).count;
        return typeof n === 'number' && Number.isFinite(n) ? Math.min(LINES.max, Math.max(LINES.min, Math.round(n))) : LINES.initial;
    }

    static gadgetSlotType(gadget: Pick<HomeGadget, 'type' | 'config'>): PublicAdType | null {
        if (gadget.type === 'AD') return UtilAd.gadgetAdType(gadget.config);
        if (gadget.type === 'AD_TEXT_LINE') return 'TEXT_LINE';
        return null;
    }

    static gadgetTypes(gadgets: readonly Pick<HomeGadget, 'type' | 'config'>[]): PublicAdType[] {
        const want = new Set(gadgets.map(UtilAd.gadgetSlotType).filter((t): t is PublicAdType => t !== null));
        return TYPES.filter((t) => want.has(t));
    }

    /** 광고 가젯의 `data` 는 화면이 채운다(`useAdGadgets`) — 모양이 아니면 받는 중 */
    static gadgetData(data: unknown): AdGadgetData {
        const d = obj(data);
        return { ads: Array.isArray(d.ads) ? (d.ads as PublicAd[]) : null, placement: PLACEMENTS.find((p) => p === d.placement) ?? 'HOME' };
    }

    /** 기본은 한 줄 광고 — 띠 배너 12:1 은 모바일 360px 에서 높이 약 30px 라 읽기 어렵다 */
    static inlineType(value: unknown): PostInlineAdType {
        return INLINE_TYPES.find((t) => t === value) ?? 'TEXT_LINE';
    }

    static inlinePosition(value: unknown): PostInlinePosition {
        return value === 'AFTER_BODY' ? 'AFTER_BODY' : 'BETWEEN_CARDS';
    }

    /** 날개를 둘 수 있는 화면 — 홈 · 가젯 페이지 · 게시판 목록 · 글 보기(로그인·설정·결제 등은 null) */
    static wingScreen(path: string): WingScreen | null {
        if (path === '/' || path === '') return 'HOME';
        if (/^\/p\/[^/]+\/?$/.test(path)) return 'PAGE';
        if (/^\/boards\/[^/]+\/?$/.test(path)) return 'BOARD';
        if (/^\/posts\/[^/]+\/?$/.test(path)) return 'POST';
        return null;
    }

    static wingScreens(value: unknown): WingScreen[] {
        return Array.isArray(value) ? WING_SCREENS.filter((s) => value.includes(s)) : [];
    }

    /** 양쪽에 같은 광고를 두지 않는다 — 왼쪽부터 고르고 남은 후보에서 오른쪽 */
    static pickWings(ads: readonly PublicAd[], sides: { left: boolean; right: boolean }, random: () => number = Math.random): { left: PublicAd | null; right: PublicAd | null } {
        const left = sides.left ? UtilAd.pick(ads, random) : null;
        const rest = left ? ads.filter((ad) => ad.id !== left.id) : ads;
        const right = sides.right ? UtilAd.pick(rest, random) : null;
        return { left, right };
    }

    static feedPlacement(value: unknown): BoardFeedPlacement {
        const v = obj(value);
        const after = typeof v.after === 'number' && Number.isFinite(v.after) ? Math.min(FEED_AFTER.max, Math.max(FEED_AFTER.min, Math.round(v.after))) : FEED_AFTER.initial;
        return { enabled: v.enabled === true, after };
    }

    /** 한 줄 광고를 넣을 일반 글 차례(그 줄 뒤, 0부터) — 글이 적으면 맨 끝, 없으면 null */
    static feedIndex(rows: number, after: number): number | null {
        if (rows <= 0) return null;
        return Math.min(rows, after) - 1;
    }

    /**
     * 게시판 맥락 광고(목록 한 줄·글 보기·핫딜·게시판 화면의 날개)를 그리지 않는 게시판 — 「광고 숨김」(`adsHidden`), 또는 19금(`allowAdult`)인데 자리 설정 `adult.enabled` 가 꺼짐.
     * `adultEnabled` 는 자리 설정을 아직 못 받았으면 false 로 넘긴다.
     * 메뉴의 게시판 요약(`BoardView`)에는 `adsHidden` 이 없다 — 상세를 받기 전에는 서버의 `board` 판정(빈 목록)이 막는다.
     */
    static boardAdsHidden(board: { adsHidden?: boolean; allowAdult?: boolean } | null, adultEnabled: boolean): boolean {
        return board !== null && (board.adsHidden === true || (board.allowAdult === true && !adultEnabled));
    }

    /** 게시판 화면(`/boards/<slug>`)의 slug — 날개를 그 게시판 판정으로 부른다. 다른 주소는 빈 글자 */
    static boardSlugOf(path: string): string {
        const m = /^\/boards\/([^/]+)\/?$/.exec(path);
        if (!m?.[1]) return '';
        try {
            return decodeURIComponent(m[1]);
        } catch {
            return m[1];
        }
    }

    /** 게시판 화면·글 보기가 알려 준 맥락으로 날개를 뺄지 — 소모임 · 광고 숨김 · 19금(옵션 꺼짐) */
    static wingContextHidden(ctx: { adsHidden: boolean; adult: boolean; group: boolean }, adultEnabled: boolean): boolean {
        return ctx.group || ctx.adsHidden || (ctx.adult && !adultEnabled);
    }
}
