import { mockAdClick, mockAdImpressions, mockAdPlacements, mockPublicAds } from '~/composables/ad/api/mock/ad-mock';
import type { AdClickRequest, AdImpressionsRequest, PublicAdPlacements, PublicAdsQuery, PublicAdsResponse } from '~/composables/ad/api/usePublicAdApi.types';

/**
 * 공개 광고 노출 API — 계약 `docs/features/ad-contract.md`. API 후보:
 *  - `GET /api/ads?types=…[&board=&prefix=]` → `PublicAdsResponse`(진행 중 건 + 최신 승인 소재, 서버 사본 20초)
 *  - `GET /api/ads/placements` → `PublicAdPlacements`
 *  - `POST /api/ads/impressions` `{ items[{ adId, version, placement }] }` → 204
 *  - `POST /api/ads/{id}/clicks` `{ version, placement }` → 204
 * ⚠ `sendBeacon` 은 CSRF 머리글을 붙일 수 없다 — 서버가 두 집계 주소를 CSRF 검사에서 빼야 한다.
 * MOCK: 서버가 붙으면 `// MOCK:` 줄과 목업 import 를 지우고 `// REAL:` 줄을 살린다.
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
    // REAL: const auth = useAuthApi();
    // REAL: const client = useApiClient({ refreshCsrf: () => auth.me() });
    void PATH;
    void beacon;

    return {
        list: (query: PublicAdsQuery, signal?: AbortSignal): Promise<PublicAdsResponse> =>
            // MOCK:
            (void signal, mockPublicAds(query)),
        // REAL: client.get<PublicAdsResponse>(PATH.ads, { query: { types: query.types.join(','), board: query.board, prefix: query.prefix }, signal }),

        placements: (signal?: AbortSignal): Promise<PublicAdPlacements> =>
            // MOCK:
            (void signal, mockAdPlacements()),
        // REAL: client.get<PublicAdPlacements>(PATH.placements, { signal }),

        impressions: (request: AdImpressionsRequest): Promise<void> =>
            // MOCK:
            mockAdImpressions(request),
        // REAL: client.post<void>(PATH.impressions, request),

        /** 창을 떠날 때 남은 노출 */
        impressionsBeacon: (request: AdImpressionsRequest): boolean =>
            // MOCK:
            (void mockAdImpressions(request), true),
        // REAL: beacon(PATH.impressions, request),

        click: (adId: string, request: AdClickRequest): void =>
            // MOCK:
            mockAdClick(adId, request),
        // REAL: void beacon(PATH.click(adId), request),
    };
}
