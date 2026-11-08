/**
 * 캐시 영역 미리보기 — 관리자 캐시 관리(admin-web `/settings/caches` 「미리보기」)가 이 앱을 iframe 으로 `?adminPreview=1&cachePreview=1` 로 띄우면,
 * 화면에서 `data-cache-area="<캐시 키>"` 가 붙은 요소 위에 테두리 상자와 이름표를 겹쳐 그린다(`CacheAreaOverlay`). 사용자 결정 2026-10-28: 미리보기는 실제 공개 화면 그대로.
 *
 * - `data-cache-area` 는 늘 붙어 있다(공개 화면에는 아무 영향이 없다). 값은 관리자 캐시 키(서버 캐시 이름) 그대로다:
 *   머리글 로고·이름 `site.info` · 윗줄·사이드 메뉴 `menu.public` · 공지 띠 `site.noticeBar` · 팝업 `site.popups` · 홈 격자 `home.layout` · 가젯 칸 `gadget.<종류>` ·
 *   가젯 페이지 `pages.layout` · 최신글 목록 `home.latest` · 인기글 목록 `home.popular` · 게시판 레이아웃 틀 `boards.layout` · 전체 공지 줄 `notice.global` · 게시판 머리 `board.catalog` · 바닥글 약관 링크 `terms.public`.
 *   캐시하지 않는 가젯 종류는 `nocache:<종류>`(바로가기·안내 글은 배치 캐시에 포함, 광고 2종은 노출 집계·순환 때문에 캐시 안 함), 게시판 본문 칸은 붙이지 않는다.
 *   설정 사본·내부 규칙(2026-11-08): 포인트 화면 요약·규칙·내역 `point.policy` · 핫딜 「구매하러 가기」·글쓰기 링크 칸 `hotdeal.rules` · 핫딜 목록·딜 카드 `hotdeal.settings` ·
 *   알림 패널·알림 화면 목록 `notification.templates` · 포인트 상점 구매·공동구매 주문·결제 단추 `commerce.rateLimits` · 게시판 글쓰기 단추·글쓰기 게시판 고르기·게시판 모아보기 `board.catalog` ·
 *   약관 화면 본문·가입 약관 동의 `terms.public`.
 * - **한 요소가 캐시 여럿에 해당하면 공백으로 구분해 여러 키를 단다**(`data-cache-area="gadget.HOT_DEALS hotdeal.settings"`) — 상자는 하나이고 이름표가 키마다 붙으며, 수는 키마다 센다(`keysOf`).
 * - 받는 것 `roboyam:cache-preview` { keys, showAll, labels?, off? }(off = 꺼진 캐시 키 — 회색 점선) — 강조할 캐시 키 · 모든 영역 보기 · 이름표 글자(관리자가 고른 언어의 캐시 이름).
 * - 보내는 것 `roboyam:cache-preview-pick` { key }(상자를 눌렀다) · `roboyam:cache-preview-areas` { counts }(지금 화면에서 찾은 영역 수 — 키마다, 바뀔 때만).
 * 테마 미리보기(`util-admin-preview.ts`)와 같은 창·같은 허용 주소를 쓰지만 메시지 종류가 따로라 섞이지 않는다.
 */

/** 서버 `HomeGadgetKinds` 중 「가젯 데이터」 캐시를 두는 종류 — admin-web 캐시 관리 목업의 `gadget.<종류>` 와 같은 목록 */
const CACHED_GADGET_KINDS: readonly string[] = ['POST_LIST', 'HIGHLIGHT', 'GALLERY', 'BOARD_CARDS', 'GROUPS', 'GROUP_BUY', 'HOT_DEALS'];
/** 게시판 레이아웃의 고정 칸 「게시판 본문」 — 가젯 캐시가 아니다(`util-admin-preview` 의 `BOARD_BODY` 와 같은 값) */
const BOARD_BODY = 'BOARD_BODY';

/** 관리자 → 공개 사이트 */
export const CACHE_PREVIEW_APPLY = 'roboyam:cache-preview';
/** 공개 사이트 → 관리자 — 상자를 눌렀다 · 찾은 영역 수 */
export const CACHE_PREVIEW_PICK = 'roboyam:cache-preview-pick';
export const CACHE_PREVIEW_AREAS = 'roboyam:cache-preview-areas';
/** 캐시하지 않는 영역의 키 앞머리 */
export const NOCACHE_PREFIX = 'nocache:';

export interface CachePreviewMessage {
    /** 강조할 캐시 키(없으면 강조 없음) */
    keys: string[];
    /** 모든 영역을 이름표와 함께 */
    showAll: boolean;
    /** 키 → 이름표 글자(없으면 키) */
    labels: Record<string, string>;
    /** 꺼진 캐시 키 — 회색 점선으로 「꺼짐」을 구분해 그린다 */
    off: string[];
}

/** 화면 위 사각형(창 기준 px) */
export interface CacheAreaRect {
    left: number;
    top: number;
    width: number;
    height: number;
}

function isRecord(value: unknown): value is Record<string, unknown> {
    return typeof value === 'object' && value !== null && !Array.isArray(value);
}

