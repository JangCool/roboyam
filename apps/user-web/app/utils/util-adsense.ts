import type { AdSenseDisplaySlot, AdSenseMode, PublicAdSense } from '~/composables/ad/api/usePublicAdApi.types';

/** 애드센스 단위를 그리는 자리 */
export type AdSensePlace = AdSenseDisplaySlot | 'BOARD_LIST';

/**
 * 단위 모양 — 배너 가젯(가로형·사각형) · 날개(160×600 고정) · 글 보기(반응형 디스플레이) · 목록 한 줄(인피드).
 * `<ins>` 의 `data-ad-format` 등은 `UtilAdSense.insAttrs` 가 정한다.
 */
export type AdSenseFormat = 'horizontal' | 'rectangle' | 'wing' | 'responsive' | 'infeed';

/** 그릴 단위 하나 */
export interface AdSenseUnitSpec {
    place: AdSensePlace;
    format: AdSenseFormat;
    client: string;
    slot: string;
    /** 인피드만 */
    layoutKey: string | null;
    adtest: boolean;
}

/** 애드센스를 빼는 맥락(게시판 화면·글 보기가 알려 준 날개 맥락과 같은 모양 + 비밀글) */
export interface AdSenseContext {
    adsHidden: boolean;
    adult: boolean;
    group: boolean;
    secret?: boolean;
}

const MODES: readonly AdSenseMode[] = ['OFF', 'VERIFY', 'SHOW'];
const DISPLAY_SLOTS: readonly AdSenseDisplaySlot[] = ['WIDE_BANNER', 'STRIP_BANNER', 'SQUARE_BANNER', 'SMALL_BANNER', 'WING_BANNER', 'POST_INLINE'];
const CLIENT_RE = /^ca-pub-\d{16}$/;
const SLOT_RE = /^\d{10}$/;
const LAYOUT_KEY_RE = /^[-+]?[0-9a-z]+(?:[-+][0-9a-z]+)+$/i;

const FORMAT_OF: Readonly<Record<AdSensePlace, AdSenseFormat>> = {
    WIDE_BANNER: 'horizontal',
    STRIP_BANNER: 'horizontal',
    SQUARE_BANNER: 'rectangle',
    SMALL_BANNER: 'horizontal',
    WING_BANNER: 'wing',
    POST_INLINE: 'responsive',
    BOARD_LIST: 'infeed',
};

/**
 * 자리 최소 높이(px) — 광고가 늦게 와도 아래가 덜 밀리게. 애드센스가 실제로 고르는 크기에 가까운 값(가로형 728×90·320×100, 사각형 300×250, 반응형 글 보기 모바일 기준 250).
 * 인피드는 목록 글 줄 높이(부르는 쪽이 `variant` 로 고른다 — `AdSenseFeedRow`).
 */
const MIN_HEIGHT: Readonly<Record<Exclude<AdSenseFormat, 'infeed'>, number>> = {
    horizontal: 90,
    rectangle: 250,
    wing: 600,
    responsive: 250,
};

function obj(value: unknown): Record<string, unknown> {
    return typeof value === 'object' && value !== null && !Array.isArray(value) ? (value as Record<string, unknown>) : {};
}

function slotOf(value: unknown): string | null {
    return typeof value === 'string' && SLOT_RE.test(value) ? value : null;
}

/**
 * 구글 애드센스 공개 노출의 순수 계산 — 장부 광고(사업자 신청·운영자 등록)가 없는 자리만 채운다(장부 우선).
 */
export default class UtilAdSense {
    static readonly DISPLAY_SLOTS = DISPLAY_SLOTS;
    static readonly WING = { width: 160, height: 600 } as const;
    /**
     * 광고 차단으로 보고 자리를 접기까지 기다리는 시간(ms) — push 뒤 이 시간 안에 스크립트가 `<ins>` 를 처리하지 않았거나(`data-adsbygoogle-status` 없음 = 스크립트가 막힘)
     * 차단기가 `<ins>` 를 숨겼으면(높이 0) 빈칸을 남기지 않고 접는다. 처리는 됐는데 광고만 늦게 오는 경우는 접지 않고 기다린다.
     */
    static readonly BLOCKED_COLLAPSE_MS = 5000;

