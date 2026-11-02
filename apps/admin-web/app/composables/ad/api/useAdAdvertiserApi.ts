import type {
    AdAdvertiserCreateBody,
    AdAdvertiserDetail,
    AdAdvertiserPage,
    AdAdvertiserQuery,
    AdAdvertiserUpdateBody,
} from '~/composables/ad/api/useAdAdvertiserApi.types';
import type { AdAdvertiserRevealed, AdStatRange, AdStatsOverview } from '~/composables/ad/api/useAdApi.types';

/**
 * 광고주 관리 API(관리자 `/ad-advertisers`) — 서버 주소가 있는 **유일한 곳**이다. 상태는 없다.
 * 계약 `docs/features/ad-advertiser-contract.md`(규칙 1~11). 응답은 no-store.
 *  - `GET    /api/admin/ad-advertisers?kind&state&q&sort&dir&page&size(≤100)`(`ad.read`) → `AdAdvertiserPage`(직접 등록 + 사업자 — 사업자는 한 줄 `biz-<신원 id>`)
 *  - `GET    /api/admin/ad-advertisers/{id}`(`ad.read`) → `AdAdvertiserDetail`(연결된 광고주의 옛 ID `adv-…` 는 별칭 — `biz-…` 로 돌려준다, 없으면 404 `AD_ADVERTISER_NOT_FOUND`)
 *  - `POST   /api/admin/ad-advertisers`(`ad.manage`) `AdAdvertiserCreateBody` → Detail(같은 이름 400 `fields.name`, 연락처 9~11자리, 이메일 형식)
 *  - `PATCH  /api/admin/ad-advertisers/{id}`(`ad.manage`) `AdAdvertiserUpdateBody` → Detail(빠진 칸 그대로 · 빈 글자 지우기, 사업자 광고주 이름 400 `fields.name`)
 *  - `POST   /api/admin/ad-advertisers/{id}/archive` · `…/unarchive`(`ad.manage`) → Detail(직접 등록만 — 끝나지 않은 건 409 `AD_ADVERTISER_HAS_OPEN_BOOKINGS`, 사업자 409 `AD_ADVERTISER_BUSINESS`)
 *  - `DELETE /api/admin/ad-advertisers/{id}`(`ad.manage`) → 204(직접 등록 + 신청 건 0 만 — 아니면 409 `AD_ADVERTISER_HAS_BOOKINGS`)
 *  - 연락처 평문: `POST /api/admin/ads/advertisers/{id}/reveal` `{ reason }`(`ad.contact.reveal`, 사유 5~200자 — `biz-…` 는 광고 담당자 → 없으면 사업자 신원 연락처)
 *  - 성과: `GET /api/admin/ads/stats?from&to&advertiserId`(`ad.read` — `biz-…` 거르기 = 그 사업자의 모든 건)
 */
const PATH = {
    root: '/api/admin/ad-advertisers',
    one: (id: string) => `${PATH.root}/${encodeURIComponent(id)}`,
    reveal: (id: string) => `/api/admin/ads/advertisers/${encodeURIComponent(id)}/reveal`,
    stats: () => '/api/admin/ads/stats',
};

export function useAdAdvertiserApi() {
    const auth = useAdminAuthApi();
    const client = useApiClient({ refreshCsrf: () => auth.me() });

    return {
        list: (query: AdAdvertiserQuery): Promise<AdAdvertiserPage> => client.get<AdAdvertiserPage>(PATH.root, { query: { ...query } }),

        detail: (id: string): Promise<AdAdvertiserDetail> => client.get<AdAdvertiserDetail>(PATH.one(id)),

        create: (body: AdAdvertiserCreateBody): Promise<AdAdvertiserDetail> => client.post<AdAdvertiserDetail>(PATH.root, body),

        update: (id: string, body: AdAdvertiserUpdateBody): Promise<AdAdvertiserDetail> => client.patch<AdAdvertiserDetail>(PATH.one(id), body),

        setArchived: (id: string, archived: boolean): Promise<AdAdvertiserDetail> => client.post<AdAdvertiserDetail>(`${PATH.one(id)}/${archived ? 'archive' : 'unarchive'}`),

        remove: (id: string): Promise<void> => client.del<void>(PATH.one(id)),

        /** 연락처 평문 — 사유 5~200자. 화면은 `hideAt` 이 지나면 다시 가린다 */
        reveal: (id: string, reason: string): Promise<AdAdvertiserRevealed> => client.post<AdAdvertiserRevealed>(PATH.reveal(id), { reason }),

        /** 광고주 성과(합계·날짜별·유형별) — 광고 성과 화면과 같은 API */
        stats: (id: string, range: AdStatRange): Promise<AdStatsOverview> => client.get<AdStatsOverview>(PATH.stats(), { query: { ...range, advertiserId: id } }),
    };
}
