import type { AdClickRequest, AdImpressionsRequest, PublicAdPlacements, PublicAdsQuery, PublicAdsResponse } from '~/composables/ad/api/usePublicAdApi.types';

/**
 * 공개 광고 노출 API(api 서버, 로그인 없이) — 계약 `docs/features/ad-contract.md` 「공개 노출 목업」.
 *  - `GET /api/ads?types=…[&board=<slug>][&prefix=]` → `PublicAdsResponse`(진행 중 건 + 최신 승인 소재, 서버 사본 20초).
 *    `board` 는 게시판 맥락 호출(목록 한 줄·글 보기·핫딜)만 — 숨김·19금·소모임·공개 아닌 게시판이면 서버가 빈 목록을 준다.
 *  - `GET /api/ads/placements` → `PublicAdPlacements`(저장한 적 없으면 서버 기본값)
 *  - `POST /api/ads/impressions` `{ items[{ adId, version, placement }] }`(0~20건) · `POST /api/ads/{id}/clicks` `{ version, placement }` → data null.
 *    두 집계 주소는 서버가 CSRF 검사에서 뺀다 — `sendBeacon` 이 머리글을 붙이지 못한다.
 */
const PATH = {
    ads: '/api/ads',
    placements: '/api/ads/placements',
    impressions: '/api/ads/impressions',
    click: (id: string) => `/api/ads/${encodeURIComponent(id)}/clicks`,
};

/** 창이 닫혀도 보내진다 — 못 보내면 false */
function beacon(url: string, body: unknown): boolean {
    if (typeof navigator === 'undefined' || typeof navigator.sendBeacon !== 'function') return false;
    return navigator.sendBeacon(url, new Blob([JSON.stringify(body)], { type: 'application/json' }));
}

export function usePublicAdApi() {
    const client = useApiClient();

    return {
        list: (query: PublicAdsQuery, signal?: AbortSignal): Promise<PublicAdsResponse> =>
            client.get<PublicAdsResponse>(PATH.ads, { query: { types: query.types.join(','), board: query.board, prefix: query.prefix }, signal }),

        placements: (signal?: AbortSignal): Promise<PublicAdPlacements> => client.get<PublicAdPlacements>(PATH.placements, { signal }),

        impressions: (request: AdImpressionsRequest): Promise<void> => client.post<void>(PATH.impressions, request),

        /** 창을 떠날 때 남은 노출 */
        impressionsBeacon: (request: AdImpressionsRequest): boolean => beacon(PATH.impressions, request),

        /** 클릭은 링크가 열리는 중에도 나가야 해서 `sendBeacon`, 못 쓰면 보통 요청 */
        click: (adId: string, request: AdClickRequest): void => {
            if (!beacon(PATH.click(adId), request)) client.post<void>(PATH.click(adId), request).catch(() => undefined);
        },
    };
}