    /** 서버 값 읽기 — 모르는 단계는 끔, 형식이 틀린 단위 ID 는 그 자리만 버린다. 묶음이 없으면 null(끔) */
    static read(raw: unknown): PublicAdSense | null {
        if (raw == null) return null;
        const a = obj(raw);
        const mode = MODES.find((m) => m === a.mode) ?? 'OFF';
        const client = typeof a.client === 'string' && CLIENT_RE.test(a.client) ? a.client : '';
        const u = obj(a.units);
        const units: PublicAdSense['units'] = {};
        for (const key of DISPLAY_SLOTS) {
            const slot = slotOf(u[key]);
            if (slot) units[key] = slot;
        }
        const feed = obj(u.BOARD_LIST);
        const feedSlot = slotOf(feed.slot);
        if (feedSlot && typeof feed.layoutKey === 'string' && LAYOUT_KEY_RE.test(feed.layoutKey)) units.BOARD_LIST = { slot: feedSlot, layoutKey: feed.layoutKey };
        return { mode, client, adtest: a.adtest === true, units };
    }

    /** 화면이 단위를 그리는 단계인가 — 「광고 표시」이고 게시자 ID 가 맞을 때만(「사이트 확인만」은 서버가 머리에 스크립트만 넣는다) */
    static showing(settings: PublicAdSense | null | undefined): settings is PublicAdSense {
        return settings?.mode === 'SHOW' && CLIENT_RE.test(settings.client);
    }

    /** 그 자리의 단위 — 단계가 「광고 표시」가 아니거나 단위 ID 가 없으면 null */
    static unit(settings: PublicAdSense | null | undefined, place: AdSensePlace): AdSenseUnitSpec | null {
        if (!UtilAdSense.showing(settings)) return null;
        if (place === 'BOARD_LIST') {
            const feed = settings.units.BOARD_LIST;
            return feed ? { place, format: 'infeed', client: settings.client, slot: feed.slot, layoutKey: feed.layoutKey, adtest: settings.adtest } : null;
        }
        const slot = settings.units[place];
        return slot ? { place, format: FORMAT_OF[place], client: settings.client, slot, layoutKey: null, adtest: settings.adtest } : null;
    }

    /**
     * 게시판 화면·글 보기 맥락으로 애드센스를 빼는가 — 19금(게시판·글 — 자리 설정 「19금에도 광고 보이기」와 상관없이, 애드센스 정책) · 광고 숨김 · 소모임 · 비밀글.
     * 장부 광고는 이 판정을 쓰지 않는다(지금 규칙 그대로 — `UtilAd.wingContextHidden`·`boardAdsHidden`).
     */
    static contextBlocked(ctx: AdSenseContext): boolean {
        return ctx.adult || ctx.adsHidden || ctx.group || ctx.secret === true;
    }

    /** 목록 한 줄 자리 중 장부 광고가 채우지 못한 자리 — 장부가 앞 자리부터 차지하고(`useBoardFeedAd` 의 차례) 남은 자리를 애드센스로 */
    static feedRest(positions: readonly number[], ledgerCount: number): number[] {
        return positions.slice(Math.max(0, ledgerCount));
    }

    /** 일반 글 차례 `i` 뒤에 넣을 애드센스 인피드 — 없으면 null */
    static feedAt(list: readonly { spec: AdSenseUnitSpec; afterIndex: number }[] | null | undefined, i: number): AdSenseUnitSpec | null {
        return list?.find((f) => f.afterIndex === i)?.spec ?? null;
    }

    /** 자리 최소 높이(px) — 인피드는 null(부르는 쪽이 목록 줄 높이로) */
    static minHeight(format: AdSenseFormat): number | null {
        return format === 'infeed' ? null : MIN_HEIGHT[format];
    }

    /**
     * `<ins class="adsbygoogle">` 에 붙일 속성 — 인피드 `fluid` + 레이아웃 키 · 반응형 `auto` + `data-full-width-responsive` · 배너 가젯 가로형·사각형 힌트 · 날개는 고정 크기(형식 속성 없음, 부품이 160×600 으로 그린다).
     * 시험 표시면 `data-adtest="on"`.
     */
    static insAttrs(spec: AdSenseUnitSpec): Record<string, string> {
        const out: Record<string, string> = { 'data-ad-client': spec.client, 'data-ad-slot': spec.slot };
        if (spec.format === 'infeed') {
            out['data-ad-format'] = 'fluid';
            if (spec.layoutKey) out['data-ad-layout-key'] = spec.layoutKey;
        } else if (spec.format === 'responsive') {
            out['data-ad-format'] = 'auto';
            out['data-full-width-responsive'] = 'true';
        } else if (spec.format === 'horizontal' || spec.format === 'rectangle') {
            out['data-ad-format'] = spec.format;
            out['data-full-width-responsive'] = 'true';
        }
        if (spec.adtest) out['data-adtest'] = 'on';
        return out;
    }
}
