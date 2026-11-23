/**
 * 광고 자리 미리보기 — 관리자 「광고 자리 관리」(admin-web `/ads/slots`)가 이 앱을 iframe 으로 `?adminPreview=1&adSlotPreview=1` 로 띄우면,
 * 화면에서 `data-ad-slot="<광고 종류>"` 가 붙은 요소 위에 상자와 이름표를 겹쳐 그린다(`AdSlotOverlay`). 캐시 영역 미리보기(`util-cache-area.ts`)와 같은 틀이다.
 *
 * - `data-ad-slot` 은 늘 붙어 있다(공개 화면에는 아무 영향이 없다). 값은 광고 종류(서버 `BizProduct` 이름) 그대로다:
 *   배너 가젯·글 보기 배너 `AdSlot`(그 `type`) · 날개 `WING_BANNER`(`AdSlot`·미리보기 빈 틀 `AdWings`) · 한 줄 광고 가젯·글 보기 한 줄 `AdTextLines` · 게시판 목록 한 줄 `AdFeedRow`(`TEXT_LINE`) ·
 *   핫딜 게시판 상단·말머리 상단 `AdHotdealPlacement`(그 `placement` = `HOTDEAL_TOP`·`HOTDEAL_CATEGORY`).
 * - 광고가 없는 자리는 `data-ad-slot-empty` 도 붙는다. **이 미리보기에서만** 빈 자리의 틀을 남긴다(`useAdminPreview().adSlotMode` — 일반 방문자 화면은 그대로 접힌다).
 *   애드센스 단위는 관리자 미리보기에서 그리지 않으므로(`useAdSense`) 애드센스가 채울 빈 자리도 빈 틀로 보인다.
 * - 받는 것 `irobit:ad-slot-preview` { labels(종류 → 이름표 글자), active(강조할 종류 — 없으면 null) }.
 * - 보내는 것 `irobit:ad-slot-preview-pick` { slotType }(상자를 눌렀다 — `type` 은 메시지 종류라 따로 이름) · `irobit:ad-slot-preview-areas` { counts, empty }(지금 화면에서 찾은 자리 수 · 그중 빈 자리 수 — 종류마다, 바뀔 때만).
 * 테마·캐시 미리보기와 같은 창·같은 허용 주소를 쓰지만 메시지 종류가 따로라 섞이지 않는다.
 */

/** 관리자 → 공개 사이트 */
export const AD_SLOT_PREVIEW_APPLY = 'irobit:ad-slot-preview';
/** 공개 사이트 → 관리자 — 상자를 눌렀다 · 찾은 자리 수 */
export const AD_SLOT_PREVIEW_PICK = 'irobit:ad-slot-preview-pick';
export const AD_SLOT_PREVIEW_AREAS = 'irobit:ad-slot-preview-areas';

/** 받을 수 있는 광고 종류 — 서버 `BizProduct` 의 광고 유형(admin-web `BIZ_AD_TYPES` 와 같은 목록) */
const AD_SLOT_TYPES: readonly string[] = ['WIDE_BANNER', 'STRIP_BANNER', 'SQUARE_BANNER', 'SMALL_BANNER', 'HALF_BANNER', 'WING_BANNER', 'TEXT_LINE', 'HOTDEAL_TOP', 'HOTDEAL_CATEGORY'];

export interface AdSlotPreviewMessage {
    /** 종류 → 이름표 글자(없으면 종류 이름) */
    labels: Record<string, string>;
    /** 강조할 종류(드로어에 열린 종류) — 없으면 null */
    active: string | null;
}

function isRecord(value: unknown): value is Record<string, unknown> {
    return typeof value === 'object' && value !== null && !Array.isArray(value);
}

export default class UtilAdSlotArea {
    /**
     * 광고 자리 미리보기 창인가 — 관리자 미리보기 창(`UtilAdminPreview.isPreviewWindow`)이면서 주소에 `adSlotPreview=1` 이 있다. 앱이 뜰 때의 주소로 한 번 본다.
     * @param {string} search - 처음 주소의 쿼리.
     * @returns {boolean} 광고 자리 미리보기면 true.
     */
    static isAdSlotPreview(search: string): boolean {
        return new URLSearchParams(search).get('adSlotPreview') === '1';
    }

    /**
     * 아는 광고 종류인가.
     * @param {string | undefined} value - `data-ad-slot` 값.
     * @returns {boolean} 아는 종류면 true.
     */
    static isType(value: string | undefined): value is string {
        return typeof value === 'string' && AD_SLOT_TYPES.includes(value);
    }

    /**
     * 받은 메시지를 읽는다 — 모양이 아니면 null.
     * @param {unknown} data - `MessageEvent.data`.
     * @returns {AdSlotPreviewMessage | null} 읽은 값.
     */
    static read(data: unknown): AdSlotPreviewMessage | null {
        if (!isRecord(data) || data.type !== AD_SLOT_PREVIEW_APPLY) return null;
        const labels: Record<string, string> = {};
        if (isRecord(data.labels)) for (const [k, v] of Object.entries(data.labels)) if (UtilAdSlotArea.isType(k) && typeof v === 'string') labels[k] = v.slice(0, 80);
        const active = typeof data.active === 'string' && UtilAdSlotArea.isType(data.active) ? data.active : null;
        return { labels, active };
    }

    /**
     * 종류마다 찾은 자리 수 · 빈 자리 수.
     * @param {readonly { type: string; empty: boolean }[]} slots - 화면에서 찾은 자리들.
     * @returns 종류 → 수(`counts`), 종류 → 빈 자리 수(`empty` — 0 은 뺀다).
     */
    static counts(slots: readonly { type: string; empty: boolean }[]): { counts: Record<string, number>; empty: Record<string, number> } {
        const counts: Record<string, number> = {};
        const empty: Record<string, number> = {};
        for (const s of slots) {
            counts[s.type] = (counts[s.type] ?? 0) + 1;
            if (s.empty) empty[s.type] = (empty[s.type] ?? 0) + 1;
        }
        return { counts, empty };
    }

    /**
     * 화면 전체를 덮고 자리 사각형만 뚫은 SVG 경로(`fill-rule="evenodd"`) — 나머지 화면을 흐리게 한다.
     * @param {number} width - 창 폭.
     * @param {number} height - 창 높이.
     * @param {readonly { left: number; top: number; width: number; height: number }[]} holes - 뚫을 사각형들.
     * @returns {string} SVG `d` 값.
     */
    static veilPath(width: number, height: number, holes: readonly { left: number; top: number; width: number; height: number }[]): string {
        const r = (n: number) => Math.round(n * 10) / 10;
        let d = `M0 0H${r(width)}V${r(height)}H0Z`;
        for (const h of holes) d += `M${r(h.left)} ${r(h.top)}h${r(h.width)}v${r(h.height)}h${r(-h.width)}Z`;
        return d;
    }
}
