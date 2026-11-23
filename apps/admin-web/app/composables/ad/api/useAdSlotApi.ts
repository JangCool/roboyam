import type { AdApplicationStats, AdBookingDetail, AdType } from '~/composables/ad/api/useAdApi.types';
import type { AdSlotListView, AdSlotStatusFilter, AdSlotSummaryView, AdSlotTakedownRequest, AdSlotTakedownResult } from '~/composables/ad/api/useAdSlotApi.types';

/**
 * 광고 자리 관리 API(관리자 `/ads/slots`) — 목업 단계(계약 `docs/features/ad-slot-manager-contract.md` 「API 후보」). 상태는 없다.
 *  - `GET  /api/admin/ads/slots`(`ad.read`) → `AdSlotSummaryView`(종류별 진행 중 수 · 한도 · 애드센스 채움 · 나가는 곳 — 미리보기 이름표·드로어 머리)
 *  - `GET  /api/admin/ads/slots/{type}?status=`(`ad.read`) → `AdSlotListView`(카드 목록 + 거르기별 건수, 최근 7일 노출·클릭, 예시 주소 여부)
 *  - `POST /api/admin/ads/takedown`(`ad.takedown`) `{ ids[], reason, notifyAdvertiser }` → `AdSlotTakedownResult`(기존 단건 긴급 내림과 같은 규칙·감사)
 * 「기존 서랍에서 열기」의 광고 건 상세·성과는 광고 일정표 API(`useAdApi().detail`·`stats` — 주소는 그쪽 한 곳)를 쓴다.
 */
const PATH = {
    slots: '/api/admin/ads/slots',
    slot: (type: AdType) => `${PATH.slots}/${encodeURIComponent(type)}`,
    takedown: '/api/admin/ads/takedown',
};

export function useAdSlotApi() {
    const auth = useAdminAuthApi();
    const client = useApiClient({ refreshCsrf: () => auth.me() });
    const ads = useAdApi();

    return {
        /** 종류별 요약 */
        summary: (): Promise<AdSlotSummaryView> => {
            return client.get<AdSlotSummaryView>(PATH.slots);
        },

        /** 한 종류의 광고 카드 — `status` 가 없으면 전체 */
        list: (type: AdType, status: AdSlotStatusFilter | null): Promise<AdSlotListView> => {
            return client.get<AdSlotListView>(PATH.slot(type), { query: status ? { status } : {} });
        },

        /** 여러 건 내리기 — 사유 하나로 한 번에 */
        takedown: (body: AdSlotTakedownRequest): Promise<AdSlotTakedownResult> => {
            return client.post<AdSlotTakedownResult>(PATH.takedown, body);
        },

        /** 「기존 서랍에서 열기」 — 광고 건 상세 */
        detail: (id: string): Promise<AdBookingDetail> => {
            return ads.detail(id);
        },

        /** 「기존 서랍에서 열기」의 성과 탭 */
        stats: (id: string): Promise<AdApplicationStats> => {
            return ads.stats(id);
        },
    };
}
