import { mockAdClick, mockAdImpressions, mockAdPlacements, mockPublicAds } from '~/composables/ad/api/mock/ad-mock';
import type { AdClickRequest, AdImpressionsRequest, PublicAdPlacements, PublicAdsQuery, PublicAdsResponse } from '~/composables/ad/api/usePublicAdApi.types';

/**
 * 공개 광고 노출 API — 서버 주소 문자열(`/api/ads/**`)이 있는 **유일한 곳**이다. 상태는 없다(후보 사본·노출 모으기는 `usePublicAds`). 로그인 없이 된다.
 * 설계 정본 `docs/plans/biz-ad-hotdeal/02-ad.md` §7-7 · `docs/features/ad-contract.md` 「공개 노출 쿼리 후보」. 모양은 `usePublicAdApi.types.ts`. **API 후보(정본 후보)**:
 *
 *  - `GET /api/ads?types=WIDE_BANNER,TEXT_LINE,…[&board=&prefix=]` → `PublicAdsResponse { items[{ id, type, version, imageUrl?, text?, linkUrl, altText, weight, deal? }] }`
 *      유형별 **후보 목록** — 그 상품 줄이 있고 `status = 'SCHEDULED' AND start_at <= now < end_at` 인 건 + 최신 **승인된** 소재 버전(`biz_creative_versions.approved_at IS NOT NULL` 중 최신).
 *      서버 사본 20초(`Cache-Control` 공개 캐시 가능 — 보는 사람 몫이 섞이지 않는다). 긴급 내림·되돌리기는 사본 갱신(최대 20초)으로 반영된다.
 *      화면이 비중(`weight`) 가중 무작위로 자리마다 1건(한 줄 광고는 N건 중복 없이)을 고른다(`UtilAd.pick`·`pickMany`).
 *      `HOTDEAL_TOP`·`HOTDEAL_CATEGORY` 는 `board`(핫딜 게시판 slug) · `prefix`(말머리 ID — 말머리 상단만)를 함께 보내고, 후보에 핫딜 글(`deal`)이 붙는다.
 *  - `GET /api/ads/placements` → `PublicAdPlacements { postInline{ enabled, adType: TEXT_LINE|STRIP_BANNER|SQUARE_BANNER, position: BETWEEN_CARDS|AFTER_BODY }, wings{ left, right, screens[HOME|PAGE|BOARD|POST] } }`
 *      `boardList{ enabled, after: 1~20(기본 5) }`(게시판 목록 한 줄 광고 — 일반 글 N번째 뒤 한 줄, 첫 쪽만)
 *      — 가젯이 아닌 자리(글 보기 · 날개 배너 · 게시판 목록) 설정, 사이트 단위. 후보에 `advertiserName`(선택 — 목록 줄의 작성자 자리)을 실어 주면 좋다. 사본 20초. 날개 배너 후보는 `GET /api/ads?types=WING_BANNER`(새 유형 — 서버 상품에 더해야 한다).
 *  - `POST /api/ads/impressions` `{ items[{ adId, version, placement }] }` → `204` — 실제로 화면에 보였을 때 한 번(절반 이상 보임, IntersectionObserver), 1초 모아 보내기.
 *      창을 떠날 때 남은 것은 `sendBeacon`(같은 주소). 집계는 일별 `ad_daily_stats(campaign_id, version, day, impressions, clicks)`(버전 단위, A2).
 *  - `POST /api/ads/{id}/clicks` `{ version, placement }` → `204` — 링크를 누를 때 `sendBeacon`. 링크 `href` 는 광고 주소 그대로다(서버 리다이렉트를 거치지 않는다).
 *  ⚠ `sendBeacon` 은 CSRF 머리글을 붙일 수 없다 — 서버가 두 집계 주소를 CSRF 검사에서 빼야 한다(로그인 불필요·멱등 아님 — 서버가 IP·세션 기준으로 걸러 부풀림을 막는다, 정책 미확정).
 *
 * ⚠ MOCK: 서버에 API 가 없어 `./mock/ad-mock.ts` 를 부른다. 서버가 붙으면 `// MOCK:` 줄과 목업 import 를 지우고 `// REAL:` 줄을 살린다.
 */
const PATH = {
    ads: '/api/ads',
    placements: '/api/ads/placements',
    impressions: '/api/ads/impressions',
    click: (id: string) => `/api/ads/${encodeURIComponent(id)}/clicks`,
};

/** `sendBeacon` 으로 JSON 을 보낸다 — 창이 닫혀도 브라우저가 보낸다. 못 보내면(없음·거절) false */
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
        /** 유형별 노출 후보 */
        list: (query: PublicAdsQuery, signal?: AbortSignal): Promise<PublicAdsResponse> =>
            // MOCK:
            (void signal, mockPublicAds(query)),
        // REAL: client.get<PublicAdsResponse>(PATH.ads, { query: { types: query.types.join(','), board: query.board, prefix: query.prefix }, signal }),

        /** 가젯이 아닌 자리 설정(글 보기 · 날개 배너) */
        placements: (signal?: AbortSignal): Promise<PublicAdPlacements> =>
            // MOCK:
            (void signal, mockAdPlacements()),
        // REAL: client.get<PublicAdPlacements>(PATH.placements, { signal }),

        /** 노출 모아 보내기 */
        impressions: (request: AdImpressionsRequest): Promise<void> =>
            // MOCK:
            mockAdImpressions(request),
        // REAL: client.post<void>(PATH.impressions, request),

        /** 창을 떠날 때 남은 노출 — `sendBeacon`(못 보내면 false) */
        impressionsBeacon: (request: AdImpressionsRequest): boolean =>
            // MOCK:
            (void mockAdImpressions(request), true),
        // REAL: beacon(PATH.impressions, request),

        /** 클릭 — `sendBeacon`(링크 이동을 막지 않는다) */
        click: (adId: string, request: AdClickRequest): void =>
            // MOCK:
            mockAdClick(adId, request),
        // REAL: void beacon(PATH.click(adId), request),
    };
}