export default class UtilCacheArea {
    /**
     * 캐시 영역 미리보기 창인가 — 관리자 미리보기 창(`UtilAdminPreview.isPreviewWindow`)이면서 주소에 `cachePreview=1` 이 있다. 앱이 뜰 때의 주소로 한 번 본다.
     * @param {string} search - 처음 주소의 쿼리.
     * @returns {boolean} 캐시 영역 미리보기면 true.
     */
    static isCachePreview(search: string): boolean {
        return new URLSearchParams(search).get('cachePreview') === '1';
    }

    /**
     * 가젯 칸의 영역 키 — 캐시하는 종류는 `gadget.<종류>`, 아니면 `nocache:<종류>`, 게시판 본문은 없음(속성을 달지 않는다).
     * @param {string} type - 가젯 종류.
     * @returns {string | undefined} 영역 키.
     */
    static gadgetArea(type: string): string | undefined {
        if (type === BOARD_BODY) return undefined;
        if (!CACHED_GADGET_KINDS.includes(type)) return `${NOCACHE_PREFIX}${type}`;
        // 핫딜 가젯은 딜 표시에 핫딜 설정 사본도 쓴다
        return type === 'HOT_DEALS' ? `gadget.${type} hotdeal.settings` : `gadget.${type}`;
    }

    /**
     * 속성 값 → 영역 키들(공백으로 구분, 같은 키는 한 번).
     * @param {string | undefined} value - `data-cache-area` 값.
     * @returns {string[]} 키들.
     */
    static keysOf(value: string | undefined): string[] {
        return [...new Set((value ?? '').split(/\s+/).filter(Boolean))];
    }

    /** 캐시하지 않는 영역인가 */
    static isNoCache(key: string): boolean {
        return key.startsWith(NOCACHE_PREFIX);
    }

    /**
     * 받은 메시지를 읽는다 — 모양이 아니면 null.
     * @param {unknown} data - `MessageEvent.data`.
     * @returns {CachePreviewMessage | null} 읽은 값.
     */
    static read(data: unknown): CachePreviewMessage | null {
        if (!isRecord(data) || data.type !== CACHE_PREVIEW_APPLY) return null;
        const keys = Array.isArray(data.keys) ? data.keys.filter((k): k is string => typeof k === 'string' && k.length > 0 && k.length <= 100).slice(0, 50) : [];
        const labels: Record<string, string> = {};
        if (isRecord(data.labels)) for (const [k, v] of Object.entries(data.labels)) if (typeof v === 'string') labels[k] = v.slice(0, 80);
        const off = Array.isArray(data.off) ? data.off.filter((k): k is string => typeof k === 'string' && k.length > 0 && k.length <= 100).slice(0, 100) : [];
        return { keys, showAll: data.showAll === true, labels, off };
    }

    /**
     * 이 영역을 그릴까 — 모든 영역 보기면 전부, 아니면 강조할 키만.
     * @param {string} key - 영역 키.
     * @param {CachePreviewMessage} msg - 받은 값.
     * @returns {boolean} 그리면 true.
     */
    static shown(key: string, msg: CachePreviewMessage): boolean {
        return msg.showAll || msg.keys.includes(key);
    }

    /**
     * 여러 사각형을 감싸는 사각형 — `display: contents` 요소(자기 상자가 없다)는 자식들의 상자를 합친다. 비면 null.
     * @param {readonly CacheAreaRect[]} rects - 사각형들(폭·높이 0 은 뺀다).
     * @returns {CacheAreaRect | null} 감싼 사각형.
     */
    static union(rects: readonly CacheAreaRect[]): CacheAreaRect | null {
        const real = rects.filter((r) => r.width > 0 && r.height > 0);
        if (!real.length) return null;
        const left = Math.min(...real.map((r) => r.left));
        const top = Math.min(...real.map((r) => r.top));
        const right = Math.max(...real.map((r) => r.left + r.width));
        const bottom = Math.max(...real.map((r) => r.top + r.height));
        return { left, top, width: right - left, height: bottom - top };
    }

    /**
     * 키마다 찾은 영역 수.
     * @param {readonly string[]} keys - 화면에 보이는 영역들의 키(같은 키가 여럿일 수 있다).
     * @returns {Record<string, number>} 키 → 수.
     */
    static counts(keys: readonly string[]): Record<string, number> {
        const out: Record<string, number> = {};
        for (const key of keys) out[key] = (out[key] ?? 0) + 1;
        return out;
    }

    /**
     * 화면에서 센 수에 데이터로 센 수를 덮는다 — 데이터로 센 키는 그 수(화면에 숨겨 둔 사이트 팝업처럼 상자가 없어도 「띄울 것이 있다」), 0 이면 뺀다.
     * @param {Record<string, number>} dom - 화면 상자로 센 수.
     * @param {Record<string, number>} data - 부품이 데이터로 센 수.
     * @returns {Record<string, number>} 합친 수.
     */
    static merge(dom: Record<string, number>, data: Record<string, number>): Record<string, number> {
        const out = { ...dom };
        for (const [key, n] of Object.entries(data)) {
            if (n > 0) out[key] = n;
            else delete out[key];
        }
        return out;
    }
}
