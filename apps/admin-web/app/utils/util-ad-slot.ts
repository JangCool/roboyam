import type { AdType } from '~/composables/ad/api/useAdApi.types';

/** 미리보기로 띄울 공개 화면 — 홈 · 게시판 목록 · 글 보기 · 가젯 페이지 */
export type AdSlotScreen = 'HOME' | 'BOARD' | 'POST' | 'PAGE';
export const AD_SLOT_SCREENS: readonly AdSlotScreen[] = ['HOME', 'BOARD', 'POST', 'PAGE'];

/** 관리자 → 공개 사이트(user-web `util-ad-slot-area.ts` 와 같은 값) */
export const AD_SLOT_PREVIEW_APPLY = 'irobit:ad-slot-preview';
/** 공개 사이트 → 관리자 — 상자를 눌렀다 { slotType } · 찾은 자리 수 { counts, empty } */
export const AD_SLOT_PREVIEW_PICK = 'irobit:ad-slot-preview-pick';
export const AD_SLOT_PREVIEW_AREAS = 'irobit:ad-slot-preview-areas';

/**
 * 광고 자리 관리(`/ads/slots`) 화면 규칙 — 입력만 보고 답하는 순수 함수.
 */
export default class UtilAdSlot {
    /**
     * 카드 섬네일 높이(px) — 전체 폭으로 깔고 종류마다 높이만 정한다(카드를 낮게). 한 줄 광고·핫딜 자리는 섬네일이 없다(null).
     * 가로로 긴 배너는 잘라(`cover`), 네모·날개는 높이에 맞춰 통째로(`contain`) 보인다.
     */
    static readonly THUMB: Readonly<Record<AdType, { height: number; fit: 'cover' | 'contain' } | null>> = {
        WIDE_BANNER: { height: 88, fit: 'cover' },
        STRIP_BANNER: { height: 40, fit: 'cover' },
        SQUARE_BANNER: { height: 112, fit: 'contain' },
        SMALL_BANNER: { height: 56, fit: 'contain' },
        HALF_BANNER: { height: 48, fit: 'cover' },
        WING_BANNER: { height: 120, fit: 'contain' },
        TEXT_LINE: null,
        HOTDEAL_TOP: null,
        HOTDEAL_CATEGORY: null,
    };

    /**
     * 미리보기 주소 — `{공개 사이트 origin}{path}?adminPreview=1&adSlotPreview=1`(경로에 쿼리가 있으면 그 뒤에 잇는다).
     * @param {string} origin - 공개 사이트 origin.
     * @param {string} path - 공개 화면 경로(`/` · `/boards/{slug}` · `/posts/{id}` · `/p/{key}`).
     * @returns {string} iframe 주소.
     */
    static previewSrc(origin: string, path: string): string {
        const clean = path.startsWith('/') ? path : `/${path}`;
        return `${origin}${clean}${clean.includes('?') ? '&' : '?'}adminPreview=1&adSlotPreview=1`;
    }

    /**
     * 고른 화면의 공개 경로 — 게시판·글·페이지는 하나를 골라야 열린다(아직 안 골랐으면 null).
     * @param {AdSlotScreen} screen - 화면 종류.
     * @param {string | null} target - 게시판 slug · 글 id · 가젯 페이지 공개 경로(`/p/{key}`·`/tips`).
     * @returns {string | null} 경로.
     */
    static screenPath(screen: AdSlotScreen, target: string | null): string | null {
        if (screen === 'HOME') return '/';
        if (!target) return null;
        if (screen === 'BOARD') return `/boards/${encodeURIComponent(target)}`;
        if (screen === 'POST') return `/posts/${encodeURIComponent(target)}`;
        return target.startsWith('/') ? target : `/p/${encodeURIComponent(target)}`;
    }

    /**
     * 공개 사이트가 보낸 「상자를 눌렀다」를 읽는다 — 아는 종류가 아니면 null.
     * @param {unknown} data - `MessageEvent.data`.
     * @param {readonly string[]} types - 아는 광고 종류.
     * @returns {string | null} 누른 종류.
     */
    static readPick(data: unknown, types: readonly string[]): string | null {
        const d = data as Record<string, unknown> | null;
        return d?.type === AD_SLOT_PREVIEW_PICK && typeof d.slotType === 'string' && types.includes(d.slotType) ? d.slotType : null;
    }

    /**
     * 공개 사이트가 보낸 「찾은 자리 수」를 읽는다(종류 → 수, 숫자만).
     * @param {unknown} value - `counts` 또는 `empty`.
     * @returns {Record<string, number>} 읽은 수.
     */
    static readCounts(value: unknown): Record<string, number> {
        const out: Record<string, number> = {};
        if (value && typeof value === 'object') for (const [k, v] of Object.entries(value as Record<string, unknown>)) if (typeof v === 'number' && Number.isFinite(v)) out[k] = v;
        return out;
    }

    /**
     * 기간 끝의 마지막 순간 — 기간은 반열림 `[startAt, endAt)` 이라 카드의 짧은 날짜(「~12-01」)는 끝 시각 1ms 앞 날짜로 적는다.
     * @param {string} endAt - 끝 시각(ISO).
     * @returns {string} 1ms 앞 시각(ISO).
     */
    static lastMoment(endAt: string): string {
        return new Date(Date.parse(endAt) - 1).toISOString();
    }

    /**
     * 고른 카드 중 내릴 수 있는 것(진행·예약 — 기존 긴급 내림과 같은 조건 `UtilAd.canTakedown`)만 남긴다.
     * @param {readonly string[]} picked - 체크한 카드 id.
     * @param {readonly { id: string; status: string }[]} cards - 지금 보이는 카드.
     * @returns {string[]} 내릴 id.
     */
    static takedownIds(picked: readonly string[], cards: readonly { id: string; status: string }[]): string[] {
        const ok = new Set(cards.filter((c) => c.status === 'RUNNING' || c.status === 'SCHEDULED').map((c) => c.id));
        return picked.filter((id) => ok.has(id));
    }
}
